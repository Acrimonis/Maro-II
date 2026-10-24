package ykws.android.maro.data.track

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The source reading: one point with an accuracy makes a track GPS, none makes it demo, and the index carries it. */
class TrackGpsSourcedTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun point(accuracyM: Float?, type: PointType = PointType.NORMAL) =
        TrackPoint(lat = 43.5, lon = 7.0, accuracyM = accuracyM, type = type)

    private fun track(id: String, vararg points: TrackPoint, demo: Boolean = false) = Track(
        id = id,
        name = id,
        startTimeMs = 1_000L,
        endTimeMs = 2_000L,
        trackPoints = points.toList(),
        demo = demo
    )

    @Test
    fun aSingleAccuracyedPointMakesATrackGps() {
        assertTrue(track("t", point(null), point(5f), point(null)).hasGpsFix())
    }

    @Test
    fun noAccuracyAnywhereLeavesATrackWithoutAFix() {
        assertFalse(track("t", point(null), point(null)).hasGpsFix())
        assertFalse(track("empty").hasGpsFix())
    }

    @Test
    fun theIndexCarriesTheReadingNotTheStickyDemoFlag() = runBlocking {
        val repo = TrackRepository(temp.root)
        // A mostly-GPS trip that took one simulated sample: the flag says demo, the points say GPS.
        repo.save(track("mixed", point(4f), point(null), demo = true))
        repo.save(track("simulated", point(null), point(null), demo = true))

        val summaries = repo.listTracks().associateBy { it.id }

        assertTrue(summaries.getValue("mixed").gpsSourced)
        assertTrue(summaries.getValue("mixed").demo)
        assertFalse(summaries.getValue("simulated").gpsSourced)
    }

    @Test
    fun anIndexFromBeforeTheFieldIsRebuiltSoNoTrackReadsAsDemoByDefault() = runBlocking {
        val repo = TrackRepository(temp.root)
        repo.save(track("gps", point(4f)))
        // The index the previous build wrote: current-looking content, but the old stamp and no field.
        java.io.File(temp.root, "index.bin").writeBytes(
            ProtoBuf.Default.encodeToByteArray(
                TrackSummaryList.serializer(),
                TrackSummaryList(
                    tracks = listOf(TrackSummary(id = "gps", name = "gps", startTimeMs = 1_000L, endTimeMs = 2_000L)),
                    version = SUMMARY_INDEX_VERSION - 1
                )
            )
        )

        val summaries = repo.listTracks()

        assertEquals(1, summaries.size)
        assertTrue(summaries.first().gpsSourced)
    }
}
