package ykws.android.maro.ui.map

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ykws.android.maro.R
import ykws.android.maro.config.AppConfig
import ykws.android.maro.ui.components.ConfirmAction
import ykws.android.maro.ui.components.ConfirmActionRole
import ykws.android.maro.ui.components.ConfirmRequest
import ykws.android.maro.ui.components.ListSelectionCheck
import ykws.android.maro.ui.components.LocalConfirmDialogHost

/** The tile's own margin inside its cell — the band the two-tone selection ring is drawn into. */
private val CELL_MARGIN = 6.dp

/** Room between the tile's corner and the selection disc: half the disc, clamped on a small tile. */
private val CHECK_INSET = 12.dp
private val CHECK_INSET_SMALL = 8.dp
private val SMALL_CELL = 64.dp

/** The selection ring's fixed weight, and the trough its brightness falls to on each beat. */
private val RING_WIDTH = 4.dp
private const val RING_ALPHA_MIN = 0.25f
private const val RING_PULSE_MS = 900

private const val PORTRAIT_COLUMNS = 4
private const val LANDSCAPE_COLUMNS = 5

/**
 * This picker's share of the panel, below the grid: the title block and the divider in portrait, the dense
 * chrome in landscape, the action button, the panel's own padding, the navigation bar and a breathing line.
 * It is the picker's budget, not a restatement of `ConfirmDialog`'s cap.
 */
private const val PORTRAIT_CHROME_DP = 190
private const val LANDSCAPE_CHROME_DP = 150

/**
 * The one colour picker — raised, never painted in place.
 *
 * It draws nothing itself: a drawer-hosted surface (the settings rows, the marker wizard) calls this
 * and the screen's ladder host renders the panel above every drawer, so the picker is never clipped by
 * the surface that opened it. The panel is the app's own [`ConfirmDialog`] with the swatch grid in its
 * `options` slot and Cancel as its single action; in landscape it turns `dense`, dropping the title
 * block so five rows still clear the panel's height cap.
 *
 * A dismissal from any door runs [onDismiss], so the caller clears its own `showPicker` flag; a caller
 * that hides the picker by other means is not expected — the request it raised is the host's.
 *
 * @param visible      Raise the panel while true.
 * @param currentColorIndex Palette index drawn as selected, null for none (a stored colour outside the
 *                     palette matches no swatch).
 * @param onColorSelected The picked palette index.
 */
@Composable
fun ShowMarkerColorPicker(
    visible: Boolean,
    currentColorIndex: Int?,
    onColorSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val host = LocalConfirmDialogHost.current ?: return
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    // The panel names the task, never the colour's owner: the row that raised it already says which colour this
    // is, and one generic title serves the settings rows and the wizard alike (2026-10-23).
    val title = stringResource(R.string.color_picker_title)
    val cancel = stringResource(R.string.action_cancel)

    LaunchedEffect(visible, isLandscape, currentColorIndex) {
        if (visible) {
            host.show(
                ConfirmRequest(
                    title = title,
                    options = {
                        MarkerColorGrid(
                            currentColorIndex = currentColorIndex,
                            onPick = { index ->
                                onColorSelected(index)
                                // The pick closes the panel from inside: `dismiss()` runs the request's
                                // own onDismiss, so the caller's `showPicker` flag clears with it.
                                host.dismiss()
                            }
                        )
                    },
                    actions = listOf(
                        ConfirmAction(
                            label = cancel,
                            role = ConfirmActionRole.SECONDARY,
                            onClick = { host.dismiss() }
                        )
                    ),
                    dense = isLandscape,
                    onDismiss = onDismiss
                )
            )
        }
    }
}

/**
 * The 20 swatches as one selectable group: 4 columns by 5 rows in portrait, 5 by 4 in landscape, so
 * neither orientation scrolls and no swatch is orphaned.
 *
 * The cells are **square and sized by whichever budget is tighter** — the panel's own width, or the screen
 * height once this picker's chrome is paid for. Portrait is width-bound, so the grid fills the panel edge to
 * edge; landscape is height-bound, because four rows cannot both fill a portrait-width panel and clear its
 * cap, so the grid centres with a narrow gutter instead. Cells tile with no gutter of their own: the spacing
 * the eye sees is [CELL_MARGIN] on every side of every tile, equal in both axes, and the selection ring lives
 * in that same margin so marking a colour never resizes it.
 */
@Composable
internal fun ColumnScope.MarkerColorGrid(
    currentColorIndex: Int?,
    onPick: (Int) -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val columns = if (isLandscape) LANDSCAPE_COLUMNS else PORTRAIT_COLUMNS
    val colors = MarkerColors.all
    val rows = (colors.size + columns - 1) / columns
    val highlight = ComposeColor(AppConfig.uiHighlight)
    val divider = ComposeColor(AppConfig.uiDividerColor)
    // The picked swatch's ring keeps one weight and pulses its **brightness**, from solid down to a quarter:
    // a width beat was too quiet to read on a 5 dp band, while a full-to-quarter fade is unmistakable and
    // still leaves the ring visible at its trough (2026-10-23).
    val pulse = rememberInfiniteTransition(label = "markerColorPulse")
    val ringAlpha by pulse.animateFloat(
        initialValue = 1f,
        targetValue = RING_ALPHA_MIN,
        animationSpec = infiniteRepeatable(
            animation = tween(RING_PULSE_MS, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "markerColorRingAlpha"
    )

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val heightBudget = (
            configuration.screenHeightDp -
                (if (isLandscape) LANDSCAPE_CHROME_DP else PORTRAIT_CHROME_DP)
            ).coerceAtLeast(160)
        val cell = minOf(maxWidth / columns, (heightBudget / rows).dp)

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier
                .fillMaxWidth()
                .height(cell * rows)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(0.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            itemsIndexed(colors) { index, color ->
                val selected = index == currentColorIndex
                val name = stringResource(MarkerColors.nameResOf(index))
                Box(
                    modifier = Modifier
                        .size(cell)
                        .selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = { onPick(index) }
                        )
                        .semantics { contentDescription = name }
                        // Two-tone ring: pure white covers the cell's outer 0–2.5 dp and the accent the
                        // 2.5–4.5 dp inside it, so one of the two always contrasts the panel and the swatch
                        // (2026-10-23). The tile starts at 6 dp, leaving a 1.5 dp gap. Only the accent
                        // pulses, so the white edge never fades.
                        .drawWithContent {
                            drawContent()
                            if (selected) {
                                val whiteInset = 1.25.dp.toPx()
                                drawRoundRect(
                                    color = ComposeColor.White,
                                    topLeft = Offset(whiteInset, whiteInset),
                                    size = Size(size.width - 2 * whiteInset, size.height - 2 * whiteInset),
                                    cornerRadius = CornerRadius(10.dp.toPx()),
                                    style = Stroke(width = 2.5.dp.toPx())
                                )
                                val accentInset = 3.5.dp.toPx()
                                drawRoundRect(
                                    color = highlight.copy(alpha = ringAlpha),
                                    topLeft = Offset(accentInset, accentInset),
                                    size = Size(size.width - 2 * accentInset, size.height - 2 * accentInset),
                                    cornerRadius = CornerRadius(9.dp.toPx()),
                                    style = Stroke(width = RING_WIDTH.toPx())
                                )
                            }
                        },
                contentAlignment = Alignment.Center
            ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(CELL_MARGIN)
                            .clip(RoundedCornerShape(8.dp))
                            .background(ComposeColor(color))
                            .border(
                                width = if (selected) 0.dp else 1.dp,
                                color = divider,
                                shape = RoundedCornerShape(8.dp)
                            )
                    )
                    // The app's own selection face, stamped in the tile's top-left corner: a 24 dp accent
                    // disc with a white 16 dp check, so the mark is shape on a known backing rather than a
                    // second hue. Silent, because the swatch itself already announces its radio state.
                    if (selected) {
                        ListSelectionCheck(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(if (cell < SMALL_CELL) CHECK_INSET_SMALL else CHECK_INSET),
                            announceSelection = false
                        )
                    }
                }
            }
        }
    }
}
