package ykws.android.maro.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.Properties

/**
 * Ties the accepted tap's gold ring and the beat's ceiling to the file that ships them: each shipped
 * token is held against its own `AppConfig` default — the colour parsed with the `#RRGGBB` /
 * `#AARRGGBB` forms `AppConfig` itself accepts, the alpha read as a float — so a key name misspelled
 * on one side, which would leave the code's default standing, or a value changed on one side only
 * fails here instead of shipping invisibly, the two being the same gold and the same ceiling.
 *
 * `maro.properties` is the file read: the flash's colour and its alpha are functional settings of the
 * effect rather than palette tokens, so they ship beside the tap zone and the beat's timing. The file
 * location changes nothing for the app — `AppConfig.init` loads all three `.properties` files into one
 * `Properties` instance, so `AppConfig` still resolves both keys.
 *
 * The test CWD is the `app` module (the convention `HeatmapRampPropertiesTest` follows);
 * `maro.repoDir` is honoured first so the file is found from a repo-root run too.
 */
class MapMarkerTapFlashColorPropertiesTest {

    private val propertiesFile: File = System.getProperty("maro.repoDir")
        ?.let { File(it, "app/src/main/assets/maro.properties") }
        ?.takeIf { it.isFile }
        ?: File("src/main/assets/maro.properties")

    private fun shippedProperties(): Properties {
        assumeTrue("maro.properties not found", propertiesFile.isFile)
        return Properties().apply { propertiesFile.inputStream().use { load(it) } }
    }

    /** The same `#RRGGBB` / `#AARRGGBB` forms `AppConfig`'s colour parser accepts. */
    private fun hexToArgb(value: String): Int {
        val hex = value.trim().removePrefix("#")
        return when (hex.length) {
            6 -> (0xFF000000L or hex.toLong(16)).toInt()
            8 -> hex.toLong(16).toInt()
            else -> error("unparseable colour: $value")
        }
    }

    @Test
    fun theShippedFlashTokenAgreesWithTheCodesOwnDefault() {
        val token = shippedProperties().getProperty("map.marker.tap.flash.color")

        assertNotNull("maro.properties must carry map.marker.tap.flash.color", token)
        assertEquals(
            "the shipped token and AppConfig.mapMarkerTapFlashColor must name the same colour",
            AppConfig.mapMarkerTapFlashColor,
            hexToArgb(token!!)
        )
    }

    @Test
    fun theShippedFlashAlphaAgreesWithTheCodesOwnDefault() {
        val token = shippedProperties().getProperty("map.marker.tap.flash.alpha")

        assertNotNull("maro.properties must carry map.marker.tap.flash.alpha", token)
        assertEquals(
            "the shipped token and AppConfig.mapMarkerTapFlashAlpha must name the same alpha",
            AppConfig.mapMarkerTapFlashAlpha.toDouble(),
            token!!.trim().toDouble(),
            1e-6,
        )
    }
}
