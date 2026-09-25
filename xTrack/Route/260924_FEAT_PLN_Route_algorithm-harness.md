<!-- scope: feature -->
# Route — algorithm harness: several engines behind one seam, chosen in Settings

**Created:** 2026-09-24 · **Branch:** `feature/route-avoid` (cut from `origin/develop` `85ef085`, then re-cut from `feature/route-dummy` `13bc023`) · **Status:** shipped 2026-09-24

## What this is for

One algorithm ships today: [`RouteDummyEngine`](../../app/src/main/java/ykws/android/maro/spatial/RouteDummyEngine.kt), named at a single expression in `MapScreen`. This plan makes a second one **coexist** — `RouteAvoidEngine` — and gives the user a Settings row that decides which one the app arms with. The harness is the deliverable; the avoidance algorithm is not, and ships as a straight line for now so the harness can be exercised before any search exists.

## Decisions

- **D1 — The base.** The work sits on `feature/route-avoid`, re-cut from `feature/route-dummy` (`13bc023`) because `origin/develop` carries none of the route work. The user's call, 2026-09-24.
- **D2 — The seam is untouched.** `RouteEngine` gains no id, no name and no member: an engine still implements the five answers it implements today. The algorithm's identity belongs to the harness, and no engine holds user-facing text.
- **D3 — The identity is a registry, not a field.** One spec per algorithm — a stable `id`, a `@StringRes` label, a factory — the `CustomSortField` shape the rulebook names. The registry lives beside the seam in `spatial/`, so a new algorithm is one row plus one class.
- **D4 — The id is the persisted value, seeded the way the pace is.** `route.engine.id` is the value's one home, parsed by `AppConfig` as the shipped default; the user's choice persists in `AppSettings`, **seeded from the property** exactly as `route.freeWaterPaceKn` seeds its preference ([`SettingsManager.kt:150`](../../app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt:150)). An id no registry row claims falls back to the shipped default and is **reported at startup** on the toast surface the colour-key report already uses ([`MainActivity.kt:221`](../../app/src/main/java/ykws/android/maro/MainActivity.kt:221)), with a new `@StringRes` key.
- **D5 — The choice applies at the next arming (the user's call, 2026-09-24).** A live route keeps the engine that drew it; the newly selected one takes over the next time the toggle is turned on.
- **D6 — The avoid engine is a straight line for now.** `RouteAvoidEngine` implements the same contract and answers one segment from the frozen origin to the aimed point, promising no water, no zone and no berth.
- **D7 — The setting is never disabled.** It is writable at any time, and the arming rule of D5 is what keeps a live line honest.
- **D8 — The avoid engine prices at the pace in force (the user's call, 2026-09-24).** It plans its own duration at the live set pace (`route.freeWaterPaceKn`), which also gives the pace setting something to move. R28 scopes the 15 kn fiction to the dummy alone ([`RouteEngine.kt:20`](../../app/src/main/java/ykws/android/maro/spatial/RouteEngine.kt:20)), so this breaks no rule; its real cost is that the pace setting moves a route for the first time, and the epic's `routing-engine` rules gain that sentence.
- **D9 — The control is a dropdown because the list grows, not because two rows need one.** Today's pair would fit the inline `SegmentedRow`; the dropdown is the shape for an algorithm list the user can extend.

## What D5 costs — the one rule it revises

The epic's `routing-engine` section said the engine is **a parameter of `RouteViewModel` built once at `MapScreen`'s composition point**. A ViewModel built once for the screen's lifetime cannot honour D5: it would keep its first engine until the screen is recreated, and the setting would never move it.

So the rule became: **the selected engine is resolved when the mode arms and held for that session**, and the ViewModel takes the selection as a **flow of the selected engine**, not as an instance.

- The composition builds that selection from the setting and the registry: one live engine instance for the chosen id. While no route runs, the ViewModel collects the **selected** engine's `state` — the readiness the toggle gates on.
- On the move into `Choosing`, the ViewModel captures `selection.value` as the **session engine** and holds it for the whole mode, releasing it on the return to `Idle`. Every call the feature makes to an engine goes through that session field, so a selection changed mid-route cannot reach a line already drawn.
- The engine expression in `MapScreen` became the lookup that feeds the selection.
- The two seam tests moved to the selection form; a new test pins that a selection changed while a route runs leaves the standing line untouched.

## Files in play

- `app/src/main/java/ykws/android/maro/spatial/RouteAvoidEngine.kt` — new
- `app/src/main/java/ykws/android/maro/spatial/RouteEngineChoice.kt` — new: the spec and the registry
- `app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt` — the selection, the session engine
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — the composition expression and the factory call
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — the field, its key, its two reads
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt` — the key's accessor and default
- `app/src/main/assets/maro.properties` — `route.engine.id`
- `app/src/main/java/ykws/android/maro/MainActivity.kt` — the startup report for an id nothing claims
- `app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt` — the new System-tab section
- `app/src/main/java/ykws/android/maro/ui/components/DropdownRow.kt` — new
- `app/src/main/res/values/strings.xml` · `values-fr/strings.xml`, `docs/ui-component-guidelines.md`

## Out of scope

- The avoidance itself — no water, no zone, no berth, no bake, no artifact, and no new dependency.
- Any cue showing which algorithm drew the standing line.
- Re-planning or re-asking a live route on a settings change (D5 refuses it).
- The Tracks-side rename and the trace work: this plan touches no part of the save.

## Outcome

Shipped 2026-09-24 in one `#implement` pipeline on `feature/route-avoid`: the registry, the two engines, the persisted selection, the Settings section and the dropdown row, the revised seam (selection flow + session engine) and the tests, all green under `apk-build.bat`.

Deviations from this plan:

- The registry's `factory` became `(paceKn: () -> Double) -> RouteEngine` rather than a no-arg factory — the Ask hop caught that `RouteAvoidEngine` priced at the static property default instead of the live set pace, so the pace is injected through the factory: the `avoid` row passes it and the `dummy` row ignores it.
- The dropdown row's label was dropped (`label` made nullable) rather than given its own string, so the section header is the one label and no new wording was invented.
- The section description wording was authored by the first Code hop and is left unapproved — user-facing text that awaits the user's eye.
- The duplication between `RouteAvoidEngine` and `RouteDummyEngine` was kept: the two placeholders diverge next, so no shared base was extracted.
- The Ask hop returned `revise` once (the D8 defect) and a second Code hop closed the blocker and the two code-side should-fixes; no second Ask hop ran, per the no Code↔Ask ping-pong rule.
