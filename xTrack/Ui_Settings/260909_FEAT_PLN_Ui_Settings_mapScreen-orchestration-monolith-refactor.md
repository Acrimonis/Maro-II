<!-- scope: feature -->

# MapScreen orchestration-monolith refactor (code health) — step 2

**Status:** Ask-reviewed (2026-09-09). Locked for implementation on `feature/mapscreen-refactor`.

## Context

After the settings-subtree extraction (step 1, PR #224),
[`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1) is ~3,506 lines. `fun MapScreen`
spans ~387→2847 (body ≈ 2,460 lines of orchestration) plus `MapContent` (~2863→3355) and bottom sheets.
The remaining monolith bundles, in one body:

- **State:** ≈ **44** `collectAsState` across 4 view models (`NavigationViewModel`, `DepthViewModel`,
  `MarkersViewModel`, `TrackViewModel`) + ≈ **35** `remember`/`rememberSaveable` UI states.
- **Side effects:** ≈ **31** `LaunchedEffect` + 1 `DisposableEffect` (`1684`) that drive OSMdroid overlays,
  service intents, dialogs, settings wiring.
- **Local helpers:** `closeSelectedItemDashboards`, snack-queue fns, `openTrackDetail`, `openMarkerDetail`, …
- **UI host:** `MapContent` stable slot (comment 1806–08, call 1934), `OverlayLayer` mega-parameter call
  (`2316`→`2592`, dozens of state + lambdas), dialogs/sheets/scrims, snackbar stack.

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
| C12 | `MapScreenState.kt` holder + `OverlayLayer` param collapse | collapse `2316–2592` mega-param block into grouped state holders | — | **Last.** Tiered: read-only param groups → grouped data classes (mechanical/safe); mutation-callback consolidation → **guard against stale capture** (⚠ `onGpsModeChange` `717–726` closes over `appSettings.gpsMode` + `trackRecorderState.state` BY VALUE — a `remember{}` holder freezes the read and breaks the GPS toggle; same risk for ~15 toggle lambdas). `screenLocked` (`413`) + `selectedTab` (`552`) must keep flowing from MapScreen `rememberSaveable` through to OverlayLayer `2067`/`2425` — never folded into a `remember` holder. |
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
  calls at `1945–48`/`2084`); `OverlayLayer.kt` only if its signature changes (C12)

## Verification

- `fun MapScreen` body shrinks by ≥ ~1,500 lines with zero logic change.
- `apk-build.bat` SUCCESS after every commit.
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
- **Stale-capture in collapsed callbacks** (`onGpsModeChange` trap) — C12 only; read-only grouping safe.
- **C8 output contract** — must rewire `MapContent`/`DashboardPanel` param sources in the same commit or the
  live/cached depth fallback priority changes.
- **`rememberSaveable`** (`screenLocked` 413, `selectedTab` 552) never moves — they are the only two in the
  body and both stay hoisted.

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

## Branch note

Apply on `feature/mapscreen-refactor` (created 2026-09-09 from `origin/develop`, includes PR #224
filters-link). One low-risk refactor commit per seam (C1→C12).
