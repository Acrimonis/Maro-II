package ykws.android.maro.spatial.multipass

import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.spatial.SpatialOperations
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Relaxes a slow-water approach together with its adjoining legs. A deletion-only pull can leave an
 * open-water bend pinned by an old crossing point: its skipping chord spends too long in slow water.
 * Here a bounded window may become two legs through a NEW point, moving that crossing at the same time.
 *
 * Windows keep their ends and use the existing field, berth, pace and clearance proofs. Only a strict
 * total-cost improvement is spliced. The search may explore a replacement costlier than the original
 * while moving its pivot; that provisional geometry is never emitted. This is a bounded local search,
 * not a guarantee of the globally cheapest approach. It runs before straightening and corner rounding.
 */
internal object RouteApproachPass {
    private const val MAX_SPAN = 4
    private const val MAX_PASSES = 2
    private const val MAX_SCALES = 6
    private const val DIRECTIONS = 8
    private val metresPerDegree = SpatialOperations.EARTH_RADIUS_M * PI / 180.0

    fun refine(
        setup: PullSetup,
        line: List<LatLng>,
        bounds: BBox,
        /**
         * **This pass's own step (m)** — the smallest radius a window is tried at, the floor of the scale
         * loop and the lattice its slow-water read samples a leg on: a *geometry* step, the fine cell the
         * line was resolved at. It is deliberately apart from [PullSetup.coarseStepM], the clearance walk's
         * *proof* step, which may move without this pass's windows moving with it.
         */
        stepM: Double,
        /** The pass's own memo, so a chord the pull already proved is answered rather than re-read. */
        memo: MarkMemo? = null,
        checkCancelled: () -> Unit = {},
        trace: (() -> String) -> Unit = {}
    ): List<LatLng> {
        if (line.size < 3 || !setup.field.hasSoft || setup.paceMps <= 0.0) {
            trace { "APPROACH skipped=no-candidates" }
            return line
        }
        // Use the same orientation even when this pass is called directly rather than by finePass.
        val reversed = !sortsFirst(line.first(), line.last())
        val work = (if (reversed) line.asReversed() else line).toMutableList()
        val ctx = (if (reversed) setup.reversed() else setup).context(memo = memo)
        val slowLegs = mutableMapOf<Pair<LatLng, LatLng>, Boolean>()
        fun slow(a: LatLng, b: LatLng): Boolean = slowLegs.getOrPut(a to b) {
            hasSlowWater(a, b, ctx, stepM, checkCancelled)
        }
        var moved = 0
        var candidates = 0
        var savedSec = 0.0
        for (pass in 0 until MAX_PASSES) {
            var changed = false
            var from = 0
            while (from < work.size - 2) {
                checkCancelled()
                val a = work[from]
                val firstSlow = slow(a, work[from + 1])
                for (span in minOf(MAX_SPAN, work.lastIndex - from) downTo 2) {
                    val to = from + span
                    val e = work[to]
                    val lastSlow = slow(work[to - 1], e)
                    // A crossing and adjoining open water, not an unrelated all-slow or all-free run.
                    if (firstSlow == lastSlow) continue
                    val seedIndex = if (firstSlow) from + 1 else to - 1
                    val seed = work[seedIndex]
                    val near = if (firstSlow) a else e
                    val radiusM = 2.0 * SpatialOperations.haversine(near, seed)
                    if (radiusM < stepM) continue
                    val oldSec = (from until to).sumOf {
                        MultipassPull.totalSec(work[it], work[it + 1], ctx)
                    }
                    var pivot = seed
                    var bestSec = replacementSec(a, pivot, e, ctx, bounds)
                    var scaleM = radiusM / 2.0
                    for (scale in 0 until MAX_SCALES) {
                        if (scaleM < stepM) break
                        val centre = pivot
                        for (direction in 0 until DIRECTIONS) {
                            checkCancelled()
                            val angle = direction * 2.0 * PI / DIRECTIONS
                            val candidate = offset(centre, scaleM * cos(angle), scaleM * sin(angle))
                            if (SpatialOperations.haversine(seed, candidate) > radiusM) continue
                            candidates++
                            val sec = replacementSec(a, candidate, e, ctx, bounds)
                            if (sec < bestSec) {
                                bestSec = sec
                                pivot = candidate
                            }
                        }
                        scaleM /= 2.0
                    }
                    if (bestSec >= oldSec || !bestSec.isFinite()) continue
                    savedSec += oldSec - bestSec
                    work.subList(from + 1, to).clear()
                    work.add(from + 1, pivot)
                    moved++
                    changed = true
                    break
                }
                from++
            }
            if (!changed) break
        }
        trace { "APPROACH moved=$moved candidates=$candidates savedSec=${fmt(savedSec, 3)}" }
        return if (reversed) work.asReversed().toList() else work.toList()
    }

    /** Source-owned price arms identify slow water; marker/depth prices alone do not trigger a search. */
    private fun hasSlowWater(
        a: LatLng,
        b: LatLng,
        ctx: PullContext,
        stepM: Double,
        checkCancelled: () -> Unit
    ): Boolean {
        fun slowAt(p: LatLng) = ctx.field.armPricesAt(p).any {
            (it.first == PriceArm.BAND || it.first == PriceArm.ZONE) && it.second > 0.0
        }
        if (slowAt(a) || slowAt(b)) return true
        val marks = MarkLattice(SpatialOperations.haversine(a, b), stepM)
        for (i in 0 until marks.count) {
            if (i % 64 == 0) checkCancelled()
            val t = marks.markT(i)
            if (slowAt(LatLng(
                    a.latitude + (b.latitude - a.latitude) * t,
                    a.longitude + (b.longitude - a.longitude) * t
                ))) return true
        }
        return false
    }

    private fun replacementSec(a: LatLng, p: LatLng, b: LatLng, ctx: PullContext, bounds: BBox): Double {
        if (!inBox(bounds, p)) return Double.POSITIVE_INFINITY
        // Only the real route ends retain waivers: a window end or a new pivot earns no exemption.
        if (!MultipassPull.legClear(a, p, ctx) || !MultipassPull.legClear(p, b, ctx)) {
            return Double.POSITIVE_INFINITY
        }
        return MultipassPull.totalSec(a, p, ctx) + MultipassPull.totalSec(p, b, ctx)
    }

    private fun offset(p: LatLng, eastM: Double, northM: Double): LatLng = LatLng(
        p.latitude + northM / metresPerDegree,
        p.longitude + eastM / (metresPerDegree * cos(Math.toRadians(p.latitude)))
    )
}
