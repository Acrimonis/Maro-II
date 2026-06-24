---
name: Ui_General
status: active
created: 2026-06-08 16:43
modified: 2026-06-24 09:13
active_subfeature: overlay styling
---

# Feature: Ui_General

**Description:**
App-lifecycle UX for the Maro-II app: intercept the system back action to guard
against accidental exits, and let the user keep the device screen awake while the
app is running. Extended with page-layout concerns: edge-to-edge rendering, status
bar immersion, and WindowInsets management.

## Subfeatures

### BackToExitConfirm  [x]

Intercept the in-app back action and require confirmation before exiting. On the
first back press, show a transient 2-second "Press back again to exit" toast; exit
(`finishAffinity`) only if back is pressed again within that window. The settings-
overlay back is unchanged (it just closes settings).

#### Todos
- [x] Intercept the back action while in the app (Compose `BackHandler`, enabled when no overlay).
- [x] On first back press, show a 2-second "Press back again to exit" toast.
- [x] Exit only when back is pressed again within the 2-second window; otherwise cancel.

#### Rules

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — exit-confirm `BackHandler`, `Context.findActivity()` helper, and the toast (rendered in `MapContent`'s bottom-overlay slot: bottom-centre, padded `end = 76dp` to clear the right-edge control stack; colour `0xFF16213E` = dashboard tile `cardBg`).
- `app/src/main/res/values/strings.xml`, `values-fr/strings.xml` — `exit_press_back_again`.

### KeepScreenOn  [x]

Add a user setting "Keep the phone on when app is running" that prevents the
screen from sleeping while the app is in the foreground.

#### Todos
- [x] Add a "Keep phone on" toggle to Settings (bottom of the Display section).
- [x] Keep the screen awake while the app runs when the setting is enabled.
- [x] Persist the setting alongside the existing app settings.

#### Rules

#### Key Files
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — `keepScreenOn` field/key/load/persist (SharedPreferences, mirrors `languageCode`).
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — `SettingsToggleRow` + `DisposableEffect` driving `LocalView.keepScreenOn`.

### page layout  [x]

Edge-to-edge rendering with `enableEdgeToEdge()`, immersive status bar (dark background, light icons), and proper WindowInsets consumption in MapScreen to fix portrait dashboard bottom clipping.

#### Todos
- [x] Add `enableEdgeToEdge()` in MainActivity.onCreate() before setContent
- [x] Add `WindowInsetsController` status bar appearance (light icons on dark background)
- [x] Add `WindowInsets.systemBars` padding in MapScreen root Box
- [x] Verify build — BUILD SUCCESSFUL

#### Rules
- `enableEdgeToEdge()` must be called before `setContent()` in Activity.onCreate()
- WindowInsets handling must not break existing landscape layout
- Status bar icons must be light (white) on the dark background

#### Key Files
- `app/src/main/java/ykws/android/maro/MainActivity.kt` — add `enableEdgeToEdge()`
- `app/src/main/res/values/themes.xml` — theme update
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — WindowInsets padding
- `gradle/libs.versions.toml` — check activity-ktx version

### immersive ui rework  [x]

Extend `enableEdgeToEdge()` to the nav bar: remove blanket `windowInsetsPadding(WindowInsets.systemBars)` from the root Box and apply targeted insets only to the overlay controls. The map fills the full screen behind both system bars.

#### Todos
- [x] Remove `windowInsetsPadding(WindowInsets.systemBars)` from the root `Box` in `MapScreen` — map draws full-screen behind both status bar and nav bar
- [x] Add `windowInsetsPadding(WindowInsets.statusBars)` + left padding to top-left icons Row (GPS + EarthWater) — removed redundant 6dp top padding
- [x] Add `windowInsetsPadding(WindowInsets.systemBars)` + horizontal padding to right-edge control stack Column — removed redundant top/bottom
- [x] Add `windowInsetsPadding(WindowInsets.navigationBars)` to bottom overlay areas (loading overlay, exit toast, zone info row)
- [x] Build & run — BUILD SUCCESSFUL, debounced on device (controls visible, map fills full screen)
- [-] DashboardPanel nav bar handling: decision — dashboard background (#16213E) matches window background, extends behind nav bar seamlessly; no extra padding needed
- [-] `portraitDashboardHeight` formula unchanged — dashboard extends behind nav bar without adjustment

#### Rules
- The map surface must fill the entire screen (behind both system bars)
- Overlay controls (GPS/EarthWater icons, Settings button, zoom, dashboard) must remain visible and not overlap with system bars
- `WindowInsets` consumption order: `windowInsetsPadding` first, then manual `padding`, so insets are consumed before extra spacing

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — root Box modifier, top-left icons Row, right-edge Column, DashboardPanel modifiers, bottom overlays
- `app/src/main/java/ykws/android/maro/MainActivity.kt` — no change needed (already calls `enableEdgeToEdge()`)

### ButtonColors  [x]
### ButtonColors  [ ]

Harmonise map control buttons (zoom +/- , settings gear, GPS/EarthWater toggles, dashboard tile accent strips) onto the dark dashboard theme using the centralized `colors.properties` palette. Replace hardcoded `0xFF...` literals with named colour tokens.

#### Todos
- [ ]

#### Rules

#### Key Files

### SVSpacing  [x]
### SVSpacing  [ ]

Adjust vertical spacing between buttons in the right-edge control stack for improved touch targeting and visual balance.

#### Todos

#### Rules

#### Key Files

### map-print-layout  [ ]

#### Todos

#### Rules

#### Key Files

### reg speed zone  [x]

**Focus:** Auto-show the regulated zone overlay (`regulatedZonesVisible`) when approaching a speed-enforced regulated zone — extending the same distance/time-threshold pattern used by the 300m band auto-show.

#### Todos
- [x] Add regulated zone overlay auto-show block in CoastlineViewModel onEach pipeline (uses `speedZoneQuery.distanceToBoundaryM` as trigger)
- [x] Add `regulatedZoneAutoShowGps`/`regulatedZoneAutoShowDemo` settings fields + persistence
- [x] Add settings UI toggles in Navigation tab
- [x] Wire `onToggleRegulatedZones` to `toggleRegulatedZonesVisibility()` for manual override tracking
- [x] Fix `armed` param: use settings toggle (`regAutoShowEnabled`) instead of `regulatedZoneManuallyHidden` — reveals on first approach
- [x] Fix `zoneAutoShowDecision()` reveal: add `insideZoneReveal` for speed zone config (catches enter-between-ticks)
- [x] Fix speed zone hide: add `exitedZone` (outside past revealDistM) and `locationUnknown` (dist=null on land) conditions
- [x] BUILD SUCCESSFUL — show+hide cycle verified on device

#### Rules
- Always-on auto-show when settings toggle is ON (no manual pre-hide needed)
- Hides when boat exits zone past revealDistM (100m default) or goes on land
- `regulationInfoVisible` (text panel) stays under manual control only

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt` — regulated zone auto-show block, `insideZoneReveal`/`exitedZone`/`locationUnknown` in `zoneAutoShowDecision()`
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — `regulatedZoneAutoShowGps`/`regulatedZoneAutoShowDemo` fields
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — settings toggles, `onToggleRegulatedZones` wiring
- `plans/speed-enforcement-zone-auto-show-plan.md` — design plan

### fan tweak  [x]

**Focus:** Remove the transparent scrim that intercepts taps when the fan is expanded. Instead, let the MapView itself detect the tap, dismiss the fan, and process the touch normally — so a single tap both closes the fan AND pans/zooms the map.

**Design:** The scrim (`Modifier.clickable`) at `MapContent` level consumed all touches before they reached the osmdroid `MapView`. By replacing it with an Android `View.OnTouchListener` set directly on the `MapView`, we can observe `ACTION_DOWN`, call `onDismissFan()`, and return `false` — the unconsumed event flows to `MapView.onTouchEvent` for normal pan/zoom. This is the only reliable pass-through path across the Compose→AndroidView boundary.

#### Todos
- [x] Remove scrim `Box(Modifier.fillMaxSize().clickable { onDismissFan() })` from `MapContent()` (line ~1138)
- [x] Add `expandedFanId: ControlId?` and `onDismissFan: () -> Unit` parameters to `CoastlineMapView()`
- [x] In `CoastlineMapView.update` block: `mapView.setOnTouchListener { _, event -> if (event.action == MotionEvent.ACTION_DOWN && expandedFanId != null) onDismissFan(); false }`
- [x] Thread `expandedFanId` and `onDismissFan` from `MapContent` call site through to `CoastlineMapView`
- [x] Build & verify: BUILD SUCCESSFUL

#### Implemented
- [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1129) — scrim removed (was lines 1132-1140), `CoastlineMapView` gains `expandedFanId`/`onDismissFan` params, `update` block sets `setOnTouchListener` gated on `ACTION_DOWN`, call site threads both params. 1 file, 4 touch points. Build ✅.

#### Rules
- `setOnTouchListener` returning `false` is mandatory — never consume, always let MapView handle
- Gate only on `ACTION_DOWN` to avoid calling `onDismissFan()` on every MOVE event
- Fan child buttons (arc buttons rendered by `FanLayout`) are Compose overlays on top of `AndroidView` — they consume their own taps first, so toggling a layer button still works without dismissing

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — `MapContent()` (remove scrim, thread params), `CoastlineMapView()` (add params, set touch listener)

### toast and progress dialog  [x]

**Focus:** The exit toast and loading/progress overlay should fill all available horizontal space in the bottom zone. Legacy padding values from before the 2-column Row refactor unnecessarily constrain their width.

**Root cause:** After the `Row(fillMaxSize)` 2-column layout was introduced, the LEFT COLUMN already fills only the space not consumed by the RIGHT COLUMN. But the bottom overlays still carry manual padding (`start=56dp, end=76dp`) from the pre-refactor era. Additionally, `LoadingOverlay` internally constrains to `.fillMaxWidth(0.66f)`.

#### Todos
- [x] Change both bottom Box padding: `start = 56.dp, end = 76.dp` → `start = 6.dp, end = 6.dp`
- [x] Change `LoadingOverlay` modifier: `.fillMaxWidth(0.66f)` → `.fillMaxWidth()`
- [x] Build & verify: BUILD SUCCESSFUL

#### Implemented
- [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1227) — 3 changes: loading/error Box padding 56→6dp, exit toast Box padding 56→6dp, LoadingOverlay fillMaxWidth(0.66f)→fillMaxWidth(). 1 file, 3 lines. Build ✅.

#### Rules
- `start = 6.dp` matches the regulated zone icon row's padding — consistent with the 2-column Row layout
- `end = 6.dp` provides minimal breathing room to the RIGHT COLUMN edge
- No overlap risk: the Row layout already separates left and right columns

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — both bottom Box padding + LoadingOverlay modifier

### overlay styling  [x]

**Focus:** Unify the three bottom overlays (exit toast, loading/progress, error) with a shared navy card design at 80% opacity, differentiated by border and text color.

**Design:**

| Overlay | Background | Border | Text |
|----------|-----------|--------|------|
| Toast | `#CC16213E` | `#1565C0` blue | White |
| Progress | `#CC16213E` | `#1565C0` blue | White (bar blue) |
| Error | `#CC16213E` | `#C62828` red | Title red, body white |

#### Todos
- [x] Toast: bg → `buttonActionBgColor`, add blue border, reduce padding to `h=16dp v=10dp`
- [x] LoadingOverlay: wrap in Surface (bg `buttonActionBgColor`, blue border, `h=16dp v=10dp`), text white
- [x] ErrorOverlay: bg → `buttonActionBgColor`, add red border, title red, body white, padding match
- [x] Build & verify: BUILD SUCCESSFUL

#### Implemented
- [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1254) — 3 composables unified: toast Surface (bg + border + padding), LoadingOverlay (Surface wrapper + white text), ErrorOverlay (Surface + red border + red title). 1 file. Build ✅.

#### Rules
- Background `#CC16213E` = `buttonActionBgColor` — navy at 80% opacity, consistent across all three
- Border colors from existing tokens: blue = `uiSettingsAccent` (`#1565C0`), red = `uiDashboardZoneDanger` (`#C62828`)
- Progress bar fill and spinner keep blue (`uiProgressAccent`) — functional indicators

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — toast Surface, LoadingOverlay, ErrorOverlay

## Todos
- [ ] Rename "Track List..." → "Manage Tracks..." in TrackDrawerOverlay.kt:212
- [ ] Add point count (`TrackSummary.pointCount`) and display "xxx pts" left of pin/share icons in TrackHistoryOverlay track cards

## Rules

## Key Files

## Docs
- `xTrack/Ui_General/FEAT_PLN_Ui_General_portrait-bottom-space.md` — analysis of portrait bottom space and status bar immersion
- `plans/btn-color-harmonization.md` — button color harmonization: match map control buttons to dashboard dark theme
- `docs/color-scheme.md` — canonical reference for all colour tokens in the app
- `plans/color-props-migration-plan.md` — migration plan: all colours → colors.properties
- `plans/button-colors-discussion.md` — discussion: UI round button color identification and exploration
- `plans/right-edge-controls-gap-asymmetry-analysis.md` — root cause analysis of asymmetric gap between right-edge controls and map edges after immersive rework
- `plans/map-overlay-layout-rationalization.md` — complete layout refactor: 2-column Row structure, symmetric 6dp margins, orientation-aware insets
- `plans/map-overlay-layout-inventory.md` — current overlay inventory and planned evolution audit
- `docs/material-icons-standalone-guide.md` — How to add Material Symbols icons as standalone ImageVector .kt files
