package ykws.android.maro.ui.map

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.markers.UserMarkerRepository
import ykws.android.maro.data.model.CoastlineData
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.MarkerGeometry
import ykws.android.maro.data.model.markers.UserMarker
import ykws.android.maro.data.settings.AppSettings
import ykws.android.maro.data.settings.SettingsManager
import ykws.android.maro.spatial.MarkerMatcher
import ykws.android.maro.spatial.MatchResult
import ykws.android.maro.spatial.ProximityConfig
import ykws.android.maro.spatial.TieredMatchResult
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

    /** Viewing an existing marker's details (read-only, with Edit/Close buttons). */
    data class Viewing(val markerId: String) : MarkerDrawerState()

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
    val radiusM: Double = 200.0,
    val widthM: Double = 300.0,
    val proximityOverrideM: String = "",  // empty = use computed default
    val description: String = "",
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

    /** Wizard animation direction: true = forward (Next), false = backward (Previous). */
    var wizardForward = true
        private set

    /** ID of the marker being edited via wizard, or null for creation. */
    private var editingMarkerId: String? = null

    /** ID of the last saved marker (for post-save undo Snackbar). */
    private val _lastSavedMarkerId = MutableStateFlow<String?>(null)
    val lastSavedMarkerId: StateFlow<String?> = _lastSavedMarkerId.asStateFlow()

    /** Result of the last "where am I?" query. */
    private val _matchResult = MutableStateFlow<TieredMatchResult?>(null)
    val matchResult: StateFlow<TieredMatchResult?> = _matchResult.asStateFlow()

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

    // ── Coastline data (injected by the screen) ───────────────────────────
    /** Set by [MapScreen] when coastline is ready. Used by land-blocking engine. */
    var coastlineData: CoastlineData? = null

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

    /** Opens drawer in viewing mode for the marker with [markerId]. */
    fun openEditDrawer(markerId: String) {
        val marker = _markers.value.find { it.id == markerId } ?: return
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
        val radiusM = (marker.geometry as? MarkerGeometry.Circle)?.radiusM ?: 200.0
        val widthM = (marker.geometry as? MarkerGeometry.Corridor)?.widthM ?: 300.0
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
        _drawerState.value = MarkerDrawerState.Viewing(markerId)
    }

    /** Closes the drawer. */
    fun closeDrawer() {
        _drawerState.value = MarkerDrawerState.Hidden
        _wizardStep.value = null
        editingMarkerId = null
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

    /** Begin wizard in creation mode with optional [initialType] and [initialPos]. */
    fun startWizard(initialType: MarkerType = MarkerType.PIN, initialPos: LatLng? = null) {
        showLayer()
        editingMarkerId = null
        val now = Date()
        _createForm.value = CreateFormState(
            type = initialType,
            position = initialPos,
            radiusM = 200.0,
            name = dateFormat.get()!!.format(now),
            description = dateTimeFormat.get()!!.format(now)
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
            val radiusM = (marker.geometry as? MarkerGeometry.Circle)?.radiusM ?: 200.0
            val widthM = (marker.geometry as? MarkerGeometry.Corridor)?.widthM ?: 300.0
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
        // Start at TypeSelect so user can change type; Position pre-filled
        _wizardStep.value = seq.first()
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
    }

    /** Go back to the previous wizard step. */
    fun wizardPrevious() {
        val current = _wizardStep.value ?: return
        val seq = stepSequenceFor(_createForm.value.type)
        val idx = seq.indexOf(current)
        if (idx <= 0) return
        wizardForward = false
        _wizardStep.value = seq[idx - 1]
    }

    /** Cancel wizard — discard form, close drawer. */
    fun wizardCancel() {
        _wizardStep.value = null
        editingMarkerId = null
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
            confirmed = true
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

    /** Soft-delete: remove from UI but allow undo. */
    fun softDeleteMarker(markerId: String) {
        pendingDeletes.add(markerId)
        _markers.value = _markers.value.filter { it.id != markerId }
    }

    /** Undo a soft-delete: restore the marker to the UI. */
    fun undoDeleteMarker(markerId: String) {
        pendingDeletes.remove(markerId)
        // Reload from repo — the marker was never actually deleted
        viewModelScope.launch {
            _markers.value = withContext(Dispatchers.IO) { repo.loadAll() }
        }
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
     * coastline data.  Posts the result to [matchResult] and switches the
     * drawer to [MarkerDrawerState.MatchResult].
     */
    fun whereAmI(boatPos: LatLng) {
        val coast = coastlineData ?: return
        val all = _markers.value
        if (all.isEmpty()) {
            _matchResult.value = TieredMatchResult(emptyList())
            _drawerState.value = MarkerDrawerState.MatchResult
            return
        }

        val config = ProximityConfig(
            pinM = AppConfig.markerProximityPinM,
            zoneMultiplier = AppConfig.markerProximityZoneMultiplier
        )

        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                MarkerMatcher.resolveAllMarkers(boatPos, all, coast, config)
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
