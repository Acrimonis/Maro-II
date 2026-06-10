<!-- scope: feature -->

# Settings Tab Organization — Implementation Plan

## Context

Current [`SettingsOverlay`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1246) is a single vertically-scrolling page with 5 sections and ~24 settings:

| # | Section | Items |
|---|---------|-------|
| 1 | Language | Language selector (system/en/fr) |
| 2 | Position Source | GPS mode toggle, recenter delay slider |
| 3 | Display | Coastline toggle, Z300 toggle, low-depth warning toggle + threshold slider + opacity slider, heading line toggle, cap arrow toggle, EMODnet shallow cutoff slider |
| 4 | Power Saving | Keep screen on toggle, GPS frequency preset (3 options), FPS slider, idle interval slider, adaptive window slider (advanced, collapsible), adaptive distance slider (advanced) |
| 5 | Advanced | Z300 auto-show GPS toggle, Z300 auto-show demo toggle, alert distance slider, alert delay slider |

**Decision:** Material 3 Tabs (`TabRow` + `HorizontalPager`) — confirmed by user.

## Architecture

### Tab grouping

| Tab | Label | Icon | Content |
|-----|-------|------|---------|
| 0 | **Display** | `Icons.Default.Visibility` | Coastline, Z300, low-depth warning + threshold + opacity, heading line, cap arrow, EMODnet cutoff |
| 1 | **Navigation** | `Icons.Default.Navigation` | GPS mode, recenter delay, GPS frequency presets, idle interval, adaptive window, adaptive distance |
| 2 | **System** | `Icons.Default.Settings` (or `Build`/`Tune`) | Language, keep screen on, FPS slider, regenerate checkboxes + button, Z300 auto-show toggles + distance/time |

### Layout structure

```
┌─────────────────────────────┐
│ ← Settings                 │  ← Header (unchanged)
├─────────────────────────────┤
│ [Display] [Navigation] [System] │  ← TabRow
├─────────────────────────────┤
│                             │
│   (tab content, scrollable) │  ← HorizontalPager page
│                             │
└─────────────────────────────┘
```

The header row (back button + "Settings" title) stays pinned above the tabs — always visible.

### State persistence requirements

| State | Mechanism | Scope |
|-------|-----------|-------|
| Settings values | `SettingsManager` via `SharedPreferences` | **Already implemented** — no change |
| Tab selection | `rememberSaveable { mutableIntStateOf(0) }` | Survives overlay dismiss/reopen, survives config change |
| Scroll state per tab | 3x hoisted `ScrollState` at `MapScreen` level (alongside existing `settingsScrollOffset`) | Session-only, resets on app restart |

The existing session scroll persistence (hoisted [`ScrollState`](plans/settings-scroll-persistence.md)) is extended per-tab: instead of one scroll state, hold 3 — one per tab, hoisted to [`MapScreen`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:159) alongside `showSettings`.

## Implementation steps

### Step 1: Extract 3 tab content composables from `SettingsOverlay`

The current [`SettingsOverlay`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1246) body has inline settings grouped with `SectionHeader` calls. Extract into 3 `@Composable private fun` functions:

```kotlin
@Composable
private fun DisplaySettings(
    settings: AppSettings,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit
) {
    // Existing Display section code: coastline, Z300, low-depth warning,
    // heading line, cap arrow toggles + depth threshold/opacity sliders + EMODnet cutoff
}

@Composable
private fun NavigationSettings(
    settings: AppSettings,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onGpsModeChange: (Boolean) -> Unit
) {
    // Existing Position Source + Power Saving (GPS frequency, idle, adaptive) code
    // GPS mode toggle, recenter delay, GPS frequency presets, FPS slider,
    // idle interval, adaptive window + distance
}

@Composable
private fun SystemSettings(
    settings: AppSettings,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onRegenerateRasters: (List<RasterCache.Step>) -> Unit
) {
    // Language, keep screen on, regenerate checkboxes + button,
    // Z300 auto-show toggles + distance/time
}
```

Each composable gets its own `Column` with `.verticalScroll(scrollState)` — the scroll state is passed in from the parent.

### Step 2: Add `TabRow` + `HorizontalPager` to the overlay

Replace the single scroll column with:

```kotlin
@Composable
private fun SettingsOverlay(
    settings: AppSettings,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onGpsModeChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onRegenerateRasters: (List<RasterCache.Step>) -> Unit = {},
    // Scroll states for each tab (hoisted from parent)
    displayScrollState: ScrollState,
    navigationScrollState: ScrollState,
    systemScrollState: ScrollState,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ComposeColor(0xFF1A1A2E))
    ) {
        // ── Header row (unchanged from current code) ──
        Row(...) { /* back button + title */ }
        Spacer(Modifier.height(16.dp))

        // ── Tab bar ──
        TabRow(selectedTabIndex = selectedTab) {
            /* Display / Navigation / System tabs */
        }

        // ── Tab content ──
        HorizontalPager(
            pageCount = 3,
            state = rememberPagerState(pageCount = { 3 })
        ) { page ->
            when (page) {
                0 -> DisplaySettings(settings, onUpdateSettings, displayScrollState)
                1 -> NavigationSettings(settings, onUpdateSettings, onGpsModeChange, navigationScrollState)
                2 -> SystemSettings(settings, onUpdateSettings, onRegenerateRasters, systemScrollState)
            }
        }
    }
}
```

### Step 3: Hoist 3 scroll states to `MapScreen`

At [`MapScreen`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:159), alongside existing `showSettings` and `settingsScrollOffset`:

```kotlin
var showSettings by remember { mutableStateOf(false) }
// Replace single settingsScrollOffset with per-tab scroll states:
val displayScrollState = rememberScrollState()
val navigationScrollState = rememberScrollState()
val systemScrollState = rememberScrollState()
```

Pass to `SettingsOverlay`:
```kotlin
SettingsOverlay(
    settings = appSettings,
    onUpdateSettings = viewModel::updateSettings,
    onGpsModeChange = onGpsModeChange,
    onDismiss = { showSettings = false },
    displayScrollState = displayScrollState,
    navigationScrollState = navigationScrollState,
    systemScrollState = systemScrollState,
    onRegenerateRasters = { steps -> ... }
)
```

Remove the old `initialScrollOffset` / `onScrollChanged` parameters — no longer needed since scroll states are now direct references from the parent.

### Step 4: Update string resources (optional)

The current `settings_section_*` string resources for section headers can stay, but you may want shorter tab labels. Consider adding:

```xml
<string name="settings_tab_display">Display</string>
<string name="settings_tab_navigation">Navigation</string>
<string name="settings_tab_system">System</string>
```

### No changes needed

| Area | Status |
|------|--------|
| `SettingsManager.kt` | Unchanged — SharedPreferences persistence is intact |
| `AppSettings` data class | Unchanged |
| `CoastlineViewModel.kt` | Unchanged — `updateSettings` lambda works the same |
| `DepthViewModel.kt` | Unchanged |
| Strings | Existing section header strings are reused inside each tab; only tab labels are new |
| Scroll persistence | Replaces the single hoisted state with 3 per-tab states — same session-only semantics |
