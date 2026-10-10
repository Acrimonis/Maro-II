package ykws.android.maro.ui.map

import ykws.android.maro.R
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.settings.AppSettings
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Identifies a control in the right-edge stack. Add new entries when adding controls. */
internal enum class ControlId { SETTINGS, LAYER_FAN, ROUTE_FAN, ZOOM, MENU }

/**
 * Side (dp) of one square in the map's top-left toggle-button row, from the palette's
 * `ui.map.toggle.square` (default 44 dp) — these buttons' own size, and the single home for it: the
 * row's chrome in `MapScreen.kt` reads it for its inset arithmetic too.
 */
internal val TOP_TOGGLE_SQUARE: Dp get() = AppConfig.uiMapToggleSquare.dp

/** Emoji glyph size (sp) drawn inside one of those squares — `ui.map.toggle.icon.size` (default 22 sp). */
internal val TOP_TOGGLE_ICON_SIZE: TextUnit get() = AppConfig.uiMapToggleIconSize.sp

/**
 * One square of the map's top-left toggle row, listed in the row's own left-to-right order — the
 * order's single home. [shown] is that square's own visibility, so the row composes straight from
 * [row] and the locked-screen mirror reads the same list to find where the lock square lands; neither
 * can drift from the other the way a hand-kept slot id did. Only [LAND_WATER] is ever conditional —
 * `showLandWaterIcon` takes it away — and everything after it shifts.
 *
 * The recenter square is deliberately absent: it is appended after [LOCK] and never shifts a slot.
 */
internal enum class TopToggleControl(val shown: (AppSettings) -> Boolean) {
    GPS({ true }),
    TRACKING({ true }),
    LAND_WATER({ it.showLandWaterIcon }),
    INSPECT({ true }),
    ROUTE({ true }),
    LOCK({ true });

    companion object {
        /**
         * The row's squares as drawn: the listed order with the hidden ones dropped, in one place so
         * the row and the lock mirror derive from the same source.
         */
        fun row(appSettings: AppSettings): List<TopToggleControl> =
            entries.filter { it.shown(appSettings) }
    }
}

/**
 * Start inset (dp) of the bottom band's banner family: the band's own gutter, plus the width the
 * bottom-left tag column takes — one `ui.map.toggle.square` and the `ui.map.overlay.gap` the info text
 * sits at — while [tagsDrawn]. Callers read it wherever they place a banner, so the pill and the two
 * cards clear that column the same way.
 *
 * The tag stack is a vertical `Column` one square wide (see `RegulatedZoneComponents.kt`), so its width
 * is a constant per state rather than a measurement. The right control column is deliberately not part
 * of it: [MapBanner] owns that half of the clearance, from the placement its `reservesControlColumn`
 * states (docs/ui-drawer-guidelines.md §1).
 */
internal fun bannerStartInset(tagsDrawn: Boolean): Dp =
    TOP_TOGGLE_GUTTER + (if (tagsDrawn) TOP_TOGGLE_SQUARE + AppConfig.uiMapOverlayGap.dp else 0.dp)

/** Corner of the banner family — the pill and the two cards share it. */
private val BANNER_CORNER = RoundedCornerShape(14.dp)

/** Border width (dp) of the banner family, stroked in the caller's own colour. */
private val BANNER_BORDER_WIDTH = 2.dp

/** Shadow (dp) under every banner. */
private val BANNER_SHADOW_ELEVATION = 8.dp

/**
 * A 44×44 dp icon square showing either water (🌊) or earth (🏔️), painted by [MapToggleSquare] on the
 * shared [MapSurface].
 *
 * A **reading** rather than a control: the side under the boat is the whole state — the nominal blue
 * over water, the hazard red over land — and the square wears **no data mark**, which is what tells a
 * reading from a control at a glance (plan §the two channels). No text caption either — icon only, and
 * no semantics: the square has no tap, so nothing here was ever exposed to accessibility.
 */
@Composable
internal fun EarthWaterIcon(
    isWater: Boolean,
    modifier: Modifier = Modifier
) {
    MapToggleSquare(
        face = earthWaterFace(isWater).toSurfaceFace(),
        modifier = modifier
    ) {
        Text(
            text = if (isWater) "🌊" else "🏔️",
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
 * The state table is this control's own; the resolution is not. `gpsFace` (`MapToggleFace.kt`) turns a
 * state into the square's two channels, and this control only paints them: both the tile and the data
 * mark are painted by [MapToggleSquare] from the resolved face.
 *
 * - [GpsIconState.DEMO]: GPS off — the shared pale face, glyph dimmed, no dot
 * - [GpsIconState.ACQUIRING]: no fix yet — the acquiring amber, **red** dot
 * - [GpsIconState.HEALTHY]: a fix held and moving — nominal blue, **green** dot
 * - [GpsIconState.IDLE]: a fix held while still — the same nominal blue, **green** dot (no idle face)
 * - [GpsIconState.STALE]: the fix lost — hazard red, **red** dot
 * - [GpsIconState.ESTIMATING]: dead reckoning — the acquiring amber, **red** dot (`status.gps.estimating`)
 * - [GpsIconState.WEAK]: a fix too weak to trust — the acquiring amber, **red** dot
 */
internal enum class GpsIconState { DEMO, ACQUIRING, HEALTHY, IDLE, STALE, ESTIMATING, WEAK }

@Composable
internal fun GpsStatusIcon(
    state: GpsIconState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val face = gpsFace(state)
    MapToggleSquare(face = face.toSurfaceFace(), onClick = onClick, modifier = modifier) {
        Text(text = "📡", fontSize = TOP_TOGGLE_ICON_SIZE)
    }
}

/**
 * Recenter button — appears in the top-left status row while the map is suppressed (auto-follow stopped
 * by a pan, a drawer or the wizard) and is simply absent otherwise: that absence is the one behaviour it
 * owns. Tapping immediately smooth-scrolls back to the GPS position.
 *
 * Shown, it is an ordinary family square wearing the app's accent blue — `ui.accent`, the palette's
 * `semantic.info` — at the shared active alpha, and **no data mark**: it is an action rather than a
 * mode. It reads the accent key because the colour is the app's accent rather than another control's
 * state, and it types no colour of its own (D4, map-surface normalization).
 */
@Composable
internal fun RecenterButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    MapToggleSquare(
        face = recenterFace().toSurfaceFace(),
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
 * face changes: unlocked is the shared pale face with that glyph dimmed and no dot, locked the nominal
 * blue with the complete-data green dot. The paint is [MapToggleSquare]'s; this control owns the two
 * resolved faces and the content description.
 */
@Composable
internal fun LockScreenButton(
    locked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val face = lockFace(locked)
    val cd = stringResource(if (locked) R.string.cd_unlock_screen else R.string.cd_lock_screen)
    MapToggleSquare(
        face = face.toSurfaceFace(),
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
 * The bottom band's one banner control — the three pills and the two cards' shared container.
 *
 * It owns the family's skin (a [BANNER_CORNER] corner, a [BANNER_BORDER_WIDTH] border in [borderColor]
 * over [AppConfig.uiCardBackground], a [BANNER_SHADOW_ELEVATION] shadow and
 * [AppConfig.buttonActionBgColor] as the fill) and the band's whole clearance — the full-width box that
 * centres the face in the band's free space, `start = bannerStartInset(tagsDrawn)` and the right control
 * column reserved while [reservesControlColumn]. The caller's own content arrives as [content]'s slot,
 * and its modifier carries only what the placement still needs — the band's own 6 dp, an alignment. A
 * face that wants the band's whole width states it in its own content, since the Surface wraps what it
 * is given.
 *
 * The rules this family follows are written in `docs/ui-component-guidelines.md` §5.7 and nowhere
 * else — this KDoc and the two call-site faces below point at it rather than restating it.
 *
 * @param borderColor the border's own colour, the one thing that distinguishes the faces: the
 *        recording red or [AppConfig.uiDashboardBackground] for the exit banner, the fixed
 *        [AppConfig.uiDashboardBackground] for the lock and status banners,
 *        [AppConfig.uiDashboardZoneDanger] for the error card.
 * @param tagsDrawn whether the bottom-left tag stack draws at least one tag — the band's one answer to
 *        "is that column there", read through `regulatedZoneTags`.
 * @param reservesControlColumn whether the banner's parent is full width, so nothing else keeps its face
 *        off the right control column. True for [LockBanner] and [MapStatusBanner], whose parent is the
 *        whole map area; false for the exit toast and the two cards, whose parent is the map's left
 *        overlay column and already ends where that column does.
 */
@Composable
internal fun MapBanner(
    borderColor: ComposeColor,
    tagsDrawn: Boolean,
    modifier: Modifier = Modifier,
    reservesControlColumn: Boolean = false,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = bannerStartInset(tagsDrawn),
                end = if (reservesControlColumn) RIGHT_CONTROL_COLUMN_INSET else 0.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = BANNER_CORNER,
            color = ComposeColor(AppConfig.buttonActionBgColor),
            shadowElevation = BANNER_SHADOW_ELEVATION,
            modifier = Modifier.border(BANNER_BORDER_WIDTH, borderColor, BANNER_CORNER)
        ) {
            Box(modifier = Modifier.background(ComposeColor(AppConfig.uiCardBackground))) {
                content()
            }
        }
    }
}

/**
 * The banner family's one line style — 16 sp Medium in [AppConfig.uiToastText], the family's whole text
 * definition. The pill's line and the action row's message both read it, so the family's text is written
 * once (`docs/ui-component-guidelines.md` §5.7).
 */
@Composable
internal fun bannerLineStyle(): TextStyle = TextStyle(
    color = ComposeColor(AppConfig.uiToastText),
    fontSize = 16.sp,
    fontWeight = FontWeight.Medium
)

/**
 * The banner face's one line — what the three pills hand [MapBanner]'s slot: [bannerLineStyle], centred,
 * on 16/10 padding, and with no `maxLines` and no ellipsis, so a long message wraps uncapped and the
 * bottom-anchored pill grows upward rather than cutting its instruction.
 */
@Composable
internal fun MapBannerText(text: String) {
    Text(
        text = text,
        style = bannerLineStyle(),
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
    )
}

/**
 * Lock/unlock feedback banner — the screen-lock face of [MapBanner]. Its parent is the whole map area,
 * so it is the control that reserves the right control column (`reservesControlColumn = true`), and its
 * pill centres in the band's free space: clear of the bottom-left tag column while one is drawn
 * ([tagsDrawn]), and of the control column always. The border is the fixed
 * [AppConfig.uiDashboardBackground]. Non-interactive.
 */
@Composable
internal fun LockBanner(
    locked: Boolean,
    tagsDrawn: Boolean,
    modifier: Modifier = Modifier
) {
    MapBanner(
        borderColor = ComposeColor(AppConfig.uiDashboardBackground),
        tagsDrawn = tagsDrawn,
        modifier = modifier.padding(bottom = 6.dp),
        reservesControlColumn = true
    ) {
        MapBannerText(
            text = stringResource(
                if (locked) R.string.toast_screen_locked else R.string.toast_screen_unlocked
            )
        )
    }
}

/**
 * Map status banner — the import and in-progress track-operation face of [MapBanner]. Same fixed
 * [AppConfig.uiDashboardBackground] border and the same full-size parent as [LockBanner], so it asks
 * the control for the same right-control-column reserve and centres in the band's free space beside it.
 * Non-interactive.
 */
@Composable
internal fun MapStatusBanner(
    message: String,
    tagsDrawn: Boolean,
    modifier: Modifier = Modifier
) {
    MapBanner(
        borderColor = ComposeColor(AppConfig.uiDashboardBackground),
        tagsDrawn = tagsDrawn,
        modifier = modifier.padding(bottom = 6.dp),
        reservesControlColumn = true
    ) {
        MapBannerText(text = message)
    }
}
