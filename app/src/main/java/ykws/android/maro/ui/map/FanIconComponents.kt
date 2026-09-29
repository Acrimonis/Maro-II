
package ykws.android.maro.ui.map
import ykws.android.maro.R
import ykws.android.maro.config.AppConfig
import ykws.android.maro.ui.icons.Activity_zone
import ykws.android.maro.ui.icons.Conversion_path
import ykws.android.maro.ui.icons.LocationOn
import ykws.android.maro.ui.icons.Output_circle
import ykws.android.maro.ui.icons.Stacks
import ykws.android.maro.ui.icons.bolt
import ykws.android.maro.ui.icons.cancel
import ykws.android.maro.ui.icons.logout
import ykws.android.maro.ui.icons.route
import ykws.android.maro.ui.icons.save

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AreaChart
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
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

// ── The route fan's own faces (R75–R80) ───────────────────────────────────────

/**
 * The route fan's **parent**: the mode's own face on the anchor that opens the arc (R76).
 *
 * It states the phase where the square it replaced used to arm it, carrying R51's three faces — the
 * `route` mark worn in all of them at the stack's own icon colour, the fan carrying no hue at all
 * (2026-09-29): the faces are told apart by the shared pulsing dot alone (R69), drawn while the mode is
 * on-phase. The parent opens the fan and arms nothing, so this is a reading of the mode and never a
 * state of its own.
 */
@Composable
fun RouteFanParentIcon(
    armed: Boolean,
    following: Boolean,
    searching: Boolean
) {
    Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = route,
            contentDescription = stringResource(R.string.cd_route_fan),
            tint = ButtonColors.icon,
            modifier = Modifier.size(ButtonColors.iconSizeDp.dp)
        )
        if (armed && (following || searching)) {
            MapPulseDot(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(AppConfig.uiMapSurfacePadding.dp)
            )
        }
    }
}

/**
 * One child of the route fan: its mark at full tint, or the **disabled face** — the inactive alpha, with
 * its press already suppressed by the framework ([FanLayout]'s `enabledStates`, R78).
 *
 * No child of this fan toggles, so a quarter-strength glyph cannot be read as "off" here: the dimming
 * *is* the phase saying the action has nothing to act on.
 */
@Composable
fun RouteFanChildIcon(
    imageVector: ImageVector,
    enabled: Boolean,
    @StringRes contentDescription: Int,
    tint: ComposeColor = ButtonColors.icon
) {
    Icon(
        imageVector = imageVector,
        contentDescription = stringResource(contentDescription),
        tint = tint,
        modifier = Modifier
            .size(ButtonColors.iconSizeDp.dp)
            .alpha(if (enabled) 1f else ButtonColors.inactiveAlpha)
    )
}

/** Child 1 — **Route (auto)**: the arming that takes the settled line the instant it exists (R80). */
@Composable
fun RouteFanAutoIcon(enabled: Boolean) = RouteFanChildIcon(
    imageVector = bolt,
    enabled = enabled,
    contentDescription = R.string.cd_route_fan_auto
)

/** Child 2 — **Route**: the plain arming door, offered in Idle alone (R77). */
@Composable
fun RouteFanArmIcon(enabled: Boolean) = RouteFanChildIcon(
    imageVector = route,
    enabled = enabled,
    contentDescription = R.string.route_action_arm
)

/** Child 3 — **Save to track**: whatever a line stands unwritten (R77). */
@Composable
fun RouteFanSaveIcon(enabled: Boolean) = RouteFanChildIcon(
    imageVector = save,
    enabled = enabled,
    contentDescription = R.string.route_action_save_track
)

/** Child 4 — **Save to track and exit**: the following phase's own save-and-leave (R77). */
@Composable
fun RouteFanSaveExitIcon(enabled: Boolean) = RouteFanChildIcon(
    imageVector = logout,
    enabled = enabled,
    contentDescription = R.string.route_exit_save
)

/**
 * Child 5 — **Discard**, the one door that asks nothing (R79): the stack's own icon colour, like every
 * other glyph in the arc — the loss is told apart by its mark and its position, not by a hue.
 */
@Composable
fun RouteFanDiscardIcon(enabled: Boolean) = RouteFanChildIcon(
    imageVector = cancel,
    enabled = enabled,
    contentDescription = R.string.route_exit_discard
)

/** User markers layer toggle: LocationOn (outlined map pin). */
@Composable
fun LocationOnIcon(alpha: Float) {
    Icon(
        imageVector = LocationOn,
        contentDescription = null,
        tint = ButtonColors.icon,
        modifier = Modifier.size(ButtonColors.iconSizeDp.dp).alpha(alpha)
    )
}

