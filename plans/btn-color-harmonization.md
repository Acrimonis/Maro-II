# Button Color Harmonization — Map Control Stack

## Current State

All right-edge map control buttons share the same styling pattern:

| Property | Current Value |
|---|---|
| **Background** | White (`0xCCFFFFFF` semi-transparent, or `0xFFFFFFFF` solid) |
| **Shape** | Circle, 64 dp |
| **Icon color** | `ThemeBlue` = `0xFF1565C0` (Material Blue 800) |
| **Icon size** | 28–32 dp |

Affected composables:
- [`MapControlButton`](../app/src/main/java/ykws/android/maro/ui/map/MapControlButton.kt:22) — shared base (fan children, parent badge)
- [`SettingsButton`](../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1489) — white bg, blue gear
- [`ZoneLayerButton`](../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1533) — white bg, blue circles
- [`DangerLayerButton`](../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1577) — white bg, blue triangle
- [`ZoomButton`](../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3087) — white bg, blue +/- glyphs
- [`EarthWaterIcon`](../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1462) — white bg when inactive
- All fan icon components in [`FanIconComponents.kt`](../app/src/main/java/ykws/android/maro/ui/map/FanIconComponents.kt:20) — `ThemeBlue` strokes/fills

## Reference Palette — Dashboard

From [`DashboardPanel.kt`](../app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt:46):

| Token | Color | Hex |
|---|---|---|
| `background` | Very dark navy | `0xFF1A1A2E` |
| `cardBg` | Dark navy blue | `0xFF16213E` |
| `textPrimary` | Light grey | `0xFFE0E0E0` |
| `textMuted` | Blue-grey | `0xFF90A4AE` |
| (accent) | Blue 800 | `0xFF1565C0` |

The app uses a **dark theme** throughout. The white control buttons sit in stark visual contrast — they look like floating action buttons rather than integrated UI elements.

## Post-Mortem — Why Option A failed

Three concrete problems identified after building and reviewing:

1. **Semi-transparent bg is unstable** — `0xCC16213E` (80% alpha) lets the map show through. Against dark water areas, the effective background becomes near-black, collapsing contrast with the grey and blue icons.

2. **Inconsistent toggle language across buttons** — Fan children use blue/grey hue switching (blue=ON, grey=OFF), while `ZoneLayerButton` and `DangerLayerButton` use blue-only alpha switching (blue@100%=ON, blue@25%=OFF). `SettingsButton` uses light grey (no toggle at all). Three different conventions for the same button stack.

3. **Grey `0xFFE0E0E0` on semi-dark bg** — even at full alpha, ~8:1 on solid `0xFF16213E` but much worse on `0xCC16213E` over dark water. Inactive at 25% alpha becomes invisible.

## Revised Proposal — Option E: Normalize + Maximum Contrast

### Core principle

**All control-stack buttons share an identical visual formula:**
- Same dark opaque background
- Same white icon colour
- Toggle state distinguished by alpha only (no hue switching)

| Element | Value | Contrast vs `0xFF16213E` |
|---|---|---|
| **Button bg** | `0xFF16213E` (solid cardBg, Option D) | ✅ matches dashboard exactly |
| **All icons — static** (gear, zoom +/-, GPS DEMO) | `0xFFFFFFFF` (white) @ 100% | **~13:1 WCAG AAA** |
| **All icons — toggle ON** (layer active) | `0xFFFFFFFF` (white) @ 100% | **~13:1 WCAG AAA** |
| **All icons — toggle OFF** (layer inactive) | `0xFFFFFFFF` (white) @ 25% alpha = `0x40FFFFFF` | ~3:1 — clearly dimmed |
| **GPS state colours** (green/amber/red/blue) | Keep as-is — functional status indicators | ✅ |

### Why pure white instead of grey or blue

| Option | ON vs bg | OFF vs bg | Problem |
|---|---|---|---|
| Blue active / grey inactive | 4:1 | 2:1 | OFF near-invisible; multi-hue inconsistent |
| **White active / dim white inactive** | **13:1** | **3:1** | **Maximum contrast; single hue; clearly visible OFF** |

### Normalization — every button uses the same template

All **circle buttons** (64dp, right-edge control stack) use [`MapControlButton`](../app/src/main/java/ykws/android/maro/ui/map/MapControlButton.kt:22):

| Button | Current wrapper | After normalization |
|---|---|---|
| Settings (gear) | Standalone `Button` | → `MapControlButton` using `GearIcon()` |
| ZoneLayer (dual circles) | Standalone `Button` + Canvas | → `MapControlButton` + `Canvas` (keep custom, but use white) |
| DangerLayer (triangle) | Standalone `Button` + Canvas | → `MapControlButton` + `Canvas` (keep custom, but use white) |
| Fan children (depth, regulated, 300m, warning) | Already `MapControlButton` ✅ | Same; icons switch to white |
| Fan parent (three-stripe) | Already `MapControlButton` ✅ | Same; icon switches to white |
| Zoom +/− | Already `MapControlButton` ✅ | Same; icons already white |
| ZoomButton (dead code) | Standalone `Button` | Update for consistency |

The **non-circle** indicators (44dp rounded square) keep their shape but get the same bg:

| Indicator | Current bg | After normalization |
|---|---|---|
| EarthWaterIcon | `ButtonColors.bg` (was `0xCC16213E`) | → `0xFF16213E` (opaque) |
| GPS status icon DEMO state | `ButtonColors.bg` (was `0xCC16213E`) | → `0xFF16213E` (opaque) |
| GPS status icon (acquiring, healthy, idle, stale) | Coloured (amber, green, blue, red) | Keep — functional |

### Toggle state logic (white-only alpha)

All 5 fan icon components in [`FanIconComponents.kt`](../app/src/main/java/ykws/android/maro/ui/map/FanIconComponents.kt:20) — keep the `isActive: Boolean` signature, just change the colours:

```kotlin
// Before:
val color = if (isActive) ButtonColors.iconActive else ButtonColors.iconDefault
val alpha = if (isActive) 1f else INACTIVE_ALPHA

// After:
// colour is always white; alpha distinguishes state
val alpha = if (isActive) 1f else INACTIVE_ALPHA
// draw with ComposeColor.White at calculated alpha
```

Same for `ZoneLayerButton` and `DangerLayerButton` — draw in white, alpha distinguishes state.

### ButtonColors palette (updated)

```kotlin
object ButtonColors {
    /** Fully opaque dark navy bg matching dashboard cardBg. */
    val bg = ComposeColor(0xFF16213E)
}
```

No `iconDefault`/`iconActive` needed anymore — all icons use `ComposeColor.White` directly. The `INACTIVE_ALPHA = 0.25f` constant remains.

### MapControlButton changes

Already updated to use `ButtonColors.bg` — just need to change `0xCC16213E` → `0xFF16213E`.

### Files to modify

| File | Changes |
|---|---|
| [`FanIconComponents.kt`](../app/src/main/java/ykws/android/maro/ui/map/FanIconComponents.kt) | `ButtonColors.bg` → `0xFF16213E`; remove `iconDefault`/`iconActive`; all icons use `ComposeColor.White` |
| [`MapControlButton.kt`](../app/src/main/java/ykws/android/maro/ui/map/MapControlButton.kt) | `containerColor` already `ButtonColors.bg` — no code change needed if `bg` value updated |
| [`MapScreen.kt`](../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt) | SettingsButton → use `MapControlButton` + `GearIcon()`; ZoneLayerButton swap to white; DangerLayerButton swap to white; EarthWaterIcon inactive bg → `ButtonColors.bg`; GPS DEMO bg → `ButtonColors.bg` (already set) |

## New Normalized Implementation

1. **`ButtonColors.bg`** → change from `0xCC16213E` to `0xFF16213E` (fully opaque). Remove `iconDefault` and `iconActive`. All icons use `ComposeColor.White` directly.

2. **`MapControlButton`** — already uses `ButtonColors.bg`; no code change needed since `bg` value updates.

3. **`MapScreen.kt` — SettingsButton** → refactor to use `MapControlButton` with `GearIcon()`, eliminating the standalone `Button`.

4. **`MapScreen.kt` — ZoneLayerButton + DangerLayerButton** — keep as standalone `Button` (custom Canvas drawing), but:
   - Use `ButtonColors.bg` for containerColor
   - Draw all Canvas shapes in white (`ComposeColor.White`)
   - Active = alpha 1.0, Inactive = alpha 0.25

5. **`FanIconComponents.kt`** — all 5 toggle icon functions keep `isActive: Boolean` signature, but draw in `ComposeColor.White`:
   - Active = alpha 1.0
   - Inactive = alpha `INACTIVE_ALPHA` (0.25)
   - `PlusIcon`, `MinusIcon`, `GearIcon` → draw in `ComposeColor.White` at alpha 1.0

6. **EarthWaterIcon** inactive bg → uses `ButtonColors.bg` (already does, just value changes).

7. **GPS DEMO state** bg → uses `ButtonColors.bg` (already does, just value changes).

8. **`activeChildCount` badge** — the 18 dp blue circle + white count — no change needed.

### Contrast verification

| State | Colour on `0xFF16213E` | Contrast |
|---|---|---|
| Active / static icon | White `0xFFFFFFFF` | **13.1:1** — WCAG AAA ✅ |
| Inactive icon | White `0x40FFFFFF` (25%) | **3.3:1** — clearly dimmed ✅ |
| GPS green `0xFF2E7D32` | On `0xFF16213E` | 6.8:1 — fine ✅ |
| GPS amber `0xFFFFA726` | On `0xFF16213E` | 9.0:1 — fine ✅ |
| GPS red `0xFFF44336` | On `0xFF16213E` | 6.6:1 — fine ✅ |
| GPS blue `0xFF1565C0` | On `0xFF16213E` | 2.9:1 — marginal, but same blue as before |

## Files to Modify

| File | Change |
|---|---|
| `app/src/main/java/ykws/android/maro/ui/map/MapControlButton.kt` | `containerColor` → dark bg constant |
| `app/src/main/java/ykws/android/maro/ui/map/FanIconComponents.kt` | `ThemeBlue` → `iconDefault`; add active-state color logic; add `ButtonColors` object |
| `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` | `SettingsButton` containerColor; `ZoomButton` containerColor; `ZoneLayerButton` icon color; `DangerLayerButton` icon color; `EarthWaterIcon` bg when inactive |
| `app/src/main/java/ykws/android/maro/ui/map/FanLayout.kt` | Active badge already blue — verify contrast against new dark parent |

## Acceptance Criteria

1. All right-edge control buttons use dark `cardBg`-based background instead of white
2. All icons use light grey (`textPrimary`) default color, accent blue for active states
3. Map readability not degraded — buttons remain distinguishable from map content
4. No regressions in active/inactive alpha differentiation (0.25 = inactive, 1.0 = active)
5. Build passes
