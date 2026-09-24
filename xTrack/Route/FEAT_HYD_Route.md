# Context Hydration — Route — 2026-09-24

**Last Bake:** 2026-09-24 20:49 UTC — written by `#bake`

**Directive trace:** no dependency added, no machine-shaped data file opened, no device touched and nothing deployed; every read was of the corpus or of the files the plan owns. The work was ordered throughout — each of the eight points was settled on the user's own word, the walk was opened by the invoked `#walk` and closed by exhaustion, and no git write ran in this session.

## State

The route mode's workflow is **designed and implementation-ready; nothing is built.** The brief replaces the app's guesses with two explicit phases: **acquisition**, where an `Acquire route` button asks the search and a `Confirm` stays disabled until a plan stands, and **route**, where `Save track`, `Reroute`, `New route` and `Exit` are the whole action set. The timer that fired a search 300 ms after a drag and the one-second clock that re-asked while following both leave, so the line never changes under the user; the anchor is re-read on every entry into acquisition from the boat's own position, led a flat ten seconds by `route.anchor.leadSec` and falling back to the live fix where that prediction is not water. The eight points were settled in an eight-item walk closed by exhaustion, and the plan carries the ten-step landing order, the review's nine findings with their dispositions, and the thirteen new string keys in both locales. The duplicate-save todo is left standing to be resolved after the implementation, and the all-scope save is withdrawn with its return parked as its own todo.

## Target Files

- `xTrack/Route/260924_FEAT_PLN_Route_acquisition-and-route-workflow.md` — the plan of record, in design
- `app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt` — the button-driven ask, the per-acquisition anchor, the phase-aware exit
- `app/src/main/java/ykws/android/maro/ui/map/RouteConfirmPanel.kt` · `RouteHost.kt` — the action matrix, the comment line, the deleted clock
- `app/src/main/java/ykws/android/maro/ui/components/ConfirmActionButton.kt` — the `enabled` member and the disabled face
- `app/src/main/java/ykws/android/maro/spatial/RouteEngine.kt` — the acquisition-stage channel
- `xTrack/Route/FEAT_DSC_Route.md` — the walk, closed with its resolutions
- `xTrack/Route/260922_FEAT_PLN_Route_ask-policy-and-target-validity.md` — the master book whose R2, R3 and R10–R17, R22 and R23 this pass rewrites

## Next Step

The landing order's ten steps, starting with the master book's rows; the two standing todos wait until after the implementation. One bake duty is owed to the Code hop: the Route row of `## Feature Summaries` is a line no tool in this mode can read back whole, so it is patched with `findstr` there rather than here.
