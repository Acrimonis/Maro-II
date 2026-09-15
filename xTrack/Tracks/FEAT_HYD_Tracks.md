# Context Hydration — Tracks — 2026-09-15

**Last Bake:** 2026-09-15 14:06 UTC — written by `#bake`; absence means never baked
**Branch:** feature/no-black-casing, created from `b2f6d69`

## State

Two things shipped here today, both green and both uncommitted. First, the selected track's 16f black casing is deleted: `SELECTED_CASING` and its uses on the gold and banded paths, the chevron casing in `TrackDirectionOverlay` with its stroke width and draw block, the `arrowCasingAppearance` field, and the banded path's now-unread `selected` flag — so the selected track is drawn as its core alone, above every other overlay. Second, the per-type widths were ported from `2728c78` with every rim piece left behind: `track.width.live=12`, `.selected=12`, `.newest=10`, `.pinned=8`, `.history=6` with their `AppConfig` accessors, a per-type width resolver replacing the four constants, the gold core and all nine live-line sites reading the keys, the newest track derived from recency rather than the loop's index, and the five values in the rebuild key list. `apk-build.bat` SUCCESS on both passes, the scoped track tests green, and the three `HeatmapRampPropertiesTest` reds are the pre-existing `track.heatmap.*` file-versus-default drift. The rim is parked in `260914_FEAT_PLN_Tracks_pinned-cue-casing.md` with the alpha-stacking finding and the three shapes that would repair it, and the walk's item 3 closed on reading 4 — no dark stroke, no new map cue — with the rim's reading parked beside it.

## Target Files

- `xTrack/Tracks/260914_FEAT_PLN_Tracks_pinned-cue-casing.md` — the requirements as they now stand, the parked rim and its three candidate shapes
- `app/src/main/assets/maro.properties` — the `track.width.*` group
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt` — the five accessors
- `app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt` — the per-type resolver, the recency-derived newest track, the rebuild keys
- `app/src/test/java/ykws/android/maro/ui/map/TrackOutlineTest.kt` — width mapping, recency, and the shipped keys against the defaults

## Next Step

Take the two changes to a device — the selection with no casing, and the 12 / 10 / 8 / 6 widths — then decide whether the rim returns and in which shape. `feature/track-speed` remains the reference for the abandoned rim implementation and is already pushed.
