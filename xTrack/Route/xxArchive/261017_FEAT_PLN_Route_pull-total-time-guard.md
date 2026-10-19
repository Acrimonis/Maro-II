<!-- scope: feature -->
# Route — the pull's guard on total time, not the soft price alone

**Date:** 2026-10-17 · **Status:** implemented (its device pass open) · **Order:** *plan fix* after the 18:13 device pass — the line keeps a longer inward bend because the straightening chord is refused on soft price even though its total time is lower.

## Why

- The 18:13 sweep refuses every straightening chord on PRICE — e.g. `SWEEP keep idx=5 at=43.54543,7.13969 why=PRICE chordSec=1.333 pathSec=0.781` — so the line keeps a 45° bend it does not need.
- The chord there is ~3.6 m **shorter** (it cuts the corner), which at 5 kn saves ~1.4 s of travel, against a 0.55 s higher soft price: the chord is ~0.85 s **cheaper overall**, yet refused.
- The guard compares only the soft price: [`priceRefusal`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:924) tests `softPriceSec(chord) > pathPriceSec[probe] - pathPriceSec[anchor]`, and the same soft-only proof sits in the sweep and in `straightenLine`'s `chordLegal`. None of them reads `haversine / pace`, so a legal chord that trades a little standoff for a shorter line is thrown away.
- The straightener changed nothing for the same reason (`STRAIGHTEN points=14→14`, `12→12`): it reuses the guard.

## The change

1. **One total-seconds law.** A shared `totalSec(a, b, ctx) = haversine(a, b) / paceMps + softPriceSec(a, b, ctx)` — the travel the chord costs plus the water it pays for — is the number every proof compares.
2. **The pace reaches the walk.** `PullSetup` and `PullContext` gain `paceMps` (the pace the caller already holds), so the guard can turn distance into seconds; every construction site (the runner, the fine pass, `solveCrossing`, the fixtures) passes it.
3. **The price proof compares totals.** [`priceRefusal`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt:924) refuses only where `totalSec(chord)` exceeds the run's total — the prefix becomes a total-seconds prefix, not a soft-price one — and the sweep's two proofs and `chordLegal` read the same comparison.
4. **The wall is untouched.** `legClearCause` still refuses the chord that comes inside the 20 m margin; only the *price* half becomes total-aware.
5. **The shortcut contract tightens, not loosens.** A chord through slow water is now accepted exactly when it is **faster in total** — which is what the A\* already minimises on the grid — so the pull stops keeping detours that are longer and slower.

## Review — 2026-10-17

- **The pace is a new context value, not a re-derivation.** It must be threaded through every `PullSetup` site; a missed site is a compile error, so the change is self-checking.
- **The prefix changes name and meaning.** `pathPriceSec` (soft) becomes a total-seconds prefix; the memo and the price reads are unchanged, only the number summed beside them is.
- **The allowance still applies.** The sweep's bounded allowance rides the total comparison so a difference below the sampling precision does not pin a vertex.
- **The A\* is untouched.** It already minimises total time; this makes the pull's refinement agree with it instead of second-guessing it.

## Tests

- A legal chord that is shorter but dearer in soft price is now **accepted** (the reference case: `chordSec` higher, travel saving larger, total lower).
- A chord that is both longer and dearer is still **refused**.
- A chord that crosses the 20 m wall is still refused by `legClearCause`, whatever its total.
- The sweep and `straightenLine` drop the reference corner once the guard is total-aware.

## Implemented — 2026-10-17

- **The law** — `totalSec(a, b, ctx)` in [`MultipassPull.kt`](../../app/src/main/java/ykws/android/maro/spatial/multipass/MultipassPull.kt): `haversine / paceMps` plus `softPriceSec`, and `totalPrefix` beside `softPricePrefix`.
- **The pace** — `paceMps` on `PullSetup` and `PullContext`, threaded from the runner and both fine-pass sites through `Units.knotsToMps(pace)`; a fixture that omits it (`0.0`) reads soft price only, unchanged.
- **The proofs** — `priceRefusal`, the sweep's triple and window proofs, and `straightenLine`'s `chordLegal` all compare total seconds now; the wall (`legClearCause`) is untouched.
- **Tests** — `totalSec` reads the travel when the water is free; three passes refused by the chord remain refused whatever the guard. Build: 1318 tests, 1 pre-existing failure (`PullAllowancePropertiesTest`), 12 skipped; `assembleDebug` green.

## Open

- Whether the A\* itself should price continuous chords (out of scope — the pull's refinement is the home of this fix).
- The device pass: does the inward bend disappear, and does the line shorten without losing its standoff?

## Outcome

Shipped as planned: every chord proof in the pull — the price refusal, the sweep's triple and window, and the straightener's `chordLegal` — compares total seconds (travel at the pace plus soft price) instead of soft price alone. Its rule and open points are promoted to `FEAT_DSC_Route.md` ## Rules and `FEAT_DOC_Route_decisions.md` §8; the device pass is owed there.
