package ykws.android.maro.data.track

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import ykws.android.maro.data.model.RouteSlowLimit
import ykws.android.maro.data.model.SlowWater
import kotlin.math.roundToLong

/**
 * **One slow-water entry a track or a route stores** — the whole seconds spent on one limit's water, or
 * in the 300 m band when [isBand] is set. The stored twin of [RouteSlowLimit], which is not serializable
 * and carries fractional seconds; every field has a default so a field protocol buffers dropped for
 * equalling it decodes back.
 */
@Serializable
data class SlowWaterEntry(
    @ProtoNumber(1) val limitKn: Double = 0.0,
    @ProtoNumber(2) val seconds: Long = 0L,
    @ProtoNumber(3) val isBand: Boolean = false
)

/**
 * **The time a track spends on water with a limit** — the figure a card's `Open water · 300M · Zones` row
 * reads, measured off the stored points with the zone-and-band test the screen attaches.
 */
object TrackSlowWater {

    /**
     * The stamp a route's figure carries: any non-zero value marks it present, and a route's figure is a
     * source fact — the plan's own seconds, which cannot be re-derived — so no regulation bake re-marks it.
     */
    const val ROUTE_STAMP = 1L

    /** The stamp a recorded track's figure takes when no regulation set is bundled. */
    private const val NO_REGULATION_STAMP = 1L

    /**
     * The stamp a recorded track's figure is computed under: the regulation set's fetch time, so a bake
     * re-marks every track uncomputed. Never `0`, which is the stored "not computed".
     */
    fun regulationStamp(fetchTimestampMs: Long?): Long =
        fetchTimestampMs?.takeIf { it > 0L } ?: NO_REGULATION_STAMP

    /**
     * The moving seconds spent on each limit's water. Each interval between two consecutive real points
     * that is not an idle stretch ([isIdleStretch]) charges its seconds to the water at its **start**
     * point ([classifier], the zone over the band); open water charges nothing, the card reading it as the
     * remainder. A `GAP` ends a run, a negative interval (a point with no time) counts zero, and there is
     * no per-interval cap — stops are removed by the idle test, and a simplified track's long straight
     * intervals are real time under way.
     *
     * Null when the track has no time span — fewer than two real points, or every point at one instant —
     * so no figure is ever shown for it. Entries come band-first, then by ascending limit.
     */
    fun of(points: List<TrackPoint>, classifier: (Double, Double) -> SlowWater?): List<SlowWaterEntry>? {
        val real = points.filter { it.type != PointType.GAP }
        if (real.size < 2 || real.maxOf { it.timeOffsetMs } <= real.minOf { it.timeOffsetMs }) return null
        val totalsMs = LinkedHashMap<Pair<Double, Boolean>, Long>()
        var previous: TrackPoint? = null
        for (point in points) {
            if (point.type == PointType.GAP) { previous = null; continue }
            val prev = previous
            previous = point
            if (prev == null) continue
            val dtMs = (point.timeOffsetMs - prev.timeOffsetMs).coerceAtLeast(0L)
            if (dtMs == 0L || isIdleStretch(prev, point)) continue
            val water = classifier(prev.lat, prev.lon) ?: continue
            val key = water.limitKn to (water is SlowWater.Band)
            totalsMs[key] = (totalsMs[key] ?: 0L) + dtMs
        }
        return totalsMs.entries
            .map { SlowWaterEntry(it.key.first, (it.value + 500L) / 1000L, it.key.second) }
            .sortedWith(compareBy({ if (it.isBand) 0 else 1 }, { it.limitKn }))
    }

    /** A settled route's attribution as the entries its saved track stores, each rounded to a whole second. */
    fun fromRoute(limits: List<RouteSlowLimit>): List<SlowWaterEntry> =
        limits.map { SlowWaterEntry(it.limitKn, it.seconds.roundToLong(), it.isBand) }
}

/** The card's `Open water · 300M · Zones` row, in whole seconds. */
data class SlowWaterCells(val openWaterSec: Long, val bandSec: Long, val zonesSec: Long)

/**
 * **The one answer for the three cells** — the band's seconds, every zone's seconds as one total (no
 * per-limit split on any card), and open water as the remainder of [totalSec]: a route's Total (est.),
 * a track's Nav. Integer arithmetic throughout, so the three always sum to [totalSec] while the remainder
 * is not negative; a figure measured past it reads open water as zero rather than below.
 */
fun slowWaterCells(totalSec: Long, entries: List<SlowWaterEntry>): SlowWaterCells {
    val bandSec = entries.filter { it.isBand }.sumOf { it.seconds }
    val zonesSec = entries.filterNot { it.isBand }.sumOf { it.seconds }
    return SlowWaterCells((totalSec - bandSec - zonesSec).coerceAtLeast(0L), bandSec, zonesSec)
}
