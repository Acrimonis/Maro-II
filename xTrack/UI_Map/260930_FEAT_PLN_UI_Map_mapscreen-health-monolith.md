<!-- scope: feature -->

# MapScreen remaining-monolith remedy (code health) — step 3

## Context

[`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1) is 4,421 lines, and
[`fun MapScreen()`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:492) alone spans ~3,240 lines
(492–3731). Steps 1–2 (settings extraction + C1–C12 seams) brought it to ~2,587, then two feature waves —
**Inspect mode** and **Route destination mode** — each bolted a new state cluster, a set of orchestration
closures and new effects straight into the body, and grew the [`OverlayLayer(...)`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3031)
call site to ~430 lines of inline callbacks. The seam files from step 2 still exist and still own their
effects; what regrew is the *orchestration glue* between them.

## Goal

Decompose the remaining body into focused, same-package files so `MapScreen` keeps only the shell — ViewModel
acquisition, drawer-visibility booleans, the `MapContent` slot, `OverlayLayer` invocation, the back-handler
ladder. Target `fun MapScreen` ≈ 3,240 → ~800–1,000 lines. **Zero behavior/visual change**, one seam per
commit, build-verified after each.

## Design principles (locked, carried over from step 2)

1. Same package (`ykws.android.maro.ui.map`); only `private`→`internal` where a moved symbol crosses files.
2. Move, don't rewrite — no logic/string/color/layout change inside any moved block.
3. Preserve every `LaunchedEffect`/`DisposableEffect` key tuple and null-guard shape byte-for-byte.
4. Unconditional hosts for effects that must survive condition changes (demo feed already extracted).
5. No new deps, no ViewModel restructure in the mechanical tiers, no package moves, no `rememberSaveable`
   relocation without a `Saver`.

## The five remaining clusters

| # | Cluster | Current lines | Shape |
|---|---|---|---|
| A | State-read layer | 497–510, 630–678, 877–880 | ~50 `collectAsState` over 5 ViewModels + derived values (`routeLeadFix`, `routeStart`, `routeSelectedPage`, `routeFrontSaved`, `menuTrackIds`, `trackMapVisibleCount`, `trackListIds`) |
| B | Inspect-mode orchestration | 540–584 state, 1593–2110 actions, 2134–2345 + 2878–2906 effects | ~12 state vars + closures (`armInspectMode`, `disarmInspectMode`, `applyInspectStep`, `applyInspectDemoExit`, `openInspectCard`, `abandonInspectOpen`, `advanceMarkerCardFrom`) + effects (handoff landing, card-return, process-death re-arm, marker framing) |
| C | Route-mode orchestration | 586–662 state, 1819–1980 actions, 952–962 + 2428 effects, 3555–3731 exit dialog | `avoidWorldProvider`, `routeEngineSelection`, `RouteViewModel` wiring + closures (`armRouteMode`, `endRouteMode`, `requestRouteExit`, `leaveRouteMode`, `toggleRouteOff`, `followRoute`, `followSavedTrack`, `saveRouteTrack`) + effects + route-exit `ConfirmDialog` |
| D | Dashboard/snack/import glue | 688–761, 763–838 | R1/R2 close rules + snack queue helpers + import banner helpers |
| E | `OverlayLayer` call site | 3031–3461 | 6 bundles already collapsed the signature; the 44 inline callbacks carry multi-line filter/sort/reset/action bodies |

## Decomposition (tiered — mechanical first)

### Tier 1 — callback-body extraction (lowest risk, biggest line win)

- E: extract each multi-line `OverlayLayer` callback body into a named private function
  (`applyTrackSortChange`, `applyTrackFilterChange`, `applyTrackMapFilterChange`, `applyMarkerFilterChange`, …)
  taking the same args the lambda captures. Each lambda shrinks to one line; the ~430-line call site
  drops toward ~120. This is the single largest win and is purely mechanical.
- A: extract the pure derived values into top-level pure functions with explicit args
  (`menuTrackReferential`, `trackMapVisibleCount`, `trackListWalkWorld`, `routeLeadFix`, `routeSelectedPage`).
  State declarations stay hoisted.

### Tier 2 — state-holder "session" classes (codebase pattern: pure Kotlin controller)

- B: new `InspectSession.kt` (same package) owning the inspect state vars + the arm/disarm/step/handoff
  logic. `MapScreen` holds `remember { InspectSession(...) }`. The existing
  [`InspectMode.kt`](app/src/main/java/ykws/android/maro/ui/map/InspectMode.kt:1) (ranking/cursor algorithm)
  stays untouched; the class orchestrates over it. `inspectArmed` is `rememberSaveable` — keep it hoisted or
  give the class a `Saver`; the rest are plain `remember` and move freely.
- C: new `RouteSession.kt` owning the route switch state (`routeArmed`, `routePinned`, `routeExitRequested`,
  `routeAutoPick`) + the lifecycle closures. [`RouteViewModel.kt`](app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt:1)
  already owns runtime state and stays; this class owns the *switch* the toggle writes.
- D: new `MapDashboardController.kt` owning the R1/R2 close rules and the snack queue helpers
  (`enqueueSnack`/`promoteQueued`/`onSnackUndo`/`onSnackTimeout`).

### Tier 3 — optional, only if ordered separately

- Move the route-exit dialog into its own host file (it currently sits inline at 3555–3731).
- Fold inspect/route session logic into their ViewModels — a layering change, riskier, out of scope unless
  explicitly requested.

## Sequencing & verification

1. Land Tier 1 first (E then A): one commit per file, `apk-build.bat` SUCCESS after each, zero behavior change.
2. Land Tier 2 in B → C → D order, each its own commit.
3. Stop and flag to Ask if any cut turns out non-mechanical — do not improvise.
4. On-device smoke via the LOGCAT workflow (user-driven): map renders, inspect arm/step/card, route arm/exit,
   drawers, filters/sorts/resets, snackbar undo, dialogs.

## Files affected (candidate)

- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — shell (~3,240 → ~800–1,000)
- New same-package files: `MapScreenDerived.kt` (Tier 1 A), `InspectSession.kt`, `RouteSession.kt`,
  `MapDashboardController.kt` (Tier 2), optional `MapRouteExitDialog.kt` (Tier 3)

## Risks & mitigations

- **Key-tuple drift** — the inspect/route effects have wide key tuples; any simplification stops the flow.
  Enforce byte-for-byte.
- **`rememberSaveable` relocation** — `inspectArmed`/`routeArmed`/`screenLocked`/`selectedTab` survive
  process death; a plain class drops that unless a `Saver` is added. Keep them hoisted in Tier 2.
- **Strong-skipping regression** — Tier 1 only renames bodies into functions; callbacks stay inline in call
  position, so Compose lambda memoization is preserved (the reason C12 rejected an `OverlayCallbacks` object).
- **Cross-cluster state** — the R1/R2 rules and snack undo touch `highlightedTrackId`/`trackDrawerState`/
  `trackNavigateState` (MapScreen-owned); the controller must receive them as parameters, never own them.

## Out of scope

- ViewModel/repository/domain changes, new dependencies, package moves.
- Any behavioral or visual change.
- `MapContent` internals — separate concern, untouched.

## ELIJP

The file regrew because two new modes were bolted into the one body; the plan extracts their orchestration
into focused files so MapScreen returns to a thin shell.
