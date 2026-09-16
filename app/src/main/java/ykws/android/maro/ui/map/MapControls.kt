package ykws.android.maro.ui.map

import ykws.android.maro.R
import ykws.android.maro.config.AppConfig
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Identifies a control in the right-edge stack. Add new entries when adding controls. */
internal enum class ControlId { SETTINGS, LAYER_FAN, ZOOM, MENU }

/**
 * Side (dp) of one square in the map's top-left toggle-button row, from the palette's
 * `ui.map.toggle.square` (default 44 dp) — these buttons' own size, and the single home for it: the
 * row's chrome in `MapScreen.kt` reads it for its inset arithmetic too.
 */
internal val TOP_TOGGLE_SQUARE: Dp get() = AppConfig.uiMapToggleSquare.dp

/** Corner radius (dp) of one of those squares — `ui.map.toggle.corner.radius` (default 8 dp). */
internal val TOP_TOGGLE_CORNER_RADIUS: Dp get() = AppConfig.uiMapToggleCornerRadius.dp

/** Emoji glyph size (sp) drawn inside one of those squares — `ui.map.toggle.icon.size` (default 22 sp). */
internal val TOP_TOGGLE_ICON_SIZE: TextUnit get() = AppConfig.uiMapToggleIconSize.sp

/**
 * A 44×44 dp icon square representing either water (🌊) or earth (🏔️).
 *
 * No text caption — icon only. The background tint indicates whether this
 * side is currently active based on the map center position.
 */
@Composable
internal fun EarthWaterIcon(
    emoji: String,
    isActive: Boolean,
    activeColor: ComposeColor,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(TOP_TOGGLE_SQUARE)
            .clip(RoundedCornerShape(TOP_TOGGLE_CORNER_RADIUS))
            .background(
                if (isActive) activeColor.copy(alpha = AppConfig.uiMapToggleActiveBackgroundAlpha)
                else ComposeColor(AppConfig.uiMapToggleInactiveBackground)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = emoji,
            fontSize = TOP_TOGGLE_ICON_SIZE
        )
    }
}


/**
 * Hamburger menu icon: three horizontal lines (classic menu button).
 */
@Composable
internal fun HamburgerIcon() {
    Icon(
        imageVector = Icons.Filled.Menu,
        contentDescription = stringResource(R.string.cd_menu),
        tint = ButtonColors.icon,
        modifier = Modifier.size(36.dp)
    )
}

/**
 * 5-state GPS indicator icon — leftmost in the top-left status row.
 *
 * States match the derived [GpsIconState] enum:
 * - [DEMO]: GPS toggle off, gray satellite outline
 * - [ACQUIRING]: GPS on but no fix yet, amber background + pulsing dot
 * - [HEALTHY]: GPS fix good, green background
 * - [IDLE]: GPS fix but stationary (reduced cadence), cyan background
 * - [STALE]: GPS lost / hasLock false / error, red background
 */
internal enum class GpsIconState { DEMO, ACQUIRING, HEALTHY, IDLE, STALE, ESTIMATING, WEAK }

@Composable
internal fun GpsStatusIcon(
    state: GpsIconState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val baseColor: ComposeColor
    val bgAlpha: Float
    val contentAlpha: Float
    when (state) {
        GpsIconState.DEMO -> {
            // The row's one inactive fill, painted whole: `ui.map.toggle.inactive.background` already
            // carries its weight (`ui.map.surface.inactive`, white at 66 %), so the box adds no alpha of
            // its own and `contentAlpha` never reaches the fill — the glyph alone dims.
            baseColor = ComposeColor(AppConfig.uiMapToggleInactiveBackground)
            bgAlpha = 1f
            contentAlpha = AppConfig.uiMapToggleInactiveIconAlpha
        }
        GpsIconState.ACQUIRING -> { baseColor = ComposeColor(AppConfig.statusGpsAcquiring); bgAlpha = AppConfig.uiMapToggleActiveBackgroundAlpha; contentAlpha = 1f }
        GpsIconState.HEALTHY -> { baseColor = ComposeColor(AppConfig.statusGpsHealthy); bgAlpha = AppConfig.uiMapToggleActiveBackgroundAlpha; contentAlpha = 1f }
        GpsIconState.IDLE -> { baseColor = ComposeColor(AppConfig.statusGpsIdle); bgAlpha = AppConfig.uiMapToggleActiveBackgroundAlpha; contentAlpha = 1f }
        GpsIconState.STALE -> { baseColor = ComposeColor(AppConfig.statusGpsStale); bgAlpha = AppConfig.uiMapToggleActiveBackgroundAlpha; contentAlpha = 1f }
        GpsIconState.ESTIMATING -> { baseColor = ComposeColor(AppConfig.statusGpsEstimating); bgAlpha = AppConfig.uiMapToggleActiveBackgroundAlpha; contentAlpha = 1f }
        GpsIconState.WEAK -> { baseColor = ComposeColor(AppConfig.statusGpsAcquiring); bgAlpha = AppConfig.uiMapToggleActiveBackgroundAlpha; contentAlpha = 1f }
    }
    Box(
        modifier = modifier
            .size(TOP_TOGGLE_SQUARE)
            .clip(RoundedCornerShape(TOP_TOGGLE_CORNER_RADIUS))
            .background(baseColor.copy(alpha = bgAlpha))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "📡",
            fontSize = TOP_TOGGLE_ICON_SIZE,
            modifier = if (contentAlpha < 1f) Modifier.alpha(contentAlpha) else Modifier
        )
    }
}

/**
 * Recenter button — appears in the top-left status row when the map is frozen
 * (auto-follow suppressed by pan, drawer, or wizard). Tapping immediately
 * smooth-scrolls back to the GPS position.
 */
@Composable
internal fun RecenterButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(TOP_TOGGLE_SQUARE)
            .clip(RoundedCornerShape(TOP_TOGGLE_CORNER_RADIUS))
            .background(ComposeColor(0xFF2196F3).copy(alpha = 0.30f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "📍",
            fontSize = TOP_TOGGLE_ICON_SIZE
        )
    }
}

/**
 * Screen-lock toggle — leftmost in the top-left status row. 📱 (unlocked,
 * dimmed inactive) ↔ 🔒 (locked, caution/amber active). Matches the
 * GpsStatusIcon / TrackStatusIcon recipe: 44dp box, 8dp radius, 22sp emoji.
 */
@Composable
internal fun LockScreenButton(
    locked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Unlocked, the box is the row's inactive fill painted whole (see GpsIconState.DEMO), so the
    // glyph alone carries the dim.
    val baseColor = if (locked) ComposeColor(AppConfig.statusLockOn) else ComposeColor(AppConfig.uiMapToggleInactiveBackground)
    val bgAlpha = if (locked) AppConfig.uiMapToggleActiveBackgroundAlpha else 1f
    val contentAlpha = if (locked) 1f else AppConfig.uiMapToggleInactiveIconAlpha
    val cd = stringResource(if (locked) R.string.cd_unlock_screen else R.string.cd_lock_screen)
    Box(
        modifier = modifier
            .size(TOP_TOGGLE_SQUARE)
            .clip(RoundedCornerShape(TOP_TOGGLE_CORNER_RADIUS))
            .background(baseColor.copy(alpha = bgAlpha))
            .clickable(onClick = onClick)
            .semantics { contentDescription = cd }
            .alpha(contentAlpha),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "\uD83D\uDCF5",
            fontSize = TOP_TOGGLE_ICON_SIZE
        )
    }
}

/**
 * Full-screen transparent input blocker shown when the screen is locked.
 * Consumes every pointer event (tap, drag, pinch) so nothing below it —
 * osmdroid map, dashboard, drawers, controls — receives touch.
 */
@Composable
internal fun LockScrim(
    onInterceptedTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentOnTap by rememberUpdatedState(onInterceptedTap)
    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.any { it.pressed && !it.previousPressed }) {
                            currentOnTap()
                        }
                        event.changes.forEach { it.consume() }
                    }
                }
            }
    )
}

/**
 * Zoom +/− control pair — two 64dp buttons with a 6dp gap. Shared by the normal
 * right column and the locked-screen overlay so both stay identical.
 *
 * @param doubleTap When true, each button zooms only on a double-tap (single
 *                  splash taps are ignored); when false, normal single-tap.
 */
@Composable
internal fun ZoomControls(
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    modifier: Modifier = Modifier,
    doubleTap: Boolean = false
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ZoomButton(onClick = onZoomIn, doubleTap = doubleTap) { PlusIcon() }
        ZoomButton(onClick = onZoomOut, doubleTap = doubleTap) { MinusIcon() }
    }
}

@Composable
internal fun ZoomButton(
    onClick: () -> Unit,
    doubleTap: Boolean,
    icon: @Composable () -> Unit
) {
    if (doubleTap) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(ButtonColors.bg)
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { onClick() })
                },
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
    } else {
        MapControlButton(onClick = onClick) { icon() }
    }
}

/**
 * Lock/unlock feedback banner — reuses the generic exit-toast style (rounded
 * Surface, 2dp border, card background) at the bottom-left of the map, left of
 * the right-edge control column. Non-interactive.
 */
@Composable
internal fun LockBanner(
    locked: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 6.dp, end = RIGHT_CONTROL_COLUMN_INSET, bottom = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = ComposeColor(AppConfig.buttonActionBgColor),
            shadowElevation = 8.dp,
            modifier = Modifier.border(2.dp, ComposeColor(AppConfig.uiDashboardBackground), RoundedCornerShape(14.dp))
        ) {
            Box(modifier = Modifier.background(ComposeColor(AppConfig.uiCardBackground))) {
                Text(
                    text = stringResource(
                        if (locked) R.string.toast_screen_locked else R.string.toast_screen_unlocked
                    ),
                    color = ComposeColor(AppConfig.uiToastText),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }
    }
}

/**
 * Map status banner — generic transient toast-style banner reusing the
 * exit-toast / LockBanner style (14dp Surface, 2dp border, card background)
 * at the bottom of the map, centered in the space left of the right-edge
 * control column. Non-interactive. Used for import results and in-progress
 * track operations (export/import).
 */
@Composable
internal fun MapStatusBanner(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 6.dp, end = RIGHT_CONTROL_COLUMN_INSET, bottom = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = ComposeColor(AppConfig.buttonActionBgColor),
            shadowElevation = 8.dp,
            modifier = Modifier.border(2.dp, ComposeColor(AppConfig.uiDashboardBackground), RoundedCornerShape(14.dp))
        ) {
            Box(modifier = Modifier.background(ComposeColor(AppConfig.uiCardBackground))) {
                Text(
                    text = message,
                    color = ComposeColor(AppConfig.uiToastText),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }
    }
}
