package ykws.android.maro.data.route

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.abs
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.coastline.CoastlineRepository
import ykws.android.maro.data.depth.DepthIsobaths
import ykws.android.maro.data.depth.DepthRepository
import ykws.android.maro.data.model.BoundingBox
import ykws.android.maro.data.model.CoastlineSegment
import ykws.android.maro.data.model.CoastlineState
import ykws.android.maro.data.model.DepthGrid
import ykws.android.maro.data.model.DepthSample
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.data.regulation.RegulatedZonesRepository
import ykws.android.maro.data.regulation.SpeedZone
import ykws.android.maro.data.regulation.SpeedZoneBuilder
import ykws.android.maro.spatial.CoastlineSpatialIndex
import ykws.android.maro.spatial.PricedZoneRef
import ykws.android.maro.spatial.RoutePointQueries
import ykws.android.maro.spatial.SpeedZoneIndex
import ykws.android.maro.spatial.taut.TautObstacles
import ykws.android.maro.spatial.taut.TautTerrainGeneration
import ykws.android.maro.spatial.taut.TautWorld
import ykws.android.maro.spatial.taut.TautWorldReadiness
import ykws.android.maro.spatial.taut.TautZoneShape

/**
 * The route's read-only view of the live spatial layers, and **the only file in the feature that
 * imports them**: `CoastlineSpatialIndex`, `SpeedZoneIndex` and `RegulatedZonesRepository` appear
 * here and nowhere else under `route`/`ui.map` route code, which is what keeps the search and the
 * domain types free of any dependency on the coastline or the regulation model.
 *
 * It is a delegator with no logic of its own beyond one line of mapping: it holds the two indices
 * the app already builds, answers the search's three questions from them, and reads the band's
 * limit from the one home that owns it ([AppConfig.zoneRegulatorySpeedKn]) rather than declaring a
 * value of its own.
 *
 * **It answers the corridor tracer's questions too** ([TautWorld]): the rings inside a box, the 2 m
 * contour the depth feature already knows how to trace, and the sounding at a point. Those are the
 * same world seen from a corridor instead of from a point, so they belong on this one seam rather
 * than on a second importer — and the contour is the depth layer's own marching squares at the
 * depth layer's own fine-level rule, so the tracer inherits the depth feature's distrust of a coarse
 * cell by construction rather than by copying it.
 *
 * **What it reads, and when it fetches.** [loadIfNeeded] reads what the coastline, regulation and
 * depth repositories hold, and on a miss it asks each layer for its **own** load rather than waiting
 * for another screen: the depth grid through its bundled asset, and the coastline through
 * [`CoastlineRepository.loadCoastline`] — which runs the **OSM generation pipeline** when neither the
 * cache nor the bundled asset answers. So a first route on a fresh install can fetch, and saying
 * otherwise would be a promise this file cannot keep. The regulation is never asked for: a zone layer
 * that has not landed simply answers "no limit", the permissive default the seam's contract states.
 */
class RouteSpatialAdapter(
    private val coastline: CoastlineRepository,
    private val regulatedZones: RegulatedZonesRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val depth: DepthRepository? = null
) : TautWorld {

    @Volatile
    private var coast: CoastlineSpatialIndex? = null

    /** The coastline polylines the index was built over, so a polyline index resolves to its points. */
    @Volatile
    private var coastSegments: List<CoastlineSegment> = emptyList()

    @Volatile
    private var speedZones: SpeedZoneIndex? = null

    /** The zones the index was built from — the corridor tracer needs the rings, not only the queries. */
    @Volatile
    private var speedZoneList: List<SpeedZone> = emptyList()

    /** The 2 m contour, derived once per depth grid: marching squares over the whole field is not cheap. */
    private var contourGrid: DepthGrid? = null
    private var contours: List<List<LatLng>> = emptyList()

    /**
     * **The corridor terrain's own invalidator** — [TautWorld.generation], counted over the three layers'
     * identities as each of them lands **or is replaced** (§19.4, and §19.5 C4 for the grid).
     *
     * The rule and its argument live with [TautTerrainGeneration], which is also where the identity test
     * is readable; this is the one place the three layers are handed to it — the coastline index, the zone
     * index and the depth grid, read **after** the load attempt so a layer that has just arrived counts on
     * the same call.
     *
     * The contour is deliberately **not** a fourth layer: it is derived from the grid and cached against
     * the grid's own identity, so a grid that moves brings its contour with it. That is now the *second*
     * thing that follows from a grid's replacement rather than the only one: the grid's own identity is
     * observed below, so a depth refresh invalidates a kept terrain exactly as a first load does.
     */
    private val layerGeneration = TautTerrainGeneration()

    /**
     * Serialises the build, so each index is built **single-flight**: the initial preparation and the
     * readiness retry can overlap, and the search asks on every route, so without this two callers
     * could build the same index at once.
     */
    private val loadMutex = Mutex()

    /**
     * Builds the two indices from the repositories' current state, and reads the depth grid.
     *
     * Idempotent and cheap after the first call: a layer that is still loading is simply picked up
     * on a later call, which is why this is safe to call on every search rather than needing a
     * lifecycle hook of its own. A concurrent second call waits for the first and then finds each
     * index already built — or still absent, because its layer has not landed, which it re-checks
     * for the price of reading two state values.
     *
     * **The depth grid is asked for rather than waited on.** The depth layer reads its bundled asset
     * when its own screen wants it and not before, and a corridor tracer cannot answer honestly
     * without it, so its readiness is what triggers the read — the same asset, through the app's own
     * reader, and only from here.
     */
    suspend fun loadIfNeeded() {
        // **The coastline's own load, waited out rather than skipped.** It reads its bundled asset or
        // its cache when a screen wants it and not before, and the tracer's wall *is* land — so its
        // readiness cannot be left to somebody else's screen any more than the depth grid's can. A load
        // already **in flight is therefore awaited**, and the state re-read afterwards: skipping it left
        // the wall unbuilt for the whole session and `readiness` latched at `COASTLINE_MISSING`, which is
        // the cold-start order the walk names (§17 item 3) — the map's own coastline load is usually the
        // one running when a route is first armed. One already done is not repeated.
        if (coast == null && coastline.state.value is CoastlineState.Loading) {
            coastline.state.first { it !is CoastlineState.Loading }
        }
        if (coast == null && coastline.state.value.let { it !is CoastlineState.Ready }) {
            withContext(dispatcher) { coastline.loadCoastline() }
        }
        if (coast == null || speedZones == null) {
            loadMutex.withLock {
                withContext(dispatcher) {
                    if (coast == null) {
                        val segments =
                            (coastline.state.value as? CoastlineState.Ready)?.data?.allSegments
                        if (!segments.isNullOrEmpty()) {
                            coastSegments = segments
                            coast = CoastlineSpatialIndex(segments)
                        }
                    }
                    if (speedZones == null) {
                        val zoneSet = regulatedZones.zoneSet.value
                        if (zoneSet != null) {
                            val zones = SpeedZoneBuilder.build(zoneSet)
                            speedZoneList = zones
                            speedZones = SpeedZoneIndex(zones)
                        }
                    }
                }
            }
        }
        // The grid is asked for while it is absent — and **its replacement is a move like its first
        // arrival** (§19.5 C4): the grid's object is what the soundings, the depth box and the contour are
        // all read from, so a refresh that installs a new one (`DepthRepository.refreshDepth`) would leave
        // a kept terrain answering about soundings that are no longer behind it. The contour's own
        // identity cache hides that for the contour alone, which is precisely the disagreement the review
        // found; with the grid observed below, both invalidators move on the same event.
        depth?.let { repository ->
            if (repository.getGrid() == null) {
                withContext(dispatcher) { repository.loadGridFromAssets() }
            }
        }
        // **The layers as they are now**, which is the whole of the invalidator: a layer that has just
        // arrived and a layer that has just been replaced are the same event to a kept terrain, and a
        // repeat of the same objects is not an event at all.
        layerGeneration.observe(coast, speedZones, depth?.getGrid())
    }

    /**
     * **The world's readiness, read off the two layers the wall is built from.**
     *
     * Both the coastline and the depth grid must be in: a missing coastline reads as water, so land is
     * not a wall and the shore offset cannot apply, and a missing grid reads as no sounding, so the 2 m
     * gate has nothing to stand on. Either way a search would answer a line over water the boat cannot
     * use, which is why the engine refuses rather than pricing it. The regulation is deliberately absent:
     * an absent zone layer prices open water, the permissive default the seam's contract states for it.
     */
    override val readiness: TautWorldReadiness
        get() = when {
            coast == null -> TautWorldReadiness.COASTLINE_MISSING
            depth?.getGrid() == null -> TautWorldReadiness.DEPTH_MISSING
            else -> TautWorldReadiness.READY
        }

    /**
     * **The world's own generation** — see [TautWorld.generation], and [layerGeneration] for where the
     * three layers' arrivals and replacements are counted.
     */
    override val generation: Int get() = layerGeneration.value

    /** No coastline loaded yet reads as water, which is the permissive default for a mesh query. */
    override fun isWater(latitude: Double, longitude: Double): Boolean =
        coast?.isWater(latitude, longitude) ?: true

    /**
     * The most restrictive priced zone covering the point, or null — see [RoutePointQueries].
     *
     * The query is the zone index's own `query`, whose inside-zones list is already sorted by limit
     * ascending, so the first entry is the most restrictive one and nothing has to be re-sorted here.
     * No zone layer loaded yet answers null, which is the permissive default for the search: a route
     * planned before the regulation has loaded is priced as open water rather than refused.
     */
    override fun pricedZoneAt(latitude: Double, longitude: Double): PricedZoneRef? {
        val index = speedZones ?: return null
        val zone = index.query(latitude, longitude).allInsideZones.firstOrNull() ?: return null
        return PricedZoneRef(zone.id, zone.name, zone.speedLimitKn)
    }

    /**
     * The zone layer's own nearest-boundary reading, unsigned: the index answers with a negative
     * distance inside a zone, and the berth costs the same either side of the edge.
     */
    override fun distanceToZoneM(latitude: Double, longitude: Double): Double {
        val index = speedZones ?: return Double.POSITIVE_INFINITY
        val signed = index.query(latitude, longitude).distanceToBoundaryM
            ?: return Double.POSITIVE_INFINITY
        return abs(signed)
    }

    /**
     * The band's own limit, read from the single setting that owns it — the same value the dashboard
     * and the band's auto-reveal already use, so a corrected regulation moves the route's pricing too.
     */
    override val coastalBandSpeedLimitKn: Double get() = AppConfig.zoneRegulatorySpeedKn.toDouble()

    // ── The corridor tracer's own questions ───────────────────────────────────

    /**
     * The coastline polylines that meet [box], each whole — islands closed, the open mainland not.
     *
     * A whole polyline rather than the segments inside the box: the abstraction and the dilation are
     * properties of the shape, and a ring cut in half would be dilated as an open line. The box query
     * is the index's own cell walk, so a 5 km corridor does not pay for the whole region.
     */
    override fun landPolylinesIn(box: BoundingBox): List<List<LatLng>> {
        val index = coast ?: return emptyList()
        if (segmentCount(index) == 0) return emptyList()
        val bbox = BBox(box.latSouth, box.latNorth, box.lonWest, box.lonEast)
        val wanted = LinkedHashSet<Int>()
        for (segment in index.segmentsInBbox(bbox)) wanted.add(segment.polylineIdx)
        val segments = coastSegments
        val out = ArrayList<List<LatLng>>(wanted.size)
        for (polylineIdx in wanted) {
            val polyline = segments.getOrNull(polylineIdx) ?: continue
            val points = polyline.points.map { LatLng(it.lat.toDouble(), it.lon.toDouble()) }
            if (points.size >= 2) out.add(points)
        }
        return out
    }

    /** The priced zones whose own box meets [box] — ring, holes, name and limit. */
    override fun zoneShapesIn(box: BoundingBox): List<TautZoneShape> =
        speedZoneList.filter { zone -> overlaps(zone, box) }.map { zone ->
            TautZoneShape(
                id = zone.id,
                name = zone.name,
                limitKn = zone.speedLimitKn,
                outerRing = zone.outerRing,
                holes = zone.holes
            )
        }

    /**
     * The **2 m contour** inside [box], traced by the depth feature's own marching squares at its own
     * fine-level rule — a level that shallow is drawn only where the source resolution is fine, so no
     * contour is faked from a 115 m EMODnet cell. Where it cannot be drawn, the shore offset is the
     * wall instead, and this list is empty there.
     *
     * It is derived once per depth grid and held: the derivation walks every cell of the field, and
     * every search of a session would otherwise pay for it again.
     */
    override fun shallowContoursIn(box: BoundingBox): List<List<LatLng>> {
        val grid = depth?.getGrid() ?: return emptyList()
        val levels = listOf(TautObstacles.SHALLOW_GATE_M.toFloat())
        if (contourGrid !== grid) {
            contours = DepthIsobaths.build(grid, levels = levels)
                .flatMap { isobath -> isobath.lines.map { it.points } }
            contourGrid = grid
        }
        return contours.filter { line -> line.any { inside(it, box) } }
    }

    override fun depthSampleAt(latitude: Double, longitude: Double): DepthSample =
        depth?.getGrid()?.depthAt(latitude, longitude) ?: DepthSample.NONE

    /** The coastline's own distance reading; nothing loaded is `+∞`, the permissive default. */
    override fun distanceToCoastM(latitude: Double, longitude: Double): Double =
        coast?.query(latitude, longitude)?.distanceMeters ?: Double.POSITIVE_INFINITY

    override val depthBox: BoundingBox?
        get() = depth?.getGrid()?.boundingBox

    /**
     * The 300 m band's own width, read from the coastline feature that owns it, beside the band's limit
     * which this adapter already answers from `AppConfig`.
     */
    override val coastalBandWidthM: Double get() = CoastlineRepository.ZONE_DISTANCE_M

    private fun segmentCount(index: CoastlineSpatialIndex): Int = if (index.hasData) 1 else 0

    private fun overlaps(zone: SpeedZone, box: BoundingBox): Boolean {
        for (point in zone.outerRing) {
            if (point.latitude >= box.latSouth && point.latitude <= box.latNorth &&
                point.longitude >= box.lonWest && point.longitude <= box.lonEast
            ) {
                return true
            }
        }
        return false
    }

    private fun inside(point: LatLng, box: BoundingBox): Boolean =
        point.latitude >= box.latSouth && point.latitude <= box.latNorth &&
            point.longitude >= box.lonWest && point.longitude <= box.lonEast
}
