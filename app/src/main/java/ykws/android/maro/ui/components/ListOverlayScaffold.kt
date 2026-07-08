package ykws.android.maro.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
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
import ykws.android.maro.ui.icons.FilterAlt
import ykws.android.maro.ui.icons.FilterList
import ykws.android.maro.ui.icons.Refresh
import ykws.android.maro.ui.map.ButtonColors
import kotlin.math.roundToInt

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

    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = FilterList,
                contentDescription = stringResource(R.string.cd_sort),
                tint = ButtonColors.icon,
                modifier = Modifier.size(ButtonColors.iconSizeDp.dp)
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
                    color = Color(AppConfig.uiSettingsBackground),
                    shadowElevation = 8.dp,
                    modifier = Modifier.width(240.dp).border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        // General section
                        Text(stringResource(R.string.filter_section_general),
                            color = Color(AppConfig.uiDashboardTextMuted),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
                        Surface(shape = RoundedCornerShape(12.dp), color = Color(AppConfig.uiCardBackground)) {
                            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                ListSortField.entries.forEach { field ->
                                    val isSelected = field == state.field && state.customFieldKey == null
                                    Row(
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            if (isSelected) onStateChange(state.copy(descending = !state.descending))
                                            else onStateChange(state.copy(field = field, customFieldKey = null))
                                            expanded = false
                                        }.padding(horizontal = 16.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) {
                                            if (isSelected) Text("\u2713", color = Color(AppConfig.uiSettingsAccent), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Text(stringResource(field.labelResId), color = Color(AppConfig.uiSettingsTextPrimary), fontSize = 15.sp, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium)
                                    }
                                }
                            }
                        }
                        // Custom fields card (only if non-empty)
                        if (customFields.isNotEmpty()) {
                            Text(customSectionLabel,
                                color = Color(AppConfig.uiDashboardTextMuted),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
                            Surface(shape = RoundedCornerShape(12.dp), color = Color(AppConfig.uiCardBackground)) {
                                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                    customFields.forEach { cf ->
                                        val isSelected = cf.key == state.customFieldKey
                                        Row(
                                            modifier = Modifier.fillMaxWidth().clickable {
                                                if (isSelected) onStateChange(state.copy(descending = !state.descending))
                                                else onStateChange(state.copy(field = ListSortField.CREATED, customFieldKey = cf.key))
                                                expanded = false
                                            }.padding(horizontal = 16.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) {
                                                if (isSelected) Text("\u2713", color = Color(AppConfig.uiSettingsAccent), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                            }
                                            Spacer(Modifier.width(8.dp))
                                            Text(stringResource(cf.labelResId), color = Color(AppConfig.uiSettingsTextPrimary), fontSize = 15.sp, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium)
                                        }
                                    }
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

@Composable
internal fun FilterControl(
    filterState: ListFilter,
    filterAxes: List<FilterAxisSpec>,
    onFilterChange: (ListFilter) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val hasActiveFilter = filterState.axes.isNotEmpty()

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
                    color = Color(AppConfig.uiSettingsBackground),
                    shadowElevation = 8.dp,
                    modifier = Modifier.width(240.dp).border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        filterAxes.forEach { axis ->
                            val isDisabled = axis.dependsOn != null && axis.dependsOnValue != null &&
                                filterState.axes[axis.dependsOn] == axis.dependsOnValue
                            val currentValue = filterState.axes[axis.key] ?: axis.options.firstOrNull { it.isDefault }?.value ?: "ALL"
                            
                            Text(axis.label,
                                color = Color(AppConfig.uiDashboardTextMuted),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
                            Surface(shape = RoundedCornerShape(12.dp), color = Color(AppConfig.uiCardBackground)) {
                                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                    axis.options.forEach { option ->
                                        val isSelected = option.value == currentValue
                                        Row(
                                            modifier = Modifier.fillMaxWidth().clickable(enabled = !isDisabled) {
                                                val newAxes = if (option.isDefault) filterState.axes - axis.key else filterState.axes + (axis.key to option.value)
                                                onFilterChange(ListFilter(newAxes))
                                            }.padding(horizontal = 16.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) {
                                                if (isSelected) Text("\u2713",
                                                    color = if (isDisabled) Color(AppConfig.uiSettingsTextMuted).copy(alpha = 0.4f) else Color(AppConfig.uiSettingsAccent),
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Spacer(Modifier.width(8.dp))
                                            Text(option.label,
                                                color = if (isDisabled) Color(AppConfig.uiSettingsTextMuted).copy(alpha = 0.4f) else Color(AppConfig.uiSettingsTextPrimary),
                                                fontSize = 15.sp,
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
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

@Composable
private fun <T : ListableItem> SwipeableItemCard(
    item: T,
    accentColor: Color,
    cardContent: @Composable (T) -> Unit,
    onSoftDelete: (T) -> Unit,
    onUndoDelete: (T) -> Unit,
    onPermanentDelete: (T) -> Unit
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

    Column(modifier = Modifier.animateContentSize(tween(300))) {
        AnimatedVisibility(
            visible = state == SwipeState.CARD,
            enter = slideInHorizontally(spring(dampingRatio = 1.0f, stiffness = 350f)) { it },
            exit = slideOutHorizontally(tween(150)) { it } + fadeOut(tween(150))
        ) {
            Box(
                modifier = Modifier.fillMaxWidth()
                    .offset { IntOffset(cardSwipeOffset.roundToInt(), 0) }
                    .onSizeChanged { cardWidthPx = it.width.toFloat() }
                    .then(if (state == SwipeState.CARD && !cardDismissed) Modifier.pointerInput(item.id) {
                        detectHorizontalDragGestures(onDragEnd = {
                            val threshold = cardWidthPx * DRAG_THRESHOLD
                            if (cardDragOffset < -threshold) { scope.launch { cardDragOffset = -cardWidthPx; delay(220); cardDismissed = true; state = SwipeState.SNACKBAR; onSoftDelete(item) } }
                            else cardDragOffset = 0f
                        }) { _, dragAmount -> cardDragOffset = (cardDragOffset + dragAmount).coerceIn(-cardWidthPx, 0f) }
                    } else Modifier)
            ) { cardContent(item) }
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
        Text(stringResource(R.string.snackbar_deleted, name), color = Color(AppConfig.uiSettingsTextPrimary), fontSize = 14.sp, maxLines = 3, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = onUndo) { Text(stringResource(R.string.action_undo), color = Color(AppConfig.uiSettingsAccent), fontWeight = FontWeight.Bold, fontSize = 14.sp) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Main scaffold
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun <T : ListableItem> ListOverlayScaffold(
    items: List<T>,
    title: String,
    sectionLabel: String,
    sortState: ListSortState,
    onSortStateChange: (ListSortState) -> Unit,
    customSortFields: List<CustomSortField> = emptyList(),
    customSortLabel: String = "Custom",
    filterAxes: List<FilterAxisSpec> = emptyList(),
    filterState: ListFilter = ListFilter(),
    onFilterChange: (ListFilter) -> Unit = {},
    onReset: () -> Unit = {},
    accentColors: (List<T>) -> Map<String, Color>,
    cardContent: @Composable (T) -> Unit,
    liveCardContent: @Composable (T) -> Unit = {},
    emptyState: @Composable () -> Unit = {},
    onAction: (ListAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pendingDeletes = remember { mutableStateListOf<String>() }

    BackHandler {
        pendingDeletes.forEach { id -> onAction(ListAction.PermanentDelete(id)) }
        pendingDeletes.clear()
        onDismiss()
    }

    val colorMap = remember(items, accentColors) { accentColors(items) }
    val sortedItems = remember(items) { items.sortedByDescending { it.isLive } }
    val shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
    val hasActiveFilter = filterState.axes.isNotEmpty()

    Box(
        modifier = modifier.fillMaxSize().clip(shape)
            .background(Color(AppConfig.uiSettingsBackground))
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { pendingDeletes.forEach { id -> onAction(ListAction.PermanentDelete(id)) }; pendingDeletes.clear(); onDismiss() },
                    modifier = Modifier.size(32.dp).clip(CircleShape).background(Color(AppConfig.uiSettingsSwitchTrackInactive))
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color(AppConfig.uiSettingsTextPrimary), modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(16.dp))
                Text(title, color = Color(AppConfig.uiSettingsTextPrimary), fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(16.dp))

            // Section label + controls row
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(sectionLabel, color = Color(AppConfig.uiSettingsAccent), fontSize = 17.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Filter
                    if (filterAxes.isNotEmpty()) {
                        FilterControl(filterState = filterState, filterAxes = filterAxes, onFilterChange = onFilterChange)
                    }
                    // Sort
                    SortControl(state = sortState, customFields = customSortFields, customSectionLabel = customSortLabel, onStateChange = onSortStateChange)
                    // Direction toggle
                    val isSortDefault = sortState.field == ListSortField.CREATED && sortState.customFieldKey == null && sortState.descending
                    val sortAlpha = if (isSortDefault) ButtonColors.inactiveAlpha else ButtonColors.activeAlpha
                    IconButton(
                        onClick = { onSortStateChange(sortState.copy(descending = !sortState.descending)) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = if (sortState.descending) Icons.Filled.ArrowDropDown else Icons.Filled.ArrowDropUp,
                            contentDescription = if (sortState.descending) stringResource(R.string.cd_descending) else stringResource(R.string.cd_ascending),
                            tint = ButtonColors.icon,
                            modifier = Modifier.size(ButtonColors.iconSizeDp.dp)
                                .alpha(sortAlpha)
                        )
                    }
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

            if (sortedItems.isEmpty()) {
                if (hasActiveFilter) {
                    // Filter active + empty → show "No items match filters" + clear button
                    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.filter_no_match), color = Color(AppConfig.uiSettingsTextMuted), fontSize = 18.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(16.dp))
                            TextButton(onClick = onReset) {
                                Text(stringResource(R.string.filter_clear), color = Color(AppConfig.uiSettingsAccent), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                } else {
                    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) { emptyState() }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(sortedItems, key = { it.id }) { item ->
                        if (item.isLive) {
                            key(item.id) { liveCardContent(item) }
                        } else {
                            key(item.id) {
                                SwipeableItemCard(
                                    item = item,
                                    accentColor = colorMap[item.id] ?: Color.Unspecified,
                                    cardContent = { cardContent(it) },
                                    onSoftDelete = { pendingDeletes.add(it.id); onAction(ListAction.SoftDelete(it.id, it.title)) },
                                    onUndoDelete = { pendingDeletes.remove(it.id); onAction(ListAction.UndoDelete(it.id)) },
                                    onPermanentDelete = { pendingDeletes.remove(it.id); onAction(ListAction.PermanentDelete(it.id)) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
