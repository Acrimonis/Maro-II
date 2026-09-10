<!-- scope: feature -->

<!-- C12 APPENDIX START (Ask + Code reviewed 2026-09-10) -->

## C12 — OverlayLayer parameter-object collapse (Ask-reviewed; supersedes the first draft)

### Inventory — verified against the working tree, [`OverlayLayer.kt:83`](../../app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:83)

- **89 named params** (lines 85–194) — not ≈60. Composition: **42 read-only values**, **45 function-typed params** (44 Unit callbacks + `trackTitleLookup`), **2 ViewModels**.
- **Exactly one call site:** [`MapScreen.kt:1498`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1498)–`1773`. Repo-wide `OverlayLayer(` search = 2 hits (definition + this call) → no tests or previews to migrate.
- **All 89 params are passed explicitly** at that call site → every signature default is currently dead code.
- Consumer surface → body lines: scrim `:208-232`, wizard `:243`, menu `:305-349`, marker drawer `:363-418`, track info `:422-632`, track history `:647-685`, marker mgmt `:701-719`, settings `:735-747`.
- **Re-verified 2026-09-10 on `feature/refact-C12`** (post-PR-#225, tip `9d95a16`): C1–C11 seam files all present and wired in `MapScreen.kt` — `MapGpsFollowEffects` `:711`, `MapDepthRasterEffects` `:724`, `MapMarkerEffects` `:748`, `MapMarkerDebugEffects` `:851`, `MapServiceEffects` `:860`, `MapTrackOverlayHistoryDiff` `:870`, `MapTrackOverlayLiveEffects` `:880`, `MapSnackbarHost` `:1784`, `MapDialogHost` `:1794`, `MapImportConflictHost` `:1853`; `RecordingExitSheet`/`ImportConflictSheet` correctly remain at `:2450`/`:2522`; file is 2,587 lines. All inventory counts (89 / 42 / 45 / 2) and the single call site `1498–1773` reconfirmed.

### Read-only vs callback — classification corrections

- **C was mis-scoped** — these are *menu-referential*, not track-history: `trackMapFilterState` `:137` (consumed `:331`), `trackFilterLinked` `:140` (consumed `:334` **and** `:672`), `trackMapCount` `:142` (consumed `:314`).
- **E was mis-scoped** — same pattern: `markerMapFilterState` `:189` (consumed `:337`), `markerFilterLinked` `:192` (consumed `:340` **and** `:716`), `markerMapCount` `:194` (consumed `:315`).
- **F over-reached** — `appSettings` `:145` is also read by `TrackHistoryOverlay` `:674-683`, not only by `SettingsOverlay` `:736`.
- **Group G fold was wrong** — `boatPosition` `:156` feeds `MarkerDrawer` `:367`/`:389`/`:413` (not `MarkerManagementOverlay`); `trackTitleLookup` `:176` is shared by `MarkerDrawer` `:369`/`:391`/`:415` **and** `MarkerManagementOverlay` `:703`, and is a function type, so by the draft's own rule it is a callback.
- **Layout is not state** — `isLandscape`/`portraitDashboardHeight`/`landscapeDashboardWidth` `:94-96` are read by 7 of 8 surfaces (`:250-288`, `:298`, `:353`, `:395`, `:424`, `:531`, `:636`, `:693`, `:727`).
- **Effectively-constant callbacks today** (capture only `MutableState`-backed locals [`MapScreen.kt:389-393`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:389)): `onDismissSettings`, `onDismissMenu`, `onDismissTrackHistory`, `onDismissMarkerManagement`, `onOpenTrackHistoryFromMenu`, `onOpenMarkerManagementFromMenu`, `onOpenSettingsFromMenu` (`MapScreen.kt:1509-1519`), `onTabChange` (`MapScreen.kt:1608`).

### Final grouping — by consuming surface, read-only only

| Bundle | Fields (count) | Consumer |
|---|---|---|
| `OverlayChrome` | showSettings, showTrackDrawer, showTrackHistory, showMarkerManagement, showWizard, wizardStep, drawerState (7) | scrim + wizard + all `DrawerSlot` visibility |
| `MenuOverlayData` | gpsMode, autoShowMasterVisible, autoShowMasterOverride, gpsToggleColor, markerZonesVisible, tracksDirectionVisible, firstTrackId, firstMarkerId, trackMapFilterState, trackMapCount, markerMapFilterState, markerMapCount (12) | `MenuDrawerOverlay` `:305-349` |
| `TrackListOverlayData` | trackSortState, trackFilterState, trackListState (3) | `TrackHistoryOverlay` `:647-685` |
| `MarkerListOverlayData` | markers, markerSortState, markerFilterState, markerListState (4) | `MarkerManagementOverlay` `:701-719` |
| `TrackInfoOverlayData` | showTrackInfoDrawer, trackInfoDrawerData, trackListIds, currentTrackIndex (4) | track-info slot `:422-632` |
| `SettingsOverlayData` | selectedTab, displayScrollState, navigationScrollState, positionScrollState, systemScrollState (5) | `SettingsOverlay` `:735-747` |

**Stay explicit (8):** `isLandscape`, `portraitDashboardHeight`, `landscapeDashboardWidth` (cross-cutting geometry), `trackFilterLinked`, `markerFilterLinked` (menu **and** list), `appSettings`, `boatPosition`, `trackTitleLookup` (shared). Plus the 2 ViewModels and **the 44 callbacks inline**.

**Dropped from the draft:** `OverlayCallbacks`, the group-G folds, and folding layout dims into a state bundle.

### Naming

Avoid the `*State` suffix: [`MarkerDrawerState`](../../app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt:41) (sealed) and [`TrackDrawerState`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:224) already own that namespace, and [`SettingsOverlay`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:211) is an existing composable. Draft names `OverlayLayerUiState` / `MenuDrawerState` / `SettingsOverlayState` are rejected.

### Tiered ordering

- **Tier 0 (pre-flight, no code):** refresh the stale citations below; **create** the bundle host file — ⚠ `MapScreenState.kt` does **not** exist in the tree (verified 2026-09-10; the C12 row's reference to it is a phantom), so the bundles need a **new** same-package file (`OverlayLayerParams.kt` recommended) or a block inside `OverlayLayer.kt`; decide visibility; lock the default-retention policy (keep the defaults on the 8 explicit values + the 44 callbacks); and pick the recomposition-measurement mechanism (R10).
- **Tier 1a-i:** `OverlayChrome` (7 params — the true pilot; `drawerState` + `wizardStep` are the trickiest field types). **Tier 1a-ii:** `MenuOverlayData` (12 params). Split so a field mis-map is isolable below pair granularity (R1 is High).
- **Tier 1b:** `SettingsOverlayData` + `TrackInfoOverlayData`.
- **Tier 1c:** `TrackListOverlayData` + `MarkerListOverlayData`.

### Exact edits
1. `OverlayLayer.kt`: declare the 6 bundles top-level in the same package with **fields named identically to the current params**; replace the read-only params with the 6 bundles; keep the 8 explicit values + 2 ViewModels + the 44 inline callbacks. Add a destructure block at the top of the body (`val showSettings = chrome.showSettings` …) so the body and its child calls stay byte-for-byte unchanged. Two additions: (a) the bundles need a **new import** — `androidx.compose.runtime.Immutable`, currently absent from the entire `ui/map` package; (b) the destructure block **grows per tier** (7 → 19 → 24 → 28 → 35 lines as each bundle lands) — writing all 42 lines up front would reference fields that do not exist yet and break the build.
2. `OverlayLayer.kt`: **delete the `rememberLazyListState()` defaults** on `trackListState`/`markerListState` (`:171-172`) — a `@Composable` call cannot be a data-class field default; the call site already passes both ([`MapScreen.kt:1771-1772`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1771)).
3. `OverlayLayer.kt`: remove the orphaned [`rememberLazyListState` import](../../app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:56) (repo target is a zero-warning build).
4. `MapScreen.kt` call ([`1498`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1498)–`1773`): rewrite the arg list to construct the 6 bundles inline. **Callbacks keep their current inline form — do not wrap them in an object and do not `remember` anything.**
5. Child composable calls inside the body keep receiving the same values via the destructured locals (menu `:305-349`, marker drawer `:363-418`, track info `:422-632`, track history `:647-685`, marker mgmt `:701-719`, settings `:735-747`).
6. Docs to sync in the same commit: [`docs/ui-drawer-guidelines.md:191`](../../docs/ui-drawer-guidelines.md:191) (the "wire … through OverlayLayer's parameter list" procedure), [`docs/maro-code.md:92`](../../docs/maro-code.md:92), [`FEAT_DSC_UI_Map.md:150`](../UI_Map/FEAT_DSC_UI_Map.md:150), [`FEAT_HYD_UI_Map.md:22`](../UI_Map/FEAT_HYD_UI_Map.md:22), [`FEAT_DSC_Ui_Menu.md:11`](../Ui_Menu/FEAT_DSC_Ui_Menu.md:11), `FEAT_HYD_Ui_Settings.md`, and this plan's status line.
7. Citation-drift pass: the destructure block plus the shrunken signature shift every line below in `OverlayLayer.kt`, invalidating the ~30 xTrack/doc anchors into that file (e.g. [`Ui_Menu/260909_FEAT_PLN_Ui_Menu_menu-render-upt.md:16`](../Ui_Menu/260909_FEAT_PLN_Ui_Menu_menu-render-upt.md:16) → now-stale `OverlayLayer.kt:285`; [`Ui_Dashboard/260908…item-dashboard-min-size.md:59`](../Ui_Dashboard/260908_FEAT_PLN_Ui_Dashboard_item-dashboard-min-size.md:59) → `:337/:360/:379/:404/:511`; [`Ui_General/260904…landscape-drawer-settings-sizing.md:26`](../Ui_General/260904_FEAT_PLN_Ui_General_landscape-drawer-settings-sizing.md:26) → `:273/:625`; [`Ui_Settings/260909…mapScreen-settings-extract.md:73`](260909_FEAT_PLN_Ui_Settings_mapScreen-settings-extract.md:73) → `:714`).

### Compose-safety rulings
- **Destructure-at-top is truly body-preserving.** No param is referenced from a signature default, and no body local collides with the 42 names (`trackRecorderState` `:197`, `trackSummaries` `:198`, `activeStep` `:242`, `isAtTrackFirst` `:422`, `isAtTrackLast` `:423`, `track` `:506`, `summary` `:507`, `cardHeight` `:526`, `footerMeasuredHeight` `:527`, `targetHeight` `:531`, `animatedHeight` `:532`).
- **`selectedTab` stays a plain value:** `rememberSaveable` [`MapScreen.kt:534`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:534) → `:1607` → `OverlayLayer.kt:147` → `:740`. An inline-constructed bundle does not move the `rememberSaveable`; only a `remember`ed holder would (still forbidden).
- **Vanished defaults are safe:** all 89 params are supplied at the single call site, so dropping them is behaviour-neutral — there are **45** defaults (not ~60). ~20 sit on read-only params that disappear into the bundles; ~25 sit on callbacks that stay. Recommendation: **keep** the defaults on the 8 explicit values + the 44 callbacks (smaller diff, zero risk). The only exception to "just delete the default" is the `trackListState`/`markerListState` pair (composable default, unrepresentable in a data class).
- **`@Immutable` is a promise, not a check.** 7 bundled fields are mutable holders (`drawerState`, 4× `ScrollState`, 2× `LazyListState`). Correctness still holds: those holders are `remember`ed in `MapScreen`, so identity (and therefore `data class` equality) is stable across recompositions, and every consumer reads `.value`/`.currentValue` directly — its own scope is invalidated regardless of parameter skipping. Add a KDoc contract line on each bundle ("all fields `val`; mutable holders are read via their own state, never via equality") so a later edit cannot quietly break it. If the team prefers not to rely on that, drop `@Immutable` and let the measurement decide.
- **`OverlayCallbacks` is dropped.** Moving 44 lambdas out of composable-call argument position (where the Compose compiler memoizes them) into a plain constructor call plausibly defeats that memoization — Kotlin `2.1.20` + compose-bom `2026.05.00` implies strong skipping is on, so several of these params currently compare equal and let `OverlayLayer` skip. It also saves no call-site lines (44 `x = …` lines either way) and is the only part with a stale-capture trap. If a smaller signature is ever wanted, split callbacks per consumer — behind a recomposition counter proving no regression.
- **Correction to the C12 row's rationale:** `appSettings` is a `by collectAsState()` local delegate ([`MapScreen.kt:387`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:387)), so reads inside `onGpsModeChange` ([`MapScreen.kt:699-708`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:699), not `717-726`) go through the `State` object at call time — a `remember`ed lambda would *not* freeze that read. `onGpsModeChange` is also a hoisted `val` lambda, already re-created each recomposition. Re-verify before treating "remember freezes the GPS toggle" as a hard rule. `screenLocked` is not an `OverlayLayer` parameter at all.

### Verification
- Mechanical first: diff the 42-line destructure block against the old signature name-by-name (42 vs 42) so a field mis-map is caught by inspection, not by smoke test.
- `apk-build.bat` SUCCESS with **zero new warnings**.
- On-device smoke (LOGCAT workflow, user-driven): every drawer opens/dismisses; menu + list filters and both Link toggles; track-info prev/next; settings tabs.

### Migration risk register (final review 2026-09-10)

| # | Risk | Sev | Mitigation |
|---|---|---|---|
| R1 | Destructure field mis-map (42 fields) → wrong drawer state | High | Copy signature names verbatim into the destructure block; mechanical name diff **per tier**; one bundle per commit — Tier 1a split into 1a-i / 1a-ii so a mis-map is isolable below pair granularity; build + smoke after each |
| R2 | Recomposition/skipping regression — bundle fields include unstable types (`List`, `ScrollState`, `LazyListState`, `AppSettings`, `WizardStep?`, `MarkerDrawerState`) that can defeat Compose strong-skipping on `OverlayLayer` | High | Annotate the 6 bundles **`@Immutable`** (see the contract caveat in Compose-safety rulings); keep callbacks inline (OverlayCallbacks already dropped). Verify with a **recomposition counter / Compose compiler metrics before-vs-after per tier** — do not assume parity; the measurement mechanism has no existing tooling, so it must be chosen at Tier 0 (R10) |
| R3 | Line-anchor drift — signature shrink + destructure shifts every line below, invalidating ~30 xTrack/doc anchors into `OverlayLayer.kt` | Med | Land the citation-drift pass as an **immediate follow-up commit** (do not mix it into a tier commit — it would mask the code diff). Count is known; re-grep after Tier 1c |
| R4 | Merge conflicts — `OverlayLayer.kt` + `MapScreen.kt` are large shared files | Med | Rebase `feature/mapscreen-refactor` on `origin/develop` **before** starting C12; land per-tier commits promptly |
| R5 | `rememberSaveable` `selectedTab` regressed | Low | Stays a plain passed value; never folded into a `remember` holder (restated in Compose-safety rulings) |
| R6 | Removed signature defaults change behaviour | Low | Single call site supplies all 89 params (repo search: 2 hits = definition + this call) → defaults are dead code |
| R7 | Drawer z-order / scrim regression | Low | Grouping is value-only — no call reordering, no DrawerSlot changes; smoke all 8 surfaces |
| R8 | Build-loop budget / thrash | Low | Halt after 2 consecutive build failures (AGENTS §4); one bundle group per build |
| R9 | Bundle host file does not exist — `MapScreenState.kt` is absent from the tree, and `@Immutable` is used nowhere in `ui/map` | Med | Tier 0 **creates** `OverlayLayerParams.kt` (same package) and adds the `androidx.compose.runtime.Immutable` import; never write into a non-existent path |
| R10 | The recomposition/skipping gate is unexecutable as written — no measurement tooling exists in the repo | Med | Choose one at Tier 0: temporary `androidx.enableComposeCompilerMetrics=true` in `gradle.properties`, or a DEBUG-only `SideEffect` counter inside `OverlayLayer` removed after the tier |
| R11 | `#new` left the branch tracking `origin/develop` (git: "set up to track remote branch 'develop'"), so a bare `git push` under `push.default=upstream` would target the one forbidden branch | Med | `#push` is safe (explicit refspec); before any bare push run `git push -u origin feature/refact-C12` once, or `git branch --unset-upstream` |

### Rollout / rollback strategy
- **Pre-flight (Tier 0):** `#merge`/rebase onto latest `origin/develop`; pick the bundle host file + visibility; refresh the stale citations inventory.
- **Per-tier gate:** Tier 1a-i (`OverlayChrome`, 7 params) is the highest-value/lowest-risk pilot — land it **alone**, build, and capture the recomposition metric before attempting 1a-ii/1b/1c.
- **Rollback:** each tier is an independent commit → revert a single tier without touching the others. Do **not** squash the tiers, so a regression isolates to one bundle group.
- **Abort criterion:** if R2 shows a skipping regression that `@Immutable` cannot fix, stop after the failing tier and keep the remaining params as-is (partial C12 is still a net win).
- **Tier 1a-i result (2026-09-10, `feature/refact-C12`):** landed — `OverlayChrome` (7) in the new `OverlayLayerParams.kt`; `OverlayLayer` 89 → 83 params; single call site patched in place (2587 → 2589 lines); `apk-build.bat` SUCCESS with zero new warnings; Ask review 8/8 pass. One forced deviation: the bundle is **public**, not `internal` (`OverlayLayer` is public, so an `internal` type cannot be exposed).
- **Tier 1a-ii result (2026-09-10):** landed — `MenuOverlayData` (12) added; `OverlayLayer` 83 → 72 params; the 12 read-only params removed from three separate signature regions (menu 8, track-history 2, marker-management 2) with the 4 interleaved callbacks left in place; destructure 7 → 19 lines; `apk-build.bat` SUCCESS, zero new warnings; Ask review 8/8 pass, with the call-site expressions verified as not simplified (`trackMapCount = trackMapVisibleCount`, `markerMapCount = mapMarkersState.size`).
- **R10 waiver — EXTENDED to Tier 1a-ii (2026-09-10):** Ask reasoned the bundle is skipping-neutral-or-better than its pre-image — `MenuOverlayData` is `@Immutable`, all field types are value-stable (`ListFilter` is a data class), and it converts 2 previously-unstable `ListFilter` params into one stable bundle. No measurement was taken (no tooling in-repo; the cheap route is a one-off `gradlew -Pandroidx.enableComposeCompilerMetrics=true :app:assembleDebug` and diffing `app/build/compose-metrics/`, which needs two builds plus a base-ref comparison). **The waiver does NOT extend to Tier 1b:** `SettingsOverlayData` carries four identically-typed `ScrollState` fields — the highest silent cross-wire risk in C12 — so 1b needs a construction↔destructure name diff beyond the (already self-checking) destructure, and a measurement if any skipping anomaly appears.
- **Known nit (2026-09-10):** the comment near `OverlayLayer.kt:179` still says the block heads the chrome bundle although it now also heads the menu bundle — fold the reword into the next tier commit.

<!-- C12 APPENDIX END -->

# MapScreen orchestration-monolith refactor (code health) — step 2

**Status:** Ask-reviewed (2026-09-09); C1–C11 **verified landed in code** (all 8 seam composables wired in
`MapScreen.kt`, 3506 → 2587). **C12 re-reviewed + code-verified 2026-09-10 on `feature/refact-C12`** — read-only
bundles only, `OverlayCallbacks` dropped, Tier 1a split into 1a-i/1a-ii, coverage gaps C1–C8 folded in; see the
C12 APPENDIX above for the locked change list.

## Context

After the settings-subtree extraction (step 1, PR #224),
[`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1) was ~3,506 lines. After C1–C11 it is
~2,587 lines; the spans quoted below (`fun MapScreen` ~387→2847, `MapContent` ~2863→3355) are pre-refactor
references — `fun MapScreen` body ≈ 2,460 lines of orchestration plus `MapContent` and bottom sheets.
The remaining monolith bundles, in one body:

- **State:** ≈ **44** `collectAsState` across 4 view models (`NavigationViewModel`, `DepthViewModel`,
  `MarkersViewModel`, `TrackViewModel`) + ≈ **35** `remember`/`rememberSaveable` UI states.
- **Side effects:** ≈ **31** `LaunchedEffect` + 1 `DisposableEffect` (`1684`) that drive OSMdroid overlays,
  service intents, dialogs, settings wiring.
- **Local helpers:** `closeSelectedItemDashboards`, snack-queue fns, `openTrackDetail`, `openMarkerDetail`, …
- **UI host:** `MapContent` stable slot (comment 1806–08, call 1934), `OverlayLayer` mega-parameter call
  (`1498`→`1773` in the current tree, **89 params**, dozens of state + lambdas), dialogs/sheets/scrims,
  snackbar stack. ⚠ The `2316`→`2592` span quoted in the first draft predates C1–C11.

**Key render-location fact (drives seam scoping):** import-banner + trackOp-status banners render *inside*
`MapContent` (params at `2061`, rendered at `3324`/`3348`) — not in `MapScreen`. Any seam that claims to own
their "feedback rendering" would have to edit `MapContent` internals, which is **out of scope**. Their state
therefore stays hoisted in `MapScreen`.

## Goal

Refactor the orchestration monolith into focused, same-package files so `MapScreen` keeps only true
cross-cutting orchestration (drawer visibility, click-n-move navigation, back-handler, `MapContent` slot,
`OverlayLayer` invocation) and each file owns one concern cluster. **Zero behavior/visual change** —
mechanical, build-verified decomposition, one commit per seam (spirit of step 1). Target `fun MapScreen`
body ≈ 2,460 → ~800–1,000 lines.

## Design principles (locked)

1. **Same package, same visibility** — all extraction stays in `ykws.android.maro.ui.map`; only
   `private`→`internal` where a moved symbol crosses a file (e.g. `DIRECTION_ARROW_SPACING_DP` is `private`
   at `218` and becomes `internal`). Zero import churn outside the touched files.
2. **Move, don't rewrite.** No logic/string/color/layout change inside any moved composable.
3. **Key/guard fidelity.** Preserve each effect's exact key tuple and null-guard shape **byte-for-byte**
   (`run { Log…; return@LaunchedEffect }` at `1589`, `return@collect` mid-stream at `979`, plain
   `?: return@LaunchedEffect` elsewhere). Do NOT "simplify" keys to `Unit`, do NOT "normalize" guards, do NOT
   modernize one-shot effects — each is a behavior edit.
4. **Unconditional hosts.** Any moved effect that must survive runtime conditions stays in an
   unconditionally-composed child. ⚠ The demo-mode sample feed is `LaunchedEffect(Unit)` explicitly **NOT**
   keyed on `gpsMode` (`1118`) — toggling position source must NOT tear down/restart it → its host child must
   be unconditional.
5. **No new deps, no ViewModel restructure, no package moves, no `rememberSaveable` relocation.**

## Decomposition (Ask-corrected; each extraction mechanical & build-verifiable)

> ⚠ C1–C11 are landed (`MapScreen.kt` 3506 → ~2560), so the line spans in the table below are **pre-refactor
> references only** — use them as seam identifiers, not as navigation targets. C12 lives entirely in the
> C12 APPENDIX above.

| Commit | New file (same package) | Moves (MapScreen.kt line spans) | Inputs it needs (all forwarded, reused as keys verbatim) | Notes |
|---|---|---|---|---|
| C1 | `MapGpsFollowEffects.kt` | contiguous trio `728–820`: mapView-zoom re-apply, GPS auto-follow DR + heading-up, demo two-finger heading-up | `mapView`, `appSettings`, `viewModel`, `autoFollowSuppressed`, **`depthViewModel`** (auto-follow collect calls `depthViewModel.updateMapCenter` at `803`), `trackViewModel` | **Pilot.** ONLY the contiguous trio. The pause `DisposableEffect` at `1684` is NOT contiguous (~860 lines later) — do not fold into this seam. |
| C2 | stays in shell OR tiny move | pause/save-on-pause `DisposableEffect` `1684–1699` | needs `trackRecorderState` | Lifecycle observer is order-independent → may stay in the shell this feature; move only if convenient. |
| C3 | `MapTrackOverlayEffects.kt` | history/pinned incremental-diff `1193–1472` | `mapView`, `appSettings` (~15 keys), `showSettings`, `highlightedTrackId`, `allTrackSummaries`, **`viewModel`** (trailing polyline reads `viewModel.displayPosition` via snapshotFlow `1561`), `trackViewModel` | Owns `renderedTrackIds` (`1195`, written only `1469`) → file-local. `highlightedTrackId` is read-only key — parameterize, never relocate (it is cross-cutting: snack undo `484`, `openTrackDetail` `2280`, back handler). Key tuple at `1197–1204` preserved verbatim. |
| C4 | same file, 2nd commit | live-recording trio `1474–1584`: active trace, incremental point append, trailing dead-reckon | as C3 + `trackRecorderState`, `viewModel.displayPosition` | Split C3/C4 = two commits (file is the largest seam, ~390 lines). |
| C5 | `MapMarkerEffects.kt` | marker wiring `893–949`: settings bridge (`893–899` wires **both** `markersViewModel` + `trackViewModel.observeSettings`), crash-orphan cleanup `902` (Unit-keyed one-shot, runs once — move verbatim), marker-change watcher `912–921`, idle/`BoatMarker` callback `923–940`, `WhereAmIProvider` bridge `945–949` | `mapView`, `appSettings`, `markersViewModel`, **`trackViewModel`**, `userMarkers` | Relocate `markerChangeFlow` (`550`) + `skipFirstComposition` (`914`) file-local (single-owner). Cluster is 3 non-contiguous regions — acceptable: reorders only Unit-keyed one-shots. |
| C6 | `MapMarkerEffects.kt` (2nd commit) | marker ray-tracer sync `1046–1053` + WhereAmI debug-segment overlay render `1586–1612` (add `mapView`; keep guard shape at `1589`) | `mapView`, `appSettings`, `debugSegments` | These belong to the marker seam (missing from the original draft). |
| C7 | `MapServiceEffects.kt` | service intents: notification updates `1617–1649`, water-state push `1654–1680`, demo-mode sample feed `1120–1190` | `mapView`-free; `context`, `trackRecorderState`, `appSettings`, `boatIsWater`, `trackViewModel` | **Host must be UNCONDITIONAL** (demo feed, §principle 4). Former "system effects" split: data-producing depth/raster and track-event observer are NOT here. |
| C8 | `MapDepthRasterEffects.kt` (+ contract) | depth-layer + silent raster lazy-init `822–1109`: both `produceState` live-builds (`838`, `854`), both cached reads (`1087`, `1095`), effective-priority merge (`1106–09`) | `mapView`… , **RETURNS** `effectiveDepthBitmap`/`effectiveLowDepthWarning`/`depthReadout`/`depthBox`/`isobaths` | NOT a pure effect move — it must **return** an output model feeding `MapContent` `1945–48` + `DashboardPanel` `2084`. Rewire those param sources in the SAME commit. Regulated-zones `produceState` (`875`) extracted separately (own small seam). |
| C9 | `MapDialogHost.kt` (windows only) | windowed `AlertDialog`/`ModalBottomSheet`: exit/stop-recording `1759/1789`, recovery `2632`, bg-location `2682`, GPS-permission `2707`, source-switch `2732`, battery-opt `2747` | hoisted booleans + callbacks | **Lock scrim `2791–2845` does NOT move here** — it is composited last in the outer Box so it paints ABOVE `OverlayLayer`, needs `isLandscape` + dashboard paddings + `lockTopInset`. Keep as final child of the Box (or a dedicated `MapLockOverlay` that is guaranteed last). Windows tolerate repositioning (1759/1789 sit before MapContent today). |
| C10 | `MapSnackbarHost.kt` (render-only) | stack render `2602–2629`; `SnackRow` already top-level `280–319` | `activeSnacks`, `onUndo`, `onTimeout` | NOT a full ownership move: queue fns (`465–520`) write cross-cutting MapScreen state (undo reopens track → `highlightedTrackId`/`trackDrawerState`/`trackNavigateState` `484–493`; timeouts drive VM deletes; `pendingDeleteIds` written by OverlayLayer callbacks `2547/2568`, read at `2250/2527–38`). Extract render only; fold ownership into C12. |
| C11 | `MapImportConflictHost.kt` | conflict path only: `pendingTrackImport` state + `ImportConflictSheet` invocation + `runImport` `2652–2679` + OpenDocument `importLauncher` IO callback `661–704` | callbacks up; creates its own `rememberCoroutineScope` | Re-scoped from "import host": import/banner **state stays hoisted** (renders in `MapContent` — out of scope). Banner state `455–460` + `trackOpStatus` `462` remain in `MapScreen`. |
| C12 | `MapScreenState.kt` holder + `OverlayLayer` param collapse | collapse the read-only params ([`OverlayLayer.kt:85-194`](../../app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:85), 89 params) into 6 bundles at the single call [`MapScreen.kt:1498–1773`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1498) | — | **Last. Ask-reviewed — detail in the C12 APPENDIX above.** Read-only only: `OverlayChrome`(7) + `MenuOverlayData`(12) + `TrackListOverlayData`(3) + `MarkerListOverlayData`(4) + `TrackInfoOverlayData`(4) + `SettingsOverlayData`(5). 44 callbacks stay **inline** — `OverlayCallbacks` dropped (it would defeat Compose lambda memoization and save no call-site lines). C10's "fold ownership into C12" is **not** covered by this delta (no snack ownership move). `screenLocked` (`396`) is not an `OverlayLayer` param at all; `selectedTab` (`534`) keeps flowing from `MapScreen` `rememberSaveable` → `:1607` → `OverlayLayer.kt:147` → `:740` — never into a `remember` holder. ⚠ `appSettings` is a `by collectAsState()` local delegate (`:387`), so `onGpsModeChange` (`:699-708`, not `717-726`) reads it through the `State` object at call time — re-verify the "a `remember`ed holder freezes the read" claim before relying on it. |
| — | **Stays in MapScreen shell** | the 4 drawer-visibility booleans + fan, screen-lock toggle, click-n-move state (`navigateToTarget`/`trackNavigateState`), `MapContent` stable slot + map offset, `OverlayLayer` invocation, back-handler ladder, **track-event observer `961–1044`** | — | Track-event observer stays: widest-input effect in the file (`markersViewModel.drawerState` 965/971, `gpsPosition`/`mapCenter` 966, `mapView` 979, `trackViewModel`, `appSettings`, `OverlayZOrder`) and its `Resumed` branch **duplicates the live-polyline rebuild** seam C4 owns (980–1039). Do NOT split it across files (non-mechanical). Track-info error auto-dismiss `952` → with C4/C7 owner of `trackRecorderState`. |

## Implementation steps

1. **Inventory pass.** Read `MapScreen.kt` 385→2847; enumerate every state/effect against the table above;
   confirm no seam leaks cross-cluster state; lock the output model for C8 before touching it.
2. **Execute C1 → C12 in order**, each its own commit, `apk-build.bat` SUCCESS + no behavior change after
   every commit. Stop and flag to Ask if a cut turns out non-mechanical (do not improvise).
3. **Ask code-health review** of the finished branch, then report.

## Files Affected (candidate)

- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — orchestration shell (~2460 → ~800–1000)
- New same-package files: `MapGpsFollowEffects.kt`, `MapTrackOverlayEffects.kt`, `MapMarkerEffects.kt`,
  `MapServiceEffects.kt`, `MapDepthRasterEffects.kt`, `MapDialogHost.kt`, `MapSnackbarHost.kt`,
  `MapImportConflictHost.kt`, `MapScreenState.kt` (+ optional `MapLockOverlay.kt`)
- `MapContent.kt`-side param rewire in C8 only (same file `MapScreen.kt` — `MapContent` params at `2061`,
  calls at `1945–48`/`2084`).
- **C12:** `app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt` (signature + 42-line destructure block +
  `rememberLazyListState` import removal) and `MapScreen.kt` (call-site rewrite `1498–1773`). The 6 bundles live
  in the C12 host file — `MapScreenState.kt` or a dedicated `OverlayLayerParams.kt` (decide at Tier 0).

## Verification

- `fun MapScreen` body shrinks by ≥ ~1,500 lines with zero logic change.
- `apk-build.bat` SUCCESS (zero new warnings) after every commit.
- Runtime smoke on device: map renders; GPS follow + heading-up intact; tracks/markers draw/update; drawers,
  dialogs, snackbar, screen-lock z-order, demo-feed continuity, import conflict behave identically
  (on-device steps need the user to deploy — LOGCAT workflow).
- No import edits outside touched files (same-package moves).

## Risks & mitigations

- **Demo feed restart on mode toggle** — host must be unconditional (§principle 4); if re-created the
  recording sample feed restarts.
- **Key-tuple drift** (track-diff `1197–1204` ~15 keys) — simplifying keys stops the diff overlay refreshing.
  Enforce byte-for-byte.
- **mapView-touch effect `733`** has no `onDispose` and must stay unconditional + keyed on the MapView
  instance.
- **C12 callback grouping — dropped.** No `OverlayCallbacks`: wrapping the 44 lambdas in an inline-constructed
  object pulls them out of composable-call argument position, defeating Compose lambda memoization, for zero
  call-site line saving. Read-only bundle grouping is the whole C12 delta.
- **C8 output contract** — must rewire `MapContent`/`DashboardPanel` param sources in the same commit or the
  live/cached depth fallback priority changes.
- **`rememberSaveable`** (`screenLocked` 396, `selectedTab` 534) never moves — they are the only two in the
  body and both stay hoisted. `screenLocked` is not an `OverlayLayer` param.

## Out of scope

- ViewModel restructure, repository/domain changes, package moves, new dependencies.
- Any behavioral/visual change.
- `MapContent` internals (banner rendering at `3324/3348`, offset overlay layering) — separate concern.
- Track-event observer `961–1044` extraction (stays in shell).

## Ask-review notes (locked for implementation)

Structural drift corrected vs draft: state/effect counts were undercounts (~44/~35/~31 actual); import/trackOp
banners render in `MapContent` (drove seam-7 re-scope → `MapImportConflictHost`); seams 1–2 input tables
completed (`depthViewModel`, `viewModel.displayPosition`, `trackViewModel`); seam-3 omission (debug-segment
render `1586`) added; seam-4-as-listed **dropped** → split into `MapServiceEffects` (C7) + depth/raster seam
with output contract (C8); seam 5 split (windows host vs lock scrim kept last); seam 6 render-only (C10);
seam 7 re-scoped (C11); recommended commit order C1–C12 with C1 pilot = contiguous 728–820 trio only.

**C12 re-review (2026-09-10):** inventory recounted (89 params = 42 read-only + 45 function-typed + 2 ViewModels,
not ≈60); groups C/E re-cut by consuming surface (menu vs list, not by theme); `appSettings`, `trackFilterLinked`,
`markerFilterLinked`, `trackTitleLookup` kept explicit (cross-consumer); `OverlayCallbacks` dropped; layout dims
stay explicit. Verified: exactly one call site, all params supplied there (defaults dead), destructure-at-top is
body-preserving (no default references, no local-name collisions), `selectedTab` untouched. Detail: C12 APPENDIX.

## Branch note

C1–C11 merged into `develop` via PR #225 (squash, `9d95a16`, 2026-09-10). **C12 applies on
`feature/refact-C12`** (created 2026-09-10 from `origin/develop`). One low-risk refactor commit per seam
(C1→C12); the C12 tiers land as 1a-i, 1a-ii, 1b, 1c.
