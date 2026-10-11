package ykws.android.maro.data.markers

import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import ykws.android.maro.data.model.markers.UserMarker
import java.util.Base64

/**
 * The one home for a marker's wire payload.
 *
 * A marker travels in two forms, both written into a GPX file's `<extensions>`: a base64 protobuf
 * blob of the whole [UserMarker], which is the lossless path, and a readable JSON mirror of every
 * field, which a human or a foreign tool can read and which the importer falls back to when the blob
 * is absent.
 *
 * Three readers share this object — [MarkerGpxExporter], [MarkerGpxImporter] and
 * [UserMarkerRepository] — so [json] is deliberately the repository's own configuration rather than a
 * second one: the file on disk and the mirror inside an export are the same encoding of the same
 * object, and neither can drift from the other.
 *
 * **Base64 is the JVM's, not Android's.** The app's floor is API 26, where `java.util.Base64` exists,
 * and the unit-test classpath carries no Android APIs at all — so this is the one codec that can be
 * tested off-device, which is exactly what a wire format needs. Reading is lenient (`getMimeDecoder`
 * ignores the whitespace a foreign writer may have wrapped its output with); writing never wraps.
 */
object MarkerPayload {

    /**
     * The marker JSON configuration, and the encoder the on-disk store reads through.
     *
     * `ignoreUnknownKeys` lets a payload written by a later version still load, and `prettyPrint` keeps
     * the readable mirror readable.
     */
    val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private val proto = ProtoBuf.Default
    private val encoder = Base64.getEncoder()
    private val decoder = Base64.getMimeDecoder()

    /** The base64 protobuf blob of [marker] — the lossless payload. */
    fun blobOf(marker: UserMarker): String =
        encoder.encodeToString(proto.encodeToByteArray(UserMarker.serializer(), marker))

    /** The readable JSON mirror of [marker], every field spelled out. */
    fun jsonOf(marker: UserMarker): String = json.encodeToString(UserMarker.serializer(), marker)

    /** Decode a blob, or null when it is malformed or written by an incompatible version. */
    fun markerFromBlob(base64: String): UserMarker? = try {
        proto.decodeFromByteArray(UserMarker.serializer(), decoder.decode(base64))
    } catch (_: Exception) {
        null
    }

    /** Decode a file's mirror, or null when the text is malformed — the blob always wins over this. */
    fun markerFromJson(text: String): UserMarker? = try {
        json.decodeFromString(UserMarker.serializer(), text)
    } catch (_: Exception) {
        null
    }

    /**
     * Wrap free text in a `CDATA` section: a `]]>` inside it would close the section early, so the
     * sequence is split and the section resumed. [readCdata] is its exact inverse.
     */
    fun writeCdata(text: String): String = "<![CDATA[" + text.replace("]]>", "]]]]><![CDATA[>") + "]]>"

    /**
     * One marker's payload element, as it rides inside a trip file's `<extensions>` beside the track's
     * own blob — the same two forms a marker file carries, so one codec writes both files.
     */
    fun markerElementOf(marker: UserMarker): String = buildString {
        append(MARKER_OPEN)
        append("<maro:markerData>")
        append(blobOf(marker))
        append("</maro:markerData>")
        append("<maro:markerJson>")
        append(writeCdata(jsonOf(marker)))
        append("</maro:markerJson>")
        append(MARKER_CLOSE)
    }

    /**
     * Every marker a trip file carries, in order. A marker whose payloads are unreadable is skipped
     * rather than guessed at, exactly as a single marker file's unreadable payload is refused.
     */
    fun markersIn(xml: String): List<UserMarker> {
        val markers = mutableListOf<UserMarker>()
        var index = xml.indexOf(MARKER_OPEN)
        while (index >= 0) {
            val end = xml.indexOf(MARKER_CLOSE, index)
            if (end < 0) break
            val block = xml.substring(index + MARKER_OPEN.length, end)
            val fromBlob = elementOf(block, "maro:markerData")?.let { markerFromBlob(it) }
            val marker = fromBlob
                ?: elementOf(block, "maro:markerJson")?.let { markerFromJson(readCdata(it)) }
            if (marker != null) markers.add(marker)
            index = xml.indexOf(MARKER_OPEN, end + MARKER_CLOSE.length)
        }
        return markers
    }

    /** The trimmed content of the first [tag] element in [xml], or null when it has none. */
    internal fun elementOf(xml: String, tag: String): String? {
        val open = "<$tag>"
        val close = "</$tag>"
        val start = xml.indexOf(open)
        if (start < 0) return null
        val contentStart = start + open.length
        val end = xml.indexOf(close, contentStart)
        if (end < 0) return null
        return xml.substring(contentStart, end).trim()
    }

    private const val MARKER_OPEN = "<maro:marker>"
    private const val MARKER_CLOSE = "</maro:marker>"

    /** The inverse of [writeCdata]: the original text of a `CDATA` section's content. */
    fun readCdata(content: String): String {
        val trimmed = content.trim()
        val body = if (trimmed.startsWith("<![CDATA[") && trimmed.endsWith("]]>")) {
            trimmed.removePrefix("<![CDATA[").removeSuffix("]]>")
        } else {
            trimmed
        }
        return body.replace("]]]]><![CDATA[>", "]]>")
    }
}
