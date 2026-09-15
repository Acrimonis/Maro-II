# Context Hydration — Tracks — 2026-09-15

**Last Bake:** 2026-09-15 19:58 UTC — written by `#bake`; absence means never baked
**Branch:** feature/no-black-casing, created from `b2f6d69`

## State

Shipped on this branch: the per-type widths are configuration — `track.width.live` / `.selected` / `.newest` / `.pinned` / `.history` / `.selected.casing` = 12 / 14 / 10 / 8 / 6 / 22 px, every code default mirroring the file key for key — the selected track is opaque, cased and drawn above every other trace, the chevrons are in proportion to their own line with an outside-only dark rim, the controls are separated, the arrowhead's rim reads the line's own width, and the legend gate asks whether a banded stroke is painted on the map rather than what the focused track's fill is. **The strip's chrome was then rebuilt after the device rejected its first shape**: it is one toggle button wide with its left edge on the row's own gutter, its card paints what a disabled toggle paints (white at 50 %, `copy(alpha = …)` replacing the token's own 20 % rather than multiplying it), the `ui.legend.background` token is gone from all four files that named it, and with it the six Ask-hop lows. Two nits stay in the plan's §5: a code comment repeating the superseded ~10 % arithmetic, and the test's hand-mirrored outer wiring. `apk-build.bat` SUCCESS with 126 scoped `ui.map` and `config` tests green, and the work sits uncommitted on top of `16a4f09`, which carries the bake and the plan's rewrite.

## Target Files

- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — the legend's width, start and padding; the toggle row's shared square and gutter; the gate's arguments and derived state
- `app/src/main/java/ykws/android/maro/ui/map/TrackSpeedLegend.kt` — its own padding, the label fit and the stale KDoc
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt`, `app/src/main/assets/colors.properties`, `docs/color-scheme.md` — the legend token, to be removed
- `app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt` — `legendVisibleFor` and the painted-id set
- Tests: `TrackDirectionOverlayTest.kt`, `TrackRenderModePathTest.kt`

## Next Step

Correct the two nits the Ask hop left — the strip's background comment and the test's outer wiring — then run the device look at all three modes with a translucent track set, whose cases are in the plan's §3.
