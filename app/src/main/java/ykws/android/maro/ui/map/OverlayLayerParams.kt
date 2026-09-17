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
    /**
     * True while any modal `ConfirmDialog` is visible. The ladder scrim yields to the dialog's own
     * scrim so the two dim layers never stack; both are hard on/off toggles (no fade).
     */
    val dialogScrimActive: Boolean = false,
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
    /** The arrows axis the menu's first chip reads and writes (D5). */
    val trackArrows: Boolean,
    /** The colours axis its second chip reads and writes — the same single owner (D5). */
    val trackColours: Boolean,
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
    /**
     * The colours axis, which the drawer header's eye mirrors while it has never been tapped. It rides
     * the track-info bundle because the control lives in that drawer's header — the alternative was
     * widening an already wide `OverlayLayer` (B17).
     */
    val trackColours: Boolean = true,
    /**
     * The drawer header's eye (D10): the selected track's own override, null meaning "follow
     * [trackColours]" — which is what an install whose eye was never tapped holds, the key being
     * written from the first tap on. It lives on the selection, so it applies to whichever track the
     * drawer has open, and it moves that track's fill alone: the chevrons follow the arrows flag
     * whatever the eye says. It never moves either flag — the menu's twin box stays their only writer.
     */
    val eyeOverride: Boolean? = null,
    /** Drawer-header eye toggle: flips [eyeOverride] for the selected track alone. */
    val onToggleEyeOverride: () -> Unit = {},
    /**
     * Inspect mode's provenance flag (plan §5): the frozen distance ladder, in walk order, when the
     * card on screen was opened by an inspect pick, and null when it was opened anywhere else.
     *
     * It decides whether [trackListIds] / [currentTrackIndex] read the ladder or the list world, so
     * a list-opened track keeps walking the list even while the mode is armed — and it is also the
     * flag the drawer's own close path reads to disarm.
     */
    val inspectLadder: List<String>? = null,
    /**
     * True while an inspect open is in flight, so this card is the predecessor being held for its
     * successor (plan §5): both walk buttons read as at their end and grey out, because a step taken
     * now would rewrite the navigate target and cancel the open that is about to land here.
     */
    val walkHeld: Boolean = false,
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
