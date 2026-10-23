package ykws.android.maro.data.track

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.LatLng

/** The demo flag: set by a simulated sample, never by a GPS one, and carried by the blob in both directions. */
class TrackDemoFlagTest {

    private fun sample(isDemo: Boolean) = TrackSample(
        position = LatLng(43.5, 7.0),
        speedMps = 10f,
        bearingDeg = 0f,
        hasLock = !isDemo,
        timestampEpochMs = 0L,
        isDemo = isDemo
    )

    private val track = Track(id = "t", name = "t", startTimeMs = 1L)

    @Test
    fun aTrackWithoutTheFlagReadsAsGps() {
        assertFalse(track.demo)
    }

    @Test
    fun aGpsSampleNeverMarksATrack() {
        assertSame(track, track.markedFor(sample(isDemo = false)))
    }

    @Test
    fun aSimulatedSampleMarksATripForGood() {
        val marked = track.markedFor(sample(isDemo = true))
        assertTrue(marked.demo)
        // A later GPS sample does not clear it: a trip that ever ran on the simulated boat is demo.
        assertTrue(marked.markedFor(sample(isDemo = false)).demo)
    }

    @Test
    fun theBlobCarriesTheFlagThroughTheRoundTrip() {
        val demoTrack = track.copy(demo = true)
        val decoded = ProtoBuf.Default.decodeFromByteArray(
            Track.serializer(),
            ProtoBuf.Default.encodeToByteArray(Track.serializer(), demoTrack)
        )
        assertTrue(decoded.demo)
        assertEquals(demoTrack.id, decoded.id)
    }

    @Test
    fun anOlderBlobWithoutTheFieldReadsAsGps() {
        val decoded = ProtoBuf.Default.decodeFromByteArray(
            Track.serializer(),
            ProtoBuf.Default.encodeToByteArray(OlderTrack.serializer(), OlderTrack("old", "Old", 5L))
        )
        assertFalse(decoded.demo)
        assertEquals("old", decoded.id)
    }

    @Test
    fun aSummaryWithoutTheFieldReadsAsGps() {
        assertFalse(TrackSummary(id = "s", name = "s", startTimeMs = 1L).demo)
    }

    /** The blob an older build wrote: the first fields only, none of the later ones. */
    @Serializable
    private data class OlderTrack(
        @ProtoNumber(1) val id: String,
        @ProtoNumber(2) val name: String,
        @ProtoNumber(4) val startTimeMs: Long
    )
}
