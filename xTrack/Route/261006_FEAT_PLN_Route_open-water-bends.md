<!-- scope: feature -->
# Route — the bends the pull leaves in open water

**Date:** 2026-10-06 · **Status:** **closed by decision** — the diagnosis is complete, one fix landed, and the
rest is **accepted as a known defect** on the user's word of 2026-10-06: *we will stick with the first fix and
accept this current bug.*

## What was found

- **The step walls are real water, not artefacts** — every one read **Litto3D at confidence 90, 2.2 to 2.9 m**,
  the finest source the app holds, so the EMODnet filter was never their subject.
- **The coast walls are one real shore** — every distance refusal named the same **mainland OSM segment**
  (`osm:4212554`) with readings of 41 to 49 m against the shipped 50 m margin, and each printed shore point
  agrees with its own measurement.
- **And the long off-axis leg is the search's tie-break over uniform water** — every chain cell reads
  `FREE limit=0.0kn`, the straight alternative reads `price=0.00s`, and every chord the pull takes from the
  start is `clear` (`0→1 65.1m` … `0→6 772.3m`). The chain's head runs along the lattice's own axes — a 45°
  diagonal across eight 100 m cells on one pair, six cells due east then a diagonal on the other — so the pull
  faithfully tautens that staircase into one long leg at the staircase's bearing and only then turns.
- **The fix for that leg, recorded and not taken**: break the search's ties **toward the aim** — expand the
  smallest remaining-distance neighbour first, or add a small straightness term. Not taken because with eight
  neighbours **every monotone path is genuinely optimal** there, so the change would move the line on every rung
  and every pair rather than repair an error.

## The fix that landed — the one kept

- **The routing's depth read passes the chart's own EMODnet shallow filter, at the setting's value** —
  `emodnetShallowCutoffM`, default 2.0 m, a 0 to 5 slider; `LiveMultipassWorld.depthAt` returns the gated
  sample, the provider is threaded from `MapScreen`, and `RouteEmodnetShallowGateTest` pins both halves: a
  shallow EMODnet cell no longer walls a chord, a shallow **fine-source** cell still does.
- **Its own limit, stated rather than implied** — the surviving step walls read 2.2 to 2.9 m, **above** the
  shipped 2.0 default, so the win depends on the cutoff's value; and it touches **no** coast refusal.

## Accepted, and not to be re-opened without a new word

- **The off-axis first leg** — the search's tie-break on uniform water, as described above.
- **The shore margin refusing chords at 41 to 49 m** — a 50 m margin against a real mainland shore, strict but
  true; loosening it is a policy decision, not a defect repair.
- **The instruments that found all this were removed on the same word**, so these figures live in
  [`route-phase9.txt`](../../route-phase9.txt:1) through [`route-phase13.txt`](../../route-phase13.txt:1) at the
  repo root and in this record, never in the code.

## What would re-open it

- **A new device reading on a pair that shows the leg, printing the search's own cell costs** — the one figure
  never printed and the only one that would distinguish a tie-break from a cost.
- **The user's word on a value**, since every remaining lever is a setting: the shore margin, the gate's depth,
  the EMODnet cutoff, or a tie-break in the search.
