<!-- scope: feature -->
# Speed carry window — remove `carryMaxSec` and its logic

**Date:** 2026-09-15 · **Status:** shipped the same day — `apk-build.bat` SUCCESS, 43 tests with the three pre-existing reds unchanged
**Request:** remove `track.heatmap.carryMaxSec` and everything that exists to serve it.

## 1. What exists today

- [`resolveSpeeds`](../../app/src/main/java/ykws/android/maro/ui/map/TrackSpeedHeatmap.kt:29) resolves each point's speed in three steps: the stored value, else a derivation from the neighbour, else the last known speed repeated while it is within `carryMaxSec`, with a GAP resetting the carry.
- The reachable set is narrow, measured this session: [`TrackRecorder.kt`](../../app/src/main/java/ykws/android/maro/data/track/TrackRecorder.kt:706) never stores a fix that has no speed, and the null-speed points we do write are GAP markers, which the carry explicitly refuses to cross.
- That leaves imported and merged tracks — [`GpxImporter.kt`](../../app/src/main/java/ykws/android/maro/data/track/GpxImporter.kt:322) stores whatever the file holds — as the only place the carry can fire, and there only when [`deriveSpeedMps`](../../app/src/main/java/ykws/android/maro/data/track/TrackSpeed.kt:28) also answers null, i.e. a neighbour sharing the timestamp or absent.

## 2. Decision

- `resolveSpeeds` becomes two steps: a GAP answers null, otherwise the stored speed, else the derived one, else null.
- The key, the `AppConfig` field and its parse, the parameter through `bandedPath` and `storedTrackRendering`, and both call sites go with it.
- Consequence accepted: an underivable imported point takes the neutral tint rather than the previous speed, so a patch that is currently smeared with a carried value becomes a visible seam. The alternative — keeping a window that no recorded track can reach — was rejected as dead configuration.

## 3. Work package

- `ui/map/TrackSpeedHeatmap.kt` — the signature, the carry state and branch, and the KDoc paragraph that describes them.
- `ui/map/MapTrackOverlayEffects.kt` — the parameter through `bandedPath`, `storedTrackRendering` and both callers (history and pinned).
- `ui/map/TrackDirectionOverlay.kt` — the comment that names the key while explaining why the arrow path never sees it.
- `config/AppConfig.kt` — the field and the parse block.
- `app/src/main/assets/maro.properties` — the key and its comment line.
- `TrackSpeedHeatmapTest.kt` — the fixture parameter and every call site; the test asserting that a backwards stall stays outside the window has no subject left.

## 4. Verification

- `apk-build.bat` green and the heatmap tests green, the three pre-existing `HeatmapRampPropertiesTest` reds excepted.
- Device: a recorded track containing a break still draws its seam dashed in the neutral colour, since that path is the GAP branch and is untouched.

## 5. Out of scope

- `unknownColor` stays: it paints the GAP seam, which remains reachable, and the underivable point.
- The families, the tick table, the legend, and this plan's sibling — the alpha ceiling removal of the same day.

## Outcome

**Shipped 2026-09-15** through a Code pass; `apk-build.bat` SUCCESS and `TrackSpeedHeatmapTest` 32/32 green, the three pre-existing `HeatmapRampPropertiesTest` reds unchanged.

- **Built:** `resolveSpeeds` is GAP → null, otherwise stored-then-derived, else null; the parameter left `bandedPath`, `storedTrackRendering` and both callers; the key, the `AppConfig` field and its parse are gone, as is the Colours rebuild key that read it.
- **Deviations, all recorded:** two tests had no subject left, not the one the plan named — the carry test's second point now resolves to null, so it went with the backwards-stall test; the GAP-seam test kept its assertions and lost its carry wording; and the rebuild-key leaf was a fourth site the plan's file list missed.
- **Residue:** the v5 block header named the carry window and was corrected in the comment pass that followed; what that pass left is the word "window" still naming the ramp's own domain in the family-run paragraph, and the legend topping out at the 35 kn row while family 8 runs to 70 — so the purple end is painted but never labelled on the scale.
- **Device check not run:** the GAP branch is untouched and the seam test is green, which is the unit-level evidence standing in for the on-device look.
