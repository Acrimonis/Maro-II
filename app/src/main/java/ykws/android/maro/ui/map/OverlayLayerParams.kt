package ykws.android.maro.ui.map

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import ykws.android.maro.data.model.ListFilter

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
