package ykws.android.maro.data.track

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/** How the BoatMarker was triggered — idle auto-detection or manual user tap. */
enum class BoatMarkerTrigger { IDLE, MANUAL }

/**
 * A snapshot of a single marker's state at the moment of capture.
 *
 * Survives marker mutations and deletions — this is a historical record,
 * not a live reference.
 *
 * @property markerId       ID of the source [UserMarker].
 * @property name           Marker name at snapshot time.
 * @property geometryType   "Pin", "Circle", or "Corridor".
 * @property lat            Pin: marker position. Circle: center. Corridor: center point.
 * @property lon            Pin: marker position. Circle: center. Corridor: center point.
 * @property distanceNm     Sea distance from boat to marker (nautical miles).
 * @property bearingDeg     Bearing from boat to marker (0-360).
 * @property zoneSizeM      Circle: radius in metres. Pin/Corridor: 0.0 (n/a).
 * @property icon           Marker's emoji icon at snapshot time, or null.
 */
@Serializable
data class MarkerSnapshot(
    @ProtoNumber(1) val markerId: String,
    @ProtoNumber(2) val name: String,
    @ProtoNumber(3) val geometryType: String,
    @ProtoNumber(4) val lat: Double,
    @ProtoNumber(5) val lon: Double,
    @ProtoNumber(6) val distanceNm: Double,
    @ProtoNumber(7) val bearingDeg: Double,
    @ProtoNumber(8) val zoneSizeM: Double = 0.0,
    @ProtoNumber(9) val icon: String? = null
)

/**
 * A group of marker snapshots captured at a single moment during track recording.
 *
 * Each idle period or manual tap creates one [BoatMarker] entry. Stored in
 * the [Track.boatMarkers] list chronologically.
 *
 * @property trigger         [BoatMarkerTrigger.IDLE] or [MANUAL].
 * @property startTimeMs     Epoch millis when this tracking event started.
 * @property endTimeMs       Epoch millis when the idle period ended, or null if still in-progress.
 * @property markers         Snapshots of nearby markers at capture time.
 * @property boatLat         Boat latitude at capture time.
 * @property boatLon         Boat longitude at capture time.
 * @property sequenceIndex   Ordinal within the track: 0, 1, 2, ...
 */
@Serializable
data class BoatMarker(
    @ProtoNumber(1) val trigger: BoatMarkerTrigger,
    @ProtoNumber(2) val startTimeMs: Long,
    @ProtoNumber(3) val endTimeMs: Long? = null,
    @ProtoNumber(4) val markers: List<MarkerSnapshot> = emptyList(),
    @ProtoNumber(5) val boatLat: Double = 0.0,
    @ProtoNumber(6) val boatLon: Double = 0.0,
    @ProtoNumber(7) val sequenceIndex: Int = 0
    // NOTE: @ProtoNumber(8) is reserved — it previously held autoMarkerId and must
    // never be reused for a different type.
)
