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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.markers.MarkerGeometry
import ykws.android.maro.data.model.markers.UserMarker
import kotlin.math.roundToInt

/**
 * Full-screen overlay displaying a LazyColumn of user marker cards with
 * swipe-to-delete, following the track-list paradigm from
 * `FEAT_PLN_BoatTrace_TrackList_Design.md`.
 *
 * **P8:** Each row has an explicit **Edit** button. Tap-on-row no longer edits.
 *
 * **Swipe-to-delete lifecycle:**
 * 1. Swipe card left → card slides out left, snackbar slides in from right
 * 2. Snackbar is inline (same list slot), "{name} deleted" + Undo button
 * 3. Tap Undo → snackbar shrinks, card slides back in from right
 * 4. Swipe snackbar left → permanent delete, snackbar slides out left
 * 5. Close panel → all pending deletes commit permanently
 *
 * **Empty state** (UI review G1): centered pin icon + "No markers yet" +
 * "Create First Marker" button.
 *
 * @param markers              List of all user markers.
 * @param onTapMarker          Called when user taps a marker card → opens drawer in viewing mode.
 * @param onEditMarker         Called when user taps Edit button → opens wizard in edit mode.
 * @param onSoftDeleteMarker   Called to soft-delete a marker (UI removal, undoable).
 * @param onUndoDeleteMarker   Called to undo a soft-delete.
 * @param onPermanentDelete    Called to permanently delete a marker.
 * @param onCommitPendingDeletes Called when panel closes to persist pending deletes.
 * @param onCreateFirst        Called when user taps "Create First Marker" from empty state.
 * @param onDismiss            Called to close the management page.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MarkerManagementOverlay(
    markers: List<UserMarker>,
    onTapMarker: (String) -> Unit,
    onEditMarker: (String) -> Unit = {},
    onSoftDeleteMarker: (String) -> Unit,
    onUndoDeleteMarker: (String) -> Unit,
    onPermanentDelete: (String) -> Unit,
    onCommitPendingDeletes: () -> Unit,
    onCreateFirst: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pendingDeletes = remember { mutableListOf<String>() }

    BackHandler {
        onCommitPendingDeletes()
        onDismiss()
    }

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
                            onCommitPendingDeletes()
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
                        text = "Markers \u00B7 ${markers.size}",
                        color = Color(AppConfig.uiSettingsTextPrimary),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Empty state ────────────────────────────────────────────
            if (markers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Centered pin icon
                        CenteredPinIcon(sizeDp = 64)
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "No markers yet",
                            color = Color(AppConfig.uiSettingsTextMuted),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onCreateFirst,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(AppConfig.buttonActionBgColor)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                "Create First Marker",
                                color = Color(AppConfig.buttonActionIconColor),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            } else {
                // ── Section label ──────────────────────────────────────
                Text(
                    text = "YOUR MARKERS",
                    color = Color(AppConfig.uiSettingsAccent),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))

                // ── Marker list ────────────────────────────────────────
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(markers, key = { it.id }) { marker ->
                        SwipeToDeleteMarkerCard(
                            marker = marker,
                            modifier = Modifier.animateItemPlacement(),
                            onTap = { onTapMarker(marker.id) },
                            onEdit = { onEditMarker(marker.id) },
                            onDelete = {
                                pendingDeletes.add(marker.id)
                                onSoftDeleteMarker(marker.id)
                            },
                            onUndo = {
                                pendingDeletes.remove(marker.id)
                                onUndoDeleteMarker(marker.id)
                            },
                            onPermanentDelete = {
                                pendingDeletes.remove(marker.id)
                                onPermanentDelete(marker.id)
                            }
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Swipe-to-delete state machine
// ─────────────────────────────────────────────────────────────────────────────

private enum class MarkerItemState { CARD, SNACKBAR, DELETED }

@Composable
private fun SwipeToDeleteMarkerCard(
    marker: UserMarker,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onUndo: () -> Unit,
    onPermanentDelete: () -> Unit
) {
    var state by remember { mutableStateOf(MarkerItemState.CARD) }
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

    Column(modifier = modifier.animateContentSize(animationSpec = tween(300))) {
        // ── Card layer ─────────────────────────────────────────────────
        AnimatedVisibility(
            visible = state == MarkerItemState.CARD,
            enter = slideInHorizontally(animationSpec = tween(250)) { it },
            exit = slideOutHorizontally(animationSpec = tween(250)) { it }
                + fadeOut(animationSpec = tween(150))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(cardSwipeOffset.roundToInt(), 0) }
                    .onSizeChanged { cardWidthPx = it.width.toFloat() }
                    .then(
                        if (state == MarkerItemState.CARD && !cardDismissed) {
                            Modifier.pointerInput(Unit) {
                                detectHorizontalDragGestures(
                                    onDragEnd = {
                                        val threshold = cardWidthPx * 0.30f
                                        if (cardDragOffset < -threshold) {
                                            scope.launch {
                                                cardDragOffset = -cardWidthPx
                                                delay(220)
                                                cardDismissed = true
                                                state = MarkerItemState.SNACKBAR
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
                    .clip(RoundedCornerShape(12.dp))
            ) {
                MarkerCardContent(marker, onTap = onTap, onEdit = onEdit)
            }
        }

        // ── Snackbar layer ──────────────────────────────────────────────
        AnimatedVisibility(
            visible = state == MarkerItemState.SNACKBAR,
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
                                        state = MarkerItemState.DELETED
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
                MarkerSnackbarSlot(marker.name, onUndo = {
                    scope.launch {
                        state = MarkerItemState.CARD
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

// ─────────────────────────────────────────────────────────────────────────────
// Marker card content
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MarkerCardContent(
    marker: UserMarker,
    onTap: () -> Unit,
    onEdit: () -> Unit
) {
    val geometryDesc = when (val g = marker.geometry) {
        is MarkerGeometry.Pin -> "Pin"
        is MarkerGeometry.Circle -> "Circle \u00B7 ${g.radiusM.toLong()} m"
        is MarkerGeometry.Corridor -> "Corridor \u00B7 ${g.widthM.toLong()} m"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(AppConfig.uiSettingsCardBackground))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = marker.name,
                    color = Color(AppConfig.uiSettingsTextPrimary),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = geometryDesc,
                    color = Color(AppConfig.uiSettingsTextMuted),
                    fontSize = 12.sp
                )
            }
            // P8: Explicit Edit button
            Button(
                onClick = onEdit,
                modifier = Modifier.height(34.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(AppConfig.buttonActionBgColor)
                ),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
            ) {
                Text(
                    "Edit",
                    color = Color(AppConfig.buttonActionIconColor),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        if (marker.description.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider(
                thickness = 0.5.dp,
                color = Color(AppConfig.uiSettingsDivider)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = marker.description,
                color = Color(AppConfig.uiSettingsTextMuted),
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Inline snackbar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MarkerSnackbarSlot(markerName: String, onUndo: () -> Unit) {
    val bgColor = Color(AppConfig.uiSettingsCardBackground)
        .copy(alpha = 0.102f * 0.75f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp, max = 96.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "\u201C$markerName\u201D deleted",
            color = Color(AppConfig.uiSettingsTextPrimary),
            fontSize = 14.sp,
            maxLines = 3,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        TextButton(onClick = onUndo) {
            Text(
                "Undo",
                color = Color(AppConfig.uiSettingsAccent),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Empty state pin icon (Canvas-drawn, centered)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CenteredPinIcon(sizeDp: Int) {
    val density = LocalDensity.current
    val sizePx = with(density) { sizeDp.dp.toPx() }
    val color = Color(AppConfig.uiSettingsTextMuted).copy(alpha = 0.4f)

    androidx.compose.foundation.Canvas(
        modifier = Modifier.size(sizeDp.dp)
    ) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val strokeW = w * 0.10f

        // Pin body (circle)
        val pinRadius = w * 0.25f
        val pinCenterY = h * 0.45f
        drawCircle(
            color = color,
            radius = pinRadius,
            center = androidx.compose.ui.geometry.Offset(cx, pinCenterY),
            style = Stroke(width = strokeW)
        )

        // Pin point (downward triangle from circle bottom)
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

        // Inner dot
        drawCircle(
            color = color.copy(alpha = 0.6f),
            radius = pinRadius * 0.35f,
            center = androidx.compose.ui.geometry.Offset(cx, pinCenterY)
        )
    }
}
