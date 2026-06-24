package ykws.android.maro.ui.map

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.MarkerGeometry
import ykws.android.maro.data.model.markers.UserMarker
import ykws.android.maro.spatial.MatchResult
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.spatial.TieredMatchResult

// ─────────────────────────────────────────────────────────────────────────────
// Public composable
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Animated drawer for marker viewing and match results.
 * Creating/Editing is now handled by [WizardDrawer].
 *
 * Portrait: slides up from bottom, covering the dashboard area.
 * Landscape: slides in from the left, covering the dashboard area.
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

    AnimatedVisibility(
        visible = isOpen,
        enter = if (isLandscape) slideInHorizontally { -it } + fadeIn()
        else slideInVertically { it } + fadeIn(),
        exit = if (isLandscape) slideOutHorizontally { -it } + fadeOut()
        else slideOutVertically { it } + fadeOut()
    ) {
        // Drawer panel — no scrim, close only via back/BackHandler/Cancel
        Box(
            modifier = Modifier
                .fillMaxWidth(if (isLandscape) 0.75f else 1f)
                .then(
                    if (isLandscape) Modifier.height(androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp)
                    else Modifier.heightIn(max = 350.dp).wrapContentHeight()
                )
                .clip(
                    if (isLandscape) RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
                    else RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                )
                .background(ComposeColor(AppConfig.uiSettingsBackground))
        ) {
            when (val state = drawerState) {
                is MarkerDrawerState.Viewing -> ViewingContent(viewModel, state.markerId, onClose)
                is MarkerDrawerState.MatchResult -> MatchResultContent(viewModel, onClose)
                else -> { /* Creating/Editing handled by WizardDrawer */ }
            }
        }
    }

    // Back handler when drawer is open
    if (isOpen) {
        BackHandler { onClose() }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Viewing content
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ViewingContent(viewModel: MarkersViewModel, markerId: String, onClose: () -> Unit) {
    val markers by viewModel.markers.collectAsState()
    val marker = markers.find { it.id == markerId }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        DrawerHeader(title = marker?.name ?: "Marker", onClose = onClose)

        Spacer(Modifier.height(12.dp))
        Text(
            text = "MARKER DETAILS",
            color = ComposeColor(AppConfig.uiSettingsAccent),
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(8.dp))

        if (marker != null) {
            // Geometry + proximity inline
            val geometryDesc = when (val g = marker.geometry) {
                is MarkerGeometry.Pin -> "Pin at ${"%.4f".format(g.position.latitude)}, ${"%.4f".format(g.position.longitude)}"
                is MarkerGeometry.Circle -> "Circle — radius ${g.radiusM.toLong()} m"
                is MarkerGeometry.Corridor -> "Corridor — width ${g.widthM.toLong()} m"
            }
            val proximityM = marker.proximityOverrideM ?: when (marker.geometry) {
                is MarkerGeometry.Pin -> AppConfig.markerProximityPinM
                is MarkerGeometry.Circle -> marker.geometry.radiusM * AppConfig.markerProximityZoneMultiplier
                is MarkerGeometry.Corridor -> marker.geometry.widthM * AppConfig.markerProximityZoneMultiplier
            }
            Text(
                "$geometryDesc  ·  proximity ${proximityM.toLong()} m",
                color = ComposeColor(AppConfig.uiSettingsTextPrimary),
                fontSize = 13.sp
            )

            if (marker.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    marker.description,
                    color = ComposeColor(AppConfig.uiSettingsTextPrimary),
                    fontSize = 13.sp
                )
            }
        } else {
            Text(
                "Marker not found",
                color = ComposeColor(AppConfig.uiSettingsTextMuted),
                fontSize = 13.sp
            )
        }

        Spacer(Modifier.height(6.dp))

        // Edit + Close buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onClose,
                modifier = Modifier.weight(1f).height(40.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ComposeColor(AppConfig.uiSettingsSwitchTrackInactive)
                ),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("Close", fontSize = 13.sp, color = ComposeColor(AppConfig.uiSettingsTextPrimary))
            }
            Button(
                onClick = {
                    viewModel.closeDrawer()
                    viewModel.startWizard(markerId)
                },
                modifier = Modifier.weight(1f).height(40.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ComposeColor(AppConfig.buttonActionBgColor)
                ),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("Edit", fontSize = 13.sp, color = ComposeColor(AppConfig.buttonActionIconColor))
            }
        }

        Spacer(Modifier.height(4.dp))

        // Delete button with confirmation
        var showDeleteConfirm by remember { mutableStateOf(false) }
        HorizontalDivider(color = ComposeColor(AppConfig.uiSettingsTextMuted).copy(alpha = 0.3f))
        Spacer(Modifier.height(4.dp))
        Button(
            onClick = { showDeleteConfirm = true },
            modifier = Modifier.fillMaxWidth().height(40.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ComposeColor(AppConfig.semanticDanger).copy(alpha = 0.15f)
            ),
            shape = RoundedCornerShape(6.dp)
        ) {
            Text("Delete Marker", fontSize = 13.sp, color = ComposeColor(AppConfig.semanticDanger))
        }

        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("Delete Marker") },
                text = { Text("Delete \"${marker?.name ?: "this marker"}\"? This cannot be undone.") },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteMarker(markerId)
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

        Spacer(Modifier.height(4.dp))
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

        val matches = result?.matches ?: emptyList()
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
            matches.forEach { match ->
                MatchResultRow(match, indent = 0)
            }
        }

        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun MatchResultRow(match: MatchResult, indent: Int) {
    val prefix = "  ".repeat(indent)
    when (match) {
        is MatchResult.ZoneMatch -> {
            val g = match.marker.geometry
            val geometryDesc = when (g) {
                is MarkerGeometry.Circle -> "circle ${g.radiusM.toLong()}m"
                is MarkerGeometry.Corridor -> "corridor ${g.widthM.toLong()}m"
                is MarkerGeometry.Pin -> "pin"
            }
            Text(
                "${prefix}\u2514\u2500 ${match.marker.name}  \u00B7  $geometryDesc  \u00B7  inside zone",
                color = ComposeColor(AppConfig.uiSettingsTextPrimary),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            match.children.forEach { child ->
                MatchResultRow(child, indent + 1)
            }
        }
        is MatchResult.ProximityMatch -> {
            val g = match.marker.geometry
            val geometryDesc = when (g) {
                is MarkerGeometry.Circle -> "circle ${g.radiusM.toLong()}m"
                is MarkerGeometry.Corridor -> "corridor ${g.widthM.toLong()}m"
                is MarkerGeometry.Pin -> "pin"
            }
            Text(
                "${prefix}\uD83D\uDCCD ${match.marker.name}  \u00B7  $geometryDesc  \u00B7  ${"%.0f".format(match.distanceM)} m",
                color = ComposeColor(AppConfig.uiSettingsTextPrimary),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
        is MatchResult.NoMatch -> { /* unreachable */ }
    }
    Spacer(Modifier.height(2.dp))
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared components
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Drawer header with optional action buttons.
 *
 * @param title   Header title text.
 * @param onClose Back button handler.
 * @param actions Optional composable for right-aligned action buttons.
 */
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
            modifier = Modifier.weight(1f)
        )
        if (actions != null) {
            actions()
        }
    }
}
