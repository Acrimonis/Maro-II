package ykws.android.maro.spatial.multipass

import ykws.android.maro.data.model.BoundingBox
import ykws.android.maro.data.model.DepthDatum
import ykws.android.maro.data.model.DepthGrid
import ykws.android.maro.data.model.DepthMetadata
import ykws.android.maro.data.model.DepthSample
import ykws.android.maro.data.model.DepthSource
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.spatial.SpatialOperations
import kotlin.math.PI
import kotlin.math.cos

/**
 * **The depth gate's shared fixtures** — a real [DepthGrid] raster, read through its own bilinear
 * `depthAt` exactly as the app reads the baked one, a world over it, and the [GateRule] the engine would
 * build on it. Positions are written in **metres north and east of the raster's south-west corner**, so a
 * test states its geometry in the unit it reasons in.
 */
internal object GateTestFixtures {

    const val LAT0 = 43.5
    const val LON0 = 7.0
    const val MIN_DEPTH_M = 3.0

    val M_PER_DEG_LAT = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
    val M_PER_DEG_LON = M_PER_DEG_LAT * cos(Math.toRadians(LAT0))

    /** The point [northM] north and [eastM] east of the raster's south-west corner. */
    fun at(northM: Double, eastM: Double): LatLng =
        LatLng(LAT0 + northM / M_PER_DEG_LAT, LON0 + eastM / M_PER_DEG_LON)

    /** A box from [southM] to [northM] and [westM] to [eastM], in the same metres. */
    fun box(southM: Double, northM: Double, westM: Double, eastM: Double): BBox =
        BBox(at(southM, 0.0).latitude, at(northM, 0.0).latitude, at(0.0, westM).longitude, at(0.0, eastM).longitude)

    /**
     * A raster of [rows] × [cols] nodes at [cellM], its south-west corner at the origin, node `(r, c)`
     * standing at `((r + 0.5) × cellM, (c + 0.5) × cellM)` and holding [depth] of that position (NaN for
     * NoData).
     */
    fun raster(
        rows: Int,
        cols: Int,
        cellM: Double,
        source: (northM: Double, eastM: Double) -> DepthSource = { _, _ -> DepthSource.LITTO3D },
        depth: (northM: Double, eastM: Double) -> Double
    ): DepthGrid {
        val dLat = cellM / M_PER_DEG_LAT
        val dLon = cellM / M_PER_DEG_LON
        val depths = FloatArray(rows * cols) { i ->
            depth((i / cols + 0.5) * cellM, (i % cols + 0.5) * cellM).toFloat()
        }
        return DepthGrid(
            regionId = "test",
            boundingBox = BoundingBox(LAT0, LAT0 + rows * dLat, LON0, LON0 + cols * dLon),
            rows = rows,
            cols = cols,
            cellSizeDegLat = dLat,
            cellSizeDegLon = dLon,
            datum = DepthDatum.LAT,
            depths = depths,
            source = ByteArray(rows * cols) { i -> source((i / cols + 0.5) * cellM, (i % cols + 0.5) * cellM).id.toByte() },
            confidence = ByteArray(rows * cols) { 90 },
            metadata = DepthMetadata("test", 1L, cellM, rows * cols, 0, 0f, 0f)
        )
    }

    /** The gate's own read of [grid] — its bilinear depth in metres, NaN where it holds nothing. */
    fun depthOf(grid: DepthGrid): (Double, Double) -> Double = { lat, lon ->
        val sample = grid.depthAt(lat, lon)
        if (sample.hasData && !sample.depthM.isNaN()) sample.depthM.toDouble() else Double.NaN
    }

    /** The edge index over [grid], built exactly as the live world builds it — no chart cutoff unless named. */
    fun edgesOf(grid: DepthGrid, cutoffM: Float = 0f): ShallowEdgeIndex = shallowEdgesOver(grid, cutoffM, MIN_DEPTH_M)

    /**
     * The gate rule over [grid]: the wall at [hardM], the preferred standoff and the ends' disc at
     * [preferredM], and the berth's outer edge at [berthOuterM] — `evolutive`'s own shape by default.
     */
    fun gateOver(
        grid: DepthGrid,
        hardM: Double,
        preferredM: Double = maxOf(hardM, 20.0),
        berthOuterM: Double = 50.0
    ): GateRule = GateRule(edgesOf(grid), hardM = hardM, preferredM = preferredM, waiverM = preferredM, berthOuterM = berthOuterM)

    /**
     * A world of open water over [grid]: no coast, every point water, the raster's own depth and edge.
     * [bandWidthM] is the priced band's width — above 0 it arms `evolutive`'s fine water, whose depth collar
     * then follows the measured edge; with no coast the band itself prices nothing.
     */
    class RasterWorld(
        private val grid: DepthGrid,
        override val bandWidthM: Double = 0.0,
        /**
         * The chart's EMODnet cutoff where the world reads depth as the live one does
         * ([DepthGrid.depthAtFilled], excluded and missing nodes at the gate depth); `null` reads the raster
         * plainly, as every fixture before the filled read did.
         */
        private val readCutoffM: Float? = null
    ) : MultipassWorld {
        private val edges = edgesOf(grid, readCutoffM ?: 0f)
        override val coastlineReady: Boolean get() = true
        override val depthReady: Boolean get() = true
        override val regionBounds: BBox? get() = null
        override fun segmentsIn(box: BBox): List<MultipassEdge> = emptyList()
        override fun openCoastIn(box: BBox): List<List<LatLng>> = emptyList()
        override fun isWater(latitude: Double, longitude: Double): Boolean = true
        override fun distanceToCoastM(latitude: Double, longitude: Double): Double = Double.MAX_VALUE
        override fun depthAt(latitude: Double, longitude: Double): DepthSample =
            readCutoffM?.let { grid.depthAtFilled(latitude, longitude, it, MIN_DEPTH_M.toFloat()) }
                ?: grid.depthAt(latitude, longitude)
        override fun shallowEdges(minDepthM: Double): ShallowEdgeIndex = edges
    }

    /** The depth gate's own rastered wall over [world] — the interior the field paints cell by cell. */
    fun gateField(world: MultipassWorld): RouteCostField =
        RouteCostField(listOf(depthGateSource(MIN_DEPTH_M) { p -> depthMAt(world, p) }))
}
