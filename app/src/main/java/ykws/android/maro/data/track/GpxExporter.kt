package ykws.android.maro.data.track

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)

/**
 * Convert [track] to GPX 1.1 XML.
 */
fun Track.toGpx(): String = buildString {
    append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
    append("<gpx version=\"1.1\" creator=\"Maro II\"")
    append(" xmlns=\"http://www.topografix.com/GPX/1/1\"")
    append(" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"")
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
        val pointTime = Date(startTimeMs + point.timeOffsetSec * 1000L)
        append("<time>${isoFormat.format(pointTime)}</time>")
        append("</trkpt>")
    }
    append("</trkseg>")
    append("</trk>")
    append("</gpx>")
}

private fun escapeXml(text: String): String = buildString {
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
