
package ykws.android.maro.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import ykws.android.maro.data.regulation.contains
import ykws.android.maro.data.regulation.displayCategories
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.regulation.RegulatedZoneSet
import ykws.android.maro.data.regulation.ZoneDisplayCategory
import ykws.android.maro.data.settings.AppSettings

/**
 * Priority for vertical stack ordering — most restrictive (lowest index)
 * placed at the bottom, informational (highest index) at the top.
 */
val CATEGORY_PRIORITY: Map<ZoneDisplayCategory, Int> = mapOf(
    ZoneDisplayCategory.SPEED_LIMIT to 0,
    ZoneDisplayCategory.NO_ACCESS to 1,
    ZoneDisplayCategory.NO_ANCHOR to 2,
    ZoneDisplayCategory.NO_DIVING to 3,
    ZoneDisplayCategory.FISHING_PROHIBITED to 4,
    ZoneDisplayCategory.MOORING to 5,
    ZoneDisplayCategory.SEAPLANE to 6,
    ZoneDisplayCategory.ENVIRONMENTAL to 7,
    ZoneDisplayCategory.INFORMATION to 8,
)

/**
 * Bottom-left warning strip showing icons as a vertical stack.
 *
 * Icons are 44×44 dp, ordered from most restrictive (SPEED_LIMIT at the
 * bottom) to informational (INFORMATION at the top). Deduplicates by
 * (displayCategory, speedLimitKn).
 *
 * When [inZone300] is true, the 300m zone is injected as a SPEED_LIMIT entry
 * at the highest priority (bottom of stack), and regulated SPEED_LIMIT icons
 * are suppressed to avoid duplicating speed limit info.
 */
@Composable
fun RegulatedZoneWarningStrip(
    regulatedZones: RegulatedZoneSet?,
    boatPosition: LatLng? = null,
    inZone300: Boolean = false,
    modifier: Modifier = Modifier
) {
    val categories = remember(regulatedZones, boatPosition, inZone300) {
        val base = if (regulatedZones != null && regulatedZones.zones.isNotEmpty()) {
            val zones = if (boatPosition != null) {
                regulatedZones.zones.filter { it.contains(boatPosition) }
            } else {
                regulatedZones.zones
            }
            if (zones.isEmpty()) {
                emptyList()
            } else {
                zones
                    .flatMap { zone ->
                        val speed = zone.speedLimitKn
                            ?: parseSpeedFromDescription(zone.description)
                        zone.displayCategories().map { cat -> cat to speed }
                    }
                    .filter { (cat, speed) -> cat != ZoneDisplayCategory.SPEED_LIMIT || speed != null }
                    // When in 300m zone, suppress regulated speed limit icons (300m replaces them)
                    .filter { (cat, _) -> !(inZone300 && cat == ZoneDisplayCategory.SPEED_LIMIT) }
                    .distinct()
            }
        } else {
            emptyList()
        }

        // When in the 300m zone, inject it as the highest-priority SPEED_LIMIT entry
        val withZone300 = if (inZone300) {
            val zoneSpeed = AppConfig.zoneRegulatorySpeedKn.toDouble()
            base + (ZoneDisplayCategory.SPEED_LIMIT to zoneSpeed)
        } else {
            base
        }

        if (withZone300.isEmpty()) return@remember emptyList()

        // Sort by priority — most restrictive first (bottom of stack)
        withZone300.sortedBy { (cat, _) -> CATEGORY_PRIORITY[cat] ?: Int.MAX_VALUE }
    }

    if (categories.isEmpty()) return

    // Vertical column: first item at bottom (most restrictive), last at top
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // Render in reverse so the first sorted item (most restrictive)
        // appears at the bottom of the stack
        categories.reversed().forEach { (category, speedKn) ->
            RegulationZoneCategoryIcon(category = category, speedKn = speedKn)
        }
    }
}

/**
 * A single 44×44 dp icon for a [ZoneDisplayCategory], displaying the category's
 * emoji (or speed number) on a coloured rounded-square background, with a thin
 * Canvas-drawn red diagonal strike overlay for prohibition categories.
 *
 * Speed limit zones render the knot value as bold white text instead of an emoji
 * (e.g. "5" or "10") so the user can distinguish different speed limits at a glance.
 *
 * Background alpha is sourced from [RegulatedZoneIconProvider.alphaForCategory]:
 * prohibition/warning icons use [AppConfig.iconBackActiveAlpha] (75 %),
 * informational icons use [AppConfig.iconBackInactiveAlpha] (50 %).
 *
 * Categories requiring a strike (NO_ANCHOR, NO_DIVING, NO_ACCESS) render the emoji
 * Text first, then overlay a thin red diagonal line via Canvas on top.
 */
@Composable
fun RegulationZoneCategoryIcon(
    category: ZoneDisplayCategory,
    speedKn: Double? = null,
    modifier: Modifier = Modifier
) {
    val bgColor = RegulatedZoneIconProvider.colorForCategory(category)
    val alpha = RegulatedZoneIconProvider.alphaForCategory(category)
    val hasStrike = category == ZoneDisplayCategory.NO_ANCHOR ||
            category == ZoneDisplayCategory.NO_DIVING ||
            category == ZoneDisplayCategory.NO_ACCESS ||
            category == ZoneDisplayCategory.FISHING_PROHIBITED

    Box(
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor.copy(alpha = alpha)),
        contentAlignment = Alignment.Center
    ) {
        if (category == ZoneDisplayCategory.SPEED_LIMIT) {
            Text(
                text = if (speedKn != null) "${speedKn.toInt()}" else "",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = ComposeColor(AppConfig.uiSettingsTextPrimary)
            )
        } else {
            // Emoji Text for all non-speed categories
            Text(
                text = RegulatedZoneIconProvider.emojiForCategory(category),
                fontSize = 24.sp
            )
        }

        // Thin red diagonal strike overlay for prohibition categories
        if (hasStrike) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val sw = size.width * 0.04f // thinner strike
                drawLine(
                    color = ComposeColor.Red,
                    start = Offset(size.width * 0.08f, size.height * 0.08f),
                    end = Offset(size.width * 0.92f, size.height * 0.92f),
                    strokeWidth = sw
                )
            }
        }
    }
}


/**
 * Zone info text panel — shows zone info text beside the vertical icon stack.
 *
 * Builds the same deduplicated category list as [RegulatedZoneWarningStrip] so
 * text lines match the icon stack exactly — same emoji, same priority order.
 *
 * Format per line: {category_emoji} {zone.name or fallback} — {speed or desc}
 * Ordered by [CATEGORY_PRIORITY] (most restrictive at bottom, matching icons).
 * Auto-wraps within the available space.
 */
@Composable
fun RegulatedZoneInfoText(
    regulatedZones: RegulatedZoneSet?,
    boatPosition: LatLng? = null,
    inZone300: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Derive the same deduplicated (category, speedKn) pairs as the warning strip
    val categoryLines = remember(regulatedZones, boatPosition, inZone300) {
        val base = if (regulatedZones != null && regulatedZones.zones.isNotEmpty()) {
            val zones = if (boatPosition != null) {
                regulatedZones.zones.filter { it.contains(boatPosition) }
            } else {
                regulatedZones.zones
            }
            if (zones.isEmpty()) {
                emptyList()
            } else {
                zones
                    .flatMap { zone ->
                        val speed = zone.speedLimitKn
                            ?: parseSpeedFromDescription(zone.description)
                        // Pair each display category with the zone it came from
                        zone.displayCategories().map { cat -> Triple(cat, speed, zone) }
                    }
                    .filter { (cat, speed, _) -> cat != ZoneDisplayCategory.SPEED_LIMIT || speed != null }
                    // When in 300m zone, suppress regulated speed limit info text (300m replaces it)
                    .filter { (cat, _, _) -> !(inZone300 && cat == ZoneDisplayCategory.SPEED_LIMIT) }
                    .distinctBy { (cat, speed, _) -> cat to speed }
            }
        } else {
            emptyList()
        }

        // When in the 300m zone, inject it as the highest-priority SPEED_LIMIT info line
        val withZone300 = if (inZone300) {
            val zoneSpeed = AppConfig.zoneRegulatorySpeedKn.toDouble()
            base + Triple(ZoneDisplayCategory.SPEED_LIMIT, zoneSpeed, null)
        } else {
            base
        }

        if (withZone300.isEmpty()) return@remember emptyList()

        withZone300.sortedBy { (cat, _, _) -> CATEGORY_PRIORITY[cat] ?: Int.MAX_VALUE }
    }

    if (categoryLines.isEmpty()) return

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Bottom
    ) {
        // Render in reverse so most restrictive (first in sorted order) is at bottom
        categoryLines.reversed().forEach { (category, speedKn, zone) ->
            val emoji = if (category == ZoneDisplayCategory.SPEED_LIMIT) {
                "\uD83D\uDD34"  // 🔴 red dot for speed info
            } else {
                RegulatedZoneIconProvider.emojiForCategory(category)
            }
            // 300m zone synthetic entry (zone == null) uses hardcoded name
            val name = if (zone != null) {
                val rawName = zone.name
                if (rawName.isNullOrBlank() || rawName.equals("null", ignoreCase = true)) {
                    zone.zoneType.name.lowercase().replace('_', ' ')
                } else {
                    rawName
                }
            } else {
                "300 m Zone"
            }
            val keyInfo = when {
                speedKn != null -> "${speedKn.toInt()} nds"
                zone != null && zone.description.isNotBlank() -> zone.description.replace("\n", " ")
                else -> ""
            }
            Text(
                text = if (keyInfo.isNotBlank()) "$emoji $name — $keyInfo" else "$emoji $name",
                fontSize = 9.sp,
                lineHeight = 14.sp,
                color = ComposeColor(AppConfig.uiSettingsTextPrimary),
            )
        }
    }
}

/**
 * Category icon visibility toggles for the regulated zone warning strip.
 * Each icon type can be individually hidden.
 */
@Composable
fun RegulatedZoneCategoryToggles(
    settings: AppSettings,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ComposeColor(AppConfig.uiSettingsCardBackground))
    ) {
        categoryToggleItems.forEachIndexed { index, item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (item.iconIsRedBox) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(ComposeColor(AppConfig.uiSettingsDanger)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("10", color = ComposeColor(AppConfig.uiSettingsTextPrimary), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Text(item.emoji, fontSize = 18.sp)
                        }
                        // Red diagonal strike overlay for prohibition categories
                        if (item.hasStrike) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val sw = size.width * 0.06f
                                drawLine(
                                    color = ComposeColor.Red,
                                    start = Offset(size.width * 0.1f, size.height * 0.1f),
                                    end = Offset(size.width * 0.9f, size.height * 0.9f),
                                    strokeWidth = sw
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = item.label, color = ComposeColor(AppConfig.uiSettingsTextPrimary), fontSize = 14.sp)
                }
                Switch(
                    checked = item.isVisible(settings),
                    onCheckedChange = { visible ->
                        onUpdateSettings { item.setter(it, visible) }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ComposeColor(AppConfig.uiSettingsAccent),
                        checkedTrackColor = ComposeColor(AppConfig.uiSettingsAccent).copy(alpha = 0.4f)
                    )
                )
            }
            if (index < categoryToggleItems.size - 1) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .padding(horizontal = 16.dp)
                        .background(ComposeColor(AppConfig.uiSettingsCardBackground))
                )
            }
        }
    }
}

/**
 * Boat size slider (3–25m) for filtering regulated zones by vessel length.
 * Shown in its own collapsible expander within the regulated zones card.
 */
@Composable
fun BoatSizeSlider(
    settings: AppSettings,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ComposeColor(AppConfig.uiSettingsCardBackground))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "\uD83D\uDEA4 Boat length",
                color = ComposeColor(AppConfig.uiSettingsTextPrimary),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${settings.boatSizeM.toInt()} m",
                color = ComposeColor(AppConfig.uiSettingsAccent),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Slider(
            value = settings.boatSizeM.toFloat(),
            onValueChange = { v ->
                onUpdateSettings { it.copy(boatSizeM = v.toDouble().coerceIn(3.0, 25.0)) }
            },
            valueRange = 3f..25f,
            steps = 21,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = ComposeColor(AppConfig.uiSettingsAccent),
                activeTrackColor = ComposeColor(AppConfig.uiSettingsAccent),
                inactiveTrackColor = ComposeColor(AppConfig.uiSettingsAccent).copy(alpha = 0.3f)
            )
        )
    }
}

data class CategoryToggleItem(
    val emoji: String,
    val label: String,
    val iconIsRedBox: Boolean = false,
    val hasStrike: Boolean = false,
    val isVisible: (AppSettings) -> Boolean,
    val setter: (AppSettings, Boolean) -> AppSettings,
)

val categoryToggleItems = listOf(
    CategoryToggleItem("", "Speed limit", iconIsRedBox = true,
        isVisible = { it.showCategorySpeedLimit },
        setter = { s, v -> s.copy(showCategorySpeedLimit = v) }),
    CategoryToggleItem("\uD83D\uDEA4", "No access",
        hasStrike = true,
        isVisible = { it.showCategoryNoAccess },
        setter = { s, v -> s.copy(showCategoryNoAccess = v) }),
    CategoryToggleItem("\u2693\uFE0F", "No anchor",
        hasStrike = true,
        isVisible = { it.showCategoryNoAnchor },
        setter = { s, v -> s.copy(showCategoryNoAnchor = v) }),
    CategoryToggleItem("\uD83E\uDD3F", "No diving",
        hasStrike = true,
        isVisible = { it.showCategoryNoDiving },
        setter = { s, v -> s.copy(showCategoryNoDiving = v) }),
    CategoryToggleItem("\uD83D\uDC1F", "Fishing prohibited",
        hasStrike = true,
        isVisible = { it.showCategoryFishingProhibited },
        setter = { s, v -> s.copy(showCategoryFishingProhibited = v) }),
    CategoryToggleItem("\uD83D\uDEA4", "Mooring",
        isVisible = { it.showCategoryMooring },
        setter = { s, v -> s.copy(showCategoryMooring = v) }),
    CategoryToggleItem("\u2708\uFE0F", "Seaplane",
        isVisible = { it.showCategorySeaplane },
        setter = { s, v -> s.copy(showCategorySeaplane = v) }),
    CategoryToggleItem("\uD83C\uDF3F", "Environmental",
        isVisible = { it.showCategoryEnvironmental },
        setter = { s, v -> s.copy(showCategoryEnvironmental = v) }),
    CategoryToggleItem("\u2139\uFE0F", "Information",
        isVisible = { it.showCategoryInformation },
        setter = { s, v -> s.copy(showCategoryInformation = v) }),
)

/**
 * Extract a speed limit (knots) from a zone description string as fallback
 * when [RegulatedZone.speedLimitKn] is null. Handles "speed is limited to
 * 10 knots", "speed limit of 5 knots", "3 kn", "10 noeuds", etc.
 */
fun parseSpeedFromDescription(desc: String?): Double? {
    if (desc == null) return null
    // Handle "5 knots", "10 kn", "3 noeuds" — plural 's' is optional so
    // "10 knots" doesn't get blocked by the \b word boundary after "knot".
    return Regex("""(\d+[.]?\d*)\s*(?:knots?|noeuds?|nds|kn)\b""", RegexOption.IGNORE_CASE)
        .find(desc.lowercase())
        ?.groupValues?.get(1)?.toDoubleOrNull()
}

/**
 * Filter regulated zones by boat size and per-category visibility.
 *
 * Pipeline:
 * 1. Remove zones that don't apply to the configured [boatSizeM] (e.g. "≥ 24m" with a 6m boat)
 * 2. Remove zones whose display categories are all toggled off in settings
 * 3. Return null if no zones remain (layer auto-hides)
 */
fun filterRegulatedZones(
    zones: RegulatedZoneSet?,
    boatSizeM: Double,
    isCategoryVisible: (ZoneDisplayCategory) -> Boolean
): RegulatedZoneSet? {
    if (zones == null) return null
    val filtered = zones.zones.filter { zone ->
        if (!zone.appliesTo(boatSizeM)) return@filter false
        val cats = zone.displayCategories()
        if (cats.isEmpty()) return@filter false
        cats.any { isCategoryVisible(it) }
    }
    if (filtered.isEmpty()) return null
    return zones.copy(zones = filtered)
}
