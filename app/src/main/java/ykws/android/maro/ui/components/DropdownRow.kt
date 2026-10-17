package ykws.android.maro.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import ykws.android.maro.config.AppConfig

/**
 * The slot before the box has measured — the bars' own cell figure, replaced by the box's real height on the
 * first frame. The popup only opens from a laid-out box, so this is a guard, not a reading.
 */
private const val WHEEL_SLOT_FALLBACK_DP = 38f

/**
 * How long the popup's fade and rise take — one figure for the enter and the exit alike, so the close
 * mirrors the open rather than paraphrasing it (2026-10-17).
 */
private const val POPUP_TRANSITION_DURATION_MS = 120

/**
 * How far the popup is drawn off its rest slot — up as it opens, and back to this same rise as it closes.
 * A translation, so no measured size or offset moves.
 */
private const val POPUP_RISE_DP = 4f

/**
 * The popup's own phase — **a closed set, so no pair of booleans can drift** (2026-10-17). `Open` and
 * `Closing` both compose the popup, the second holding the window only while the exit frame draws; `Closed`
 * composes nothing.
 */
private enum class DropdownPhase { Closed, Open, Closing }

/**
 * **A dropdown field: an optional label and description over one box, and the wheel that box opens.**
 *
 * The box itself is [`DropdownBox`], which owns its surface, its metrics and the style its value is drawn in —
 * this control owns everything around it: the optional heading, the anchor, the popup and its wheel. Nothing
 * here re-spells a padding or a style.
 *
 * **What its environment sets is the behaviour, [sizing]** — the width the caller gives ([DropdownSizing.Fill],
 * the default) or the width this field's longest entry needs ([DropdownSizing.Content]), which is asked of the
 * box through [`dropdownBoxWidth`] and applied here. No caller hands this control a width of its own.
 *
 * A passed `label`/`description` stacks above the box; every call site passes none today, a section header or
 * the drawer's own comment being what names the control. **A required `accessibleName` is the one string each
 * call site hands it**, carried by the box as its node's `contentDescription` together with `Role.DropdownList`,
 * because a label-less box would otherwise announce nothing at all.
 *
 * **The list is a §2.10 popup the box itself positions, and its body is a wheel** (2026-09-29): a [Popup]
 * **centred on the box** — its width the box's own measured width, its surface carrying no inset so the card
 * fills the popup and no `uiBackground` ring shows (2026-10-05), and its offset closing the card's own vertical
 * inset and the wheel's end padding, so the banded slot is exactly the box's rectangle (§5) — carrying a
 * [PopupSurface] with `scrollable = false` and `contentPadding = 0.dp` — the wheel owns the only scroll —
 * whose content is a [DropdownWheel]. Material 3's own menu was retired for it:
 * `ExposedDropdownMenu` sized itself from the anchor and then shifted to stay inside the window, which showed
 * as a horizontal offset against the box in landscape — placement we could neither see nor override.
 *
 * **The wheel's state is hoisted here, and the field's own drag detector drives it** (2026-10-05): the popup
 * is a separate window, so the pointer that opened it never reaches the wheel, and the vertical drag that
 * begins on the box both opens the popup and scrolls the wheel through `scrollBy`. The drag is taken
 * unconditionally, so the page under that finger does not scroll with it (D0); a tap still opens the popup,
 * so the control keeps a non-drag path. The slot is the box's own measured height, so the two surfaces cannot
 * drift at any font scale.
 *
 * **The release is the commit, and nothing cancels** (2026-10-17, revision 2): the field drag's `onDragEnd`
 * writes the row the band names at that instant and closes in place of the retired snap, the wheel's own
 * pointer-up does the same inside the popup, and a tap on a row is the degenerate release of the row tapped.
 * An outside tap writes the band it finds — the scrim means exactly what tapping the banded row does — while
 * **back closes without a write**, since it can only arrive before the wheel moved, and a cancelled drag
 * closes with nothing written. The band is therefore the value, not a candidate.
 *
 * The generic [T] is the option value the caller persists (the `CustomSortField` shape): the row never holds
 * user-facing text, the caller hands in already-resolved labels for each option and the selected value.
 *
 * **The selection is resolved once, and both surfaces read that one answer** (2026-09-29): the box's word and
 * the wheel's entry come from the same resolved index, so a value the options do not carry shows the entry the
 * popup bands rather than a blank box beside a banded first row. §2.12 carries the rule.
 */
@Composable
internal fun <T> DropdownRow(
    label: String?,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    accessibleName: String,
    description: String? = null,
    sizing: DropdownSizing = DropdownSizing.Fill,
    textAlign: TextAlign = TextAlign.Center,
    modifier: Modifier = Modifier
) {
    // One phase, never a boolean pair: `Open` and `Closing` both compose the popup, `Closed` composes nothing.
    var phase by remember { mutableStateOf(DropdownPhase.Closed) }
    var anchorSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    // The hoisted wheel state, held in a slot the popup fills while it is open: the detector below reads it
    // through the holder, and a fresh instance per open keeps frame 0 the rest position the landing counts from.
    var wheelState by remember { mutableStateOf<LazyListState?>(null) }
    // One resolution, read by both surfaces: an entry the options do not carry leaves this at the first
    // option, and the box paints that same option's word — never a blank box against a banded entry 0.
    val selectedIndex = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    val selectedLabel = options.getOrNull(selectedIndex)?.second.orEmpty()
    val labels = options.map { it.second }
    // **The one band commit** (2026-10-17): the row under the band, read through the wheel's single reader off
    // the hoisted state and written through the control's own choice. The field's release and an outside tap
    // reach this; the wheel's own release reads the same row inside the popup, and a tap on a row writes that
    // row. No exit is a cancel, so a mistaken gesture is undone by opening the popup again.
    val commitBand: (LazyListState?) -> Unit = { state ->
        state?.wheelCentredRow()?.let { index -> options.getOrNull(index)?.let { onSelect(it.first) } }
    }
    // The detector below is keyed on nothing that moves per recomposition, so it reaches the commit through a
    // holder: a caller's new option list must not leave an older one behind the gesture.
    val currentCommitBand by rememberUpdatedState(commitBand)
    // **The one close path** (2026-10-17): a row tap, the wheel's pointer-up, the field's `onDragEnd`, an
    // outside tap, back, a cancelled drag and the box's own toggle all reach this, and only an open popup
    // answers it. The window held for the exit is therefore deaf by construction — a gesture inside the fade
    // finds a `Closing` phase, so it writes nothing and cannot restart the popup.
    val close: () -> Unit = { if (phase == DropdownPhase.Open) phase = DropdownPhase.Closing }
    // Its mirror: only a closed control opens, so nothing can start the popup out of its own fade.
    val open: () -> Unit = { if (phase == DropdownPhase.Closed) phase = DropdownPhase.Open }
    // **The slot is the box's own measured height** (§2), taken as dp; the bars' figure stands in until the
    // first measure, which always precedes an open.
    val slotDp = with(density) {
        if (anchorSize.height > 0) anchorSize.height.toDp().value else WHEEL_SLOT_FALLBACK_DP
    }
    val boundDp = popupMaxHeightDp(LocalConfiguration.current.screenHeightDp)
    val slots = remember(labels.size, slotDp, boundDp) { wheelSlotsFitting(labels.size, slotDp, boundDp) }
    val boxWidth = with(density) { anchorSize.width.toDp() }
    // The surface carries no inset (2026-10-05), so the content is the popup's full width and the box's own
    // measured width is exactly what the popup takes.
    val menuWidth = if (anchorSize.width > 0) boxWidth else POPUP_WIDTH_DP.dp
    // §5: the popup is centred on the box, so the band's rectangle is the box's own. With no surface inset the
    // horizontal offset is nought, and the vertical figure is the wheel's end padding plus the card's own
    // vertical inset — the same end-pad expression that sizes the wheel.
    val popupOffset = with(density) {
        val endPadPx = wheelEndPadDp(slots, slotDp).dp.toPx()
        val sectionPadPx = POPUP_SECTION_PAD_VERTICAL_DP.dp.toPx()
        IntOffset(x = 0, y = -(endPadPx + sectionPadPx).roundToInt())
    }

    Column(
        modifier = when (sizing) {
            // A field fixed to its longest entry hugs that box; a filling one takes the row it was given.
            DropdownSizing.Content -> modifier
            DropdownSizing.Fill -> modifier.fillMaxWidth()
        }
    ) {
        if (label != null) {
            Text(
                text = label,
                color = ComposeColor(AppConfig.uiTextPrimary),
                fontSize = AppConfig.uiFontToggleSize.sp,
                fontWeight = FontWeight.Medium
            )
        }
        if (description != null) {
            Text(
                text = description,
                color = ComposeColor(AppConfig.uiTextMuted),
                fontSize = AppConfig.uiFontDescSize.sp
            )
        }
        Box {
            DropdownBox(
                value = selectedLabel,
                accessibleName = accessibleName,
                onClick = { if (phase == DropdownPhase.Open) close() else open() },
                textAlign = textAlign,
                // The behaviour turned into the width it means: the box's own answer for the longest entry, or
                // the row the caller gave. Either way the number is the box's business, never the caller's.
                modifier = when (sizing) {
                    DropdownSizing.Content -> Modifier.width(dropdownBoxWidth(labels))
                    DropdownSizing.Fill -> Modifier.fillMaxWidth()
                }.pointerInput(Unit) {
                    // **The opening drag owns the popup's scroll** (§4). The pointer is the field's, so the
                    // field scrolls the hoisted state by hand; the library's snap belongs to a drag begun
                    // inside the open popup and cannot be borrowed here. The drag is taken unconditionally,
                    // so the page underneath does not scroll under that finger, and **its release is the
                    // commit** (2026-10-17): the band the finger leaves is written and the popup closes, in
                    // place of the retired snap.
                    detectVerticalDragGestures(
                        onDragStart = { open() },
                        onDragEnd = {
                            // The commit stays where it was, before the close — and only an open popup
                            // answers it, so a release inside the held fade writes nothing.
                            if (phase == DropdownPhase.Open) currentCommitBand(wheelState)
                            close()
                        },
                        onDragCancel = { close() },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            val wheel = wheelState
                            if (wheel != null) scope.launch { wheel.scrollBy(-dragAmount) }
                        }
                    )
                },
                onMeasured = { anchorSize = it }
            )
            if (phase != DropdownPhase.Closed) {
                // A fresh state per open, so frame 0 is the rest frame the landing counts from; the holder
                // lets the detector above reach it, and clears it when the popup closes.
                val listState = remember { LazyListState() }
                DisposableEffect(listState) {
                    wheelState = listState
                    onDispose { if (wheelState === listState) wheelState = null }
                }
                // **The enter and the exit, one run each** (2026-10-17): `AnimatedVisibility(visible = true)`
                // guarantees nothing on a first frame, so the idiom the drawer's dissolve already ships is
                // used instead — an `Animatable` held by this branch. A fresh open creates it at zero and
                // drives it to one; the close drives that same value back to zero and only then moves the
                // phase to `Closed`, so the window is held for the fade and released for real after it. The
                // effect is keyed on the phase, so the two directions never share a run, and leaving
                // composition with `Closed` discards the value, so a re-open always starts from zero again.
                val progress = remember { Animatable(0f) }
                LaunchedEffect(phase) {
                    when (phase) {
                        DropdownPhase.Open ->
                            progress.animateTo(1f, tween(POPUP_TRANSITION_DURATION_MS))
                        else -> {
                            progress.animateTo(0f, tween(POPUP_TRANSITION_DURATION_MS))
                            phase = DropdownPhase.Closed
                        }
                    }
                }
                val risePx = with(density) { POPUP_RISE_DP.dp.toPx() }
                Popup(
                    alignment = Alignment.TopStart,
                    offset = popupOffset,
                    // The outside click commits the band and closes — the scrim means what tapping the banded
                    // row does. Back does not arrive here: its dismissal is switched off below and the
                    // popup's own `BackHandler` closes without a write.
                    onDismissRequest = {
                        // The scrim writes the band it finds, as it always did — but only for an open popup,
                        // so a second tap inside the held fade neither writes nor restarts anything.
                        if (phase == DropdownPhase.Open) commitBand(listState)
                        close()
                    },
                    properties = PopupProperties(
                        focusable = true,
                        dismissOnBackPress = false,
                        usePlatformDefaultWidth = false
                    )
                ) {
                    // **Back closes with no write** (§5): every gesture writes and closes on its lift, so the
                    // band can only still hold the stored entry when back arrives, and writing it would be a
                    // redundant persistence write. This handler owns the key; the outside tap keeps
                    // `onDismissRequest`.
                    BackHandler { close() }
                    // The wheel owns the popup's scroll, so the surface does not add one of its own; and it
                    // takes no inset, so the section card fills the popup with no `uiBackground` ring.
                    PopupSurface(
                        maxHeight = boundDp.dp,
                        width = menuWidth,
                        scrollable = false,
                        contentPadding = 0.dp,
                        // Drawn only: an alpha and a translation on the popup's own surface, never a size, an
                        // offset or the popup's position — so the band stays the box's rectangle and the
                        // wheel's landing reads the same layout it always did.
                        modifier = Modifier.graphicsLayer {
                            alpha = progress.value
                            translationY = (1f - progress.value) * risePx
                        }
                    ) {
                        DropdownWheel(
                            labels = labels,
                            selectedIndex = selectedIndex,
                            // The rows answer only an open popup, so a tap inside the held fade is a
                            // no-op rather than a second write.
                            onChoose = { index ->
                                if (phase == DropdownPhase.Open) {
                                    options.getOrNull(index)?.let { onSelect(it.first) }
                                }
                            },
                            slotDp = slotDp,
                            listState = listState,
                            onDismiss = close,
                            textAlign = textAlign
                        )
                    }
                }
            }
        }
    }
}
