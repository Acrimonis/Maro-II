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
 * The outline contract: one width per track type read from `maro.properties`, the newest track found
 * by recency rather than by the loop's position, and the selection's own two rules — full alpha
 * whatever its class transparency says, and the legacy dark casing at the width its key reads. Pure
 * functions only — the osmdroid drawing stays in the Compose shell, device-verified.
 *
 * **The widths are dp since 2026-09-19**, the reference density being the 3× device they were chosen
 * on, so every shipped number is the px it used to state divided by three and the drawn weight there
 * is unchanged. The caller multiplies by the density at the paint site; nothing here does.
 *
 * The last tests read the real `maro.properties`, the shape `HeatmapRampPropertiesTest` uses for the
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
    fun theShippedWidthsAreTheFilesNumbersInDp() {
        // The px the table used to state, over the 3× reference: 12 / 10 / 11 / 9 / 8.
        assertEquals(4f, AppConfig.trackWidthLiveDp, 1e-6f)
        assertEquals(10f / 3f, AppConfig.trackWidthSelectedDp, 1e-6f)
        assertEquals(11f / 3f, AppConfig.trackWidthNewestDp, 1e-6f)
        assertEquals(3f, AppConfig.trackWidthPinnedDp, 1e-6f)
        assertEquals(8f / 3f, AppConfig.trackWidthHistoryDp, 1e-6f)
        assertTrue(
            "the selection must out-weigh the oldest class it sits among",
            AppConfig.trackWidthSelectedDp > AppConfig.trackWidthHistoryDp
        )
    }

    @Test
    fun theWidthATrackEarnsFollowsItsTypeNotTheLoopsPosition() {
        assertEquals(
            AppConfig.trackWidthSelectedDp,
            storedTrackWidth(selected = true, pinned = true, newest = false),
            0f
        )
        assertEquals(
            AppConfig.trackWidthSelectedDp,
            storedTrackWidth(selected = true, pinned = false, newest = true),
            0f
        )
        assertEquals(
            AppConfig.trackWidthPinnedDp,
            storedTrackWidth(selected = false, pinned = true, newest = false),
            0f
        )
        assertEquals(
            AppConfig.trackWidthNewestDp,
            storedTrackWidth(selected = false, pinned = false, newest = true),
            0f
        )
        assertEquals(
            AppConfig.trackWidthHistoryDp,
            storedTrackWidth(selected = false, pinned = false, newest = false),
            0f
        )
    }

    // ── The newest track is found by recency, not by loop position ───────

    @Test
    fun theNewestTrackIsTheNewestByRecencyNotWhoeverTheRankingPutsFirst() {
        // The selection policy ranks the focused track first, so the drawn list opens on the user's
        // selection: finding the newest by position would hand the history width to the one track the
        // user is looking at, on the same map where the true newest takes its own.
        val selected = summary("selected", startTimeMs = 1_000L)
        val newest = summary("newest", startTimeMs = 3_000L)
        val older = summary("older", startTimeMs = 2_000L)
        val ranked = listOf(selected, newest, older)
        val newestId = newestTrackId(ranked)

        assertEquals("newest", newestId)
        assertEquals(
            AppConfig.trackWidthSelectedDp,
            storedTrackWidth(selected = true, pinned = false, newest = selected.id == newestId),
            0f
        )
        assertEquals(
            AppConfig.trackWidthNewestDp,
            storedTrackWidth(selected = false, pinned = false, newest = newest.id == newestId),
            0f
        )
        assertEquals(
            AppConfig.trackWidthHistoryDp,
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

    // ── The selection's opacity, and the casing it wears ─────────────────

    @Test
    fun theSelectionIsDrawnAtFullAlphaWhateverItsClassTransparencySays() {
        // The rule is one line and it is the whole exemption: the selected track's transparency
        // setting does not apply to it, while every other stored track keeps the fade it earned.
        assertEquals(1f, storedTrackFade(selected = true, fade = 0.2f), 0f)
        assertEquals(1f, storedTrackFade(selected = true, fade = 0f), 0f)
        assertEquals(1f, storedTrackFade(selected = true, fade = 1f), 0f)
        assertEquals(0.2f, storedTrackFade(selected = false, fade = 0.2f), 0f)
        assertEquals(1f, storedTrackFade(selected = false, fade = 1f), 0f)
    }

    @Test
    fun theCasingIsTheLegacyDarkAtTheWidthItsOwnKeyReads() {
        // Held against the shipped pair rather than the code's own two defaults: the claim being
        // guarded is that the dark shows outside the core the file actually draws, so a casing a
        // wider shipped core would hide fails here even while both defaults agree with each other.
        val casing = selectedTrackCasing()
        val props = shippedProperties()

        assertEquals(0xCC000000.toInt(), casing.argb)
        val shippedCore = props.getProperty("track.width.selected")!!.toFloat()
        assertEquals(
            props.getProperty("track.width.selected.casing")!!.toFloat(),
            casing.strokeWidth,
            0f
        )
        assertTrue(
            "the casing must show outside the selected core it is drawn under",
            casing.strokeWidth > shippedCore
        )
        assertEquals(
            "the rim shows 1 dp a side — 5.333 over the shipped 3.333 buys the legacy pair's 3 px",
            1f,
            (casing.strokeWidth - shippedCore) / 2f,
            1e-6f
        )
    }

    // ── The shipped file, tied to the code's own defaults ────────────────

    /**
     * The six width keys, read from the real file and held against `AppConfig`'s defaults: a drift
     * between the two fails here rather than shipping silently, and the key set is asserted first
     * because a misspelled name would leave the default standing without a word. The file is the
     * source of truth, so it is the defaults that follow it — which is what the drift the strokes
     * change left behind is settled by.
     *
     * Every mismatch is collected and reported in one go: the file can disagree with the code in more
     * than one family, and asserting them one at a time reports only the first, hiding the rest
     * behind a red that reads as a single-key drift.
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
                "track.width.selected",
                "track.width.selected.casing"
            ),
            props.stringPropertyNames().filter { it.startsWith("track.width.") }.sorted()
        )

        val mismatches = listOf(
            "track.width.live" to AppConfig.trackWidthLiveDp,
            "track.width.selected" to AppConfig.trackWidthSelectedDp,
            "track.width.newest" to AppConfig.trackWidthNewestDp,
            "track.width.pinned" to AppConfig.trackWidthPinnedDp,
            "track.width.history" to AppConfig.trackWidthHistoryDp,
            "track.width.selected.casing" to AppConfig.trackWidthSelectedCasingDp
        ).mapNotNull { (key, default) ->
            val shipped = props.getProperty(key)?.toFloatOrNull()
            if (shipped == default) null else "$key: shipped $shipped, default $default"
        }
        assertEquals(
            "shipped width keys must match the code's defaults",
            emptyList<String>(),
            mismatches
        )
    }

    /**
     * The casing's own key, held against the code alone: the shipped width above can drift from the
     * default without touching the casing, so this one stands on its own rather than behind a red.
     */
    @Test
    fun theCasingWidthKeyParsesToTheCodesOwnDefault() {
        val props = shippedProperties()

        assertEquals(16f / 3f, AppConfig.trackWidthSelectedCasingDp, 1e-6f)
        assertEquals(
            AppConfig.trackWidthSelectedCasingDp,
            props.getProperty("track.width.selected.casing")!!.toFloat(),
            1e-6f
        )
    }

    /**
     * The knee rides with the widths it compares against, so it is a dp core width too: a threshold
     * left in px would compare a px number against dp cores and silently stop tempering anything.
     */
    @Test
    fun theShippedKneeIsDpAndMovesWithTheWidthsItComparesAgainst() {
        val props = shippedProperties()

        assertEquals(10f / 3f, AppConfig.trackArrowScaleKneeDp, 1e-6f)
        assertEquals(
            AppConfig.trackArrowScaleKneeDp,
            props.getProperty("track.arrow.scaleKnee")!!.toFloat(),
            1e-6f
        )
        // Read against the same table: the knee is no longer assumed to sit between the classes, only
        // to be stated in the unit the cores are.
        assertTrue(
            "the knee must be a width of the same order as the table it bounds",
            AppConfig.trackArrowScaleKneeDp <= widestStoredTrackWidth()
        )
    }
}
