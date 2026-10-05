package ykws.android.maro.ui.map

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import ykws.android.maro.data.model.markers.UserMarker
import ykws.android.maro.data.settings.AppSettings

/**
 * Builders for the read-only `OverlayLayer` data bundles.
 *
 * Each builder is a verbatim move of one construction that lived inline in `MapScreen.kt`'s
 * `OverlayLayer` call: the expressions are unchanged and the read-only values that feed them travel
 * as parameters. A construction whose inputs would need more than eight parameters stays inline in
 * `MapScreen` rather than gain an unwieldy signature here.
 */

/** The menu drawer's data bundle, moved verbatim from the `OverlayLayer` call site. */
internal fun buildMenuOverlayData(
    appSettings: AppSettings,
    firstTrackId: String?,
    firstRouteId: String?,
    firstMarkerId: String?,
    trackMapVisibleCount: Int,
    routeMapVisibleCount: Int,
    markerMapCount: Int,
): MenuOverlayData = MenuOverlayData(
    firstTrackId = firstTrackId,
    firstRouteId = firstRouteId,
    firstMarkerId = firstMarkerId,
    trackMapFilterState = appSettings.trackMapFilter,
    trackMapCount = trackMapVisibleCount,
    routeMapFilterState = appSettings.routeMapFilter,
    routeMapCount = routeMapVisibleCount,
    markerMapFilterState = appSettings.markerMapFilter,
    markerMapCount = markerMapCount,
)

/** The track-history surface's data bundle, moved verbatim from the `OverlayLayer` call site. */
internal fun buildTrackListOverlayData(
    appSettings: AppSettings,
    trackListState: LazyListState,
): TrackListOverlayData = TrackListOverlayData(
    trackSortState = appSettings.trackListSort,
    trackFilterState = appSettings.trackListFilter,
    trackListState = trackListState,
)

/** The routes list's data bundle — the route-scoped mirror of [buildTrackListOverlayData] (D4). */
internal fun buildRouteListOverlayData(
    appSettings: AppSettings,
    routeListState: LazyListState,
): RouteListOverlayData = RouteListOverlayData(
    routeSortState = appSettings.routeListSort,
    routeFilterState = appSettings.routeListFilter,
    routeListState = routeListState,
)

/** The settings surface's data bundle, moved verbatim from the `OverlayLayer` call site. */
internal fun buildSettingsOverlayData(
    selectedTab: SettingsTab,
    layersScrollState: ScrollState,
    navigationScrollState: ScrollState,
    routingScrollState: ScrollState,
    systemScrollState: ScrollState,
): SettingsOverlayData = SettingsOverlayData(
    selectedTab = selectedTab,
    layersScrollState = layersScrollState,
    navigationScrollState = navigationScrollState,
    routingScrollState = routingScrollState,
    systemScrollState = systemScrollState,
)

/** The marker-management surface's data bundle, moved verbatim from the `OverlayLayer` call site. */
internal fun buildMarkerListOverlayData(
    markers: List<UserMarker>,
    appSettings: AppSettings,
    markerListState: LazyListState,
): MarkerListOverlayData = MarkerListOverlayData(
    markers = markers,
    markerSortState = appSettings.markerListSort,
    markerFilterState = appSettings.markerListFilter,
    markerListState = markerListState,
)

/** The track-info drawer's data bundle, moved verbatim from the `OverlayLayer` call site. */
internal fun buildTrackInfoOverlayData(
    trackDrawerState: TrackDrawerState,
    trackListIds: List<String>,
    inspectHandoff: InspectHandoff?,
    appSettings: AppSettings,
    onToggleEyeOverride: () -> Unit,
): TrackInfoOverlayData = TrackInfoOverlayData(
    showTrackInfoDrawer = trackDrawerState.isOpen,
    trackInfoDrawerData = trackDrawerState.track,
    trackListIds = trackListIds,
    currentTrackIndex = trackListIds.indexOf(trackDrawerState.track?.id ?: "").coerceAtLeast(0),
    // An in-flight inspect open holds this card as its predecessor: both walk buttons grey
    // out for that window rather than letting a second step cancel the pending landing (§5).
    walkHeld = inspectHandoff != null,
    trackColours = appSettings.trackColours,
    eyeOverride = appSettings.trackSelectionBanded,
    onToggleEyeOverride = onToggleEyeOverride,
)
