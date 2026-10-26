<!-- scope: feature -->
# Stage 7 — device-first routing gate

## Disposition: unverified, conditional implementation not entered
The user confirmed on 2026-10-26: **No device measurements available.** There is therefore no device evidence that coast reads dominate the current pull's coast, edge and price reads. No coast-distance field is implemented, and this stage is neither a performance pass nor a measured rejection of that field.

Base: upstream develop `b1f40aa211c02c2882bb676514bee753cecacebe`, containing merged upstream PR #382. The initial pre-edit ancestry/source inspection confirmed the shared probe and grown build, primitive heap/maps, canonical drawing and boundary splitting. Those changes were not implemented again. This branch changes only this record.

## Baseline and comparison
| Required evidence | Before | After |
|---|---|---|
| Device route/workload and build identity | Not supplied | No candidate build |
| Device pull time, coast/edge/price reads and costs | Unavailable | Not measured |
| Coast share including field preparation and memory | Unknown | No field |
| Exact route/chord/ranking differential | Unmodified upstream reference | Not applicable without a candidate |
| Baked-region and thermal/repeated-run evidence | Unavailable | Unverified |

`MultipassPull.kt` retains the existing PullTiming clearance/price clocks, price reads, mark counts and memo hit counters. Those counters are useful context, but the clearance total and memo hits alone do not establish the live coast-versus-edge cost split. No desktop synthetic result is substituted for the required device baseline.

## Validation and preserved reference
No executable source changed, so no new build/test run is attributed to this documentation-only stage. The baseline remains the unmodified reader for any eventual conservative-field differential. No bisection count, chord probe order, sampling lattice, route point, ranking, margin, source interpretation or price changed.

The prerequisite remains unmet. A future field would still need its lower-bound proof, unknown/out-of-grid fallback, complete geometry coverage and bit/exact-result differentials; absent device evidence does not justify choosing or enabling it.
