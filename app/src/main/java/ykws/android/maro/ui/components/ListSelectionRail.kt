package ykws.android.maro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ykws.android.maro.R

/**
 * The width of the list card's leading **selection door** visuals: the 6 dp accent bar plus the 8 dp
 * of the content's own leading padding, folded into the door. The two cards set their content column's
 * leading padding to 0, so the text keeps the place that padding gave it and the door is the card's
 * whole left edge. One width across both modes, so no card reflows as selection comes and goes.
 */
val ListSelectionZoneWidth: Dp = 14.dp

/**
 * The width of the door's **touch zone**: [ListSelectionZoneWidth]'s 14 dp of visuals widened to a
 * 24 dp pointer band. The extra 10 dp reaches past the accent bar into the card body's own leading
 * padding, so the door is easier to hit than the 14 dp it draws. That overlap is the accepted cost of
 * the wider pointer zone — no pixel of the 10 dp is drawn, and the visuals stay at 14 dp.
 */
val ListSelectionTouchWidth: Dp = 24.dp

/** The accent bar's own width at the door's leading edge — the item's colour, full height. */
private val RAIL_BAR_WIDTH = 6.dp

/**
 * The list card's **leading selection door bar** — the full-height visual half of the door, as wide as
 * the 6 dp accent bar plus the content's folded leading padding. It draws the bare accent strip on the
 * item's own colour ([accentColor]) and nothing else: no pointer handling, no name, no selection mark.
 * It is a child of the card's content row, so it also reserves the 14 dp the folded padding would have
 * taken and the content keeps starting at the door's trailing edge.
 *
 * **One home.** The door's pointer handling lives in [ListSelectionTouchZone], never here; the check is
 * [ListSelectionCheck], drawn on the card's first line.
 */
@Composable
fun ListSelectionRail(
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(ListSelectionZoneWidth)
            .fillMaxHeight(),
        contentAlignment = Alignment.TopStart
    ) {
        // The 6 dp accent bar — the item's own colour, full height, at the door's leading edge.
        Box(
            modifier = Modifier
                .width(RAIL_BAR_WIDTH)
                .fillMaxHeight()
                .background(accentColor)
        )
    }
}

/**
 * The list card's **selection door touch zone** — the pointer half of the door: a full card-height
 * band [ListSelectionTouchWidth] wide at the card's leading edge, drawn as the outer `Box`'s **last
 * child** so it sits above the card content. A tap enters multiselect and selects the item through
 * [onSelect]; a long-press resolves exactly as a long-press on the card body through [onLongPress], so
 * the widened band is no dead spot.
 *
 * **Inert without a door.** Both [onSelect] and [onLongPress] null emits **no node at all** — which is
 * what the drawer and inspect call sites pass — so those cards are untouched. When the door is present,
 * the zone carries the `cd_select` name unless the card is already selected (the check's own
 * `cd_selected` speaks for it then).
 *
 * **An overlay, not a layout child.** This is a `BoxScope` extension and must be called directly inside
 * the card's outer `Box`: it matches the card's size through `matchParentSize()` and narrows its
 * *pointer* surface to [ListSelectionTouchWidth], so it consumes no layout width. Only the 24 dp band
 * takes pointers — the rest of the overlay carries no pointer node — so the card's own tap outside the
 * band is unaffected, the multiselect interceptor drawn above the content still owns the tap while
 * selection is active, and the ancestor swipe detector still wins once a drag passes touch slop.
 */
@Composable
fun BoxScope.ListSelectionTouchZone(
    isSelected: Boolean,
    onSelect: (() -> Unit)?,
    onLongPress: (() -> Unit)?
) {
    val door = onSelect ?: onLongPress
    if (door == null) return
    val doorLabel = if (!isSelected) stringResource(R.string.cd_select) else null
    Box(modifier = Modifier.matchParentSize()) {
        Box(
            modifier = Modifier
                .width(ListSelectionTouchWidth)
                .fillMaxHeight()
                .combinedClickable(
                    onClick = { onSelect?.invoke() },
                    onLongClick = onLongPress
                )
                .then(
                    if (doorLabel != null) Modifier.semantics { contentDescription = doorLabel }
                    else Modifier
                )
        )
    }
}
