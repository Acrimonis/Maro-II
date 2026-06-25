package ykws.android.maro.ui.map

import android.app.Application
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
import ykws.android.maro.data.model.markers.MarkerGeometry
import ykws.android.maro.data.model.markers.UserMarker
import ykws.android.maro.data.settings.AppSettings
import ykws.android.maro.data.settings.SettingsManager
import ykws.android.maro.spatial.CoastlineSpatialIndex
import ykws.android.maro.spatial.MarkerMatcher
import ykws.android.maro.spatial.ProximityConfig
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

    /** Loaded user markers (reactive). */
    private val _markers = MutableStateFlow<List<UserMarker>>(emptyList())
    val markers: StateFlow<List<UserMarker>> = _markers.asStateFlow()

    /** Whether the user markers layer is visible (FanLayout toggle). */
    val userMarkersVisible: StateFlow<Boolean> =
        settingsManager.settings.let { flow ->
            // Bridge: mirror settings into our own StateFlow, seeded from initial value
            val initial = flow.value.userMarkersVisible
            MutableStateFlow(initial).also { sf ->
                viewModelScope.launch {
                    flow.collect { sf.value = it.userMarkersVisible }
                }
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

    // ── Coastline index (injected by the screen) ──────────────────────────
    /** Set by [MapScreen] when coastline is ready. Used by land-blocking engine. */
    var coastlineIndex: CoastlineSpatialIndex? = null

    // ── Init ──────────────────────────────────────────────────────────────

    init {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) { repo.loadAll() }
            _markers.value = loaded
        }
    }

    // ── Visibility toggle ─────────────────────────────────────────────────

    /** Toggles the user markers layer on/off. */
    fun toggleVisibility() {
        val current = userMarkersVisible.value
        settingsManager.update { it.copy(userMarkersVisible = !current) }
    }

    /** Shows the user markers layer if not already visible (no-op if visible). */
    fun showLayer() {
        if (!userMarkersVisible.value) toggleVisibility()
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
        emitMapCenterFor(ids[newIndex])
    }

    /** Navigate to the next marker in the multi-marker selection (§11). */
    fun viewNextMarker() {
        val ids = _selectedMarkerIds.value
        if (ids.size <= 1) return
        val current = _selectedMarkerIndex.value
        val newIndex = if (current < ids.lastIndex) current + 1 else 0
        _selectedMarkerIndex.value = newIndex
        _selectedMarkerId.value = ids[newIndex]
        emitMapCenterFor(ids[newIndex])
    }

    private fun emitMapCenterFor(markerId: String) {
        val marker = _markers.value.find { it.id == markerId } ?: return
        val pos = when (val g = marker.geometry) {
            is MarkerGeometry.Pin -> g.position
            is MarkerGeometry.Circle -> g.center
            is MarkerGeometry.Corridor -> g.p1
        }
        _mapCenterRequest.value = pos
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

    /** Icon matching the description field: 📌 ⭕ 📏 */
    private fun typeIcon(type: MarkerType): String = when (type) {
        MarkerType.PIN -> "\uD83D\uDCCC"     // 📌
        MarkerType.CIRCLE -> "\u2B55"         // ⭕
        MarkerType.CORRIDOR -> "\uD83D\uDCCF" // 📏
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
        val defaultName = "${typeIcon(initialType)} ${colorName(colorIdx)}"
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
        _wizardStep.value = null
        editingMarkerId = null
        _selectedMarkerId.value = null
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

        val marker = UserMarker(
            id = UUID.randomUUID().toString(),
            name = form.name.ifBlank { "Marker" },
            geometry = geometry,
            description = form.description,
            proximityOverrideM = proximityOverride,
            confirmed = true,
            colorIndex = form.colorIndex
        )

        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.add(marker) }
            _markers.value = withContext(Dispatchers.IO) { repo.loadAll() }
            _drawerState.value = MarkerDrawerState.Hidden
            _lastSavedMarkerId.value = marker.id
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

        val updated = existing.copy(
            name = form.name.ifBlank { existing.name },
            geometry = geometry,
            description = form.description,
            proximityOverrideM = proximityOverride
        )

        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.update(updated) }
            _markers.value = withContext(Dispatchers.IO) { repo.loadAll() }
            _drawerState.value = MarkerDrawerState.Hidden
        }
    }

    /** Delete a marker by ID. */
    fun deleteMarker(markerId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.delete(markerId) }
            _markers.value = withContext(Dispatchers.IO) { repo.loadAll() }
            _drawerState.value = MarkerDrawerState.Hidden
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
            _markers.value = withContext(Dispatchers.IO) { repo.loadAll() }
        }
    }

    // ── Post-save undo (Snackbar) ─────────────────────────────────────────

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

    /**
     * Runs [MarkerMatcher.resolveAllMarkers] at [boatPos] using the injected
     * coastline spatial index.  Posts the result to [matchResult] and switches the
     * drawer to [MarkerDrawerState.MatchResult].
     */
    fun whereAmI(boatPos: LatLng) {
        val index = coastlineIndex ?: return
        val all = _markers.value
        if (all.isEmpty()) {
            _matchResult.value = WhereAmIResult(emptyList())
            _drawerState.value = MarkerDrawerState.MatchResult
            return
        }

        val config = ProximityConfig(
            pinM = AppConfig.markerProximityPinM,
            zoneMultiplier = AppConfig.markerProximityZoneMultiplier
        )

        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                MarkerMatcher.resolveAllMarkers(boatPos, all, index, config)
            }
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
