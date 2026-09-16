package ykws.android.maro.ui.map

import ykws.android.maro.R
import ykws.android.maro.config.AppConfig
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
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

/** Emoji glyph size (sp) drawn inside one of those squares — `ui.map.toggle.icon.size` (default 22 sp). */
internal val TOP_TOGGLE_ICON_SIZE: TextUnit get() = AppConfig.uiMapToggleIconSize.sp

/**
 * A 44×44 dp icon square showing either water (🌊) or earth (🏔️), painted by [MapToggleSquare] on the
 * shared [MapSurface].
 *
 * No text caption — icon only, and no semantics either: the square has no tap, so nothing here was ever
 * exposed to accessibility and nothing is lost by not naming it. [color] is the side that is under the
 * boat, so the square always paints an active face and there is no inactive wing left to reach: one
 * resolved face is the whole state (D5, map-surface normalization).
 */
@Composable
internal fun EarthWaterIcon(
    emoji: String,
    color: ComposeColor,
    modifier: Modifier = Modifier
) {
    MapToggleSquare(
        face = mapSurfaceFaceActive(color),
        modifier = modifier
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
 * 7-state GPS indicator icon — leftmost in the top-left status row.
 *
 * The state table below is this control's own; the painting is not. Every branch resolves one
 * [MapSurfaceFace] and hands it to [MapToggleSquare], which is the row's single square.
 *
 * - [GpsIconState.DEMO]: GPS toggle off, satellite outline — the inactive face, glyph dimmed
 * - [GpsIconState.ACQUIRING]: GPS on but no fix yet, amber face
 * - [GpsIconState.HEALTHY]: GPS fix good, green face
 * - [GpsIconState.IDLE]: GPS fix but stationary (reduced cadence), blue face
 * - [GpsIconState.STALE]: GPS lost / hasLock false / error, red face
 * - [GpsIconState.ESTIMATING]: dead-reckoning fix, its own amber — `AppConfig.statusGpsEstimating`, a code
 *   default the palette has no key for (deliberate: `colors.properties` holds the other five states)
 * - [GpsIconState.WEAK]: fix too weak to trust, the acquiring amber
 */
internal enum class GpsIconState { DEMO, ACQUIRING, HEALTHY, IDLE, STALE, ESTIMATING, WEAK }

@Composable
internal fun GpsStatusIcon(
    state: GpsIconState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val face = when (state) {
        // The inactive face paints the shared fill whole and dims the glyph alone — the fill's own
        // weight is in the token, so no box alpha is added on top of it.
        GpsIconState.DEMO -> mapSurfaceFaceInactive()
        GpsIconState.ACQUIRING, GpsIconState.WEAK -> mapSurfaceFaceActive(ComposeColor(AppConfig.statusGpsAcquiring))
        GpsIconState.HEALTHY -> mapSurfaceFaceActive(ComposeColor(AppConfig.statusGpsHealthy))
        GpsIconState.IDLE -> mapSurfaceFaceActive(ComposeColor(AppConfig.statusGpsIdle))
        GpsIconState.STALE -> mapSurfaceFaceActive(ComposeColor(AppConfig.statusGpsStale))
        GpsIconState.ESTIMATING -> mapSurfaceFaceActive(ComposeColor(AppConfig.statusGpsEstimating))
    }
    MapToggleSquare(face = face, onClick = onClick, modifier = modifier) {
        Text(text = "📡", fontSize = TOP_TOGGLE_ICON_SIZE)
    }
}

/**
 * Recenter button — appears in the top-left status row while the map is suppressed (auto-follow stopped
 * by a pan, a drawer or the wizard) and is simply absent otherwise: that absence is the one behaviour it
 * owns. Tapping immediately smooth-scrolls back to the GPS position.
 *
 * Shown, it is an ordinary family square wearing the app's accent blue — `ui.accent`, the palette's
 * `semantic.info` — at the shared active alpha. It reads the accent key because the colour is the app's
 * accent rather than another control's state, and it types no colour of its own (D4, map-surface
 * normalization).
 */
@Composable
internal fun RecenterButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    MapToggleSquare(
        face = mapSurfaceFaceActive(ComposeColor(AppConfig.uiAccent)),
        onClick = onClick,
        modifier = modifier
    ) {
        Text(
            text = "📍",
            fontSize = TOP_TOGGLE_ICON_SIZE
        )
    }
}

/**
 * Screen-lock toggle — one slot in the top-left status row. The glyph is 📵 in both states and only the
 * face changes: unlocked is the inactive face with that glyph dimmed, locked paints `status.lock.on` blue
 * at the shared active alpha, as the design has it. The square's paint is [MapToggleSquare]'s; this
 * control owns the two faces and the content description.
 */
@Composable
internal fun LockScreenButton(
    locked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val face = if (locked) {
        mapSurfaceFaceActive(ComposeColor(AppConfig.statusLockOn))
    } else {
        mapSurfaceFaceInactive()
    }
    val cd = stringResource(if (locked) R.string.cd_unlock_screen else R.string.cd_lock_screen)
    MapToggleSquare(
        face = face,
        onClick = onClick,
        modifier = modifier,
        contentDescription = cd
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
