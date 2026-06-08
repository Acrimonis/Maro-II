package ykws.android.maro.data.depth

import ykws.android.maro.data.model.BoundingBox
import ykws.android.maro.data.model.CoastlineSegment
import ykws.android.maro.data.model.DepthSource
import ykws.android.maro.data.model.MutableDepthGrid
import ykws.android.maro.spatial.CoastlineSpatialIndex
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.IntStream
import kotlin.math.PI
import kotlin.math.cos

/**
 * Clips a baked depth grid to the **navigable zone**: every populated cell whose centre lies
 * farther than [maxDistM] from the coastline is reset to NoData (`NaN` / [DepthSource.NONE]).
 * With the default 6 NM this is exactly the licence navigation limit, so depth is drawn only
 * where the user may actually go.
 *
 * **Why a mask, not a re-shaped grid:** the runtime keeps its axis-aligned grid + `GroundOverlay`
 * untouched — masked cells render fully transparent through the existing ramp, so the
 * coast-following 6 NM buffer simply "falls out" of the rectangular envelope with zero rendering
 * changes. Distance is measured from the **coastline itself** (not a straight baseline), so bays
 * AND capes both get a uniform 6 NM — no cape under-coverage.
 *
 * Pure JVM, build-time only. Parallel over rows; cells map to disjoint array indices so the
 * concurrent writes into [MutableDepthGrid] are race-free, and [CoastlineSpatialIndex.query] is
 * read-only after construction (same pattern as [ykws.android.maro.spatial.Zone300Builder]).
 */
object DepthZoneMask {

    /** 6 NM in metres — the navigable-zone radius around the coast. 1 NM = 1852 m. */
    const val SIX_NM_M: Double = 6.0 * 1852.0

    /**
     * The axis-aligned grid envelope for the zone: [coastBbox] dilated by [marginM] (default 6 NM) on
     * every side, so the full coast-to-6 NM buffer fits inside the rectangle that [apply] then trims.
     * Replaces the old hardcoded depth box — the only extent is now the real coast + 6 NM.
     */
    fun envelopeOf(coastBbox: BoundingBox, marginM: Double = SIX_NM_M): BoundingBox {
        val mPerDegLat = 6_371_000.0 * PI / 180.0
        val dLat = marginM / mPerDegLat
        val dLon = marginM / (mPerDegLat * cos(Math.toRadians(coastBbox.centerLat)))
        return BoundingBox(
            latSouth = coastBbox.latSouth - dLat,
            latNorth = coastBbox.latNorth + dLat,
            lonWest = coastBbox.lonWest - dLon,
            lonEast = coastBbox.lonEast + dLon
        )
    }

    /**
     * Erases (→ NoData) every non-NaN cell of [grid] farther than [maxDistM] from the nearest of
     * [segments] (typically `CoastlineData.allSegments`). Returns the number of cells erased.
     * No-op (returns 0) when [segments] is empty or carries no usable geometry.
     */
    fun apply(
        grid: MutableDepthGrid,
        segments: List<CoastlineSegment>,
        maxDistM: Double = SIX_NM_M
    ): Int {
        if (segments.isEmpty()) return 0
        val index = CoastlineSpatialIndex(segments)
        if (!index.hasData) return 0
        val cols = grid.cols
        val erased = AtomicInteger(0)
        IntStream.range(0, grid.rows).parallel().forEach { r ->
            val lat = grid.cellCenterLat(r)
            for (c in 0 until cols) {
                if (grid.get(r, c).isNaN()) continue           // already NoData — skip
                val lon = grid.cellCenterLon(c)
                if (index.query(lat, lon).distanceMeters > maxDistM) {
                    grid.set(r, c, Float.NaN, DepthSource.NONE, 0)
                    erased.incrementAndGet()
                }
            }
        }
        return erased.get()
    }
}
