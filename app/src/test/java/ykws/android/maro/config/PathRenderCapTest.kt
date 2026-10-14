package ykws.android.maro.config

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The render count's one home (2026-10-14): [AppConfig.pathRenderCap] resolves the All sentinel to an
 * uncapped limit and clamps every other value into the countable 0..[AppConfig.PATH_COUNT_MAX] band.
 * Pure — no device, no IO, no properties file.
 */
class PathRenderCapTest {

    @Test
    fun allSentinel_resolvesToUncapped() {
        assertEquals(Int.MAX_VALUE, AppConfig.pathRenderCap(AppConfig.PATH_COUNT_ALL))
    }

    @Test
    fun countableValues_passThroughUnchanged() {
        assertEquals(AppConfig.PATH_COUNT_MAX, AppConfig.pathRenderCap(AppConfig.PATH_COUNT_MAX))
        assertEquals(30, AppConfig.pathRenderCap(30))
        assertEquals(5, AppConfig.pathRenderCap(5))
        assertEquals(0, AppConfig.pathRenderCap(0))
    }

    @Test
    fun outOfRangeValues_clampIntoTheCountableBand() {
        assertEquals(0, AppConfig.pathRenderCap(-5))
        assertEquals(AppConfig.PATH_COUNT_MAX, AppConfig.pathRenderCap(AppConfig.PATH_COUNT_ALL + 1))
        assertEquals(AppConfig.PATH_COUNT_MAX, AppConfig.pathRenderCap(1_000))
    }

    @Test
    fun sentinelSitsOnePastTheBand() {
        // One home checked against itself: the sentinel just above the countable top is what makes
        // 0..30 a count and 31 "All" — and it is the sliders' own top position, 32 stops wide.
        assertEquals(AppConfig.PATH_COUNT_MAX + 1, AppConfig.PATH_COUNT_ALL)
        assertEquals(32, AppConfig.PATH_COUNT_ALL + 1)
    }
}
