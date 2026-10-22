package ykws.android.maro.data.track

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.ImportMode
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.MarkerGeometry
import ykws.android.maro.data.model.markers.MarkerOrigin
import ykws.android.maro.data.model.markers.UserMarker
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The importer's safety rules — an untrusted id never names a file (N06), a copy of a trip never
 * re-points the original's markers (N07), an archive past its budget is counted rather than read or
 * thrown (N08), and the count of what was refused reaches the result (U17).
 *
 * Every case rides the Maro-blob path: the standard `<trkpt>` parser has no JVM implementation here.
 */
class GpxImporterSafetyTest {

    private fun trip(id: String = "t-1", markerId: String = "m-1") = Track(
        id = id,
        name = "Trip",
        startTimeMs = 1_700_000_000_000L,
        trackPoints = listOf(
            TrackPoint(lat = 43.5, lon = 7.2, timeOffsetMs = 0L),
            TrackPoint(lat = 43.6, lon = 7.3, timeOffsetMs = 60_000L)
        ),
        boatMarkers = listOf(
            BoatMarker(
                trigger = BoatMarkerTrigger.IDLE,
                startTimeMs = 1_700_000_050_000L,
                markers = listOf(
                    MarkerSnapshot(
                        markerId = markerId,
                        name = "Mouillage",
                        geometryType = "Pin",
                        lat = 43.55,
                        lon = 7.25,
                        distanceNm = 0.1,
                        bearingDeg = 90.0
                    )
                )
            )
        )
    )

    private fun ownedMarker(id: String = "m-1", trackId: String = "t-1") = UserMarker(
        id = id,
        name = "Mouillage",
        geometry = MarkerGeometry.Pin(LatLng(43.55, 7.25)),
        description = "stop",
        createdAtEpochMs = 1_700_000_050_000L,
        origin = MarkerOrigin.IDLE_AUTO,
        trackId = trackId,
        keepable = true
    )

    private fun importTrip(
        bytes: ByteArray,
        extension: String = "gpx",
        names: Set<String> = emptySet(),
        ids: Set<String> = emptySet(),
        mode: ImportMode = ImportMode.SKIP_EXISTING
    ) = GpxImporter.import(bytes, extension, names, ids, emptyMap(), mode)

    private fun zipOf(entries: List<Pair<String, ByteArray>>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            entries.forEach { (name, bytes) ->
                zos.putNextEntry(ZipEntry(name))
                zos.write(bytes)
                zos.closeEntry()
            }
        }
        return out.toByteArray()
    }

    @Test
    fun `importing a trip as new twice mints distinct marker ids and re-points the snapshots`() {
        val xml = trip().toGpx(listOf(ownedMarker())).toByteArray()

        val first = importTrip(xml, mode = ImportMode.IMPORT_NEW)
        val second = importTrip(xml, names = setOf(first.tracks.single().name), mode = ImportMode.IMPORT_NEW)

        val tracks = listOf(first, second).map { it.tracks.single() }
        val markers = listOf(first, second).map { it.markers.single() }
        assertNotEquals(tracks[0].id, tracks[1].id)
        assertNotEquals("the original's marker is never re-pointed", "m-1", markers[0].id)
        assertNotEquals("the two copies never share a marker", markers[0].id, markers[1].id)
        for (i in 0..1) {
            assertEquals(tracks[i].id, markers[i].trackId)
            assertEquals(
                "the stop snapshot names the minted marker",
                markers[i].id,
                tracks[i].boatMarkers.single().markers.single().markerId
            )
        }
    }

    @Test
    fun `a skip import keeps the marker and snapshot ids it was given`() {
        val xml = trip().toGpx(listOf(ownedMarker())).toByteArray()

        val batch = importTrip(xml)

        assertEquals("m-1", batch.markers.single().id)
        assertEquals("m-1", batch.tracks.single().boatMarkers.single().markers.single().markerId)
    }

    @Test
    fun `a blob id that is not a file name lands under a minted id in every mode`() {
        val xml = trip(id = "../escaped").toGpx(listOf(ownedMarker(trackId = "../escaped"))).toByteArray()

        for (mode in ImportMode.entries) {
            val batch = importTrip(xml, ids = setOf("../escaped"), mode = mode)

            assertEquals("$mode imports the track", 1, batch.imported)
            val track = batch.tracks.single()
            assertTrue("$mode mints a safe id", TrackRepository.isSafeId(track.id))
            assertEquals("$mode re-points the marker", track.id, batch.markers.single().trackId)
        }
    }

    @Test
    fun `an archive past the entry budget counts the excess as invalid without throwing`() {
        val excess = 3
        val entries = (1..GpxImporter.MAX_ZIP_ENTRIES + excess).map { i ->
            "trip-$i.gpx" to trip(id = "t-$i").toGpx(emptyList()).toByteArray()
        }

        val batch = importTrip(zipOf(entries), extension = "zip")

        assertEquals(GpxImporter.MAX_ZIP_ENTRIES, batch.imported)
        assertEquals(0, batch.ignored)
        assertEquals(excess, batch.invalid)
        assertEquals(excess, batch.result.invalid)
    }

    @Test
    fun `an unreadable file is counted rather than hidden`() {
        val batch = importTrip("<gpx><maro:data>not base64 at all</maro:data></gpx>".toByteArray())

        assertEquals(0, batch.imported)
        assertEquals(1, batch.invalid)
        assertEquals(ImportResult(imported = 0, ignored = 0, invalid = 1), batch.result)
    }
}
