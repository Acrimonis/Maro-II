package ykws.android.maro.ui.map

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import ykws.android.maro.ui.components.ConfirmSheet
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ykws.android.maro.R
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.MarkerGeometry
import ykws.android.maro.data.model.markers.UserMarker
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.spatial.WhereAmIMatch
import ykws.android.maro.spatial.WhereAmIResult
import ykws.android.maro.ui.components.DrawerScaffold

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
    boatPosition: LatLng? = null,
    onRequestDelete: (String, String) -> Unit = { _, _ -> }
) {
    val drawerState by viewModel.drawerState.collectAsState()
    val isOpen = drawerState !is MarkerDrawerState.Hidden

    val panelShape = if (isLandscape) RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
        else RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)

    // Back handler when drawer is open — registered before content so the card's
    // edit-revert BackHandler (composed later) wins while editing.
    if (isOpen) {
        BackHandler { onClose() }
    }

    when (drawerState) {
        is MarkerDrawerState.Viewing -> ViewingContent(viewModel, onClose, boatPosition, panelShape, onRequestDelete, isLandscape)
        is MarkerDrawerState.MatchResult -> MatchResultContent(viewModel, onClose, boatPosition, panelShape, isLandscape)
        else -> { /* Creating/Editing handled by WizardDrawer */ }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Viewing content — card layout redesign
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ViewingContent(
    viewModel: MarkersViewModel,
    onClose: () -> Unit,
    boatPosition: LatLng? = null,
    shape: Shape,
    onRequestDelete: (String, String) -> Unit = { _, _ -> },
    isLandscape: Boolean
) {
    val markers by viewModel.markers.collectAsState()
    val selectedIds by viewModel.selectedMarkerIds.collectAsState()
    val selectedIndex by viewModel.selectedMarkerIndex.collectAsState()

    val currentId = selectedIds.getOrNull(selectedIndex)
    val marker = currentId?.let { id -> markers.find { it.id == id } }
    val hasMultiple = selectedIds.size > 1

    DrawerScaffold(
        title = marker?.name ?: "Marker",
        onClose = onClose,
        headerHorizontalPadding = 12.dp,
        scrollable = true,
        statusBarsInset = isLandscape,
        shape = shape,
        contentPadding = PaddingValues(horizontal = 12.dp),
        headerActions = {
            if (marker != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(
                        onClick = { onRequestDelete(marker.id, marker.name) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.cd_delete),
                            tint = ButtonColors.icon,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    ) {
        if (marker != null) {
            Spacer(Modifier.height(8.dp))

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
                Text(
                    text = "$dir of boat - $distStr",
                    color = ComposeColor(AppConfig.uiSettingsTextMuted),
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(6.dp))
            }

            MarkerCardContent(
                marker = marker,
                onTap = {},
                onEdit = {
                    viewModel.closeDrawer()
                    viewModel.startWizard(marker.id)
                },
                onSetIcon = { id, icon -> viewModel.setMarkerIcon(id, icon) },
                onTogglePin = { viewModel.togglePin(marker.id) },
                onUpdateText = { name, desc -> viewModel.updateMarkerText(marker.id, name, desc) },
                onLongPress = null,
                showChevron = false
            )

            // ── Previous/Next navigation (wizard-style pills) ─────────────
            if (hasMultiple) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = ComposeColor(AppConfig.uiSettingsDivider))
                Spacer(Modifier.height(8.dp))
                val accentBg = ComposeColor(AppConfig.uiSettingsAccent)
                val accentFg = ComposeColor(AppConfig.uiSettingsTextPrimary)
                val disabledAlpha = 0.35f
                val isListMode = viewModel.drawerSource == DrawerSource.LIST
                val isAtFirst = isListMode && selectedIndex == 0
                val isAtLast = isListMode && selectedIndex == selectedIds.lastIndex

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(accentBg.copy(alpha = if (isAtFirst) disabledAlpha else 1f))
                            .then(
                                if (!isAtFirst) Modifier.clickable { viewModel.viewPreviousMarker() }
                                else Modifier
                            )
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Previous",
                            color = accentFg.copy(alpha = if (isAtFirst) disabledAlpha else 1f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(accentBg.copy(alpha = if (isAtLast) disabledAlpha else 1f))
                            .then(
                                if (!isAtLast) Modifier.clickable { viewModel.viewNextMarker() }
                                else Modifier
                            )
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Next",
                            color = accentFg.copy(alpha = if (isAtLast) disabledAlpha else 1f),
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
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Match result content
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MatchResultContent(
    viewModel: MarkersViewModel,
    onClose: () -> Unit,
    boatPosition: LatLng? = null,
    shape: Shape,
    isLandscape: Boolean
) {
    val result by viewModel.matchResult.collectAsState()

    DrawerScaffold(
        title = "Where Am I?",
        onClose = onClose,
        headerHorizontalPadding = 12.dp,
        scrollable = true,
        statusBarsInset = isLandscape,
        shape = shape,
        contentPadding = PaddingValues(horizontal = 12.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        val matches = result?.allMatches ?: emptyList()
        if (matches.isEmpty()) {
            Text(
                text = "in the middle of nowhere",
                color = ComposeColor(AppConfig.uiSettingsTextMuted),
                fontSize = 14.sp,
                fontStyle = FontStyle.Italic,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                matches.forEach { match ->
                    MatchRow(match, boatPosition)
                }
            }
        }

        Spacer(Modifier.height(6.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Match result row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MatchRow(match: WhereAmIMatch, boatPosition: LatLng?) {
    val marker = when (match) {
        is WhereAmIMatch.ZoneMatch -> match.marker
        is WhereAmIMatch.LineOfSightMatch -> match.marker
    }
    val icon = marker.icon

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(6.dp))
            .background(ComposeColor(AppConfig.uiCardBackground))
    ) {
        // Left-edge color accent bar
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
            // Direction + distance (LineOfSightMatch only)
            if (match is WhereAmIMatch.LineOfSightMatch) {
                val dir = cardinalDirection(match.bearingDeg)
                val distStr = if (boatPosition != null) {
                    val dist = geometricDistanceToZone(boatPosition, marker.geometry)
                    if (dist < 1000.0) "${dist.toLong()} m" else "%.1f km".format(dist / 1000.0)
                } else null
                val text = if (distStr != null) "$dir of boat - $distStr" else dir
                Text(
                    text = text,
                    color = ComposeColor(AppConfig.uiSettingsTextMuted),
                    fontSize = 11.sp
                )
            }
            // Name + icon row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = marker.name,
                    color = ComposeColor(AppConfig.uiSettingsTextPrimary),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (icon != null) {
                    Text(
                        text = icon,
                        color = ComposeColor(AppConfig.uiSettingsTextMuted),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

/** Straight-line (flight-of-bird) distance from [boat] to the nearest edge of [geometry]. */
private fun geometricDistanceToZone(boat: LatLng, geometry: MarkerGeometry): Double {
    return when (geometry) {
        is MarkerGeometry.Pin -> SpatialOperations.haversine(boat, geometry.position)
        is MarkerGeometry.Circle -> {
            val distToCenter = SpatialOperations.haversine(boat, geometry.center)
            (distToCenter - geometry.radiusM).coerceAtLeast(0.0)
        }
        is MarkerGeometry.Corridor -> {
            val dSeg = SpatialOperations.pointToSegmentDistance(boat, geometry.p1, geometry.p2)
            (dSeg - geometry.widthM / 2.0).coerceAtLeast(0.0)
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
        title = { Text(stringResource(R.string.marker_color_title)) },
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
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
