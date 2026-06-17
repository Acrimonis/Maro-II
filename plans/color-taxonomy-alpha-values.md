# Plan: Centralize Alpha/Opacity/Rendering Values in colors.properties

**Feature:** ColorManagement
**Branch:** `feature/more-colors`
**Status:** Plan

---

## Boundary Rule

| File | Purpose | Contents |
|------|---------|----------|
| **`colors.properties`** | **HOW things look** | Colors, opacity, alpha, stroke widths |
| **`zone.properties`** | **WHERE/WHEN things happen** | Distances (m), speeds (kn), thresholds |
| **`maro.properties`** | *(empty — deleted or removed)* | — |

---

## Current State

### zone.properties Remaining Contents

After previous cleanups (isobar colors already in colors.properties, orphaned keys removed):

| Key | Value | Nature | Consumer | Verdict |
|-----|-------|--------|----------|---------|
| `distanceToZoneGradientText` | 600 | **Spatial** — fade distance | **None** — dead code | 🔴 Remove |
| `distanceToZoneGradientColor` | 300 | **Spatial** — fade distance | **None** — dead code | 🔴 Remove |
| `distanceToZoneGradientTransp` | 33 | **Rendering** — transparency % | **None** — dead code | 🔴 Remove |
| `zoneAutoRevealDistanceM` | 100 | **Spatial** — GPS distance | CoastlineViewModel | 🔵 Stay |
| `zoneRegulatorySpeedKn` | 5 | **Spatial** — speed threshold | CoastlineViewModel, MapScreen | 🔵 Stay |
| `isobar.color.litto3d` | `${ui.dashboard.status.success}` | **Color** — already in colors.properties | ✅ Already moved |
| `isobar.color.emodnet` | `#00008B` | **Color** — already in colors.properties | ✅ Already moved |
| `isobar.color.default` | `#37474F` | **Color** — already in colors.properties | ✅ Already moved |
| `isobar.width.litto3d` | +1 | **Rendering** — stroke width | Loaded into AppConfig | 🟢 Move to colors.properties |
| `isobar.width.emodnet` | -1 | **Rendering** — stroke width | Loaded into AppConfig | 🟢 Move to colors.properties |
| `lowDepthWarningMinOpacityPct` | 25 | **Rendering** — opacity % | Dual-sourced with `overlay.lowDepth.minOpacity` | 🟢 Consolidate |

### maro.properties

Already cleaned of `cap.arrow.color` and `direction.line.color`. Remaining 3 alpha keys are dead code (never loaded):

| Key | Value | Nature | Consumer | Verdict |
|-----|-------|--------|----------|---------|
| `water.icon.bg.alpha` | 128 | **Rendering** — alpha | None — dead | 🔴 Remove (already done) |
| `gps.icon.bg.alpha` | 128 | **Rendering** — alpha | None — dead (runtime uses `statusGpsAlphaActive=0.75`) | 🔴 Remove (already done) |
| `gps.icon.dim.alpha` | 64 | **Rendering** — alpha | None — dead (runtime uses `statusGpsAlphaDimmed=0.50`) | 🔴 Remove (already done) |

---

## Final Implementation Steps

### Phase 1: Remove dead gradient fields from AppConfig.kt

1. Remove `distanceToZoneGradientText`, `distanceToZoneGradientColor`, `distanceToZoneGradientTransp` field declarations from [`AppConfig.kt`](../app/src/main/java/ykws/android/maro/config/AppConfig.kt) (lines 24-34)
2. Remove their `props.getProperty(...)` loading lines (lines 442-450)

### Phase 2: Add isobar stroke width tokens to colors.properties

3. Add to [`colors.properties`](../app/src/main/assets/colors.properties):
   ```properties
   # ── Isobath stroke width bonuses (px) ─────────────────
   # Extra px added on top of major/minor base per source. Range -4 to 6.
   map.isobar.litto3d.width=1
   map.isobar.emodnet.width=-1
   ```

### Phase 3: Add AppConfig loading for isobar width tokens

4. Add loading lines in [`AppConfig.kt`](../app/src/main/java/ykws/android/maro/config/AppConfig.kt) colors.properties section:
   ```kotlin
   props.getProperty("map.isobar.litto3d.width")?.toFloatOrNull()?.let { isobarWidthBonuses[DepthSource.LITTO3D] = it.coerceIn(-4f, 6f) }
   props.getProperty("map.isobar.emodnet.width")?.toFloatOrNull()?.let { isobarWidthBonuses[DepthSource.EMODNET] = it.coerceIn(-4f, 6f) }
   ```

### Phase 4: Consolidate lowDepthWarningMinOpacityPct dual-source

5. Remove `lowDepthWarningMinOpacityPct` loading line from zone.properties section of [`AppConfig.kt`](../app/src/main/java/ykws/android/maro/config/AppConfig.kt) (line 463-465)
   - The `SettingsManager` will continue to use its own default; the AppConfig field is no longer needed as a separate source

### Phase 5: Clean up zone.properties

6. Remove from [`zone.properties`](../app/src/main/assets/zone.properties):
   - `distanceToZoneGradientText`, `distanceToZoneGradientColor`, `distanceToZoneGradientTransp` (dead code)
   - `isobar.color.litto3d`, `isobar.color.emodnet`, `isobar.color.default` (already in colors.properties)
   - `isobar.width.litto3d`, `isobar.width.emodnet` (moved to colors.properties)
   - `lowDepthWarningMinOpacityPct` (consolidated with colors.properties)

### Phase 6: Verify

7. Build: `gradlew assembleDebug`

---

## Final File States

### zone.properties (after cleanup)

```properties
# ── Zone300 proximity auto-reveal ───────────────────────
zoneAutoRevealDistanceM=100
zoneRegulatorySpeedKn=5
```

### maro.properties (after cleanup)

Deleted or kept as empty placeholder.

---

## Files Touched

| File | Change |
|------|--------|
| [`colors.properties`](../app/src/main/assets/colors.properties) | Add `map.isobar.litto3d.width`, `map.isobar.emodnet.width` |
| [`AppConfig.kt`](../app/src/main/java/ykws/android/maro/config/AppConfig.kt) | Remove 3 dead gradient fields + loading; remove lowDepthWarningMinOpacityPct loading; add isobar width loading |
| [`zone.properties`](../app/src/main/assets/zone.properties) | Remove all rendering/color/dead keys — keep only `zoneAutoRevealDistanceM` and `zoneRegulatorySpeedKn` |
