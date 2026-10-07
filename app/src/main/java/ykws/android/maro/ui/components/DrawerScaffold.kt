package ykws.android.maro.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ykws.android.maro.R
import ykws.android.maro.config.AppConfig

// ─────────────────────────────────────────────────────────────────────────────
// DrawerHeader — canonical drawer header promoted from MarkerDrawer.kt
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Canonical drawer header used by [DrawerScaffold] and available standalone.
 *
 * Tokens per [docs/ui-drawer-guidelines.md §6]:
 * - Back button: 32dp IconButton, CircleShape, uiSwitchTrackInactive bg
 * - Back icon: ArrowBack 18dp, uiTextPrimary tint
 * - Title: 17sp Bold, uiTextPrimary, maxLines=1, ellipsis overflow
 * - Back→title spacer: 16dp
 */
@Composable
fun DrawerHeader(
    title: String,
    onClose: () -> Unit,
    showBack: Boolean = true,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    horizontalPadding: Dp = 24.dp,
    verticalPadding: Dp = AppConfig.uiPaddingHeaderVertical.dp,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showBack) {
            // Plain clickable 32dp circle (not IconButton) so the Material3 minimum
            // interactive-size backing does not overflow the 48dp header row and clip
            // the button's top edge.
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(ComposeColor(AppConfig.uiSwitchTrackInactive))
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_close),
                    tint = ComposeColor(AppConfig.uiTextPrimary),
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(16.dp))
        }
        Text(
            text = title,
            color = ComposeColor(AppConfig.uiTextPrimary),
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        actions()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DrawerScaffold — fixed-header + scrollable-body drawer shell
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Reusable drawer scaffold: fixed [DrawerHeader] at top, scrollable (or static)
 * body below.
 *
 * Structure:
 * ```
 * Box(fillMaxSize, clip(shape), background(uiBackground), modifier)
 *   └─ Column(fillMaxSize)
 *        ├─ DrawerHeader(title, onClose, headerActions, hPad, vPad)  ← FIXED
 *        └─ Box(Modifier.weight(1f).fillMaxWidth())                   ← scroll host
 *             └─ if (scrollable) Column(verticalScroll, contentPadding) { content() }
 *                else Column(contentPadding) { content() }
 * ```
 *
 * @param title                    Header title text (used only when no [header] slot is supplied).
 * @param onClose                  Back-button / dismiss callback. Required only while a header is
 *                                 drawn: a header-less panel omits it, and omits [title] with it.
 * @param modifier                 Outer Modifier applied to the root Box.
 * @param headerActions            Composable slot in the header Row (right side).
 * @param headerHorizontalPadding  Horizontal padding for the header Row (default 24dp).
 * @param headerVerticalPadding    Vertical padding for the header Row (default 6dp).
 * @param contentPadding           Padding applied around the scrollable content body.
 * @param scrollable               Whether the body scrolls (true) or is static (false).
 * @param suppressOverscrollWhenFits If true, disables the overscroll effect while the
 *                                 body content fits the viewport (no scroll range).
 * @param statusBarsInset          If true, applies .windowInsetsPadding(statusBars)
 *                                 after the background (for full-screen drawer panels).
 * @param header                   Optional header slot, defaulting to today's [DrawerHeader] when
 *                                 the caller supplies [onClose]. Null with no [onClose] draws no
 *                                 header at all — the title-less dashboard's mode.
 * @param onMeasuredHeight         Optional report of the panel's own measured height (header +
 *                                 body + footer, floored at [wrapContentMinHeight] in wrap mode),
 *                                 read by the map's band in portrait. Both branches report it.
 * @param backgroundColor          Background colour of the visible panel (default the shared
 *                                 [AppConfig.uiBackground] token). A dashboard passes its own
 *                                 `ui.dashboard.background` so the frame change keeps it (F4).
 * @param wrapContentMaxHeight     Optional ceiling for the visible wrap-content panel: a taller
 *                                 panel's body scrolls (when [scrollable]) instead of covering the
 *                                 map strip below it (F5). Null keeps the full-screen ceiling, which
 *                                 is every caller that does not pass a cap.
 * @param shape                    Clip shape for the root Box (default left-side drawer).
 * @param content                  Body content, in a [ColumnScope].
 */
@Composable
fun DrawerScaffold(
    title: String = "",
    onClose: (() -> Unit)? = null,
    showBack: Boolean = true,
    modifier: Modifier = Modifier,
    headerActions: @Composable RowScope.() -> Unit = {},
    headerHorizontalPadding: Dp = 24.dp,
    headerVerticalPadding: Dp = AppConfig.uiPaddingHeaderVertical.dp,
    header: (@Composable ColumnScope.() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp),
    scrollable: Boolean = true,
    suppressOverscrollWhenFits: Boolean = false,
    bottomAnchoredContent: Boolean = false,
    wrapContent: Boolean = false,
    /**
     * Fade this panel's **body** in on its first composition (default false). The map's selected-item
     * cards opt in, so a cross-type swap paints the incoming content in rather than cutting to it — over
     * the panel's own opaque background, never over the map (2026-10-07).
     */
    fadeInOnEnter: Boolean = false,
    wrapContentMinHeight: Dp = 0.dp,
    /**
     * The height the wrap frame occupies *before* its parts have reported (default: the floor). A host
     * that swaps one panel for another in the same slot passes the outgoing panel's measured height, so
     * the incoming card is laid out at the size the screen already shows and settles once — instead of
     * snapping through the floor in the one frame where a wrap frame knows no header, no body and no
     * footer (2026-10-07). A host that never swaps (a drawer) leaves it null.
     */
    initialHeight: Dp? = null,
    statusBarsInset: Boolean = false,
    onMeasuredHeight: ((Dp) -> Unit)? = null,
    backgroundColor: ComposeColor = ComposeColor(AppConfig.uiBackground),
    wrapContentMaxHeight: Dp? = null,
    shape: Shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
    footer: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    // The optional header slot: the caller's own, else today's [DrawerHeader] when a close callback
    // is supplied, else nothing at all — which is how the title-less dashboard wears this frame.
    val hc: (@Composable ColumnScope.() -> Unit)? = when {
        header != null -> header
        onClose != null -> {
            {
                DrawerHeader(
                    title = title,
                    onClose = onClose,
                    showBack = showBack,
                    actions = headerActions,
                    horizontalPadding = headerHorizontalPadding,
                    verticalPadding = headerVerticalPadding
                )
            }
        }
        else -> null
    }

    // The non-wrap panel keeps the full screen, exactly as before (W1): the ceiling is the wrap
    // branch's alone now (R5), so a landscape caller can never be silently shrunk by a portrait cap.
    val bgModifier = Modifier
        .fillMaxSize()
        .background(backgroundColor, shape)

    // The dissolve (2026-10-07), opted into by the map's selected-item cards. It is the **body** that
    // fades, never the frame: the panel keeps its background and its header opaque, so the incoming
    // content paints itself in over the dashboard's own colour instead of letting the map show through
    // the panel. A card is a different composable from its neighbour, so a cross-type swap replaces this
    // whole call site and the fade runs again from zero — the incoming half of the dissolve, the half
    // that reads as smoothness while a drag keeps changing the nearest. (The non-wrap landscape frame has
    // no body of its own to fade, so a landscape card changes exactly as it did before.)
    val fade = remember { Animatable(if (fadeInOnEnter) 0f else 1f) }
    LaunchedEffect(Unit) { if (fadeInOnEnter) fade.animateTo(1f, tween(120)) }
    val bodyFade = if (fadeInOnEnter) Modifier.graphicsLayer { alpha = fade.value } else Modifier

    Box(
        modifier = modifier
            // Wrap mode: the root Box stays fillMaxSize() ONLY as the invisible bounded
            // measurement parent (it provides the real screen height for the body's scroll
            // ceiling). The background/shape clip is NOT drawn here — it lives on the
            // wrap-content Column below so the visible panel collapses to content height.
            // Non-wrap mode keeps the full-screen background on the root (unchanged).
            .then(if (wrapContent) Modifier.fillMaxSize() else bgModifier)
            .then(if (statusBarsInset) Modifier.windowInsetsPadding(WindowInsets.statusBars) else Modifier)
    ) {
        if (wrapContent) {
            // Wrap-content mode: the panel sizes to its content's natural height — no fixed
            // height formula. The root Box stays fillMaxSize() as the bounded parent / screen (see
            // above); the inner Column wraps at natural height and
            // carries the background + shape clip so the visible panel collapses to content.
            // Header + footer stay fixed at natural height. The body wraps at natural height but
            // is scrollable ONLY if it exceeds the available screen height (heightIn(max) +
            // verticalScroll), so very tall content scrolls instead of clipping.
            // bottomAnchoredContent **is** honoured here: with it the panel keeps the
            // wrapContentMinHeight floor and its body and footer sit at the panel's bottom, the slack
            // opening between the header and the body. Without it the slack opens below the footer,
            // which floats a footer high on the panel.
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                var headerHeight by remember { mutableStateOf(0.dp) }
                var bodyHeight by remember { mutableStateOf(0.dp) }
                var footerHeight by remember { mutableStateOf(0.dp) }
                // The panel's own ceiling (F5): the caller's cap, itself bounded by the screen and
                // floored at the base, so a taller dashboard's body scrolls rather than covering
                // the map strip below it. No cap keeps the full screen, exactly as before.
                val frameCeiling = minOf(maxHeight, wrapContentMaxHeight ?: maxHeight)
                    .coerceAtLeast(wrapContentMinHeight)
                // The parts report their own heights on their own first layout, and a content swap
                // replaces this very call site (two selected-item cards are different composables), so
                // the remembers above restart at zero and "not yet measured" would read as "zero tall"
                // for a frame: the body would be free to measure against the entire frame — the panel
                // ballooning to almost the middle of the screen before collapsing. Until a part reports,
                // the panel is therefore pinned at [preSize] and the body gets only what that height
                // leaves under the header, so a swap paints the size the outgoing card already had and
                // settles once — rather than jumping through the floor on its way to its own height.
                val partsMeasured = headerHeight > 0.dp || bodyHeight > 0.dp || footerHeight > 0.dp
                val preSize = (initialHeight ?: wrapContentMinHeight)
                    .coerceIn(wrapContentMinHeight, frameCeiling)
                val availableBodyHeight = if (partsMeasured) {
                    (frameCeiling - headerHeight - footerHeight).coerceAtLeast(0.dp)
                } else {
                    (preSize - headerHeight).coerceAtLeast(0.dp)
                }
                // Whatever the card falls short of the floor by, and only that — nothing until a part
                // has reported, or the slack would itself open at the floor.
                val bottomSlack = if (partsMeasured) {
                    (wrapContentMinHeight - headerHeight - bodyHeight - footerHeight)
                        .coerceAtLeast(0.dp)
                } else {
                    0.dp
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Until the parts report, the frame is exactly [preSize]; from then on it is the
                        // floor-to-ceiling range the measured layout has always used.
                        .heightIn(
                            min = if (partsMeasured) wrapContentMinHeight else preSize,
                            max = if (partsMeasured) frameCeiling else preSize
                        )
                        .onSizeChanged {
                            onMeasuredHeight?.invoke(with(density) { it.height.toDp() })
                        }
                        .align(Alignment.BottomCenter)
                        .background(backgroundColor, shape)
                ) {
                    if (hc != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onSizeChanged { headerHeight = with(density) { it.height.toDp() } }
                        ) {
                            hc()
                        }
                    }
                    if (bottomAnchoredContent) {
                        // Measured: the fixed slack that fills the floor. Unmeasured: that slack is not
                        // known yet, so the spacer just absorbs whatever the pre-sized frame leaves. This
                        // is what holds the body and the footer on the frame's bottom edge in the frame
                        // between a swap and the parts' report — the very place the measured layout puts
                        // them a frame later, and what the frame before this fix got wrong, floating the
                        // walk row up by the whole slack and dropping it again.
                        if (partsMeasured) {
                            if (bottomSlack > 0.dp) Spacer(Modifier.height(bottomSlack))
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                    if (scrollable) {
                        val scrollState = rememberScrollState()
                        val canScroll by remember(scrollState) {
                            derivedStateOf { scrollState.maxValue > 0 }
                        }
                        val suppressOverscroll = suppressOverscrollWhenFits && !canScroll
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onSizeChanged { bodyHeight = with(density) { it.height.toDp() } }
                                .heightIn(max = availableBodyHeight)
                                .then(bodyFade)
                                .then(
                                    if (suppressOverscroll) {
                                        Modifier.verticalScroll(state = scrollState, overscrollEffect = null)
                                    } else {
                                        Modifier.verticalScroll(state = scrollState)
                                    }
                                )
                                .padding(contentPadding),
                            content = content
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onSizeChanged { bodyHeight = with(density) { it.height.toDp() } }
                                .heightIn(max = availableBodyHeight)
                                .then(bodyFade)
                                .padding(contentPadding),
                            content = content
                        )
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onSizeChanged { footerHeight = with(density) { it.height.toDp() } }
                    ) {
                        footer()
                    }
                }
            }
        } else {
            val density = LocalDensity.current
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // G3: the non-wrap panel reports its own measured height too, so the map band
                    // reads the frame's measurement whichever branch the open panel runs.
                    .onSizeChanged {
                        onMeasuredHeight?.invoke(with(density) { it.height.toDp() })
                    }
            ) {
                if (hc != null) {
                    Column(modifier = Modifier.fillMaxWidth()) { hc() }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (scrollable) {
                        val scrollState = rememberScrollState()
                        val canScroll by remember(scrollState) {
                            derivedStateOf { scrollState.maxValue > 0 }
                        }
                        val suppressOverscroll = suppressOverscrollWhenFits && !canScroll
                        Box(Modifier.fillMaxSize()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(if (bottomAnchoredContent) Alignment.BottomCenter else Alignment.TopCenter)
                                    .then(
                                        if (suppressOverscroll) {
                                            Modifier.verticalScroll(state = scrollState, overscrollEffect = null)
                                        } else {
                                            Modifier.verticalScroll(state = scrollState)
                                        }
                                    )
                                    .padding(contentPadding),
                                content = content
                            )
                        }
                    } else {
                        Box(Modifier.fillMaxSize()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(if (bottomAnchoredContent) Alignment.BottomCenter else Alignment.TopCenter)
                                    .padding(contentPadding),
                                content = content
                            )
                        }
                    }
                }
                footer()
            }
        }
    }
}
