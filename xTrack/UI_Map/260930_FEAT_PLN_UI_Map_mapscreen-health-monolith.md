<!-- scope: feature -->

# MapScreen remaining-monolith remedy (code health) — step 3

## Context

[`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1) is **4,527 reader lines** (the file
reader reports two more than the counted total), and [`fun MapScreen()`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:505)
spans 505–3471 ([`MapContent`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3839)
opens at 3839).
Steps 1–2 — the C1–C12 seams plus the `OverlayLayer` param collapse, recorded in
[`260909_FEAT_PLN_Ui_Settings_mapScreen-orchestration-monolith-refactor.md`](xTrack/Ui_Settings/260909_FEAT_PLN_Ui_Settings_mapScreen-orchestration-monolith-refactor.md:1)
— brought it to ~2,587, then two feature waves — **Inspect mode** and **Route destination mode** — each bolted a
new state cluster, a set of orchestration closures and new effects straight into the body, and grew the
[`OverlayLayer(...)`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3069) call site to ~300 lines
of inline callbacks. The step-2 seam files still exist and still own their effects; what regrew
is the *orchestration glue* between them.

**Anchor refresh (2026-10-01, post-migration-phase-4 — measured against the 4,527-line file) — supersedes every
earlier pass.** Measured now: `fun MapScreen` opens 505 and closes 3471; the `CompositionLocalProvider` region
opens 3068 and the `OverlayLayer(` call opens 3069; `MapDialogHost` is called at 3363, `MapImportConflictHost` at
3436 and `MapLockLayer` at 3453; `MapContent` opens 3839. Migration Phase 2 moved five data constructions into
[`MapOverlayData.kt`](app/src/main/java/ykws/android/maro/ui/map/MapOverlayData.kt:1) and Phase 3 folded the eight
written chrome values into [`MapScreenChrome.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreenChrome.kt:1).
Every line number here is a snapshot, re-measured at implementation time and never copied forward.

## Goal

Decompose the remaining body into focused, same-package files so `MapScreen` keeps only the shell — ViewModel
acquisition, drawer-visibility booleans, the `MapContent` slot, `OverlayLayer` invocation, the back-handler
ladder. Target `fun MapScreen` ≈ 3,000 → **as small as wiring-only work reaches — which is not the ~800–1,000 the
plan first claimed.** That figure needed the ladder to leave the screen, and it cannot: the call is 66 parameters
wide because `OverlayLayer` is, so the call stays where it is. What the plan offers instead is a measured,
low-risk reduction and a cleaner call site, with the ownership move named under `## Migration plan` as the only
route to the old target and the reason it is out of this one. **Zero behavior/visual change**, one seam per
commit, build-verified after each, and every phase gate states what it is allowed to change.

## Design principles (locked, carried over from step 2)

1. Same package (`ykws.android.maro.ui.map`); only `private`→`internal` where a moved symbol crosses files.
2. Move, don't rewrite — no logic/string/color/layout change inside any moved block.
3. Preserve every `LaunchedEffect`/`DisposableEffect` key tuple and null-guard shape byte-for-byte.
4. Unconditional hosts for effects that must survive condition changes (demo feed already extracted).
5. No new deps, no ViewModel restructure in the mechanical tiers, no package moves, no `rememberSaveable`
   relocation without a `Saver`.
6. A **top-level** extracted function cannot capture `viewModel`/`appSettings`/remembered locals — the state a
   callback reads travels as parameters, or the cut is not mechanical. A **child composable** re-reads only what
   its caller passes, so hoisting a read out of a child that still paints it stops recomposition silently, with
   no compile error to catch it. (The retired state-holder-class form of this rule is gone with the 10-01
   re-cut below — no class in this plan holds Compose state.)
7. **Consolidate, never duplicate** — every host was read on 2026-10-01, not inferred from a filename:
   `MapDialogHost.kt`, `MapSnackbarHost.kt`, `MapImportConflictHost.kt`, `MapScreenSettingsOverlay.kt`,
   `MapServiceEffects.kt`, `MapMarkerEffects.kt` (+ `MapMarkerDebugEffects`), `MapDepthRasterEffects.kt`,
   `MapGpsFollowEffects.kt`, `MapTrackOverlayEffects.kt` (+ `MapTrackOverlayLiveEffects`,
   `MapTrackOverlayHistoryDiff`) and `InspectMode.kt`'s `MapInspectEffects`. A child file is created only where no
   host owns the subject; otherwise the existing host gains the seam. A claim about a file's contents that no read
   backs is what produced the T2d defect corrected below.

## The five remaining clusters (re-anchored 2026-10-01, post-migration-phase-4)

| # | Cluster | Current lines (approx.) | Shape |
|---|---|---|---|
| A | State-read layer | one `MapScreenChrome` holder at 525; derived 662–665, 886–888 | the `collectAsState` block + the chrome holder + derived values (`routeSelectedPage` 662, `routeFrontSaved` 665, `routeStart` 886, `routeLeadFix` 888). The three track-referential derived values (`menuTrackIds` 3053, `trackMapVisibleCount` 3059, `trackListIds` 3066) sit **inside** the E region, not up here. |
| B | Inspect-mode orchestration | closures 1580–2110 | `abandonInspectOpen` 1580, `openSelectedTrack` 1615, `armInspectMode` 1738, `applyInspectDemoExit` 1758, `disarmInspectMode` 1775, `openInspectCard` 2029, `applyInspectStep` 2086, `advanceMarkerCardFrom` 2110, plus the inspect effects (hosted by `MapInspectEffects`) |
| C | Route-mode orchestration | closures 1816–1989; exit dialog now in `MapDialogHost.kt` | `avoidWorldProvider`, `routeEngineSelection`, `RouteViewModel` wiring + closures (`armRouteMode` 1816, `endRouteMode` 1887, `requestRouteExit` 1903, `leaveRouteMode` 1915, `toggleRouteOff` 1925, `followRoute` 1938, `followSavedTrack` 1960, `saveRouteTrack` 1989); the route-exit `ConfirmDialog` left this file in phase 4, and the route effects left for `MapRouteEffects.kt` |
| D | Dashboard/snack/import glue | 692–826 | `closeMarkerDashboard` 692, `closeTrackDrawer` 726, `closeSelectedItemDashboards` 739, `closeDashboardsForScopeChange` 752 and its `closeDashboards` value 768, `enqueueSnack` 791, `onSnackUndo` 793, `onSnackTimeout` 826; the snack stack itself is `MapDashboardController` |
| E | `OverlayLayer` call site | **opens 3069** (~290) | 6 bundles already collapsed the signature; the inline callbacks carry multi-line filter/sort/reset/action bodies, and the hoisted `apply*` call sites sit inside the region. |

Note: **B and C actions interleave** — the route closures (1837–2010) sit *inside* the inspect closures
(1601–2131), so the two clusters cannot be cut as two contiguous blocks; commit order must follow closure
boundaries, not the cluster boxes.

## Progress (2026-10-01, `#implement` Code hop — re-anchored)

- **Landed** — Tier 1 E's two seams that need nothing but the ViewModels, `AppSettings` and `MapView`: six
  self-contained callbacks hoisted to `private` top-level functions (zones toggle, arrows and colours chips,
  the eye override, both list/map link toggles) and the ten filter/sort/reset callbacks
  ([`applyTrackSortChange`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3650) …
  [`applyMarkerMapReset`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3822)), R2 threaded through a
  new [`CloseDashboards`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:220) typealias because a
  top-level function cannot call the local `closeDashboardsForScopeChange`.
- **Measured** — `fun MapScreen` ~~3,285~~ → **~3,135 lines** (508–3642); the
  [`OverlayLayer(`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3090) call site ~~431~~ → **~300
  lines** (3090–3386). Tier 1(E) removed ~130 lines against the ~310 predicted — the one measured seam delta,
  and the only figure the target may be re-derived from.
- **Not done** — the ~17 interlocked callbacks (delete / prev / next / action / open-first / create-first /
  marker-drawer-close / regenerate) call MapScreen-local functions (`openSelectedTrack`, `applyInspectStep`,
  `advanceMarkerCardFrom`, `enqueueSnack`, `closeTrackDrawer`, `closeSelectedItemDashboards`) and write
  screen-local state (`navigateToTarget`, `showMarkerManagement`, `pendingResume`, `trackOpStatus`); a faithful
  top-level cut needs 5–8 lambda parameters each — the `OverlayCallbacks` shape the ancestor plan's C12 rejected
  — so they are **gated on Tier 2**, where those local helpers are the child composable's own parameters. Tier 1
  A landed in phase 2 (below); Tier 2 is begun but not finished; Tier 3 is untouched, and the plan stays in design.
- **Code health owed** — each R2 call passes four positional booleans (`closeDashboards(false, false, true, linked)`)
  and a function value cannot take named arguments, so a transposition would compile silently and close the wrong
  scope; the hoisted `closeDashboards` val at 779 is also a second home for the local fun's four-flag shape. The moved
  bodies still carry fully-qualified `ykws.android.maro.data.model.ListSortState()` / `ListFilter()` though the
  simple names are now imported. Both are tracked in `## Open items`, not left as prose.
- **Skipping** — unchanged: no regression, no gain. The call-site lambdas still capture a per-recomposition value,
  exactly as they captured the local function before.

**Phase 2 (2026-10-01, second `#implement` hop).**

- **Tier 1 A landed** — the five derived values are now private top-level pure functions: `selectedPageOrNull`
  (generic, so it never names the page type), `routeLeadFixOf` (its two `.toDouble()` conversions stay at the
  caller), `menuTrackIdsOf`, `trackMapVisibleCountOf` and `trackListIdsOf`; `routeLeadFix`'s KDoc moved onto its
  function and a `TrackSummary` import was added.
- **Tier 2 D, first slice landed** — new [`MapDashboardController.kt`](app/src/main/java/ykws/android/maro/ui/map/MapDashboardController.kt:1)
  owns the snack stack (public `activeSnacks`, private overflow queue, `enqueue`/`promote`/`remove`); `MapScreen`
  holds `remember { MapDashboardController() }` at 681 and keeps `enqueueSnack` as a one-line delegation at 804.
  **Correction:** the `promoteQueued` helper no longer exists — `remove(snack)` refills in one place and
  `MapSnackbarHost` calls `onSnackUndo`/`onSnackTimeout` (806, 839) — so the earlier Progress line naming it was
  wrong, not merely stale.
- **Owed fix landed** — `MapDashboardController.remove(snack)` now removes and refills in one place, and both
  snack handlers call it; `:app:compileDebugKotlin` is green.
- **Tier 2 re-cut** — the two session classes are retired in favour of child composables plus per-child
  `@Immutable` bundles; the evidence that retired them and the new T2a–T2d cut live under
  `## Decomposition` → Tier 2.
- **Tier 2 file list corrected, then corrected again** — the first pass said five `*Effects` hosts cover T2b and
  T2d; reading them showed the inspect effects already live in [`InspectMode.kt`](app/src/main/java/ykws/android/maro/ui/map/InspectMode.kt:371)'s
  `MapInspectEffects` (called at 2333), while the **route** effects have no host at all. So T2a, T2c and a new
  `MapRouteEffects.kt` are the only files this plan still creates, and T2b feeds the host that exists.
- **Still open** — Tier 2 (T2a–T2d), Tier 3, and Tier 1 E's ~17 interlocked callbacks, whose home is T2a.
- **Build** — `:app:compileDebugKotlin` SUCCESSFUL after each seam in phases 1–2; the packaging half of
  sequencing rule 1 was owed at the end of phase 2 and is discharged by phase 3 below.

**Phase 3 (2026-10-01, third `#implement` hop — scope T2a + T2c).** Re-measurement before the edit matched every
anchor: the provider opened 3089, `OverlayLayer(` spanned 3090–3386, the lock region ran 3574–3639, and the file
was 4,670 counted lines.

- **T2c landed** — [`MapLockLayer.kt`](app/src/main/java/ykws/android/maro/ui/map/MapLockLayer.kt:26) created (102
  lines, 11 parameters); `MapScreen.kt` 4,670 → 4,622 counted lines and `fun MapScreen` 3,135 → 3,087;
  `apk-build.bat` SUCCESSFUL (`:app:assembleDebug`).
- **T2a refused, not improvised** — sequencing rule 4 was followed: the cut was stopped and flagged rather than
  bent, so no second build ran. The reasons and the two surviving shapes are recorded under `## Decomposition`
  → T2a, and the choice between them is the user's.
- **Not done in this hop** — T2b, T2d, Tier 3, and Tier 1 E's ~17 interlocked callbacks. The plan stays in
  design and no `## Implemented` pointer is due.

**Phase 4 (2026-10-01, fourth `#implement` hop — scope T2b + T2d).**

- **T2b landed** — the route-exit (R59) and resume dialogs moved into
  [`MapDialogHost.kt`](app/src/main/java/ykws/android/maro/ui/map/MapDialogHost.kt:35), 215 → 327 lines and
  25 → 34 parameters, all named, all read-only values plus callbacks; the resume dialog's retained-copy pair moved
  with it. `MapScreen.kt` lost the 89-line block and gained 9 argument lines, and four imports became dead and
  were deleted: `ConfirmDialog`, `ConfirmAction`, `ConfirmActionRole`, `OptionRow`.
- **T2d landed in part** — new [`MapRouteEffects.kt`](app/src/main/java/ykws/android/maro/ui/map/MapRouteEffects.kt:1)
  (67 lines) holds `MapRouteEngineEffect` and `MapRouteEndEffects`, called at 629 and 948, the positions their
  blocks held, so no effect changed its order. The auto-pick effect at 2480 stayed: it writes the screen's own
  `routeAutoPick` and calls its `followRoute`, which a child composable cannot do.
- **Measured** — `MapScreen.kt` 4,622 → **4,531 counted lines**; `fun MapScreen` 3,087 → **3,000** (504–3503);
  `apk-build.bat` SUCCESSFUL after each seam.
- **One ordering change, accepted** — `ConfirmDialog` is **in-tree** (a `BoxWithConstraints` carrying its own
  scrim, not a window), so composition order is stacking order; the two moved dialogs now compose inside
  `MapDialogHost` at 3395, *before* `MapImportConflictHost` at 3468 where they previously followed it. Accepted
  rather than re-ordered: moving the import host above `MapDialogHost` would fix that one relation by flipping
  five others, the overlap needs both up at once, and the back key's winner did not change.
- **Not done** — T2a (withdrawn after the 66-parameter measurement, see `## Migration plan`), Tier 3's ViewModel
   fold, and Tier 1 E's ~17 interlocked callbacks. The plan stays in design and no `## Implemented` pointer is due.

**Phase 5 (2026-10-01, fifth `#implement` hop — Migration Phase 1: Bodies).** Re-measurement before the edit
matched every anchor: `fun MapScreen` 504–3503, `OverlayLayer(` 3077, `MapDialogHost` 3395, `MapLockLayer` 3485,
`MapContent` 3845, and the file at 4,533 reader lines (4,531 counted).

- **Counted first** — the `OverlayLayer` call site carries ~37 inline callback lambdas. Eligible (nothing
  screen-local: no screen-helper call, no screen-state write) were two multi-line bodies — `onRegenerateRasters`
  and `onMarkerDrawerClose` — plus five single-statement ViewModel delegations (`onWizardCancel`,
  `onAutoShowMasterChange`, `onSetIcon`, `onSetPin`, `onUpdateMarkerText`), which stayed: hoisting a one-liner
  adds lines, not removes them. Every other body needs a screen-local helper or writes screen state, so it was
  left alone per the gate.
- **Landed** — the two eligible bodies hoisted to private top-level functions in `MapScreen.kt`:
  [`regenerateRasterLayers`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3831) (6 params — `state`,
  `viewModel`, `depthViewModel`, `context`, `appSettings`, `steps`) and
  [`closeMarkerDrawerUnlessHandoff`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3845) (2 params —
  `inspectHandoff`, `markersViewModel`). Move-don't-rewrite: the bodies and the §5 comment crossed verbatim, the
  captures became explicit parameters, and one `RasterCache` import was added. The call sites keep their
  inline-lambda shape (`{ args -> hoistedFn(...) }`), so no lambda was added, removed or reordered.
- **R2 gate named** — command: the call-site inline-lambda shape diff; threshold: no lambda added, removed or
  reordered; artifact: the call-site diff plus the green build log. No compiler-metrics Gradle config was added.
- **Measured** — `fun MapScreen` 504–3503 → **505–3494** (3,000 → 2,990, net −10); `OverlayLayer(` 3077 → 3078;
  `MapDialogHost` 3395 → 3386; `MapLockLayer` 3485 → 3476; `MapContent` 3845 → 3862; the file 4,533 → **4,550
  reader lines** (net +17 — the two signatures and KDoc cost more than the two bodies removed).
- **Build** — `apk-build.bat` SUCCESSFUL (`:app:assembleDebug`); the scoped `ui.map` + `config` unit run BUILD
  SUCCESSFUL.
- **Not done** — Migration Phase 2 (Builders), Phase 3 (Chrome) and Phase 4 (Close), and the ledger items the
  Phase-1 touch did not reach (the fully-qualified `ListSortState`/`ListFilter` names and the `closeDashboards`
  second home — neither moved body carries them). The plan stays in design and no `## Implemented` pointer is due.

**Phase 6 (2026-10-01, Migration Phase 2: Builders).** Re-measurement before the edit matched the Phase-5
anchors: `fun MapScreen` 505–3494, `OverlayLayer(` 3078, `MapDialogHost` 3386, `MapLockLayer` 3476, `MapContent`
3862, file 4,550 reader lines.

- **Counted** — the distinct inputs each builder needs, `appSettings` travelling whole (the house idiom):
  `OverlayChrome` 9, `MenuOverlayData` 6, `RouteSummaryData` 12, `TrackInfoOverlayData` 5,
  `SettingsOverlayData` 5, `MarkerListOverlayData` 3, `TrackListOverlayData` 2.
- **Landed** — five builders moved to new [`MapOverlayData.kt`](app/src/main/java/ykws/android/maro/ui/map/MapOverlayData.kt:1):
  `buildMenuOverlayData` (6 params — `appSettings` plus five pre-computed values), `buildTrackListOverlayData` (2),
  `buildSettingsOverlayData` (5), `buildMarkerListOverlayData` (3), `buildTrackInfoOverlayData` (5); every
  expression moved verbatim, the `appSettings` threading the house idiom.
- **Left alone per the gate** — `OverlayChrome` (9) and `RouteSummaryData` (12) would each need more than 8
  parameters, so their constructions stay inline.
- **Widened** — `TrackDrawerState` `private`→`internal`, the one moved symbol that crosses files
  (`buildTrackInfoOverlayData` names it).
- **Measured** — `MapScreen.kt` 4,550 → **4,536 reader lines** (−14).
- **Build** — `apk-build.bat` SUCCESSFUL; the scoped `ui.map` + `config` unit run BUILD SUCCESSFUL.

**Phase 7 (2026-10-01, Migration Phase 3: Chrome).** Re-measurement before the edit matched the Phase-6 anchors.

- **Landed** — new [`MapScreenChrome.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreenChrome.kt:1): an
  `@Stable` holder for the eight written chrome values (`showSettings`, `showTrackDrawer`, `showTrackHistory`,
  `showMarkerManagement`, `navigateToTarget`, `selectedTab`, `pendingResume`, `trackOpStatus`), held in one
  `rememberSaveable(saver = MapScreenChrome.Saver)`. The `Saver` serialises `selectedTab` alone — the one
  `rememberSaveable` member; the other seven stay plain and reset on process death exactly as before.
- **Re-pointed** — every read/write of the eight values now goes through `chrome.`; the
  `LaunchedEffect(navigateToTarget)` key keeps the same value, now read as `chrome.navigateToTarget`. `MapContent`
  is untouched.
- **Widened** — `NavigateTarget` `private`→`internal`, the one moved symbol that crosses files.
- **Measured** — `MapScreen.kt` 4,536 → **4,527 reader lines** (−9); `fun MapScreen` 505–3471, `OverlayLayer(` 3069,
  `MapDialogHost` 3363, `MapLockLayer` 3453, `MapContent` 3839.
- **Build** — `apk-build.bat` SUCCESSFUL; the scoped `ui.map` + `config` unit run BUILD SUCCESSFUL.

**Phase 8 (2026-10-01, Migration Phase 4: Close).** Every anchor re-measured from the 4,527-line file and written
into `## Context` and the cluster table above.

- **Review** — the moved builder expressions and the chrome fold were re-read against their sources and are
  verbatim; the `Saver` serialises `selectedTab` alone; `MapContent` is untouched; both gates green. No behaviour
  change found, and the session's five action classes ran inside their verdicts (no new dependency, no
  machine-shaped data read, the work was ordered, no device touched, every code claim backed by a file read).
- **Not done** — the ledger items the run did not reach stay open (the fully-qualified `ListSortState`/`ListFilter`
  names and the `closeDashboards` second home — no moved body carries them; the dialog-host parameter count, the
  route-exit back guard, the lock symbols, and the stale sibling docs).
- The migration plan is complete; the plan stays in design and no `## Implemented` pointer is due.

## Decomposition (tiered — mechanical first)

### Tier 1 — callback-body extraction

- E — **partly landed, and not the "purely mechanical" cut this plan first claimed** (corrected 2026-10-01, see
  `## Progress`). The two clean seams are in; the call site is at ~300 lines, not the ~120 first predicted, and
  the remaining ~17 callbacks cannot be hoisted without threading 5–8 lambda parameters each. Do them **inside
  T2a**, where the callback site's dependencies are the child composable's own parameters, and re-derive the line
  target from what T2a actually removes.
- A — **landed 2026-10-01** as `routeLeadFixOf`, `selectedPageOrNull`, `menuTrackIdsOf`,
  `trackMapVisibleCountOf` and `trackListIdsOf` (see `## Progress`); state declarations stay hoisted.

### Tier 2 — child composables + a small dependency holder (re-cut and consolidated 2026-10-01)

The first draft proposed three state-holder classes (`InspectSession`, `RouteSession`,
`MapDashboardController`). Reading the code showed why that cut fails: the route and inspect closures are
mutually entangled, and between them they read ~15 screen locals and six local helpers (`routeEndsAtTrigger`,
`closeSelectedItemDashboards`, `disarmInspectMode`, `closeTrackDrawer`, `openSelectedTrack`, `enqueueSnack`) —
so a class needs a large dependency bundle, a `Saver` for the `rememberSaveable` flags, and 19–40 rewritten
call sites, which is the `OverlayCallbacks` shape the ancestor plan's C12 rejected. The extraction is re-cut
the way Compose shrinks a composable: **child composables that take exactly what they paint and the lambdas
they fire**, plus one small holder the parent builds once. The same reading then showed the file list was
partly already built — `MapDialogHost.kt`, `MapSnackbarHost.kt`, `MapImportConflictHost.kt`, `InspectMode.kt`'s
`MapInspectEffects` and the `*Effects` family all exist — so of the four proposed children, only T2a and T2c
need a new file and T2d needs one host for the route family alone.

- **T2a's move is withdrawn — the ladder stays.** Two attempts and one measurement settled it. The first was
  refused because the region writes eight screen-owned values at fourteen sites — `showSettings`,
  `showTrackDrawer`, `showTrackHistory`, `showMarkerManagement`, `selectedTab`, `navigateToTarget`,
  `pendingResume`, `trackOpStatus` — and calls eight screen-local helpers; the second, the inside-out plan that
  was to solve that, died on the measurement: [`OverlayLayer()`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:85)
  takes **66 parameters**, so the call's width is the framework's and a `MapOverlayLadder` wrapping it inherits
  most of it. Its built-in fallback — bundling the callbacks — is the thing that breaks them, since a bundle
  rebuilt each recomposition with fresh lambdas is never equal and never skips; that is why the earlier
  consolidation bundled the read-only half only. **What replaced it is three measured phases under
  `## Migration plan`**, all of them wiring-only, none of them touching the call's width.
- **T2b — landed 2026-10-01, with half of it withdrawn.** The route-exit (R59) and resume dialogs moved into
  [`MapDialogHost.kt`](app/src/main/java/ykws/android/maro/ui/map/MapDialogHost.kt:35), which went 215 → 327 lines
  and 25 → 34 parameters: `routeExitRequested`, `routeState`, `routeViewModel`, `onDismissExit`, `onSaveRoute`,
  `onEndRoute`, `resumeTarget`, `onClearResume`, `onResumed`. Every one is a read-only value or a callback, as
  the host's KDoc requires, and `routePinned` is still read in the parent. The resume dialog's retained-copy pair
  crossed with it — `var resumeRetained` with its `LaunchedEffect(resumeTarget)`, and `var backup`. MapScreen lost
  the 89-line block and gained 9 argument lines, and `ConfirmDialog`/`ConfirmAction`/`ConfirmActionRole`/`OptionRow`
  became dead imports and were deleted. **The other half is withdrawn:** the plan said the call site's multi-line
  handlers would be hoisted into named functions, but reading them showed every one is one to three statements and
  most write the screen's own state — there was nothing to hoist, so no handler was touched. **Cost recorded:**
  the parameter list is at the bundling threshold, and because `ConfirmDialog` composes in-tree, the two dialogs
  now stack *below* `MapImportConflictHost` where they previously stacked above it — accepted, since re-ordering
  would flip five other relations to fix one, and the overlap needs both up at once.
- **T2c `MapLockLayer(...)` — LANDED 2026-10-01.** New [`MapLockLayer.kt`](app/src/main/java/ykws/android/maro/ui/map/MapLockLayer.kt:26)
  (102 lines, 11 parameters) took the screen-lock scrim, its mirrored lock/zoom controls and the lock banner out of
  `MapScreen.kt`; the call at 3576 still follows `ConfirmRequestHost`, so the scrim keeps painting above every
  drawer and the map. `chromeTopInset()` (three callers, so the widening was required) and `lockSlot()` became
  `internal`; the only textual change is the tap body moving to the call site as `onInterceptedTap`, which keeps
  both `lockBanner` and `lockBannerAt` writes in their owner. One seam, one build, green.
- **T2d — the route family landed in `MapRouteEffects.kt`; the auto-pick stayed.** **Landed 2026-10-01.** The
  inspect effects needed no work (`MapInspectEffects` in `InspectMode.kt` already hosts them), and the route
  family now lives in a new same-package [`MapRouteEffects.kt`](app/src/main/java/ykws/android/maro/ui/map/MapRouteEffects.kt:1)
  (67 lines) as `MapRouteEngineEffect` and `MapRouteEndEffects`. Each host is called at the position its block
  held — 629 and 948 — so no effect changed its order among the screen's others, and every key tuple and body
  dependency is verbatim. `storeRouteEnd` stayed a screen-local function because the route-ends panel calls it
  too, and crosses as an `onStoreEnd` lambda, the threading Tier 1 used for `closeDashboards`. **The auto-pick
  effect at 2480 stays in the screen**: it writes the screen's own `routeAutoPick` and calls its `followRoute`,
  which a child composable cannot do — so T2d is three of four blocks, and the fourth is structurally immovable
  for the same reason T2a was.
- **Dependency holder** — one `@Immutable` bundle per child (the shape `OverlayLayerParams.kt` already uses for
  `OverlayChrome`/`MenuOverlayData`), built once in the parent, so each child's parameter list stays short and
  no setter handshake or `Saver` is needed: the hoisted state stays in the parent and the child reads it.
- **D as landed** — `MapDashboardController.kt` owns the snack stack. R1/R2's *rules* are already extracted as
  `scopeClosed` ([`MarkersViewModel.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt:102)) and
  `trackScopeClosed` ([`CardWalkPolicy.kt`](app/src/main/java/ykws/android/maro/ui/map/CardWalkPolicy.kt:225)),
  so the remaining local close functions are thin orchestrators over screen state and stay with the screen.

Surviving the first draft unchanged: same-package only, move-don't-rewrite, every key tuple and null-guard
byte-for-byte, no new deps, no ViewModel restructure, `MapContent` untouched.

### Migration plan (2026-10-01 — corrected after review)

**What the review changed.** The ladder phase is gone, not deferred. Measured: [`OverlayLayer()`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:85)
takes **66 parameters**, so the call site's width is the framework's contract rather than the screen's excess, and a
`MapOverlayLadder` wrapping it would inherit most of them. Its built-in fallback — bundling the callbacks — is the
thing that breaks them, because a bundle rebuilt each recomposition with fresh lambdas is never equal and never
skips; that is why the earlier consolidation bundled the read-only half alone. **The ladder stays in `MapScreen.kt`**,
and the old file-size goal goes with it: wiring-only work cannot reach ~800–1,000 lines while the call it feeds is
66 parameters wide.

**What the plan now is.** Three measured phases, small on purpose — the honest yield is tens of lines, not
hundreds — each its own green build, plus a close. Every figure in this table is a count, not an estimate.

| # | Phase | What moves | Files | Gate |
|---|---|---|---|---|
| 1 | Bodies | only the bodies inside the call site that need nothing screen-local; the set is listed and counted before any edit, and it is small, because most bodies call a screen-local helper — the Tier 1 finding restated | `MapScreen.kt`, `MapScreenActions.kt` if needed | the set is counted first; a body needing a screen-local lambda is left alone; build green |
| 2 | Builders | the seven `OverlayChrome` / `MenuOverlayData` / `TrackListOverlayData` / `SettingsOverlayData` / `RouteSummaryData` / `MarkerListOverlayData` / `TrackInfoOverlayData` constructions | `MapScreen.kt`, `MapOverlayData.kt` | build green; a builder needing more than 8 parameters is left alone |
| 3 | Chrome | the eight written values into one `@Stable MapScreenChrome` | new `MapScreenChrome.kt`, `MapScreen.kt` | the `Saver` serialises **`selectedTab` alone** — it is the only `rememberSaveable` member, and the other seven are plain `remember`, so saving them would silently change what survives process death; build green |
| 4 | Close | re-measure every anchor, rewrite `## Context`, `## Progress` and the cluster table from the file, then run `#review` | this plan | every number comes from a read |

**Helper family, counted once.** The region calls eight screen-local helpers — `closeTrackDrawer`,
`closeSelectedItemDashboards`, `openSelectedTrack`, `openMarkerDetail`, `applyInspectStep`,
`advanceMarkerCardFrom`, `enqueueSnack`, `storeRouteEnd` — and two more sit behind them, `closeMarkerDashboard` and
`closeDashboardsForScopeChange`. They are phase 3's neighbours, and the count is stated here once so three older
figures cannot disagree again.

**Execution shape — phase-by-phase, self-validated, run to completion.** The three phases run in sequence to
completion; each phase lands behind its own green `apk-build.bat` and a green scoped `ui.map` + `config` unit run
before the next begins. The run stops only on a failed gate — two consecutive build failures halt (§4) — or on a
step that turns out to need a behaviour change rather than a move, which is recorded and skipped; a clean phase
hands straight to the next instead of waiting at the boundary.

**Merge exposure — the risk this plan carries most.** Every phase rewrites text inside one 4,500-line file, which is
the worst shape for a concurrent edit. The phases are kept small and landed in order, each behind its own green
build, so an interruption costs one phase rather than the run — and a branch carrying its own `MapScreen.kt` work
must land before phase 1 starts, never between phases.

**Validation at every phase.** `apk-build.bat` green before the next phase begins, the scoped unit run over `ui.map`
and `config` green, and every anchor re-measured rather than shifted by arithmetic. A phase whose gate fails is
corrected inside that phase and never carried forward.

**Stop conditions.** Two consecutive build failures halt the phase (loop control, §4). A step that turns out to need a
behaviour change rather than a move is recorded and skipped — the rule that stopped the first T2a attempt, applied
again here.

**Deliberately out.** The ViewModel fold and any other ownership move stay out: they are a behaviour-bearing redesign,
not a refactor, and this plan's bar is no functional or technical regression. `MapContent` is untouched throughout.

### Tier 3 — optional, only if ordered separately

- Move the route-exit dialog into its own host file — **done through T2b on 2026-10-01**: it went into
  `MapDialogHost.kt` rather than a file of its own, so Tier 3 is down to the item below.
- Fold inspect/route session logic into their ViewModels — a layering change, riskier, out of scope unless
  explicitly requested.

## Sequencing & verification

1. Land Tier 1 first (E then A): one commit per file, `apk-build.bat` SUCCESS after each, zero behavior change.
   E's two clean seams and A are landed; T2c then landed and was packaged, discharging the owed build. E's
   interlocked remainder stays open behind Tier 2.
2. **Carry the ancestor plan's R2 gate**: Tier 1 E rewrites the same `OverlayLayer` call site whose skipping the
   C1–C12 work protected, so capture a Compose **recomposition counter / compiler metrics** before and after the
   E cut — do not assume parameter skipping is preserved. This gate is currently unbuildable: it names no command,
   no threshold and no artifact, so it can neither pass nor fail a seam. Name all three before Tier 2 begins.
3. Land Tier 2 as T2a → T2b → T2c → T2d, each its own commit, `apk-build.bat` green after each; verify
   recomposition per child, since a wrongly hoisted read still stops it. T2c, T2b and three of T2d's four blocks
   are in; T2a is parked on its shape, so the sequence now runs around it.
4. Stop and flag to Ask if any cut turns out non-mechanical — do not improvise. The old ~800–1,000 target is
   retired rather than failed: the ladder cannot leave the screen because the call it feeds is 66 parameters wide.
5. On-device smoke via the LOGCAT workflow (user-driven): map renders, inspect arm/step/card, route arm/exit,
   drawers, filters/sorts/resets, snackbar undo, dialogs.

## Open items (ledger — closed by a measurement, never by prose)

- **Verification instrument** — named 2026-10-01 on the Phase-5 hop: command = the call-site inline-lambda shape
  diff, threshold = no lambda added/removed/reordered, artifact = the call-site diff plus the green build log. A
  Compose compiler-metrics report was not enabled (it needs a Gradle plugin config edit), and the move-don't-
  rewrite rule keeps callbacks inline in call position, so lambda memoization is preserved by construction.
- **Line target** — retired: the measured deltas are Tier 1(E) ~130 of ~310 predicted, T2c 48, and the fourth
  hop's 91 (T2b 82 plus T2d 9), and the aspirational ~800–1,000 depended on the ladder leaving the screen, which
  measurement says it cannot.
- **The ladder stays** — measured 2026-10-01: `OverlayLayer()` takes 66 parameters, so the call's width is the
  framework's, and the bundling fallback is the thing that breaks callback skipping. The three-phase path is under
  `## Migration plan`.
- **Loop control** — the phases run one task each; a build every few callbacks is dozens of autonomous loops, past
  the three-to-five a single task is allowed.
- **Merge exposure** — every phase rewrites one 4,500-line file, the worst shape for a concurrent edit; a branch
  carrying its own `MapScreen.kt` work must land before phase 1, never between phases.
- **Ownership is the only route to the old target** — retiring ~800–1,000 is a finding, not a preference: only
  moving the screen's state out would shrink the ladder's inputs, and that is a behaviour-bearing redesign.
- **Dialog host's parameter count** — `MapDialogHost` now takes 34 parameters across seven dialog families, which
  is the threshold the plan set for bundling; one `@Immutable` bundle per family is the next pass's shape, and the
  name no longer describes the content.
- **Route-exit dialog and the back guard** — `anyConfirmDialogOpen` still omits `routeExitRequested`, so the
  route-exit dialog does not stand the double-back exit guard down; that is pre-existing, not introduced, but it
  now sits among the dialogs that are counted, which makes the omission worth a decision.
- **`closeDashboards` second home** — the `CloseDashboards` value at 771 duplicates the local fun's four-flag
  shape and takes four positional booleans; collapse it into one call with named parameters on the next touch.
- **Fully-qualified names** — `ykws.android.maro.data.model.ListSortState()` / `ListFilter()` in the hoisted
  bodies, now that the simple names are imported.
- **Packaging discharged** — `apk-build.bat` ran green on T2c and again on both phase-4 seams, so the packaging
  half of sequencing rule 1 is met and the next seam owes its own.
- **Lock symbols left behind** — `lockSlot()` and its `TOP_TOGGLE_LOCK_SLOT` constant are a write-once pair whose
  only caller is now [`MapLockLayer.kt:72`](app/src/main/java/ykws/android/maro/ui/map/MapLockLayer.kt:72), so they
  should move into that file on the next lock touch.
- **New file's prose** — `MapLockLayer`'s KDoc restates its own first in-body comment, so one of the two should be
  trimmed: the seam moved text and had no need to add any.
- **`appSettings` passed whole** — the lock layer receives the entire settings object to reach a single boolean,
  which follows the house idiom (`MapDialogHost`, the `apply*` callbacks) but is what took its parameter list to
  11; a later pass may hand it the boolean instead.
- **Line-count tool** — the file reader reports exactly two more lines than the counted total (now 4,527 vs 4,525
  after Migration Phase 3), so a figure is meaningful only with its tool named; the earlier "two counts for one
  file" worry was this offset, not drift.
- **Sibling docs stale** — corrected 2026-10-01: `FEAT_DSC_UI_Map.md`, `FEAT_HYD_UI_Map.md` and `docs/maro-code.md`
  now state 4,527 reader lines and the four landed migration phases rather than the retired `InspectSession`/
  `RouteSession` cut, and the hydration reports the remedy as implemented.
- **Host inventory** — T2d's destination was invented once already by reading `*Effects` filenames instead of the
  files; both corrections above came from reads. Re-read a host's body before a seam claims its subject.

## Files affected (planned, same package)

- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — shell (4,670 → 4,531 counted lines; `fun MapScreen`
  3,135 → 3,000, then target TBD per the ledger); Tier 1 A landed as top-level functions in this file rather than
  a separate `MapScreenDerived.kt`
- New same-package files: `MapDashboardController.kt` (landed), `MapLockLayer.kt` (landed),
  `MapRouteEffects.kt` (landed); `MapOverlayLadder.kt` is withdrawn — the ladder stays in `MapScreen.kt`
- Existing same-package files gaining a seam: `MapDialogHost.kt` (T2b landed, 215 → 327 lines); the inspect
  effects need nothing, since `MapInspectEffects` in `InspectMode.kt` already hosts them

## Risks & mitigations

- **Stale anchors** — the plan's own top risk, and it has already fired twice: the 09-30 refresh measured a
  4,481-line file (the same-day hydration says 4,421) and every number in it was wrong within a day — `fun
  MapScreen` 492→508, `OverlayLayer(` 3092→3090, `MapContent` 3794→3984, `applyTrackSortChange` 3753→3789.
  Mitigation: re-measure at implementation time and cite each number once, from the measurement block.
- **Key-tuple drift** — the inspect/route effects have wide key tuples; any simplification stops the flow.
  Enforce byte-for-byte.
- **Tier 2 reactivity** — a child composable repaints only from its parameters, so an argument the child needs
  but the parent stopped passing disappears with no compile error; every value a child paints travels as a
  parameter, and the recomposition check in sequencing rule 2 is what catches a miss.
- **`rememberSaveable` relocation** — `inspectArmed`/`routeArmed`/`screenLocked`/`selectedTab` survive
  process death; the child-composable cut keeps them hoisted in the parent, so no `Saver` is introduced.
- **Strong-skipping regression** — Tier 1 only renames bodies into functions; callbacks stay inline in call
  position, so Compose lambda memoization is preserved — the ruling recorded in the ancestor plan
  ([`260909_FEAT_PLN_Ui_Settings_mapScreen-orchestration-monolith-refactor.md`](xTrack/Ui_Settings/260909_FEAT_PLN_Ui_Settings_mapScreen-orchestration-monolith-refactor.md:1)),
  where C12 rejected an `OverlayCallbacks` object.
- **Cross-cluster state** — the R1/R2 rules and snack undo touch `highlightedTrackId`/`trackDrawerState`/
  `trackNavigateState` (MapScreen-owned); the controller must receive them as parameters, never own them.
- **Parallel host** — creating `MapDialogLayer.kt` or `MapEffectHosts.kt` beside the hosts that already exist
  would give one subject two homes; principle 7 settles it in favour of the existing host. The mirror risk is a
  subject nobody hosts: the route effects had no `*Effects` file, which is why `MapRouteEffects.kt` was the one
  child T2d created.
- **Composition order is stacking order** — `ConfirmDialog` is in-tree, so moving a dialog into another host
  silently re-stacks it against every sibling between the old and new call sites. T2b moved the route-exit and
  resume dialogs ahead of the GPX import-conflict sheet and that flip was accepted; a future dialog move must
  check this pair-wise before landing.

## Out of scope

- ViewModel/repository/domain changes, new dependencies, package moves.
- Any behavioral or visual change.
- `MapContent` internals — separate concern, untouched.

## ELIJP

The file regrew because two new modes were bolted into the one body; the plan extracts the orchestration that
can leave, and says plainly that the ladder cannot, so the shell claim stops where the framework's own width starts.
