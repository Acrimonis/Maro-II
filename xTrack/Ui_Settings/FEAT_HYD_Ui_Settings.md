# Context Hydration — Ui_Settings — 2026-10-20

**Last Bake:** 2026-10-20 19:40 UTC — written by `#bake`; absence means never baked

**Directive trace:** no covered action stopped. The rename and the expander swap ran on an explicit implementation order; no dependency was added, no machine-shaped data file was opened, the device was never touched, and every claim rests on a file read. The only git writes are the user's `#commit` and `#push` that close the session.

## State

The Layers tab's card title reads **Routes and Tracks** (EN; FR "Routes et traces"), its key renamed `settings_section_tracks_and_routes` → `settings_section_routes_and_tracks` in both locales with the one reader and the `TrackRenderStringsTest` expected/retired lists moved together. The two appearance collapsibles swapped so Routes Appearance leads and Tracks Appearance follows, adjacent with no divider; the expand-state keys `routes_appearance` and `track_rendering` are unchanged, so persisted open state survives. `:app:compileDebugKotlin` and the scoped `TrackRenderStringsTest` pass, and the review hop found no code blocker — only prose drift in one KDoc and two doc lines, and the description `settings_tracks_and_routes_desc` still reading "Recorded tracks and routes", a user-owned wording call left open.

## Target Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt` — `LayersSettings`: the renamed header and the swapped expanders
- `app/src/main/res/values/strings.xml` + `values-fr/strings.xml` — the renamed section key
- `app/src/test/java/ykws/android/maro/ui/map/TrackRenderStringsTest.kt` — expected/retired key lists

## Next Step

The device pass is the user's: open Layers and confirm the card reads Routes and Tracks with Routes Appearance first.
