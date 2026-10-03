# FEAT_PLN Route — the selected route's reinforcement

**Date:** 2026-10-03 · **Feature:** Route · **Status:** in design — implemented-ness is read from the
`## Implemented` pointer in [`FEAT_DSC_Route.md`](FEAT_DSC_Route.md), never from this line.

## 1. What changes

The chosen route is reinforced by **shape as well as opacity**, in the acquisition and in the following.

1. **A derived edge, in both phases** — an under-stroke drawn beneath the chosen line: the selected rung
   during the acquisition, the followed line during `Following`. Its colour is **the line's own colour
   pushed toward black by the app's one reinforcement lever**, so it follows Settings' **Active route**
   colour and needs no colour key of its own. Its width is the route's own key.
2. **The followed line splits at the boat** — the run **behind** the boat wears the shared dimming key
   (the weight the unchosen rungs already wear) and the run **ahead** stays at full strength under the
   edge, so the emphasis tracks progress.

Settled with the user on 2026-10-03: no direction chevrons; **no contrast floor** — the edge is always
the line's colour pushed the lever's distance, and a dark colour pick simply shows a faint edge.

**ELIJP** — the line you picked will look picked, because it gets a dark outline; while you drive it, the
part you already covered goes pale so your eye stays on what is left.

## 2. The one lever and its helper

- **Key:** `ui.reinforce.darkenPct=55` in [`ui.properties`](../../app/src/main/assets/ui.properties:107),
  beside `ui.dashboard.dullAlpha` ([line 102](../../app/src/main/assets/ui.properties:102)).
- **Reading:** the app-wide convention counts **the strength of the effect**, never the result (0 = opaque,
  100 = invisible), so 55 means pushed 55 % of the way toward black. Keep 45 % and darken 55 % are the same
  operation; the notation only decides which end the number counts from.
- **Home:** `ui.properties` follows `dullAlpha`'s own re-homing, and the review named the contradiction it
  exposes: [`AppConfig`'s load-order comment](../../app/src/main/java/ykws/android/maro/config/AppConfig.kt:1240)
  still claims colors.properties holds **ALL** colour values, while
  [`uiDashboardDullAlpha`'s KDoc](../../app/src/main/java/ykws/android/maro/config/AppConfig.kt:767) still
  points at colors.properties although the key sits in ui.properties. **Both sentences are repaired in this
  change** — the load-order comment gains the derivation-parameter exception and the KDoc names the real file.
- **Helper:** `reinforcedColor(color: Int, darkenPct: Int): Int` in a new
  `app/src/main/java/ykws/android/maro/ui/color/ColorReinforcement.kt`. Pure, RGB-only: the alpha is
  preserved, each channel is scaled by `(1 − darkenPct/100)` with rounding, and `darkenPct` is clamped to
  0..100. It is **kept pure with the caller reading the lever**, matching the convention that a shared
  drawing holds no settings read of its own (`DirectionLine`, `MarkerHalo`).
- **A black base is invariant** under the helper, which is why the two existing `#CC000000` under-strokes
  could later adopt it without changing a pixel — recorded, not done here.

## 3. The route's own values

- `route.line.casing.widthDp=8` in [`maro.properties`](../../app/src/main/assets/maro.properties:174),
  seeded 8 dp over the 6 dp core so the rim is 1 dp each side, with its `AppConfig` accessor beside
  `routeLineWidthDp`. Its accessor **reuses the core width's own `1f / 3f..24f` bounds** rather than a
  bespoke range — a deliberate deviation, recorded here rather than left silent.
- **The edge takes the line's own transparency**, so there is no colour key, no brightness key and no alpha
  key. `route.dimmed.transparencyPct` is reused for the travelled run — no second value.
- No user-facing text is added: no `strings.xml` change and no new Settings row.

## 4. The split

`remainingFrom` returns figures only, so the split needs the runs:

- Extend the plan's own projection — add `RoutePlan.splitAt(from): RouteSplit` beside `remainingFrom` in
  [`RouteViewModel.kt`](../../app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt:79), carrying the
  travelled run, the remaining run (both starting/ending at the projected point), the **best-leg index** the
  projection landed on (`-1` with no leg) and the two figures, and
  **refactor `remainingFrom` onto it** so the nearest-leg projection has **one home**. Its two callers
  ([`MapScreen.kt`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2878),
  [`RouteConfirmPanel.kt`](../../app/src/main/java/ykws/android/maro/ui/map/RouteConfirmPanel.kt:389)) keep
  their signature and are re-verified.
- **The two ends are stated, not implied:** the projection clamps to `0..1` on the **nearest leg**, so a
  boat off to the side or past the destination yields a wholly travelled or wholly remaining line; and the
  boat source is the **dashboard position** (`routeStart`), which in demo mode is the **map marker** — so in
  demo mode the fade follows the marker.
- **Arrival:** where the remaining run holds fewer than two points, the whole line stays at full strength
  under the edge — the mode's own arrival rule (the trip cell reads zero while the line stays drawn) holds
  without a new state.

## 5. The paint

In [`RouteHost.kt`](../../app/src/main/java/ykws/android/maro/ui/map/RouteHost.kt:69):

- Two overlays are attached once, beside the pool: `ROUTE_CASING_TITLE = "route_casing"` and
  `ROUTE_TRAVELLED_TITLE = "route_travelled"`. Both keep the `route_` prefix, so `trackTierOf` places them
  in the route tier; neither matches the pool's `startsWith(ROUTE_LINE_TITLE)` filter, so the pool keeps its
  three rungs. **Verified:** `reorder` sorts by rank with a stable sort and returns early when the ranks
  never decrease, so attaching the casing (and the travelled run) **before** the pool keeps them under the
  rungs — under the core, above every stored track.
- Order inside the tier, bottom to top: `route_casing` → `route_travelled` → the pool → `route_progress`
  → the pin. **While `Following`, slot 0 draws the split's remaining run alone** — never the whole line
  beneath the travelled overlay — the casing mirrors **exactly what slot 0 draws**, and `route_travelled`
  draws the travelled run **only when slot 0 actually carries the remaining run**. Where the remaining run
  falls under two points (the arrival case) slot 0 keeps the **whole** line at full strength, the travelled
  overlay stays off, and the casing mirrors that whole line.
- The discard window clears the casing and the travelled run with the pool and the pin, and `onDispose`
  removes both.
- **The paint cost is answered, not inherited:** the effect's inputs now include the boat's fix, so it must
  not repaint a stationary boat. Derive the split under `remember` keyed on a **split identity** — the fix
  rounded to a fixed precision — so a jittering boat does not re-derive it, and repaint on the split's own
  identity: the **best-leg index, exposed on `RouteSplit`**, the projected point rounded to the same step,
  and **the arrival discriminator** — whether the remaining run still stands. The flag is required, not
  decorative: at the flip to arrival the projected point has already clamped to the destination, so the leg
  and the rounded point are unchanged and the flag is the only input that moves. Inside a bucket nothing
  but the flag can change, so an ordinary fix never repaints. State the step in a comment.
- The boat's position reaches the host as **one new `boatPosition` parameter** taken from the shell's
  `routeStart` (the value the trip cell already reads), so one source feeds both.

## 6. The guideline

- In [`ui-component-guidelines.md`](../../docs/ui-component-guidelines.md:324), beside the app-wide
  transparency convention: **when a colour needs reinforcing (a selected line's edge, an under-stroke),
  derive it from the base with `reinforcedColor` and the one lever `ui.reinforce.darkenPct`; never add a
  per-feature colour key for a reinforcement.**
- **The rule is scoped to a derived variant of a colour the user can pick** — a designed token pair stays a
  token pair, and the palette's deliberately hand-picked near-duplicates (e.g. `ui.dashboard.zone.caution`
  against `semantic.caution`) are not read against by it.
- A pointer from [`color-scheme.md`](../../docs/color-scheme.md:101) keeps the value's home single.

## 7. Build order

1. `#new route-render` — the branch cut the session's first message invoked; if `feature/route-render`
   already exists, ask as the command requires. Never `develop`, never `main`.
2. The lever, the `AppConfig` accessor and boundaries, and the two repaired sentences (§2).
3. `reinforcedColor` and its JVM test (§2).
4. `route.line.casing.widthDp` and its accessor (§3).
5. `RoutePlan.splitAt`, the `remainingFrom` delegation and the split's tests (§4).
6. `RouteHost`'s two overlays, the split at paint and the `boatPosition` parameter, with the shell's single
   `RouteHost(...)` call updated (§5).
7. `apk-build.bat`, then the suites (§8).
8. The guideline rule, the `color-scheme.md` pointer, and `FEAT_DSC_Route.md` — the drawing bullet (which
   currently says the selected line is drawn at the route's own transparency with the others dimmed), **R93**,
   the Key Files rows (`ColorReinforcement.kt`, the new keys), and this plan's pointer in `## Implemented`.
9. The pipeline performs **no git write**: no stage, no commit, no push.

## 8. Verification

- `ColorReinforcementTest` — 0 % leaves the colour untouched; 100 % is black; the alpha survives; the
  percentage is clamped.
- `RoutePlanTest` — `splitAt`'s runs and figures agree with `remainingFrom` on the same inputs; the
  off-line and past-the-destination clamps; a plan under two points.
- The route suites stay green: `RouteAcquisitionTest`, `RouteEngineSeamTest`, `RoutePlanTest`,
  `RouteStoredMatchTest`, `RouteMirrorPlanTest`.
- `apk-build.bat` green.
- **Owed to the user, not the agent:** the device pass over the edge's contrast, the paint order inside the
  route tier, the split's join and its behaviour at a leg boundary, and the demo-mode marker.

## 9. Out of scope

- No chevrons; no contrast floor.
- The two `#CC000000` under-strokes adopting the helper, and the alpha-only `dimColor` duplicated in
  [`MarkerAppearance.kt`](../../app/src/main/java/ykws/android/maro/ui/map/MarkerAppearance.kt:121) and
  [`MarkerOverlay.kt`](../../app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt:963) — recorded as
  findings, untouched here.
