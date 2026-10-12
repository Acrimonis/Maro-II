package ykws.android.maro.data.regulation

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

/**
 * **Read-only schema probe** — prints the property schema the SHOM INSPIRE WFS actually
 * serves for each layer in [ShomRegulationClient.CANDIDATE_TYPENAMES], and names the
 * properties the parser ignores.
 *
 * For every candidate typeName it performs:
 * 1. `DescribeFeatureType` → the distinct `<xsd:element name="...">` entries of the XSD
 *    (geometry element excluded).
 * 2. `GetFeature` (single feature, GeoJSON, EPSG:3857) → the distinct keys of the first
 *    feature's `properties` object.
 * 3. The keys the parser does **not** read, i.e. the above minus the parser's read set.
 *
 * Purely diagnostic: it asserts nothing, and it writes nothing. If the endpoint is down
 * it prints the error and moves on.
 */
class ShomWfsSchemaProbeTest {

    private val json = Json { ignoreUnknownKeys = true }

    /** Property names the parser in [ShomRegulationClient] reads, verbatim. */
    private val parserReadSet = setOf(
        "RESTRN", "restrn", "CATREA", "catrea", "catach", "vitesse_max",
        "INFORM", "inform", "TXTDSC", "objnam", "nobjnm", "nom",
        "inspireid", "id_reglementation", "INSPIREID", "ninfom",
        "description", "type_reglementation"
    )

    @Test
    fun `probe INSPIRE WFS schema per candidate layer`() {
        for (typeName in ShomRegulationClient.CANDIDATE_TYPENAMES) {
            println("══════════════════════════════════════════════════════════════")
            println("[probe] LAYER: $typeName")

            val xsdProperties = probeDescribeFeatureType(typeName)
            println("[probe]   DescribeFeatureType properties (${xsdProperties.size}): $xsdProperties")

            val featureKeys = probeFirstFeatureKeys(typeName)
            println("[probe]   First feature property keys (${featureKeys.size}): $featureKeys")

            val ignored = featureKeys - parserReadSet
            println("[probe]   IGNORED by parser (${ignored.size}): $ignored")

            val matched = featureKeys intersect parserReadSet
            println("[probe]   READ by parser (${matched.size}): $matched")
        }
        println("══════════════════════════════════════════════════════════════")
    }

    /** GET DescribeFeatureType and extract the distinct `<xsd:element name="...">` entries. */
    private fun probeDescribeFeatureType(typeName: String): List<String> {
        val url = ShomRegulationClient.INSPIRE_BASE_URL +
                "?service=WFS&version=2.0.0&request=DescribeFeatureType" +
                "&typeNames=$typeName"
        val body = httpGet(url) ?: return emptyList()
        // `name` may sit after other attributes (maxOccurs, type, …), so match the whole
        // tag and pull the attribute out, rather than requiring it to come first.
        val tagRegex = Regex("""<(?:xsd:|xs:)?element\b[^>]*>""")
        val nameRegex = Regex("""\bname="([^"]+)"""")
        return tagRegex.findAll(body)
            .mapNotNull { nameRegex.find(it.value)?.groupValues?.get(1) }
            .filterNot { it.contains("geom", ignoreCase = true) || it.contains("shape", ignoreCase = true) }
            .distinct()
            .sorted()
            .toList()
    }

    /** GET a single GeoJSON feature and return the distinct keys of its `properties` object. */
    private fun probeFirstFeatureKeys(typeName: String): List<String> {
        val url = ShomRegulationClient.INSPIRE_BASE_URL +
                "?service=WFS&version=2.0.0&request=GetFeature" +
                "&typeNames=$typeName" +
                "&outputFormat=application/json&srsName=EPSG:3857&count=1"
        val body = httpGet(url) ?: return emptyList()
        return runCatching {
            val root = json.parseToJsonElement(body).jsonObject
            val features = root["features"]?.jsonArray ?: return emptyList()
            val first = features.firstOrNull()?.jsonObject ?: return emptyList()
            val properties = first["properties"]?.jsonObject ?: return emptyList()
            properties.keys.sorted()
        }.getOrElse {
            println("[probe]   JSON parse error for $typeName: ${it.message}")
            emptyList()
        }
    }

    private fun httpGet(url: String): String? {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "MaroII-Schema-Probe/1.0")
            conn.inputStream.bufferedReader().readText()
        } catch (e: Exception) {
            println("[probe]   HTTP error for $url: ${e.message}")
            null
        }
    }
}
