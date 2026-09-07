package ykws.android.maro.ui.components

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
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
import androidx.compose.ui.graphics.Shape

// ─────────────────────────────────────────────────────────────────────────────
// DrawerHeader — canonical drawer header promoted from MarkerDrawer.kt
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Canonical drawer header used by [DrawerScaffold] and available standalone.
 *
 * Tokens per [docs/ui-drawer-guidelines.md §6]:
 * - Back button: 32dp IconButton, CircleShape, uiSettingsSwitchTrackInactive bg
 * - Back icon: ArrowBack 18dp, uiSettingsTextPrimary tint
 * - Title: 17sp Bold, uiSettingsTextPrimary, maxLines=1, ellipsis overflow
 * - Back→title spacer: 16dp
 */
@Composable
fun DrawerHeader(
    title: String,
    onClose: () -> Unit,
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
        // Plain clickable 32dp circle (not IconButton) so the Material3 minimum
        // interactive-size backing does not overflow the 48dp header row and clip
        // the button's top edge.
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(ComposeColor(AppConfig.uiSettingsSwitchTrackInactive))
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.cd_close),
                tint = ComposeColor(AppConfig.uiSettingsTextPrimary),
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = title,
            color = ComposeColor(AppConfig.uiSettingsTextPrimary),
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
 * Box(fillMaxSize, clip(shape), background(uiSettingsBackground), modifier)
 *   └─ Column(fillMaxSize)
 *        ├─ DrawerHeader(title, onClose, headerActions, hPad, vPad)  ← FIXED
 *        └─ Box(Modifier.weight(1f).fillMaxWidth())                   ← scroll host
 *             └─ if (scrollable) Column(verticalScroll, contentPadding) { content() }
 *                else Column(contentPadding) { content() }
 * ```
 *
 * @param title                    Header title text.
 * @param onClose                  Back-button / dismiss callback.
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
 * @param shape                    Clip shape for the root Box (default left-side drawer).
 * @param content                  Body content, in a [ColumnScope].
 */
@Composable
fun DrawerScaffold(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    headerActions: @Composable RowScope.() -> Unit = {},
    headerHorizontalPadding: Dp = 24.dp,
    headerVerticalPadding: Dp = AppConfig.uiPaddingHeaderVertical.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp),
    scrollable: Boolean = true,
    suppressOverscrollWhenFits: Boolean = false,
    bottomAnchoredContent: Boolean = false,
    wrapContent: Boolean = false,
    statusBarsInset: Boolean = false,
    shape: Shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
    footer: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    val bgModifier = Modifier
        .fillMaxSize()
        .background(ComposeColor(AppConfig.uiSettingsBackground), shape)

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
            // height formula, no MeasureHeight probe. The root Box stays fillMaxSize() as the
            // bounded parent / screen (see above); the inner Column wraps at natural height and
            // carries the background + shape clip so the visible panel collapses to content.
            // Header + footer stay fixed at natural height. The body wraps at natural height but
            // is scrollable ONLY if it exceeds the available screen height (heightIn(max) +
            // verticalScroll), so very tall content scrolls instead of clipping.
            // bottomAnchoredContent is meaningless here (no weight(1f) host) and is ignored.
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                var headerHeight by remember { mutableStateOf(0.dp) }
                var footerHeight by remember { mutableStateOf(0.dp) }
                val availableBodyHeight =
                    (maxHeight - headerHeight - footerHeight).coerceAtLeast(0.dp)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .align(Alignment.BottomCenter)
                        .background(ComposeColor(AppConfig.uiSettingsBackground), shape)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onSizeChanged { headerHeight = with(density) { it.height.toDp() } }
                    ) {
                        DrawerHeader(
                            title = title,
                            onClose = onClose,
                            actions = headerActions,
                            horizontalPadding = headerHorizontalPadding,
                            verticalPadding = headerVerticalPadding
                        )
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
                                .heightIn(max = availableBodyHeight)
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
                                .heightIn(max = availableBodyHeight)
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
            Column(modifier = Modifier.fillMaxSize()) {
                DrawerHeader(
                    title = title,
                    onClose = onClose,
                    actions = headerActions,
                    horizontalPadding = headerHorizontalPadding,
                    verticalPadding = headerVerticalPadding
                )
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
