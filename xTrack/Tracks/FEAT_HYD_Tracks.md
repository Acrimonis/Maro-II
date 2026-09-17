# Context Hydration — Tracks — 2026-09-17

**Last Bake:** 2026-09-17 10:03 UTC — written by `#bake`; absence means never baked
**Branch:** feature/twks-props, created from `origin/develop` at `2fef14e`

## State

The menu's render control is two axes now, not one mode. `AppSettings.trackArrows` (`track_arrows`, default
off) and `trackColours` (`track_colours`, default on) replace the retired `track_render_mode`, which
`migrateRenderAxes()` reads once at cold start — only while neither new key exists — and erases in the same
edit, so an install holding `SIMPLE`, `DIR_SPEED` or `HEATMAP` keeps exactly the look it had;
`TrackRenderMode` and `menu_render_mode_*` left the tree outright, enum, key, parse, strings and both test
names with them. The menu row is the twin box (`MultiSelectRow`, new, in `ui/components` beside a moved
`SegmentedRow`) under the caption "Display Tracks with:", one callback per chip
(`onTrackArrowsChange` / `onTrackColoursChange`), and `trackRenderPlan`, `selectionBandedAfterTap` and
`legendVisibleFor` read the flags while the rebuild keys follow them. The same day's second pass put the
standalone `Speed` vector on the drawer header's toggle — keeping the accent/inactive tint readout — and
narrowed the legend gate to follow the open track's fill: flipping a selection gold hides the scale while
other painted tracks stay banded, an eye-banded selection still raises it with `Colours` off, and with
nothing selected the flag alone decides.

**Open and recorded:** the device pass is owed over the four combinations, the new glyph, the new gate, and
a migrated install's two flags with the vanished old key. The migration's mapping is the one decision no
test covers — its correctness rests on the constructor's argument order (`SettingsManager.kt:465`) — so
lifting `legacy mode → (arrows, colours)` into a pure function and pinning the three tokens is the cheap
close. One dead import (`selectableGroup`) is still in `MapScreenSettingsOverlay.kt`. The 2026-09-15 walk
level stays open on items 12 and 13, which is what blocks this file's own fold: `#bake` folded no section
this pass, every one of them still holding an open todo or a doc mapping.

## Target Files

- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — the two flags, their key constants and the one-edit migration
- `app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt` — `trackRenderPlan`, `selectionBandedAfterTap`, `legendVisibleFor`, the rebuild keys
- `app/src/main/java/ykws/android/maro/ui/components/MultiSelectRow.kt` — the twin box, with `SegmentedRow` beside it
- `app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt` — the row, its caption and the two chip callbacks
- `app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt` — the drawer header's `Speed` toggle and the two `bandedOn` sites
- `app/src/main/java/ykws/android/maro/ui/icons/speed.kt` — the standalone speedometer that toggle wears

## Next Step

Run the owed device pass — the four chip combinations, the legend gate against the selection's fill, and
the migrated flags on an install that held each retired value — then decide whether the migration's pure
mapping and the dead import earn a short cleanup hop.
