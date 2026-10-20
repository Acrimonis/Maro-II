package ykws.android.maro.data.model

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max

/** Vertical datum a depth value is referenced to. LAT = lowest tide = chart datum. */
enum class DepthDatum { LAT, MSL, IGN69, UNKNOWN }

/**
 * Per-cell provenance for a merged depth value.
 *
 * @property nominalResM source grid resolution in metres (smaller = finer; wins deep merges).
 * @property seedConfidence 0..100 starting confidence from the source's nominal accuracy.
 */
enum class DepthSource(val id: Int, val nominalResM: Double, val seedConfidence: Int) {
    NONE(0, Double.MAX_VALUE, 0),
    LITTO3D(1, 1.0, 90),
    SHOM(2, 50.0, 80),
    EMODNET(3, 115.0, 60),
    SDB(4, 10.0, 70),
    GEBCO(5, 450.0, 30),
    INTERPOLATED(6, 0.0, 20);

    companion object {
        fun fromId(i: Int): DepthSource = entries.firstOrNull { it.id == i } ?: NONE

        /**
         * **The one EMODnet exclusion rule** — an EMODnet reading (115 m cells, often a coast or rock
         * artefact) shallower than [cutoffM] is unreliable for navigation and taken for no reading at all.
         * Only EMODnet is excluded; finer sources and deeper EMODnet pass. A reading above the datum
         * (negative) is excluded at any cutoff, 0 included. Read by the chart's gates and the router's read
         * alike; the chart's per-cell colour gate ([DepthGrid.depthGated]) alone keeps negatives and cutoff 0
         * open, so it can colour drying ground.
         */
        fun isExcludedEmodnet(depthM: Float, sourceId: Int, cutoffM: Float): Boolean =
            sourceId == EMODNET.id && depthM < cutoffM
    }
}

/**
 * Result of a point depth query. [depthM] is metres below datum (positive-down);
 * [hasData] is false (and depth NaN) when the point falls on a NoData gap.
 */
data class DepthSample(
    val depthM: Float,
    val source: DepthSource,
    val confidence: Int,
    val hasData: Boolean
) {
    /**
     * Gate coarse EMODnet point readings in the shallow danger band: an EMODnet cell
     * (115 m, often a coast/rock artifact) reading shallower than [cutoffM] is unreliable
     * for navigation → presented as no-data ([NONE]). Only EMODnet is gated; finer sources
     * (Litto3D/SDB) and deeper EMODnet pass through. [cutoffM] = 0 disables the gate.
     */
    fun gatedForEmodnetShallow(cutoffM: Float): DepthSample =
        if (hasData && DepthSource.isExcludedEmodnet(depthM, source.id, cutoffM)) NONE else this

    companion object {
        val NONE = DepthSample(Float.NaN, DepthSource.NONE, 0, false)
    }
}

/** Provenance + summary stats for a [DepthGrid]. */
data class DepthMetadata(
    val source: String,
    val fetchTimestampMs: Long,
    val gridResM: Double,
    val cellCount: Int,
    val noDataCount: Int,
    val minDepthM: Float,
    val maxDepthM: Float,
    val validation: ValidationReport? = null
)

/**
 * Immutable sampled bathymetric raster — the single source of truth for the depth
 * feature. [depths] are metres below [datum] (positive-down); `Float.NaN` = NoData.
 *
 * Row 0 = south, col 0 = west, row-major (matches `CoastlineSpatialIndex`). Both
 * isobaths (marching squares) and the colour map are *derived* from this grid.
 */
class DepthGrid(
    val regionId: String,
    val boundingBox: BoundingBox,
    val rows: Int,
    val cols: Int,
    val cellSizeDegLat: Double,
    val cellSizeDegLon: Double,
    val datum: DepthDatum,
    val depths: FloatArray,      // size rows*cols
    val source: ByteArray,       // size rows*cols, DepthSource.id
    val confidence: ByteArray,   // size rows*cols, 0..100
    val metadata: DepthMetadata
) {
    fun idx(r: Int, c: Int): Int = r * cols + c
    fun depthRaw(r: Int, c: Int): Float = depths[idx(r, c)]
    fun hasData(r: Int, c: Int): Boolean = !depths[idx(r, c)].isNaN()
    fun sourceAt(r: Int, c: Int): DepthSource = DepthSource.fromId(source[idx(r, c)].toInt())
    fun confidenceAt(r: Int, c: Int): Int = confidence[idx(r, c)].toInt() and 0xFF

    /**
     * Gate coarse EMODnet cell readings in the shallow danger band: an EMODnet cell
     * (115 m, often a coast/rock artifact) shallower than [cutoffM] returns [Float.NaN]
     * (NoData) so downstream builders (bitmap, warning, isobaths) skip it.
     * Only EMODnet is gated; finer sources (Litto3D/SDB) and deeper EMODnet pass through.
     * [cutoffM] = 0 disables the gate. NaN or above-datum cells pass through unchanged — the chart's own
     * exception to [DepthSource.isExcludedEmodnet], so it can colour drying ground.
     */
    fun depthGated(r: Int, c: Int, cutoffM: Float): Float {
        val d = depthRaw(r, c)
        if (d.isNaN() || d < 0f) return d
        if (cutoffM > 0f && DepthSource.isExcludedEmodnet(d, source[idx(r, c)].toInt(), cutoffM)) return Float.NaN
        return d
    }

    /**
     * **The depth at a point as the route search reads it** — bilinear like [depthAt], but a node that is
     * excluded EMODnet ([DepthSource.isExcludedEmodnet] under [excludeBelowM]) or holds no data takes part in
     * the blend **at [fillM]** rather than with its own value or not at all. With [fillM] at the gate depth,
     * such a node can neither drag its neighbours under the gate nor wall anything itself, while a real
     * sounding beside it still reads as it is: the blend `fill + Σw·(v − fill) / Σw` lies under the gate
     * exactly where the real nodes' own blend does, and an all-filled quad reads [fillM] exactly.
     *
     * A sample whose four nodes all hold no data (land, beyond the raster) is still no data. A sample
     * holding no real sounding at all reads [fillM] with no source ([DepthSource.NONE]), so a reader can
     * tell an assumed depth from a measured one; otherwise the source is the heaviest real node's.
     */
    fun depthAtFilled(lat: Double, lon: Double, excludeBelowM: Float, fillM: Float): DepthSample {
        var wSum = 0.0
        var deltaSum = 0.0
        var anyData = false
        var bestW = -1.0
        var bestR = -1
        var bestC = -1
        forEachNode(lat, lon) { r, c, w ->
            val v = depthRaw(r, c)
            if (!v.isNaN()) anyData = true
            wSum += w
            // A node is real unless it holds no data or is excluded EMODnet — the source byte read only
            // where the depth is under the cutoff, so the common node pays nothing for it.
            val real = !v.isNaN() && !(v < excludeBelowM && DepthSource.isExcludedEmodnet(v, source[idx(r, c)].toInt(), excludeBelowM))
            if (real) {
                deltaSum += w * (v - fillM)
                if (w > bestW) { bestW = w; bestR = r; bestC = c }
            }
        }
        if (wSum <= 0.0 || !anyData) return DepthSample.NONE
        val depth = (fillM + deltaSum / wSum).toFloat()
        return if (bestR < 0) {
            DepthSample(depthM = fillM, source = DepthSource.NONE, confidence = 0, hasData = true)
        } else {
            DepthSample(depthM = depth, source = sourceAt(bestR, bestC), confidence = confidenceAt(bestR, bestC), hasData = true)
        }
    }

    /**
     * **The four raster nodes around a point**, each handed to [visit] with its bilinear weight — the one
     * geometry both reads ([depthAt], [depthAtFilled]) blend over, so they can never disagree about which
     * nodes a point reads or how much each weighs. A node outside the grid or of zero weight is skipped.
     */
    private inline fun forEachNode(lat: Double, lon: Double, visit: (r: Int, c: Int, w: Double) -> Unit) {
        if (rows == 0 || cols == 0) return
        val fr = (lat - boundingBox.latSouth) / cellSizeDegLat - 0.5
        val fc = (lon - boundingBox.lonWest) / cellSizeDegLon - 0.5
        val r0 = floor(fr).toInt()
        val c0 = floor(fc).toInt()
        val tr = fr - r0
        val tc = fc - c0
        for (dr in 0..1) for (dc in 0..1) {
            val r = r0 + dr
            val c = c0 + dc
            if (r < 0 || r >= rows || c < 0 || c >= cols) continue
            val w = (if (dr == 0) 1.0 - tr else tr) * (if (dc == 0) 1.0 - tc else tc)
            if (w > 0.0) visit(r, c, w)
        }
    }

    fun cellCenterLat(r: Int): Double = boundingBox.latSouth + (r + 0.5) * cellSizeDegLat
    fun cellCenterLon(c: Int): Double = boundingBox.lonWest + (c + 0.5) * cellSizeDegLon

    /**
     * Bilinear depth at a geographic point, skipping NaN neighbours and renormalising
     * over the available ones. Returns [DepthSample.NONE] when all four neighbours are
     * NoData or the point is outside the grid. Source/confidence come from the
     * highest-weight valid neighbour.
     */
    fun depthAt(lat: Double, lon: Double): DepthSample {
        var wSum = 0.0
        var vSum = 0.0
        var bestW = -1.0
        var bestR = -1
        var bestC = -1
        forEachNode(lat, lon) { r, c, w ->
            val v = depthRaw(r, c)
            if (!v.isNaN()) {
                wSum += w
                vSum += w * v
                if (w > bestW) { bestW = w; bestR = r; bestC = c }
            }
        }
        if (wSum <= 0.0 || bestR < 0) return DepthSample.NONE
        return DepthSample(
            depthM = (vSum / wSum).toFloat(),
            source = sourceAt(bestR, bestC),
            confidence = confidenceAt(bestR, bestC),
            hasData = true
        )
    }
}

/**
 * Mutable accumulator used while fetching + merging sources. Built empty over a bbox,
 * written cell-by-cell by the merge functions, then frozen via [toImmutable].
 */
class MutableDepthGrid private constructor(
    val regionId: String,
    val boundingBox: BoundingBox,
    val rows: Int,
    val cols: Int,
    val cellSizeDegLat: Double,
    val cellSizeDegLon: Double,
    val datum: DepthDatum,
    val depths: FloatArray,
    val source: ByteArray,
    val confidence: ByteArray,
    val gridResM: Double
) {
    fun idx(r: Int, c: Int): Int = r * cols + c
    fun get(r: Int, c: Int): Float = depths[idx(r, c)]
    fun sourceAt(r: Int, c: Int): DepthSource = DepthSource.fromId(source[idx(r, c)].toInt())
    fun cellCenterLat(r: Int): Double = boundingBox.latSouth + (r + 0.5) * cellSizeDegLat
    fun cellCenterLon(c: Int): Double = boundingBox.lonWest + (c + 0.5) * cellSizeDegLon

    fun set(r: Int, c: Int, depthM: Float, src: DepthSource, conf: Int) {
        val i = idx(r, c)
        depths[i] = depthM
        source[i] = src.id.toByte()
        confidence[i] = conf.coerceIn(0, 100).toByte()
    }

    fun noDataCount(): Int = depths.count { it.isNaN() }

    fun toImmutable(
        validation: ValidationReport?,
        fetchTimestampMs: Long,
        sourceLabel: String
    ): DepthGrid {
        var min = Float.MAX_VALUE
        var maxV = -Float.MAX_VALUE
        var noData = 0
        for (v in depths) {
            if (v.isNaN()) noData++ else { if (v < min) min = v; if (v > maxV) maxV = v }
        }
        if (noData == depths.size) { min = Float.NaN; maxV = Float.NaN }
        return DepthGrid(
            regionId = regionId,
            boundingBox = boundingBox,
            rows = rows,
            cols = cols,
            cellSizeDegLat = cellSizeDegLat,
            cellSizeDegLon = cellSizeDegLon,
            datum = datum,
            depths = depths,
            source = source,
            confidence = confidence,
            metadata = DepthMetadata(
                source = sourceLabel,
                fetchTimestampMs = fetchTimestampMs,
                gridResM = gridResM,
                cellCount = depths.size,
                noDataCount = noData,
                minDepthM = min,
                maxDepthM = maxV,
                validation = validation
            )
        )
    }

    companion object {
        private const val EARTH_RADIUS_M = 6_371_000.0

        /** Empty grid (all NaN) covering [bbox] at ~[gridResM] metre cells. */
        fun empty(
            regionId: String,
            bbox: BoundingBox,
            gridResM: Double,
            datum: DepthDatum = DepthDatum.LAT
        ): MutableDepthGrid {
            val mPerDegLat = EARTH_RADIUS_M * PI / 180.0
            val refLat = bbox.centerLat
            val cellLat = gridResM / mPerDegLat
            val cellLon = gridResM / (mPerDegLat * cos(Math.toRadians(refLat)))
            val rows = max(1, ceil(bbox.heightDeg / cellLat).toInt())
            val cols = max(1, ceil(bbox.widthDeg / cellLon).toInt())
            val n = rows * cols
            // ceil() rounds coverage up, so the grid spans slightly more than the requested
            // bbox. Store the ACTUAL covered extent so boundingBox stays consistent with the
            // cell geometry (used for sampling, GroundOverlay placement, isobath mapping).
            val actualBbox = BoundingBox(
                latSouth = bbox.latSouth,
                latNorth = bbox.latSouth + rows * cellLat,
                lonWest = bbox.lonWest,
                lonEast = bbox.lonWest + cols * cellLon
            )
            return MutableDepthGrid(
                regionId = regionId,
                boundingBox = actualBbox,
                rows = rows,
                cols = cols,
                cellSizeDegLat = cellLat,
                cellSizeDegLon = cellLon,
                datum = datum,
                depths = FloatArray(n) { Float.NaN },
                source = ByteArray(n),
                confidence = ByteArray(n),
                gridResM = gridResM
            )
        }
    }
}
