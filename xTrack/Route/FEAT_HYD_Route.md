# Context Hydration — Route — 2026-10-28

**Last Bake:** 2026-10-28 17:45 UTC — written by `#bake` on `feature/roooouuutttiiinnngggg`.

**Directive trace:** no dependency was added and no binary file opened; the device was read only after the user's word that a build was deployed and run (two logcat dumps, 18:16 and 18:36), never written; no commit or push was raised unasked; none of the covered action classes stopped.

## State

Committed up to `d8e51fe6` (the bare-bends S fix). Uncommitted at this bake: the leftovers — `route.turn.transitionSec` and `route.speed.accelMps2`'s code defaults follow `maro.properties`, their comments rewritten, `TurnConfigFixture.withTurn` pinning three fixtures at 0.33 m/s² and 2 s, the slide, split and leg searches members of `Search` — and the lone-turns P0: `RouteApproachPass` counts the lone price turns left on its line (`loneTurns=`) and names each with its slack (`APPROACH lone … slackM= toward= travelSec=`), moving no line. The device's first instance: "Around"'s 43° turn at 43.53999,7.10292, 1 483 m of slack along its incoming leg, 9.4 s. 1 666 tests green; the harness's lines identical on both pairs.

## Target Files

- `app/src/main/java/ykws/android/maro/spatial/multipass/RouteApproachPass.kt` — the lone-turn count after the passes, `slackToward`, `Search.searchAlongLegs`
- `app/src/test/java/ykws/android/maro/spatial/multipass/RouteApproachPassTest.kt` — `aLoneTurnIsCountedAndTheLineKept`
- `app/src/test/java/ykws/android/maro/spatial/multipass/TurnConfigFixture.kt` — `withTurn`
- `xTrack/Route/261028_FEAT_PLN_Route_lone-turns.md` — §3 as built, §10–§11, `## Implemented`

## Next Step

P1 of the lone-turns plan on the user's recommendations (D1 band and zone, D2 one-dimensional along the legs, D3 evidence and the harness budget, D4 the approach pass).
