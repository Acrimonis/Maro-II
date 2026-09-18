package ykws.android.maro.ui.map

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.markers.UserMarkerRepository
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.ListFilter
import ykws.android.maro.data.model.MapRenderFocus
import ykws.android.maro.data.model.MarkerSelectionPolicy
import ykws.android.maro.data.model.markers.MarkerGeometry
import ykws.android.maro.data.model.markers.UserMarker
import ykws.android.maro.data.model.matchesFilter
import ykws.android.maro.data.settings.AppSettings
import ykws.android.maro.spatial.CoastlineSpatialIndex
import ykws.android.maro.spatial.DebugSegment
import ykws.android.maro.spatial.MarkerMatcher
import ykws.android.maro.spatial.NoOpWhereAmIDebugger
import ykws.android.maro.spatial.VisualWhereAmIDebugger
import ykws.android.maro.spatial.WhereAmIMatch
import ykws.android.maro.spatial.WhereAmIResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

// ─────────────────────────────────────────────────────────────────────────────
// Drawer state sealed hierarchy
// ─────────────────────────────────────────────────────────────────────────────

/** Which mode the marker drawer is in. */
sealed class MarkerDrawerState {
    /** Drawer is hidden. */
    data object Hidden : MarkerDrawerState()

    /** Creating a new marker (now driven by wizard). */
    data object Creating : MarkerDrawerState()

    /** Viewing existing markers' details (read-only, driven by selectedMarkerIds/selectedMarkerIndex). */
    data object Viewing : MarkerDrawerState()

    /** Editing an existing marker by ID (now driven by wizard). */
    data class Editing(val markerId: String) : MarkerDrawerState()

    /** Showing "where am I?" match results. */
    data object MatchResult : MarkerDrawerState()
}

/** Binary marker layer visibility for the fan layer marker toggle. */
enum class MarkerLayerState { HIDDEN, SHOW_ALL }

/** Source of drawer opening — controls prev/next navigation behavior. */
enum class DrawerSource {
    /** Opened from the marker list → prev/next follows the list world, clamps at edges. */
    LIST,
    /** Opened by tapping a marker on the map → prev/next follows the map world, clamps at edges. */
    MAP,
    /**
     * Opened by an inspect pick → prev/next follows the frozen distance ladder, which the inspect
     * cursor above both drawers owns, so this source never walks a world of its own.
     */
    INSPECT,
    /** Opened from whereAmI query → prev/next wraps, existing behavior. */
    WHERE_AM_I
}

/**
 * R2 core — does a change to a referential close the open dashboard's walk?
 *
 * A viewing panel's Prev/Next reads the world its surface was opened from: the list referential for
 * [DrawerSource.LIST], the map referential for [DrawerSource.MAP]. [DrawerSource.INSPECT] answers on
 * the map world because its ladder is snapshotted from the same map-filtered set a map filter change
 * rewrites. [DrawerSource.WHERE_AM_I] walks the match set of the query, which no list or map filter
 * rewrites, so it never closes this way.
 *
 * The caller answers the two flags for the world its change landed in: the list filter and the list sort
 * answer for the list world, the map filter and the map reset for the map world. Membership is the answer
 * where it exists — the selected marker having left that world — while a sort or a reset, which leave no
 * membership to test, pass the world itself as the change.
 */
internal fun scopeClosed(source: DrawerSource, inListWorld: Boolean, inMapWorld: Boolean): Boolean =
    when (source) {
        DrawerSource.LIST -> inListWorld
        DrawerSource.MAP -> inMapWorld
        DrawerSource.INSPECT -> inMapWorld
        DrawerSource.WHERE_AM_I -> false
    }

// ─────────────────────────────────────────────────────────────────────────────
// Create/edit form state
// ─────────────────────────────────────────────────────────────────────────────

/** Which geometry type is being created/edited. */
enum class MarkerType { PIN, CIRCLE, CORRIDOR }

/** Wizard step for the create/edit flow. */
sealed class WizardStep {
    data object TypeSelect : WizardStep()
    data object Position : WizardStep()        // Pin center / Circle center / Corridor P1
    data object PositionP2 : WizardStep()      // Corridor P2 only
    data object Radius : WizardStep()          // Circle / Corridor
    data object Proximity : WizardStep()       // All types
    data object Title : WizardStep()           // All types
    data object Description : WizardStep()     // All types
}

/**
 * Mutable form state for marker creation/editing.
 * Hosted inside [MarkersViewModel] and observed by wizard steps.
 */
data class CreateFormState(
    val name: String = "",
    val type: MarkerType = MarkerType.PIN,
    val position: LatLng? = null,         // pin position / circle centre / corridor p1
    val radiusM: Double = 100.0,
    val widthM: Double = 100.0,
    val proximityOverrideM: String = "",  // empty = use computed default
    val description: String = "",
    val colorIndex: Int = 0,
    val icon: String? = null,            // POI emoji/unicode icon, null = no icon
    // Corridor 2nd-point
    val corridorP2: LatLng? = null
)

// ─────────────────────────────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────────────────────────────

/**
 * StateFlow bridge for user-defined markers.
 *
 * Owns marker CRUD, visibility toggle, drawer state, wizard step navigation,
 * and on-demand "where am I?" match resolution.  Injects coastline data from
 * [CoastlineViewModel] for land-blocking; all compute-intensive work
 * runs on [Dispatchers.Default].
 */
class MarkersViewModel(
    application: Application
) : AndroidViewModel(application) {

    // ── Dependencies ──────────────────────────────────────────────────────

    private val repo: UserMarkerRepository =
        UserMarkerRepository(java.io.File(application.filesDir, "markers"))

    /** Shared map-selection policy — marker map set is filter-only (no cap, no focus override). */
    private val markerSelectionPolicy = MarkerSelectionPolicy()

    /** Marker map path carries no session focus; kept for signature parity with the policy. */
    private val markerMapFocus = MapRenderFocus()

    // ── Settings injection (set via observeSettings from NavigationViewModel) ──

    private var settingsFlow: StateFlow<AppSettings>? = null
    private var updateSettings: (((AppSettings) -> AppSettings) -> Unit)? = null

    // ── StateFlows ────────────────────────────────────────────────────────

    /** WhereAmI debug segments for visual overlay (green = clear, red = blocked). */
    private val _debugSegments = MutableStateFlow<List<DebugSegment>>(emptyList())
    val debugSegments: StateFlow<List<DebugSegment>> = _debugSegments.asStateFlow()

    /** Unfiltered source of truth — reloaded from repository. */
    private val _allMarkers = MutableStateFlow<List<UserMarker>>(emptyList())

    /** Unfiltered marker list — source of truth for whereAmI matching and reloads. */
    val allMarkers: StateFlow<List<UserMarker>> = _allMarkers.asStateFlow()

    /** Loaded user markers (reactive, filtered + sorted by the LIST filter). */
    private val _markers = MutableStateFlow<List<UserMarker>>(emptyList())
    val markers: StateFlow<List<UserMarker>> = _markers.asStateFlow()

    /** User markers filtered by the MAP filter only (no sort) — drives the map overlay. */
    private val _mapMarkers = MutableStateFlow<List<UserMarker>>(emptyList())
    val mapMarkers: StateFlow<List<UserMarker>> = _mapMarkers.asStateFlow()

    /** Unfiltered all-marker ID set — ghost-pin render-time existence checks. */
    val allMarkerIds: StateFlow<Set<String>> = _allMarkers
        .map { list -> list.mapTo(HashSet()) { it.id } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** Binary layer visibility (FanLayout toggle). */
    private val _markerLayerState = MutableStateFlow(MarkerLayerState.HIDDEN)
    val markerLayerState: StateFlow<MarkerLayerState> = _markerLayerState.asStateFlow()

    /** Derived: true when layer is not hidden. */
    val userMarkersVisible: StateFlow<Boolean> =
        markerLayerState.let { flow ->
            MutableStateFlow(flow.value != MarkerLayerState.HIDDEN).also { sf ->
                viewModelScope.launch { flow.collect { sf.value = it != MarkerLayerState.HIDDEN } }
            }.asStateFlow()
        }

    /** Current drawer mode — written only through [setDrawerState], which owns the ray lifetime. */
    private val _drawerState = MutableStateFlow<MarkerDrawerState>(MarkerDrawerState.Hidden)
    val drawerState: StateFlow<MarkerDrawerState> = _drawerState.asStateFlow()

    /** Source of the current drawer opening — controls prev/next behavior. */
    var drawerSource: DrawerSource = DrawerSource.WHERE_AM_I
        private set

    /** Current wizard step (null when wizard is not active). */
    private val _wizardStep = MutableStateFlow<WizardStep?>(null)
    val wizardStep: StateFlow<WizardStep?> = _wizardStep.asStateFlow()

    /** One-shot request to centre the map on a given position (edit mode). */
    private val _mapCenterRequest = MutableStateFlow<LatLng?>(null)
    val mapCenterRequest: StateFlow<LatLng?> = _mapCenterRequest.asStateFlow()

    /** Gate that suspends form position tracking during animateTo (prevents
     *  intermediate mapCenter values from overwriting form position/P2). */
    private val _suspendTracking = MutableStateFlow(false)
    val suspendTracking: StateFlow<Boolean> = _suspendTracking.asStateFlow()

    /** Wizard animation direction: true = forward (Next), false = backward (Previous). */
    var wizardForward = true
        private set

    /** ID of the marker being edited via wizard, or null for creation. */
    private var editingMarkerId: String? = null

    /** ID of the last saved marker (for post-save undo Snackbar). */
    private val _lastSavedMarkerId = MutableStateFlow<String?>(null)
    val lastSavedMarkerId: StateFlow<String?> = _lastSavedMarkerId.asStateFlow()

    /** ID of the currently selected marker (for map highlighting). */
    private val _selectedMarkerId = MutableStateFlow<String?>(null)
    val selectedMarkerId: StateFlow<String?> = _selectedMarkerId.asStateFlow()

    /** List of marker IDs selected for viewing (multi-marker navigation, §11). */
    private val _selectedMarkerIds = MutableStateFlow<List<String>>(emptyList())
    val selectedMarkerIds: StateFlow<List<String>> = _selectedMarkerIds.asStateFlow()

    /** Index into [selectedMarkerIds] for multi-marker Previous/Next navigation (§11). */
    private val _selectedMarkerIndex = MutableStateFlow(0)
    val selectedMarkerIndex: StateFlow<Int> = _selectedMarkerIndex.asStateFlow()

    /** Result of the last "where am I?" query. */
    private val _matchResult = MutableStateFlow<WhereAmIResult?>(null)
    val matchResult: StateFlow<WhereAmIResult?> = _matchResult.asStateFlow()

    /** Mutable form state for creation/editing (lives here so it survives drawer close). */
    private val _createForm = MutableStateFlow(CreateFormState())
    val createForm: StateFlow<CreateFormState> = _createForm.asStateFlow()

    /** Date formatter for default title/description (thread-safe via ThreadLocal). */
    private val dateFormat = ThreadLocal.withInitial {
        SimpleDateFormat("EEE, dd MMM yy", Locale.US)
    }
    private val dateTimeFormat = ThreadLocal.withInitial {
        SimpleDateFormat("EEE, dd MMM yy 'at' HH:mm", Locale.US)
    }
    private val shortDateFormat = ThreadLocal.withInitial {
        SimpleDateFormat("dd MMM yy", Locale.US)
    }

    // ── Coastline index (injected by the screen) ──────────────────────────
    /** Set by [MapScreen] when coastline is ready. Used by land-blocking engine. */
    var coastlineIndex: CoastlineSpatialIndex? = null

    // ── Init ──────────────────────────────────────────────────────────────

    private var isLoaded = false

    /**
     * Injects shared settings flow + updater from NavigationViewModel.
     * Must be called once by MapScreen before the ViewModel is used.
     */
    fun observeSettings(flow: StateFlow<AppSettings>, updater: ((AppSettings) -> AppSettings) -> Unit) {
        this.settingsFlow = flow
        this.updateSettings = updater
        _markerLayerState.value = flow.value.markerLayerState
        viewModelScope.launch {
            flow.collect { settings ->
                if (isLoaded) {
                    applyFilterSort(settings.markerListFilter, settings.markerListSort)
                }
                _markerLayerState.value = settings.markerLayerState
            }
        }
        // Map-referential stream: reactive to both the MAP filter and any allMarkers reload.
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(flow, _allMarkers) { settings, all ->
                markerSelectionPolicy.select(
                    items = all,
                    filter = settings.markerMapFilter,
                    cap = Int.MAX_VALUE,
                    focus = markerMapFocus,
                    todayMidnightMs = 0L
                )
            }.collect { _mapMarkers.value = it }
        }
    }

    init {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) { repo.loadAll() }
            _allMarkers.value = loaded
            // Initial sort: no filter applied until observeSettings wires up the flow
            _markers.value = sortMarkers(loaded, ykws.android.maro.data.model.ListSortState())
            isLoaded = true
        }
        // Keep _allMarkers fresh after service-side writes (AutoMarkerManager).
        viewModelScope.launch {
            UserMarkerRepository.markerChanges.collect {
                val all = withContext(Dispatchers.IO) { repo.loadAll() }
                val settings = settingsFlow?.value
                _allMarkers.value = all
                val filter = settings?.markerListFilter ?: ListFilter()
                val sort = settings?.markerListSort ?: ykws.android.maro.data.model.ListSortState()
                _markers.value = sortMarkers(all.filter { it.matchesFilter(filter) }, sort)
            }
        }
    }

    private fun sortMarkers(
        markers: List<UserMarker>,
        state: ykws.android.maro.data.model.ListSortState
    ): List<UserMarker> {
        return state.applySort(markers) { key ->
            when (key) {
                "origin" -> compareBy { it.origin }
                else -> null  // fallback to updatedAtEpochMs
            }
        }
    }

    // ── Visibility toggle ─────────────────────────────────────────────────

    /** Toggles the marker layer between HIDDEN and SHOW_ALL (binary). */
    fun toggleMarkerLayer() {
        val next = if (markerLayerState.value == MarkerLayerState.HIDDEN) MarkerLayerState.SHOW_ALL else MarkerLayerState.HIDDEN
        Log.d("MaroMapRefresh", "toggleMarkerLayer: ${markerLayerState.value} → $next")
        updateSettings?.invoke { it.copy(markerLayerState = next) }
    }

    /** Shows the user markers layer (sets to SHOW_ALL) if currently HIDDEN. */
    fun showLayer() {
        if (markerLayerState.value == MarkerLayerState.HIDDEN)
            updateSettings?.invoke { it.copy(markerLayerState = MarkerLayerState.SHOW_ALL) }
    }

    // ── Drawer control ────────────────────────────────────────────────────

    /**
     * The sole writer of [_drawerState], and so the owner of the Where-Am-I invariant.
     *
     * The rays and the run id that publishes them belong to the MatchResult dashboard alone: any
     * other destination — a marker card, the wizard, Hidden — retires both here, whichever route
     * asks for it. A tap that swaps the card in therefore leaves no stale ray on the map, and a run
     * still in flight cannot publish over the state that replaced it, its id having just been
     * retired — [whereAmI] reads that id at publication, not at capture (R14/R15).
     *
     * Keeping the field private and this function its only writer is what makes the rule hold for
     * every route, including one added later, rather than for the close funnel it used to sit in.
     */
    private fun setDrawerState(next: MarkerDrawerState) {
        if (next !is MarkerDrawerState.MatchResult) {
            _debugSegments.value = emptyList()
            currentOpenId = 0L
        }
        _drawerState.value = next
    }

    /** Opens drawer in viewing mode for a single marker (convenience). */
    fun openEditDrawer(markerId: String, selectedId: String? = null, source: DrawerSource = DrawerSource.WHERE_AM_I) {
        openEditDrawer(listOf(markerId), selectedId = selectedId, source = source)
    }

    /** Opens drawer in viewing mode for one or more markers (§11 multi-marker). */
    fun openEditDrawer(markerIds: List<String>, selectedId: String? = null, source: DrawerSource = DrawerSource.WHERE_AM_I) {
        if (markerIds.isEmpty()) return
        drawerSource = source
        _selectedMarkerIds.value = markerIds
        val index = if (selectedId != null) {
            markerIds.indexOf(selectedId).coerceAtLeast(0)
        } else {
            0
        }
        _selectedMarkerIndex.value = index
        val lookupId = markerIds[index]
        val marker = _allMarkers.value.find { it.id == lookupId } ?: return
        _selectedMarkerId.value = lookupId
        val pos = when (val g = marker.geometry) {
            is MarkerGeometry.Pin -> g.position
            is MarkerGeometry.Circle -> g.center
            is MarkerGeometry.Corridor -> g.p1
        }
        val colorIndex = marker.colorIndex ?: 0
        val type = when (marker.geometry) {
            is MarkerGeometry.Pin -> MarkerType.PIN
            is MarkerGeometry.Circle -> MarkerType.CIRCLE
            is MarkerGeometry.Corridor -> MarkerType.CORRIDOR
        }
        val radiusM = (marker.geometry as? MarkerGeometry.Circle)?.radiusM ?: 100.0
        val widthM = (marker.geometry as? MarkerGeometry.Corridor)?.widthM ?: 100.0
        val corridorP2 = (marker.geometry as? MarkerGeometry.Corridor)?.p2

        _createForm.value = CreateFormState(
            name = marker.name,
            type = type,
            position = pos,
            radiusM = radiusM,
            widthM = widthM,
            proximityOverrideM = marker.proximityOverrideM?.toString() ?: "",
            description = marker.description,
            colorIndex = colorIndex,
            icon = marker.icon,
            corridorP2 = corridorP2
        )
        setDrawerState(MarkerDrawerState.Viewing)
    }

    private fun isClampedSource() = drawerSource == DrawerSource.LIST || drawerSource == DrawerSource.MAP ||
        drawerSource == DrawerSource.INSPECT

    /** Navigate to the previous marker. Clamps when LIST/MAP/INSPECT source, wraps when WHERE_AM_I. */
    fun viewPreviousMarker() {
        val ids = _selectedMarkerIds.value
        if (ids.size <= 1) return
        // Inspect: the cursor above the drawers owns the merged walk and never moves the camera, so
        // this ViewModel's own marker walk stands down entirely.
        if (drawerSource == DrawerSource.INSPECT) return
        val current = _selectedMarkerIndex.value
        val newIndex = if (isClampedSource()) {
            (current - 1).coerceAtLeast(0)
        } else {
            if (current > 0) current - 1 else ids.lastIndex  // wrap (WHERE_AM_I)
        }
        if (newIndex == current) return  // clamped at edge
        _selectedMarkerIndex.value = newIndex
        _selectedMarkerId.value = ids[newIndex]
        if (isClampedSource()) {
            emitMapCenterForMarker(ids[newIndex])
        }
    }

    /** Navigate to the next marker. Clamps when LIST/MAP/INSPECT source, wraps when WHERE_AM_I. */
    fun viewNextMarker() {
        val ids = _selectedMarkerIds.value
        if (ids.size <= 1) return
        // Inspect: as above — the cursor walks the merged ladder.
        if (drawerSource == DrawerSource.INSPECT) return
        val current = _selectedMarkerIndex.value
        val newIndex = if (isClampedSource()) {
            (current + 1).coerceAtMost(ids.lastIndex)
        } else {
            if (current < ids.lastIndex) current + 1 else 0  // wrap (WHERE_AM_I)
        }
        if (newIndex == current) return  // clamped at edge
        _selectedMarkerIndex.value = newIndex
        _selectedMarkerId.value = ids[newIndex]
        if (isClampedSource()) {
            emitMapCenterForMarker(ids[newIndex])
        }
    }

    /** Emit a map-center request for the given marker ID (LIST/MAP-mode prev/next). */
    private fun emitMapCenterForMarker(markerId: String) {
        val marker = _allMarkers.value.find { it.id == markerId } ?: return
        val pos = when (val g = marker.geometry) {
            is MarkerGeometry.Pin -> g.position
            is MarkerGeometry.Circle -> g.center
            is MarkerGeometry.Corridor -> g.p1
        }
        _mapCenterRequest.value = pos
    }

    /** Re-apply filter + sort with current settings. */
    fun refreshSort(sortState: ykws.android.maro.data.model.ListSortState? = null, filter: ListFilter? = null) {
        val settings = settingsFlow?.value
        val effective = sortState ?: settings?.markerListSort ?: ykws.android.maro.data.model.ListSortState()
        val effectiveFilter = filter ?: settings?.markerListFilter ?: ListFilter()
        applyFilterSort(effectiveFilter, effective)
    }

    /**
     * R2 entry point for the map referential: the map world was rewritten, so re-test the open dashboard.
     * The list-referential write is served by [applyFilterSort]; a map write need not pass through it, so
     * the map controls report here.
     */
    fun onMapReferentialChanged() {
        val listFilter = settingsFlow?.value?.markerListFilter ?: ListFilter()
        applyScopeGuard(_allMarkers.value.filter { it.matchesFilter(listFilter) })
    }

    /** Filter + sort into [_markers]; the scope guard then re-tests the open dashboard (R2). */
    private fun applyFilterSort(filter: ListFilter, sort: ykws.android.maro.data.model.ListSortState) {
        val filtered = _allMarkers.value.filter { it.matchesFilter(filter) }
        _markers.value = sortMarkers(filtered, sort)
        applyScopeGuard(filtered)
    }

    /**
     * R2: the world the open walk reads decides whether the dashboard survives — the list referential for
     * a list-opened panel, the map referential for a map-opened one ([scopeClosed]), so a write to the
     * other world is display-only and leaves it open.
     *
     * @param listWorld the markers the list referential holds after the change.
     */
    private fun applyScopeGuard(listWorld: List<UserMarker>) {
        if (_drawerState.value !is MarkerDrawerState.Viewing) return
        val selected = _selectedMarkerId.value ?: return
        val mapFilter = settingsFlow?.value?.markerMapFilter ?: ListFilter()
        val listWorldLost = listWorld.none { it.id == selected }
        val mapWorldLost = _allMarkers.value.none { it.id == selected && it.matchesFilter(mapFilter) }
        if (scopeClosed(drawerSource, inListWorld = listWorldLost, inMapWorld = mapWorldLost)) {
            closeDrawer()
        }
    }

    /**
     * Closes the drawer, taking the Where-Am-I rays with it and retiring the open the runs publish to.
     *
     * The in-flight run is deliberately left to finish: it still owes its MANUAL note to the
     * recording (§10), and the retired id is what stops it from publishing. Nothing cancels it, so
     * rapid taps no longer cancel each other either — the id decides who publishes.
     *
     * The rays and the id are [setDrawerState]'s to clear, so this route and every other one carry
     * the same rule; what is left here is only what a close means on its own.
     */
    fun closeDrawer() {
        setDrawerState(MarkerDrawerState.Hidden)
        _wizardStep.value = null
        editingMarkerId = null
        _selectedMarkerId.value = null
        _selectedMarkerIds.value = emptyList()
        _selectedMarkerIndex.value = 0
    }

    // ── Wizard state machine ──────────────────────────────────────────────

    /** Returns the ordered list of steps for the given marker type. */
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
     * Icon matching the geometry type: 📍 🎯 🛤️.
     * Delegates to the single source of truth [MarkerGeometry.iconFor] via a
     * representative geometry for the wizard [MarkerType] (used pre-geometry in
     * default-name generation).
     */
    private fun typeIcon(type: MarkerType): String = MarkerGeometry.iconFor(
        when (type) {
            MarkerType.PIN -> MarkerGeometry.Pin(LatLng(0.0, 0.0))
            MarkerType.CIRCLE -> MarkerGeometry.Circle(LatLng(0.0, 0.0), 1.0)
            MarkerType.CORRIDOR -> MarkerGeometry.Corridor(LatLng(0.0, 0.0), LatLng(0.0, 0.0), 1.0)
        }
    )

    /** Human-readable name for a color index (0-15). */
    private fun colorName(index: Int): String = when (index) {
        0 -> "Red"
        1 -> "Blue"
        2 -> "Green"
        3 -> "Orange"
        4 -> "Purple"
        5 -> "Cyan"
        6 -> "Deep Orange"
        7 -> "Indigo"
        8 -> "Light Green"
        9 -> "Yellow"
        10 -> "Pink"
        11 -> "Brown"
        12 -> "Teal"
        13 -> "Deep Purple"
        14 -> "Lime"
        15 -> "Blue Grey"
        else -> "Grey"
    }

    /** Begin wizard in creation mode with optional [initialType] and [initialPos]. */
    fun startWizard(initialType: MarkerType = MarkerType.PIN, initialPos: LatLng? = null) {
        showLayer()
        editingMarkerId = null
        val now = Date()
        val colorIdx = MarkerColors.randomIndex()
        val defaultName = "(${shortDateFormat.get()!!.format(now)}) ${typeIcon(initialType)} ${colorName(colorIdx)}"
        _createForm.value = CreateFormState(
            type = initialType,
            position = initialPos,
            name = defaultName,
            description = dateTimeFormat.get()!!.format(now),
            colorIndex = colorIdx
        )
        wizardForward = true
        _wizardStep.value = WizardStep.TypeSelect
        setDrawerState(MarkerDrawerState.Creating)
    }

    /** Begin wizard in edit mode, pre-filled with the marker identified by [markerId]. */
    fun startWizard(markerId: String) {
        val marker = _markers.value.find { it.id == markerId } ?: return
        editingMarkerId = markerId
        // Populate form from marker if not already populated by Viewing → Edit flow
        if (_createForm.value.position == null || _createForm.value.name != marker.name) {
            val pos = when (val g = marker.geometry) {
                is MarkerGeometry.Pin -> g.position
                is MarkerGeometry.Circle -> g.center
                is MarkerGeometry.Corridor -> g.p1
            }
            val type = when (marker.geometry) {
                is MarkerGeometry.Pin -> MarkerType.PIN
                is MarkerGeometry.Circle -> MarkerType.CIRCLE
                is MarkerGeometry.Corridor -> MarkerType.CORRIDOR
            }
            val radiusM = (marker.geometry as? MarkerGeometry.Circle)?.radiusM ?: 100.0
            val widthM = (marker.geometry as? MarkerGeometry.Corridor)?.widthM ?: 100.0
            val corridorP2 = (marker.geometry as? MarkerGeometry.Corridor)?.p2
            _createForm.value = CreateFormState(
                name = marker.name,
                type = type,
                position = pos,
                radiusM = radiusM,
                widthM = widthM,
                proximityOverrideM = marker.proximityOverrideM?.toString() ?: "",
                description = marker.description,
                colorIndex = marker.colorIndex ?: 0,
                icon = marker.icon,
                corridorP2 = corridorP2
            )
        }
        wizardForward = true
        val seq = stepSequenceFor(_createForm.value.type)
        // Skip TypeSelect — edit already knows the type; jump to Position step
        _wizardStep.value = seq[1]
        // Emit one-shot map-centre request so MapScreen animates to the marker
        _mapCenterRequest.value = _createForm.value.position
        setDrawerState(MarkerDrawerState.Editing(markerId))
    }

    /** Advance to the next wizard step. */
    fun wizardNext() {
        val current = _wizardStep.value ?: return
        val seq = stepSequenceFor(_createForm.value.type)
        val idx = seq.indexOf(current)
        if (idx < 0 || idx >= seq.lastIndex) return
        wizardForward = true
        _wizardStep.value = seq[idx + 1]
        recenterMapOnStep(seq[idx + 1])
    }

    /** Go back to the previous wizard step. */
    fun wizardPrevious() {
        val current = _wizardStep.value ?: return
        val seq = stepSequenceFor(_createForm.value.type)
        val idx = seq.indexOf(current)
        if (idx <= 0) return
        wizardForward = false
        _wizardStep.value = seq[idx - 1]
        recenterMapOnStep(seq[idx - 1])
    }

    /** During edit mode, recenter the map when entering a position step. */
    private fun recenterMapOnStep(step: WizardStep) {
        if (editingMarkerId == null) return  // only during edit
        val form = _createForm.value
        // Suspend form tracking so animateTo intermediate mapCenter values
        // don't overwrite the position we just restored from the marker.
        _suspendTracking.value = true
        viewModelScope.launch {
            delay(600L)
            _suspendTracking.value = false
        }
        when (step) {
            is WizardStep.Position -> _mapCenterRequest.value = form.position  // P1
            is WizardStep.PositionP2 -> _mapCenterRequest.value = form.corridorP2  // P2
            else -> { /* no recenter for non-position steps */ }
        }
    }

    /** Cancel wizard — discard form, close drawer, reset form state. */
    fun wizardCancel() {
        _wizardStep.value = null
        editingMarkerId = null
        _selectedMarkerId.value = null
        _createForm.value = CreateFormState()
        setDrawerState(MarkerDrawerState.Hidden)
    }

    /** Finish early — save immediately with defaults for remaining steps. */
    fun wizardFinish() {
        val form = _createForm.value
        // Corridor requires P2; block finish if P2 not set
        if (form.type == MarkerType.CORRIDOR && form.corridorP2 == null) return

        if (editingMarkerId != null) {
            updateMarker(editingMarkerId!!)
        } else {
            saveMarker()
        }
    }

    /** Whether Finish is allowed at the current step (corridor needs P2). */
    fun canFinish(): Boolean {
        val form = _createForm.value
        return form.type != MarkerType.CORRIDOR || form.corridorP2 != null
    }

    // ── Form mutations ────────────────────────────────────────────────────

    fun updateForm(transform: (CreateFormState) -> CreateFormState) {
        _createForm.value = transform(_createForm.value)
    }

    // ── CRUD ──────────────────────────────────────────────────────────────

    /** Save a new marker from the current form state. */
    fun saveMarker() {
        val form = _createForm.value
        val pos = form.position ?: return

        val geometry = when (form.type) {
            MarkerType.PIN -> MarkerGeometry.Pin(pos)
            MarkerType.CIRCLE -> MarkerGeometry.Circle(pos, form.radiusM.coerceAtLeast(1.0))
            MarkerType.CORRIDOR -> {
                val p2 = form.corridorP2 ?: return // corridor needs p2
                MarkerGeometry.Corridor(pos, p2, form.widthM.coerceAtLeast(1.0))
            }
        }

        val proximityOverride = form.proximityOverrideM.toDoubleOrNull()?.coerceAtLeast(0.0)
            ?: when (form.type) {
                MarkerType.PIN -> AppConfig.markerProximityPinM
                MarkerType.CIRCLE -> form.radiusM * AppConfig.markerProximityZoneMultiplier
                MarkerType.CORRIDOR -> form.widthM * AppConfig.markerProximityZoneMultiplier
            }

        val marker = UserMarker(
            id = UUID.randomUUID().toString(),
            name = form.name.ifBlank { "Marker" },
            geometry = geometry,
            description = form.description,
            proximityOverrideM = proximityOverride,
            confirmed = true,
            colorIndex = form.colorIndex,
            icon = form.icon,
            createdAtEpochMs = System.currentTimeMillis()
        )

        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.add(marker) }
            val all = withContext(Dispatchers.IO) { repo.loadAll() }
            val settings = settingsFlow?.value
            _allMarkers.value = all
            val filter = settings?.markerListFilter ?: ListFilter()
            val sort = settings?.markerListSort ?: ykws.android.maro.data.model.ListSortState()
            _markers.value = sortMarkers(all.filter { it.matchesFilter(filter) }, sort)
            setDrawerState(MarkerDrawerState.Hidden)
            _lastSavedMarkerId.value = marker.id
            _wizardStep.value = null
            editingMarkerId = null
            _selectedMarkerId.value = null
        }
    }

    /** Update an existing marker from the current form state. */
    fun updateMarker(markerId: String) {
        val existing = _markers.value.find { it.id == markerId } ?: return
        val form = _createForm.value
        val pos = form.position ?: return

        val geometry = when (form.type) {
            MarkerType.PIN -> MarkerGeometry.Pin(pos)
            MarkerType.CIRCLE -> MarkerGeometry.Circle(pos, form.radiusM.coerceAtLeast(1.0))
            MarkerType.CORRIDOR -> {
                val p2 = form.corridorP2 ?: return
                MarkerGeometry.Corridor(pos, p2, form.widthM.coerceAtLeast(1.0))
            }
        }

        val proximityOverride = form.proximityOverrideM.toDoubleOrNull()?.coerceAtLeast(0.0)
            ?: when (form.type) {
                MarkerType.PIN -> AppConfig.markerProximityPinM
                MarkerType.CIRCLE -> form.radiusM * AppConfig.markerProximityZoneMultiplier
                MarkerType.CORRIDOR -> form.widthM * AppConfig.markerProximityZoneMultiplier
            }

        val updated = existing.copy(
            name = form.name.ifBlank { existing.name },
            geometry = geometry,
            description = form.description,
            proximityOverrideM = proximityOverride,
            icon = form.icon
        )

        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.update(updated) }
            val all = withContext(Dispatchers.IO) { repo.loadAll() }
            val settings = settingsFlow?.value
            _allMarkers.value = all
            val filter = settings?.markerListFilter ?: ListFilter()
            val sort = settings?.markerListSort ?: ykws.android.maro.data.model.ListSortState()
            _markers.value = sortMarkers(all.filter { it.matchesFilter(filter) }, sort)
            setDrawerState(MarkerDrawerState.Hidden)
            _wizardStep.value = null
            editingMarkerId = null
            _selectedMarkerId.value = null
        }
    }

    /** Inline text edit (name/description) — persists like [updateMarker] without touching geometry. */
    fun updateMarkerText(id: String, name: String? = null, description: String? = null) {
        val existing = _markers.value.find { it.id == id } ?: return
        val updated = existing.copy(
            name = name?.takeIf { it.isNotBlank() } ?: existing.name,
            description = description ?: existing.description
        )
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.update(updated) }
            val all = withContext(Dispatchers.IO) { repo.loadAll() }
            val settings = settingsFlow?.value
            _allMarkers.value = all
            val filter = settings?.markerListFilter ?: ListFilter()
            val sort = settings?.markerListSort ?: ykws.android.maro.data.model.ListSortState()
            _markers.value = sortMarkers(all.filter { it.matchesFilter(filter) }, sort)
        }
    }

    /** Delete a marker by ID. [closeDrawer] is false when the drawer is already showing another marker. */
    fun deleteMarker(markerId: String, closeDrawer: Boolean = true) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.delete(markerId) }
            val all = withContext(Dispatchers.IO) { repo.loadAll() }
            val settings = settingsFlow?.value
            _allMarkers.value = all
            val filter = settings?.markerListFilter ?: ListFilter()
            val sort = settings?.markerListSort ?: ykws.android.maro.data.model.ListSortState()
            _markers.value = sortMarkers(all.filter { it.matchesFilter(filter) }, sort)
            if (closeDrawer) {
                setDrawerState(MarkerDrawerState.Hidden)
            }
        }
    }

    // ── Auto-marker (idle 🕐 pin) ─────────────────────────────────────────

    /**
     * Create a temporary 🕐 auto-marker pin at the idle position.
     * Returns the marker ID so MapScreen can pass it to TrackRecorder,
     * or an empty string if a nearby auto-marker already exists.
     *
     * Title = date only (e.g. "2026-07-01").
     * Description = timing placeholder (e.g. "@ 14:15 -> ...").
     */
    fun addTempAutoMarker(lat: Double, lon: Double, startTimeMs: Long): String {
        val dedupRadiusM = settingsFlow?.value?.boatMarkerAutoMarkerDedupRadiusM
            ?: AppConfig.boatMarkerAutoMarkerDedupRadiusM
        val newPos = LatLng(lat, lon)

        // ── Proximity dedup: scan existing IDLE_AUTO markers within dedupRadiusM ──
        val existingAutoMarkers = _allMarkers.value.filter { it.origin == ykws.android.maro.data.model.markers.MarkerOrigin.IDLE_AUTO }
        val nearest = existingAutoMarkers.minByOrNull { ykws.android.maro.spatial.SpatialOperations.haversine(newPos, it.centerPoint) }
        if (nearest != null) {
            val dist = ykws.android.maro.spatial.SpatialOperations.haversine(newPos, nearest.centerPoint)
            if (dist <= dedupRadiusM) {
                if (!nearest.confirmed) {
                    // Reuse the existing temp marker — update its position
                    val updated = nearest.copy(geometry = MarkerGeometry.Pin(newPos))
                    viewModelScope.launch {
                        withContext(Dispatchers.IO) { repo.update(updated) }
                        val all = withContext(Dispatchers.IO) { repo.loadAll() }
                        val settings = settingsFlow?.value
                        _allMarkers.value = all
                        val filter = settings?.markerListFilter ?: ListFilter()
                        val sort = settings?.markerListSort ?: ykws.android.maro.data.model.ListSortState()
                        _markers.value = sortMarkers(all.filter { it.matchesFilter(filter) }, sort)
                    }
                    return nearest.id
                } else {
                    // Already have a confirmed auto-marker here — skip
                    return ""
                }
            }
        }

        // No nearby auto-marker → create normally
        val now = Date()
        val title = dateFormat.get()!!.format(now)
        val startTime = SimpleDateFormat("HH:mm", Locale.US).format(Date(startTimeMs))
        val desc = "@ $startTime -> ..."
        val marker = UserMarker(
            id = UUID.randomUUID().toString(),
            name = title,
            description = desc,
            geometry = MarkerGeometry.Pin(newPos),
            proximityOverrideM = AppConfig.boatMarkerAutoMarkerProximityM,
            confirmed = false,
            icon = "\uD83D\uDD50",  // 🕐
            createdAtEpochMs = System.currentTimeMillis(),
            origin = ykws.android.maro.data.model.markers.MarkerOrigin.IDLE_AUTO,
            keepable = false
        )
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.add(marker) }
            val all = withContext(Dispatchers.IO) { repo.loadAll() }
            val settings = settingsFlow?.value
            _allMarkers.value = all
            val filter = settings?.markerListFilter ?: ListFilter()
            val sort = settings?.markerListSort ?: ykws.android.maro.data.model.ListSortState()
            _markers.value = sortMarkers(all.filter { it.matchesFilter(filter) }, sort)
        }
        return marker.id
    }

    /**
     * Confirm a temporary 🕐 auto-marker — sets confirmed=true, keepable=true,
     * and updates the name and description with final values.
     */
    fun confirmAutoMarker(id: String, name: String, description: String) {
        val marker = _markers.value.find { it.id == id } ?: return
        val updated = marker.copy(confirmed = true, keepable = true, name = name, description = description)
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.update(updated) }
            val all = withContext(Dispatchers.IO) { repo.loadAll() }
            val settings = settingsFlow?.value
            _allMarkers.value = all
            val filter = settings?.markerListFilter ?: ListFilter()
            val sort = settings?.markerListSort ?: ykws.android.maro.data.model.ListSortState()
            _markers.value = sortMarkers(all.filter { it.matchesFilter(filter) }, sort)
        }
    }

    // ── Soft-delete for management page undo ──────────────────────────────

    /** Set of marker IDs pending deletion (not yet persisted). */
    val pendingDeletes: MutableSet<String> = mutableSetOf()

    /** Soft-delete: mark for deletion but keep in list so the inline snackbar stays composed. */
    fun softDeleteMarker(markerId: String) {
        pendingDeletes.add(markerId)
        // Keep marker in _markers — the SwipeToDeleteMarkerCard composable
        // transitions to SNACKBAR state and must remain in the LazyColumn.
    }

    /** Undo a soft-delete: remove from pending set. Marker was never removed from list. */
    fun undoDeleteMarker(markerId: String) {
        pendingDeletes.remove(markerId)
    }

    /** Commit all pending soft-deletes to persistent storage. */
    fun commitPendingDeletes() {
        if (pendingDeletes.isEmpty()) return
        val ids = pendingDeletes.toSet()
        pendingDeletes.clear()
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                ids.forEach { id -> repo.delete(id) }
            }
            val all = withContext(Dispatchers.IO) { repo.loadAll() }
            val settings = settingsFlow?.value
            _allMarkers.value = all
            val filter = settings?.markerListFilter ?: ListFilter()
            val sort = settings?.markerListSort ?: ykws.android.maro.data.model.ListSortState()
            _markers.value = sortMarkers(all.filter { it.matchesFilter(filter) }, sort)
        }
    }

    // ── Post-save undo (Snackbar) ─────────────────────────────────────────

    // ── Icon management ───────────────────────────────────────────────────

    /** Set the icon on a marker (null = remove icon). Icon is purely decorative — no pin semantics. */
    fun setMarkerIcon(markerId: String, icon: String?) {
        val marker = _allMarkers.value.find { it.id == markerId } ?: return
        val updated = marker.copy(icon = icon)
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.update(updated) }
            val all = withContext(Dispatchers.IO) { repo.loadAll() }
            val settings = settingsFlow?.value
            _allMarkers.value = all
            val filter = settings?.markerListFilter ?: ListFilter()
            val sort = settings?.markerListSort ?: ykws.android.maro.data.model.ListSortState()
            _markers.value = sortMarkers(all.filter { it.matchesFilter(filter) }, sort)
        }
    }

    /** Set the pinned flag on a marker (mirrors [TrackViewModel.setPinned]). */
    fun setMarkerPinned(markerId: String, pinned: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.setPinned(markerId, pinned) }
            val all = withContext(Dispatchers.IO) { repo.loadAll() }
            val settings = settingsFlow?.value
            _allMarkers.value = all
            val filter = settings?.markerListFilter ?: ListFilter()
            val sort = settings?.markerListSort ?: ykws.android.maro.data.model.ListSortState()
            _markers.value = sortMarkers(all.filter { it.matchesFilter(filter) }, sort)
        }
    }

    /** Dismiss the last-saved-marker Snackbar without undoing. */
    fun dismissLastSaved() {
        _lastSavedMarkerId.value = null
    }

    /** Undo the last created marker (soft-delete for Snackbar undo). */
    fun undoCreateMarker() {
        val id = _lastSavedMarkerId.value ?: return
        _lastSavedMarkerId.value = null
        softDeleteMarker(id)
        // Commit immediately (no management-page undo chain)
        commitPendingDeletes()
    }

    // ── "Where am I?" on-demand match ─────────────────────────────────────

    /** A resolution's matches plus the ray segments its own debugger collected. */
    private data class Resolution(val result: WhereAmIResult, val segments: List<DebugSegment>)

    /**
     * Monotonic id of the run that opened the dashboard on screen; 0 while none is open.
     * [setDrawerState] retires it on every move off MatchResult, which is what dates a run as stale.
     */
    private var openCounter = 0L
    private var currentOpenId = 0L

    /**
     * The one resolution body, at [boatPos]: the coastline-index guard, the marker snapshot, the
     * per-call debugger choice and the resolver call, returning the result with its segments.
     *
     * The two empties stay apart (CH5): a missing coastline index returns null and so opens no
     * dashboard, while an empty marker list resolves to an empty result its caller still publishes.
     * [collectRays] is the per-run debugger choice — a fresh visual collector only for a UI run whose
     * rays are wanted, the no-op everywhere else, where the capture would only be discarded (CH4).
     */
    private fun resolveAt(boatPos: LatLng, collectRays: Boolean): Resolution? {
        val index = coastlineIndex ?: return null
        val debugger = if (collectRays) VisualWhereAmIDebugger() else NoOpWhereAmIDebugger
        // The empty marker list is the resolver's own case — it answers an empty result, which the
        // caller still publishes — while the missing index above returns null and opens nothing.
        val result = MarkerMatcher.resolveAllMarkers(boatPos, _allMarkers.value, index, debugger)
        return Resolution(result, debugger.getSegments())
    }

    /**
     * Synchronous whereAmI — used by the idle threshold callback
     * which runs inside a coroutine on the recorder's scope, and by the service bridge.
     * Returns markers snapshotted at [boatPos].
     */
    fun whereAmISync(boatPos: LatLng): WhereAmIResult =
        resolveAt(boatPos, collectRays = false)?.result ?: WhereAmIResult(emptyList())

    /**
     * Runs the resolution at [boatPos] and opens the Where-Am-I dashboard on its result.
     *
     * No run cancels another and a close cancels nothing: either would drop the MANUAL note the run
     * owes the recording (§10). The id stamped here is instead compared at publication — read there
     * and not captured earlier (R14) — so only the newest run, and only while its own open is still
     * the current one, publishes; [setDrawerState] retires that id on any move off MatchResult, which
     * is what keeps a late older run from re-opening a closed dashboard or painting over the card,
     * the wizard or a newer open (R15).
     *
     * Every run hands its result to [onResolved] ahead of that guard, so the note reaches the
     * recording whatever the drawer does: a tap followed by an instant close still records where the
     * boat was, and two quick taps record two notes exactly as the two calls do today.
     */
    fun whereAmI(boatPos: LatLng, onResolved: (WhereAmIResult) -> Unit = {}) {
        val runId = ++openCounter
        currentOpenId = runId
        val collectRays = settingsFlow?.value?.markerDebugRays == true
        viewModelScope.launch {
            val resolution = withContext(Dispatchers.Default) { resolveAt(boatPos, collectRays) }
                ?: return@launch
            onResolved(resolution.result)
            if (currentOpenId != runId) return@launch
            _debugSegments.value = resolution.segments
            _matchResult.value = resolution.result
            setDrawerState(MarkerDrawerState.MatchResult)
        }
    }

    // ── Factory ───────────────────────────────────────────────────────────

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(
                modelClass: Class<T>,
                extras: androidx.lifecycle.viewmodel.CreationExtras
            ): T {
                val application = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    ?: error("Cannot create MarkersViewModel without APPLICATION_KEY")
                return MarkersViewModel(application as Application) as T
            }
        }
    }
}

/** Top-level extension: convert WhereAmIMatch → MarkerSnapshot (used by idle + manual paths). */
fun WhereAmIMatch.toMarkerSnapshot(): ykws.android.maro.data.track.MarkerSnapshot {
    val m = when (this) {
        is WhereAmIMatch.ZoneMatch -> marker
        is WhereAmIMatch.LineOfSightMatch -> marker
    }
    val (centerLat, centerLon) = when (val g = m.geometry) {
        is MarkerGeometry.Pin -> g.position.latitude to g.position.longitude
        is MarkerGeometry.Circle -> g.center.latitude to g.center.longitude
        is MarkerGeometry.Corridor -> (g.p1.latitude + g.p2.latitude) / 2.0 to (g.p1.longitude + g.p2.longitude) / 2.0
    }
    val zoneSize = when (m.geometry) {
        is MarkerGeometry.Circle -> m.geometry.radiusM
        is MarkerGeometry.Corridor -> m.geometry.widthM
        else -> 0.0
    }
    val (distNm, bearingDeg) = when (this) {
        is WhereAmIMatch.ZoneMatch -> distanceToCenterM / 1852.0 to bearingDeg
        is WhereAmIMatch.LineOfSightMatch -> seaDistanceM / 1852.0 to bearingDeg
    }
    return ykws.android.maro.data.track.MarkerSnapshot(
        markerId = m.id,
        name = m.name,
        geometryType = m.geometry::class.simpleName ?: "Unknown",
        lat = centerLat,
        lon = centerLon,
        distanceNm = distNm,
        bearingDeg = bearingDeg,
        zoneSizeM = zoneSize,
        icon = m.icon
    )
}
