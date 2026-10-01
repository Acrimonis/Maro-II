<!-- scope: feature -->

# MapScreen remaining-monolith remedy (code health) — step 3

## Context

[`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1) is **4,481 lines**, and
[`fun MapScreen()`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:492) alone spans ~3,285 lines
(492–3777; the file's closing braces sit at 3776–3778 and [`MapContent`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3794)
opens at 3794). Steps 1–2 — the C1–C12 seams plus the `OverlayLayer` param collapse, recorded in
[`260909_FEAT_PLN_Ui_Settings_mapScreen-orchestration-monolith-refactor.md`](xTrack/Ui_Settings/260909_FEAT_PLN_Ui_Settings_mapScreen-orchestration-monolith-refactor.md:1)
— brought it to ~2,587, then two feature waves — **Inspect mode** and **Route destination mode** — each bolted a
new state cluster, a set of orchestration closures and new effects straight into the body, and grew the
[`OverlayLayer(...)`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3092) call site to ~430 lines
(3092–3522) of inline callbacks. The step-2 seam files still exist and still own their effects; what regrew
is the *orchestration glue* between them.

**Anchor refresh (2026-09-30, measured against the 4,481-line file).** Every line reference below was re-read
from source; the first draft's lower-half numbers were ~60 lines short. Measured: `fun MapScreen` 492; the
`OverlayLayer(` call opens 3092 and closes 3522; the route-exit `ConfirmDialog` is 3622–3661; `MapContent` is 3794.

## Goal

Decompose the remaining body into focused, same-package files so `MapScreen` keeps only the shell — ViewModel
acquisition, drawer-visibility booleans, the `MapContent` slot, `OverlayLayer` invocation, the back-handler
ladder. Target `fun MapScreen` ≈ 3,285 → ~800–1,000 lines. **Zero behavior/visual change**, one seam per
commit, build-verified after each.

## Design principles (locked, carried over from step 2)

1. Same package (`ykws.android.maro.ui.map`); only `private`→`internal` where a moved symbol crosses files.
2. Move, don't rewrite — no logic/string/color/layout change inside any moved block.
3. Preserve every `LaunchedEffect`/`DisposableEffect` key tuple and null-guard shape byte-for-byte.
4. Unconditional hosts for effects that must survive condition changes (demo feed already extracted).
5. No new deps, no ViewModel restructure in the mechanical tiers, no package moves, no `rememberSaveable`
   relocation without a `Saver`.
6. A **top-level** extracted function cannot capture `viewModel`/`appSettings`/remembered locals — the state a
   callback reads travels as parameters, or the cut is not mechanical. A **Tier-2 state-holder class** must
   expose Compose `MutableState`/`State`, never plain `var` fields, or recomposition silently stops.

## The five remaining clusters (re-anchored)

| # | Cluster | Current lines (approx.) | Shape |
|---|---|---|---|
| A | State-read layer | 497–510, 630–678, 877–880; derived 657–662, 898 | ~50 `collectAsState` over 5 ViewModels + derived values (`routeLeadFix` 898, `routeStart`, `routeSelectedPage` 657, `routeFrontSaved` 662). The three track-referential derived values (`menuTrackIds` 3072, `trackMapVisibleCount` 3080, `trackListIds` 3088) sit **inside** the E region, not up here. |
| B | Inspect-mode orchestration | state ~540–600; actions 1560–2130; effects 2160–2410 + 2905–2935 | ~12 state vars + closures (`armInspectMode` 1755, `disarmInspectMode` 1792, `applyInspectStep` 2103, `applyInspectDemoExit` 1775, `openInspectCard` 2046, `abandonInspectOpen` 1597, `advanceMarkerCardFrom` 2127) + effects (handoff landing, card-return 2285, process-death re-arm 2364, marker framing 2914) |
| C | Route-mode orchestration | state 586–662; actions 1819–2045; effects 952–962 + ~2428; exit dialog **3622–3661** | `avoidWorldProvider`, `routeEngineSelection`, `RouteViewModel` wiring + closures (`armRouteMode` 1833, `endRouteMode` 1904, `requestRouteExit` 1920, `leaveRouteMode` 1932, `toggleRouteOff` 1942, `followRoute` 1955, `followSavedTrack` 1977, `saveRouteTrack` 2006) + effects + route-exit `ConfirmDialog` |
| D | Dashboard/snack/import glue | 688–838 | R1/R2 close rules 689–766 + import banner 767–778 + snack queue helpers 778–838 (`enqueueSnack` 779, `promoteQueued` 784, `onSnackUndo` 790, `onSnackTimeout` 824) |
| E | `OverlayLayer` call site | **3092–3522** | 6 bundles already collapsed the signature; the 44 inline callbacks carry multi-line filter/sort/reset/action bodies |

Note: **B and C actions interleave** — the route closures (1833–2006) sit *inside* the inspect closures
(1597–2127), so the two clusters cannot be cut as two contiguous blocks; commit order must follow closure
boundaries, not the cluster boxes.

## Progress (2026-10-01, `#implement` Code hop)

- **Landed** — Tier 1 E's two seams that need nothing but the ViewModels, `AppSettings` and `MapView`: six
  self-contained callbacks hoisted to `private` top-level functions (zones toggle, arrows and colours chips,
  the eye override, both list/map link toggles) and the ten filter/sort/reset callbacks
  ([`applyTrackSortChange`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3753) …
  [`applyMarkerMapReset`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3925)), R2 threaded through a
  new `CloseDashboards` typealias because a top-level function cannot call the local
  `closeDashboardsForScopeChange`.
- **Measured** — `fun MapScreen` ~~3,285~~ → ~3,138 lines; the
  [`OverlayLayer(`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3118) call site ~~431~~ → ~300 lines.
  Build green: `:app:compileDebugKotlin` and `apk-build.bat` both SUCCESSFUL.
- **Not done** — the ~17 interlocked callbacks (delete / prev / next / action / open-first / create-first /
  marker-drawer-close / regenerate) call MapScreen-local functions (`openSelectedTrack`, `applyInspectStep`,
  `advanceMarkerCardFrom`, `enqueueSnack`, `closeTrackDrawer`, `closeSelectedItemDashboards`) and write
  screen-local state (`navigateToTarget`, `showMarkerManagement`, `pendingResume`, `trackOpStatus`); a faithful
  top-level cut needs 5–8 lambda parameters each — the `OverlayCallbacks` shape the ancestor plan's C12 rejected
  — so they are **gated on Tier 2**, when those local functions live in session classes. Tier 1 A, Tier 2 and
  Tier 3 are untouched, and the plan stays in design.
- **Code health owed** — each R2 call passes four positional booleans (`closeDashboards(false, false, true, linked)`)
  and a function value cannot take named arguments, so a transposition would compile silently and close the wrong
  scope; the hoisted `closeDashboards` val is also a second home for the local fun's four-flag shape. The moved
  bodies still carry fully-qualified `ykws.android.maro.data.model.ListSortState()` / `ListFilter()` though the
  simple names are now imported.
- **Skipping** — unchanged: no regression, no gain. The call-site lambdas still capture a per-recomposition value,
  exactly as they captured the local function before.

## Decomposition (tiered — mechanical first)

### Tier 1 — callback-body extraction

- E — **partly landed, and not the "purely mechanical" cut this plan first claimed** (corrected 2026-10-01, see
  `## Progress`). The two clean seams are in; the call site is at ~300 lines, not the ~120 first predicted, and
  the remaining ~17 callbacks cannot be hoisted without threading 5–8 lambda parameters each. Do them **after**
  Tier 2, so the local functions travel as class methods rather than as parameters, and revise the line target
  before attempting them.
- A — untouched. Extract the pure derived values into top-level pure functions with explicit args
  (`routeLeadFix`, `routeSelectedPage`, `menuTrackReferential`, `trackMapVisibleCount`, `trackListWalkWorld`).
  The last three are computed inside the E region, not with the other collectors, and are extracted from there.
  State declarations stay hoisted.

### Tier 2 — state-holder "session" classes (Compose `MutableState`, not plain fields)

- B: new `InspectSession.kt` (same package) owning the inspect state vars + the arm/disarm/step/handoff logic,
  **holding its state as `mutableStateOf`/`State`** so recomposition survives. `MapScreen` holds
  `remember { InspectSession(...) }`. The existing
  [`InspectMode.kt`](app/src/main/java/ykws/android/maro/ui/map/InspectMode.kt:1) (ranking/cursor algorithm)
  stays untouched; the class orchestrates over it. `inspectArmed` is `rememberSaveable` — keep it hoisted or
  give the class a `Saver`; the rest are plain `remember` and move freely.
- C: new `RouteSession.kt` owning the route switch state (`routeArmed`, `routePinned`, `routeExitRequested`,
  `routeAutoPick`) + the lifecycle closures. [`RouteViewModel.kt`](app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt:1)
  already owns runtime state and stays; this class owns the *switch* the toggle writes.
- D: new `MapDashboardController.kt` owning the R1/R2 close rules and the snack queue helpers. It moves the
  **helpers only** — the render host [`MapSnackbarHost.kt`](app/src/main/java/ykws/android/maro/ui/map/MapSnackbarHost.kt:1)
  already exists and stays, as do `MapDialogHost.kt`, `MapImportConflictHost.kt`, `RouteHost.kt` and
  `RouteConfirmPanel.kt`, which already own their surfaces.

### Tier 3 — optional, only if ordered separately

- Move the route-exit dialog (now 3622–3661, ~40 lines) into its own host file — a small win, not the
  ~176-line block the first draft implied.
- Fold inspect/route session logic into their ViewModels — a layering change, riskier, out of scope unless
  explicitly requested.

## Sequencing & verification

1. Land Tier 1 first (E then A): one commit per file, `apk-build.bat` SUCCESS after each, zero behavior change.
   E's two clean seams are landed and green (2026-10-01); its interlocked remainder and A stay open, the
   remainder behind Tier 2.
2. **Carry the ancestor plan's R2 gate**: Tier 1 E rewrites the same `OverlayLayer` call site whose skipping the
   C1–C12 work protected, so capture a Compose **recomposition counter / compiler metrics** before and after the
   E cut — do not assume parameter skipping is preserved.
3. Land Tier 2 in B → C → D order, each its own commit; verify recomposition on each (a plain-`var` holder
   compiles but freezes the UI).
4. Stop and flag to Ask if any cut turns out non-mechanical — do not improvise.
5. On-device smoke via the LOGCAT workflow (user-driven): map renders, inspect arm/step/card, route arm/exit,
   drawers, filters/sorts/resets, snackbar undo, dialogs.

## Files affected (planned, same package)

- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — shell (~3,285 → ~800–1,000)
- New same-package files: `MapScreenDerived.kt` (Tier 1 A), `InspectSession.kt`, `RouteSession.kt`,
  `MapDashboardController.kt` (Tier 2), optional `MapRouteExitDialog.kt` (Tier 3)

## Risks & mitigations

- **Stale anchors** — the first draft's lower-half line numbers were ~60 short; every reference above was
  re-measured 2026-09-30 against the 4,481-line file, and anchors must be re-checked again at implementation
  time.
- **Key-tuple drift** — the inspect/route effects have wide key tuples; any simplification stops the flow.
  Enforce byte-for-byte.
- **Tier 2 reactivity** — a state-holder class whose fields are plain `var` compiles but stops recomposition;
  the class must hold `MutableState`/`State`.
- **`rememberSaveable` relocation** — `inspectArmed`/`routeArmed`/`screenLocked`/`selectedTab` survive
  process death; a plain class drops that unless a `Saver` is added. Keep them hoisted in Tier 2.
- **Strong-skipping regression** — Tier 1 only renames bodies into functions; callbacks stay inline in call
  position, so Compose lambda memoization is preserved — the ruling recorded in the ancestor plan
  ([`260909_FEAT_PLN_Ui_Settings_mapScreen-orchestration-monolith-refactor.md`](xTrack/Ui_Settings/260909_FEAT_PLN_Ui_Settings_mapScreen-orchestration-monolith-refactor.md:1)),
  where C12 rejected an `OverlayCallbacks` object.
- **Cross-cluster state** — the R1/R2 rules and snack undo touch `highlightedTrackId`/`trackDrawerState`/
  `trackNavigateState` (MapScreen-owned); the controller must receive them as parameters, never own them.

## Out of scope

- ViewModel/repository/domain changes, new dependencies, package moves.
- Any behavioral or visual change.
- `MapContent` internals — separate concern, untouched.

## ELIJP

The file regrew because two new modes were bolted into the one body; the plan extracts their orchestration
into focused files so MapScreen returns to a thin shell.
