package ykws.android.maro.data.markers

import android.content.Context
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.MarkerGeometry
import ykws.android.maro.data.model.markers.MarkerOrigin
import ykws.android.maro.data.model.markers.UserMarker
import ykws.android.maro.spatial.SpatialOperations
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Data-layer owner of the 🕐 IDLE_AUTO marker lifecycle.
 *
 * Backed by [UserMarkerRepository] (Context constructor — same pattern as the
 * track repository) so the service-owned recorder can create/confirm/delete
 * auto-markers with no UI dependency.
 */
class AutoMarkerManager(context: Context) {

    private val repo = UserMarkerRepository(context)

    /**
     * Create a temporary 🕐 auto-marker at the idle position, preserving the
     * dedup parity of the legacy UI create path: reuse an existing unconfirmed
     * temp marker within [AppConfig.boatMarkerAutoMarkerDedupRadiusM], skip
     * (return "") if a confirmed marker already exists there, otherwise create.
     */
    suspend fun createTemp(lat: Double, lon: Double, startTimeMs: Long, trackId: String?): String {
        val dedupRadiusM = AppConfig.boatMarkerAutoMarkerDedupRadiusM
        val newPos = LatLng(lat, lon)

        // ── Proximity dedup: scan existing IDLE_AUTO markers within dedupRadiusM ──
        val existingAutoMarkers = repo.loadAll().filter { it.origin == MarkerOrigin.IDLE_AUTO }
        val nearest = existingAutoMarkers.minByOrNull { SpatialOperations.haversine(newPos, it.centerPoint) }
        if (nearest != null) {
            val dist = SpatialOperations.haversine(newPos, nearest.centerPoint)
            if (dist <= dedupRadiusM) {
                if (!nearest.confirmed) {
                    // Reuse the existing temp marker — update its position + ownership
                    repo.update(nearest.copy(geometry = MarkerGeometry.Pin(newPos), trackId = trackId))
                    return nearest.id
                } else {
                    // Already have a confirmed auto-marker here — skip
                    return ""
                }
            }
        }

        // No nearby auto-marker → create normally
        val title = SimpleDateFormat("EEE, dd MMM yy", Locale.US).format(Date())
        val startTime = SimpleDateFormat("HH:mm", Locale.US).format(Date(startTimeMs))
        val marker = UserMarker(
            id = UUID.randomUUID().toString(),
            name = title,
            description = "@ $startTime -> ...",
            geometry = MarkerGeometry.Pin(newPos),
            proximityOverrideM = AppConfig.boatMarkerAutoMarkerProximityM,
            confirmed = false,
            pinned = true,
            icon = "\uD83D\uDD50",  // 🕐
            createdAtEpochMs = System.currentTimeMillis(),
            origin = MarkerOrigin.IDLE_AUTO,
            trackId = trackId,
            keepable = false
        )
        repo.add(marker)
        return marker.id
    }

    /**
     * Confirm a temporary 🕐 auto-marker — sets confirmed=true, keepable=true
     * and finalises the name/description with the idle timing.
     */
    suspend fun confirm(id: String, startTimeMs: Long, endTimeMs: Long, durationSec: Long) {
        val marker = repo.loadAll().find { it.id == id } ?: return
        val title = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(startTimeMs))
        val startFmt = SimpleDateFormat("HH:mm", Locale.US).format(Date(startTimeMs))
        val desc = if (endTimeMs == 0L) {
            "@ $startFmt -> ?"
        } else {
            val endFmt = SimpleDateFormat("HH:mm", Locale.US).format(Date(endTimeMs))
            val durMin = durationSec / 60
            "@ $startFmt -> $endFmt ($durMin min)"
        }
        repo.update(marker.copy(confirmed = true, keepable = true, name = title, description = desc))
    }

    /** Delete an IDLE_AUTO marker by ID (too-short idle, or confirm fallback). */
    suspend fun delete(id: String) {
        repo.delete(id)
    }
}
