package ykws.android.maro.spatial.avoid

import kotlinx.coroutines.flow.first
import ykws.android.maro.data.coastline.CoastlineRepository
import ykws.android.maro.data.model.CoastlineState
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.spatial.LandRingOrientation
import ykws.android.maro.spatial.RouteEngineState
import ykws.android.maro.spatial.RouteUnavailableReason

/** One coastline edge with the orientation of the polyline it belongs to. */
data class AvoidEdge(
    val a: LatLng,
    val b: LatLng,
    val orientation: LandRingOrientation
)

/**
 * **The avoid engine's own world interface** — what stage 1 needs, in the engine's own vocabulary,
 * so the feature imports no coastline type beyond this file. An engine that wants the water
 * declares this; the live adapter below is the single importer that translates the coastline
 * repository into it.
 */
interface AvoidWorld {

    /** Whether the coastline is loaded and queryable right now — the readiness gate. */
    val coastlineReady: Boolean

    /** The region this world can answer for, or `null` before it is loaded; the corridor is clamped to it. */
    val regionBounds: BBox?

    /** Every ring/basin land edge whose bounding box overlaps [box], each carrying its ring orientation. */
    fun segmentsIn(box: BBox): List<AvoidEdge>

    /**
     * Every open mainland-coast polyline crossing [box], as ordered vertex lists — the ordered
     * form the rasterizer closes into a land polygon. Rings and basins stay on [segmentsIn].
     */
    fun openCoastIn(box: BBox): List<List<LatLng>>

    /** Water/land, as the index answers it — `true` when no coastline is loaded (the index's own default). */
    fun isWater(latitude: Double, longitude: Double): Boolean

    /** Distance (m) to the nearest coastline — the pull's margin check. */
    fun distanceToCoastM(latitude: Double, longitude: Double): Double

    /**
     * Makes the coastline ready if it can be, and reports what the engine reached. Fired by
     * `prepare()` on a miss: an idle repository is loaded, a loading one is awaited, and the
     * answer is [RouteEngineState.Ready] once the index exists, else
     * [RouteEngineState.Unavailable] with [RouteUnavailableReason.COASTLINE_NOT_LOADED].
     */
    suspend fun load(): RouteEngineState
}

/**
 * The live adapter over [CoastlineRepository] — the one file in the feature that imports the
 * coastline, translating its index into [AvoidEdge]s and its readiness into this world's. It holds
 * no data of its own: every query reads the repository's current index, so a load completed after
 * construction is picked up on the next call.
 */
class CoastlineAvoidWorld(
    private val repository: CoastlineRepository
) : AvoidWorld {

    override val coastlineReady: Boolean
        get() = repository.spatialIndex != null

    override val regionBounds: BBox?
        get() = repository.regionBounds?.let {
            BBox(it.latSouth, it.latNorth, it.lonWest, it.lonEast)
        }

    override fun segmentsIn(box: BBox): List<AvoidEdge> {
        val index = repository.spatialIndex ?: return emptyList()
        return index.segmentsInBbox(box).mapNotNull { seg ->
            val orientation = repository.landRingOrientation(seg.polylineIdx)
            if (orientation == LandRingOrientation.OPEN_COAST) null
            else AvoidEdge(seg.a, seg.b, orientation)
        }
    }

    override fun openCoastIn(box: BBox): List<List<LatLng>> =
        repository.spatialIndex?.openCoastPolylinesIn(box) ?: emptyList()

    override fun isWater(latitude: Double, longitude: Double): Boolean =
        repository.spatialIndex?.isWater(latitude, longitude) ?: true

    override fun distanceToCoastM(latitude: Double, longitude: Double): Double =
        repository.distanceToCoastMeters(latitude, longitude)

    override suspend fun load(): RouteEngineState {
        when (val state = repository.state.value) {
            is CoastlineState.Loading ->
                // A load is already in flight (the map's own cold-start load): wait for it rather
                // than starting a second one.
                repository.state.first { it is CoastlineState.Ready || it is CoastlineState.Error }
            is CoastlineState.Idle, is CoastlineState.Error -> repository.loadCoastline()
            is CoastlineState.Ready -> Unit
        }
        return if (coastlineReady) RouteEngineState.Ready
        else RouteEngineState.Unavailable(RouteUnavailableReason.COASTLINE_NOT_LOADED)
    }
}
