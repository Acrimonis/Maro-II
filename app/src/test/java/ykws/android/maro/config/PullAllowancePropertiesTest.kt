package ykws.android.maro.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.Properties

/**
 * Holds the settled lookback's price allowance to its two homes, and its bounds to the arithmetic the
 * drop rule rests on.
 *
 * `route.avoid.pull.allowanceShare` is the value's home and [AppConfig]'s accessor is its one copy, read
 * at the app edge and clamped on the bounds beside it. The key is pinned in the shape the other
 * `config/` property suites use, so a key misspelled on one side or a value moved on one side alone
 * fails here; the floor and the ceiling are pinned as **rules** — a share of zero restores the strict
 * `not dearer` test, and the ceiling stays a share rather than a free hand to cut priced water.
 *
 * The test CWD is the `app` module (the convention these suites follow); `maro.repoDir` is honoured
 * first so the file is found from a repo-root run too.
 */
class PullAllowancePropertiesTest {

    /** The share shipped today. */
    private val expectedShare = "0.1"

    private fun shipped(relative: String): Properties {
        val file = System.getProperty("maro.repoDir")
            ?.let { File(it, "app/src/main/assets/$relative") }
            ?.takeIf { it.isFile }
            ?: File("src/main/assets/$relative")
        assumeTrue("$relative not found", file.isFile)
        return Properties().apply { file.inputStream().use { load(it) } }
    }

    @Test
    fun theAllowanceShipsItsShareAndTheAccessorMirrorsIt() {
        val props = shipped("maro.properties")

        assertEquals(
            "route.avoid.pull.allowanceShare must ship at 0.02",
            expectedShare,
            props.getProperty("route.avoid.pull.allowanceShare").trim()
        )
        assertEquals(
            "the app edge's accessor must mirror the share",
            expectedShare.toDouble(),
            AppConfig.routeAvoidPullAllowanceShare,
            1e-9
        )
    }

    @Test
    fun theFloorRestoresTheStrictTest() {
        assertEquals(
            "a share of zero must be an accepted setting: it restores the strict `not dearer` test",
            0.0, AppConfig.ROUTE_AVOID_PULL_ALLOWANCE_SHARE_MIN, 0.0
        )
    }

    @Test
    fun theCeilingLeavesRoomAboveTheShippedShareButStaysAShare() {
        assertTrue(
            "the ceiling must leave room above the shipped share",
            AppConfig.ROUTE_AVOID_PULL_ALLOWANCE_SHARE_MAX > AppConfig.routeAvoidPullAllowanceShare
        )
        assertTrue(
            "and it must stay a share, never a free hand to cut priced water",
            AppConfig.ROUTE_AVOID_PULL_ALLOWANCE_SHARE_MAX < 1.0
        )
    }
}
