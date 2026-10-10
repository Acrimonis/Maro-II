package ykws.android.maro.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ykws.android.maro.R
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.CustomSortField
import ykws.android.maro.data.model.FilterAxisSpec
import ykws.android.maro.data.model.FilterOptionSpec
import ykws.android.maro.data.model.ListAction
import ykws.android.maro.data.model.ListFilter
import ykws.android.maro.data.model.ListSortField
import ykws.android.maro.data.model.ListSortState
import ykws.android.maro.data.model.ListableItem
import ykws.android.maro.data.model.MultiActionSpec
import ykws.android.maro.ui.icons.FilterAlt
import ykws.android.maro.ui.icons.FilterList
import ykws.android.maro.ui.icons.Refresh
import ykws.android.maro.ui.map.ButtonColors
import ykws.android.maro.ui.map.dpToPx
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
// Saved scroll state for list-detail navigation
// ─────────────────────────────────────────────────────────────────────────────

/** Snapshot of a LazyListState for save/restore across composition cycles. */
data class SavedScrollState(
    val firstVisibleItemIndex: Int,
    val scrollOffset: Int
)

/** Vertical space a list popup leaves for the window chrome and the header it hangs below. */
private const val POPUP_VERTICAL_RESERVE_DP = 96f

/** Degenerate-window guard — never reached on a real device. */
private const val POPUP_MIN_HEIGHT_DP = 120f

/**
 * The tallest a list popup may grow: the window's own height less [POPUP_VERTICAL_RESERVE_DP].
 * A popup wraps its content below that bound and scrolls above it, so the tail of a long axis
 * set stays reachable in landscape instead of being clipped past the screen edge.
 */
internal fun popupMaxHeightDp(screenHeightDp: Int): Float =
    (screenHeightDp - POPUP_VERTICAL_RESERVE_DP).coerceAtLeast(POPUP_MIN_HEIGHT_DP)

// ─────────────────────────────────────────────────────────────────────────────
// Sort dropdown — field selector (no direction arrow, no pinned grouping)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SortControl(
    state: ListSortState,
    customFields: List<CustomSortField>,
    customSectionLabel: String,
    onStateChange: (ListSortState) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val isSortDefault = state.field == ListSortField.CREATED && state.customFieldKey == null && state.descending
    val sortAlpha = if (isSortDefault) ButtonColors.inactiveAlpha else ButtonColors.activeAlpha
    val maxPopupHeight = popupMaxHeightDp(LocalConfiguration.current.screenHeightDp).dp
    val sortArrow: @Composable () -> Unit = {
        Icon(
            imageVector = if (state.descending) Icons.Filled.ArrowDropDown else Icons.Filled.ArrowDropUp,
            contentDescription = null,
            tint = Color(AppConfig.uiTextPrimary),
            modifier = Modifier.size(28.dp)
        )
    }

    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = FilterList,
                contentDescription = stringResource(R.string.cd_sort),
                tint = ButtonColors.icon,
                // Vertical mirror: mirrored when ascending, upright when descending (big base at top).
                modifier = Modifier.size(ButtonColors.iconSizeDp.dp)
                    .scale(1f, if (state.descending) 1f else -1f)
                    .alpha(sortAlpha)
            )
        }
        if (expanded) {
            Popup(
                alignment = Alignment.TopEnd,
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(AppConfig.uiBackground),
                    shadowElevation = POPUP_SHADOW_DP.dp,
                    modifier = Modifier.width(POPUP_WIDTH_DP.dp).border(POPUP_BORDER_DP.dp, Color(AppConfig.uiAccent), RoundedCornerShape(POPUP_CORNER_DP.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .heightIn(max = maxPopupHeight)
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // General section
                        PopupSectionTitle(stringResource(R.string.filter_section_general))
                        PopupSectionCard {
                            ListSortField.entries.forEach { field ->
                                val isSelected = field == state.field && state.customFieldKey == null
                                PopupRow(
                                    text = stringResource(field.labelResId),
                                    selected = isSelected,
                                    onClick = {
                                        if (isSelected) onStateChange(state.copy(descending = !state.descending))
                                        else onStateChange(state.copy(field = field, customFieldKey = null, descending = (field == ListSortField.CREATED)))
                                        expanded = false
                                    },
                                    trailing = if (isSelected) sortArrow else null
                                )
                            }
                        }
                        // Custom fields card (only if non-empty)
                        if (customFields.isNotEmpty()) {
                            PopupSectionTitle(customSectionLabel)
                            PopupSectionCard {
                                customFields.forEach { cf ->
                                    val isSelected = cf.key == state.customFieldKey
                                    PopupRow(
                                        text = stringResource(cf.labelResId),
                                        selected = isSelected,
                                        onClick = {
                                            if (isSelected) onStateChange(state.copy(descending = !state.descending))
                                            else onStateChange(state.copy(field = ListSortField.CREATED, customFieldKey = cf.key, descending = cf.descendingDefault))
                                            expanded = false
                                        },
                                        trailing = if (isSelected) sortArrow else null
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Filter dropdown — combined filter menu with sections
// ─────────────────────────────────────────────────────────────────────────────

/**
 * **The filter popup, and what dismisses it** (2026-09-29): a filter is a set of groups and the user works
 * through them in one visit, so a row tap writes its choice **live** and leaves the popup open — the list
 * behind answers as the taps land — and the popup itself is dismissed by an **outside tap or by back**, which
 * is the `Popup`'s own `onDismissRequest`. It holds no draft state and knows nothing of how many groups it
 * was handed: one row, one write, the popup staying put.
 */
@Composable
internal fun FilterControl(
    filterState: ListFilter,
    filterAxes: List<FilterAxisSpec>,
    onFilterChange: (ListFilter) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val hasActiveFilter = filterState.axes.isNotEmpty()
    val maxPopupHeight = popupMaxHeightDp(LocalConfiguration.current.screenHeightDp).dp

    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = FilterAlt,
                contentDescription = stringResource(R.string.cd_filter),
                tint = ButtonColors.icon,
                modifier = Modifier.size(ButtonColors.iconSizeDp.dp)
                    .alpha(if (hasActiveFilter) ButtonColors.activeAlpha else ButtonColors.inactiveAlpha)
            )
        }
        if (expanded) {
            Popup(
                alignment = Alignment.TopEnd,
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(AppConfig.uiBackground),
                    shadowElevation = POPUP_SHADOW_DP.dp,
                    modifier = Modifier.width(POPUP_WIDTH_DP.dp).border(POPUP_BORDER_DP.dp, Color(AppConfig.uiAccent), RoundedCornerShape(POPUP_CORNER_DP.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .heightIn(max = maxPopupHeight)
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        filterAxes.forEach { axis ->
                            val gatingValue = axis.dependsOn?.let { filterState.axes[it] }
                            val isDisabled = gatingValue != null && axis.dependsOnValues != null &&
                                gatingValue in axis.dependsOnValues
                            val currentValue = filterState.axes[axis.key] ?: axis.options.firstOrNull { it.isDefault }?.value ?: "ALL"
                            
                            PopupSectionTitle(stringResource(axis.labelResId))
                            PopupSectionCard {
                                axis.options.forEach { option ->
                                    val isSelected = option.value == currentValue
                                    PopupRow(
                                        text = stringResource(option.labelResId),
                                        selected = isSelected,
                                        enabled = !isDisabled,
                                        onClick = {
                                            val newAxes = if (option.isDefault) filterState.axes - axis.key else filterState.axes + (axis.key to option.value)
                                            // A row tap writes the choice and nothing else: the popup stays open,
                                            // and the outside tap or the back press is what dismisses it.
                                            onFilterChange(ListFilter(newAxes))
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Swipe-to-delete state machine
// ─────────────────────────────────────────────────────────────────────────────

private enum class SwipeState { CARD, SNACKBAR, DELETED }

private const val SNACKBAR_BG_ALPHA = 0.0765f
private const val DRAG_THRESHOLD = 0.30f
private const val ANIM_DURATION_MS = 200
private const val SNACK_ANIM_MS = 250
private const val PIN_HOLD_MS = 1000L

// The pin hold's gap in dp — the reveal's own arithmetic mirrored: the 16 dp leading inset plus
// the 24 dp glyph plus 16 dp of clearance. A fixed space, so the flash reads the same on every card.
private const val PIN_REVEAL_GAP_DP = 56f

/**
 * One pending deletion, in the **one** set the shell and a list surface share (2026-10-10): [key] wears
 * the shell's `kind:id` spelling, and [hideFromMap] says whether the item leaves the map while it waits
 * — true for a card's deferred delete, false for a list row's swipe, whose item the map keeps drawing.
 */
internal data class PendingDeletion(val key: String, val hideFromMap: Boolean)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun <T : ListableItem> SwipeableItemCard(
    item: T,
    accentColor: Color = Color.Unspecified,
    cardContent: @Composable (T) -> Unit,
    onSoftDelete: (T) -> Unit,
    onUndoDelete: (T) -> Unit,
    onPermanentDelete: (T) -> Unit,
    onTogglePin: (T) -> Unit,
    isMultiSelectMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelection: () -> Unit = {}
) {
    var state by remember(item.id) { mutableStateOf(SwipeState.CARD) }
    val scope = rememberCoroutineScope()
    var cardWidthPx by remember { mutableFloatStateOf(0f) }
    var cardDragOffset by remember { mutableFloatStateOf(0f) }
    val cardSwipeOffset by animateFloatAsState(cardDragOffset, tween(ANIM_DURATION_MS))
    var snackWidthPx by remember { mutableFloatStateOf(0f) }
    var snackDragOffset by remember { mutableFloatStateOf(0f) }
    val snackSwipeOffset by animateFloatAsState(snackDragOffset, tween(ANIM_DURATION_MS))
    var cardDismissed by remember { mutableStateOf(false) }
    // The hold's gap, a fixed 56 dp through the project's own density-explicit dp→px helper, so the
    // flash reads the same on every card rather than following whatever height it happens to have.
    val pinRevealGapPx = dpToPx(PIN_REVEAL_GAP_DP, LocalDensity.current.density)
    // The pin reveal belongs to the rightward travel alone: it is drawn only while the drag stands
    // to the right of rest, so a leftward delete can never uncover it as the card leaves the slot.
    val pinRevealAlpha = if (cardSwipeOffset > 0f) 1f else 0f
    // The detector's block is re-launched only when the item's id changes, so the `item` and the
    // callback it closes over would keep the values captured when it was created — every swipe
    // would recompute the same target. Both are read through the updated state, so the target is
    // resolved at the moment of the fire.
    val currentItem by rememberUpdatedState(item)
    val currentOnTogglePin by rememberUpdatedState(onTogglePin)

    Column(modifier = Modifier.animateContentSize(tween(300))) {
        AnimatedVisibility(
            visible = state == SwipeState.CARD,
            enter = slideInHorizontally(spring(dampingRatio = 1.0f, stiffness = 350f)) { it },
            exit = slideOutHorizontally(tween(150)) { it } + fadeOut(tween(150))
        ) {
            Box(
                modifier = Modifier.fillMaxWidth()
                    .onSizeChanged { cardWidthPx = it.width.toFloat() }
            ) {
                // Layer 0: the pin reveal — beneath the card, anchored at the card's own leading
                // edge and uncovered as the card travels right. The glyph is the card's own pair,
                // a pinned item offering the unpin door, on the card's own tint; no word joins it,
                // so neither locale grows a string.
                Box(
                    modifier = Modifier.matchParentSize().alpha(pinRevealAlpha),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Icon(
                        imageVector = if (item.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        // Decorative: the card's own pin button already names the action, so the
                        // reveal contributes no second accessibility node.
                        contentDescription = null,
                        tint = ButtonColors.icon,
                        modifier = Modifier.padding(start = 16.dp).size(24.dp)
                    )
                }
                // Layers 1–3: the card itself, on the signed drag offset
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .offset { IntOffset(cardSwipeOffset.roundToInt(), 0) }
                        .then(
                            if (state == SwipeState.CARD && !cardDismissed && !isMultiSelectMode)
                                Modifier.pointerInput(item.id) {
                                    // Per-gesture feedback state, scoped to this detector: the flag
                                    // makes a held drag toggle once and not once a frame, and the
                                    // job is what a new drag cancels to interrupt a running hold.
                                    var pinFiredThisGesture = false
                                    var holdJob: Job? = null
                                    detectHorizontalDragGestures(
                                        onDragStart = {
                                            holdJob?.cancel()
                                            holdJob = null
                                        },
                                        onDragCancel = {
                                            // A gesture broken by a second pointer leaves the offset
                                            // wherever it stood; return the card and its reveal to rest.
                                            cardDragOffset = 0f
                                        },
                                        onDragEnd = {
                                            when {
                                                // The delete lifecycle outranks the hold: a gesture
                                                // that fired the pin and then resolved Delete deletes,
                                                // the left swipe keeping precedence.
                                                swipeOutcome(cardDragOffset, cardWidthPx, DRAG_THRESHOLD) == SwipeOutcome.Delete ->
                                                    scope.launch { cardDragOffset = -cardWidthPx; delay(220); cardDismissed = true; state = SwipeState.SNACKBAR; onSoftDelete(item) }
                                                // The pin already committed mid-gesture; the release
                                                // owns only the hold — out to the fixed reveal gap, a
                                                // full second there, then home, driven from wherever
                                                // the offset stands.
                                                pinFiredThisGesture -> holdJob = scope.launch {
                                                    cardDragOffset = pinHoldOffset(pinRevealGapPx, cardWidthPx)
                                                    delay(ANIM_DURATION_MS.toLong() + PIN_HOLD_MS)
                                                    cardDragOffset = 0f
                                                }
                                                else -> cardDragOffset = 0f
                                            }
                                        }
                                    ) { _, dragAmount ->
                                        cardDragOffset = swipeClampedOffset(cardDragOffset + dragAmount, cardWidthPx)
                                        // The toggle fires the moment the offset crosses the
                                        // threshold, not on release, so the reveal's glyph and the
                                        // card's own pin button both read the new flag at once. The
                                        // flag re-arms when the offset falls back inside the line, so
                                        // a card held past the threshold cannot re-fire on the next
                                        // drag's first frame.
                                        when (swipeOutcome(cardDragOffset, cardWidthPx, DRAG_THRESHOLD)) {
                                            SwipeOutcome.TogglePin -> if (!pinFiredThisGesture) {
                                                pinFiredThisGesture = true
                                                currentOnTogglePin(currentItem)
                                            }
                                            SwipeOutcome.None -> pinFiredThisGesture = false
                                            else -> Unit
                                        }
                                    }
                                }
                            else Modifier
                        )
                ) {
                    // Layer 1: consumer's card content
                    cardContent(item)
                    // Layer 2: the multiselect tonal shift alone (only when selected) — the check mark
                    // itself now lives on the title line's type glyph, which the consumer draws.
                    if (isSelected) {
                        Box(
                            modifier = Modifier.matchParentSize()
                                .background(Color.White.copy(alpha = 0.15f))
                        )
                    }
                    // Layer 3: tap interceptor overlay — only in multiselect mode
                    if (isMultiSelectMode) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { onToggleSelection() }
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = state == SwipeState.SNACKBAR,
            enter = slideInHorizontally(tween(SNACK_ANIM_MS)) { it } + fadeIn(tween(150)),
            exit = slideOutHorizontally(tween(SNACK_ANIM_MS)) { it } + fadeOut(tween(150))
        ) {
            Box(
                modifier = Modifier.fillMaxWidth()
                    .offset { IntOffset(snackSwipeOffset.roundToInt(), 0) }
                    .onSizeChanged { snackWidthPx = it.width.toFloat() }
                    .pointerInput(item.id) {
                        detectHorizontalDragGestures(onDragEnd = {
                            val threshold = snackWidthPx * DRAG_THRESHOLD
                            if (snackDragOffset < -threshold) { scope.launch { snackDragOffset = -snackWidthPx; delay(220); onPermanentDelete(item); state = SwipeState.DELETED } }
                            else snackDragOffset = 0f
                        }) { _, dragAmount -> snackDragOffset = (snackDragOffset + dragAmount).coerceIn(-snackWidthPx, 0f) }
                    }
            ) { SnackbarSlot(item.title, onUndo = { scope.launch { state = SwipeState.CARD; cardDismissed = false; cardDragOffset = 0f; snackDragOffset = 0f; onUndoDelete(item) } }) }
        }
    }
}

@Composable
private fun SnackbarSlot(name: String, onUndo: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp, max = 96.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(AppConfig.uiCardBackground).copy(alpha = SNACKBAR_BG_ALPHA))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(stringResource(R.string.snackbar_deleted, name), color = Color(AppConfig.uiTextPrimary), fontSize = 14.sp, maxLines = 3, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = onUndo) { Text(stringResource(R.string.action_undo), color = Color(AppConfig.uiAccent), fontWeight = FontWeight.Bold, fontSize = 14.sp) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Main scaffold
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun <T : ListableItem> ListOverlayScaffold(
    items: List<T>,
    title: String,
    sectionLabel: String,
    sortState: ListSortState,
    onSortStateChange: (ListSortState) -> Unit,
    customSortFields: List<CustomSortField> = emptyList(),
    customSortLabelResId: Int = R.string.sort_group_custom,
    filterAxes: List<FilterAxisSpec> = emptyList(),
    filterState: ListFilter = ListFilter(),
    onFilterChange: (ListFilter) -> Unit = {},
    onReset: () -> Unit = {},
    filterLinked: Boolean = true,
    onToggleLink: () -> Unit = {},
    accentColors: (List<T>) -> Map<String, Color>,
    cardContent: @Composable (
        item: T,
        isSelected: Boolean,
        onSelect: (() -> Unit)?,
        onLongPress: (() -> Unit)?
    ) -> Unit,
    liveCardContent: @Composable (T) -> Unit = {},
    emptyState: @Composable () -> Unit = {},
    onAction: (ListAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    multiActions: List<MultiActionSpec> = emptyList(),
    headerActions: @Composable () -> Unit = {},
    lazyListState: LazyListState = rememberLazyListState(),
    restoredScrollState: SavedScrollState? = null,
    /**
     * The pending-deletion set the **consumer owns**, so the shell's deferred card deletes and this
     * surface's own share one instance (2026-10-10). Null falls back to a local set, which keeps the
     * scaffold usable on its own — a consumer passing nothing leaves its inline Undo inert and changes
     * nothing else.
     */
    sharedPending: MutableList<PendingDeletion>? = null,
    /**
     * The key prefix this surface's records take in the shared set (`"t:"` / `"m:"`) — the one boundary
     * at which a kind-agnostic scaffold meets the shell's spelling. Blank keeps the ids bare.
     */
    pendingKeyPrefix: String = ""
) {
    val pendingDeletes = sharedPending ?: remember { mutableStateListOf<PendingDeletion>() }

    // ── Restore scroll position on reopen ──────────────────────────────
    LaunchedEffect(restoredScrollState) {
        restoredScrollState?.let { state ->
            lazyListState.scrollToItem(state.firstVisibleItemIndex, state.scrollOffset)
        }
    }

    // ── Multiselect state ──────────────────────────────────────────────
    var isMultiSelectMode by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<String>() }
    val nonLiveCount = items.count { !it.isLive }
    val selectedCount = selectedIds.size
    val allSelected = selectedCount == nonLiveCount && nonLiveCount > 0

    // Enter multiselect: commit pending deletes first
    fun enterMultiselect(id: String) {
        if (isMultiSelectMode) return
        // Commit any pending soft-deletes before entering multiselect
        pendingDeletes.forEach { entry -> onAction(ListAction.PermanentDelete(entry.key)) }
        pendingDeletes.clear()
        isMultiSelectMode = true
        selectedIds.add(id)
    }

    fun exitMultiselect() {
        isMultiSelectMode = false
        selectedIds.clear()
    }

    fun toggleSelection(id: String) {
        if (selectedIds.contains(id)) {
            selectedIds.remove(id)
            if (selectedIds.isEmpty()) exitMultiselect()
        } else {
            selectedIds.add(id)
        }
    }

    fun selectAll() {
        selectedIds.clear()
        selectedIds.addAll(items.filter { !it.isLive }.map { it.id })
    }

    fun invertSelection() {
        val nonLiveIds = items.filter { !it.isLive }.map { it.id }
        val inverted = nonLiveIds.filterNot { selectedIds.contains(it) }
        selectedIds.clear()
        if (inverted.isEmpty()) exitMultiselect() else selectedIds.addAll(inverted)
    }

    // ── BackHandler ────────────────────────────────────────────────────
    BackHandler {
        if (isMultiSelectMode) {
            exitMultiselect()
        } else {
            pendingDeletes.forEach { entry -> onAction(ListAction.PermanentDelete(entry.key)) }
            pendingDeletes.clear()
            onDismiss()
        }
    }

    // ── Commit pending deletes on disposal (covers scrim dismiss) ──────
    DisposableEffect(Unit) {
        onDispose {
            if (pendingDeletes.isNotEmpty()) {
                pendingDeletes.forEach { entry -> onAction(ListAction.PermanentDelete(entry.key)) }
                pendingDeletes.clear()
            }
        }
    }

    // ── Item reconciliation: drop stale selected IDs ───────────────────
    LaunchedEffect(items) {
        val currentIds = items.map { it.id }.toSet()
        selectedIds.removeAll { it !in currentIds }
        if (selectedIds.isEmpty() && isMultiSelectMode) {
            exitMultiselect()
        }
    }

    // ── Auto-exit when no non-live items ───────────────────────────────
    LaunchedEffect(nonLiveCount) {
        if (nonLiveCount == 0 && isMultiSelectMode) {
            exitMultiselect()
        }
    }

    val colorMap = remember(items) { accentColors(items) }
    val sortedItems = remember(items) { items.sortedByDescending { it.isLive } }
    val shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
    val hasActiveFilter = filterState.axes.isNotEmpty()

    // ── Confirmation dialogs (hoisted to the overlay ladder) ──────────────
    // A full-surface overlay cannot live inside this clipped list drawer, so multi-action
    // confirmations are raised through `LocalConfirmDialogHost` and painted by the screen above the
    // drawers; the drawer stays open behind them.
    val confirmDialogHost = LocalConfirmDialogHost.current
    val batchDeleteTitle = stringResource(R.string.confirm_batch_delete_title)
    val deleteActionLabel = stringResource(R.string.action_delete)
    val cancelActionLabel = stringResource(R.string.action_cancel)

    Box(
        modifier = modifier.fillMaxSize().clip(shape)
            .background(Color(AppConfig.uiBackground))
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Header ─────────────────────────────────────────────────
            if (isMultiSelectMode) {
                // Multiselect header: Close (X) + "N selected" + invert and select-all text chips
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { exitMultiselect() },
                        modifier = Modifier.size(32.dp).clip(CircleShape).background(Color(AppConfig.uiSwitchTrackInactive))
                    ) {
                        Icon(Icons.Filled.Close, stringResource(R.string.cd_close_multiselect), tint = Color(AppConfig.uiTextPrimary), modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Text(
                        stringResource(R.string.multiselect_count, selectedCount),
                        color = Color(AppConfig.uiTextPrimary),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    if (nonLiveCount > 0) {
                        // Invert — one action, two words: inverting a full selection is a clear.
                        TextButton(onClick = { invertSelection() }) {
                            Text(
                                stringResource(if (allSelected) R.string.multiselect_clear else R.string.multiselect_invert),
                                color = Color(AppConfig.uiAccent),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        // Select all — a no-op once every non-live item is picked, so it dims then.
                        TextButton(
                            onClick = { selectAll() },
                            enabled = !allSelected
                        ) {
                            Text(
                                stringResource(R.string.multiselect_select_all),
                                color = Color(AppConfig.uiAccent),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.alpha(if (allSelected) 0.25f else 1f)
                            )
                        }
                    }
                }
            } else {
                // Normal header: Back + Title
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { pendingDeletes.forEach { entry -> onAction(ListAction.PermanentDelete(entry.key)) }; pendingDeletes.clear(); onDismiss() },
                        modifier = Modifier.size(32.dp).clip(CircleShape).background(Color(AppConfig.uiSwitchTrackInactive))
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.settings_back), tint = Color(AppConfig.uiTextPrimary), modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Text(title, color = Color(AppConfig.uiTextPrimary), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (!isMultiSelectMode) Spacer(Modifier.height(16.dp))

            // ── Section label + controls row (hidden in multiselect) ───
            if (!isMultiSelectMode) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(sectionLabel, color = Color(AppConfig.uiAccent), fontSize = 17.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        headerActions()
                        // Link toggle (list referential vs map referential), left of the filter icon
                        if (filterAxes.isNotEmpty()) {
                            IconButton(
                                onClick = onToggleLink,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = if (filterLinked) ykws.android.maro.ui.icons.Link else ykws.android.maro.ui.icons.LinkOff,
                                    contentDescription = null,
                                    tint = ButtonColors.icon,
                                    modifier = Modifier.size(ButtonColors.iconSizeDp.dp)
                                )
                            }
                        }
                        // Filter
                        if (filterAxes.isNotEmpty()) {
                            FilterControl(filterState = filterState, filterAxes = filterAxes, onFilterChange = onFilterChange)
                        }
                        // Sort
                        SortControl(state = sortState, customFields = customSortFields, customSectionLabel = stringResource(customSortLabelResId), onStateChange = onSortStateChange)
                        // Reset (direction is toggled in the sort menu on the selected field)
                        val isSortDefault = sortState.field == ListSortField.CREATED && sortState.customFieldKey == null && sortState.descending
                        // Reset
                        val hasActive = hasActiveFilter || !isSortDefault
                        IconButton(
                            onClick = onReset,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Refresh,
                                contentDescription = stringResource(R.string.cd_reset),
                                tint = ButtonColors.icon,
                                modifier = Modifier.size(ButtonColors.iconSizeDp.dp)
                                    .alpha(if (hasActive) ButtonColors.activeAlpha else ButtonColors.inactiveAlpha)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            } else {
                Spacer(Modifier.height(4.dp))
            }

            // ── Action bar (multiselect mode, in-flow between header and list) ──
            AnimatedVisibility(
                visible = isMultiSelectMode && multiActions.isNotEmpty(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(AppConfig.uiBackground))
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        multiActions.forEach { spec ->
                            val isActionEnabled = spec.enabled(selectedIds.toSet())
                            val tint = when {
                                spec.isDestructive -> Color(AppConfig.uiDashboardZoneDanger)
                                else -> ButtonColors.icon
                            }
                            var showDropdown by remember { mutableStateOf(false) }

                            Box {
                                TextButton(
                                    onClick = {
                                        if (isActionEnabled) {
                                            val ids = selectedIds.toSet()
                                            val host = confirmDialogHost
                                            when {
                                                spec.subActions.isNotEmpty() -> showDropdown = true
                                                spec.confirmRequest != null -> {
                                                    val factory = spec.confirmRequest
                                                    if (host != null) {
                                                        host.show(
                                                            factory.invoke(
                                                                ids,
                                                                { host.dismiss() },
                                                                { host.dismiss(); exitMultiselect() }
                                                            )
                                                        )
                                                    }
                                                }
                                                spec.confirmMessage != null -> {
                                                    val message = spec.confirmMessage
                                                    if (host != null) {
                                                        host.show(
                                                            ConfirmRequest(
                                                                title = batchDeleteTitle,
                                                                message = message,
                                                                actions = listOf(
                                                                    ConfirmAction(
                                                                        deleteActionLabel,
                                                                        ConfirmActionRole.DANGER
                                                                    ) {
                                                                        spec.action(ids)
                                                                        host.dismiss()
                                                                        exitMultiselect()
                                                                    },
                                                                    ConfirmAction(
                                                                        cancelActionLabel,
                                                                        ConfirmActionRole.SECONDARY
                                                                    ) {
                                                                        host.dismiss()
                                                                    }
                                                                )
                                                            )
                                                        )
                                                    }
                                                }
                                                else -> {
                                                    spec.action(ids)
                                                    exitMultiselect()
                                                }
                                            }
                                        }
                                    },
                                    enabled = isActionEnabled
                                ) {
                                    Icon(
                                        imageVector = spec.icon,
                                        contentDescription = spec.label,
                                        tint = if (isActionEnabled) tint else tint.copy(alpha = 0.25f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        spec.label,
                                        color = if (isActionEnabled) tint else tint.copy(alpha = 0.25f),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                // Sub-actions dropdown
                                if (spec.subActions.isNotEmpty()) {
                                    DropdownMenu(
                                        expanded = showDropdown,
                                        onDismissRequest = { showDropdown = false }
                                    ) {
                                        spec.subActions.forEach { sub ->
                                            DropdownMenuItem(
                                                text = { Text(sub.label) },
                                                onClick = {
                                                    sub.action(selectedIds.toSet())
                                                    showDropdown = false
                                                    exitMultiselect()
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                        }
                    }
                    HorizontalDivider(thickness = 0.5.dp, color = Color(AppConfig.uiDividerColor))
                }
            }

            if (sortedItems.isEmpty()) {
                if (hasActiveFilter && !isMultiSelectMode) {
                    // Filter active + empty → show "No items match filters" + clear button
                    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.filter_no_match), color = Color(AppConfig.uiTextMuted), fontSize = 18.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(16.dp))
                            TextButton(onClick = onReset) {
                                Text(stringResource(R.string.filter_clear), color = Color(AppConfig.uiAccent), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                } else {
                    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) { emptyState() }
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 24.dp),
                    state = lazyListState,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(sortedItems, key = { it.id }) { item ->
                        if (item.isLive) {
                            key(item.id) { liveCardContent(item) }
                        } else {
                            key(item.id) {
                                val isSelected = selectedIds.contains(item.id)
                                val cardShape = RoundedCornerShape(12.dp)
                                Box(
                                    modifier = Modifier
                                        .clip(cardShape)
                                        .then(
                                            // The selection ring at 2 dp — a step up from the 1 dp
                                            // baseline, so the picked card's outline reads at a glance.
                                            if (isSelected) Modifier.border(2.dp, Color(AppConfig.uiAccent), cardShape)
                                            else Modifier
                                        )
                                ) {
                                    SwipeableItemCard(
                                        item = item,
                                        accentColor = colorMap[item.id] ?: Color.Unspecified,
                                        cardContent = {
                                            cardContent(
                                                it,
                                                isSelected,
                                                if (multiActions.isNotEmpty() && !isMultiSelectMode) { { enterMultiselect(item.id) } } else null,
                                                if (multiActions.isNotEmpty() && !isMultiSelectMode) { { enterMultiselect(item.id) } } else null
                                            )
                                        },
                                        onSoftDelete = {
                                            // A swipe's record enters with hideFromMap false: its row leaves the
                                            // list, and the map keeps drawing the item until the commit.
                                            pendingDeletes.add(PendingDeletion(pendingKeyPrefix + it.id, hideFromMap = false))
                                            onAction(ListAction.SoftDelete(it.id, it.title))
                                        },
                                        onUndoDelete = {
                                            pendingDeletes.removeAll { entry -> entry.key == pendingKeyPrefix + it.id }
                                            onAction(ListAction.UndoDelete(it.id))
                                        },
                                        onPermanentDelete = {
                                            pendingDeletes.removeAll { entry -> entry.key == pendingKeyPrefix + it.id }
                                            onAction(ListAction.PermanentDelete(pendingKeyPrefix + it.id))
                                        },
                                        onTogglePin = { onAction(ListAction.TogglePin(it.id, !it.isPinned)) },
                                        isMultiSelectMode = isMultiSelectMode,
                                        isSelected = isSelected,
                                        onToggleSelection = { toggleSelection(item.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

    }
}
