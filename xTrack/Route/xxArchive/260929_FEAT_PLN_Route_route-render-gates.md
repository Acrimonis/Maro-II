<!-- scope: feature -->
# The two route render gates

**Opened 2026-09-29** on the user's own word (`#impl`), on branch `feature/rte-color`.

## The requirement (the user's word)

The Settings switches **Speed colours on routes** (`routeSpeedColor`) and **Arrows on routes**
(`routeSpeedArrows`) do **not** supersede the menu's two track chips — **Colours** and **Arrows**.

- Each switch *gates* its chip: at `on` a route follows that chip; at `off` a route never takes it.
- **Arrows on routes** on → a route draws chevrons exactly while the Arrows chip is on; off → never.
- **Speed colours on routes** on → a route bands by speed exactly while the Colours chip is on;
  off → the route is always drawn in its own pair (`tracking.color.routeFrom` / `routeTo`).
- So the chips keep their say for routes, and a switch only removes the chip's reach when it is off.

## What the change was

The arrow gate already matched (R38): a route's chevrons are `trackArrows && routeSpeedArrows`. The
colour gate did not — it **replaced** the Colours chip (R37 as written), so a route banded on
`routeSpeedColor` alone, whatever the chip said. This pass makes it an AND-gate in the route role's
one home: `trackColours && routeSpeedColour`.

## Sites

- [`MapTrackOverlayEffects.kt`](../../app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt) —
  `trackRenderPlan`'s route branch bands on `trackColours && routeSpeedColour`; `selectionBandedFor`
  reads the same for a selected route; the rebuild keys add the route colour pair whenever the route is
  **not** banded (`!(trackColours && routeSpeedColor)`) and read the ramp on `trackColours` alone. The
  KDocs on `routeTrackRenderPlan`, `TrackRenderPath.ROUTE`, `trackRenderPlan`, `legendVisibleFor`,
  `selectionBandedFor`, `legendVisibleForState` and `bandedStrokeOnMap` follow.
- [`SettingsManager.kt`](../../app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt) —
  the `trackColours` and `routeSpeedColor` KDocs now state the join.
- [`maro.properties`](../../app/src/main/assets/maro.properties) — the two gates' own comment.
- [`MapScreen.kt`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt) — the legend gate's comment.
- [`260922_FEAT_PLN_Route_ask-policy-and-target-validity.md`](260922_FEAT_PLN_Route_ask-policy-and-target-validity.md) —
  **R37 amended** and §6's rendering sentence corrected.

## Left as-is, by decision

- The drawer **eye** (D10) still does not reach a route: the user's word names the two chips, and the
  route role keeps its own gates. A route opened in the drawer bands only through the chip-and-gate pair.

## Tests

- `TrackRouteRoleTest.theSpeedColourGateJoinsTheChipsToBandARoute` (was `…ReplacesThePairWithTheRamp`) —
  banded only with both on; the gate alone and the chip alone each land on the route pair.
- `TrackRouteRoleTest.aRouteWithItsGateOffTakesItsOwnPairWhateverTheChipsSay` — renamed for the gate.
- `TrackRenderFlagsPathTest.theLegendSeesARouteOnlyWhenBothItsGateAndTheChipsAreOn` — the legend needs
  both the chip and the gate.

## Outcome

Shipped 2026-09-29 on `feature/rte-color`: `gradlew :app:assembleDebug :app:testDebugUnitTest`
**BUILD SUCCESSFUL**, `app-debug.apk` produced, the whole unit suite green. Nothing device-validated.
