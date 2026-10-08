package ykws.android.maro.ui.map

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.settings.AppSettings

/**
 * The two "Speed Display" chip rows after the 2026-10-07 rearrangement: the tracks row exposes the
 * recorded kind's own pair and the routes row the saved route's, neither reading the other's — and the
 * block that moved above export/import still wires the same shared arrow-tuning keys it wrote before.
 *
 * The pairing is pinned as pure contract through the accessors the two rows call, so the wiring is
 * covered without a Compose harness. The arrow-tuning half and the retired-keys half read the overlay
 * source and the two locale files as text, the file-first convention `TrackRenderStringsTest` uses.
 */
class SpeedDisplayChipsTest {

    /** The four axes set to a pattern that collides nowhere, so a cross-read shows up at once. */
    private val skewed = AppSettings(
        trackArrows = true,
        trackColours = false,
        routeSpeedArrows = false,
        routeSpeedColor = true
    )

    @Test
    fun theTracksChipsReadTheRecordedPairAlone() {
        assertTrue(trackSpeedDisplayOn(skewed, SpeedDisplayAxis.ARROWS))
        assertFalse(trackSpeedDisplayOn(skewed, SpeedDisplayAxis.COLOURS))
    }

    @Test
    fun theRoutesChipsReadTheRoutePairAlone() {
        assertFalse(routeSpeedDisplayOn(skewed, SpeedDisplayAxis.ARROWS))
        assertTrue(routeSpeedDisplayOn(skewed, SpeedDisplayAxis.COLOURS))
    }

    @Test
    fun eachChipFlipsItsKindAndLeavesTheOtherUntouched() {
        val off = AppSettings(
            trackArrows = false,
            trackColours = false,
            routeSpeedArrows = false,
            routeSpeedColor = false
        )

        // Each of the four axes, flipped on alone, must leave the other three down (F7).
        val tracksArrows = off.toggleTrackSpeedDisplay(SpeedDisplayAxis.ARROWS)
        assertTrue(tracksArrows.trackArrows)
        assertFalse(tracksArrows.trackColours)
        assertFalse(tracksArrows.routeSpeedArrows)
        assertFalse(tracksArrows.routeSpeedColor)

        val tracksColours = off.toggleTrackSpeedDisplay(SpeedDisplayAxis.COLOURS)
        assertTrue(tracksColours.trackColours)
        assertFalse(tracksColours.trackArrows)
        assertFalse(tracksColours.routeSpeedArrows)
        assertFalse(tracksColours.routeSpeedColor)

        val routeArrows = off.toggleRouteSpeedDisplay(SpeedDisplayAxis.ARROWS)
        assertTrue(routeArrows.routeSpeedArrows)
        assertFalse(routeArrows.routeSpeedColor)
        assertFalse(routeArrows.trackArrows)
        assertFalse(routeArrows.trackColours)

        val routeColours = off.toggleRouteSpeedDisplay(SpeedDisplayAxis.COLOURS)
        assertTrue(routeColours.routeSpeedColor)
        assertFalse(routeColours.routeSpeedArrows)
        assertFalse(routeColours.trackArrows)
        assertFalse(routeColours.trackColours)
    }

    @Test
    fun theMovedBlockStillWiresTheSharedArrowTuningKeys() {
        val source = fileText("src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt")
        // The block's own slice alone — from the expander that opens it to the export/import line that
        // follows it — so a key surviving elsewhere in the file cannot stand in for the move (F3).
        val block = source
            .substringAfter("settings_tracks_direction_settings_label")
            .substringBefore("settings_tracks_transfer_desc")
        // Only this block writes these five, so their presence inside it is that the move kept the
        // shared tuning both kinds' chevrons read.
        listOf(
            "trackDirectionDensity",
            "trackDirectionMinSpacingDp",
            "trackDirectionMaxSpacingDp",
            "trackDirectionSpeedFloorKn",
            "trackDirectionSpeedCeilingKn"
        ).forEach { key ->
            assertTrue("the moved block no longer writes $key", block.contains(key))
        }
    }

    @Test
    fun theRouteSwapRetiredItsThreeKeysInBothLocales() {
        // The chip swap left the two gate labels and the removed block's own label unread; a stale key
        // matches nothing, so their absence in both locales is the guard that no reader survived.
        val retired = listOf(
            "settings_routes_speed_color_label",
            "settings_routes_arrows_label",
            "settings_routes_speed_direction_label"
        )

        listOf("values", "values-fr").forEach { locale ->
            val xml = fileText("src/main/res/$locale/strings.xml")
            retired.forEach { key ->
                assertFalse("$key is still declared in $locale", xml.contains("name=\"$key\""))
            }
        }
    }

    /** The shared file-first reader, so the convention lives once (F7). */
    private fun fileText(relative: String): String = sourceText(relative)
}
