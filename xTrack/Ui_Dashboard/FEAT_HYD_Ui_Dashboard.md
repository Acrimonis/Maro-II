# Context Hydration — Ui_Dashboard — 2026-10-03

**Last Bake:** 2026-10-03 17:51 UTC — written by `#bake`; absence means never baked

**Directive trace:** All five covered action classes were met with a verdict line and none stopped — no dependency added, no machine-shaped data file opened, every step taken on an explicit order, the device untouched (its one verdict came from the user's own run), and every claim about the code backed by a file read. The one git write, the branch creation, ran on the user's own `#new` invocation; nothing is committed yet.

## State

The bottom dashboard family sits on one auto-resizing frame: `dashboardBaseHeight` is the named floor, the Layer-0 grid and Where-Am-I ride `DrawerScaffold`, and the band the map keeps clear is one animated value read from the open panel's measured height, so a taller dashboard pushes the map rather than covering it. The corners stay square at every size — the round-once-grown effect was withdrawn on the device's word — and the walk's residue landed: the dead `MeasureHeight` probe, the unused non-wrap ceiling, the validation-badge residue and the stray landscape measurement are gone, `bandHeightFor` lives in `ui/components`, and the outer inset and grid spacing tightened to 4h/2v and 4 dp. `apk-build.bat` and `gradlew :app:testDebugUnitTest` are green; the open items are W20 and the device pass.

## Target Files

- `app/src/main/java/ykws/android/maro/ui/components/DrawerScaffold.kt` — the shared frame: the optional header, `backgroundColor`, `wrapContentMaxHeight`, `onMeasuredHeight`, and no shape gate
- `app/src/main/java/ykws/android/maro/ui/components/DashboardBandGeometry.kt` — the pure `bandHeightFor` the map's height reads
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — `dashboardBaseHeight`, the `DashboardBandState` holder, `MapContent`'s band
- `app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt` — the migrated 2×2 grid, the one `padV` and the off-water caption
- `app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt` — the panel slots, the ceiling and the portrait-only measurement

## Next Step

Take the device pass: the square corners at every size, the map shrinking and returning as a card opens and closes, the tightened inset, the landscape column's spacing, and whether the dashboard sits behind the navigation bar (plan §14 W19).
