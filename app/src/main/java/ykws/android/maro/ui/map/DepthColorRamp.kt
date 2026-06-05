package ykws.android.maro.ui.map

import ykws.android.maro.data.depth.DepthConstants
import kotlin.math.roundToInt

/**
 * Hypsometric colour ramp for the depth map: shallow = pale cyan → deep = navy, with a
 * red-orange **warning tint** blended into the 0–5 m collision band. Returns packed ARGB
 * ints (semi-transparent, for a translucent overlay). NaN / above-datum → fully transparent.
 *
 * Pure (no Android types) so it is JVM-unit-testable; [DepthBitmap] applies it per cell.
 */
object DepthColorRamp {

    const val MAX_DEPTH_M = 60f
    private const val ALPHA = 160

    fun argb(depthM: Float): Int {
        if (depthM.isNaN() || depthM < 0f) return 0  // NoData or above datum → transparent
        val d = depthM.coerceIn(0f, MAX_DEPTH_M)
        val t = d / MAX_DEPTH_M  // 0 shallow .. 1 deep

        // Base ramp: (200,232,255) pale cyan → (10,30,90) navy.
        var r = lerp(200, 10, t)
        var g = lerp(232, 30, t)
        var b = lerp(255, 90, t)

        // Collision band 0–5 m: blend toward warning red-orange, strongest at the surface.
        if (d <= DepthConstants.COLLISION_MAX_DEPTH_M.toFloat()) {
            val w = 0.6f * (1f - d / DepthConstants.COLLISION_MAX_DEPTH_M.toFloat())
            r = lerp(r, 255, w)
            g = lerp(g, 80, w)
            b = lerp(b, 60, w)
        }
        return (ALPHA shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun lerp(a: Int, b: Int, t: Float): Int =
        (a + (b - a) * t).roundToInt().coerceIn(0, 255)
}
