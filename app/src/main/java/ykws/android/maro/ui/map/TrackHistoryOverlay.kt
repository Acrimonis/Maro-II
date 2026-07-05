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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.outlined.LocationOff
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
import androidx.compose.ui.text.style.TextAlign
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ykws.android.maro.R
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.CustomSortField
import ykws.android.maro.data.model.FilterAxisSpec
import ykws.android.maro.data.model.ListAction
import ykws.android.maro.data.model.ListFilter
import ykws.android.maro.data.model.ListSortState
import ykws.android.maro.data.model.trackFilterAxes
import ykws.android.maro.data.track.TrackRecorderState
import ykws.android.maro.data.track.TrackRecorderUiState
import ykws.android.maro.data.track.TrackSummary
import ykws.android.maro.ui.components.ListOverlayScaffold
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
    sortState: ListSortState,
    onSortStateChange: (ListSortState) -> Unit,
    filterState: ListFilter = ListFilter(),
    onFilterChange: (ListFilter) -> Unit = {},
    onReset: () -> Unit = {},
    isOpen: Boolean = true,
    modifier: Modifier = Modifier,
    // ── Render preview settings ───────────────────────────────────────
    tracksVisible: Boolean = true,
    trackingRenderNb: Int = 20,
    trackingTransparencyNewest: Int = 20,
    trackingTransparencyOldest: Int = 80,
    trackingColorPastFrom: Int = 0xFF1565C0.toInt(),
    trackingColorPastTo: Int = 0xFF42A5F5.toInt(),
    trackingTransparencyPinnedNewest: Int = 0,
    trackingTransparencyPinnedOldest: Int = 20,
    trackingColorPinnedFrom: Int = 0xFFFF6F00.toInt(),
    trackingColorPinnedTo: Int = 0xFFFF8F00.toInt()
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
    val accentColorMap = remember(trackSummaries, tracksVisible, trackingRenderNb,
        trackingTransparencyNewest, trackingTransparencyOldest,
        trackingColorPastFrom, trackingColorPastTo,
        trackingTransparencyPinnedNewest, trackingTransparencyPinnedOldest,
        trackingColorPinnedFrom, trackingColorPinnedTo
    ) {
        val pinnedSummaries = trackSummaries.filter { it.pinned }.sortedByDescending { it.startTimeMs }
        val historySummaries = trackSummaries.filter { !it.pinned }.sortedByDescending { it.startTimeMs }
        val map = mutableMapOf<String, Color>()
        val greyColor = Color(AppConfig.uiSettingsTextMuted).copy(alpha = 0.15f)
        val pinnedTotal = pinnedSummaries.size
        for ((index, summary) in pinnedSummaries.withIndex()) {
            val appearance = computeTrackPolylineAppearance(
                index, pinnedTotal,
                trackingTransparencyPinnedNewest, trackingTransparencyPinnedOldest,
                trackingColorPinnedFrom, trackingColorPinnedTo, 6f
            )
            val a = appearance.argb
            map[summary.id] = Color(red = (a shr 16) and 0xFF, green = (a shr 8) and 0xFF, blue = a and 0xFF, alpha = (a ushr 24) and 0xFF)
        }
        val renderCount = trackingRenderNb.coerceIn(0, 20)
        val historyTotal = historySummaries.size
        for ((index, summary) in historySummaries.withIndex()) {
            if (index < renderCount) {
                val effectiveTotal = minOf(renderCount, historyTotal)
                val appearance = computeTrackPolylineAppearance(
                    index, effectiveTotal,
                    trackingTransparencyNewest, trackingTransparencyOldest,
                    trackingColorPastFrom, trackingColorPastTo,
                    if (index == 0) 8f else 6f
                )
                val a = appearance.argb
                map[summary.id] = Color(red = (a shr 16) and 0xFF, green = (a shr 8) and 0xFF, blue = a and 0xFF, alpha = (a ushr 24) and 0xFF)
            } else {
                map[summary.id] = greyColor
            }
        }
        map
    }

    val liveState = liveTrackState

    ListOverlayScaffold(
        items = trackSummaries,
        title = "Track History",
        sectionLabel = "RECORDED TRACKS",
        sortState = sortState,
        onSortStateChange = onSortStateChange,
        customSortFields = trackCustomSortFields,
        customSortLabel = "Tracks",
        filterAxes = trackFilterAxes(),
        filterState = filterState,
        onFilterChange = onFilterChange,
        onReset = onReset,
        accentColors = { accentColorMap },
        cardContent = { summary ->
            TrackCardContent(
                summary = summary,
                dateFormat = dateFormat,
                accentColor = accentColorMap[summary.id] ?: Color(AppConfig.uiSettingsTextMuted).copy(alpha = 0.15f),
                onUpdateTrack = onUpdateTrack,
                onShareGpx = { onAction(ListAction.ExportGpx(summary.id)) },
                onTap = { onNavigateToTrack(summary.id) }
            )
        },
        liveCardContent = if (liveState != null && liveState.state == TrackRecorderState.ON) {
            { _ -> LiveTrackCard(liveState = liveState, dateFormat = dateFormat, onUpdateMeta = onUpdateLiveTrack) }
        } else {
            {}
        },
        onAction = onAction,
        onDismiss = onDismiss,
        modifier = modifier
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
    onTap: (() -> Unit)? = null
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
                .clickable { onTap?.invoke() }
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
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
        // ── Date + time range + action icons ────────────────────────
        val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.US) }
        val dateLabel = remember(summary.id) {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(summary.startTimeMs))
        }
        val startTime = remember(summary.id) {
            timeFormat.format(Date(summary.startTimeMs))
        }
        val endTime = summary.endTimeMs?.let { timeFormat.format(Date(it)) }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (endTime != null) "$dateLabel  $startTime→$endTime"
                       else "$dateLabel  $startTime",
                color = Color(AppConfig.uiSettingsTextMuted), fontSize = 11.sp
            )
            Text(
                text = "${summary.pointCount} pts",
                color = Color(AppConfig.uiSettingsTextMuted), fontSize = 11.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButton(
                    onClick = { onUpdateTrack(summary.id, null, null, !pinned) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (pinned) Icons.Filled.LocationOn else Icons.Outlined.LocationOff,
                        contentDescription = if (pinned) "Unpin" else "Pin",
                        tint = ButtonColors.icon,
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(
                    onClick = { onShareGpx(summary.id) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Upload,
                        contentDescription = "Export GPX",
                        tint = ButtonColors.icon,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(2.dp))
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 8.dp),
            thickness = 0.5.dp, color = Color(AppConfig.uiSettingsDivider)
        )
        Spacer(Modifier.height(2.dp))

        // ── Editable name ───────────────────────────────────────────
        if (editingField == EditingField.NAME) {
            LaunchedEffect(Unit) { nameFocus.requestFocus() }
            TextField(
                value = nameField,
                onValueChange = { nameField = it },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = Color(AppConfig.uiSettingsTextPrimary),
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedTextColor = Color(AppConfig.uiSettingsTextPrimary),
                    unfocusedTextColor = Color(AppConfig.uiSettingsTextPrimary),
                    cursorColor = Color(AppConfig.uiSettingsTextPrimary),
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
                color = Color(AppConfig.uiSettingsTextPrimary),
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .clickable {
                        // Commit currently-edited field before switching
                        if (editingField == EditingField.COMMENT) {
                            onUpdateTrack(summary.id, null, commentField.text, null)
                        }
                        editingField = EditingField.NAME
                    }
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
                    color = Color(AppConfig.uiSettingsTextMuted), fontSize = 13.sp
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedTextColor = Color(AppConfig.uiSettingsTextMuted),
                    unfocusedTextColor = Color(AppConfig.uiSettingsTextMuted),
                    cursorColor = Color(AppConfig.uiSettingsTextPrimary),
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
                text = summary.comment.ifBlank { "Add a comment..." },
                color = if (summary.comment.isBlank()) Color(AppConfig.uiSettingsTextMuted).copy(alpha = 0.4f)
                        else Color(AppConfig.uiSettingsTextMuted),
                fontSize = 13.sp, maxLines = 3,
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .clickable { editingField = EditingField.COMMENT }
            )
        }

        Spacer(Modifier.height(2.dp))
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 8.dp),
            thickness = 0.5.dp, color = Color(AppConfig.uiSettingsDivider)
        )
        Spacer(Modifier.height(2.dp))

        // ── Stats grid: 3-column × 2-row ───────────────────────────
        val totalSec = if (summary.endTimeMs != null)
            (summary.endTimeMs - summary.startTimeMs) / 1000 else 0L
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
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "View track",
            tint = Color(AppConfig.uiSettingsTextMuted),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 4.dp, bottom = 4.dp)
                .size(28.dp)
        )
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
    val stateLabel = if (liveState.isMoving) "Recording" else "Idle"

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
        liveState.currentTrackName ?: "Recording..."
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
                color = Color(AppConfig.uiSettingsTextMuted), fontSize = 11.sp
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stateLabel,
                    color = Color(AppConfig.uiSettingsTextMuted), fontSize = 11.sp
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
            thickness = 0.5.dp, color = Color(AppConfig.uiSettingsDivider)
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
                    color = Color(AppConfig.uiSettingsTextPrimary),
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedTextColor = Color(AppConfig.uiSettingsTextPrimary),
                    unfocusedTextColor = Color(AppConfig.uiSettingsTextPrimary),
                    cursorColor = Color(AppConfig.uiSettingsTextPrimary),
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
                text = liveState.currentTrackName ?: "Recording...",
                color = Color(AppConfig.uiSettingsTextPrimary),
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
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
                    color = Color(AppConfig.uiSettingsTextMuted), fontSize = 13.sp
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedTextColor = Color(AppConfig.uiSettingsTextMuted),
                    unfocusedTextColor = Color(AppConfig.uiSettingsTextMuted),
                    cursorColor = Color(AppConfig.uiSettingsTextPrimary),
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
                text = commentText.ifBlank { "Add a comment..." },
                color = if (commentText.isBlank()) Color(AppConfig.uiSettingsTextMuted).copy(alpha = 0.4f)
                        else Color(AppConfig.uiSettingsTextMuted),
                fontSize = 13.sp, maxLines = 3,
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                    .clickable { editingField = EditingField.COMMENT }
            )
        }

        Spacer(Modifier.height(2.dp))
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 8.dp),
            thickness = 0.5.dp, color = Color(AppConfig.uiSettingsDivider)
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

/** Single cell in the 3-column stats grid: label (33%, right-aligned) + value (66%, left-aligned). */
@Composable
private fun StatCell(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label:",
            color = Color(AppConfig.uiSettingsTextMuted),
            fontSize = 11.sp,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.weight(0.33f)
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = value,
            color = Color(AppConfig.uiSettingsTextPrimary),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            textAlign = TextAlign.Start,
            modifier = Modifier.weight(0.66f)
        )
    }
}

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
    val kn = speedMps * 1.94384f
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
