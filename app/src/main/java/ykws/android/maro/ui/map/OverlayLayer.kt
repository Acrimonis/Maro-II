package ykws.android.maro.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.res.stringResource
import ykws.android.maro.R
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.settings.AppSettings
import ykws.android.maro.data.depth.RasterCache
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.UserMarker
import ykws.android.maro.ui.components.ConfirmAction
import ykws.android.maro.ui.components.ConfirmActionButton
import ykws.android.maro.ui.components.ConfirmActionRole
import ykws.android.maro.ui.components.DrawerScaffold
import ykws.android.maro.ui.icons.Speed

/** Returns the step sequence for the given marker type (mirror of VM method for UI use). */
private fun stepSequenceFor(type: MarkerType): List<WizardStep> = when (type) {
    MarkerType.PIN -> listOf(
        WizardStep.TypeSelect, WizardStep.Position,
        WizardStep.Proximity, WizardStep.RoutingCost, WizardStep.Title, WizardStep.Description
    )
    MarkerType.CIRCLE -> listOf(
        WizardStep.TypeSelect, WizardStep.Position,
        WizardStep.Radius, WizardStep.Proximity, WizardStep.RoutingCost, WizardStep.Title,
        WizardStep.Description
    )
    MarkerType.CORRIDOR -> listOf(
        WizardStep.TypeSelect, WizardStep.Position,
        WizardStep.PositionP2, WizardStep.Radius,
        WizardStep.Proximity, WizardStep.RoutingCost, WizardStep.Title, WizardStep.Description
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
    /** The dashboard's base height — the portrait floor every panel uses (R2, R6). */
    dashboardBaseHeight: Dp,
    landscapeDashboardWidth: Dp,
    /**
     * The one portrait band ceiling (F5), computed by `MapScreen` — threaded to every portrait
     * bottom panel so a taller one's body scrolls instead of covering the map strip. Null keeps
     * the full-screen ceiling; landscape ignores it, its frame being untouched.
     */
    panelMaxHeight: Dp? = null,
    /**
     * The open portrait bottom dashboard's measured height, surfaced to the map (Phase 2). The base
     * is reported while nothing is open, so the band returns to the floor.
     */
    onDashboardMeasuredHeight: ((Dp) -> Unit)? = null,
    /**
     * The height the outgoing card in the selected-item slot reported — the seed the incoming card's
     * wrap frame is pre-sized at, so a route↔marker swap in that slot starts at the size already on
     * screen (2026-10-07). Landscape ignores it, its frame being the full height already.
     */
    initialDashboardHeight: Dp? = null,

    // ── Callbacks ────────────────────────────────────────────────────────
    onDismissSettings: () -> Unit,
    onDismissMenu: () -> Unit,
    onDismissTrackHistory: () -> Unit,
    onDismissRouteHistory: () -> Unit = {},
    onDismissMarkerManagement: () -> Unit,
    onWizardCancel: () -> Unit,
    onMarkerDrawerClose: () -> Unit,
    /** R1: the marker wizard takes the dashboard slot — the other selected-item dashboard closes first. */
    onMarkerWizardEntry: () -> Unit = {},
    onOpenTrackHistoryFromMenu: () -> Unit,
    onOpenRouteHistoryFromMenu: () -> Unit = {},
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
     * The route mode's read-only summary, arriving as a bundle rather than as seven more parameters
     * on this already wide signature — the shape `OverlayLayerParams.kt` exists for. Only the menu
     * drawer reads it; the mode's own state lives in `RouteViewModel`.
     */
    routeSummary: RouteSummaryData = RouteSummaryData(),

    // ── Track history data ───────────────────────────────────────────────
    onTrackAction: (ykws.android.maro.data.model.ListAction) -> Unit,
    trackList: TrackListOverlayData,
    /** The routes list's own bundle, read only at [ListScope.ROUTES] (S11, D4). */
    routeList: RouteListOverlayData = RouteListOverlayData(),
    onTrackSortStateChange: (ykws.android.maro.data.model.ListSortState) -> Unit,
    onTrackFilterChange: (ykws.android.maro.data.model.ListFilter) -> Unit = {},
    onTrackReset: () -> Unit = {},
    // Map referential (menu filter) + link flag. The link toggle lives in the menu only.
    onTrackMapFilterChange: (ykws.android.maro.data.model.ListFilter) -> Unit = {},
    onTrackMapReset: () -> Unit = {},
    trackFilterLinked: Boolean = true,
    onToggleTrackLink: () -> Unit = {},
    // ── Routes list filter + map referential (the route-scoped mirror of the track set) ──
    onRouteSortStateChange: (ykws.android.maro.data.model.ListSortState) -> Unit = {},
    onRouteFilterChange: (ykws.android.maro.data.model.ListFilter) -> Unit = {},
    onRouteReset: () -> Unit = {},
    onRouteMapFilterChange: (ykws.android.maro.data.model.ListFilter) -> Unit = {},
    onRouteMapReset: () -> Unit = {},
    routeFilterLinked: Boolean = true,
    onToggleRouteLink: () -> Unit = {},
    /** The two kinds' map-visibility eyes in the drawer headers (2026-10-05). */
    onToggleTrackVisible: () -> Unit = {},
    onToggleRouteVisible: () -> Unit = {},
    /** The routes chevron's own menu world, mirroring [onOpenFirstTrack]. */
    onOpenFirstRoute: (String) -> Unit = {},

    // ── Settings data ────────────────────────────────────────────────────
    appSettings: AppSettings,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    settings: SettingsOverlayData,
    onTabChange: (SettingsTab) -> Unit,
    onRegenerateRasters: (List<RasterCache.Step>) -> Unit,

    // ── Marker drawer data ───────────────────────────────────────────────
    boatPosition: LatLng?,

    // ── Track info drawer data ───────────────────────────────────────────
    trackInfo: TrackInfoOverlayData,
    onTrackDrawerClose: () -> Unit = {},
    onNavigateToTrack: (String) -> Unit = {},
    /** Opens the resume confirmation sheet; `fromList` selects which surface closes on confirm. */
    onResumeRequest: (String, Boolean) -> Unit = { _, _ -> },
    /** Follows a saved route from its card — the route mode's own door, no confirmation sheet. */
    onFollowRequest: (String, Boolean) -> Unit = { _, _ -> },
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
    val showRouteHistory = chrome.showRouteHistory
    val showMarkerManagement = chrome.showMarkerManagement
    val showWizard = chrome.showWizard
    val wizardStep = chrome.wizardStep
    val drawerState = chrome.drawerState
    val dialogScrimActive = chrome.dialogScrimActive
    val routeSummaryVisible = chrome.routeSummaryVisible
    val firstTrackId = menu.firstTrackId
    val firstMarkerId = menu.firstMarkerId
    val trackMapFilterState = menu.trackMapFilterState
    val trackMapCount = menu.trackMapCount
    val markerMapFilterState = menu.markerMapFilterState
    val markerMapCount = menu.markerMapCount
    val selectedTab = settings.selectedTab
    val layersScrollState = settings.layersScrollState
    val navigationScrollState = settings.navigationScrollState
    val routingScrollState = settings.routingScrollState
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
    val routeSummaries by trackViewModel.routeSummaries.collectAsState()

    // ── The one list surface, resolved to a scope (S4, S11) ──────────────────
    // The two chrome flags are mutually exclusive, so whichever stands names the scope — never a
    // nullable scope and never both at once. Every value the surface reads is picked here, so the
    // tracks list and the routes list share one composable and one shape without sharing a referential.
    val listScope = listScopeOf(showTrackHistory, showRouteHistory) ?: ListScope.TRACKS
    val activeListSummaries = if (listScope == ListScope.ROUTES) routeSummaries else trackSummaries
    val activeListSortState = if (listScope == ListScope.ROUTES) routeList.routeSortState else trackSortState
    val activeListFilterState = if (listScope == ListScope.ROUTES) routeList.routeFilterState else trackFilterState
    val activeListState = if (listScope == ListScope.ROUTES) routeList.routeListState else trackListState
    val activeListOnSortChange = if (listScope == ListScope.ROUTES) onRouteSortStateChange else onTrackSortStateChange
    val activeListOnFilterChange = if (listScope == ListScope.ROUTES) onRouteFilterChange else onTrackFilterChange
    val activeListOnReset = if (listScope == ListScope.ROUTES) onRouteReset else onTrackReset
    val activeListLinked = if (listScope == ListScope.ROUTES) routeFilterLinked else trackFilterLinked
    val activeListOnToggleLink = if (listScope == ListScope.ROUTES) onToggleRouteLink else onToggleTrackLink
    val activeListOnDismiss = if (listScope == ListScope.ROUTES) onDismissRouteHistory else onDismissTrackHistory

    // ── Keyboard: read for the scrim only ────────────────────────────────
    // The wizard is positioned by the platform's own pan, as the track card's fields are (P7a).
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    val imeHeightDp = with(LocalDensity.current) { imeBottom.toDp() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // ── Ladder scrim ─────────────────────────────────────────────────────
    // A plain on/off dim (no fade) that touch-blocks the whole screen (map included) while a drawer,
    // settings or the wizard is open. It yields to a visible `ConfirmDialog`, which paints its own
    // scrim above the drawers, so the two dim layers never stack.

    val showScrim = (showSettings
        || showTrackDrawer
        || showTrackHistory
        || showRouteHistory
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
    val panelOwnsRegion = showTrackDrawer || showSettings || showTrackHistory || showRouteHistory || showMarkerManagement

    // ── The open portrait bottom dashboard's measured height (Phase 2) ─────────────────
    // Each wrap-content bottom panel reports its own measured height, and the base is reported
    // while none is open so the map band returns to the floor (R5, R6).
    val portraitBottomDashboardOpen = !isLandscape && !panelOwnsRegion && (
        showWizard || showTrackInfoDrawer ||
            drawerState is MarkerDrawerState.Viewing || drawerState is MarkerDrawerState.MatchResult
        )
    LaunchedEffect(portraitBottomDashboardOpen) {
        if (!portraitBottomDashboardOpen) onDashboardMeasuredHeight?.invoke(dashboardBaseHeight)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // ── 1. Scrim (hard toggle — no animation) ────────────────────────
        if (showScrim) {
            val scrimDismiss: () -> Unit = {
                when {
                    showSettings -> onDismissSettings()
                    showTrackDrawer -> onDismissMenu()
                    showTrackHistory -> onDismissTrackHistory()
                    showRouteHistory -> onDismissRouteHistory()
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
                        steps = seq,
                        step = activeStep,
                        dashboardBaseHeight = dashboardBaseHeight
                    )
                }
            } else {
                // The panel wraps its card and floors at the dashboard height inside the scaffold,
                // so the slot carries no height of its own — only the keyboard offset.
                DrawerSlot(
                    visible = true,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                    slideDirection = SlideDirection.FROM_BOTTOM,
                    shadowEdge = ShadowEdge.TOP
                ) {
                    WizardDrawer(
                        viewModel = markersViewModel,
                        isLandscape = false,
                        onCancel = onWizardCancel,
                        steps = seq,
                        step = activeStep,
                        dashboardBaseHeight = dashboardBaseHeight,
                        onMeasuredHeight = onDashboardMeasuredHeight,
                        panelMaxHeight = panelMaxHeight
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
                routeSummary = routeSummary,
                routeSummaryVisible = routeSummaryVisible,
                recorderState = trackRecorderState,
                trackCount = trackMapCount,
                markerCount = markerMapCount,
                onViewTrackList = {
                    onDismissMenu()
                    onOpenTrackHistoryFromMenu()
                },
                routeCount = menu.routeMapCount,
                onViewRouteList = {
                    onDismissMenu()
                    onOpenRouteHistoryFromMenu()
                },
                onOpenFirstRoute = menu.firstRouteId?.let { id -> { onDismissMenu(); onOpenFirstRoute(id) } },
                routeFilterState = menu.routeMapFilterState,
                onRouteFilterChange = onRouteMapFilterChange,
                onRouteReset = onRouteMapReset,
                routeFilterLinked = routeFilterLinked,
                onToggleRouteLink = onToggleRouteLink,
                trackVisible = menu.tracksVisible,
                routeVisible = menu.routesVisible,
                onToggleTrackVisible = onToggleTrackVisible,
                onToggleRouteVisible = onToggleRouteVisible,
                routeFilterAxes = ykws.android.maro.data.model.trackFilterAxes(),
                onManageMarkers = {
                    onDismissMenu()
                    onOpenMarkerManagementFromMenu()
                },
                onOpenFirstTrack = firstTrackId?.let { id -> { onDismissMenu(); onOpenFirstTrack(id) } },
                onOpenFirstMarker = firstMarkerId?.let { id -> { onDismissMenu(); onOpenFirstMarker(id) } },
                // The create path this layer already holds: the menu closes before the wizard opens,
                // its own order, the chevron's pattern above (D7).
                onCreateMarker = { onDismissMenu(); onCreateFirst() },
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
                    // Landscape's non-wrap frame ignores the floor; stated explicitly so no
                    // default can silently drop R2 (G4).
                    minPanelHeight = 0.dp,
                    walk = markerInspectWalk
                )
            }
        } else {
            DrawerSlot(
                visible = drawerState is MarkerDrawerState.MatchResult && !panelOwnsRegion,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                slideDirection = SlideDirection.FROM_BOTTOM,
                shadowEdge = ShadowEdge.TOP
            ) {
                // The card wraps and floors at the base inside its own frame, like its siblings
                // (R1, R2) — the slot carries no height of its own any more.
                MarkerDrawer(
                    viewModel = markersViewModel,
                    isLandscape = false,
                    onClose = onMarkerDrawerClose,
                    boatPosition = boatPosition,
                    onRequestDelete = onRequestMarkerDelete,
                    trackTitleLookup = trackTitleLookup,
                    onOpenMarkerTrack = { id -> onMarkerDrawerClose(); onOpenMarkerTrack(id) },
                    onWizardEntry = onMarkerWizardEntry,
                    minPanelHeight = dashboardBaseHeight,
                    onMeasuredHeight = onDashboardMeasuredHeight,
                    panelMaxHeight = panelMaxHeight
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
                        // The map card family's one header padding, the marker viewer's own (2026-10-07),
                        // and the same dissolve on a swap.
                        headerHorizontalPadding = 12.dp,
                        fadeInOnEnter = true,
                        statusBarsInset = true,
                        bottomAnchoredContent = true,
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp),
                        // The dashboard's one shape (2026-10-07): no rounded corner on a map dash panel,
                        // in either orientation — the marker card and the route panel are squared with it.
                        shape = RoundedCornerShape(0.dp),
                        headerActions = {
                            TrackDrawerHeaderActions(
                                bandedOn = eyeOverride ?: trackInfoColours,
                                onToggleEyeOverride = onToggleEyeOverride,
                                onDelete = { onDeleteTrack(track.id) }
                            )
                        },
                        // The walk row's one spacing, the route panel's own (2026-10-07): the row's own
                        // 8 dp vertical padding sets both the frame-to-row and the row-to-edge gap, and
                        // Previous/Next sit 8 dp apart — the route footer's frame and its very buttons.
                        footer = {
                            if (trackListIds.size > 1) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    ConfirmActionButton(
                                        action = ConfirmAction(
                                            label = stringResource(R.string.action_previous),
                                            role = ConfirmActionRole.SECONDARY,
                                            enabled = !isAtTrackFirst,
                                            onClick = onTrackPrev
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                    ConfirmActionButton(
                                        action = ConfirmAction(
                                            label = stringResource(R.string.action_next),
                                            role = ConfirmActionRole.PRIMARY,
                                            enabled = !isAtTrackLast,
                                            onClick = onTrackNext
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
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
                            onFollowRoute = { id -> onFollowRequest(id, false) },
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
                        // Landscape's non-wrap frame ignores the floor; stated explicitly so no
                        // default can silently drop R2 (G4).
                        minPanelHeight = 0.dp,
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
            // The portrait half of the one selected-item slot: the slot stays mounted and only its
            // content changes (the landscape half is declared above). Whatever it renders — the
            // track card or a marker card — floors and caps inside its own scaffold, so the slot
            // carries no height of its own and the frame is the one home of the panel's height.
            DrawerSlot(
                visible = (markerCardOpen || showTrackInfoDrawer) && !panelOwnsRegion,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                slideDirection = SlideDirection.FROM_BOTTOM,
                shadowEdge = ShadowEdge.TOP
            ) {
                if (showTrackInfoDrawer && track != null && summary != null) {
                    DrawerScaffold(
                        title = track.name,
                        onClose = onTrackDrawerClose,
                        // The map card family's one header padding and Scrollable body, the marker
                        // viewer's own (2026-10-07): a long card scrolls rather than clipping, and the
                        // card dissolves in on a swap.
                        headerHorizontalPadding = 12.dp,
                        fadeInOnEnter = true,
                        scrollable = true,
                        // The portrait track card wears the same wrap-content frame as its siblings
                        // (R1, R2): it floors at the base, caps at the band ceiling (R5, F5) and
                        // reports its own measured height to the map, so the frame — not a probe —
                        // holds the card's height (G3).
                        wrapContent = true,
                        wrapContentMinHeight = dashboardBaseHeight,
                        initialHeight = initialDashboardHeight,
                        onMeasuredHeight = onDashboardMeasuredHeight,
                        wrapContentMaxHeight = panelMaxHeight,
                        bottomAnchoredContent = true,
                        suppressOverscrollWhenFits = true,
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp),
                        // Square top corners, like every portrait bottom panel.
                        shape = RoundedCornerShape(0.dp),
                        headerActions = {
                            TrackDrawerHeaderActions(
                                bandedOn = eyeOverride ?: trackInfoColours,
                                onToggleEyeOverride = onToggleEyeOverride,
                                onDelete = { onDeleteTrack(track.id) }
                            )
                        },
                        // The walk row's one spacing, the route panel's own (2026-10-07): the row's own
                        // 8 dp vertical padding sets both the frame-to-row and the row-to-edge gap, and
                        // Previous/Next sit 8 dp apart — the route footer's frame and its very buttons.
                        footer = {
                            if (trackListIds.size > 1) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    ConfirmActionButton(
                                        action = ConfirmAction(
                                            label = stringResource(R.string.action_previous),
                                            role = ConfirmActionRole.SECONDARY,
                                            enabled = !isAtTrackFirst,
                                            onClick = onTrackPrev
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                    ConfirmActionButton(
                                        action = ConfirmAction(
                                            label = stringResource(R.string.action_next),
                                            role = ConfirmActionRole.PRIMARY,
                                            enabled = !isAtTrackLast,
                                            onClick = onTrackNext
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
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
                            onFollowRoute = { id -> onFollowRequest(id, false) },
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
                        // dashboard — its wrap-content panel floors at dashboardBaseHeight.
                        minPanelHeight = dashboardBaseHeight,
                        onMeasuredHeight = onDashboardMeasuredHeight,
                        panelMaxHeight = panelMaxHeight,
                        walk = markerInspectWalk,
                        initialHeight = initialDashboardHeight
                    )
                }
            }
        }

        // ── 5. Track / Route history ─────────────────────────────────────
        DrawerSlot(
            visible = showTrackHistory || showRouteHistory,
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
                trackSummaries = activeListSummaries,
                liveTrackState = trackRecorderState,
                onUpdateTrack = { id, name, comment, pinned ->
                    pinned?.let { trackViewModel.setPinned(id, it) }
                    if (name != null || comment != null) trackViewModel.updateTrack(id, name, comment)
                },
                onUpdateLiveTrack = { name, comment ->
                    trackViewModel.updateLiveTrackMeta(name, comment)
                },
                onAction = onTrackAction,
                onDismiss = activeListOnDismiss,
                onNavigateToTrack = onNavigateToTrack,
                onResumeTrack = { id -> onResumeRequest(id, true) },
                onFollowTrack = { id -> onFollowRequest(id, true) },
                onMergeTracks = { ids, name, keepOriginals ->
                    trackViewModel.mergeTracks(ids, name, keepOriginals)
                },
                sortState = activeListSortState,
                onSortStateChange = activeListOnSortChange,
                filterState = activeListFilterState,
                onFilterChange = activeListOnFilterChange,
                onReset = activeListOnReset,
                filterLinked = activeListLinked,
                onToggleLink = activeListOnToggleLink,
                // The scope's own kind gates the preview: the routes list keys off routesVisible, the
                // tracks list off tracksVisible (2026-10-05).
                tracksVisible = if (listScope == ListScope.ROUTES) appSettings.routesVisible
                                else appSettings.tracksVisible,
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
                trackingTransparencyPinnedRouteNewest = appSettings.trackingTransparencyPinnedRouteNewest,
                trackingTransparencyPinnedRouteOldest = appSettings.trackingTransparencyPinnedRouteOldest,
                trackingColorPinnedRouteFrom = appSettings.trackingColorPinnedRouteFrom,
                trackingColorPinnedRouteTo = appSettings.trackingColorPinnedRouteTo,
                trackingTransparencyRouteNewest = appSettings.trackingTransparencyRouteNewest,
                trackingTransparencyRouteOldest = appSettings.trackingTransparencyRouteOldest,
                trackingColorRouteFrom = appSettings.trackingColorRouteFrom,
                trackingColorRouteTo = appSettings.trackingColorRouteTo,
                scope = listScope,
                lazyListState = activeListState
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
                onImportTracks = { onDismissSettings(); onTrackAction(ykws.android.maro.data.model.ListAction.ImportTracks) },
                onExportAllTracks = { onDismissSettings(); onTrackAction(ykws.android.maro.data.model.ListAction.BatchExportGpx(trackSummaries.map { it.id }.toSet())) },
                onDismiss = onDismissSettings,
                selectedTab = selectedTab,
                onTabChange = onTabChange,
                layersScrollState = layersScrollState,
                navigationScrollState = navigationScrollState,
                routingScrollState = routingScrollState,
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
