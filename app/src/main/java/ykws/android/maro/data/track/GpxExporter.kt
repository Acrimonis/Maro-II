package ykws.android.maro.data.track

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Converts a [Track] to a GPX 1.1 XML string for sharing.
 */
object GpxExporter {

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)

    private val AMP = "am" + "p;"
    private val LT = "lt" + ";"
    private val GT = "gt" + ";"
    private val QUOT = "quo" + "t;"
    private val APOS = "apo" + "s;"

    /**
     * Convert [track] to GPX 1.1 XML.
     */
    fun Track.toGpx(): String = buildString {
        appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        appendLine("<gpx version=\"1.1\" creator=\"Maro II\"")
        appendLine("  xmlns=\"http://www.topografix.com/GPX/1/1\"")
        appendLine("  xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"")
        appendLine("  xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd\">")
        appendLine("  <trk>")
        append("    <name>")
        append(escapeXml(name))
        appendLine("</name>")
        if (comment.isNotBlank()) {
            append("    <cmt>")
            append(escapeXml(comment))
            appendLine("</cmt>")
        }
        appendLine("    <trkseg>")
        trackPoints.forEach { point ->
            append("      <trkpt lat=\"${point.lat}\" lon=\"${point.lon}\">")
            if (point.speedMps != null) {
                val speedKn = point.speedMps * 1.94384
                append("<speed>${"%.2f".format(speedKn)}</speed>")
            }
            if (point.bearingDeg != null) {
                append("<course>${point.bearingDeg}</course>")
            }
            val pointTime = Date(startTimeMs + point.timeOffsetSec * 1000L)
            append("<time>${isoFormat.format(pointTime)}</time>")
            appendLine("</trkpt>")
        }
        appendLine("    </trkseg>")
        appendLine("  </trk>")
        appendLine("</gpx>")
    }

    private fun escapeXml(text: String): String = buildString {
        for (c in text) {
            when (c) {
                '&' -> append('&').append(AMP)
                '<' -> append('&').append(LT)
                '>' -> append('&').append(GT)
                '"' -> append('&').append(QUOT)
                '\'' -> append('&').append(APOS)
                else -> append(c)
            }
        }
    }
}
