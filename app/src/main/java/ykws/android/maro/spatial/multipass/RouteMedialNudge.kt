package ykws.android.maro.spatial.multipass

import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.SpatialOperations
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sqrt

/**
 * **The medial nudge — a passage crossed down its middle.**
 *
 * The pull keeps only the vertices a wall pins, so through a passage between two shallow banks the drawn
 * line runs along whichever bank its grid row happened to stand nearer. This pass reads the settled line
 * at [SAMPLE_M] steps; where a sample stands inside the berth's priced span and a ray along each side of
 * its normal meets the measured 3 m edge with the two banks no further apart than twice that span — water
 * both banks price — the sample is in a **passage**, and its target is the midpoint between the two banks.
 * A run of passage samples is a stretch: each target is smoothed with its neighbours on the same leg, and a
 * vertex is placed every [VERTEX_EVERY] samples and at the stretch's two ends.
 *
 * **A stretch is moved only where the pull's own proofs accept it** — a new vertex standing nearer the
 * edge than the sample it replaces is dropped, and the stretch stands only where every new chord is clear
 * ([MultipassPull.legClearCause]) and its travel plus price is no dearer than the line it replaces
 * ([MultipassPull.totalSec]). A stretch the proofs refuse stays as the pull drew it, so the nudge can
 * centre a line but never breach the wall or make the route dearer. Samples inside the gate's own waiver
 * ([GateRule.waives]) are never nudged.
 */
internal object RouteMedialNudge {

    /** The line is read every this many metres. */
    private const val SAMPLE_M = 10.0

    /** A nudged stretch keeps one vertex per this many samples, plus its two ends. */
    private const val VERTEX_EVERY = 4

    /** A passage is at most this many berth spans wide, bank to bank. */
    private const val PASSAGE_SPAN_FACTOR = 2.0

    private val M_PER_DEG_LAT = SpatialOperations.EARTH_RADIUS_M * PI / 180.0

    /** One sample: where it stands, the leg it stands on, its unit normal (east, north) and its target. */
    private class Sample(val at: LatLng, val leg: Int, val normalEast: Double, val normalNorth: Double) {
        /** The metres along the normal to the passage's middle, `NaN` where the sample is in no passage. */
        var offsetM: Double = Double.NaN

        /** The sample's own edge reading (m), the floor a nudged vertex may not go under. */
        var edgeM: Double = Double.MAX_VALUE
    }

    fun nudge(
        setup: PullSetup,
        gate: GateRule,
        line: List<LatLng>,
        trace: (() -> String) -> Unit = {}
    ): List<LatLng> {
        val berth = setup.berth ?: return line
        if (line.size < 2) return line
        val ctx = setup.context()
        val spanM = berth.outerM * PASSAGE_SPAN_FACTOR
        val samples = sampleLine(line)
        for (s in samples) {
            if (gate.waives(s.at, setup.start, setup.aim, setup.approaches)) continue
            val edge = gate.edgeM(s.at, berth.outerM)
            if (edge >= berth.outerM) continue
            val left = gate.edges.rayHitM(s.at, s.normalEast, s.normalNorth, spanM) ?: continue
            val right = gate.edges.rayHitM(s.at, -s.normalEast, -s.normalNorth, spanM - left) ?: continue
            s.edgeM = edge
            s.offsetM = (left - right) / 2.0
        }
        val out = ArrayList<LatLng>(line.size + 8)
        var nextVertex = 0
        var stretches = 0
        var moved = 0
        var i = 0
        while (i < samples.size) {
            if (samples[i].offsetM.isNaN()) {
                i++
                continue
            }
            var j = i
            while (j + 1 < samples.size && !samples[j + 1].offsetM.isNaN()) j++
            val first = samples[i]
            val last = samples[j]
            if (j > i) stretches++
            // A stretch starting on a leg the previous moved stretch already consumed is left alone.
            if (j > i && first.leg >= nextVertex) {
                while (nextVertex <= first.leg) out.add(line[nextVertex++])
                val vertices = stretchVertices(samples, i, j, gate)
                if (vertices != null && accepts(out.last(), vertices, line, first.leg, last.leg, ctx)) {
                    out.addAll(vertices)
                    nextVertex = last.leg + 1
                    moved++
                }
            }
            i = j + 1
        }
        while (nextVertex < line.size) out.add(line[nextVertex++])
        trace { "NUDGE stretches=$stretches moved=$moved points=${line.size}→${out.size}" }
        return out
    }

    /** The line's samples, every [SAMPLE_M] along each leg at the legs' own interval midpoints. */
    private fun sampleLine(line: List<LatLng>): List<Sample> {
        val out = ArrayList<Sample>()
        for (leg in 0 until line.size - 1) {
            val a = line[leg]
            val b = line[leg + 1]
            val mLon = M_PER_DEG_LAT * cos(Math.toRadians(a.latitude))
            val dx = (b.longitude - a.longitude) * mLon
            val dy = (b.latitude - a.latitude) * M_PER_DEG_LAT
            val len = sqrt(dx * dx + dy * dy)
            if (len <= 0.0) continue
            val count = max(1, floor(len / SAMPLE_M).toInt())
            for (k in 0 until count) {
                val t = (k + 0.5) / count
                val at = LatLng(a.latitude + (b.latitude - a.latitude) * t, a.longitude + (b.longitude - a.longitude) * t)
                out.add(Sample(at, leg, -dy / len, dx / len))
            }
        }
        return out
    }

    /**
     * The stretch's new vertices — every [VERTEX_EVERY]th sample and the two ends, each moved along its
     * normal to the middle smoothed over its neighbours **on the same leg** (an offset is measured along its
     * own leg's normal, so two legs' offsets are never averaged) — or `null` where none survives. A vertex
     * that would stand nearer the edge than its sample did, or inside the wall, is dropped on its own.
     */
    private fun stretchVertices(samples: List<Sample>, from: Int, to: Int, gate: GateRule): List<LatLng>? {
        val out = ArrayList<LatLng>()
        var k = from
        while (k <= to) {
            val s = samples[k]
            var sum = s.offsetM
            var count = 1
            for (n in intArrayOf(k - 1, k + 1)) {
                if (n < from || n > to || samples[n].leg != s.leg) continue
                sum += samples[n].offsetM
                count++
            }
            val offsetM = sum / count
            val mLon = M_PER_DEG_LAT * cos(Math.toRadians(s.at.latitude))
            val moved = LatLng(
                s.at.latitude + s.normalNorth * offsetM / M_PER_DEG_LAT,
                s.at.longitude + s.normalEast * offsetM / mLon
            )
            val edge = gate.edgeM(moved, s.edgeM + gate.preferredM)
            if (edge >= s.edgeM && edge >= gate.hardM) out.add(moved)
            k = if (k == to) to + 1 else minOf(k + VERTEX_EVERY, to)
        }
        return out.ifEmpty { null }
    }

    /**
     * Whether the moved stretch stands: every chord from [anchor] through [vertices] to the line's vertex
     * after [lastLeg] clear, and its travel plus price no dearer than the original from [anchor] over the
     * vertices of legs `firstLeg + 1 .. lastLeg` to the same end.
     */
    private fun accepts(
        anchor: LatLng,
        vertices: List<LatLng>,
        line: List<LatLng>,
        firstLeg: Int,
        lastLeg: Int,
        ctx: PullContext
    ): Boolean {
        val end = line[lastLeg + 1]
        val moved = ArrayList<LatLng>(vertices.size + 2)
        moved.add(anchor)
        moved.addAll(vertices)
        moved.add(end)
        for (k in 0 until moved.size - 1) {
            if (MultipassPull.legClearCause(moved[k], moved[k + 1], ctx) != null) return false
        }
        val original = ArrayList<LatLng>()
        original.add(anchor)
        for (v in firstLeg + 1..lastLeg) original.add(line[v])
        original.add(end)
        return totalSec(moved, ctx) <= totalSec(original, ctx)
    }

    private fun totalSec(points: List<LatLng>, ctx: PullContext): Double {
        var total = 0.0
        for (k in 0 until points.size - 1) total += MultipassPull.totalSec(points[k], points[k + 1], ctx)
        return total
    }
}
