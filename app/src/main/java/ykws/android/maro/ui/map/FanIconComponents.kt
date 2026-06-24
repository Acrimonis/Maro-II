
package ykws.android.maro.ui.map
import ykws.android.maro.config.AppConfig
import ykws.android.maro.ui.icons.Activity_zone
import ykws.android.maro.ui.icons.Conversion_path
import ykws.android.maro.ui.icons.Output_circle
import ykws.android.maro.ui.icons.Stacks

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AreaChart
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.unit.dp

// ── Single source of truth for all action-button colours ──────────────────────

object ButtonColors {
    val bg: ComposeColor get() = ComposeColor(AppConfig.buttonActionBgColor)
    val icon: ComposeColor get() = ComposeColor(AppConfig.buttonActionIconColor)
    val activeAlpha: Float get() = AppConfig.buttonActionIconActiveAlpha
    val inactiveAlpha: Float get() = AppConfig.buttonActionIconInactiveAlpha
    val badgeText: ComposeColor get() = ComposeColor(AppConfig.uiButtonBadgeText)
    val badgeActiveAlpha: Float get() = AppConfig.buttonBadgeActiveAlpha
    val badgeInactiveAlpha: Float get() = AppConfig.buttonBadgeInactiveAlpha
}

private const val ICON_SIZE_DP = 28

// ── Arc menu fan icons ────────────────────────────────────────────────────────
// Google Fonts name → Compose name:
//   stacks           → Layers
//   area_chart       → AreaChart
//   activity_zone    → GpsFixed       (no exact match)
//   output_circle    → Circle
//   conversion_path  → AltRoute       (no exact match)
//   warning          → Warning
//   add              → Add
//   remove           → Remove
//   menu             → Menu
//   location_on/off  → LocationOn/LocationOff
//   file_export      → Upload          (no exact match)

@Composable
fun WarningTriangleIcon(alpha: Float) {
    Icon(
        imageVector = Icons.Filled.Warning,
        contentDescription = null,
        tint = ButtonColors.icon,
        modifier = Modifier.size(ICON_SIZE_DP.dp).alpha(alpha)
    )
}

@Composable
fun PlusIcon() {
    Icon(
        imageVector = Icons.Filled.Add,
        contentDescription = null,
        tint = ButtonColors.icon,
        modifier = Modifier.size(ICON_SIZE_DP.dp)
    )
}

@Composable
fun MinusIcon() {
    Icon(
        imageVector = Icons.Filled.Remove,
        contentDescription = null,
        tint = ButtonColors.icon,
        modifier = Modifier.size(ICON_SIZE_DP.dp)
    )
}

@Composable
fun GearIcon() {
    Icon(
        imageVector = Icons.Default.Settings,
        contentDescription = null,
        tint = ButtonColors.icon,
        modifier = Modifier.size(ICON_SIZE_DP.dp)
    )
}

/** Fan parent: stacks */
@Composable
fun ThreeStripeLayerIcon(alpha: Float) {
    Icon(
        imageVector = Stacks,
        contentDescription = null,
        tint = ButtonColors.icon,
        modifier = Modifier.size(ICON_SIZE_DP.dp).alpha(alpha)
    )
}

/** Depth layer child: area_chart → AreaChart */
@Composable
fun DepthBarIcon(alpha: Float) {
    Icon(
        imageVector = Icons.Filled.AreaChart,
        contentDescription = null,
        tint = ButtonColors.icon,
        modifier = Modifier.size(ICON_SIZE_DP.dp).alpha(alpha)
    )
}

/** Regulated zones child: activity_zone */
@Composable
fun RegulatedZoneIcon(alpha: Float) {
    Icon(
        imageVector = Activity_zone,
        contentDescription = null,
        tint = ButtonColors.icon,
        modifier = Modifier.size(ICON_SIZE_DP.dp).alpha(alpha)
    )
}

/** 300m zone child: output_circle */
@Composable
fun DoubleCircleIcon(alpha: Float) {
    Icon(
        imageVector = Output_circle,
        contentDescription = null,
        tint = ButtonColors.icon,
        modifier = Modifier.size(ICON_SIZE_DP.dp).alpha(alpha)
    )
}

/** Tracks child: conversion_path */
@Composable
fun TrackLayerIcon(alpha: Float) {
    Icon(
        imageVector = Conversion_path,
        contentDescription = null,
        tint = ButtonColors.icon,
        modifier = Modifier.size(ICON_SIZE_DP.dp).alpha(alpha)
    )
}
