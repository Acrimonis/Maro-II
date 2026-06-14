<!-- scope: feature -->

# Settings Apply-on-Close — Discussion (Refined)

## Problem

Currently every settings toggle/slider calls `onUpdateSettings` → `settingsManager.update()` → `SharedPreferences.edit().apply()` + `StateFlow` emission immediately. Sliders are the main pain point: dragging a slider from 1 to 5 fires the heavy side effect on every intermediate step (potentially 8–10 times).

## Refined Requirement

- **Immediate (keep as-is):** `gpsMode`, `languageCode`, `keepScreenOn` — lightweight, safety-critical, or needed for the settings page itself
- **Deferred (batch on dismiss):** everything else — sliders and toggles whose side effects are wasted behind a full-screen overlay

## Settings Classification

### Immediate (bypass draft)

| Setting | Why immediate |
|---------|--------------|
| `gpsMode` | Safety-critical; user expects GPS icon/status feedback when they flip the switch |
| `languageCode` | Settings page renders in current language; labels should update for consistency |
| `keepScreenOn` | Lightweight Window flag; prevents screen dimming while user reads settings |

### Deferred (draft → apply on dismiss)

| Setting | Heavy side effect |
|---------|------------------|
| `coastlineVisible` | Coastline polyline visibility recomposition |
| `zone300Visible` | Zone 300 overlay visibility recomposition |
| `lowDepthWarningVisible` | Low-depth warning overlay visibility recomposition |
| `lowDepthWarningMaxM` | `produceState` rebuild of warning bitmap (expensive) |
| `lowDepthWarningMinOpacityPct` | Same bitmap rebuild |
| `zoneAutoRevealDistanceM` | Zone auto-reveal threshold recalculation |
| `zoneAutoRevealTimeS` | Zone auto-reveal threshold recalculation |
| `zone300AutoShowGps` | Zone decision logic reconfiguration |
| `zone300AutoShowDemo` | Zone decision logic reconfiguration |
| `recenterDelaySeconds` | GPS auto-follow resume timer |
| `gpsActiveIntervalSec` | GPS acquisition preset |
| `gpsActiveMinDistanceM` | GPS acquisition preset |
| `adaptiveWindowSec` | Adaptive idle detector reconfiguration |
| `adaptiveDistanceM` | Adaptive idle detector reconfiguration |
| `adaptiveIdleIntervalSec` | Adaptive idle detector reconfiguration |
| `mapRefreshFps` | GPS throttle `Flow` recomposition |
| `emodnetShallowCutoffM` | Depth readout gate |
| `defaultLatitude`, `defaultLongitude` | Map center default (rarely changed) |
| Regenerate toggles | Already explicit button — NOT affected |

## Proposed Implementation: Hybrid Draft State

```kotlin
// In MapScreen body
var showSettings by remember { mutableStateOf(false) }
var draftSettings by remember { mutableStateOf<AppSettings?>(null) }
val settingsScrollState = rememberScrollState()

// Seed draft when overlay opens
if (showSettings && draftSettings == null) {
    draftSettings = appSettings
}

if (showSettings) {
    SettingsOverlay(
        scrollState = settingsScrollState,
        settings = draftSettings!!,
        // Draft-only: defers to dismiss
        onUpdateSettings = { transform ->
            draftSettings = transform(draftSettings!!)
        },
        // Immediate: bypasses draft, hits real settings
        onGpsModeChange = { enable ->
            viewModel.updateSettings { it.copy(gpsMode = enable) }
            draftSettings = draftSettings!!.copy(gpsMode = enable)  // keep draft in sync
        },
        onDismiss = {
            // Batch-apply deferred changes
            viewModel.updateSettings { draftSettings!! }
            draftSettings = null
            showSettings = false
        },
        onRegenerateRasters = { steps ->
            val waterTest = if (state is CoastlineState.Ready) viewModel::isOnWater else { _, _ -> true }
            depthViewModel.generateRasterLayers(context, steps, appSettings, waterTest)
        }
    )
}
```

### Key design point: languageCode is immediate

`SettingsLanguageRow` already calls `onUpdateSettings { it.copy(languageCode = code) }`. With the hybrid approach, this would be deferred — but the user wants it immediate. So `SettingsLanguageRow` needs a dedicated callback that hits `settingsManager.update()` directly AND updates the draft:

```kotlin
SettingsLanguageRow(
    languageCode = draftSettings!!.languageCode,
    onSelect = { code ->
        viewModel.updateSettings { it.copy(languageCode = code) }  // immediate
        draftSettings = draftSettings!!.copy(languageCode = code)  // sync draft
    }
)
```

Alternatively, keep it simple: `languageCode` could go through the draft like everything else. The settings page label text is determined by `rememberLocalizedContext(languageCode)` at the `MapScreen` level (line 48), not inside `SettingsOverlay`. So deferring `languageCode` means the settings labels won't change until next open — which is the user's call to make.

**User's call:** `languageCode` should be immediate. The `SettingsLanguageRow` gets a dedicated immediate callback, same pattern as `gpsMode`.

## Edge Cases

1. **Back button dismiss** — must apply draft: `BackHandler { viewModel.updateSettings { draftSettings!! }; draftSettings = null; showSettings = false }`
2. **External mutation while overlay open** (e.g., auto-reveal toggles `zone300Visible`) — draft wins on dismiss. Edge case, acceptable.
3. **Immediate setting also in draft** — `gpsMode` and `languageCode` must sync to the draft after immediate apply, otherwise dismiss would overwrite the immediate change with stale draft value.

## Files Affected

- [`app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt) — draft state, hybrid dispatch, dismiss logic
