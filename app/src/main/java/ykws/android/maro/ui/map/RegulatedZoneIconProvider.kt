
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
}
