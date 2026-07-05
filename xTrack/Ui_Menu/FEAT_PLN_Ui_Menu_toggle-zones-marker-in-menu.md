# Plan: Toggle Marker Zones Visibility in Menu Drawer

**Feature:** Ui_Menu → `toggle-zones-marker-in-menu`
**Branch:** `feature/toggle-zones-marker-in-menu`
**Created:** 2026-07-05

## Scope

Add a "Show zones" toggle (`Switch`) to the MARKERS card in `MenuDrawerOverlay`, mirroring the POSITION SOURCE GPS toggle pattern. The toggle controls `AppSettings.markerZonesVisible` — same state already toggled in the Settings page and consumed by `MarkerOverlay`.

## Design

```
┌──────────────────────────────────────┐
│  Show Zones on Map             [🔘]  │  ← NEW: label + Switch row
│  ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ │
│  Manage Markers                  >   │  ← existing
└──────────────────────────────────────┘
```

Two rows inside the same `Column` card, separated by `HorizontalDivider`. Matches TRACKS card divider pattern (Manage Tracks / live stats).

## Data Flow

```
MapScreen (owns appSettings.markerZonesVisible, onUpdateSettings)
  └─ OverlayLayer (new params: markerZonesVisible: Boolean, onToggleMarkerZones: () -> Unit)
       └─ MenuDrawerOverlay (new params: markerZonesVisible: Boolean, onToggleMarkerZones: () -> Unit)
            └─ Switch row inside MARKERS card
```

No new ViewModel state. `markerZonesVisible` already exists in `AppSettings` → `SettingsManager` → SharedPreferences. The `onUpdateSettings { it.copy(markerZonesVisible = ...) }` lambda is already available in `MapScreen`.

## Files Changed

| File | Change |
|------|--------|
| [`MenuDrawerOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt) | Add `markerZonesVisible` + `onToggleMarkerZones` params; insert Switch row + divider in MARKERS card |
| [`OverlayLayer.kt`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt) | Add `markerZonesVisible` + `onToggleMarkerZones` params; thread to `MenuDrawerOverlay` call |
| [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt) | Pass `appSettings.markerZonesVisible` + toggle lambda to `OverlayLayer` |

## Step-by-Step

### 1. `MenuDrawerOverlay.kt` — Add params + Switch row

**New parameters:**
```kotlin
markerZonesVisible: Boolean = true,
onToggleMarkerZones: () -> Unit = {},
```

**In MARKERS card** (after line 316, before closing `}` of the card `Column`):

Insert divider + new row:
```kotlin
Spacer(Modifier.height(6.dp))
HorizontalDivider(thickness = 0.5.dp, color = Color(AppConfig.uiSettingsDivider))
Spacer(Modifier.height(6.dp))
Row(
    modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 48.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
) {
    Text(
        text = "Show Zones on Map",
        color = Color(AppConfig.uiSettingsTextPrimary),
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium
    )
    Switch(
        checked = markerZonesVisible,
        onCheckedChange = { onToggleMarkerZones() },
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color(AppConfig.uiSettingsAccent),
            checkedTrackColor = Color(AppConfig.uiSettingsAccent).copy(alpha = 0.4f),
            uncheckedThumbColor = Color(AppConfig.uiSettingsTextMuted),
            uncheckedTrackColor = Color(AppConfig.uiSettingsSwitchTrackInactive)
        )
    )
}
```

### 2. `OverlayLayer.kt` — Thread new params

**New parameters** (in the Menu drawer data section, ~line 84):
```kotlin
markerZonesVisible: Boolean = true,
onToggleMarkerZones: () -> Unit = {},
```

**At `MenuDrawerOverlay` call site** (~line 221), pass through:
```kotlin
markerZonesVisible = markerZonesVisible,
onToggleMarkerZones = onToggleMarkerZones,
```

### 3. `MapScreen.kt` — Wire state + callback

**At `OverlayLayer` call site** (search for `OverlayLayer(`), add:
```kotlin
markerZonesVisible = appSettings.markerZonesVisible,
onToggleMarkerZones = {
    onUpdateSettings { it.copy(markerZonesVisible = !appSettings.markerZonesVisible) }
},
```

### 4. BUILD SUCCESSFUL

## Switch Colors

Match the Settings page "Zone shapes" switch at [`MapScreen.kt:3327`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3327):

| Token | Value |
|-------|-------|
| `checkedThumbColor` | `uiSettingsAccent` |
| `checkedTrackColor` | `uiSettingsAccent.copy(alpha = 0.4f)` |
| `uncheckedThumbColor` | `uiSettingsTextMuted` |
| `uncheckedTrackColor` | `uiSettingsSwitchTrackInactive` |

## Edge Cases

| Case | Behavior |
|------|----------|
| Switch toggled OFF while creating/editing a marker | Zone preview still renders — `MarkerOverlay` gates only *confirmed* markers; unconfirmed always show full geometry (line 172-173) |
| Settings "Zone shapes" toggle | Both controls read/write same `markerZonesVisible` in `SettingsManager` — always in sync |
| Menu drawer closed while creating marker | No impact — toggle only affects confirmed marker rendering |
