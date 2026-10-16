package ykws.android.maro.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.Properties

/**
 * Holds the warm-up's growth target to its one home, and its top bound to the rule that a reserve survives.
 *
 * `route.prewarm.targetPct` is the value's home and [AppConfig]'s accessor is its one copy, read at the app
 * edge and clamped on the two bounds beside it. The key is pinned in the shape
 * `FineTileStoreCeilingPropertiesTest` uses, so a key misspelled on one side or a value moved on one side
 * alone fails here; the top bound is pinned as a **rule** rather than as a number, because a target of 100
 * would spend the whole ceiling and leave nothing of the reserve the share exists for.
 *
 * The test CWD is the `app` module (the convention the `config/` property suites follow); `maro.repoDir` is
 * honoured first so the file is found from a repo-root run too.
 */
class PrewarmTargetPropertiesTest {

    /** The share shipped today — 80 %, settled 2026-10-15 (§11 of the warm-area plan). */
    private val expectedPct = 80

    private fun shipped(relative: String): Properties {
        val file = System.getProperty("maro.repoDir")
            ?.let { File(it, "app/src/main/assets/$relative") }
            ?.takeIf { it.isFile }
            ?: File("src/main/assets/$relative")
        assumeTrue("$relative not found", file.isFile)
        return Properties().apply { file.inputStream().use { load(it) } }
    }

    @Test
    fun theGrowthTargetShipsAtEightyPerCentAndItsAccessorMirrorsIt() {
        val props = shipped("maro.properties")

        assertEquals(
            "route.prewarm.targetPct must ship at 80",
            expectedPct,
            props.getProperty("route.prewarm.targetPct").trim().toInt()
        )
        assertEquals(
            "the app edge's accessor must mirror the key",
            expectedPct,
            AppConfig.routePrewarmTargetPct
        )
    }

    @Test
    fun theTopBoundKeepsAReserveAndTheFloorKeepsGrowthWorthWhile() {
        assertTrue(
            "the highest accepted target must stay below 100, or the walk would spend the whole ceiling",
            AppConfig.ROUTE_PREWARM_TARGET_PCT_MAX < 100
        )
        assertTrue(
            "and the floor must stay above zero, so a pass still prepares water",
            AppConfig.ROUTE_PREWARM_TARGET_PCT_MIN > 0
        )
    }
}
