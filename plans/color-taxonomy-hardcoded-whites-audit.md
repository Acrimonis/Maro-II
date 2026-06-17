# Plan: Replace Hardcoded `ComposeColor.White` with Config Tokens

**Feature:** ColorManagement
**Branch:** `feature/more-colors`
**Status:** Plan — pending implementation go-ahead

---

## Problem

48 occurrences of `ComposeColor.White` are hardcoded across 3 Kotlin files, bypassing the centralized [`colors.properties`](../app/src/main/assets/colors.properties) mechanism. While most happen to render the same value as their intended token (pure white), this creates:

1. **Maintenance fragility** — changing `ui.settings.text.primary` to off-white would silently orphan ~30 hardcoded whites
2. **Divider alpha drift** — 11 references use `0.1f` (10%) instead of the config `#14FFFFFF` (7.8%)
3. **Error title inconsistency** — title uses full white while the error description on the same card correctly uses `ui.error.text`
4. **Cursor color** — uses white instead of accent blue (UX preference)
5. **Missing token link** — `FanLayout.kt` child count text doesn't use `ButtonColors.icon`

## Scope

| Layer | Phase | Risk |
|-------|-------|------|
| A — Stale AppConfig defaults (10 fields) | 1st | Low — defaults only used if colors.properties fails to load |
| B — MapScreen.kt hardcoded whites (45 refs) | 2nd | Medium — many refs across 3993-line file |
| C — FanLayout.kt child count (1 ref) | 2nd | Low — single line |
| D — FanIconComponents.kt structural whites (2 refs) | **Skip** | Keep as hardcoded — icon glyph elements |
| E — Optional: cursor color token | 3rd | Low — pure design change |
| F — Centralize zone.properties colors | 4th | Medium — removes dead code, adds isobar tokens |

## Taxonomy Impact

**No new color tokens required for most fixes.** Every `ComposeColor.White` maps to an existing `colors.properties` entry:

| Maps To | Token | Value | Code Count |
|---------|-------|-------|------------|
| [`ui.settings.text.primary`](../app/src/main/assets/colors.properties#123) | `ui.settings.text.primary` | `#FFFFFFFF` | ~28 |
| [`ui.settings.divider`](../app/src/main/assets/colors.properties#133) | `ui.settings.divider` | `#14FFFFFF` | 11 |
| [`ui.settings.toast.text`](../app/src/main/assets/colors.properties#55) | `ui.settings.toast.text` | `#FFFFFF` | 1 |
| [`ui.error.text`](../app/src/main/assets/colors.properties#185) | `ui.error.text` | `#EEFFFFFF` | 1 |
| [`ui.button.icon`](../app/src/main/assets/colors.properties#43) via `ButtonColors.icon` | `ui.button.icon` | `#E0E0E0` | 1 (FanLayout) |
| *(keep structural icon white)* | — | — | 2 (FanIconComponents) |

**1 optional new token** proposed:

| Proposed Token | Purpose | Suggested Value |
|----------------|---------|----------------|
| `ui.settings.input.cursor` | Text field cursor color | `#FF1565C0` (accent blue) or keep existing |

## Phase A — Sync Stale AppConfig Defaults

### Approach: Update 10 hardcoded default values (Approach 1)

**Why this approach:** The defaults serve as both documentation and fallback if `colors.properties` fails to load. Making them nullable (Approach 2) would require null-safety in every hot-path consumer. Simple value correction is safer.

For each of the 10 fields, change the `0x...` literal and the KDoc comment to match `colors.properties`. For aliased values (`${ui.dashboard.status.success}`), use the resolved hex `0xCC4CAF50`:

| Field (line) | Current Default | Correct Default | colors.properties Source |
|--------------|----------------|----------------|--------------------------|
| `buttonActionBgColor` (81) | `0xCCFFFFFF` | `0xCC16213E` | `ui.button.background = #CC16213E` |
| `buttonActionIconColor` (86) | `0xFF1565C0` | `0xFFE0E0E0` | `ui.button.icon = #E0E0E0` |
| `uiDashboardZoneSafe` (129) | `0xFF2E7D32` | `0xCC4CAF50` | `${ui.dashboard.status.success}` → `#CC4CAF50` |
| `statusGpsHealthy` (204) | `0xFF2E7D32` | `0xCC4CAF50` | `${ui.dashboard.status.success}` → `#CC4CAF50` |
| `statusEarthWaterLand` (223) | `0xFF2E7D32` | `0xCC4CAF50` | `${ui.dashboard.status.success}` → `#CC4CAF50` |
| `uiDashboardDistanceExit` (150) | `0xFF2E7D32` | `0xCC4CAF50` | `${ui.dashboard.status.success}` → `#CC4CAF50` |
| `regulatedZoneTypeEnvironmental` (291) | `0xFF2E7D32` | `0xCC4CAF50` | `${ui.dashboard.status.success}` → `#CC4CAF50` |
| `mapZoneAheadLine` (314) | `0xFF00C800` | `0xCC4CAF50` | `${ui.dashboard.status.success}` → `#CC4CAF50` |
| `uiArcAnchorColor` (231) | `0xFF1565C0` | `0xFFE0E0E0` | `ui.arc.anchor.color = #E0E0E0` |
| `uiArcAnchorBackground` (234) | `0xCCFFFFFF` | `0xCC16213E` | `ui.arc.anchor.background = #CC16213E` |

**Example diff for one field:**
```diff
-    /** ARGB colour for action-button background (right-edge control stack).
-     *  Default `#CCFFFFFF` (semi-transparent white). Set via `ui.button.background` in colors.properties. */
-    var buttonActionBgColor: Int = 0xCCFFFFFF.toInt()
+    /** ARGB colour for action-button background (right-edge control stack).
+     *  Default `#CC16213E` (semi-transparent dark blue). Set via `ui.button.background` in colors.properties. */
+    var buttonActionBgColor: Int = 0xCC16213E.toInt()
```

## Phase F — Centralize zone.properties Colors Into colors.properties

### Discovery

The `AppConfig.init()` loads from zone.properties FIRST, then overrides with colors.properties. This has created **dual-source confusion** — some color values are loaded from zone.properties into one set of fields, and from colors.properties into another set of fields with different names.

#### Dead / Orphaned Fields (to be REMOVED)

These 4 fields are loaded from zone.properties but **never read by any consumer** — their colors.properties counterparts are used instead:

| Orphaned Field | zone.properties Key | Dead Since | Live Counterpart | colors.properties Key |
|---------------|---------------------|------------|-----------------|----------------------|
| `lowDepthWarningColor` | `lowDepthWarningColor` | Always? Consumer uses `overlayLowDepthColor` | `overlayLowDepthColor` | `overlay.lowDepth.color` |
| `nodataColor` | `nodata.color` | Always? Consumer uses `mapDepthNodataColor` | `mapDepthNodataColor` | `map.depth.nodata.color` |
| `capArrowColor` | `cap.arrow.color` | Always? Consumer uses `mapNavigationArrowColor` | `mapNavigationArrowColor` | `map.navigation.arrow.color` |
| `directionLineColor` | `direction.line.color` | Always? Consumer uses `mapNavigationLineColor` | `mapNavigationLineColor` | `map.navigation.line.color` |

**Action:** Remove these 4 fields, their KDoc, their init loading lines, and the property keys from zone.properties.

#### Active Fields Without a colors.properties Home

These isobar colors are actively used by `MapScreen.kt` via `AppConfig.isobarColor()` but have NO token in colors.properties:

| Field | Current Home | Used By | colors.properties Key (to add) |
|-------|-------------|---------|-------------------------------|
| `isobarColors[LITTO3D]` | zone.properties (`isobar.color.litto3d`) | `MapScreen.kt` isobath rendering | `map.isobar.litto3d.color` |
| `isobarColors[EMODNET]` | zone.properties (`isobar.color.emodnet`) | `MapScreen.kt` isobath rendering | `map.isobar.emodnet.color` |
| `isobarColorDefault` | zone.properties (`isobar.color.default`) | `MapScreen.kt` isobath rendering | `map.isobar.default.color` |

**Action:** Add these 3 tokens to colors.properties, add loading lines in the colors.properties section of AppConfig.init(), and update the KDoc.

### Migration Steps

1. **Add isobar tokens to [`colors.properties`](../app/src/main/assets/colors.properties):**
   ```properties
   # ── Isobath line colours ─────────────────────────────
   # Litto3D source isobath stroke (dark green)
   map.isobar.litto3d.color=#FF1B5E20
   # EMODNET source isobath stroke (dark blue)
   map.isobar.emodnet.color=#FF00008B
   # Default isobath stroke for any source without explicit entry (muted blue-grey)
   map.isobar.default.color=#FF37474F
   ```

2. **Add loading in [`AppConfig.kt`](../app/src/main/java/ykws/android/maro/config/AppConfig.kt):**
   In the colors.properties section (after line 611), add:
   ```kotlin
   // ── Isobath colours ──────────────────────────────────────
   props.getProperty("map.isobar.litto3d.color")?.let { parseColorOrNull(it) }?.let { isobarColors[DepthSource.LITTO3D] = it }
   props.getProperty("map.isobar.emodnet.color")?.let { parseColorOrNull(it) }?.let { isobarColors[DepthSource.EMODNET] = it }
   props.getProperty("map.isobar.default.color")?.let { parseColorOrNull(it) }?.let { isobarColorDefault = it }
   ```

3. **Remove orphaned fields** (lines 58-77 in AppConfig.kt) — delete `lowDepthWarningColor`, `nodataColor`, `capArrowColor`, `directionLineColor` declarations.

4. **Remove orphaned loading** (lines 483-494 in AppConfig.kt) — delete the `props.getProperty(...)` calls for the 4 orphaned keys.

5. **Clean up zone.properties** — remove `lowDepthWarningColor`, `nodata.color`, `cap.arrow.color`, `direction.line.color` keys.

## Implementation Steps

### Phase 1: Sync AppConfig defaults

1. [`AppConfig.kt`](../app/src/main/java/ykws/android/maro/config/AppConfig.kt) — Update 10 stale default values to match `colors.properties`

### Phase 2: Remove orphaned zone.properties fields

2. [`AppConfig.kt`](../app/src/main/java/ykws/android/maro/config/AppConfig.kt) — Delete `lowDepthWarningColor`, `nodataColor`, `capArrowColor`, `directionLineColor` field declarations (lines 58-77)
3. [`AppConfig.kt`](../app/src/main/java/ykws/android/maro/config/AppConfig.kt) — Delete their init loading (lines 483-494)
4. [`zone.properties`](../app/src/main/assets/zone.properties) — Remove the 4 orphaned property keys

### Phase 3: Add isobar tokens to colors.properties

5. [`colors.properties`](../app/src/main/assets/colors.properties) — Add `map.isobar.litto3d.color`, `map.isobar.emodnet.color`, `map.isobar.default.color`
6. [`AppConfig.kt`](../app/src/main/java/ykws/android/maro/config/AppConfig.kt) — Add loading lines in colors.properties section
7. [`AppConfig.kt`](../app/src/main/java/ykws/android/maro/config/AppConfig.kt) — Update `isobarColors` and `isobarColorDefault` KDoc

### Phase 4: Replace hardcoded `ComposeColor.White` in MapScreen.kt

8. [`MapScreen.kt`](../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt) — Replace ~28 `color = ComposeColor.White` with `color = ComposeColor(AppConfig.uiSettingsTextPrimary)` for settings labels, switch labels, tab text, back button tint, zone labels, boat length text
9. [`MapScreen.kt`](../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt) — Replace 11 `.background(ComposeColor.White.copy(alpha = 0.1f))` with `.background(ComposeColor(AppConfig.uiSettingsDivider))`
10. [`MapScreen.kt`](../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt) — Replace line 876 `ComposeColor.White` with `ComposeColor(AppConfig.uiSettingsToastText)`
11. [`MapScreen.kt`](../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt) — Replace line 1088 `ComposeColor.White` with `ComposeColor(AppConfig.uiErrorText)`

### Phase 5: FanLayout.kt

12. [`FanLayout.kt`](../app/src/main/java/ykws/android/maro/ui/map/FanLayout.kt) — Replace line 195 `ComposeColor.White` with `ButtonColors.icon`

### Phase 6 (optional): Cursor color

13. [`colors.properties`](../app/src/main/assets/colors.properties) — Add `ui.settings.input.cursor` token
14. [`AppConfig.kt`](../app/src/main/java/ykws/android/maro/config/AppConfig.kt) — Add field + init loading
15. [`MapScreen.kt`](../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt) — Replace line 3101 `cursorColor = ComposeColor.White` with new token

### Verification

16. Build: `gradlew assembleDebug`
17. Deploy: `adb install -r ...`
18. Visual check: settings panel text, dividers, error card, exit toast, zone icons, arc child count, isobath colors

## Files Touched

| File | Change Type |
|------|-------------|
| [`app/src/main/assets/colors.properties`](../app/src/main/assets/colors.properties) | Add 3 isobar tokens; optional: add cursor token |
| [`app/src/main/assets/zone.properties`](../app/src/main/assets/zone.properties) | Remove 4 orphaned property keys |
| [`app/src/main/java/ykws/android/maro/config/AppConfig.kt`](../app/src/main/java/ykws/android/maro/config/AppConfig.kt) | Sync 10 defaults; remove 4 orphaned fields + loading; add isobar loading |
| [`app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`](../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt) | Replace ~42 `ComposeColor.White` refs |
| [`app/src/main/java/ykws/android/maro/ui/map/FanLayout.kt`](../app/src/main/java/ykws/android/maro/ui/map/FanLayout.kt) | Replace 1 `ComposeColor.White` ref |
| [`app/src/main/java/ykws/android/maro/ui/map/FanIconComponents.kt`](../app/src/main/java/ykws/android/maro/ui/map/FanIconComponents.kt) | **No change** — structural icon whites kept |

## Risk Assessment

- **No visual regression risk** for Phase 4 items that map to `ui.settings.text.primary` — `#FFFFFFFF` === `ComposeColor.White`
- **Minor visual change** for dividers: 10% alpha → 7.8% alpha (barely perceptible)
- **Minor visual change** for error title: full white → 93% white (`#EE`)
- **Zero visual change** for Phase 1 — defaults only used as fallback
- **Zero visual change** for Phase 2 — fields are already dead code
- **Zero visual change** for Phase 3 — same values, just loaded from colors.properties
- **Build break risk**: Low — all replacements are simple token swaps of the same type (`Int` → `ComposeColor(Int)`)
