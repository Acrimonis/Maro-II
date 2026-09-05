package ykws.android.maro.ui.map

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import ykws.android.maro.R
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.CustomSortField
import ykws.android.maro.data.model.FilterAxisSpec
import ykws.android.maro.data.model.ListAction
import ykws.android.maro.data.model.ListFilter
import ykws.android.maro.data.model.MultiActionSpec
import ykws.android.maro.data.model.MultiActionSubSpec
import ykws.android.maro.data.model.markerFilterAxes
import ykws.android.maro.data.model.markers.MarkerGeometry
import ykws.android.maro.data.model.markers.UserMarker
import ykws.android.maro.ui.components.ListOverlayScaffold

/**
 * Full-screen overlay displaying a LazyColumn of user marker cards with
 * swipe-to-delete. Delegates structure to [ListOverlayScaffold].
 *
 * @param markers              List of all user markers.
 * @param onAction             Single callback for all list actions.
 * @param onCreateFirst        Called when user taps "Create First Marker" from empty state.
 * @param onDismiss            Called to close the management page.
 * @param onSetIcon            Called to set/change marker icon.
 * @param sortOrder            Current sort order from settings.
 * @param onSortOrderChange    Called when user picks a new sort order.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MarkerManagementOverlay(
    markers: List<UserMarker>,
    trackTitleLookup: (String) -> String? = { null },
    onAction: (ykws.android.maro.data.model.ListAction) -> Unit,
    onCreateFirst: () -> Unit,
    onDismiss: () -> Unit,
    onSetIcon: (String, String?) -> Unit = { _, _ -> },
    onSetPin: (String, Boolean) -> Unit = { _, _ -> },
    onUpdateMarkerText: (String, String?, String?) -> Unit = { _, _, _ -> },
    sortState: ykws.android.maro.data.model.ListSortState,
    onSortStateChange: (ykws.android.maro.data.model.ListSortState) -> Unit,
    filterState: ListFilter = ListFilter(),
    onFilterChange: (ListFilter) -> Unit = {},
    onReset: () -> Unit = {},
    modifier: Modifier = Modifier,
    lazyListState: LazyListState = rememberLazyListState()
) {
    val markerCustomSortFields = remember {
        listOf(
            CustomSortField("origin", R.string.sort_custom_origin, descendingDefault = false)
        )
    }

    val deleteLabel = stringResource(R.string.action_delete)
    val iconLabel = stringResource(R.string.action_icon)
    val confirmDeleteMsg = stringResource(R.string.confirm_delete_markers)
    val setIconAllLabel = stringResource(R.string.action_set_icon_all)
    val clearIconAllLabel = stringResource(R.string.action_clear_icon_all)
    val pinLabel = stringResource(R.string.action_pin)
    val pinAllLabel = stringResource(R.string.action_pin_all)
    val unpinAllLabel = stringResource(R.string.action_unpin_all)
    val togglePinsLabel = stringResource(R.string.action_toggle_pins)
    val pendingIconApplyIds = remember { mutableStateOf<Set<String>?>(null) }

    val markerMultiActions = remember(markers) {
        listOf(
            MultiActionSpec(
                id = "delete",
                label = deleteLabel,
                icon = Icons.Filled.Delete,
                isDestructive = true,
                confirmMessage = confirmDeleteMsg,
                action = { ids -> ids.forEach { onAction(ListAction.PermanentDelete(it)) } }
            ),
            MultiActionSpec(
                id = "pin",
                label = pinLabel,
                icon = Icons.Filled.PushPin,
                subActions = listOf(
                    MultiActionSubSpec(
                        id = "pin_all",
                        label = pinAllLabel,
                        action = { ids -> ids.forEach { onSetPin(it, true) } }
                    ),
                    MultiActionSubSpec(
                        id = "unpin_all",
                        label = unpinAllLabel,
                        action = { ids -> ids.forEach { onSetPin(it, false) } }
                    ),
                    MultiActionSubSpec(
                        id = "toggle_pins",
                        label = togglePinsLabel,
                        action = { ids ->
                            ids.forEach { id ->
                                val current = markers.find { it.id == id }?.pinned ?: false
                                onSetPin(id, !current)
                            }
                        }
                    )
                )
            ),
            MultiActionSpec(
                id = "icon",
                label = iconLabel,
                icon = Icons.Outlined.LocationOff,
                subActions = listOf(
                    MultiActionSubSpec(
                        id = "set_icon",
                        label = setIconAllLabel,
                        action = { ids -> pendingIconApplyIds.value = ids }
                    ),
                    MultiActionSubSpec(
                        id = "clear_icon",
                        label = clearIconAllLabel,
                        action = { ids -> ids.forEach { onSetIcon(it, null) } }
                    )
                )
            )
        )
    }

    ListOverlayScaffold(
        items = markers,
        title = "Markers \u00B7 ${markers.size}",
        sectionLabel = "YOUR MARKERS",
        sortState = sortState,
        onSortStateChange = onSortStateChange,
        customSortFields = markerCustomSortFields,
        customSortLabel = "Markers",
        filterAxes = markerFilterAxes(),
        filterState = filterState,
        onFilterChange = onFilterChange,
        onReset = onReset,
        accentColors = { list -> list.associate { it.id to Color(ykws.android.maro.ui.map.MarkerColors.of(it.colorIndex)) } },
        cardContent = { marker, onLongPress ->
            MarkerCardContent(
                marker = marker,
                trackTitle = marker.trackId?.let(trackTitleLookup),
                onTap = { onAction(ykws.android.maro.data.model.ListAction.NavigateToItem(marker.id)) },
                onEdit = { onAction(ykws.android.maro.data.model.ListAction.EditItem(marker.id)) },
                onSetIcon = onSetIcon,
                onSetPin = onSetPin,
                onUpdateText = { name, desc -> onUpdateMarkerText(marker.id, name, desc) },
                onLongPress = onLongPress
            )
        },
        emptyState = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CenteredPinIcon(sizeDp = 64)
                Spacer(modifier = Modifier.height(24.dp))
                Text(stringResource(R.string.marker_empty), color = Color(AppConfig.uiSettingsTextMuted), fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onCreateFirst,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(AppConfig.buttonActionBgColor)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.marker_create_first), color = Color(AppConfig.buttonActionIconColor), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        onAction = onAction,
        onDismiss = onDismiss,
        modifier = modifier,
        multiActions = markerMultiActions,
        lazyListState = lazyListState
    )

    pendingIconApplyIds.value?.let { ids ->
        IconPickerDialog(
            currentIcon = null,
            onIconSelected = { icon ->
                ids.forEach { onSetIcon(it, icon) }
                pendingIconApplyIds.value = null
            },
            onDismiss = { pendingIconApplyIds.value = null }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Marker card content — matching track list item pattern
// ─────────────────────────────────────────────────────────────────────────────

private val MARKER_CARD_RADIUS = 12.dp
private val MARKER_ACCENT_BAR_WIDTH = 4.dp
private val MARKER_CONTENT_PAD_H = 8.dp
private val MARKER_CONTENT_PAD_V = 2.dp
private val MARKER_HEADER_FONT_SIZE = 11.sp
private val MARKER_TITLE_FONT_SIZE = 15.sp
private val MARKER_GEOMETRY_FONT_SIZE = 14.sp
private val MARKER_DESC_FONT_SIZE = 13.sp

@Composable
internal fun MarkerCardContent(
    marker: UserMarker,
    trackTitle: String? = null,
    onTap: () -> Unit,
    onEdit: () -> Unit,
    onSetIcon: (String, String?) -> Unit,
    onSetPin: (String, Boolean) -> Unit = { _, _ -> },
    onUpdateText: (String?, String?) -> Unit,
    onLongPress: (() -> Unit)? = null,
    showChevron: Boolean = true
) {
    var editingField by remember(marker.id) { mutableStateOf<String?>(null) }
    var nameText by remember(marker.id) { mutableStateOf(marker.name) }
    var descText by remember(marker.id) { mutableStateOf(marker.description) }
    val keyboard = LocalSoftwareKeyboardController.current
    BackHandler(enabled = editingField != null) {
        editingField = null
        keyboard?.hide()
    }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .clip(RoundedCornerShape(MARKER_CARD_RADIUS))
                .background(Color(AppConfig.uiCardBackground))
                .combinedClickable(
                    onClick = onTap,
                    onLongClick = onLongPress
                )
        ) {
            Box(
                modifier = Modifier
                    .width(MARKER_ACCENT_BAR_WIDTH)
                    .fillMaxHeight()
                    .background(Color(MarkerColors.of(marker.colorIndex)))
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = MARKER_CONTENT_PAD_H, top = MARKER_CONTENT_PAD_V, end = MARKER_CONTENT_PAD_H, bottom = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = coordinateHeader(marker),
                        color = Color(AppConfig.uiSettingsTextMuted),
                        fontSize = MARKER_HEADER_FONT_SIZE,
                        lineHeight = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        IconButton(
                            onClick = { onSetPin(marker.id, !marker.pinned) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (marker.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                contentDescription = if (marker.pinned) stringResource(R.string.cd_unpin) else stringResource(R.string.cd_pin),
                                tint = ButtonColors.icon,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        var showIconPicker by remember { mutableStateOf(false) }
                        IconButton(
                            onClick = { showIconPicker = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            if (marker.icon != null) {
                                Text(marker.icon!!, fontSize = 20.sp)
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.LocationOff,
                                    contentDescription = stringResource(R.string.cd_set_icon),
                                    tint = ButtonColors.icon,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        if (showIconPicker) {
                            IconPickerDialog(
                                currentIcon = marker.icon,
                                onIconSelected = { icon ->
                                    onSetIcon(marker.id, icon)
                                    showIconPicker = false
                                },
                                onDismiss = { showIconPicker = false }
                            )
                        }
                        IconButton(
                            onClick = onEdit,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = stringResource(R.string.cd_edit),
                                tint = ButtonColors.icon,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                if (editingField == "name") {
                    TextField(
                        value = nameText,
                        onValueChange = { nameText = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = Color(AppConfig.uiSettingsTextPrimary),
                            fontSize = MARKER_TITLE_FONT_SIZE,
                            lineHeight = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = Color(AppConfig.uiSettingsTextPrimary),
                            unfocusedTextColor = Color(AppConfig.uiSettingsTextPrimary),
                            cursorColor = Color(AppConfig.uiSettingsTextPrimary)
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            onUpdateText(nameText.trim(), null)
                            editingField = null
                            keyboard?.hide()
                        }),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        text = marker.name,
                        color = Color(AppConfig.uiSettingsTextPrimary),
                        fontSize = MARKER_TITLE_FONT_SIZE,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth().combinedClickable(
                            onClick = onTap,
                            onDoubleClick = {
                                nameText = marker.name
                                editingField = "name"
                            }
                        )
                    )
                }

                if (trackTitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "\uD83D\uDEE4 $trackTitle",
                        color = Color(AppConfig.uiSettingsAccent),
                        fontSize = 12.sp,
                        lineHeight = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(2.dp))
                if (editingField == "description") {
                    TextField(
                        value = descText,
                        onValueChange = { descText = it },
                        singleLine = false,
                        minLines = 1,
                        maxLines = 3,
                        textStyle = TextStyle(color = Color(AppConfig.uiSettingsTextMuted), fontSize = MARKER_DESC_FONT_SIZE, lineHeight = 14.sp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = Color(AppConfig.uiSettingsTextMuted),
                            unfocusedTextColor = Color(AppConfig.uiSettingsTextMuted),
                            cursorColor = Color(AppConfig.uiSettingsTextPrimary)
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            onUpdateText(null, descText.trim())
                            editingField = null
                            keyboard?.hide()
                        }),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        text = marker.description.ifBlank { "Add description..." },
                        color = if (marker.description.isBlank()) Color(AppConfig.uiSettingsTextMuted).copy(alpha = 0.4f)
                                else Color(AppConfig.uiSettingsTextMuted),
                        fontSize = MARKER_DESC_FONT_SIZE,
                        lineHeight = 14.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().combinedClickable(
                            onClick = onTap,
                            onDoubleClick = {
                                descText = marker.description
                                editingField = "description"
                            }
                        )
                    )
                }
            }
        }
        if (showChevron) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(48.dp)
                    .clickable(onClick = onTap),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.cd_view_marker),
                    tint = Color(AppConfig.uiSettingsTextMuted),
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

private fun coordinateHeader(marker: UserMarker): String {
    val icon = MarkerGeometry.iconFor(marker.geometry)
    fun fmt(ll: ykws.android.maro.data.model.LatLng) =
        "%.4f, %.4f".format(ll.latitude, ll.longitude)
    return when (val g = marker.geometry) {
        is MarkerGeometry.Pin -> "$icon [${fmt(g.position)}]"
        is MarkerGeometry.Circle -> "$icon [${fmt(g.center)}]"
        is MarkerGeometry.Corridor -> "$icon [${fmt(g.p1)}] \u2192 [${fmt(g.p2)}]"
    }
}

@Composable
private fun CenteredPinIcon(sizeDp: Int) {
    val density = LocalDensity.current
    val color = Color(AppConfig.uiSettingsTextMuted).copy(alpha = 0.4f)

    androidx.compose.foundation.Canvas(
        modifier = Modifier.size(sizeDp.dp)
    ) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val strokeW = w * 0.10f
        val pinRadius = w * 0.25f
        val pinCenterY = h * 0.45f
        drawCircle(
            color = color,
            radius = pinRadius,
            center = androidx.compose.ui.geometry.Offset(cx, pinCenterY),
            style = Stroke(width = strokeW)
        )
        val tipY = h * 0.95f
        val pointLeft = cx - pinRadius * 0.7f
        val pointRight = cx + pinRadius * 0.7f
        val pointTop = pinCenterY + pinRadius * 0.9f
        val path = Path().apply {
            moveTo(pointLeft, pointTop)
            lineTo(cx, tipY)
            lineTo(pointRight, pointTop)
            close()
        }
        drawPath(path = path, color = color, style = Stroke(width = strokeW))
        drawCircle(
            color = color.copy(alpha = 0.6f),
            radius = pinRadius * 0.35f,
            center = androidx.compose.ui.geometry.Offset(cx, pinCenterY)
        )
    }
}
