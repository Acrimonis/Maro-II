<!-- scope: feature -->
# Speed ramp — the step grid re-cut for no jitter and high-speed smoothness

## 1. Decision

The nine families keep their ranges and their colours; their `stepKn` values are re-cut, the family bound rises
from eight to nine so the ninth is read at all, and the code's default ramp and the properties test are reconciled
with the shipped grid.

| Family | Range (kn) | Was `stepKn` | Now | Shades painted |
|--------|-----------|--------------|-----|----------------|
| 1 | 0–4 | 0.5 | 1.0 | 1 — flat, so the step is inert |
| 2 | 4–6 | 0.25 | 1.0 | 2 |
| 3 | 6–7 | 0.25 | 1.0 | 1 |
| 4 | 7–9 | 0.5 | 1.0 | 1 — flat, so the step is inert |
| 5 | 9–12 | 0.5 | 1.0 | 3 |
| 6 | 12–13 | 1.0 | 1.0 | 1 |
| 7 | 13–22 | 2.0 | 1.0 | 9 |
| 8 | 22–32 | 3.0 | 1.0 | 10 |
| 9 | 32–70 | 5.0 | 2.0 | 19 |

A family's first rung is unreachable from above, since the quantiser never snaps down onto its own lower boundary,
so a family's painted shades are its span divided by its step, excluding the boundary rung.

## 2. Why this cut

- Below about 13 kn the lattice is made coarse: there the speed signal is smaller than the noise carried on it, so a
  shade flip has to be earned by a real change rather than produced by jitter.
- Above 13 kn it is made fine: speed variation there is genuine, and one rung's colour change is small enough that a
  flip would not be seen even if the noise caused one.
- The numbers: 0–13 kn drops from 19 shades to 7, 13–70 kn rises from 17 to 38, and the ramp goes from about 36
  shades to about 47 — the increase lands entirely where the plateaus were visible.
- The assumed noise is roughly ±0.3 kn on a stored speed fix and more on a derived one. Nothing in the record
  measures it, so the low-range steps are sized around that figure and a device reading would settle it.
- Rejected: hysteresis at a rung edge. The merge rule is the colour rule, and damping it would break the
  nearest-point colour the render depends on.

## 3. The prerequisite defect this exposes

`HEATMAP_MAX_FAMILIES` is 8 in `config/HeatmapRamp.kt` while `maro.properties` declares nine families, so the ninth
is never parsed: everything above 32 kn paints family 8's top colour, the ramp's own span stops at 32 kn while the
legend's bar runs to its last tick at 35, and the block header beside the keys still says "up to eight families".
The same drift leaves `AppConfig.trackHeatmapRamp` and `HeatmapRampPropertiesTest` describing the older
eight-family grid with the older colours — which is why that test stands among the six known reds. Without the
bound, any step written for family 9 is inert.

## 4. Edits

- `app/src/main/assets/maro.properties` — the nine `stepKn` values; the header's "up to eight families" clause;
  family 1's comment, whose "flat green up to the 5 kn limit" sits beside `maxKn=4`.
- `config/HeatmapRamp.kt` — `HEATMAP_MAX_FAMILIES` 8 → 9, and its KDoc's "six, seven or eight" clause.
- `config/AppConfig.kt` — the default ramp mirrors the shipped nine families with the new steps; its scale-ticks and
  scale-foot defaults are checked against the file's keys and aligned if they differ.
- `app/src/test/java/ykws/android/maro/config/HeatmapRampPropertiesTest.kt` — the expectations rewritten to the
  shipped grid: nine families, the new ceilings, the new steps and the shipped colours.
- `xTrack/Tracks/FEAT_DSC_Tracks.md` — the device pass as a todo, and the `## Implemented` pointer at the close.

## 5. Verification

- `apk-build.bat` clean, no new warnings.
- The scoped `ui.map` + `config` run: the two heatmap reds turn green, taking the known set from six to four, with
  nothing else moving.
- Device pass, owed: the 4–13 kn range on a jittering fix, and the 13–40 kn gradation on a fast run.

## 6. Not in this pass

- No colour, boundary or legend-tick change.
- The 9–12 kn band keeps three shades; if smoothness there matters more than jitter, its step returning to 0.5 is
  the one-word alternative.

## Outcome

Shipped 2026-09-19 through the `#implement` pipeline, on `feature/someMui`. The nine families keep their ranges and
their colours; what moved is their draw steps — 1.0 across families 1 to 8 and 2.0 for family 9 — together with the
bound that had been hiding the ninth.

`HEATMAP_MAX_FAMILIES` rose from 8 to 9, which is the defect this pass exposed rather than planned: the file had
outgrown the read, so family 9 was never parsed, everything above 32 kn painted family 8's saturated top, the
ramp's own span stopped at 32 while the legend's bar ran to its last tick at 35, and the block header beside the
keys still said "up to eight families". `AppConfig`'s default ramp — the eight-family grid the parser falls back to
— was mirrored onto the shipped nine, and both heatmap test classes were reconciled with the file: the properties
test rewritten onto the shipped ceilings, steps and colours, and `TrackSpeedHeatmapTest`'s cap contract moved from
family 8 to family 9.

`apk-build.bat` SUCCESSFUL with no warnings. The scoped `ui.map` + `config` run came to 213 tests with four
failures — the pre-existing marker-flash and track-outline drift alone — so the two heatmap reds closed and no new
one appeared.

Deviations: none. Open: the device pass over the ramp's jitter and its high-speed gradation; the two wording drifts
the review found in the region this pass touched (`HeatmapFamily`'s KDoc still citing a 15 kn hard edge the grid no
longer has, and family 1's rewritten comment sitting wider than the block's wrap); and the legend-and-ramp gap,
which this pass enlarges rather than creates — the bar covers 2 to 35 kn while the ramp now really paints to 70,
already parked as a subject of its own in `260914_FEAT_PLN_Tracks_pinned-cue-casing.md` §5.
