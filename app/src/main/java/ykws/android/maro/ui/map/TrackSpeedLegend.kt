package ykws.android.maro.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
/** Tick label size (sp). */
private val LEGEND_LABEL_SIZE = 10.sp
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
 * of their values. Visibility is the caller's decision: heatmap mode with a track selected.
 */
@Composable
internal fun TrackSpeedLegend(
    ramp: HeatmapRamp,
    ticks: List<HeatmapScaleTick>,
    minKn: Float,
    modifier: Modifier = Modifier
) {
    val topKn = ticks.lastOrNull()?.positionKn ?: 0f
    if (ramp.families.isEmpty() || topKn - minKn <= 0f) return
    // The zone info tiles' own text token, taken as that token rather than as a copied value, so a
    // label and a tile's text stay in step through the palette.
    val labelColor = ComposeColor(AppConfig.uiTextPrimary)
    val labelHalfHeight = rememberedLabelHalfHeightDp()

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            // The same token the map's zone info text uses, taken as that token so the two match by
            // construction when the palette moves.
            .background(ComposeColor(AppConfig.uiTextScrim))
            .border(1.dp, ComposeColor(AppConfig.uiDividerColor), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
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
            Spacer(Modifier.width(6.dp))
            Box(Modifier.height(LEGEND_BAR_HEIGHT)) {
                // Every row prints: the table is the specification, so nothing here drops a label.
                ticks.forEach { tick ->
                    Text(
                        text = tick.label,
                        color = labelColor,
                        fontSize = LEGEND_LABEL_SIZE,
                        modifier = Modifier.offset(
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
 * drawn whole instead of clipped. The former floor under the measurement went with the label-drop rule
 * that consumed it; nothing here depends on a minimum gap any more.
 */
@Composable
private fun rememberedLabelHalfHeightDp(): Dp = with(LocalDensity.current) {
    rememberTextMeasurer()
        .measure(text = LEGEND_SAMPLE_LABEL, style = TextStyle(fontSize = LEGEND_LABEL_SIZE))
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
