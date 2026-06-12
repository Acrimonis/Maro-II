# Hydration: RegulatedZones

**Last Bake:** 2026-06-12 09:37 UTC+2
**State:** Active — toggle-control-merge subfeature complete.

## Summary
- **Toggle control merge implemented** — replaced separate `LayerButton` (300m) + `RegulatedZonesLayerButton` with single `ZoneLayerButton` composable cycling through **None → 300m ZONE → BOTH → Reg Zones**
- **Icon**: two concentric circles — inner (fill) = 300m state, outer (stroke) = regulated zones state, each at full or dim alpha independently
- **maro.properties** created at repo root — controls first-launch default visibility for all layers via `BuildConfig` fields
- **SettingsManager.kt** updated — `zone300Visible`, `regulatedZonesVisible`, `coastlineVisible`, `lowDepthWarningVisible` defaults sourced from `maro.properties` via BuildConfig
- **CoastlineViewModel.kt** — added `cycleZoneLayers()` with auto-reveal state machine tracking for zone300
- **Settings toggle** added — `regulatedZonesVisible` checkbox in General/Display/Layers alongside the existing zone300 toggle
- **`BUILD SUCCESSFUL`** — `assembleDebug` compiles cleanly

## Target Files
- `maro.properties` — NEW. Layer default visibility config
- `app/build.gradle.kts` — MODIFIED. Reads maro.properties, exposes BuildConfig fields
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — MODIFIED. BuildConfig-sourced layer defaults
- `app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt` — MODIFIED. Added `cycleZoneLayers()`
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — MODIFIED. `ZoneLayerState` enum, `ZoneLayerButton`, `onCycleZoneLayers`, settings toggle
- `app/src/main/res/values/strings.xml` — MODIFIED. Regulated zones settings label + description
- `app/src/main/res/values-fr/strings.xml` — MODIFIED. French translations

## Key Changes
- `LayerButton` and `RegulatedZonesLayerButton` composables removed → replaced by `ZoneLayerButton(state: ZoneLayerState, onClick)`
- Old `onToggleZone300` + `onToggleRegulatedZones` callbacks → single `onCycleZoneLayers` callback wired to `viewModel::cycleZoneLayers`
- `ZoneLayerState` enum: `NONE`, `ZONE300`, `BOTH`, `REGULATED` with `fromBooleans()` + `next()`
- Default state = NONE (both layers off), configurable via `maro.properties`

## Next Steps
None pending — feature is stable. Open for future enhancement:
- Vessel-size filter integration in display pipeline
- Per-type visibility toggles (speed, anchoring, access, etc.)
- Zone tap interaction (highlight + full details in info banner)
