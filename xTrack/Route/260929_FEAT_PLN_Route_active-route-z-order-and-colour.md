# 260929 — The active route's z-order and its own colour

**Status:** built 2026-09-29 — `apk-build.bat` SUCCESSFUL and 192 tests green; owed are the confirming device pass, the diagnostics' removal and the frame-time measurement, and the epic's `## Implemented` now carries the pointer.

**Feature:** Route · **Branch:** `feature/routweak` · **Opened:** 2026-09-29 by `#focus route`

## 1. What was asked

- **The z-order.** *Active routes at the top of the track layer; no other track painted above them* — narrowed on the device to a **saved route seen over the route being followed**.
- **The colour.** An **Active route** row in Settings, in the Tracks section's colour block **between *Pinned tracks* and *Routes***, with its value also a `maro.properties` key.

## 2. What the device settled (2026-09-29)

- **The band's order was never wrong.** It re-appends every stored group before the route tier — `STORED:track_hist_… x17 · x20 · x20 · x16 · x12 > ROUTE:route_line_1 > ROUTE:route_line_2 > ROUTE:route_progress > ROUTE:route_target | base=174 markers=20 total=284` — so no saved route stands above the followed line **inside the band**.
- **The route's settled line was outside that band.** [`reorder`](../../app/src/main/java/ykws/android/maro/ui/map/OverlayZOrder.kt:109) treats the list's first entry as the map's tile and pins it there, and this app's list holds **no tile** — the first entry was the route's own pool slot 0, re-appended first and therefore painted beneath the depth raster, the coastline and the zone polygons.
- **The host's own lines prove it** (`adb shell getprop` first explained why they had never appeared: this phone carries `log.tag.MaroRoute = [INFO]`, silencing `Log.d` on that tag): `map objects attached — pool=3 lines … overlays=6` — six overlays for exactly six route objects, so the list was empty before the route attached — and `line repaint … pool=3 attached=3`, all three pool lines attached while the band printed two.
- **The pale line the eye called the route was not the route's own.** The settled line wore `colour=ff2ecc71, selectedAlpha=216, stroke=15.75px` on the buried object, while the band's route entries were the two empty pool slots at osmdroid's default paint.
- **The traces were never above the route.** Their ladder runs 35 % → 90 % at 7 px, speed-ramped, and all of them are re-appended before the route tier; they only read as the strongest lines because the route's real line was under the map.
- **The first reading is withdrawn**: the tiers are correct, and the line was outside them.

## 3. The fix — ordered

- [x] **The order is a rank over titles, not a permutation over overlays** — shipped as [`paintRankOf`](../../app/src/main/java/ykws/android/maro/ui/map/OverlayZOrder.kt:85): base 0 · stored 1 · route 2 · live 3 · marker 4, the tier half read through the enum's own ordinal so the classifier and the sorter cannot drift, with one rank walk that decides before any sort. `orderedIndices` was this plan's earlier shape and is **not** the shipped one; `reorder`'s own list surgery stays the device's, osmdroid overlays being unconstructible on the JVM.
- [ ] **`reorder` uses it and pins nothing**: the `val tile = overlays.first()` goes, and the permutation is applied to `all = overlays.toList()` with the same `removeAll` / `addAll` primitives the file already uses.
- [ ] **Add the identity fast path**: compare the desired list with the current one by `===` and return before any surgery, so a repaint that changes nothing costs one walk and no detach/re-attach of the whole band.
- [ ] **Correct the KDoc**: drop "The tile overlay at index 0 is preserved"; state that the basemap is osmdroid's own, that this list holds no tile, and that nothing is pinned by position.
- [ ] **Keep the two intra-tier moves** in [`MapTrackOverlayEffects`](../../app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt:412) — the recording to the list's end, then the highlighted set above it: the stable sort preserves the list's own order inside a tier, so those moves are how intra-tier order is set today, not leftovers of the old scheme.
- [ ] **Keep the band log in place for one confirming pass** — the route tier must print `route_line`.
- [ ] **Then remove the diagnostics**: the log, `bandOrderOf`, `faceOf` and the tag from `OverlayZOrder`, and [`RouteHost`](../../app/src/main/java/ykws/android/maro/ui/map/RouteHost.kt:194)'s three lines back to `Log.d`.
- [x] **Tests**: [`OverlayPaintRankTest`](../../app/src/test/java/ykws/android/maro/ui/map/OverlayPaintRankTest.kt:26) — the five ranks pinned by value, a route title above every stored prefix, the live recording above the route, a marker and the events flag last, an unclaimed title first, and an interleaved list whose groups keep their own order; its header states plainly that `reorder`'s surgery stays the device's. Seven cases, all green.
- [ ] **Gate**: `apk-build.bat`; `gradlew :app:testDebugUnitTest --tests "*Route*" --tests "*OverlayTrackTierTest*"` (185 tests, 0 failures at the last run); one device pass watching the whole map — the depth raster, the coastline and the marker band as well as the route.

**What the fix is not.** No tier moves — the 2026-09-28 order stands; no paint value changes — the colour, transparency and width were right; and nothing about `route_progress`'s lifetime, since the band log reads the list and never `isEnabled`, so whether it outlives its search is unproven.

## 4. The Active route colour — spec settled: the colour alone

- **The row's path**, to the letter of the request: Layers → **Tracks** → **Tracks Appearance** (`settings_track_settings_label`, the expander keyed `track_rendering`) → its `NestedCard` colour block, with one `ColorRow` placed **just above *Routes*** — between `settings_color_pinned_tracks` and `settings_color_routes` at [`MapScreenSettingsOverlay.kt:412`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:412). The label is `settings_color_active_route`, sentence case like its siblings: EN **Active route**, FR **Route active**.
- **One key, not two**: the fact already has its home — `route.line.color`, the lime `#FF2ECC71` the app draws today — so the row **edits that value** and its half of [`maro.properties:90`](../../app/src/main/assets/maro.properties:90) loses the *no Settings row is needed until one is asked for* sentence. A second key for the same fact would be the duplicate the one-home rule forbids.
- **The two faces keep the values they already carry**: the **primary** line — the selected candidate, slot 0 of the pool — keeps `route.line.transparencyPct` (15) and the **secondary** lines of a multiple acquisition keep `route.dimmed.transparencyPct` (55), both now painted in the setting's colour. **No opacity, width or other value moves**, and the earlier reading of *full opacity* is withdrawn by the user's own word.
- **Only the colour is added**: nothing else changes shape, no key retires, and every existing dial keeps its reader.
- **Untouched**: the provisional line keeps the dimmed dial and follows the colour; the toggle keeps `route.navigate.color`'s blue while navigating, its acquiring face following the line by R51; the destination pin keeps `route.pin.color`; the width and the candidate dial are unchanged.
- **Mechanics, as shipped**: `SettingsManager` carries `routeLineColor` under `route_line_color`, seeded from `AppConfig.routeLineColor`; [`RouteHost`](../../app/src/main/java/ykws/android/maro/ui/map/RouteHost.kt:217) takes it as a parameter and reads it in the pool and the provisional line, and **both paint effects key on it**, so a colour edited while a route stands reaches the drawn line — the Ask hop's blocking finding, which had the toggle updating while the line lagged. Nothing about opacity moved: `selectedAlpha` and `dimmedAlpha` keep their keys.

## 5. The record moves

- [`maro.properties:90`](../../app/src/main/assets/maro.properties:90) — the *no Settings row is needed until one is asked for* sentence, one now being asked for.
- The master book [`260922_FEAT_PLN_Route_ask-policy-and-target-validity.md`](260922_FEAT_PLN_Route_ask-policy-and-target-validity.md) — the new rule **nothing is pinned by position**, beside the three-tier order, and the colour row's home.
- The epic's `OverlayZOrder` key-file row ([`FEAT_DSC_Route.md:104`](FEAT_DSC_Route.md:104)): its tiers stay true; check it for a tile sentence and keep the line's place in the band.
- [`docs/color-scheme.md:142`](../../docs/color-scheme.md:142) — its sentence on the map's route toggle, only if the colour row changes faces.
- No Rev line is owed on [`260928_FEAT_PLN_Route_acquisition-face-and-map-order.md`](260928_FEAT_PLN_Route_acquisition-face-and-map-order.md): the fix leaves its §6 order standing.

## 6. Open points

- **O1 — closed by the user's word**: the existing opacities stay as they are, so only the colour joins the Settings surface and `route.line.transparencyPct` keeps its reader.
- **O2** — `RouteHost`'s three lines go back to `Log.d` or stay at `Log.i`, this phone silencing `D` on `MaroRoute`; the plan assumes they go back, and the phone's per-tag level belongs in the record either way.
- **O3** — the diagnostics leave with the confirming pass, or in the same change; the plan assumes the pass first.
- **O4** — a frame-time measurement after the fix, before any further repaint work, so the churn and the static layers are judged on numbers.
- **O5 — done**: the epic's `## Implemented` carries this plan's pointer as of 2026-09-29.

## Outcome

**Built 2026-09-29, unvalidated.** `apk-build.bat` SUCCESSFUL; the route-scoped suites plus `OverlayPaintRankTest` green over 192 tests with 0 failures.

- **Shipped**: `reorder` as a stable rank sort with nothing pinned by position and the identity walk deciding before any surgery; `paintRankOf` with its seven cases; the **Active route** row in Layers → Tracks → Tracks Appearance above *Routes*, its colour threaded to the pool, the provisional line and the toggle's acquiring face; the corrected sentences in `OverlayZOrder`, `AppConfig` and [`docs/color-scheme.md`](../../docs/color-scheme.md:142); **R81** in the master book, with the epic's key-file row and its `## Implemented` entry.
- **Owed**: one confirming device pass — the band log must print `route_line` in the route tier — then the diagnostics' removal from both files, and the frame-time measurement. §6's O2, O3 and O4 stand until then.
