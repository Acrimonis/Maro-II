package ykws.android.maro.ui.map

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.markers.UserMarkerRepository
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.ListFilter
import ykws.android.maro.data.model.markers.MarkerGeometry
import ykws.android.maro.data.model.markers.UserMarker
import ykws.android.maro.data.model.matchesFilter
import ykws.android.maro.data.settings.AppSettings
import ykws.android.maro.data.settings.SettingsManager
import ykws.android.maro.spatial.CoastlineSpatialIndex
import ykws.android.maro.spatial.DebugSegment
import ykws.android.maro.spatial.MarkerMatcher
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

/** Tri-state for the fan layer marker toggle. */
enum class MarkerLayerState { HIDDEN, SHOW_ALL }

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

    private val settingsManager: SettingsManager =
        SettingsManager(application, AppConfig.zoneAutoRevealDistanceM,
            AppConfig.zoneAutoRevealTimeS, AppConfig.overlayLowDepthMinOpacity)

    // ── StateFlows ────────────────────────────────────────────────────────

    /** WhereAmI debug segments for visual overlay (green = clear, red = blocked). */
    private val _debugSegments = MutableStateFlow<List<DebugSegment>>(emptyList())
    val debugSegments: StateFlow<List<DebugSegment>> = _debugSegments.asStateFlow()

    /** Unfiltered source of truth — reloaded from repository. */
    private val _allMarkers = MutableStateFlow<List<UserMarker>>(emptyList())

    /** Loaded user markers (reactive, filtered + sorted). */
    private val _markers = MutableStateFlow<List<UserMarker>>(emptyList())
    val markers: StateFlow<List<UserMarker>> = _markers.asStateFlow()

    /** Tri-state layer visibility (FanLayout toggle). */
    val markerLayerState: StateFlow<MarkerLayerState> =
        settingsManager.settings.let { flow ->
            val initial = flow.value.markerLayerState
            MutableStateFlow(initial).also { sf ->
                viewModelScope.launch { flow.collect { sf.value = it.markerLayerState } }
            }.asStateFlow()
        }

    /** Derived: true when layer is not hidden. */
    val userMarkersVisible: StateFlow<Boolean> =
        markerLayerState.let { flow ->
            MutableStateFlow(flow.value != MarkerLayerState.HIDDEN).also { sf ->
                viewModelScope.launch { flow.collect { sf.value = it != MarkerLayerState.HIDDEN } }
            }.asStateFlow()
        }

    /** Current drawer mode. */
    private val _drawerState = MutableStateFlow<MarkerDrawerState>(MarkerDrawerState.Hidden)
    val drawerState: StateFlow<MarkerDrawerState> = _drawerState.asStateFlow()

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

    init {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) { repo.loadAll() }
            val settings = settingsManager.settings.value
            _allMarkers.value = loaded
            val filtered = loaded.filter { it.matchesFilter(settings.markerListFilter) }
            _markers.value = sortMarkers(filtered, settings.markerListSort)
        }
        viewModelScope.launch {
            settingsManager.settings.collect { settings ->
                val filtered = _allMarkers.value.filter { it.matchesFilter(settings.markerListFilter) }
                _markers.value = sortMarkers(filtered, settings.markerListSort)
            }
        }
    }

    private fun sortMarkers(
        markers: List<UserMarker>,
        state: ykws.android.maro.data.model.ListSortState
    ): List<UserMarker> {
        return state.applySort(markers) { key ->
            when (key) {
                "origin" -> compareBy { it.origin.name }
                else -> null  // fallback to updatedAtEpochMs
            }
        }
    }

    // ── Visibility toggle ─────────────────────────────────────────────────

    /** Toggles the marker layer between HIDDEN and SHOW_ALL (binary). */
    fun toggleMarkerLayer() {
        val next = if (markerLayerState.value == MarkerLayerState.HIDDEN) MarkerLayerState.SHOW_ALL else MarkerLayerState.HIDDEN
        Log.d("MaroMapRefresh", "toggleMarkerLayer: ${markerLayerState.value} → $next")
        settingsManager.update { it.copy(markerLayerState = next) }
    }

    /** Shows the user markers layer (sets to SHOW_ALL) if currently HIDDEN. */
    fun showLayer() {
        if (markerLayerState.value == MarkerLayerState.HIDDEN)
            settingsManager.update { it.copy(markerLayerState = MarkerLayerState.SHOW_ALL) }
    }

    // ── Drawer control ────────────────────────────────────────────────────

    /** Opens drawer in viewing mode for a single marker (convenience). */
    fun openEditDrawer(markerId: String) {
        openEditDrawer(listOf(markerId))
    }

    /** Opens drawer in viewing mode for one or more markers (§11 multi-marker). */
    fun openEditDrawer(markerIds: List<String>) {
        if (markerIds.isEmpty()) return
        _selectedMarkerIds.value = markerIds
        _selectedMarkerIndex.value = 0
        val firstId = markerIds.first()
        val marker = _markers.value.find { it.id == firstId } ?: return
        _selectedMarkerId.value = firstId
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
        _drawerState.value = MarkerDrawerState.Viewing
    }

    /** Navigate to the previous marker in the multi-marker selection (§11). */
    fun viewPreviousMarker() {
        val ids = _selectedMarkerIds.value
        if (ids.size <= 1) return
        val current = _selectedMarkerIndex.value
        val newIndex = if (current > 0) current - 1 else ids.lastIndex
        _selectedMarkerIndex.value = newIndex
        _selectedMarkerId.value = ids[newIndex]
    }

    /** Navigate to the next marker in the multi-marker selection (§11). */
    fun viewNextMarker() {
        val ids = _selectedMarkerIds.value
        if (ids.size <= 1) return
        val current = _selectedMarkerIndex.value
        val newIndex = if (current < ids.lastIndex) current + 1 else 0
        _selectedMarkerIndex.value = newIndex
        _selectedMarkerId.value = ids[newIndex]
    }

    /** Re-apply filter + sort with current settings. */
    fun refreshSort(sortState: ykws.android.maro.data.model.ListSortState? = null) {
        val settings = settingsManager.settings.value
        val effective = sortState ?: settings.markerListSort
        val filtered = _allMarkers.value.filter { it.matchesFilter(settings.markerListFilter) }
        _markers.value = sortMarkers(filtered, effective)
    }

    /** Closes the drawer. */
    fun closeDrawer() {
        _drawerState.value = MarkerDrawerState.Hidden
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

    /** Icon matching the geometry type: 📍 ⭕ 🔴 */
    private fun typeIcon(type: MarkerType): String = when (type) {
        MarkerType.PIN -> "\uD83D\uDCCD"     // 📍
        MarkerType.CIRCLE -> "\u2B55"         // ⭕
        MarkerType.CORRIDOR -> "\uD83D\uDD34" // 🔴
    }

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
        _drawerState.value = MarkerDrawerState.Creating
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
        _drawerState.value = MarkerDrawerState.Editing(markerId)
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
        _drawerState.value = MarkerDrawerState.Hidden
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
            pinned = form.icon != null,
            createdAtEpochMs = System.currentTimeMillis()
        )

        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.add(marker) }
            val all = withContext(Dispatchers.IO) { repo.loadAll() }
            val settings = settingsManager.settings.value
            _allMarkers.value = all
            _markers.value = sortMarkers(all.filter { it.matchesFilter(settings.markerListFilter) }, settings.markerListSort)
            _drawerState.value = MarkerDrawerState.Hidden
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
            icon = form.icon,
            pinned = form.icon != null
        )

        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.update(updated) }
            val all = withContext(Dispatchers.IO) { repo.loadAll() }
            val settings = settingsManager.settings.value
            _allMarkers.value = all
            _markers.value = sortMarkers(all.filter { it.matchesFilter(settings.markerListFilter) }, settings.markerListSort)
            _drawerState.value = MarkerDrawerState.Hidden
            _wizardStep.value = null
            editingMarkerId = null
            _selectedMarkerId.value = null
        }
    }

    /** Delete a marker by ID. */
    fun deleteMarker(markerId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.delete(markerId) }
            val all = withContext(Dispatchers.IO) { repo.loadAll() }
            val settings = settingsManager.settings.value
            _allMarkers.value = all
            _markers.value = sortMarkers(all.filter { it.matchesFilter(settings.markerListFilter) }, settings.markerListSort)
            _drawerState.value = MarkerDrawerState.Hidden
        }
    }

    // ── Auto-marker (idle 🕐 pin) ─────────────────────────────────────────

    /**
     * Create a temporary 🕐 auto-marker pin at the idle position.
     * Returns the marker ID so MapScreen can pass it to TrackRecorder.
     *
     * Title = date only (e.g. "2026-07-01").
     * Description = timing placeholder (e.g. "@ 14:15 -> ...").
     */
    fun addTempAutoMarker(lat: Double, lon: Double, startTimeMs: Long): String {
        val now = Date()
        val title = dateFormat.get()!!.format(now)
        val startTime = SimpleDateFormat("HH:mm", Locale.US).format(Date(startTimeMs))
        val desc = "@ $startTime -> ..."
        val marker = UserMarker(
            id = UUID.randomUUID().toString(),
            name = title,
            description = desc,
            geometry = MarkerGeometry.Pin(LatLng(lat, lon)),
            proximityOverrideM = AppConfig.boatMarkerAutoMarkerProximityM,
            confirmed = false,
            pinned = true,
            icon = "\uD83D\uDD50",  // 🕐
            createdAtEpochMs = System.currentTimeMillis(),
            origin = ykws.android.maro.data.model.markers.MarkerOrigin.IDLE_AUTO,
            keepable = false
        )
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.add(marker) }
            val all = withContext(Dispatchers.IO) { repo.loadAll() }
            val settings = settingsManager.settings.value
            _allMarkers.value = all
            _markers.value = sortMarkers(all.filter { it.matchesFilter(settings.markerListFilter) }, settings.markerListSort)
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
            val settings = settingsManager.settings.value
            _allMarkers.value = all
            _markers.value = sortMarkers(all.filter { it.matchesFilter(settings.markerListFilter) }, settings.markerListSort)
        }
    }

    /** Toggle the pinned state of a marker. */
    fun togglePin(markerId: String) {
        val marker = _markers.value.find { it.id == markerId } ?: return
        val updated = marker.copy(pinned = !marker.pinned)
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.update(updated) }
            val all = withContext(Dispatchers.IO) { repo.loadAll() }
            val settings = settingsManager.settings.value
            _allMarkers.value = all
            _markers.value = sortMarkers(all.filter { it.matchesFilter(settings.markerListFilter) }, settings.markerListSort)
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
            val settings = settingsManager.settings.value
            _allMarkers.value = all
            _markers.value = sortMarkers(all.filter { it.matchesFilter(settings.markerListFilter) }, settings.markerListSort)
        }
    }

    // ── Post-save undo (Snackbar) ─────────────────────────────────────────

    // ── Icon management ───────────────────────────────────────────────────

    /** Set the icon on a marker (null = remove icon, unpin). */
    fun setMarkerIcon(markerId: String, icon: String?) {
        val marker = _allMarkers.value.find { it.id == markerId } ?: return
        val updated = marker.copy(icon = icon, pinned = icon != null)
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.update(updated) }
            val all = withContext(Dispatchers.IO) { repo.loadAll() }
            val settings = settingsManager.settings.value
            _allMarkers.value = all
            _markers.value = sortMarkers(all.filter { it.matchesFilter(settings.markerListFilter) }, settings.markerListSort)
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

    private var whereAmIJob: kotlinx.coroutines.Job? = null

    /**
     * Synchronous whereAmI — used by the idle threshold callback
     * which runs inside a coroutine on the recorder's scope.
     * Returns markers snapshotted at [boatPos].
     */
    fun whereAmISync(boatPos: LatLng): WhereAmIResult {
        val index = coastlineIndex ?: return WhereAmIResult(emptyList())
        val all = _markers.value
        if (all.isEmpty()) return WhereAmIResult(emptyList())
        MarkerMatcher.debugger.clear()
        return MarkerMatcher.resolveAllMarkers(boatPos, all, index)
    }

    /**
     * Runs [MarkerMatcher.resolveAllMarkers] at [boatPos] using the injected
     * coastline spatial index.  Posts the result to [matchResult] and switches the
     * drawer to [MarkerDrawerState.MatchResult].
     *
     * Cancels any previous in-progress resolution (rapid-tap guard).
     */
    fun whereAmI(boatPos: LatLng) {
        val index = coastlineIndex ?: return
        val all = _markers.value
        if (all.isEmpty()) {
            _matchResult.value = WhereAmIResult(emptyList())
            _drawerState.value = MarkerDrawerState.MatchResult
            return
        }

        whereAmIJob?.cancel()
        MarkerMatcher.debugger.clear()
        whereAmIJob = viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                MarkerMatcher.resolveAllMarkers(boatPos, all, index)
            }
            val segs = MarkerMatcher.debugger.getSegments()
            Log.d("WIA", "DEBUGGER: getSegments returned ${segs.size} items")
            _debugSegments.value = segs
            _matchResult.value = result
            _drawerState.value = MarkerDrawerState.MatchResult
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
