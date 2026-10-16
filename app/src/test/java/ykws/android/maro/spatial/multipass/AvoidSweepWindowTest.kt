package ykws.android.maro.spatial.multipass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.SpatialOperations
import kotlin.math.PI
import kotlin.math.cos

/**
 * **The settled lookback's window and its allowance — the taunt's cure, pinned over JVM fixtures.**
 *
 * Three behaviours are read here, each on a fixture that is arithmetic rather than a device reading:
 *
 * - **The allowance is relative.** A chord dearer than the legs it replaces by **under a small share** of
 *   those legs drops; one dearer by **more** keeps; and the free-legs lane (`pathSec` zero) keeps a priced
 *   chord, because a share of nothing is nothing. The price field is a **linear** law in north, so the two
 *   sides of the comparison are exact (the midpoint rule is exact for a linear price) and the difference
 *   between a bow and its chord is a number the fixture names rather than samples.
 * - **The window collapses a flat run**, one the triple alone cannot see, to a single chord; a **genuinely
 *   turned** run emits no window at all, because the geometry gate blocks it before any field read.
 * - **The clearance proof stays first and absolute** — a chord refused by land pays no price read, proven
 *   by the walk's own `priceReads` and `marks` counters standing at zero.
 *
 * The ends and the determinism are pinned on the flat fixture: the raw ends never move, and the same input
 * sweeps to the same line twice.
 */
class AvoidSweepWindowTest {

    /** `route.avoid.obstacle.marginM` as it ships. */
    private val marginM = 25.0

    /** The walk's own fine step, handed as both the clearance and the price step so no coarsening binds. */
    private val stepM = MultipassPull.clearanceStep(marginM)

    /** The shipped allowance share, read as a value rather than a key so the fixtures name their own number. */
    private val share = 0.02

    /**
     * **A chord dearer by a whisker drops.** The line bows 5 cm north of its chord, so the two legs are a
     * hair longer than the chord and pay a hair more — the chord is **dearer**, but by about a thousandth
     * of a second, far inside the allowance. The strict test would pin this vertex; the allowance drops it.
     */
    @Test
    fun aChordDearerWithinTheAllowanceDrops() {
        val field = linearPriceField()
        val a = pt(0.0, 0.0)
        val b = pt(500.0, -0.05)
        val c = pt(1000.0, 0.0)
        val setup = PullSetup(marginM, stepM, stepM, field, a, c)
        val ctx = setup.context()

        val chordSec = MultipassPull.softPriceSec(a, c, ctx)
        val pathSec = MultipassPull.softPriceSec(a, b, ctx) + MultipassPull.softPriceSec(b, c, ctx)
        assertTrue("the chord is genuinely dearer than the legs it would replace", chordSec > pathSec)
        assertTrue(
            "but only by a whisker — inside the allowance ($chordSec vs $pathSec)",
            chordSec - pathSec < pathSec * share
        )

        val lines = mutableListOf<String>()
        val swept = MultipassPull.sweep(
            setup, listOf(a, b, c), refusals = null, trace = { lines += it() }, allowanceShare = share
        )

        assertEquals("so the vertex drops", listOf(a, c), swept)
        assertTrue(
            "and the sweep reports the drop",
            lines.any { it.startsWith("SWEEP drop idx=1 ${sweepAt(b)} turn=") }
        )
        assertTrue(
            "the summary counts it",
            lines.contains("SWEEP in=3 out=2 passes=2 dropped=1 refusedClear=0 refusedPrice=0")
        )
    }

    /**
     * **A chord dearer by a whole second keeps.** The same geometry bows 53.6 m, so the legs are longer than
     * the chord by enough to make it dearer by ~1 s — well beyond the allowance's ~0.15 s — and the vertex
     * is kept exactly as the strict test kept it.
     */
    @Test
    fun aChordDearerBeyondTheAllowanceKeeps() {
        val field = linearPriceField()
        val a = pt(0.0, 0.0)
        val b = pt(500.0, -53.6)
        val c = pt(1000.0, 0.0)
        val setup = PullSetup(marginM, stepM, stepM, field, a, c)
        val ctx = setup.context()

        val chordSec = MultipassPull.softPriceSec(a, c, ctx)
        val pathSec = MultipassPull.softPriceSec(a, b, ctx) + MultipassPull.softPriceSec(b, c, ctx)
        assertTrue(
            "the chord is dearer beyond the allowance ($chordSec vs $pathSec)",
            chordSec > pathSec * (1.0 + share)
        )

        val lines = mutableListOf<String>()
        val swept = MultipassPull.sweep(
            setup, listOf(a, b, c), refusals = null, trace = { lines += it() }, allowanceShare = share
        )

        assertEquals("so the vertex is kept", listOf(a, b, c), swept)
        assertTrue(
            "and the sweep names the price proof that kept it",
            lines.any {
                it.startsWith("SWEEP keep idx=1 ${sweepAt(b)} turn=") && it.contains("why=PRICE")
            }
        )
    }

    /**
     * **The free-legs lane keeps a dear chord.** The legs pay nothing at all — a share of nothing is
     * nothing — so the allowance cannot cut priced water: a chord of ~10 s against `pathSec = 0` keeps.
     */
    @Test
    fun aChordOverFreeLegsIsNeverCutByTheAllowance() {
        val field = discPriceField()
        val a = pt(0.0, 0.0)
        val b = pt(500.0, 40.0)
        val c = pt(1000.0, 0.0)
        val setup = PullSetup(marginM, stepM, stepM, field, a, c)
        val ctx = setup.context()

        val chordSec = MultipassPull.softPriceSec(a, c, ctx)
        val pathSec = MultipassPull.softPriceSec(a, b, ctx) + MultipassPull.softPriceSec(b, c, ctx)
        assertEquals("the legs pay nothing", 0.0, pathSec, 0.0)
        assertTrue("while the chord is priced ($chordSec)", chordSec > 0.0)

        val lines = mutableListOf<String>()
        val swept = MultipassPull.sweep(
            setup, listOf(a, b, c), refusals = null, trace = { lines += it() }, allowanceShare = share
        )

        assertEquals("so the vertex is kept, never cut by a share of nothing", listOf(a, b, c), swept)
        assertTrue(
            "and the proof prints the free legs beside the dear chord",
            lines.any { it.contains("why=PRICE chordSec=") && it.contains("pathSec=0.000") }
        )
    }

    /**
     * **A flat run collapses to one chord.** Four interior vertices in a gentle line, none turning past the
     * gate, are welded into the single chord from the raw start to the raw aim — the run the triple alone
     * would only eat one vertex a pass.
     */
    @Test
    fun aFlatRunCollapsesToOneChordAndTheWindowNamesIt() {
        val field = openWater()
        val a = pt(0.0, 0.0)
        val v1 = pt(250.0, -1.0)
        val v2 = pt(500.0, -2.0)
        val v3 = pt(750.0, -1.0)
        val c = pt(1000.0, 0.0)
        val setup = PullSetup(marginM, stepM, stepM, field, a, c)

        val lines = mutableListOf<String>()
        val swept = MultipassPull.sweep(setup, listOf(a, v1, v2, v3, c), refusals = null, trace = { lines += it() })

        assertEquals("the run collapses to one chord", listOf(a, c), swept)
        assertTrue(
            "and the window names the span it collapsed",
            lines.any { it.startsWith("SWEEP window idx=1 span=4 ${sweepAt(v1)} turn=") }
        )
        assertTrue(
            "the summary keeps its shape, the window's vertices in `dropped`",
            lines.contains("SWEEP in=5 out=2 passes=2 dropped=3 refusedClear=0 refusedPrice=0")
        )
    }

    /**
     * **A genuinely turned run is left exactly as today.** A zig-zag whose every interior turn is past the
     * gate emits no window at all — the gate blocks it before any field read — while the triple still
     * straightens the line, so today's behaviour is carried unchanged.
     */
    @Test
    fun aTurnedRunEmitsNoWindow() {
        val field = openWater()
        val a = pt(0.0, 0.0)
        val v1 = pt(200.0, 300.0)
        val v2 = pt(400.0, 0.0)
        val v3 = pt(600.0, 300.0)
        val c = pt(800.0, 0.0)
        val setup = PullSetup(marginM, stepM, stepM, field, a, c)

        val lines = mutableListOf<String>()
        val swept = MultipassPull.sweep(setup, listOf(a, v1, v2, v3, c), refusals = null, trace = { lines += it() })

        assertTrue(
            "the window never opens on a turned run",
            lines.none { it.startsWith("SWEEP window") }
        )
        assertEquals("while the triple still straightens the line", listOf(a, c), swept)
    }

    /**
     * **A chord refused by land pays no price.** The single interior vertex's skipping chord crosses a wall,
     * so the clearance proof refuses it first and short-circuits — no price read and no price partition is
     * ever built, which the walk's own counters prove.
     */
    @Test
    fun aChordRefusedByLandPaysNoPriceReadAtAll() {
        val field = fieldWithWallAndPrice()
        val a = pt(0.0, 0.0)
        val b = pt(500.0, 20.0)
        val c = pt(1000.0, 0.0)
        val setup = PullSetup(marginM, stepM, stepM, field, a, c)
        val memo = MarkMemo()
        val timing = PullTiming()

        val lines = mutableListOf<String>()
        val swept = MultipassPull.sweep(
            setup, listOf(a, b, c), refusals = null, timing = timing, memo = memo, trace = { lines += it() }
        )

        assertEquals("the land refusal keeps the vertex", listOf(a, b, c), swept)
        assertTrue(
            "and the sweep names the land proof",
            lines.any { it.startsWith("SWEEP keep idx=1 ${sweepAt(b)} turn=") && it.contains("why=LAND") }
        )
        assertEquals("no price read was paid", 0L, timing.priceReads)
        assertEquals("and no price partition was built", 0L, timing.marks)
        assertTrue(
            "no price figure is printed either",
            lines.none { it.contains("chordSec=") }
        )
    }

    /**
     * **The ends never move and one input yields one line.** The flat fixture's raw start and aim are the
     * swept line's own ends, and the same input sweeps to the same points twice — the window does not break
     * the deterministic fixed point.
     */
    @Test
    fun theEndsAndTheDeterminismSurviveTheWindow() {
        val field = openWater()
        val a = pt(0.0, 0.0)
        val v1 = pt(250.0, -1.0)
        val v2 = pt(500.0, -2.0)
        val v3 = pt(750.0, -1.0)
        val c = pt(1000.0, 0.0)
        val setup = PullSetup(marginM, stepM, stepM, field, a, c)

        val once = MultipassPull.sweep(setup, listOf(a, v1, v2, v3, c), refusals = null)
        val twice = MultipassPull.sweep(setup, listOf(a, v1, v2, v3, c), refusals = null)

        assertEquals("the same input yields the same points", once, twice)
        assertEquals("the raw start never moves", a, once.first())
        assertEquals("nor the raw aim", c, once.last())
    }

    // ── Fixtures ───────────────────────────────────────────────────────────────

    /** The linear-in-north price field: everything is priced, and the price is a straight ramp — exact. */
    private fun linearPriceField(): RouteCostField = RouteCostField(
        listOf(
            RouteCostSource.Hard(distanceAt = { Double.MAX_VALUE }),
            RouteCostSource.Soft(
                priceSec = { p -> PRICE_ALPHA * (northMetres(p) + PRICE_Y_OFFSET_M) },
                tag = MultipassCellState.ZONE
            )
        ),
        cellM = 1.0
    )

    /** Open water: a wall at `MAX_VALUE` and no price at all. */
    private fun openWater(): RouteCostField =
        RouteCostField.ofHard { Double.MAX_VALUE }

    /** A soft disc of price standing on the `a → c` chord's midpoint and off both legs. */
    private fun discPriceField(): RouteCostField = RouteCostField(
        listOf(
            RouteCostSource.Hard(distanceAt = { Double.MAX_VALUE }),
            RouteCostSource.Soft(
                priceSec = { p -> if (dist(p, DISC_CENTRE) <= DISC_RADIUS_M) DISC_PRICE_SEC else 0.0 },
                tag = MultipassCellState.ZONE
            )
        ),
        cellM = 1.0
    )

    /** A wall on the `a → c` chord's midpoint, plus a price everywhere — so a price read would be counted. */
    private fun fieldWithWallAndPrice(): RouteCostField = RouteCostField(
        listOf(
            RouteCostSource.Hard(distanceAt = { p -> dist(p, pt(500.0, 0.0)) }),
            RouteCostSource.Soft(
                priceSec = { p -> PRICE_ALPHA * (northMetres(p) + PRICE_Y_OFFSET_M) },
                tag = MultipassCellState.ZONE
            )
        ),
        cellM = 1.0
    )

    // ── Geometry and helpers ─────────────────────────────────────────────────────

    private fun dist(a: LatLng, b: LatLng): Double = SpatialOperations.haversine(a, b)

    /** The `at=lat,lon` token the sweep writes for [p]. */
    private fun sweepAt(p: LatLng): String = "at=${fmt(p.latitude, 5)},${fmt(p.longitude, 5)}"

    /** [p]'s metres north of the fixture's own parallel. */
    private fun northMetres(p: LatLng): Double = (p.latitude - FRAME_LAT) * M_PER_DEG_LAT

    private companion object {

        private const val FRAME_LAT = 43.50
        private const val FRAME_LON = 7.00

        private val M_PER_DEG_LAT = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
        private val M_PER_DEG_LON = M_PER_DEG_LAT * cos(Math.toRadians(FRAME_LAT))

        /** The linear price's slope (s per metre per metre north) and its offset (m of north, kept positive). */
        private const val PRICE_ALPHA = 0.0000373
        private const val PRICE_Y_OFFSET_M = 200.0

        /** The free-legs fixture's priced disc — on the chord's midpoint, off both legs, ~10 s of charge. */
        private val DISC_CENTRE = pt(500.0, 0.0)
        private const val DISC_RADIUS_M = 30.0
        private const val DISC_PRICE_SEC = 0.1667

        private fun east(m: Double): Double = FRAME_LON + m / M_PER_DEG_LON
        private fun north(m: Double): Double = FRAME_LAT + m / M_PER_DEG_LAT
        private fun pt(eastM: Double, northM: Double): LatLng = LatLng(north(northM), east(eastM))
    }
}
