package ykws.android.maro.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.track.TrackSummary
import java.io.File
import java.util.Properties

/**
 * The outline contract's width half: one width per track type read from `maro.properties`, and the
 * newest track found by recency rather than by the loop's position. Pure functions only — the
 * osmdroid drawing stays in the Compose shell, device-verified.
 *
 * The last test reads the real `maro.properties`, the shape `HeatmapRampPropertiesTest` uses for the
 * ramp: a misspelled key leaves the code's default standing and fails here, where a fixture built from
 * those defaults would have stayed green. The test CWD is the `app` module, and `maro.repoDir` is
 * honoured first so a repo-root run finds the file too.
 */
class TrackOutlineTest {

    private val propertiesFile: File = System.getProperty("maro.repoDir")
        ?.let { File(it, "app/src/main/assets/maro.properties") }
        ?.takeIf { it.isFile }
        ?: File("src/main/assets/maro.properties")

    private fun shippedProperties(): Properties {
        assumeTrue("maro.properties not found", propertiesFile.isFile)
        return Properties().apply { propertiesFile.inputStream().use { load(it) } }
    }

    private fun summary(id: String, startTimeMs: Long, lastPointTimeMs: Long = startTimeMs) =
        TrackSummary(id = id, name = id, startTimeMs = startTimeMs, lastPointTimeMs = lastPointTimeMs)

    // ── The per-type widths, and the shipped defaults behind them ─────────

    @Test
    fun theShippedWidthsSeparateTheSelectionFromEveryOtherType() {
        assertEquals(12f, AppConfig.trackWidthLive, 0f)
        assertEquals(12f, AppConfig.trackWidthSelected, 0f)
        assertEquals(10f, AppConfig.trackWidthNewest, 0f)
        assertEquals(8f, AppConfig.trackWidthPinned, 0f)
        assertEquals(6f, AppConfig.trackWidthHistory, 0f)
        assertTrue(
            "the selection must out-weigh the newest track it sits among",
            AppConfig.trackWidthSelected > AppConfig.trackWidthNewest
        )
    }

    @Test
    fun theWidthATrackEarnsFollowsItsTypeNotTheLoopsPosition() {
        assertEquals(
            AppConfig.trackWidthSelected,
            storedTrackWidth(selected = true, pinned = true, newest = false),
            0f
        )
        assertEquals(
            AppConfig.trackWidthSelected,
            storedTrackWidth(selected = true, pinned = false, newest = true),
            0f
        )
        assertEquals(
            AppConfig.trackWidthPinned,
            storedTrackWidth(selected = false, pinned = true, newest = false),
            0f
        )
        assertEquals(
            AppConfig.trackWidthNewest,
            storedTrackWidth(selected = false, pinned = false, newest = true),
            0f
        )
        assertEquals(
            AppConfig.trackWidthHistory,
            storedTrackWidth(selected = false, pinned = false, newest = false),
            0f
        )
    }

    // ── The newest track is found by recency, not by loop position ───────

    @Test
    fun theNewestTrackIsTheNewestByRecencyNotWhoeverTheRankingPutsFirst() {
        // The selection policy ranks the focused track first, so the drawn list opens on the user's
        // selection: finding the newest by position would hand the history width to the one track the
        // user is looking at, on the same map where the true newest takes the heavier one.
        val selected = summary("selected", startTimeMs = 1_000L)
        val newest = summary("newest", startTimeMs = 3_000L)
        val older = summary("older", startTimeMs = 2_000L)
        val ranked = listOf(selected, newest, older)
        val newestId = newestTrackId(ranked)

        assertEquals("newest", newestId)
        assertEquals(
            AppConfig.trackWidthSelected,
            storedTrackWidth(selected = true, pinned = false, newest = selected.id == newestId),
            0f
        )
        assertEquals(
            AppConfig.trackWidthNewest,
            storedTrackWidth(selected = false, pinned = false, newest = newest.id == newestId),
            0f
        )
        assertEquals(
            AppConfig.trackWidthHistory,
            storedTrackWidth(selected = false, pinned = false, newest = older.id == newestId),
            0f
        )
    }

    @Test
    fun theNewestTrackBreaksATieOnItsLastPointAndIsNullWhenTheSetIsEmpty() {
        // The ranking reads startTimeMs then lastPointTimeMs, so two tracks that started in the same
        // millisecond are separated by the one that carried on longer — the same order, so the drawn
        // width never contradicts the list's own ranking.
        val shorter = summary("shorter", startTimeMs = 5_000L, lastPointTimeMs = 6_000L)
        val longer = summary("longer", startTimeMs = 5_000L, lastPointTimeMs = 9_000L)

        assertEquals("longer", newestTrackId(listOf(shorter, longer)))
        assertEquals("longer", newestTrackId(listOf(longer, shorter)))
        assertEquals(null, newestTrackId(emptyList()))
    }

    // ── The shipped file, tied to the code's own defaults ────────────────

    /**
     * The five width keys, read from the real file and held against `AppConfig`'s defaults: a drift
     * between the two fails here rather than shipping silently, and the key set is asserted first
     * because a misspelled name would leave the default standing without a word.
     */
    @Test
    fun theShippedWidthKeysParseToTheCodesOwnDefaults() {
        val props = shippedProperties()

        assertEquals(
            listOf(
                "track.width.history",
                "track.width.live",
                "track.width.newest",
                "track.width.pinned",
                "track.width.selected"
            ),
            props.stringPropertyNames().filter { it.startsWith("track.width.") }.sorted()
        )

        assertEquals(AppConfig.trackWidthLive, props.getProperty("track.width.live")!!.toFloat(), 0f)
        assertEquals(
            AppConfig.trackWidthSelected,
            props.getProperty("track.width.selected")!!.toFloat(),
            0f
        )
        assertEquals(AppConfig.trackWidthNewest, props.getProperty("track.width.newest")!!.toFloat(), 0f)
        assertEquals(AppConfig.trackWidthPinned, props.getProperty("track.width.pinned")!!.toFloat(), 0f)
        assertEquals(AppConfig.trackWidthHistory, props.getProperty("track.width.history")!!.toFloat(), 0f)
    }
}
