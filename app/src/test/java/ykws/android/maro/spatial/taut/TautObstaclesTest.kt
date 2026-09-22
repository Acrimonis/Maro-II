package ykws.android.maro.spatial.taut

import kotlin.math.PI
import kotlin.math.cos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.BoundingBox
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.spatial.SpatialOperations

/**
 * **The judge and its early exit — the fit test's own two questions, counted apart** (walk item 5).
 *
 * The lever that item built is invisible in the line: [`TautObstacles.curveOnWater`] asks each point
 * **with the chord it leaves**, so a wall refuses a candidate at that chord rather than after the whole
 * point walk, and the answer cannot move because the predicate is an **AND** of pure questions — a curve
 * is refused by the same question whichever end of the walk reaches it first. What *does* move is **how
 * many questions a refused curve pays for**, and that count is what these readings pin, on a corridor
 * small enough to hold the wall by hand.
 *
 * **Both halves are needed, and each is the other's control.** The refused curve pins the early exit: the
 * walk asks its first point and then the chord leaving it, so the refusal costs **one** question of each
 * kind, where a revert to the old shape — every point first, the chords after — asks all three points
 * before the wall is ever consulted. The clean curve keeps that reading honest: it shows the predicate
 * still able to answer **yes** with every question asked, so the count above is a reading of a walk that
 * ended early rather than of a judge that gave up.
 *
 * The counters are read here because on a synthetic corridor the **count is the whole of the change**;
 * what guards the change on real water is the acceptance pair's six metrics and the drawn line, which is
 * the harness's own reading and not this file's.
 */
class TautObstaclesTest {

    private val originLat = 43.55
    private val originLon = 7.12
    private val berthM = 25.0
    private val shoreOffsetM = 50.0

    private val metresPerDegreeLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
    private val metresPerDegreeLon = metresPerDegreeLat * cos(originLat * PI / 180.0)

    private fun latLng(eastM: Double, northM: Double): LatLng = LatLng(
        originLat + northM / metresPerDegreeLat,
        originLon + eastM / metresPerDegreeLon
    )

    private fun at(eastM: Double, northM: Double): RoutePoint {
        val point = latLng(eastM, northM)
        return RoutePoint(point.latitude, point.longitude)
    }

    private fun rectM(westM: Double, southM: Double, eastM: Double, northM: Double): List<LatLng> = listOf(
        latLng(westM, southM),
        latLng(eastM, southM),
        latLng(eastM, northM),
        latLng(westM, northM),
        latLng(westM, southM)
    )

    /**
     * **The wall the curve's first chord crosses** — a rectangular spit standing between the first two
     * points, with every point of both curves kept well clear of it.
     *
     * It is wide and long enough to survive the harvest's own clipping and one-sided simplification, and
     * its nearest water is 180 m from the first point, so no point of either curve is refused by the shore
     * offset either — the only question the curves can be refused by is the one this file reads.
     */
    private fun spit(): List<LatLng> = rectM(180.0, -300.0, 220.0, 300.0)

    /** A box holding the curve and the spit whole, with the frame's own room around both. */
    private fun boxAround(points: List<RoutePoint>): BoundingBox {
        val marginDeg = 2_000.0 / metresPerDegreeLat
        return BoundingBox(
            points.minOf { it.latitude } - marginDeg,
            points.maxOf { it.latitude } + marginDeg,
            points.minOf { it.longitude } - marginDeg,
            points.maxOf { it.longitude } + marginDeg
        )
    }

    /**
     * **A fresh harvest for one reading**, so the counters the assertions name start at zero.
     *
     * The harvest itself never asks the judge's questions — it probes the world directly — so an instance
     * built and then handed one curve reports exactly that curve's own questions, which is why the premise
     * below is read on an instance of its own rather than on the counted one.
     */
    private fun obstaclesFor(points: List<RoutePoint>): TautObstacles {
        val world = TautTestWorld(
            land = listOf(spit()),
            depthBox = BoundingBox(43.4000, 43.7000, 7.0000, 7.2600),
            coastalBandWidthM = 0.0
        )
        return TautObstacles.harvest(world, boxAround(points), berthM, shoreOffsetM)
    }

    /**
     * **A refused curve asks its blocked chord and stops** — the lever itself.
     *
     * **The revert this catches:** asking every point before the chords, as `curveOnWater` did before walk
     * item 5. That shape answers the same boolean — it must, the predicate being an AND — but it asks the
     * curve's **three** points before the wall is ever consulted, so `pointsAsked` reads 3 here where the
     * early exit reads 1, and the assertion fails.
     */
    @Test
    fun aRefusedCurveStopsAtItsFirstBlockedChord() {
        val curve = listOf(at(0.0, 0.0), at(400.0, 0.0), at(800.0, 0.0))

        // The premise, read on its own instance: the wall crosses the curve's first chord, and every point
        // of the curve is water the boat may stand on — so the refusal below is the wall's and nothing
        // else's, and the point walk it cuts short is a walk that had somewhere to go.
        val premise = obstaclesFor(curve)
        assertTrue(
            "the spit must cross the curve's first chord",
            premise.curveBlocked(curve[0], curve[1])
        )
        for (point in curve) {
            assertTrue(
                "every point of the curve must be on water",
                premise.traversable(point.latitude, point.longitude)
            )
        }

        val obstacles = obstaclesFor(curve)
        assertFalse("a curve whose own chord is blocked is refused", obstacles.curveOnWater(curve))
        assertEquals(
            "the chord walk ends at the first blocked chord",
            1,
            obstacles.chordsAsked
        )
        assertEquals(
            "and the walk never reaches the points past it — the revert to a full point walk reads 3",
            1,
            obstacles.pointsAsked
        )
    }

    /**
     * **A clean curve asks every point and every chord** — the control, which is what makes the reading
     * above a walk that ended early rather than a judge that answered nothing.
     *
     * **The revert this catches:** the same one, from the other side. A shape that asked all the chords
     * before any point would answer `true` here too but would read 2 chords and 3 points against a *walk
     * that asks its chords as it goes* only where the curve is clean — so the pair of counts below is a
     * reading of the order, and either a chord-first or a point-first shape moves one of them.
     */
    @Test
    fun aCleanCurveAsksEveryPointAndEveryChord() {
        val curve = listOf(at(0.0, 500.0), at(400.0, 500.0), at(800.0, 500.0))

        val obstacles = obstaclesFor(curve)
        assertTrue("a curve clear of the spit is answered", obstacles.curveOnWater(curve))
        assertEquals("every point asked", 3, obstacles.pointsAsked)
        assertEquals("every chord asked", 2, obstacles.chordsAsked)
    }
}
