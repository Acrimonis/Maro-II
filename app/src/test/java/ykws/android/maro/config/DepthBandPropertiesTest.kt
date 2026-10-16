package ykws.android.maro.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.Properties

/**
 * Holds `evolutive`'s depth band to its two homes, and its bounds to the arithmetic the law rests on.
 *
 * `route.evolutive.depth.reachM` and `route.evolutive.depth.priceSecPerM` are the values' homes and
 * [AppConfig]'s two accessors are their one copy, read at the app edge and clamped on the bounds beside
 * them. The keys are pinned in the shape `PrewarmTargetPropertiesTest` uses, so a key misspelled on one
 * side or a value moved on one side alone fails here; the floors are pinned as a **rule** rather than as
 * numbers, because a reach of zero is what makes the band inert and a gradient of zero is the band's own
 * off position — an accepted setting, so neither floor may drift above zero.
 *
 * The test CWD is the `app` module (the convention the `config/` property suites follow); `maro.repoDir`
 * is honoured first so the file is found from a repo-root run too.
 */
class DepthBandPropertiesTest {

    /** The reach shipped today — 25 m of depth above the gate's own minimum (the 2026-10-16 order). */
    private val expectedExtraM = 25

    /** The gradient shipped today — 0.05 s per metre of depth shortfall, per cell, before λ. */
    private val expectedSecPerM = "0.05"

    private fun shipped(relative: String): Properties {
        val file = System.getProperty("maro.repoDir")
            ?.let { File(it, "app/src/main/assets/$relative") }
            ?.takeIf { it.isFile }
            ?: File("src/main/assets/$relative")
        assumeTrue("$relative not found", file.isFile)
        return Properties().apply { file.inputStream().use { load(it) } }
    }

    @Test
    fun theBandShipsItsReachAndItsGradientAndTheAccessorsMirrorThem() {
        val props = shipped("maro.properties")

        assertEquals(
            "route.evolutive.depth.reachM must ship at 25",
            expectedExtraM.toDouble(),
            props.getProperty("route.evolutive.depth.reachM").trim().toDouble(),
            1e-9
        )
        assertEquals(
            "the app edge's accessor must mirror the reach",
            expectedExtraM.toDouble(),
            AppConfig.routeEvolutiveDepthReachM,
            1e-9
        )
        assertEquals(
            "route.evolutive.depth.priceSecPerM must ship at 0.05",
            expectedSecPerM,
            props.getProperty("route.evolutive.depth.priceSecPerM").trim()
        )
        assertEquals(
            "the app edge's accessor must mirror the gradient",
            expectedSecPerM.toDouble(),
            AppConfig.routeEvolutiveDepthPriceSecPerM,
            1e-9
        )
    }

    @Test
    fun bothFloorsStayAtZeroSoTheBandCanBeTurnedOff() {
        assertEquals(
            "a reach of zero must be an accepted setting: its outer edge then stands on the wall itself",
            0.0, AppConfig.ROUTE_EVOLUTIVE_DEPTH_BAND_EXTRA_M_MIN, 0.0
        )
        assertEquals(
            "and so must a gradient of zero — the band's own off position",
            0.0, AppConfig.ROUTE_EVOLUTIVE_DEPTH_BAND_SEC_PER_M_MIN, 0.0
        )
    }

    @Test
    fun bothCeilingsStayPositiveAndReachAboveTheGatesOwnRange() {
        assertTrue(
            "the reach's ceiling must allow a band wider than the gate's own ceiling",
            AppConfig.ROUTE_EVOLUTIVE_DEPTH_BAND_EXTRA_M_MAX > 50.0
        )
        assertTrue(
            "and the gradient's ceiling must leave room above the shipped 0.05",
            AppConfig.ROUTE_EVOLUTIVE_DEPTH_BAND_SEC_PER_M_MAX > AppConfig.routeEvolutiveDepthPriceSecPerM
        )
    }
}
