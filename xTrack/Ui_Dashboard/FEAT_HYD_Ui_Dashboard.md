# Hydration — Ui_Dashboard

**Last Bake:** 2026-06-15 08:29 UTC
**Branch:** feature/ui-general
**Build:** green

## Session State

Removed the 64sp max clamp from AutoSizeValue (coerceIn → coerceAtLeast) so dashboard value text takes all available cell space. Added smart speed format: integer when >= 10 kn, decimal when < 10. New "size dash" subfeature created to track immersive layout verification.

## Target Files

- `app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt` — AutoSizeValue clamp removed (line 266), SpeedCard smart formatting (line 687)
- `app/src/main/res/values/strings.xml` — added `dash_value_kn_int`
- `app/src/main/res/values-fr/strings.xml` — added `dash_value_kn_int`

## Next Step

Verify dashboard sizing in immersive mode (size dash subfeature todos). Consider increasing landscape dashboard width.
