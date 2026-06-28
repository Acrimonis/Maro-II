# FEAT_PLN: Add Pinned Track Transparency RangeSlider to Settings

**Feature:** BoatTrace
**Subfeature:** track-settings
**Date:** 2026-06-28
**Branch:** feature/track-idling
**Status:** plan

## Problem

Pinned track transparency exists in backend (`trackingTransparencyPinnedNewest/Oldest` in SettingsManager + BuildConfig) and is correctly passed through to rendering, but the settings UI at [`MapScreen.kt:2783-2820`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2783) only has one Transparency RangeSlider — it controls history (non-pinned) tracks only. The pinned transparency values are stuck at defaults (0%/20%).

## Root Cause

The "Track settings" expander was written with a single "Transparency" section. The pinned transparency controls were deferred — the description text says "Pinned: reserved for future use" at [line 2832](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2832).

## Fix Plan

### Step 1 — Add pinned transparency RangeSlider to MapScreen

In `GeneralSettings()` → Tracks expander → after the existing history transparency RangeSlider (line 2820), add:

```kotlin
Spacer(Modifier.height(6.dp))
Box(
    modifier = Modifier
        .fillMaxWidth()
        .height(1.dp)
        .background(ComposeColor(AppConfig.uiSettingsDivider))
)
Spacer(Modifier.height(6.dp))

// Pinned tracks transparency
Text(
    text = "Pinned transparency",
    color = ComposeColor(AppConfig.uiSettingsTextPrimary),
    fontSize = 14.sp,
    fontWeight = FontWeight.Medium
)
Text(
    text = "Pinned tracks are always visible regardless of count. 0% = opaque, 100% = invisible.",
    color = ComposeColor(AppConfig.uiSettingsTextMuted),
    fontSize = 12.sp
)
Text(
    text = "Newest %d%%  –  Oldest %d%%".format(
        settings.trackingTransparencyPinnedNewest,
        settings.trackingTransparencyPinnedOldest
    ),
    color = ComposeColor(AppConfig.uiSettingsAccent),
    fontSize = 14.sp,
    fontWeight = FontWeight.Bold,
    textAlign = TextAlign.End,
    modifier = Modifier.fillMaxWidth()
)
RangeSlider(
    value = settings.trackingTransparencyPinnedNewest.toFloat()
        ..settings.trackingTransparencyPinnedOldest.toFloat(),
    onValueChange = { range: ClosedFloatingPointRange<Float> ->
        onUpdateSettings {
            it.copy(
                trackingTransparencyPinnedNewest = range.start.roundToInt(),
                trackingTransparencyPinnedOldest = range.endInclusive.roundToInt()
            )
        }
    },
    valueRange = 0f..100f,
    steps = 19,
    colors = SliderDefaults.colors(
        thumbColor = ComposeColor(AppConfig.uiSettingsAccent),
        activeTrackColor = ComposeColor(AppConfig.uiSettingsAccent),
        inactiveTrackColor = ComposeColor(AppConfig.uiSettingsSwitchTrackInactive)
    )
)
```

### Step 2 — Update the description text

Change the "Colors" section description at [line 2832](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2832) from:
```
"Past tracks: color gradient from newest (From) to oldest (To). Pinned: reserved for future use."
```
To:
```
"Past tracks: color gradient from newest (From) to oldest (To). Pinned tracks: amber/orange gradient."
```

## Files Touched
| File | Change |
|------|--------|
| `app/.../ui/map/MapScreen.kt` | Add pinned transparency RangeSlider + update description text |
