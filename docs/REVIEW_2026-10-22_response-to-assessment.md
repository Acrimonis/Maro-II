<!-- scope: review -->
# Response to the independent assessment of the 2026-10-22 review

> Companion to `REVIEW_2026-10-22_architecture-implementation-ux.md`. The assessment under review is
> the external file `maro-review-assessment.md` (Codex, 2026-10-22, same commit `ae55e62b`). Every
> verdict below was re-checked in source before it was written; nothing was built or run. Paths are
> relative to `app/src/main/java/ykws/android/maro/` unless they start with `app/`, `docs/` or `xTrack/`.

---

## Status as of 2026-10-23

Everything below this section is the review as written on 2026-10-22. This section says what has
happened since. All work is on [noahb/Maro-II#1](https://github.com/noahb/Maro-II/pull/1), four fix
commits (`299cfdb8`, `fa561bb4`, `84f185c0`, `4ded8c30`), reviewed twice with every reported code
defect resolved; the full change record is `FIXES_2026-10-22_high-findings.md`.

**Done**

- Fifteen of the sixteen Highs (§5): H1 startup, H2–H9 recording durability and the importer, H10–H11
  composition root and live settings, H12 raster read, H13 tests begun, H15 GPS text, H16 labels.
- The next tier (§6 steps 1–7 and 9, plus the maintenance items that were cheap): importer safety and
  budgets (N06–N08, U17), compass remap and declination (O1, O2), engine cancellation, lifecycle,
  failure reporting and dropped updates (S04, S05, S07, S10, A14), the where-am-I bridge leak (A09),
  restored armed flags (A11), the mislabelled GPS row (U07), dead parameters (C02), hot-path logging
  (E03), the architecture doc (A04, A05), and the adjacent N10, N11, D06 and O5.
- Two review rounds on the PR: eight findings, all accepted and fixed (exit awaits the finalize, a
  lifecycle lock, a retained failed save, corrupt-cache fallback, a trailing-parameter compile
  defect, the checkpoint-suffix namespace, a bounded picker read, forwarded unreadable counts).

**Remaining, and what blocks the merge**

- Validation, which the reviewer rightly holds the merge on and which needs a machine with a JDK and a
  device: `:app:assembleDebug`, `:app:testDebugUnitTest`, a clean-install launch with no grants on
  Android 14+, recorder regression tests (delayed persistence at exit, overlapping Stop and Discard,
  a failed save with no or a stale checkpoint then retry, the corrupt-raster fallback), a landscape
  compass check, and one cancelled large raster on a device.
- Two decisions: a `kxml2` test dependency so the ignored standard-GPX test can run, and whether the
  alarm channel (H14, §3) is in scope.
- Medium findings not taken up, by area. UI: the marker overlay's wholesale rebuild on selection
  (C05), the Back-handler ladder (C07), the full settings rewrite on every pan (A15), the god
  composable and ViewModel split (C01, A13), theming and component duplication (C08, C09). Engine:
  memory at the cell ceiling and the boxed heap, to be measured before touched (S02, S03), geometry
  duplication (S11, S12), the single-flight raster owner (N09). Service: the always-on GPS
  subscription (A10), the wall-clock time base (O3), the exported water-state receiver (O4), the
  cleartext whitelist (O6). UX: the settings diet (U08), route planning's three homes (U09), the
  recording permission storm and onboarding (U13), glare and a daylight theme (U02), touch targets
  (U03), the single-tap unlock (U04), colour-only warnings (U20, U27), terminology and hard-coded
  English (U23–U25), font scale (U28). Engineering: CI, lint and release keep rules (E01), root-file
  hygiene (E02), decision history in KDoc (A17).

## 0. Outcome

- **The assessment is largely right, and it is right about the things that matter.** Of its ten
  corrections, nine are conceded in full and one in part. Of its twelve new findings, all twelve are
  confirmed in source, four of them High. Its re-grading of my 24 High findings is accepted on
  thirteen rows and contested on one (the alarm channel).
- **The single most important change it forces:** on a clean install on Android 14 or later, the app
  starts a location-typed foreground service before any location permission exists, which the
  platform rejects with a `SecurityException`. The owner's device never shows this because
  `adb install -r` keeps the grants. That goes to the top of the roadmap.
- **My own second pass adds six findings neither review had**, the most useful being a compass
  azimuth that is never remapped for display rotation, so heading-up in landscape is wrong by a
  quarter turn whenever the compass fallback drives the map.
- **The revised High list** has eighteen rows: twelve retained, four added from the assessment, two
  reclassified. Six of my original Highs drop to Medium and one to Low.

---

## 1. The ten corrections

| # | Assessment's correction | Verdict | What I checked |
|---|---|---|---|
| 1 | Strong skipping is on by default under Kotlin 2.1.20, so "unstable params mean the screen can never skip" is wrong | **Concede.** No `composeCompiler` block disables it (`app/build.gradle.kts`, `gradle.properties`). The practical observation narrows to: `MapContent` recomposes at the 3 Hz centre cadence because `mapCenter` is one of its parameters and changes, not because of instability; children with unchanged inputs do skip. The `@Immutable` recommendation is downgraded to "run the compiler stability report first; annotate only holders whose whole state is immutable". |
| 2 | EN/FR screen-lock labels are not inverted; both describe the same movement gate | **Concede.** `PowerPolicy.evaluate` (`data/power/PowerPolicy.kt:80-92`) is one gate; EN "Allow lock on Idle / Do not lock while moving" and FR "Garder l'écran allumé en déplacement / Permet le verrouillage dès l'arrêt" are two emphases of it. What remains is Low: the label and description are swapped between locales (`strings.xml:127-128` vs `values-fr:126-127`, FR label = EN description), worth aligning. |
| 3 | The "GPS mode" row under Automatic map offset toggles `mapOffsetGps`, not `gpsMode` | **Concede the misidentification; the finding sharpens.** `MapScreenSettingsOverlay.kt:1507-1512` binds `settings.mapOffsetGps` to a row labelled with `settings_gps_mode_label` ("GPS mode"). The only writers of `gpsMode` are the map square's permission launcher (`MapScreen.kt:1372,1494,1497`). A row that says "GPS mode" and does something else is a mislabel, Medium. |
| 4 | The core is not free of Android/UI types | **Concede.** My grep checked only `android.content.Context`. `spatial/RouteTrace.kt`, `spatial/MarkerMatcher.kt` and `spatial/RouteAvoidEngine.kt` import `android.util.Log`; `data/model/MultiActionSpec.kt` imports Compose `ImageVector`. The layering claim becomes "holds except for four named leaks". |
| 5 | "No release build type / no static analysis" is too broad | **Concede the wording.** AGP supplies debug and release and Android Lint runs without a config. The gap is: no release customisation, no shrinking, no CI, no third-party linters, and (added) **no `app/proguard-rules.pro` at all**, so enabling shrinking needs kotlinx-serialization and protobuf-lite keep rules first. |
| 6 | `collectAsStateWithLifecycle` predates Lifecycle 2.8 | **Concede.** It arrived in 2.6. The point stands in another form: `lifecycle-runtime-compose` is not a declared dependency (`gradle/libs.versions.toml`), so the API is unavailable to the UI. |
| 7 | `DashboardColors` is a token bridge, not a literal palette | **Concede.** `ui/map/DashboardPanel.kt:55-70`: every member reads `AppConfig` except `textMutedBright = Color(0xFFB0BEC5)`. One literal, not a palette. |
| 8 | `RouteEvolutiveEngine` is live, not dead | **Concede "dead", hold "redundant".** It is registered (`spatial/RouteEngineChoice.kt:64-71`) and its 54 lines delegate to `RouteAvoidEngine(…, EvolutiveGridPlan)`. The registry could construct that directly; keeping the adapter is a taste call, not a defect. |
| 9 | `catch (e: Exception)` does not catch `OutOfMemoryError` | **Concede.** The delete-on-decode-failure finding stands for `IOException` and serialization exceptions; the OOM example is withdrawn. |
| 10 | Properties cannot be changed "without a rebuild" | **Concede the wording.** They change without recompiling Kotlin; they still need repackaging and reinstalling. `BuildConfig` keys are a separate compiled path. |

Quantitative corrections, all accepted: 287 main Kotlin files (491 was main + test); 204 test files;
8 tracked `.gpx` files at the root, not 5; 103 `!!` tokens (my 95 counted lines); 13 files with
explicit osmdroid imports. The churn headline survives either counting method (299/755 by mine,
305/755 by theirs, both ≈40 %).

---

## 2. The twelve new findings

| ID | Finding | Verdict | What I checked |
|---|---|---|---|
| N01 | Location-typed foreground service starts before location permission; `SecurityException` on Android 14+ | **Confirm, High.** `MainActivity.kt:283` calls `startForegroundService` unconditionally at the end of `onCreate`; `TrackRecordingService.onStartCommand` calls `startForeground(id, notification)` with no type override (`:206`), so the manifest's `foregroundServiceType="location"` applies; `targetSdk = 36`. The only writers of `gpsMode` run after a grant. **Why the owner never sees it:** `apk-deploy.bat` reinstalls over the existing app and Android keeps runtime grants. Validation: uninstall, install, launch with no grants on an API 34+ device. |
| N02 | `atomicReplace` swallows every failure, so `save` can return with no file and `finalizeTrack` then deletes the checkpoint | **Confirm, High.** `TrackRepository.kt:441-452`: both `catch` blocks are empty; the inner comment's "consumed by a concurrent save" cannot hold because temp names are UUID-suffixed (`:63`). `finalizeTrack` deletes the checkpoint right after (`TrackRecorder.kt:1128`). |
| N03 | Resuming a finalised track keeps its id, and recovery deletes any checkpoint whose final file exists | **Confirm, High.** `TrackRecorder.resume` keeps `track.id` (`:307-330`); `recoverOrphanedCheckpoints` does `if (trackFile(track.id).exists()) { file.delete(); null }` (`TrackRepository.kt:178-180`). A crash during the resumed portion loses it and keeps the old trip. |
| N04 | Marker `saveAll` ignores `renameTo`'s result, uses one shared temp name, and never reads its `.bak` | **Confirm, High for durability.** `data/markers/UserMarkerRepository.kt:67-80`: `tmp.renameTo(markersFile)` result discarded, then `lastKnownGood = markers` and a change emitted; `loadAll` (`:52-64`) falls back to the in-memory cache, never to the backup. |
| N05 | Standard GPX import reads `parser.name` on `TEXT` events, which is null, so time/speed/course never match | **Confirm, High for foreign files.** `data/track/GpxImporter.kt:315-328`. The mechanism is quieter than the assessment says: Kotlin resolves `parser.name.equals("speed", ignoreCase = true)` to the null-safe `String?.equals` extension, so the branches silently return false rather than throwing. Every foreign point gets `timeOffsetMs = 0` and the track takes the import time as its date; `buildForeignTrack` also stores `distanceNm = 0f` (`:381`). |
| N06 | Imported track ids become unchecked file paths via the ZIP path | **Confirm, Medium.** ZIP entries always use `SKIP_EXISTING` (`GpxImporter.kt:125`), which keeps the decoded `track.id` (`:176-179`); `trackFile(id)` is `File(tracksDir, "$id.bin")` with no validation (`TrackRepository.kt:303`). The entry-name traversal guard (`:119`) does not cover the id inside the payload. Bounded by the app sandbox and user-selected input. |
| N07 | IMPORT_NEW keeps bundled marker ids, so a duplicated trip steals the original's markers | **Confirm, Medium.** `ownedBy` only rewrites `trackId` (`GpxImporter.kt:445`); `importTracks` calls `markerRepository.update` when the id already exists (`TrackViewModel.kt:532-534`). |
| N08 | Import has no resource budget | **Confirm, Medium.** Whole-file `readBytes` on the picker thread, `zis.readBytes()` per entry, no entry/point/size caps. Not re-measured. |
| N09 | Raster path duplicates memory and work; warning raster key lacks coastline identity | **Confirm, and raise.** On a warm cache both the live producer (`ui/map/MapDepthRasterEffects.kt:65-68`) and the cache producer (`:137-140`) call `readCached`, so each layer can hold two decoded bitmaps at once; `generateRasterLayers` launches a fresh job per call with no single-flight owner (`DepthViewModel.kt:142`); `RasterCache.keyFor` (`:58-71`) has no coastline stamp although the warning raster depends on `isWater`. Medium-High for memory, on top of the main-thread read already found. |
| N10 | The service's orphan scan looks for extension `checkpoint`; files are `<id>_checkpoint.bin` | **Confirm, Medium, and it corrects my review.** `TrackRecordingService.kt:126` vs `TrackRepository.kt:304`. `hasOrphans` is always false, so my statement that a sticky restart "recolours the notification when an orphan exists" was wrong: it never does. |
| N11 | `AppConfig.init` runs only in the Activity; a service-only process restart reads code defaults | **Confirm, Medium.** The only caller is `MainActivity.kt:109`. `START_STICKY` recreation with no Activity builds the recorder from `AppConfig` defaults. |
| N12 | The warm-up pool contradicts the rulebook's "no raw threads or executors" | **Confirm.** `AGENTS.md:154` vs `ui/map/MapRouteEffects.kt:108-117`. I had noted the pool and missed the rule. |

---

## 3. Where I push back

- **Alarm channel (my #15), downgraded by the assessment to "High only if unattended alerting is a
  product requirement".** I hold High. The product's own feature file lists the alarm as unfinished
  work, not as out of scope (`xTrack/DepthSafety/FEAT_DSC_DepthSafety.md:47`), and a depth layer that
  turns magenta under a helmsman's thumb is a chart annotation, not a warning. The assessment is right
  that it is a designed feature (trigger quality, hysteresis, acknowledgment, mute), not a small fix;
  that changes its cost, not its rank.
- **God composable and god ViewModel (my #7, #8), downgraded to Medium maintenance debt.** I accept
  the relabel and keep them in the roadmap's maintenance tier, with one addition the assessment does
  not weigh: 40 % of all commits touch `MapScreen.kt`, so its shape is the cost multiplier on every
  other fix in this list.
- **"Do not fail every normal test run because an opt-in generator was skipped" (roadmap 12).**
  Agreed, and my phrasing conflated two things. The ask is a CI job that has the baked assets and
  reports regional-test skips, not a hermetic run that fails on them.

Everything else in the assessment's re-grading is accepted: #11 (unskippable screen) is withdrawn as
proven; #23 (inverted locales) drops to Low; #12, #13, #16, #17, #18, #19, #20, #21 drop to Medium with
the qualifications given, #17 re-stated as "a row labelled GPS mode toggles the map offset".

---

## 4. Second pass: findings neither review had

| ID | Finding | Severity | Evidence |
|---|---|---|---|
| O1 | **Compass azimuth is never remapped for display rotation.** `getOrientation` on the raw rotation matrix returns azimuth relative to the device's Y axis; the app supports landscape (`MapScreen.kt:269,316`) and rotates the map from the compass whenever no GPS course has arrived for 3 s (`NavigationViewModel.kt` heading fallback). In landscape with the boat stopped or slow, heading-up is wrong by 90°. | Medium | `data/location/CompassSource.kt:39-41` — no `remapCoordinateSystem`, no `Display.rotation` read |
| O2 | **Magnetic heading mixed with true course.** The compass is magnetic by design (`CompassSource.kt:17`); GPS course is true. Declination at Nice is about 2.5° E, so the map jumps by that much at every fallback hand-over. | Low | `CompassSource.kt:17`; no `GeomagneticField` anywhere |
| O3 | **Recorder and GPS source run on the wall clock.** 22 `System.currentTimeMillis()` and zero `elapsedRealtime` in `TrackRecorder.kt`; fixes are stamped with wall time (`GpsLocationSource.kt:35,70,76`). A clock step (network or GPS time resync when signal returns at sea) shifts every debounce, idle and gap decision at once. Intervals belong on the monotonic clock; wall time only on stored timestamps. | Medium-Low | `TrackRecorder.kt`, `GpsLocationSource.kt:35-76` |
| O4 | **Exported water-state receiver and unprotected broadcasts.** The Tasker hook registers with `RECEIVER_EXPORTED` and no permission, and `sendBroadcast` carries the on-water state to any app. The data is benign; the contract is undocumented and unguarded. | Low | `TrackRecordingService.kt:137-142,191` |
| O5 | **Window background reads a token before `AppConfig.init`.** `MainActivity.kt:100` reads `AppConfig.uiToastBackground` nine lines before `init` (`:109`), so the colour behind the system bars is the code default, not the property. | Low | `MainActivity.kt:100,109` |
| O6 | **Cleartext whitelist is unnecessary.** `TileSourceFactory.MAPNIK` is HTTPS in osmdroid 6.1 and the Overpass fetch is a prebake path; `network_security_config.xml` still permits cleartext to four domains. | Low | `app/src/main/res/xml/network_security_config.xml`, `ui/map/CoastlineMapView.kt:232` |

---

## 5. Revised High list

| # | Area | Finding | Status |
|---|---|---|---|
| H1 | Startup | Location FGS started before permission (N01) | **Added** |
| H2 | Data | `runBlocking` on main at Stop; whole-library index rebuild | Retained |
| H3 | Data | Recorder state mutated from several threads | Retained |
| H4 | Data | Checkpoint write not atomic; truncated checkpoint deleted | Retained |
| H5 | Data | Track file deleted on `IOException` or decode failure | Retained, OOM example removed |
| H6 | Data | `atomicReplace` can report success with no file, then the checkpoint is deleted (N02) | **Added** |
| H7 | Data | Resumed recording's checkpoint discarded on recovery (N03) | **Added** |
| H8 | Data | Marker save ignores rename failure; backup never read (N04) | **Added** |
| H9 | Data | Foreign GPX loses time, speed, course and distance (N05) | **Added** |
| H10 | Arch | Three to five repository instances; per-instance mutex | Retained, as a concurrent-write defect |
| H11 | Arch | Service settings frozen for the service lifetime | Retained |
| H12 | UI | Cached raster read and decode on main, twice per layer (C03 + N09) | Retained, raised |
| H13 | Tests | Recorder untested; no rendered Compose coverage; regional tests skip on CI | Retained as assurance priority |
| H14 | UX | No audio or haptic alarm | Retained, contested |
| H15 | UX | "— / demo mode" shown while GPS acquires | Retained |
| H16 | UX | Primary controls carry no functional labels | Retained |
| M | — | #7 #8 #9 #12 #13 #16 #17 #18 #19 #20 #21 → Medium; #11 withdrawn; #23 → Low | Reclassified |

---

## 6. Revised roadmap

Ordered by user risk over effort, incorporating the assessment's priorities and the second pass.

1. **Clean install opens.** Start the foreground service only after an eligible operation (GPS mode
   granted, or a recording request), and represent denial explicitly. Validate on an uninstalled,
   ungranted API 34+ device.
2. **No false success, no avoidable data loss.** One repository `Mutex` per store; `atomicReplace`
   reports failure and keeps the checkpoint; checkpoint and index writes go through the atomic path;
   decode failure quarantines instead of deleting; recovery distinguishes a resumed generation from a
   stale checkpoint; marker save checks `renameTo` and `loadAll` falls back to `.bak`.
3. **Stop is a transaction.** Simplification, titling and IO off the main thread, a visible
   finalising state, success emitted only after persistence. Inject a `Clock` (monotonic) and a log
   seam first, write the recorder's characterisation tests, then confine its state to one owner.
4. **One process graph.** An `Application` (or a locator it initialises) owning `AppConfig.init`,
   `SettingsManager`, both repositories and `AutoMarkerManager`; the service and ViewModels take them
   from it; the recorder observes settings with an explicit update policy.
5. **One raster owner.** `readCached` suspends on IO; one request owner per key reads or builds
   once; the warning key carries the coastline stamp; superseded requests are replaced.
6. **Importer correctness.** Track the active element in the GPX parser; derive foreign stats;
   validate ids at the repository boundary; remap bundled marker ids on IMPORT_NEW; bound input and
   expansion; keep a per-file result the user can open after the banner has gone.
7. **Truthful position state.** Source, freshness, accuracy and missing speed as distinct dashboard
   states; the mislabelled offset row renamed; compass azimuth remapped for display rotation and
   corrected for declination.
8. **Alarm as a designed feature.** Trigger quality, hysteresis, acknowledgment, mute, and
   foreground/background behaviour specified before the vibration and tone land.
9. **Functional labels** on every square, fan child and toggle; state announced, not only coloured.
10. **Maintenance tier.** Extract mode transitions and long-lived ownership from `MapScreen`; split
    `NavigationViewModel` by change rate; engine cancellation and closeable lifecycle; measured
    allocation work on the A* and grid copies; CI with the baked assets and reported skips; release
    shrinking only after keep rules and an exercised release build; docs reconciled with code; root
    files triaged by purpose rather than deleted by name.

---

## 7. Objections to this response

- **The compass finding (O1) is inferred from the API contract, not observed.** `getOrientation`
  without remapping is a well-known landscape bug, but whether the map visibly rotates wrong depends
  on how often the fallback engages on this boat; a stationary landscape test settles it in a minute.
- **The alarm's High rests on a product reading the owner may not share.** If the app is a chart
  display rather than a watch-keeper, the assessment's conditional is the right one.
- **Confirming all twelve assessment findings may look like deference.** Each was read in source and
  two were sharpened (N05's mechanism, N09's double decode); none was taken on the assessment's word.
