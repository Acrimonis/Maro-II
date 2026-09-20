package ykws.android.maro.spatial.mesh

import android.app.Application
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.coastline.CoastlineRepository
import ykws.android.maro.data.model.RouteMeshArrays
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.model.RouteResult
import ykws.android.maro.data.regulation.RegulatedZonesRepository
import ykws.android.maro.data.route.RouteSpatialAdapter
import ykws.android.maro.spatial.RouteEngine
import ykws.android.maro.spatial.RouteEngineState
import ykws.android.maro.spatial.RoutePointQueries
import ykws.android.maro.spatial.RouteUnavailableReason

/**
 * **The incumbent route engine, and the only thing in the app that knows the mesh exists.**
 *
 * It is the three files behind one door: [`RouteMeshRepository`] decodes the bundled fabric,
 * [`RouteSearch`] walks it and [`RouteSpatialAdapter`] answers the water test and the live cost — and
 * none of them is reachable from outside this package, so the mesh reads as **one path** rather than
 * as the app's default. Everything genuinely shared with a second engine lives in
 * `ykws.android.maro.spatial`: the fillet over a polyline, a turn's geometry, the clock read off a
 * drawn line and the world's read-only queries.
 *
 * Its readiness is the feature's gate: [prepare] is what decodes, and a region with no baked asset is
 * [RouteUnavailableReason.REGION_NOT_BAKED] — **a reason only this engine can give**, which is what
 * lets an engine needing no bake run beside it.
 *
 * **One search is *started* at a time, never one *running* at a time.** A caller cancels its
 * predecessor before asking again rather than asking alongside it, but a cancel is not a join:
 * cancellation is cooperative, so the predecessor can still be finishing the expansion it was in
 * while the next search begins. [`RouteSearch`]'s scratch arrays are shared by those two runs, and
 * they are made safe the way a run is — each run re-initialises every array and stamps every cached
 * lookup before reading it — so the arrays belong to the run that owns them; see [`RouteSearch`]'s
 * own note on cancellation.
 */
class MeshRouteEngine internal constructor(

    /** The world the search reads — the live adapter in the app, a stub in the readings. */
    private val queries: RoutePointQueries,

    /**
     * The live layers' own load, when [queries] is the adapter over them: a route prices its edges
     * from the coastline and the regulation, and an adapter that has not read them yet answers the
     * permissive defaults. Null when the queries were handed in ready.
     */
    private val prepareQueries: (suspend () -> Unit)?,

    /** Where the fabric comes from — the bundle, or a mesh the caller already holds. */
    private val source: MeshSource,

    private val snapRadiusM: Double = RouteSearch.DEFAULT_SNAP_RADIUS_M,
    private val zoneBerthM: () -> Double = { AppConfig.routeZoneBerthM.toDouble() },
    private val turnLateralAccelMps2: () -> Double = { AppConfig.routeTurnLateralAccelMps2.toDouble() },
    private val berthMaxPrice: Double = RouteSearch.BERTH_MAX_PRICE,
    private val shortcutPass: Boolean = true,
    private val warn: (String) -> Unit = { message -> Log.w(RouteSearch.TAG, message) }
) : RouteEngine {

    private val _state = MutableStateFlow<RouteEngineState>(RouteEngineState.NotReady)

    override val state: StateFlow<RouteEngineState> = _state.asStateFlow()

    /** The bundled asset's reader, and null for an engine whose mesh was handed in. */
    private val repository: RouteMeshRepository? =
        (source as? MeshSource.Bundle)?.let { RouteMeshRepository(it.context) }

    /** The search the current mesh answers with; rebuilt only when the mesh instance changes. */
    private var searchFor: RouteMeshArrays? = null
    private var search: RouteSearch? = null

    /**
     * Decodes the bundled fabric if it is not held yet, and reports the readiness that is the
     * feature's gate.
     *
     * **It loads the mesh, and the mesh alone.** The coastline and the regulation the adapter prices
     * from are read per search ([`RouteSpatialAdapter.loadIfNeeded`], which simply picks up a layer
     * that has landed since) rather than here, so [RouteEngineState.Ready] does not promise that they
     * have loaded: a route asked before the app's own layers are in is priced as open water and no
     * zone. That is the permissive default [`ykws.android.maro.spatial.RouteEngine.prepare`] names,
     * and it is stated there rather than here because it is a property of the contract: readiness
     * says the engine can answer, never how much of the world it has read.
     */
    override suspend fun prepare(): RouteEngineState {
        meshOrLoad()
        return _state.value
    }

    override suspend fun route(
        start: RoutePoint,
        aim: RoutePoint,
        cruiseSpeedKn: Double
    ): RouteResult {
        val mesh = meshOrLoad() ?: return RouteResult.OutsideMesh
        prepareQueries?.invoke()
        val engine = searchOver(mesh)
        return withContext(Dispatchers.Default) {
            val context = currentCoroutineContext()
            engine.search(start, aim, cruiseSpeedKn) { context.ensureActive() }
        }
    }

    /**
     * The fabric, decoding it if it is not held yet — and the state the app's gate reads, set from
     * the same answer so the two can never disagree.
     */
    private suspend fun meshOrLoad(): RouteMeshArrays? {
        val mesh = when (source) {
            is MeshSource.Held -> source.mesh
            is MeshSource.Bundle -> repository?.loadIfNeeded()
        }
        _state.value = if (mesh != null) {
            RouteEngineState.Ready
        } else {
            RouteEngineState.Unavailable(RouteUnavailableReason.REGION_NOT_BAKED)
        }
        return mesh
    }

    /** The search over [mesh], built once per mesh instance. */
    private fun searchOver(mesh: RouteMeshArrays): RouteSearch {
        if (mesh !== searchFor) {
            searchFor = mesh
            search = RouteSearch(
                mesh = mesh,
                queries = queries,
                snapRadiusM = snapRadiusM,
                zoneBerthM = zoneBerthM,
                turnLateralAccelMps2 = turnLateralAccelMps2,
                berthMaxPrice = berthMaxPrice,
                shortcutPass = shortcutPass,
                warn = warn
            )
        }
        return search ?: error("the search was built for the mesh in hand")
    }

    companion object {

        /**
         * The app's engine: the bundled mesh of the region, decoded once, priced from the very
         * coastline and regulation instances the app has already loaded.
         */
        fun overBundle(
            application: Application,
            coastline: CoastlineRepository,
            regulatedZones: RegulatedZonesRepository
        ): MeshRouteEngine {
            val adapter = RouteSpatialAdapter(coastline, regulatedZones)
            return MeshRouteEngine(
                queries = adapter,
                prepareQueries = adapter::loadIfNeeded,
                source = MeshSource.Bundle(application)
            )
        }

        /**
         * An engine over a mesh the caller already holds, priced through the queries it gives.
         *
         * It exists for the reading side: the bake-time probes search a mesh they built or
         * deserialized themselves, and this is how they measure the engine the app ships rather than
         * a second implementation of it.
         */
        fun overMesh(mesh: RouteMeshArrays, queries: RoutePointQueries): MeshRouteEngine =
            MeshRouteEngine(
                queries = queries,
                prepareQueries = null,
                source = MeshSource.Held(mesh)
            )
    }
}

/** Where a mesh engine's fabric comes from. */
internal sealed interface MeshSource {
    /** The bundled asset of the region, decoded once and held for the engine's life. */
    class Bundle(val context: Application) : MeshSource

    /** A mesh the caller already holds — the readings' own. */
    class Held(val mesh: RouteMeshArrays) : MeshSource
}
