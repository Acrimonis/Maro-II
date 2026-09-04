---
name: Ui_General
status: active
created: 2026-06-08 16:43
modified: 2026-09-04 20:10
---

# Feature: Ui_General

**Description:**
App-lifecycle UX for the Maro-II app: intercept the system back action to guard
against accidental exits, and let the user keep the device screen awake while the
app is running. Extended with page-layout concerns: edge-to-edge rendering, status
bar immersion, and WindowInsets management.

## Sections

### compact-list-cards

Track and marker list cards made vertically more compact. Description/comment
line-height 14sp (title 16sp, header 12sp, stats 12/13sp), reduced description v-padding,
asymmetric column padding (top 2dp / bottom 4dp), and the header→title `HorizontalDivider`
removed. Shared by the list overlays and the dashboard detail drawers. BUILD SUCCESSFUL.

#### Key Files
- `TrackHistoryOverlay.kt`, `MarkerManagementOverlay.kt`

### drawer-dynamic-height

Track + marker drawers pin Prev/Next at the bottom via the `DrawerScaffold.footer` slot, with
card→Prev/Next spacers trimmed and 12dp horizontal button padding (no divider, 8dp above /
6dp below). Portrait drawers are bottom-anchored and resize dynamically: fixed animated height
driven by a hidden card-height probe (`MeasureHeight`), min = dashboard height. The landscape
side panel pins the footer too. BUILD SUCCESSFUL.

#### Key Files
- `DrawerScaffold.kt`, `MeasureHeight.kt`, `OverlayLayer.kt`, `MarkerDrawer.kt`

### drawer-vertical-rhythm

Uniform drawer vertical rhythm: 12dp card padding (start/top/end) across track + marker
drawers; header vertical padding 12dp; resize formulas kept in lockstep (header +
contentPadding.top + card + footer + 4dp safety); footer 10dp spacers + 10dp Prev/Next gap
(footer height 60dp). Card stays bottom-anchored (no scroll risk). BUILD SUCCESSFUL.

#### Key Files
- `DrawerScaffold.kt`, `OverlayLayer.kt`, `MarkerDrawer.kt`

### screen-lock

Touch-input lock (splash guard): a 📵 toggle in the top-left status row (right of the
Earth/Water icon) locks the screen so the map ignores all touch except the unlock toggle
and the zoom +/− buttons (double-tap only while locked — single splash taps ignored).
Full-screen consume-all `LockScrim`; top-most duplicate unlock button + `ZoomControls` +
`LockBanner` (exit-toast style, 2s auto-dismiss); locked state colour = `semantic.info`
(blue). `status.lock.*` tokens in `colors.properties` + `AppConfig`. BUILD SUCCESSFUL.

#### Key Files
- `MapScreen.kt`, `MapControlButton.kt`, `colors.properties`, `AppConfig.kt`, `strings.xml` (EN+FR)

#### Docs
- `xTrack/Ui_General/260827_FEAT_PLN_Ui_General_touch-input-lock.md`
- `docs/ui-component-guidelines.md` (§5.5)

---

### menu-drawer-rows

Menu drawer rows renamed "Manage Tracks"→"Tracks" / "Manage Markers"→"Markers"
(EN+FR, no ellipsis). Row tap opens the list; the trailing chevron is now its own
IconButton that opens the detail drawer on the first item of the current
filtered/sorted list (track: first non-live + non-pending-delete; marker: first of
`markers`), disabled when there is no first item. BUILD SUCCESSFUL.

#### Key Files
- `MenuDrawerOverlay.kt`, `OverlayLayer.kt`, `MapScreen.kt`, `strings.xml` (EN+FR)

#### Docs
- `xTrack/Ui_General/260816_FEAT_PLN_Ui_General_menu-drawer-rows.md`

---

### delete-advance-next

Drawer delete now advances to the adjacent item (next → previous → close) instead of
just closing, with a vertical snackbar stack (cap 3 visible, FIFO overflow) for undo.
All snackbars share the stack: track delete, marker delete, and marker-created undo.
`deleteMarker` gained a `closeDrawer` flag so the advanced drawer stays open on timeout.
Snackbar stack sits at the bottom of the map area, never over the dashboard. BUILD SUCCESSFUL.

Hardening: per-snackbar unique uid key (fixes duplicate-key crash on re-delete); track
navigation (delete advance, Prev, Next) skips empty-point tracks via `openFirstValidTrack`
(fixes `NoSuchElementException` crash).

UI polish: snackbar stack + bottom overlays left-aligned and clear of the right-edge
controls (`RIGHT_CONTROL_COLUMN_INSET`, paint-only reserve); landscape drawers clear the
status bar; Undo refocuses the restored track/marker.

#### Key Files
- `MapScreen.kt`, `MarkersViewModel.kt`

#### Docs
- `xTrack/Ui_General/260816_FEAT_PLN_Ui_General_delete-advance-next.md`

---

### top-left-icons

Top-left status icons reordered to GPS → Tracking → Land/Water. GPS icon always
visible (gray DEMO state when GPS off) and now clickable — toggles GPS ↔ demo via the
permission-aware `onGpsModeChange`. Tracking icon 🚤 → 🐾 paw prints. RecenterButton
stays last, GPS-only. Idle-state pulsing dot recolored red (semantic.danger). BUILD SUCCESSFUL.

#### Key Files
- `MapScreen.kt`, `TrackStatusIcon.kt`

#### Docs
- `xTrack/Ui_General/260816_FEAT_PLN_Ui_General_top-left-icons-reorder.md`

---

### landscape-menu-drawer

Landscape menu drawer overflow fix: the body now scrolls only when its content is
taller than the viewport (`DrawerScaffold(scrollable = true)`), with the overscroll
effect suppressed while the content fits (`suppressOverscrollWhenFits`, opt-in, toggles
`overscrollEffect = null` via `derivedStateOf { scrollState.maxValue > 0 }`). Fixed
header stays pinned. Portrait unchanged — zero scroll range, no glow. BUILD SUCCESSFUL.

#### Key Files
- `DrawerScaffold.kt`, `MenuDrawerOverlay.kt`

#### Docs
- `xTrack/Ui_General/260816_FEAT_PLN_Ui_General_landscape-menu-drawer.md`

---

### notification-lifecycle

Foreground notification follows recording state: task-removed + not recording → service stops (notification gone); recording → notification stays and the track keeps recording in background (recorder + GPS moved into `TrackRecordingService`, `TrackViewModel` is a pure observer). Double-back exit dialog offers Save track / Continue recording / Discard track. Fixed startup NPE (service deps were field initializers using the un-attached context → `by lazy`).

#### Key Files
- `TrackRecordingService.kt`, `TrackViewModel.kt`, `TrackRecorder.kt`, `MapScreen.kt`, `StopRecordingReceiver.kt`, `WhereAmIProvider.kt`

#### Docs
- `xTrack/Ui_General/260815_FEAT_PLN_Ui_General_notification-lifecycle.md`

---

### filter

List filters with sort UX normalization. Extensible `ListFilter` (Map-based), filter dropdowns with sectioned card layout, dedicated direction toggle, reset button. `pinnedGrouped` removed from sort, migrated to filter axes. Unfiltered backing list pattern in ViewModels. Popup menus match settings hierarchy.

**Filter axes:**
- Tracks: date range (ALL/THIS_YEAR/LAST_30_DAYS/LAST_7_DAYS, day-based from midnight) + pinned (ALL/PINNED)
- Markers: pinned (ALL/PINNED/UNPINNED) + geometry (ALL/PINS/ZONES) + origin (ALL/MANUAL/AUTO). Geometry=ZONES bypasses origin.

**Sort:** default `CREATED` descending, `UPDATED` removed, no `pinnedGrouped`. Direction toggle (`ArrowDropUp/Down`) with active/inactive state. Context-aware titles: "General" + "Tracks"/"Markers".

**Icons:** `FilterAlt` (funnel), `FilterList` (3-bar sort), `ArrowDropUp/Down` (direction), `Refresh` (reset). All `ButtonColors.icon` compliant.

#### Key Files
- `ListFilter.kt`, `ListSortOrder.kt`, `ListOverlayScaffold.kt`, `TrackViewModel.kt`, `MarkersViewModel.kt`, `TrackHistoryOverlay.kt`, `MarkerManagementOverlay.kt`, `OverlayLayer.kt`, `MapScreen.kt`, `SettingsManager.kt`, `FilterAlt.kt`, `FilterList.kt`, `Refresh.kt`

#### Docs
- `docs/ui-lists-guidelines.md`
- `docs/material-icons-standalone-guide.md`
- `xTrack/Ui_General/260702_FEAT_PLN_Ui_General_filter.md`

---

### filter everywhere

**Principle:** Layers are a viewport onto the list. Map mirrors whatever the list shows after filtering.

1. **Marker fan → ON/OFF** — collapse tri-state (HIDDEN/SHOW_ALL/SHOW_PINNED) to binary toggle. `SHOW_PINNED` becomes a filter axis.
2. **Track map rendering reads `trackListFilter`** — filter summaries before applying `trackingRenderNb`/display settings.
3. **Marker map rendering reads `markerListFilter`** — filter `userMarkers` before `MarkerOverlay`.
4. **Menu drawer filter icons** — `FilterAlt` + `Refresh` right of `>` chevron on TRACK RECORDING and MARKERS rows. Extract `FilterControl` from `ListOverlayScaffold` (private→internal).
5. **Shared state** — both list overlay and menu drawer read/write same `ListFilter` in `SettingsManager`.

Display concerns (colors, transparency, render count, sort) unchanged.

#### Todos
- [x] Extract `FilterControl` from `ListOverlayScaffold.kt` — private → internal
- [x] Add filter icons to `MenuDrawerOverlay.kt` section headers (FilterAlt + Refresh, right-aligned)
- [x] Rename section label `TRACK RECORDING` → `TRACKS`
- [x] Thread filter state params through `OverlayLayer.kt` → `MenuDrawerOverlay`
- [x] Apply `trackListFilter` in `MapScreen.kt` track-rendering LaunchedEffect
- [x] Apply `markerListFilter` in `MapScreen.kt` marker dispatch
- [x] Simplify marker fan to binary ON/OFF (`toggleMarkerLayer()`, remove `WhereToVoteIcon` reference)
- [x] Add `UNPINNED` to track filter axis + `TrackSummary.matchesFilter()` parity
- [x] BUILD SUCCESSFUL (×2)

## Implemented

- **filter-everywhere** — ListFilter axes (tracks=date+pinned, markers=pinned+geometry+origin), fan master ON/OFF, display settings apply after filter → `xTrack/Ui_General/260702_FEAT_PLN_Ui_General_filter-everywhere.md`
- **tweak drawer** — `DrawerHeader` + `DrawerScaffold` (fixed header + scrollable/static body)
- **list sort** — `ListOverlayScaffold` generic scaffold (sort+filter, direction, swipe-to-delete)
- **list extra sort** — `CustomSortField` + 4-part format
- **fan tweak** — scrim removed, `expandedFanId`/`onDismissFan`
- **toast & progress dialog** — Box padding 56→6dp, LoadingOverlay full width
- **overlay styling** — toast/Loading/Error surfaces unified
- **click-N-move** — `ListAction.NavigateToItem(id)`
- **translation** — 84 EN strings added

---

### BackToExitConfirm

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

### KeepScreenOn

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

### page layout

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

### immersive ui rework

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

### map-print-layout

#### Todos

#### Rules

#### Key Files

### menu

Wrap drawer menu items (Position Source toggle, Manage Tracks link) in card backgrounds using `uiCardBackground` for visual grouping, matching the settings/track card pattern.

#### Todos

#### Rules

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt`

### tweak drawer

Normalize all drawer headers: extract `DrawerScaffold` + `DrawerHeader` as shared components, fix the `← [title]` header to stay fixed when content scrolls. Applied to MarkerDrawer (ViewingContent + MatchResultContent) and MenuDrawerOverlay.

- [`MarkerDrawer.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt) — removed outer Box wrapper + private `DrawerHeader`; `ViewingContent` and `MatchResultContent` use `DrawerScaffold(scrollable=true, headerHorizontalPadding=12.dp, shape=panelShape)`
- [`MenuDrawerOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt) — outer Box replaced with `DrawerScaffold(scrollable=false, statusBarsInset=true, contentPadding=24.dp, headerActions={Settings gear})`
- [`ui-drawer-guidelines.md`](docs/ui-drawer-guidelines.md) — added §12 (DrawerScaffold API + consumer table + migration guide), updated §4 (canonical skeleton now points to DrawerScaffold), updated §6 (header tokens reference DrawerHeader location), added I22 decision log entry
- BUILD SUCCESSFUL

#### Rules
- `DrawerScaffold` owns the fixed-header + scrollable-body structure: `Column(fillMaxSize)` → `DrawerHeader` + `Box(weight(1f))` for content
- `DrawerHeader` matches canonical tokens from [`ui-drawer-guidelines.md` §6](docs/ui-drawer-guidelines.md:187)
- Header padding parameterized: default `24.dp` h / `3.dp` v; MarkerDrawer overrides to `12.dp` h
- Content padding preserved via `contentPadding` per consumer: 12dp (MarkerDrawer), 24dp (MenuDrawer)

#### Plan
- `xTrack/Ui_General/260703_FEAT_PLN_Ui_General_tweak-drawer.md`

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/components/DrawerScaffold.kt` (new)
- `app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt`
- `docs/ui-drawer-guidelines.md`

### list sort

Normalize list rendering across tracks and markers by extracting a shared `ListOverlayScaffold<T : ListableItem>` composable. Both [`TrackHistoryOverlay`](app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt) and [`MarkerManagementOverlay`](app/src/main/java/ykws/android/maro/ui/map/MarkerManagementOverlay.kt) become thin consumers providing only card content slots + accent color extraction.

**2026 Compose best practice:** slot-based API + `spring()` platform defaults for animations. Scaffold owns structure; consumers provide content slots.

**Guidelines:** [`docs/ui-lists-guidelines.md`](docs/ui-lists-guidelines.md) — canonical reference created during implementation. Card shell pattern in [`ui-drawer-guidelines.md` §9](docs/ui-drawer-guidelines.md:283).

---

### `ListableItem` — extended interface

```kotlin
interface ListableItem {
    val id: String
    val title: String
    val description: String
    val createdAtEpochMs: Long
    val updatedAtEpochMs: Long
    val isPinned: Boolean
    val isLive: Boolean get() = false   // NEW — "happening right now"
}
```

---

### Sort design

- `ListSortOrder` enum: `UPDATED_DESC` (default), `CREATED_DESC`, `TITLE_ASC`, `PINNED_FIRST`
- Persisted in `AppSettings` via `SettingsManager` (SharedPreferences)
- **Per-list keys:** `trackListSortOrder` / `markerListSortOrder` — independent defaults
- ViewModels observe their key and re-sort on change
- UI: `IconButton(Sort)` → `DropdownMenu` with 4 radio-checked items

**Sort priority chain (scaffold):**
1. `isLive = true` → always top (hard override)
2. Chosen sort order → `UPDATED_DESC` | `CREATED_DESC` | `TITLE_ASC` | `PINNED_FIRST`

---

### Normalized scaffold — `ListOverlayScaffold<T : ListableItem>`

**Shell:** `Box(fillMaxSize, uiSettingsBackground, statusBars insets, clip(RoundedCornerShape(16dp top/bottom)))` — per [§4 Drawer Contract](docs/ui-drawer-guidelines.md:139).

**Header:** `Row(24dp h-pad, 3dp v-pad)` + `IconButton(32dp, CircleShape, uiSettingsSwitchTrackInactive)` + `Icon(ArrowBack, 18dp, uiSettingsTextPrimary)` + `Spacer(16dp)` + `Text(title, 17sp, Bold)` + `headerActions` slot — per [§6 Header Tokens](docs/ui-drawer-guidelines.md:188).

**Section label:** always visible, `uiSettingsAccent`, 17sp Bold, UPPERCASE, 1sp letter-spacing — per [§7](docs/ui-drawer-guidelines.md:221).

**List:** `LazyColumn(fillMaxSize, h=24dp, spacedBy=12dp)` → items sorted `isLive` first, then sort order. Each item wrapped in `SwipeableItemCard<T>`.

**Card shell:** `Row(IntrinsicSize.Min, clip 12dp, uiCardBackground)` + `Box(4dp, fillMaxHeight, accentColor)` + content slot — per [§9](docs/ui-drawer-guidelines.md:296). Accent colors via batch lambda `accentColors: (List<T>) -> Map<String, Color>` — scaffold calls once per list change, looks up `map[item.id]` per card.

**Card slot dispatch:**
- `item.isLive` → `liveCardContent: @Composable (T) -> Unit` (pulsing, live stats, no swipe)
- else → `cardContent: @Composable (T) -> Unit` (swipeable, standard card)

**Swipe-to-delete:** `CARD → SNACKBAR → DELETED`, 30% drag threshold. **Deferred batch for both lists** — scaffold owns `pendingDeletes` set internally. Swipe remembers; dismiss commits.

**Snackbar:** `SnackbarSlot(item.title)` — bg alpha `0.0765f`, enter `slideIn(tween(250))+fadeIn(150)`, exit `slideOut(tween(250))+fadeOut(150)`, heightIn(48-96dp), clip 12dp, "Undo" `TextButton`.

**Animations:** platform `spring()` defaults for card enter/exit (Compose 1.7+).

---

### Action contract — `ListAction` sealed class

Single callback: `onAction: (ListAction) -> Unit`. Scaffold emits actions; consumer wires to ViewModels.

```kotlin
sealed class ListAction {
    // Delete lifecycle — scaffold owns pending set internally
    data class SoftDelete(val id: String, val title: String) : ListAction()
    data class UndoDelete(val id: String) : ListAction()
    data class PermanentDelete(val id: String) : ListAction()
    // Item interaction — consumer wires to ViewModel
    data class SelectItem(val id: String) : ListAction()     // tap → show details
    data class EditItem(val id: String) : ListAction()       // edit → wizard
    data class ExportGpx(val id: String) : ListAction()      // share GPX
}
```

| Action | Scaffold internal | Consumer wiring |
|---|---|---|
| `SoftDelete` | Add to `pendingDeletes`, show snackbar | *(optional)* |
| `UndoDelete` | Remove from `pendingDeletes`, slide card back | *(optional)* |
| `PermanentDelete` | Remove from `pendingDeletes` | `viewModel.delete(id)` + refresh |
| `SelectItem` | — | Markers: `openEditDrawer(id)` |
| `EditItem` | — | Markers: `startWizard(id)` |
| `ExportGpx` | — | Tracks: `shareGpx(id)` |

Scaffold also exposes `onDismiss: () -> Unit` for overlay close. All inter-component communication flows through `onAction`. Card-internal callbacks (`onUpdateTrack`, `onSetIcon`, `onUpdateLiveTrack`) stay as slot-local lambdas.

---

### What each consumer provides

**TrackHistoryOverlay:**
- `title = "Track History"`, `sectionLabel = "RECORDED TRACKS"`
- `accentColors = { summaries -> /* 156-line computation from 11 render settings, returns Map<String, Color> */ }`
- `cardContent = { TrackCardContent(it, dateFormat, onUpdateTrack, onShareGpx) }`
- `liveCardContent = { LiveTrackCard(liveState, ...) }` — when recording
- Live track entry has `isLive = true` in its `TrackSummary`
- `onAction = { action -> when (action) { is ListAction.PermanentDelete -> trackViewModel.deleteTrack(action.id); refreshSummaries(); is ListAction.ExportGpx -> shareGpx(action.id) } }`

**MarkerManagementOverlay:**
- `title = "Markers · ${markers.size}"`, `sectionLabel = "YOUR MARKERS"`
- `accentColors = { markers -> markers.associate { it.id to MarkerColors.of(it.colorIndex) } }`
- `cardContent = { MarkerCardContent(it, onSetIcon) }` — onTap/onEdit now emit `SelectItem`/`EditItem` via scaffold's action callback
- `emptyState = { CenteredPinIcon + "No markers yet" + "Create First Marker" button }`
- No `liveCardContent` — markers have no live state
- `onAction = { action -> when (action) { is ListAction.PermanentDelete -> markersViewModel.deleteMarker(action.id); refresh; is ListAction.SelectItem -> openEditDrawer(action.id); is ListAction.EditItem -> startWizard(action.id) } }`
- **Removed from MarkersViewModel:** `pendingDeletes` set, `softDeleteMarker()`, `undoDeleteMarker()`, `commitPendingDeletes()`

---

### Files touched
- **New:** `ListOverlayScaffold.kt`, `ListAction.kt` (sealed class), `ListSortOrder.kt`, `docs/ui-lists-guidelines.md`
- **Modify:** `ListableItem.kt` (+`isLive`), `SettingsManager.kt` (+2 sort keys), `TrackViewModel.kt`, `MarkersViewModel.kt` (−4 delete methods), `TrackHistoryOverlay.kt`, `MarkerManagementOverlay.kt`, `OverlayLayer.kt` (wiring)

---

### Rules
- Sort is **per-list** — `trackListSortOrder` and `markerListSortOrder` are independent
- `isLive` always top; `PINNED_FIRST` groups pinned within non-live, then `UPDATED_DESC`
- Scaffold owns swipe-to-delete state + `pendingDeletes`; emits `ListAction` events; consumer wires `PermanentDelete` to ViewModel
- **Both lists use deferred batch** — swipe remembers (emits `SoftDelete`), dismiss commits (emits `PermanentDelete` per ID)
- `ListOverlayScaffold` is generic `<T : ListableItem>`
- Animations: platform `spring()` defaults, snackbar `tween(250)`
- Back button: 32dp `IconButton(CircleShape, uiSettingsSwitchTrackInactive)`
- No copy-paste between the two overlay files after normalization

### Future
- [ ] Extend `ListSortField` to support per-type sort fields (e.g., track length, marker distance). Scaffold should accept a `customSortFields: List<ListSortField>` parameter per consumer.

---

### Implementation Risks

| # | Risk | Mitigation |
|---|------|------------|
| 1 | **LiveTrackCard data mismatch** — `liveCardContent` slot receives `TrackSummary` but `LiveTrackCard` needs `TrackRecorderUiState` (speed, elapsed, state). | Capture `liveState` in lambda closure; don't pass through T. |
| 2 | **`isLive` assignment** — `TrackSummary` is protobuf-loaded; nothing marks the active track. | `TrackViewModel.refreshSummaries()` sets `isLive = true` on the currently-recording track's summary. |
| 3 | **MarkersViewModel N+1 refresh** — per-ID `deleteMarker()` calls `loadAll()` after each delete. Dismissing 5 markers = 5 full reloads. | Batch deletes: collect IDs, delete all, `loadAll()` once. |
| 4 | **Accent color staleness** — track accent colors depend on sorted list position. Must recompute on sort change. | Include sort key in `remember` dependency list for batch lambda. |
| 5 | **OverlayLayer wiring** — reducing to single `onAction` callback changes parameter lists in MapScreen → OverlayLayer → overlay chain. | Mechanical update across 3 files; no compile-time safety net. Verify delete + export + tap/edit after migration. |
| 6 | **Live items not swipeable** — scaffold must skip `SwipeableItemCard` wrapper for items with `isLive = true`. | Explicit `if (!item.isLive) SwipeableItemCard(...) else liveCardContent(item)`. |

- [`ListAction.kt`](app/src/main/java/ykws/android/maro/data/model/ListAction.kt) — sealed class: 8 variants
- [`ListSortOrder.kt`](app/src/main/java/ykws/android/maro/data/model/ListSortOrder.kt) — `ListSortField` (TITLE/CREATED), `ListSortState` (field + descending + customFieldKey), `applySort<T>()`
- Sort popup: Popup+Surface card layout matching settings hierarchy
- Filter popup: sectioned card layout with filter axis specs
- Direction toggle: `ArrowDropUp/Down` with active/inactive alpha
- Reset button: `Refresh` icon clears filter + sort to defaults
- Deferred batch delete — scaffold owns `pendingDeletes`
- BUILD SUCCESSFUL

### list extra sort

Extend `ListSortField` to support per-type sort fields via `CustomSortField` data class + `customFieldKey: String?` in `ListSortState`. Track fields: Distance (`distanceNm`), Total Time (computed), Moving Time (computed). Marker fields: Origin (`origin`). Serialization backward-compatible (`"UPDATED:true:false:distanceNm"`). All labels localized (EN + FR via `stringResource()` — ResId approach).

#### Plan
- [`260702_FEAT_PLN_Ui_General_list-extra-sort.md`](xTrack/Ui_General/260702_FEAT_PLN_Ui_General_list-extra-sort.md) — full design (CustomSortField approach, ListSortState extension, SortControl dropdown UX, ViewModel comparators, consumer wiring)

- [`ListOverlayScaffold.kt`](app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt) — `SortControl` renders custom fields section in dropdown with separator, `ListOverlayScaffold` accepts `customSortFields` param
- [`TrackViewModel.kt`](app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt) — `sortSummaries()` dispatches on `customFieldKey`: `distanceNm`, `totalTimeSec` (computed), `movingTimeSec` (computed)
- [`MarkersViewModel.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt) — `sortMarkers()` dispatches on `customFieldKey`: `origin`
- [`TrackHistoryOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt) — defines 3 track custom fields, passes to scaffold
- [`MarkerManagementOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkerManagementOverlay.kt) — defines 1 marker custom field, passes to scaffold
- BUILD SUCCESSFUL

#### Rules
- `customFieldKey: String?` — null = common field active; non-null = custom field active
- `field` is only authoritative when `customFieldKey == null`
- `isLive` pre-sort stays in scaffold, NOT in ViewModel comparators
- Serialization: 3-part string = backward compat; 4-part = custom field

### track list colors

Track list (TrackHistoryOverlay) color review — ensure track cards, stats, labels, and icons use correct tokens from `ui.card.background` and `colors.properties`, consistent with the drawer and settings card patterns.

#### Todos

#### Rules

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt`

### reg speed zone

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
- `xTrack/ZoneTile/260617_FEAT_PLN_ZoneTile_speed-enforcement-zone-auto-show-plan.md` — design plan

### fan tweak

**Focus:** Remove the transparent scrim that intercepts taps when the fan is expanded. Instead, let the MapView itself detect the tap, dismiss the fan, and process the touch normally — so a single tap both closes the fan AND pans/zooms the map.

**Design:** The scrim (`Modifier.clickable`) at `MapContent` level consumed all touches before they reached the osmdroid `MapView`. By replacing it with an Android `View.OnTouchListener` set directly on the `MapView`, we can observe `ACTION_DOWN`, call `onDismissFan()`, and return `false` — the unconsumed event flows to `MapView.onTouchEvent` for normal pan/zoom. This is the only reliable pass-through path across the Compose→AndroidView boundary.

#### Todos
- [x] Remove scrim `Box(Modifier.fillMaxSize().clickable { onDismissFan() })` from `MapContent()` (line ~1138)
- [x] Add `expandedFanId: ControlId?` and `onDismissFan: () -> Unit` parameters to `CoastlineMapView()`
- [x] In `CoastlineMapView.update` block: `mapView.setOnTouchListener { _, event -> if (event.action == MotionEvent.ACTION_DOWN && expandedFanId != null) onDismissFan(); false }`
- [x] Thread `expandedFanId` and `onDismissFan` from `MapContent` call site through to `CoastlineMapView`
- [x] Build & verify: BUILD SUCCESSFUL


#### Rules
- `setOnTouchListener` returning `false` is mandatory — never consume, always let MapView handle
- Gate only on `ACTION_DOWN` to avoid calling `onDismissFan()` on every MOVE event
- Fan child buttons (arc buttons rendered by `FanLayout`) are Compose overlays on top of `AndroidView` — they consume their own taps first, so toggling a layer button still works without dismissing

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — `MapContent()` (remove scrim, thread params), `CoastlineMapView()` (add params, set touch listener)

### toast and progress dialog

**Focus:** The exit toast and loading/progress overlay should fill all available horizontal space in the bottom zone. Legacy padding values from before the 2-column Row refactor unnecessarily constrain their width.

**Root cause:** After the `Row(fillMaxSize)` 2-column layout was introduced, the LEFT COLUMN already fills only the space not consumed by the RIGHT COLUMN. But the bottom overlays still carry manual padding (`start=56dp, end=76dp`) from the pre-refactor era. Additionally, `LoadingOverlay` internally constrains to `.fillMaxWidth(0.66f)`.

#### Todos
- [x] Change both bottom Box padding: `start = 56.dp, end = 76.dp` → `start = 6.dp, end = 6.dp`
- [x] Change `LoadingOverlay` modifier: `.fillMaxWidth(0.66f)` → `.fillMaxWidth()`
- [x] Build & verify: BUILD SUCCESSFUL


#### Rules
- `start = 6.dp` matches the regulated zone icon row's padding — consistent with the 2-column Row layout
- `end = 6.dp` provides minimal breathing room to the RIGHT COLUMN edge
- No overlap risk: the Row layout already separates left and right columns

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — both bottom Box padding + LoadingOverlay modifier

### overlay styling

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


#### Rules
- Background `#CC16213E` = `buttonActionBgColor` — navy at 80% opacity, consistent across all three
- Border colors from existing tokens: blue = `uiSettingsAccent` (`#1565C0`), red = `uiDashboardZoneDanger` (`#C62828`)
- Progress bar fill and spinner keep blue (`uiProgressAccent`) — functional indicators

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — toast Surface, LoadingOverlay, ErrorOverlay

### click-N-move

Click on a marker in the markers list closes the list, moves the map to the marker (point for zones & points, central segment point for corridors), runs whereAmI at boat position, then opens the Viewing drawer with the clicked marker selected (prev/next among whereAmI matches).

- [`UserMarker.kt`](app/src/main/java/ykws/android/maro/data/model/markers/UserMarker.kt:51) — `centerPoint` computed property: Pin→position, Circle→center, Corridor→midpoint(p1,p2)
- [`MarkerManagementOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkerManagementOverlay.kt:106) — card tap emits `NavigateToItem` instead of `SelectItem`
- [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1344) — `NavigateTarget` + `LaunchedEffect`: dismiss list → `animateTo(600ms)` → `delay(650ms)` → `withContext(Dispatchers.Default) { whereAmISync }` → `openEditDrawer(matchedIds + clickedId, selectedId)`
- [`MarkersViewModel.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt:266) — `openEditDrawer(markerIds, selectedId?)`: uses `selectedId` for index + form lookup, backward compatible
- [`MarkerManagementOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkerManagementOverlay.kt:153) — card Row wrapper: `Box` + `.clickable { onTap() }` fix + `KeyboardArrowRight` chevron at `BottomEnd`
- BUILD SUCCESSFUL (×2, initial + chevron fix)

#### Rules
- Map animation: 600ms `animateTo`, sequential after list dismiss
- Corridor center = midpoint of p1/p2 (single-segment geometry)
- `whereAmISync` wrapped in `withContext(Dispatchers.Default)` to avoid main-thread jank
- Clicked marker always unioned into `matchedIds` via `+ target.markerId).distinct()`
- `SelectItem` preserved for future batch selection; not repurposed
- `openEditDrawer` backward compatible: `selectedId` defaults to null

#### Key Files
- `ListAction.kt`, `MarkerManagementOverlay.kt`, `MapScreen.kt`, `MarkersViewModel.kt`, `UserMarker.kt`

#### Plan
- `xTrack/Ui_General/260705_FEAT_PLN_Ui_General_click-n-move.md`

### multi-select

Long-press to enter multiselect mode on list items. Scaffold owns selection state (selected IDs set, mode flag), renders a contextual bottom action bar. Consumer-injected multi-actions per list type (batch delete, batch export, batch pin/unpin). Single-tap behavior changes in multiselect mode: tap toggles selection instead of navigate.

#### Todos

#### Rules

#### Key Files

### translation

Do a pass through the codebase: identify all hardcoded user-facing strings,
extract them to `strings.xml`, and translate into corresponding locales (en + fr).
Both locales must be complete — English as baseline, French as translation.

- 84 FR translations added to `values-fr/strings.xml`
- 14 source files updated: `MenuDrawerOverlay`, `MarkerDrawer`, `IconPickerDialog`, `MarkerManagementOverlay`, `ListOverlayScaffold`, `MapScreen` (incl. Settings Navigation/System tabs), `WizardTopBar`, `DrawerScaffold`, `TypeSelectStep`, `TrackHistoryOverlay`, `DashboardPanel`
- BUILD SUCCESSFUL (×2)

#### Plan
- `xTrack/Ui_General/260706_FEAT_PLN_Ui_General_translation-survey.md`

#### Key Files
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-fr/strings.xml`
- `app/src/main/java/ykws/android/maro/MainActivity.kt` (rememberLocalizedContext)
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` (languageCode)

---

## Rules

## Key Files

## Docs
- `docs/ui-lists-guidelines.md` — ListOverlayScaffold API, filter system, sort+filter popup styling, swipe-to-delete, card tap & navigation chevron (28dp)
- `docs/ui-component-guidelines.md` — canonical UI component patterns (cards, buttons, surfaces, spacing)
- `docs/ui-drawer-guidelines.md` — DrawerScaffold API, row types (including navigation chevron 28dp), I23 chevron normalization decision
- `docs/material-icons-standalone-guide.md` — standalone icon registry (FilterAlt, FilterList, Refresh)
- `xTrack/Ui_General/260615_FEAT_PLN_Ui_General_portrait-bottom-space.md` — analysis of portrait bottom space and status bar immersion
- `docs/color-scheme.md` — canonical reference for all colour tokens in the app
- `xTrack/UI_Map/260616_FEAT_PLN_UI_Map_map-overlay-layout-rationalization.md` — complete layout refactor: 2-column Row structure, symmetric 6dp margins, orientation-aware insets
- `xTrack/UI_Map/260616_FEAT_PLN_UI_Map_map-overlay-layout-inventory.md` — current overlay inventory and planned evolution audit
- `xTrack/Ui_General/260625_FEAT_PLN_Ui_General_drawer-visual-differentiation-plan.md` — Drawer visual differentiation plan
- `xTrack/Ui_General/260712_FEAT_PLN_Ui_General_multiselect-list-plan.md` — Multiselect list plan
