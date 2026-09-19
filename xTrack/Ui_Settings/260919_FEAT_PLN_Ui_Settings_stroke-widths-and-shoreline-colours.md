# FEAT_PLN — Ui_Settings — stroke widths + shoreline colours

**Status:** implemented 2026-09-19 on `feature/extra-settings` through the `#implement` pipeline — Code, one Ask review, two repair hops; `apk-build.bat` SUCCESS and the scoped run at 216 tests holding only the six pre-existing properties-versus-`AppConfig` drift reds. The device pass is owed. Captures the 2026-09-19 discussion.
**Feature:** Ui_Settings (settings UI + persistence). Rendering touches the Coastline and RegulatedZones features' surfaces.
**Related docs:** [`docs/ui-component-guidelines.md`](../../docs/ui-component-guidelines.md) (row families, Card/Expander/NestedCard), [`docs/color-scheme.md`](../../docs/color-scheme.md) §7.

## 1. Request

- **Widths:** a user setting for the stroke width of the 300 m band's boundary line, the regulated zones' outlines and the shoreline — today three different values, two baked literals and the third a properties knob nothing exposes.
- **Colours:** a user setting for the shoreline's two colours, island and mainland, kept separate.
- **Stroke strength:** the shoreline's baked half-strength stroke becomes a property and a setting in its own right, spoken as transparency rather than as alpha.
- **Widened in discussion:** the two shoreline colours are the first of **eleven** colour keys leaving `colors.properties` for `maro.properties`, on the rule that a colour parameterising a rendering behaviour is functional data rather than a palette token. Section 7 carries the list and the line that draws it.

## 2. What the code actually carries today

| Surface | Stroke width now | Colour now | Already a user setting? |
|---|---|---|---|
| 300 m band boundary | `strokeWidth = 6f` baked at [`MapOverlayRenderer.kt:187`](../../app/src/main/java/ykws/android/maro/ui/map/MapOverlayRenderer.kt:187) | `zone300Color` setting, default `0xFFE53935` ([`SettingsManager.kt:115`](../../app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt:115)) | colour **yes**, transparency **yes**, width **no** |
| Regulated zone outline | `strokeWidth = 3f` baked at [`MapOverlayRenderer.kt:230`](../../app/src/main/java/ykws/android/maro/ui/map/MapOverlayRenderer.kt:230) | per type, palette keys `regulatedZone.type.*` ([`colors.properties:218`](../../app/src/main/assets/colors.properties:218)) | transparency **yes**, width **no** |
| Shoreline (coastline) | `AppConfig.mapCoastlineMainlandWidth` = 10 px ([`MapOverlayRenderer.kt:130`](../../app/src/main/java/ykws/android/maro/ui/map/MapOverlayRenderer.kt:130)) | palette keys `map.coastline.mainland.color` / `island.color` ([`colors.properties:120`](../../app/src/main/assets/colors.properties:120)) | **no** — neither |

Three findings that shape the work:

- The request's premise holds cleanly for the two zone strokes (6 px vs 3 px, both baked) and only partly for the shoreline, whose width comes from a properties knob rather than a literal — the knob is readable but not user-facing.
- `map.coastline.island.width` is a **dead knob**: [`AppConfig.kt:429`](../../app/src/main/java/ykws/android/maro/config/AppConfig.kt:429) parses it into `mapCoastlineIslandWidth`, and no draw site ever reads it — [`MapOverlayRenderer.kt:130`](../../app/src/main/java/ykws/android/maro/ui/map/MapOverlayRenderer.kt:130) uses the mainland width for both branches while the colour two lines above picks per branch.
- The shoreline stroke is painted at **half strength** — `alpha = 128` is baked at [`MapOverlayRenderer.kt:131`](../../app/src/main/java/ykws/android/maro/ui/map/MapOverlayRenderer.kt:131) — so a colour chosen in a picker renders dimmer than its swatch.

### 2.1 Every width the map draws today (px)

| Layer | Width | Where the number lives |
|---|---|---|
| Coastline (both branches) | 10 | `map.coastline.mainland.width` |
| Selected track's dark casing | 16 | `track.width.selected.casing` |
| Live recording line | 12 | `track.width.live` |
| Newest history track | 11 | `track.width.newest` |
| Selected stored track | 10 | `track.width.selected` |
| Pinned track | 9 | `track.width.pinned` |
| Other history track | 8 | `track.width.history` |
| 300 m band boundary | 6 | baked `6f` |
| Hazard disc, its outer ring and cross | 6, 5, 5 | baked `6f`, `5f`, `5f` |
| Marker circle and corridor band | 4 × multiplier → 4, 6.7, 10 | `MarkerAppearance.strokeMultiplier` |
| Marker halo border | 4 | `MarkerHalo.BORDER_STROKE_PX` |
| Regulated zone outline | 3 | baked `3f` |
| Isobaths, major | 3 + source bonus → 2 or 4 | baked `3f` + `map.isobar.*.width` |
| Marker proximity and corridor centre lines | 2 | baked `2f` |
| Isobaths, minor | 2 + bonus → 1 or 3, floored at 1 | baked `2f` + `map.isobar.*.width` |

- **The map's strokes run from 1 to 16 px**, so a 1–20 span brackets every one of them and leaves the top four pixels as headroom the app does not currently use.
- The navigation arrow and line sit outside both the table and the span: they are Compose strokes measured in dp (2.25 and 1), not osmdroid px.

## 3. The parameter chain a knob must thread

The renderer is stateless and the map view is selectively rebuilt, so a setting reaches the paint only if it is carried the whole way:

- [`MapScreen.kt`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt) call site → `CoastlineMapView` parameters ([`CoastlineMapView.kt:177`](../../app/src/main/java/ykws/android/maro/ui/map/CoastlineMapView.kt:177)).
- Factory draws plus the `tracker.last*` seeds ([`CoastlineMapView.kt:226`](../../app/src/main/java/ykws/android/maro/ui/map/CoastlineMapView.kt:226) to `:250`).
- Per-layer `LaunchedEffect` key list **and** its early-return guard — the 300 m layer at [`CoastlineMapView.kt:284`](../../app/src/main/java/ykws/android/maro/ui/map/CoastlineMapView.kt:284), the regulated layer at `:317`, the coastline at `:376`.
- The `last*` fields the guards compare against ([`OverlayTracker.kt:39`](../../app/src/main/java/ykws/android/maro/ui/map/OverlayTracker.kt:39) to `:48`).

**The trap:** a parameter added to the draw function but missing from either the seeds or the effect keys compiles, draws correctly once, and then silently ignores every later change to the setting. The coastline is the sharpest case — `drawCoastline` takes no appearance parameter at all and its effect keys only on `segments`.

## 4. Proposed design

### 4.1 The six values, each a property and each a setting

Every value is **both** — the property is the home of the value and the setting is the user's override — with `AppSettings` seeding each default from `AppConfig` (the shape `boatMarkerIdleThresholdSec` already uses) and the load and write lines following the transparency pair ([`SettingsManager.kt:117`](../../app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt:117), `:450`, `:600`, `:731`).

| Property key (all in `maro.properties`) | `AppSettings` field | Prefs key | Default |
|---|---|---|---|
| `map.coastline.widthPx` | `coastlineWidthPx` | `coastline_width_px` | 10 |
| `map.coastline.transparencyPct` | `coastlineTransparencyPct` | `coastline_transparency_pct` | 50 |
| `map.coastline.mainland.color` | `coastlineMainlandColor` | `coastline_mainland_color` | `#1545C0` |
| `map.coastline.island.color` | `coastlineIslandColor` | `coastline_island_color` | `#08805C` |
| `map.zone300.boundary.widthPx` | `zone300BoundaryWidthPx` | `zone300_boundary_width_px` | 6 |
| `map.regulatedZone.outline.widthPx` | `regulatedZoneOutlineWidthPx` | `regulated_zone_outline_width_px` | 3 |

- **Both colours move to `maro.properties`** (settled 2026-09-19), because a colour that is a parameter of a rendering behaviour is functional data rather than a palette token — the precedent is `map.marker.tap.flash.color` and `.alpha`, re-homed for exactly that reason and recorded at [`color-scheme.md:192`](../../docs/color-scheme.md:192). Their key names are unchanged; their entries leave `colors.properties`, so no key has two homes.
- **The shoreline's strength is a setting too** — and it speaks **transparency**, not alpha, because that is the current practice on this surface: 0 = opaque, 100 = invisible, the vocabulary of every neighbouring row and of both zone cards.
- One unit therefore runs end to end: the property is `map.coastline.transparencyPct`, the setting stores the same percentage, the row's readout is a percentage, and the renderer derives the paint's alpha by calling the existing [`transparencyPctToAlpha()`](../../app/src/main/java/ykws/android/maro/ui/map/MapOverlayRenderer.kt:35) — the same helper both zone renderers already use.
- The default 50 yields an alpha of 127 against the baked 128, because the helper divides integers — one step out of 255, invisible, and the shipped stroke stays where it is. The rejected alternative was an alpha fraction matching the renderer's own vocabulary (`map.marker.tap.flash.alpha` is the precedent), which would have broken the one-unit rule its two neighbours set.

- Every value is clamped on read — the three widths, and the transparency through the same `coerceIn(0, 100)` the helper applies when it derives an alpha ([`MapOverlayRenderer.kt:35`](../../app/src/main/java/ykws/android/maro/ui/map/MapOverlayRenderer.kt:35)); a slider's range is a UI fact, not a guarantee about stored values.
- Each colour default is **seeded from `AppConfig`**, not from a literal, so the property stays the single home of the value. The `zone300Color` precedent did the opposite — a literal in `AppSettings` while its palette key became readerless, logged in the 2026-09-18 hydration — and copying it would repeat that drift.
- Objection to the seed: the default is read once and then persisted, so a later property change reaches fresh installs only. Every persisted setting already behaves that way; the choice is which home survives, not whether the divergence exists.

### 4.2 UI placement (Layers tab, [`MapScreenSettingsOverlay.kt`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt))

- **300 m band** — inside the existing Appearance expander (`zone300_appearance`, `:673`–`:719`), one `SectionDivider()` and a width `SliderRow` added between the transparency row and the colour sub-section.
- **Regulated zones** — the same addition inside `reg_appearance` (`:586`–`:624`), so the two zone cards read identically.
- **Coastline** — today the section is one `ToggleRow` and nothing else (`:725`–`:735`). It gains a collapsible Appearance expander like its two neighbours (settled 2026-09-19), holding a width `SliderRow`, a Transparency `SliderRow`, then a colours `SubSectionHeader` and one `ColorPairRow` (mainland left / island right) — the pair component already exists ([`MapScreenSettingsOverlay.kt:1771`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:1771)) and is the shape the past/pinned track colours use at `:332`.
- Order: the two zone cards keep transparency then colour and gain their width row in the same place; the coastline card reads width, transparency, colour.

### 4.3 Renderer and plumbing

- `drawZone300` ([`:148`](../../app/src/main/java/ykws/android/maro/ui/map/MapOverlayRenderer.kt:148)) and `drawRegulatedZones` ([`:206`](../../app/src/main/java/ykws/android/maro/ui/map/MapOverlayRenderer.kt:206)) take one extra `boundaryWidthPx: Float` parameter, replacing the baked `6f` and `3f`.
- `drawCoastline` ([`:65`](../../app/src/main/java/ykws/android/maro/ui/map/MapOverlayRenderer.kt:65)) takes `mainlandColor`, `islandColor`, `widthPx` and `transparencyPct`, replacing its three `AppConfig` reads and the baked `alpha = 128`, and deriving the alpha the way its two sibling renderers already do.
- `CoastlineMapView` gains **six** parameters, seeds six `tracker.last*` fields and gains six effect keys, mirrored into the guards — the coastline layer's guard, which keys on `segments` alone today, needs all four of its new keys ([`CoastlineMapView.kt:376`](../../app/src/main/java/ykws/android/maro/ui/map/CoastlineMapView.kt:376)).
- `MapScreen.kt` passes the six `appSettings` values at the call site.

### 4.4 Strings

New keys in both [`strings.xml`](../../app/src/main/res/values/strings.xml) and [`strings.xml`](../../app/src/main/res/values-fr/strings.xml): the width row label (one shared label, since all three read the same thing), the coastline appearance expander label, the transparency row's label and description, the shoreline colours sub-heading, the mainland and island row labels, and the coastline width description. The existing `settings_coastline_label` / `_desc` stay as they are; the width row's value label reuses a plain integer format and the transparency row's follows the percentage format its neighbours use.

### 4.5 Decisions taken (agent's call, stated)

- **The unit is px and the key says so** (`widthPx`), following the file's newer unit-suffixed keys — `boatBaseDp`, `zoneDiameterDp`, `dwell.ms` — rather than the bare `.width` of the older rows; dp would silently change what every existing width key means and would need a density multiplier in three renderers.
- **Integer steps, commit on release** — the same discipline as the transparency rows; the span itself is decided in section 7, against the survey in 2.1.
- **The baked `alpha = 128` becomes `map.coastline.transparencyPct`, property and setting**, defaulting to 50 % so the stroke stays within one step in 255 of its present strength.
- **One shoreline width, applied to mainland and island alike** (settled 2026-09-19), so the width pair collapses into `map.coastline.widthPx` and both `mapCoastlineMainlandWidth` and `mapCoastlineIslandWidth` go with it — the island one having had no reader at all.
- **What px means here, and why the arrow differs from it** — a device pixel is unscaled, so one number marks a different physical weight on each screen, while the navigation arrow and its line are dp Compose strokes ([`MapOverlays.kt:341`](../../app/src/main/java/ykws/android/maro/ui/map/MapOverlays.kt:341)). The numbers, the arrow's case and the argument both ways live in section 7, which decides the question rather than re-opening it here.

## 5. Work breakdown

1. Add the six fields to `AppSettings`, their prefs keys and their load and write paths in `SettingsManager`; clamp the three widths and the transparency on read.
2. Move the eleven colour keys from `colors.properties` to `maro.properties`, names unchanged, deleting each entry from the old file so no key has two homes.
3. Seed `zone300Color` from the moved `map.zone300.boundary` rather than its literal, closing the drift logged in the 2026-09-18 hydration.
4. Add the width and colour parameters to `drawZone300` / `drawRegulatedZones` / `drawCoastline` plus the transparency percentage to the latter, deleting its `AppConfig` reads and its baked `alpha = 128`.
5. Thread the six values through `CoastlineMapView` — signature, factory draw calls, `tracker.last*` seeds, three effect key lists and three early-return guards.
6. Pass the six values from `MapScreen.kt`.
7. Add the width row to both zone appearance cards, in the same position in each.
8. Give the coastline section its Appearance expander with the width row, the Transparency row and the two colour rows.
9. Write the EN and FR strings.
10. Replace the coastline width pair with the single `map.coastline.widthPx`, deleting `map.coastline.mainland.width`, `map.coastline.island.width`, `mapCoastlineMainlandWidth` and `mapCoastlineIslandWidth` with their parse lines — and correcting the KDoc at [`AppConfig.kt:422`](../../app/src/main/java/ykws/android/maro/config/AppConfig.kt:422) and `:428`, which names `colors.properties` as the home of keys that live in `maro.properties`.
11. Trim `color-scheme.md` in the same pass: the swept families keep their key names and a pointer to `maro.properties`, and lose their restated values.
12. Add unit coverage for the six settings' clamps and round-trip; reconcile the properties test with the key list that remains.

## 6. Verification

- `apk-build.bat` SUCCESS with no new warnings.
- The scoped `ui.map` + `config` run stays at its 189 tests with only the five known `maro.properties`-versus-`AppConfig` reds — a sixth red means a default drifted from its palette value.
- New pure coverage for the clamps and the round-trip, in the style of `MapOverlayRendererTest`'s transparency derivation test.
- Device pass: each of the three widths at both extremes on its own layer, the shoreline transparency at both ends, and the two shoreline colours seen on an island and on the mainland.

## 7. Decisions, each with the alternative it beat

- **Decided 2026-09-19 — one shoreline width for both mainland and island.** The key pair becomes the single `map.coastline.widthPx` and both `AppConfig` fields go, so `drawCoastline` keeps one width value. The rejected alternative was two knobs, which would have meant fixing [`MapOverlayRenderer.kt:130`](../../app/src/main/java/ykws/android/maro/ui/map/MapOverlayRenderer.kt:130) to read per branch and would have grown the change from three controls to four.
- **Decided 2026-09-19 — eleven keys leave `colors.properties` for `maro.properties`**, names unchanged: the coastline pair, `map.zone300.fill` and `.boundary`, `map.hazard.disc.fill` and `.outline`, `map.zoneAhead.line`, `.cone.fill` and `.cone.outline`, and `map.navigation.arrow.color` with `map.navigation.line.color`. The theme doc keeps the key names and a pointer, and its value tables for these families go in the same pass.
- **Decided — the isobath colours and the `regulatedZone.type.*` family stay in `colors.properties`.** Both are keyed by the data's own taxonomy, one key per depth source and one per zone category, so the key set follows the model rather than describing one overlay's look — that is the line the two lists draw, not alias versus literal.
- **Cost inside the sweep:** `map.zone300.boundary` is the seed of the existing `zone300Color` setting, so the move lets that literal in `AppSettings` become a read of the property, which closes the drift logged in the 2026-09-18 hydration; and `map.zone300.fill`'s packed `#30` alpha is vestigial either way, since the transparency setting supplies the fill alpha.
- **Alias keys move safely — verified 2026-09-19.** All three files load into one `Properties` object, each overriding the last ([`AppConfig.kt:812`](../../app/src/main/java/ykws/android/maro/config/AppConfig.kt:812)), and the `${key}` interpolation runs as a post-load pass over that merged object ([`AppConfig.kt:850`](../../app/src/main/java/ykws/android/maro/config/AppConfig.kt:850)) — so an alias resolves wherever its key is written, and `map.zoneAhead.line=${semantic.compliant}` travels unharmed.
- **Correction to an earlier claim in this file:** the isobath aliases were never mechanically barred from moving either; they stay because they were ruled out, not because the loader forbids it.
- **Zone-ahead and navigation were both decided in the same day, bringing the sweep to eleven keys.** Each of the five families is one overlay's own look — a stroke colour, a fill, a boundary — with no taxonomy anchoring its key set, which is exactly what the two families left behind have.
- **Nothing is left standing on that line:** what stays in `colors.properties` does so by decision — the palette roles and their aliases, the two taxonomy families (`map.isobar.*.color`, `regulatedZone.type.*`), the depth ramp's channel numbers, and `overlay.lowDepth.color`.
- **Note for the move — `map.navigation.line.color` carries its own alpha.** `#4D1565C0` is a 30 % alpha inside the value, and both navigation keys are read straight into Compose colours ([`MapOverlays.kt:330`](../../app/src/main/java/ykws/android/maro/ui/map/MapOverlays.kt:330), `:373`), so that alpha is part of the colour rather than something a paint overrides — it must not be "cleaned" to a bare hex while it moves.
- **Decided 2026-09-19 — the coastline section takes a collapsible Appearance expander**, matching the 300 m band's and the Regulated zones' cards rather than sitting bare beside its toggle.
- **Decided 2026-09-19 — the strength row speaks the current practice, transparency**, so the property, the stored setting and the readout are one 0–100 percentage and the alpha is derived by the shared helper ([`MapOverlayRenderer.kt:35`](../../app/src/main/java/ykws/android/maro/ui/map/MapOverlayRenderer.kt:35)).
- **Decided and confirmed by the user 2026-09-19 — the span stays 1–20 px.** It is chosen against the survey in 2.1: everything the map draws lies between 1 and 16 px, so the span brackets it all and the top four pixels are headroom by intent rather than reach.
- **Decided by the user 2026-09-19 — the three keys ship in px, and the dp conversion is its own change.** px matches every key in the map-overlay table and needs no conversion; the whole-paint-code move to dp, with look-preserving numbers and a check at another density, is scheduled separately and carried as a global todo, rather than folded in here — where a stroke that moved could not be attributed to one change or the other.
- **The case for dp, and its one real cost.** dp is density-independent, so one value means the same physical weight on every screen — which is what the UI chrome already does — and the cost is one multiplication per paint site, because osmdroid counts device pixels.
- **A partial conversion is the trap, and it is concrete.** `MarkerHalo` composes its bitmap as `haloRadius * 2 + BORDER_STROKE_PX * 2 + 4` and the selected marker's under-stroke as `4f * multiplier + HIGHLIGHT_UNDER_STROKE_ADD(6f)`, so a ring in dp beside a padding or an add in px stops wrapping its core; the isobath widths are bonuses added to a baked base (`3f + bonus`, `2f + bonus`), so converting one side of that arithmetic changes what major and minor mean and leaves the `−4..+6` clamp bracketing nothing; and the dash patterns (`20f, 10f`, `12f, 6f`, `8f, 6f`) are lengths too, so a scaled stroke beside unscaled dashes reads wrong.
- **So the pass is all-or-nothing over the paint code** — every width, offset, padding and dash length — with the converted defaults reproducing today's px numbers at 3× so the shipped look stays put on this phone, and a visual check at a different density, since no other check can show the conversion is right.
- **Why the split, rather than folding it in.** This change's device pass is three sliders and two colours, and a whole-code unit conversion inside it would leave no way to tell which of the two moved a stroke. The objection weighed against deferring: the three new keys are written in px and will be rewritten by that pass, and deferring has slipped in this file before — the isobath and hazard baked numbers are already parked — which is answered by carrying the task as a global todo instead of in memory.

## 8. Explicitly out of scope

- Per-type regulated-zone colours, which stay palette keys; the 300 m band's colour gains no new row either — it already has one, and only its key's home changes in the sweep.
- Width knobs for isobaths, hazards, markers and tracks — none is asked for here: the first two are baked numbers the dp pass will reach, the last two already carry keys.
- The `map.zone300.fill` readerless key and the `#CC` alphas on three `regulatedZone.type.*` entries, both logged in the 2026-09-18 hydration.
- Every family's baked stroke numbers other than the three widths in this change: the isobath base widths of 3 and 2 with their alphas of 180 and 120, and the hazard disc's own strokes at 5 and 6 px — the sweep is colours only.

## 9. Session state

- `#focus settings` ran on 2026-09-19 09:54 UTC; the Focus History top entry points here.
- `feature/extra-settings` exists at `28d419f`, the tip of `origin/develop`, created the same day by the ordered overwrite; the one local change it collided with was this session's own `xTrack/GLOBAL_CONTEXT.md` focus write, stashed for the move and popped back, so the entry survived rather than being discarded.
- Its upstream is bound to `origin/develop` itself — the shape `#new`'s documented `checkout -b` produces — so a bare push from this branch would aim at the protected branch.
- **The hydration is stale, and the stale part is its State paragraph:** `xTrack/Ui_Settings/FEAT_HYD_Ui_Settings.md` describes a dirty tree on `feature/zones-transparency-settings` with two files unmerged in the index, while git reports one modified file (the focus entry) and no unmerged paths, and that branch sits at `50f6270` tracking its remote with the transparency pair as its tip commit. The device pass that work owes therefore belongs to that branch.
- The shoreline width, the colour boundary, the row's vocabulary, the slider's span and the unit are all settled and section 7 carries no open question; the plan is implemented on `feature/extra-settings`, uncommitted in the working tree at the time of writing.
- Raised in the same session and outside this plan: `#new` should warn before it switches and offer to overwrite the local branch. Parked as a global todo, because its wording is unsettled — overwrite has to be defined against `#move new`'s existing stash-then-pop.
- Parked the same way, also outside this plan: the map's paint code moving from px to dp in one complete pass, with the look-preserving numbers and a density check, so the two changes stay tellable apart.
