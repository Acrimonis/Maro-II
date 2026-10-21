# Context Hydration — Ui_General — 2026-10-21

**Last Bake:** 2026-10-21 16:30 UTC — written by `#bake`; absence means never baked

**Directive trace:** All five covered action classes were met — no dependency was added, no machine-shaped data file was opened, every write followed the user's own orders (`#new wheel-launch`, the flick discussion and its `#impl`, the box-path fix, then `#bake`/`#commit`/`#push`), the device was touched only through our own logcat reads after the user's two test runs, and every claim about the code rests on a file read. Named rather than hidden: the box-path fix shipped before the first device pass could test it, and only the second run's `MaroWheel` trace proved the flick stays open.

## State

One landing on `feature/wheel-launch`, cut from `origin/develop` with `--no-track`.

**The dropdown wheel splits its release by velocity.** `WheelPolicy.wheelFlingKeepsOpen(velocityYPx, minFlingVelocityPx)` is the one comparison (`abs(velocityYPx) >= minFlingVelocityPx`) and is pinned by two `WheelPolicyTest` cases. `DropdownWheel`'s Final-pass observer and `DropdownRow`'s box drag each feed a `VelocityTracker` per gesture: a flick (release at or above `viewConfiguration.minimumFlingVelocity`) leaves the popup open — the wheel's snap carries the band, so a long list is browsed — while a dragged non-flick lift commits the band and closes. The box drag swapped `detectVerticalDragGestures` (no velocity) for an `awaitEachGesture`; a tap stays the rows' `clickable`, an outside tap commits the band, back cancels a browse without a write, and a cancelled drag writes nothing.

**The first device pass caught the box path.** The trace showed only `box drag start` / `box drag end` and no wheel observer — the flick was the opening drag on the box, still committing and closing. The box path is now the same `awaitEachGesture` + `VelocityTracker` shape, and the second run confirms `keepsOpen=true` at 2.5–5.4k px/s and `false` near 0. The temporary `MaroWheel` instrumentation was stripped before the commit.

Gates: `:app:compileDebugKotlin`, the scoped `ui.components` suite and `:app:assembleDebug` green; the flick confirmed on device by the user's two runs.

## Target Files

- `app/src/main/java/ykws/android/maro/ui/components/WheelPolicy.kt` · `app/src/test/…/WheelPolicyTest.kt` — `wheelFlingKeepsOpen` and its two cases
- `app/src/main/java/ykws/android/maro/ui/components/DropdownWheel.kt` — the Final-pass velocity gate and the flick-keeps-open branch
- `app/src/main/java/ykws/android/maro/ui/components/DropdownRow.kt` — the box drag as `awaitEachGesture` + `VelocityTracker`, the KDoc restated
- `docs/ui-component-guidelines.md` §2.10 · §2.12 · §2.15 — the flick exception and "back cancels a browse"
- `xTrack/Ui_General/261021_FEAT_PLN_Ui_General_wheel-flick-stays-open.md` — the plan of record; `261017_…wheel-commit-on-release.md` marked superseded where it states the commit rule

## Next Step

Nothing owed on the wheel behaviour itself. Retirement candidates reported, not moved: `261017_FEAT_PLN_Ui_General_wheel-commit-on-release.md` (its rule is superseded and its work is in `## Implemented`) alongside the already-superseded `261005…` and `260929…` plans — `#archive` does the moving.

Owed in code, recorded and folded nowhere: the wheel observer's S-1 (consumption-blind `dragged` discriminator) and S-3 (field drag reading a band one `scrollBy` stale); the box path does not hand its flick momentum to the wheel's snap (a box flick keeps the popup open but stops, while the wheel's snap only carries an in-popup flick); and a flick with very few pointer samples may read near-zero velocity, which only the device can judge.
