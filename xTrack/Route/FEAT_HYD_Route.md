# Context Hydration — Route — 2026-10-29

**Last Bake:** 2026-10-29 08:20 UTC — written by `#bake` on `feature/roouttinggg`.

**Directive trace:** no dependency was added and no binary file opened; the device was read once, on the user's word that the passes had run (the 08:38 logcat dump), never written; no source file moved — the session was design only, one plan written, reviewed by the `ask` agent and folded; none of the covered action classes stopped.

## State

`feature/roouttinggg` was cut from `origin/develop` at the merge of PR #397 (lone-turns P1); the tree holds this bake and the new plan only. The device pass of 2026-10-29 08:38 ("Closest", 24 kn) showed a 43° bend at 43.56306,7.15856 between the 10 kn zone's collar and Pointe du Bacon's band that no one-point slide reaches; the user ended the patch series. The any-angle coarse search is designed, reviewed once (three blockers in the chord's gate reading, folded), its decisions taken (grid price, lazy check, no 16 headings) and every post-search stage's fate assessed (§11): ready for `#implement` from P0. The slack probe stays.

## Target Files

- `xTrack/Route/261029_FEAT_PLN_Route_any-angle-search.md` — the plan: §4 design, §5 phases, §6 guards, §7 decisions, §10 review, §11 the stages after the search
- `app/src/main/java/ykws/android/maro/spatial/multipass/MultipassSearch.kt` — `walkOnce`, `relax`, `forEachStep`, `StepGuard` (P2's seat)
- `app/src/main/java/ykws/android/maro/spatial/multipass/WalkLattice.kt` — `WalkWindows` (P1's `chordCostSec` seat)
- `app/src/main/java/ykws/android/maro/spatial/multipass/RoutePassRunner.kt` — `searchFromCanonicalEnd`, the chain handed to the pull
- `app/src/main/assets/maro.properties` — `route.search.anyAngle` to add (ONE HOME)

## Next Step

`#implement` P0 — the harness baseline on the three device pairs and the 24-pair survey, a `SEARCH ms` clock added — then P1's chord read behind its cost gate; no search code moves before that gate reads.
