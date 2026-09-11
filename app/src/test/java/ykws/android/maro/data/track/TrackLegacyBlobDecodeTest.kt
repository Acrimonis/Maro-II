package ykws.android.maro.data.track

import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import java.io.File
import java.util.Base64

/**
 * Guards the closed-forever reservation of `Track.11` and `TrackSummary.8`.
 *
 * Legacy blobs — including those inside user-held GPX exports — still carry the removed
 * `visibleOnMap` field. Decoding MUST ignore it: reusing either tag for a new field would silently
 * corrupt every old file, so this test is the enforcement half of the comment convention.
 */
class TrackLegacyBlobDecodeTest {

    private val proto = ProtoBuf.Default

    /** Tag (field 11, wire type 0) + value false — the removed `Track.visibleOnMap` field. */
    private val legacyTrackField = byteArrayOf(0x58, 0x00)

    /** Tag (field 8, wire type 0) + value false — the removed `TrackSummary.visibleOnMap` field. */
    private val legacySummaryField = byteArrayOf(0x40, 0x00)

    private fun sampleTrack() = Track(
        id = "legacy-id",
        name = "legacy",
        startTimeMs = 1_700_000_000_000L,
        trackPoints = listOf(
            TrackPoint(lat = 43.0, lon = 7.0, timeOffsetMs = 0L),
            TrackPoint(lat = 43.1, lon = 7.1, timeOffsetMs = 1000L)
        )
    )

    @Test
    fun legacyTrackBlobWithField11_stillDecodes() {
        val bytes = proto.encodeToByteArray(Track.serializer(), sampleTrack()) + legacyTrackField
        val decoded = proto.decodeFromByteArray(Track.serializer(), bytes)

        assertEquals("legacy-id", decoded.id)
        assertEquals(2, decoded.trackPoints.size)
    }

    @Test
    fun legacySummaryBlobWithField8_stillDecodes() {
        val summary = TrackSummary(id = "s1", name = "s1", startTimeMs = 1L)
        val bytes = proto.encodeToByteArray(TrackSummary.serializer(), summary) + legacySummaryField
        val decoded = proto.decodeFromByteArray(TrackSummary.serializer(), bytes)

        assertEquals("s1", decoded.id)
    }

    /**
     * The real legacy export in the repo carries field 11 inside `<maro:data>`. It must keep
     * decoding with the field removed — the import (and thus map rendering) must be unaffected by
     * a legacy `visibleOnMap = false`.
     */
    @Test
    fun realLegacyExportBlob_decodesAfterFieldRemoval() {
        val repoDir = File(System.getProperty("maro.repoDir") ?: "..")
        val gpx = repoDir.listFiles { f ->
            f.isFile && f.name.startsWith("2026_09_06_15_52") && f.name.endsWith(".gpx") &&
                !f.name.contains("_no-resume") && !f.name.contains("_clean")
        }?.maxByOrNull { it.length() }
        Assume.assumeTrue("legacy GPX fixture not present — skipping", gpx != null)

        val xml = gpx!!.readText(Charsets.UTF_8)
        val open = "<maro:data>"
        val close = "</maro:data>"
        val start = xml.indexOf(open)
        val end = xml.indexOf(close)
        Assume.assumeTrue("no maro:data blob in fixture", start >= 0 && end > start)

        val blob = Base64.getMimeDecoder().decode(xml.substring(start + open.length, end).trim())
        val track = proto.decodeFromByteArray(Track.serializer(), blob)

        assertTrue(track.id.isNotEmpty())
        assertTrue(track.trackPoints.isNotEmpty())
    }
}
