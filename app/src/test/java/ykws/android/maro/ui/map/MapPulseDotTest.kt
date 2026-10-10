package ykws.android.maro.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import ykws.android.maro.config.AppConfig
import java.io.File
import java.util.Properties

/**
 * The mark's own numbers stay where the mark keeps them — now the shipped palette's `ui.map.pulse.dot.*`
 * keys, read in the shipped-file shape `MapTogglePaletteTest` uses.
 *
 * The beat's floor decides how far the body fades before its ring holds the edge; the inset ratio decides
 * where a square places the disc. Both stay pinned, together with the keys that carry the mark's whole
 * geometry — the disc size, the inset ratio, the floor, the period and the ring width — so a key moved on
 * one side only fails here rather than leaving the code's default standing unnoticed.
 */
class MapPulseDotTest {

    private val colorsFile: File = System.getProperty("maro.repoDir")
        ?.let { File(it, "app/src/main/assets/colors.properties") }
        ?.takeIf { it.isFile }
        ?: File("src/main/assets/colors.properties")

    private fun shipped(): Properties {
        assumeTrue("colors.properties not found", colorsFile.isFile)
        return Properties().apply { colorsFile.inputStream().use { load(it) } }
    }

    @Test
    fun theSquaresInsetIsTheShippedRatioOfTheDisc() {
        assertEquals(
            "the square's own top-right inset",
            MAP_PULSE_DOT_SIZE.value * AppConfig.uiMapPulseDotInsetRatio,
            MAP_PULSE_DOT_INSET.value,
            1e-6f
        )
        assertEquals("the settled ratio", 0.25f, AppConfig.uiMapPulseDotInsetRatio, 1e-6f)
    }

    @Test
    fun theSettledGeometryValuesAreShipped() {
        val props = shipped()
        assertEquals("disc size", "12", props.getProperty("ui.map.pulse.dot.size"))
        assertEquals("inset ratio", "0.25", props.getProperty("ui.map.pulse.dot.inset.ratio"))
        assertEquals("beat floor", "0.33", props.getProperty("ui.map.pulse.dot.floor"))
        assertEquals("period", "555", props.getProperty("ui.map.pulse.dot.ms"))
        assertEquals("ring width", "1", props.getProperty("ui.map.pulse.dot.ring.width"))
    }

    @Test
    fun theMarkReadsTheShippedKeysAndLandsAtThreeDp() {
        val props = shipped()

        fun value(key: String): Float {
            val raw = props.getProperty(key)?.trim() ?: error("colors.properties must carry $key")
            return raw.toFloat()
        }

        // `AppConfig` follows the palette, which is the source of truth, so a moved number cannot pass.
        assertEquals(value("ui.map.pulse.dot.size"), AppConfig.uiMapPulseDotSize, 1e-6f)
        assertEquals(value("ui.map.pulse.dot.inset.ratio"), AppConfig.uiMapPulseDotInsetRatio, 1e-6f)
        assertEquals(value("ui.map.pulse.dot.floor"), AppConfig.uiMapPulseDotFloor, 1e-6f)
        assertEquals(value("ui.map.pulse.dot.ms"), AppConfig.uiMapPulseDotMs.toFloat(), 1e-6f)
        assertEquals(value("ui.map.pulse.dot.ring.width"), AppConfig.uiMapPulseDotRingWidth, 1e-6f)

        // The mark's accessors read those settings — and the derived inset lands at the ratio of the disc.
        assertEquals("the mark's disc", value("ui.map.pulse.dot.size"), MAP_PULSE_DOT_SIZE.value, 1e-6f)
        assertEquals("the mark's floor", value("ui.map.pulse.dot.floor"), MAP_PULSE_DOT_FLOOR, 1e-6f)
        assertEquals("the mark's period", value("ui.map.pulse.dot.ms").toInt(), MAP_PULSE_DEFAULT_MS)
        assertEquals(
            "the mark's ring width",
            value("ui.map.pulse.dot.ring.width"),
            MAP_PULSE_DOT_RING_WIDTH.value,
            1e-6f
        )
        assertEquals("the square's inset is a quarter of the 12 dp disc", 3f, MAP_PULSE_DOT_INSET.value, 1e-6f)
    }

    @Test
    fun theDiscKeyIsShipped() {
        val props = shipped()
        for (key in listOf(
            "ui.map.pulse.dot",
            "ui.map.pulse.dot.size",
            "ui.map.pulse.dot.inset.ratio",
            "ui.map.pulse.dot.floor",
            "ui.map.pulse.dot.ms",
            "ui.map.pulse.dot.ring.width"
        )) {
            assertNotNull("colors.properties must carry $key", props.getProperty(key))
        }
    }
}
