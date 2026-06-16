
package ykws.android.maro.ui.map
import ykws.android.maro.config.AppConfig

import androidx.compose.ui.graphics.Color
import ykws.android.maro.data.regulation.RegulatedZoneType
import ykws.android.maro.data.regulation.ZoneDisplayCategory

/**
 * Provides emoji and colour mappings for [RegulatedZoneType] icons used in the
 * [RegulatedZoneWarningStrip] composable, plus display-category icons for the
 * 8 semantic zone categories.
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
        RegulatedZoneType.SPEED_LIMIT -> Color(AppConfig.regulatedZoneTypeSpeedLimit)           // Blue
        RegulatedZoneType.ANCHORING_PROHIBITED -> Color(AppConfig.regulatedZoneTypeAnchoringProhibited)  // Amber
        RegulatedZoneType.ACCESS_PROHIBITED -> Color(AppConfig.regulatedZoneTypeAccessProhibited)     // Red
        RegulatedZoneType.ENVIRONMENTAL -> Color(AppConfig.regulatedZoneTypeEnvironmental)         // Green
        RegulatedZoneType.MOORING -> Color(AppConfig.regulatedZoneTypeMooring)               // Teal
        RegulatedZoneType.FISHING_PROHIBITED -> Color(AppConfig.regulatedZoneTypeFishingProhibited)    // Yellow
        RegulatedZoneType.NAVIGATION_RESTRICTION -> Color(AppConfig.regulatedZoneTypeNavigationRestriction) // Purple
        RegulatedZoneType.OTHER -> Color(AppConfig.regulatedZoneTypeOther)                 // Blue Grey
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
        ZoneDisplayCategory.SEAPLANE -> "\u2708\uFE0F"          // ✈️ airplane
        ZoneDisplayCategory.NO_ACCESS -> "\uD83D\uDEA4"         // 🚤 speedboat (same as mooring, differentiated by strike)
        ZoneDisplayCategory.FISHING_PROHIBITED -> "\uD83D\uDC1F" // 🐟 fish
        ZoneDisplayCategory.ENVIRONMENTAL -> "\uD83C\uDF3F"      // 🌿 herb
        ZoneDisplayCategory.INFORMATION -> "\u2139\uFE0F"        // ℹ️ information
    }

    /**
     * Background colour for each [ZoneDisplayCategory].
     *
     * All prohibition/info categories use dark blue (#1565C0) for a uniform
     * background, except:
     * - SPEED_LIMIT (red — stands out as primary action)
     * - SEAPLANE (grey — informational, low priority)
     */
    fun colorForCategory(category: ZoneDisplayCategory): Color = when (category) {
        ZoneDisplayCategory.NO_ANCHOR -> Color(AppConfig.regulatedZoneTypeSpeedLimit)      // Dark blue — uniform background
        ZoneDisplayCategory.MOORING -> Color(AppConfig.regulatedZoneTypeSpeedLimit)        // Dark blue — uniform background
        ZoneDisplayCategory.SPEED_LIMIT -> Color(AppConfig.regulatedZoneTypeAccessProhibited)    // Red — speed limit (stand out)
        ZoneDisplayCategory.NO_DIVING -> Color(AppConfig.regulatedZoneTypeSpeedLimit)      // Dark blue — uniform background
        ZoneDisplayCategory.SEAPLANE -> Color(AppConfig.regulatedZoneTypeOther)       // Blue Grey — low priority info
        ZoneDisplayCategory.NO_ACCESS -> Color(AppConfig.regulatedZoneTypeSpeedLimit)      // Dark blue — uniform background
        ZoneDisplayCategory.FISHING_PROHIBITED -> Color(AppConfig.regulatedZoneTypeSpeedLimit) // Dark blue — uniform background
        ZoneDisplayCategory.ENVIRONMENTAL -> Color(AppConfig.regulatedZoneTypeSpeedLimit)   // Dark blue — uniform background
        ZoneDisplayCategory.INFORMATION -> Color(AppConfig.regulatedZoneTypeSpeedLimit)     // Dark blue — uniform background
    }

    /**
     * Background alpha (0.0–1.0) for each [ZoneDisplayCategory].
     * Prohibition/warning icons use [AppConfig.statusGpsAlphaActive] (75 %),
     * informational icons use [AppConfig.statusGpsAlphaDimmed] (50 %).
     */
    fun alphaForCategory(category: ZoneDisplayCategory): Float {
        return when (category) {
            ZoneDisplayCategory.SEAPLANE -> AppConfig.statusGpsAlphaDimmed
            ZoneDisplayCategory.ENVIRONMENTAL -> AppConfig.statusGpsAlphaDimmed
            ZoneDisplayCategory.INFORMATION -> AppConfig.statusGpsAlphaDimmed
            else -> AppConfig.statusGpsAlphaActive
        }
    }
}
