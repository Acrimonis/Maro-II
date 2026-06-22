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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.markers.MarkerGeometry
import ykws.android.maro.data.model.markers.UserMarker
import ykws.android.maro.spatial.MatchResult
import ykws.android.maro.spatial.TieredMatchResult

// ─────────────────────────────────────────────────────────────────────────────
// Public composable
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Animated drawer for marker creation, editing, and match results.
 *
 * Portrait: slides up from bottom, covering the dashboard area.
 * Landscape: slides in from the left, covering the dashboard area.
 *
 * Reused for all marker drawer modes per the design plan §8.5.
 *
 * @param viewModel  The [MarkersViewModel] driving the drawer state.
 * @param isLandscape Whether the device is in landscape orientation.
 * @param onClose    Called when the drawer is dismissed.
 */
@Composable
fun MarkerDrawer(
    viewModel: MarkersViewModel,
    isLandscape: Boolean,
    onClose: () -> Unit
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
        // Scrim + drawer panel
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(ComposeColor.Black.copy(alpha = 0.3f))
                .clickable(onClick = onClose)
        ) {
            Box(
                modifier = Modifier
                    .align(if (isLandscape) Alignment.CenterStart else Alignment.BottomCenter)
                    .fillMaxWidth(if (isLandscape) 0.75f else 1f)
                    .then(
                        if (isLandscape) Modifier.height(androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp)
                        else Modifier.height(300.dp)
                    )
                    .clip(
                        if (isLandscape) RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
                        else RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                    )
                    .background(ComposeColor(AppConfig.uiSettingsBackground))
                    .clickable(enabled = false) {} // absorb clicks
            ) {
                when (val state = drawerState) {
                    is MarkerDrawerState.Creating -> CreationContent(viewModel, onClose)
                    is MarkerDrawerState.Editing -> EditContent(viewModel, state.markerId, onClose)
                    is MarkerDrawerState.MatchResult -> MatchResultContent(viewModel, onClose)
                    is MarkerDrawerState.Hidden -> { /* unreachable */ }
                }
            }
        }
    }

    // Back handler when drawer is open
    if (isOpen) {
        BackHandler { onClose() }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Creation content
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CreationContent(viewModel: MarkersViewModel, onClose: () -> Unit) {
    val form by viewModel.createForm.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(scrollState)
    ) {
        // Header
        DrawerHeader(title = "New Marker", onClose = onClose)

        Spacer(Modifier.height(8.dp))

        // Name field
        OutlinedTextField(
            value = form.name,
            onValueChange = { v -> viewModel.updateForm { it.copy(name = v) } },
            label = { Text("Name", color = ComposeColor(AppConfig.uiSettingsTextMuted)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = drawerTextFieldColors(),
            textStyle = androidx.compose.ui.text.TextStyle(color = ComposeColor(AppConfig.uiSettingsTextPrimary))
        )

        Spacer(Modifier.height(12.dp))

        // Type selector
        Text(
            "Type",
            color = ComposeColor(AppConfig.uiSettingsTextMuted),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(4.dp))
        TypeSelector(
            selected = form.type,
            onSelect = { t -> viewModel.updateForm { it.copy(type = t) } }
        )

        Spacer(Modifier.height(12.dp))

        // Geometry-specific inputs
        when (form.type) {
            MarkerType.CIRCLE -> {
                OutlinedTextField(
                    value = form.radiusM.toLong().toString(),
                    onValueChange = { v ->
                        val r = v.toDoubleOrNull()?.coerceAtLeast(1.0) ?: form.radiusM
                        viewModel.updateForm { it.copy(radiusM = r) }
                    },
                    label = { Text("Radius (m)", color = ComposeColor(AppConfig.uiSettingsTextMuted)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = drawerTextFieldColors(),
                    textStyle = androidx.compose.ui.text.TextStyle(color = ComposeColor(AppConfig.uiSettingsTextPrimary))
                )
                Spacer(Modifier.height(8.dp))
                // Show computed proximity
                val proximityDefault = form.radiusM * AppConfig.markerProximityZoneMultiplier
                Text(
                    "Proximity: auto (${proximityDefault.toLong()} m)",
                    color = ComposeColor(AppConfig.uiSettingsTextMuted),
                    fontSize = 12.sp
                )
            }
            MarkerType.CORRIDOR -> {
                when (form.corridorPhase) {
                    CorridorPhase.P1 -> {
                        OutlinedTextField(
                            value = form.widthM.toLong().toString(),
                            onValueChange = { v ->
                                val w = v.toDoubleOrNull()?.coerceAtLeast(1.0) ?: form.widthM
                                viewModel.updateForm { it.copy(widthM = w) }
                            },
                            label = { Text("Width (m)", color = ComposeColor(AppConfig.uiSettingsTextMuted)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            colors = drawerTextFieldColors(),
                            textStyle = androidx.compose.ui.text.TextStyle(color = ComposeColor(AppConfig.uiSettingsTextPrimary))
                        )
                        Spacer(Modifier.height(12.dp))

                        // "Set Point 2" button
                        Button(
                            onClick = {
                                viewModel.updateForm { it.copy(corridorPhase = CorridorPhase.SET_P2) }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ComposeColor(AppConfig.buttonActionBgColor)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Set Point 2", color = ComposeColor(AppConfig.buttonActionIconColor))
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Pan the map to place the second point",
                            color = ComposeColor(AppConfig.uiSettingsTextMuted),
                            fontSize = 12.sp
                        )
                    }
                    CorridorPhase.SET_P2 -> {
                        Text(
                            "Pan map → center = Point 2",
                            color = ComposeColor(AppConfig.uiSettingsTextPrimary),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.backToCorridorP1() },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ComposeColor(AppConfig.uiSettingsSwitchTrackInactive)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Back", color = ComposeColor(AppConfig.uiSettingsTextPrimary))
                            }
                            Button(
                                onClick = { /* p2 is set via map center captured externally */ },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ComposeColor(AppConfig.buttonActionBgColor)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Confirm", color = ComposeColor(AppConfig.buttonActionIconColor))
                            }
                        }
                        Text(
                            "Center the map on your desired second point, then tap Confirm",
                            color = ComposeColor(AppConfig.uiSettingsTextMuted),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    CorridorPhase.CONFIRM -> {
                        Text(
                            "Point 2 set",
                            color = ComposeColor(AppConfig.uiSettingsTextPrimary),
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Button(
                            onClick = { viewModel.backToCorridorP1() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ComposeColor(AppConfig.uiSettingsSwitchTrackInactive)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Change Point 2", color = ComposeColor(AppConfig.uiSettingsTextPrimary))
                        }
                    }
                }
                if (form.corridorPhase != CorridorPhase.P1) {
                    Spacer(Modifier.height(8.dp))
                }
                // Show computed proximity for corridor
                val proximityDefault = form.widthM * AppConfig.markerProximityZoneMultiplier
                Text(
                    "Proximity: auto (${proximityDefault.toLong()} m)",
                    color = ComposeColor(AppConfig.uiSettingsTextMuted),
                    fontSize = 12.sp
                )
            }
            MarkerType.PIN -> {
                // Show computed proximity for pin
                Text(
                    "Proximity: auto (${AppConfig.markerProximityPinM.toLong()} m)",
                    color = ComposeColor(AppConfig.uiSettingsTextMuted),
                    fontSize = 12.sp
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Proximity override field
        OutlinedTextField(
            value = form.proximityOverrideM,
            onValueChange = { v -> viewModel.updateForm { it.copy(proximityOverrideM = v) } },
            label = { Text("Proximity override (m)", color = ComposeColor(AppConfig.uiSettingsTextMuted)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            colors = drawerTextFieldColors(),
            textStyle = androidx.compose.ui.text.TextStyle(color = ComposeColor(AppConfig.uiSettingsTextPrimary))
        )

        Spacer(Modifier.height(8.dp))

        // Description field
        OutlinedTextField(
            value = form.description,
            onValueChange = { v -> viewModel.updateForm { it.copy(description = v) } },
            label = { Text("Description (optional)", color = ComposeColor(AppConfig.uiSettingsTextMuted)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = drawerTextFieldColors(),
            textStyle = androidx.compose.ui.text.TextStyle(color = ComposeColor(AppConfig.uiSettingsTextPrimary))
        )

        Spacer(Modifier.height(16.dp))

        // Save / Cancel buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onClose,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ComposeColor(AppConfig.uiSettingsSwitchTrackInactive)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Cancel", color = ComposeColor(AppConfig.uiSettingsTextPrimary))
            }
            Button(
                onClick = {
                    // For corridor, ensure p2 is set
                    if (form.type == MarkerType.CORRIDOR) {
                        if (form.corridorPhase == CorridorPhase.SET_P2 || form.corridorP2 == null) {
                            return@Button // can't save without p2
                        }
                    }
                    viewModel.saveMarker()
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ComposeColor(AppConfig.buttonActionBgColor)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Save", color = ComposeColor(AppConfig.buttonActionIconColor))
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Edit content
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EditContent(viewModel: MarkersViewModel, markerId: String, onClose: () -> Unit) {
    val form by viewModel.createForm.collectAsState()
    val markers by viewModel.markers.collectAsState()
    val marker = markers.find { it.id == markerId }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(scrollState)
    ) {
        // Header
        DrawerHeader(title = "Edit Marker", onClose = onClose)

        Spacer(Modifier.height(8.dp))

        // Name field
        OutlinedTextField(
            value = form.name,
            onValueChange = { v -> viewModel.updateForm { it.copy(name = v) } },
            label = { Text("Name", color = ComposeColor(AppConfig.uiSettingsTextMuted)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = drawerTextFieldColors(),
            textStyle = androidx.compose.ui.text.TextStyle(color = ComposeColor(AppConfig.uiSettingsTextPrimary))
        )

        Spacer(Modifier.height(12.dp))

        // Type selector
        Text(
            "Type",
            color = ComposeColor(AppConfig.uiSettingsTextMuted),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(4.dp))
        TypeSelector(
            selected = form.type,
            onSelect = { t -> viewModel.updateForm { it.copy(type = t) } }
        )

        Spacer(Modifier.height(12.dp))

        // Geometry-specific inputs (same as creation)
        when (form.type) {
            MarkerType.CIRCLE -> {
                OutlinedTextField(
                    value = form.radiusM.toLong().toString(),
                    onValueChange = { v ->
                        val r = v.toDoubleOrNull()?.coerceAtLeast(1.0) ?: form.radiusM
                        viewModel.updateForm { it.copy(radiusM = r) }
                    },
                    label = { Text("Radius (m)", color = ComposeColor(AppConfig.uiSettingsTextMuted)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = drawerTextFieldColors(),
                    textStyle = androidx.compose.ui.text.TextStyle(color = ComposeColor(AppConfig.uiSettingsTextPrimary))
                )
                val proximityDefault = form.radiusM * AppConfig.markerProximityZoneMultiplier
                Spacer(Modifier.height(4.dp))
                Text(
                    "Proximity: auto (${proximityDefault.toLong()} m)",
                    color = ComposeColor(AppConfig.uiSettingsTextMuted),
                    fontSize = 12.sp
                )
            }
            MarkerType.CORRIDOR -> {
                OutlinedTextField(
                    value = form.widthM.toLong().toString(),
                    onValueChange = { v ->
                        val w = v.toDoubleOrNull()?.coerceAtLeast(1.0) ?: form.widthM
                        viewModel.updateForm { it.copy(widthM = w) }
                    },
                    label = { Text("Width (m)", color = ComposeColor(AppConfig.uiSettingsTextMuted)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = drawerTextFieldColors(),
                    textStyle = androidx.compose.ui.text.TextStyle(color = ComposeColor(AppConfig.uiSettingsTextPrimary))
                )
                Spacer(Modifier.height(4.dp))
                val proximityDefault = form.widthM * AppConfig.markerProximityZoneMultiplier
                Text(
                    "Proximity: auto (${proximityDefault.toLong()} m)",
                    color = ComposeColor(AppConfig.uiSettingsTextMuted),
                    fontSize = 12.sp
                )
            }
            MarkerType.PIN -> {
                Text(
                    "Proximity: auto (${AppConfig.markerProximityPinM.toLong()} m)",
                    color = ComposeColor(AppConfig.uiSettingsTextMuted),
                    fontSize = 12.sp
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Proximity override
        OutlinedTextField(
            value = form.proximityOverrideM,
            onValueChange = { v -> viewModel.updateForm { it.copy(proximityOverrideM = v) } },
            label = { Text("Proximity override (m)", color = ComposeColor(AppConfig.uiSettingsTextMuted)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            colors = drawerTextFieldColors(),
            textStyle = androidx.compose.ui.text.TextStyle(color = ComposeColor(AppConfig.uiSettingsTextPrimary))
        )

        Spacer(Modifier.height(8.dp))

        // Description
        OutlinedTextField(
            value = form.description,
            onValueChange = { v -> viewModel.updateForm { it.copy(description = v) } },
            label = { Text("Description (optional)", color = ComposeColor(AppConfig.uiSettingsTextMuted)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = drawerTextFieldColors(),
            textStyle = androidx.compose.ui.text.TextStyle(color = ComposeColor(AppConfig.uiSettingsTextPrimary))
        )

        Spacer(Modifier.height(16.dp))

        // Save / Cancel
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onClose,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ComposeColor(AppConfig.uiSettingsSwitchTrackInactive)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Cancel", color = ComposeColor(AppConfig.uiSettingsTextPrimary))
            }
            Button(
                onClick = { viewModel.updateMarker(markerId) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ComposeColor(AppConfig.buttonActionBgColor)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Save", color = ComposeColor(AppConfig.buttonActionIconColor))
            }
        }

        Spacer(Modifier.height(12.dp))

        // Delete button
        HorizontalDivider(color = ComposeColor(AppConfig.uiSettingsTextMuted).copy(alpha = 0.3f))
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { viewModel.deleteMarker(markerId) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = ComposeColor(AppConfig.semanticDanger).copy(alpha = 0.15f)
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Delete Marker", color = ComposeColor(AppConfig.semanticDanger))
        }

        Spacer(Modifier.height(8.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Match result content
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MatchResultContent(viewModel: MarkersViewModel, onClose: () -> Unit) {
    val result by viewModel.matchResult.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(scrollState)
    ) {
        DrawerHeader(title = "Where Am I?", onClose = onClose)

        Spacer(Modifier.height(12.dp))

        val matches = result?.matches ?: emptyList()
        if (matches.isEmpty()) {
            Text(
                "No markers nearby",
                color = ComposeColor(AppConfig.uiSettingsTextMuted),
                fontSize = 14.sp
            )
        } else {
            matches.forEach { match ->
                MatchResultRow(match, indent = 0)
            }
        }

        Spacer(Modifier.height(16.dp))
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
                "${prefix}└─ ${match.marker.name}",
                color = ComposeColor(AppConfig.uiSettingsTextPrimary),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                "${prefix}   $geometryDesc — inside zone",
                color = ComposeColor(AppConfig.semanticCompliant),
                fontSize = 12.sp
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
                "${prefix}📍 ${match.marker.name}",
                color = ComposeColor(AppConfig.uiSettingsTextPrimary),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                "${prefix}   $geometryDesc — ${"%.0f".format(match.distanceM)} m",
                color = ComposeColor(AppConfig.semanticCaution),
                fontSize = 12.sp
            )
            // ProximityMatch has no children
        }
        is MatchResult.NoMatch -> { /* unreachable */ }
    }
    Spacer(Modifier.height(4.dp))
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared components
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DrawerHeader(title: String, onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(ComposeColor(AppConfig.uiSettingsSwitchTrackInactive))
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Close",
                tint = ComposeColor(AppConfig.uiSettingsTextPrimary)
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            color = ComposeColor(AppConfig.uiSettingsTextPrimary),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun TypeSelector(
    selected: MarkerType,
    onSelect: (MarkerType) -> Unit
) {
    val radioColors = RadioButtonDefaults.colors(
        selectedColor = ComposeColor(AppConfig.buttonActionBgColor),
        unselectedColor = ComposeColor(AppConfig.uiSettingsTextMuted)
    )

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelect(MarkerType.PIN) }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected == MarkerType.PIN,
                onClick = { onSelect(MarkerType.PIN) },
                colors = radioColors
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Pin (point only)",
                color = ComposeColor(AppConfig.uiSettingsTextPrimary),
                fontSize = 14.sp
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelect(MarkerType.CIRCLE) }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected == MarkerType.CIRCLE,
                onClick = { onSelect(MarkerType.CIRCLE) },
                colors = radioColors
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Circle",
                color = ComposeColor(AppConfig.uiSettingsTextPrimary),
                fontSize = 14.sp
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelect(MarkerType.CORRIDOR) }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected == MarkerType.CORRIDOR,
                onClick = { onSelect(MarkerType.CORRIDOR) },
                colors = radioColors
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Corridor",
                color = ComposeColor(AppConfig.uiSettingsTextPrimary),
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun drawerTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = ComposeColor(AppConfig.buttonActionBgColor).copy(alpha = 0.5f),
    unfocusedBorderColor = ComposeColor(AppConfig.uiSettingsTextMuted).copy(alpha = 0.3f),
    cursorColor = ComposeColor(AppConfig.uiSettingsTextPrimary)
)
