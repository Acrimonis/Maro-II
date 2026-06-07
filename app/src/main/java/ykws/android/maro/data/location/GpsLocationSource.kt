package ykws.android.maro.data.location

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
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
 */
data class GpsFix(
    val position: LatLng,
    val bearingDeg: Float?,
    val hasCourse: Boolean,
    val speedMps: Float?
)

/**
 * Cold [Flow] of [GpsFix] backed by the Android framework [LocationManager] GPS provider.
 *
 * Framework-only (no Google Play Services) to match the project's GMS-free stack (OSMdroid).
 * The Spring analogy: a reactive source bean — `requestLocationUpdates` is the subscription,
 * `removeUpdates` (in [awaitClose]) is the disposal when the collector cancels.
 *
 * The caller is responsible for holding `ACCESS_FINE_LOCATION` before collecting; if the
 * permission is missing/revoked the flow closes with the [SecurityException] so the collector
 * can `.catch` it rather than crashing.
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

        // Explicit object (not a SAM lambda) so the legacy callbacks are implemented on every
        // API level — pre-API-30 frameworks invoke them and would otherwise hit AbstractMethodError.
        @Suppress("DEPRECATION")
        val listener = object : LocationListener {
            override fun onLocationChanged(loc: Location) {
                val moving = loc.hasBearing() && loc.hasSpeed() && loc.speed > MIN_SPEED_MPS
                trySend(
                    GpsFix(
                        position = LatLng(loc.latitude, loc.longitude),
                        bearingDeg = if (moving) loc.bearing else null,
                        hasCourse = moving,
                        speedMps = if (loc.hasSpeed()) loc.speed else null
                    )
                )
            }

            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        try {
            lm.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, minIntervalMs, minDistanceM, listener
            )
        } catch (e: SecurityException) {
            close(e) // permission not granted / revoked → let the collector handle it
        }

        awaitClose { lm.removeUpdates(listener) }
    }

    companion object {
        /** Below ~1 knot the GPS-reported course is noise, so we don't trust it for heading-up. */
        const val MIN_SPEED_MPS = 0.5f
    }
}
