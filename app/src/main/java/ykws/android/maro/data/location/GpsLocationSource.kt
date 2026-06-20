package ykws.android.maro.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationProvider
import android.os.Bundle
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import ykws.android.maro.data.model.LatLng

/**
 * One device GPS fix: position plus course-over-ground when the device is actually moving.
 *
 * @property position   WGS84 latitude/longitude of the fix.
 * @property bearingDeg Course over ground in degrees (0–360, clockwise from north), or `null`
 *                      when stationary / the provider could not determine a heading.
 * @property hasCourse  True when [bearingDeg] is a usable heading (device moving fast enough).
 * @property speedMps   Speed over ground in m/s, or `null` when the provider supplies no speed.
 * @property hasLock    True when the GNSS engine has sufficient satellites or the provider
 *                      reports itself as available. Set to `false` when satellite count drops
 *                      below [MIN_SATELLITES_FOR_LOCK] or the provider signals
 *                      [LocationProvider.TEMPORARILY_UNAVAILABLE]/[OUT_OF_SERVICE]. Default `true`.
 */
data class GpsFix(
    val position: LatLng,
    val bearingDeg: Float?,
    val hasCourse: Boolean,
    val speedMps: Float?,
    val hasLock: Boolean = true,
    val timestampEpochMs: Long = System.currentTimeMillis()
)

/**
 * Cold [Flow] of [GpsFix] backed by the Android framework [LocationManager].
 *
 * Framework-only (no Google Play Services). The caller is responsible for holding
 * `ACCESS_FINE_LOCATION` before collecting; if the permission is missing/revoked
 * the flow closes with [SecurityException].
 *
 * ## Improvements over the legacy version
 * 1. **GNSS status monitoring** — [GnssStatus.Callback] tracks satellite count and sets
 *    [GpsFix.hasLock] to false when it drops below [MIN_SATELLITES_FOR_LOCK].
 * 2. **Provider status handling** — [onStatusChanged] reacts to
 *    [LocationProvider.TEMPORARILY_UNAVAILABLE]/[OUT_OF_SERVICE] by emitting a no-lock signal.
 * 3. **Passive provider supplement** — [PASSIVE_PROVIDER] alongside GPS_PROVIDER so the flow
 *    receives fixes requested by other apps at zero battery cost.
 */
class GpsLocationSource(private val context: Context) {

    /**
     * @param minIntervalMs minimum time between fixes (ms).
     * @param minDistanceM  minimum movement between fixes (m).
     */
    fun locationUpdates(
        minIntervalMs: Long = 1_000L,
        minDistanceM: Float = 1f
    ): Flow<GpsFix> = callbackFlow {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        var lastKnownPosition: LatLng? = null
        var lastGpsProviderPos: LatLng? = null
        var lastGpsProviderMs = 0L
        var lastSatelliteCount = 0
        var lastTimestamp = System.currentTimeMillis()

        /** Emit a [GpsFix] from a raw [Location], tracking lock state. */
        fun emitFix(loc: Location, lock: Boolean = true) {
            val pos = LatLng(loc.latitude, loc.longitude)
            lastKnownPosition = pos
            val nowMs = System.currentTimeMillis()
            val moving = loc.hasBearing() && loc.hasSpeed() && loc.speed > MIN_SPEED_MPS
            trySend(
                GpsFix(
                    position = pos,
                    bearingDeg = if (moving) loc.bearing else null,
                    hasCourse = moving,
                    speedMps = if (loc.hasSpeed()) loc.speed else null,
                    hasLock = lock,
                    timestampEpochMs = nowMs
                )
            )
            lastTimestamp = nowMs
        }

        /** Emit a no-lock signal at the last known position. */
        fun emitNoLock() {
            val pos = lastKnownPosition ?: return
            trySend(
                GpsFix(
                    position = pos,
                    bearingDeg = null,
                    hasCourse = false,
                    speedMps = null,
                    hasLock = false,
                    timestampEpochMs = lastTimestamp
                )
            )
        }

        // ── GNSS status callback (satellite count monitoring) ──────────────
        val gnssCallback = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                val count = status.satelliteCount
                if (count != lastSatelliteCount) {
                    lastSatelliteCount = count
                    if (count < MIN_SATELLITES_FOR_LOCK) emitNoLock()
                }
            }

            override fun onStopped() {
                lastSatelliteCount = 0
                emitNoLock()
            }
        }
        lm.registerGnssStatusCallback(gnssCallback, null)

        // ── GPS location listener ──────────────────────────────────────────
        @Suppress("DEPRECATION")
        val listener = object : LocationListener {
            override fun onLocationChanged(loc: Location) {
                if (loc.provider == LocationManager.GPS_PROVIDER) {
                    emitFix(loc, lock = true)
                    lastGpsProviderPos = LatLng(loc.latitude, loc.longitude)
                    lastGpsProviderMs = android.os.SystemClock.elapsedRealtime()
                }
            }

            @Suppress("DEPRECATION")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
                if (provider == LocationManager.GPS_PROVIDER) {
                    when (status) {
                        LocationProvider.TEMPORARILY_UNAVAILABLE,
                        LocationProvider.OUT_OF_SERVICE -> emitNoLock()
                        LocationProvider.AVAILABLE -> lastSatelliteCount = MIN_SATELLITES_FOR_LOCK
                    }
                }
            }

            override fun onProviderEnabled(provider: String) {
                if (provider == LocationManager.GPS_PROVIDER) {
                    lastSatelliteCount = MIN_SATELLITES_FOR_LOCK
                }
            }

            override fun onProviderDisabled(provider: String) {
                if (provider == LocationManager.GPS_PROVIDER) emitNoLock()
            }
        }

        // ── Passive listener (free supplements from other apps' GPS requests) ──
        @Suppress("DEPRECATION")
        val passiveListener = object : LocationListener {
            override fun onLocationChanged(loc: Location) {
                if (loc.provider == LocationManager.GPS_PROVIDER) {
                    val pos = LatLng(loc.latitude, loc.longitude)
                    val elapsed = android.os.SystemClock.elapsedRealtime() - lastGpsProviderMs
                    if (lastGpsProviderPos != null &&
                        haversineApprox(lastGpsProviderPos!!, pos) < 1.0 &&
                        elapsed < 2_000L
                    ) return
                }
                emitFix(loc, lock = true)
            }
        }

        try {
            lm.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, minIntervalMs, minDistanceM, listener
            )
            lm.requestLocationUpdates(
                LocationManager.PASSIVE_PROVIDER, 0L, 0f, passiveListener
            )

            awaitClose {
                lm.removeUpdates(listener)
                lm.removeUpdates(passiveListener)
                lm.unregisterGnssStatusCallback(gnssCallback)
            }
        } catch (e: SecurityException) {
            close(e)
        }
    }

    companion object {
        /** Below ~1 knot the GPS-reported course is noise, so we don't trust it for heading-up. */
        const val MIN_SPEED_MPS = 0.5f

        /** Minimum satellites for a usable GPS lock. */
        const val MIN_SATELLITES_FOR_LOCK = 4

        /** Quick haversine approximation for passive-fix dedup. */
        private fun haversineApprox(a: LatLng, b: LatLng): Double {
            val dLat = Math.toRadians(b.latitude - a.latitude)
            val dLon = Math.toRadians(b.longitude - a.longitude)
            val sinDLat = kotlin.math.sin(dLat / 2.0)
            val sinDLon = kotlin.math.sin(dLon / 2.0)
            val lat1Rad = Math.toRadians(a.latitude)
            val lat2Rad = Math.toRadians(b.latitude)
            val aVal = sinDLat * sinDLat + kotlin.math.cos(lat1Rad) * kotlin.math.cos(lat2Rad) * sinDLon * sinDLon
            return 6_371_000.0 * 2.0 * kotlin.math.atan2(kotlin.math.sqrt(aVal), kotlin.math.sqrt(1.0 - aVal))
        }
    }
}
