# Context Hydration — UI_Map — 2026-10-09

**Last Bake:** 2026-10-09 16:16 UTC — written by `#bake`; absence means never baked

**Directive trace:** Since the last bake (2026-10-09 14:23 UTC) the session ran on the user's own words: a defect report on the toggle icons, an order to fix it generically, and a long colour-taxonomy discussion ending in a settled plan. No dependency was added, no machine-shaped data file was opened, no work was started without an order, the device was not touched, and every claim about the code follows a file read. Git: the fix and its first bake sit in `3049e359` on `feature/route-swap-direction`, with this bake amended onto it.

## State

**What shipped.** The toggle row's lock-mirror collision is fixed **generically**. The row composes GPS · tracking · land/water · inspect · route · lock while `TOP_TOGGLE_LOCK_SLOT` still counted five squares, so the locked overlay's duplicate lock button landed on the route square. The row's order is now one home — a `TopToggleControl` enum whose entries carry their own visibility, filtered by `TopToggleControl.row(appSettings)` — the row composes from that list and the mirror reads the same list's `indexOf(LOCK)`; the constant and `lockSlot` are deleted, the recenter square stays outside the counted list, and `TopToggleControlTest` pins the index at 5 shown and 4 hidden. Suite and `apk-build.bat` green, reviewed with no blocker.

**What is in design — settled.** [`261009_FEAT_PLN_UI_Map_toggle-colour-normalization.md`](../../xTrack/UI_Map/261009_FEAT_PLN_UI_Map_toggle-colour-normalization.md): the squares ride **one constant five-colour set** — pale off · amber still getting the data · blue nominal, by house taste rather than by claim · green on and standing by · red the thing it needs is gone. **The fill** says what the square is doing; **the dot** says what its data is worth — green real or complete, amber partial, red absent — and every mode square but the recenter wears one, which also tells a control from a reading on sight. The **weights are normalized**: the family's own white sits under the colour at the shared subdued tint, the dot wears its colour fully saturated, and the pulse's floor is **60 %**. GPS has **no idle face** (a held fix reads as healthy), tracking's colours are swapped (idle green, recording blue), land/water becomes water blue and land red, the four unread off-tokens are deleted and `status.gps.estimating` gains the key it never had. The zone tags are declared out of scope, and a saturated active edge was considered and **set aside**.

**What is owed.** The plan's one open number — `ui.map.surface.active.alpha` once the white base sits under the colour — plus the device pass over the five faces and the dot's colours; then the inspect live-acquire, marker-zoom and dp-pass device checks, unchanged.

**No open walk.** The feature file holds no `## Walk` section.

## Target Files

- `app/src/main/java/ykws/android/maro/ui/map/MapControls.kt` — `TopToggleControl`, the row's squares and the faces the normalization will move
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — the row composed from the list
- `app/src/main/java/ykws/android/maro/ui/map/MapLockLayer.kt` — the mirrored lock's index read
- `app/src/main/java/ykws/android/maro/ui/map/MapSurface.kt`, `MapPulseDot.kt` — the face, the two weights and the mark's floor
- `app/src/main/assets/colors.properties`, `app/src/main/java/ykws/android/maro/config/AppConfig.kt` — the palette's changes
- `app/src/test/java/ykws/android/maro/ui/map/TopToggleControlTest.kt` — the index pin

## Next Step

Implement the settled normalization — the faces, the two weights and the dot's colours — once the work is ordered, leaving the tint's value to the device pass.
