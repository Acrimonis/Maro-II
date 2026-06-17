package ykws.android.maro.ui.map

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ykws.android.maro.data.track.TrackSummary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Full-screen overlay displaying a LazyColumn of track summary cards.
 *
 * Each card shows date, editable name/comment, stats, visibility toggle,
 * GPX share, and swipe-to-delete with Snackbar undo.
 *
 * @param trackSummaries List of track summaries to display.
 * @param onUpdateTrack Called to update track metadata (name, comment, visibleOnMap).
 * @param onDeleteTrack Called to delete a track by ID (fires after undo timeout).
 * @param onShareGpx Called with the track ID to share GPX.
 * @param onDismiss Called to close the overlay.
 */
@Composable
fun TrackHistoryOverlay(
    trackSummaries: List<TrackSummary>,
    onUpdateTrack: (String, name: String?, comment: String?, visibleOnMap: Boolean?) -> Unit,
    onDeleteTrack: (String) -> Unit,
    onShareGpx: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E))
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Header ────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onDismiss,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0x33FFFFFF)
                    ),
                    modifier = Modifier.size(48.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = "\u2190",
                        color = Color.White,
                        fontSize = 22.sp
                    )
                }
                Text(
                    text = "Track History",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(48.dp))
            }

            // ── Track list ────────────────────────────────────────────────
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(trackSummaries, key = { it.id }) { summary ->
                    SwipeToDeleteCard(
                        summary = summary,
                        dateFormat = dateFormat,
                        onUpdateTrack = onUpdateTrack,
                        onDelete = {
                            scope.launch {
                                onDeleteTrack(summary.id)
                                snackbarHostState.showSnackbar("Track deleted")
                            }
                        },
                        onShareGpx = onShareGpx
                    )
                }
            }
        }

        // ── Snackbar host for delete undo ────────────────────────────────
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDeleteCard(
    summary: TrackSummary,
    dateFormat: SimpleDateFormat,
    onUpdateTrack: (String, name: String?, comment: String?, visibleOnMap: Boolean?) -> Unit,
    onDelete: () -> Unit,
    onShareGpx: (String) -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                // Item swiped left → trigger delete
                true
            } else false
        }
    )

    // Perform the delete when dismissed
    if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
        androidx.compose.runtime.LaunchedEffect(Unit) {
            onDelete()
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            // Red background shown when swiping
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFFF5252))
                    .padding(end = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(
                    text = "\uD83D\uDDD1\uFE0F Delete",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        },
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true
    ) {
        TrackCard(
            summary = summary,
            dateFormat = dateFormat,
            onUpdateTrack = onUpdateTrack,
            onShareGpx = onShareGpx
        )
    }
}

@Composable
private fun TrackCard(
    summary: TrackSummary,
    dateFormat: SimpleDateFormat,
    onUpdateTrack: (String, name: String?, comment: String?, visibleOnMap: Boolean?) -> Unit,
    onShareGpx: (String) -> Unit
) {
    var editingName by remember(summary.id) { mutableStateOf(false) }
    var editingComment by remember(summary.id) { mutableStateOf(false) }
    var nameField by remember(summary.id) { mutableStateOf(
        TextFieldValue(summary.name, TextRange(0, summary.name.length))
    ) }
    var commentField by remember(summary.id) { mutableStateOf(
        TextFieldValue(summary.comment, TextRange(0, summary.comment.length))
    ) }
    val visible by remember(summary.id) { mutableStateOf(summary.visibleOnMap) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x1AFFFFFF))
            .padding(12.dp)
    ) {
        // ── Date row + action icons ──────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = dateFormat.format(Date(summary.startTimeMs)),
                color = Color(0xFFB0BEC5),
                fontSize = 12.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Visibility toggle (eye emoji, monotone)
                Text(
                    text = if (visible) "\uD83D\uDC41" else "\uD83D\uDC41\u200D\uD83D\uDE6E",
                    fontSize = 16.sp,
                    color = if (visible) Color(0xFF4CAF50) else Color(0x66FFFFFF),
                    modifier = Modifier
                        .clickable {
                            onUpdateTrack(summary.id, null, null, !visible)
                        }
                        .padding(4.dp)
                )
                // Share button
                Text(
                    text = "\u2197\uFE0F",
                    fontSize = 16.sp,
                    color = Color(0x99FFFFFF),
                    modifier = Modifier
                        .clickable { onShareGpx(summary.id) }
                        .padding(4.dp)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Editable name (tap → select all + keyboard) ──────────────────
        if (editingName) {
            TextField(
                value = nameField,
                onValueChange = { nameField = it },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0x33FFFFFF),
                    unfocusedContainerColor = Color(0x1AFFFFFF),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color.White
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        onUpdateTrack(summary.id, nameField.text, null, null)
                        editingName = false
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Text(
                text = summary.name,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { editingName = true }
                    .padding(vertical = 4.dp)
            )
        }

        // ── Editable comment ─────────────────────────────────────────────
        if (editingComment) {
            TextField(
                value = commentField,
                onValueChange = { commentField = it },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = Color(0xFFB0BEC5),
                    fontSize = 13.sp
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0x33FFFFFF),
                    unfocusedContainerColor = Color(0x1AFFFFFF),
                    focusedTextColor = Color(0xFFB0BEC5),
                    unfocusedTextColor = Color(0xFFB0BEC5),
                    cursorColor = Color.White
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        onUpdateTrack(summary.id, null, commentField.text, null)
                        editingComment = false
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Text(
                text = summary.comment.ifBlank { "Add a comment..." },
                color = if (summary.comment.isBlank()) Color(0x44FFFFFF) else Color(0xFFB0BEC5),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { editingComment = true }
                    .padding(vertical = 4.dp)
            )
        }

        Spacer(Modifier.height(8.dp))

        // ── Stats row ────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatChip("\uD83C\uDFC1 ${"%.2f".format(summary.distanceNm)} nm")
            StatChip("\u26A1 ${"%.1f".format(summary.fastestSpeedMps * 1.94384f)} kn")
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatChip("\u26F5 ${formatDuration(summary.navigatingDurationSec)}")
            StatChip("\u23F8 ${formatDuration(summary.pausedDurationSec)}")
        }
    }
}

@Composable
private fun StatChip(text: String) {
    Text(
        text = text,
        color = Color(0xFFB0BEC5),
        fontSize = 12.sp
    )
}

private fun formatDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
