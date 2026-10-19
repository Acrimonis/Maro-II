# Context Hydration — Ui_Settings — 2026-10-19

**Last Bake:** 2026-10-19 06:35 UTC — written by `#bake`
**Branch:** feature/props — cut from `origin/develop` this session for the `maro.properties` comment pass

**Directive trace:** no covered action stopped. No dependency was added, no machine-shaped data file was opened, no device was touched, and no build ran — the change is comments only. Every tldr was written from the comment it sits under, never from a guess; the `path.arrow.*` tuning keys, whose meaning no comment states, got none. No git write ran before the `#commit` that closes the session.

## State

**`maro.properties` speaks plainly where it was technical.** 61 `# tldr;` lines stand as the last comment line above their key, each saying in plain words what the value changes for the user — GPS cadence, the routing engines (turns, grids, margins, the three-route ladder, the pre-warm), the recording speed gates, automatic stop pins, inspect mode and the screen-hold staleness. Lines over 100 characters wrap onto `#`-led continuation lines aligned under the text. No key or value moved.

**Open and recorded.** Six comment/value contradictions were found and left unfixed, as unasked: `route.speed.accelMps2` (comment says 1.0 shipped, value 0.666667), `route.turn.lateralAccelMps2` (0.33 and a 28 kn pace in the comment, 0.666667 and 25 kn in the file), `route.turn.transitionSec` (2.5 s vs 3.333333), `route.avoid.speedZone.outsideMarginM` (100 vs 150), `route.follow.arrival.etaSec` (comment says minutes, key is seconds) and the blank-line runs in `route.avoid.*`. The `path.arrow.*` tuning keys still carry no comment of their own, so no tldr.

## Target Files

- `app/src/main/assets/maro.properties` — the 61 tldr lines

## Next Step

Nothing is owed by this pass; the contradictions above wait on the user's word.
