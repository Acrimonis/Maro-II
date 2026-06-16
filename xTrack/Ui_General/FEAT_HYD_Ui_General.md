# Hydration — Ui_General

**Last Bake:** 2026-06-16 10:18

## Summary
Full colour migration completed. All hardcoded colour values (~30 unique across ~160 locations) moved to `colors.properties` loaded via `ZoneConfig`. The `RasterCache.Key` now includes a `colorsHash` field so colour changes invalidate the raster cache. `docs/color-scheme.md` updated with 20px colour swatches and new sections for all colour groups.

## Key Files
- `app/src/main/assets/colors.properties` — single source of truth for all colours (~120 lines)
- `app/src/main/java/ykws/android/maro/ui/map/ZoneConfig.kt` — 90+ colour fields loaded from properties
- `docs/color-scheme.md` — canonical colour reference with swatches

## Next Steps
Settings panel colour fine-tuning. Zone colour type visual review.
