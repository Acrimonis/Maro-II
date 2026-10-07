package ykws.android.maro.spatial.multipass

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * **The ladder's ranking** — the preference-aware rule that picks which landed rung delivers the intent
 * the user asked for: Fast the clock, Best the clock under the budget, Fun the zone seconds whatever the
 * clock; one shared tail settles a lead tie and a total tie falls to the rung nearest the preference.
 */
class RoutePassRankingTest {

    private fun rung(index: Int, share: Double, metres: Double, seconds: Double) =
        RoutePassRanking.RungCost(index, RoutePassRanking.PassCost(share, metres, seconds))

    /** **Fast** leads on the total time, whatever water the line crosses. */
    @Test
    fun fastPicksTheShortestClock() {
        val rungs = listOf(
            rung(RoutePreference.AROUND.index, share = 0.00, metres = 0.0, seconds = 1000.0),
            rung(RoutePreference.BEST.index, share = 0.30, metres = 900.0, seconds = 800.0),
            rung(RoutePreference.FAST.index, share = 0.40, metres = 1200.0, seconds = 700.0)
        )
        assertEquals(
            "the quickest line wins outright, whatever zone it crosses",
            2,
            RoutePassRanking.bestRungIndex(RoutePreference.FAST, 25, rungs)
        )
    }

    /**
     * **Best** gates the clock on the budget: among the rungs inside it the quickest leads, and where
     * none fits the smaller zone share does.
     */
    @Test
    fun bestGatesTheClockOnTheBudget() {
        val inside = listOf(
            rung(RoutePreference.AROUND.index, share = 0.05, metres = 200.0, seconds = 1100.0),
            rung(RoutePreference.BEST.index, share = 0.20, metres = 600.0, seconds = 900.0),
            rung(RoutePreference.FAST.index, share = 0.60, metres = 1500.0, seconds = 700.0)
        )
        assertEquals(
            "the quickest rung inside the gate beats the quicker one over it",
            1,
            RoutePassRanking.bestRungIndex(RoutePreference.BEST, 25, inside)
        )
        val noneInside = listOf(
            rung(RoutePreference.AROUND.index, share = 0.50, metres = 900.0, seconds = 1000.0),
            rung(RoutePreference.BEST.index, share = 0.70, metres = 1400.0, seconds = 800.0),
            rung(RoutePreference.FAST.index, share = 0.60, metres = 1200.0, seconds = 700.0)
        )
        assertEquals(
            "with no rung inside the gate the smaller share leads",
            0,
            RoutePassRanking.bestRungIndex(RoutePreference.BEST, 25, noneInside)
        )
    }

    /** **Fun** leads on the **absolute zone seconds**, and the clock never decides it. */
    @Test
    fun funPicksTheLeastZoneSecondsWhateverTheClock() {
        val rungs = listOf(
            // The smallest *share* is the "around" rung, but its trip is long enough to spend more
            // absolute seconds slowed than the quick one — which is what Fun leads on.
            rung(RoutePreference.AROUND.index, share = 0.02, metres = 200.0, seconds = 3000.0),
            rung(RoutePreference.BEST.index, share = 0.05, metres = 400.0, seconds = 1000.0),
            rung(RoutePreference.FAST.index, share = 0.04, metres = 300.0, seconds = 900.0)
        )
        assertEquals(
            "the least zone seconds wins, though another rung carries the smaller share",
            RoutePreference.FAST.index,
            rungs[RoutePassRanking.bestRungIndex(RoutePreference.AROUND, 25, rungs)!!].ladderIndex
        )
    }

    /** A total tie falls to the rung nearest the preference. */
    @Test
    fun aTotalTieFallsToTheRungNearestThePreference() {
        val rungs = listOf(
            rung(RoutePreference.AROUND.index, share = 0.10, metres = 300.0, seconds = 1000.0),
            rung(RoutePreference.FAST.index, share = 0.10, metres = 300.0, seconds = 1000.0)
        )
        assertEquals(
            "the preference's own rung takes a dead heat",
            0,
            RoutePassRanking.bestRungIndex(RoutePreference.AROUND, 25, rungs)
        )
        assertEquals(
            "and the other stop takes it when it is the preference",
            1,
            RoutePassRanking.bestRungIndex(RoutePreference.FAST, 25, rungs)
        )
    }

    /**
     * **The running best is a fold** — it moves as each rung lands, so the best of those landed is what
     * a surface reads at every terminal, and the last fold is the final winner.
     */
    @Test
    fun theRunningBestWalksForwardAsRungsLand() {
        val first = listOf(rung(RoutePreference.AROUND.index, 0.0, 0.0, 1000.0))
        assertEquals(
            "one rung is its own best",
            0,
            RoutePassRanking.bestRungIndex(RoutePreference.FAST, 25, first)
        )
        val second = first + rung(RoutePreference.BEST.index, 0.10, 300.0, 900.0)
        assertEquals(
            "a later, quicker landing takes the lead",
            1,
            RoutePassRanking.bestRungIndex(RoutePreference.FAST, 25, second)
        )
        val third = second + rung(RoutePreference.FAST.index, 0.30, 900.0, 950.0)
        assertEquals(
            "and the fold keeps the best of the three, not the last one",
            1,
            RoutePassRanking.bestRungIndex(RoutePreference.FAST, 25, third)
        )
    }

    /** **The λ→stop mapping** — a stored aversion snaps to one stop's index, λ and word. */
    @Test
    fun thePreferenceMappingSnapshotsAStoredAversion() {
        assertEquals(RoutePreference.FAST, RoutePreference.of(0.0))
        assertEquals(RoutePreference.FAST, RoutePreference.of(1.25))
        assertEquals(RoutePreference.BEST, RoutePreference.of(2.5))
        assertEquals(RoutePreference.BEST, RoutePreference.of(3.75))
        assertEquals(RoutePreference.AROUND, RoutePreference.of(5.0))

        assertEquals("the fast stop sits at λ 0", 0.0, RoutePreference.FAST.lambda, 1e-9)
        assertEquals("the best stop at 2.5", 2.5, RoutePreference.BEST.lambda, 1e-9)
        assertEquals("and the around stop at 5", 5.0, RoutePreference.AROUND.lambda, 1e-9)

        assertEquals("index 0 is the around stop", RoutePreference.AROUND, RoutePreference.ofIndex(0))
        assertEquals("index 1 is the best stop", RoutePreference.BEST, RoutePreference.ofIndex(1))
        assertEquals("index 2 is the fast stop", RoutePreference.FAST, RoutePreference.ofIndex(2))
    }
}
