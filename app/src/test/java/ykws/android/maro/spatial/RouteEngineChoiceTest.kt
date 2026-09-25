package ykws.android.maro.spatial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.DepthSample
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.spatial.avoid.AvoidEdge
import ykws.android.maro.spatial.avoid.AvoidWorld

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
        assertTrue(
            "the dummy row builds the dummy, ignoring both providers",
            RouteEngineChoice.resolve("dummy").factory({ 15.0 }, { ChoiceWorld() }) is RouteDummyEngine
        )
        assertTrue(
            "the avoid row builds the avoid engine",
            RouteEngineChoice.resolve("avoid").factory({ 28.0 }, { ChoiceWorld() }) is RouteAvoidEngine
        )
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

/** A ready, water-everywhere world — the factory never invokes it at build time, only stores it. */
private class ChoiceWorld : AvoidWorld {
    override val coastlineReady: Boolean get() = true
    override val depthReady: Boolean get() = true
    override val regionBounds: BBox? get() = null
    override fun segmentsIn(box: BBox): List<AvoidEdge> = emptyList()
    override fun openCoastIn(box: BBox): List<List<LatLng>> = emptyList()
    override fun isWater(latitude: Double, longitude: Double): Boolean = true
    override fun distanceToCoastM(latitude: Double, longitude: Double): Double = Double.MAX_VALUE
    override fun depthAt(latitude: Double, longitude: Double): DepthSample = DepthSample.NONE
    override suspend fun load(): RouteEngineState = RouteEngineState.Ready
}
