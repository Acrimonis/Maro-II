---
feature: Markers
type: plan
created: 2026-09-28
status: implemented
---

# Marker route roles (origin / destination) and the wall — marker-side plan

The marker already carried `routingCost` (1–9, 0 = Off) from the 2026-09-25 plan. This plan adds two
independent route roles so a marker can be associated with a route endpoint, puts them beside the cost in
a two-column step, and gives the cost scale a tenth step that marks the marker as a wall. The route-side
consumption is a separate, future feature.

## Scope

- Two independent booleans on a marker — a route **origin** and a route **destination**; either, both,
  or neither may be set.
- The role control shares the existing **Routing cost wizard step**, which is two sections side by side:
  **Route role** left, **Routing cost** right, divided by the vertical rule.
- The cost scale's tenth step: **Blocked/Wall**.
- A new `ui/components/SectionRow` primitive, and the slider's bare form `SliderControl`.
- The guidelines go orientation-agnostic: §2.14's `SectionRow`, §2.6's vertical rule, §1's flow line.

## Non-scope

- No route engine, no route-mode change, no pathfinding: nothing reads the flags or the wall yet.
- No card-header glyph beyond the existing compass, no filter axis, no `MarkerMatcher` change, no sort
  change.
- No new dependency.

## Decisions

- **D1 — model.** `routeOrigin: Boolean = false` and `routeDestination: Boolean = false` on `UserMarker`.
  Both may be true; independent, never an exclusive enum. Additive on the JSON, so a legacy file reads
  `false` with no migration and no schema bump.
- **D2 — no guard needed for the roles.** A boolean has no validity rule; its default is the off state.
- **D3 — form and writes.** The pair joins `CreateFormState`, is seeded by the create, edit and
  dashboard-open seeds, and is written by `saveMarker` and `updateMarker`, as `routingCost` is carried.
- **D4 — the control.** `MultiSelectRow`'s shipped horizontal form: the options side by side in one
  outline, each `toggleable` with `Role.Checkbox`, so any combination is a state. The labels are
  shortened to **Origin** / **Dest** so both fit the narrower column.
- **D5 — the page.** One `CardArea` holding a two-column `SectionRow`: **Route role** left, **Routing
  cost** right, weighted `0.44 / 0.56` (`COST_COLUMN_WEIGHT = 0.56`). Each side leads with its heading
  and comment, then gives the slack to `Spacer(Modifier.weight(1f))` and an 8dp gap before its control, so
  the headings align at the top and the controls share one bottom line. No new wizard step, so neither
  mirrored sequence changes.
- **D6 — the primitives.** `SectionRow(weightLeft, left, right)` lays two `Column` sections side by side
  and owns the vertical rule and the row's height; `SliderControl` is the bare slider plus the optional
  end-label row, so `SliderRow` and this step share one home for the accent and track colours and for the
  readings under the track.
- **D7 — strings.** `wizard_route_role_title`, `wizard_route_role_description` (**Marker can be used as
  Origin or Dest of Route**), `wizard_route_role_origin` (**Origin** / **Origine**),
  `wizard_route_role_destination` (**Dest** in both locales), `wizard_routing_cost_description` (**Cost to
  a Route of this Marker**) and `wizard_routing_cost_blocked` (**Blocked/Wall** / **Bloqué/Mur**), both
  locales. The English is the user's own wording; the French is a translation of it.
- **D8 — the wall, and one home for the range.** `ROUTING_COST_MAX = 9` and `ROUTING_COST_BLOCKED = 10`
  live in `UserMarker.kt` and `validRoutingCost` accepts `1..ROUTING_COST_BLOCKED`, so the guard and the
  slider no longer state the bound twice — the note the 2026-09-25 plan left open. `10` is a role rather
  than a price: a reader that cares about crossing treats it apart from 1–9. The step's value line reads
  the wall's words at `10`, and the track's two ends read **Off** and **Blocked/Wall**.

## Withdrawn rounds (2026-09-28)

Three passes on the step were built, reviewed and superseded the same session, and none is kept:

- **Round 1** read "vertical" as sections stacked and put the role control **under** the slider as a
  vertical `MultiSelectRow`, through a `trailing` slot on `SliderStep`. Gone: that slot, and the vertical
  form's first outing.
- **Round 2** split the step into two plain columns with the options **side by side**, and reverted both
  pieces. Gone: the side-by-side options under the full labels, which made the eleven-character
  destination wrap in a 42 % column.
- **Round 3** stacked the options full width in that narrow column and restored the vertical form. Gone
  with it: the vertical form, once the shipped design put the options back side by side under short
  labels — an unused capability is dead code, and §2.7b no longer claims it.

## Shipped (2026-09-28)

- `RoutingCostStep` is one `CardArea` holding `SectionRow`: **Route role** left, **Routing cost** right,
  each side bottom-weighted by its own spacer.
- The left control is the horizontal `MultiSelectRow` labelled **Origin** / **Dest**; the right is a
  right-aligned value line over `SliderControl`, both under a `StepSectionHeading`.
- Comments wrap rather than being cut: the left reads **Marker can be used as Origin or Dest of Route**,
  the right **Cost to a Route of this Marker**.
- The cost scale is `0..ROUTING_COST_BLOCKED`: `0` is Off and the only clear, 1–9 are prices, and `10` is
  the wall, named **Blocked/Wall** on the value line and under the track's right end.
- `SliderRow` now calls `SliderControl`, so the slider's colour recipe and its end-label row each have one
  home.
- `SectionRow.fillMaxHeight` on both sides is what lets the caller's weighted spacer bottom-align the
  controls; the rule stretches with the row.

## File map

- `data/model/markers/UserMarker.kt` — `routeOrigin`, `routeDestination`, `ROUTING_COST_MAX`,
  `ROUTING_COST_BLOCKED` and the widened guard.
- `ui/map/MarkersViewModel.kt` — `CreateFormState`, the three seeds, `saveMarker`, `updateMarker`.
- `ui/components/SectionRow.kt` — **new**: the side-by-side section primitive.
- `ui/components/MultiSelectRow.kt` — unchanged; its shipped horizontal form is the control.
- `ui/components/SliderRow.kt` — `SliderControl` holds the colours and the end-label row; `SliderRow`
  forwards its own labels to it.
- `ui/markers/wizard/steps/RoutingCostStep.kt` — the two-column step and the wall's wording.
- `res/values/strings.xml`, `res/values-fr/strings.xml` — the role keys, the cost comment and the wall.
- `docs/ui-component-guidelines.md` — §2.14, §2.6's vertical rule, §1's flow line, §4's anti-pattern.
- `test/.../markers/MarkerRouteRoleTest.kt`, `RoutingCostGuardTest.kt`, `ui/map/MarkerRoutingCostHeaderTest.kt`
  — the JSON round-trip, the guard over 1..10 with the wall inside it, and the compass.

## Verification

- `gradlew :app:assembleDebug` recompiled and SUCCESSFUL, and
  `gradlew :app:testDebugUnitTest --tests "*Marker*" --tests "*RoutingCost*"` green.
- Manual pass on the device, owed by the user: the rule is visible between the columns, the headings and
  the controls line up, the slider reaches the wall and names it, and every label and comment reads whole.

## Open items

- **O1** The alignment rests on `height(IntrinsicSize.Min)` with both sides filling it and each holding a
  weighted spacer, so it depends on intrinsic measurement succeeding through a Material3 `Slider`. A
  refusal throws at measure time, and if the rule is still absent on the device this is the prime suspect;
  the fallback is to fix the row's height another way.
- **O2** Both comments wrap — the left to about three lines, the right to two — so the two comment blocks
  differ in height. Headings and controls still align; the user's own wording was kept rather than
  shortened for the column.
- **O3** The heading is a hand-rolled 16sp Medium label where §2.9 names the 16sp SemiBold
  `SubSectionHeader`; that composable is `private` in `MapScreenSettingsOverlay.kt:1769`. Promote it to
  `ui/components` and call it, or match its weight.
- **O4** Bottom-weighting is the caller's spacer, not the primitive's — a contract §2.14 documents but
  the code cannot enforce.
- **O5** `SectionRow` is a two-section API; a three-way row would need one.
- **O6** `OverlayLayer.stepSequenceFor` (`:60`) still duplicates `MarkersViewModel.stepSequenceFor`
  (`:552`). Named, not fixed.
- **O7** The two superseded `route-role-flags` paragraphs in
  [`FEAT_DSC_Markers.md`](FEAT_DSC_Markers.md:1)'s `## Implemented` are longer than the editing tools can
  read back whole, so they stand flagged rather than deleted — the long-line limitation `AGENTS.md`
  records. The next `#bake` owns their removal.
- **O8** `Dest` is an abbreviation chosen to fit half of the narrow column; the app's full word elsewhere
  is `Destination`.
- **O9** The wall's words appear twice by design — the value line at `10` and the track's right end — the
  user having asked for both; trimming one is a free call if it reads as noise on the device.
