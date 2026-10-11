package ykws.android.maro.data.markers

import ykws.android.maro.data.model.markers.UserMarker
import ykws.android.maro.data.track.escapeXml

/**
 * MaroII's marker namespace — never change this string, or an older export loses its payloads on
 * re-import. It is deliberately not the track namespace: the two files are told apart by their own
 * element names.
 */
const val MARO_MARKER_NS = "https://maro.ykws.android/marker/1"

/**
 * Convert [UserMarker] to GPX 1.1 XML with a MaroII extension payload for a lossless round trip.
 *
 * The waypoint carries the marker's centre point, its name and its description, so any map
 * application reads where the marker is; the full object lives in the two extension payloads — the
 * protobuf blob and the readable JSON mirror — which is what MaroII itself reads back. A Circle's
 * radius and a Corridor's second end exist only there, one waypoint per marker being the accepted
 * limitation of the readable form.
 *
 * A blank name emits no `<name>` element at all: the value would be empty, and the importer treats a
 * payload with neither an id nor a name as invalid rather than guessing at one.
 */
fun UserMarker.toMarkerGpx(): String = buildString {
    val marker = this@toMarkerGpx
    append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
    append("<gpx version=\"1.1\" creator=\"Maro II\"")
    append(" xmlns=\"http://www.topografix.com/GPX/1/1\"")
    append(" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"")
    append(" xmlns:maro=\"$MARO_MARKER_NS\"")
    append(" xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd\">")
    val centre = marker.centerPoint
    append("<wpt lat=\"${centre.latitude}\" lon=\"${centre.longitude}\">")
    if (marker.name.isNotBlank()) {
        append("<name>")
        append(escapeXml(marker.name))
        append("</name>")
    }
    if (marker.description.isNotBlank()) {
        append("<desc>")
        append(escapeXml(marker.description))
        append("</desc>")
    }
    append("<extensions>")
    append("<maro:markerData>")
    append(MarkerPayload.blobOf(marker))
    append("</maro:markerData>")
    append("<maro:markerJson>")
    append(MarkerPayload.writeCdata(MarkerPayload.jsonOf(marker)))
    append("</maro:markerJson>")
    append("</extensions>")
    append("</wpt>")
    append("</gpx>")
}
