<!-- scope: feature -->
# Toggle Control Merge — Design Discussion

## Overview

Merge the two separate layer toggle buttons for **300m zone** and **regulated zones** into a single 4-state cycle button. Add a `regulatedZonesVisible` toggle in **Settings → General/Display/Layers**.

---

## Current State

### Right-edge control stack (z-order top to bottom)

```
┌─────────────┐
│ DangerLayer │  ← warning triangle icon
├─────────────┤
│ RegZonesBtn │  ← ring + dot icon (regulatedZonesVisible)
├─────────────┤
│ LayerButton │  ← single circle icon (zone300Visible)
├─────────────┤
│  Zoom +/-   │
└─────────────┘
```

### Two independent `AppSettings` booleans

- `zone300Visible: Boolean = true` — persists via `KEY_ZONE300_VISIBLE`
- `regulatedZonesVisible: Boolean = true` — persists via `KEY_REGULATED_ZONES_VISIBLE`

### Overlay z-order on map

```
drawDepthMap        (bottom)
drawLowDepthWarning
drawIsobaths
drawRegulatedZones  ← between isobaths and zone300
drawZone300
drawCoastline       (top)
```

---

## Proposed 4-State Cycle

### Cycle order

```
None ──→ 300m ZONE ──→ BOTH ──→ Reg Zones ──→ (back to None)
```

Each click advances one step. Default state at app start: **NONE** — both layers off by default. Default visibility for each layer is configured via `maro.properties` (see below).

### State → boolean mapping

| State | `zone300Visible` | `regulatedZonesVisible` |
|-------|:----------------:|:-----------------------:|
| None | `false` | `false` |
| 300m ZONE | `true` | `false` |
| BOTH | `true` | `true` |
| Reg Zones | `false` | `true` |

### State enum

```kotlin
private enum class ZoneLayerState {
    NONE,
    ZONE300,
    BOTH,
    REGULATED;

    fun next() = entries[(ordinal + 1) % entries.size]
}
```

---

## Icon Design — Two Concentric Circles

The icon encodes both states independently using alpha:

```
      ┌──────────┐
      │ ╭──────╮ │
      │ │ ╭──╮ │ │
      │ │ │  │ │ │
      │ │ ╰──╯ │ │
      │ ╰──────╯ │
      └──────────┘
      
      Inner circle  = 300m zone indicator (filled circle)
      Outer ring    = Regulated zones indicator (stroked ring)
```

### Visual rendering (28dp Canvas inside 64dp button)

| State | Inner circle alpha | Outer ring alpha | Visual |
|-------|:------------------:|:----------------:|--------|
| None | 0.25f | 0.25f | Both dimmed — button looks inactive |
| 300m ZONE | 1.0f | 0.25f | Inner circle bright, outer ring dim |
| BOTH | 1.0f | 1.0f | Both bright — full active |
| Reg Zones | 0.25f | 1.0f | Inner circle dim, outer ring bright |

### Drawing parameters

```
Inner circle: radius = w * 0.22f, fill style (α = state alpha)
Outer ring:   radius = w * 0.40f, stroke style (α = state alpha, strokeWidth = w * 0.10f)
```

Both use theme blue (`0xFF1565C0`).

---

## Settings Addition

### Location: Settings → General/Display/Layers

Add after the existing `zone300` toggle:

```
☑ Zone 300 m band       ← existing
☐ Regulated zones       ← new: regulatedZonesVisible
☑ Low-depth warning     ← existing
```

Follow the existing pattern:
- Add `regulatedZonesVisible` field to [`AppSettings`](app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt:96)
- Add `KEY_REGULATED_ZONES_VISIBLE` constant
- Add persistence in `load()` / `save()` methods
- Add checkbox composable in [`SettingsScreen.kt`](app/src/main/java/ykws/android/maro/ui/settings/SettingsScreen.kt)

---

## Files to Modify

| File | Changes |
|------|---------|
| [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt) | Replace `RegulatedZonesLayerButton` + `LayerButton` with one `ZoneLayerButton` composable; add `ZoneLayerState` enum; update `onToggleRegulatedZones` + `onToggleZone300` → single `onCycleZoneLayers` callback |
| [`SettingsManager.kt`](app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt) | Add `regulatedZonesVisible` field (already exists from prior work), persistence key, load/save |
| [`SettingsScreen.kt`](app/src/main/java/ykws/android/maro/ui/settings/SettingsScreen.kt) | Add `regulatedZonesVisible` checkbox in Display/Layers section |

---

## Confirmed Decisions

1. **Derive 4-state from booleans** ✅ — map button state is computed from `zone300Visible` + `regulatedZonesVisible` each render, keeping it synced with independent Settings toggles.
2. **Danger layer stays separate** ✅ — only Zone300 + RegulatedZones are merged; the warning-triangle button remains independent.
3. **Default state: NONE** ✅ — both layers off at app start. Default visibility defined in `maro.properties` (see below).
4. **Cycle order: None → 300m → Both → Reg** ✅ — 300m comes first when turning on, which is the more common layer.

## maro.properties — Layer Default Visibility

A new config file `maro.properties` at the project root (alongside `gradle.properties`) defines default visibility for each map layer at app startup. The Gradle build reads it and passes values as `BuildConfig` fields or resource values.

```properties
# Default visibility for map overlay layers at app startup.
# true = layer visible on first launch, false = hidden.
layer.zone300.default=true
layer.regulatedZones.default=false
layer.lowDepthWarning.default=true
layer.coastline.default=true
```

These values feed into `AppSettings` defaults, replacing the current hardcoded `= true` / `= false` literals in the data class default constructor. If `maro.properties` is missing or a key is absent, the existing hardcoded defaults apply as fallback.

### Integration approach
1. Create `maro.properties` at repo root with layer visibility keys
2. In `app/build.gradle.kts`, add a task that reads `maro.properties` and generates a `BuildConfig` field or a resource value for each key
3. In [`SettingsManager.kt`](app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt), read the BuildConfig/resource value as the `AppSettings` default instead of `= true`

