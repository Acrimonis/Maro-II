package ykws.android.maro.ui.map

import ykws.android.maro.data.depth.DepthConstants
import kotlin.math.roundToInt

/**
 * Hypsometric colour ramp for the depth map: shallow = pale cyan → deep = navy, with a
 * red-orange **warning tint** blended into the 0–5 m collision band. Returns packed ARGB
 * ints (semi-transparent, for a translucent overlay). NaN / above-datum → fully transparent.
 *
 * Pure (no Android types) so it is JVM-unit-testable; [DepthBitmap] applies it per cell.
 * NoData colour handling (water-aware) is done in [DepthBitmap].
 */
object DepthColorRamp {

    const val MAX_DEPTH_M = 60f

    fun argb(depthM: Float): Int {
        if (depthM.isNaN() || depthM < 0f) return 0  // NoData or above datum → transparent
        val d = depthM.coerceIn(0f, MAX_DEPTH_M)
        val t = d / MAX_DEPTH_M  // 0 shallow .. 1 deep

        // Base ramp: (200,232,255) pale cyan → (10,30,90) navy.
        var r = lerp(ZoneConfig.mapDepthRampShallowR, ZoneConfig.mapDepthRampDeepR, t)
        var g = lerp(ZoneConfig.mapDepthRampShallowG, ZoneConfig.mapDepthRampDeepG, t)
        var b = lerp(ZoneConfig.mapDepthRampShallowB, ZoneConfig.mapDepthRampDeepB, t)

        // Collision band 0–5 m: blend toward warning red-orange, strongest at the surface.
        if (d <= DepthConstants.COLLISION_MAX_DEPTH_M.toFloat()) {
            val w = 0.6f * (1f - d / DepthConstants.COLLISION_MAX_DEPTH_M.toFloat())
            r = lerp(r, ZoneConfig.mapDepthRampWarningR, w)
            g = lerp(g, ZoneConfig.mapDepthRampWarningG, w)
            b = lerp(b, ZoneConfig.mapDepthRampWarningB, w)
        }
        return (ZoneConfig.mapDepthRampAlpha shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun lerp(a: Int, b: Int, t: Float): Int =
        (a + (b - a) * t).roundToInt().coerceIn(0, 255)
}
