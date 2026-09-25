package ykws.android.maro.spatial.avoid

import kotlinx.coroutines.flow.first
import ykws.android.maro.data.coastline.CoastlineRepository
import ykws.android.maro.data.depth.DepthRepository
import ykws.android.maro.data.model.CoastlineState
import ykws.android.maro.data.model.DepthSample
import ykws.android.maro.data.model.DepthState
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
 * **The avoid engine's own world interface** — what the engine needs, in its own vocabulary, so the
 * feature imports no coastline or depth type beyond this file. An engine that wants the water declares
 * this; the live adapter below is the single importer that translates the two repositories into it.
 */
interface AvoidWorld {

    /** Whether the coastline is loaded and queryable right now — the first half of the readiness gate. */
    val coastlineReady: Boolean

    /**
     * Whether the depth grid is loaded and queryable right now — the second half of the readiness
     * gate. With no grid loaded every cell reads unsurveyed and the 3 m depth gate would be silently
     * inert, so a route armed before the grid lands is refused by name rather than drawn blind.
     */
    val depthReady: Boolean

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
     * The depth at a point, as the depth layer answers it — [DepthSample.NONE] (no data) when the
     * point is unsurveyed or no grid is loaded. The 3 m gate reads this once per cell centre.
     */
    fun depthAt(latitude: Double, longitude: Double): DepthSample

    /**
     * The **priced band's** width (m) off the coast — the zone layer's own value, never one the route
     * declares: the feature keeps no band width of its own and prices whatever the layer calls the
     * band. The band's speed limit, where a later phase prices it too, is the layer's value as well.
     */
    val bandWidthM: Double

    /**
     * Makes **both layers** ready if they can be, and reports what the engine reached. Fired by
     * `prepare()` on a miss: an idle repository is loaded, a loading one is awaited, and the answer is
     * [RouteEngineState.Ready] once the index and the grid both exist, else
     * [RouteEngineState.Unavailable] with [RouteUnavailableReason.COASTLINE_NOT_LOADED] or
     * [RouteUnavailableReason.DEPTH_NOT_LOADED] — the coastline's name winning when neither is in,
     * it being the layer everything else is read against.
     */
    suspend fun load(): RouteEngineState
}

/**
 * The live adapter over [CoastlineRepository] and [DepthRepository] — the one file in the feature that
 * imports either, translating the first's index into [AvoidEdge]s and both repositories' readiness into
 * this world's. It holds no data of its own: every query reads the repositories' current index and
 * grid, so a load completed after construction is picked up on the next call.
 */
class LiveAvoidWorld(
    private val coastline: CoastlineRepository,
    private val depth: DepthRepository
) : AvoidWorld {

    override val coastlineReady: Boolean
        get() = coastline.spatialIndex != null

    override val depthReady: Boolean
        get() = depth.isLoaded()

    override val bandWidthM: Double
        get() = CoastlineRepository.ZONE_DISTANCE_M

    override val regionBounds: BBox?
        get() = coastline.regionBounds?.let {
            BBox(it.latSouth, it.latNorth, it.lonWest, it.lonEast)
        }

    override fun segmentsIn(box: BBox): List<AvoidEdge> {
        val index = coastline.spatialIndex ?: return emptyList()
        return index.segmentsInBbox(box).mapNotNull { seg ->
            val orientation = coastline.landRingOrientation(seg.polylineIdx)
            if (orientation == LandRingOrientation.OPEN_COAST) null
            else AvoidEdge(seg.a, seg.b, orientation)
        }
    }

    override fun openCoastIn(box: BBox): List<List<LatLng>> =
        coastline.spatialIndex?.openCoastPolylinesIn(box) ?: emptyList()

    override fun isWater(latitude: Double, longitude: Double): Boolean =
        coastline.spatialIndex?.isWater(latitude, longitude) ?: true

    override fun distanceToCoastM(latitude: Double, longitude: Double): Double =
        coastline.distanceToCoastMeters(latitude, longitude)

    override fun depthAt(latitude: Double, longitude: Double): DepthSample =
        depth.depthAt(latitude, longitude)

    override suspend fun load(): RouteEngineState {
        loadCoastline()
        loadDepth()
        return when {
            !coastlineReady ->
                RouteEngineState.Unavailable(RouteUnavailableReason.COASTLINE_NOT_LOADED)
            !depthReady ->
                RouteEngineState.Unavailable(RouteUnavailableReason.DEPTH_NOT_LOADED)
            else -> RouteEngineState.Ready
        }
    }

    private suspend fun loadCoastline() {
        when (val state = coastline.state.value) {
            is CoastlineState.Loading ->
                // A load is already in flight (the map's own cold-start load): wait for it rather
                // than starting a second one.
                coastline.state.first { it is CoastlineState.Ready || it is CoastlineState.Error }
            is CoastlineState.Idle, is CoastlineState.Error -> coastline.loadCoastline()
            is CoastlineState.Ready -> Unit
        }
    }

    private suspend fun loadDepth() {
        when (val state = depth.state.value) {
            // The map's own cold-start load: wait for it rather than starting a second one.
            is DepthState.Loading ->
                depth.state.first { it is DepthState.Ready || it is DepthState.Error }
            is DepthState.Idle, is DepthState.Error -> depth.loadDepth()
            is DepthState.Ready -> Unit
        }
    }
}
