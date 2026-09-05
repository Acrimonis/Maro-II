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
         * @param pinnedFillPct   Pinned halo fill opacity %.
         * @param pinnedBorderPct Pinned halo border opacity %.
         * @param unpinnedFillPct Unpinned halo fill opacity %.
         * @param unpinnedBorderPct Unpinned halo border opacity %.
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
            unpinnedBorderPct: Int
        ): MarkerAppearance {
            val isSelected = marker.id == selectedMarkerId
            val confirmed = marker.confirmed

            val baseColor = if (isSelected) {
                COLOR_SELECTED
            } else when {
                !confirmed -> COLOR_UNCONFIRMED
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
                if (fill <= 0 && border <= 0) null
                else MarkerHaloSpec(color, fill, border)
            }

            return MarkerAppearance(
                baseColor = baseColor,
                strokeMultiplier = strokeMultiplier,
                isSelected = isSelected,
                isDimmed = isDimmed,
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
 * @property fillOpacityPct Inside-fill opacity % (0-100).
 * @property borderOpacityPct Border/stroke opacity % (0-100).
 */
data class MarkerHaloSpec(
    val color: Int,
    val fillOpacityPct: Int,
    val borderOpacityPct: Int
)
