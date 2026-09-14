package ykws.android.maro.ui.map

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * The render-mode string set, tied to the two files that carry it (D9): the caption and the three
 * option labels exist in both locales, the two retired keys are gone from both, and the Settings
 * heading is renamed in both.
 *
 * The XML is read as text — no Android resource machinery — with the same file-first convention
 * `HeatmapRampPropertiesTest` uses, so the test CWD is the `app` module while `maro.repoDir` is
 * honoured first for a repo-root run.
 */
class TrackRenderModeStringsTest {

    private fun stringsFile(locale: String): File = System.getProperty("maro.repoDir")
        ?.let { File(it, "app/src/main/res/$locale/strings.xml") }
        ?.takeIf { it.isFile }
        ?: File("src/main/res/$locale/strings.xml")

    private fun stringsText(locale: String): String {
        val file = stringsFile(locale)
        assumeTrue("strings.xml not found for $locale", file.isFile)
        return file.readText()
    }

    /** Both locales are always read together, so a key added to one alone fails here. */
    private fun bothLocales(): List<Pair<String, String>> =
        listOf("values", "values-fr").map { it to stringsText(it) }

    @Test
    fun bothLocalesCarryTheCaptionAndTheThreeOptionLabels() {
        val expected = listOf(
            "menu_tracks_rendering",
            "menu_render_mode_simple",
            "menu_render_mode_dir_speed",
            "menu_render_mode_colours"
        )

        bothLocales().forEach { (locale, xml) ->
            expected.forEach { key ->
                assertTrue("$key is missing from $locale", xml.contains("name=\"$key\""))
            }
        }
    }

    @Test
    fun theTwoRetiredDirectionStringsAreGoneFromBothLocales() {
        bothLocales().forEach { (locale, xml) ->
            assertFalse(
                "menu_show_tracks_direction is still declared in $locale",
                xml.contains("name=\"menu_show_tracks_direction\"")
            )
            assertFalse(
                "settings_tracks_direction_label is still declared in $locale",
                xml.contains("name=\"settings_tracks_direction_label\"")
            )
        }
    }

    @Test
    fun theSettingsHeadingIsRenamedInBothLocales() {
        val (en, fr) = bothLocales().map { it.second }

        assertTrue(en.contains("name=\"settings_colors_label\">Default Colors<"))
        assertTrue(fr.contains("name=\"settings_colors_label\">Couleurs par défaut<"))
    }

    @Test
    fun theArrowsControlsKeepTheirOwnStrings() {
        // D6: the expander ships untouched, so its label and description must survive the rename.
        bothLocales().forEach { (locale, xml) ->
            assertTrue("$locale lost the arrow density label", xml.contains("settings_tracks_direction_desc"))
        }
    }
}
