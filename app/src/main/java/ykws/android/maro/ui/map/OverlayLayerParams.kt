package ykws.android.maro.ui.map

import androidx.annotation.StringRes
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Immutable
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.ListFilter
import ykws.android.maro.data.model.ListSortState
import ykws.android.maro.data.model.markers.UserMarker
import ykws.android.maro.data.route.RouteEndSelection
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
    /** The routes list's own gate, mutually exclusive with [showTrackHistory]. */
    val showRouteHistory: Boolean,
    val showMarkerManagement: Boolean,
    val showWizard: Boolean,
    val wizardStep: WizardStep?,
    val drawerState: MarkerDrawerState,
    /**
     * True while any modal `ConfirmDialog` is visible. The ladder scrim yields to the dialog's own
     * scrim so the two dim layers never stack; both are hard on/off toggles (no fade).
     */
    val dialogScrimActive: Boolean = false,
    /**
     * True while the route has something to say — `routeOwnsSlot && (a search is running || a plan
     * stands)`, built in `MapScreen` from `routeOwnsSlot`. It gates the drawer's route summary, so an
     * armed mode with nothing acquired and no search running draws no card at all.
     */
    val routeSummaryVisible: Boolean = false,
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
    val firstTrackId: String?,
    val firstRouteId: String?,
    val firstMarkerId: String?,
    val trackMapFilterState: ListFilter,
    val trackMapCount: Int,
    val routeMapFilterState: ListFilter,
    val routeMapCount: Int,
    val markerMapFilterState: ListFilter,
    val markerMapCount: Int,
    /** The two kinds' own map visibility, so each header's eye draws its on/off face (2026-10-05). */
    val tracksVisible: Boolean = true,
    val routesVisible: Boolean = true,
    /** The layer fan's master gate above both eyes: off, they dim and bite nothing (2026-10-10). */
    val routeTracksVisible: Boolean = true,
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
    val selectedTab: SettingsTab,
    val layersScrollState: ScrollState,
    val navigationScrollState: ScrollState,
    val routingScrollState: ScrollState,
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
     * The drawer header's eye (D10): the open track's own override, **local to the card since
     * 2026-10-07** — null means "follow [trackColours]", which is where a freshly opened card starts,
     * and the value is cleared when the card closes, nothing being written to disk. It rides the
     * selection, so it applies to whichever track the drawer has open, and it moves that track's fill
     * alone: the chevrons follow the arrows axis whatever the eye says. It never moves any axis — the
     * tracks kind's own switches stay their only writers.
     */
    val eyeOverride: Boolean? = null,
    /** Drawer-header eye toggle: flips [eyeOverride] for the selected track alone. */
    val onToggleEyeOverride: () -> Unit = {},
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
 * `RouteListOverlayData` — read-only data bundle for the routes list, the route-scoped mirror of
 * [TrackListOverlayData]: its own sort, its own filter and its own scroll state, so the two lists
 * never share a referential (D4).
 */
@Immutable
data class RouteListOverlayData(
    val routeSortState: ListSortState = ListSortState(),
    val routeFilterState: ListFilter = ListFilter(),
    val routeListState: LazyListState = LazyListState(),
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

/**
 * `RouteSummaryData` — **the drawer's Route section, and the summary kept beside it** (R44, R67).
 *
 * It was the read-only echo of the mode alone (the isolation design's seam 2); R44 puts the mode's
 * **parameters** in the drawer too, so the bundle is widened from that summary into the section's own
 * input: the two selectors' entries, the pair standing in them, and the callbacks that write a
 * selection and arm the acquisition. **The summary is kept rather than absorbed** (R67): its fields
 * ride here exactly as they did, and R68's alternative line stands above its rows.
 *
 * A bundle rather than a dozen more parameters on `OverlayLayer`, which is the shape this file exists
 * for: the section stands in the menu drawer, and the drawer is a ladder surface. **Nothing of the mode
 * state is duplicated into a composable's parameters** — no `RoutePhase` crosses: [searching] gates the
 * acquiring word, [stageRes] carries the engine's boundary while it searches, and a non-null
 * [remaining] is the followed gate. Everything the mode state itself is stays in `RouteViewModel`.
 *
 * Ids, never resolved text, for the strings **this file** carries: a `@StringRes` here and the surface
 * resolves it. A selector's entry labels are different — a flagged marker's own name is data, not a
 * localised label — so they arrive already resolved, which is the `CustomSortField` shape `DropdownRow`
 * takes.
 *
 * Contract: all fields are `val`, like every bundle beside it.
 */
@Immutable
data class RouteSummaryData(
    // ── The Route section's own inputs (R44–R49) ─────────────────────────────
    /** The start selector's entries, in the order it offers them — its first entry is the fallback. */
    val startOptions: List<RouteEndOption> = emptyList(),
    /** The destination selector's entries, in the same order rule. */
    val destinationOptions: List<RouteEndOption> = emptyList(),
    /** The start the section stands on, after the resolution a stale selection falls back through. */
    val startSelection: RouteEndSelection = RouteEndSelection.CurrentPosition,
    /** The destination the section stands on. */
    val destinationSelection: RouteEndSelection = RouteEndSelection.MarkerPosition,
    /** Writes a start selection, which persists per navigation mode (R48). */
    val onStartSelect: (RouteEndSelection) -> Unit = {},
    /** Writes a destination selection, which persists the same way. */
    val onDestinationSelect: (RouteEndSelection) -> Unit = {},

    // ── The quick access to the two settings (2026-10-04) ────────────────────
    /**
     * The pace the quick access stands on, in whole knots off the setting's own grid — the **set** pace,
     * never the boat's fitted one, so the box shows what the planner will read.
     */
    val paceKn: Float = AppConfig.routeFreeWaterPaceKn,
    /** Writes a pace: the same value, on the same setting, as the Settings page's slider. */
    val onPaceSelect: (Float) -> Unit = {},
    /**
     * The preference the quick access stands on, as its rung's λ, resolved the way the Settings page
     * resolves it — so a stored value sitting between two rungs still lands the box on a rung.
     */
    val preference: Float = routeRungLambda(AppConfig.routeAvoidSpeedZoneSoftCostAversion).toFloat(),
    /** Writes a preference rung's λ: the same value, on the same setting, as the Settings page's slider. */
    val onPreferenceSelect: (Float) -> Unit = {},

    // ── The summary kept beside it (R67) ─────────────────────────────────────
    /** True while a search is in flight — the acquiring word's own gate, and the stage's. */
    val searching: Boolean = false,
    /** The engine's boundary while it searches, or null when nothing is searching. */
    @StringRes val stageRes: Int? = null,
    /**
     * The selected route's own number, 1-based over the ladder — the figure the panel's status word
     * carries, so the band reads the same `<stage> #<n>` the header does; null where no page stands.
     */
    val routeNumber: Int? = null,
    /** The plan's own length in nautical miles, or null while no plan stands. */
    val plannedDistanceNm: Double? = null,
    /** The plan's own course seconds — the panel's own derivation, passed in. */
    val plannedEtaSeconds: Double? = null,
    /** The boat-relative remainder while a route is followed; null through the acquisition. */
    val remaining: RouteTripFigure? = null,

    // ── R68's status line, above those rows ──────────────────────────────────
    /**
     * What the best candidate that saves time would save (s), or null where none stands — one line
     * above the summary's rows, which are left unmoved by it.
     */
    val alternativeSavingSec: Double? = null,

    // ── The summary's own band (2026-10-04) ──────────────────────────────────
    /**
     * The followed line's own colour, as Settings holds it — the toggle's acquiring face (R51) and, with
     * the band's own level, the drawer's band while the engine searches. The caller passes it because the
     * colour is the user's; the default is the token the following face wears
     * ([AppConfig.routeNavigateColor]), so a summary that never draws its band still carries a sane one.
     */
    val lineColor: Int = AppConfig.routeNavigateColor,
)

/**
 * One entry of a route-end selector: the selection it stands for and the label the roller shows.
 *
 * The label arrives resolved because two of the three shapes are a `@StringRes` and one is a marker's
 * own name — data rather than UI text, which is why this type carries the string and not an id.
 */
@Immutable
data class RouteEndOption(
    val selection: RouteEndSelection,
    val label: String
)
