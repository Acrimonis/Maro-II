package ykws.android.maro.ui.components

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import ykws.android.maro.config.AppConfig

/**
 * The slot before the box has measured — the bars' own cell figure, replaced by the box's real height on the
 * first frame. The popup only opens from a laid-out box, so this is a guard, not a reading.
 */
private const val WHEEL_SLOT_FALLBACK_DP = 38f

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
 * begins on the box both opens the popup and scrolls the wheel through `scrollBy`, snapping to the nearest
 * slot on release ([wheelSnapTargetSlots]). The drag is taken unconditionally, so the page under that finger
 * does not scroll with it (D0); a tap still opens the popup, so the control keeps a non-drag path. The slot is
 * the box's own measured height, so the two surfaces cannot drift at any font scale.
 *
 * **The commit is a row tap, and an outside click cancels** (2026-10-05, revision 1): `DropdownWheel`'s row
 * tap writes through the wheel's `onChoose` and closes, and `onDismissRequest` only closes — it writes
 * nothing — so an outside tap or back is the cancel, and the drag only scrolls and snaps.
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
    var expanded by remember { mutableStateOf(false) }
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
    // **The slot is the box's own measured height** (§2), taken as dp; the bars' figure stands in until the
    // first measure, which always precedes an open.
    val slotDp = with(density) {
        if (anchorSize.height > 0) anchorSize.height.toDp().value else WHEEL_SLOT_FALLBACK_DP
    }
    val slotPx = with(density) { slotDp.dp.toPx() }
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
                onClick = { expanded = !expanded },
                textAlign = textAlign,
                // The behaviour turned into the width it means: the box's own answer for the longest entry, or
                // the row the caller gave. Either way the number is the box's business, never the caller's.
                modifier = when (sizing) {
                    DropdownSizing.Content -> Modifier.width(dropdownBoxWidth(labels))
                    DropdownSizing.Fill -> Modifier.fillMaxWidth()
                }.pointerInput(slotPx) {
                    // **The opening drag owns the popup's scroll** (§4). The pointer is the field's, so the
                    // field scrolls the hoisted state by hand; the library's snap belongs to a drag begun
                    // inside the open popup and cannot be borrowed here, which is what the release's own
                    // nearest-slot helper is for. The drag is taken unconditionally, so the page underneath
                    // does not scroll under that finger.
                    detectVerticalDragGestures(
                        onDragStart = { expanded = true },
                        onDragEnd = { snapWheelToSlot(wheelState, slotPx, scope) },
                        onDragCancel = { snapWheelToSlot(wheelState, slotPx, scope) },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            val wheel = wheelState
                            if (wheel != null) scope.launch { wheel.scrollBy(-dragAmount) }
                        }
                    )
                },
                onMeasured = { anchorSize = it }
            )
            if (expanded) {
                // A fresh state per open, so frame 0 is the rest frame the landing counts from; the holder
                // lets the detector above reach it, and clears it when the popup closes.
                val listState = remember { LazyListState() }
                DisposableEffect(listState) {
                    wheelState = listState
                    onDispose { if (wheelState === listState) wheelState = null }
                }
                Popup(
                    alignment = Alignment.TopStart,
                    offset = popupOffset,
                    // The outside click and back only close; nothing is written, so this is the cancel.
                    onDismissRequest = { expanded = false },
                    properties = PopupProperties(focusable = true, usePlatformDefaultWidth = false)
                ) {
                    // The wheel owns the popup's scroll, so the surface does not add one of its own; and it
                    // takes no inset, so the section card fills the popup with no `uiBackground` ring.
                    PopupSurface(
                        maxHeight = boundDp.dp,
                        width = menuWidth,
                        scrollable = false,
                        contentPadding = 0.dp
                    ) {
                        DropdownWheel(
                            labels = labels,
                            selectedIndex = selectedIndex,
                            onChoose = { index -> options.getOrNull(index)?.let { onSelect(it.first) } },
                            slotDp = slotDp,
                            listState = listState,
                            onDismiss = { expanded = false },
                            textAlign = textAlign
                        )
                    }
                }
            }
        }
    }
}

/**
 * **Release the opening drag onto a whole slot** — read how far the wheel has been scrolled and scroll the
 * difference to the nearest slot multiple ([wheelSnapTargetSlots]). The popup is a separate window, so the
 * library's own snap does not reach a scroll the field performed; this is that landing. Nothing moves when the
 * wheel is already on a slot, and nothing is scrolled when the popup has not composed its state yet.
 */
private fun snapWheelToSlot(state: LazyListState?, slotPx: Float, scope: CoroutineScope) {
    val wheel = state ?: return
    val offset = wheel.firstVisibleItemIndex * slotPx + wheel.firstVisibleItemScrollOffset
    val target = wheelSnapTargetSlots(offset, slotPx)
    val delta = target * slotPx - offset
    if (delta != 0f) scope.launch { wheel.scrollBy(delta) }
}
