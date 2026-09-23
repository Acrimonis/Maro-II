package ykws.android.maro.ui.map

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import ykws.android.maro.ui.components.ConfirmAction
import ykws.android.maro.ui.components.ConfirmActionRole
import ykws.android.maro.ui.components.ConfirmDialog
import ykws.android.maro.ui.components.ConfirmRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ykws.android.maro.BuildConfig
import ykws.android.maro.R
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.CustomSortField
import ykws.android.maro.data.model.FilterAxisSpec
import ykws.android.maro.data.model.ListAction
import ykws.android.maro.data.model.ListFilter
import ykws.android.maro.data.model.ListSortState
import ykws.android.maro.data.model.MultiActionSpec
import ykws.android.maro.data.model.MultiActionSubSpec
import ykws.android.maro.data.model.trackFilterAxes
import ykws.android.maro.data.track.TrackRecorderState
import ykws.android.maro.data.track.TrackRecorderUiState
import ykws.android.maro.data.track.TrackSummary
import ykws.android.maro.data.track.mergeCandidates
import ykws.android.maro.ui.components.ListOverlayScaffold
import ykws.android.maro.ui.components.OptionRow
import ykws.android.maro.ui.components.StatCell
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Full-screen overlay displaying a LazyColumn of track summary cards.
 *
 * **Swipe-to-delete lifecycle:**
 * 1. Swipe card left → card slides out left, snackbar slides in from right
 * 2. Snackbar is inline (same list slot), shorter (48–80dp), text wraps
 * 3. Tap Undo → snackbar shrinks, card slides back in from right
 * 4. Swipe snackbar left → permanent delete, snackbar slides out left
 * 5. Close panel → all pending deletes commit permanently
 *
 * **Inline editing:**
 * - Tap name/comment → auto-focus + keyboard + select all text
 * - IME Done → commit, Back → revert to original
 * - Only one field editable per card at a time
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackHistoryOverlay(
    trackSummaries: List<TrackSummary>,
    liveTrackState: TrackRecorderUiState? = null,
    onUpdateTrack: (String, name: String?, comment: String?, pinned: Boolean?) -> Unit,
    onUpdateLiveTrack: ((name: String?, comment: String?) -> Unit)? = null,
    onAction: (ListAction) -> Unit,
    onDismiss: () -> Unit,
    onNavigateToTrack: (String) -> Unit = {},
    onResumeTrack: ((String) -> Unit)? = null,
    onMergeTracks: ((Set<String>, String, Boolean) -> Unit)? = null,
    sortState: ListSortState,
    onSortStateChange: (ListSortState) -> Unit,
    filterState: ListFilter = ListFilter(),
    onFilterChange: (ListFilter) -> Unit = {},
    onReset: () -> Unit = {},
    filterLinked: Boolean = true,
    onToggleLink: () -> Unit = {},
    isOpen: Boolean = true,
    modifier: Modifier = Modifier,
    lazyListState: LazyListState = rememberLazyListState(),
    // ── Render preview settings ───────────────────────────────────────
    tracksVisible: Boolean = true,
    trackingRenderNb: Int = 20,
    /**
     * The route role's own count, which its accent strip previews: it bounds the non-pinned routes
     * alone, a pinned route being drawn whatever it says (R35). Its shipped default is the key's own.
     */
    routeRenderNb: Int = BuildConfig.TRACKING_ROUTE_RENDER_NB,
    trackingTransparencyNewest: Int = 20,
    trackingTransparencyOldest: Int = 80,
    trackingColorPastFrom: Int = 0xFF1565C0.toInt(),
    trackingColorPastTo: Int = 0xFF42A5F5.toInt(),
    trackingTransparencyPinnedNewest: Int = 0,
    trackingTransparencyPinnedOldest: Int = 20,
    trackingColorPinnedFrom: Int = 0xFFFF6F00.toInt(),
    trackingColorPinnedTo: Int = 0xFFFF8F00.toInt(),
    // The route role's own four values, which its accent strip previews: its pair and its ladder.
    trackingTransparencyRouteNewest: Int = 20,
    trackingTransparencyRouteOldest: Int = 80,
    trackingColorRouteFrom: Int = 0xFF1565C0.toInt(),
    trackingColorRouteTo: Int = 0xFF0000FF.toInt()
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }

    val trackCustomSortFields = remember {
        listOf(
            CustomSortField("distanceNm", R.string.sort_custom_distance),
            CustomSortField("totalTimeSec", R.string.sort_custom_total_time),
            CustomSortField("movingTimeSec", R.string.sort_custom_moving_time)
        )
    }

    // Pre-compute accent bar colors — batch lambda for scaffold
    val accentColorMap = remember(trackSummaries, tracksVisible, trackingRenderNb, routeRenderNb,
        trackingTransparencyNewest, trackingTransparencyOldest,
        trackingColorPastFrom, trackingColorPastTo,
        trackingTransparencyPinnedNewest, trackingTransparencyPinnedOldest,
        trackingColorPinnedFrom, trackingColorPinnedTo,
        trackingTransparencyRouteNewest, trackingTransparencyRouteOldest,
        trackingColorRouteFrom, trackingColorRouteTo
    ) {
        val pinnedSummaries = trackSummaries.filter { it.pinned }.sortedByDescending { it.startTimeMs }
        // The recorded tracks only: a route's strip is its own pair, written last below.
        val historySummaries = trackSummaries.filter { !it.pinned && !it.route }.sortedByDescending { it.startTimeMs }
        val map = mutableMapOf<String, Color>()
        val greyColor = Color(AppConfig.uiTextMuted).copy(alpha = 0.15f)
        val pinnedTotal = pinnedSummaries.size
        for ((index, summary) in pinnedSummaries.withIndex()) {
            // Only the appearance's colour is read here; its width is dp like the type's, so the old
            // 6 px / 8 px pair is written as the 2 dp / 2.667 dp the 3× reference makes them.
            val appearance = computeTrackPolylineAppearance(
                index, pinnedTotal,
                trackingTransparencyPinnedNewest, trackingTransparencyPinnedOldest,
                trackingColorPinnedFrom, trackingColorPinnedTo, 2f
            )
            val a = appearance.argb
            map[summary.id] = Color(red = (a shr 16) and 0xFF, green = (a shr 8) and 0xFF, blue = a and 0xFF, alpha = (a ushr 24) and 0xFF)
        }
        val renderCount = trackingRenderNb.coerceIn(0, 20)
        for ((index, summary) in historySummaries.withIndex()) {
            if (index < renderCount) {
                val effectiveTotal = renderCount
                val appearance = computeTrackPolylineAppearance(
                    index, effectiveTotal,
                    trackingTransparencyNewest, trackingTransparencyOldest,
                    trackingColorPastFrom, trackingColorPastTo,
                    if (index == 0) 8f / 3f else 2f
                )
                val a = appearance.argb
                map[summary.id] = Color(red = (a shr 16) and 0xFF, green = (a shr 8) and 0xFF, blue = a and 0xFF, alpha = (a ushr 24) and 0xFF)
            } else {
                map[summary.id] = greyColor
            }
        }
        // The routes follow the policy the map paints by (R34, R35): the pin buys the escape from the
        // count and nothing else — a pinned route keeps the route pair, written last so it wins over the
        // pinned group's amber above — while the unpinned ones are bounded by the route count and greyed
        // beyond it, exactly as the recorded ones above are.
        val routeCount = routeRenderNb.coerceIn(0, 20)
        val pinnedRouteSummaries = trackSummaries.filter { it.route && it.pinned }.sortedByDescending { it.startTimeMs }
        val openRouteSummaries = trackSummaries.filter { it.route && !it.pinned }.sortedByDescending { it.startTimeMs }
        val drawnRoutes = openRouteSummaries.take(routeCount)
        fun routeAccent(index: Int, total: Int): Color {
            val appearance = computeTrackPolylineAppearance(
                index, total,
                trackingTransparencyRouteNewest, trackingTransparencyRouteOldest,
                trackingColorRouteFrom, trackingColorRouteTo,
                AppConfig.trackWidthRouteDp
            )
            val a = appearance.argb
            return Color(red = (a shr 16) and 0xFF, green = (a shr 8) and 0xFF, blue = a and 0xFF, alpha = (a ushr 24) and 0xFF)
        }
        for ((index, summary) in drawnRoutes.withIndex()) {
            map[summary.id] = routeAccent(index, drawnRoutes.size)
        }
        openRouteSummaries.drop(drawnRoutes.size).forEach { map[it.id] = greyColor }
        for ((index, summary) in pinnedRouteSummaries.withIndex()) {
            map[summary.id] = routeAccent(index, pinnedRouteSummaries.size)
        }
        map
    }

    val liveState = liveTrackState

    val deleteLabel = stringResource(R.string.action_delete)
    val exportLabel = stringResource(R.string.action_export)
    val pinLabel = stringResource(R.string.action_pin)
    val confirmDeleteMsg = stringResource(R.string.confirm_delete_tracks)
    val pinAllLabel = stringResource(R.string.action_pin_all)
    val unpinAllLabel = stringResource(R.string.action_unpin_all)
    val togglePinsLabel = stringResource(R.string.action_toggle_pins)
    val mergeLabel = stringResource(R.string.action_merge)
    val cancelLabel = stringResource(R.string.action_cancel)
    val mergeNameHint = stringResource(R.string.track_merge_name_hint)
    val mergeDefaultName = stringResource(R.string.track_merge_default_name)
    val mergeKeepOriginals = stringResource(R.string.track_merge_keep_originals)
    val context = LocalContext.current

    val trackMultiActions = remember(trackSummaries, onMergeTracks, context) {
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
                id = "export",
                label = exportLabel,
                icon = Icons.Filled.Share,
                action = { ids ->
                    if (ids.size == 1) {
                        onAction(ListAction.ExportGpx(ids.first()))
                    } else {
                        onAction(ListAction.BatchExportGpx(ids))
                    }
                }
            ),
            MultiActionSpec(
                id = "pin",
                label = pinLabel,
                icon = Icons.Filled.PushPin,
                subActions = listOf(
                    MultiActionSubSpec(
                        id = "pin_all",
                        label = pinAllLabel,
                        action = { ids -> ids.forEach { onUpdateTrack(it, null, null, true) } }
                    ),
                    MultiActionSubSpec(
                        id = "unpin_all",
                        label = unpinAllLabel,
                        action = { ids -> ids.forEach { onUpdateTrack(it, null, null, false) } }
                    ),
                    MultiActionSubSpec(
                        id = "toggle_pins",
                        label = togglePinsLabel,
                        action = { ids ->
                            ids.forEach { id ->
                                val current = trackSummaries.find { it.id == id }?.pinned ?: false
                                onUpdateTrack(id, null, null, !current)
                            }
                        }
                    )
                )
            ),
            MultiActionSpec(
                id = "merge",
                label = mergeLabel,
                icon = Icons.AutoMirrored.Filled.MergeType,
                // The candidacy refuses a route rather than the action (R41): a route is a line between
                // two points, not a leg of a journey, so a selection left with fewer than two non-routes
                // disables merge by itself.
                enabled = { ids -> mergeCandidates(trackSummaries, ids).size >= 2 },
                confirmRequest = { ids, onDismiss, onConfirm ->
                    val candidates = mergeCandidates(trackSummaries, ids)
                    val nameById = trackSummaries
                        .filter { it.id in candidates }
                        .sortedBy { it.startTimeMs }
                        .map { it.name }
                    val defaultName = if (nameById.size == 2) "${nameById[0]} + ${nameById[1]}"
                        else "${nameById.first()} ... ${nameById.last()}"
                    val state = MergeDialogState(defaultName)
                    ConfirmRequest(
                        title = context.getString(R.string.track_merge_title, candidates.size),
                        message = mergeNameHint,
                        options = {
                            MergeDialogOptions(
                                state = state,
                                keepOriginalsLabel = mergeKeepOriginals
                            )
                        },
                        actions = listOf(
                            ConfirmAction(mergeLabel, ConfirmActionRole.PRIMARY) {
                                onMergeTracks?.invoke(
                                    candidates,
                                    state.name.ifBlank { mergeDefaultName },
                                    state.keepOriginals
                                )
                                onConfirm()
                            },
                            ConfirmAction(cancelLabel, ConfirmActionRole.SECONDARY) {
                                onDismiss()
                            }
                        )
                    )
                }
            )
        )
    }

    ListOverlayScaffold(
        items = trackSummaries,
        title = stringResource(R.string.track_history_title_fmt, trackSummaries.count { !it.isLive }),
        sectionLabel = stringResource(R.string.track_history_section),
        sortState = sortState,
        onSortStateChange = onSortStateChange,
        customSortFields = trackCustomSortFields,
        customSortLabelResId = R.string.menu_manage_tracks,
        filterAxes = trackFilterAxes(),
        filterState = filterState,
        onFilterChange = onFilterChange,
        onReset = onReset,
        filterLinked = filterLinked,
        onToggleLink = onToggleLink,
        accentColors = { accentColorMap },
        cardContent = { summary, onLongPress ->
            TrackCardContent(
                summary = summary,
                dateFormat = dateFormat,
                accentColor = accentColorMap[summary.id] ?: Color(AppConfig.uiTextMuted).copy(alpha = 0.15f),
                onUpdateTrack = onUpdateTrack,
                onShareGpx = { onAction(ListAction.ExportGpx(summary.id)) },
                onTap = { onNavigateToTrack(summary.id) },
                onLongPress = onLongPress,
                onResumeTrack = onResumeTrack,
                isRecording = liveState?.state == TrackRecorderState.ON
            )
        },
        liveCardContent = if (liveState != null && liveState.state == TrackRecorderState.ON) {
            { _ -> LiveTrackCard(liveState = liveState, dateFormat = dateFormat, onUpdateMeta = onUpdateLiveTrack) }
        } else {
            {}
        },
        onAction = onAction,
        onDismiss = onDismiss,
        modifier = modifier,
        multiActions = trackMultiActions,
        lazyListState = lazyListState
    )
}

/**
 * Snapshot-backed state of the merge dialog. It is created where the dialog is requested and
 * carried by the [ConfirmRequest], so the dialog's fields survive while it is composed on the
 * overlay ladder.
 */
private class MergeDialogState(defaultName: String) {
    var name by mutableStateOf(defaultName)
    var keepOriginals by mutableStateOf(true)
}

/** Options slot of the merge dialog: the name field and the keep-originals checkbox. */
@Composable
private fun MergeDialogOptions(
    state: MergeDialogState,
    keepOriginalsLabel: String
) {
    TextField(
        value = state.name,
        onValueChange = { state.name = it },
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(
            color = Color(AppConfig.uiTextPrimary),
            fontSize = 15.sp
        ),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedTextColor = Color(AppConfig.uiTextPrimary),
            unfocusedTextColor = Color(AppConfig.uiTextPrimary),
            cursorColor = Color(AppConfig.uiTextPrimary)
        ),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(8.dp))
    OptionRow(
        label = keepOriginalsLabel,
        checked = state.keepOriginals,
        onCheckedChange = { state.keepOriginals = it },
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * Track card with inline editing for name and comment.
 *
 * Editing rules:
 * - Tap → auto-focus + keyboard + select all text
 * - IME Done → commit changes
 * - Back → revert to original value
 * - One field at a time (name ↔ comment mutual exclusion)
 */
@Composable
internal fun TrackCardContent(
    summary: TrackSummary,
    dateFormat: SimpleDateFormat,
    accentColor: Color = Color.Unspecified,
    onUpdateTrack: (String, name: String?, comment: String?, pinned: Boolean?) -> Unit,
    onShareGpx: (String) -> Unit,
    onTap: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    onResumeTrack: ((String) -> Unit)? = null,
    isRecording: Boolean = false,
    showChevron: Boolean = true
) {
    // Original values for revert-on-back
    val originalName = remember(summary.id) { summary.name }
    val originalComment = remember(summary.id) { summary.comment }

    // Editing state — only one field at a time
    var editingField by remember(summary.id) { mutableStateOf<EditingField?>(null) }
    var nameField by remember(summary.id) {
        mutableStateOf(TextFieldValue(summary.name, TextRange(0, summary.name.length)))
    }
    var commentField by remember(summary.id) {
        mutableStateOf(TextFieldValue(summary.comment, TextRange(0, summary.comment.length)))
    }
    val pinned = summary.pinned

    val keyboardController = LocalSoftwareKeyboardController.current
    val nameFocus = remember { FocusRequester() }
    val commentFocus = remember { FocusRequester() }

    // BackHandler for edit undo — intercepts back when editing
    if (editingField != null) {
        BackHandler {
            when (editingField) {
                EditingField.NAME -> {
                    nameField = TextFieldValue(originalName, TextRange(0, originalName.length))
                    editingField = null
                }
                EditingField.COMMENT -> {
                    commentField = TextFieldValue(originalComment, TextRange(0, originalComment.length))
                    editingField = null
                }
                null -> {}
            }
            keyboardController?.hide()
        }
    }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(AppConfig.uiCardBackground))
                .combinedClickable(
                    onClick = { onTap?.invoke() },
                    onLongClick = onLongPress
                )
        ) {
            // Left-edge accent bar — previews the track's polyline render color
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accentColor)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp, top = 2.dp, end = 8.dp, bottom = 6.dp)
            ) {
        // ── Date + time range + action icons ────────────────────────
        val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.US) }
        val dateLabel = remember(summary.id) {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(summary.startTimeMs))
        }
        val startTime = remember(summary.id) {
            timeFormat.format(Date(summary.startTimeMs))
        }
        // A route's header carries the instant of its **generation and finalisation** — not of the
        // save, which is the stamp `TrackFromCourse` writes as its start — and **no end time** (R40),
        // so a route reads as one stamp rather than as a range.
        val endTime = if (summary.route) null else summary.endTimeMs?.let { finalizeMs ->
            val displayMs = summary.lastPointTimeMs.takeIf { it != 0L } ?: finalizeMs
            timeFormat.format(Date(displayMs))
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (endTime != null) "$dateLabel  $startTime→$endTime"
                       else "$dateLabel  $startTime",
                color = Color(AppConfig.uiTextMuted), fontSize = 11.sp, lineHeight = 12.sp
            )
            Text(
                text = stringResource(R.string.track_point_count_fmt, summary.pointCount),
                color = Color(AppConfig.uiTextMuted), fontSize = 11.sp, lineHeight = 12.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButton(
                    onClick = { onUpdateTrack(summary.id, null, null, !pinned) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = if (pinned) stringResource(R.string.cd_unpin) else stringResource(R.string.cd_pin),
                        tint = ButtonColors.icon,
                        modifier = Modifier.size(24.dp)
                    )
                }
                // One predicate on the summary, shared by every surface that offers Resume: a route
                // never resumes (R41), and the recording guard beside it is the screen's own state.
                if (summary.resumeAllowed && !isRecording && onResumeTrack != null) {
                    IconButton(
                        onClick = { onResumeTrack(summary.id) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = stringResource(R.string.cd_resume_recording),
                            tint = ButtonColors.icon,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                IconButton(
                    onClick = { onShareGpx(summary.id) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Upload,
                        contentDescription = stringResource(R.string.cd_export_gpx),
                        tint = ButtonColors.icon,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            if (showChevron) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.cd_view_track),
                    tint = Color(AppConfig.uiTextMuted),
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        // ── Editable name ───────────────────────────────────────────
        if (editingField == EditingField.NAME) {
            LaunchedEffect(Unit) { nameFocus.requestFocus() }
            TextField(
                value = nameField,
                onValueChange = { nameField = it },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = Color(AppConfig.uiTextPrimary),
                    fontSize = 15.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedTextColor = Color(AppConfig.uiTextPrimary),
                    unfocusedTextColor = Color(AppConfig.uiTextPrimary),
                    cursorColor = Color(AppConfig.uiTextPrimary),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    onUpdateTrack(summary.id, nameField.text, null, null)
                    editingField = null
                    keyboardController?.hide()
                }),
                modifier = Modifier.fillMaxWidth()
                    .focusRequester(nameFocus)
                    .heightIn(min = 0.dp)
            )
        } else {
            Text(
                text = summary.name,
                color = Color(AppConfig.uiTextPrimary),
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp,
                modifier = Modifier.fillMaxWidth()
                    .padding(start = 8.dp, top = 0.dp, end = 8.dp, bottom = 1.dp)
                    .combinedClickable(
                        onClick = { onTap?.invoke() },
                        onDoubleClick = {
                            // Commit currently-edited field before switching
                            if (editingField == EditingField.COMMENT) {
                                onUpdateTrack(summary.id, null, commentField.text, null)
                            }
                            nameField = TextFieldValue(summary.name, TextRange(0, summary.name.length))
                            editingField = EditingField.NAME
                        }
                    )
            )
        }

        // ── Editable comment ────────────────────────────────────────
        if (editingField == EditingField.COMMENT) {
            LaunchedEffect(Unit) { commentFocus.requestFocus() }
            TextField(
                value = commentField,
                onValueChange = { commentField = it },
                singleLine = false,
                minLines = 1,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = Color(AppConfig.uiTextMuted), fontSize = 13.sp, lineHeight = 14.sp
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedTextColor = Color(AppConfig.uiTextMuted),
                    unfocusedTextColor = Color(AppConfig.uiTextMuted),
                    cursorColor = Color(AppConfig.uiTextPrimary),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    onUpdateTrack(summary.id, null, commentField.text, null)
                    editingField = null
                    keyboardController?.hide()
                }),
                modifier = Modifier.fillMaxWidth()
                    .focusRequester(commentFocus)
                    .heightIn(min = 0.dp)
            )
        } else {
            Text(
                text = summary.comment.ifBlank { stringResource(R.string.track_comment_placeholder) },
                color = if (summary.comment.isBlank()) Color(AppConfig.uiTextMuted).copy(alpha = 0.4f)
                        else Color(AppConfig.uiTextMuted),
                fontSize = 13.sp, maxLines = 3, lineHeight = 14.sp,
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 1.dp)
                    .combinedClickable(
                        onClick = { onTap?.invoke() },
                        onDoubleClick = {
                            commentField = TextFieldValue(summary.comment, TextRange(0, summary.comment.length))
                            editingField = EditingField.COMMENT
                        }
                    )
            )
        }

        Spacer(Modifier.height(1.dp))
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 8.dp),
            thickness = 0.5.dp, color = Color(AppConfig.uiDividerColor)
        )
        Spacer(Modifier.height(1.dp))

        // ── Stats grid: 3-column × 2-row for a recording, one 3-column row for a route ───────────
        val totalSec = if (summary.endTimeMs != null) {
            val endMs = summary.lastPointTimeMs.takeIf { it != 0L } ?: summary.endTimeMs!!
            (endMs - summary.startTimeMs) / 1000
        } else 0L
        if (summary.route) {
            // A route keeps the grid's own three-column shape and shows three cells (R39): Dist is
            // measured off the line and needs no marker, while Total is the plan's allotted time and
            // Avg the pace it was priced with — each labelled as an estimate, the words carrying that
            // rather than the arithmetic.
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.weight(1f)) { StatCell(stringResource(R.string.track_stat_dist), fmtNm(summary.distanceNm)) }
                Box(Modifier.weight(1f)) { StatCell(stringResource(R.string.track_stat_total_estimated), fmtDuration(totalSec)) }
                Box(Modifier.weight(1f)) { StatCell(stringResource(R.string.track_stat_avg_estimated), fmtKnFromMps(summary.averageSpeedMps)) }
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.weight(1f)) { StatCell(stringResource(R.string.track_stat_total), fmtDuration(totalSec)) }
                Box(Modifier.weight(1f)) { StatCell(stringResource(R.string.track_stat_nav), fmtDuration(summary.navigatingDurationSec)) }
                Box(Modifier.weight(1f)) { StatCell(stringResource(R.string.track_stat_avg), fmtKnFromMps(summary.averageSpeedMps)) }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.weight(1f)) { StatCell(stringResource(R.string.track_stat_dist), fmtNm(summary.distanceNm)) }
                Box(Modifier.weight(1f)) { StatCell(stringResource(R.string.track_stat_idle), fmtDuration(summary.idleDurationSec)) }
                Box(Modifier.weight(1f)) { StatCell(stringResource(R.string.track_stat_max), fmtKnFromMps(summary.fastestSpeedMps)) }
            }
        }
    }
    }
    }
}

/**
 * Live track card shown at the top of the track list while recording.
 * Pulsing border + dot, editable name/comment, live stats.
 */
@Composable
private fun LiveTrackCard(
    liveState: TrackRecorderUiState,
    dateFormat: SimpleDateFormat,
    onUpdateMeta: ((name: String?, comment: String?) -> Unit)? = null
) {
    val dotColor = if (liveState.isMoving)
        Color(AppConfig.statusTrackingDotRecording)
    else
        Color(AppConfig.statusTrackingDotIdle)

    val borderColor = dotColor
    val stateLabel = if (liveState.isMoving) stringResource(R.string.state_recording)
                     else stringResource(R.string.state_idle)
    val defaultLiveName = stringResource(R.string.track_live_default_name)

    // Pulsing animation for border and dot: 0.5 → 0.2 → 0.5
    val infiniteTransition = rememberInfiniteTransition(label = "livePulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "livePulseAlpha"
    )

    val startDate = remember(liveState.currentTrackId) {
        liveState.currentTrackName ?: defaultLiveName
    }

    // Inline editing state
    var editingField by remember { mutableStateOf<EditingField?>(null) }
    var nameField by remember(liveState.currentTrackId, liveState.currentTrackName) {
        mutableStateOf(TextFieldValue(liveState.currentTrackName ?: ""))
    }
    var commentField by remember(liveState.currentTrackId, liveState.currentTrackComment) {
        mutableStateOf(TextFieldValue(liveState.currentTrackComment ?: ""))
    }
    val keyboardController = LocalSoftwareKeyboardController.current
    val nameFocus = remember { FocusRequester() }
    val commentFocus = remember { FocusRequester() }

    if (editingField != null) {
        BackHandler {
            when (editingField) {
                EditingField.NAME -> {
                    nameField = TextFieldValue(liveState.currentTrackName ?: "")
                    editingField = null
                }
                EditingField.COMMENT -> {
                    commentField = TextFieldValue("")
                    editingField = null
                }
                null -> {}
            }
            keyboardController?.hide()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(AppConfig.uiCardBackground))
            .border(
                BorderStroke(2.dp, borderColor.copy(alpha = pulseAlpha)),
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        // ── Date + "-> ..." + state label + pulsing dot ──────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$startDate -> ...",
                color = Color(AppConfig.uiTextMuted), fontSize = 11.sp
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stateLabel,
                    color = Color(AppConfig.uiTextMuted), fontSize = 11.sp
                )
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(dotColor.copy(alpha = pulseAlpha))
                )
            }
        }

        Spacer(Modifier.height(2.dp))
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 8.dp),
            thickness = 0.5.dp, color = Color(AppConfig.uiDividerColor)
        )
        Spacer(Modifier.height(2.dp))

        // ── Editable name ────────────────────────────────────────
        if (editingField == EditingField.NAME) {
            LaunchedEffect(Unit) { nameFocus.requestFocus() }
            TextField(
                value = nameField,
                onValueChange = { nameField = it },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = Color(AppConfig.uiTextPrimary),
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedTextColor = Color(AppConfig.uiTextPrimary),
                    unfocusedTextColor = Color(AppConfig.uiTextPrimary),
                    cursorColor = Color(AppConfig.uiTextPrimary),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    onUpdateMeta?.invoke(nameField.text, null)
                    editingField = null
                    keyboardController?.hide()
                }),
                modifier = Modifier.fillMaxWidth()
                    .focusRequester(nameFocus)
                    .heightIn(min = 0.dp)
            )
        } else {
            Text(
                text = liveState.currentTrackName ?: defaultLiveName,
                color = Color(AppConfig.uiTextPrimary),
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                    .clickable { editingField = EditingField.NAME }
            )
        }

        // ── Editable comment ─────────────────────────────────────
        if (editingField == EditingField.COMMENT) {
            LaunchedEffect(Unit) { commentFocus.requestFocus() }
            TextField(
                value = commentField,
                onValueChange = { commentField = it },
                singleLine = false,
                minLines = 1,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = Color(AppConfig.uiTextMuted), fontSize = 13.sp
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedTextColor = Color(AppConfig.uiTextMuted),
                    unfocusedTextColor = Color(AppConfig.uiTextMuted),
                    cursorColor = Color(AppConfig.uiTextPrimary),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    onUpdateMeta?.invoke(null, commentField.text)
                    editingField = null
                    keyboardController?.hide()
                }),
                modifier = Modifier.fillMaxWidth()
                    .focusRequester(commentFocus)
                    .heightIn(min = 0.dp)
            )
        } else {
            val commentText = commentField.text
            Text(
                text = commentText.ifBlank { stringResource(R.string.track_comment_placeholder) },
                color = if (commentText.isBlank()) Color(AppConfig.uiTextMuted).copy(alpha = 0.4f)
                        else Color(AppConfig.uiTextMuted),
                fontSize = 13.sp, maxLines = 3,
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                    .clickable { editingField = EditingField.COMMENT }
            )
        }

        Spacer(Modifier.height(2.dp))
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 8.dp),
            thickness = 0.5.dp, color = Color(AppConfig.uiDividerColor)
        )
        Spacer(Modifier.height(2.dp))

        // ── Live stats grid ────────────────────────────────────────
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.weight(1f)) { StatCell(stringResource(R.string.track_stat_total), fmtDuration(liveState.elapsedSeconds)) }
            Box(Modifier.weight(1f)) { StatCell(stringResource(R.string.track_stat_nav), fmtDuration(liveState.elapsedSeconds - liveState.idleDurationSec)) }
            Box(Modifier.weight(1f)) { StatCell(stringResource(R.string.track_stat_avg), fmtKn(liveState.avgSpeedKn)) }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.weight(1f)) { StatCell(stringResource(R.string.track_stat_dist), fmtNm(liveState.distanceNm)) }
            Box(Modifier.weight(1f)) { StatCell(stringResource(R.string.track_stat_idle), fmtDuration(liveState.idleDurationSec)) }
            Box(Modifier.weight(1f)) { StatCell(stringResource(R.string.track_stat_max), fmtKn(liveState.maxSpeedKn)) }
        }
    }
}

/** Which field is being edited — ensures mutual exclusion. */
private enum class EditingField { NAME, COMMENT }

/** Human-readable duration: "2h 30m 0s" / "32m 0s" — matches drawer format. */
private fun fmtDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "${hours}h ${minutes}m ${seconds}s"
    } else {
        "${minutes}m ${seconds}s"
    }
}

/** Speed from mps in knots with 1 decimal: "5.1 kn" — matches drawer format. */
private fun fmtKnFromMps(speedMps: Float): String {
    val kn = speedMps * ykws.android.maro.spatial.Units.KNOTS_PER_MPS.toFloat()
    return java.lang.String.format(java.util.Locale.US, "%.1f kn", kn)
}

/** Speed from knots with 1 decimal: "5.1 kn" — matches drawer format. */
private fun fmtKn(kn: Float): String {
    return java.lang.String.format(java.util.Locale.US, "%.1f kn", kn)
}

/** Distance in nm with 2 decimals: "4.20 nm" — matches drawer format. */
private fun fmtNm(distanceNm: Float): String {
    return java.lang.String.format(java.util.Locale.US, "%.2f nm", distanceNm)
}
