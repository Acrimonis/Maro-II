# Hydration — Ui_General

**Last Bake:** 2026-06-16 12:21

## Summary
ZoneConfig renamed to AppConfig and moved to `ykws.android.maro.config` package. All imports updated across 10 files. Semantic colour palette extended with `neutral` (#AA4FC3F7) and `absent` (#AA37474F). DashboardColors zone/speed/distance/readout aliases normalized to point at semantic status colours instead of dedicated fields. Warning colour changed from #FFA726 to deep amber #E65100.

## Key Files
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt` — renamed from ZoneConfig, new package
- `app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt` — aliases + neutral/absent added
- `app/src/main/assets/colors.properties` — warning changed, neutral + absent added
- `docs/color-scheme.md` — updated with new tokens

## Next Steps
Visual review of neutral/absent colours on dashboard. Potential settings panel neutral state colouring.
