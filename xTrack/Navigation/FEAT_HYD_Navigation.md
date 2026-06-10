# Hydration: Navigation

**State:** Active — cap subfeature complete. Settings toggle rows restored after being lost in PR #57 merge.

**Session summary:** Validated that NavigationState data class, DirectionLine composable, cap arrow rendering, and all constants (CAP_MIN_DP, CAP_MAX_DP, CAP_DP_PER_KNOT, CAP_MIN_SPEED_KNOTS) are present in origin/develop. Found that the Navigation settings toggle rows (Heading Line + Variable Arrow) under Display section were missing. Restored them in MapScreen.kt, added EN and FR string resources.

**Target files:**
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — Navigation sub-section under Display with toggle rows
- `app/src/main/res/values/strings.xml` — EN strings: heading_line_label/desc, cap_arrow_label/desc
- `app/src/main/res/values-fr/strings.xml` — FR strings: Ligne de cap / Flèche variable

**Next step:** apk-build.bat to verify build, then on-device visual verification of toggles.
