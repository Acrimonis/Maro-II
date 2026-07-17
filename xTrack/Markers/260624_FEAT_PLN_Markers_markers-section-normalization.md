# Normalize MARKERS Section in TrackDrawerOverlay

**Per [`docs/ui-drawer-guidelines.md`](../docs/ui-drawer-guidelines.md) and [`docs/ui-component-guidelines.md`](../docs/ui-component-guidelines.md).**

## Problem

The MARKERS section in [`TrackDrawerOverlay.kt`](../app/src/main/java/ykws/android/maro/ui/map/TrackDrawerOverlay.kt:267) doesn't follow the drawer card pattern used by POSITION SOURCE and TRACK RECORDING:
- Bare `Row` instead of a card wrapper (`uiCardBackground`, 12dp radius, 16×10dp pad)
- `HorizontalDivider` before section header (other sections don't have dividers)
- Missing trailing `KeyboardArrowRight` chevron on the navigation row
- Missing visibility toggles for markers and marker zones (settings exist but not surfaced)

## Changes

### 1. `TrackDrawerOverlay.kt` — Rebuild MARKERS section

Replace lines 267–292 with a card following the drawer guidelines:

```kotlin
// ── MARKERS section ─────────────────────────────
Text(
    text = "MARKERS",
    color = Color(AppConfig.uiSettingsAccent),
    fontSize = 17.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = 1.sp
)

Spacer(Modifier.height(8.dp))

Column(
    modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .background(Color(AppConfig.uiCardBackground))
        .padding(horizontal = 16.dp, vertical = 10.dp)
) {
    // Show on map toggle
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Show on map",
            color = Color(AppConfig.uiSettingsTextPrimary),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
        Switch(
            checked = userMarkersVisible,
            onCheckedChange = onToggleUserMarkers,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(AppConfig.uiSettingsAccent),
                checkedTrackColor = Color(AppConfig.uiSettingsAccent).copy(alpha = 0.4f),
                uncheckedThumbColor = Color(AppConfig.uiSettingsTextMuted),
                uncheckedTrackColor = Color(AppConfig.uiSettingsSwitchTrackInactive)
            )
        )
    }

    // Show zones toggle
    HorizontalDivider(thickness = 0.5.dp, color = Color(AppConfig.uiSettingsDivider))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Show zones",
            color = Color(AppConfig.uiSettingsTextPrimary),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
        Switch(
            checked = markerZonesVisible,
            onCheckedChange = onToggleMarkerZones,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(AppConfig.uiSettingsAccent),
                checkedTrackColor = Color(AppConfig.uiSettingsAccent).copy(alpha = 0.4f),
                uncheckedThumbColor = Color(AppConfig.uiSettingsTextMuted),
                uncheckedTrackColor = Color(AppConfig.uiSettingsSwitchTrackInactive)
            )
        )
    }

    // Manage Markers nav row
    HorizontalDivider(thickness = 0.5.dp, color = Color(AppConfig.uiSettingsDivider))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onManageMarkers),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Manage Markers...",
            color = Color(AppConfig.uiSettingsTextPrimary),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Manage markers",
            tint = Color(AppConfig.uiSettingsTextMuted),
            modifier = Modifier.size(20.dp)
        )
    }
}
```

### 2. `TrackDrawerOverlay.kt` — Add new parameters

Add to the function signature (before `onManageMarkers`):

```kotlin
    userMarkersVisible: Boolean,
    onToggleUserMarkers: (Boolean) -> Unit,
    markerZonesVisible: Boolean,
    onToggleMarkerZones: (Boolean) -> Unit,
```

### 3. `MapScreen.kt` — Wire new callbacks

At the `TrackDrawerOverlay` invocation site (line ~1083), add:

```kotlin
    userMarkersVisible = appSettings.userMarkersVisible,
    onToggleUserMarkers = { viewModel.updateSettings { it.copy(userMarkersVisible = it) } },
    markerZonesVisible = appSettings.markerZonesVisible,
    onToggleMarkerZones = { viewModel.updateSettings { it.copy(markerZonesVisible = it) } },
```

Check if `viewModel` (CoastlineViewModel) has `updateSettings` — if not, we may need a lambda directly.

### 4. Check `updateSettings` availability

`CoastlineViewModel` should expose `updateSettings: ((AppSettings) -> AppSettings) -> Unit`. If not, pass a lambda from `MapScreen`.

## Files to touch

| # | File | Change |
|---|------|--------|
| 1 | `TrackDrawerOverlay.kt` | Rebuild MARKERS section, add 4 new params |
| 2 | `MapScreen.kt` | Wire new params at invocation site |
