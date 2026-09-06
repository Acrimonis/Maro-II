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

    /** Radius of the small centre dot in px (matches MarkerOverlay's dot). */
    const val DOT_ANCHOR_RADIUS_PX = 18f

    /** Radius of the emoji-icon anchor in px (larger than the dot). */
    const val ICON_ANCHOR_RADIUS_PX = 30f

    /** Absolute halo ring radius in px at size % = 0 (1× dot size). */
    private const val MIN_RADIUS_PX = 18f

    /** Absolute halo ring radius in px at size % = 100 (2× icon size). */
    private const val MAX_RADIUS_PX = 60f

    /** Border ring stroke width in px. */
    private const val BORDER_STROKE_PX = 4f

    /**
     * Map a halo size % (0-100) to an absolute halo ring radius in px.
     *
     * The radius is absolute — 18px at size 0 → 60px at size 100 — and is applied
     * uniformly to both dot and icon anchors (NOT proportional to the anchor).
     */
    fun radiusPxFor(sizePct: Int): Float {
        val t = sizePct.coerceIn(0, 100) / 100f
        return MIN_RADIUS_PX + (MAX_RADIUS_PX - MIN_RADIUS_PX) * t
    }

    /**
     * Build a halo [Bitmap]: a filled disc of [spec.color] at [spec.fillTransparencyPct]
     * with a border ring at [spec.borderTransparencyPct].
     *
     * The halo ring radius is absolute ([radiusPxFor]) and independent of the anchor
     * it surrounds; [anchorRadiusPx] is retained only for sizing/centering the bitmap
     * so the halo is drawn behind the anchor.
     *
     * @param sizePct     Halo size % (0-100) controlling the ring radius.
     * @param dimFraction Optional fade factor (0..1) applied when the marker is a
     *                    search non-match so the halo fades together with the marker.
     */
    fun createBitmap(
        spec: MarkerHaloSpec,
        anchorRadiusPx: Float,
        sizePct: Int,
        dimFraction: Float = 1f
    ): Bitmap {
        val haloRadius = radiusPxFor(sizePct)
        val size = (haloRadius * 2 + BORDER_STROKE_PX * 2 + 4).toInt()
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
                strokeWidth = BORDER_STROKE_PX
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
