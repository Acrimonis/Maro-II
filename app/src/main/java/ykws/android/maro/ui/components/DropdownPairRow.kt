package ykws.android.maro.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ykws.android.maro.config.AppConfig

// The gap between a pair's two boxes is the shared token `ui.spacing.dropdown.gap`, held with every other
// dimension in `ui.properties` and read through AppConfig: the space between two fields of one group is one
// value, whichever way the two sit, and it is deliberately small (2026-10-04) because two fields pay their
// chrome twice — what the pair leaves between them is the space its two words are read in.

/**
 * **One side of a [DropdownPairRow]** — everything a [DropdownRow] takes except its label (the comment above
 * the pair names both sides) and its width (the pair's own parameters set that).
 */
@Immutable
internal data class DropdownField<T>(
    val options: List<Pair<T, String>>,
    val selected: T,
    val onSelect: (T) -> Unit,
    val accessibleName: String
)

/**
 * **What a side of a [DropdownPairRow] is told to do about its width** — a behaviour, never a measurement:
 *
 * - [Content] holds the width of its own **longest option**, so no entry of that list can ever be cut, and the
 *   side takes no share of the row at all;
 * - [Remainder] takes whatever the row has left, which makes it the **elastic** side — its own value is what
 *   trims on one line when the row is short;
 * - [Proportional] takes a share of the row **in proportion to its own longest option** (2026-10-05): each
 *   side's measured content width is the weight the row normalises, so a wider word earns a wider box.
 *
 * Which side is which is the environment's word, and the width itself is the field's own business: the pair
 * translates this into the field's own behaviour and hands over no number.
 */
internal enum class DropdownPairWidth { Content, Remainder, Proportional }

/**
 * **Two of these controls side by side** — the shape a row of two settings wears: each side a label-less
 * [`DropdownRow`] whose list opens as the wheel in its popup, under one comment naming both, with **no vertical
 * rule between them** (§2.14's `SectionRow` lays out two *sections*, and this is two controls in one).
 *
 * **The capability is the width, and its default is the pair the drawer's quick access wants**: a
 * [DropdownPairWidth.Content] left and a [DropdownPairWidth.Remainder] right. A caller that wants the two boxes
 * to share the row evenly passes [DropdownPairWidth.Remainder] for both; one that wants both sized to their own
 * words passes [DropdownPairWidth.Content] for both and leaves the row's tail empty; and one that wants each box
 * to take a share matching the word it must hold passes [DropdownPairWidth.Proportional] for both.
 *
 * It hands each side its behaviour and nothing else — no width, no metric — and the sides' own names travel as
 * their `accessibleName`, so neither carries a visible label: the shape the route ends already wear.
 */
@Composable
internal fun <L, R> DropdownPairRow(
    left: DropdownField<L>,
    right: DropdownField<R>,
    modifier: Modifier = Modifier,
    leftWidth: DropdownPairWidth = DropdownPairWidth.Content,
    rightWidth: DropdownPairWidth = DropdownPairWidth.Remainder,
    gap: Dp = AppConfig.uiSpacingDropdownGap.dp,
    verticalPadding: Dp = AppConfig.uiPaddingToggleVertical.dp
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = verticalPadding),
        horizontalArrangement = Arrangement.spacedBy(gap)
    ) {
        DropdownPairSide(left, leftWidth)
        DropdownPairSide(right, rightWidth)
    }
}

/**
 * One side, told what to do about its width. It is a `RowScope` extension because the elastic and proportional
 * behaviours are [`RowScope.weight`] — the row is what a side that shares is measured against.
 */
@Composable
private fun <T> RowScope.DropdownPairSide(field: DropdownField<T>, width: DropdownPairWidth) {
    when (width) {
        // Fixed to its longest entry: the field hugs its own box and takes no share, and the width is asked of
        // the box rather than named here.
        DropdownPairWidth.Content -> DropdownPairField(field, DropdownSizing.Content, Modifier)
        // Elastic: whatever the fixed side and the gap leave.
        DropdownPairWidth.Remainder ->
            DropdownPairField(field, DropdownSizing.Fill, Modifier.weight(1f))
        // Proportional: this side's own longest word is its weight, and the row normalises the two sides' — so
        // each box takes the row's space in the ratio of the widest word it must hold.
        DropdownPairWidth.Proportional ->
            DropdownPairField(field, DropdownSizing.Fill, Modifier.weight(dropdownPairShare(field.options)))
    }
}

/**
 * **A side's own weight in a proportional pair** — the width its longest option needs, asked of the box
 * ([`dropdownBoxWidth`], which reads the box's chrome and the theme's own style), floored at one so a weighted
 * child always takes a positive figure. The `Row` normalises the two sides' weights, so the pair splits in
 * proportion to what each box must hold.
 */
@Composable
private fun <T> dropdownPairShare(options: List<Pair<T, String>>): Float =
    dropdownBoxWidth(options.map { it.second }).value.coerceAtLeast(1f)

/** The label-less row itself, so both behaviours above compose one statement of it. */
@Composable
private fun <T> DropdownPairField(
    field: DropdownField<T>,
    sizing: DropdownSizing,
    modifier: Modifier
) {
    DropdownRow(
        label = null,
        options = field.options,
        selected = field.selected,
        onSelect = field.onSelect,
        accessibleName = field.accessibleName,
        sizing = sizing,
        modifier = modifier
    )
}
