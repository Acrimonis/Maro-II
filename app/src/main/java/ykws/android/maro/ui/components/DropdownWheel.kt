package ykws.android.maro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
 * inside the family's `PopupSectionCard` so the band's taken face reads against `uiCardBackground` exactly as
 * it does on the bars.
 *
 * Two things are deliberate here. **The band is the mark** — `uiSelectContainer` behind an accent rule above
 * and below, the app's own taken-choice face — and only the label under it is bold, so the wheel needs no
 * check glyph of its own. **The release is the commit** (2026-10-17, revision 2): the row the band names at
 * the instant the pointer lifts is written through [onChoose] and the popup closes with that write, so the
 * band is **the value, not a candidate**. A tap on a row is the degenerate release — no movement, that row's
 * own write and close; a drag begun inside the wheel writes its band at its own pointer-up, the observer
 * reading at the Final pass and consuming nothing; and the fling `rememberSnapFlingBehavior` would have
 * carried is cut by the close rather than chased across it. An outside tap writes the band it finds, so the
 * scrim means the same as tapping the banded row; **back alone closes without a write**, and a cancelled drag
 * closes with nothing written. This is the gesture the retired roller could not make reliable, re-entered by
 * decision and guarded by reading the same band the wheel draws — the commit is *guarded*, not proven
 * unrelated to that fault.
 *
 * **The slot is the box's own measured height**, taken as a parameter rather than measured here from the
 * label, so the two surfaces cannot drift at any font scale: the wheel's height is `slots × slot` and its
 * centre is the box's centre by construction. **The rows' alignment is the control's, not the wheel's own**
 * (2026-10-05): it arrives as [textAlign] with a centred default, so the wheel's labels and the field's value
 * are drawn on the same axis. **The wheel no longer owns its [LazyListState]** (2026-10-05)
 * either: the field hoists it so its own drag detector can scroll it, the popup being a separate window the
 * pointer that opened it never reaches. That owner composes the state afresh on every open, so **frame 0 is
 * still the rest position** the landing counts from — a caller hoisting one state above the open would break
 * it.
 *
 * The arithmetic — the slot count, the quantised height, the end padding, the row the band names and the
 * landing that puts the chosen entry under it — lives in [WheelPolicy] and is unit-tested there; this file is
 * layout only.
 */
@Composable
internal fun DropdownWheel(
    labels: List<String>,
    selectedIndex: Int,
    onChoose: (Int) -> Unit,
    slotDp: Float,
    listState: LazyListState,
    onDismiss: () -> Unit,
    textAlign: TextAlign = TextAlign.Center,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val boundDp = popupMaxHeightDp(LocalConfiguration.current.screenHeightDp)
    val slots = remember(labels.size, slotDp, boundDp) { wheelSlotsFitting(labels.size, slotDp, boundDp) }
    val endPadDp = remember(slots, slotDp) { wheelEndPadDp(slots, slotDp) }
    // The centre snap, which is the public form (2026-09-29): `rememberSnapFlingBehavior` takes a state and a
    // position and nothing else, and the `SnapFlingBehavior` class that would take a spring and a decay is
    // internal to the library — so the spring and the fling's friction are the library's own until a
    // `TargetedFlingBehavior` of ours earns the pair.
    val snap = rememberSnapFlingBehavior(listState, SnapPosition.Center)
    // The callbacks are held rather than captured by the gesture below, which is keyed on nothing that moves
    // per recomposition: a caller's new option list must not leave an older one behind the observer.
    val currentOnChoose by rememberUpdatedState(onChoose)
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    val centred by remember { derivedStateOf { listState.wheelCentredRow() } }

    // **The landing, and the one comparison the wheel never made** (2026-09-29). From its rest frame the list
    // scrolls **by** the entry's own distance on the slot grid — `index × slot` — so no offset sign and no
    // origin have to be assumed, and the end pad cancels out of the difference instead of entering the sum.
    // The row the band then names is read from the first laid-out frame and compared with the entry the caller
    // holds: once, and only here, so the wheel's answer and its drawing cannot drift apart. **It stays keyed
    // on the open** (2026-10-17, revision 2): the landing is the one scroll no finger makes — every gesture
    // that moves the wheel writes and closes on its lift — so keying on `selectedIndex` would re-land the
    // wheel under the close that write has already ordered.
    LaunchedEffect(Unit) {
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
                .pointerInput(Unit) {
                    // **The wheel's own release** (2026-10-17): a drag begun inside the popup writes the row
                    // the band names at its pointer-up and closes. The observer reads at the Final pass and
                    // consumes nothing, so the LazyColumn keeps its scroll and its snap; it answers only a
                    // gesture that moved past the touch slop, leaving taps to the rows' own `clickable`, so
                    // one tap writes once.
                    awaitEachGesture {
                        val down = awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Final
                        )
                        var dragged = false
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Final)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                dragged = true
                            }
                            if (change.changedToUpIgnoreConsumed()) break
                        }
                        if (dragged) {
                            centred?.let { currentOnChoose(it) }
                            currentOnDismiss()
                        }
                    }
                }
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
                            // The degenerate release: a tap on a row writes that row and the caller closes.
                            // A drag never arrives here — the wheel's own observer answers it — and every
                            // exit is a write, so no tap is a cancel.
                            .clickable {
                                onChoose(index)
                                onDismiss()
                            }
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
                            textAlign = textAlign,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * **The row the band names, read off the hoisted state** — the one bridge from Compose's layout to the pure
 * [wheelCentredIndex]. Every commit reaches the band through this same read: the wheel's own release, the
 * field drag's release and an outside tap all answer from it, so no second definition of the row under the
 * band exists to drift from the drawing.
 */
internal fun LazyListState.wheelCentredRow(): Int? {
    val layout = layoutInfo
    return wheelCentredIndex(
        visible = layout.visibleItemsInfo.map { WheelItemBounds(it.index, it.offset, it.size) },
        viewportStartOffsetPx = layout.viewportStartOffset,
        viewportSizePx = layout.viewportSize.height
    )
}
