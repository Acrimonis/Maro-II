package ykws.android.maro.data.model.markers

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.LatLng

/**
 * The two route-role flags are additive on the marker JSON: they round-trip as written, the two are
 * independent so either may stand alone, and a payload that predates them reads both as `false` —
 * which is why no migration and no schema bump are needed.
 */
class MarkerRouteRoleTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun marker(
        routeOrigin: Boolean = false,
        routeDestination: Boolean = false
    ) = UserMarker(
        id = "m",
        name = "m",
        geometry = MarkerGeometry.Pin(LatLng(43.0, 7.0)),
        routeOrigin = routeOrigin,
        routeDestination = routeDestination
    )

    @Test
    fun `both route roles round-trip through the marker JSON`() {
        val encoded = json.encodeToString(
            UserMarker.serializer(),
            marker(routeOrigin = true, routeDestination = true)
        )
        val restored = json.decodeFromString(UserMarker.serializer(), encoded)

        assertTrue(restored.routeOrigin)
        assertTrue(restored.routeDestination)
    }

    @Test
    fun `the roles are independent, so one may stand alone`() {
        val encoded = json.encodeToString(
            UserMarker.serializer(),
            marker(routeOrigin = true)
        )
        val restored = json.decodeFromString(UserMarker.serializer(), encoded)

        assertTrue(restored.routeOrigin)
        assertFalse(restored.routeDestination)
    }

    @Test
    fun `a payload that predates the keys reads both roles as false`() {
        val fields = json.parseToJsonElement(
            json.encodeToString(UserMarker.serializer(), marker())
        ).jsonObject
        val legacy = JsonObject(fields - "routeOrigin" - "routeDestination").toString()
        val restored = json.decodeFromString(UserMarker.serializer(), legacy)

        assertFalse(restored.routeOrigin)
        assertFalse(restored.routeDestination)
    }
}
