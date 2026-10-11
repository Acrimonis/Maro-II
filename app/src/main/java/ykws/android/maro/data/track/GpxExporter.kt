package ykws.android.maro.data.track

// The JVM's Base64, not Android's: the app's floor is API 26 where it exists, and the unit-test
// classpath carries no Android API at all — so the whole GPX surface stays reachable from a JVM test,
// which is what a wire format needs. Reading is lenient about whitespace; writing never wraps.
import java.util.Base64
import kotlinx.serialization.protobuf.ProtoBuf
import ykws.android.maro.data.markers.MarkerPayload
import ykws.android.maro.data.model.markers.UserMarker
import ykws.android.maro.spatial.Units
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}
private val proto = ProtoBuf.Default

/** MaroII namespace URI — never change this or old exports lose MaroII data on re-import. */
const val MARO_NS = "https://maro.ykws.android/track/1"

/**
 * Convert [track] to GPX 1.1 XML with a MaroII extension blob for lossless round-trip.
 *
 * Standard GPX consumers (QGIS, Google Earth, OsmAnd) parse the GPX normally and
 * ignore the `<maro:data>` extension. MaroII re-import decodes the protobuf blob
 * directly for full fidelity including markers, pinned state, color, etc.
 */
fun Track.toGpx(markers: List<UserMarker>): String = buildString {
    append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
    append("<gpx version=\"1.1\" creator=\"Maro II\"")
    append(" xmlns=\"http://www.topografix.com/GPX/1/1\"")
    append(" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"")
    append(" xmlns:maro=\"$MARO_NS\"")
    append(" xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd\">")
    // The trip's own stops as plain waypoints, so another application sees them — GPX puts every
    // `wpt` ahead of the `trk`, and the lossless copies ride in the track's extensions below. These
    // companions are for foreign readers only: MaroII reads the payloads, so a trip file never
    // double-counts a stop.
    for (marker in markers) {
        val centre = marker.centerPoint
        append("<wpt lat=\"${centre.latitude}\" lon=\"${centre.longitude}\">")
        if (marker.name.isNotBlank()) {
            append("<name>")
            append(escapeXml(marker.name))
            append("</name>")
        }
        append("</wpt>")
    }
    append("<trk>")
    append("<name>")
    append(escapeXml(name))
    append("</name>")
    if (comment.isNotBlank()) {
        append("<cmt>")
        append(escapeXml(comment))
        append("</cmt>")
    }
    append("<trkseg>")
    for (point in trackPoints) {
        append("<trkpt lat=\"${point.lat}\" lon=\"${point.lon}\">")
        if (point.speedMps != null) {
            val speedKn = point.speedMps.toDouble() * Units.KNOTS_PER_MPS
            append("<speed>${"%.2f".format(speedKn)}</speed>")
        }
        if (point.bearingDeg != null) {
            append("<course>${point.bearingDeg}</course>")
        }
        if (point.accuracyM != null) {
            append("<accuracy>${"%.1f".format(point.accuracyM)}</accuracy>")
        }
        val pointTime = Date(startTimeMs + point.timeOffsetMs)
        append("<time>${isoFormat.format(pointTime)}</time>")
        append("</trkpt>")
    }
    append("</trkseg>")
    // ── MaroII extension blob: base64-encoded protobuf of the full Track ──
    val blob = Base64.getEncoder().encodeToString(proto.encodeToByteArray(Track.serializer(), this@toGpx))
    append("<extensions>")
    append("<maro:data>")
    append(blob)
    append("</maro:data>")
    // The owned markers' own two payloads, written by the same codec a marker file uses.
    for (marker in markers) {
        append(MarkerPayload.markerElementOf(marker))
    }
    append("</extensions>")
    append("</trk>")
    append("</gpx>")
}

/**
 * The inverse of [escapeXml] — the five entities it writes, decoded back.
 *
 * Needed wherever an element's text is read without a platform parser: the marker importer reads a
 * foreign waypoint itself so the transfer stays reachable from the JVM unit tests, and a name carrying
 * an `&` must arrive as an ampersand rather than as its entity.
 */
internal fun unescapeXml(text: String): String {
    // The ampersand is built from its own character, never written as an entity in this source: the
    // five sequences are assembled as it concatenated with their tails, exactly as `escapeXml` builds
    // them from single characters, so an editor or a tool that rewrites `&xxx;` cannot corrupt this.
    val amp = "&"
    return text
        .replace(amp + "lt;", "<")
        .replace(amp + "gt;", ">")
        .replace(amp + "quot;", "\"")
        .replace(amp + "apos;", "'")
        .replace(amp + "amp;", amp)
}

internal fun escapeXml(text: String): String = buildString {
    for (c in text) {
        when (c) {
            '&' -> { append('&'); append('a'); append('m'); append('p'); append(';') }
            '<' -> { append('&'); append('l'); append('t'); append(';') }
            '>' -> { append('&'); append('g'); append('t'); append(';') }
            '"' -> { append('&'); append('q'); append('u'); append('o'); append('t'); append(';') }
            '\'' -> { append('&'); append('a'); append('p'); append('o'); append('s'); append(';') }
            else -> append(c)
        }
    }
}
