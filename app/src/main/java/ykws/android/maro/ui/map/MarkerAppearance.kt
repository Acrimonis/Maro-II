package ykws.android.maro.ui.map

import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.markers.UserMarker

/**
 * Centralises the marker visual-state decision: given a marker and the current
 * interaction context (selected / unconfirmed / match / pinned / unpinned), it
 * resolves the concrete colour + stroke + halo appearance used by [MarkerOverlay].
 *
 * Pure and testable — no map coupling. MarkerOverlay stays the conductor (deciding
 * *whether* a marker gets a halo) and delegates the *what* to this class.
 *
 * ## Colour precedence (matches the pre-refactor inline chain)
 * selected (gold) > unconfirmed (caution) > (match active) non-match dim >
 * (match active) match full > pinned full > unpinned full.
 *
 * Note: pin dimming is removed — unpinned markers now render at full marker colour
 * like pinned ones; the halo is the only pinned-vs-unpinned differentiator.
 * Search dimming is kept (non-matching markers fade during a whereAmI result).
 *
 * The **wizard's synthetic preview** ([isPreview]) is the one unconfirmed marker that keeps its palette
 * colour: it stands for a marker the user is still describing, so a swatch picked in the wizard has to
 * read on the map at once. A stored unconfirmed marker — the 🕐 idle pin — keeps the caution colour,
 * and no unconfirmed marker draws a halo either way.
 *
 * A **routing-weight-only** marker (`routingOnly`) is a *variant*, not a state: it keeps its place in the
 * precedence above, and only its rings and its fill change — every ring dashes and the fill hatches. That
 * decision is made here, once, so the two painters ([MarkerHalo]'s bitmap and [MarkerOverlay]'s zone)
 * read the same flag instead of each deciding for itself.
 */
data class MarkerAppearance(
    /** Resolved marker colour (ARGB) used for dots, lines and zone strokes. */
    val baseColor: Int,
    /** Stroke-width multiplier (selected 2.5×, matched 1.67×, else 1.0×). */
    val strokeMultiplier: Float,
    /** True when this marker is the currently selected/viewed marker (gold dual-outline). */
    val isSelected: Boolean,
    /** True when a whereAmI result is active and this marker is NOT a match (faded). */
    val isDimmed: Boolean,
    /**
     * True for a routing-weight-only marker: every ring it draws is dashed and its fill is hatched, so
     * the map tells it apart from a marker that also names where the boat is.
     */
    val isRoutingOnly: Boolean,
    /** Halo spec when a halo ring should be drawn, else null (unconfirmed / selected). */
    val halo: MarkerHaloSpec?
) {

    companion object {
        /** Gold colour for the selected marker. */
        private val COLOR_SELECTED = 0xFFFFD700.toInt()

        /** Unconfirmed (in-progress) marker colour (semantic.caution amber). */
        private val COLOR_UNCONFIRMED = AppConfig.semanticCaution

        /** Alpha for dimmed (non-matched) markers during match-result highlighting (30%). */
        private const val DIMMED_ALPHA_FRACTION = 0.30f

        /** Brighter stroke multiplier for matched markers (3dp → 5dp ≈ 1.67×). */
        private const val MATCHED_STROKE_MULTIPLIER = 1.67f

        /** Stroke multiplier for the selected marker. */
        private const val SELECTED_STROKE_MULTIPLIER = 2.5f

        /**
         * Resolve the appearance for [marker].
         *
         * @param markerColor     The marker's own palette colour ([MarkerColors.of]).
         * @param isMatched       True when a whereAmI result is active and this marker matched.
         * @param matchActive     True when a whereAmI result is currently displayed.
         * @param selectedMarkerId The currently selected/viewed marker id (single driver for gold).
         * @param pinnedColor     Pinned halo colour (ARGB).
         * @param unpinnedColor   Unpinned halo colour (ARGB).
         * @param pinnedFillPct   Pinned halo fill transparency % (0 = opaque, 100 = invisible).
         * @param pinnedBorderPct Pinned halo border transparency %.
         * @param unpinnedFillPct Unpinned halo fill transparency %.
         * @param unpinnedBorderPct Unpinned halo border transparency %.
         * @param isPreview       True for the wizard's synthetic placement marker, which draws its own
         *                        palette colour instead of the caution colour.
         */
        fun of(
            marker: UserMarker,
            markerColor: Int,
            isMatched: Boolean,
            matchActive: Boolean,
            selectedMarkerId: String?,
            pinnedColor: Int,
            unpinnedColor: Int,
            pinnedFillPct: Int,
            pinnedBorderPct: Int,
            unpinnedFillPct: Int,
            unpinnedBorderPct: Int,
            isPreview: Boolean = false
        ): MarkerAppearance {
            val isSelected = marker.id == selectedMarkerId
            val confirmed = marker.confirmed

            val baseColor = if (isSelected) {
                COLOR_SELECTED
            } else when {
                !confirmed && !isPreview -> COLOR_UNCONFIRMED
                matchActive && !isMatched -> dimColor(markerColor, DIMMED_ALPHA_FRACTION)
                matchActive -> markerColor
                else -> markerColor   // pinned and unpinned both render full colour (no pin dimming)
            }

            val strokeMultiplier = when {
                isSelected -> SELECTED_STROKE_MULTIPLIER
                matchActive && isMatched -> MATCHED_STROKE_MULTIPLIER
                else -> 1.0f
            }

            val isDimmed = matchActive && !isMatched

            // Halo: only on confirmed markers; selected overrides pinned (gold replaces halo).
            val halo = if (!confirmed || isSelected) {
                null
            } else {
                val (color, fill, border) = if (marker.pinned) {
                    Triple(pinnedColor, pinnedFillPct, pinnedBorderPct)
                } else {
                    Triple(unpinnedColor, unpinnedFillPct, unpinnedBorderPct)
                }
                // Both fully transparent (100) → no visible halo.
                if (fill >= 100 && border >= 100) null
                else MarkerHaloSpec(color, fill, border, dashed = marker.routingOnly)
            }

            return MarkerAppearance(
                baseColor = baseColor,
                strokeMultiplier = strokeMultiplier,
                isSelected = isSelected,
                isDimmed = isDimmed,
                isRoutingOnly = marker.routingOnly,
                halo = halo
            )
        }

        /** Dim an ARGB colour by reducing its alpha channel to [alphaFraction] of the original. */
        fun dimColor(color: Int, alphaFraction: Float): Int {
            val newAlpha = ((color ushr 24) * alphaFraction).toInt().coerceIn(0, 255)
            return (newAlpha shl 24) or (color and 0x00FFFFFF)
        }
    }
}

/**
 * Describes a halo ring to draw behind a confirmed marker's centre dot/icon.
 *
 * @property color        Shared halo colour (opaque ARGB).
 * @property fillTransparencyPct Inside-fill transparency % (0 = opaque, 100 = invisible).
 * @property borderTransparencyPct Border/stroke transparency % (0 = opaque, 100 = invisible).
 */
data class MarkerHaloSpec(
    val color: Int,
    val fillTransparencyPct: Int,
    val borderTransparencyPct: Int,
    /** True when the border ring is dashed rather than solid — a routing-weight-only marker's ring. */
    val dashed: Boolean = false
)
