package ykws.android.maro.ui.map

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * The render-control string set, tied to the two files that carry it: the Speed Display heading and the
 * two option labels exist in both locales, the retired triple's label is gone from both, and the
 * Settings heading is renamed in both.
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
    fun bothLocalesCarryTheDisplayHeadingAndTheTwoOptionLabels() {
        val expected = listOf(
            "settings_tracks_speed_display_label",
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
    fun bothLocalesCarryEveryRouteAndStartupLineTheWorkAdded() {
        // The kind axis and its three options, the two estimated-cell labels, the route colour row, the
        // opacity row, the count row, the two gates and the start-time colour report: each is a
        // locale-keyed line this work added, and each could lose one locale silently without this.
        val expected = listOf(
            "filter_axis_kind",
            "filter_option_all",
            "filter_option_tracks",
            "filter_option_routes",
            "track_stat_total_estimated",
            "track_stat_avg_estimated",
            "settings_color_routes",
            "settings_route_transparency_label",
            "settings_routes_count_label",
            "settings_routes_speed_color_label",
            "settings_routes_arrows_label",
            "startup_colour_value_unreadable"
        )

        bothLocales().forEach { (locale, xml) ->
            expected.forEach { key ->
                assertTrue("$key is missing from $locale", xml.contains("name=\"$key\""))
            }
        }
    }

    @Test
    fun theTabSweepCarriesItsNewKeysAndRetiresTheOldOnesInBothLocales() {
        // The tab order and header sweep: every renamed group key must exist in both locales, and every
        // key it replaced must be gone — the retired half is what stops a forgotten reader from silently
        // relabelling another surface, since a stale key left behind matches nothing.
        val expected = listOf(
            "settings_tab_routing",
            "filter_axis_position",
            "settings_section_regulated_zones",
            "settings_section_zone300",
            "settings_section_coastline",
            "settings_section_land_water_icon",
            "settings_section_danger_zones",
            "settings_section_depth_map",
            "settings_section_redisplay",
            "settings_section_map_offset",
            "settings_section_stop_detection",
            "settings_section_regenerate_layers",
            "settings_section_gps_tuning",
            "settings_section_position_source",
            "settings_section_appearance",
            "settings_section_routing_tuning",
            "settings_depth_cutoff_expander"
        )
        val retired = listOf(
            "settings_tab_position",
            "settings_section_layers",
            "settings_section_navigation",
            "settings_section_advanced",
            "settings_section_position",
            "settings_emodnet_section_label",
            "settings_emodnet_section_desc",
            "settings_idle_section_label",
            "settings_idle_section_desc",
            "settings_regulated_zones_label",
            "settings_zone300_label",
            "settings_danger_zones_label",
            "settings_depth_label",
            "settings_redisplay_label",
            "settings_map_offset_label",
            "settings_regenerate_layers"
        )

        bothLocales().forEach { (locale, xml) ->
            expected.forEach { key ->
                assertTrue("$key is missing from $locale", xml.contains("name=\"$key\""))
            }
            retired.forEach { key ->
                assertFalse("$key is still declared in $locale", xml.contains("name=\"$key\""))
            }
        }
    }
}
