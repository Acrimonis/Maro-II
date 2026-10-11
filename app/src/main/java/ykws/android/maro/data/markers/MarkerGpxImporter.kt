package ykws.android.maro.data.markers

import ykws.android.maro.data.model.ImportMode
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.MarkerGeometry
import ykws.android.maro.data.model.markers.MarkerOrigin
import ykws.android.maro.data.model.markers.UserMarker
import ykws.android.maro.data.track.GpxImporter
import ykws.android.maro.data.track.unescapeXml
import java.util.UUID
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

/**
 * Import markers from a GPX file or a ZIP archive of GPX files — the reader [MarkerGpxExporter]
 * writes for.
 *
 * **Dispatch:** a single `.gpx` imports one marker and may ask what to do about a match; a `.zip`
 * extracts every `.gpx` entry and imports each with [ImportMode.SKIP_EXISTING], so a re-import is
 * idempotent and never prompts, exactly as the tracks' archive does.
 *
 * **Identity:** a marker's identity is its `id` when the file carries a Maro payload — the protobuf
 * blob first, the JSON mirror second — else the waypoint's `name` for a file from another
 * application. [ImportMode] decides what happens on a match. A name is not unique in the marker
 * store, so a name match is first-wins and only the id path is a true identity.
 *
 * **A file that carries a `<trk>` is not a marker file** and is refused rather than read: a trip file
 * opened at this door writes nothing. The reverse holds too, since the track reader looks only at
 * `<trk>` elements — a marker file opened there imports nothing.
 *
 * **No platform parser.** The Maro payloads are read by element scan, and a foreign waypoint — the
 * fallback path, for a file another application wrote — by one narrow attributes-and-children read.
 * That keeps the whole transfer reachable from the JVM unit tests, which the Android XML parser is
 * not, and the failure mode is a refusal rather than a wrong marker.
 */
object MarkerGpxImporter {

    /** Outcome of a single-file import attempt. */
    private sealed interface SingleOutcome {
        data class Imported(val marker: UserMarker) : SingleOutcome
        data object SkippedDuplicate : SingleOutcome
        data object Invalid : SingleOutcome
    }

    /** A waypoint read from a file with no MaroII payload — the interoperable, lossy path. */
    private data class Waypoint(val position: LatLng, val name: String?, val description: String?)

    private val trackElement = Regex("""<trk[\s>]""")
    private val waypointOpen = Regex("""<wpt\b[^>]*>""")
    private val latitude = Regex("""\blat\s*=\s*"([^"]+)"""")
    private val longitude = Regex("""\blon\s*=\s*"([^"]+)"""")
    private val nameElement = Regex("""<name>(.*?)</name>""", RegexOption.DOT_MATCHES_ALL)
    private val descElement = Regex("""<desc>(.*?)</desc>""", RegexOption.DOT_MATCHES_ALL)

    /**
     * Import markers from a byte array.
     *
     * @param input            The file or stream bytes (GPX or ZIP).
     * @param extension        File extension hint ("gpx" or "zip").
     * @param existingNames    Names already held, for the foreign identity and the collision rule.
     * @param existingIds      Ids already held, for the payload identity.
     * @param existingIdByName Name → id lookup, so a foreign update can keep the existing id.
     * @param mode             How to treat a match. A ZIP is always [ImportMode.SKIP_EXISTING].
     */
    fun import(
        input: ByteArray,
        extension: String,
        existingNames: Set<String>,
        existingIds: Set<String>,
        existingIdByName: Map<String, String> = emptyMap(),
        mode: ImportMode = ImportMode.SKIP_EXISTING
    ): MarkerImportBatch = when (extension.lowercase()) {
        "zip" -> importZip(input, existingNames, existingIds, existingIdByName)
        else -> when (val outcome = importSingle(input, existingNames, existingIds, existingIdByName, mode)) {
            is SingleOutcome.Imported -> MarkerImportBatch(listOf(outcome.marker), 1, 0)
            SingleOutcome.SkippedDuplicate -> MarkerImportBatch(emptyList(), 0, 1)
            SingleOutcome.Invalid -> MarkerImportBatch(emptyList(), 0, 0)
        }
    }

    /**
     * Peek at a single file and report whether it matches a marker already held: the matched marker's
     * id and name, or null when there is no match or the input is a ZIP, which never prompts.
     */
    fun peekImportMatch(
        input: ByteArray,
        extension: String,
        existingIds: Set<String>,
        existingIdByName: Map<String, String>
    ): MarkerImportMatch? {
        if (extension.lowercase() == "zip") return null
        return try {
            val xml = String(input, Charsets.UTF_8)
            if (carriesTrack(xml)) return null
            val marker = markerOfPayload(xml)
            if (marker != null) {
                if (marker.id in existingIds) MarkerImportMatch(marker.id, marker.name) else null
            } else {
                val waypoint = parseWaypoint(xml) ?: return null
                val name = waypoint.name ?: return null
                val existingId = existingIdByName[name]
                if (existingId != null) MarkerImportMatch(existingId, name) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Import a ZIP archive of `.gpx` files, always skipping what is already held. Guards against path
     * traversal, which it refuses by name before reading an entry.
     */
    private fun importZip(
        input: ByteArray,
        existingNames: Set<String>,
        existingIds: Set<String>,
        existingIdByName: Map<String, String>
    ): MarkerImportBatch {
        val markers = mutableListOf<UserMarker>()
        var ignored = 0
        val nameSet = existingNames.toMutableSet()
        val idSet = existingIds.toMutableSet()
        val idByName = existingIdByName.toMutableMap()
        try {
            ZipInputStream(input.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val entryName = entry.name
                    if (entryName.contains("..") || entryName.startsWith("/")) {
                        entry = zis.nextEntry
                        continue
                    }
                    if (entryName.endsWith(".gpx", ignoreCase = true) && !entry.isDirectory) {
                        when (val outcome = importSingle(zis.readBytes(), nameSet, idSet, idByName, ImportMode.SKIP_EXISTING)) {
                            is SingleOutcome.Imported -> {
                                markers.add(outcome.marker)
                                nameSet.add(outcome.marker.name)
                                idSet.add(outcome.marker.id)
                                idByName[outcome.marker.name] = outcome.marker.id
                            }
                            SingleOutcome.SkippedDuplicate -> ignored++
                            SingleOutcome.Invalid -> Unit
                        }
                    }
                    entry = zis.nextEntry
                }
            }
        } catch (_: ZipException) {
            // Corrupt archive — surface the partial result rather than propagating.
        }
        return MarkerImportBatch(markers, markers.size, ignored)
    }

    /** Parse one file and report its outcome. */
    private fun importSingle(
        input: ByteArray,
        existingNames: Set<String>,
        existingIds: Set<String>,
        existingIdByName: Map<String, String>,
        mode: ImportMode
    ): SingleOutcome {
        return try {
            val xml = String(input, Charsets.UTF_8)
            if (carriesTrack(xml)) return SingleOutcome.Invalid

            val payload = markerOfPayload(xml)
            if (payload != null) {
                when (mode) {
                    ImportMode.SKIP_EXISTING ->
                        if (payload.id in existingIds) SingleOutcome.SkippedDuplicate
                        else SingleOutcome.Imported(payload)
                    ImportMode.IMPORT_NEW ->
                        SingleOutcome.Imported(payload.freshCopy(existingNames))
                    ImportMode.UPDATE_EXISTING ->
                        if (payload.id in existingIds) SingleOutcome.Imported(payload)
                        else SingleOutcome.Imported(payload.freshCopy(existingNames))
                }
            } else {
                val waypoint = parseWaypoint(xml) ?: return SingleOutcome.Invalid
                val name = waypoint.name ?: return SingleOutcome.Invalid
                when (mode) {
                    ImportMode.SKIP_EXISTING ->
                        if (name in existingNames) SingleOutcome.SkippedDuplicate
                        else SingleOutcome.Imported(foreignMarker(waypoint, name))
                    ImportMode.IMPORT_NEW ->
                        SingleOutcome.Imported(
                            foreignMarker(waypoint, GpxImporter.resolveNameCollision(name, existingNames))
                        )
                    ImportMode.UPDATE_EXISTING -> {
                        val existingId = existingIdByName[name]
                        SingleOutcome.Imported(
                            if (existingId != null) foreignMarker(waypoint, name, existingId)
                            else foreignMarker(waypoint, GpxImporter.resolveNameCollision(name, existingNames))
                        )
                    }
                }
            }
        } catch (_: Exception) {
            SingleOutcome.Invalid
        }
    }

    /**
     * The marker a file carries, read from its payloads: the protobuf blob first — the lossless path —
     * and the readable JSON mirror when the blob is absent or unreadable. Both are read through
     * [MarkerPayload], which is also what the repository writes, so a marker this importer rebuilds is
     * the marker the store holds.
     */
    private fun markerOfPayload(xml: String): UserMarker? {
        MarkerPayload.elementOf(xml, "maro:markerData")?.let { blob ->
            MarkerPayload.markerFromBlob(blob)?.let { return it }
        }
        MarkerPayload.elementOf(xml, "maro:markerJson")?.let { mirror ->
            return MarkerPayload.markerFromJson(MarkerPayload.readCdata(mirror))
        }
        return null
    }

    /** True when the document carries a track element, which makes it a trip file, not a marker file. */
    private fun carriesTrack(xml: String): Boolean = trackElement.containsMatchIn(xml)

    /**
     * Read the first `<wpt>` of a file with no MaroII payload: its `lat`/`lon` attributes and its
     * `<name>` and `<desc>` children, entity-decoded. Returns null when the file carries no waypoint
     * with usable coordinates, which the importer reports as nothing imported rather than guessing.
     */
    private fun parseWaypoint(xml: String): Waypoint? {
        val open = waypointOpen.find(xml) ?: return null
        val attributes = open.value
        val lat = latitude.find(attributes)?.groupValues?.get(1)?.toDoubleOrNull() ?: return null
        val lon = longitude.find(attributes)?.groupValues?.get(1)?.toDoubleOrNull() ?: return null
        val bodyEnd = xml.indexOf("</wpt>", open.range.last + 1)
        val body = xml.substring(open.range.last + 1, if (bodyEnd > 0) bodyEnd else xml.length)
        val name = nameElement.find(body)?.groupValues?.get(1)?.let { unescapeXml(it).trim() }
        val description = descElement.find(body)?.groupValues?.get(1)?.let { unescapeXml(it).trim() }
        return Waypoint(LatLng(lat, lon), name?.takeIf { it.isNotBlank() }, description)
    }

    /**
     * A marker born from a plain waypoint: a Pin, a keeper, and the user's own marker rather than an
     * auto one.
     *
     * `keepable` is not decoration: the startup cleanup deletes every `IDLE_AUTO` marker that is not
     * keepable, so a waypoint imported as an auto marker would be gone at the next launch.
     */
    private fun foreignMarker(waypoint: Waypoint, name: String, id: String = UUID.randomUUID().toString()): UserMarker {
        val now = System.currentTimeMillis()
        return UserMarker(
            id = id,
            name = name,
            geometry = MarkerGeometry.Pin(waypoint.position),
            description = waypoint.description ?: "",
            confirmed = true,
            origin = MarkerOrigin.USER,
            keepable = true,
            createdAtEpochMs = now,
            updatedAtEpochMs = now
        )
    }

    /** A payload marker as a new entity: a fresh id and a name made distinct from the ones held. */
    private fun UserMarker.freshCopy(existingNames: Set<String>): UserMarker = copy(
        id = UUID.randomUUID().toString(),
        name = GpxImporter.resolveNameCollision(name, existingNames)
    )
}

/** Aggregate import counts: markers persisted versus duplicates skipped. */
data class MarkerImportResult(val imported: Int, val ignored: Int)

/** Full batch result: the imported markers plus aggregate counts. */
data class MarkerImportBatch(val markers: List<UserMarker>, val imported: Int, val ignored: Int) {
    val result: MarkerImportResult get() = MarkerImportResult(imported, ignored)
}

/** Result of a single-file match peek: the marker already held that would be affected. */
data class MarkerImportMatch(val id: String, val name: String)
