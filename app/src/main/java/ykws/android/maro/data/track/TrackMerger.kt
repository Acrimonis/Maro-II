package ykws.android.maro.data.track

import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.SpatialOperations
import java.util.UUID

/** Implied-speed ceiling (m/s, ~1 kn) for the reconciled-idle classifier (mirrors TrackRecorder). */
private const val IDLE_MAX_SPEED_MPS = 0.5
/** Net-displacement ceiling (m) for the reconciled-idle classifier (mirrors TrackRecorder). */
private const val IDLE_MAX_DRIFT_M = 500.0

/**
 * Pure utility to merge 2+ finalized tracks into a single new track.
 * No Android dependencies — operates entirely on in-memory [Track] objects.
 *
 * Algorithm:
 * 1. Concatenate trackPoints with timeOffsetMs rebasing to earliest start
 * 2. Insert GAP markers between segments (always — merge is always a gap)
 * 3. Concatenate BoatMarkers with renumbered sequenceIndex
 * 4. Synthesize stats from per-track values (no O(n) recompute)
 */
class TrackMerger {

    /**
     * Merge [tracks] into a single new [Track] with a new UUID.
     *
     * @param tracks      Source tracks, pre-sorted by startTimeMs ascending.
     *                    Must have >= 2 entries, all finalized with non-null endTimeMs
     *                    and non-empty trackPoints.
     * @param mergedName  Name for the merged track (user-provided via dialog).
     * @return A new [Track] with concatenated points, GAP markers between segments,
     *         renumbered BoatMarkers, and synthesized stats.
     */
    fun merge(tracks: List<Track>, mergedName: String): Track {
        require(tracks.size >= 2) { "Need at least 2 tracks to merge, got ${tracks.size}" }
        require(tracks.all { it.endTimeMs != null }) { "All tracks must be finalized" }
        require(tracks.all { it.trackPoints.isNotEmpty() }) { "All tracks must have points" }

        val mergedStartMs = tracks.first().startTimeMs
        val mergedEndMs = tracks.last().endTimeMs!!

        // ── 1. Concatenate trackPoints with timeOffsetMs rebasing (M3) ──
        val mergedPoints = mutableListOf<TrackPoint>()
        var lastOffsetMs = -1L

        for ((index, track) in tracks.withIndex()) {
            if (index > 0) {
                // Insert GAP marker between segments (M4)
                val gap = TrackPoint(
                    lat = mergedPoints.last().lat,
                    lon = mergedPoints.last().lon,
                    speedMps = null,
                    bearingDeg = null,
                    timeOffsetSec = ((lastOffsetMs + 1) / 1000).toInt(),
                    timeOffsetMs = lastOffsetMs + 1,
                    type = PointType.GAP
                )
                mergedPoints.add(gap)
                lastOffsetMs = gap.timeOffsetMs
            }

            val trackDeltaMs = track.startTimeMs - mergedStartMs
            for (point in track.trackPoints) {
                var rebasedMs = point.timeOffsetMs + trackDeltaMs
                if (rebasedMs <= lastOffsetMs) {
                    rebasedMs = lastOffsetMs + 1  // monotonicity enforcement
                }
                mergedPoints.add(
                    point.copy(
                        timeOffsetMs = rebasedMs,
                        timeOffsetSec = (rebasedMs / 1000).toInt()
                    )
                )
                lastOffsetMs = rebasedMs
            }
        }

        // ── 2. Concatenate BoatMarkers with renumbered sequenceIndex ──
        val mergedMarkers = mutableListOf<BoatMarker>()
        var seqIdx = 0
        for (track in tracks) {
            for (marker in track.boatMarkers) {
                mergedMarkers.add(marker.copy(sequenceIndex = seqIdx++))
            }
        }

        // ── 3. Compute stats from per-track values (M2, M5) ──
        val totalPoints = tracks.sumOf { it.trackPoints.size }
        val avgSpeedMps = if (totalPoints > 0) {
            tracks.sumOf { it.averageSpeedMps.toDouble() * it.trackPoints.size } / totalPoints
        } else 0.0

        // ── 4. Inter-track gap idle/moving estimation ──
        var gapIdleAccum = 0.0
        var gapMovingAccum = 0.0

        for (i in 0 until tracks.size - 1) {
            val a = tracks[i]
            val b = tracks[i + 1]
            val gapMs = b.startTimeMs - a.endTimeMs!!
            if (gapMs <= 0) continue

            val gapSec = gapMs / 1000.0
            val lastPt = a.trackPoints.last()
            val firstPt = b.trackPoints.first()
            val gapDistM = SpatialOperations.haversine(
                LatLng(lastPt.lat, lastPt.lon),
                LatLng(firstPt.lat, firstPt.lon)
            )

            // Compound same-area shortcut: slow AND within the area → whole gap idle.
            if (gapDistM / gapSec < IDLE_MAX_SPEED_MPS && gapDistM < IDLE_MAX_DRIFT_M) {
                gapIdleAccum += gapSec
                continue
            }

            val pA = a.trackPoints.size.toDouble()
            val pB = b.trackPoints.size.toDouble()
            val pairAvgMps = if (pA + pB > 0.0) {
                (a.averageSpeedMps.toDouble() * pA + b.averageSpeedMps.toDouble() * pB) / (pA + pB)
            } else avgSpeedMps

            val estMovingSec = if (pairAvgMps > 0.0) {
                minOf(gapDistM / pairAvgMps, gapSec)
            } else 0.0

            gapMovingAccum += estMovingSec
            gapIdleAccum += (gapSec - estMovingSec)
        }

        val gapMovingSec = gapMovingAccum.toLong()
        val gapIdleSec = gapIdleAccum.toLong()

        // ── 5. Assemble merged track ──
        val mergedLastPointTimeMs = mergedPoints.lastOrNull { it.type != PointType.GAP }
            ?.let { mergedStartMs + it.timeOffsetMs } ?: mergedEndMs
        return Track(
            id = UUID.randomUUID().toString(),
            name = mergedName,
            startTimeMs = mergedStartMs,
            endTimeMs = mergedEndMs,
            lastPointTimeMs = mergedLastPointTimeMs,
            trackPoints = mergedPoints,
            boatMarkers = mergedMarkers,
            distanceNm = tracks.sumOf { it.distanceNm.toDouble() }.toFloat(),
            fastestSpeedMps = tracks.maxOf { it.fastestSpeedMps },
            averageSpeedMps = avgSpeedMps.toFloat(),
            navigatingDurationSec = tracks.sumOf { it.navigatingDurationSec } + gapMovingSec,
            idleDurationSec = tracks.sumOf { it.idleDurationSec } + gapIdleSec,
            trackColorArgb = tracks.first().trackColorArgb,
            pinned = tracks.all { it.pinned },
            visibleOnMap = true,
            updatedAtEpochMs = System.currentTimeMillis()
        )
    }
}
