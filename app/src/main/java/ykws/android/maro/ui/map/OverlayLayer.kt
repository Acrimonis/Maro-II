package ykws.android.maro.ui.map

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.res.stringResource
import ykws.android.maro.R
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import ykws.android.maro.ui.components.MeasureHeight
import ykws.android.maro.ui.icons.Speed

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
internal fun OverlayLayer(
    // ── State flags ──────────────────────────────────────────────────────
    chrome: OverlayChrome,
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
    /** R1: the marker wizard takes the dashboard slot — the other selected-item dashboard closes first. */
    onMarkerWizardEntry: () -> Unit = {},
    onOpenTrackHistoryFromMenu: () -> Unit,
    onOpenMarkerManagementFromMenu: () -> Unit,
    onOpenSettingsFromMenu: () -> Unit,
    onOpenFirstTrack: (String) -> Unit = {},
    onOpenFirstMarker: (String) -> Unit = {},

    // ── ViewModels ───────────────────────────────────────────────────────
    markersViewModel: MarkersViewModel,
    trackViewModel: ykws.android.maro.data.track.TrackViewModel,

    // ── Menu drawer data ─────────────────────────────────────────────────
    menu: MenuOverlayData,
    /**
     * The route's read-only state, arriving as a bundle rather than as three more parameters on this
     * already wide signature — the shape `OverlayLayerParams.kt` exists for. Only the menu entry
     * reads it; the mode's own state lives in `RouteViewModel`.
     */
    route: RouteOverlayData = RouteOverlayData(),
    onGpsModeChange: (Boolean) -> Unit,
    onAutoShowMasterChange: (Boolean) -> Unit = {},
    onToggleMarkerZones: () -> Unit = {},
    /** The menu's arrows chip (D5): one half of the pair that writes the two render axes. */
    onTrackArrowsChange: (Boolean) -> Unit = {},
    /** The menu's colours chip (D5): the other half of that same writer. */
    onTrackColoursChange: (Boolean) -> Unit = {},

    // ── Track history data ───────────────────────────────────────────────
    onTrackAction: (ykws.android.maro.data.model.ListAction) -> Unit,
    trackList: TrackListOverlayData,
    onTrackSortStateChange: (ykws.android.maro.data.model.ListSortState) -> Unit,
    onTrackFilterChange: (ykws.android.maro.data.model.ListFilter) -> Unit = {},
    onTrackReset: () -> Unit = {},
    // Map referential (menu filter) + link flag. The link toggle lives in the menu only.
    onTrackMapFilterChange: (ykws.android.maro.data.model.ListFilter) -> Unit = {},
    onTrackMapReset: () -> Unit = {},
    trackFilterLinked: Boolean = true,
    onToggleTrackLink: () -> Unit = {},

    // ── Settings data ────────────────────────────────────────────────────
    appSettings: AppSettings,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    settings: SettingsOverlayData,
    onTabChange: (Int) -> Unit,
    onRegenerateRasters: (List<RasterCache.Step>) -> Unit,

    // ── Marker drawer data ───────────────────────────────────────────────
    boatPosition: LatLng?,

    // ── Track info drawer data ───────────────────────────────────────────
    trackInfo: TrackInfoOverlayData,
    onTrackDrawerClose: () -> Unit = {},
    onNavigateToTrack: (String) -> Unit = {},
    /** Opens the resume confirmation sheet; `fromList` selects which surface closes on confirm. */
    onResumeRequest: (String, Boolean) -> Unit = { _, _ -> },
    onTrackPrev: () -> Unit = {},
    onTrackNext: () -> Unit = {},
    /**
     * The inspect cursor's own Prev/Next for an inspect-opened marker card (plan §5): the merged
     * ladder's walk replaces the marker walk while this is non-null. Null everywhere else, so a
     * list- or map-opened card keeps walking its own world.
     */
    markerInspectWalk: InspectWalk? = null,
    onShareTrack: (String) -> Unit = {},
    onRequestMarkerDelete: (String, String) -> Unit = { _, _ -> },
    onDeleteTrack: (String) -> Unit = {},
    // ── Marker management data ───────────────────────────────────────────
    markerList: MarkerListOverlayData,
    trackTitleLookup: (String) -> String? = { null },
    onOpenMarkerTrack: (String) -> Unit = {},
    onMarkerAction: (ykws.android.maro.data.model.ListAction) -> Unit,
    onCreateFirst: () -> Unit,
    onSetIcon: (String, String?) -> Unit,
    onSetPin: (String, Boolean) -> Unit = { _, _ -> },
    onUpdateMarkerText: (String, String?, String?) -> Unit = { _, _, _ -> },
    onMarkerSortStateChange: (ykws.android.maro.data.model.ListSortState) -> Unit,
    onMarkerFilterChange: (ykws.android.maro.data.model.ListFilter) -> Unit = {},
    onMarkerReset: () -> Unit = {},
    // Map referential (menu filter) + link flag for markers.
    onMarkerMapFilterChange: (ykws.android.maro.data.model.ListFilter) -> Unit = {},
    onMarkerMapReset: () -> Unit = {},
    markerFilterLinked: Boolean = true,
    onToggleMarkerLink: () -> Unit = {}
) {
    // ── Destructured bundle locals (chrome, menu, settings, track info, track list, marker list) ──
    val showSettings = chrome.showSettings
    val showTrackDrawer = chrome.showTrackDrawer
    val showTrackHistory = chrome.showTrackHistory
    val showMarkerManagement = chrome.showMarkerManagement
    val showWizard = chrome.showWizard
    val wizardStep = chrome.wizardStep
    val drawerState = chrome.drawerState
    val dialogScrimActive = chrome.dialogScrimActive
    val gpsMode = menu.gpsMode
    val autoShowMasterVisible = menu.autoShowMasterVisible
    val autoShowMasterOverride = menu.autoShowMasterOverride
    val gpsToggleColor = menu.gpsToggleColor
    val markerZonesVisible = menu.markerZonesVisible
    val trackArrows = menu.trackArrows
    val trackColours = menu.trackColours
    val firstTrackId = menu.firstTrackId
    val firstMarkerId = menu.firstMarkerId
    val trackMapFilterState = menu.trackMapFilterState
    val trackMapCount = menu.trackMapCount
    val markerMapFilterState = menu.markerMapFilterState
    val markerMapCount = menu.markerMapCount
    val selectedTab = settings.selectedTab
    val displayScrollState = settings.displayScrollState
    val navigationScrollState = settings.navigationScrollState
    val positionScrollState = settings.positionScrollState
    val systemScrollState = settings.systemScrollState
    val showTrackInfoDrawer = trackInfo.showTrackInfoDrawer
    val trackInfoDrawerData = trackInfo.trackInfoDrawerData
    val trackListIds = trackInfo.trackListIds
    val currentTrackIndex = trackInfo.currentTrackIndex
    val trackWalkHeld = trackInfo.walkHeld
    val trackInfoColours = trackInfo.trackColours
    val eyeOverride = trackInfo.eyeOverride
    val onToggleEyeOverride = trackInfo.onToggleEyeOverride

    // ── Opened track's accent bar ────────────────────────────────────────
    // Identity, not selection (A9): the accent is the track's own resolved render colour — the one
    // the list shows and an unselected map line paints — clamped to full opacity because the bar sits
    // on a card rather than blending over water. The list derives its accent from pinned/history
    // splits, a recency sort and a render cap; the drawer holds only a flat, uncapped index, so the
    // shared helper takes the already-resolved colour rather than recomputing one from other inputs.
    val trackAccent = ComposeColor(
        computeTrackPolylineAppearance(
            index = currentTrackIndex.coerceAtLeast(0),
            total = trackListIds.size,
            transparencyNewest = appSettings.trackingTransparencyNewest,
            transparencyOldest = appSettings.trackingTransparencyOldest,
            colorFrom = appSettings.trackingColorPastFrom,
            colorTo = appSettings.trackingColorPastTo
        ).argb or 0xFF000000.toInt()
    )
    val trackSortState = trackList.trackSortState
    val trackFilterState = trackList.trackFilterState
    val trackListState = trackList.trackListState
    val markers = markerList.markers
    val markerSortState = markerList.markerSortState
    val markerFilterState = markerList.markerFilterState
    val markerListState = markerList.markerListState

    // ── Collect track ViewModel state ────────────────────────────────────
    val trackRecorderState by trackViewModel.uiState.collectAsState()
    val trackSummaries by trackViewModel.summaries.collectAsState()

    // ── Keyboard offset for wizard portrait ──────────────────────────────
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    val imeHeightDp = with(LocalDensity.current) { imeBottom.toDp() }
    val keyboardOffsetDp = 0.dp - imeHeightDp
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // ── Ladder scrim ─────────────────────────────────────────────────────
    // A plain on/off dim (no fade) that touch-blocks the whole screen (map included) while a drawer,
    // settings or the wizard is open. It yields to a visible `ConfirmDialog`, which paints its own
    // scrim above the drawers, so the two dim layers never stack.

    val showScrim = (showSettings
        || showTrackDrawer
        || showTrackHistory
        || showMarkerManagement
        || (showWizard && imeHeightDp > 0.dp))
        && !dialogScrimActive

    // ── A panel over the map owns the region while it is open ────────────
    // The menu, the settings page, the track history and the marker management list are panels over the
    // map, not occupants of the dashboard slot, so R1 keeps the selection open behind them (which is what
    // leaves the render chips, the display settings and the list filters reachable with an item selected).
    // The ladder declares the scrim and the menu before the detail slots, so the slots stand down here:
    // without this gate a surviving dashboard would draw over the panel's own scrim. State is untouched,
    // so the dashboard returns when the panel closes.
    val panelOwnsRegion = showTrackDrawer || showSettings || showTrackHistory || showMarkerManagement

    Box(modifier = Modifier.fillMaxSize()) {
        // ── 1. Scrim (hard toggle — no animation) ────────────────────────
        if (showScrim) {
            val scrimDismiss: () -> Unit = {
                when {
                    showSettings -> onDismissSettings()
                    showTrackDrawer -> onDismissMenu()
                    showTrackHistory -> onDismissTrackHistory()
                    showMarkerManagement -> onDismissMarkerManagement()
                    showWizard -> {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ComposeColor.Black.copy(alpha = AppConfig.uiScrimAlpha))
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
                .then(
                    if (isLandscape) Modifier.width(landscapeDashboardWidth * 0.75f * AppConfig.uiLandscapePanelWidthScale)
                    else Modifier.fillMaxWidth(0.75f)
                )
                .fillMaxHeight(),
            slideDirection = SlideDirection.FROM_RIGHT,
            shadowEdge = ShadowEdge.LEFT
        ) {
            MenuDrawerOverlay(
                isOpen = true,
                routeActive = route.active,
                routeAvailable = route.available,
                onOpenDestination = route.onOpenDestination,
                gpsMode = gpsMode,
                onGpsModeChange = onGpsModeChange,
                autoShowMasterVisible = autoShowMasterVisible,
                autoShowMasterOverride = autoShowMasterOverride,
                onAutoShowMasterChange = onAutoShowMasterChange,
                gpsToggleColor = gpsToggleColor,
                recorderState = trackRecorderState,
                trackCount = trackMapCount,
                markerCount = markerMapCount,
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
                trackFilterState = trackMapFilterState,
                onTrackFilterChange = onTrackMapFilterChange,
                onTrackReset = onTrackMapReset,
                trackFilterLinked = trackFilterLinked,
                onToggleTrackLink = onToggleTrackLink,
                trackFilterAxes = ykws.android.maro.data.model.trackFilterAxes(),
                markerFilterState = markerMapFilterState,
                onMarkerFilterChange = onMarkerMapFilterChange,
                onMarkerReset = onMarkerMapReset,
                markerFilterLinked = markerFilterLinked,
                onToggleMarkerLink = onToggleMarkerLink,
                markerFilterAxes = ykws.android.maro.data.model.markerFilterAxes(),
                markerZonesVisible = markerZonesVisible,
                onToggleMarkerZones = onToggleMarkerZones,
                trackArrows = trackArrows,
                trackColours = trackColours,
                onTrackArrowsChange = onTrackArrowsChange,
                onTrackColoursChange = onTrackColoursChange,
                onImportTracks = { onDismissMenu(); onTrackAction(ykws.android.maro.data.model.ListAction.ImportTracks) },
                onExportAllTracks = { onDismissMenu(); onTrackAction(ykws.android.maro.data.model.ListAction.BatchExportGpx(trackSummaries.map { it.id }.toSet())) }
            )
        }

        // ── 4. MatchResult (Where-Am-I) — unchanged full-height fixed slot (its own scroll host) ──
        // It is not a selected item, so it keeps its own surface in both orientations. Viewing (a marker
        // card) and the track card share the one selected-item slot declared below, beside the track
        // panel's own measuring: a single surface is what lets a cross-type step swap its content in
        // place, in portrait and in landscape alike (plan §5).
        if (isLandscape) {
            DrawerSlot(
                visible = drawerState is MarkerDrawerState.MatchResult && !panelOwnsRegion,
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
                    onRequestDelete = onRequestMarkerDelete,
                    trackTitleLookup = trackTitleLookup,
                    onOpenMarkerTrack = { id -> onMarkerDrawerClose(); onOpenMarkerTrack(id) },
                    onWizardEntry = onMarkerWizardEntry,
                    walk = markerInspectWalk
                )
            }
        } else {
            DrawerSlot(
                visible = drawerState is MarkerDrawerState.MatchResult && !panelOwnsRegion,
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
                    onRequestDelete = onRequestMarkerDelete,
                    trackTitleLookup = trackTitleLookup,
                    onOpenMarkerTrack = { id -> onMarkerDrawerClose(); onOpenMarkerTrack(id) },
                    onWizardEntry = onMarkerWizardEntry
                )
            }
        }

        // ── 4b. The selected-item slot: one surface, its content swapped in place (no scrim —
        //        the map stays interactive). R1 makes the two selected-item panels exclusive, so
        //        "which panel is open" is the whole decision, and a cross-type step changes which
        //        panel this slot renders rather than closing one card and opening the other: the slot
        //        stays mounted and only its content changes, where two slots would play one card's
        //        exit alongside the other's enter and read as two events (plan §5). Landscape and
        //        portrait each get their own slot geometry — the landscape panel's is the fixed
        //        full-height left column, so its content swap has no measured size to resize to,
        //        while portrait's wraps to the measured card. The slot closes nothing itself, so
        //        every existing close rule stays the one author of that.
        // A walk held for an in-flight open reads as at both ends at once, so its two buttons grey
        // out and carry no tap: the step surface is inert until the successor lands (plan §5).
        val isAtTrackFirst = currentTrackIndex <= 0 || trackWalkHeld
        val isAtTrackLast = currentTrackIndex >= trackListIds.lastIndex || trackWalkHeld
        // Which panel the one selected-item slot renders: R1 keeps the two selected-item panels
        // exclusive, so "which panel is open" is the whole decision.
        val markerCardOpen = drawerState is MarkerDrawerState.Viewing
        if (isLandscape) {
            DrawerSlot(
                visible = (markerCardOpen || showTrackInfoDrawer) && !panelOwnsRegion,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(landscapeDashboardWidth)
                    .fillMaxHeight(),
                slideDirection = SlideDirection.FROM_LEFT,
                shadowEdge = ShadowEdge.RIGHT
            ) {
                val track = trackInfoDrawerData
                if (showTrackInfoDrawer && track != null) {
                    val summary = ykws.android.maro.data.track.TrackSummary(
                        id = track.id,
                        name = track.name,
                        comment = track.comment,
                        startTimeMs = track.startTimeMs,
                        endTimeMs = track.endTimeMs,
                        lastPointTimeMs = track.lastPointTimeMs,
                        fastestSpeedMps = track.fastestSpeedMps,
                        distanceNm = track.distanceNm,
                        navigatingDurationSec = track.navigatingDurationSec,
                        pausedDurationSec = track.pausedDurationSec,
                        averageSpeedMps = track.averageSpeedMps,
                        pinned = track.pinned,
                        pointCount = track.trackPoints.size,
                        idleDurationSec = track.idleDurationSec,
                        // The flag rides the hand-built summary too: both dashboard cards render the
                        // same card as the list, so a route opened on the map reads as a route — its
                        // three cells, its creation stamp and its refused Resume hang off this field.
                        route = track.route
                    )
                    DrawerScaffold(
                        title = track.name,
                        onClose = onTrackDrawerClose,
                        statusBarsInset = true,
                        bottomAnchoredContent = true,
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 16.dp),
                        headerActions = {
                            TrackDrawerHeaderActions(
                                bandedOn = eyeOverride ?: trackInfoColours,
                                onToggleEyeOverride = onToggleEyeOverride,
                                onDelete = { onDeleteTrack(track.id) }
                            )
                        },
                        footer = {
                            if (trackListIds.size > 1) {
                                Spacer(Modifier.height(10.dp))
                                val accentBg = ComposeColor(AppConfig.uiAccent)
                                val accentFg = ComposeColor(AppConfig.uiTextPrimary)
                                val disabledAlpha = 0.35f
                                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Box(Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                                        .background(accentBg.copy(alpha = if (isAtTrackFirst) disabledAlpha else 1f))
                                        .then(if (!isAtTrackFirst) Modifier.clickable { onTrackPrev() } else Modifier)
                                        .padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                        Text(stringResource(R.string.action_previous), color = accentFg.copy(alpha = if (isAtTrackFirst) disabledAlpha else 1f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Box(Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                                        .background(accentBg.copy(alpha = if (isAtTrackLast) disabledAlpha else 1f))
                                        .then(if (!isAtTrackLast) Modifier.clickable { onTrackNext() } else Modifier)
                                        .padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                        Text(stringResource(R.string.action_next), color = accentFg.copy(alpha = if (isAtTrackLast) disabledAlpha else 1f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                            }
                        }
                    ) {
                        TrackCardContent(
                            summary = summary,
                            dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US),
                            accentColor = trackAccent,
                            onUpdateTrack = { id, name, comment, pinned ->
                                pinned?.let { trackViewModel.setPinned(id, it) }
                                if (name != null || comment != null) trackViewModel.updateTrack(id, name, comment)
                            },
                            onShareGpx = { onShareTrack(track.id) },
                            onResumeTrack = { id -> onResumeRequest(id, false) },
                            isRecording = trackRecorderState.state == ykws.android.maro.data.track.TrackRecorderState.ON,
                            onTap = null,
                            showChevron = false
                        )
                    }
                } else if (markerCardOpen) {
                    // The marker card, on the same surface: an inspect-opened one takes the merged
                    // ladder's cursor walk, null everywhere else so a list- or map-opened card walks
                    // its own world exactly as it always did.
                    MarkerDrawer(
                        viewModel = markersViewModel,
                        isLandscape = true,
                        onClose = onMarkerDrawerClose,
                        boatPosition = boatPosition,
                        onRequestDelete = onRequestMarkerDelete,
                        trackTitleLookup = trackTitleLookup,
                        onOpenMarkerTrack = { id -> onMarkerDrawerClose(); onOpenMarkerTrack(id) },
                        onWizardEntry = onMarkerWizardEntry,
                        walk = markerInspectWalk
                    )
                }
            }
        } else {
            val track = trackInfoDrawerData
            val summary = track?.let {
                ykws.android.maro.data.track.TrackSummary(
                    id = it.id,
                    name = it.name,
                    comment = it.comment,
                    startTimeMs = it.startTimeMs,
                    endTimeMs = it.endTimeMs,
                    lastPointTimeMs = it.lastPointTimeMs,
                    fastestSpeedMps = it.fastestSpeedMps,
                    distanceNm = it.distanceNm,
                    navigatingDurationSec = it.navigatingDurationSec,
                    pausedDurationSec = it.pausedDurationSec,
                    averageSpeedMps = it.averageSpeedMps,
                    pinned = it.pinned,
                    pointCount = it.trackPoints.size,
                    idleDurationSec = it.idleDurationSec,
                    // The flag rides the hand-built summary too: the two dashboard cards render the
                    // same card as the list, so a route opened there must read as a route — its three
                    // cells, its creation stamp and its refused Resume all hang off this one field.
                    route = it.route
                )
            }
            var cardHeight by remember { mutableStateOf(0.dp) }
            var footerMeasuredHeight by remember { mutableStateOf(0.dp) }
            // Header is 48dp (DrawerHeader heightIn min) incl. its 6dp bottom padding (post-header gap).
            // Render-measure-resize: size the drawer to header + card + measured footer so the card sits
            // right below the header with a ~6dp gap and no leftover scroll.
            val targetHeight = maxOf(portraitDashboardHeight, 48.dp + cardHeight + footerMeasuredHeight)
            val animatedHeight by animateDpAsState(targetHeight, tween(250))

            // The portrait half of the one selected-item slot: the slot stays mounted and only its
            // content and its measured height change (the landscape half is declared above).
            DrawerSlot(
                visible = (markerCardOpen || showTrackInfoDrawer) && !panelOwnsRegion,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    // Only the track panel measures itself; the marker panel keeps the wrap-content
                    // sizing it has always had, floored at the dashboard height.
                    .then(if (showTrackInfoDrawer) Modifier.height(animatedHeight) else Modifier),
                slideDirection = SlideDirection.FROM_BOTTOM,
                shadowEdge = ShadowEdge.TOP
            ) {
                if (showTrackInfoDrawer && track != null && summary != null) {
                    DrawerScaffold(
                        title = track.name,
                        onClose = onTrackDrawerClose,
                        bottomAnchoredContent = true,
                        suppressOverscrollWhenFits = true,
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
                        headerActions = {
                            TrackDrawerHeaderActions(
                                bandedOn = eyeOverride ?: trackInfoColours,
                                onToggleEyeOverride = onToggleEyeOverride,
                                onDelete = { onDeleteTrack(track.id) }
                            )
                        },
                        footer = {
                            if (trackListIds.size > 1) {
                                Spacer(Modifier.height(10.dp))
                                val accentBg = ComposeColor(AppConfig.uiAccent)
                                val accentFg = ComposeColor(AppConfig.uiTextPrimary)
                                val disabledAlpha = 0.35f
                                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Box(Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                                        .background(accentBg.copy(alpha = if (isAtTrackFirst) disabledAlpha else 1f))
                                        .then(if (!isAtTrackFirst) Modifier.clickable { onTrackPrev() } else Modifier)
                                        .padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                        Text(stringResource(R.string.action_previous), color = accentFg.copy(alpha = if (isAtTrackFirst) disabledAlpha else 1f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Box(Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                                        .background(accentBg.copy(alpha = if (isAtTrackLast) disabledAlpha else 1f))
                                        .then(if (!isAtTrackLast) Modifier.clickable { onTrackNext() } else Modifier)
                                        .padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                        Text(stringResource(R.string.action_next), color = accentFg.copy(alpha = if (isAtTrackLast) disabledAlpha else 1f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                            }
                        }
                    ) {
                        TrackCardContent(
                            summary = summary,
                            dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US),
                            accentColor = trackAccent,
                            onUpdateTrack = { id, name, comment, pinned ->
                                pinned?.let { trackViewModel.setPinned(id, it) }
                                if (name != null || comment != null) trackViewModel.updateTrack(id, name, comment)
                            },
                            onShareGpx = { onShareTrack(track.id) },
                            onResumeTrack = { id -> onResumeRequest(id, false) },
                            isRecording = trackRecorderState.state == ykws.android.maro.data.track.TrackRecorderState.ON,
                            onTap = null,
                            showChevron = false
                        )
                    }
                } else if (markerCardOpen) {
                    // The marker card, on the same surface: an inspect-opened one takes the merged
                    // ladder's cursor walk, null everywhere else so a list- or map-opened card walks
                    // its own world exactly as it always did.
                    MarkerDrawer(
                        viewModel = markersViewModel,
                        isLandscape = false,
                        onClose = onMarkerDrawerClose,
                        boatPosition = boatPosition,
                        onRequestDelete = onRequestMarkerDelete,
                        trackTitleLookup = trackTitleLookup,
                        onOpenMarkerTrack = { id -> onMarkerDrawerClose(); onOpenMarkerTrack(id) },
                        onWizardEntry = onMarkerWizardEntry,
                        // Portrait marker detail drawer must never be smaller than the original
                        // dashboard — its wrap-content panel floors at portraitDashboardHeight.
                        minPanelHeight = portraitDashboardHeight,
                        walk = markerInspectWalk
                    )
                }
            }

            MeasureHeight(onMeasured = { cardHeight = it }) {
                if (summary != null) {
                    Box(Modifier.padding(start = 12.dp, end = 12.dp)) {
                        TrackCardContent(
                            summary = summary,
                            dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US),
                            accentColor = trackAccent,
                            onUpdateTrack = { _, _, _, _ -> },
                            onShareGpx = {},
                            onTap = null,
                            showChevron = false
                        )
                    }
                }
            }

            // Render-measure-resize for the Prev/Next footer (mirrors the real footer structure).
            // NOTE: MeasureHeight measures only measurables[0], so the footer must be a single Column child.
            MeasureHeight(onMeasured = { footerMeasuredHeight = it }) {
                if (trackListIds.size > 1) {
                    Column {
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.weight(1f).padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.action_previous), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                            Box(Modifier.weight(1f).padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.action_next), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }

        // ── 5. TrackHistory ──────────────────────────────────────────────
        DrawerSlot(
            visible = showTrackHistory,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .then(
                    if (isLandscape) Modifier.width(landscapeDashboardWidth * AppConfig.uiLandscapePanelWidthScale)
                    else Modifier.fillMaxWidth()
                )
                .fillMaxHeight(),
            slideDirection = SlideDirection.FROM_RIGHT,
            shadowEdge = ShadowEdge.LEFT
        ) {
            TrackHistoryOverlay(
                trackSummaries = trackSummaries,
                liveTrackState = trackRecorderState,
                onUpdateTrack = { id, name, comment, pinned ->
                    pinned?.let { trackViewModel.setPinned(id, it) }
                    if (name != null || comment != null) trackViewModel.updateTrack(id, name, comment)
                },
                onUpdateLiveTrack = { name, comment ->
                    trackViewModel.updateLiveTrackMeta(name, comment)
                },
                onAction = onTrackAction,
                onDismiss = onDismissTrackHistory,
                onNavigateToTrack = onNavigateToTrack,
                onResumeTrack = { id -> onResumeRequest(id, true) },
                onMergeTracks = { ids, name, keepOriginals ->
                    trackViewModel.mergeTracks(ids, name, keepOriginals)
                },
                sortState = trackSortState,
                onSortStateChange = onTrackSortStateChange,
                filterState = trackFilterState,
                onFilterChange = onTrackFilterChange,
                onReset = onTrackReset,
                filterLinked = trackFilterLinked,
                onToggleLink = onToggleTrackLink,
                tracksVisible = appSettings.tracksVisible,
                trackingRenderNb = appSettings.trackingRenderNb,
                routeRenderNb = appSettings.routeRenderNb,
                trackingTransparencyNewest = appSettings.trackingTransparencyNewest,
                trackingTransparencyOldest = appSettings.trackingTransparencyOldest,
                trackingColorPastFrom = appSettings.trackingColorPastFrom,
                trackingColorPastTo = appSettings.trackingColorPastTo,
                trackingTransparencyPinnedNewest = appSettings.trackingTransparencyPinnedNewest,
                trackingTransparencyPinnedOldest = appSettings.trackingTransparencyPinnedOldest,
                trackingColorPinnedFrom = appSettings.trackingColorPinnedFrom,
                trackingColorPinnedTo = appSettings.trackingColorPinnedTo,
                trackingTransparencyRouteNewest = appSettings.trackingTransparencyRouteNewest,
                trackingTransparencyRouteOldest = appSettings.trackingTransparencyRouteOldest,
                trackingColorRouteFrom = appSettings.trackingColorRouteFrom,
                trackingColorRouteTo = appSettings.trackingColorRouteTo,
                lazyListState = trackListState
            )
        }

        // ── 6. Marker Management ─────────────────────────────────────────
        DrawerSlot(
            visible = showMarkerManagement,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .then(
                    if (isLandscape) Modifier.width(landscapeDashboardWidth * AppConfig.uiLandscapePanelWidthScale)
                    else Modifier.fillMaxWidth()
                )
                .fillMaxHeight(),
            slideDirection = SlideDirection.FROM_RIGHT,
            shadowEdge = ShadowEdge.LEFT
        ) {
            MarkerManagementOverlay(
                markers = markers,
                trackTitleLookup = trackTitleLookup,
                onOpenMarkerTrack = { id -> onDismissMarkerManagement(); onOpenMarkerTrack(id) },
                onAction = onMarkerAction,
                onCreateFirst = onCreateFirst,
                onDismiss = onDismissMarkerManagement,
                onSetIcon = onSetIcon,
                onSetPin = onSetPin,
                onUpdateMarkerText = onUpdateMarkerText,
                sortState = markerSortState,
                onSortStateChange = onMarkerSortStateChange,
                filterState = markerFilterState,
                onFilterChange = onMarkerFilterChange,
                onReset = onMarkerReset,
                filterLinked = markerFilterLinked,
                onToggleLink = onToggleMarkerLink,
                lazyListState = markerListState
            )
        }

        // ── 7. Settings ──────────────────────────────────────────────────
        DrawerSlot(
            visible = showSettings,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .then(
                    if (isLandscape) Modifier.width(landscapeDashboardWidth * AppConfig.uiLandscapePanelWidthScale)
                    else Modifier.fillMaxWidth()
                )
                .fillMaxHeight(),
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
                positionScrollState = positionScrollState,
                systemScrollState = systemScrollState,
                onRegenerateRasters = onRegenerateRasters
            )
        }
    }
}

/**
 * The track drawer's header actions: the speed toggle, then the trash.
 *
 * The toggle carries no label — its state rides the icon convention, the accent at full alpha while the
 * selected track is banded and the inactive alpha token otherwise — and it wears the same speedometer
 * the speed scale's collapsed face does, so the control that reads the ramp and the one that flips it
 * for a single track speak one visual language. It moves that one track's fill and never the stored
 * flags (D10), and the map's legend doubles as its readout in that direction: the legend is drawn while
 * a banded stroke is on the map *and*, once a track is open, while that track's own fill is the ramp —
 * so the scale follows the very fill this toggle owns.
 */
@Composable
private fun TrackDrawerHeaderActions(
    bandedOn: Boolean,
    onToggleEyeOverride: () -> Unit,
    onDelete: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        IconButton(onClick = onToggleEyeOverride, modifier = Modifier.size(36.dp)) {
            Icon(
                Speed,
                "Track rendering",
                tint = ButtonColors.icon.copy(
                    alpha = if (bandedOn) 1f else AppConfig.buttonActionIconInactiveAlpha
                ),
                modifier = Modifier.size(24.dp)
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.Delete, "Delete", tint = ButtonColors.icon, modifier = Modifier.size(24.dp))
        }
    }
}
