# Context Hydration — MapDisplay

**Last Bake:** 2026-06-07

**State:** Active — layer refresh subfeature in progress.

**What was done:**
- Analyzed root cause of depth layer redraw on orientation switch: missing `android:configChanges` in manifest caused Activity destruction/recreation, tearing down `MapView` and all overlays.
- Fixed Compose layout: refactored `MapScreen.kt` from `if/else` + `Row`/`Column` to a single `Box` parent with stable `MapContent` slot and overlaid `DashboardPanel` via `Modifier.align()`.
- Fixed manifest: added `android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize"` to `<activity>`.

**Key files:**
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`
- `app/src/main/AndroidManifest.xml`
- `xTrack/FEATURE_SCOPE_MapDisplay.md`

**Next step:** Build APK (`apk-build.bat`), deploy, and test that overlays survive rotation without redrawing.
