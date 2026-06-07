package ykws.android.maro.data.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Cold [Flow] of the device azimuth (compass heading) in degrees (0–360, clockwise from
 * magnetic north), backed by the fused [Sensor.TYPE_ROTATION_VECTOR] sensor.
 *
 * Used as the heading-up fallback when GPS provides no course (device stationary). No runtime
 * permission is required. Magnetic north is used as-is — no true-north declination correction
 * in v1. Disposal (`unregisterListener`) happens in [awaitClose] when the collector cancels.
 */
class CompassSource(private val context: Context) {

    /**
     * @param samplingPeriodUs sensor delivery rate. Defaults to [SensorManager.SENSOR_DELAY_NORMAL]
     *        (~5 Hz) rather than `SENSOR_DELAY_UI` (~16 Hz): the heading flow is `sample()`-throttled
     *        to ~5 Hz downstream anyway, so a faster sensor rate only burns power producing events
     *        that are discarded.
     */
    fun azimuthUpdates(
        samplingPeriodUs: Int = SensorManager.SENSOR_DELAY_NORMAL
    ): Flow<Float> = callbackFlow {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        val listener = object : SensorEventListener {
            private val rotation = FloatArray(9)
            private val orientation = FloatArray(3)

            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotation, event.values)
                SensorManager.getOrientation(rotation, orientation)
                // orientation[0] = azimuth in radians (−π…π) → degrees normalised to 0…360.
                val deg = (Math.toDegrees(orientation[0].toDouble()) + 360.0) % 360.0
                trySend(deg.toFloat())
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        if (sensor != null) {
            sm.registerListener(listener, sensor, samplingPeriodUs)
        }

        awaitClose { sm.unregisterListener(listener) }
    }
}
