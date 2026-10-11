package ykws.android.maro.data.markers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
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
 * The marker GPX transfer — what [MarkerGpxExporter] writes and [MarkerGpxImporter] reads back.
 *
 * Every case here runs on the JVM with no Android API and no platform XML parser, which is the point
 * of the codec's `java.util.Base64` and the importer's own element scan: a wire format's guard has to
 * reach the artefact, not a description of it.
 */
class MarkerGpxTransferTest {

    private val amp = "&"

    private fun sampleMarker(
        id: String = "m-1",
        name: String = "Anse",
        description: String = "mouillage calme",
        origin: MarkerOrigin = MarkerOrigin.USER,
        trackId: String? = null,
        keepable: Boolean = true,
        confirmed: Boolean = true
    ) = UserMarker(
        id = id,
        name = name,
        geometry = MarkerGeometry.Corridor(LatLng(43.50, 7.10), LatLng(43.52, 7.16), widthM = 80.0),
        description = description,
        proximityOverrideM = 250.0,
        confirmed = confirmed,
        colorIndex = 7,
        icon = "\uD83D\uDD50",
        createdAtEpochMs = 1_700_000_000_000L,
        origin = origin,
        trackId = trackId,
        keepable = keepable,
        pinned = true,
        updatedAtEpochMs = 1_700_000_100_000L,
        routingCost = 4,
        routeOrigin = true,
        routeDestination = false,
        routingOnly = true
    )

    private fun importFile(
        bytes: ByteArray,
        names: Set<String> = emptySet(),
        ids: Set<String> = emptySet(),
        idByName: Map<String, String> = emptyMap(),
        mode: ImportMode = ImportMode.SKIP_EXISTING
    ) = MarkerGpxImporter.import(bytes, "gpx", names, ids, idByName, mode)

    /** A file another application wrote: one waypoint, no MaroII payload. */
    private fun waypointFile(name: String?, description: String? = null): ByteArray = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        append("<gpx version=\"1.1\" creator=\"Other\" xmlns=\"http://www.topografix.com/GPX/1/1\">")
        append("<wpt lat=\"43.5\" lon=\"7.2\">")
        if (name != null) append("<name>$name</name>")
        if (description != null) append("<desc>$description</desc>")
        append("</wpt></gpx>")
    }.toByteArray()

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
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
    fun `a marker survives the round trip through its payload`() {
        val original = sampleMarker()

        val batch = importFile(original.toMarkerGpx().toByteArray())

        assertEquals(1, batch.imported)
        assertEquals(0, batch.ignored)
        assertEquals(original, batch.markers.single())
    }

    @Test
    fun `the readable mirror alone rebuilds the marker when the blob is stripped`() {
        val original = sampleMarker()
        val stripped = original.toMarkerGpx()
            .replace(Regex("""<maro:markerData>.*?</maro:markerData>""", RegexOption.DOT_MATCHES_ALL), "")
        assertFalse("the blob must be gone", stripped.contains("maro:markerData"))

        val batch = importFile(stripped.toByteArray())

        assertEquals(original, batch.markers.single())
    }

    @Test
    fun `a second import of the same file is skipped by id`() {
        val imported = importFile(sampleMarker().toMarkerGpx().toByteArray()).markers.single()

        val second = importFile(
            sampleMarker().toMarkerGpx().toByteArray(),
            names = setOf(imported.name),
            ids = setOf(imported.id)
        )

        assertEquals(0, second.imported)
        assertEquals(1, second.ignored)
    }

    @Test
    fun `import as new mints a fresh id and a distinct name`() {
        val original = sampleMarker()

        val batch = importFile(original.toMarkerGpx().toByteArray(), names = setOf(original.name), mode = ImportMode.IMPORT_NEW)

        val imported = batch.markers.single()
        assertNotEquals(original.id, imported.id)
        assertEquals("Anse (2)", imported.name)
    }

    @Test
    fun `override keeps the matched id so links to the marker survive`() {
        val original = sampleMarker()

        val batch = importFile(original.toMarkerGpx().toByteArray(), ids = setOf(original.id), mode = ImportMode.UPDATE_EXISTING)

        assertEquals(original.id, batch.markers.single().id)
    }

    @Test
    fun `a foreign waypoint becomes a keeper pin`() {
        val bytes = waypointFile(name = "Cale " + amp + "amp; quai", description = "escale")

        val batch = importFile(bytes)

        val imported = batch.markers.single()
        assertEquals("Cale " + amp + " quai", imported.name)
        assertEquals("escale", imported.description)
        assertTrue("a plain waypoint is a pin", imported.geometry is MarkerGeometry.Pin)
        assertEquals(LatLng(43.5, 7.2), (imported.geometry as MarkerGeometry.Pin).position)
        assertTrue("it must survive the startup cleanup", imported.keepable)
        assertTrue(imported.confirmed)
        assertEquals(MarkerOrigin.USER, imported.origin)
    }

    @Test
    fun `a foreign waypoint updates by name onto the existing id`() {
        val bytes = waypointFile(name = "Cale")

        val batch = importFile(bytes, names = setOf("Cale"), idByName = mapOf("Cale" to "kept-id"), mode = ImportMode.UPDATE_EXISTING)

        assertEquals("kept-id", batch.markers.single().id)
    }

    @Test
    fun `a waypoint with no name is refused rather than guessed at`() {
        val batch = importFile(waypointFile(name = null))

        assertEquals(0, batch.imported)
        assertEquals(0, batch.ignored)
        assertTrue(batch.markers.isEmpty())
    }

    @Test
    fun `a trip file is refused at the marker door`() {
        val trip = buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
            append("<gpx version=\"1.1\" creator=\"Maro II\" xmlns=\"http://www.topografix.com/GPX/1/1\">")
            append("<trk><name>trip</name><trkseg><trkpt lat=\"43.5\" lon=\"7.2\"><time>2026-01-01T00:00:00.000Z</time></trkpt></trkseg></trk>")
            append("</gpx>")
        }.toByteArray()

        val batch = importFile(trip)

        assertEquals(0, batch.imported)
        assertEquals(0, batch.ignored)
        assertTrue(batch.markers.isEmpty())
        assertNull(MarkerGpxImporter.peekImportMatch(trip, "gpx", emptySet(), emptyMap()))
    }

    @Test
    fun `an unowned auto marker travels in the backup`() {
        val auto = sampleMarker(
            id = "a-1",
            name = "2026-07-01",
            origin = MarkerOrigin.IDLE_AUTO,
            trackId = null,
            keepable = false,
            confirmed = false
        )

        val imported = importFile(auto.toMarkerGpx().toByteArray()).markers.single()

        assertEquals(auto, imported)
        assertEquals(MarkerOrigin.IDLE_AUTO, imported.origin)
        assertNull(imported.trackId)
    }

    @Test
    fun `a dangling track id is kept verbatim`() {
        val owned = sampleMarker(id = "a-2", trackId = "a-track-that-is-not-here")

        val imported = importFile(owned.toMarkerGpx().toByteArray()).markers.single()

        assertEquals("a-track-that-is-not-here", imported.trackId)
    }

    @Test
    fun `a zip skips what is held and refuses a traversing entry`() {
        val held = sampleMarker(id = "held")
        val fresh = sampleMarker(id = "fresh", name = "Fresh")
        val evil = sampleMarker(id = "evil", name = "Evil")

        val zip = zipOf(
            "held.gpx" to held.toMarkerGpx().toByteArray(),
            "fresh.gpx" to fresh.toMarkerGpx().toByteArray(),
            "../evil.gpx" to evil.toMarkerGpx().toByteArray()
        )

        val batch = MarkerGpxImporter.import(zip, "zip", emptySet(), setOf(held.id))

        assertEquals(1, batch.imported)
        assertEquals(1, batch.ignored)
        assertEquals("fresh", batch.markers.single().id)
    }

    @Test
    fun `a description that would close the cdata section survives`() {
        val tricky = sampleMarker(description = "avant ]]> apres")

        val imported = importFile(tricky.toMarkerGpx().toByteArray()).markers.single()

        assertEquals("avant ]]> apres", imported.description)
    }

    @Test
    fun `a blank name emits no name element and still round trips by id`() {
        val nameless = sampleMarker(name = "")

        val xml = nameless.toMarkerGpx()
        assertFalse("no empty name element", xml.contains("<name>"))
        assertEquals(nameless, importFile(xml.toByteArray()).markers.single())
    }

    @Test
    fun `the peek reports the marker already held`() {
        val held = sampleMarker()

        val match = MarkerGpxImporter.peekImportMatch(held.toMarkerGpx().toByteArray(), "gpx", setOf(held.id), emptyMap())

        assertEquals(MarkerImportMatch(held.id, held.name), match)
    }
}
