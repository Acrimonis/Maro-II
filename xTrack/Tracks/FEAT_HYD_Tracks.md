# Context Hydration — Tracks — 2026-09-15

**Last Bake:** 2026-09-15 19:58 UTC — written by `#bake`; absence means never baked
**Branch:** feature/no-black-casing, created from `b2f6d69`

## State

Shipped on this branch: the per-type widths are configuration — `track.width.live` / `.selected` / `.newest` / `.pinned` / `.history` / `.selected.casing` = 12 / 14 / 10 / 8 / 6 / 22 px, every code default mirroring the file key for key — the selected track is opaque, cased and drawn above every other trace, the chevrons are in proportion to their own line with an outside-only dark rim, and the controls are separated: arrows follow the stored mode alone, the drawer eye picks only the selection's fill and persists on the first tap, and the legend was given the eye as a trigger. **Open, in `260914_FEAT_PLN_Tracks_pinned-cue-casing.md` §2**, whose three items were reviewed twice and are executable as written: §2.1 the arrowhead rim at the line's weight, §2.2 the legend gate on banded strokes rather than the focused fill, §2.3 the strip's chrome — the toggle row's width and its own background token. The walk's level of 2026-09-15 is open, Active 6, its items 6 and 7 being §2.2 and §2.3 of that plan. The rim itself stays parked with the alpha-stacking finding and the three shapes that would repair it.

## Target Files

- `app/src/main/java/ykws/android/maro/ui/map/TrackDirectionOverlay.kt` — the rim offset's argument and the comments that argue the tempered reference
- `app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt` — `legendVisibleFor` and the painted-id set it needs
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — the gate's caller, the toggle row's geometry, the legend's modifier
- `app/src/main/java/ykws/android/maro/ui/map/TrackSpeedLegend.kt` — the strip's alignment and background
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt` — the new legend token's accessor, then `app/src/main/assets/colors.properties` for the token
- Tests: `app/src/test/java/ykws/android/maro/ui/map/TrackDirectionOverlayTest.kt`, `TrackRenderModePathTest.kt`

## Next Step

Implement §2.1 on its own, then §2.2 and §2.3 as one ordered pair, per the plan's Order line; `apk-build.bat` with the scoped `ui.map` tests green, the pre-existing reds outside that scope untouched.
