# 300m Zone Speed Limit Badge — Design Discussion

## Overview

Add a dedicated speed limit badge at the bottom-left of the map when the boat is inside the 300m regulatory band (`inZone300 == true`). When shown, the existing regulated zone speed limit icons are suppressed (the 300m zone supersedes them), but **other** regulated zone categories (anchoring, diving, environmental, etc.) continue to render.

## Current State

### Data Flow

```
CoastlineViewModel
  ├── inZone300: StateFlow<Boolean>         # true when boat inside 300m band
  ├── distanceToZone: StateFlow<Double?>     # signed distance (m), + outside, - inside
  └── zone300: StateFlow<Zone300Data?>       # precomputed 300m band geometry

AppSettings
  ├── zone300Visible: Boolean                # overlay layer toggle
  └── regulatedZonesVisible: Boolean         # regulated zones toggle

ZoneConfig
  └── zoneRegulatorySpeedKn: Float = 5f       # regulatory speed inside the band

RegulatedZoneWarningStrip                      # bottom-left icon stack
  └── uses geo-fence: it.contains(boatPosition)
  └── deduplicates by (displayCategory, speedLimitKn)
  └── renders SPEED_LIMIT as bold "5" text on colored bg
```

### Bottom-Left Layout (current)

```kotlin
Row(Alignment.BottomStart) {
    RegulatedZoneWarningStrip(...)         // vertical icon stack (44×44 dp each)
    if (regulationInfoVisible)
        RegulatedZoneInfoText(...)         // text beside icons
}
```

## Design

### New Composable: `Zone300SpeedBadge`

A standalone badge showing a red "5" icon + zone description text, placed in the same bottom-left `Row` as the regulated zone strip.

```
┌─────────────────────────────────────┐
│ ┌──────┐                            │
│ │  5   │ 300 m Zone                 │
│ │      │ Speed limit 5 kn           │
│ └──────┘                            │
└─────────────────────────────────────┘
```

- **Icon**: 44×44 dp rounded-square, red background (`0xFFE53935`), bold white "5" (28 sp), matching `RegulationZoneCategoryIcon` dimensions
- **Text**: Two lines — "300 m Zone" (bold) + "Speed limit 5 kn" (normal), white, 9 sp
- **Visibility**: `inZone300` (always show when boat is inside the band, regardless of layer visibility toggles)

### Icon Layout (revised)

```kotlin
Row(Alignment.BottomStart) {
    // New: 300m zone badge takes priority when inZone300
    if (inZone300 && (zone300Visible || regulatedZonesVisible))
        Zone300SpeedBadge(...)

    // Modified: regulated zone icons, excluding SPEED_LIMIT when in 300m zone
    RegulatedZoneWarningStrip(
        regulatedZones = visibleRegulatedZones,
        boatPosition = boatPosition,
        suppressSpeedLimit = inZone300,           // ← new parameter
    )

    if (regulationInfoVisible)
        RegulatedZoneInfoText(...)
}
```

### Changes Required

#### 1. MapScreen.kt — New composable `Zone300SpeedBadge`

- 44×44 dp red rounded-square `Box` with centered bold "5" text
- Beside it, a `Column` with two `Text` lines:
  - "300 m Zone" (bold, 9 sp)
  - "Speed limit {n} kn" (normal, 9 sp), using `ZoneConfig.zoneRegulatorySpeedKn`
- Both sit inside a `Row` with `Arrangement.spacedBy(4.dp)`, aligned `Bottom`

#### 2. MapScreen.kt — Pass `inZone300` to the bottom-left slot

- `MapContent` already receives `navigationState`, `boatPosition`, etc.
- Need to add `inZone300: Boolean` parameter to `MapContent`
- At the `MapScreen` level, `inZone300` is already collected: `val inZone300 by viewModel.inZone300.collectAsState()`
- Pass it through to `MapContent`

#### 3. MapScreen.kt — Modify bottom-left `Row` (lines 750-771)

- Show `Zone300SpeedBadge` when `inZone300 && (zone300Visible || regulatedZonesVisible)`
- Add `suppressSpeedLimit = inZone300` parameter to `RegulatedZoneWarningStrip`

#### 4. MapScreen.kt — `RegulatedZoneWarningStrip` signature

- Add `suppressSpeedLimit: Boolean = false` parameter
- When `true`, filter out `ZoneDisplayCategory.SPEED_LIMIT` entries from the deduplicated `categories` list

#### 5. MapScreen.kt — `RegulatedZoneInfoText` (optional)

- Consider also suppressing the SPEED_LIMIT info text line when `inZone300`
- Or keep it since the user might want to see the zone name info — but user request says "exclude this" so let's suppress it

### Data Dependencies

| Input | Source | Already available? |
|---|---|---|
| `inZone300` | `viewModel.inZone300` (StateFlow) | ✅ Already collected |
| `zone300Visible` | `appSettings.zone300Visible` | ✅ Already in `AppSettings` |
| `regulatedZonesVisible` | `appSettings.regulatedZonesVisible` | ✅ Already in `AppSettings` |
| `zoneRegulatorySpeedKn` | `ZoneConfig.zoneRegulatorySpeedKn` | ✅ Already loaded (default 5) |

### No New Resources Needed

- No new drawables or strings — the "5" is rendered as composable `Text`, the zone text is hardcoded strings (or can use existing string resources)
- No new properties — the speed limit comes from `ZoneConfig.zoneRegulatorySpeedKn`

### Visual Mockup

```
Current state (boat in regulated zone with 5kn speed limit):
┌──────────────────────────┐
│ ┌──────┐                 │
│ │  5   │ Zone name       │
│ │      │ — 5 nds         │
│ └──────┘                 │
└──────────────────────────┘

New state (boat in 300m zone):
┌─────────────────────────────┐
│ ┌──────┐                    │
│ │  5   │ 300 m Zone         │
│ │  🔴  │ Speed limit 5 kn   │
│ └──────┘                    │
└─────────────────────────────┘

New state (boat in 300m zone + other regulated zones):
┌─────────────────────────────────────┐
│ ┌──────┐ ┌──────┐                   │
│ │  🚫  │ │  5   │ 300 m Zone       │
│ │  ⚓   │ │  🔴  │ Speed limit 5 kn │
│ └──────┘ └──────┘                   │
└─────────────────────────────────────┘
   ↑ NO_ANCHOR   ↑ ZONE300 badge
```

### Edge Cases

1. **Boat in 300m zone AND a separate regulated speed limit zone (e.g., 10kn)**: The regulated 10kn icon should still show since it's a different (higher) speed limit from a different regulation. Only suppress `SPEED_LIMIT` categories when the speed matches the 300m zone's 5kn — or simply suppress ALL speed limits when in 300m zone since the 300m zone is the binding restriction.

2. **Boat enters 300m zone with zone300 layer hidden**: If `zone300Visible == false` but `regulatedZonesVisible == true`, show the badge (the band is still active even if the visual overlay is hidden). If both are hidden, don't show the badge.

3. **Boat exits 300m zone**: Badge disappears, regulated speed limit icons reappear if applicable.

4. **Regulated zones not loaded**: Badge should still show based solely on `inZone300` (which is computed from coastline distance, independent of regulated zones).

### Summary of File Changes

| File | Change |
|---|---|
| `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` | Add `Zone300SpeedBadge` composable |
| same file | Add `inZone300` param to `MapContent` |
| same file | Pass `inZone300` through from `MapScreen` → `MapContent` |
| same file | Modify bottom-left `Row` to render `Zone300SpeedBadge` when applicable |
| same file | Add `suppressSpeedLimit` param to `RegulatedZoneWarningStrip` |
| same file | Add filter logic in `RegulatedZoneWarningStrip` to exclude `SPEED_LIMIT` when suppressed |
