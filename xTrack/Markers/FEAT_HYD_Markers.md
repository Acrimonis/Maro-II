# Context Hydration — Markers — 2026-09-28

**Last Bake:** 2026-09-28 09:55 UTC — written by `#bake`; absence means never baked

**Directive trace:** No dependency was added, no machine-shaped data file was opened and the device was untouched — the user's orders each named their action (the `#new` branch, the role flags, then three layout corrections, the wall, and this bake with `#commit` and `#push`); every claim written about the code carried a read behind it, and the gaps the two Ask reviews named still stand: the alignment's dependence on intrinsic measurement, and the two comment blocks differing in height.

## State

One session, 2026-09-28, on `feature/marker-to-route`, cut from `origin/develop` at `192fb2b`: the marker
gained the two route roles, the cost scale gained its wall, and the Routing cost step was rebuilt as two
columns.

**The roles.** `UserMarker.routeOrigin` and `.routeDestination` are independent booleans, both defaulting
`false`, so either, both or neither may be set — never an exclusive enum. `CreateFormState` carries the
pair through the dashboard-open seed and both `startWizard` entries, and `saveMarker` and `updateMarker`
write it, exactly as `routingCost` is carried. Additive on the JSON: a legacy file reads both false, with
no migration and no schema bump.

**The wall.** `ROUTING_COST_MAX` is 9 for the prices and `ROUTING_COST_BLOCKED` is 10 for a wall the route
may never cross, both in
[`UserMarker.kt`](../../app/src/main/java/ykws/android/maro/data/model/markers/UserMarker.kt), and
`validRoutingCost` accepts `1..ROUTING_COST_BLOCKED` — the range now has one home, which closes the note
the 2026-09-25 plan left open. The step names it **Blocked/Wall** / **Bloqué/Mur** on the value line and
under the track's right end.

**The step.** `RoutingCostStep` is one `CardArea` holding two sections side by side, divided by a vertical
rule: **Route role** left with its comment and its Origin / Dest options side by side, **Routing cost**
right with its comment, a right-aligned value line and the slider, the columns weighted 0.44 / 0.56. Each
side leads with its heading block, then gives the slack to a weighted spacer, so the headings align at the
top and the controls share one bottom line. No new wizard step: both mirrored sequences are unchanged.

**The primitives.** `ui/components/SectionRow.kt` is new — two weighted `Column` sections divided by the
vertical rule, its sides filling the row's height so a caller's spacer can bottom-weight them — and
`SliderControl` holds the slider's colour recipe and its end-label row, which `SliderRow` now forwards to.
`docs/ui-component-guidelines.md` gained §2.14 and the §2.6 vertical rule.

**Build.** `gradlew :app:assembleDebug` SUCCESSFUL with only pre-existing warnings, and
`gradlew :app:testDebugUnitTest --tests "*Marker*" --tests "*RoutingCost*"` green: `RoutingCostGuardTest`
rewritten for 1..10, `MarkerRoutingCostHeaderTest` for the wall's compass, beside the new
`MarkerRouteRoleTest`.

## Target Files

- `app/src/main/java/ykws/android/maro/data/model/markers/UserMarker.kt` — the two roles, the two range constants, the widened guard
- `app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt` — `CreateFormState`, the three seeds, `saveMarker`, `updateMarker`
- `app/src/main/java/ykws/android/maro/ui/markers/wizard/steps/RoutingCostStep.kt` — the two-column step and the wall's wording
- `app/src/main/java/ykws/android/maro/ui/components/SectionRow.kt` — **new**: the side-by-side section primitive
- `app/src/main/java/ykws/android/maro/ui/components/SliderRow.kt` — `SliderControl` holds the colours and the end-label row
- `app/src/main/java/ykws/android/maro/ui/components/MultiSelectRow.kt` — unchanged; its shipped horizontal form is the control
- `app/src/main/res/values/strings.xml`, `app/src/main/res/values-fr/strings.xml` — the role keys, the cost comment and the wall
- `docs/ui-component-guidelines.md` — §2.14, §2.6's vertical rule, §1's flow line, §4's anti-pattern
- `xTrack/Markers/260928_FEAT_PLN_Markers_route-role-flags.md` — the plan, its three withdrawn rounds and its nine open items

## Next Step

The device pass over the rebuilt step, owed by the user: the vertical rule visible between the columns,
the headings and the controls lined up, the slider reaching **Blocked/Wall** and naming it, and both
comments reading whole where they wrap. Then the plan's open items — chiefly the alignment's dependence
on `height(IntrinsicSize.Min)`, the first suspect if the rule is unseen, and §2.14's claim that no caller
hand-rolls a divider — with the two superseded `route-role-flags` paragraphs in the feature's
`## Implemented` left for a `findstr`-based patch or the next bake.
