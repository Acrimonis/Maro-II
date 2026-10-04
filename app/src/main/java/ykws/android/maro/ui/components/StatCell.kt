package ykws.android.maro.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ykws.android.maro.config.AppConfig

// ─────────────────────────────────────────────────────────────────────────────
// One reading, stated once.
//
// The cell is drawn in two shapes — the cards' fixed 33/66 split and the columned one a table gives a
// measured label width — and both compose the same three parts, so the reading's metrics, its colours
// and its separator have one home each and cannot drift between the shapes.
// ─────────────────────────────────────────────────────────────────────────────

/** The separator a label and its value are read apart by — the one statement of it. */
private const val SEPARATOR = ":"

/** Width (dp) of the separator's own slot in the cell's columned shape. */
private const val SEPARATOR_SLOT_DP = 6

/** The label's type: 11 sp `uiTextMuted`, one line. */
private val LABEL_FONT_SIZE = 11.sp
private val LABEL_LINE_HEIGHT = 12.sp

/** The value's type: 12 sp `uiTextPrimary` Medium, one line. */
private val VALUE_FONT_SIZE = 12.sp
private val VALUE_LINE_HEIGHT = 13.sp

/**
 * The label, with the separator it is read with.
 *
 * [separatorCentred] is the columned shape's placement: the label stands without its colon and the
 * separator sits centred in a slot of its own. Otherwise the separator rides the label's own end,
 * which is the cards' placement. [labelModifier] belongs to the label in both: the cell hands the
 * default shape a weighted share and the columned one the table's measured width.
 */
@Composable
private fun StatLabel(label: String, separatorCentred: Boolean, labelModifier: Modifier) {
    if (separatorCentred) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                color = Color(AppConfig.uiTextMuted),
                fontSize = LABEL_FONT_SIZE,
                lineHeight = LABEL_LINE_HEIGHT,
                textAlign = TextAlign.End,
                maxLines = 1,
                modifier = labelModifier
            )
            Box(
                modifier = Modifier.width(SEPARATOR_SLOT_DP.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = SEPARATOR,
                    color = Color(AppConfig.uiTextMuted),
                    fontSize = LABEL_FONT_SIZE,
                    lineHeight = LABEL_LINE_HEIGHT,
                    maxLines = 1
                )
            }
        }
    } else {
        Text(
            text = label + SEPARATOR,
            color = Color(AppConfig.uiTextMuted),
            fontSize = LABEL_FONT_SIZE,
            lineHeight = LABEL_LINE_HEIGHT,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = labelModifier
        )
    }
}

/** The value — its colour, type and alignment stated once, placed by [modifier]. */
@Composable
private fun StatValue(value: String, modifier: Modifier) {
    Text(
        text = value,
        color = Color(AppConfig.uiTextPrimary),
        fontSize = VALUE_FONT_SIZE,
        lineHeight = VALUE_LINE_HEIGHT,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        textAlign = TextAlign.Start,
        modifier = modifier
    )
}

/**
 * One cell of a readings grid.
 *
 * **The default shape** is the fixed split the cards use: the label (33 %, right-aligned) carrying its
 * separator, a 3 dp gap, then the value (66 %, left-aligned), each held to one line.
 *
 * **The columned shape** ([labelWidth] non-null) gives the three parts a column each: the label is
 * held to that width and right-aligned, the separator alone stands centred in a 6 dp slot, and the
 * value takes what is left, left-aligned. The caller that owns the table measures the width once — its
 * widest label — so every cell of that table shares one label column and one separator column, and a
 * fixed share of the cell no longer stands empty before a short label. Without a shared width the
 * values would follow each label's own end, which is why the width belongs to the table, not the cell.
 *
 * **The app's one rendering of a reading.** The track and route cards' stats grids and the route
 * panel's own row all read this cell, so a figure the app shows twice looks the same twice; the grid
 * the cell sits in — three columns on a card, one row of as many readings as the surface carries on
 * the route panel — belongs to the caller, and `docs/ui-drawer-guidelines.md` §9 is where the card's
 * grid is specified. The label, the separator and the value are defined once in this file and composed
 * by both shapes, so no part of the reading is stated twice.
 *
 * [modifier] is what lets a caller place it: the cell itself is always `fillMaxWidth` inside the box
 * the caller weights.
 */
@Composable
internal fun StatCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    labelWidth: Dp? = null
) {
    if (labelWidth != null) {
        Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatLabel(label = label, separatorCentred = true, labelModifier = Modifier.width(labelWidth))
            StatValue(value = value, modifier = Modifier.weight(1f))
        }
    } else {
        Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatLabel(label = label, separatorCentred = false, labelModifier = Modifier.weight(0.33f))
            Spacer(Modifier.width(3.dp))
            StatValue(value = value, modifier = Modifier.weight(0.66f))
        }
    }
}
