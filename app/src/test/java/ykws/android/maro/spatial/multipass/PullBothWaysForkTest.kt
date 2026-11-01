package ykws.android.maro.spatial.multipass

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.SpatialOperations
import kotlin.math.abs
import kotlin.math.hypot

/**
 * **The two walks side by side draw the line a memo-less pull draws, and leave a memo that answers as fresh reads
 * do.** [MultipassPull.pullBothWays] runs its walks on [MarkMemo.fork]s joined back after; a memo is a cache, so
 * the line must be the one a call with no memo at all returns, it must be one of the two walks' own lines, and a
 * later stage reading the joined memo — the snap and final pull, the fine pass — must draw what it draws on a fresh
 * one. The last is where a wrong far-edge join (a short "none" kept over a longer one or over a found distance)
 * would show. Read over a shoal behind the measured gate, under berth laws whose proofs read the far edge, on
 * chains from the open water to the shoal's rim.
 */
class PullBothWaysForkTest {
    private val shoalNorth = 1300.0
    private val shoalEast = 1900.0
    private val raster = GateTestFixtures.raster(130, 190, 20.0) { n, e ->
        if (hypot(n - shoalNorth, e - shoalEast) < 80.0) 2.0 else 15.0
    }
    private val depth = GateTestFixtures.depthOf(raster)
    private val discCentre = GateTestFixtures.at(1100.0, 1500.0)

    private val laws = listOf(
        BerthLaw(marginM = 20.0, outerM = 50.0, secPerM = 0.1, floorM = 10.0, innerSecPerM = 0.5),
        BerthLaw(marginM = 20.0, outerM = 220.0, secPerM = 0.1, floorM = 10.0, innerSecPerM = 0.5),
        BerthLaw(marginM = 0.0, outerM = 500.0, secPerM = 0.1),
        null
    )

    @Test
    fun theForkedWalksDrawTheMemoLessLineAndLeaveAFaithfulMemo() {
        val random = java.util.Random(20261030L)
        var compared = 0
        for (law in laws) {
            val setup = setup(law)
            repeat(25) {
                val path = chain(random)
                val shared = MarkMemo()
                val line = runBlocking { MultipassPull.pullBothWays(setup, path, memo = shared) }
                val memoLess = runBlocking { MultipassPull.pullBothWays(setup, path, memo = null) }
                assertEquals("law $law: the forked memo moved the line", memoLess, line)

                val forward = MultipassPull.pull(setup, path, memo = null)
                val backward = MultipassPull.pull(setup.reversed(), path.reversed(), memo = null).reversed()
                assertTrue("law $law: the line is neither walk's", line == forward || line == backward)

                val later = chain(random)
                assertEquals(
                    "law $law: the joined memo answered a later pull otherwise than fresh reads",
                    MultipassPull.pull(setup, later, memo = MarkMemo()),
                    MultipassPull.pull(setup, later, memo = shared)
                )
                compared++
            }
        }
        assertEquals(100, compared)
    }

    /** A chain from the start to the aim, its inner points scattered up to 600 m off the straight, near the shoal too. */
    private fun chain(random: java.util.Random): List<LatLng> {
        val inner = List(10) { k ->
            val t = (k + 1) / 11.0
            GateTestFixtures.at(
                10.0 + t * 2580.0 + (random.nextDouble() - 0.5) * 1200.0,
                10.0 + t * 3780.0 + (random.nextDouble() - 0.5) * 1200.0
            )
        }
        return listOf(start) + inner + listOf(aim)
    }

    private val start = GateTestFixtures.at(10.0, 10.0)
    private val aim = GateTestFixtures.at(2590.0, 3790.0)

    private fun setup(law: BerthLaw?): PullSetup {
        val gateSource = depthGateSource(3.0) { depth(it.latitude, it.longitude) }
        val field = RouteCostField(listOf(
            gateSource,
            RouteCostSource.Soft(
                priceSec = { p -> if (SpatialOperations.haversine(p, discCentre) <= 400.0) 0.3 else 0.0 },
                tag = MultipassCellState.BAND,
                clearanceAt = { abs(SpatialOperations.haversine(it, discCentre) - 400.0) }
            )
        ))
        return PullSetup(
            marginM = 25.0, coarseStepM = 20.0, priceStepM = 100.0, field = field, start = start, aim = aim,
            gate = GateTestFixtures.gateOver(
                raster, hardM = law?.floorM ?: 20.0, preferredM = law?.marginM ?: 20.0, berthOuterM = law?.outerM ?: 0.0
            ),
            berth = law, berthLambda = if (law != null) 3.0 else 0.0, paceMps = 15.0
        )
    }
}
