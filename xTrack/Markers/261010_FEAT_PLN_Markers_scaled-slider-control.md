<!-- scope: feature -->
# Markers — the scaled slider: a ×10 range toggle, extracted as a named control scaffold

**Date:** 2026-10-10 · **Status:** implemented — ordered by the user's word, *"range to 2500 is not practical. next to the value add a checkbox 'x 10' off by default … Make a control scaffold out of this and find a name."*, and landed on `feature/rte-mark-cost` (see `## Implemented`).

## Assessment — what stands today

- **The chain is three deep.** The wizard's [`SliderStep`](../../app/src/main/java/ykws/android/maro/ui/markers/wizard/steps/SliderStep.kt:19) wraps one [`SliderRow`](../../app/src/main/java/ykws/android/maro/ui/components/SliderRow.kt:32), which draws one [`SliderControl`](../../app/src/main/java/ykws/android/maro/ui/components/SliderRow.kt:93) — label + value over the bare slider, the settings rows and this wizard sharing the one implementation.
- **The range is a fixed parameter.** Both the Radius/Width and the Proximity steps pass `range = 0.0..2500.0` (raised this session) — a flat span that is **impractical**: 2500 m across ~100 steps loses the fine control a 250 m span gave.
- **The value is not close to the slider.** [`SliderRow`](../../app/src/main/java/ykws/android/maro/ui/components/SliderRow.kt:44) puts the label and the value in one `Arrangement.SpaceBetween` row **above** the slider, so the value floats at the container's far right rather than beside the track it names.
- **The request.** Keep the fine 250 m default, open 2500 m on demand through an **off-by-default ×10 checkbox**, draw the value **right-aligned close to the slider with the checkbox just to its left**, and lift the whole thing into one **named, reusable control**.

## The control — name and shape

- **The name: `ScaledSliderRow`.** It sits in the `SliderRow` / `SliderControl` family (`ui/components`), so the file stays [`SliderRow.kt`](../../app/src/main/java/ykws/android/maro/ui/components/SliderRow.kt:1). The name states what is new — a range that **scales** — and reads beside its siblings. Alternatives weighed and set aside: `MultiplierSliderRow` (names the mechanism, not the row), `DecadeSliderRow` (too clever), `RangeToggleSliderRow` (long).
- **One header row, one trailing group.** `[ label + description ] ····· [ ☐×10 ][ value ]` — the label column takes the weight, and the trailing group is **tight**: the checkbox immediately left of the value, the value at the row's right edge, a short gap between them and the track beneath. **The value moves from the header's far right into that trailing group**, which is the layout the request asks for.
- **The toggle is the caller's state, off by default.** `ScaledSliderRow` takes `scaled: Boolean` and `onScaledChange`; the wizard keeps it in a `remember` per step (or the form), so both range steps open at the fine span.
- **The range follows the toggle.** Off → `0..250 m` at the base step; on → `0..2500 m`. The end label under the track reads the active ceiling, so `250 m` and `2500 m` are what the two states show.
- **A value beyond the ceiling clamps, never jumps.** Turning the toggle **off** with a value above 250 clamps it to 250 (the row reports the clamp through `onValueChange`), matching every other bounded slider's "a stored value is never a promise" read.

## Plan

1. **The scaffold.** Add `ScaledSliderRow` to [`SliderRow.kt`](../../app/src/main/java/ykws/android/maro/ui/components/SliderRow.kt:1) — the header row with the tight `[checkbox][value]` trailing group, the range chosen from the toggle, the clamp on switching off — reusing `SliderControl` for the track. Its KDoc states the layout order once; `docs/ui-component-guidelines.md` §2.2 gains the row's note.
2. **The ×10 label.** The checkbox's label and its content description live in `values/strings.xml` and `values-fr/strings.xml` (`wizard_range_scale_x10` and its `cd_` twin); the literal `×10` is never in the composable.
3. **The wizard adopts it.** [`SliderStep`](../../app/src/main/java/ykws/android/maro/ui/markers/wizard/steps/SliderStep.kt:19) passes `baseRange = 0..250`, `scale = 10` and the toggle through to the scaffold; the Radius/Width and Proximity steps drop the flat `0..2500` for it. The 2500 m flat range shipped this session is reverted by it.
4. **Tests and the record.** A JVM test pins the range pair (off `0..250`, on `0..2500`), the clamp on switching off, and the step counts; the row's note lands in `docs/ui-component-guidelines.md`.

## Open questions

- **The checkbox's face.** A Material3 `Checkbox` styled to the house tokens, or a compact toggle chip in the `MultiSelectRow` family. Recommendation: the `Checkbox`, since it carries the on/off meaning the request names.
- **The coarse step when scaled.** 25 m across 2500 m is 99 steps (fine); a 50 m or 100 m step at the ×10 span would make the drag less fiddly. Recommendation: keep 25 m — the toggle already makes coarse jumps rare.
- **Where the toggle's state lives** — a per-step `remember` (resets each wizard open) or the form (survives a step's back-and-forth). Recommendation: the form, so a user who set a 1200 m radius sees the ×10 back on when they return.

## Implemented

**Landed 2026-10-10** on `feature/rte-mark-cost` — the scaled slider. [`ScaledSliderRow`](../../app/src/main/java/ykws/android/maro/ui/components/SliderRow.kt:83) joins [`SliderRow.kt`](../../app/src/main/java/ykws/android/maro/ui/components/SliderRow.kt:1): the header keeps the label and description on the left and a tight `[☐×10][value]` group at the right — the checkbox immediately left of the value, the value right-aligned close to the track beneath — with the range `0–250 m` off and `0–2500 m` on, a clamp on switching off, and the `×10` label in both locales. [`SliderStep`](../../app/src/main/java/ykws/android/maro/ui/markers/wizard/steps/SliderStep.kt:19) was rewritten onto it and the wizard's Radius and Proximity steps adopt it, the flat 2500 m range this session briefly shipped now reverted. `gradlew :app:assembleDebug` SUCCESSFUL. **Deviations**: the toggle's state is a per-step `remember` seeded by the value (>250 ⇒ on), not the form; and the Phase 4 JVM test is not written (the range maths is inline). **Owed**: the device pass — the toggle's feel and the row's height in the wizard's tight frame.
