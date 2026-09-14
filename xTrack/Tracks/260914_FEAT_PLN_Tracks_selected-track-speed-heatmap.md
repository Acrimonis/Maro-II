<!-- scope: feature -->
# Selected track rendering — speed heatmap option

**Date:** 2026-09-14 · **Status:** shipped — first ramp built and reviewed on `feature/track-speed`; §10's ramp v2 and the review follow-ups are the pending work under walk item 13
**Request:** extend the rendering of a *selected* track with a speed heatmap, as an alternative to today's gold highlight; keep the direction arrows when that option is on; the selected track must still read as highlighted; settings must cover the choice between the two renderings.

## 1. What exists today (measured, not assumed)

- **Selection driver:** `MapRenderFocus.highlightedId` — an ephemeral single id, in-memory only, cleared on process death; `MapTrackOverlayEffects` calls `focus.highlight(highlightedTrackId)` and then branches on `summary.id == highlightedTrackId`.
- **Selected rendering:** the selected id gets `listOf(TrackPolylineAppearance(0xCC000000, 16f), TrackPolylineAppearance(0xFFFFD700, 8f))` — a dark 16f casing under a gold 8f core. The same two-stroke branch is written twice: once in the history loop and once in the pinned loop.
- **Other tracks:** one appearance from `computeTrackPolylineAppearance(index, total, transparencyNewest, transparencyOldest, colorFrom, colorTo, strokeWidth)` in `MapScreen.kt` — the colour is interpolated by *recency index* between `trackingColorPastFrom` / `trackingColorPastTo`, alpha comes from the two transparency settings, width 6f (8f for the newest).
- **Arrows:** `TrackDirectionOverlay(points, appearances: List<TrackPolylineAppearance>, spacingPx: (speedKn: Float) -> Float, maxArrows = 2000)` — spacing is already speed-aware through `spacingPxForSpeed`, but `draw` iterates the appearance list *outermost* and paints **every** anchor for each appearance, so a banded list would repaint each chevron once per band with the last band on top; per-anchor colouring therefore needs a resolver, not a longer list. The overlay already derives a missing `speedMps` from time delta and haversine distance (`fillMissingSpeed`, GAP mapped to zero), and re-samples anchors when the integer zoom changes.
- **Speed data:** `TrackPoint.speedMps: Float?` — nullable by design. Synthetic `GAP` points carry no speed, and imported GPX points may lack it.
- **Settings precedent:** `maro.properties` already carries a knots speed window for arrows — `track.direction.speedFloorKn=3.0`, `track.direction.speedCeilingKn=35.0`, plus `minSpacingDp` / `maxSpacingDp`; a heatmap block sits naturally beside it and reuses the same vocabulary.
- **Ramp precedent:** `DepthColorRamp.kt` with its unit test and tokens held in `colors.properties` through ColorManagement; `TrackPolylineAppearance` is a pure data class produced by a pure function that is already unit-tested (`TrackPolylineAppearanceTest`).
- **GAP treatment:** `buildSegmentOverlays` splits each appearance into solid segments and dashed GAP seams, so any banded rendering multiplies by that split.
- **Live line:** a separate path — `MapTrackOverlayLiveEffects` draws the `track_recording` polyline in `trackingColorActive` at a fixed 10f with no appearance list, created on the recorder's OFF→ON transition, appended point-by-point from `newPointStream`, split by GAP points into a dashed seam plus a fresh solid line, and capped by a display-only `track_trailing` overlay at ~40% alpha. Selection never touches it — the selected track's overlays are merely lifted above it, so the live line keeps its colour while a selected track can paint over it.
- **Selection's full effect:** sets the single ephemeral id, recolours that track's two strokes, force-includes it against both the map filter and the render cap, opens the track drawer with prev/next over the non-live list, and rebuilds its arrows when they are on.

## 2. The core tension

Gold currently does two jobs: it is the *selection cue* and it is the *fill colour*. A speed heatmap needs the fill to carry speed, so the cue must move to the geometry around the line. Three shapes:

- **A1 — cue becomes the casing.** Keep the dual stroke: the casing stays as the selected-track marker (gold, or darkened gold) and the 8f core becomes the speed band. Selection language survives unchanged and the heatmap is readable inside it. Objection: gold casing beside a yellow/amber top band shares hue, and at low zoom a 16f casing with an 8f core leaves little room for band discrimination.
- **A2 — no gold in heatmap mode.** Casing becomes neutral dark, core carries the ramp, selection is signalled by the extra width and top z-band alone. Best colour fidelity and the simplest legend. Objection: it breaks the app's established gold-means-selected language, which the marker layer shares.
- **A3 — three strokes.** Casing, gold line, heatmap line innermost. Most faithful to "still highlighted" and keeps gold literally on the track. Objection: three polylines per selected track per band, and the inner line becomes too thin to read bands at low zoom.

**Recommendation was A1 — superseded by D1**, which took A2: heatmap mode drops gold entirely, so the palette no longer has to avoid a gold casing, though it still stops short of yellow to stay clear of the map's other yellow geometry (hazard discs, the zone-ahead cone). Strongest objection stands: at low zoom the bands compress inside the casing, so the mode is at its best zoomed in — acceptable, because reading one track's speed profile is the point.

## 3. Open decisions

- **D1 — selection cue (CLOSED 2026-09-14).** In heatmap mode the heatmap *replaces* gold outright — the selected track is the dark casing plus the banded speed core — and gold survives only in the default highlight mode. Selection then rests on geometry: the 16f casing against the 6f single stroke of every other track, plus the top z-band. Residual risk accepted: at low zoom the casing dominates, so the selected line can read as a dark line, mitigated by the dashboard and the list still naming the selected track.
- **D2 — speed scale (CLOSED 2026-09-14).** Absolute bands shared by every track, default window 3–35 kn, reusing the arrows' knot vocabulary rather than a per-track min→max fit — a fit would always exercise the full palette but would make two tracks incomparable and would paint a slow drift like a speed run. The deciding constraint is compliance reading: the render must show **below 5 kn** and **below 10 kn** at a glance, so band edges are anchored on those limits instead of being spaced evenly across the window. The ramp's own domain is 0–35 kn, stated once as the ramp's span — zero to `family3.maxKn` — so sub-3 kn drift and imported points still map to a band; 3–35 kn is the arrows' spacing window alone.
- **D2a — band set (CLOSED 2026-09-14).** Three semantic families — compliant green to 5 kn, caution orange from 5 to 10 kn, danger red above — with the hue jumps landing exactly on the compliance limits and lightness grading continuously inside each family. The line stays a gradient: the mapping is continuous and monotonic in speed, and only its quantised drawing is bounded. Stops, quantisation rules and the objections are in §3a.
- **D3 — where the band colours live (CLOSED 2026-09-14).** Decision: raw hexes and thresholds both live in `maro.properties` — one self-contained file, hand-editable in one place beside `track.direction.*` — with the hybrid and the all-colours-in-`colors.properties` options considered and set aside. Mechanically sound: `AppConfig` loads `maro.properties`, then `ui.properties`, then `colors.properties`, each overriding the previous, so colour keys there are read like any other. Two consequences to hold: that same load order means a duplicate key in `colors.properties` silently wins, so a colour key must live in exactly one file; and the app's convention is that all colour values live in `colors.properties` (`map.depth.ramp.*` endpoints included), which is where the `${semantic.*}` aliases and `docs/color-scheme.md` keep them legible. Objection, recorded but overruled: raw hexes there duplicate semantic values — `#4CAF50` instead of `semantic.compliant` — and escape colour governance.
- **D4 — null-speed and GAP policy (CLOSED 2026-09-14).** Derive speed from consecutive points where the stored value is absent (distance over Δt), carry the last known value across short gaps so a single lost fix cannot flicker the colour, and fall back to a fixed neutral tint for long spans and for GAP seams — which keeps the dashed seams reading as discontinuities rather than as speeds. Objection recorded: carry-forward invents data and a derived value is noisier than a recorded one; the neutral tint and the legend are what keep that honest.
- **D4a — arrow colour inside a banded line (CLOSED 2026-09-14).** Arrows adopt the local band colour — each arrow takes the colour of the segment it sits on, so the chevrons read as part of the speed profile. The constant-contrast and per-arrow under-stroke alternatives were dropped with the choice: the first loses the arrow-as-speed cue, the second adds up to `maxArrows = 2000` strokes on the overlay rebuild path. Residual risk accepted: an arrow can vanish where its band matches the line beneath it, most likely in the flat stretches the compliance bands already mark. **Mechanism (walk item 7):** the overlay gains an *optional* colour resolver — null preserves today's per-appearance iteration, so no other track changes behaviour, while heatmap mode draws one chevron per anchor in that anchor's band and lays the dark casing chevron once beneath. Spacing is untouched.
- **D5 — scope (CLOSED 2026-09-14).** The mode governs the selected track only: any track selected while heatmap mode is on renders as a heatmap, and the rest of the selection path — force-inclusion against the filter and the render cap, the drawer with prev/next, the arrow rebuild — is untouched. The live recording line is explicitly out of scope: it is a separate incremental path (`MapTrackOverlayLiveEffects`, fixed `trackingColorActive`, no appearance list, its own GAP and trailing overlays), so including it would be a different and larger change rather than a reuse of the stored-track branch. Non-selected tracks keep today's recency-interpolated colour. Runtime override (R1): the mode is switchable session-wide by the eye toggle in the selected track's detail header — one state, not one per track — with the `maro.properties` key as the start-up default and nothing persisted.
- **D6 — legend (CLOSED 2026-09-14).** A compact legend strip ships on the map — ticks on the family boundaries and the window top, which at three families means 5 and 10 kn — shown only while heatmap mode is on and a track is selected, so it spends map space only in the situation it explains. The Settings preview option died with the Settings surface (item 4), and file-only documentation was rejected because a ramp whose edges sit at 5 and 10 kn is unreadable without a visible scale. Shape, scale and anchor are in §3c.

## 3a. Speed ramp and quantisation (closed 2026-09-14)

**Superseded by ramp v2 in §10 — kept as the record of the first shape.** Three semantic families, hue jump on the limit, lightness graded inside each family so speed reads as a gradient rather than three flat stripes. The shape was never fixed at three, which is exactly why v2's five families cost values and no code.

| Speed | Family | Anchor token | Suggested stops |
|---|---|---|---|
| 0 → 5 kn | compliant green | `semantic.compliant` `#CC4CAF50` | `#81C784` at rest → `#4CAF50` at 5 kn |
| 5 → 10 kn | caution orange | `semantic.caution` `#CCEF6C00` | `#EF6C00` at 5 kn → `#E65100` at 10 kn |
| 10 → 35 kn | danger red | `semantic.danger` `#CCB71C1C` | `#E53935` at 10 kn → `#8E0000` at the window top, with `#B71C1C` lying on that path rather than being a stop of its own (B15) |

- **Piecewise-continuous and bounded (B11):** the colour is continuous inside each family with lightness monotone across the whole domain, and deliberately discontinuous in hue at the compliance edges — so "continuous and monotonic" was wrong at the seams. The *drawing* is quantised into a bounded number of steps, because a smooth ramp would otherwise need one overlay per point pair; quantisation is invisible in practice, at the cost that a value is painted as its nearest step rather than exactly.
- **Non-uniform steps (revised by R2):** 1.0 kn below 10 kn, because that is where the limit is read and it keeps both compliance edges exact, and 5.0 kn above, where only the trend matters — fifteen draw bands over the 0–35 kn domain instead of the thirty a 0.5 kn step would give, which is what keeps §4's overlay budget honest.
- **No yellow:** the ramp stops climbing hue at `#E65100`, because gold `#FFD700` is the selected-track cue and a yellow stop would fight it.
- **Lightness is monotonic with speed** — light green, mid orange, dark red — so the ramp survives deuteranopia, greyscale and sunlight. Residual objection: green and orange are the classic confusion pair, mitigated by that grading but not eliminated; cyan→yellow→red would separate better and would cost the app's green-means-compliant language.
- **Precedent:** the depth readout already teaches a two-threshold idiom at the same values — `ui.dashboard.readout.collision` at ≤5 m and `.shallow` at ≤10 m.
- **Alpha floor (named by R4):** the banded core renders at a fixed `track.heatmap.coreAlpha` of 0.9 so the ramp is never washed out, while the casing keeps the user's transparency settings and remains the part those controls govern. This is a behaviour change for the selected track in this mode and is noted in §5.

## 3b. Proposed configuration shape (D3, closed)

> **Superseded by §16 (2026-09-14):** the key block below is ramp v4's six families at 5/6/10/15/35/70 and no longer describes the shipped file. §16 is the current contract; this section stays as the record.

Segments mirror the existing track-colour idiom — a from/to interpolation pair — so each family is a segment with its own endpoints:

```
# ── Selected-track speed heatmap ──────────────────────────────────────────
# Start-up rendering for the selected track; the eye toggle overrides it at runtime.
track.heatmap.mode=highlight

# Ramp families, numbered 1..familyCount and read in order, in knots. Each family grades
# its own from → to colour AND declares its own draw-band step; equal from and to make a
# flat zone, which collapses to one band whatever its step. The window is the ramp itself:
# zero to the highest family that parses. Ramp v4 (§12).
track.heatmap.familyCount=6
# Family 1 — the slow compliant zone: flat green up to the 5 kn limit.
track.heatmap.family1.maxKn=5
track.heatmap.family1.from=#4CAF50
track.heatmap.family1.to=#4CAF50
track.heatmap.family1.stepKn=0.5
# Family 2 — the one-knot changeover to blue, which is the entire transition.
track.heatmap.family2.maxKn=6
track.heatmap.family2.from=#4CAF50
track.heatmap.family2.to=#1E88E5
track.heatmap.family2.stepKn=0.25
# Family 3 — flat blue up to the 10 kn limit.
track.heatmap.family3.maxKn=10
track.heatmap.family3.from=#1E88E5
track.heatmap.family3.to=#1E88E5
track.heatmap.family3.stepKn=0.5
# Family 4 — blue warming to light orange across 10 to 15 kn.
track.heatmap.family4.maxKn=15
track.heatmap.family4.from=#1E88E5
track.heatmap.family4.to=#FFB74D
track.heatmap.family4.stepKn=0.5
# Family 5 — the hard edge at 15 kn: light orange gives way to orange, grading to red at 35.
track.heatmap.family5.maxKn=35
track.heatmap.family5.from=#EF6C00
track.heatmap.family5.to=#B71C1C
track.heatmap.family5.stepKn=1.0
# Family 6 — red to purple from 35 to 70 kn, the range almost nothing occupies.
track.heatmap.family6.maxKn=70
track.heatmap.family6.from=#B71C1C
track.heatmap.family6.to=#6A1B9A
track.heatmap.family6.stepKn=5.0

# Line and null policy. The neutral tint repeats ui.text.muted's value today, which is the
# duplication D3's objection predicted (B14); it stays a key so the two can part company.
track.heatmap.coreAlpha=0.9
track.heatmap.unknownColor=#FF90A4AE
track.heatmap.carryMaxSec=10
```

- **Taxonomy (revised 2026-09-14, ramp v4):** twenty-nine keys in three groups, in reading order — the mode, then the ramp families, then the line's alpha and its null policy; the separate step keys are gone, since each family now carries its own `stepKn`. Every key is named after the noun it carries, no key embeds a threshold that could move, and the window is not a key at all: it is the ramp's own span, zero to the highest parsed family's `maxKn`, so the top is written once. The ramp is list-shaped in code and numbered in the file, so v2's five families and v4's six each cost values and no code; `familyCount` bounds the read loop, capped at eight (B13), and a missing index ends it. The values are then used exactly as written — no validation, no warning channel, no fallback, the policy having been withdrawn on 2026-09-14 — so a re-cut ramp that no longer changes colour at the 5 and 10 kn limits is honoured without comment.
- The hexes above sit in `maro.properties` by decision D3 — one self-contained file, no aliasing; the hybrid that referenced `colors.properties` anchors was set aside.
- Values are read once through `AppConfig`, so a ramp change needs a rebuild — the same as every other properties value in the app.
- The example hexes are proposals, not tokens yet: the app has no heatmap keys today.

## 3c. Legend presentation (D6, closed)

> **Tick rule superseded by §16 (2026-09-14):** the legend no longer derives its ticks from the family boundaries. The anchor, panel treatment, colour source and visibility below still stand.

A vertical strip drawn as Compose chrome at the map's **top-left, below the toggle-button row** (§12, superseding the bottom-left anchor of B3), not as an osmdroid overlay, so a polyline can never paint over it. It ships as `ui/map/TrackSpeedLegend.kt` (R6), hosted in the map's own chrome in that slot, and takes the same panel treatment as the zone info tiles that stack at the map's bottom-left — white labels over the translucent grey backdrop those tiles sit on, taken as that token rather than as a copied hex. The zone stack and the legend never share a corner, since the tiles are bottom-left and the legend sits top-left.

```
     ╭──────────────────────────╮   ← tile backdrop, 1 dp border
  35 │▏█████████████████████████│   red at the labelled top edge
     │▏█████████████████████████│   orange grading to red below it
  15 │▏▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓│   blue has warmed to light orange
  10 │▏▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒│   blue, the second limit
     │▏▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒│
   5 │▏░░░░░░░░░░░░░░░░░░░░░░░░░│   green, the first limit
     ╰──────────────────────────╯
      ▲ 14 dp bar, white labels at 10 sp, hairlines in the muted token
```

- **Anchor (§12):** the top-left, below the toggle-button row, on the app's standard gutter — the right edge was rejected because it already carries the control stack, the fan and the drawers, and the bottom-left anchor that B3 chose was replaced by the user so the control sits with the rest of the map chrome. Final anchor and spacing follow `docs/ui-component-guidelines.md`.
- **Scale is linear over a written maximum (§15):** the bar runs from zero to `track.heatmap.scaleMaxKn` — linear, its top edge labelled with that value, and no cap above it. The shipped 35 gives ticks at 5, 6, 10 and 15 with the 6 kn hairline unlabelled and the 35 kn edge labelled, while speeds above the maximum are painted on the line and simply absent from the scale. Labels take the bar's end inset, so the 5 kn label lands about 21 dp from the foot. Known weakness, open as §14 finding 2: the greedy label rule drops the 10 kn label before the 15 kn one once the font scale passes roughly 1.3.
- **Source of colour:** the bar is filled by the same ramp function the track uses, so legend and line cannot drift apart.
- **Visibility:** on while the mode is heatmap and a track is selected; hidden on deselection or a mode switch, kept while the track drawer is open since it explains the track being shown.

## 4. Performance sketch

> **The band counts below are v4's (superseded 2026-09-14):** on the v5 grid the same two sweeps re-derive to 27 bands from 0 to 35 kn and 19 for the 8 to 30 kn span, so the 21 that follows is stale. The colour budget of 43 plus the neutral tint is unaffected, the steps having not changed. Recorded in the Outcome.

Banded rendering means one polyline per **run per band**, not per band (A3): `buildSegmentOverlays` emits one polyline per GAP-separated run, so a band is a set of runs. Corrected twice now, and the current figures are v4's (third Ask pass, finding 6): the six-family grid allows **43 ramp colours plus the neutral band**, so a gapless run draws at most 44 plus the casing — v2's 36 and the earlier forty-five were both counting something other than how many colours actually get painted. Measured on the shipped grid: 27 bands for a gapless track sampled at a knot from 0 to 35 kn, 21 for one spanning 8 to 30. **The product is still not a true bound (finding 3 of the second pass):** merging applies only to consecutive equal appearances, so a 1 Hz signal straddling a step edge can approach one band per point, and a track with a handful of GAPs multiplies that. Accepted rather than guarded — hysteresis would bend the nearest-point colour rule the render depends on — but stated here so the z-lift and `OverlayZOrder.reorder` cost (R8) is never mistaken for a small constant.

## 5. Non-goals

- No change to how non-selected tracks are coloured or to the recency-based interpolation.
- No change to arrow spacing rules or the arrow density settings.
- No new persistence: the mode is a properties default overridden for the session, and the selection stays ephemeral.
- No dimming of non-selected tracks to make the selection stand out — that is a separate behaviour, logged as a suggestion only.
- No transparency authority over the selection (B4): the casing keeps today's fixed alpha, so neither stroke of the selected track is governed by the transparency settings in this mode, and the heatmap core uses `coreAlpha` instead. The earlier claim that the casing "keeps the user's transparency settings" was new behaviour described as preserved.
- In heatmap mode the drawer accent stops signalling selection and signals identity only (B4, A9), the open drawer plus whatever the map draws being what says "this is the track you opened".

## 6. Implementation work package (walk item 9)

- **New `ui/map/TrackSpeedHeatmap.kt`** — the pure mapping: ordered points plus the ramp configuration in, a quantised `List<TrackPolylineAppearance>` out, with the D4 policy inside it (derive where a value is absent, carry over short gaps, neutral tint for long spans and GAP seams). Sited here rather than in `MapScreen.kt` by the item-5 decision, mirroring `DepthColorRamp.kt`. **R7:** the derive-and-fill step is shared *from* here and consumed by `TrackDirectionOverlay`, whose private `fillMissingSpeed` and `deriveSpeedMps` are deleted; the shared function returns null for an underivable point so each caller applies its own GAP policy — zero for spacing, neutral for colour.
- **Composition of that mapping (user request 2026-09-14).** Three small pure functions rather than one, split by what actually changes: `resolveSpeeds(points, carryMaxSec)` applies the D4 policy; `colorAt(speedKn, ramp)` holds the gradient for a single point, takes a nullable speed, returns the opaque hue and answers an unknown speed with the neutral tint; `bandedAppearances(points, speeds, ramp, steps)` quantises and groups runs into bands, applying the core alpha when it builds each band. Retuning the ramp therefore touches the gradient alone, and changing the band width touches the quantiser alone. The legend consumes the same `colorAt` that paints the line, which is what makes "legend and line cannot drift apart" mechanical rather than a promise. The ramp arrives as a `HeatmapRamp` data class holding its families as a *list*, so three, four or five families need no code change; `AppConfig` parses those keys into that list and the heatmap consumes it, since a parsed collection already lives in that object — `isobarColors` is the precedent (B2 dissolved, B14) — while the *nullable* derive primitive lives in `data/track` beside `TrackPoint` so the arrow overlay never depends on a heatmap-named file (A5). **A2:** only that nullable primitive is shared with `TrackDirectionOverlay`; carry-forward stays inside `resolveSpeeds` and `carryMaxSec` never reaches the arrow path, whose zero-for-GAP behaviour is untouched. Escalation if a second ramp ever appears: a `fun interface SpeedRamp` with the segment implementation behind it — not now, since one implementation behind an interface is speculation.
- **New `TrackSpeedHeatmapTest.kt`** — the mapping's own unit test, beside `TrackPolylineAppearanceTest`.
- **`config/HeatmapRamp.kt` (added 2026-09-14, §11 finding 4)** — the ramp cluster: the mode, the family with its own `stepKn`, the ramp, the family cap and the pure `parseHeatmapFamilies()` that `AppConfig` calls, so `data/track` keeps only the nullable derive primitive and neither a config-to-UI edge nor a presentation type in the domain package remains.
- **`app/src/test/java/ykws/android/maro/config/HeatmapRampPropertiesTest.kt` (added 2026-09-14)** — drives the parser from the real `maro.properties` text, so a typo such as `family3.too=` cannot truncate the ramp with a green suite; proving visibility is all the withdrawn validation policy allows.
- **`app/src/main/assets/maro.properties`** — twenty-two keys in four groups: `mode`; `familyCount` capped at eight with five numbered families as `familyN.maxKn`, `.from`, `.to`; `stepFineKn` 0.5 and `stepCoarseKn` 1.0, the knee having lost its job once ramp v2 graded the whole domain (§10); then `coreAlpha` 0.9 per R4, `unknownColor` `#FF90A4AE` and `carryMaxSec` 10 per R5. No `minKn` or `maxKn` key remains — R3's domain is the ramp's span, read from the highest family that parses — and the colours stay raw hexes by D3.
- **`config/AppConfig.kt`** — typed accessors for those keys, parsed through the existing colour parser, with the gold rendering as the default whenever a key is missing.
- **`ui/map/MapTrackOverlayEffects.kt` with `MapScreen.kt` (A1, A8, A12)** — one dispatcher plus two self-contained path builders for the selected track, consumed by the history loop and the pinned loop; the runtime mode becomes a parameter of the effect whose state lives in `MapScreen`, and the effect's key list gains *that mode alone*, because the properties are read once before composition — an edited file needs a relaunch, not a new key.
- **`ui/map/TrackDirectionOverlay.kt`** — the optional colour resolver with its contract explicit (A4): the casing appearance and the chevron metrics are passed in rather than inferred, since `draw` derives both lengths and stroke widths from the appearance inside its loop, and the resolver is typed `anchor → TrackPolylineAppearance`; null preserves today's per-appearance iteration, so no other track changes behaviour. **A6:** every band of the selected track carries one title — the z-lift matches exact titles while teardown matches prefixes, so a per-band suffix would drop the selected track below the others at the end of the effect.
- **The legend strip** — `ui/map/TrackSpeedLegend.kt` (R6), a compact map element with ticks on the family boundaries, appearing only while the mode is heatmap and a track is selected; it sits at the map's top-left below the toggle row (§12, superseding B3's bottom-left), with spacing and the shared chrome inset following `MapScreen`'s own constants and `docs/ui-component-guidelines.md`.
- **The rendering toggle (A1, B16)** — an eye icon in the drawer header of `OverlayLayer.kt`, left of the trash, in both the landscape and the portrait drawer, because that is where the header and its `headerActions` row actually live; `TrackHistoryOverlay` holds only the multi-select delete spec and no header of its own. It carries no label, so its state rides the existing icon convention — accent at full alpha for speed mode, the `buttonActionIconInactiveAlpha` token for gold, which is a fan-button convention the drawer header icons do not currently use, so the state alpha is new there — and the map legend doubles as its readout, since the legend exists only in speed mode. Glyph risk recorded: the app already had an eye toggle that meant *visibility* until the `visibleOnMap` flag was dropped with no replacement, so this eye reads as rendering-only and any future visibility control needs a different glyph.
- **Verification criteria (R10, B6, §12, §15):** on a track crossing the limits the hue change lands on the first point past each of the 5 and 10 kn edges — nearest-point rather than interpolated, since the geometry is point-indexed and a mid-segment crossing changes colour at the next fix, which at 1 Hz smears the transition over a few metres; the ramp's one deliberate discontinuity, the hard edge at 15 kn, lands the same way and is distinguishable from a rendering fault; the flat 0–5 and 6–10 zones render as single colours rather than staircases; a track stored with no speed renders the neutral tint end to end; a short track inside a single band renders one colour under the casing; GAP seams are single and neutral, never one seam per appearance; the legend appears and disappears with the eye toggle at the map's top-left, drawing linearly from zero to `scaleMaxKn` with that value labelled at its top edge and nothing above it; and the gold path renders exactly as it does today.
- **The drawer card accent (`OverlayLayer.kt`, A9, B7)** — the opened track's accent bar stops being hardcoded gold and takes the track's own render colour. The *function* is shared with the list, but its inputs cannot be: the list derives from pinned and history splits, a recency sort and a render cap, while the drawer holds only a flat, uncapped index — so the shared helper takes an already-resolved colour instead of recomputing one from different arguments, and the clamp to full opacity is a deliberate divergence from the map, because the bar sits on a card rather than blending over water. The `MeasureHeight` twin at line 613 changes with it.
- **No validator (withdrawn 2026-09-14)** — the properties are used as written, so the machine that would have judged them, the snackbar variant it needed and the message documentation it required are all out of scope; A7, A14 and B9 go with them.
- **`docs/color-scheme.md` (A10)** — one sentence amended so it no longer calls `maro.properties` spatial tunables only, recording that the speed ramp's band colours live there with their thresholds as functional data. No colour rows, no aliases, no tokens.
- **`ui/map/MapTrackSegments.kt` (B1)** — a second entry point taking one band's geometry and its appearance and returning that run's solid and dashed polylines, beside the existing whole-track builder, so the banded path never loops appearances over a full point list.
- **Two seams recorded (B17)** — the mode value and its toggle callback join the overlay parameter object rather than widening an already wide `OverlayLayer`, and `TrackDirectionOverlay` will hold two notions of a null speed on purpose: zero for spacing, the carried value for colour.
- **Untouched:** the live recording path, the non-selected tracks' colours, the arrow spacing rules, and the selection path beyond the two strokes, this one new control and the accent bar.

## 7. Review findings — self-review, 2026-09-14

Findings from the `#review` pass over this plan, ordered by severity. All ten were folded on 2026-09-14: R1 closed as the eye toggle in the detail header, and R2–R10 corrected the body in place — the band count and the overlay budget now agree at §3a and §4, the ramp's domain is declared once in §3b, the core alpha and its bypass of the transparency settings are named, the neutral tint and the carry window carry values, the legend has its own file, the derivation is shared rather than duplicated, the z-lift cost is stated against the reduced band count, the seam rule is recorded, and the verification criteria are expanded in §6.

- **R1 — the Request line contradicts D3 and item 4.** Line 5 demands that settings cover the choice between the two renderings; the decisions put the mode in a `maro.properties` key and removed every UI surface, so the mode cannot be switched on the water without a rebuild. Either the Request line is re-worded to record that the switch is file-only for this pass, or a minimal control returns. **Resolved 2026-09-14:** a control returns, but neither in Settings nor as a labelled row — an eye icon toggle in the selected track's own detail header, left of the trash, starting from the `maro.properties` value and overriding it at runtime. The Request line therefore stands unchanged, and item 4's decision stands untouched because this is not a Settings surface.
- **R2 — the band arithmetic contradicts the performance budget.** §3a's steps of roughly 0.5 kn below 10 kn and 2.5 kn above, applied over a 0–35 kn window, yield about thirty draw bands, while §4 assumes five to eight; the overlay count, the GAP split and the per-rebuild remove/add all scale with the larger number.
- **R3 — the ramp domain is stated twice, differently.** D2 gives a 3–35 kn window while the band edges, the step sizes and the legend ticks are all written from zero; one domain has to be named and used everywhere.
- **R4 — the alpha floor is unstated and silently overrides the user's transparency.** §3a requires a minimum alpha on the heatmap core and records no value, while the opaques hexes in §3b suggest the transparency settings may not apply at all to the selected track in this mode — a behaviour change absent from both the risks and §5.
- **R5 — two keys have no proposed defaults.** The neutral tint for long spans and GAP seams and the carry window that defines a short gap both appear in §6's key list without a value, so the implementer must invent them.
- **R6 — the legend has no owner file.** §6 lists five files plus "the legend strip" with no path or composable named, and §3c does not say which chrome layer hosts it, so the work package cannot be executed as written.
- **R7 — speed derivation would exist twice.** `TrackSpeedHeatmap` is to carry the D4 derivation while `TrackDirectionOverlay` already holds private `fillMissingSpeed` and `deriveSpeedMps`; sharing or duplicating has to be decided, or the two derivations drift.
- **R8 — banded overlays multiply the title-based z-lift.** The lift that raises the selected track filters overlays by title and re-adds them, so it would move one overlay per band per rebuild — a cost that grows with R2's band count, and the same for `OverlayZOrder.reorder`.
- **R9 — GAP seams would be drawn once per appearance.** `buildSegmentOverlays` splits every appearance into solid and dashed parts, so a casing, a band and a neutral tint each spawn their own dashed seam over the same gap; the seam should be neutral-only.
- **R10 — verification has no compliance criterion.** Item 10 is a single line, with no test that the hue change lands exactly on the 5 and 10 kn edges on a real track, no short-track or single-band case, and no check that the gold mode renders byte-for-byte as before.

**Verified sound, not to be re-checked:** the `AppConfig` load order and the `colors.properties`-wins-on-collision claim; the overlay's null-resolver default preserving today's iteration; and the exclusion of the live recording path.

## 8. Ask-review findings — independent pass, 2026-09-14

An Ask-mode reviewer verified this plan against the tree in a fresh context and returned fourteen findings with a verdict of *not ready to implement as written*. All fourteen are settled: A7, A9 and A10 by the user's calls, and the rest by fixes applied to the body on 2026-09-14 — the work package now names `OverlayLayer.kt` and `MapScreen.kt`, only the nullable speed primitive is shared, a band is defined as a set of runs, the resolver and title contracts are explicit, `HeatmapRamp`'s builder and the mode's home are named, the key-list premise is reworded, the legend's host is named, and the sanity checks are one validator.

- **A1 (high — fix queued):** the toggle's site did not exist as written. The drawer header that carries the delete icon is `OverlayLayer.kt`, portrait and landscape branches, through `DrawerScaffold(headerActions = …)`; `TrackHistoryOverlay` holds only a multi-select delete spec and has no header of its own. The work package must name `OverlayLayer.kt` and `MapScreen.kt`.
- **A2 (high — fix queued):** share only the nullable primitive. `deriveSpeedMps` returns zero for GAP and never carries, while D4 puts carry-forward inside the shared step — which would silently change arrow spacing for every track holding null speeds, against §5. `carryMaxSec` never reaches the arrow path; the overlay keeps its zero, the heatmap keeps its neutral tint.
- **A3 (high — fix queued):** band-to-geometry was undefined, so §4's figure was a lower bound. `buildSegmentOverlays` emits one polyline per GAP-separated run, so a band is *a set of runs* and the count is runs × bands, not bands; §4 is restated against that definition rather than assuming a gapless track.
- **A4 (medium — fix queued):** the resolver's contract was implicit twice over. `draw` derives both chevron length and stroke width from the appearance inside its loop, so the resolver must take the casing appearance and the metrics explicitly, or be typed `anchor → TrackPolylineAppearance`.
- **A5 (medium — fix queued):** the shared primitive must not live in a heatmap-named file. It moves to `data/track` beside `TrackPoint` — or a small speed-data file — so the arrow path never depends on a heatmap file.
- **A6 (medium — fix queued):** the title contract is now stated, because it is a correctness trap rather than only a cost. The z-lift matches exact titles while teardown matches prefixes, so a per-band title suffix would leave the selected track dropped below the others at the end of the effect: one title per track across every band, or the lift switches to `startsWith`.
- **A7 (medium — withdrawn 2026-09-14):** the validation-and-warning policy this finding was closed under was dropped by the user, so no validator, no refusal and no warning channel ships and the properties are used as written. The reviewer's underlying point survives as a stated boundary rather than a guard: a hand-edited ramp that loses a 5 or 10 kn boundary loses the limit read with nothing to announce it. §3c's sketch still needs redrawing proportionally.
- **A8 (medium — fix queued):** nobody owned `HeatmapRamp` or the mode state. `AppConfig` is a primitives-only object, so the ramp is built outside it, the session mode lives in `MapScreen`, and the effect takes it as a new parameter.
- **A9 (medium — CLOSED 2026-09-14):** the drawer card accent drops gold for the track's own colour — the one it already shows as a list item and as an unselected map line — so the accent becomes an identity marker rather than a selection marker, and no gold is left to meet the ramp. Measured rather than counted: the hardcoded `0xFFFFD700` sits at `OverlayLayer.kt` 499 and 592 for the two visible drawers plus 613 inside `MeasureHeight`, which is a measurement twin of the card and must be kept in step — the reviewer's "three surfaces" is two bars and one mirror. The colour is sourced through the same appearance call the list uses, which needs the opened track's index and total; both already reach the layer as `currentTrackIndex` and `trackListIds`, since the drawer owns prev/next. The accent must not inherit the transparency alpha, or a user's invisibility setting would erase the bar.
- **A10 (medium — CLOSED 2026-09-14):** the band colours are *functional* data rather than palette entries, so they stay in `maro.properties` beside the thresholds they describe — the user's call, and the reviewer's evidence supports it twice over, since `${semantic.*}` interpolation runs on the merged properties object and track colours already live in that file as decimal ARGB. `docs/color-scheme.md` therefore gains no colour row and no token; its single sentence describing `maro.properties` as spatial tunables only is amended instead, so the document stops contradicting the file. A11's note that `maro.properties` has a second reader — the `BuildConfig` bake — is carried for the implementer.
- **A11 (low — closed, no body change):** the reviewer was right that D3's sentence overstates the convention — but the sentence is about colour *tokens*, which is accurate enough, and A10 settled that the ramp's band colours are functional data rather than tokens. The finding's second half is carried forward instead: `maro.properties` has a second reader, the `BuildConfig` bake, so an implementer changing its shape should check that reader.
- **A12 (low — fix queued):** the effect key list rested on a false premise — properties are read once before composition, so an edited file needs a relaunch and only the runtime mode needs a key. §3b and §6 are reworded.
- **A13 (low — fix queued):** the legend's host is named properly, and the invented "chrome band" vocabulary is dropped.
- **A14 (low — withdrawn 2026-09-14):** the parse guard went with A7's validator. What remains is parsing rather than judgement: `familyCount` bounds the loop and a missing index ends it.

**Unverified by the reviewer, disposed here:** `feature/track-speed` exists — commit `322f4cc` — and the "below 5 and 10 kn at a glance" constraint is recorded, as the deciding line of D2. It was right about the stale walk line: the R1 child's resolution still said "two-option Rendering toggle" where the eye toggle is what was chosen, and that is corrected.

## 9. Ask-review findings, second pass — 2026-09-14

A second independent Ask pass read the folded plan and returned seventeen findings with a verdict of *not ready as written*. B1 and B3 change what gets built and stay open; **B2, B8 and B9 dissolved on 2026-09-14 when the user dropped the validation policy** — with no validator there is nothing to see a failed parse, no warning to carry, and no message list to write; the rest are consistency repairs.

- **B1 (high — contract hole):** the banded geometry has no carrier. `TrackPolylineAppearance` is colour and width only, and `buildSegmentOverlays` applies one appearance to the whole point list, with the caller looping appearances over that same full list — so §6's stated output type would paint fifteen full-length lines instead of bands. Fix: return paired geometry, a band carrying its own points or index range beside its appearance, and add the matching segment-builder entry point.
- **B2 (high — dissolved 2026-09-14):** the failed-parse blindness only mattered because a validator was to act on it. With the policy withdrawn, `AppConfig` reads the heatmap keys through its normal accessors like every other key, defaults stand in for absent ones, and nothing reports. B14's observation is what makes this clean rather than a fudge — `isobarColors` shows that object already carries a parsed collection, so the ramp's family list belongs there too.
- **B3 (medium — placement):** §3c hosts the legend "in that same column" — the right-edge control column by its own constant — while also anchoring it on the left edge and rejecting the right. Either the left slot is named as its own host or the anchor moves to the column.
- **B4 (medium):** §5 never gained the core-alpha bypass §3a promised it would note, and "the casing keeps the user's transparency settings" is new rather than preserved: today's selected strokes are the fixed `0xCC000000` and `0xFFFFD700`, which no transparency setting governs. §5 must state the bypass and the accent's loss of selection signal, and the casing's status needs deciding.
- **B5 (medium):** §4's arithmetic is still inverted — a gapless track that never crosses a band edge occupies one band, so it is two polylines with the casing, not sixteen — and the run product is unbounded, since GAP runs are first-class and nothing caps it. Restate as a bound with one worked worst case, and cap or merge adjacent runs.
- **B6 (medium):** the exact-edge criterion is unsatisfiable as written: geometry is point-index based, and a mid-segment band change is the normal case at 1 Hz. Either interpolate crossing points or restate the criterion as nearest-point.
- **B7 (medium):** the accent's parity premise fails on what the drawer holds — the list derives its accent from pinned and history splits, a recency sort and a render cap, while the drawer carries a flat, uncapped index — and the alpha clamp is precisely what breaks parity. Share one accent helper or hoist the colour map, and record the clamp as a deliberate divergence.
- **B8 (medium — dissolved 2026-09-14):** the snackbar was needed only as the warning channel; with the warning withdrawn there is no variant to budget, no no-undo row mode and no trigger to name.
- **B9 (medium — dissolved 2026-09-14):** the documentation this finding demanded was the validator's message list, which no longer exists.
- **B10 (medium):** the quantiser's knee is hard-coded at 10 kn while the family boundaries are keys, so a re-cut ramp leaves the legend's ticks on boundaries that no longer exist.
- **B11 (low):** "continuous and monotonic" is false at the seams; the truth is piecewise-continuous, with lightness monotonic across the domain.
- **B12 (low):** §6 still carries the old tick wording while §3c says ticks follow family boundaries.
- **B13 (low):** the taxonomy claims five groups and lists four, and `familyCount`'s bound — which the validator must enforce — is never stated.
- **B14 (low):** `unknownColor` duplicates the existing `ui.text.muted` value, and "AppConfig is a primitives-only object" is a preference rather than an invariant, since `isobarColors` is a map in that same file.
- **B15 (low):** the danger family's third stop cannot be expressed in a from/to pair; it happens to lie on the linear path, so it is redundancy to drop.
- **B16 (low):** the toggle cites the 25% alpha as a literal where `buttonActionIconInactiveAlpha` is a token, and the drawer header icons currently carry no state alpha at all.
- **B17 (low — sound, two seams to record):** the mode parameter and the shared primitive are idiomatic and the legend invents no layer, but the mode value and toggle callback belong in the overlay parameter object rather than widening an already wide layer, and the arrow overlay will hold two notions of a null speed — zero for spacing, carried for colour.

**Unverified by the reviewer, disposed here:** the branch and commit exist (`feature/track-speed`, `322f4cc`); the snackbar's draw order holds either way, since the legend is Compose chrome; the left-edge slot is this plan's own decision rather than a guidelines claim; and 5 and 10 kn as compliance limits are the user's domain statement, recorded as D2's deciding constraint. The reviewer was right that A7's documentation half was unfulfilled, and B9 records it.

## 10. Ramp v2 — proposed 2026-09-14, pending confirmation

The shipped ramp read badly: the coarse quantiser flattened 10–15 kn into a single colour, and the shape below is the replacement the user described afterwards.

- **Stops:** green pure from 0 to 5 kn; green to blue across 5 to 10; blue to light orange across 10 to 15; light orange to orange across 15 to 20; orange to red from 20 to the window top. Five families, so the model needs no change at all — a family whose `from` equals its `to` is a flat zone, which is exactly what the 0–5 compliant band should be.
- **Anchors (CONFIRMED 2026-09-14):** 5, 10, 15, 20 and the window top, with each compliant colour pure *at* its limit — green to 5, blue at 10, warming after each. The literal 6 and 12 were never the intent; they are where the warming has visibly begun, which is how the two earlier specifications reconcile.
- **Colour proposal:** green `#4CAF50` (the compliant token), blue `#1E88E5`, light orange `#FFB74D`, orange `#EF6C00` (the caution token), and red `#B71C1C` (the danger token) at the window top. The blue is deliberately not `#1565C0`, which already marks the heading arrow and the control accent, so a speed band in that exact blue would read as chrome.
- **Quantisation tightens with it:** a graded ramp drawn on 5 kn steps is what produced the flat 10–15 band, so the steps become uniform at 1.0 kn — or 0.5 below 10 kn and 1.0 above it — and the fine/coarse knee stops earning its own key. This also settles the Ask hop's top medium, in which 10.0–12.4 kn painted as 15 kn.
- **Side benefit:** the flat compliant zone collapses to one band, so a slow track draws one polyline per run rather than several, and the ramp reads well at both ends — colour identity below, resolution above.
- **Colour-blindness note:** green to blue to red is safer than green to orange to red, since blue separates the pair deuteranopia confuses. Honest caveat: lightness is *not* monotone across these five anchors — mid green, darker blue, light orange, mid orange, dark red — so the separation rests on hue, and the two orange steps are told apart by lightness rather than by hue.
- **Continuity corrected (second Ask pass, finding 1):** every boundary in the shipped ramp is C0-continuous, because each family's `from` equals the previous family's `to` — so **no compliance edge is a hue discontinuity**, and both the plan's and the code's "hue jump on purpose" wording is wrong. The consequence deserves stating: D2's at-a-glance read now rests on green-to-5 and blue-to-10 identity plus the legend hairline at each anchor rather than on a step at the limit, which is a weaker read than D2's deciding line implies. Accepted, since the anchors were chosen deliberately, but recorded so the weakening is not rediscovered as a defect.

## 12. Heatmap control v2 and ramp v4 — proposed 2026-09-14, pending the legend question

The control's placement and background are settled; the ramp has been re-cut twice since, and this section carries the current shape along with the two consequences that shape creates.

- **Placement (supersedes §3c's bottom-left anchor, and with it B3):** the map's top-left, *below* the toggle-button row, so it sits with the app's other map chrome rather than beside the dashboard.
- **Background:** the same panel treatment as the zone info tiles that stack at the map's bottom-left — white text over the translucent grey backdrop those tiles sit on, taken as that token rather than a copied hex, so the two cannot drift apart when the palette changes.
- **Ramp v4, six families:** flat green to 5 kn; green to blue across 5 to 6, which is the whole transition; flat blue from 6 to 10; blue to light orange across 10 to 15; a hard edge at 15 from light orange `#FFB74D` to orange `#EF6C00`, grading to red at 35; then red to purple `#6A1B9A` from 35 to 70. The window therefore triples to 70 kn, and the 35 kn mark that was v2's top becomes a family boundary. Yellow is gone, which retires the hazard-and-zone-yellow risk this section raised, and the compliant zones now sit exactly where the requirement put them — green to the 5 kn limit, blue to the 10 kn limit, warming only after.
- **Consequence one — legend scale (resolved by recommendation 2026-09-14, open to override):** a plain linear bar over the full window is not viable, since the 5, 6 and 10 kn ticks would land about 2 and 6 dp apart on a 120 dp bar — the same unreadability the user first reported, reintroduced through the range rather than the colours. The bar therefore draws linear over 0–35 kn, keeping the two compliant bands and the warm run at honest spacing, and caps itself with a purple chip marked 35+ so the extension to 70 kn is represented without compressing the part anyone reads.
- **Consequence two — quantisation grid (resolved by recommendation 2026-09-14, open to override):** the fine/coarse knee no longer fits, because it reads the second family's boundary, now 6 kn, and a fine grid below that with a knot above would ask one step to cover 6 to 70. A step is therefore declared *per family*: a quarter knot across the one-knot transition, half a knot through 10–15, a knot through 15–35 and five knots through 35–70 — and the flat families collapse to a single band whatever their step, so the grid only shapes the four gradient families and a typical track lands in the high teens of bands per run rather than the sixty-odd a uniform fine grid would give.
- **Colour collision noted:** the app already paints navigation-restriction zones `#8E24AA`, so the purple family is proposed as the deeper `#6A1B9A` to stay clear of zone geometry.
- **Unchanged by this round:** the family model, the flat-zone rule, the derived knee, the half-knot resolution where it matters, and every other decision in §3b — only the values, the grid's shape, the control's anchor and its background are in play.

## 11. Ask-review findings, ramp v2 pass — 2026-09-14

The pipeline's second Ask hop reviewed ramp v2 and the remediation. Its verdict: v2 is correct, complete and faithful to §10 and §3b — five families, the confirmed anchors, the derived knee, the half-knot and knot grid, the collapsing flat zone and both load-bearing test claims hold in code — while the plan itself lagged behind, which is what the corrections in §4, §10 and the Outcome address. Six medium and five low findings, with dispositions:

- **1 (medium — plan corrected):** no boundary is a hue discontinuity and D2's read is weaker than its deciding line implies; §10 carries the addendum.
- **2 (medium — plan corrected):** §4's bound was the stop count rather than the painted colours; it now states 36 ramp colours plus the neutral band per run.
- **3 (medium — accepted, stated):** the runs × bands product is not a true bound, because merging applies only to consecutive equal appearances, so a boundary-straddling 1 Hz signal can approach one band per point; hysteresis was rejected as bending the nearest-point rule, and §4 states the honest case instead.
- **4 (medium — open, item 13):** `data/track` now carries presentation types — ARGB values, the core alpha and the render mode — trading the config-to-UI edge for a data-purity edge. The cleaner split puts the ramp cluster and `parseHeatmapFamilies()` in `config` beside `AppConfig`, leaving only the derive primitive in `data/track` where A5 placed it.
- **5 (medium — open, item 13):** the shipped ramp exists three times — the properties file, the `AppConfig` default and the test fixture — with nothing tying them, so a typo such as `family3.too=` truncates the ramp with all twenty tests still green. One test driving `parseHeatmapFamilies()` from the real properties text would close it.
- **6 (medium — open, item 13):** the legend's five ticks are code-correct but legibility is unproven on a fixed-height bar, and §3c's sketch still draws two ticks.
- **7 (low — open):** the arrow resolver's band table is untested, including its shared-boundary overwrite.
- **8 (low — open):** the knee's derivation is untested — a regression to a literal 10 kn would break nothing — and `CAUTION_FAMILY_INDEX` is v1 vocabulary inside a green-to-blue-to-orange ramp.
- **9 (low — open):** three duplicates survive the pass — the core stroke width, a second metres-to-knots factor beside the arrow path's, and a KDoc claiming the app's only haversine while two other copies live on.
- **10 (low — open):** the banded path still emits one degenerate dashed bridge per GAP — invisible, but counted in the overlay list.
- **11 (low — partly corrected):** §6 and the Outcome described `TrackSpeed.kt` as the derive primitive alone; the Outcome is corrected here, and §6's sentence is left for the remediation pass since it omits rather than contradicts.

**Nothing in the pass breaks a decision:** the primitive's home in `data/track` is A5 verbatim, the extracted parser is what makes B13's cap testable rather than a departure, and the band-total difference only obliged §4's correction.

## 13. Ask-review findings, ramp v4 pass — 2026-09-14

The pipeline's third Ask hop reviewed ramp v4, the legend re-anchor and the four fixes. Verdict: the code is correct, complete and consistent with §3b, §3c and §12 — the plan was the thing lagging behind, which §4's restatement and §6's two new bullets repair. Six medium and seven low findings, no high.

- **1 (medium — accepted by policy):** a key that does not parse ends the ramp, so a typo silently shortens it; the new properties test proves that truncation is visible rather than guarding against it, which is the withdrawn policy applied exactly as written and now stated in the file's own comment.
- **2 (medium — open):** the legend's ten-dp label-drop threshold sits below the label's own line box and ignores `fontScale`, so at large font settings two labels the rule means to separate can still touch.
- **3 (medium — open):** the ramp shipped as `AppConfig`'s default is tied to nothing, so the code's six families and the file's can drift apart unnoticed.
- **4 (medium — open):** `LEGEND_READABLE_MAX_KN` duplicates a ramp boundary rather than reading it, so a re-cut ramp would leave the bar's readable range behind.
- **5 (medium — plan corrected):** §6 never named `config/HeatmapRamp.kt`; it does now.
- **6 (medium — plan corrected):** §4 still carried v2's arithmetic; v4's grid allows 43 colours plus the neutral band.
- **7 (low — open):** the legend's top-offset arithmetic is written in three places in `MapScreen`.
- **8 (low — open):** the 35 kn label and its hairline sit on the bar's own edge.
- **9 (low — CLOSED 2026-09-14):** the user confirmed the reference is the zone info tiles that stack at the map's bottom-left — white text over a light, translucent grey panel — and the token already in use, `ui.text.scrim`, is the backdrop those tiles sit on, so the build matches the intent and only §3c's wording was wrong. The correction also exposed a gap the plan never named: the legend's labels must be white like the tiles' text, where the shipped legend draws them in the muted token, and that belongs with the rest of item 13's hygiene.
- **10 (low — open):** the promoted knot factor's KDoc still claims a single factor in `ui/map` while `TrackHistoryOverlay` keeps its own.
- **11 (low — still open from §11):** the arrow resolver's band table is unchanged and untested.
- **12 (low — accepted):** the casing draws its own GAP bridge; §6's "single and neutral" describes that loosely rather than wrongly.
- **13 (low — contained):** `drawableBandSegments` duplicates part of `splitTrackSegments` by design, which is small and leaves the legacy path alone.

**Coverage:** every code claim in §3b, §3c and §6 holds — the six families and steps match the file, flat families collapse to one band, the 15 kn edge needs no special case, the legend's values and cap are right, the deletions left no stale reference, the live path, non-selected colours, arrow spacing and the band table are untouched, and the 27 and 21 band counts re-derive by hand. §11's findings 4, 9 and 10 are closed, 5 and 6 partly, 7 and 11 still open.

## 14. Ask-review findings, hygiene pass — 2026-09-14

The pipeline's fourth Ask hop reviewed the hygiene pass. Verdict: complete as a hygiene pass and structurally clean — one inset by construction, the band-table and readable-max derivations preserving the semantics they claim, and all seven closures holding — but not proven regression-free. Twelve findings, three medium, plus the confirmation that the default-tie test would really fail on a family, alpha or tint drift while the band-table cases pin the shared-boundary rule.

- **1 (medium — open):** the landscape branch of `chromeTopInset` cannot be shown to match the arithmetic it replaced, and the two comments left behind still describe the old expression; if that branch is new, the toggle row, settings button, lock button and legend all moved six dp down in landscape. One `git diff` settles it, then either the branch or those comments is corrected.
- **2 (medium — open):** the greedy label rule keeps labels in ascending order and drops the later of a crowded pair, so from about font scale 1.3 it drops the **10 kn limit label** while keeping the non-limit 15 kn — D2's deciding constraint lost for the users who enlarge text. Fix by prioritising the limit labels, or by growing the bar with the font scale rather than only the label box.
- **8 (medium — open):** the only guard tying the code's default ramp to the shipped file uses `assumeTrue`, so all three of its tests skip silently when the properties file is not resolvable from the test's working directory — a green run then proves nothing, which defeats the finding it exists to serve.
- **3 (low — wording):** the measured box is the label's glyph box rather than the drawn line box, since the label inherits the theme's body style; the arithmetic is defensible and only the KDoc's phrase is wrong.
- **4 (low — no defect):** the six-dp floor is strictly conservative, every consumer using it monotonically, so it cannot reintroduce the collision it guards against.
- **5 (low — open):** a hand-edited ramp whose first family ends at 0 kn removes the legend entirely, and the ascending-order assumption behind the ticks is unstated rather than enforced — correct for a file read as written, so the KDoc needs the assumption rather than the code needing a guard.
- **6 (low — open):** the horizontal half of the same arithmetic stays literal in the lock-mirror expression while the constants naming those numbers sit unused beside it.
- **7 (low — open):** tick hairlines now share the label inset, so the 35 kn hairline sits below the bar's top while the fill above still paints the 35 kn colour — the clipping is genuinely fixed, the tick-to-edge correspondence lost, and the cap chip carrying the top.
- **12 (plan staleness — corrected here):** §3c's sketch caption still described muted labels, its dp figures predated the derived readable max and the end inset, §6 still placed the legend bottom-left, §12's background bullet still read as a bare colour rather than the tiles' panel treatment, and the Outcome repeated the bottom-left anchor; all five are fixed in this pass. §13's rows keep their original "open" text as the record, with this section standing as the current state.
- **Closures confirmed (seven of seven):** the label colour matches the zone tiles' own token; the readable-max literal is gone; the default is genuinely tied to the file; §6 names `config/HeatmapRamp.kt`; §4 carries v4's arithmetic; the vertical offset arithmetic has one home; the 35 kn label is no longer clipped; and §11's finding 11 — the arrow resolver's band table — is closed.

## 15. Scale range as one key — decided 2026-09-14

The 35+ cap rendered as a flat block. Validated from the code rather than from a screen: the cap was a solid swatch with no gradient and no proportional relationship to the bar beneath it, so it read as a stray rectangle rather than as a continuation of the scale — the design's fault, since the bar-to-a-derived-maximum plus cap shape only ever existed to avoid compressing the readable range.

- **Decision:** one key, `track.heatmap.scaleMaxKn`, writing the scale's top once. The bar draws linearly from zero to that value with its top edge labelled and nothing above it; speeds beyond it are still painted on the line, they are simply not on the scale. `scaleMaxKn=35` gives exactly what was asked for — a bar over 0–35 kn with ticks at 5, 6, 10 and 15 and the 35 kn edge labelled.
- **Default when absent:** the ramp's own top, so a ramp carrying no key shows its whole span. The shipped file therefore writes `scaleMaxKn=35` explicitly, and a user who removes the key gets a bar over the full range with the crowding that implies — their file, read as written.
- **Accepted limitation:** one value cannot express a gap, so excluding a middle band is impossible by design. The per-family flag was rejected on that trade: six optional keys and a rule for compressed spans, bought for a case nobody has.
- **The value is a window, not necessarily a boundary:** a value falling inside a family truncates the bar mid-family, and the top edge still carries it.
- **What it retires:** the cap and chip, `readableMaxKn` with its single-family fallback (§14 finding 5), and the tick-to-edge oddity of §14 finding 7 — all three existed only because the cap did.
- **What it does not change:** the ramp's colours, the per-family steps, the mode key, the eye toggle, the legend's anchor and panel treatment, and the line's own rendering.

## 16. Ramp v5 and a declared scale — decided 2026-09-14

> **Superseded in part by §18 and §19 (2026-09-14):** the label-drop rule this section's bar-height rationale rests on is removed, the shipped tick table is §18's five-row one, and §19 removes the `familyCount` key written below and adds the scale's foot. The ramp, the families and the steps here all stand.

The legend was drawing the ramp's own boundaries, so on the hand-tuned file it printed 7, 12 and 35 and neither compliance limit appeared. This section supersedes §3b's key block and §3c's tick rule, and returns the ramp to six families, since the hand-tuned file had lost both the flat-blue and the light-orange family.

```
# ── Selected-track speed heatmap (ramp v5) ────────────────────────────────
# Start-up rendering for the selected track; the eye toggle overrides it at runtime.
track.heatmap.mode=heatmap

# Scale ticks: one hairline per row, drawn at positionKn and labelled textKn, read as
# written with no validation. The first two rows carry the compliance limits deliberately
# off their boundaries — 5 under the green-to-blue change, 10 under the end of flat blue —
# and every later row labels itself truthfully.
track.heatmap.scaleTicks=7:5,12:10,15:15,20:20,25:25,30:30,35:35

# Ramp families, numbered 1..familyCount and read in order, in knots. Each family grades
# its own from -> to colour and declares its own draw-band step; equal from and to make a
# flat zone. The window is zero to the highest family that parses.
track.heatmap.familyCount=6
track.heatmap.family1.maxKn=5
track.heatmap.family1.from=#4CAF50
track.heatmap.family1.to=#4CAF50
track.heatmap.family1.stepKn=0.5
track.heatmap.family2.maxKn=7
track.heatmap.family2.from=#4CAF50
track.heatmap.family2.to=#1E88E5
track.heatmap.family2.stepKn=0.25
track.heatmap.family3.maxKn=12
track.heatmap.family3.from=#1E88E5
track.heatmap.family3.to=#1E88E5
track.heatmap.family3.stepKn=0.5
track.heatmap.family4.maxKn=15
track.heatmap.family4.from=#1E88E5
track.heatmap.family4.to=#FFB74D
track.heatmap.family4.stepKn=0.5
track.heatmap.family5.maxKn=35
track.heatmap.family5.from=#EF6C00
track.heatmap.family5.to=#B71C1C
track.heatmap.family5.stepKn=1.0
track.heatmap.family6.maxKn=70
track.heatmap.family6.from=#B71C1C
track.heatmap.family6.to=#6A1B9A
track.heatmap.family6.stepKn=5.0

track.heatmap.coreAlpha=0.9
track.heatmap.unknownColor=#FF90A4AE
track.heatmap.carryMaxSec=10
```

- **`track.heatmap.scaleMaxKn` is deleted as subsumed:** the bar's top is the tick table's last position, so the top is still written once and in only one place.
- **The table is the legend's only source of ticks and labels.** `tickBoundaries()` reads it rather than the family list, and the `familyN.*` keys then describe colour alone.
- **Bar height 120 → 150 dp**, because the 10 and 15 rows are 3 kn apart — 9.3 dp at the current height, under the crowd rule's ~12 dp label floor — so the 15 would silently drop. At 150 dp every adjacent pair clears the floor and all seven rows label.
- **Cost, accepted:** the legend no longer follows the ramp, so re-cutting the ramp's colours leaves the scale printing old numbers with no test between the two, and the low end reads about 2 kn optimistic — 5 marks where blue begins, and 10 sits 2 kn below the end of flat blue. Everything from 15 up is exact.
- **`#FFB74D` is the 12–15 endpoint**, adopted as the proposal's X; writing `#EF6C00` instead removes the step at 15 and makes 12–15 one continuous sweep. One value, no code.
- **Same-pass file repairs:** the four hand-tuned family comments in `maro.properties` lag their values by one index, the stray blank line in the ramp's middle goes, and the five hand-tuned families at 7/8/12/35/70 are replaced by v5's six.
- **Tests:** both red `HeatmapRampPropertiesTest` assertions are rewritten onto v5 and the tick table, and a new case parses the table — including a row whose label differs from its position, which is the whole point of the key.
- **Open, not touched here:** `track.heatmap.mode` writes `heatmap` while `AppConfig` accepts only `speed`, so every other token falls to HIGHLIGHT and the shipped start-up default is gold. Flagged, out of this pass's scope.

## 17. Cheating the scale, the mode token and persistence — 2026-09-14

- **No marks inside the colour bar.** The full-width hairline drawn at every row is removed: over a saturated band a muted one-pixel line reads as a pale stripe across the ramp, which is what was reported. The label, vertically centred on its own row, carries the position alone. Supersedes §3c's "hairline tick on each family boundary".
- **Tick semantics confirmed.** A row is `positionKn:textKn` — the text is written at the position, so `7:5` prints 5 where 7 would be and `12:10` prints 10 at the 12 knot. The cheat is deliberate, and the Ask hop's account of `15:20` as a mislabelled typo is withdrawn: it prints 20 at the 15 kn row. That five-row table is the shipped contract, **pending the arithmetic question below**, since it places a 10 kn step across 43% of the bar and a 3 kn step across 9%.
- **The mode token accepts `heatmap`.** The parser accepts only `speed` today, so the file's own value falls to HIGHLIGHT, which is one of the two independent reasons start-up is gold. `heatmap` becomes the token and `speed` stays an alias.
- **The eye toggle persists — a reversal.** §5's "no new persistence" non-goal and the level-2 record under walk item 9 are both reversed: the chosen mode is stored, and `track.heatmap.mode` becomes the first-run default only. Shape to settle: a nullable mode field in `AppSettings` beside a KEY constant, loaded through the existing prefs and `StateFlow` pair, where null means follow the file — against dropping the file key and seeding from the default constant.
- **Still open from the Ask pass, unchanged:** the crowd rule's basis (finding 1), the NaN position reaching a canvas offset (2), the untested twelve-row cap (3), and the bar's top being free file text (4).
- **Void:** the Ask hop's claim that the loosened basis is "painting the mislabelled `20`" — the label is intentional. The basis question it raised remains real, and with this table it decides whether the 20 row prints at all.

## 18. Every tick prints — decided 2026-09-14

The label-drop rule hid any row landing too close to the one above it, which is why the 15 and 20 kn rows were at risk and why the rule's own gap measurement needed defending. The decision is to remove the mechanism rather than keep tuning it: the table is the specification, so the scale draws exactly what it lists.

- **The rule and its helper are removed.** `labelledTicks()`, its minimum-gap floor and the `LEGEND_LABEL_MIN_HALF_HEIGHT` consumer of that floor all go; every row of `track.heatmap.scaleTicks` prints its own text at its own position, and overlapping labels are accepted rather than resolved.
- **The shipped table stands as written,** `7:5,12:10,15:20,30:30,35:35` — so 20 prints at the 15 kn row by intent, and the earlier "20:20" alternative and the arithmetic objection against this table are both withdrawn.
- **The 150 dp bar stays but is no longer load-bearing:** with no rule to satisfy, the height is a free readability knob rather than a requirement. §16's bullet that justified it by the dropped pair is superseded.
- **The end inset survives** — it exists only so the topmost and bottommost labels are drawn whole instead of clipped, and it is unaffected by the removal.
- **Findings this dissolves:** Ask finding 1 (the crowd rule's basis) and finding 2 (§14's label-drop pathology at large font scale), together with walk items 1 and 5 of the parent-13 child. **Findings it leaves:** the NaN position reaching a canvas offset, the untested twelve-row cap, the bar's top being the last parsed row's position, the mode token, and persistence.
- **Cost, accepted:** a crowded table — two rows within a label box — now draws overlapping text. That is legible failure rather than silent omission, which is the trade the decision makes.

## 19. No count key, and a scale foot — decided 2026-09-14

- **`track.heatmap.familyCount` is removed.** The ramp read walks the `familyN.*` indices from 1 upward to `HEATMAP_MAX_FAMILIES` and stops at the first index missing any of its four keys, so the file alone decides the ramp's length — and a truncated ramp still registers as truncated rather than passing as complete. The key and its comment are gone from `maro.properties`, and the family tests were re-pointed at the bound rather than at a count.
- **The scale has a foot:** `track.heatmap.scaleMinKn`, shipped as 2. The bar runs from it to the tick table's last position — 2 to 35 kn — so the fill maps over that span, the foot paints the 2 kn colour, and a row placed below the foot clamps onto it rather than leaving the bar. The top needs no twin, because the table's last row is the top; the foot has no row to ride on, which is why it is a key. An absent or unreadable key leaves the default standing, and a minimum at or above the top draws no bar at all.
- **Cost of the foot, accepted:** everything above stretches by about 6%, and the 0–2 kn stretch leaves the bar. Nothing is lost visually today, the first family being flat green to 5 kn and painting the same colour at 2 as at 0 — but a future ramp with a boundary inside 0–2 would show that boundary on the line and not on the bar.
- **Defaults mirrored, not endorsed:** `AppConfig`'s default ramp and tick table are re-cut to the file as it stood — seven families, the fifth and sixth both ending at 35 kn so the sixth is a zero-span hard edge producing only the jump to red, and a table whose third row prints 25 at the 23 kn position. Both were live-tuned; if either is a leftover rather than intent, the default moves with the file and these words go with it.
- **Residue unchanged:** `TrackSpeedHeatmapTest` still pins the v4 grid rather than the shipped one, so its band counts describe the fixture and not the file.

## Outcome

**Shipped 2026-09-14 on `feature/track-speed` through the `#implement` pipeline** — with the mode on, the selected track renders as a speed heatmap, the drawer header's eye toggle moves that mode for the session, and the legend sits at the map's top-left below the toggle row, borrowing the zone tiles' panel treatment.

- **Built:** `ui/map/TrackSpeedHeatmap.kt` (the ramp type with families as a list, plus `resolveSpeeds`, `colorAt` and `bandedAppearances` returning paired geometry), `ui/map/TrackSpeedLegend.kt`, `data/track/TrackSpeed.kt` (the shared nullable derive primitive), `ui/icons/Visibility.kt` (a standalone glyph rather than a new dependency) and `TrackSpeedHeatmapTest.kt`; changed `AppConfig`, `MapScreen`, `MapTrackOverlayEffects`, `MapTrackSegments`, `TrackDirectionOverlay`, `OverlayLayer`, `OverlayLayerParams`, `maro.properties` (sixteen keys at that point — ramp v2 takes them to twenty-two) and one sentence of `docs/color-scheme.md`.
- **Verified:** twelve scoped unit tests green, `apk-build.bat` SUCCESS; the Ask hop found no high-severity defect, all eleven §6 items present in code, the four targeted risks holding — one seam per gap, no double-draw at band edges, the title rule surviving the lift, the key list reacting to the mode — and the out-of-scope surfaces intact.
- **Deviations, all recorded:** a standalone visibility icon instead of a new dependency; `AppConfig` importing ramp types from `ui/map`, one direction with no cycle; the carry window measured from the last known speed so a same-timestamp lost fix qualifies; sixteen quantised stops rather than the fifteen §3a estimated; the accent resolving from the past-colour and transparency pair; legend ticks as muted hairlines with literal 10 sp labels; and the arrow resolver colouring anchors through a band index table.
- **Open follow-ups:** the second Ask pass's four open mediums are recorded in §11 — the ramp cluster's home in `data/track` against `config`, the shipped ramp existing untied three times, the legend's five ticks with its stale sketch, and the untested knee derivation — alongside five lows, and a device E2E that has not been run.
- **Ramp v2 shipped (2026-09-14, second `#implement` pass):** five families with the confirmed anchors, the knee read from the ramp's own second boundary, a half-knot grid below it and a knot above, and the flat compliant zone collapsing to one band; the ramp surface — mode, family, ramp, steps and the family cap — moved into `data/track` beside the derive primitive, and the family assembly became the pure `parseHeatmapFamilies()` that `AppConfig` calls. Twenty scoped tests green, `apk-build.bat` SUCCESS, no git writes. The review that followed judged v2 correct and faithful to §10 and §3b, correcting instead the plan's own §4 bound, its "hue jump" claim and one §6 omission.
- **Ramp v5 and a declared scale shipped (2026-09-14, third `#implement` pass):** six families at 5/7/12/15/35/70, each on its own draw step; `track.heatmap.scaleTicks` replacing `scaleMaxKn` as the one key that writes the scale, with a row's printed label free to differ from its position; no mark drawn inside the colour bar; and §18's removal of the label-drop rule, so every row prints and close rows overlap rather than vanishing. Forty scoped tests green, `apk-build.bat` SUCCESS, no git writes. §4's v4 band counts are superseded above, and one residue stands: `TrackSpeedHeatmapTest` still pins the v4 grid rather than the shipped one.
- **Mode token and persistence shipped with it:** `heatmap` is now accepted as the token with `speed` as an alias, and the drawer header's eye toggle stores its choice in `AppSettings.speedHeatmap`, which leaves `track.heatmap.mode` a first-run default only. The review then found that storing it bought nothing at cold start — the settings flow was seeded empty, so the first composition always read the file — and the fix hop seeds `NavigationViewModel.settings` from the snapshot the view-model had already loaded, which is what its own KDoc had always claimed.
- **Open after this pass:** the device E2E has still not been run, and the findings this pass parked rather than fixed are listed in §18 and in the walk's closed level-2 summary — the NaN position reaching a canvas offset, the untested twelve-row cap, the landscape inset diff, the `assumeTrue` fallback, and the bar's top being free file text.
