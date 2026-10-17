# Context Hydration — Ui_General — 2026-10-17

**Last Bake:** 2026-10-17 06:21 UTC — written by `#bake`; absence means never baked

**Directive trace:** All five covered action classes were met — no dependency was added, no machine-shaped data file was opened, every write followed the user's own orders (`#new sp-ui`, `#focus ui general`, the slider order, the wheel discussion and its `#impl`, the transition order and its `#impl`, then `#bake`/`#commit`/`#push`), the device was never touched, and every claim about the code rests on a file read. Named rather than hidden: the Ask hop on the wheel landing could not run the build, having no shell, so its green state is the Code hop's word; and the settings-slider normalisation ran as one Code hop with no independent review.

## State

Three landings on `feature/sp-ui`, cut from `origin/develop` at `cdbd887`.

**The settings sliders went onto one component.** `SliderRow` and `ScaledSliderRow` now bottom-align their trailing content, so a value sits on the track it names rather than beside a wrapping description; the tracks count row, the routes count row and `BoatSizeSlider` — three hand-rolled copies — are each one `SliderRow` call, and the boat size's value takes the row family's face instead of 14 sp accent. The `%` and `m` suffixes moved into `settings_value_pct_fmt` and `settings_value_m_fmt` beside `settings_value_dp_fmt`, and §2.2 plus §4 of the component guidelines now state both rules, including the missing one-knob anti-pattern bullet.

**The dropdown wheel commits on release.** The band is the value: a row tap, a drag inside the popup, the box drag's `onDragEnd` and an outside tap each write the band and close, while back and a cancelled drag close writing nothing — the cancel is gone by design. `wheelCentredRow` is the one bridge over `wheelCentredIndex`, and the snap lost its reader, so `snapWheelToSlot`, `wheelSnapTargetSlots` and their unit test went with it.

**The popup transition is symmetric.** An `Animatable` at 120 ms with a 4 dp rise runs on open and its exact reverse on close, the window held for the exit because a `Popup` dies with its composition. `Open` / `Closing` / `Closed` is the whole state, one `close()` guards every door so the fading window writes nothing, and the drawn motion is a `graphicsLayer` alpha and translation, so no measured size or offset moves.

Gates: `:app:compileDebugKotlin` green at each landing, and the scoped `ui.components` suite green on the wheel landing — no device pass has been taken.

## Target Files

- `app/src/main/java/ykws/android/maro/ui/components/SliderRow.kt` — bottom alignment, the `[☐ ×10][value]` group travelling with it
- `app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt` · `RegulatedZoneComponents.kt` — the count rows and `BoatSizeSlider` through `SliderRow`
- `app/src/main/res/values/strings.xml` · `values-fr/strings.xml` — `settings_value_pct_fmt`, `settings_value_m_fmt`
- `app/src/main/java/ykws/android/maro/ui/components/DropdownRow.kt` — the phase set, `open()`/`close()`, the band commit and the popup animation
- `app/src/main/java/ykws/android/maro/ui/components/DropdownWheel.kt` — the Final-pass release observer and `wheelCentredRow`
- `app/src/main/java/ykws/android/maro/ui/components/WheelPolicy.kt` · `app/src/test/…/WheelPolicyTest.kt` — the snap helpers retired
- `docs/ui-component-guidelines.md` §2.2 · §2.10 · §2.12 · §2.15 · §4 — the rules both landings moved
- `xTrack/Ui_General/261017_FEAT_PLN_Ui_General_settings-slider-normalization.md` · `261017_FEAT_PLN_Ui_General_wheel-commit-on-release.md` — the two plans of record

## Next Step

The device pass, owed to the user: a slider value reading at the bottom of its row and the three normalised rows in place; the wheel committing the band the finger leaves on both route ends and the algorithm row; the popup fading in and out with nothing accepting a touch during the fade; and a drag released outside the popup window, which the plan names as platform-dependent.

Owed in code, recorded and folded nowhere: the wheel plan's §8 — S-1 the consumption-blind drag discriminator, S-2 the snap that can no longer decide a value (weakened, not gone, since it may animate during the 120 ms fade), S-3 the field drag reading a band one `scrollBy` stale, S-4 the index-to-value write spelled twice — and the one should-fix the second review added, the `phase == Open` predicate living in three lambdas.
