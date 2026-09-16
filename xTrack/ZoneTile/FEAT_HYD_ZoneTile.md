# ZoneTile — Hydration Snapshot

**Baked:** 2026-09-02 21:35 UTC+2

## Active State
- **Subfeature:** none
- **Branch:** feature/zone-info

## What Changed This Session
1. **Zone info text per-line scrim** — `RegulatedZoneInfoText` lines render on a semi-transparent rounded card with 4dp corners, 3/1dp padding and 2dp line spacing. **Superseded 2026-09-15** (`feature/no-black-casing`): the navy `ui.settings.text.scrim` fill and the white text are gone — the card now reads `semantic.inactive` at `ui.button.disabled.background.alpha` with `uiTextSecondary` text, the same surface the speed-scale card wears (see the feature file's Implemented entry), and `ui.text.scrim` has no reader left.
2. **Zone line on the map overlay family** — superseded again on 2026-09-16: the card reads `ui.map.overlay.*` over the shared `ui.map.surface.inactive` (`#A8FFFFFF`), so its fill, text colour, 100–900 weight, size, corner radius, padding and column gap are all configuration, the same family the speed scale reads. The converged values are what the device must judge — type 9 → 10 sp, padding 3/1 → 6 dp, corners 4 → 8 dp, which grows a stacked set by roughly 10 dp a line — and `AppConfig.buttonDisabled*` went with the family that replaced them. `lineHeight = 14.sp` is the one type value still outside the family.

## Design Decisions
- Per-line scrim (not a single panel) so each line carries its own contrast pocket and the map stays visible between lines.
- Token-driven via `colors.properties` so the fill's colour and alpha are tunable at runtime without Kotlin changes: the colour is `semantic.inactive` and the alpha `ui.button.disabled.background.alpha`, the pair the speed-scale card reads too.

## Target Files
- `app/src/main/java/ykws/android/maro/ui/map/RegulatedZoneComponents.kt`
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt`
- `app/src/main/assets/colors.properties`
