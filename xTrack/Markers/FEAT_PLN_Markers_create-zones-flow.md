# Markers — Wizard Creation Flow

> **Feature:** Markers | **Subfeature:** create-zones-flow
> **Created:** 2026-06-23 | **Status:** Design — confirmed

---

## 1. Problem

The current [`MarkerDrawer.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt) crams all creation fields into a single scrollable form: type selector, name, geometry params (radius/width), corridor 2-point flow, proximity override, and description. This is crowded and overwhelming — especially on small screens where the Save button scrolls off.

Per [workflow review](xTrack/Markers/FEAT_PLN_Markers_workflow-review.md): P4 (Save out of view), P1 (type always defaults to Pin), and general crowding are confirmed pain points.

## 2. Proposal: Wizard-Oriented Creation

Replace the single-form creation drawer with a step-by-step wizard. Each step presents one decision/input. The wizard slides in place of the dashboard — same size, same position.

### 2.1 Absolute Requirements (from user)

| # | Requirement |
|---|-------------|
| R1 | Drawer slides in place of dashboard (bottom→up in portrait, left→in in landscape) |
| R2 | Drawer size = dashboard size (`maxWidth * 3/5` portrait, `maxHeight` landscape) |
| R3 | Map stays visible AND draggable during point-placement steps. No scrim. |
| R4 | Exit via cancel/back button at top-left OR ok/finish. Back gesture also closes. |
| R5 | Text input steps: select-all on focus, open keyboard, drawer moves UP to stay visible above keyboard |
| R6 | Dashboard is hidden (replaced) while wizard is active |

### 2.2 Wizard Step Flow

```
                         ┌──────────────┐
                         │ TYPE SELECT  │
                         │ Pin/Circle/  │
                         │ Corridor     │
                         └──────┬───────┘
                                │ Next
                   ┌────────────┼────────────┐
                   ▼            ▼            ▼
              ┌─────────┐ ┌─────────┐ ┌──────────┐
              │  PIN    │ │ CIRCLE  │ │ CORRIDOR │
              └────┬────┘ └────┬────┘ └────┬─────┘
                   │           │           │
    ┌──────────────┤    ┌──────┤    ┌──────┤
    │ POSITION     │    │ POS   │    │ P1 POS │
    │ drag map     │    │ drag  │    │ drag   │
    └──────┬───────┘    └──┬───┘    └──┬─────┘
           │               │           │
    ┌──────▼──────┐   ┌────▼────┐ ┌────▼─────┐
    │ PROXIMITY   │   │ RADIUS  │ │ P2 POS   │
    │ slider      │   │ slider  │ │ drag map │
    │ 0→500m      │   │ 0→500m  │ │          │
    └──────┬──────┘   └────┬───┘ └────┬─────┘
           │               │          │
    ┌──────▼──────┐   ┌────▼────┐ ┌────▼─────┐
    │ TITLE       │   │ PROX    │ │ RADIUS   │
    │ text input  │   │ slider  │ │ slider   │
    └──────┬──────┘   └────┬───┘ └────┬─────┘
           │               │          │
    ┌──────▼──────┐   ┌────▼────┐ ┌────▼─────┐
    │ DESCRIPTION │   │ TITLE   │ │ PROX     │
    │ text input  │   │ text    │ │ slider   │
    └─────────────┘   └────┬───┘ └────┬─────┘
                           │          │
                      ┌────▼────┐ ┌────▼─────┐
                      │ DESC    │ │ TITLE    │
                      │ text    │ │ text     │
                      └─────────┘ └────┬─────┘
                                       │
                                  ┌────▼─────┐
                                  │ DESC     │
                                  │ text     │
                                  └──────────┘
```

### 2.3 Button Layout (per step)

| Position | Button | Icon/Style | Behavior |
|----------|--------|------------|----------|
| Top-left | Cancel | ← back arrow (settings-style) | Discard everything, close wizard |
| Bottom | Previous | Text button (settings-style) | Go back one step |
| Bottom | Next | Text button (settings-style) | Advance to next step |
| Bottom | Finish | Text button (settings-style) | Save immediately with defaults; dimmed when invalid |

Per-step bottom row:

| Step | Buttons |
|------|---------|
| Type Select | `[Next] [Finish]` |
| Position (all types) | `[Previous] [Next] [Finish]` |
| Radius / Proximity (sliders) | `[Previous] [Next] [Finish]` |
| Title (text) | `[Previous] [Next] [Finish]` |
| Description (last) | `[Previous] [Finish]` |

Previous omitted on first step, Next omitted on last step. Finish always present. Finish is **disabled (dimmed at 40% alpha)** for Corridor until after P2 position step (corridor requires 2 points).

All buttons use the same style as Settings/Language page buttons (`RoundedCornerShape(6.dp)`, `ButtonColors` from `AppConfig`).

### 2.4 Step Details

#### Type Selection
- Three tappable cards: Pin, Circle (zone), Corridor
- Each shows an icon + brief description
- Next → proceeds to geometry-specific first step
- Finish → saves immediately with defaults (disabled for Corridor — needs P2)

#### Position Steps (Pin center / Circle center / Corridor P1 / Corridor P2)
- Instruction text: "Drag the map to position the [point/center]"
- Live preview: the unconfirmed marker renders on the map in real-time (existing `unconfirmedMarker` mechanism in `MapScreen.kt`)
- Map is fully interactive — user pans/zooms normally, no scrim
- Position tracks map center continuously (existing `LaunchedEffect` in `MapScreen.kt`)

#### Slider Steps (Radius, Proximity)
- Card-style layout matching Settings page sliders ([`BoatSizeSlider`](app/src/main/java/ykws/android/maro/ui/map/RegulatedZoneComponents.kt:377)): rounded 12dp card background (`uiSettingsCardBackground`), title + value in a row, `uiSettingsAccent` colors
- Radius: 0 → 500m, step 5m, default 200m
- Proximity: 0 → 500m, step 5m
  - Pin default: 200m
  - Circle default: radius (not radius×3)
  - Corridor default: width (not width×3)
- **Real-time rendering:** Radius and proximity changes update the unconfirmed marker overlay instantly. Proximity override value passed to unconfirmed marker so `MarkerOverlay` renders the correct preview circle.

#### Text Input Steps (Title, Description)
- `OutlinedTextField`, single-line for title, multi-line for description
- On focus: select-all existing text, keyboard opens
- Default values (formatted at creation time):
  - Title: `[ddd, dd MMM yy]` — e.g. "Mon, 23 Jun 26"
  - Description: `Created on [ddd, dd MMM yy] at [HH:mm]` — e.g. "Created on Mon, 23 Jun 26 at 09:14"
- **Drawer moves UP** to stay visible above keyboard (see §2.5)

### 2.5 Keyboard Handling (R5)

**Strategy:** Toggle `windowSoftInputMode` programmatically at runtime — do NOT change the manifest. The single-activity manifest change would break keyboard behavior for Settings text fields and Track rename fields elsewhere in the app.

| When | Mode | Reason |
|------|------|--------|
| Wizard enters a text step (Title / Description) | `adjustNothing` | MapView stays stable, wizard shifts up via offset |
| Wizard leaves text step or closes | Restore `adjustPan` | All other text fields (Settings, Track rename) work normally |

Implementation: `activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)` on text step enter, restore `SOFT_INPUT_ADJUST_PAN` on exit.

| Orientation | Keyboard-open behavior |
|-------------|----------------------|
| **Portrait** | Wizard panel gets `Modifier.offset(y = -imeHeight)` — shifts up by keyboard height, sits above keyboard. MapView (OpenGL) never resizes — zero glitch risk. Map remains visible above the shifted wizard. |
| **Landscape** | No offset. Wizard is full-height on the left. Text input content is positioned in the upper portion of the wizard panel (using `Column` weight/spacer), naturally above the keyboard zone. |

**Known cosmetic issue:** When keyboard is open in portrait, an `imeHeight`-tall gap appears between the MapView's padded bottom edge and the wizard's shifted position. This is because `MapContent` has `padding(bottom = portraitDashboardHeight)` — the map tiles don't render in that strip. The gap shows the background color (~300dp on typical phones). Accepting this for v1; fixing it requires dynamically adjusting MapView padding (which risks OpenGL resize glitches — the very thing `adjustNothing` avoids).

```
Portrait — keyboard open:
┌──────────────┐
│              │
│     MAP      │
│              │
├──────────────┤ ← wizard shifted up by imeHeight
│   WIZARD     │
├──────────────┤
│  KEYBOARD    │
└──────────────┘
```

Landscape — keyboard open:
```
┌────────┬──────────┐
│ WIZARD │          │
│  text  │   MAP    │
│  field │          │
│ (upper)│          │
├────────┤          │
│(lower) ├──────────┤
│        │ KEYBOARD │
└────────┴──────────┘
```

`WindowInsets.ime` read from root view — works on API 30+ (project uses latest SDK).

### 2.6 Dashboard Replacement (R1, R6)

In [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:934-948), when `drawerState is MarkerDrawerState.Creating` or `MarkerDrawerState.Editing`, render the wizard panel **instead of** `DashboardPanel`. Same `Modifier`:

- Portrait: `.align(BottomCenter).fillMaxWidth().height(portraitDashboardHeight).offset(y = -imeHeight)`
- Landscape: `.align(CenterStart).width(landscapeDashboardWidth).fillMaxHeight()`

### 2.7 No Scrim (R3)

Already the case in current drawer. Inherited: no scrim, map is fully interactive behind/above the wizard panel.

### 2.8 Step Animation (Q5)

Slide animation between steps using `AnimatedContent`:
- **Next:** `slideInHorizontally { it }` + `slideOutHorizontally { -it }` (right → left)
- **Previous:** `slideInHorizontally { -it }` + `slideOutHorizontally { it }` (left → right)

### 2.9 Edit Mode (Q2)

Wizard used for **both** Create and Edit. When editing, steps are pre-filled with existing marker data.

**Edit-specific behavior:**
- **Skip TypeSelect:** Wizard starts at the Position step (not TypeSelect) — type is already known. For Corridor, starts at Position (P1), with P2 also pre-filled.
- **Center map on marker:** On edit start, map animates to the existing marker position via `mapCenterRequest` StateFlow observed by MapScreen.
- **Preserve values:** All slider/text fields pre-filled. User can Next through unchanged or modify.

### 2.10 Finish Behavior (Q4)

**Finish saves immediately with all defaults for remaining steps.** No jump-to-Description intermediate step.

Defaults applied:
- Radius: 200m
- Proximity: pin=200m, circle=radius, corridor=width
- Title: `[ddd, dd MMM yy]`
- Description: `Created on [ddd, dd MMM yy] at [HH:mm]`

Exception: Corridor Finish is **disabled** (dimmed) until after P2 position step. Corridor requires 2 points — cannot save with 1.

### 2.11 Post-Save Undo (P5)

After Finish saves the marker, show a Snackbar:
- Text: `"Marker [name]" created`
- Action: "Undo" — deletes the marker immediately
- Duration: 3 seconds
- Dismissed: marker is permanent

Uses the same soft-delete pattern as the management page (`pendingDeletes` set). No new infrastructure.

### 2.12 Match Highlighting on Map (P6)

When "Where am I?" results are shown (`drawerState is MatchResult`), matched markers render with a highlighted border/glow on the map. Unmatched markers dim to 30% alpha. When the match drawer closes, all markers return to neutral rendering.

**Implementation in [`MarkerOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt):**
- New parameter: `matchedMarkerIds: Set<String>?` (null = normal mode, all neutral)
- MapScreen passes `matchResult?.matches?.map { it.marker.id }?.toSet()` when `drawerState is MatchResult`
- Highlight style: thicker stroke (3dp → 5dp), brighter color (semantic.info at full alpha)
- Dimmed style: 30% alpha on stroke + fill
- Pin markers: dot radius unchanged, only alpha affected
- Corridor: centerline + boundary lines both dimmed/highlighted

### 2.13 Management Page Edit Button (P8)

Each marker row in [`MarkerManagementOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkerManagementOverlay.kt) gets an explicit **Edit** button. No more tap-on-row to edit. Edit button opens the wizard in edit mode pre-filled with that marker's data.

### 2.14 Marker Tap on Map (P10)

Restore tap-to-view on map markers using OSMdroid's `MapEventsOverlay` (which distinguishes single tap from drag — no drag-blocking issue). Tap a marker on the map → opens drawer in **Viewing** mode. From there, user can tap **Edit** to open the wizard, or **Delete** to remove.

Implementation: register a `MapEventsOverlay` on the MapView that performs hit-testing against marker geometries. On single tap confirmed (not a drag), find the tapped marker and call `openEditDrawer(markerId)` — which shows Viewing mode first.

## 3. State Machine

```kotlin
sealed class WizardStep {
    data object TypeSelect : WizardStep()
    data object Position : WizardStep()        // Pin center / Circle center / Corridor P1
    data object PositionP2 : WizardStep()      // Corridor P2 only
    data object Radius : WizardStep()          // Circle / Corridor
    data object Proximity : WizardStep()       // All types
    data object Title : WizardStep()           // All types
    data object Description : WizardStep()     // All types
}
```

`CreateFormState` accumulates data across steps. Wizard step determines which UI to show.

**Step sequence per type:**

| Step | Pin | Circle | Corridor |
|------|:---:|:------:|:--------:|
| TypeSelect | ✓ | ✓ | ✓ |
| Position | ✓ | ✓ | P1 |
| PositionP2 | — | — | ✓ |
| Radius | — | ✓ | ✓ |
| Proximity | ✓ | ✓ | ✓ |
| Title | ✓ | ✓ | ✓ |
| Description | ✓ | ✓ | ✓ |

**Navigation:**
- `Cancel` (top-left ←) → close wizard, discard form, restore dashboard
- `Previous` → go back one step
- `Next` → advance to next step
- `Finish` → save immediately with defaults for remaining steps
- Back gesture / OS back → same as Cancel
- Edit mode: on save, update existing marker; on cancel, revert to original values

## 4. What Happens to the Old Drawer?

| Mode | Fate |
|------|------|
| `Creating` | **REPLACED** by wizard |
| `Editing` | **REPLACED** by wizard (pre-filled) |
| `Viewing` | **KEPT** — read-only marker view with Edit/Delete buttons |
| `MatchResult` | **KEPT** — "where am I?" results |

`CreationContent` and `EditContent` composables in [`MarkerDrawer.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt) are removed. `ViewingContent` and `MatchResultContent` remain.

## 5. Implementation Phases

### Phase 1: Wizard state machine + compose shell
- Add `WizardStep` sealed class to `MarkersViewModel`
- Add `wizardStep: StateFlow<WizardStep>`
- Add `nextStep()`, `previousStep()`, `finishEarly()`, `cancelWizard()` methods
- Build `WizardDrawer.kt` — new composable replacing `DashboardPanel` when wizard active
- Shell: top bar (Cancel ← + step title), content area with `AnimatedContent`, bottom button row

### Phase 2: Step content composables
- `TypeSelectContent` — three tappable cards (Pin/Circle/Corridor)
- `PositionStepContent` — instruction text, leverages existing `unconfirmedMarker` preview
- `SliderStepContent` — reusable slider with value label, configurable range/default/title
- `TextInputStepContent` — text field with auto-select-all, keyboard handling, default date formatting

### Phase 3: MapScreen integration
- Replace dashboard with wizard when `drawerState is Creating` or `Editing`
- Wire keyboard insets to wizard panel offset (portrait only)
- Extend `LaunchedEffect` position tracking (line 810) from `MarkerDrawerState.Creating` to also cover `MarkerDrawerState.Editing` — enables map-drag repositioning in edit mode
- Ensure map padding (`bottom = portraitDashboardHeight`) adjusts for wizard+keyboard state

### Phase 4: Keyboard behavior
- On wizard text step enter: `activity.window.setSoftInputMode(SOFT_INPUT_ADJUST_NOTHING)`
- On wizard text step exit / close: restore `SOFT_INPUT_ADJUST_PAN`
- Add `Modifier.offset(y = -imeHeight)` to wizard panel in portrait
- Landscape: position text content in upper portion of wizard, no offset
- Test on device: verify Settings/Track rename fields still work after wizard closes

### Phase 5: Cleanup — Dead Code Removal & Class Dispatch

**Remove from [`MarkerDrawer.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt):**
- `CreationContent` composable (lines 138-448) — replaced by wizard
- `EditContent` composable (lines 578-906) — replaced by wizard (pre-filled)
- `TypeSelector` composable (lines 1054-1118) — if only used by CreationContent/EditContent
- `drawerTextFieldColors()` (lines 1120-1125) — if only used by removed content
- `DrawerHeader` composable (lines 1016-1052) — if only used by removed content; ViewingContent also uses it, so keep if still needed

**Remove from [`MarkersViewModel.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt):**
- `openCreateDrawer()` — replaced by `startWizard()`
- `openEditMode()` — replaced by `startWizard(markerId)`
- `backToCorridorP1()` — wizard handles back navigation generically
- `setCorridorP2()` — wizard handles P2 confirmation
- `CorridorPhase` enum — wizard handles P2 flow via `WizardStep.PositionP2`

**Keep in [`MarkersViewModel.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt):**
- `MarkerType` enum — needed for step sequence determination (`stepSequenceFor`) and geometry construction in `saveMarker()`
- `CreateFormState` — accumulates data across wizard steps, unchanged structure
- `updateForm()` — used by wizard steps to mutate accumulated state
- `saveMarker()` / `updateMarker()` / `deleteMarker()` — CRUD, unchanged
- `whereAmI()` — on-demand match, unchanged

**Retain in [`MarkerDrawer.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt):**
- `MarkerDrawer` entry point composable — still handles Viewing and MatchResult modes
- `ViewingContent` — read-only marker view
- `MatchResultContent` + `MatchResultRow` — "where am I?" results
- Shared components only if still used by ViewingContent/MatchResultContent

**New file: [`WizardDrawer.kt`](app/src/main/java/ykws/android/maro/ui/map/WizardDrawer.kt)**
- `WizardDrawer` entry composable (replaces DashboardPanel in MapScreen)
- `WizardStepContent` — dispatches to step-specific composables based on `wizardStep`
- `TypeSelectStep` — three tappable type cards
- `PositionStep` — instruction text + map interaction hint
- `SliderStep` — reusable parameterized slider (radius, proximity)
- `TextInputStep` — text field with auto-select-all, keyboard handling, date defaults
- `WizardButtonRow` — Previous/Next/Finish button bar
- `WizardTopBar` — Cancel (←) + step title

**New state in [`MarkersViewModel.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt):**
- `WizardStep` sealed class (7 step types)
- `wizardStep: StateFlow<WizardStep>`
- `startWizard(initialType: MarkerType? = null)` — begins wizard flow
- `startWizard(markerId: String)` — begins wizard in edit mode, pre-filled
- `wizardNext()`, `wizardPrevious()`, `wizardFinish()`, `wizardCancel()`
- `stepSequenceFor(type: MarkerType): List<WizardStep>` — returns ordered steps per type

### Phase 6: Polish — P6, P8, P10

**P6 — Match highlighting on map:**
- Add `matchedMarkerIds: Set<String>?` parameter to `MarkerOverlay` composable
- MapScreen passes `matchResult?.matches?.map { it.marker.id }?.toSet()` when `drawerState is MatchResult`
- Highlight style: thicker stroke (3dp→5dp), semantic.info color. Dimmed: 30% alpha.

**P8 — Management page Edit button:**
- Add explicit Edit button per row in `MarkerManagementOverlay`
- Remove tap-on-row to edit
- Edit button calls `markersViewModel.openEditDrawer(markerId)` → opens wizard in edit mode

**P10 — Marker tap on map:**
- Register `MapEventsOverlay` on MapView (single-tap, not drag)
- Hit-test tap point against marker geometries
- On match: `markersViewModel.openEditDrawer(markerId)` → Viewing drawer → Edit button → wizard
- Wire `ViewingContent` Edit button from `openEditMode()` to `startWizard(markerId)`

## 6. Key Files

| File | Role |
|------|------|
| `app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt` | Remove CreationContent + EditContent; keep ViewingContent + MatchResultContent |
| `app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt` | Add `WizardStep`, `wizardStep` StateFlow, navigation methods |
| `app/src/main/java/ykws/android/maro/ui/map/WizardDrawer.kt` | **NEW** — wizard composable (replaces DashboardPanel) |
| `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` | Replace dashboard with wizard when Creating/Editing; keyboard offset |
| `app/src/main/AndroidManifest.xml` | No change — `windowSoftInputMode` toggled programmatically at runtime |
| `xTrack/Markers/FEAT_DSC_Markers.md` | Add `create-zones-flow` subfeature |
