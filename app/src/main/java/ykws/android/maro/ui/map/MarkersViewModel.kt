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
import java.util.UUID

// ─────────────────────────────────────────────────────────────────────────────
// Drawer state sealed hierarchy
// ─────────────────────────────────────────────────────────────────────────────

/** Which mode the marker drawer is in. */
sealed class MarkerDrawerState {
    /** Drawer is hidden. */
    data object Hidden : MarkerDrawerState()

    /** Creating a new marker. */
    data object Creating : MarkerDrawerState()

    /** Editing an existing marker by ID. */
    data class Editing(val markerId: String) : MarkerDrawerState()

    /** Showing "where am I?" match results. */
    data object MatchResult : MarkerDrawerState()
}

// ─────────────────────────────────────────────────────────────────────────────
// Create/edit form state
// ─────────────────────────────────────────────────────────────────────────────

/** Which geometry type is being created/edited. */
enum class MarkerType { PIN, CIRCLE, CORRIDOR }

/** Sub-phase for corridor 2nd-point placement flow. */
enum class CorridorPhase { P1, SET_P2, CONFIRM }

/**
 * Mutable form state for marker creation/editing.
 * Hosted inside [MarkersViewModel] and observed by [MarkerDrawer].
 */
data class CreateFormState(
    val name: String = "",
    val type: MarkerType = MarkerType.PIN,
    val position: LatLng? = null,         // pin position / circle centre / corridor p1
    val radiusM: Double = 500.0,
    val widthM: Double = 300.0,
    val proximityOverrideM: String = "",  // empty = use computed default
    val description: String = "",
    // Corridor 2nd-point flow
    val corridorP2: LatLng? = null,
    val corridorPhase: CorridorPhase = CorridorPhase.P1
)

// ─────────────────────────────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────────────────────────────

/**
 * StateFlow bridge for user-defined markers.
 *
 * Owns marker CRUD, visibility toggle, drawer state, and on-demand
 * "where am I?" match resolution.  Injects coastline data from
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

    /** Result of the last "where am I?" query. */
    private val _matchResult = MutableStateFlow<TieredMatchResult?>(null)
    val matchResult: StateFlow<TieredMatchResult?> = _matchResult.asStateFlow()

    /** Mutable form state for creation/editing (lives here so it survives drawer close). */
    private val _createForm = MutableStateFlow(CreateFormState())
    val createForm: StateFlow<CreateFormState> = _createForm.asStateFlow()

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

    // ── Drawer control ────────────────────────────────────────────────────

    /** Opens drawer in creation mode. [initialPos] seeds the pin/circle centre/corridor p1. */
    fun openCreateDrawer(initialPos: LatLng) {
        _createForm.value = CreateFormState(position = initialPos)
        _drawerState.value = MarkerDrawerState.Creating
    }

    /** Opens drawer in editing mode, pre-filling from the marker with [markerId]. */
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
        val radiusM = (marker.geometry as? MarkerGeometry.Circle)?.radiusM ?: 500.0
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
            corridorP2 = corridorP2,
            corridorPhase = if (corridorP2 != null) CorridorPhase.CONFIRM else CorridorPhase.P1
        )
        _drawerState.value = MarkerDrawerState.Editing(markerId)
    }

    /** Closes the drawer. */
    fun closeDrawer() {
        _drawerState.value = MarkerDrawerState.Hidden
    }

    // ── Form mutations ────────────────────────────────────────────────────

    fun updateForm(transform: (CreateFormState) -> CreateFormState) {
        _createForm.value = transform(_createForm.value)
    }

    // ── Corridor 2nd-point flow ───────────────────────────────────────────

    /** Sets p2 for corridor and moves to CONFIRM phase. */
    fun setCorridorP2(p2: LatLng) {
        _createForm.value = _createForm.value.copy(
            corridorP2 = p2,
            corridorPhase = CorridorPhase.CONFIRM
        )
    }

    /** Goes back from CONFIRM to SET_P2 to reposition p2. */
    fun backToCorridorP1() {
        _createForm.value = _createForm.value.copy(
            corridorP2 = null,
            corridorPhase = CorridorPhase.P1
        )
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
