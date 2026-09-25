---
feature: Markers
type: plan
created: 2026-09-25
status: implemented
---

# Marker routing cost (1–9, 0 = Off) — marker-side plan

The marker carries a cost for a routing feature that does not exist yet. This plan covers the marker
side only: the value, the wizard step that sets it, and the glyph that shows it.

## Scope

- An optional integer 1–9 on a marker, off by default, set on **a slider whose 0 position means Off**,
  in a wizard step titled **Routing cost**.
- A 🧭 beside the geometry glyph in the marker card while the value is set.
- One control — the wizard's own slider step — so nothing new has to be made to fit the frame.

## Non-scope

- No routing engine, no pathfinding, no cost consumer: nothing reads the value except the card glyph.
- No `MarkerMatcher` change, no map-overlay or pin rendering change, no filter or sort axis.
- No new dependency, and no settings surface gains the control now.
- No wheel: the wheel shape is withdrawn and the file that carried it goes with it (Round 3).

## Current state (read)

- `UserMarker` — `data/model/markers/UserMarker.kt:32`; the repo's JSON reader carries
  `ignoreUnknownKeys = true` (`data/markers/UserMarkerRepository.kt:40`), so a new nullable field is
  additive and legacy files read `null`. No schema version exists on the marker file to bump.
- Wizard model — `WizardStep` (`ui/map/MarkersViewModel.kt:110`), the sequence
  `stepSequenceFor` (`:551`) **and its UI mirror** `ui/map/OverlayLayer.kt:60`, dispatch
  `ui/map/WizardDrawer.kt:233`, dot progress `ui/markers/wizard/WizardTopBar.kt:35`.
- Form — `CreateFormState` (`MarkersViewModel.kt:124`), create entry `startWizard` (`:603`), the
  dashboard-open seed (`:412`), edit entry `startWizard(markerId)` (`:622`), writes `saveMarker` and
  `updateMarker` (`:813`).
- Card header — `MarkerCardContent` (`ui/map/MarkerManagementOverlay.kt:260`), `coordinateHeader()`
  `:504`; render sites are the marker list `:198` and the detail drawer, so one edit serves both.
- The wizard's frame — mounted at a fixed height in portrait (`ui/map/OverlayLayer.kt:328`) derived
  from `maxWidth * 3 / 5` (`ui/map/MapScreen.kt:1266`), with 56dp of top bar
  (`ui/markers/wizard/WizardTopBar.kt:42`) and 56dp of button row (`WizardButtonRow.kt:45`) taken out
  of it. That leaves the step **104dp on a 360dp-wide phone and 135dp on a 411dp one**.
- The slider precedent — `ui/markers/wizard/steps/SliderStep.kt:31`, already the Radius and Proximity
  steps' control, and therefore already proven to fit that same 104–135dp.

## Decisions

- **D1 — model.** `routingCost: Int? = null` on `UserMarker`; `null` means off, and a set value ranges
  1–9. The slider's `0` is how a user reaches `null`, so the data side needs no clearing rule of its own.
- **D2 — two pure rules, one home.** `validRoutingCost(value: Int?): Int?` in `UserMarker.kt` reads a
  stored value, returning it only inside 1–9, so `null`, `0` and anything out of range all read as off;
  `routingCostForSlider(position: Int)` beside it maps a slider position to a cost, `0` giving `null`.
  Both live in the model file so the rule "0 means off" is reachable by a test rather than a lambda in
  the UI. Stored data is never rewritten on read.
- **D3 — the step sits before Title**, not last: Round 4 moved it. It stays in all three type sequences
  and creation and edition still carry it alike.
- **D4 — the control is the wizard's slider, not a new widget.** Range `0.0..9.0` with a one-unit
  step, so the knob rests on whole numbers and the left end is the Off position.
- **D5 — `0` is Off and is the only clear.** The value line reads the Off label at 0 and the number
  above it, and 0 maps to `null`. There is **no Reset row**: the 0 position is the clear, and the row a
  Reset would have taken is what the wheel could not afford (Round 3).
- **D6 — the glyph.** `coordinateHeader()` appends the 🧭 only when `validRoutingCost(...) != null`,
  after the geometry glyph, so the header reads `[lat, lng] 📍 🧭`; the value itself is not drawn.
- **D7 — strings.** `wizard_routing_cost_title`, a one-line `wizard_routing_cost_description`, and
  `wizard_routing_cost_unset` **revalued** to `Off` / `Désactivé` — the same key does the same job, so
  no second key was minted for it — in `values/strings.xml` and `values-fr/strings.xml`. Only the
  wheel's `wizard_routing_cost_reset` and `..._stop_fmt` are retired with it, in both locales.

## Round 3 — the slider replaces the wheel (2026-09-25)

Round 2 — the sideways wheel — is **withdrawn, never built**. Round 2's own upright shape is
withdrawn with it. What follows replaces both.

**Why the wheel was given up.** The numbers, each from a read constant: a visibility window of three
lines needs 120dp at 40dp rows and a half-row-above-and-below window needs 80dp, while the step's own
label, value line, gaps and Reset row cost 126dp before a single stop is drawn — 72dp even with the
description, the value line and one gap removed. Against a content area of 104dp on a 360dp-wide phone
and 135dp on a 411dp one, no window of one line or more could be reached, so the wheel had nothing to
shrink into. The frame was the binding constraint, not the row height.

**What ships instead.** The wizard's existing slider step, whose own arithmetic already fits the same
frame — Radius and Proximity use it today.

- One slider, one home: `SliderStep` gains three optional parameters — `valueLabel`, `startLabel` and
  `endLabel` — each defaulting to the exact expression it replaced, so Radius and Proximity are
  untouched. The cost step passes the Off string as both the value line at zero and the start label,
  and `ROUTING_COST_MAX` as the end label, so the step's own ceiling has one home. No second copy of
  the slider recipe is written.
- `0` maps to `null` at the step, and the guard in D2 reads a stored `0` as off as well, so the two
  ends agree.
- The step keeps the wizard's tight card shell — 8×4dp padding, 12dp radius, `uiCardBackground` — and
  nothing else about pass 1 moves: the sequences, the dispatch, the three form seeds, the
  `updateMarker` copy, the compass in `coordinateHeader()` and both test classes stand as shipped.
- The description is trimmed to a single short line and carries `maxLines = 1`, which is how the app
  keeps a line from wrapping rather than a character count.
- `ui/components/WheelRow.kt` is **deleted**. It was created in this same session, nothing else
  consumes it, and the control it implements is withdrawn — a deletion, named as one.

## Round 4 — the slider row reads like a settings row, and the step moves up (2026-09-25, APPLIED)

**Scope, as ordered.** Normalize the value-and-control composition of every wizard step that carries a
slider — Radius, Proximity and Routing cost, all three through the one `SliderStep`; give every wizard
slider's content a horizontal inset; and move the Routing cost step from last to **before Title**.

**Composition, taken from §2.2's `SliderRow` and applied inside `SliderStep`:**

| Part | Today | Normalized |
|------|-------|------------|
| Label | 14sp Medium `uiTextPrimary` | 16sp Medium `uiTextPrimary` (`uiFontToggleSize`) |
| Description | 11sp `uiTextMuted` | 13sp `uiTextMuted` (`uiFontDescSize`) |
| Value | 14sp Bold in the accent colour | 14sp Bold in `uiValueText` (`uiFontValueSize`) |
| Label → control | a 4dp `Spacer` | `uiSpacingLabelControl` (16dp) |
| Slider colours | accent active, accent at 30 % inactive | `uiAccent` active, `uiSwitchTrackInactive` inactive |
| End labels | 11sp `uiTextSecondary` | 13sp `uiTextMuted` |

**The one inset.** The track, the value and the two end labels carry `uiPaddingCardHorizontal` (16dp) of
horizontal padding, so a full-bleed track never reaches the card's edge. That is a deliberate exception
to the container-owns-the-inset rule of §1/§2.0, and it is written down as one in §2.2 of the
guidelines rather than left as a habit.

**The order.** `WizardStep.RoutingCost` moves to sit **before `WizardStep.Title`** — PIN
`TypeSelect, Position, Proximity, RoutingCost, Title, Description`; CIRCLE `TypeSelect, Position,
Radius, Proximity, RoutingCost, Title, Description`; CORRIDOR `TypeSelect, Position, PositionP2, Radius,
Proximity, RoutingCost, Title, Description` — in `MarkersViewModel.stepSequenceFor` **and** its
`OverlayLayer` mirror.

**The height this costs, named rather than discovered later.** The 16sp label, the 13sp description and
the 16dp gap that replaces a 4dp spacer add about 17dp, landing the step near 115–120dp against a frame
that leaves about 104dp on a 360dp-wide phone and 135dp on a 411dp one. The end-label row is the part at
risk, and the device pass is what says whether it clips — the same measurement the frame has been owed
since Round 3.

**Files this round touches.** `ui/markers/wizard/steps/SliderStep.kt` — the composition, the inset, the
end labels; `ui/map/MarkersViewModel.kt` and `ui/map/OverlayLayer.kt` — the reorder in both mirrors;
`docs/ui-component-guidelines.md` — §2.2 gains the wizard slider composition and the card-owned inset.
`RoutingCostStep.kt` inherits the new recipe and changes only if the order list needs it.

## Round 5 — a route-cost filter on the marker list (2026-09-25, APPLIED)

**Scope.** A fourth marker filter axis — label **Routing cost** — with three options All (default),
Route cost and No route cost, joining icon / pinned / origin and obeying the same linked/unlinked
filter behaviour.

**Where it lands.** `UserMarker.matchesFilter` (`data/model/ListFilter.kt:87`) gains the `"routeCost"`
branch, and `markerFilterAxes()` (`:167`) gains the axis, appended last so the existing axes keep their
order. The branch reads the shared guard rather than a raw null test — Route cost means
`validRoutingCost(routingCost) != null` — so the filter and the card's compass agree on what counts as
set, and No route cost is the complement.

**What it implies, named.** The marker map set is filter-only, so a linked filter change hides the
excluded markers from the map as well as the list — the existing behaviour, not a new one.

**Strings.** `filter_axis_route_cost`, `filter_option_route_cost` and `filter_option_no_route_cost` in
both locales; `filter_option_all` is reused.

**Tests.** The three values get JVM cases where the marker predicate is tested; if the predicate has no
test class, one is added with those cases.

## File map

- `data/model/markers/UserMarker.kt` — `routingCost`, `validRoutingCost` and the new
  `routingCostForSlider()`.
- `ui/markers/wizard/steps/SliderStep.kt` — the three label parameters from Round 3, then Round 4's
  composition, inset and end labels.
- `ui/markers/wizard/steps/RoutingCostStep.kt` — the step, now a `SliderStep` on `0.0..9.0`.
- `ui/components/WheelRow.kt` — **deleted**.
- `docs/ui-component-guidelines.md` — §2.2's wizard slider composition and the card-owned inset.
- `data/model/ListFilter.kt` — the `"routeCost"` branch in `UserMarker.matchesFilter` and the axis in
  `markerFilterAxes()`.
- `app/src/main/res/values/strings.xml`, `values-fr/strings.xml` — three keys from Round 4, then the
  filter's three keys; two keys retired.
- Everything else from pass 1 stands: the two step sequences, `WizardDrawer`'s dispatch, the form
  seeds, `saveMarker`/`updateMarker`, `coordinateHeader()`, the guard and header test classes.

## Verification

- `apk-build.bat` reaches SUCCESS with no new warning, and the four marker test classes stay green —
  `RoutingCostGuardTest`, `MarkerRoutingCostHeaderTest`, `MarkerFocusTest`, `MarkerFilterMigrationTest`.
- Scoped tests: the guard asserted over `null`, `0`, `1`, `9`, `10`, and the header string with and
  without a cost.
- Manual pass on the device, owed by the user: the slider's left end reads `Off` and sits at 0, the
  knob steps by one up to 9, the card shows the compass only above 0, editing a marker returns its
  stored value, and the step renders whole — no clipped row — on a small phone and a large one.
- Round 4's regression surface, owed with it: Radius and Proximity take the new composition too, so
  both must be read on the device, and the dot strip must show Routing cost before Title in creation
  and in edition.
- Round 5: the new axis shows in both the list overlay and the menu's marker section, All/Route
  cost/No route cost each filter the list, and a linked change also drops the excluded markers from
  the map — read on the device with the JVM cases green.

## Open items

- **O1** A Pin traverses no zone, yet the step appears for it and the description speaks of a zone.
  The step's shape does not depend on the answer; the wording does.
- **O2** `OverlayLayer.stepSequenceFor` (`:60`) still duplicates `MarkersViewModel.stepSequenceFor`
  (`:551`). Both carry the step; the duplication is named rather than fixed.
- **O3** The Focus History stack in `xTrack/GLOBAL_CONTEXT.md` sits above its cap of ten, so `#bake`'s
  prune is owed independently of this work.
- **O4** The `1–9` bound is stated twice — the guard's range and the step's `ROUTING_COST_MAX` — and
  giving it one home means the guard exporting its range. Named, not fixed.
- **O5** The KDoc at `MarkersViewModel.kt:138` still names the wheel, the withdrawn control, and sits
  outside this plan's file map. Named, not fixed.
