# Hydration: ColorManagement

**Last Bake:** 2026-09-16 12:32 UTC
**Branch:** `feature/no-black-casing`

## State

The map-surface normalization shipped through the `#implement` pipeline, and it is the pass that finally gave
the palette one home for the map's chrome. `ui.map.surface.*` now owns the fill, the corner, the padding, the
border and both alphas — `inactive`, `.corner.radius`, `.padding`, `.border.color`, `.border.width`,
`.inactive.content.alpha` and `.active.alpha`; the keys that duplicated any of them retired, including
`ui.map.toggle.inactive.background`,
`ui.map.overlay.background`, both `corner.radius` keys, `ui.map.overlay.padding`, both `ui.map.overlay.border.*`,
`ui.map.toggle.inactive.icon.alpha` and `ui.map.toggle.active.background.alpha`. One colour accessor survives —
`uiMapSurfaceInactive`, which had no reader at all before and now has every one, so the "one set of settings"
claim is finally true of the code and not only of the file. New `ui/map/MapSurface.kt` is the single painting
path: `MapSurface` paints the fill, corner, border, padding and a **content-only** fade, with `MapToggleSquare`
adding the row's fixed size and tap, which is what killed the two whole-box `.alpha()` calls that made the
tracking and lock off boxes land twice as faint.

**Also settled:** every surface wears one corner and one outline, so the zone-info line gained the border it
never had; the recenter button joined the family on `ui.accent` at the shared active alpha, still absent when
there is nothing to recenter; the land/water square takes one resolved face; and every alpha key in all three
palette files now states `1.0 = fully opaque, 0.0 = fully invisible`, with the 0–255 depth ramp flagged as the
exception and the percentage-transparency settings named as the inverse. The Ask hop sent one real defect back
— the tracking dot sat at `22 − glyphBox/2` rather than 6 dp, drifting with the emoji's metrics — now fixed by
construction, plus nine claim drifts corrected across the file, `AppConfig` and the two docs.

The session then carried the same family onto the map's bottom-left zone stack: `RegulationZoneCategoryIcon` is
painted by `MapToggleSquare` with `mapSurfaceFaceActive(colorForCategory())`, its 44 dp and 8 dp literals and its
hand-painted fill gone, the emoji on `ui.map.toggle.icon.size`, the speed number at 26 sp inside the surface's
32 dp content box, and the column gap, the strip's two insets and the zone-info line's start gap all reading
shared keys. The alpha tier flattened onto `ui.map.surface.active.alpha`, retiring `alphaForCategory()` with the
two `status.gps.alpha.*` keys. That hop's Ask pass returned fourteen findings, closed in one remediation hop
under the new one-home-per-fact rule: `MapSurface.kt` is the only home for the family's caller and exception
lists, the properties file is the source of truth for values — `AppConfig`'s two surface alpha defaults and every
doc moved onto it, and the recipe doc and the state files now name keys rather than numbers — and the two
retired keys gained their record. Two findings stay open for the user.

## Target Files

- `app/src/main/assets/colors.properties`, `ui.properties`, `maro.properties` — the surface block, the retirements and every alpha comment
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt` — one colour accessor, defaults realigned to the file
- `app/src/main/java/ykws/android/maro/ui/map/MapSurface.kt` — the one painting path
- `app/src/main/java/ykws/android/maro/ui/map/MapControls.kt`, `TrackStatusIcon.kt`, `TrackSpeedLegend.kt`, `RegulatedZoneComponents.kt`, `MapScreen.kt` — the nine surfaces
- `app/src/main/java/ykws/android/maro/ui/map/RegulatedZoneComponents.kt` — the zone tag joins the family, with the column gap and the strip's insets
- `docs/color-scheme.md`, `docs/ui-component-guidelines.md` — the rows and the recipe

## Next Step

The device judgements the two plans leave open, taken in one run: the outline on a 44 dp square beside a 22 sp
glyph, the brighter off boxes, the recenter button at `ui.map.surface.active.alpha` where it was 0.30, and now
the zone stack on that same `0.65` — which dims all five active squares with it — with the 22 sp emoji and the
26 sp number in the 32 dp box and the taller four-tag column. Two Ask findings wait on the user: the dead
`emojiForType()`/`colorForType()` pair, and an unarchived RegulatedZones plan that still teaches the superseded
hand-painted recipe. Both sit in an open walk: `xTrack/ColorManagement/FEAT_DSC_ColorManagement.md`, `## Walk`.
