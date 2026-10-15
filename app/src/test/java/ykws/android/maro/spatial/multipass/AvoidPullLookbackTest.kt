package ykws.android.maro.spatial.multipass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.SpatialOperations
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min

/**
 * **P0, P0b and P2 of `261014_FEAT_PLN_Route_pull-lookback-pass.md` — the pull's spurious bend, and the
 * sweep that removes it, pinned over JVM.**
 *
 * The forward-only greedy commits a waypoint on the **first** refused chord and re-anchors there, so a
 * vertex standing over open water between two real corners can survive a chord that would skip it. The
 * shared fixture is exactly that shape: two point walls make the greedy refuse `start→dip` (committing
 * `suspect`) and then `suspect→cornerSuccessor` (committing `corner`), after which the water reopens
 * straight to the aim — so the raw pull draws `[start, suspect, corner, aim]`.
 *
 * **P2 flipped this file from characterisation to specification.** It once pinned `[start, suspect,
 * corner, aim]` as today's output — the pre-change baseline `P1`'s sweep was measured against; a red test
 * then would have meant the artifact was already gone. With the sweep wired into the fine pass's settled
 * line the spurious bend is gone, so the file now asserts the **swept** line (`suspect` dropped, `corner`
 * kept) and pins the raw pull only as the input the sweep is handed.
 *
 * **The sweep's price half is proven here, never vacuous.** The clearance fixture carries no soft source,
 * so its price condition holds trivially — a price equal on both sides is never dearer. The second
 * fixture adds a soft disc standing **on the skipping chord** `start→corner` and off both segments it
 * would replace, so the skipping chord is **dearer** and the vertex is **kept**: the "a dearer chord
 * never shortcuts priced water" half of the plan's guarantee, read on a line that would otherwise drop.
 * On every fixture the corner stays, because the chord that would skip it is refused.
 *
 * **The sweep's own report is read here, not assumed.** Each fixture injects a sink and asserts the lines
 * the sweep now emits — the `SWEEP drop` at the suspect vertex and the `SWEEP keep … why=PRICE` at its own,
 * each beside the summary line's counts — so the instrumentation is pinned by the same fixtures that pin
 * the behaviour it witnesses, and a silent sweep can no longer pass for one that ran.
 */
class AvoidPullLookbackTest {

    /** `route.avoid.obstacle.marginM` as it ships. */
    private val marginM = 25.0

    /** The walk's own fine step, handed as both the clearance and the price step so no coarsening binds. */
    private val stepM = MultipassPull.clearanceStep(marginM)

    /**
     * **The clearance fixture's own line.** One wall detour that reopens, so the raw pull keeps the
     * `suspect` vertex over open water; the sweep's skipping chord is clear and unpriced, so it drops it.
     * The field is the distance to the nearer of two point walls — a materialized wall, so the margin
     * reads [RouteCostField.hardDistanceM] and no rastered `blocked` step interferes — and it carries no
     * price, so the guard is inert and the drop rests on the clearance alone.
     */
    @Test
    fun theSweepDropsTheVertexTheForwardOnlyPullKept() {
        val field = RouteCostField.ofHard { p -> min(dist(p, WALL1), dist(p, WALL2)) }
        val path = listOf(START, SUSPECT, DIP, CORNER, CORNER_SUCCESSOR, AIM)

        val setup = PullSetup(marginM, stepM, stepM, field, START, AIM)
        val refusals = PullRefusals()
        val pulled = MultipassPull.pull(setup, path, refusals = refusals)
        val ctx = setup.context()

        // ── The raw pull's own output — the input the sweep is handed ─────────────
        assertEquals(
            "the forward-only pull keeps the suspect vertex standing over open water",
            listOf(START, SUSPECT, CORNER, AIM), pulled
        )
        assertTrue("the walk refused chords, so the sweep's gate opens", refusals.land >= 1)
        assertEquals("both refusals are land — no price is in play", 0, refusals.price)

        // The refusals were real decisions, not a slack margin: the two chords the walk refused do fail.
        assertTrue(
            "the chord that committed the suspect vertex is refused",
            MultipassPull.legClearCause(START, DIP, ctx) != null
        )
        assertTrue(
            "the chord that committed the corner is refused",
            MultipassPull.legClearCause(SUSPECT, CORNER_SUCCESSOR, ctx) != null
        )

        // ── THE SWEEP — the spurious bend is gone, the real corner stays ──────────
        val lines = mutableListOf<String>()
        val swept = MultipassPull.sweep(setup, pulled, refusals, trace = { lines += it() })
        assertEquals(
            "the sweep drops the suspect vertex and keeps the real corner",
            listOf(START, CORNER, AIM), swept
        )

        // ── THE REPORT — the sweep names the vertex it dropped and the proof that kept the corner ──
        assertTrue(
            "the sweep reports dropping the suspect vertex",
            lines.any { it.startsWith("SWEEP drop idx=1 ${sweepAt(SUSPECT)} turn=") }
        )
        assertTrue(
            "the summary counts the drop beside the two clearance refusals that kept the corner",
            lines.contains("SWEEP in=4 out=3 passes=2 dropped=1 refusedClear=2 refusedPrice=0")
        )

        // Both proofs held on the dropped triple, read here so the drop is a proof and not a guess.
        assertTrue(
            "the dropped vertex's skipping chord start→corner is clear",
            MultipassPull.legClearCause(START, CORNER, ctx) == null
        )
        val skipping = MultipassPull.softPriceSec(START, CORNER, ctx)
        val replaced = MultipassPull.softPriceSec(START, SUSPECT, ctx) +
            MultipassPull.softPriceSec(SUSPECT, CORNER, ctx)
        assertTrue(
            "and it is not dearer than the two segments it replaces",
            skipping <= replaced
        )

        // The corner is NOT removable: on the swept triple its own skipping chord start→aim still grazes
        // the second wall, so a sweep keeps it.
        assertTrue(
            "the corner stays: the chord that would skip it is refused",
            MultipassPull.legClearCause(START, AIM, ctx) != null
        )
    }

    /**
     * **The price half, on a dearer chord.** The clearance fixture's price condition passes vacuously —
     * no soft source stands anywhere, so the skipping chord prices the same as the two segments it would
     * replace. Here a soft disc of [SOFT_PRICE_SEC] seconds stands **on** the skipping chord `start→
     * corner` and off both segments, so the chord is strictly dearer. The disc touches no raw segment, so
     * the raw pull is unmoved and the sweep is handed the same `[start, suspect, corner, aim]` line — and
     * keeps it, which is the plan's "a dearer chord never shortcuts priced water" read on a live drop.
     */
    @Test
    fun aVertexWhoseSkippingChordIsDearerIsKept() {
        val field = pricedField()
        val path = listOf(START, SUSPECT, DIP, CORNER, CORNER_SUCCESSOR, AIM)

        val setup = PullSetup(marginM, stepM, stepM, field, START, AIM)
        val refusals = PullRefusals()
        val pulled = MultipassPull.pull(setup, path, refusals = refusals)
        val ctx = setup.context()

        assertEquals(
            "the price stands off every raw segment, so the pull draws the same line",
            listOf(START, SUSPECT, CORNER, AIM), pulled
        )
        assertTrue("the walk refused chords, so the sweep's gate opens", refusals.land >= 1)

        // The clearance half holds — the skipping chord is clear — but the price half refuses the drop.
        assertTrue(
            "the skipping chord start→corner is clear",
            MultipassPull.legClearCause(START, CORNER, ctx) == null
        )
        val skipping = MultipassPull.softPriceSec(START, CORNER, ctx)
        val replaced = MultipassPull.softPriceSec(START, SUSPECT, ctx) +
            MultipassPull.softPriceSec(SUSPECT, CORNER, ctx)
        assertEquals("the two segments pay no price at all", 0.0, replaced, 0.0)
        assertTrue(
            "but the skipping chord is dearer than the two segments ($skipping > $replaced)",
            skipping > replaced
        )

        val lines = mutableListOf<String>()
        val swept = MultipassPull.sweep(setup, pulled, refusals, trace = { lines += it() })
        assertEquals(
            "so the sweep keeps the vertex rather than shortcutting the priced water",
            listOf(START, SUSPECT, CORNER, AIM), swept
        )

        // ── THE REPORT — the sweep names the priced proof that refused the drop ──
        assertTrue(
            "the sweep names the vertex and the price proof that kept it",
            lines.any {
                it.startsWith("SWEEP keep idx=1 ${sweepAt(SUSPECT)} turn=") && it.contains("why=PRICE")
            }
        )
        assertTrue(
            "the price proof prints its two sides — the dearer chord against the free path",
            lines.any { it.contains("why=PRICE chordSec=") && it.contains("pathSec=0.000") }
        )
        assertTrue(
            "the arm split names the price that kept it, and the place its charge falls",
            lines.any { it.startsWith("SWEEP arms idx=1 zone=") && it.contains("worst=ZONE at=") }
        )
        assertTrue(
            "and every priced mark of the chord is named, with the arm that charged it",
            lines.any { it.startsWith("SWEEP mark idx=1 i=") && it.contains(" arm=ZONE sec=") }
        )
        assertTrue(
            "the summary counts the price refusal — beside the corner's own land refusal — and no drop",
            lines.contains("SWEEP in=4 out=4 passes=1 dropped=0 refusedClear=1 refusedPrice=1")
        )
    }

    /**
     * **The rate, pinned.** A price is a **per-cell** cost, so the same water must read the same seconds
     * whatever cell the field was priced at: two fields differing only in [RouteCostField.cellM] — 1 m
     * and [PRICE_CELL_M] — must sweep the same line to the same verdict, with the dearer cell's figure
     * exactly [PRICE_CELL_M] times smaller. That is the neutrality the rate carries, and the reading no
     * fixture could see while the sum multiplied a cell's price by metres.
     */
    @Test
    fun thePriceWalkReportsSecondsWhateverCellPricedTheField() {
        val path = listOf(START, SUSPECT, DIP, CORNER, CORNER_SUCCESSOR, AIM)
        val perMetre = sweptChordSec(pricedField(cellM = 1.0), path)
        val perCell = sweptChordSec(pricedField(cellM = PRICE_CELL_M), path)

        assertTrue("the vertex is kept either way — no verdict moved", perMetre > 0.0 && perCell > 0.0)
        assertEquals(
            "and the dearer cell's own figure is smaller by exactly its cell",
            PRICE_CELL_M,
            perMetre / perCell,
            1e-9
        )
    }

    /** The `chordSec=` figure the sweep reports for the suspect vertex's own skipping chord. */
    private fun sweptChordSec(field: RouteCostField, path: List<LatLng>): Double {
        val setup = PullSetup(marginM, stepM, stepM, field, START, AIM)
        val refusals = PullRefusals()
        val pulled = MultipassPull.pull(setup, path, refusals = refusals)
        val lines = mutableListOf<String>()
        MultipassPull.sweep(setup, pulled, refusals, trace = { lines += it() })
        return lines.first { it.contains("why=PRICE") }
            .substringAfter("chordSec=")
            .substringBefore(" ")
            .toDouble()
    }

    /** The point-to-point distance the field's wall source answers with. */
    private fun dist(a: LatLng, b: LatLng): Double = SpatialOperations.haversine(a, b)

    /** The `at=lat,lon` token the sweep writes for [p] — the identifier that matches a bend across rungs. */
    private fun sweepAt(p: LatLng): String = "at=${fmt(p.latitude, 5)},${fmt(p.longitude, 5)}"

    /**
     * The priced fixture's field: the two walls the clearance fixture walks, plus a soft disc of
     * [SOFT_PRICE_SEC] seconds centred on [SOFT_CENTRE] — the point the skipping chord `start→corner`
     * passes through and both segments it replaces clear by more than the disc's own radius, so only the
     * skipping chord is priced. The disc declares no boundary, so the walk reads every fine mark and the
     * priced sum is the model's own, with no grouping to reason about.
     */
    private fun pricedField(cellM: Double = PRICE_CELL_M): RouteCostField = RouteCostField(
        listOf(
            RouteCostSource.Hard(distanceAt = { p -> min(dist(p, WALL1), dist(p, WALL2)) }),
            RouteCostSource.Soft(
                priceSec = { p -> if (dist(p, SOFT_CENTRE) <= SOFT_RADIUS_M) SOFT_PRICE_SEC else 0.0 },
                tag = MultipassCellState.ZONE,
                // The arm the split must name — the very test the price above applies, so the report and
                // the charge cannot disagree about who charged the point.
                armAt = { p ->
                    PriceArm.ZONE to (if (dist(p, SOFT_CENTRE) <= SOFT_RADIUS_M) SOFT_PRICE_SEC else 0.0)
                }
            )
        ),
        cellM = cellM
    )

    private companion object {

        /** The cell the priced fixture's field is priced at — the divisor its own figures carry. */
        private const val PRICE_CELL_M = 20.0

        /** The chord's own parallel, so every fixture offsets in metres without a projection. */
        private const val FRAME_LAT = 43.50

        /** The chord's own origin longitude. */
        private const val FRAME_LON = 7.00

        private val M_PER_DEG_LAT = SpatialOperations.EARTH_RADIUS_M * PI / 180.0

        private val M_PER_DEG_LON = M_PER_DEG_LAT * cos(Math.toRadians(FRAME_LAT))

        /** A longitude [m] metres east of the frame's origin. */
        private fun east(m: Double): Double = FRAME_LON + m / M_PER_DEG_LON

        /** A latitude [m] metres north of the frame's parallel. */
        private fun north(m: Double): Double = FRAME_LAT + m / M_PER_DEG_LAT

        /** The point [eastM] east and [northM] north of the frame's origin, in the fixture's own metric. */
        private fun pt(eastM: Double, northM: Double): LatLng = LatLng(north(northM), east(eastM))

        /** The wall the path grazes at its dip — the refusal that commits the suspect vertex. */
        private val WALL1 = pt(0.0, 0.0)

        /** The wall just past the corner — the refusal that makes the corner a real corner. */
        private val WALL2 = pt(400.0, 130.0)

        /** The two raw ends the walk must reach. */
        private val START = pt(-400.0, 0.0)
        private val AIM = pt(900.0, 250.0)

        /**
         * The raw path: `START` runs east, bulges north to `SUSPECT`, dips back to `DIP` (whose chord
         * from `START` grazes `WALL1`), turns north to the real `CORNER` (whose chord to
         * `CORNER_SUCCESSOR` grazes `WALL2`), then opens straight to the aim.
         */
        private val SUSPECT = pt(100.0, 60.0)
        private val DIP = pt(300.0, 20.0)
        private val CORNER = pt(300.0, 200.0)
        private val CORNER_SUCCESSOR = pt(700.0, 200.0)

        /**
         * The priced fixture's soft disc: centred exactly on the `START→CORNER` chord's midpoint at a
         * radius well inside the clearance the two segments keep from it — so only the skipping chord is
         * priced, and its price is strictly positive where the two segments pay nothing.
         */
        private val SOFT_CENTRE = pt(-50.0, 100.0)
        private const val SOFT_RADIUS_M = 40.0
        private const val SOFT_PRICE_SEC = 1.0
    }
}
