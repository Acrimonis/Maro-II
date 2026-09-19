package ykws.android.maro.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint

/**
 * Owns the halo visuals for confirmed markers: computes the concrete colour/opacity
 * from a [MarkerHaloSpec] and builds a halo [Bitmap] (inner fill disc + outer border
 * ring) sized to its anchor — the small centre dot or the larger emoji icon.
 *
 * Pure logic, no map coupling. Bitmaps are created fresh per rebuild (marker counts
 * are low); bitmap caching is a possible future evolution.
 */
object MarkerHalo {

    /** Radius of the small centre dot in dp (matches MarkerOverlay's dot). */
    const val DOT_ANCHOR_RADIUS_DP = 6f

    /** Radius of the emoji-icon anchor in dp (larger than the dot). */
    const val ICON_ANCHOR_RADIUS_DP = 10f

    /** Halo ring radius in dp at size % = 0 and zoom 100 % (1× dot size). */
    private const val MIN_RADIUS_DP = 6f

    /** Halo ring radius in dp at size % = 100 and zoom 100 % (2× icon size). */
    private const val MAX_RADIUS_DP = 20f

    /** Border ring stroke width in dp. */
    private const val BORDER_STROKE_DP = 4f / 3f

    /** Padding left round the ring inside the bitmap, in dp. */
    private const val BITMAP_PADDING_DP = 4f / 3f

    /**
     * Map a halo size % (0-100) to a halo ring radius in dp.
     *
     * The radius at marker zoom 100 % is absolute — 6 dp at size 0 → 20 dp at size 100 — applied
     * uniformly to both dot and icon anchors (NOT proportional to the anchor). The marker
     * "point/icon rendering zoom" scales the whole marker (dot/icon), so by rule of three the ring
     * scales by the same factor ([zoomPct]/100) — at zoom 100 % the rendering is identical to today.
     *
     * Dp, like the rest of the map's lengths: [createBitmap] is where a density turns it into px.
     */
    fun radiusDpFor(sizePct: Int, zoomPct: Int = 100): Float {
        val t = sizePct.coerceIn(0, 100) / 100f
        val radiusAtZoom100 = MIN_RADIUS_DP + (MAX_RADIUS_DP - MIN_RADIUS_DP) * t
        return radiusAtZoom100 * zoomPct.coerceIn(50, 150) / 100f
    }

    /**
     * Build a halo [Bitmap]: a filled disc of [spec.color] at [spec.fillTransparencyPct]
     * with a border ring at [spec.borderTransparencyPct].
     *
     * The halo ring radius is absolute ([radiusDpFor]) and independent of the anchor
     * it surrounds; [anchorRadiusPx] is retained only for sizing/centering the bitmap
     * so the halo is drawn behind the anchor.
     *
     * @param density     The screen density: the ring, its border and the bitmap's padding are dp,
     *                    and this is the one conversion point in the object.
     * @param sizePct     Halo size % (0-100) controlling the ring radius.
     * @param zoomPct     Marker point/icon rendering zoom % (50-150). Scales the ring
     *                    with the marker (rule of three); 100 = today's size.
     * @param dimFraction Optional fade factor (0..1) applied when the marker is a
     *                    search non-match so the halo fades together with the marker.
     */
    fun createBitmap(
        spec: MarkerHaloSpec,
        anchorRadiusPx: Float,
        density: Float,
        sizePct: Int,
        zoomPct: Int = 100,
        dimFraction: Float = 1f
    ): Bitmap {
        val haloRadius = dpToPx(radiusDpFor(sizePct, zoomPct), density)
        val borderPx = dpToPx(BORDER_STROKE_DP, density)
        val size = (haloRadius * 2 + borderPx * 2 + dpToPx(BITMAP_PADDING_DP, density)).toInt()
        val center = size / 2f

        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Inner fill disc (transparency 100 = fully invisible → skip)
        if (spec.fillTransparencyPct < 100) {
            val fillPaint = Paint().apply {
                color = withAlpha(spec.color, spec.fillTransparencyPct, dimFraction)
                isAntiAlias = true
                style = Paint.Style.FILL
            }
            canvas.drawCircle(center, center, haloRadius, fillPaint)
        }

        // Outer border ring (transparency 100 = fully invisible → skip)
        if (spec.borderTransparencyPct < 100) {
            val borderPaint = Paint().apply {
                color = withAlpha(spec.color, spec.borderTransparencyPct, dimFraction)
                isAntiAlias = true
                style = Paint.Style.STROKE
                strokeWidth = borderPx
            }
            canvas.drawCircle(center, center, haloRadius, borderPaint)
        }

        return bitmap
    }

    /**
     * Return [color] with its alpha scaled by [transparencyPct] (0-100, 0 = opaque,
     * 100 = invisible) and an optional [dimFraction] (0..1). Used for the corridor
     * under-line halo so it matches the ring halos' colour/transparency treatment.
     */
    fun colorWithTransparency(color: Int, transparencyPct: Int, dimFraction: Float = 1f): Int =
        withAlpha(color, transparencyPct, dimFraction)

    /** Apply a transparency % (0-100) and an optional [dimFraction] to [color]'s alpha. */
    private fun withAlpha(color: Int, transparencyPct: Int, dimFraction: Float): Int {
        val baseAlpha = (color ushr 24) and 0xFF
        val scaled = (baseAlpha * ((100 - transparencyPct) / 100f) * dimFraction).toInt().coerceIn(0, 255)
        return (scaled shl 24) or (color and 0x00FFFFFF)
    }
}
