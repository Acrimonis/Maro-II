package ykws.android.maro.spatial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.config.AppConfig

/**
 * The registry's own contract: every shipped id resolves to its own row, each row builds the engine it
 * names, and an id nothing claims falls back to the shipped default rather than leaving the app with no
 * engine to arm.
 */
class RouteEngineChoiceTest {

    @Test
    fun everyShippedIdResolvesToItsOwnRow() {
        for (choice in RouteEngineChoice.all) {
            assertEquals(
                "each shipped id answers its own row",
                choice,
                RouteEngineChoice.resolve(choice.id)
            )
        }
    }

    @Test
    fun eachRowBuildsTheEngineItNames() {
        assertTrue("the dummy row builds the dummy", RouteEngineChoice.resolve("dummy").factory { 15.0 } is RouteDummyEngine)
        assertTrue("the avoid row builds the avoid engine", RouteEngineChoice.resolve("avoid").factory { 28.0 } is RouteAvoidEngine)
    }

    @Test
    fun anUnknownIdFallsBackToTheShippedDefault() {
        assertEquals(
            "an id nothing claims answers the property's default",
            AppConfig.routeEngineId,
            RouteEngineChoice.resolve("nothing-claims-me").id
        )
    }

    @Test
    fun theShippedDefaultIsOneOfTheRows() {
        assertTrue(
            "the default the registry falls back to is a shipped row",
            RouteEngineChoice.all.any { it.id == AppConfig.routeEngineId }
        )
    }
}
