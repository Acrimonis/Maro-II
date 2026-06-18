package ykws.android.maro.ui.map

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ykws.android.maro.config.AppConfig
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
    onUpdateTrack: (String, name: String?, comment: String?, visibleOnMap: Boolean?) -> Unit,
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
            StatCell("Total", fmtDuration(totalSec))
            StatCell("Nav", fmtDuration(summary.navigatingDurationSec))
            StatCell("Avg", fmtSpeed(summary.averageSpeedMps))
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            StatCell("Dist", fmtDistance(summary.distanceNm))
            StatCell("Idle", fmtDuration(summary.pausedDurationSec))
            StatCell("Max", fmtSpeed(summary.fastestSpeedMps))
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

/** Single cell in the 3-column stats grid: "Title: value" inline, title right-aligned. */
@Composable
private fun StatCell(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(0.33f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label:",
            color = Color(AppConfig.uiSettingsTextMuted),
            fontSize = 11.sp,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth(0.5f)
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = value,
            color = Color(AppConfig.uiSettingsTextPrimary),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

/** Human-readable duration: "2 h 13 min" / "53 min" / "45 s". */
private fun fmtDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> "${hours} h ${minutes} min"
        minutes > 0 -> "${minutes} min"
        else -> "${seconds} s"
    }
}

/** Speed in knots with comma decimal: "5,2 kn". */
private fun fmtSpeed(speedMps: Float): String {
    val kn = speedMps * 1.94384f
    return "${fmtDecimal(kn)} kn"
}

/** Distance in nautical miles with comma decimal: "4,2 nm". */
private fun fmtDistance(distanceNm: Float): String {
    return "${fmtDecimal(distanceNm)} nm"
}

/** Format a float with 1 decimal place using comma as separator: 4.2 → "4,2". */
private fun fmtDecimal(value: Float): String {
    val rounded = (value * 10).roundToInt()
    val whole = rounded / 10
    val tenth = rounded % 10
    return "$whole,$tenth"
}
