package ykws.android.maro.data.track

import android.util.Base64
import kotlinx.serialization.protobuf.ProtoBuf
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
fun Track.toGpx(): String = buildString {
    append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
    append("<gpx version=\"1.1\" creator=\"Maro II\"")
    append(" xmlns=\"http://www.topografix.com/GPX/1/1\"")
    append(" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"")
    append(" xmlns:maro=\"$MARO_NS\"")
    append(" xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd\">")
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
            val speedKn = point.speedMps * 1.94384
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
    val blob = Base64.encodeToString(proto.encodeToByteArray(Track.serializer(), this@toGpx), Base64.NO_WRAP)
    append("<extensions>")
    append("<maro:data>")
    append(blob)
    append("</maro:data>")
    append("</extensions>")
    append("</trk>")
    append("</gpx>")
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
