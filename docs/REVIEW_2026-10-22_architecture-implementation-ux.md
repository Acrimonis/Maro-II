<!-- scope: review -->
# Maro II — Architecture, Implementation and UX Review (2026-10-22)

> Read-only review of the whole app at commit `ae55e62b` (branch `claude/app-architecture-ux-review-dc01c9`).
> Method: a structural survey of every package, a full read of the entry points and the largest files,
> four parallel area reviews (data layer, spatial engine, Compose UI, UX) and a spot-check of every
> High-severity claim against source before it was kept. Nothing was built or run: this machine has
> no JDK or Android SDK, so unit-test and build results are taken from the repo's own records.
> Every finding names a file and a line so it can be re-verified. Paths are relative to
> `app/src/main/java/ykws/android/maro/` unless they start with `app/`, `docs/` or `xTrack/`.
>
> **Errata (2026-10-22, later the same day):** an independent assessment corrected ten claims here and
> added twelve findings, four of them High; a second pass added six more. The revised High list and
> roadmap live in `REVIEW_2026-10-22_response-to-assessment.md`, which supersedes §5 and §6 below.
> The main corrections: strong skipping is on, so §3.3's "can never skip" is withdrawn; the EN/FR
> lock labels are not inverted; the "GPS mode" settings row toggles the map offset, not GPS; the
> core has four Android/Compose import leaks; 287 main Kotlin files, not 491.

---

## 0. Verdict

- **The app is a serious, well-reasoned single-screen navigation tool with an unusually strong
  computational core and an unusually weak seam between that core and the screen.** The pure Kotlin
  layers (spatial, multipass, serializers, policies) are tested, documented and mostly clean. The
  layers that touch Android (service, recorder, map screen) carry the risk: a 3 800-line composable,
  a 1 700-line recorder written from three threads, a main-thread `runBlocking` on Stop, and a
  raster read on the UI thread.
- **Architecture, high level: B.** The stated layering (`docs/maro-code.md`) is sound and mostly
  honoured, but the app has no composition root, so repositories are instantiated three to five
  times and settings go stale inside the foreground service. Two architecture docs contradict the
  code (memory-mapping, "pure consumer").
- **Implementation, low level: C+.** Correct on the happy path, fragile under concurrency, crashes
  and growth. The riskiest logic (the recorder's spike gates, finalise, checkpoints) has zero tests
  while the engine has five hundred.
- **UX: C.** Thoughtful interaction design for an expert owner, but the product is silent where it
  must shout (no audio or haptic alarm for shallow water or speed limits), opens in a demo mode the
  user must discover their way out of, and ships roughly ninety settings controls plus developer
  surfaces.
- **Highest-impact fixes, in order:** (1) move `finalizeTrack` off `runBlocking`; (2) make
  checkpoint writes atomic and stop deleting user tracks on decode errors; (3) move the cached
  raster read off the main thread; (4) add a composition root so one `SettingsManager` and one
  repository per type exist; (5) add an at-the-helm alarm channel; (6) split `MapScreen`.

---

## 1. Shape of the codebase

| Measure | Value |
|---|---|
| Kotlin, main | 491 files, 76 612 lines (21 855 comment lines, 28 %) |
| Kotlin, test | 34 399 lines, ~190 files, 1 418 tests per the latest hydration |
| Largest files | `MapScreen.kt` 5 614 · `AppConfig.kt` 2 794 · `MapScreenSettingsOverlay.kt` 2 220 · `NavigationViewModel.kt` 2 025 · `TrackRecorder.kt` 1 718 · `MultipassPull.kt` 1 588 |
| ViewModels | 6 (`Navigation`, `Markers`, `Route`, `Depth`, `Track`, `Settings`) |
| Top-level singletons (`object`) | 61, of which ~6 hold mutable session state |
| Strings | 648 EN + 648 FR, 4 plurals |
| Settings fields | ~150 keys in one `AppSettings` data class (53 declared on the first page alone) |
| Config keys | `maro.properties` 215 · `colors.properties` 113 · `ui.properties` 33 |
| Dependencies | Compose BOM, Material3, osmdroid, OkHttp, kotlinx-serialization (json + protobuf), protobuf-javalite. No DI, no Room, no Navigation, no WorkManager |
| Commits | 755 over 2026-05-28 → 2026-10-21, single author, 276 in October alone |
| Churn | `MapScreen.kt` touched in 299 commits, `AppConfig.kt` 121, `SettingsManager.kt` 79 |
| Process docs | `xTrack/` 613 Markdown files, 7.8 MB, 528 plan files; `docs/` 364 KB |

Two numbers frame everything below: **40 % of all commits touch `MapScreen.kt`**, and **the three
most-churned files are the three files with the fewest tests**.

---

## 2. High-level architecture

### 2.1 What is stated and what holds

`docs/maro-code.md` declares `ui/ → spatial/ + data/*/ → data/model/`, with `config/AppConfig` as a
constants file. Checked against imports:

- **Holds:** `spatial/` and `data/model/` import no Android `Context` and no UI types. `ui/` never
  imports a raw data source (OSM, EMODnet, SHOM clients). osmdroid is confined to 14 files under
  `ui/map/`. The `LineRenderSeam` and `RouteEngine` seams are real seams.
- **Breaks (Medium):** `data/settings/SettingsManager.kt:267,337` imports `ui.map.MarkerLayerState`
  and `ui.map.TrackDirectionDensity`, a data → ui edge the map forbids.
- **Breaks (Medium):** `config/AppConfig.kt` is not a constants file; it is a 2 794-line mutable
  service locator (315 `var … private set`, 249 `props.getProperty` calls) that every layer reads at
  call time and that must be `init`-ed before anything renders. It is imported by `spatial/`, so the
  "pure Kotlin" core has a hidden dependency on an asset-loaded global.
- **Doc contradicts code (Medium):** `docs/MARO_ARCHITECTURE.md` forbids heap float arrays and
  mandates memory-mapped `FileChannel` reads; `data/depth/DepthRepository.kt:71-78` and
  `DepthSerializer.kt:189` stream the whole depth grid into a heap `FloatArray`, and
  `CoastlineRepository.kt:86` uses `readBytes()`. Only `RasterCache` uses a channel, for bitmaps.
- **Doc contradicts code (Medium):** the same doc says the app is a "pure consumer" and the
  on-device generation model was rolled back; `CoastlineRepository.kt:170-301` still carries the OSM
  generator, a `filesDir` cache write and a background band build. The manifest's
  `network_security_config.xml` still whitelists Overpass mirrors in cleartext. Either the paths are
  dead and should go, or the doc is wrong.

### 2.2 No composition root

There is no `Application` subclass and no DI. Consequences, all verified:

- `TrackRepository` is constructed three times (`MainActivity.kt:248`,
  `data/track/TrackViewModel.kt:42`, `data/track/TrackRecordingService.kt:88`); `UserMarkerRepository`
  five times. The marker repository's `writeMutex` (whose KDoc says it serialises the UI and the
  service) is per-instance and serialises nothing across them. **High.**
- The foreground service has its own `SettingsManager` (`TrackRecordingService.kt:85`) with no
  preference-change listener anywhere in the codebase, so the recorder's geofence, accuracy, simplify
  and idle thresholds are frozen at service start. A user who changes them in Settings changes nothing
  until process death. **High.**
- ViewModels are hand-built with factories, three of them created mid-composition
  (`ui/map/MapScreen.kt:744,793,910`); `RouteViewModel.factory` closes over a `remember`-ed
  `MutableStateFlow`, so a ViewModel's lifetime now depends on composition-created state. **Medium.**
- Process-global mutable statics stand in for scopes: the service companion flows
  (`TrackRecordingService.kt:485-507`), `WhereAmIProvider` (function references into a
  `MarkersViewModel`, set at `ui/map/MapMarkerEffects.kt:92-94` and never cleared),
  `UserMarkerRepository.markerChanges`, `FineTileCache`, `WalkIndexCache`, `LiveShallowEdges`. **Medium.**

For a Spring developer: this is the equivalent of every `@Service` calling `new Repository()` in its
constructor, with a static `Holder` class where an `ApplicationContext` should be.

### 2.3 Lifecycle and process model

- The foreground service is started on every `MainActivity.onCreate` (`MainActivity.kt:283`) and,
  through `onCreate → ensureRecorder → startGpsSampling`, holds a 1 Hz GPS subscription whenever the
  service is alive in GPS mode, recording or not. A persistent "Ready" notification is the user-visible
  symptom; battery is the invisible one. **Medium.**
- `android:configChanges` swallows rotation, so `remember` state survives it, but `MapScreenChrome.Saver`
  persists only the selected tab while `routeArmed` and `inspectArmed` are `rememberSaveable`
  (`MapScreen.kt:696,804`). After process death a restored `routeArmed=true` meets an idle
  `RouteViewModel`. **Medium.**
- `START_STICKY` restarts the service with the recorder OFF and only recolours the notification when an
  orphaned checkpoint exists (`TrackRecordingService.kt:126-128,370`); recovery is offered only when a
  `TrackViewModel` is created. A crash mid-trip records nothing until the app is reopened. **Medium.**
- `android:largeHeap="true"` is set to survive the depth layer's cold path. That is a symptom of §2.1's
  heap-array choice, not a fix.

### 2.4 State management

- Six ViewModels, but the responsibilities are lopsided. `NavigationViewModel` (2 025 lines) owns
  settings, coastline, zone300, speed and regulated zones, GPS, dead reckoning, accuracy, inspect mode,
  demo mode and every layer toggle (`:126-1315`). `SettingsViewModel` is twelve lines holding an
  expander map. `MarkersViewModel` (1 463) mixes list filter/sort, the drawer state machine, the wizard,
  import and where-am-I. **High.**
- Three event idioms coexist: callbacks down (dominant), a `SharedFlow` channel
  (`trackViewModel.events`), and pull-style `consumeCardReturnRequest()`. All `SharedFlow` bridges use
  `tryEmit` with no replay: a `Finalized` event fired while no collector exists (background auto-stop)
  is dropped, and the route engine's updates (`spatial/RouteAvoidEngine.kt:963,994`) can drop the
  terminal update under a slow collector. **Medium.**
- `AppSettings` is one ~150-field data class, rewritten in full to `SharedPreferences` on every
  change (`SettingsManager.kt:863-1019`) and used as a single recomposition key; map centre and zoom
  live inside it, so panning the map persists and re-emits every setting. **Medium.**

### 2.5 Configuration system

The `.properties` → `AppConfig` → `BuildConfig` cascade is the project's distinctive choice: one
source of truth for tunables, readable without a rebuild, with a precedence rule for `path.*` keys and
unreadable-key reporting at startup. Strengths: it is genuinely one home per value, tests exist per
family (`test/config/*PropertiesTest.kt`), and the bake scripts read the same file. Costs:

- `AppConfig` is read at composition time, not as Compose state; a reload would not recompose, and
  311 `Color(AppConfig.x)` sites bypass `MaterialTheme`, which is reduced to one `primary` literal that
  duplicates a token (`MainActivity.kt:201`). **Medium.**
- Two sizing systems coexist: 458 `N.dp` and 104 `N.sp` literals in `ui/` beside the tokenised
  `AppConfig.uiPadding*` path. **Medium.**
- The 104 route tunables (`route.*`) expose the engine's internals to the properties file and, through
  the Routing tab, to the end user (see §5).

### 2.6 Process and documentation weight

`xTrack/` carries 613 Markdown files and 528 plan files for a 76 k-line app, and source KDoc is a
decision log: plan references ("D36", "R99", "plan §5"), dates, and the same invariant restated at
every call site ("one home" appears dozens of times). The *why* is valuable and rare; the *history*
belongs in the tracker, not the source. Half of `MultipassPull.kt`'s lines are prose. **Medium** for
maintainability, since a new reader cannot tell a live rule from a superseded one.

---

## 3. Low-level implementation

### 3.1 Data layer: tracks, service, settings

- **`runBlocking` on the main thread (High).** `TrackRecorder.finalizeTrack` runs
  `runBlocking(Dispatchers.IO)` (`data/track/TrackRecorder.kt:1110-1130`) doing a marker
  confirm/save, `repository.save` and `deleteCheckpoint`. `save` triggers `rebuildIndex`, which decodes
  every track file in the library (`TrackRepository.kt:308-354`). `recorder?.stop()` is called from
  `onStartCommand` (`TrackRecordingService.kt:169`), i.e. the main thread, so Stop blocks the UI for a
  time proportional to library size.
- **Unsynchronised recorder state (High).** Only `state` is `@Volatile` (`TrackRecorder.kt:178`).
  `currentTrack`, `activeSession`, counters and two `ArrayDeque`s are read-modify-written from the
  sample collector on `Dispatchers.Default`, the idle timer on another Default thread, the marker
  observer, and the main thread via intents (`updateCurrentTrackMeta`, `addManualBoatMarker`, `stop`,
  `discard`). Two `currentTrack = track.copy(...)` writes racing is a lost update of points or markers.
- **Checkpoint durability (High).** `saveCheckpoint` is a bare `writeBytes`
  (`TrackRepository.kt:161-164`) while `save` uses tmp + `ATOMIC_MOVE`; a crash mid-write leaves a
  truncated checkpoint that `recoverOrphanedCheckpoints` silently deletes (`:181-184`). Concurrent
  checkpoint launches interleave on one file with no mutex.
- **Delete-on-decode-error (High).** `load()` deletes the track file on any exception
  (`TrackRepository.kt:86-90`), as does `rebuildIndex` (`:346-349`). A transient `IOException` or OOM on
  read erases user data.
- **Recorder bricking path (Medium).** `stop()` while OFF calls `cleanup()`, nulling `scope`;
  `ensureRecorder` never recreates the recorder, so a later `startManual` enters ON with no sample
  collection.
- **Timing-based sync (Medium).** The ViewModel refreshes the list after `delay(500)` following
  stop/discard/resume (`TrackViewModel.kt:177-258`); a finalise longer than 500 ms shows stale state.
- **Growth (Medium).** Every save, delete and pin rebuilds the index by decoding every track; the
  live track is an immutable list copied per fix (O(n²) over a multi-hour 1 Hz trip) and fully
  re-serialised every 30 s.
- **Persistence formats.** Protobuf (tracks, checkpoints, index), JSON (markers, one codec shared with
  GPX, good), GPX with a base64 protobuf blob, RawBuf raster cache. Versioning rests on commented-out
  reserved field numbers; `TrackSummary.isLive` already collided with field 18 once.
- **`TrackRecorder.kt` (High for health).** 1 718 lines, 52 methods, five concerns: a four-gate spike
  filter with ~20 scalar fields, the idle/BoatMarker session, auto-marker lifecycle, checkpoint and
  timer jobs, and 375 lines of title composition. `beginRecording` and `resume` hand-reset the same 25
  fields. It has **zero tests**, reads `System.currentTimeMillis()` directly and logs via
  `android.util.Log` on every path, so it cannot be tested without a `Clock` and a logging seam.
- **Good:** the GPS `callbackFlow` is correctly `flowOn(Main.immediate)`; `AdaptiveGpsPolicy`,
  `PowerPolicy`, `SpeedFreshness`, `TrackMerger`, `TrackNaming`, serializers and the GPX codec are pure
  and tested; the `SettingsManager` is tested through a `ContextWrapper` stub, a pattern worth copying.

### 3.2 Spatial and route engine

- **Shape.** One grid machine (`RouteCostField` → `MultipassGrid` → `MultipassSearch` → `MultipassPull`
  → corner/fine passes) behind a thin `RouteEngine` seam. `RouteEvolutiveEngine` is a 54-line delegate
  to `RouteAvoidEngine(plan)`: the "three engines" are one engine, a grid plan and a straight-line stub.
  The "one evaluator" claim holds only partly: the A* and the clock read prices through two other paths
  (`RoutePassRunner.kt:104-116`, `RoutePassPrimitives.kt:352-367`), consistent by tests and prose, not
  by type. **Medium.**
- **Memory at the ceiling (High).** Seven parallel primitive arrays, 49 B/cell, up to
  `route.walk.maxCells=600 000` (~29 MB); `blockedCopy` clones all seven per rung for the forced-crossing
  probe while three rungs run, plus 21 B/cell of A* arrays per rung: ~100 MB transient on a phone. If
  typical corridors stay near the documented ~33 k cells this drops to Low.
- **Boxed A* open set (High).** `PriorityQueue<Node>` with a `data class Node` and `compareBy` lambdas
  (`MultipassSearch.kt:49,287`): one allocation per relaxation and boxed comparisons in the hottest
  loop, while the rasteriser already has a primitive `CellHeap` (`MultipassGrid.kt:738-783`).
- **Cancellation (Medium).** Only the A* checks `ensureActive` (every 256 expansions);
  `rasterizeFrame`, the gate relaxation, both pulls, the sweep, the corner and fine passes are plain
  functions, so `cancelLookup` during a 600 k-cell raster waits for it.
- **Engine lifecycle (Medium).** `RouteEngine` has no dispose; `MapRouteEngineEffect` rebuilds the
  engine on id change (`ui/map/MapRouteEffects.kt:53-62`) and the replaced engine's `computeScope`
  keeps running. Engine fields (`repairedOrigin`, `declarations`, `nextComputationId`, `landedRungs`)
  are plain vars written on the caller's thread and read on `Dispatchers.Default`.
- **Correctness edges (Medium).** An end outside `regionBounds` is clamped to the edge cell and the
  line drawn from the raw point; `RouteReason.OFF_WATER` is declared and never emitted. Land/water rests
  on water-on-right and a shoelace sign in raw degrees with no runtime check; rings under ~10 m² are
  dropped. `shallowEdgesOver` holds the grid by `WeakReference` and reads NaN → deep once collected,
  so a wall can silently disappear. A failed build is reported as `NO_PATH`, hiding crashes.
- **Allocation in sweeps (Medium).** `forEachCellNear` allocates a `LatLng` per cell and recomputes
  `cos(midLat)` per call; the pull memo is a `HashMap<LatLng, Double>`; zone containment on the route
  path is `zones.filter{}.filter{contains}` per mark although `PolygonIndexBase` exists; `TileKey`
  embeds full polygon rings so every tile lookup hashes them.
- **Duplication (Medium).** Four point-to-segment kernels, three metres-per-degree derivations
  (`111 320` in `MultipassPull.kt:601` vs `111 195` elsewhere), three even-odd crossing lambdas, four
  "metres of line whose midpoint is in X" loops.
- **Dead code (Low).** `passIndex`, `isWaterByRayCast`, `OFF_WATER`, `RouteEvolutiveEngine`, a no-op
  branch in `polylinesMinDistance`.
- **Tests.** 79 files, ~514 tests, mostly unit over synthetic worlds, plus a harness tier over the
  gitignored bake that **skips when the assets are absent** (`test/.../BakedRegionWorld.kt:81-86`), so a
  clean CI silently loses every real-region guard. Several tests run real A* under `runBlocking` with
  `nanoTime` assertions, flake-prone on shared runners.

### 3.3 Compose UI

- **God composable (High).** `MapScreen` spans `ui/map/MapScreen.kt:646-4459`: 3 812 lines, 58
  `collectAsState`, ~102 `remember`, 76 effects, the route, inspect and track state machines, 17
  imperative `mapView?.controller` calls and local functions that mutate a dozen `var`s. No screen-level
  state holder exists, so nothing here is reachable without composing the screen.
- **Parameter explosion (High).** `MapContent` takes 82 parameters (`:4859-4969`); ten are dead,
  verified by grep of the body: `showTrackDrawer, showTrackHistory, trackSummaries, recoveryTrack,
  onDismissTrackHistory, onUpdateTrack, onDeleteTrack, onShareGpx, onDiscardRecovery, onSaveRecovery`.
  `OverlayLayer` takes ~79 despite `OverlayLayerParams.kt` existing to bundle them.
- **Main-thread raster read (High).** `produceState` on the main dispatcher calls
  `depthViewModel.readCached` (`ui/map/MapDepthRasterEffects.kt:67,86,137,145`); it is not `suspend`,
  and `RasterCache.read` does a synchronous channel read into a direct buffer plus decode
  (`data/depth/RasterCache.kt:104-118`). The live build path correctly uses `Dispatchers.Default`.
- **Stability (High).** `AppSettings` is a plain data class carrying a `Set<String>` with no
  `@Immutable`, so it is unstable; `MapContent` also takes `List<…>` and `List<() -> Unit>` params. Net
  effect: `MapContent` and `CoastlineMapView` can never skip, and `uiMapCenter` emits at 3 Hz during a
  drag, so the whole screen recomposes about three times a second while panning. `@Stable/@Immutable`
  appear 12 times in the app, nine of them in one file.
- **Marker overlay rebuild (Medium).** `MarkerOverlay` keys a `DisposableEffect` on 14 inputs
  including `selectedMarkerId` (`ui/map/MarkerOverlay.kt:163-168`) and recreates every marker,
  including `Bitmap.createBitmap` + `Paint()` per icon on main. Selecting one marker re-rasterises all.
- **Hand-enumerated effect keys (Medium).** The track rebuild uses `LaunchedEffect(*rebuildKeys)` over
  ~40 keys built into a list every recomposition (`ui/map/MapTrackOverlayEffects.kt:~60-168`).
- **Back handling (Medium).** A ladder of five `if (flag) BackHandler {}` plus one exit handler whose
  `enabled` is a seven-term expression (`MapScreen.kt:1844-1870`); drawer-level handlers win only by
  composition order.
- **Theming (Medium).** `MaterialTheme` is a shell; no `Typography` is defined; `DashboardColors`
  restates tokens as literals (`ui/map/DashboardPanel.kt:60`); 37 `Color.White/Black` literals.
- **Component library (Low/Medium).** `ui/components/` (29 files) is well documented and points at the
  guideline sections. Inconsistencies: some rows take no `modifier`, visibility mixes `internal` and
  public, `DrawerSlot` lives in `ui/map/`. The list-item shell is implemented twice
  (`TrackHistoryOverlay.kt:597`, `MarkerManagementOverlay.kt:318`) and the inline text editor four times.
- **Tests (High gap).** 56 JVM tests under `ui/` target pure functions only. No compose-ui-test,
  Robolectric or screenshot tooling in `app/build.gradle.kts`. Uncovered: every composable, the Back
  ladder, drawer transitions, visibility gating, every Settings slider binding, the multiselect state
  machine, overlay diffing. The structural blockers are the ones in §2.2.
- **Good:** osmdroid interop is otherwise well diffed (`AndroidView(factory)` with per-layer effects
  and identity guards); draw paths reuse `Paint`/`GeoPoint`; `DisposableEffect` cleanups are correct in
  `RouteHost`, `InspectMode`, `MarkerOverlay`; `LazyColumn` items are keyed.

### 3.4 Cross-cutting engineering

- **No static analysis, no CI, no release config (Medium).** No `.github/`, no detekt/ktlint/lint
  config, no `buildTypes` block (so no `isMinifyEnabled`, no shrinking, no ProGuard for a release build),
  `versionCode = 1`. Dependencies are current (AGP 8.9, Kotlin 2.1, Compose BOM 2026.05) but
  `lifecycle` 2.8.0 predates the `collectAsStateWithLifecycle` idiom the UI never uses.
- **Repository hygiene (Low).** Tracked at the root: five personal `.gpx` files (two with emoji
  names), `build-log.txt`, `build-log2.txt`, `fix_imports.bat`, five `tmp-hop*-harness*.xml` fixtures,
  three PNG renders. `.gitignore` covers `*.log` but not these.
- **Logging (Low).** 81 `Log.*` calls in main, several inside hot flows (`NavigationViewModel.kt:650-660`
  formats five strings per speed-zone query at ~6 Hz) with no debug gate.
- **Force unwraps (Low).** 95 `!!`, 14 in `TrackRecorder.kt`, 5 in `NavigationViewModel.kt` (e.g.
  `mostRestrictiveSpeedKn!!` inside the zone pipeline).
- **Shell portability (Low).** All pipeline scripts are `.bat`; the review machine is Linux, and
  `AGENTS.md §9` already assumes Windows. Fine for a one-person project, a wall for anyone else.

---

## 4. UX review

Context assumed: a small-boat skipper on the Nice–Fréjus corridor, phone mounted or hand-held, sun
glare, spray, one hand on the wheel, often not looking at the screen.

### 4.1 Core use context

- **No audio or haptic channel at all (High).** `Vibrator`, `HapticFeedback`, `ToneGenerator`,
  `MediaPlayer`, `TextToSpeech`: zero hits in `app/src/main`. Every safety cue is visual. The
  shallow-water alarm is an open todo (`xTrack/DepthSafety/FEAT_DSC_DepthSafety.md:47`); what ships is a
  magenta overlay and a card tint (`ui/map/DashboardPanel.kt:953`).
- **Translucent chrome over a light sea tile (High).** Toggle faces `#8CFFFFFF` with content alpha
  0.5–0.6 (`colors.properties:71,83,86`); overlay text 10 sp `#78909C`. Legend, zone info and tag
  numbers will wash out in glare.
- **Touch targets (Medium).** Right column 64 dp (`MapControlButton.kt:35`), top-row squares 44 dp,
  but list and drawer icon buttons 36–40 dp, colour swatches 24 dp, wizard type chips 18 dp icon with
  12 sp text. Wet-thumb use argues for 48 dp everywhere.
- **Lock mode (Medium).** Zoom is protected by double-tap but the unlock square is a single tap
  (`ui/map/MapLockLayer.kt:72-75`); a splash unlocks the screen.
- **Dark-only, no daylight variant (Medium).** Dashboard value size derives from cell dp and ignores
  the system font scale (`DashboardPanel.kt:383-389`).
- **Emoji as the icon set (Low).** 📡🐾🌊🏔️🕵🏽🧭📵📍 render per OEM font and carry no label.

### 4.2 Information architecture

- **Mode state lives in face colour and a 6 dp dot (High).** GPS has seven states, tracking three,
  inspect two, route four, lock two. Demo versus GPS is the worst: GPS is off by default
  (`SettingsManager.kt:71`), and the only "GPS mode" switch in Settings sits under "Automatic map offset"
  (`MapScreenSettingsOverlay.kt:1509`).
- **Configuration overload (High).** Roughly 88 user-facing controls behind ~20 expanders (Layers ≈52
  including nine zone-category toggles and six marker-halo rows; Navigation ≈20; System ≈12; Routing 4).
  Developer surfaces ship to users: "Debug rays (WhereAmI)", "Regenerate Layers", a "Route algorithm"
  dropdown, the EMODnet cutoff.
- **Route planning is spread across three homes (Medium).** Ends and Go in the hamburger drawer
  (unlabeled dropdowns, `MenuDrawerOverlay.kt:601-616`), arming on the compass square or the route fan,
  pace duplicated in the drawer and the Routing tab. Nothing on the map says "route here".
- **Tapping a marker no longer opens its card (Medium).** The doors are lists, the menu chevron and
  the detective-emoji inspect mode, which is not a discoverable substitute for tapping a pin.
- **Naming collisions (Medium).** Two keys both display "Navigation" (`strings.xml:27-28`); the
  Settings pager is not swipeable (`userScrollEnabled = false`).

### 4.3 Key flows

- **First launch (High).** The foreground service starts unconditionally in `onCreate`, so a
  persistent "Maro II • Demo • Idle • Ready • On Water" notification appears before any interaction,
  and `POST_NOTIFICATIONS` is requested at the head of composition with no rationale. No onboarding; the
  app opens in demo at a fixed point and the user must guess that 📡 asks for location.
- **Start recording (High).** Tap 🐾 → background-location dialog → battery-optimisation dialog →
  recording: three system interruptions at the moment of casting off. Stop is good: one three-door
  Save/Continue/Discard dialog with Discard in the danger role (`ui/map/MapDialogHost.kt:117-133`).
  Exit copy overstates ("press back again to stop and exit" actually opens that dialog).
- **Route to a marker (High).** Drawer → two unlabeled dropdowns → cruising speed and a "Slow water"
  dial reading "Fastest · 1x … 4x · Ignore time" → Go → a panel titled "Route search" showing "Landed
  2/3 · 25 kn · 2x", rungs "Closest/Balanced/Detour" and stage words "Corridor/Grid/Search/Pull/Snap/
  Refine". Engine internals are the primary feedback. Arrival is a modal dialog raised while docking.
- **Create marker (Medium).** Five to seven wizard steps; position is "move the map under the
  crosshair" with no "use boat position" shortcut; routing-cost labels "Wall / Max / Only"; Finish is
  enabled before a name exists.
- **GPX import (Medium).** Result and failure banners auto-hide after 2 s (`MapScreen.kt:1153-1155`);
  "3 imported, 2 ignored" vanishes before it is read and gives no reason per file.
- **Language (Low, good).** Segmented System/English/Français, instant, no restart.

### 4.4 Feedback and alerts

- **Speed-limit and 300 m warnings are card-colour changes only (High).** Amber up to 1.4× the
  limit; auto-reveal defaults 100 m / 10 s (`SettingsManager.kt:64-65`), a ten-second warning at 20 kn
  for a fifteen-knot drop.
- **No-fix state is mislabeled (High).** A null speed prints "— / demo mode" even in GPS mode while
  acquiring (`DashboardPanel.kt:980-987`); STALE/WEAK/ESTIMATING exist only as square colours. The user
  never reads "no GPS fix" or "±40 m".
- **Four feedback vehicles (Medium).** Startup toasts (which leak `maro.properties` to end users),
  2 s banners, undo snack rows, and `ConfirmDialog`, each with its own lifetime.
- **Route refusals are good plain sentences.** Keep that register.

### 4.5 Copy and localisation

- **Inverted meaning across locales (High).** EN "Allow lock on Idle" / "Do not lock while moving"
  versus FR "Garder l'écran allumé en déplacement" / "Permet le verrouillage dès l'arrêt"
  (`strings.xml:130-131`, `values-fr:129-130`); the keep-screen label/description pair is also swapped.
- **Terminology drift (Medium).** "300 m zone / 300m Zone / 300 m band / 300M"; FR track =
  traces / parcours / pistes / trajet; knots = kn / nd / nds / nœuds; nm vs NM.
- **Hard-coded English (Medium).** "open water", "→ shore", "land" (`DashboardPanel.kt:655-657,823`);
  "300 m Zone", "nds" and nine category labels (`RegulatedZoneComponents.kt:264-407`); "Track rendering",
  "Delete" (`OverlayLayer.kt:998,1006`); "Unknown"; durations "1h 2m 3s"; `DepthViewModel.progressTitle()`.
  This contradicts `AGENTS.md §1`'s own no-hardcoded-strings rule.
- **Grammar and plurals (Low).** "Last 2 month", "%d selected" and "Merge %1$d tracks" without
  plurals, "Marker Auto Creation management", emoji inside a label.

### 4.6 Accessibility

- **Primary controls are unlabeled (High).** The top-row squares, zoom, every fan icon and the three
  Link toggles pass `contentDescription = null` or none; TalkBack reads "Button" for the whole primary
  surface (18 nulls, 49 set, mostly in lists).
- **Colour-only encoding (Medium).** Compliance green/amber/red, GPS dot green/red, depth confidence
  as hue, zone-category squares, the speed heatmap (the legend at least prints ticks).
- **Fixed 10–13 sp overlay text (Medium)** and the dp-derived dashboard value clip under large font
  scale.

---

## 5. Consolidated findings (High only)

| # | Area | Finding | Where |
|---|---|---|---|
| 1 | Data | `runBlocking(IO)` on the main thread at Stop, index rebuild decodes whole library | `data/track/TrackRecorder.kt:1110`, `TrackRecordingService.kt:169` |
| 2 | Data | Recorder state written from three dispatchers, one `@Volatile` | `TrackRecorder.kt:177-247` |
| 3 | Data | Checkpoint write not atomic; truncated checkpoint silently deleted | `TrackRepository.kt:161-164,181-184` |
| 4 | Data | Track file deleted on any decode exception | `TrackRepository.kt:86-90,346-349` |
| 5 | Arch | No composition root: 3× `TrackRepository`, 5× `UserMarkerRepository`, per-instance mutex | `MainActivity.kt:248`, `TrackViewModel.kt:42`, `TrackRecordingService.kt:88` |
| 6 | Arch | Service's `SettingsManager` never observes changes; recorder thresholds frozen | `TrackRecordingService.kt:85,217-247` |
| 7 | Arch | `NavigationViewModel` owns eight unrelated concerns | `ui/map/NavigationViewModel.kt` |
| 8 | UI | `MapScreen` 3 812-line composable, no state holder | `ui/map/MapScreen.kt:646-4459` |
| 9 | UI | `MapContent` 82 params, 10 dead | `MapScreen.kt:4859-4969` |
| 10 | UI | Cached raster read on the main thread | `ui/map/MapDepthRasterEffects.kt:67`, `data/depth/RasterCache.kt:104` |
| 11 | UI | `AppSettings` unstable; whole screen recomposes ~3 Hz during drag | `SettingsManager.kt:58`, `NavigationViewModel.kt:193` |
| 12 | Engine | Boxed `PriorityQueue<Node>` in the A* hot loop | `multipass/MultipassSearch.kt:49,287` |
| 13 | Engine | ~100 MB transient at the cell ceiling (seven arrays × rungs + `blockedCopy`) | `multipass/MultipassGrid.kt:87-138,373-391` |
| 14 | Tests | `TrackRecorder` untested; no Compose tests; baked-region tests skip silently | `app/src/test/…` |
| 15 | UX | No audio/haptic alarm; shallow-water alarm an open todo | `xTrack/DepthSafety/FEAT_DSC_DepthSafety.md:47` |
| 16 | UX | Chrome translucency and 10 sp text under glare | `colors.properties:71-124` |
| 17 | UX | Mode state carried by face colour; GPS off by default and mislocated in Settings | `MapControls.kt:157`, `SettingsManager.kt:71` |
| 18 | UX | ~88 settings controls; developer surfaces ship | `MapScreenSettingsOverlay.kt:1556,1819,1845` |
| 19 | UX | First-launch notification and permission storm, no onboarding | `MainActivity.kt:144-153,283` |
| 20 | UX | Route panel shows engine internals; arrival is a modal | `strings.xml:693-729` |
| 21 | UX | Speed-limit and band warnings are colour-only | `DashboardPanel.kt:832-835,1001-1007` |
| 22 | UX | "— / demo mode" shown for no-fix in GPS mode | `DashboardPanel.kt:980-987` |
| 23 | UX | EN/FR screen-lock labels inverted | `strings.xml:127-131`, `values-fr:126-130` |
| 24 | UX | Primary controls have no content descriptions | `FanIconComponents.kt:68-143`, `MapSurface.kt:123` |

---

## 6. Ranked roadmap

Ordered by user risk divided by effort. Each item is scoped so it lands on its own.

1. **Recorder finalise off the main thread.** Make `finalizeTrack` a `suspend` on the recorder's own
   scope; the service calls it from `serviceScope` and emits `Finalized` after IO completes. Small,
   local, removes an ANR path.
2. **Durable track files.** Atomic `saveCheckpoint` and `writeIndex` through the existing
   `atomicReplace` behind a repository `Mutex`; quarantine instead of delete on decode failure.
3. **Raster read off main.** `DepthViewModel.readCached` becomes `suspend` with
   `withContext(Dispatchers.IO)`; the four `produceState` blocks await it.
4. **Composition root.** An `Application` subclass (or a `ServiceLocator` object initialised there)
   holding one `SettingsManager`, `TrackRepository`, `UserMarkerRepository`, `AutoMarkerManager`; the
   service and ViewModels take them from it; the recorder observes the settings flow. This also fixes
   the frozen-settings bug and makes the marker mutex real.
5. **Alarm channel.** Vibration plus tone plus a full-width red banner for shallow water and
   speed-limit breach, behind one Settings toggle; ship DepthSafety B4.
6. **Honest GPS state.** Replace "— / demo mode" with "No GPS fix", "GPS weak (±N m)", "GPS lost";
   make GPS the first-run default with a single onboarding screen that asks location, background
   location, notifications and battery exemption once, before the service starts.
7. **Recorder thread confinement and tests.** Inject a `Clock` and a log seam first, add a recorder
   test harness, then route commands through a `Channel` merged with the sample flow so no field is
   written from two dispatchers. Then split into `FixGate`, `IdleSessionTracker`, `TrackNamer`.
8. **Stability pass.** `@Immutable` on `AppSettings` and the list-carrying params; delete the ten dead
   `MapContent` parameters; verify with the compose-compiler stability report.
9. **Split the screen.** A ViewModel-free `MapScreenState` holder; `MapContent` into `MapLayer`,
   `MapChrome`, `MapBanners` each fed by one immutable bundle; `NavigationViewModel` into
   `SettingsStore`, `GpsViewModel`, `ZonesViewModel`; a single `MapBackStack` owner for Back.
10. **Engine hot path.** Primitive heap in `MultipassSearch` (reuse `CellHeap`); `BooleanArray`
    overlay instead of `blockedCopy`; `ensureActive` per N rows in the raster and pulls; a closeable
    `RouteEngine` cancelled on swap; a suspending emit or counted drops instead of `tryEmit`.
11. **Settings diet.** Essentials (units, alerts, GPS source, language) versus Advanced; remove Debug
    rays, Regenerate Layers and the algorithm picker from release builds; one home per term in both
    locales and the inverted FR labels fixed.
12. **Engineering floor.** `buildTypes { release { isMinifyEnabled = true } }`, ktlint or detekt, a
    GitHub Actions `testDebugUnitTest` that fails loudly when the baked-region harness is skipped, and
    the root junk files untracked.
13. **Docs reconciled.** Either memory-map the depth grid or amend `MARO_ARCHITECTURE.md`; delete or
    document the on-device coastline generation paths; move decision history out of KDoc into xTrack.

---

## 7. What is working and should be kept

- The pure-Kotlin core: `spatial/` and `data/model/` with no Android types, a real `RouteEngine`
  seam, a real `LineRenderSeam`, serializers and codecs shared between disk and GPX.
- The properties-driven tunable system with per-family tests and startup reporting of unreadable
  keys; the single-source region corridor in `gradle.properties`.
- The prebake pipeline: gather → process → serialize at build time, the app a loader; the gated
  JUnit "tools" that skip in normal runs.
- The component library and the three UI guideline docs, which the components actually cite.
- osmdroid interop: per-layer effects with identity guards, reused `Paint`/`GeoPoint`, keyed lists.
- The three-door Stop dialog, the undo snack rows, the plain-sentence route refusals, the instant
  language switch, the `ContextWrapper`-based settings test pattern.
- The commit discipline: 755 small commits with descriptive messages, a merge-by-PR rule for
  `develop`, and a tracker that records why each decision was made.

---

## 8. Objections to this review's own recommendations

- **The user base may be one expert owner.** If so, the onboarding, settings-diet and route-copy
  items (6, 11, parts of 20) lose most of their value, and the review's UX grade is harsh for the
  actual audience. The safety items (5, 21, 22) stand regardless.
- **Memory items assume the cell ceiling is reached.** If real corridors stay near ~33 k cells, items
  12–13 drop to Low and the engine's ranking becomes cancellation and lifecycle first.
- **Refactoring the recorder and the screen without tests is riskier than leaving them.** Item 7
  deliberately puts the test seam before the thread-confinement change; item 9 should not start before
  item 8's stability report and a first Compose test exist.
- **Replacing the properties system with Material theming would lose a feature.** The tokens are
  editable without a rebuild and tested; the recommendation is to *map* them into `MaterialTheme` once,
  not to abandon them.
