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
 * Single [LocationManager.GPS_PROVIDER] listener ensures strict FIFO ordering
 * of GPS fixes. The passive provider was removed — it introduced ordering races
 * (two producers feeding one callbackFlow) that caused zigzag artifacts on the
 * active track polyline.
 *
 * ## Improvements over the legacy version
 * 1. **GNSS status monitoring** — [GnssStatus.Callback] tracks satellite count and sets
 *    [GpsFix.hasLock] to false when it drops below [MIN_SATELLITES_FOR_LOCK].
 * 2. **Provider status handling** — [onStatusChanged] reacts to
 *    [LocationProvider.TEMPORARILY_UNAVAILABLE]/[OUT_OF_SERVICE] by emitting a no-lock signal.
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

        try {
            lm.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, minIntervalMs, minDistanceM, listener
            )

            awaitClose {
                lm.removeUpdates(listener)
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
    }
}
