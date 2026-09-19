# Context Hydration — Route — 2026-09-19

**Last Bake:** 2026-09-19 22:38 UTC — written by `#bake`; absence means never baked

**Directive trace:** no covered action stopped while this feature was designed — no dependency was added without the ask (JTS and poly2tri were asked for and approved), no machine-shaped data file was opened, no work began without an explicit order, the device was never touched, and every claim about the code came from a file read. One error of mine is on the record: an asset path rewritten from a misread listing and reverted in the same session.

## State

**In design — nothing implemented, and no source file touched.** The epic went from the retired subfeature shape to a settled design, was baked and committed as `f3dc7cc`, and then took a full review: six gaps closed by decision, the one hole — arrival — settled by the user, and the search's heuristic confirmed admissible. The plan was consolidated the same evening into a register at `xTrack/Route/260919_FEAT_PLN_Route_registration-and-drift.md`, which is where the why lives; the epic carries the what.

- Two walk levels closed on 2026-09-19 with nothing dropped and nothing parked: eleven challenge items, then thirteen pending items.
- The design's spine: a packed-array mesh under javalite carrying geometry and connectivity only; limits resolved per search from the zone layer; water below 2.5 m closed to routing; the 300 m band's shape baked with its value live; the route placed in the app's own tree; a route toggle in the control stack; the trip figure in the dashboard's distance-to-shore cell.
- **The review's additions, all in the epic's rules:** the boat leaving the covered water keeps the line with its recompute skipped and the trip figure marked stale; the route and inspect modes are mutually exclusive; the pin is drawn at the resolved destination rather than the raw aim; the line's appearance lives in `maro.properties` with no Settings row yet; a saved track takes the Tracks feature's standard auto-name; arrival carries no state and no cue; and A*'s heuristic is admissible by construction.
- **Six prerequisites wait on other features or the build**, none of them Route's work: the units fold into one shared file, the band's value and the zone-to-effective-limit mapping gaining one home each in RegulatedZones, the dashboard cell swap in Ui_Dashboard, and the two approved test-only libraries entering the version catalogue.
- **Two conditions return as questions rather than decisions:** if the prebake benchmark cannot hold the ≤ 500 ms target at the chosen refinement, or the pace sampling proves too thin to trust.
- **Open and untouched:** whether to recover the discarded commit `a1ca620`, whose hydration and gradle.properties hunk sit in the reflog.

## Target Files

- `xTrack/Route/FEAT_DSC_Route.md` — the epic: four sections, the rules carrying every decision, and the two closed walks
- `xTrack/Route/260919_FEAT_PLN_Route_registration-and-drift.md` — the consolidated design, its register and the full review's findings
- `xTrack/GLOBAL_CONTEXT.md` — Route's routing row, its summary row and the focus entry
- Nothing under `app/` — no source, build or asset file has been changed

## Next Step

The units fold and the RegulatedZones prerequisites, both of which precede any Route code, then the implementation sequence in the plan's §7 — and the two conditions above are what would bring the design back for a decision.
