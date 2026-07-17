# Marker Settings Rework — Plan

**Created:** 2026-07-03 10:30 UTC
**Branch:** feature/markers-settings
**Feature:** Markers / subfeature: setting-markers
**Ref:** UI guidelines at `docs/ui-component-guidelines.md`

## Goal

Rework the marker section of the settings page to match the grouped-card pattern used by other layers (Low-depth warning, Regulated zones). Move "Show zone shapes" under a collapsible expander. Clean up `SHOW_PINNED` dead code.

## Current State

```
[Standalone toggle] "Marker zones" — Show zone shapes and proximity previews
```

This is a bare `SettingsToggleRow` at line 2807-2815. No grouping, no sub-settings, isolated from the marker layer toggle (fan button).

## Target State

```
┌─ Markers ──────────────────────────────────────┐
│  Show markers on map                    [Switch]│  ← mirrors fan button (markerLayerState)
│                                                  │
│  ▼ Appearance                                   │  ← SettingsExpander (visible when ON)
│  ┌──────────────────────────────────────────┐   │
│  │  Show zone shapes                 [Switch]│   │  ← markerZonesVisible
│  │  (corridor edges, circle outlines,       │   │
│  │   proximity previews)                    │   │
│  └──────────────────────────────────────────┘   │
└──────────────────────────────────────────────────┘
```

Follows the exact same grouped-card + inline-toggle + SettingsExpander pattern as Low-depth warning (lines 2705-2791) and Regulated zones (lines 2819-2890).

## Implementation (3 files)

### 1. MapScreen.kt — Replace standalone toggle with grouped card

**Remove** lines 2805-2815 (standalone `SettingsToggleRow` + spacer).

**Insert** before the existing "Regulated zones" card (line 2819):

```kotlin
// ── Markers overlay toggle — grouped card ───────────────────
Column(
    modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .background(ComposeColor(AppConfig.uiCardBackground))
        .padding(vertical = 8.dp)
) {
    // Inline toggle row
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Markers",
                color = ComposeColor(AppConfig.uiSettingsTextPrimary),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "User-created pins, circles, corridors and auto-markers",
                color = ComposeColor(AppConfig.uiSettingsTextMuted),
                fontSize = 13.sp
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Switch(
            checked = markerLayerState != MarkerLayerState.HIDDEN,
            onCheckedChange = { visible ->
                if (visible) markersViewModel.showLayer()
                else markersViewModel.toggleMarkerLayer()
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = ComposeColor(AppConfig.uiSettingsAccent),
                checkedTrackColor = ComposeColor(AppConfig.uiSettingsAccent).copy(alpha = 0.4f),
                uncheckedThumbColor = ComposeColor(AppConfig.uiSettingsTextMuted),
                uncheckedTrackColor = ComposeColor(AppConfig.uiSettingsSwitchTrackInactive)
            )
        )
    }

    // Appearance settings — collapsible, only visible when layer is ON
    if (markerLayerState != MarkerLayerState.HIDDEN) {
        Spacer(Modifier.height(8.dp))
        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
            var markerExpanded by remember { mutableStateOf(false) }
            SettingsExpander(
                label = "Appearance",
                expanded = markerExpanded,
                onToggle = { markerExpanded = !markerExpanded }
            ) {
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ComposeColor(0x0DFFFFFF))
                        .border(1.dp, ComposeColor(0x40FFFFFF), RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "Show zone shapes (corridor edges, circle outlines) and proximity previews",
                        color = ComposeColor(AppConfig.uiDashboardTextMuted),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Zone shapes",
                            color = ComposeColor(AppConfig.uiSettingsTextPrimary),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Switch(
                            checked = settings.markerZonesVisible,
                            onCheckedChange = { visible ->
                                onUpdateSettings { it.copy(markerZonesVisible = visible) }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = ComposeColor(AppConfig.uiSettingsAccent),
                                checkedTrackColor = ComposeColor(AppConfig.uiSettingsAccent).copy(alpha = 0.4f),
                                uncheckedThumbColor = ComposeColor(AppConfig.uiSettingsTextMuted),
                                uncheckedTrackColor = ComposeColor(AppConfig.uiSettingsSwitchTrackInactive)
                            )
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

Spacer(modifier = Modifier.height(12.dp))
```

### 2. MarkerLayerState enum — Remove SHOW_PINNED

In `MarkersViewModel.kt:57`:
```kotlin
// Before:
enum class MarkerLayerState { HIDDEN, SHOW_ALL, SHOW_PINNED }
// After:
enum class MarkerLayerState { HIDDEN, SHOW_ALL }
```

### 3. MarkerOverlay.kt — Remove SHOW_PINNED gate

Lines 166-170, simplify `drawGeometry`:
```kotlin
// Before:
val drawGeometry = !confirmed
    || markerLayerState != MarkerLayerState.SHOW_PINNED
    || marker.id == selectedMarkerId
// After:
val drawGeometry = true  // always render in SHOW_ALL (only state besides HIDDEN)
```

Also remove the `// In SHOW_PINNED, zones only render for the selected marker.` comment at line 174.

### 4. SettingsManager — Add migration

Add a migration to force `SHOW_PINNED` → `SHOW_ALL` for devices that still have it persisted:
```kotlin
if (savedVersion < 5) {
    val oldState = prefs.getString(KEY_MARKER_LAYER_STATE, "SHOW_ALL")
    if (oldState == "SHOW_PINNED") {
        editor.putString(KEY_MARKER_LAYER_STATE, "SHOW_ALL")
    }
}
```

Bump `CURRENT_VERSION` to 5.

## Key Files

- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — grouped card UI
- `app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt` — MarkerLayerState enum
- `app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt` — drawGeometry simplification
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — migration, version bump
