package ykws.android.maro.spatial.multipass

import ykws.android.maro.data.coastline.CoastlineRepository
import ykws.android.maro.data.depth.DepthRepository
import ykws.android.maro.data.model.DepthSample
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.data.regulation.SpeedZone
import ykws.android.maro.spatial.LandRingOrientation

/** One coastline edge with the orientation of the polyline it belongs to. */
data class MultipassEdge(
    val a: LatLng,
    val b: LatLng,
    val orientation: LandRingOrientation
)

/**
 * **The avoid engine's own world interface** — what the engine needs, in its own vocabulary, so the
 * feature imports no coastline or depth type beyond this file. An engine that wants the water declares
 * this; the live adapter below is the single importer that translates the two repositories into it.
 */
interface MultipassWorld {

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

    /**
     * The coastline's **generation stamp** — a reload changes it, so a mask cache keyed on it
     * invalidates when the coast moves. 0 where the world names none.
     */
    val coastlineGenerationStamp: Long get() = 0L

    /**
     * The depth grid's **generation stamp** (`fetchTimestampMs`), the value the raster cache already
     * keys on — a reload changes it. 0 where no grid is loaded or the world names none.
     */
    val depthGenerationStamp: Long get() = 0L

    /** The chart's EMODnet shallow cutoff (m) — the same setting [depthAt] is gated by. 0 disables it. */
    val emodnetCutoffM: Float get() = 0f

    /** Every ring/basin land edge whose bounding box overlaps [box], each carrying its ring orientation. */
    fun segmentsIn(box: BBox): List<MultipassEdge>

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
     * The depth at a point **as the router reads the water** — [DepthSample.NONE] (no data) when the
     * point is unsurveyed or no grid is loaded, and, on a coarse EMODnet cell reading shallower than
     * the chart's own `emodnetShallowCutoffM`, [DepthSample.NONE] too: the same gate the chart draws
     * with ([DepthSample.gatedForEmodnetShallow]), at the same setting's value, so a cell the chart
     * calls no-data never walls the route. The 3 m gate reads this once per cell centre.
     */
    fun depthAt(latitude: Double, longitude: Double): DepthSample

    /**
     * The **priced band's** width (m) off the coast — the zone layer's own value, never one the route
     * declares: the feature keeps no band width of its own and prices whatever the layer calls the
     * band. The band's speed limit, where a later phase prices it too, is the layer's value as well.
     */
    val bandWidthM: Double

    /**
     * Every speed zone whose bounding box overlaps [box], with the excluded ids already dropped. The
     * rasterizer even-odd-fills these polygons once per zone — never a per-cell index query, which is
     * the explosion the stage-1 budget forbids.
     */
    fun speedZonesIn(box: BBox): List<SpeedZone> = emptyList()

    /**
     * The strictest limit (lowest kn) in force at a point across the non-excluded zones containing it,
     * or `null` outside all of them — the ETA's point read, taken along the emitted line.
     */
    fun zoneLimitKnAt(latitude: Double, longitude: Double): Double? = null
}

/**
 * The live adapter over [CoastlineRepository], [DepthRepository] and the speed-zone list — the one file
 * in the feature that imports them, translating the first's index into [MultipassEdge]s and the three
 * layers' readiness into this world's. It holds no data of its own: every query reads the layers'
 * current state, so a load completed after construction is picked up on the next call.
 *
 * **The mutation window, stated once and only here.** The depth grid and the coastline index move at a
 * **reload** alone, and a reload lands **between solves**, never inside one: the grid is swapped whole
 * ([DepthRepository]'s single `var`) and the index rebuilt whole ([CoastlineRepository.spatialIndex]),
 * each read fresh per call but stable for the life of a solve. A consumer that caches a read for a walk
 * — [MarkMemo] — therefore needs no invalidation within a solve; the two stamps ([depthGenerationStamp],
 * [coastlineGenerationStamp]) are what tell two solves apart. A reload that ever landed mid-solve would
 * be this contract broken, and is not silently tolerated.
 */
class LiveMultipassWorld(
    private val coastline: CoastlineRepository,
    private val depth: DepthRepository,
    private val zonesProvider: () -> List<SpeedZone> = { emptyList() },
    private val excludedZoneIds: () -> Set<String> = { emptySet() },
    /**
     * The chart's own `emodnetShallowCutoffM`, read fresh on every call so a slider move reaches the
     * next search — the same setting the bitmap, the warning layer, the isobaths and the chart
     * readout are built with. 0 disables the gate.
     */
    private val emodnetShallowCutoffM: () -> Float = { 0f }
) : MultipassWorld {

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

    /** The index's own identity, so a rebuilt coastline changes the stamp and a mask cache invalidates. */
    override val coastlineGenerationStamp: Long
        get() = coastline.spatialIndex?.hashCode()?.toLong() ?: 0L

    /** The loaded grid's `fetchTimestampMs`, the value the raster cache already keys on. */
    override val depthGenerationStamp: Long
        get() = depth.getGrid()?.metadata?.fetchTimestampMs ?: 0L

    /** The chart's own cutoff, read fresh so a slider move reaches the next key. */
    override val emodnetCutoffM: Float
        get() = emodnetShallowCutoffM()

    override fun segmentsIn(box: BBox): List<MultipassEdge> {
        val index = coastline.spatialIndex ?: return emptyList()
        return index.segmentsInBbox(box).mapNotNull { seg ->
            val orientation = coastline.landRingOrientation(seg.polylineIdx)
            if (orientation == LandRingOrientation.OPEN_COAST) null
            else MultipassEdge(seg.a, seg.b, orientation)
        }
    }

    override fun openCoastIn(box: BBox): List<List<LatLng>> =
        coastline.spatialIndex?.openCoastPolylinesIn(box) ?: emptyList()

    override fun isWater(latitude: Double, longitude: Double): Boolean =
        coastline.spatialIndex?.isWater(latitude, longitude) ?: true

    override fun distanceToCoastM(latitude: Double, longitude: Double): Double =
        coastline.distanceToCoastMeters(latitude, longitude)

    /**
     * The live depth read, passed through the same EMODnet shallow gate the chart applies
     * ([DepthSample.gatedForEmodnetShallow]) at the setting's own value: a coarse EMODnet cell the
     * chart calls no-data reads as no-data here, so the engine's shallow wall stands only where the
     * water is trustworthy, and a finer source (Litto3D/SDB) shallower than the gate still walls.
     */
    override fun depthAt(latitude: Double, longitude: Double): DepthSample =
        depth.depthAt(latitude, longitude).gatedForEmodnetShallow(emodnetShallowCutoffM())

    override fun speedZonesIn(box: BBox): List<SpeedZone> =
        speedZonesInBox(zonesProvider(), box, excludedZoneIds())

    override fun zoneLimitKnAt(latitude: Double, longitude: Double): Double? =
        strictestLimitKnAt(zonesProvider(), excludedZoneIds(), latitude, longitude)
}
