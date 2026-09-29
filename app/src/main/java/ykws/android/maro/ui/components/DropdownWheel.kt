package ykws.android.maro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ykws.android.maro.config.AppConfig

/** The wheel's label size — the family's own list figure, §2.10's 15sp. */
private const val WHEEL_LABEL_SP = 15

/** The width of the accent rule that closes the band above and below. */
private const val BAND_RULE_DP = 1

/**
 * **The dropdown's list as a wheel** (2026-09-29): a snapped column whose centre slot is the choice, drawn
 * inside the family's `PopupSectionCard` so the band's taken face reads against `uiCardBackground` exactly
 * as it does on the bars.
 *
 * Three things are deliberate here. **The band is the mark** — `uiSelectContainer` behind an accent rule
 * above and below, the app's own taken-choice face — and only the label under it is bold, so the wheel needs
 * no check glyph of its own. **A tap is what chooses**, not the drag: the drag scrolls and snaps, the band
 * shows what a tap would take, and the commit that the retired roller could not make reliably is a finger
 * down on a row instead. **The slot is the bars' own height**, `WHEEL_ITEM_DP` at scale 1.0 and never less,
 * measured from the label so a large font scale grows the wheel as it grows the bars.
 *
 * The arithmetic — the slot count, the quantised height, the end padding, the row the band names and the
 * landing that puts the chosen entry under it — lives in [WheelPolicy] and is unit-tested there; this file
 * is layout only.
 *
 * **Two invariants hold the landing up, and both are stated rather than implied** (2026-09-29). The popup
 * is composed afresh on every open, so **frame 0 is the rest position** the landing counts from — a caller
 * hoisting the list state above the open would break it. And the last entry's own distance sits inside the
 * scroll range because the end pad is `(slots − 1)/2 × slot` with `slots` taken from the same expression
 * that sizes the wheel.
 */
@Composable
internal fun DropdownWheel(
    labels: List<String>,
    selectedIndex: Int,
    onChoose: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()

    // The slot at this font scale: the bars' figure as a floor, the label's own line when it is larger.
    val slotDp = remember(density.fontScale) {
        val lineDp = with(density) { measurer.measure("Ag", TextStyle(fontSize = WHEEL_LABEL_SP.sp)).size.height.toDp() }
        maxOf(WHEEL_ITEM_DP, lineDp.value + 2f * BAR_CELL_PAD_VERTICAL_DP)
    }
    val boundDp = popupMaxHeightDp(LocalConfiguration.current.screenHeightDp)
    val slots = remember(labels.size, slotDp, boundDp) { wheelSlotsFitting(labels.size, slotDp, boundDp) }
    val endPadDp = remember(slots, slotDp) { wheelEndPadDp(slots, slotDp) }
    val listState = rememberLazyListState()
    // The centre snap, which is the public form (2026-09-29): `rememberSnapFlingBehavior` takes a state
    // and a position and nothing else, and the `SnapFlingBehavior` class that would take a spring and a
    // decay is internal to the library — so the spring and the fling's friction are the library's own
    // until a `TargetedFlingBehavior` of ours earns the pair.
    val snap = rememberSnapFlingBehavior(listState, SnapPosition.Center)

    val centred by remember {
        derivedStateOf {
            val layout = listState.layoutInfo
            wheelCentredIndex(
                visible = layout.visibleItemsInfo.map { WheelItemBounds(it.index, it.offset, it.size) },
                viewportStartOffsetPx = layout.viewportStartOffset,
                viewportSizePx = layout.viewportSize.height
            )
        }
    }

    // **The landing, and the one comparison the wheel never made** (2026-09-29). From its rest frame the
    // list scrolls **by** the entry's own distance on the slot grid — `index × slot` — so no offset sign
    // and no origin have to be assumed, and the end pad cancels out of the difference instead of entering
    // the sum. The row the band then names is read from the first laid-out frame and compared with the
    // entry the caller holds: once, and only here, so the wheel's answer and its drawing cannot drift
    // apart, and no later drag is second-guessed.
    LaunchedEffect(selectedIndex, slotDp, slots, labels.size) {
        if (labels.isEmpty()) return@LaunchedEffect
        val target = selectedIndex.coerceIn(0, labels.lastIndex)
        val slotPx = with(density) { slotDp.dp.toPx() }
        val landing = wheelTargetScrollPx(target, slotPx)
        if (landing != 0f) listState.scrollBy(landing)
        withFrameNanos { } // the landing's own layout, before its result is read back
        val correction = wheelCorrectionSlots(target, centred ?: target)
        if (correction != 0) listState.scrollBy(correction * slotPx)
    }

    PopupSectionCard {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height((slots * slotDp).dp)
        ) {
            // The band is the bottom layer, so the centred row's own label draws over it rather than under.
            Column(modifier = Modifier.align(Alignment.Center).fillMaxWidth().height(slotDp.dp)) {
                Box(Modifier.fillMaxWidth().height(BAND_RULE_DP.dp).background(ComposeColor(AppConfig.uiAccent)))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(ComposeColor(AppConfig.uiSelectContainer))
                )
                Box(Modifier.fillMaxWidth().height(BAND_RULE_DP.dp).background(ComposeColor(AppConfig.uiAccent)))
            }
            LazyColumn(
                state = listState,
                flingBehavior = snap,
                contentPadding = PaddingValues(vertical = endPadDp.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height((slots * slotDp).dp)
            ) {
                itemsIndexed(labels) { index, label ->
                    val isCentred = index == centred
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(slotDp.dp)
                            .clickable { onChoose(index) }
                            .semantics {
                                selected = isCentred
                                role = Role.RadioButton
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            color = ComposeColor(AppConfig.uiTextPrimary),
                            fontSize = WHEEL_LABEL_SP.sp,
                            fontWeight = if (isCentred) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
