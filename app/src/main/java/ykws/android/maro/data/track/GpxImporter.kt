package ykws.android.maro.data.track

import android.util.Base64
import kotlinx.serialization.protobuf.ProtoBuf
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

/**
 * Import tracks from a GPX file or a ZIP archive of GPX files.
 *
 * **Dispatch:**
 * - Single `.gpx` → import one track
 * - `.zip` archive → extract all `.gpx` files, import each (always [ImportMode.SKIP_EXISTING])
 *
 * **Identity:** a track's identity is its `id` when a `<maro:data>` blob is present,
 * else its `name` (foreign GPX). [ImportMode] decides what happens on a match.
 */
object GpxImporter {

    private val proto = ProtoBuf.Default

    /** Outcome of a single-GPX import attempt. */
    private sealed interface SingleImportOutcome {
        data class Imported(val track: Track) : SingleImportOutcome
        data object SkippedDuplicate : SingleImportOutcome
        data object Invalid : SingleImportOutcome
    }

    /**
     * Import tracks from a byte array. Dispatch based on content type.
     *
     * @param input            The file/stream bytes (GPX or ZIP).
     * @param extension        File extension hint ("gpx" or "zip").
     * @param existingNames    Set of existing track names (anti-collision / foreign match).
     * @param existingIds      Set of existing track ids (Maro blob match).
     * @param existingIdByName Existing name → id lookup, so a foreign UPDATE can keep the existing id.
     * @param mode             How to treat matches. ZIP input is always [ImportMode.SKIP_EXISTING].
     * @return Full batch result: imported tracks plus aggregate counts.
     */
    fun import(
        input: ByteArray,
        extension: String,
        existingNames: Set<String>,
        existingIds: Set<String>,
        existingIdByName: Map<String, String> = emptyMap(),
        mode: ImportMode = ImportMode.SKIP_EXISTING
    ): ImportBatch {
        return when (extension.lowercase()) {
            "zip" -> importZip(input, existingNames, existingIds, existingIdByName)
            else -> when (val outcome = importSingleGpx(input, existingNames, existingIds, existingIdByName, mode)) {
                is SingleImportOutcome.Imported -> ImportBatch(listOf(outcome.track), 1, 0)
                SingleImportOutcome.SkippedDuplicate -> ImportBatch(emptyList(), 0, 1)
                SingleImportOutcome.Invalid -> ImportBatch(emptyList(), 0, 0)
            }
        }
    }

    /**
     * Peek at a single GPX input and report whether it matches an existing track.
     * Returns the matched track's id + name, or null when there is no match or the
     * input is a ZIP (batch imports never prompt).
     */
    fun peekImportMatch(
        input: ByteArray,
        extension: String,
        existingIds: Set<String>,
        existingIdByName: Map<String, String>
    ): ImportMatch? {
        if (extension.lowercase() == "zip") return null
        return try {
            val xml = String(input, Charsets.UTF_8)
            val maroBlob = extractMaroBlob(xml)
            if (maroBlob != null) {
                val track = proto.decodeFromByteArray(Track.serializer(), maroBlob)
                if (track.id in existingIds) ImportMatch(track.id, track.name) else null
            } else {
                val name = parseStandardGpxData(input)?.name ?: return null
                val existingId = existingIdByName[name]
                if (existingId != null) ImportMatch(existingId, name) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Import tracks from a ZIP archive containing `.gpx` files.
     * Always uses [ImportMode.SKIP_EXISTING] (idempotent re-import, no dialog).
     * Guards against path traversal (rejects `../` and absolute paths).
     */
    private fun importZip(
        input: ByteArray,
        existingNames: Set<String>,
        existingIds: Set<String>,
        existingIdByName: Map<String, String>
    ): ImportBatch {
        val tracks = mutableListOf<Track>()
        var ignored = 0
        val nameSet = existingNames.toMutableSet()
        val idSet = existingIds.toMutableSet()
        val idByName = existingIdByName.toMutableMap()
        try {
            ZipInputStream(input.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val entryName = entry.name
                    // Path traversal guard
                    if (entryName.contains("..") || entryName.startsWith("/")) {
                        entry = zis.nextEntry
                        continue
                    }
                    if (entryName.endsWith(".gpx", ignoreCase = true) && !entry.isDirectory) {
                        when (val outcome = importSingleGpx(zis.readBytes(), nameSet, idSet, idByName, ImportMode.SKIP_EXISTING)) {
                            is SingleImportOutcome.Imported -> {
                                tracks.add(outcome.track)
                                nameSet.add(outcome.track.name)
                                idSet.add(outcome.track.id)
                                idByName[outcome.track.name] = outcome.track.id
                            }
                            SingleImportOutcome.SkippedDuplicate -> ignored++
                            SingleImportOutcome.Invalid -> Unit
                        }
                    }
                    entry = zis.nextEntry
                }
            }
        } catch (_: ZipException) {
            // Corrupt archive — surface the partial result instead of propagating.
        }
        return ImportBatch(tracks, tracks.size, ignored)
    }

    /**
     * Parse a single GPX file and report the outcome: [SingleImportOutcome.Imported],
     * [SingleImportOutcome.SkippedDuplicate], or [SingleImportOutcome.Invalid].
     */
    private fun importSingleGpx(
        input: ByteArray,
        existingNames: Set<String>,
        existingIds: Set<String>,
        existingIdByName: Map<String, String> = emptyMap(),
        mode: ImportMode = ImportMode.SKIP_EXISTING
    ): SingleImportOutcome {
        return try {
            val xml = String(input, Charsets.UTF_8)

            // Try MaroII extension blob first (lossless round-trip, carries the Track id).
            val maroBlob = extractMaroBlob(xml)
            if (maroBlob != null) {
                val decoded = proto.decodeFromByteArray(Track.serializer(), maroBlob)
                // Normalize lastPointTimeMs for exports saved before the field existed.
                val track = if (decoded.endTimeMs != null && decoded.lastPointTimeMs == 0L) {
                    decoded.copy(lastPointTimeMs = decoded.lastRealPointTimeMsOrNull() ?: 0L)
                } else decoded

                when (mode) {
                    ImportMode.SKIP_EXISTING -> {
                        if (track.id in existingIds) SingleImportOutcome.SkippedDuplicate
                        else SingleImportOutcome.Imported(track)
                    }
                    ImportMode.IMPORT_NEW -> {
                        val safeName = resolveNameCollision(track.name, existingNames)
                        SingleImportOutcome.Imported(track.copy(id = UUID.randomUUID().toString(), name = safeName))
                    }
                    ImportMode.UPDATE_EXISTING -> {
                        if (track.id in existingIds) {
                            SingleImportOutcome.Imported(updateTrackFromBlob(track, input))
                        } else {
                            val safeName = resolveNameCollision(track.name, existingNames)
                            SingleImportOutcome.Imported(track.copy(id = UUID.randomUUID().toString(), name = safeName))
                        }
                    }
                }
            } else {
                // Foreign GPX — parse standard elements (lossy, fresh id).
                val std = parseStandardGpxData(input) ?: return SingleImportOutcome.Invalid
                val name = std.name ?: return SingleImportOutcome.Invalid
                when (mode) {
                    ImportMode.SKIP_EXISTING -> {
                        if (name in existingNames) SingleImportOutcome.SkippedDuplicate
                        else SingleImportOutcome.Imported(buildForeignTrack(std, name))
                    }
                    ImportMode.IMPORT_NEW -> {
                        val safeName = resolveNameCollision(name, existingNames)
                        SingleImportOutcome.Imported(buildForeignTrack(std, safeName))
                    }
                    ImportMode.UPDATE_EXISTING -> {
                        val existingId = existingIdByName[name]
                        if (existingId != null) {
                            // Update by name — keep the existing track's id.
                            SingleImportOutcome.Imported(buildForeignTrack(std, name).copy(id = existingId))
                        } else {
                            val safeName = resolveNameCollision(name, existingNames)
                            SingleImportOutcome.Imported(buildForeignTrack(std, safeName))
                        }
                    }
                }
            }
        } catch (_: Exception) {
            SingleImportOutcome.Invalid
        }
    }

    /**
     * UPDATE a Maro-blob track whose id matched: prefer standard `<trkpt>` points
     * when present (the human-editable part); metadata (name/comment/color/pinned/
     * boatMarkers) stays from the blob; derived stats are recomputed from the final points.
     */
    private fun updateTrackFromBlob(blob: Track, input: ByteArray): Track {
        val std = parseStandardGpxData(input)
        val updated = if (std != null && std.points.isNotEmpty()) {
            val firstMs = std.firstTimeMs ?: blob.startTimeMs
            val lastOffset = std.points.lastOrNull { it.type != PointType.GAP }?.timeOffsetMs ?: 0L
            blob.copy(
                trackPoints = std.points,
                startTimeMs = firstMs,
                endTimeMs = firstMs + lastOffset
            )
        } else {
            blob
        }
        return updated.withDerivedStats()
    }

    /**
     * Extract the base64-encoded protobuf blob from `<maro:data>` in the GPX XML.
     */
    internal fun extractMaroBlob(xml: String): ByteArray? {
        val dataStart = xml.indexOf("<maro:data>")
        if (dataStart < 0) return null
        val contentStart = dataStart + "<maro:data>".length
        val dataEnd = xml.indexOf("</maro:data>", contentStart)
        if (dataEnd < 0) return null
        val base64 = xml.substring(contentStart, dataEnd).trim()
        return try {
            Base64.decode(base64, Base64.DEFAULT)
        } catch (_: Exception) {
            null
        }
    }

    /** Raw data extracted from a standard (foreign) GPX `<trk>` element. */
    private data class StandardGpx(
        val name: String?,
        val comment: String,
        val points: List<TrackPoint>,
        val firstTimeMs: Long?
    )

    /**
     * Parse a standard GPX file (no MaroII extension) into raw track data.
     * Returns null when the track has no name or no points.
     */
    private fun parseStandardGpxData(bytes: ByteArray): StandardGpx? {
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(bytes.inputStream(), "UTF-8")

        var trackName: String? = null
        var trackComment = ""
        val points = mutableListOf<TrackPoint>()
        var inTrk = false
        var inName = false
        var inCmt = false
        var inTrkpt = false
        var currentLat = 0.0
        var currentLon = 0.0
        var currentSpeed: Float? = null
        var currentBearing: Float? = null
        var currentTimeMs = 0L
        var firstTimeMs: Long? = null

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name.lowercase()) {
                        "trk" -> inTrk = true
                        "name" -> if (inTrk) inName = true
                        "cmt" -> if (inTrk) inCmt = true
                        "trkpt" -> {
                            inTrkpt = true
                            currentLat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull() ?: 0.0
                            currentLon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull() ?: 0.0
                            currentSpeed = null
                            currentBearing = null
                            currentTimeMs = 0L
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    val text = parser.text?.trim() ?: ""
                    if (inName) { trackName = text; inName = false }
                    if (inCmt) { trackComment = text; inCmt = false }
                    if (inTrkpt) {
                        when {
                            parser.name.equals("speed", ignoreCase = true) ->
                                currentSpeed = text.toFloatOrNull()
                            parser.name.equals("course", ignoreCase = true) ->
                                currentBearing = text.toFloatOrNull()
                            parser.name.equals("time", ignoreCase = true) -> {
                                currentTimeMs = parseIsoTime(text) ?: 0L
                                if (firstTimeMs == null) firstTimeMs = currentTimeMs
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name.lowercase()) {
                        "trk" -> inTrk = false
                        "trkpt" -> {
                            if (inTrkpt && currentLat != 0.0 && currentLon != 0.0) {
                                val offsetMs = if (firstTimeMs != null) currentTimeMs - firstTimeMs else 0L
                                points.add(
                                    TrackPoint(
                                        lat = currentLat,
                                        lon = currentLon,
                                        speedMps = currentSpeed,
                                        bearingDeg = currentBearing,
                                        timeOffsetMs = offsetMs
                                    )
                                )
                            }
                            inTrkpt = false
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        if (trackName == null || points.isEmpty()) return null
        return StandardGpx(
            name = trackName,
            comment = trackComment,
            points = points,
            firstTimeMs = firstTimeMs
        )
    }

    /** Build a fresh [Track] from standard GPX data with a fresh UUID. */
    private fun buildForeignTrack(std: StandardGpx, name: String): Track {
        val now = System.currentTimeMillis()
        val startMs = std.firstTimeMs ?: now
        val endMs = if (std.points.isNotEmpty() && std.firstTimeMs != null) {
            std.firstTimeMs + std.points.last().timeOffsetMs
        } else now

        return Track(
            id = UUID.randomUUID().toString(),
            name = name,
            comment = std.comment,
            startTimeMs = startMs,
            endTimeMs = endMs,
            lastPointTimeMs = endMs,
            trackPoints = std.points,
            trackColorArgb = 0xFFFF6F00.toInt(),
            pinned = false,
            distanceNm = 0f,
            navigatingDurationSec = (endMs - startMs) / 1000,
            updatedAtEpochMs = now
        )
    }

    /**
     * Resolve name collisions by appending " (2)", " (3)", etc.
     */
    internal fun resolveNameCollision(name: String, existingNames: Set<String>): String {
        if (name !in existingNames) return name
        var suffix = 2
        while ("$name ($suffix)" in existingNames) suffix++
        return "$name ($suffix)"
    }

    /** Parse ISO 8601 timestamp to epoch millis. Handles Z and ±HH:MM offsets. */
    private fun parseIsoTime(text: String): Long? {
        return try {
            val sanitized = text.replace("Z", "+0000")
                .replace(Regex("""([+-]\d{2}):(\d{2})$"""), "$1$2")
            val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", java.util.Locale.US)
            format.parse(sanitized)?.time
        } catch (_: Exception) {
            try {
                val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", java.util.Locale.US)
                format.parse(text.replace("Z", "+0000").replace(Regex("""([+-]\d{2}):(\d{2})$"""), "$1$2"))?.time
            } catch (_: Exception) {
                null
            }
        }
    }
}

/** Aggregate import counts: tracks persisted vs. duplicates skipped. */
data class ImportResult(val imported: Int, val ignored: Int)

/** Full batch result: imported [Track] objects plus aggregate counts. */
data class ImportBatch(val tracks: List<Track>, val imported: Int, val ignored: Int) {
    val result: ImportResult get() = ImportResult(imported, ignored)
}

/** How a track import should treat an existing track with the same identity. */
enum class ImportMode { SKIP_EXISTING, UPDATE_EXISTING, IMPORT_NEW }

/** Result of a single-GPX match peek: the existing track that would be affected. */
data class ImportMatch(
    val id: String,
    val name: String
)
