package ykws.android.maro.ui.map

import androidx.compose.ui.graphics.Color
import ykws.android.maro.data.regulation.RegulatedZoneType
import ykws.android.maro.data.regulation.ZoneDisplayCategory

/**
 * Provides emoji and colour mappings for [RegulatedZoneType] icons used in the
 * [RegulatedZoneWarningStrip] composable, plus display-category icons for the
 * 5 semantic zone categories.
 *
 * This is a pure mapping provider — no bitmap generation needed since the
 * warning strip uses Compose [Text] with emoji directly (same pattern as
 * [GpsStatusIcon] uses "📡").
 */
object RegulatedZoneIconProvider {

    /** Emoji character for each zone type (legacy — used for map rendering). */
    fun emojiForType(type: RegulatedZoneType): String = when (type) {
        RegulatedZoneType.SPEED_LIMIT -> "\u26A1\uFE0F"           // ⚡️ lightning
        RegulatedZoneType.ANCHORING_PROHIBITED -> "\u2693\uFE0F"  // ⚓️ anchor
        RegulatedZoneType.ACCESS_PROHIBITED -> "\uD83D\uDEAB"     // 🚫 prohibited
        RegulatedZoneType.ENVIRONMENTAL -> "\uD83C\uDF3F"          // 🌿 herb
        RegulatedZoneType.MOORING -> "\uD83D\uDE9F"                // 🛟 ring buoy
        RegulatedZoneType.FISHING_PROHIBITED -> "\uD83D\uDC1F"     // 🐟 fish
        RegulatedZoneType.NAVIGATION_RESTRICTION -> "\u26A0\uFE0F" // ⚠️ warning
        RegulatedZoneType.OTHER -> "\u2753"                        // ❓ question mark
    }

    /** Background colour tint for each zone type (legacy — used for map rendering). */
    fun colorForType(type: RegulatedZoneType): Color = when (type) {
        RegulatedZoneType.SPEED_LIMIT -> Color(0xFF1565C0)           // Blue
        RegulatedZoneType.ANCHORING_PROHIBITED -> Color(0xFFFF8F00)  // Amber
        RegulatedZoneType.ACCESS_PROHIBITED -> Color(0xFFE53935)     // Red
        RegulatedZoneType.ENVIRONMENTAL -> Color(0xFF2E7D32)         // Green
        RegulatedZoneType.MOORING -> Color(0xFF00897B)               // Teal
        RegulatedZoneType.FISHING_PROHIBITED -> Color(0xFFFDD835)    // Yellow
        RegulatedZoneType.NAVIGATION_RESTRICTION -> Color(0xFF8E24AA) // Purple
        RegulatedZoneType.OTHER -> Color(0xFF78909C)                 // Blue Grey
    }

    // ── Display category mappings (warning strip) ──────────────────────────

    /**
     * Emoji character for each [ZoneDisplayCategory].
     * Speed limit zones use the knot number instead of an emoji (rendered elsewhere).
     */
    fun emojiForCategory(category: ZoneDisplayCategory): String = when (category) {
        ZoneDisplayCategory.NO_ANCHOR -> "\u2693\uFE0F"         // ⚓️ anchor (emoji-presentation)
        ZoneDisplayCategory.MOORING -> "\uD83D\uDEA4"           // 🚤 speedboat
        ZoneDisplayCategory.SPEED_LIMIT -> ""                   // number rendered separately
        ZoneDisplayCategory.NO_DIVING -> "\uD83E\uDD3F"         // 🤿 diving mask
        ZoneDisplayCategory.SEAPLANE -> "\uD83D\uDEEB"          // 🛬 airplane landing
        ZoneDisplayCategory.NO_ACCESS -> "\uD83D\uDEA4"         // 🚤 speedboat (same as mooring, differentiated by strike)
    }

    /** Background colour for each [ZoneDisplayCategory] — all except SPEED use dark blue theme. */
    fun colorForCategory(category: ZoneDisplayCategory): Color = when (category) {
        ZoneDisplayCategory.NO_ANCHOR -> Color(0xFF1565C0)      // Dark blue — uniform background
        ZoneDisplayCategory.MOORING -> Color(0xFF1565C0)        // Dark blue — uniform background
        ZoneDisplayCategory.SPEED_LIMIT -> Color(0xFFE53935)    // Red — speed limit (unchanged)
        ZoneDisplayCategory.NO_DIVING -> Color(0xFF1565C0)      // Dark blue — uniform background
        ZoneDisplayCategory.SEAPLANE -> Color(0xFF1565C0)       // Dark blue — uniform background
        ZoneDisplayCategory.NO_ACCESS -> Color(0xFF1565C0)      // Dark blue — uniform background
    }

    /**
     * Background alpha (0.0–1.0) for each [ZoneDisplayCategory].
     * Prohibition/warning icons use [ZoneConfig.iconBackActiveAlpha] (75 %),
     * informational icons use [ZoneConfig.iconBackInactiveAlpha] (50 %).
     */
    fun alphaForCategory(category: ZoneDisplayCategory): Float {
        val alphaInt = when (category) {
            ZoneDisplayCategory.SEAPLANE -> ZoneConfig.iconBackInactiveAlpha
            else -> ZoneConfig.iconBackActiveAlpha
        }
        return alphaInt.toFloat() / 255f
    }
}
