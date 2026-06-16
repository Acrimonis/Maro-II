# Hydration — Ui_General

**Last Bake:** 2026-06-16 13:58

## Summary
Alias interpolation landed in `AppConfig.init()` — `${key}` references in `colors.properties` values are now resolved at load time, unblocking the colour token chain. Green status entries (8 in total) were normalized to point at `ui.dashboard.status.success` instead of hardcoded values. The `overlay.lowDepth` colour was reassigned from neutral to the error palette (`#CCB71C1C`, dark red), correcting a visual misclassification. The `nodata` colour was migrated from absent to a distinct pale yellow (`#60FFF59D`) for clear differentiation from real depth data. `zoneNormal` was removed from the palette (absent handles that role now). All semantic status colours (success/warning/error) are rendered at 80% opacity for a softer integration with the map. `color-scheme.md` updated with alias chain documentation and the new token entries. Build: green.

## Key Files
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt` — `${key}` alias resolution in `init()`
- `app/src/main/assets/colors.properties` — green entries → `success` alias; lowDepth → error; nodata → pale yellow; zoneNormal removed
- `docs/color-scheme.md` — alias chain documentation, new tokens

## Next Steps
Visual review of the new 80% opacity semantic colours on the map overlay. Monitor for any missing alias references that might fall through to raw property names.
