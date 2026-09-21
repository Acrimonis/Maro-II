package ykws.android.maro.spatial.taut

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.BoundingBox
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.SpatialOperations

/**
 * **The band's closed form read against the capsule it claims to be.**
 *
 * The spans are solved, not sampled: the strip's slab, the projection of the leg onto the segment's own
 * range, and the two endpoint circles. Every case here is one the band's own tests never named, and each
 * fails on the superset the slab alone produced — which is the class note's reason for intersecting the
 * slab with the projection before the hull is taken.
 *
 * The world is built in the frame's own metres, so the numbers below are the geometry and not a
 * projection of it: `Frame(originLat, originLon)` maps the latitude and longitude these helpers build
 * back to exactly the east and north metres they were asked for.
 */
class TautBandTest {

    private val originLat = 43.55
    private val originLon = 7.12

    /** The band's own width in every case below, in metres. */
    private val widthM = 10.0

    private val metresPerDegreeLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
    private val metresPerDegreeLon = metresPerDegreeLat * cos(originLat * PI / 180.0)

    private val frame = Frame(originLat, originLon)

    /** A box wide enough for every case, centred on the frame's own origin. */
    private val box = BoundingBox(
        latSouth = originLat - 0.05,
        latNorth = originLat + 0.05,
        lonWest = originLon - 0.05,
        lonEast = originLon + 0.05
    )

    private fun latLng(eastM: Double, northM: Double): LatLng = LatLng(
        originLat + northM / metresPerDegreeLat,
        originLon + eastM / metresPerDegreeLon
    )

    private fun at(eastM: Double, northM: Double): Pt = Pt(eastM, northM)

    /**
     * A world whose coastline is exactly the polylines given, each named in the frame's own metres. The
     * band reads the **coastline**, not the abstracted walls, which is why a bare polyline is the whole
     * obstacle set here.
     */
    private fun worldOf(vararg polylines: List<Pair<Double, Double>>): TautTestWorld = TautTestWorld(
        land = polylines.map { line -> line.map { latLng(it.first, it.second) } },
        depthBox = box,
        coastalBandWidthM = widthM
    )

    private fun unitOf(world: TautWorld): TautBand =
        TautBand.of(world, frame, box)
            ?: error("the band was not built over the world's own coastline")

    /** A coast running east from [fromM] for [lengthM] metres — the segment every case is measured on. */
    private fun coast(lengthM: Double, fromM: Double = 0.0): List<Pair<Double, Double>> =
        listOf(fromM to 0.0, fromM + lengthM to 0.0)

    /**
     * **A leg parallel to a segment's own line, within the width of it, and entirely beyond its end.**
     *
     * Every point of the leg stands 9 m off the segment's *infinite line* and more than 10 m off the
     * segment itself, so none of it is in the band. The slab alone reports the whole leg — the superset
     * the projection intersection removes, and the case a hull of a superset cannot see.
     */
    @Test
    fun `a leg parallel and beyond the segment's end stands outside the band`() {
        val unit = unitOf(worldOf(coast(100.0)))
        val spans = unit.spansOf(at(130.0, 9.0), at(230.0, 9.0))
        assertTrue(
            "none of the leg is in the band — its nearest point stands 31 m off the coast, against a " +
                "width of $widthM m (read ${spans.size} span(s))",
            spans.isEmpty()
        )
    }

    /**
     * **A leg that only touches the capsule is not a leg inside it.**
     *
     * Two coincident roots, one on each side of the closed form: the leg below runs at exactly the width
     * from the segment's own line, and its projection onto the segment reaches the segment's end at a
     * single parameter — so the slab's interval and the projection's meet at one point, and the endpoint
     * circle's quadratic has a zero discriminant there. Measure-zero contact is not an interval, so the
     * band has nothing to cut and nothing to charge.
     */
    @Test
    fun `a leg tangent to the capsule at a single point reports no span`() {
        val unit = unitOf(worldOf(coast(100.0)))
        val spans = unit.spansOf(at(100.0, 10.0), at(200.0, 10.0))
        assertTrue(
            "the tangent's roots coincide, so the inside interval is empty (read ${spans.size} span(s))",
            spans.isEmpty()
        )
    }

    /**
     * **A leg that enters through an endpoint circle and leaves through the strip's own side** — the two
     * halves of "within the width" meeting, which is where the caps are not decoration.
     *
     * The leg's start stands 15 m from the coast's own end, outside the band; the entry is on the circle
     * around that end, and the exit is where the leg's perpendicular distance reaches the width with its
     * projection well inside the segment. So the interval begins at the cap and ends at the slab, and the
     * metres before the entry — which the un-intersected slab absorbed — are not in the band at all.
     */
    @Test
    fun `a leg entering through a cap and leaving through the slab is the interval between`() {
        val world = worldOf(coast(100.0))
        val unit = unitOf(world)
        val from = at(-15.0, 0.0)
        val to = at(50.0, 12.0)
        val spans = unit.spansOf(from, to)
        assertEquals("one interval", 1, spans.size)
        val span = spans.single()
        // The circle around the coast's own end: |p(t) − (0, 0)| = 10 at t = (1950 − √1618000) / 8738.
        val capEntry = (1950.0 - sqrt(1_618_000.0)) / 8738.0
        assertEquals("the entry is the endpoint circle's own root", capEntry, span.from, 1e-6)
        // The slab: the leg's perpendicular distance is 12·t, so the width is reached at t = 10 / 12.
        assertEquals("and the exit is the slab's own boundary", 10.0 / 12.0, span.to, 1e-9)
        assertTrue(
            "the leg's own start stands outside the band, which the un-intersected slab absorbed",
            span.from > 0.05
        )
        assertOnTheLocus(world, from, to, span)
    }

    /**
     * **Two disjoint capsules whose over-wide slabs used to merge.** The coast is two pieces 200 m apart
     * and one leg runs beside both: the band's answer is two intervals with open water between them, while
     * the slab alone — every point of the leg standing within the width of *one* of the two lines — merged
     * them into one span covering the whole leg.
     */
    @Test
    fun `two capsules with open water between them stay two spans`() {
        val world = worldOf(coast(100.0), coast(100.0, fromM = 300.0))
        val unit = unitOf(world)
        val from = at(50.0, 5.0)
        val to = at(350.0, 5.0)
        val spans = unit.spansOf(from, to)
        assertEquals("the leg stands in two capsules, and the answer is two intervals", 2, spans.size)
        // The first capsule ends on its far endpoint's circle: |300·t − 50| = √75 from the leg's own x.
        assertEquals(
            "the first span ends on the far end of the first coast",
            1.0 / 6.0 + sqrt(75.0) / 300.0,
            spans[0].to,
            1e-6
        )
        // The second begins on the near endpoint's circle: |300·t − 250| = √75.
        assertEquals(
            "the second begins on the near end of the second coast",
            25.0 / 30.0 - sqrt(75.0) / 300.0,
            spans[1].from,
            1e-6
        )
        assertTrue(
            "and the open water between them is not reported: ${spans[1].from - spans[0].to} of the leg",
            spans[1].from - spans[0].to > 0.5
        )
        assertOnTheLocus(world, from, to, spans[0])
        assertOnTheLocus(world, from, to, spans[1])
    }

    /**
     * **The spans' own ends stand on the width's own locus** — the band's arithmetic checked against a
     * second metric rather than against itself.
     *
     * The spans are solved in the frame's metres while the world's own distance is the coastline index's
     * projection, so the two are different arithmetic answering the same question. A cap's root stands the
     * width from the endpoint; a slab's stands the width from the segment's line with its projection on
     * the segment, so its distance to the **segment** is the width there too.
     */
    private fun assertOnTheLocus(world: TautWorld, from: Pt, to: Pt, span: Span) {
        for (t in listOf(span.from, span.to)) {
            // **An end that is the leg's own end is not a crossing** (t = 0 or t = 1): the leg simply begins
            // or finishes inside the band, which is where its span begins or ends, and its own distance to
            // the coast is whatever the caller placed it at. Only an **interior** end is a claim about the
            // width's own locus, so only an interior end is checked — demanding otherwise had this helper
            // assert that a leg starting 5 m off the coast starts 10 m off it, which is the test being
            // wrong rather than the band.
            if (t <= 0.0 || t >= 1.0) continue
            val point = frame.latLng(Pt(from.x + (to.x - from.x) * t, from.y + (to.y - from.y) * t))
            val distance = world.distanceToCoastM(point.latitude, point.longitude)
            assertTrue(
                "the span's own end at t = $t stands ${"%.2f".format(distance)} m off the coast, " +
                    "against the band's own width of $widthM m",
                abs(distance - widthM) <= 1.0
            )
        }
    }
}
