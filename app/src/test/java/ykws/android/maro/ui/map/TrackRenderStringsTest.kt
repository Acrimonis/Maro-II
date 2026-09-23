package ykws.android.maro.ui.map

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * The render-control string set, tied to the two files that carry it: the caption and the two option
 * labels exist in both locales, the retired triple's label is gone from both, and the Settings heading
 * is renamed in both.
 *
 * The XML is read as text — no Android resource machinery — with the same file-first convention
 * `HeatmapRampPropertiesTest` uses, so the test CWD is the `app` module while `maro.repoDir` is
 * honoured first for a repo-root run.
 */
class TrackRenderStringsTest {

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
    fun bothLocalesCarryTheCaptionAndTheTwoOptionLabels() {
        val expected = listOf(
            "menu_tracks_rendering",
            "menu_render_arrows",
            "menu_render_colours"
        )

        bothLocales().forEach { (locale, xml) ->
            expected.forEach { key ->
                assertTrue("$key is missing from $locale", xml.contains("name=\"$key\""))
            }
        }
    }

    @Test
    fun theRetiredTriplesLabelAndTheTwoDirectionStringsAreGoneFromBothLocales() {
        val retired = listOf(
            "menu_render_mode_simple",
            "menu_render_mode_dir_speed",
            "menu_render_mode_colours",
            "menu_show_tracks_direction",
            "settings_tracks_direction_label"
        )

        bothLocales().forEach { (locale, xml) ->
            retired.forEach { key ->
                assertFalse("$key is still declared in $locale", xml.contains("name=\"$key\""))
            }
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
        // The expander ships untouched, so its label and description must survive the rename.
        bothLocales().forEach { (locale, xml) ->
            assertTrue("$locale lost the arrow density label", xml.contains("settings_tracks_direction_desc"))
        }
    }

    @Test
    fun bothLocalesCarryEveryTraceAndStartupLineTheWorkAdded() {
        // The kind axis and its three options, the two estimated-cell labels, the trace colour row, the
        // opacity row, the count row, the two gates and the start-time colour report: each is a
        // locale-keyed line this work added, and each could lose one locale silently without this.
        val expected = listOf(
            "filter_axis_trace",
            "filter_option_all",
            "filter_option_tracks",
            "filter_option_traces",
            "track_stat_total_estimated",
            "track_stat_avg_estimated",
            "settings_color_trace_tracks",
            "settings_trace_transparency_label",
            "settings_traces_count_label",
            "settings_traces_speed_color_label",
            "settings_traces_arrows_label",
            "startup_colour_value_unreadable"
        )

        bothLocales().forEach { (locale, xml) ->
            expected.forEach { key ->
                assertTrue("$key is missing from $locale", xml.contains("name=\"$key\""))
            }
        }
    }
}
