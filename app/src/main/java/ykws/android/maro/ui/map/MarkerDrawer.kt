package ykws.android.maro.ui.map

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.MarkerGeometry
import ykws.android.maro.data.model.markers.UserMarker
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.spatial.WhereAmIMatch
import ykws.android.maro.spatial.WhereAmIResult

// ─────────────────────────────────────────────────────────────────────────────
// Public composable
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Pure-content drawer for marker viewing and match results.
 *
 * Animation and shadow are provided by [OverlayLayer].
 * Creating/Editing is now handled by [WizardDrawer].
 *
 * @param viewModel     The [MarkersViewModel] driving the drawer state.
 * @param isLandscape   Whether the device is in landscape orientation.
 * @param onClose       Called when the drawer is dismissed.
 * @param boatPosition  Current boat position for distance-to-boat display.
 */
@Composable
fun MarkerDrawer(
    viewModel: MarkersViewModel,
    isLandscape: Boolean,
    onClose: () -> Unit,
    boatPosition: LatLng? = null
) {
    val drawerState by viewModel.drawerState.collectAsState()
    val isOpen = drawerState !is MarkerDrawerState.Hidden

    val panelShape = if (isLandscape) RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
        else RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)

    // ── Panel ──
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(panelShape)
            .background(ComposeColor(AppConfig.uiSettingsBackground))
    ) {
        when (drawerState) {
            is MarkerDrawerState.Viewing -> ViewingContent(viewModel, onClose, boatPosition)
            is MarkerDrawerState.MatchResult -> MatchResultContent(viewModel, onClose)
            else -> { /* Creating/Editing handled by WizardDrawer */ }
        }
    }

    // Back handler when drawer is open
    if (isOpen) {
        BackHandler { onClose() }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Viewing content — card layout redesign
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ViewingContent(
    viewModel: MarkersViewModel,
    onClose: () -> Unit,
    boatPosition: LatLng? = null
) {
    val markers by viewModel.markers.collectAsState()
    val selectedIds by viewModel.selectedMarkerIds.collectAsState()
    val selectedIndex by viewModel.selectedMarkerIndex.collectAsState()

    val currentId = selectedIds.getOrNull(selectedIndex)
    val marker = currentId?.let { id -> markers.find { it.id == id } }
    val hasMultiple = selectedIds.size > 1

    var showDeleteConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        // ── Header: back + title + edit/delete icons inline right-aligned ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(ComposeColor(AppConfig.uiSettingsSwitchTrackInactive))
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Close",
                    tint = ComposeColor(AppConfig.uiSettingsTextPrimary),
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(16.dp))
            Text(
                text = marker?.name ?: "Marker",
                color = ComposeColor(AppConfig.uiSettingsTextPrimary),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (marker != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(
                        onClick = {
                            viewModel.closeDrawer()
                            viewModel.startWizard(marker.id)
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Edit",
                            tint = ButtonColors.icon,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete",
                            tint = ButtonColors.icon,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        if (marker != null) {
            Spacer(Modifier.height(8.dp))

            // ── Info card with left accent bar + content ──────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ComposeColor(AppConfig.uiCardBackground))
            ) {
                // Left accent bar — same rendering as list items
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(ComposeColor(MarkerColors.of(marker.colorIndex)))
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                // Geometry desc — numeric-only compact format
                Text(
                    text = markerFormatText(marker),
                    color = ComposeColor(AppConfig.uiSettingsTextPrimary),
                    fontSize = 14.sp
                )

                // Direction + distance (if boatPosition available)
                if (boatPosition != null) {
                    val markerPos = when (val g = marker.geometry) {
                        is MarkerGeometry.Pin -> g.position
                        is MarkerGeometry.Circle -> g.center
                        is MarkerGeometry.Corridor -> g.p1
                    }
                    val bearing = SpatialOperations.initialBearing(boatPosition, markerPos)
                    val distM = SpatialOperations.haversine(markerPos, boatPosition)
                    val dir = cardinalDirection(bearing)
                    val distStr = if (distM < 1000.0) "${distM.toLong()} m"
                        else "%.1f km".format(distM / 1000.0)

                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "$dir of boat - $distStr",
                        color = ComposeColor(AppConfig.uiSettingsTextPrimary),
                        fontSize = 13.sp
                    )
                }

                // Description
                if (marker.description.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = ComposeColor(AppConfig.uiSettingsDivider)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = marker.description,
                        color = ComposeColor(AppConfig.uiSettingsTextMuted),
                        fontSize = 13.sp
                    )
                }

                // Page counter (bottom-right of card)
                if (hasMultiple) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${selectedIndex + 1}/${selectedIds.size}",
                        color = ComposeColor(AppConfig.uiSettingsTextPrimary),
                        fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Right
                    )
                }
            }  // closes inner Column
            }  // closes Row (info card)

            // ── Previous/Next navigation (wizard-style pills) ─────────────
            if (hasMultiple) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = ComposeColor(AppConfig.uiSettingsDivider))
                Spacer(Modifier.height(8.dp))
                val accentBg = ComposeColor(AppConfig.uiSettingsAccent)
                val accentFg = ComposeColor(AppConfig.uiSettingsTextPrimary)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(accentBg)
                            .clickable { viewModel.viewPreviousMarker() }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Previous",
                            color = accentFg,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(accentBg)
                            .clickable { viewModel.viewNextMarker() }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Next",
                            color = accentFg,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        } else {
            Spacer(Modifier.height(12.dp))
            Text(
                "Marker not found",
                color = ComposeColor(AppConfig.uiSettingsTextMuted),
                fontSize = 13.sp
            )
        }

        Spacer(Modifier.height(4.dp))

        // ── Delete confirmation dialog ───────────────────────────────────
        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("Delete Marker") },
                text = { Text("Delete \"${marker?.name ?: "this marker"}\"? This cannot be undone.") },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteConfirm = false
                        currentId?.let { viewModel.deleteMarker(it) }
                    }) {
                        Text("Delete", color = ComposeColor(AppConfig.semanticDanger))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Match result content
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MatchResultContent(viewModel: MarkersViewModel, onClose: () -> Unit) {
    val result by viewModel.matchResult.collectAsState()
    val allMarkers by viewModel.markers.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        DrawerHeader(title = "Where Am I?", onClose = onClose)

        Spacer(Modifier.height(12.dp))
        Text(
            text = "MATCH RESULTS",
            color = ComposeColor(AppConfig.uiSettingsAccent),
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(8.dp))

        val matches = result?.allMatches ?: emptyList()
        if (matches.isEmpty()) {
            if (allMarkers.isEmpty()) {
                Text(
                    "No markers placed yet",
                    color = ComposeColor(AppConfig.uiSettingsTextMuted),
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Create a marker first, then tap the boat icon to find nearby markers.",
                    color = ComposeColor(AppConfig.uiSettingsTextMuted).copy(alpha = 0.6f),
                    fontSize = 11.sp
                )
            } else {
                Text(
                    "No markers in range",
                    color = ComposeColor(AppConfig.uiSettingsTextMuted),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Land may be blocking all markers, or you are too far away.",
                    color = ComposeColor(AppConfig.uiSettingsTextMuted).copy(alpha = 0.6f),
                    fontSize = 11.sp
                )
            }
        } else {
            val sentence = matches.joinToString(", ") { match ->
                when (match) {
                    is WhereAmIMatch.ZoneMatch -> match.marker.name
                    is WhereAmIMatch.ProximityMatch -> "${cardinalDirection(match.bearingDeg)} of ${match.marker.name}"
                }
            }
            Text(
                text = sentence,
                color = ComposeColor(AppConfig.uiSettingsTextPrimary),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(Modifier.height(6.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared components
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DrawerHeader(
    title: String,
    onClose: () -> Unit,
    actions: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(ComposeColor(AppConfig.uiSettingsSwitchTrackInactive))
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Close",
                tint = ComposeColor(AppConfig.uiSettingsTextPrimary),
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = title,
            color = ComposeColor(AppConfig.uiSettingsTextPrimary),
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (actions != null) {
            actions()
        }
    }
}

/** Converts a bearing (0-360°) to a cardinal direction: N, NE, E, SE, S, SW, W, NW. */
private fun cardinalDirection(bearingDeg: Double): String {
    val normalized = ((bearingDeg % 360) + 360) % 360
    return when {
        normalized < 22.5 || normalized >= 337.5 -> "N"
        normalized < 67.5 -> "NE"
        normalized < 112.5 -> "E"
        normalized < 157.5 -> "SE"
        normalized < 202.5 -> "S"
        normalized < 247.5 -> "SW"
        normalized < 292.5 -> "W"
        else -> "NW"
    }
}

/** Numeric-only compact format: "📌 / 200", "⭕ / 200 / 200", "📏 / 100 / 200". */
private fun markerFormatText(marker: UserMarker): String {
    val proximityM = marker.proximityOverrideM
        ?: when (val g = marker.geometry) {
            is MarkerGeometry.Pin -> AppConfig.markerProximityPinM
            is MarkerGeometry.Circle -> g.radiusM * AppConfig.markerProximityZoneMultiplier
            is MarkerGeometry.Corridor -> g.widthM * AppConfig.markerProximityZoneMultiplier
        }
    val prox = proximityM.toLong().toString()
    return when (marker.geometry) {
        is MarkerGeometry.Pin -> "\uD83D\uDCCC / $prox"
        is MarkerGeometry.Circle -> {
            val r = marker.geometry.radiusM.toLong().toString()
            "\u2B55 / $r / $prox"
        }
        is MarkerGeometry.Corridor -> {
            val w = marker.geometry.widthM.toLong().toString()
            "\uD83D\uDCCF / $w / $prox"
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Color picker — 4×4 swatch grid from MarkerColors.all
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MarkerColorPickerDialog(
    currentColorIndex: Int?,
    onColorSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MarkerColors.all

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Marker Color") },
        text = {
            Column {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.height(216.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(16) { index ->
                        val color = colors[index]
                        val isSelected = index == currentColorIndex
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(ComposeColor(color))
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) ComposeColor(AppConfig.uiSettingsAccent)
                                        else ComposeColor(AppConfig.uiSettingsDivider),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { onColorSelected(index) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
