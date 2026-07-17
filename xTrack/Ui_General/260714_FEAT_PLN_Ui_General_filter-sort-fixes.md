# Filter/Sort Fixes — Audit & Plan

**Created:** 2026-07-14 13:37 UTC
**Updated:** 2026-07-14 14:02 UTC (Ask review: found writes + markerLayerState dependency)
**Status:** planned — framework fix reviewed
**Scope:** `MarkersViewModel.kt`, `TrackViewModel.kt`, `MapScreen.kt`, `ListOverlayScaffold.kt`

---

## Parts 1 & 2 — Implemented ✅

9 fixes across 4 files. BUILD SUCCESSFUL × 2.

---

## Part 3 — Root Cause: Three `SettingsManager` Instances

Three ViewModels each create their own `SettingsManager` — each with a separate `MutableStateFlow<AppSettings>`:

| ViewModel | Writes? | Reads? |
|-----------|---------|--------|
| `NavigationViewModel` | Filter/sort/layer (primary) | All |
| `MarkersViewModel` | `toggleMarkerLayer()`, `showLayer()` | Filter/sort/layer/init |
| `TrackViewModel` | None | Filter/sort/init |

When MapScreen calls `viewModel.updateSettings { it.copy(markerListFilter = newFilter) }`, only NavigationViewModel's StateFlow updates. MarkersViewModel's `refreshSort()` reads its own stale StateFlow → filter never reaches list. Same for TrackViewModel.

### Ask review found: MarkersViewModel also WRITES

`toggleMarkerLayer()` and `showLayer()` call `settingsManager.update()` on MarkersViewModel's own instance — NavigationViewModel never sees these changes either. The `markerLayerState` and `userMarkersVisible` StateFlows are derived from MarkersViewModel's SettingsManager.

### Framework Fix

Remove `SettingsManager` from MarkersViewModel and TrackViewModel. Inject shared `StateFlow<AppSettings>` (read) and updater callback (write, MarkersViewModel only).

**TrackViewModel** — read-only:
```kotlin
fun observeSettings(flow: StateFlow<AppSettings>) {
    viewModelScope.launch {
        flow.collect { settings ->
            if (!isLoaded) return@collect
            _summaries.value = sortSummaries(
                _allSummaries.value.filter { it.matchesFilter(settings.trackListFilter, midnightMs) },
                settings.trackListSort
            )
        }
    }
}
```

**MarkersViewModel** — read + write:
```kotlin
private var settingsFlow: StateFlow<AppSettings>? = null
private var updateSettings: (((AppSettings) -> AppSettings) -> Unit)? = null

fun observeSettings(flow: StateFlow<AppSettings>, updater: ((AppSettings) -> AppSettings) -> Unit) {
    this.settingsFlow = flow; this.updateSettings = updater
    _markerLayerState.value = flow.value.markerLayerState
    viewModelScope.launch {
        flow.collect { settings ->
            if (isLoaded) _markers.value = sortMarkers(...)
            _markerLayerState.value = settings.markerLayerState
        }
    }
}
```

`markerLayerState` → plain `MutableStateFlow` (was derived from `settingsManager.settings`).
`toggleMarkerLayer()`/`showLayer()` → `updateSettings?.invoke { it.copy(markerLayerState = next) }`.
`refreshSort()`/`refreshSummaries()` → optional `filter: ListFilter?` param, passed from MapScreen callbacks.

**MapScreen wiring:**
```kotlin
LaunchedEffect(Unit) {
    markersViewModel.observeSettings(viewModel.settings, viewModel::updateSettings)
    trackViewModel.observeSettings(viewModel.settings)
}
```

**Callbacks pass filter directly (bypass flow round-trip):**
```kotlin
onMarkerFilterChange = { newFilter ->
    viewModel.updateSettings { it.copy(markerListFilter = newFilter) }
    markersViewModel.refreshSort(filter = newFilter)
}
onTrackFilterChange = { newFilter ->
    viewModel.updateSettings { it.copy(trackListFilter = newFilter) }
    trackViewModel.refreshSummaries(filter = newFilter, reloadFromDisk = false)
}
```

### What gets removed

| ViewModel | Removed |
|-----------|---------|
| `MarkersViewModel` | `private val settingsManager: SettingsManager` field, init collect block, `markerLayerState` derivation, `userMarkersVisible` derivation |
| `TrackViewModel` | `private val settingsManager: SettingsManager` field, init collect block |

---

## Implementation

| # | File | Change |
|---|------|--------|
| 9a | `MarkersViewModel.kt` | Remove `settingsManager`; add `settingsFlow`/`updateSettings` fields + `observeSettings()`; `_markerLayerState`→plain MutableStateFlow; repoint `toggleMarkerLayer`/`showLayer` to `updateSettings`; add `filter` param to `refreshSort()`; update all `settingsManager.settings.value` reads→`settingsFlow?.value` |
| 9b | `TrackViewModel.kt` | Remove `settingsManager`; add `observeSettings()`; add `filter` param to `refreshSummaries()`; update reads→`settingsFlow?.value` |
| 10 | `MapScreen.kt` | Two `LaunchedEffect(Unit)` for `observeSettings`; pass `filter` in callbacks |

**Files:** 3 | **~60 lines removed, ~25 added** | Build risk: Medium

## Verification

1. Build: `gradlew assembleDebug`
2. Markers: change origin → list refreshes
3. Tracks: change date/pinned → list refreshes
4. Toggle marker layer → layer state syncs
5. Sort changes → work (same root cause)
6. Filter persists across list open/close
