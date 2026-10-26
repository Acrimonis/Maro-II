package ykws.android.maro.spatial.multipass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.config.AppConfig
import ykws.android.maro.config.VerdictDial

/**
 * **The ladder's ranking, one sum** — which of the landed rungs takes the crown:
 * `trip time + weight × slow-water seconds`, lowest wins, a flip guarded by the tie tolerance, and the
 * dial's top stop weighing the slow seconds alone with the clock breaking an exact tie.
 *
 * **The ranks are distinct** (2026-10-15): the tolerance draws the groups it holds level, the chain breaks
 * the tie inside one — the exact measure, then the fewer slow seconds, then the shorter time, then the
 * arrival order — and the crown is always the head of its own group, so rank 1 is the crowned rung and no
 * two settled rungs share a rank.
 *
 * The **4x** stop is the shipped default read arithmetically: `route.avoid.slowWater.weight` ships at 4,
 * so the crown this file pins is the one the app answers with out of the box.
 */
class RoutePassRankingTest {

    /** The shipped tie tolerance (score seconds) — [AppConfig.routeAvoidLadderTieToleranceSec]'s value. */
    private val toleranceSec = 30.0

    private fun line(durationSec: Double, slowWaterSec: Double) =
        RoutePassRanking.PassCost(slowWaterSec = slowWaterSec, durationSec = durationSec)

    /**
     * **The three rungs of a coastal leg** — the around rung the longest clock and the least slow water,
     * the fast rung the shortest clock and the most, and the balanced one between the two. Their order
     * never changes: a dearer stop is what moves the crown, never the fixture.
     */
    private val coastal = listOf(
        line(durationSec = 1100.0, slowWaterSec = 40.0),
        line(durationSec = 900.0, slowWaterSec = 120.0),
        line(durationSec = 800.0, slowWaterSec = 150.0)
    )

    /** **1x is the clock's own lead** — the quickest line takes the crown, as today's Fast seat does. */
    @Test
    fun atOneTimesTheQuickestLineTakesTheCrown() {
        assertEquals(
            "the fastest line wins, though it spends the most seconds under a limit",
            2,
            RoutePassRanking.bestRungIndex(1.0, toleranceSec, coastal)
        )
    }

    /** **A dearer stop buys the cleaner line** — at 4x the slow water outweighs the clock's lead. */
    @Test
    fun aDearerStopCrownsTheCleanerLine() {
        assertEquals(
            "the least slow water wins once each second of it costs four",
            0,
            RoutePassRanking.bestRungIndex(4.0, toleranceSec, coastal)
        )
    }

    /** **The ignore-time stop reads the slow seconds alone**, the clock deciding nothing. */
    @Test
    fun atIgnoreTimeTheFewestSlowSecondsTakeTheCrown() {
        val lines = listOf(
            line(durationSec = 1000.0, slowWaterSec = 40.0),
            line(durationSec = 1100.0, slowWaterSec = 90.0),
            line(durationSec = 700.0, slowWaterSec = 100.0)
        )
        assertEquals(
            "4x crowns the quickest line of the three, whose slow water is the dearest",
            2,
            RoutePassRanking.bestRungIndex(4.0, toleranceSec, lines)
        )
        assertEquals(
            "and ignore time crowns the fewest minutes instead, whatever the clock",
            0,
            RoutePassRanking.bestRungIndex(VerdictDial.IGNORE_TIME.weight, toleranceSec, lines)
        )
    }

    /** **Only a dead heat in the slow seconds lets the clock decide** the ignore-time stop. */
    @Test
    fun atIgnoreTimeAnExactTieInSlowSecondsFallsToTheClock() {
        val lines = listOf(
            line(durationSec = 900.0, slowWaterSec = 100.0),
            line(durationSec = 850.0, slowWaterSec = 100.0)
        )
        assertEquals(
            "the shorter clock takes a dead heat in slow water",
            1,
            RoutePassRanking.bestRungIndex(VerdictDial.IGNORE_TIME.weight, toleranceSec, lines)
        )
        assertEquals(
            "and it takes it landing first as well, since the longer clock never improves on it",
            0,
            RoutePassRanking.bestRungIndex(VerdictDial.IGNORE_TIME.weight, toleranceSec, lines.reversed())
        )
    }

    /**
     * **The tolerance is what stops the crown flapping**: a rung ahead of the incumbent by less than it
     * leaves the incumbent standing, whether the flip is read on the sum or on the slow seconds alone.
     */
    @Test
    fun aDifferenceUnderTheToleranceDoesNotFlipTheCrown() {
        val incumbent = line(durationSec = 900.0, slowWaterSec = 40.0)
        val justBetter = listOf(incumbent, line(durationSec = 900.0, slowWaterSec = 35.0))
        assertEquals(
            "five score seconds is under the tolerance, so the crown does not move",
            0,
            RoutePassRanking.bestRungIndex(1.0, toleranceSec, justBetter)
        )
        val clearlyBetter = listOf(incumbent, line(durationSec = 900.0, slowWaterSec = 0.0))
        assertEquals(
            "forty score seconds is over it, so it does",
            1,
            RoutePassRanking.bestRungIndex(1.0, toleranceSec, clearlyBetter)
        )
        val slowIncumbent = line(durationSec = 900.0, slowWaterSec = 100.0)
        val slowJustBetter = listOf(slowIncumbent, line(durationSec = 900.0, slowWaterSec = 80.0))
        assertEquals(
            "and the ignore-time stop reads the same tolerance on its own figure",
            0,
            RoutePassRanking.bestRungIndex(VerdictDial.IGNORE_TIME.weight, toleranceSec, slowJustBetter)
        )
        val slowClearlyBetter = listOf(slowIncumbent, line(durationSec = 900.0, slowWaterSec = 60.0))
        assertEquals(
            "while a wider lead takes the crown there too",
            1,
            RoutePassRanking.bestRungIndex(VerdictDial.IGNORE_TIME.weight, toleranceSec, slowClearlyBetter)
        )
    }

    /**
     * **The running best is a fold** — it moves as each rung lands, so the best of those landed is what
     * a surface reads at every terminal, and the last fold is the final winner.
     */
    @Test
    fun theRunningBestWalksForwardAsRungsLand() {
        val first = listOf(line(durationSec = 1000.0, slowWaterSec = 40.0))
        assertEquals(
            "one rung is its own best",
            0,
            RoutePassRanking.bestRungIndex(1.0, toleranceSec, first)
        )
        val second = first + line(durationSec = 800.0, slowWaterSec = 60.0)
        assertEquals(
            "a later, clearly quicker landing takes the lead",
            1,
            RoutePassRanking.bestRungIndex(1.0, toleranceSec, second)
        )
        val third = second + line(durationSec = 700.0, slowWaterSec = 160.0)
        assertEquals(
            "and the fold keeps the best of the three, not the last one",
            1,
            RoutePassRanking.bestRungIndex(1.0, toleranceSec, third)
        )
    }

    /** Nothing landed ranks nothing. */
    @Test
    fun anEmptySetRanksNothing() {
        assertNull(RoutePassRanking.bestRungIndex(1.0, toleranceSec, emptyList()))
    }

    /**
     * **The weight 0 stop crowns the genuinely fastest line, and the dial can reach it.** At 0 the slow
     * seconds weigh nothing at all, so the quickest clock wins even with the most slow water under it —
     * the dial's own *Fastest* end, which the retired bridge (its floor at 1x) could not reach.
     */
    @Test
    fun atZeroWeightTheGenuinelyFastestLineTakesTheCrown() {
        assertEquals(
            "with no weight at all the clock decides alone, whatever slow water the line crosses",
            2,
            RoutePassRanking.bestRungIndex(0.0, toleranceSec, coastal)
        )
        assertEquals("and the weight the stop carries is 0", 0.0, VerdictDial.FASTEST.weight!!, 1e-9)
        assertEquals("the dial's own track offers it as its first stop", 0, VerdictDial.FASTEST.index)
        assertEquals("which is the index a stored stop reads", VerdictDial.FASTEST, VerdictDial.ofIndex(0))
    }

    /** **The default weight stands at the shipped stop** — the box the crown is answered with unset. */
    @Test
    fun theShippedWeightStandsAtItsOwnStop() {
        assertEquals("the shipped weight is 4", 4.0, AppConfig.routeAvoidSlowWaterWeight, 1e-9)
        assertEquals(
            "and reads as the dial's fourth numbered stop",
            VerdictDial.FOUR,
            VerdictDial.ofWeight(AppConfig.routeAvoidSlowWaterWeight)
        )
    }

    /**
     * **The order and the crown are one seat** — [RoutePassRanking.ranks] reads the very sum and tolerance
     * [RoutePassRanking.bestRungIndex] weighs by, so the crowned rung is always the rank-1 one.
     */
    @Test
    fun theRanksReadTheSameSumAndTheCrownIsAlwaysRankOne() {
        assertEquals(
            "at 1x the quickest line leads, then the middle, then the dearest of the three",
            listOf(3, 2, 1),
            RoutePassRanking.ranks(1.0, toleranceSec, coastal)
        )
        assertEquals(
            "and the crowned rung is the rank-1 one",
            1,
            RoutePassRanking.ranks(1.0, toleranceSec, coastal)[
                RoutePassRanking.bestRungIndex(1.0, toleranceSec, coastal)!!
            ]
        )
        assertEquals(
            "at 4x the cleanest line leads and the two level ones take 2 and 3, the chain settling them",
            listOf(1, 2, 3),
            RoutePassRanking.ranks(4.0, toleranceSec, coastal)
        )
        assertEquals(
            "and at ignore time the slow seconds alone rank, the around rung first and the rest on their own",
            listOf(1, 2, 3),
            RoutePassRanking.ranks(VerdictDial.IGNORE_TIME.weight, toleranceSec, coastal)
        )
    }

    /**
     * **A tie inside the tolerance breaks by the chain, and the ranks read `1, 2, 3`** (2026-10-15) — the
     * first two stand ten score seconds apart, level to the tolerance, so the chain's first step settles
     * them on the exact measure (940 against 950) while the rung the tolerance clearly leaves behind reads
     * the last rank.
     */
    @Test
    fun rungsInsideTheToleranceReadRanksOfTheirOwn() {
        val lines = listOf(
            line(durationSec = 900.0, slowWaterSec = 40.0),
            line(durationSec = 905.0, slowWaterSec = 45.0),
            line(durationSec = 900.0, slowWaterSec = 100.0)
        )
        assertEquals(
            "the chain settles the level pair and the outsider takes the last rank",
            listOf(1, 2, 3),
            RoutePassRanking.ranks(1.0, toleranceSec, lines)
        )
        assertEquals(
            "which is the crown the same set was folded to, rank 1 always",
            1,
            RoutePassRanking.ranks(1.0, toleranceSec, lines)[
                RoutePassRanking.bestRungIndex(1.0, toleranceSec, lines)!!
            ]
        )
    }

    /**
     * **The tolerance still owns the crown, and the chain reads only behind it** (2026-10-15): a challenger
     * whose own score is strictly lower — five score seconds under the tolerance — does not take the crown,
     * and reads rank 2 while the incumbent it failed to unseat reads rank 1, so the mark a surface draws on
     * rank 1 is the engine's own winner and never the chain's favourite. A lead wider than the tolerance
     * flips both, the challenger carrying rank 1.
     */
    @Test
    fun theToleranceGovernsTheCrownWhileTheChainRanksBehindIt() {
        val lines = listOf(
            line(durationSec = 900.0, slowWaterSec = 25.0),
            line(durationSec = 900.0, slowWaterSec = 0.0),
            line(durationSec = 1100.0, slowWaterSec = 25.0)
        )
        assertEquals(
            "the sub-tolerance challenger's lower score leaves the crown where it stands",
            0,
            RoutePassRanking.bestRungIndex(1.0, toleranceSec, lines)
        )
        assertEquals(
            "yet every settled rung reads a rank of its own, the crown heading its group",
            listOf(1, 2, 3),
            RoutePassRanking.ranks(1.0, toleranceSec, lines)
        )
        val flipped = listOf(
            line(durationSec = 950.0, slowWaterSec = 25.0),
            line(durationSec = 900.0, slowWaterSec = 0.0)
        )
        assertEquals(
            "a lead over the tolerance does flip it",
            1,
            RoutePassRanking.bestRungIndex(1.0, toleranceSec, flipped)
        )
        assertEquals(
            "and the challenger carries rank 1 with it, the beaten incumbent second",
            listOf(2, 1),
            RoutePassRanking.ranks(1.0, toleranceSec, flipped)
        )
    }

    /**
     * **The fold reads the ladder's order, never the landing order.** Three lines 25 and 20 score seconds
     * apart, inside the 30 s tolerance pairwise but not end to end: folded as they were declared, the
     * fastest clearly beats the around rung and takes the crown; folded in the order the threads finished
     * — the balanced rung first — the balanced rung held it against both, and the crown was a matter of
     * timing. [RoutePassRanking.settle] answers the same crown and the same ranks from either landing order.
     */
    @Test
    fun theSettledFoldCrownsTheSameRungFromEitherLandingOrder() {
        val around = line(durationSec = 1000.0, slowWaterSec = 0.0)
        val balanced = line(durationSec = 975.0, slowWaterSec = 0.0)
        val fast = line(durationSec = 955.0, slowWaterSec = 0.0)
        // The fold this seat replaces: in landing order the balanced rung keeps the crown it took first.
        assertEquals(
            "the plain fold crowns the first to land when the rest stay inside the tolerance of it",
            0,
            RoutePassRanking.bestRungIndex(1.0, toleranceSec, listOf(balanced, around, fast))
        )
        assertEquals(
            "and another rung when the landing order differs",
            2,
            RoutePassRanking.bestRungIndex(1.0, toleranceSec, listOf(around, balanced, fast))
        )
        val declared = RoutePassRanking.settle(1.0, toleranceSec, listOf(0, 1, 2), listOf(around, balanced, fast))
        val landedOtherwise = RoutePassRanking.settle(1.0, toleranceSec, listOf(1, 0, 2), listOf(balanced, around, fast))
        assertEquals("settled in ladder order, the fast rung takes the crown", 2, declared!!.winner)
        assertEquals("from the other landing order too, as the position of that same rung", 2, landedOtherwise!!.winner)
        assertEquals("with the ranks of the one fold", listOf(3, 2, 1), declared.ranks)
        assertEquals(
            "read back into the other order's positions",
            listOf(2, 3, 1),
            landedOtherwise.ranks
        )
        assertEquals("the crown is rank 1 either way", 1, landedOtherwise.ranks[landedOtherwise.winner])
    }

    /** Nothing landed ranks nothing. */
    @Test
    fun anEmptySetHasNoRanks() {
        assertTrue(RoutePassRanking.ranks(1.0, toleranceSec, emptyList()).isEmpty())
    }
}
