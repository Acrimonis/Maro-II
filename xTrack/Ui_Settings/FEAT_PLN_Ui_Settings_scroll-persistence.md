<!-- scope: feature -->

# Settings Scroll Persistence — Discussion

## Problem

The settings page is long (language, position source, display toggles, depth sliders, power/adaptive, advanced zone alerts, EMODnet cutoff, regenerate section). Users scroll down to tweak a setting (e.g., adaptive idle interval), dismiss the overlay, and when they reopen it the scroll position resets to the top. This is frustrating for iterative tuning.

**Current code:** [`MapScreen.kt:1174`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1174)

```kotlin
.verticalScroll(rememberScrollState())
```

`rememberScrollState()` is scoped to the `SettingsOverlay` composable. When `showSettings` becomes `false`, the composable leaves composition, the `remember` is dropped, and the scroll position is lost.

## Requirement

- **Within one app session:** scroll position survives overlay dismiss/reopen
- **App restart:** scroll resets to top (acceptable, no SharedPreferences persistence needed)

## Proposed Solution

Hoist the `ScrollState` out of `SettingsOverlay` into the parent scope where `showSettings` lives. Since `showSettings` is a `remember { mutableStateOf(false) }` at the `MapScreen` level (line 142), the `ScrollState` can live alongside it:

```kotlin
var showSettings by remember { mutableStateOf(false) }
val settingsScrollState = rememberScrollState()  // survives overlay dismiss
```

Then pass `settingsScrollState` into `SettingsOverlay`:

```kotlin
if (showSettings) {
    SettingsOverlay(
        scrollState = settingsScrollState,
        ...
    )
}
```

Inside `SettingsOverlay`, replace:

```kotlin
.verticalScroll(rememberScrollState())
```

with:

```kotlin
.verticalScroll(scrollState)
```

## Impact Analysis

| Aspect | Change |
|--------|--------|
| `SettingsOverlay` signature | Add `scrollState: ScrollState` parameter |
| Call site (`MapScreen` body) | Hoist `rememberScrollState()`, pass in |
| Other callers | None — `SettingsOverlay` is `private` in `MapScreen.kt` |
| Behavior on restart | `rememberScrollState()` starts at 0 → top of page (correct) |
| Behavior on dismiss/reopen | `ScrollState` lives in parent scope → position preserved (desired) |

## Alternatives Considered

1. **`rememberSaveable`** — survives process death but not needed; the user explicitly said restart→top is fine. Overkill.
2. **SharedPreferences** — persists across restarts; user doesn't want this.
3. **`LaunchedEffect` + `snapshotFlow`** — unnecessary complexity for a simple hoist.

## Files Affected

- [`app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt) — hoist scroll state + pass parameter + consume in `SettingsOverlay`
