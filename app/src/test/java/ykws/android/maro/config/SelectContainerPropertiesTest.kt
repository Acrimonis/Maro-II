package ykws.android.maro.config

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.Properties

/**
 * Pins the selected-state container's one value, `ui.select.container`.
 *
 *  - The shipped token is held against the default the code carries — the habit every other `AppConfig`
 *    colour follows, the fine-cell ratio being the precedent — so a key misspelled on one side, which
 *    would leave the code's default standing, or a value changed on one side only, fails here.
 *  - The tone is the **accent at a low alpha**: the container's RGB agrees with `ui.accent`'s and its
 *    alpha is the low weight that makes it a tonal face rather than a fill, so a selected group of
 *    choices and the action's own face share one hue while no longer sharing one fill (§5).
 *
 * The test CWD is the `app` module (the convention `CoastlineAppearancePropertiesTest` and
 * `HeatmapRampPropertiesTest` follow); `maro.repoDir` is honoured first so the file is found from a
 * repo-root run too.
 */
class SelectContainerPropertiesTest {

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

    @Test
    fun theSelectContainerShipsAtTheCodesDefaultAndTheAccentHue() {
        val colors = shipped("colors.properties")

        assertEquals(
            "the shipped token and AppConfig.uiSelectContainer must name the same colour",
            AppConfig.uiSelectContainer,
            hexToArgb(colors.getProperty("ui.select.container"))
        )
        assertEquals(
            "the container keeps the accent's own RGB",
            AppConfig.uiAccent and 0x00FFFFFF,
            AppConfig.uiSelectContainer and 0x00FFFFFF
        )
        assertEquals(
            "the container's alpha is the tonal weight that makes it a face, not a fill",
            0x4D,
            (AppConfig.uiSelectContainer ushr 24) and 0xFF
        )
    }
}
