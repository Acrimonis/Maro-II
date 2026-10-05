package ykws.android.maro.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ykws.android.maro.config.AppConfig

// ─────────────────────────────────────────────────────────────────────────────
// The box, and nothing else.
//
// One dropdown's field — the surface and the value — with its own metrics and its own value style, stated
// here once. `DropdownRow` composes it for the labelled field, `DropdownPairRow` for two of them side by
// side, and neither re-spells a padding or a style: a caller's business is the behaviour, never the
// measurement.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The box's own metrics; its vertical padding is the bar cells' own [`BAR_CELL_PAD_VERTICAL_DP`].
 *
 * The horizontal padding is **small on purpose** (the user's word, 2026-10-04): a box's chrome is what a row
 * of two of them cannot spare — two fields pay it twice — and the wider field is what cut the second one's
 * word. 8dp is what lets a pair hold both words whole.
 */
private const val FIELD_PAD_HORIZONTAL_DP = 8
private const val FIELD_BORDER_DP = 1

/**
 * **How a field takes its width** — the behaviour its environment sets, and never a measurement:
 *
 * - [Fill] takes the width the caller gives it, which is the shape a card's row wants;
 * - [Content] takes the width this field's own longest entry needs, asked of the box ([`dropdownBoxWidth`]), so
 *   no entry of that list can ever be cut and the field holds that width whatever the row does.
 */
internal enum class DropdownSizing { Fill, Content }

/**
 * **One dropdown's box** — the control the whole family stands on: a `Row` on the bars' own base
 * (`uiRadiusCard` behind the **1dp `uiAccent`** edge), holding the value in `uiTextPrimary` **Bold** on one
 * line, **drawn on the axis the caller's `textAlign` names** (centred by default, 2026-10-05), ellipsised only
 * where it is given less than it needs. **It carries no arrow** (2026-10-05): the
 * `KeyboardArrowDown` glyph and the width it reserved are gone, so the field is its value alone. It paints no
 * surface of its own, as the bars paint none.
 *
 * **It is still the anchor, the tap target and the measured size** (§2.12's single-target rule): the click
 * that opens the list and the node's `accessibleName` with `Role.DropdownList` live here, so a label-less box
 * still announces itself, and it reports its own measured size ([onMeasured]) — the wheel takes that size as
 * its slot — so its list is placed from bounds the control measured rather than from a library's guess.
 *
 * [modifier] is the width the caller decided on — this box never decides for itself who sizes it; that answer
 * is [`dropdownBoxWidth`], which the caller applies.
 */
@Composable
internal fun DropdownBox(
    value: String,
    accessibleName: String,
    onClick: () -> Unit,
    textAlign: TextAlign = TextAlign.Center,
    modifier: Modifier = Modifier,
    onMeasured: (IntSize) -> Unit = {}
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(AppConfig.uiRadiusCard.dp))
            .border(
                width = FIELD_BORDER_DP.dp,
                color = ComposeColor(AppConfig.uiAccent),
                shape = RoundedCornerShape(AppConfig.uiRadiusCard.dp)
            )
            .onSizeChanged(onMeasured)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = accessibleName
                role = Role.DropdownList
            }
            .padding(
                horizontal = FIELD_PAD_HORIZONTAL_DP.dp,
                vertical = BAR_CELL_PAD_VERTICAL_DP.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = boxValueStyle(),
            textAlign = textAlign,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * **How the box's value reads** — the one statement of it, read by the `Text` that draws the value and by
 * [`dropdownBoxWidth`] that measures it. The size and the weight are the box's own; the rest is the theme's,
 * merged in exactly the way `Text` merges `LocalTextStyle`, so the two read the same style and a measurement
 * can never come out short of what is drawn.
 */
@Composable
private fun boxValueStyle(): TextStyle = LocalTextStyle.current.merge(
    TextStyle(
        color = ComposeColor(AppConfig.uiTextPrimary),
        fontSize = AppConfig.uiFontValueSize.sp,
        fontWeight = FontWeight.Bold
    )
)

/**
 * **The width this box takes to hold [words]** — its own chrome (its 8dp padding and its rim, every metric
 * read from the constants above) plus the widest of them measured in [`boxValueStyle`], the very style the
 * value is drawn in.
 *
 * It is the box's own answer, so a caller that must fix a field's width — a pair's fixed side — asks for this
 * rather than re-spelling a padding or a style. The 1dp rim is added on **both** sides although
 * `Modifier.border` draws inside the bounds and adds nothing to the size: that 2dp is the answer's rounding
 * slack, and it is what keeps the widest entry from rounding into the ellipsis it must never wear.
 */
@Composable
internal fun dropdownBoxWidth(words: List<String>): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = boxValueStyle()
    val widest = remember(words.joinToString("\u0000"), style) {
        with(density) { words.maxOf { measurer.measure(it, style).size.width }.toDp() }
    }
    return widest +
        (FIELD_PAD_HORIZONTAL_DP * 2).dp +
        (FIELD_BORDER_DP * 2).dp
}
