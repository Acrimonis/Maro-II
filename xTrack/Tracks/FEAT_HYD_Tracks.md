# Context Hydration — Tracks — 2026-09-16

**Last Bake:** 2026-09-16 06:31 UTC — written by `#bake`; absence means never baked
**Branch:** feature/no-black-casing, created from `b2f6d69`

## State

The selection rendering and the legend work are shipped and reviewed: the selected track is opaque, cased and drawn above every other trace, the arrowhead's rim reads the line's own width, the legend gate keys banded strokes through one shared entry point, and the speed scale is one toggle button wide on the row's gutter with its gap equal to that same gutter. Since 2026-09-16 the map's chrome around it is configuration: two families, `ui.map.toggle.*` and `ui.map.overlay.*`, over one shared `ui.map.surface.inactive` (`#A8FFFFFF`), so the five squares' size, radius and glyph size and both cards' fill, text colour, 100–900 weight, size, radius, padding, border and gaps all live in `colors.properties`; the `ui.button.disabled.*` trio and three `status.*.alpha.*` keys went with it, `status.gps.alpha.active` staying for the regulated-zone icons that read it. `apk-build.bat` SUCCESS and 126 scoped tests green. **Open and recorded:** the legend's label box is back to 12 dp where the second pass had lifted it to 16, the zone line's converged padding takes its line gap from 4 to 14 dp, three boxes read the glyph dim where the keys and docs say four, `EarthWaterIcon`'s inactive arm is dead code its call site never reaches, and several keys now have no reader — `ui.text.scrim`, two retired-by-use alphas and four state colours.

## Target Files

- `app/src/main/assets/colors.properties` — the two families and the shared source, with the retired keys gone
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt` — the eighteen accessors, their loader lines and the retired ones
- `app/src/main/java/ykws/android/maro/ui/map/MapControls.kt` and `TrackStatusIcon.kt` — the five squares
- `app/src/main/java/ykws/android/maro/ui/map/TrackSpeedLegend.kt`, `RegulatedZoneComponents.kt` and `MapScreen.kt` — the two cards and the row's geometry
- `docs/color-scheme.md` and `docs/ui-component-guidelines.md` — the family sections and the recipes

## Next Step

Judge the converged values on a device — the legend's label fit at the largest font scale, the zone stack's new line gap at three lines, and the mid-grey text on the 66 % fill over pale shallow water — then clear the readerless keys and the `EarthWaterIcon` arm in one hop.
