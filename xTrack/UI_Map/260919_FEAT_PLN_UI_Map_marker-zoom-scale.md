<!-- scope: feature -->
# Marker zoom scaling — exponent becomes a property, curve flattened to 0.35

## 1. Decision

The growth exponent of the centre sprite and the cap arrow becomes a configuration value,
`map.marker.size.zoomExponent` in `maro.properties`, read through `AppConfig`, and its value moves from
0.45 to **0.35**.

## 2. Why

The zoom range settled on device at 11–20 (`MAP_MIN_ZOOM` / `MAP_MAX_ZOOM` in `CoastlineMapView.kt`), and
at 20 the centre sprite is too large — most visibly offshore, where the coast-shrink ramp no longer applies
(it runs 0.3 → 1.0 over 2 000 m, so beyond that the sprite is at full size). Flattening the exponent is the
change that leaves the sprite's shape at mid zoom alone while cutting the top of the range.

## 3. Numbers — boat sprite in dp, before 0.45 → after 0.35

| Zoom | At the coast ×0.30 | Mid 1 km ×0.65 | Open sea ×1.00 |
|------|--------------------|----------------|----------------|
| 11 | 7 → 8 | 15 → 16 | 23 → 25 |
| 12 | 10 → 10 | 21 → 21 | 32 → 32 |
| 13 | 13 → 12 | 28 → 27 | 44 → 41 |
| 14 | 18 → 16 | 39 → 34 | 60 → 52 |
| 15 | 24 → 20 | 53 → 43 | 82 → 66 |
| 16 | 33 → 25 | 72 → 55 | 111 → 84 |
| 17 | 46 → 32 | 99 → 70 | 152 → 108 |
| 18 | 62 → 41 | 135 → 89 | 208 → 137 |
| 19 | 85 → 53 | 185 → 114 | 284 → 175 |
| 20 | 116 → 67 | 252 → 145 | 388 → 223 |

The land dot is a quarter of every cell. Zoom 12 is the reference level, so it is unchanged by
construction; the cut grows with depth — 19 % at 15, 29 % at 17, 43 % at 20 at sea.

## 4. Rejected alternatives

- **A ceiling on the final size** — it stops the sprite tracking the ground above the knee, which is a depth
  cue lost rather than a curve flattened.
- **A knee at z16 with a second, gentler exponent above** — same endpoint with an extra constant to tune by
  eye, for a range the device visits least.
- **Leaving the exponent a code constant** — a value judged by eye on device belongs with the other
  tunables, and `maro.properties` is the source of truth for every value.

## 5. Property contract

| Item | Value |
|------|-------|
| Key | `map.marker.size.zoomExponent` |
| Default | 0.35 |
| Range | 0.0–1.0, clamped on parse; `0.0` = size fixed at any zoom, `1.0` = grows exactly like the ground |
| Growth per level | `2^exponent − 1`: +27.5 % at 0.35, +36.6 % at the old 0.45 |
| Readers | the centre sprite (boat / land dot), the crosshair branch, and the cap arrow — one factor, three sites in `MapOverlays.kt` |
| Left in code | `REF_ZOOM = 12.0`, the coast-shrink pair and the arrow's speed clamps — the property comment names them, so a tuner can read the whole formula. The base pair this row first recorded as code moved to properties in §8 |

## 6. Edits

- `app/src/main/assets/maro.properties` — a new documented block for the key, beside the `map.marker.tap.*`
  group: the formula, the reference zoom, the growth per level, the range and the reason for 0.35.
- `config/AppConfig.kt` — accessor `mapMarkerSizeZoomExponent: Float = 0.35f`, parsed in the same
  `props.getProperty` block as the tap values and clamped to 0–1.
- `ui/map/MapOverlays.kt` — delete `ZOOM_EXPONENT` and its KDoc; read the accessor at the crosshair branch,
  the sprite's base size and the cap arrow. Repair the claims the move makes false: the reference-zoom
  comment still saying `11.0 -to 18.0`, the "~8× over the full 8–18 range" line, the `(8.0–18.0)` KDoc, and
  the `[ZOOM_EXPONENT]` reference.
- `ui/map/NavigationViewModel.kt` — the `zoomLevel` KDoc still says 8.0–18.0.
- `xTrack/UI_Map/FEAT_DOC_UI_Map_marker-sizing.md` — its tuning table is wrong on every row (reference 11.0,
  boat 48, dot 16, exponent 0.5, shrink 0.5) and points at `MapScreen.kt:526`; correct the illustrative
  tables to the real curve and replace the duplicated constants table with a pointer to the property key and
  the code's own home for the rest.

## 7. Verification

- `apk-build.bat` clean, with no new warnings.
- Scoped `ui.map` + `config` unit run green apart from the six properties-versus-default reds — the five the
  hydrations name plus the tap flash's alpha — none of which reads the new key.
- Device pass owed: the sprite at 19 and 20 offshore and inshore against the table above.

## 8. Follow-on — the reference sizes move to properties and rise 15 %

### 8.1 Decision

The two base sizes leave the code as `map.marker.size.boatBaseDp` and `map.marker.size.dotBaseDp`, and both
rise 15 % as a first device test: 32 → **36.8** dp and 8 → **9.2** dp. The boat : dot ratio stays 4 : 1, and
`REF_ZOOM = 12.0` stays in the code.

### 8.2 Numbers at sea, ×1.00 — before → after

| Zoom | Boat | Dot |
|------|------|-----|
| 11 | 25 → 29 | 6 → 7 |
| 12 | 32 → 37 | 8 → 9 |
| 15 | 66 → 76 | 17 → 19 |
| 18 | 137 → 158 | 34 → 39 |
| 20 | 223 → 256 | 56 → 64 |

Inshore the same 15 % applies on the 0.3 multiplier, so the ratio of the two regimes is untouched.

### 8.3 Contract

| Key | Default | Range | Reader |
|-----|---------|-------|--------|
| `map.marker.size.boatBaseDp` | 36.8 | 1–128, clamped | the boat sprite's size at `REF_ZOOM` |
| `map.marker.size.dotBaseDp` | 9.2 | 1–128, clamped | the land dot's size at `REF_ZOOM` |

The clamp is there because a zero or negative dp would break the layout rather than merely look wrong.

### 8.4 Not in this pass

- The wizard's crosshair keeps its own literal `32.0` in `MapOverlays.kt`: it stands in for the sprite but is
  not one of the two reference sizes, and whether it should follow the boat is a visible choice — named here,
  untouched.
- The review's F3 — no shipped-token test for the sizing keys — would guard all three once written.

### 8.5 Edits

- `maro.properties` — the two keys documented inside the existing sizing block.
- `AppConfig.kt` — two accessors with their parse and clamp.
- `MapOverlays.kt` — `BOAT_BASE_DP` / `DOT_BASE_DP` deleted, their readers moved to the accessors, and the KDoc
  and comment references to the pair repaired.
- `xTrack/UI_Map/FEAT_DOC_UI_Map_marker-sizing.md` — the tunables table gains the two keys and the illustrative
  sizes rise with them.

## Outcome

Shipped 2026-09-19 through the `#implement` pipeline, in two passes.

The first moved the growth exponent out of the code into `map.marker.size.zoomExponent` (0.35) behind
`AppConfig.mapMarkerSizeZoomExponent`, clamped 0–1, and deleted `ZOOM_EXPONENT`: its three readers — the sprite,
the wizard's crosshair and the cap arrow — read the one setting, so a single factor still governs all three. The
second took the base pair with it as `map.marker.size.boatBaseDp` (36.8) and `map.marker.size.dotBaseDp` (9.2),
clamped 1–128, a 15 % raise on 32 / 8 as a first device test, deleting `BOAT_BASE_DP` / `DOT_BASE_DP` and
reading the sprite's base from the pair. What stays code is the reference zoom, the coast-shrink pair and the
arrow's speed clamps, each named where it is used and in the property comment.
`FEAT_DOC_UI_Map_marker-sizing.md` was rewritten to the real curve, its sizes raised with the pair, and its
tunables table now points at the keys instead of restating them.

`apk-build.bat` SUCCESSFUL with no warnings on both passes, and the scoped `ui.map` + `config` run came to 213
tests with the six properties-versus-default reds, none reading the new keys. The review's one must-fix — the
exponent's KDoc still calling the base pair a code constant after the second pass — was closed in a one-sentence
follow-up hop, rebuilt clean.

Deviations: none. Open: the device pass on the sprite at 19–20 and on the 15 % raise; the review's F3, none of
the three keys having a shipped-token-versus-default test where every sibling key has one, and F1, the scale
formula written out at three sites in `MapOverlays.kt`; F2, the crosshair's own `32.0` now 13 % under the boat's
base, a visible choice left to the user; F7, the 4 : 1 boat-to-dot ratio being a comment rather than a guard; and
F5, `docs/maro-code.md`'s routing row not naming the sizing keys.
