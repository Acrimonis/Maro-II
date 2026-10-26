package ykws.android.maro.data.track

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.markers.toMarkerGpx
import ykws.android.maro.data.model.ImportMode
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.MarkerGeometry
import ykws.android.maro.data.model.markers.MarkerOrigin
import ykws.android.maro.data.model.markers.UserMarker

/**
 * The trip carriage — a track's own markers riding its exported file, and the four rules that keep the
 * link honest on the way back in: the re-point to the imported track's id, the skip-consistency, the
 * keeper filter, and the refusal a marker file gets at this door.
 */
class TrackMarkerCarriageTest {

    private fun trip(id: String = "t-1") = Track(
        id = id,
        name = "Trip",
        startTimeMs = 1_700_000_000_000L,
        trackPoints = listOf(
            TrackPoint(lat = 43.5, lon = 7.2, timeOffsetMs = 0L),
            TrackPoint(lat = 43.6, lon = 7.3, timeOffsetMs = 60_000L)
        )
    )

    private fun ownedMarker(
        id: String = "m-1",
        trackId: String? = "t-1",
        keepable: Boolean = true
    ) = UserMarker(
        id = id,
        name = "Mouillage",
        geometry = MarkerGeometry.Pin(LatLng(43.55, 7.25)),
        description = "stop",
        confirmed = keepable,
        createdAtEpochMs = 1_700_000_050_000L,
        origin = MarkerOrigin.IDLE_AUTO,
        trackId = trackId,
        keepable = keepable
    )

    private fun importTrip(
        bytes: ByteArray,
        names: Set<String> = emptySet(),
        ids: Set<String> = emptySet(),
        mode: ImportMode = ImportMode.SKIP_EXISTING
    ) = GpxImporter.import(bytes, "gpx", names, ids, emptyMap(), mode)

    @Test
    fun `a trip carries its own markers and keeps their link`() {
        val marker = ownedMarker()
        val xml = trip().toGpx(listOf(marker))

        val batch = importTrip(xml.toByteArray())

        assertEquals(1, batch.imported)
        assertEquals("t-1", batch.tracks.single().id)
        assertEquals(1, batch.markers.size)
        assertEquals(marker, batch.markers.single())
        assertEquals("t-1", batch.markers.single().trackId)
    }

    @Test
    fun `a trip file also carries the stops as plain waypoints`() {
        val xml = trip().toGpx(listOf(ownedMarker()))

        assertTrue("the readable stop reaches other applications", xml.contains("<wpt lat=\"43.55\" lon=\"7.25\">"))
        assertTrue(xml.contains("<name>Mouillage</name>"))
        // GPX requires every wpt ahead of the trk, so the readable form is schema-legal too.
        assertTrue(xml.indexOf("<wpt") < xml.indexOf("<trk>"))
    }

    @Test
    fun `import as new re-points every bundled marker at the minted track`() {
        val xml = trip().toGpx(listOf(ownedMarker()))

        val batch = importTrip(xml.toByteArray(), names = setOf("Trip"), mode = ImportMode.IMPORT_NEW)

        val importedTrack = batch.tracks.single()
        assertNotEquals("t-1", importedTrack.id)
        assertEquals(1, batch.markers.size)
        assertEquals(importedTrack.id, batch.markers.single().trackId)
        // A copy of a trip is a new trip: its markers are minted too, never the original's (N07).
        assertNotEquals("m-1", batch.markers.single().id)
    }

    @Test
    fun `a skipped duplicate carries no markers`() {
        val xml = trip().toGpx(listOf(ownedMarker()))

        val batch = importTrip(xml.toByteArray(), ids = setOf("t-1"))

        assertEquals(0, batch.imported)
        assertEquals(1, batch.ignored)
        assertTrue("nothing may point at a track that was never written", batch.markers.isEmpty())
    }

    @Test
    fun `a transient idle marker is not carried`() {
        val transient = ownedMarker(id = "temp", keepable = false)
        val keeper = ownedMarker(id = "keep")
        val xml = trip().toGpx(listOf(transient, keeper))

        val batch = importTrip(xml.toByteArray())

        assertEquals(1, batch.markers.size)
        assertEquals("keep", batch.markers.single().id)
        assertTrue("a transient marker would be deleted by the startup cleanup", batch.markers.single().keepable)
    }

    @Test
    fun `a marker file is not a trip file`() {
        val markerFile = UserMarker(
            id = "m-9",
            name = "Alone",
            geometry = MarkerGeometry.Pin(LatLng(43.5, 7.2)),
            createdAtEpochMs = 1_700_000_000_000L
        )

        val batch = importTrip(markerFile.toMarkerGpx().toByteArray())

        assertEquals("a waypoint-only file has no track to import", 0, batch.imported)
        assertTrue(batch.tracks.isEmpty())
    }
}
