package ykws.android.maro.spatial.multipass

import kotlin.math.abs
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.SpatialOperations

/** The Douglas–Peucker straightener — points only ever drop, the wall and the price bound every chord. */
class RouteStraightenTest {

    private val mPerDegLat = 111_320.0
    private val mPerDegLon = 80_070.0

    /** A point obstacle's distance field — a rock of [radiusM] at [center], the wall at its rim. */
    private fun rockField(center: LatLng, radiusM: Double): RouteCostField =
        RouteCostField.ofHard { p ->
            val northM = (p.latitude - center.latitude) * mPerDegLat
            val eastM = (p.longitude - center.longitude) * mPerDegLon
            hypot(eastM, northM) - radiusM
        }

    private fun setupOf(field: RouteCostField, marginM: Double, points: List<LatLng>): PullSetup =
        PullSetup(
            marginM, coarseStepM = marginM, priceStepM = 100.0,
            field = field, start = points.first(), aim = points.last()
        )

    @Test
    fun `a near-collinear run collapses into one chord`() {
        val field = rockField(LatLng(44.0, 7.0), 40.0)
        val points = listOf(
            LatLng(43.9900, 6.9900),
            LatLng(43.9900, 6.9950),
            LatLng(43.9900, 7.0000),
            LatLng(43.9900, 7.0050)
        )
        val straightened = MultipassPull.straightenLine(setupOf(field, 20.0, points), points)
        assertEquals(listOf(points.first(), points.last()), straightened)
    }

    @Test
    fun `a point off the chord is kept`() {
        val field = rockField(LatLng(44.0, 7.0), 40.0)
        val points = listOf(
            LatLng(43.9900, 6.9900),
            LatLng(43.9920, 7.0000),
            LatLng(43.9900, 7.0100)
        )
        val straightened = MultipassPull.straightenLine(setupOf(field, 20.0, points), points)
        assertEquals(points, straightened)
    }

    @Test
    fun `a chord that would cross the wall is refused`() {
        val field = rockField(LatLng(44.0, 7.0), 40.0)
        val a = LatLng(43.99960, 6.99940)
        val p = LatLng(43.99910, 7.00000)
        val b = LatLng(43.99960, 7.00060)
        val points = listOf(a, p, b)
        assertTrue(field.hardDistanceM(a) >= 20.0)
        assertTrue(field.hardDistanceM(p) >= 20.0)
        assertTrue(field.hardDistanceM(b) >= 20.0)
        // The chord a→b passes ≈4 m from the rock, so the middle waypoint must be kept.
        val straightened = MultipassPull.straightenLine(setupOf(field, 20.0, points), points)
        assertEquals(points, straightened)
    }

    /**
     * **The price branch of `chordLegal`.** A run whose middle point stands 8 m off its chord — inside the
     * deviation, the wall far away — so only the chord's total seconds decide: a slow disc standing on the chord
     * and off the run makes the chord dearer and the point is kept, while the same disc standing on the run's
     * middle and off the chord makes it cheaper and the run collapses.
     */
    @Test
    fun `a dearer chord is refused while a cheaper one collapses`() {
        val north = 43.99
        val east = 7.0
        fun pt(eastM: Double, northM: Double) = LatLng(north + northM / mPerDegLat, east + eastM / mPerDegLon)
        fun metres(a: LatLng, b: LatLng) = hypot((a.longitude - b.longitude) * mPerDegLon, (a.latitude - b.latitude) * mPerDegLat)
        val points = listOf(pt(-500.0, 0.0), pt(0.0, 8.0), pt(500.0, 0.0))
        fun slowDiscOn(centre: LatLng, radiusM: Double): RouteCostField = RouteCostField(
            listOf(
                RouteCostSource.Hard(distanceAt = { p -> metres(p, LatLng(44.0, 9.0)) }),
                RouteCostSource.Soft(
                    priceSec = { p -> if (metres(p, centre) < radiusM) 0.5 else 0.0 },
                    tag = MultipassCellState.BAND,
                    clearanceAt = { p -> abs(metres(p, centre) - radiusM) }
                )
            )
        )
        fun setupOn(field: RouteCostField) = PullSetup(
            20.0, coarseStepM = 20.0, priceStepM = 20.0,
            field = field, start = points.first(), aim = points.last(), paceMps = 2.0
        )
        fun runSec(setup: PullSetup): Double {
            val ctx = setup.context()
            return points.zipWithNext { a, b -> MultipassPull.totalSec(a, b, ctx) }.sum()
        }
        fun chordSec(setup: PullSetup) = MultipassPull.totalSec(points.first(), points.last(), setup.context())

        // The disc's top stands 4 m north of the chord, under it; its bottom 4 m south of the run's middle, over it.
        val underTheChord = setupOn(slowDiscOn(pt(0.0, -196.0), 200.0))
        val underTheRun = setupOn(slowDiscOn(pt(0.0, 204.0), 200.0))
        for (setup in listOf(underTheChord, underTheRun)) {
            assertEquals(
                "the chord is clear of the wall", null,
                MultipassPull.legClearCause(points.first(), points.last(), setup.context())
            )
        }
        assertTrue("the chord crossing the disc is dearer than the run", chordSec(underTheChord) > runSec(underTheChord))
        assertTrue("the run crossing the disc is dearer than the chord", chordSec(underTheRun) < runSec(underTheRun))

        assertEquals("the dearer chord is refused", points, MultipassPull.straightenLine(underTheChord, points))
        assertEquals(
            "the cheaper chord collapses the run",
            listOf(points.first(), points.last()), MultipassPull.straightenLine(underTheRun, points)
        )
    }

    @Test
    fun `straightening never adds points and keeps the ends`() {
        val field = rockField(LatLng(44.0, 7.0), 40.0)
        val points = (0..6).map { LatLng(43.9900 + 0.0003 * it, 6.9900 + 0.0020 * it) }
        val straightened = MultipassPull.straightenLine(setupOf(field, 20.0, points), points)
        assertTrue(straightened.size <= points.size)
        assertEquals(points.first(), straightened.first())
        assertEquals(points.last(), straightened.last())
    }

    @Test
    fun `a two-point line is returned unchanged`() {
        val field = rockField(LatLng(44.0, 7.0), 40.0)
        val points = listOf(LatLng(43.99, 6.99), LatLng(43.99, 7.01))
        val straightened = MultipassPull.straightenLine(setupOf(field, 20.0, points), points)
        assertEquals(points, straightened)
    }

    @Test
    fun `total seconds are the travel when the water is free`() {
        // A wall far away and no soft source: the total is the travel alone, so the guard can weigh a
        // shorter chord against a longer path by distance at the walk's pace.
        val field = rockField(LatLng(44.0, 9.0), 40.0)
        val a = LatLng(43.99, 7.00)
        val b = LatLng(43.99, 7.01)
        val setup = PullSetup(
            20.0, coarseStepM = 20.0, priceStepM = 100.0,
            field = field, start = a, aim = b, paceMps = 2.0
        )
        val ctx = setup.context()
        val dist = SpatialOperations.haversine(a, b)
        assertEquals(dist / 2.0, MultipassPull.totalSec(a, b, ctx), 1e-6)
    }
}
