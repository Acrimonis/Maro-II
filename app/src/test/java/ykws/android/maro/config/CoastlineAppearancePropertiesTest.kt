package ykws.android.maro.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.Properties
import ykws.android.maro.data.settings.AppSettings

/**
 * Holds the stroke-width change's key layout against the files that ship it.
 *
 *  - The six new appearance keys are `maro.properties` values, each checked equal to the default the
 *    code carries — a key misspelled on one side, which would leave the code's default standing, or a
 *    value changed on one side only, fails here.
 *  - The eleven re-homed colour keys are present in `maro.properties` and **absent** from
 *    `colors.properties`: no key has two homes, and the deletion half of the move is asserted rather
 *    than assumed.
 *  - The retired coastline width pair is gone from `maro.properties`, replaced by the single key.
 *  - The five heading-line and cap-arrow keys the appearance change added are checked the same way —
 *    the property file against the default the code carries — and the direction line's colour against
 *    its now-opaque hue, so a misspelled or one-sided navigation key fails here as well.
 *
 * The test CWD is the `app` module (the convention `HeatmapRampPropertiesTest` follows);
 * `maro.repoDir` is honoured first so the file is found from a repo-root run too.
 */
class CoastlineAppearancePropertiesTest {

    private fun assetFile(relative: String): File = System.getProperty("maro.repoDir")
        ?.let { File(it, "app/src/main/assets/$relative") }
        ?.takeIf { it.isFile }
        ?: File("src/main/assets/$relative")

    private fun shipped(relative: String): Properties {
        val file = assetFile(relative)
        assumeTrue("$relative not found", file.isFile)
        return Properties().apply { file.inputStream().use { load(it) } }
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

    private val movedColourKeys = listOf(
        "map.coastline.mainland.color",
        "map.coastline.island.color",
        "map.zone300.fill",
        "map.zone300.boundary",
        "map.hazard.disc.fill",
        "map.hazard.outline",
        "map.zoneAhead.line",
        "map.zoneAhead.cone.fill",
        "map.zoneAhead.cone.outline",
        "map.navigation.arrow.color",
        "map.navigation.line.color"
    )

    @Test
    fun theSixAppearanceKeysShipInMaroPropertiesAtTheCodesDefaults() {
        val props = shipped("maro.properties")

        // A shipped width is the value its setting seeds from: asserted against the setting, since the
        // property parse carries no clamp and the 1–20 span lives on the read.
        assertEquals(
            props.getProperty("map.coastline.widthPx").trim().toInt(),
            AppSettings().coastlineWidthPx
        )
        assertEquals(
            AppConfig.mapCoastlineTransparencyPct,
            props.getProperty("map.coastline.transparencyPct").trim().toInt()
        )
        assertEquals(
            props.getProperty("map.zone300.boundary.widthPx").trim().toInt(),
            AppSettings().zone300BoundaryWidthPx
        )
        assertEquals(
            props.getProperty("map.regulatedZone.outline.widthPx").trim().toInt(),
            AppSettings().regulatedZoneOutlineWidthPx
        )
        assertEquals(
            AppConfig.mapCoastlineMainlandColor,
            hexToArgb(props.getProperty("map.coastline.mainland.color"))
        )
        assertEquals(
            AppConfig.mapCoastlineIslandColor,
            hexToArgb(props.getProperty("map.coastline.island.color"))
        )
    }

    @Test
    fun theFiveNavigationKeysShipInMaroPropertiesAtTheCodesDefaults() {
        val props = shipped("maro.properties")

        // The widths follow the coastline rows' rule: asserted against the setting, since the property
        // parse carries no clamp and the dp spans are applied where the setting is read.
        assertEquals(
            props.getProperty("map.navigation.arrow.widthDp").trim().toFloat(),
            AppSettings().navigationArrowWidthDp,
            0f
        )
        assertEquals(
            AppConfig.mapNavigationArrowTransparencyPct,
            props.getProperty("map.navigation.arrow.transparencyPct").trim().toInt()
        )
        assertEquals(
            AppConfig.mapNavigationArrowFollowSpeedColour,
            props.getProperty("map.navigation.arrow.followSpeedColour").trim().toBooleanStrict()
        )
        assertEquals(
            props.getProperty("map.navigation.line.widthDp").trim().toFloat(),
            AppSettings().navigationLineWidthDp,
            0f
        )
        assertEquals(
            AppConfig.mapNavigationLineTransparencyPct,
            props.getProperty("map.navigation.line.transparencyPct").trim().toInt()
        )
        // The line colour is the opaque hue: the alpha it once packed now rides the transparency key,
        // so a re-packed alpha fails against the code's own opaque default here.
        assertEquals(
            AppConfig.mapNavigationLineColor,
            hexToArgb(props.getProperty("map.navigation.line.color"))
        )
    }

    @Test
    fun theElevenColourKeysLiveOnlyInMaroProperties() {
        val maro = shipped("maro.properties")
        val colors = shipped("colors.properties")

        movedColourKeys.forEach { key ->
            assertNotNull("maro.properties must carry $key", maro.getProperty(key))
            assertNull("$key must not have a second home in colors.properties", colors.getProperty(key))
        }
    }

    @Test
    fun theCoastlineWidthPairIsRetiredInFavourOfTheSingleKey() {
        val maro = shipped("maro.properties")

        assertNull("map.coastline.mainland.width is retired", maro.getProperty("map.coastline.mainland.width"))
        assertNull("map.coastline.island.width is retired", maro.getProperty("map.coastline.island.width"))
    }
}
