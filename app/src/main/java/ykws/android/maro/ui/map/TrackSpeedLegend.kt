package ykws.android.maro.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ykws.android.maro.R
import ykws.android.maro.config.AppConfig
import ykws.android.maro.config.HeatmapRamp
import ykws.android.maro.config.HeatmapScaleTick
import kotlin.math.roundToInt

/**
 * Legend bar height (dp) — the scale's top at the top, its minimum at the foot. With the label-drop
 * rule gone (§18) every row prints whatever its neighbours do, so the height no longer has to clear a
 * crowd floor and 150 dp is a free readability knob rather than one the shipped table imposes.
 */
private val LEGEND_BAR_HEIGHT = 150.dp
/** Legend bar width (dp). */
private val LEGEND_BAR_WIDTH = 14.dp
/** Tick label size (sp) — `ui.map.overlay.text.size`, the overlay family's one text size. */
private val LEGEND_LABEL_SIZE: TextUnit get() = AppConfig.uiMapOverlayTextSize.sp
/** Any single-line label, measured for its line box: every label shares that height. */
private const val LEGEND_SAMPLE_LABEL = "35"

/**
 * The selected track's speed scale: a vertical strip on a card backdrop, filled by the same [colorAt]
 * that paints the line, with every row of the tick table printing its own text beside the bar.
 *
 * The bar runs linearly from [minKn] to [ticks]' last position — 2 to 35 kn as shipped — so the scale's
 * top is the table's own last row rather than a key of its own, while the foot, having no row to ride
 * on, is one. A speed outside that window is still painted on the line and simply absent from the
 * scale. The table *is* the specification: no mark is drawn inside the colour and no row is dropped, so
 * the labels carry position alone and two rows closer than a label box overlap rather than losing one
 * of their values. Visibility is the caller's decision: the map carrying the ramp's fill — and, while a
 * track is open, that track's own fill — which is what [legendVisibleForState] asks.
 *
 * The card *is* the collapse target: no handle, no chevron — the whole strip is one tap surface, and
 * [onToggle] is what the caller writes when it is tapped. It paints through [MapSurface], which also
 * takes the tap inside its own clip so the ripple stays bounded by the card's corner; the geometry is
 * otherwise untouched, so the expanded face stays pixel-identical to the strip that shipped before the
 * toggle — the card's inset included, since `ui.map.surface.padding` carries the 6 dp the card used to
 * apply itself.
 */
@Composable
internal fun TrackSpeedLegend(
    ramp: HeatmapRamp,
    ticks: List<HeatmapScaleTick>,
    minKn: Float,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val topKn = ticks.lastOrNull()?.positionKn ?: 0f
    if (ramp.families.isEmpty() || topKn - minKn <= 0f) return
    // The overlay cards' own pair, so the labels move with the fill they sit on:
    // `ui.map.overlay.text.color` — mid blue-grey, the palette's secondary token — reads over both
    // bright water and dark, and `ui.map.overlay.text.weight` owns the face.
    val labelColor = ComposeColor(AppConfig.uiMapOverlayTextColor)
    val labelWeight = FontWeight(AppConfig.uiMapOverlayTextWeight)
    val labelHalfHeight = rememberedLabelHalfHeightDp(labelWeight)
    // What the tap will do, not the state it is in: the card is shown while the scale is expanded.
    val collapseCd = stringResource(R.string.cd_collapse_speed_scale)

    // Start-aligned rather than centred: the caller gives the card one toggle button's width on the
    // row's own gutter, so the bar sits on that gutter with the card's padding alone between them.
    MapSurface(
        face = mapSurfaceFace(),
        modifier = modifier,
        onClick = onToggle,
        contentDescription = collapseCd,
        contentAlignment = Alignment.CenterStart
    ) {
        // The row takes the card's whole content box: the bar keeps its own width, the gap its own,
        // and the label column the rest — from the bar's right edge to the card's right inner edge.
        // Content-sizing the column left the labels hard against the 12 dp the card's 32 dp content
        // box has left after the bar and the gap, so the column now spans bar-to-border instead.
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Canvas(modifier = Modifier.width(LEGEND_BAR_WIDTH).height(LEGEND_BAR_HEIGHT)) {
                // The scale is inset at both ends by half a label, so the end labels are drawn whole;
                // the fill still covers the whole bar, clamped at the ends.
                val insetPx = labelHalfHeight.toPx()
                val plotHeight = (size.height - insetPx * 2f).coerceAtLeast(1f)
                val rows = size.height.roundToInt().coerceAtLeast(1)
                val rowHeight = size.height / rows
                for (row in 0 until rows) {
                    val centre = (row + 0.5f) * rowHeight
                    val fraction = ((centre - insetPx) / plotHeight).coerceIn(0f, 1f)
                    drawRect(
                        color = ComposeColor(colorAt(minKn + (topKn - minKn) * (1f - fraction), ramp)),
                        topLeft = Offset(0f, row * rowHeight),
                        size = Size(size.width, rowHeight + 1f)
                    )
                }
            }
            Spacer(Modifier.width(AppConfig.uiMapOverlayGap.dp))
            // `weight(1f)` gives the column everything the bar and the gap leave, so the labels own
            // the space between the bar and the card's right inner edge. They are centred in it
            // rather than stacked at the bar's edge because what the card hands out is a column, not
            // a text width: centring spends an unused remainder evenly instead of leaving a ragged
            // right edge. Horizontal packing only — the bar, the gap, the tick offsets, the label
            // measurement and the height arithmetic are untouched.
            Box(Modifier.height(LEGEND_BAR_HEIGHT).weight(1f)) {
                // Every row prints: the table is the specification, so nothing here drops a label.
                ticks.forEach { tick ->
                    Text(
                        text = tick.label,
                        color = labelColor,
                        fontSize = LEGEND_LABEL_SIZE,
                        // The weight is the property's: a heavier stroke of the same colour reads
                        // stronger over pale water, and the measurement is handed the same face so the
                        // bar's end insets stay true to what is drawn.
                        fontWeight = labelWeight,
                        // One line, so a label that does not fit shows rather than wrapping.
                        maxLines = 1,
                        // And no wrapping either: `softWrap = false` is the rule that cannot be left
                        // to `maxLines`, which only caps the line count — a label too wide for its box
                        // overflows instead of reflowing onto a second line.
                        softWrap = false,
                        // Centred in the column the row leaves it. The text fills that column so the
                        // alignment has a box to centre in: left to itself the box would shrink to the
                        // text and `Center` would be a no-op.
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(
                                y = tickOffsetDp(
                                    kn = tick.positionKn,
                                    minKn = minKn,
                                    topKn = topKn,
                                    barHeightDp = LEGEND_BAR_HEIGHT.value,
                                    insetDp = labelHalfHeight.value
                                ).dp - labelHalfHeight
                            )
                    )
                }
            }
        }
    }
}

/**
 * Half the label's *real* line box at the current font scale (dp), measured rather than guessed. The
 * labels are centred on their rows by it, and it is the bar's own end inset, so the endmost labels are
 * drawn whole instead of clipped. The style carries [weight], the face the labels themselves draw — so
 * the box is the one actually used rather than the other face's: the two share vertical metrics, but
 * measuring the drawn face no longer rests on their agreeing. The former floor under the measurement
 * went with the label-drop rule that consumed it; nothing here depends on a minimum gap any more.
 */
@Composable
private fun rememberedLabelHalfHeightDp(weight: FontWeight): Dp = with(LocalDensity.current) {
    rememberTextMeasurer()
        .measure(
            text = LEGEND_SAMPLE_LABEL,
            style = TextStyle(fontSize = LEGEND_LABEL_SIZE, fontWeight = weight)
        )
        .size.height
        .toDp() / 2f
}

/**
 * Downward offset (dp) of one position on the bar's linear [minKn] → [topKn] scale, inset at both ends
 * by [insetDp] — half a label — so a label at either end is drawn whole rather than clipped. A position
 * outside that window clamps to the end it falls beyond, so a row below the foot sits on the foot
 * rather than leaving the bar. Rows are assumed ascending, which is what makes the insets cover the
 * endmost two. Pure and dp-valued, so the fill, the labels and a JVM test rest on one arithmetic.
 */
internal fun tickOffsetDp(
    kn: Float,
    minKn: Float,
    topKn: Float,
    barHeightDp: Float,
    insetDp: Float
): Float {
    val fraction = 1f - ((kn - minKn) / (topKn - minKn)).coerceIn(0f, 1f)
    return insetDp + (barHeightDp - insetDp * 2f).coerceAtLeast(1f) * fraction
}
