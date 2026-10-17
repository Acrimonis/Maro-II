package ykws.android.maro.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.Properties

/**
 * Holds `evolutive`'s gate berth to its homes, and its bounds to the arithmetic the law rests on.
 *
 * `route.evolutive.berth.outerM` and `route.evolutive.berth.secPerM` are the values' homes and
 * [AppConfig]'s two accessors are their one copy, read at the app edge and clamped on the bounds beside
 * them. The berth's inner end is the gate's own margin (`route.avoid.depthGate.marginM`), so it has no
 * key of its own. The keys are pinned in the shape `PrewarmTargetPropertiesTest` uses; the floors are
 * pinned as a **rule** rather than as numbers, because an outer edge of zero and a rate of zero are the
 * berth's own off positions.
 *
 * The test CWD is the `app` module (the convention the `config/` property suites follow); `maro.repoDir`
 * is honoured first so the file is found from a repo-root run too.
 */
class BerthPropertiesTest {

    /** The outer edge shipped today — 50 m off the gate edge, where the price ends. */
    private val expectedOuterM = 50

    /** The full rate shipped today — 0.1 s per metre of water at the margin, before λ. */
    private val expectedSecPerM = "0.1"

    private fun shipped(relative: String): Properties {
        val file = System.getProperty("maro.repoDir")
            ?.let { File(it, "app/src/main/assets/$relative") }
            ?.takeIf { it.isFile }
            ?: File("src/main/assets/$relative")
        assumeTrue("$relative not found", file.isFile)
        return Properties().apply { file.inputStream().use { load(it) } }
    }

    @Test
    fun theBerthShipsItsOuterEdgeAndItsRateAndTheAccessorsMirrorThem() {
        val props = shipped("maro.properties")

        assertEquals(
            "route.evolutive.berth.outerM must ship at 50",
            expectedOuterM.toDouble(),
            props.getProperty("route.evolutive.berth.outerM").trim().toDouble(),
            1e-9
        )
        assertEquals(
            "the app edge's accessor must mirror the outer edge",
            expectedOuterM.toDouble(),
            AppConfig.routeEvolutiveBerthOuterM,
            1e-9
        )
        assertEquals(
            "route.evolutive.berth.secPerM must ship at 0.1",
            expectedSecPerM,
            props.getProperty("route.evolutive.berth.secPerM").trim()
        )
        assertEquals(
            "the app edge's accessor must mirror the rate",
            expectedSecPerM.toDouble(),
            AppConfig.routeEvolutiveBerthSecPerM,
            1e-9
        )
    }

    @Test
    fun theBerthHasNoInnerEdgeKeyOfItsOwn() {
        val props = shipped("maro.properties")

        assertEquals(
            "the berth's inner end is the gate's own margin, so no inner-edge key exists",
            null, props.getProperty("route.evolutive.berth.marginM")
        )
    }

    @Test
    fun bothFloorsStayAtZeroSoTheBerthCanBeTurnedOff() {
        assertEquals(
            "an outer edge of zero must be an accepted setting: nothing can stand inside it",
            0.0, AppConfig.ROUTE_EVOLUTIVE_BERTH_OUTER_M_MIN, 0.0
        )
        assertEquals(
            "and so must a rate of zero — the berth's own off position",
            0.0, AppConfig.ROUTE_EVOLUTIVE_BERTH_SEC_PER_M_MIN, 0.0
        )
    }

    @Test
    fun bothCeilingsStayPositiveAndAboveTheShippedValues() {
        assertTrue(
            "the outer edge's ceiling must allow a berth wider than the shipped 50 m",
            AppConfig.ROUTE_EVOLUTIVE_BERTH_OUTER_M_MAX > AppConfig.routeEvolutiveBerthOuterM
        )
        assertTrue(
            "and the rate's ceiling must leave room above the shipped 0.1",
            AppConfig.ROUTE_EVOLUTIVE_BERTH_SEC_PER_M_MAX > AppConfig.routeEvolutiveBerthSecPerM
        )
    }
}
