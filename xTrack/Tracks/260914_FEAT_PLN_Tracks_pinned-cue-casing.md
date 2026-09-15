<!-- scope: feature -->
# Pinned-track cue — a casing as the pinned marker

**Date:** 2026-09-14 · **Status:** split on 2026-09-15 — the dark casing half shipped alone on `feature/no-black-casing`, and the rim is parked below with the finding that stopped it
**Request:** tell a pinned track from an unpinned one on the map while the ramp owns the interior colour, by giving tracks a thin coloured rim — the pinned colour pair for pinned tracks, the recency pair for the rest. The casing half of the same question shipped first, by itself.

## Requirements, as they now stand

- **No dark stroke.** The selected track is never drawn with a black casing; the 16f stroke is deleted and nothing takes its place.
- **The selection is width, stacking and the drawer.** The selected track renders above every other overlay and is 12 px against the newest track's 10, the pinned tracks' 8 and the rest's 6 — all five widths read from `maro.properties` in the three modes — and it is gold wherever the ramp is not painting.
- **The interior carries speed in Colours**, and the outside carries identity only if a rim is built, which is exactly what is parked below.
- **Transparency governs the interior.** A track's transparency setting describes the stroke the user sees; a second stroke cannot hold an independent transparency of its own while it overlaps the first.

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
- **F5 — under the interior**, dashed across GAP seams, sharing the track's title so prefix teardown and the selected track's z-lift keep working. A selected pinned track would show an amber rim under the bands, the dark casing this reading once assumed having been removed on 2026-09-15.
- **Objection to this shape:** the rim is the only pinned cue in Colours mode, so a heavily faded pinned track can read as an ordinary faint one, and the cue is weakest exactly where the map is busiest.

## 6. Open questions

- Whether the graded pinned pair is right, or one flat pinned token: the pair states age as well as identity, the token states identity alone.
- Whether the pinned-only scope should widen later to every stored track, which would restore the recency gradient the ramp displaced at the cost of a geometry copy per point.
- Whether the rim is worth its polylines at low zoom, where 1.5 dp compresses against the stroke until it reads as a slightly thicker line.
- How the rim sits with the parked walk item 3, whose "casing carries both cues" reading this realises for pinned tracks while the selection half stays parked.

## 7. Surfaced while discussing (2026-09-14)

- **The ramp file's comments lagged its values by one family** — corrected in the comment pass of 2026-09-15, which rewrote every label from the hexes and boundaries actually in the file; the file now holds eight families, 5 / 7 / 10 / 13 / 15 / 25 / 32 / 70 kn.
- **The legend cannot show the whole ramp.** The bar runs from `track.heatmap.scaleMinKn` (2) to the tick table's last position (35), so the red-to-purple family from 35 to 70 never appears on the legend, and `scaleTicks` holds no row between `12:10` and `30:30`, leaving the orange-to-red middle of the bar unlabelled.
- **The shipped table and the plan disagree.** `260914_FEAT_PLN_Tracks_selected-track-speed-heatmap.md` §16 and §18 describe the five-row table `7:5,12:10,15:20,30:30,35:35` as the shipped contract while `maro.properties` holds four rows, so the contract and the file need reconciling once, and the three `HeatmapRampPropertiesTest` reds sit in that same gap.
- **Not answerable here:** whether the recorded tracks simply never exceed the blue families — the track files are protobuf binaries and off limits to read, so that question goes to the app's own max-speed figures.

## Parked — why the rim did not ship

- **The finding that stopped it (2026-09-15, on the device).** The rim is a *wider stroke drawn under the interior*, so the two alphas composite across the interior's whole width rather than only outside it: with the shipped defaults a class fade of 0.8 gave the rim a clamped 1.0 and made the newest track fully opaque with its transparency slider inert, a fade of 0.5 composited to 0.85 and a fade of 0.2 to 0.52 — the interior's own setting stopped describing what the user sees.
- **A boost of 0 does not repair it.** Two strokes at the same alpha still composite to `a(2 − a)`, so a translucent track stays denser than its setting; the boost only decides how much worse than that it gets.
- **The three shapes that would repair it, and their costs.** *Offset parallel lines* — the border as two polylines shifted perpendicular by half the core plus half the border, so it sits beside the core and each stroke keeps its own alpha; it costs a normal and a mitre per point, a recalculation on every zoom, and care at hairpins. *A geometric ring* — stroke wide, subtract the inner band, fill the ring at one alpha: the only shape where both transparencies mean what they say, at the price of a custom overlay re-implementing what osmdroid's polylines do today, with per-frame path work on a map that already redraws up to twenty-five tracks. *A ring only where the interior is opaque* — no new machinery, but the cue survives only on the selected track and is dropped from every translucent one, which was the rim's purpose.
- **What shipped instead of it, the same afternoon:** the per-type width table — `track.width.live=12`, `.selected=12`, `.newest=10`, `.pinned=8`, `.history=6`, ported from `2728c78` with the rim left behind — so the selection keeps a width separation in place of the casing it lost.

## Outcome

**Shipped 2026-09-15 on `feature/no-black-casing`**, branched from `b2f6d69`: the dark casing is deleted — `SELECTED_CASING` and its uses on the gold and banded paths, the chevron casing in `TrackDirectionOverlay` with its stroke width and draw block, the `arrowCasingAppearance` field, and the banded path's now-unread `selected` flag — with `apk-build.bat` SUCCESS and the three pre-existing `HeatmapRampPropertiesTest` reds untouched.

- **The widths followed the same afternoon**, ported from `2728c78` with every rim piece left behind: five `track.width.*` keys, their `AppConfig` accessors, a per-type width resolver replacing the four constants, the gold core's width from `track.width.selected`, the live line's nine hardcoded widths on `track.width.live`, the newest track derived from recency rather than the loop's index, and the five values in the rebuild key list — `apk-build.bat` SUCCESS and the scoped track tests green but for the same three reds.
- **What that buys:** the selected track is 12 px against the newest track's 10, so the separation the casing carried survives in width alone, which was the open item the removal left.
- **Not shipped, parked above:** the rim, its width factor and its alpha boost, with the finding that stopped them and the three shapes that would repair it.
- **Still open:** the rim's legibility at the thin end, whether it survives contact with markers, and the file-versus-default drift behind the three ramp reds.
