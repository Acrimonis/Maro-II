package ykws.android.maro.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.settings.AppSettings
import ykws.android.maro.data.depth.RasterCache
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.UserMarker
import ykws.android.maro.ui.components.DrawerScaffold
import ykws.android.maro.ui.components.SavedScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState

/** Returns the step sequence for the given marker type (mirror of VM method for UI use). */
private fun stepSequenceFor(type: MarkerType): List<WizardStep> = when (type) {
    MarkerType.PIN -> listOf(
        WizardStep.TypeSelect, WizardStep.Position,
        WizardStep.Proximity, WizardStep.Title, WizardStep.Description
    )
    MarkerType.CIRCLE -> listOf(
        WizardStep.TypeSelect, WizardStep.Position,
        WizardStep.Radius, WizardStep.Proximity, WizardStep.Title, WizardStep.Description
    )
    MarkerType.CORRIDOR -> listOf(
        WizardStep.TypeSelect, WizardStep.Position,
        WizardStep.PositionP2, WizardStep.Radius,
        WizardStep.Proximity, WizardStep.Title, WizardStep.Description
    )
}

/**
 * Self-contained overlay layer that renders all transient UI elements
 * (drawers, Wizard, Settings, scrim) on a unified layer above the main app layout.
 *
 * Layer 0 (main layout: MapContent + DashboardPanel + controls) is permanent.
 * Layer 1 (this composable) is transient — any overlay fits into this framework.
 */
@Composable
fun OverlayLayer(
    // ── State flags ──────────────────────────────────────────────────────
    showSettings: Boolean,
    showTrackDrawer: Boolean,
    showTrackHistory: Boolean,
    showMarkerManagement: Boolean,
    showWizard: Boolean,
    wizardStep: WizardStep?,
    drawerState: MarkerDrawerState,

    // ── Layout ───────────────────────────────────────────────────────────
    isLandscape: Boolean,
    portraitDashboardHeight: Dp,
    landscapeDashboardWidth: Dp,

    // ── Callbacks ────────────────────────────────────────────────────────
    onDismissSettings: () -> Unit,
    onDismissMenu: () -> Unit,
    onDismissTrackHistory: () -> Unit,
    onDismissMarkerManagement: () -> Unit,
    onWizardCancel: () -> Unit,
    onMarkerDrawerClose: () -> Unit,
    onOpenTrackHistoryFromMenu: () -> Unit,
    onOpenMarkerManagementFromMenu: () -> Unit,
    onOpenSettingsFromMenu: () -> Unit,
    onOpenFirstTrack: (String) -> Unit = {},
    onOpenFirstMarker: (String) -> Unit = {},

    // ── ViewModels ───────────────────────────────────────────────────────
    markersViewModel: MarkersViewModel,
    trackViewModel: ykws.android.maro.data.track.TrackViewModel,

    // ── Menu drawer data ─────────────────────────────────────────────────
    gpsMode: Boolean,
    onGpsModeChange: (Boolean) -> Unit,
    gpsToggleColor: ComposeColor,
    markerZonesVisible: Boolean = true,
    onToggleMarkerZones: () -> Unit = {},
    firstTrackId: String? = null,
    firstMarkerId: String? = null,

    // ── Track history data ───────────────────────────────────────────────
    onTrackAction: (ykws.android.maro.data.model.ListAction) -> Unit,
    trackSortState: ykws.android.maro.data.model.ListSortState,
    onTrackSortStateChange: (ykws.android.maro.data.model.ListSortState) -> Unit,
    trackFilterState: ykws.android.maro.data.model.ListFilter = ykws.android.maro.data.model.ListFilter(),
    onTrackFilterChange: (ykws.android.maro.data.model.ListFilter) -> Unit = {},
    onTrackReset: () -> Unit = {},

    // ── Settings data ────────────────────────────────────────────────────
    appSettings: AppSettings,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    selectedTab: Int,
    onTabChange: (Int) -> Unit,
    displayScrollState: androidx.compose.foundation.ScrollState,
    navigationScrollState: androidx.compose.foundation.ScrollState,
    systemScrollState: androidx.compose.foundation.ScrollState,
    onRegenerateRasters: (List<RasterCache.Step>) -> Unit,

    // ── Marker drawer data ───────────────────────────────────────────────
    boatPosition: LatLng?,

    // ── Track info drawer data ───────────────────────────────────────────
    showTrackInfoDrawer: Boolean = false,
    trackInfoDrawerData: ykws.android.maro.data.track.Track? = null,
    onTrackDrawerClose: () -> Unit = {},
    onNavigateToTrack: (String) -> Unit = {},
    trackListIds: List<String> = emptyList(),
    currentTrackIndex: Int = -1,
    onTrackPrev: () -> Unit = {},
    onTrackNext: () -> Unit = {},
    onShareTrack: (String) -> Unit = {},
    onTrackMetadataChanged: () -> Unit = {},
    onRequestMarkerDelete: (String, String) -> Unit = { _, _ -> },
    onDeleteTrack: (String) -> Unit = {},
    // ── List overlay scroll state ────────────────────────────────────────
    trackListState: LazyListState = rememberLazyListState(),
    markerListState: LazyListState = rememberLazyListState(),
    trackRestoredScrollState: SavedScrollState? = null,
    markerRestoredScrollState: SavedScrollState? = null,

    // ── Marker management data ───────────────────────────────────────────
    markers: List<UserMarker>,
    onMarkerAction: (ykws.android.maro.data.model.ListAction) -> Unit,
    onCreateFirst: () -> Unit,
    onSetIcon: (String, String?) -> Unit,
    onToggleMarkerPin: (String, Boolean) -> Unit = { _, _ -> },
    onMergeMarkers: (Set<String>, String, Boolean) -> Unit = { _, _, _ -> },
    markerSortState: ykws.android.maro.data.model.ListSortState,
    onMarkerSortStateChange: (ykws.android.maro.data.model.ListSortState) -> Unit,
    markerFilterState: ykws.android.maro.data.model.ListFilter = ykws.android.maro.data.model.ListFilter(),
    onMarkerFilterChange: (ykws.android.maro.data.model.ListFilter) -> Unit = {},
    onMarkerReset: () -> Unit = {}
) {
    // ── Collect track ViewModel state ────────────────────────────────────
    val trackRecorderState by trackViewModel.uiState.collectAsState()
    val trackSummaries by trackViewModel.summaries.collectAsState()

    // ── Keyboard offset for wizard portrait ──────────────────────────────
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    val imeHeightDp = with(LocalDensity.current) { imeBottom.toDp() }
    val keyboardOffsetDp = 0.dp - imeHeightDp

    // ── Unified scrim ────────────────────────────────────────────────────
    val showScrim = showTrackDrawer
        || showTrackHistory
        || showMarkerManagement
        || (showWizard && wizardStep !is WizardStep.Position && wizardStep !is WizardStep.PositionP2)
        || (drawerState is MarkerDrawerState.Viewing || drawerState is MarkerDrawerState.MatchResult)

    Box(modifier = Modifier.fillMaxSize()) {
        // ── 1. Scrim ─────────────────────────────────────────────────────
        DrawerSlot(
            visible = showScrim,
            modifier = Modifier.fillMaxSize(),
            slideDirection = SlideDirection.FADE_ONLY
        ) {
            val scrimDismiss: () -> Unit = {
                when {
                    showTrackDrawer -> onDismissMenu()
                    showTrackHistory -> onDismissTrackHistory()
                    showMarkerManagement -> onDismissMarkerManagement()
                    showWizard -> onWizardCancel()
                    drawerState is MarkerDrawerState.Viewing || drawerState is MarkerDrawerState.MatchResult -> onMarkerDrawerClose()
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ComposeColor.Black.copy(alpha = 0.32f))
                    .clickable { scrimDismiss() }
            )
        }

        // ── 2. WizardDrawer ──────────────────────────────────────────────
        val activeStep = wizardStep
        if (showWizard && activeStep != null) {
            // Compute step sequence from form type
            val form by markersViewModel.createForm.collectAsState()
            val seq = stepSequenceFor(form.type)
            val stepIndex = seq.indexOf(activeStep)
            val totalSteps = seq.size

            if (isLandscape) {
                DrawerSlot(
                    visible = true,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(landscapeDashboardWidth)
                        .fillMaxHeight(),
                    slideDirection = SlideDirection.FROM_LEFT,
                    shadowEdge = ShadowEdge.RIGHT
                ) {
                    WizardDrawer(
                        viewModel = markersViewModel,
                        isLandscape = true,
                        onCancel = onWizardCancel,
                        step = activeStep,
                        totalSteps = totalSteps,
                        stepIndex = stepIndex
                    )
                }
            } else {
                DrawerSlot(
                    visible = true,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(portraitDashboardHeight)
                        .offset(y = keyboardOffsetDp),
                    slideDirection = SlideDirection.FROM_BOTTOM,
                    shadowEdge = ShadowEdge.TOP
                ) {
                    WizardDrawer(
                        viewModel = markersViewModel,
                        isLandscape = false,
                        onCancel = onWizardCancel,
                        step = activeStep,
                        totalSteps = totalSteps,
                        stepIndex = stepIndex
                    )
                }
            }
        }

        // ── 3. MenuDrawer ────────────────────────────────────────────────
        DrawerSlot(
            visible = showTrackDrawer,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .fillMaxWidth(0.75f)
                .fillMaxHeight(),
            slideDirection = SlideDirection.FROM_RIGHT,
            shadowEdge = ShadowEdge.LEFT
        ) {
            MenuDrawerOverlay(
                isOpen = true,
                gpsMode = gpsMode,
                onGpsModeChange = onGpsModeChange,
                gpsToggleColor = gpsToggleColor,
                recorderState = trackRecorderState,
                onViewTrackList = {
                    onDismissMenu()
                    onOpenTrackHistoryFromMenu()
                },
                onManageMarkers = {
                    onDismissMenu()
                    onOpenMarkerManagementFromMenu()
                },
                onOpenFirstTrack = firstTrackId?.let { id -> { onDismissMenu(); onOpenFirstTrack(id) } },
                onOpenFirstMarker = firstMarkerId?.let { id -> { onDismissMenu(); onOpenFirstMarker(id) } },
                onDismiss = onDismissMenu,
                onOpenSettings = {
                    onDismissMenu()
                    onOpenSettingsFromMenu()
                },
                trackFilterState = trackFilterState,
                onTrackFilterChange = onTrackFilterChange,
                onTrackReset = onTrackReset,
                trackFilterAxes = ykws.android.maro.data.model.trackFilterAxes(),
                markerFilterState = markerFilterState,
                onMarkerFilterChange = onMarkerFilterChange,
                onMarkerReset = onMarkerReset,
                markerFilterAxes = ykws.android.maro.data.model.markerFilterAxes(),
                markerZonesVisible = markerZonesVisible,
                onToggleMarkerZones = onToggleMarkerZones,
                onImportTracks = { onTrackAction(ykws.android.maro.data.model.ListAction.ImportTracks) },
                onExportAllTracks = { onTrackAction(ykws.android.maro.data.model.ListAction.BatchExportGpx(trackSummaries.map { it.id }.toSet())) }
            )
        }

        // ── 4. MarkerDrawer ──────────────────────────────────────────────
        if (isLandscape) {
            DrawerSlot(
                visible = drawerState is MarkerDrawerState.Viewing || drawerState is MarkerDrawerState.MatchResult,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(landscapeDashboardWidth)
                    .fillMaxHeight(),
                slideDirection = SlideDirection.FROM_LEFT,
                shadowEdge = ShadowEdge.RIGHT
            ) {
                MarkerDrawer(
                    viewModel = markersViewModel,
                    isLandscape = true,
                    onClose = onMarkerDrawerClose,
                    boatPosition = boatPosition,
                    onRequestDelete = onRequestMarkerDelete
                )
            }
        } else {
            DrawerSlot(
                visible = drawerState is MarkerDrawerState.Viewing || drawerState is MarkerDrawerState.MatchResult,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(portraitDashboardHeight),
                slideDirection = SlideDirection.FROM_BOTTOM,
                shadowEdge = ShadowEdge.TOP
            ) {
                MarkerDrawer(
                    viewModel = markersViewModel,
                    isLandscape = false,
                    onClose = onMarkerDrawerClose,
                    boatPosition = boatPosition,
                    onRequestDelete = onRequestMarkerDelete
                )
            }
        }

        // ── 4b. TrackInfoDrawer (no scrim — map stays interactive) ────────
        val isAtTrackFirst = currentTrackIndex <= 0
        val isAtTrackLast = currentTrackIndex >= trackListIds.lastIndex
        if (isLandscape) {
            DrawerSlot(
                visible = showTrackInfoDrawer,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(landscapeDashboardWidth)
                    .fillMaxHeight(),
                slideDirection = SlideDirection.FROM_LEFT,
                shadowEdge = ShadowEdge.RIGHT
            ) {
                trackInfoDrawerData?.let { track ->
                    val summary = ykws.android.maro.data.track.TrackSummary(
                        id = track.id,
                        name = track.name,
                        comment = track.comment,
                        startTimeMs = track.startTimeMs,
                        endTimeMs = track.endTimeMs,
                        fastestSpeedMps = track.fastestSpeedMps,
                        distanceNm = track.distanceNm,
                        visibleOnMap = track.visibleOnMap,
                        navigatingDurationSec = track.navigatingDurationSec,
                        pausedDurationSec = track.pausedDurationSec,
                        averageSpeedMps = track.averageSpeedMps,
                        pinned = track.pinned,
                        pointCount = track.trackPoints.size,
                        idleDurationSec = track.idleDurationSec
                    )
                    DrawerScaffold(
                        title = track.name,
                        onClose = onTrackDrawerClose,
                        statusBarsInset = true,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
                        headerActions = {
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                IconButton(onClick = { onShareTrack(track.id) }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Filled.Share, "Share GPX", tint = ButtonColors.icon, modifier = Modifier.size(24.dp))
                                }
                                IconButton(onClick = { onDeleteTrack(track.id) }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Filled.Delete, "Delete", tint = ButtonColors.icon, modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    ) {
                        TrackCardContent(
                            summary = summary,
                            dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US),
                            accentColor = ComposeColor(0xFFFFD700.toInt()),
                            onUpdateTrack = { id, name, comment, pinned ->
                                pinned?.let { trackViewModel.setPinned(id, it) }
                                trackViewModel.updateTrack(id, name, comment)
                            },
                            onShareGpx = { onShareTrack(track.id) },
                            onTap = null
                        )
                        // ── Prev/Next pills ──────────────────────────
                        if (trackListIds.size > 1) {
                            Spacer(Modifier.height(12.dp))
                            HorizontalDivider(color = ComposeColor(AppConfig.uiSettingsDivider))
                            Spacer(Modifier.height(8.dp))
                            val accentBg = ComposeColor(AppConfig.uiSettingsAccent)
                            val accentFg = ComposeColor(AppConfig.uiSettingsTextPrimary)
                            val disabledAlpha = 0.35f
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                                    .background(accentBg.copy(alpha = if (isAtTrackFirst) disabledAlpha else 1f))
                                    .then(if (!isAtTrackFirst) Modifier.clickable { onTrackPrev() } else Modifier)
                                    .padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                    Text("Previous", color = accentFg.copy(alpha = if (isAtTrackFirst) disabledAlpha else 1f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                                Box(Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                                    .background(accentBg.copy(alpha = if (isAtTrackLast) disabledAlpha else 1f))
                                    .then(if (!isAtTrackLast) Modifier.clickable { onTrackNext() } else Modifier)
                                    .padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                    Text("Next", color = accentFg.copy(alpha = if (isAtTrackLast) disabledAlpha else 1f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            DrawerSlot(
                visible = showTrackInfoDrawer,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(portraitDashboardHeight),
                slideDirection = SlideDirection.FROM_BOTTOM,
                shadowEdge = ShadowEdge.TOP
            ) {
                trackInfoDrawerData?.let { track ->
                    val summary = ykws.android.maro.data.track.TrackSummary(
                        id = track.id,
                        name = track.name,
                        comment = track.comment,
                        startTimeMs = track.startTimeMs,
                        endTimeMs = track.endTimeMs,
                        fastestSpeedMps = track.fastestSpeedMps,
                        distanceNm = track.distanceNm,
                        visibleOnMap = track.visibleOnMap,
                        navigatingDurationSec = track.navigatingDurationSec,
                        pausedDurationSec = track.pausedDurationSec,
                        averageSpeedMps = track.averageSpeedMps,
                        pinned = track.pinned,
                        pointCount = track.trackPoints.size,
                        idleDurationSec = track.idleDurationSec
                    )
                    DrawerScaffold(
                        title = track.name,
                        onClose = onTrackDrawerClose,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                        headerActions = {
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                IconButton(onClick = { onShareTrack(track.id) }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Filled.Share, "Share GPX", tint = ButtonColors.icon, modifier = Modifier.size(24.dp))
                                }
                                IconButton(onClick = { onDeleteTrack(track.id) }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Filled.Delete, "Delete", tint = ButtonColors.icon, modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    ) {
                        TrackCardContent(
                            summary = summary,
                            dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US),
                            accentColor = ComposeColor(0xFFFFD700.toInt()),
                            onUpdateTrack = { id, name, comment, pinned ->
                                pinned?.let { trackViewModel.setPinned(id, it) }
                                trackViewModel.updateTrack(id, name, comment)
                            },
                            onShareGpx = { onShareTrack(track.id) },
                            onTap = null
                        )
                        // ── Prev/Next pills ──────────────────────────
                        if (trackListIds.size > 1) {
                            Spacer(Modifier.height(12.dp))
                            HorizontalDivider(color = ComposeColor(AppConfig.uiSettingsDivider))
                            Spacer(Modifier.height(8.dp))
                            val accentBg = ComposeColor(AppConfig.uiSettingsAccent)
                            val accentFg = ComposeColor(AppConfig.uiSettingsTextPrimary)
                            val disabledAlpha = 0.35f
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                                    .background(accentBg.copy(alpha = if (isAtTrackFirst) disabledAlpha else 1f))
                                    .then(if (!isAtTrackFirst) Modifier.clickable { onTrackPrev() } else Modifier)
                                    .padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                    Text("Previous", color = accentFg.copy(alpha = if (isAtTrackFirst) disabledAlpha else 1f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                                Box(Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                                    .background(accentBg.copy(alpha = if (isAtTrackLast) disabledAlpha else 1f))
                                    .then(if (!isAtTrackLast) Modifier.clickable { onTrackNext() } else Modifier)
                                    .padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                    Text("Next", color = accentFg.copy(alpha = if (isAtTrackLast) disabledAlpha else 1f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── 5. TrackHistory ──────────────────────────────────────────────
        DrawerSlot(
            visible = showTrackHistory,
            modifier = Modifier.fillMaxSize(),
            slideDirection = SlideDirection.FROM_RIGHT,
            shadowEdge = ShadowEdge.LEFT
        ) {
            TrackHistoryOverlay(
                trackSummaries = trackSummaries,
                liveTrackState = trackRecorderState,
                onUpdateTrack = { id, name, comment, pinned ->
                    pinned?.let { trackViewModel.setPinned(id, it) }
                    trackViewModel.updateTrack(id, name, comment)
                },
                onUpdateLiveTrack = { name, comment ->
                    trackViewModel.updateLiveTrackMeta(name, comment)
                },
                onAction = onTrackAction,
                onDismiss = onDismissTrackHistory,
                onNavigateToTrack = onNavigateToTrack,
                onResumeTrack = { id ->
                    trackViewModel.resumeTrack(id)
                    onDismissTrackHistory()
                },
                onMergeTracks = { ids, name, keepOriginals ->
                    trackViewModel.mergeTracks(ids, name, keepOriginals)
                },
                sortState = trackSortState,
                onSortStateChange = onTrackSortStateChange,
                filterState = trackFilterState,
                onFilterChange = onTrackFilterChange,
                onReset = onTrackReset,
                tracksVisible = appSettings.tracksVisible,
                trackingRenderNb = appSettings.trackingRenderNb,
                trackingTransparencyNewest = appSettings.trackingTransparencyNewest,
                trackingTransparencyOldest = appSettings.trackingTransparencyOldest,
                trackingColorPastFrom = appSettings.trackingColorPastFrom,
                trackingColorPastTo = appSettings.trackingColorPastTo,
                trackingTransparencyPinnedNewest = appSettings.trackingTransparencyPinnedNewest,
                trackingTransparencyPinnedOldest = appSettings.trackingTransparencyPinnedOldest,
                trackingColorPinnedFrom = appSettings.trackingColorPinnedFrom,
                trackingColorPinnedTo = appSettings.trackingColorPinnedTo,
                lazyListState = trackListState,
                restoredScrollState = trackRestoredScrollState
            )
        }

        // ── 6. Marker Management ─────────────────────────────────────────
        DrawerSlot(
            visible = showMarkerManagement,
            modifier = Modifier.fillMaxSize(),
            slideDirection = SlideDirection.FROM_RIGHT,
            shadowEdge = ShadowEdge.LEFT
        ) {
            MarkerManagementOverlay(
                markers = markers,
                onAction = onMarkerAction,
                onCreateFirst = onCreateFirst,
                onDismiss = onDismissMarkerManagement,
                onSetIcon = onSetIcon,
                onTogglePin = onToggleMarkerPin,
                onMergeMarkers = onMergeMarkers,
                sortState = markerSortState,
                onSortStateChange = onMarkerSortStateChange,
                filterState = markerFilterState,
                onFilterChange = onMarkerFilterChange,
                onReset = onMarkerReset,
                lazyListState = markerListState,
                restoredScrollState = markerRestoredScrollState
            )
        }

        // ── 7. Settings ──────────────────────────────────────────────────
        DrawerSlot(
            visible = showSettings,
            modifier = Modifier.fillMaxSize(),
            slideDirection = SlideDirection.FROM_RIGHT,
            shadowEdge = ShadowEdge.LEFT
        ) {
            SettingsOverlay(
                settings = appSettings,
                onUpdateSettings = onUpdateSettings,
                onGpsModeChange = onGpsModeChange,
                onDismiss = onDismissSettings,
                selectedTab = selectedTab,
                onTabChange = onTabChange,
                displayScrollState = displayScrollState,
                navigationScrollState = navigationScrollState,
                systemScrollState = systemScrollState,
                onRegenerateRasters = onRegenerateRasters
            )
        }
    }
}
