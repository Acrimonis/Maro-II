package ykws.android.maro.ui.map

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import ykws.android.maro.data.track.TrackRecorderState
import ykws.android.maro.data.track.TrackRecorderUiState
import ykws.android.maro.data.track.TrackSummary
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
@Composable
fun TrackHistoryOverlay(
    trackSummaries: List<TrackSummary>,
    liveTrackState: TrackRecorderUiState? = null,
    onUpdateTrack: (String, name: String?, comment: String?, visibleOnMap: Boolean?) -> Unit,
    onUpdateLiveTrack: ((name: String?, comment: String?) -> Unit)? = null,
    onDeleteTrack: (String) -> Unit,
    onUndoDeleteTrack: (String) -> Unit,
    onShareGpx: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pendingDeletes = remember { mutableListOf<String>() }

    BackHandler {
        pendingDeletes.forEach { id -> onDeleteTrack(id) }
        pendingDeletes.clear()
        onDismiss()
    }

    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(AppConfig.uiSettingsBackground))
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Header ─────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = {
                            pendingDeletes.forEach { id -> onDeleteTrack(id) }
                            pendingDeletes.clear()
                            onDismiss()
                        },
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(AppConfig.uiSettingsSwitchTrackInactive)
                        ),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(AppConfig.uiSettingsTextPrimary),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Track History",
                        color = Color(AppConfig.uiSettingsTextPrimary),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "RECORDED TRACKS",
                color = Color(AppConfig.uiSettingsAccent),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))

            // ── Track list ─────────────────────────────────────────────
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Live track card (index 0) when recording
                if (liveTrackState != null && liveTrackState.state == TrackRecorderState.ON) {
                    item(key = "__live__") {
                        LiveTrackCard(
                            liveState = liveTrackState,
                            dateFormat = dateFormat,
                            onUpdateMeta = onUpdateLiveTrack,
                        )
                    }
                }

                items(trackSummaries, key = { it.id }) { summary ->
                    SwipeToDeleteCard(
                        summary = summary,
                        dateFormat = dateFormat,
                        onUpdateTrack = onUpdateTrack,
                        onShareGpx = onShareGpx,
                        onDelete = { pendingDeletes.add(summary.id) },
                        onUndo = {
                            pendingDeletes.remove(summary.id)
                            onUndoDeleteTrack(summary.id)
                        },
                        onPermanentDelete = {
                            pendingDeletes.remove(summary.id)
                            onDeleteTrack(summary.id)
                        }
                    )
                }
            }
        }
    }
}

private enum class ItemState { CARD, SNACKBAR, DELETED }

@Composable
private fun SwipeToDeleteCard(
    summary: TrackSummary,
    dateFormat: SimpleDateFormat,
    onUpdateTrack: (String, name: String?, comment: String?, visibleOnMap: Boolean?) -> Unit,
    onShareGpx: (String) -> Unit,
    onDelete: () -> Unit,
    onUndo: () -> Unit,
    onPermanentDelete: () -> Unit
) {
    var state by remember { mutableStateOf(ItemState.CARD) }
    val scope = rememberCoroutineScope()

    // Card swipe state
    var cardWidthPx by remember { mutableFloatStateOf(0f) }
    var cardDragOffset by remember { mutableFloatStateOf(0f) }
    val cardSwipeOffset by animateFloatAsState(
        targetValue = cardDragOffset, animationSpec = tween(200), label = "cardSwipe"
    )

    // Snackbar swipe state
    var snackWidthPx by remember { mutableFloatStateOf(0f) }
    var snackDragOffset by remember { mutableFloatStateOf(0f) }
    val snackSwipeOffset by animateFloatAsState(
        targetValue = snackDragOffset, animationSpec = tween(200), label = "snackSwipe"
    )

    var cardDismissed by remember { mutableStateOf(false) }

    Column(modifier = Modifier.animateContentSize(animationSpec = tween(300))) {
        // ── Card layer ─────────────────────────────────────────────────
        AnimatedVisibility(
            visible = state == ItemState.CARD,
            exit = slideOutHorizontally(animationSpec = tween(250)) { it }
                + fadeOut(animationSpec = tween(150))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(cardSwipeOffset.roundToInt(), 0) }
                    .onSizeChanged { cardWidthPx = it.width.toFloat() }
                    .then(
                        if (state == ItemState.CARD && !cardDismissed) {
                            Modifier.pointerInput(Unit) {
                                detectHorizontalDragGestures(
                                    onDragEnd = {
                                        val threshold = cardWidthPx * 0.30f
                                        if (cardDragOffset < -threshold) {
                                            scope.launch {
                                                cardDragOffset = -cardWidthPx
                                                delay(220)
                                                cardDismissed = true
                                                state = ItemState.SNACKBAR
                                                onDelete()
                                            }
                                        } else {
                                            cardDragOffset = 0f
                                        }
                                    }
                                ) { _, dragAmount ->
                                    cardDragOffset = (cardDragOffset + dragAmount)
                                        .coerceIn(-cardWidthPx, 0f)
                                }
                            }
                        } else Modifier
                    )
            ) {
                TrackCardContent(summary, dateFormat, onUpdateTrack, onShareGpx)
            }
        }

        // ── Snackbar layer ──────────────────────────────────────────────
        AnimatedVisibility(
            visible = state == ItemState.SNACKBAR,
            enter = slideInHorizontally(animationSpec = tween(250)) { it }
                + fadeIn(animationSpec = tween(150)),
            exit = slideOutHorizontally(animationSpec = tween(250)) { it }
                + fadeOut(animationSpec = tween(150))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(snackSwipeOffset.roundToInt(), 0) }
                    .onSizeChanged { snackWidthPx = it.width.toFloat() }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                val threshold = snackWidthPx * 0.30f
                                if (snackDragOffset < -threshold) {
                                    scope.launch {
                                        snackDragOffset = -snackWidthPx
                                        delay(220)
                                        onPermanentDelete()
                                        state = ItemState.DELETED
                                    }
                                } else {
                                    snackDragOffset = 0f
                                }
                            }
                        ) { _, dragAmount ->
                            snackDragOffset = (snackDragOffset + dragAmount)
                                .coerceIn(-snackWidthPx, 0f)
                        }
                    }
            ) {
                SnackbarSlot(summary.name, onUndo = {
                    scope.launch {
                        state = ItemState.CARD
                        cardDismissed = false
                        cardDragOffset = 0f
                        snackDragOffset = 0f
                        onUndo()
                    }
                })
            }
        }
    }
}

/** Inline snackbar with 48–80dp height, card-bg × 0.75 alpha. */
@Composable
private fun SnackbarSlot(trackName: String, onUndo: () -> Unit) {
    val bgColor = Color(AppConfig.uiSettingsCardBackground)
        .copy(alpha = 0.102f * 0.75f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp, max = 80.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "\u201C$trackName\u201D deleted",
            color = Color(AppConfig.uiSettingsTextPrimary),
            fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        TextButton(onClick = onUndo) {
            Text("Undo", color = Color(AppConfig.uiSettingsAccent),
                fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
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
private fun TrackCardContent(
    summary: TrackSummary,
    dateFormat: SimpleDateFormat,
    onUpdateTrack: (String, name: String?, comment: String?, visibleOnMap: Boolean?) -> Unit,
    onShareGpx: (String) -> Unit
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
    val visible = summary.visibleOnMap

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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(AppConfig.uiSettingsCardBackground))
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
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButton(
                    onClick = { onUpdateTrack(summary.id, null, null, !visible) },
                    modifier = Modifier.size(36.dp)
                ) {
                    if (visible) VisibilityIcon(1f) else VisibilityOffIcon(0.5f)
                }
                IconButton(
                    onClick = { onShareGpx(summary.id) },
                    modifier = Modifier.size(36.dp)
                ) { ShareIcon() }
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
            Box(Modifier.weight(1f)) { StatCell(stringResource(R.string.track_stat_idle), fmtDuration(summary.pausedDurationSec)) }
            Box(Modifier.weight(1f)) { StatCell(stringResource(R.string.track_stat_max), fmtKnFromMps(summary.fastestSpeedMps)) }
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
            .background(Color(AppConfig.uiSettingsCardBackground))
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
            Box(Modifier.weight(1f)) { StatCell(stringResource(R.string.track_stat_nav), fmtDuration(liveState.elapsedSeconds)) }
            Box(Modifier.weight(1f)) { StatCell(stringResource(R.string.track_stat_avg), fmtKn(liveState.avgSpeedKn)) }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.weight(1f)) { StatCell(stringResource(R.string.track_stat_dist), fmtNm(liveState.distanceNm)) }
            Box(Modifier.weight(1f)) { StatCell(stringResource(R.string.track_stat_idle), fmtDuration(0)) }
            Box(Modifier.weight(1f)) { StatCell(stringResource(R.string.track_stat_max), fmtKn(liveState.maxSpeedKn)) }
        }
    }
}

/** Which field is being edited — ensures mutual exclusion. */
private enum class EditingField { NAME, COMMENT }

// ── Canvas icon composables (28dp, matching ICON_SIZE_DP) ───────────────────

@Composable
private fun VisibilityIcon(alpha: Float) {
    Canvas(modifier = Modifier.size(28.dp)) {
        val w = size.width; val h = size.height; val c = ButtonColors.icon
        val cx = w / 2f; val cy = h / 2f; val rx = w * 0.40f; val ry = h * 0.22f
        drawArc(c, -180f, 180f, false, alpha = alpha,
            topLeft = Offset(cx - rx, cy - ry), size = Size(rx * 2, ry * 2),
            style = Stroke(w * 0.12f))
        drawArc(c, 0f, 180f, false, alpha = alpha,
            topLeft = Offset(cx - rx, cy - ry), size = Size(rx * 2, ry * 2),
            style = Stroke(w * 0.12f))
        drawCircle(c, w * 0.08f, Offset(cx, cy), alpha)
    }
}

@Composable
private fun VisibilityOffIcon(alpha: Float) {
    Canvas(modifier = Modifier.size(28.dp)) {
        val w = size.width; val h = size.height; val c = ButtonColors.icon
        val cx = w / 2f; val cy = h / 2f; val rx = w * 0.40f; val ry = h * 0.22f
        drawArc(c, -180f, 180f, false, alpha = alpha,
            topLeft = Offset(cx - rx, cy - ry), size = Size(rx * 2, ry * 2),
            style = Stroke(w * 0.12f))
        drawArc(c, 0f, 180f, false, alpha = alpha,
            topLeft = Offset(cx - rx, cy - ry), size = Size(rx * 2, ry * 2),
            style = Stroke(w * 0.12f))
        drawLine(c, Offset(w * 0.15f, h * 0.85f), Offset(w * 0.85f, h * 0.15f),
            w * 0.10f, cap = StrokeCap.Round, alpha = alpha)
    }
}

@Composable
private fun ShareIcon() {
    Canvas(modifier = Modifier.size(28.dp)) {
        val w = size.width; val h = size.height; val c = ButtonColors.icon
        val cx = w / 2f; val cy = h / 2f
        val stemEnd = Offset(cx + w * 0.25f, cy - h * 0.30f)
        drawLine(c, Offset(cx, cy), stemEnd, w * 0.12f, cap = StrokeCap.Round)
        val headLen = w * 0.15f
        drawLine(c, stemEnd, Offset(stemEnd.x - headLen, stemEnd.y + headLen * 0.4f),
            w * 0.10f, cap = StrokeCap.Round)
        drawLine(c, stemEnd, Offset(stemEnd.x - headLen * 0.4f, stemEnd.y - headLen),
            w * 0.10f, cap = StrokeCap.Round)
        drawRoundRect(c, topLeft = Offset(cx - w * 0.10f, cy + h * 0.05f),
            size = Size(w * 0.35f, h * 0.30f),
            cornerRadius = CornerRadius(3f, 3f), alpha = 0.7f,
            style = Stroke(w * 0.10f))
    }
}

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
