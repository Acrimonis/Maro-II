# Hydration: ColorManagement

**Last Bake:** 2026-09-16 12:32 UTC
**Branch:** `feature/no-black-casing`

## State

The map-surface normalization shipped through the `#implement` pipeline, and it is the pass that finally gave
the palette one home for the map's chrome. `ui.map.surface.*` now owns the fill (`#A8FFFFFF`), the corner (8),
the padding (6), the border (`${ui.divider.color}` at 1) and both alphas (`inactive.content.alpha` 0.45,
`active.alpha` 0.75); the keys that duplicated any of them retired, including `ui.map.toggle.inactive.background`,
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

## Target Files

- `app/src/main/assets/colors.properties`, `ui.properties`, `maro.properties` — the surface block, the retirements and every alpha comment
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt` — one colour accessor, defaults realigned to the file
- `app/src/main/java/ykws/android/maro/ui/map/MapSurface.kt` — the one painting path
- `app/src/main/java/ykws/android/maro/ui/map/MapControls.kt`, `TrackStatusIcon.kt`, `TrackSpeedLegend.kt`, `RegulatedZoneComponents.kt`, `MapScreen.kt` — the nine surfaces
- `docs/color-scheme.md`, `docs/ui-component-guidelines.md` — the rows and the recipe

## Next Step

The three device judgements the plan leaves open, taken with the legend's own owed run: the outline on a
44 dp square beside a 22 sp glyph, the brighter off boxes, and the recenter button at 0.75 where it was 0.30.
