<!-- scope: review -->
# Fixes for the agreed High findings (2026-10-22)

> What was changed to close the High findings both reviews agreed on (H1–H13, H15, H16 of
> `REVIEW_2026-10-22_response-to-assessment.md`; H14, the alarm channel, was contested and is not
> here). **Nothing was compiled or run: this machine has no JDK and no Android SDK.** Every file was
> re-read after editing and every signature a change relies on was checked in source, but the build
> and the tests are the first thing to run. Paths are relative to `app/src/main/java/ykws/android/maro/`.

## 1. What to run first

```bash
./gradlew :app:assembleDebug
```

```bash
./gradlew :app:testDebugUnitTest
```

Then the one device check that no unit test can give: **uninstall the app, install it fresh, launch
with no permission granted on an Android 14+ device.** Before this change that launch died in
`startForeground`; it must now open in demo mode with no notification, and the notification must
appear the moment GPS mode is granted.

## 2. The fixes, by finding

| Finding | Fix | Files |
|---|---|---|
| H1 Location foreground service started before permission | The service promotes itself only while a location grant stands (`promoteToForeground`, via `ServiceCompat.startForeground` with the location type) and otherwise runs as a plain started service; `MainActivity` starts it with `startForegroundService` only when GPS mode is on and a grant exists, else `startService`, re-run on every GPS-mode change so the first grant promotes the running service | `data/track/TrackRecordingService.kt`, `MainActivity.kt` |
| H2 `runBlocking` on main at Stop | `finalizeTrack` is `suspend` and runs on the recorder's own lane; the IO block is `withContext(Dispatchers.IO)`; the service's `stop()` returns at once and the recorder reads ON until the file is on disk | `data/track/TrackRecorder.kt` |
| H3 Recorder state mutated from several threads | One serial lane: `dispatcher.limitedParallelism(1)` behind every coroutine the recorder owns; every public command (`start`, `resume`, `stop`, `discard`, `startManual`, `updateCurrentTrackMeta`, `addManualBoatMarker`) posts its work there and returns; samples arriving during a finalize are dropped; `stop`/`discard` while OFF are no-ops instead of tearing the collector down (the bricking path) | `TrackRecorder.kt` |
| H4 Checkpoint write not atomic | Every write (track, checkpoint, index) goes through `writeAtomically`: UUID temp file + atomic move; a repository `Mutex` serialises all writers and index rebuilds | `data/track/TrackRepository.kt` |
| H5 Track deleted on decode failure | `decodeTrackOrQuarantine`: an undecodable file moves to `tracks/quarantine/<name>.<epoch>`; an `IOException` on the read leaves the file alone | `TrackRepository.kt` |
| H6 `atomicReplace` could report success with no file | Atomic move, fallback to a plain replacing move only on `AtomicMoveNotSupportedException`, any other failure propagates; the recorder catches it, keeps the checkpoint, emits no `Finalized`, and surfaces the failure through `infoError` | `TrackRepository.kt`, `TrackRecorder.kt` |
| H7 Resumed recording's checkpoint discarded on recovery | `recoverOrphanedCheckpoints` keeps a checkpoint whose last point is later than the finalised file's last point (a resumed generation) and drops only one the finalised file already covers | `TrackRepository.kt` |
| H8 Marker save ignored rename failure; backup never read | `saveAll` is the locked entry; `saveAllLocked` writes a UUID temp and atomically moves it, propagating failure; `loadAll` falls back to the `.bak`; an unreadable store refuses to be overwritten by an empty list | `data/markers/UserMarkerRepository.kt` |
| H9 Foreign GPX lost time, speed, course, distance | The parser tracks the current `<trkpt>` child instead of reading `parser.name` on TEXT events; `buildForeignTrack` derives stats with `withDerivedStats()` | `data/track/GpxImporter.kt` |
| H10 Three to five repository instances | `MaroApp : Application` is the composition root: one `SettingsManager`, one `UserMarkerRepository`, one `TrackRepository`, one `AutoMarkerManager`; the service, both ViewModels and `MainActivity` take them from it (with a construct-your-own fallback for a stub-application test) | `MaroApp.kt` (new), `AndroidManifest.xml`, `TrackRecordingService.kt`, `TrackViewModel.kt`, `ui/map/MarkersViewModel.kt`, `ui/map/NavigationViewModel.kt`, `data/markers/AutoMarkerManager.kt`, `MainActivity.kt` |
| H11 Service settings frozen | With the shared store the service sees UI writes; the recorder's tunables are a `TrackRecorderConfig` snapshot the service replaces live from the settings flow (`observeSettings` → `applyConfig`), and the GPS gate follows `gpsMode` too | `TrackRecorder.kt`, `TrackRecordingService.kt` |
| H12 Raster read on main, twice per layer | `DepthViewModel.readCached` is `suspend` on IO; the live producers skip when the cache file exists so each layer decodes once | `ui/map/DepthViewModel.kt`, `ui/map/MapDepthRasterEffects.kt` |
| H13 Tests | New: `TrackRepositoryDurabilityTest` (recovery rule, quarantine, no temp leftovers), `UserMarkerRepositoryDurabilityTest` (backup recovery, refused empty overwrite), `GpxStandardImportTest` (ignored, see §4) | `app/src/test/…` |
| H15 "— / demo mode" while GPS acquires | The speed card reads the GPS square's state: "No GPS fix", "GPS stale", "GPS weak (±N m)", "Estimating"; "demo mode" only in demo mode | `ui/map/DashboardPanel.kt`, `ui/map/MapScreen.kt` (two call sites), both `strings.xml` |
| H16 Primary controls unlabelled | Content descriptions with state on the GPS, recording and inspect squares, recenter, zoom, the six layer-fan children and the three filter-link toggles; 27 new keys in both locales | `ui/map/MapControls.kt`, `TrackStatusIcon.kt`, `InspectMode.kt`, `FanIconComponents.kt`, `MenuDrawerOverlay.kt`, both `strings.xml` |

Two adjacent Medium findings were fixed because the High fixes made them wrong otherwise: the
service's orphan scan now matches `<id>_checkpoint.bin` (N10), and the track ViewModel refreshes its
lists on the recorder's `Finalized`/`Stopped`/`Started` events instead of a 500 ms delay (D06), since
finalize is now asynchronous with no fixed duration. `AppConfig.init` moving to `MaroApp.onCreate`
also closes N11 and the window-background-before-init slip.

## 3. Behaviour that changed on purpose

- **Without a location grant the service is not a foreground service.** It runs while the app is in
  front and is stopped by the system when the app leaves the screen. Demo-mode recording therefore
  does not survive backgrounding until GPS mode has been granted once. Before this change the app
  could not launch at all in that state on Android 14+.
- **A failed final save no longer deletes the checkpoint.** The recorder goes OFF, emits `Stopped`
  but not `Finalized`, shows the failure text in the existing error overlay, and the next launch offers
  the recording for recovery.
- **Repository writes can throw `IOException`.** Previously a failed replace was swallowed. The
  recorder handles it; ViewModel callers inside `viewModelScope.launch` would now surface a write
  failure as a crash where they used to show a silent non-save — the same behaviour they already had
  for a failed `writeBytes`.
- **Recorder commands are asynchronous.** `stop()` and `discard()` return before the work runs; the
  UI already reads the recorder's state flow, so nothing visible changes except that Stop no longer
  freezes the screen.

## 4. Known gaps and your calls

- **`GpxStandardImportTest` is `@Ignore`d.** The standard-GPX path uses `XmlPullParserFactory`, which
  the mockable `android.jar` stubs; running it on the JVM needs `kxml2` as a `testImplementation`
  dependency, which is a dependency decision (AGENTS.md §4). The fix itself is in; the test stands
  ready.
- **`testOptions.unitTests.isReturnDefaultValues = true` was added to `app/build.gradle.kts`** so
  the repositories' `Log` calls on their failure paths do not throw in unit tests. It is a build
  setting, not a dependency; say so if you would rather have a `Log` shim.
- **`limitedParallelism`** is used on the recorder's dispatcher; it is stable in the coroutines
  version Compose pulls in, but if the build flags it as experimental an `@OptIn` on the property is
  the whole fix.
- **Foreign GPX `fastestSpeedMps`** stays 0 (`withDerivedStats` does not compute it) and namespaced
  `<gpxtpx:speed>` children still do not match; both are one-liners if wanted, left out as beyond the
  agreed High.

## 5. Review round one (PR noahb/Maro-II#1)

Four findings, all accepted and fixed.

| Finding | Fix |
|---|---|
| P1 Save-and-exit stopped the service 300 ms after Stop, cancelling the now-asynchronous finalize | The exit waits for the recorder's own OFF (`awaitRecorderOff`, bounded at 20 s) before `stopService`; a save that failed leaves the recorder ON with its error shown and the app stays open. The finalize's persistence block runs under `NonCancellable`, so a service torn down mid-write no longer cuts it short. Discard deletes before reading OFF, under the same guard. |
| P1 A single worker does not serialise across suspension points | A `lifecycleMutex` held for the whole of every transition: Stop, Discard, start, resume, meta update, manual marker and every sample. A second Stop or a Discard now waits for a finalize in flight and then finds the recorder OFF. |
| P1 A failed final save cleared the only complete copy | On failure the recorder stays ON holding the finalised snapshot, writes it as a checkpoint (best effort), restarts the periodic checkpoint job and reports through `infoError`; the next Stop retries from the snapshot and only a successful save or an explicit Discard clears it. Every checkpoint write is now failure-tolerant (`checkpointQuietly`), and a coroutine that dies on the recorder's lane is logged rather than crashing the process. |
| P2 A corrupt cache file left the layer absent | The cached producers note when a cache file existed but did not decode (the reader evicts it) and bump a tick the live producers and the lazy-init effect are keyed on, so the layer is built live and the cache regenerated. |

## 6. Strongest objection

The recorder and service changes are the riskiest edits in the set and they were made without a
compiler or a device. The serial-lane design is simple on purpose, but a recorder characterisation
test (roadmap step 3) should be written before anything further is changed there.
