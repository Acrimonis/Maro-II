package ykws.android.maro.spatial.mesh

import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.spatial.SpatialOperations
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * **The shortcut pass — the chain the water forces, and nothing else.**
 *
 * The search answers with the vertex chain it walked, and every one of those vertices is a place the
 * line may turn: A* minimises time over a fabric refined to 60 m beside a constraint, so the chain
 * carries a bend every 60 m near the coast and each of those bends is a corner the eye reads. What
 * the drawn line needs is the **corners the water forces** — from a vertex, the farthest vertex ahead
 * whose straight segment stays on water the mesh itself vetted, then the same again from there. That
 * is this pass: the study's string-pull, with the test the sampled version it first proposed never
 * had, **exact containment** in the bake's own kept triangles ([`RouteMeshContainment`]), every
 * sample of a candidate required to stand in the boat's own stretch.
 *
 * **Why the sampled oracle was retired, and why this is not it.** A sampled `isWater` test judges a
 * point by the coastline oracle alone, and a point can be water while the mesh has no face there — a
 * pocket the depth gate closed reads exactly like open sea, which is how a corner got cut across one.
 * A *kept* triangle is the bake's own assertion that its interior cleared the depth gate and the
 * channel floor, so "inside a kept triangle of my own stretch" cannot be fooled by a patch smaller
 * than the sample step. The two are different tests, and only the second one is a proof.
 *
 * **The pass is geometric and pure.** It returns the kept vertices' positions and what the water said
 * about the candidates it tried; it changes no time, no cost and no other geometry, and the search
 * telescopes its own cumulative times through it exactly as it does through the collinear merge, so
 * the plan's duration survives the straightening as it already survives the fillet's own shortening.
 *
 * **What it can still miss, stated rather than hidden.** A candidate is sampled every [SAMPLE_M]
 * metres, so a hole in the mesh's own triangle union narrower than that step could be stepped over.
 * The step is the one the fillet samples its arcs at, and the mesh beside a constraint — the only
 * place a hole in this fabric can be — is refined to 60 m, twenty times the step. The reading that
 * would show one is the minimum clearance along the line, which the trajectory probe reports beside
 * every other number.
 */
internal object RouteShortcut {

    /** One sample every this many metres along a candidate — the fillet's own step, for its own reason. */
    const val SAMPLE_M = 10.0

    /**
     * What the pass kept.
     *
     * @property positions the kept vertices' positions in the chain handed in, ascending — always
     *           beginning at 0 and ending at the chain's last vertex, so the two ends survive.
     * @property refused how many candidate shortcuts the water refused. It is the pass's own evidence
     *           that it tested anything: zero refusals says every straight line it tried lay on
     *           vetted water, and a count near the chain's own length says almost nothing could be cut.
     * @property removed how many vertices the pass dropped — the chain's own count less the kept one.
     * @property refusedByCost how many candidates the **price** refused instead of the water: a
     *           straight run that lay on vetted water and was still no cheaper than the chain it would
     *           have replaced. Reported separately because the two refusals are different findings —
     *           one is about the mesh's water, this one is about the cost model the search priced with.
     */
    class Kept(
        val positions: IntArray,
        val refused: Int,
        val removed: Int,
        val refusedByCost: Int = 0
    )

    /**
     * The chain as it stands: every position kept, nothing refused, nothing removed.
     *
     * It exists so a reading can take the same search with the pass switched off — that line is the
     * one the device draws today — instead of comparing two revisions and calling the difference the
     * pass's.
     */
    fun chainIntact(count: Int): Kept = Kept(IntArray(count) { it }, 0, 0)

    /**
     * Walks [points] and keeps only the vertices the water and the price together force.
     *
     * From the current vertex the walk tries the **farthest** vertex first and steps back until one
     * is accepted, so what it keeps is the farthest the water allows: the greedy form is what gives
     * the fewest vertices on water that is visible ahead, and the two ends are preserved by
     * construction. The leg the chain already holds is never tested — it is a mesh edge the search
     * walked and priced — so it is the fallback the walk always has.
     *
     * **The pass re-prices what it changes.** A candidate is accepted only when the water vouches for
     * it **and** [admissible] agrees: this pass is geometric and knows no limits, so the search hands
     * it the model — the candidate's own cost at the limits in force over the chain legs it replaces,
     * against the cost of those legs — and a straight run that would be *slower* than the chain it
     * cuts is refused. That is the whole of the guard: what remains is the farthest candidate that is
     * both on water and no worse than the legs it replaces, and where none is, the walk falls back on
     * the chain's own leg, which is the search's own decision preserved.
     *
     * @param insideWater the test a candidate sample must pass; the search supplies its own kept-water
     *        test **plus the boat's own stretch** — and, while the zones' interiors are not
     *        traversable, the same rule, so a shortcut can never enter one. The two endpoints are not
     *        sampled: they are the chain's own vertices, already on water the search priced.
     * @param admissible the price side of the same decision, asked once per candidate with the indices
     *        it spans. It defaults to admitting everything, which is the pass as it stood before the
     *        cost model was handed to it.
     */
    fun keep(
        points: List<RoutePoint>,
        insideWater: (latitude: Double, longitude: Double) -> Boolean,
        admissible: (from: Int, to: Int) -> Boolean = { _, _ -> true }
    ): Kept {
        val n = points.size
        if (n < 3) return Kept(IntArray(n) { it }, 0, 0)

        val kept = ArrayList<Int>(n)
        kept.add(0)
        var refused = 0
        var refusedByCost = 0
        var from = 0
        while (from < n - 1) {
            var to = n - 1
            while (to > from + 1) {
                val onWater = staysOnWater(points[from], points[to], insideWater)
                if (onWater && admissible(from, to)) break
                if (onWater) refusedByCost++ else refused++
                to--
            }
            kept.add(to)
            from = to
        }
        return Kept(kept.toIntArray(), refused, n - kept.size, refusedByCost)
    }

    /**
     * Whether the straight segment between two vertices stays on vetted water, sampled along it.
     *
     * The interior alone is walked: both ends are vertices of the chain, so they are points the search
     * itself stood on, and asking about them would only add a way for a rounding of a triangle's edge
     * to refuse a segment the water never refused.
     */
    private fun staysOnWater(
        from: RoutePoint,
        to: RoutePoint,
        insideWater: (latitude: Double, longitude: Double) -> Boolean
    ): Boolean {
        val lengthM = metresBetween(from, to)
        val steps = max(2, ceil(lengthM / SAMPLE_M).toInt())
        for (k in 1 until steps) {
            val t = k.toDouble() / steps
            val latitude = from.latitude + (to.latitude - from.latitude) * t
            val longitude = from.longitude + (to.longitude - from.longitude) * t
            if (!insideWater(latitude, longitude)) return false
        }
        return true
    }

    /** Geodesic metres between two vertices, so a sample step is the distance it names. */
    private fun metresBetween(from: RoutePoint, to: RoutePoint): Double {
        val dLat = (to.latitude - from.latitude) * PI / 180.0
        val dLon = (to.longitude - from.longitude) * PI / 180.0
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(from.latitude * PI / 180.0) * cos(to.latitude * PI / 180.0) * sin(dLon / 2) * sin(dLon / 2)
        return 2.0 * SpatialOperations.EARTH_RADIUS_M * asin(min(1.0, sqrt(a)))
    }
}
