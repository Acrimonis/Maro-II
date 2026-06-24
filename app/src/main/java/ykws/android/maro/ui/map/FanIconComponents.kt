
package ykws.android.maro.ui.map
import ykws.android.maro.config.AppConfig
import ykws.android.maro.ui.icons.Activity_zone
import ykws.android.maro.ui.icons.Conversion_path
import ykws.android.maro.ui.icons.Output_circle
import ykws.android.maro.ui.icons.Stacks

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
    val iconSizeDp: Int = 28
}

/** Standard icon size for control-stack buttons (28 dp). */
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
        modifier = Modifier.size(ButtonColors.iconSizeDp.dp).alpha(alpha)
    )
}

@Composable
fun PlusIcon() {
    Icon(
        imageVector = Icons.Filled.Add,
        contentDescription = null,
        tint = ButtonColors.icon,
        modifier = Modifier.size(ButtonColors.iconSizeDp.dp)
    )
}

@Composable
fun MinusIcon() {
    Icon(
        imageVector = Icons.Filled.Remove,
        contentDescription = null,
        tint = ButtonColors.icon,
        modifier = Modifier.size(ButtonColors.iconSizeDp.dp)
    )
}

@Composable
fun GearIcon() {
    Icon(
        imageVector = Icons.Default.Settings,
        contentDescription = null,
        tint = ButtonColors.icon,
        modifier = Modifier.size(ButtonColors.iconSizeDp.dp)
    )
}

/** Fan parent: stacks */
@Composable
fun ThreeStripeLayerIcon(alpha: Float) {
    Icon(
        imageVector = Stacks,
        contentDescription = null,
        tint = ButtonColors.icon,
        modifier = Modifier.size(ButtonColors.iconSizeDp.dp).alpha(alpha)
    )
}

/** Depth layer child: area_chart → AreaChart */
@Composable
fun DepthBarIcon(alpha: Float) {
    Icon(
        imageVector = Icons.Filled.AreaChart,
        contentDescription = null,
        tint = ButtonColors.icon,
        modifier = Modifier.size(ButtonColors.iconSizeDp.dp).alpha(alpha)
    )
}

/** Regulated zones child: activity_zone */
@Composable
fun RegulatedZoneIcon(alpha: Float) {
    Icon(
        imageVector = Activity_zone,
        contentDescription = null,
        tint = ButtonColors.icon,
        modifier = Modifier.size(ButtonColors.iconSizeDp.dp).alpha(alpha)
    )
}

/** 300m zone child: output_circle */
@Composable
fun DoubleCircleIcon(alpha: Float) {
    Icon(
        imageVector = Output_circle,
        contentDescription = null,
        tint = ButtonColors.icon,
        modifier = Modifier.size(ButtonColors.iconSizeDp.dp).alpha(alpha)
    )
}

/** Tracks child: conversion_path */
@Composable
fun TrackLayerIcon(alpha: Float) {
    Icon(
        imageVector = Conversion_path,
        contentDescription = null,
        tint = ButtonColors.icon,
        modifier = Modifier.size(ButtonColors.iconSizeDp.dp).alpha(alpha)
    )
}

/**
 * Icon: filled pin (Add Pin button). Drawn with [Canvas].
 * A filled drop-pin shape — distinct from the outlined toggle pin.
 */
@Composable
fun FilledPinIcon() {
    Canvas(modifier = Modifier.size(ICON_SIZE_DP.dp)) {
        val w = size.width; val h = size.height
        val cx = w / 2f; val cy = h * 0.38f; val r = w * 0.28f
        // Circle (pin head) — filled
        drawCircle(ButtonColors.icon, r, Offset(cx, cy))
        // Stem (triangle pointing down)
        val stemTop = cy + r * 0.7f
        val stemBot = h * 0.92f
        val stemHalfW = w * 0.12f
        val path = Path().apply {
            moveTo(cx - stemHalfW, stemTop)
            lineTo(cx + stemHalfW, stemTop)
            lineTo(cx, stemBot)
            close()
        }
        drawPath(path, ButtonColors.icon)
    }
}

/**
 * Icon: outlined pin (user markers layer toggle in FanLayout).
 * Distinct from [FilledPinIcon] — outline-only stroke.
 *
 * @param alpha Opacity (1.0 = active, 0.25 = inactive).
 */
@Composable
fun OutlinedPinIcon(alpha: Float) {
    Canvas(modifier = Modifier.size(ICON_SIZE_DP.dp)) {
        val w = size.width; val h = size.height
        val cx = w / 2f; val cy = h * 0.38f; val r = w * 0.28f
        val strokeW = w * 0.10f
        // Circle (pin head) — stroked
        drawCircle(ButtonColors.icon, r, Offset(cx, cy), alpha, style = Stroke(strokeW))
        // Stem (triangle pointing down) — stroked
        val stemTop = cy + r * 0.7f
        val stemBot = h * 0.92f
        val stemHalfW = w * 0.12f
        val path = Path().apply {
            moveTo(cx - stemHalfW, stemTop)
            lineTo(cx + stemHalfW, stemTop)
            lineTo(cx, stemBot)
            close()
        }
        drawPath(path, ButtonColors.icon, alpha, style = Stroke(strokeW, cap = StrokeCap.Round))
    }
}
