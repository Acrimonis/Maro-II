# Hydration — Ui_General

**Last Bake:** 2026-06-16 17:04

## Summary
ButtonColors subfeature progressed through color harmonization of all round UI buttons to the dark dashboard theme. All zoom ± buttons, settings gear, GPS/EarthWater toggles, and dashboard tile accent strips now use `#CC16213E` background (80% opacity dark navy) with `#E0E0E0` icon tint from the centralized `colors.properties` palette. Hardcoded `0xFF...` literals were replaced with named colour tokens. An inline `#` comment in `colors.properties` was breaking `Color.parseColor()` — the comment was removed to fix silent parse failures. A `${variable}` interpolation timing issue in `AppConfig.init()` was also corrected to ensure alias resolution happens before colour token consumption. The subfeature remains todo-pending for verification/deploy.

## Key Files
- `app/src/main/assets/colors.properties` — button background/tint tokens, inline # comment removed
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt` — alias interpolation timing fix

## Next Steps
Rebuild and deploy to verify dark button theme renders correctly on device. Confirm all control buttons (zoom ±, settings, GPS, EarthWater, dashboard accent strips) use the harmonised colours.
