<!-- scope: feature -->
# Pinned-track cue — a casing as the pinned marker

**Date:** 2026-09-14 · **Status:** in design — no decision taken; resumed from the parked walk item 3 and the note in `260914_FEAT_PLN_Tracks_render-modes.md` §7
**Request:** tell a pinned track from an unpinned one on the map while the ramp owns the interior colour, by giving tracks a thin coloured rim — the pinned colour pair for pinned tracks, the recency pair for the rest.

## 1. What exists today (measured, not assumed)

- **Colours mode spends the interior on speed.** `trackRenderPlan()` puts every stored track on the banded path, and D2 of the render-modes plan accepted that pinned tracks lose the amber gradient — so in that mode nothing on the map says which tracks are pinned.
- **The casing is an established shape.** The selected track draws a 16f dark casing under its 8f core (`SELECTED_CASING`), one polyline per segment, sharing the track's title so prefix teardown and the exact-title z-lift both keep working.
- **A rim is a second polyline, not a stroke outline.** An osmdroid `Polyline` carries one `outlinePaint`, so widening one stroke cannot produce a rim; the banded builder already proves the per-band pattern, and `buildSegmentOverlays(points, appearance, title)` is the plain-path call a rim would reuse.
- **Cost, measured:** one extra polyline per track *segment* — ~+100 draw calls at 25 stored tracks, against today's ~100 in Simple mode and 400–1000 in Colours — plus one further copy of every point of every track as `GeoPoint`s, around 50 000 objects at 25 × 2 000 points, which is the same order the rebuild already pays once.
- **The rim must be dashed across GAP seams**, or it paints a solid coloured line where the core draws a dashed break.
- **Widths today:** 8f for the newest history track, 6f for every other and for pinned, the selected track 8f under its 16f casing (D11). Pixels, not dp — at 3× density a 2 px rim is about 0.67 dp and would read as a hairline, so the rim needs a dp figure.
- **The colours it would use already exist as settings:** `trackingColorPastFrom`/`To` for unpinned, `trackingColorPinnedFrom`/`To` for pinned, both applied today by `computeTrackPolylineAppearance` by recency index.

## 2. What the rim buys that nothing else does

- **It restores the age cue the ramp displaced.** In Colours mode the interior no longer carries the recency gradient, so a rim drawn in the track's graded default colour brings that reading back on the outside of the line — which makes the rim informative for unpinned tracks too, not decoration.
- **It restores the pinned cue** in the one mode that gave it up, and it does so in the same stroke for every track, so the map keeps a single language: outside = identity, inside = speed.
- **It survives selection.** A selected pinned track keeps its dark 16f casing, so the rim sits inside it and the selection still reads as the widest, darkest stroke.

## 3. The design forks

- **F1 — scope of the rim.** Every stored track, or only pinned ones. Everything favours the wide scope for coherence after §2, and everything favours the narrow one for cost: a rim on pinned tracks alone is a handful of polylines, since pinned tracks are few.
- **F2 — colour.** The track's graded default value (age preserved, pinned hue for pinned tracks) or one flat token per category (category only, no age). The graded form is the one that pays for itself per §2; the flat form is the one that reads unambiguously as "pinned".
- **F3 — modes.** Colours mode only, or all three. A same-colour rim over an interior that already carries that colour states nothing, so Simple and Dir & Speed gain nothing but draw cost.
- **F4 — width and fade.** A dp width — 1.5–2 dp reads at 3× where 2 px does not — and the rim should take the same fade as the interior (D8), since an opaque rim on a heavily faded track would read as a newer track than it is.
- **F5 — draw order.** Rim first, interior over it, per track, sharing the track's title; the selected track's casing stays outermost.

## 4. The alternatives, measured against the same goal

- **A1 — a pin glyph per pinned track.** A small marker at the track's first or last point: no extra polyline, no geometry copy, and it reads as a category rather than as age. It answers "which are pinned" more cheaply than any rim, and it does nothing for the age cue.
- **A2 — a pinned-only fade.** Zero geometry cost, but it reads as age rather than as category, and the pinned fade is already a user setting that means transparency, not identity.
- **A3 — a wider stroke for pinned tracks.** Collides with the recency width rule D11 settled, and width is the selection cue's own axis.
- **A4 — dashing pinned tracks.** Collides with the GAP seam language, which already uses dashes for discontinuity.

## 5. Decided shape (2026-09-14)

- **F1 — pinned tracks only, to start.** The rim ships on pinned tracks alone, so the cost is a handful of polylines rather than one more copy of every rendered point, and the pinned cue is restored in the one mode that gave it up. The wide scope stays open later, since it would add one more appearance at the same call site rather than a new mechanism.
- **F2 — the pinned track's own default colour.** The rim takes the colour the track would carry on the plain path: `trackingColorPinnedFrom`/`To` interpolated by recency index across the pinned list, which is what "the default colour of the pinned track" means today. The flat-token alternative is kept in §6.
- **F3 — Colours mode only.** In Simple and Dir & Speed the interior already carries that colour, so a same-colour rim would state nothing while still being drawn.
- **F4 — 1.5–2 dp, taking the interior's fade (decided 2026-09-14).** One transparency rule governs the whole track, so a heavily faded pinned track loses rim and interior together; the exemption and the fixed-alpha token were dropped with the choice.
- **F5 — under the interior**, dashed across GAP seams, sharing the track's title so prefix teardown and the selected track's z-lift keep working. A selected pinned track then shows the dark casing outside an amber rim outside the bands — three concentric strokes, which is a device-look item rather than a design risk.
- **Objection to this shape:** the rim is the only pinned cue in Colours mode, so a heavily faded pinned track can read as an ordinary faint one, and the cue is weakest exactly where the map is busiest.

## 6. Open questions

- Whether the graded pinned pair is right, or one flat pinned token: the pair states age as well as identity, the token states identity alone.
- Whether the pinned-only scope should widen later to every stored track, which would restore the recency gradient the ramp displaced at the cost of a geometry copy per point.
- Whether the rim is worth its polylines at low zoom, where 1.5 dp compresses against the stroke until it reads as a slightly thicker line.
- How the rim sits with the parked walk item 3, whose "casing carries both cues" reading this realises for pinned tracks while the selection half stays parked.

## 7. Surfaced while discussing (2026-09-14)

- **The ramp file's comments lag its values by one family.** Family 3's comment reads "7 to 12" against `maxKn=10`, family 4 reads "12 to 15" against `maxKn=14`, families 5 and 6 both end at 35 so the sixth is a zero-span hard edge, and family 7's comment describes the 35–70 range that only it occupies. Reading the file's prose therefore suggests a scale that stops earlier than its values do.
- **The legend cannot show the whole ramp.** The bar runs from `track.heatmap.scaleMinKn` (2) to the tick table's last position (35), so the red-to-purple family from 35 to 70 never appears on the legend, and `scaleTicks` holds no row between `12:10` and `30:30`, leaving the orange-to-red middle of the bar unlabelled.
- **The shipped table and the plan disagree.** `260914_FEAT_PLN_Tracks_selected-track-speed-heatmap.md` §16 and §18 describe the five-row table `7:5,12:10,15:20,30:30,35:35` as the shipped contract while `maro.properties` holds four rows, so the contract and the file need reconciling once, and the three `HeatmapRampPropertiesTest` reds sit in that same gap.
- **Not answerable here:** whether the recorded tracks simply never exceed the blue families — the track files are protobuf binaries and off limits to read, so that question goes to the app's own max-speed figures.

## Outcome

[Appended if it ships: which forks were taken, the measured cost against these estimates, and what it did to the parked cue item.]
