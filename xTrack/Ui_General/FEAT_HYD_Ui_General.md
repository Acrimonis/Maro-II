# Context Hydration — Ui_General — 2026-10-01

**Last Bake:** 2026-10-01 20:16 UTC — written by `#bake`; absence means never baked

**Directive trace:** All five covered action classes were met and none stopped this session — no dependency was added, no machine-shaped data file was opened, every write followed an order (the branch on `#new`, each pass on the user's explicit approval, the commit on `#commit`), the device was never touched (builds only), and every claim written about the code rests on a file read in the session. The gaps left open are named: every UI change in this pass is build- and unit-test-validated only, nothing device-validated, and the Feature Summaries row and Focus History prune remain blocked by the long-line cap recorded in the Global Todos.

## State

One branch, `feature/ui-shuffle`, cut from `origin/develop` on `#new` and rebased onto `db0a01f` on `#merge`: one day of menu-and-settings reshuffling, shipped in a run of small passes.

**The menu's Navigation card was trimmed.** The GPS-mode toggle and the Auto-show zones master switch left the right-side menu; the route summary and route-ends block now stand alone in that card, and the master switch's backing field `autoShowMasterOverride` was deleted from `AppSettings` so the NavigationViewModel gate follows the per-mode `approachAutoShowGps` / `approachAutoShowDemo` alone.

**Show zones re-homed.** The menu's Markers "Show zones" toggle moved to Settings → Layers → Markers, above *Markers Appearance*, still on the persisted `markerZonesVisible` preference and the `menu_show_zones` label.

**Tracks rendering and Import/Export moved into Settings.** The menu's `Display Tracks with:` twin-box and its Import/Export pair left the menu's Tracks card (which keeps only live stats and the Track List row) and landed in Settings → Layers → Tracks; the twin-box was then re-organized into a titled **Speed Display** subsection inside *Track Speed and Direction*, the route gates (Speed colours on routes, Arrows on routes) into a **Route** subsection below it, and the chip option renamed "Speed Colors" / "Couleurs de vitesse". The menu's Route arm button now wears the same `SECONDARY` face as Import/Export.

**The Settings tabs were reordered.** The Route section (free-water pace + slow-water budget) now leads the Navigation tab; Stop detection moved beneath it from Position; the GPS-mode row left Settings Position and the Navigation/GPS-tuning group moved to Settings System above Screen, leaving the Position tab empty. The dead `onGpsModeChange` chain was trimmed through `SettingsOverlay` / `OverlayLayer` / `MapScreen` and `settings_gps_mode_desc` removed from both locales.

`gradlew :app:assembleDebug :app:testDebugUnitTest` BUILD SUCCESSFUL on every pass; `TrackRenderStringsTest` re-pinned to the new heading.

## Target Files

- `app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt` — Navigation card trim, Show zones and Tracks/Import-Export removal, Route arm face
- `app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt` — Tracks card rework (Speed Display, Route subsection), Navigation/Position/System reorder
- `app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt` · `OverlayLayerParams.kt` · `MapOverlayData.kt` — menu-data plumbing shrinkage
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — dead helpers and the `onGpsModeChange` arg
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — `autoShowMasterOverride` removal
- `app/src/main/java/ykws/android/maro/ui/map/NavigationViewModel.kt` — per-mode auto-show gate
- `app/src/main/res/values/strings.xml` · `values-fr/strings.xml` — Speed Display heading, Speed Colors chip, dead strings removed
- `app/src/test/java/ykws/android/maro/ui/map/TrackRenderStringsTest.kt` — heading pin

## Next Step

The device pass over the reorganized Settings tabs and the trimmed menu — the empty Position tab, the Navigation/System re-homes, the Speed Display and Route subsections, and the re-faced Route arm — is owed; nothing here is device-validated.
