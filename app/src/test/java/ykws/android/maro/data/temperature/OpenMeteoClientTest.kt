package ykws.android.maro.data.temperature

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenMeteoClientTest {

    @Test
    fun `an air answer yields its current temperature`() {
        val body = """{"latitude":43.5,"current":{"time":"2026-11-11T10:00","temperature_2m":17.4}}"""
        assertEquals(17.4, OpenMeteoClient.parseCurrent(body, "temperature_2m")!!, 0.0)
    }

    @Test
    fun `a sea answer yields its current temperature`() {
        val body = """{"current":{"sea_surface_temperature":19.85}}"""
        assertEquals(19.85, OpenMeteoClient.parseCurrent(body, "sea_surface_temperature")!!, 0.0)
    }

    @Test
    fun `a JSON null is no value`() {
        val body = """{"current":{"sea_surface_temperature":null}}"""
        assertNull(OpenMeteoClient.parseCurrent(body, "sea_surface_temperature"))
    }

    @Test
    fun `a missing field or section is no value`() {
        assertNull(OpenMeteoClient.parseCurrent("""{"current":{}}""", "temperature_2m"))
        assertNull(OpenMeteoClient.parseCurrent("""{"error":true,"reason":"x"}""", "temperature_2m"))
    }

    @Test
    fun `malformed text is no value`() {
        assertNull(OpenMeteoClient.parseCurrent("<html>", "temperature_2m"))
        assertNull(OpenMeteoClient.parseCurrent("", "temperature_2m"))
    }

    @Test
    fun `a refused request is a failure carrying the status and the API reason, air or sea`() {
        val body = """{"error":true,"reason":"Cannot initialize model"}"""
        val client = OpenMeteoClient()
        assertEquals(FetchResult.Failure(400, "Cannot initialize model"), client.classify(TemperatureSource.AIR, 400, body))
        assertEquals(FetchResult.Failure(400, "Cannot initialize model"), client.classify(TemperatureSource.WATER, 400, body))
        assertEquals(FetchResult.Failure(404, null), client.classify(TemperatureSource.AIR, 404, "<html>"))
    }

    @Test
    fun `rate limits and server errors are failures, a good answer is a value`() {
        val client = OpenMeteoClient()
        assertEquals(FetchResult.Failure(429, null), client.classify(TemperatureSource.AIR, 429, ""))
        assertEquals(FetchResult.Failure(503, null), client.classify(TemperatureSource.WATER, 503, ""))
        assertEquals(
            FetchResult.Value(17.4),
            client.classify(TemperatureSource.AIR, 200, """{"current":{"temperature_2m":17.4}}""")
        )
        // Sea over land has no data, which is normal; air always has some, so its absence is a failure.
        assertEquals(FetchResult.Empty, client.classify(TemperatureSource.WATER, 200, """{"current":{"sea_surface_temperature":null}}"""))
        assertEquals(FetchResult.Failure(200, null), client.classify(TemperatureSource.AIR, 200, """{"current":{}}"""))
        // A page that is not Open-Meteo's JSON (a captive portal) must not be remembered as "no sea here".
        assertEquals(FetchResult.Failure(200, null), client.classify(TemperatureSource.WATER, 200, "<html>login</html>"))
        assertEquals(FetchResult.Failure(200, null), client.classify(TemperatureSource.WATER, 200, """{"hourly":{}}"""))
        assertEquals(true, TemperatureProblem(TemperatureSource.AIR, 200, null).answeredWithoutValue)
        assertEquals(false, TemperatureProblem(TemperatureSource.AIR, 503, null).answeredWithoutValue)
        assertEquals(false, TemperatureProblem(null, null, null).answeredWithoutValue)
    }

    @Test
    fun `the air request names the AROME model and the water request does not`() {
        val client = OpenMeteoClient()
        assertEquals(
            "https://api.open-meteo.com/v1/forecast?latitude=43.56960&longitude=7.13130" +
                "&current=temperature_2m&models=meteofrance_arome_seamless",
            client.urlFor(TemperatureSource.AIR, 43.5696, 7.1313)
        )
        assertEquals(
            "https://marine-api.open-meteo.com/v1/marine?latitude=43.56960&longitude=7.13130" +
                "&current=sea_surface_temperature",
            client.urlFor(TemperatureSource.WATER, 43.5696, 7.1313)
        )
    }
}
