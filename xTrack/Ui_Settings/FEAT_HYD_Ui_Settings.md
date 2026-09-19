# Context Hydration — Ui_Settings — 2026-09-19

**Last Bake:** 2026-09-19 12:44 UTC — written by `#bake`; absence means never baked

**Directive trace:** no covered action stopped since the last bake — no dependency was added, no machine-shaped data file was opened, no work began without an explicit order, the device was never touched, and every claim about the code came from a file read. One sub-task returned work other than what it was sent for, and that divergence was reported rather than absorbed.

## State

Branch **`feature/extra-settings`**, whose first change — the map strokes and shoreline colours — was committed as `82b2b1d`, rebased onto `origin/develop` and pushed as `217a405`, with a pull request open. On top of it sit two more changes, uncommitted: the heading line and head arrow appearance (seven settings, the Speed Colour mode, an Appearance expander under each toggle) with its four device-review corrections, and a colour-row tidy that removes the Pick text from every `ColorRow` and records the rule in the guidelines.

- The arrow/line change built SUCCESS, the scoped run at 233 tests holding its six pre-existing drift reds; the colour-picker change has never compiled, three builds having died on a lock over `R.jar` held outside Gradle's daemon.
- **Open, and the records lead the code here:** the cap-arrow repairs the Ask hop named — the shaft inset's divisor (`sin`, not `tan`), the head capped against a short arrow, and the inset extracted as a pure tested function. The plans and the Navigation rules now state the corrected geometry; the code still carries the old.
- Open: the device pass over both changes, and the wording finding that the heading line's colour row reads **Default colour** with no alternative to default from.
- Logged: the six properties-versus-`AppConfig` reds are a live contradiction in the heatmap ramp, the track-outline widths and the tap-flash alpha; the px-to-dp pass over the map's paint code stays parked, and the direction line's dash has left it, being proportional now.

## Target Files

- `app/src/main/java/ykws/android/maro/ui/map/MapOverlays.kt` — the arrow and the line: parameters, the head derivation, the dash ratios, the shaft inset
- `app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt` — the two Appearance expanders paired with their toggles, and the `ColorRow` tidy
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — the seven settings, their seeds and their clamps
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt` — the moved keys' readers and the new defaults
- `app/src/main/assets/maro.properties` — the five new keys, and the direction line's colour stripped to its opaque hue
- `app/src/main/res/values/strings.xml`, `app/src/main/res/values-fr/strings.xml` — the new labels in both locales, and the retired pick label gone from both
- `app/src/test/java/ykws/android/maro/ui/map/NavigationOverlayAppearanceTest.kt` — the head derivation and band lookup coverage
- `app/src/test/java/ykws/android/maro/data/settings/NavigationAppearanceSettingsTest.kt`, `app/src/test/java/ykws/android/maro/config/CoastlineAppearancePropertiesTest.kt` — the seeds, clamps and the pinned shipped keys
- `docs/ui-component-guidelines.md`, `docs/color-scheme.md` — the colour-row rule, and the swept-colour pointers
- `xTrack/Ui_Settings/260919_FEAT_PLN_Ui_Settings_heading-line-and-arrow-appearance.md`, `xTrack/Ui_Settings/260919_FEAT_PLN_Ui_Settings_colour-row-pick-label.md` — the two plans, their statuses and their outstanding items

## Next Step

The cap-arrow repairs the Ask hop named, then a build and the device pass over both changes.
