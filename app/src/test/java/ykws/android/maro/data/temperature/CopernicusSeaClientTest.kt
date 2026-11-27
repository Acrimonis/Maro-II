package ykws.android.maro.data.temperature

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CopernicusSeaClientTest {

    private val client = CopernicusSeaClient()

    @Test
    fun `the point off Nice maps to the tile and pixel the service answered for`() {
        // Checked against the live service on 2026-11-27: this request answered 290.81 K at 43.679 N, 7.304 E.
        assertEquals(
            "https://wmts.marine.copernicus.eu/teroWmts?SERVICE=WMTS&VERSION=1.0.0&REQUEST=GetFeatureInfo" +
                "&LAYER=SST_MED_SST_L4_NRT_OBSERVATIONS_010_004/SST_MED_SST_L4_NRT_OBSERVATIONS_010_004_c_V2_202311" +
                "/analysed_sst&STYLE=&TILEMATRIXSET=EPSG:4326&TILEMATRIX=10&TILEROW=263&TILECOL=1065&I=135&J=130" +
                "&INFOFORMAT=application/json",
            client.urlFor(43.68, 7.30)
        )
    }

    @Test
    fun `a value is read in kelvin and given in celsius`() {
        val body = """{"type":"FeatureCollection","features":[{"type":"Feature",
            "properties":{"variableId":"analysed_sst","value":290.80999755859375,"units":"kelvin"}}]}"""
        val result = client.classify(200, body) as FetchResult.Value
        assertEquals(17.66, result.celsius, 0.001)
    }

    @Test
    fun `a null value is land, an answer of any other shape a failure`() {
        assertEquals(FetchResult.Empty, client.classify(200, """{"features":[{"properties":{"value":null}}]}"""))
        assertEquals(FetchResult.Failure(200, null), client.classify(200, """{"features":[]}"""))
        assertEquals(FetchResult.Failure(200, null), client.classify(200, "<html>login</html>"))
    }

    @Test
    fun `a refused request carries the service's exception text`() {
        val body = """<ExceptionReport><Exception exceptionCode="MissingParameterValue" locator="tilematrixset">""" +
            """Missing tileMatrixSet</Exception></ExceptionReport>"""
        assertEquals(FetchResult.Failure(400, "Missing tileMatrixSet"), client.classify(400, body))
    }

    @Test
    fun `outside the Mediterranean dataset nothing is asked and the answer is empty`() = runTest {
        assertTrue(CopernicusSeaClient.covers(43.68, 7.30))
        assertFalse(CopernicusSeaClient.covers(48.4, -4.5)) // Brest
        assertEquals(FetchResult.Empty, CopernicusSeaClient(baseUrl = "http://unreachable.invalid").fetch(48.4, -4.5))
    }
}
