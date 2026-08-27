package ykws.android.maro.data.track

import android.util.Base64
import kotlinx.serialization.protobuf.ProtoBuf
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipInputStream

/**
 * Import tracks from a GPX file or a ZIP archive of GPX files.
 *
 * **Dispatch:**
 * - Single `.gpx` → import one track
 * - `.zip` archive → extract all `.gpx` files, import each
 *
 * **Per-track import logic:**
 * 1. Parse GPX XML
 * 2. If `<maro:data>` extension present → protobuf decode → **lossless round-trip**
 * 3. If absent (foreign GPX) → parse standard `<trkpt>` elements → lossy fallback
 * 4. **Anti-collision:** if a track with the same name exists, append " (2)", " (3)", etc.
 */
object GpxImporter {

    private val proto = ProtoBuf.Default

    /**
     * Import tracks from an [InputStream]. Dispatch based on content type.
     *
     * @param input     The file/stream to import (GPX or ZIP).
     * @param extension File extension hint ("gpx" or "zip").
     * @param existingNames Set of existing track names for anti-collision.
     * @return List of successfully imported [Track] objects.
     */
    fun import(input: InputStream, extension: String, existingNames: Set<String>): List<Track> {
        return when (extension.lowercase()) {
            "zip" -> importZip(input, existingNames)
            else -> {
                val track = importSingleGpx(input, existingNames)
                if (track != null) listOf(track) else emptyList()
            }
        }
    }

    /**
     * Import tracks from a ZIP archive containing `.gpx` files.
     * Guards against path traversal (rejects `../` and absolute paths).
     */
    private fun importZip(input: InputStream, existingNames: Set<String>): List<Track> {
        val tracks = mutableListOf<Track>()
        val nameSet = existingNames.toMutableSet()
        ZipInputStream(input).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val entryName = entry.name
                // Path traversal guard
                if (entryName.contains("..") || entryName.startsWith("/")) {
                    entry = zis.nextEntry
                    continue
                }
                if (entryName.endsWith(".gpx", ignoreCase = true) && !entry.isDirectory) {
                    // Copy to temp file for XmlPullParser (needs a stream we can read fully)
                    val tempFile = File.createTempFile("maro_import_", ".gpx")
                    try {
                        FileOutputStream(tempFile).use { fos -> zis.copyTo(fos) }
                        val track = importSingleGpx(tempFile.inputStream(), nameSet)
                        if (track != null) {
                            tracks.add(track)
                            nameSet.add(track.name)
                        }
                    } finally {
                        tempFile.delete()
                    }
                }
                entry = zis.nextEntry
            }
        }
        return tracks
    }

    /**
     * Parse a single GPX file and return a [Track], or null on failure.
     */
    fun importSingleGpx(input: InputStream, existingNames: Set<String>): Track? {
        return try {
            val bytes = input.readBytes()
            val xml = String(bytes, Charsets.UTF_8)

            // Try MaroII extension blob first (lossless round-trip)
            val maroBlob = extractMaroBlob(xml)
            if (maroBlob != null) {
                val track = proto.decodeFromByteArray(Track.serializer(), maroBlob)
                // Normalize lastPointTimeMs for exports saved before the field existed.
                val normalized = if (track.endTimeMs != null && track.lastPointTimeMs == 0L) {
                    track.copy(lastPointTimeMs = track.lastRealPointTimeMsOrNull() ?: 0L)
                } else track
                // Anti-collision on name
                val safeName = resolveNameCollision(normalized.name, existingNames)
                if (safeName != normalized.name) normalized.copy(name = safeName) else normalized
            } else {
                // Foreign GPX — parse standard elements (lossy)
                parseStandardGpx(bytes, existingNames)
            }
        } catch (_: Exception) {
            null
        }
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

    /**
     * Parse a standard GPX file (no MaroII extension) into a Track with defaults.
     */
    private fun parseStandardGpx(bytes: ByteArray, existingNames: Set<String>): Track? {
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

        val now = System.currentTimeMillis()
        val safeName = resolveNameCollision(trackName, existingNames)
        val startMs = firstTimeMs ?: now
        val endMs = if (points.isNotEmpty() && firstTimeMs != null) {
            firstTimeMs + points.last().timeOffsetMs
        } else now

        return Track(
            id = UUID.randomUUID().toString(),
            name = safeName,
            comment = trackComment,
            startTimeMs = startMs,
            endTimeMs = endMs,
            lastPointTimeMs = endMs,
            trackPoints = points,
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
