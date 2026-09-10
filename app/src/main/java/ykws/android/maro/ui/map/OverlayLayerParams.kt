package ykws.android.maro.ui.map

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import ykws.android.maro.data.model.ListFilter
import ykws.android.maro.data.model.ListSortState
import ykws.android.maro.data.model.markers.UserMarker
import ykws.android.maro.data.track.Track

/**
 * `OverlayChrome` — visibility/state bundle for the overlay chrome surfaces
 * (scrim, wizard, and every `DrawerSlot`). Field names mirror the former
 * `OverlayLayer` parameters verbatim.
 *
 * Contract: all fields are `val`. Mutable state is read through its own holder,
 * never via equality — do not convert a field to `var`.
 */
@Immutable
data class OverlayChrome(
    val showSettings: Boolean,
    val showTrackDrawer: Boolean,
    val showTrackHistory: Boolean,
    val showMarkerManagement: Boolean,
    val showWizard: Boolean,
    val wizardStep: WizardStep?,
    val drawerState: MarkerDrawerState,
)

/**
 * `MenuOverlayData` — read-only data bundle for the menu drawer surface
 * (`MenuDrawerOverlay`). Field names mirror the former `OverlayLayer`
 * parameters verbatim.
 *
 * Contract: all fields are `val`. Mutable state is read through its own holder,
 * never via equality — do not convert a field to `var`.
 */
@Immutable
data class MenuOverlayData(
    val gpsMode: Boolean,
    val autoShowMasterVisible: Boolean,
    val autoShowMasterOverride: Boolean,
    val gpsToggleColor: Color,
    val markerZonesVisible: Boolean,
    val tracksDirectionVisible: Boolean,
    val firstTrackId: String?,
    val firstMarkerId: String?,
    val trackMapFilterState: ListFilter,
    val trackMapCount: Int,
    val markerMapFilterState: ListFilter,
    val markerMapCount: Int,
)

/**
 * `SettingsOverlayData` — read-only data bundle for the settings surface
 * (`SettingsOverlay`). Field names mirror the former `OverlayLayer` parameters
 * verbatim.
 *
 * Contract: all fields are `val`. Mutable state is read through its own holder,
 * never via equality — do not convert a field to `var`.
 */
@Immutable
data class SettingsOverlayData(
    val selectedTab: Int,
    val displayScrollState: ScrollState,
    val navigationScrollState: ScrollState,
    val positionScrollState: ScrollState,
    val systemScrollState: ScrollState,
)

/**
 * `TrackInfoOverlayData` — read-only data bundle for the track-info surface
 * (track-info slot). Field names mirror the former `OverlayLayer` parameters
 * verbatim.
 *
 * Contract: all fields are `val`. Mutable state is read through its own holder,
 * never via equality — do not convert a field to `var`.
 */
@Immutable
data class TrackInfoOverlayData(
    val showTrackInfoDrawer: Boolean,
    val trackInfoDrawerData: Track?,
    val trackListIds: List<String>,
    val currentTrackIndex: Int,
)

/**
 * `TrackListOverlayData` — read-only data bundle for the track-history surface
 * (`TrackHistoryOverlay`). Field names mirror the former `OverlayLayer`
 * parameters verbatim.
 *
 * Contract: all fields are `val`. Mutable state is read through its own holder,
 * never via equality — do not convert a field to `var`.
 */
@Immutable
data class TrackListOverlayData(
    val trackSortState: ListSortState,
    val trackFilterState: ListFilter,
    val trackListState: LazyListState,
)

/**
 * `MarkerListOverlayData` — read-only data bundle for the marker-management
 * surface (`MarkerManagementOverlay`). Field names mirror the former
 * `OverlayLayer` parameters verbatim.
 *
 * Contract: all fields are `val`. Mutable state is read through its own holder,
 * never via equality — do not convert a field to `var`.
 */
@Immutable
data class MarkerListOverlayData(
    val markers: List<UserMarker>,
    val markerSortState: ListSortState,
    val markerFilterState: ListFilter,
    val markerListState: LazyListState,
)
