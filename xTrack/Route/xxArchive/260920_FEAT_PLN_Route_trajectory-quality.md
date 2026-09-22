# Route — trajectory quality: a study, four rounds

> **Digest floor — superseded 2026-09-22.** Every mechanism this study designed — the sampled string-pull,
> the turn penalty, the fillet, the zone berth, the bridging gate — belonged to the engine that was
> **removed with its code**. Its rounds, its acceptance numbers and its tripwires are carried with their
> provenance into [`260922_FEAT_DOC_Route_mesh-engine.md`](../260922_FEAT_DOC_Route_mesh-engine.md), and the
> removal is [`260922_FEAT_PLN_Route_dummy-engine-and-engine-removal.md`](../260922_FEAT_PLN_Route_dummy-engine-and-engine-removal.md).
> Nothing in this file is pending, and the paths it backticks name files the tree no longer carries.

**Date:** 2026-09-20 · **Branch:** `feature/route` · **Status:** built — §15's P0, P3 and P2-with-fallback shipped on 2026-09-20 (§18), the plan's pointer in the epic's `## Implemented`
**Reported on device:** a whole bay missing from the routable water, a track that draws jagged, corners taken sharply at regulated-zone edges, and a wish for a 25 m berth around the zones.

This file carries a **proposal per point**, with the evidence behind it, the numbers a proposal fixes, what would prove it worked, and the strongest objection to it. Nothing here is a decision until it is refined — the decisions the next exchange has to settle are listed at §9 and referred to as D1…D5.

## 1. What is actually wrong, in the code's own terms

- The drawn route **is the A\* node chain**: [`RouteSearch`](app/src/main/java/ykws/android/maro/spatial/RouteSearch.kt:261) answers `points = nodes.map { mesh.point(it) }`, so every bend happens at a mesh vertex and nothing between two nodes exists.
- That chain is the minimum-time path over a Delaunay fabric, and the cost is **pure time** — `edgeLengthM / speedMps` at [`:133`](app/src/main/java/ykws/android/maro/spatial/RouteSearch.kt:133) — with no term for how sharp a turn is and none for how close a boundary is. Hugging whichever constraint is cheapest is therefore optimal, and on this fabric hugging reads as zig-zag.
- The fabric's spacing is **60 m near a constraint and 500 m offshore**, grading over 1.2 km ([`Tuning`](app/src/test/java/ykws/android/maro/data/route/RouteMeshBuilder.kt:83)): near the coast, where zones and the band are, a bend every 60 m is a bend the eye sees.
- The zones are **triangulation constraints**, respected to `boundaryInsetM = 0.5 m` ([`:99`](app/src/test/java/ykws/android/maro/data/route/RouteMeshBuilder.kt:99)) — so today a route may round a zone about a metre off its edge, and will, because that is the shortest way.
- A face is kept only when **one interior point** passes the water oracle ([`:159`](app/src/test/java/ykws/android/maro/data/route/RouteMeshBuilder.kt:159)), and the water it must pass is the app's own `CoastlineSpatialIndex.isWater` — a single false discards the whole face, however large it is. That is the shape of the bay report.

## 2. Round 0 — the instrument, before any change

Nothing below can be judged by eye, and one of the two reports (the bay) cannot even be explained without measurement. So the round is a harness, not a fix.

- **Bay probe.** A JVM harness over a grid across the bay's own bounding box, printing at each sample: `isWater` (the app's oracle, as the prebake calls it), `depthM` (the bake's own reading), whether the point falls in a kept face, and the distance to the nearest mesh node with its stretch id. Run once, it names the closing cause: the oracle, the 2.5 m gate, or the simplification. No `.bin` is opened by hand — the harness loads it through the serializer, which is the only reader that exists.
- **Coverage report.** Meshed area against the coastline's own water area inside the box, so "a bay is missing" becomes a number and the *other* missing water, if any, shows up in the same pass.
- **Route metrics.** One fixed start/aim pair per report, printing: vertex count, total absolute heading change, the worst single-vertex turn, the minimum distance from the line to a zone ring, total length, and the ETA. Every later round is judged against these six numbers, which is the only way to tell a smoother line from a better path.

## 3. P1 — the bay is not water

**Evidence.** A face survives only if its interior point is water (§1). The three candidates, in the order their blast radius suggests:

1. **The water oracle** — the coastline index says land there, or the baked coastline's polygon for that stretch is unclosed or simplified away. A whole-bay failure is this one's signature: the oracle is all-or-nothing per face.
2. **The 2.5 m gate** — the bay is shallow on the soundings, or holds NoData that the gate reads as shallow. The project already carries an EMODnet shallow gate because false near-coast readings were seen, so a data artefact here is not a hypothesis without precedent.
3. **Simplification and the erosion** — `simplifyToleranceM = 15 m` on the coastline plus the gate's erode-by-vertices step; a plausible contributor to a bay's *edges*, not to a whole bay's disappearance.

**Proposal.** Diagnose with §2 before proposing a fix, and let the answer pick the round:

- Oracle at fault → the fix belongs to the coastline/water definition and needs a regression test that names the bay; the route feature consumes it and changes nothing.
- Gate at fault → the real decision is D4: a bay whose soundings say 1.8 m is either closed by design or opened by policy, and opening it means either lowering the floor, trusting a different source locally, or moving the draught half of the gate into phase 2 as the plan always intended.
- Erosion at fault → retune `simplifyToleranceM` / the inset, which costs a rebake and nothing else.

**Objection to the proposal.** It spends a round producing a diagnosis rather than a fix, and the diagnosis may well land on "the soundings are shallow" — where the honest answer is that the gate is doing its job. That is still worth the round: the same harness answers the coverage question and produces the metrics every later round needs.

## 4. P2 — why the track is jagged

Three causes, all of them structural rather than a bug (§1): node-by-node geometry, a cost blind to turn sharpness and to clearance, and a fabric whose spacing is coarse exactly where the eye looks. Two of them are the same defect seen twice — the path keeps more vertices than the geometry needs, and each of those vertices can be a corner.

**Proposal.** Attack it in the order P4 → P3 → (P5 keeps the berth), because string-pulling removes most vertices before any curve is drawn, and a fillet applied to a chain that should not have had those vertices in the first place is a polish on the wrong shape.

**Objection.** Smoothing is the visible half and the tempting first move; done first, it hides a detour behind a pretty curve. Hence the metrics: length and ETA are reported alongside the look, every round.

## 5. P3 — comfortable turns (the fillet)

> **Superseded in part, 2026-09-20:** the *time floor* proposed below was replaced by the lateral-acceleration cap the user chose — `route.turn.lateralAccelMps2`, §9's D2, and §11 for what the first run of it measured. The pass itself, its constraint test and its counted sharp vertices stand exactly as proposed.

**Proposal.** One post-search geometric pass, in three steps, all in the local metre frame the bake already uses.

1. **Retain** the vertices the path needs — that is P4's output.
2. **Fillet** every retained turn with a speed-dependent radius. The user's rule — a direction change is allowed to take **10 s** — reads as: `R = v · T / θ`, with `T = 10 s`, `v` the leg's speed (`min(cruise, limit)`) and `θ` the turn in radians. The numbers: a 90° turn takes **49 m** of radius at 15 kn, **92 m** at 28 kn and 16 m at 5 kn; the implied lateral acceleration is 1.2 m/s² at 15 kn and 2.25 m/s² at 28 kn, both comfortable for a planing hull.
3. **Constrain it.** Every inserted arc point is tested against the same free-water test the search uses, at the 25 m berth of §7; the ideal radius is bisected down to the largest that fits, and where no radius fits the vertex stays sharp and the count of such vertices is reported. That is a metric, never a silent fallback.

**Sampling.** Arc points every ~10 m so the line reads smooth without turning a 200-vertex route into a 2,000-vertex one.

**Cost, stated honestly.** A fillet is always slightly longer than the corner it replaces, and where the fillet has to be cut back to fit, nothing changes at all — tight harbour entrances will stay angular.

**Objection.** The rule is a *time* floor, not a comfort model: it says nothing about roll, sea state or a helmsman's reaction, and at 15 kn a 49 m radius is a firm turn rather than a gentle one. If the aim is genuinely "best comfortable trajectory", the honest next step is a lateral-acceleration cap the user sets in m/s² (with 1.2 as the default this rule already implies), which the same pass implements by swapping one formula.

## 6. P4 — the shortest sensible path

> **Superseded, 2026-09-20:** the sampled visibility test proposed below was replaced by a **turn penalty inside the search's cost**, a sample test being unable to see a small hole the depth gate had closed (§11). The goal it was written for — fewest vertices and fewest turns — is what shipped.

**Proposal.** After the search, a **string-pull over the node chain**: from vertex *i*, walk forward to the farthest vertex *j* whose straight segment `i → j` still lies in free water with the berth, keep *j*, and continue. The result keeps only the corners the geometry forces — which is at once the shortest path the water allows and the fewest turns, the two complaints in one pass.

**How the test is made.** Two options, and the proposal takes the first because it needs no format change: **(a)** sample the candidate segment every ~10 m through `RoutePointQueries` (`isWater` plus the zone distance), so visibility is judged against the same data the search prices with; **(b)** ship the bake's triangle set so visibility is exact, which costs bytes in the `.bin` and a serializer change. Start with (a); move to (b) only if a sample-cut corner is seen on the water.

**Objection.** A sampled visibility test can accept a corner a real mesh edge would have refused — a hairline channel between two zones could be cut across. Dense sampling plus the clearance term makes this rare, and the harness measures it: the minimum clearance along the line is one of the six numbers, so a cut corner shows up as a clearance drop rather than as a surprise on the boat.

## 7. P5 — the 25 m berth around the zones

**Proposal.** Bake it, in `Tuning`: `zoneClearanceM = 25.0`, applied as an outward buffer on the zone rings before the overlay, so the mesh's kept water begins 25 m outside every zone and the search cannot hug a zone edge any closer than that. The zone's **shape** is already baked and its **limit** is already live; this only moves the baked shape outward, which is the same mechanism one buffer call larger.

- Weighing the alternatives: a search-time clearance *penalty* would avoid a rebake, but it can still touch the boundary and it muddies the cost model the heuristic's admissibility rests on. The bake-time buffer is exact, free at search time, and the rebake is already owed whenever zones change.
- It composes with P3: the fillet is cut back against the same 25 m, so a smoothed arc keeps the berth too.
- The **band's boundary and the coastline** are constraints of the same kind, so extending the berth to either is one more tuning value — that is D1.

**Objection.** A hard 25 m buffer costs 50 m of width in any passage between two zones, and today the 30 m channel floor is measured **to land only** — so a berth that a boat would legally have taken could make a passage unroutable. Two answers: make the berth **soft** (a cost penalty that a narrow passage can outweigh), or keep it hard and let the harness name every passage narrower than 50 m before the choice is made. The proposal is to measure first and decide with the list in hand — that is D5.

## 8. The rounds, and what proves each one

| Round | Work | Proved by |
|---|---|---|
| 0 | Bay probe, coverage report, route metrics | a named cause for the bay; six numbers for the current line |
| 1 | The 25 m berth, baked (§7) | minimum clearance ≥ 25 m minus the smoothing's own tolerance |
| 2 | String-pull (§6) | fewer vertices, length not worse, clearance not worse |
| 3 | The fillet (§5) | worst single-vertex turn inside the speed's limit; length and ETA reported |
| 4 | The bay, on Round 0's answer (§3) | the bay's centre routable, or the gate's decision recorded as such |

Every round ends with a rebake, a build, and one device look at the drawn line; rounds 1 and 4 change the `.bin`, rounds 2 and 3 change only what the app draws and what it costs to draw it.

## 9. Decisions to settle before building

- **D1 — the berth's extent:** regulated zones only, or the band's boundary and the coastline too?
- **D2 — the turn floor:** 10 s at every speed, or only above 15 kn with the present geometry standing below it? And when no radius fits, is a sharp vertex acceptable (counted) or should the pass prefer a longer path?
- **D3 — the comfort budget:** how much extra length may P3 and P4 spend? A fillet lengthens; the berth and the string-pull may shorten. 5 % is the proposal's working figure.
- **D4 — what NoData means:** the reported bay is sealed by missing soundings at its mouth rather than by shallow ones, so the question is whether a cell with no depth reading is *unknown* (kept, so water the oracle accepts stays connected) or *shallow* (dropped, today's behaviour). Keeping it is the proposal; the objection is that it opens water nobody has sounded, which is the opposite of what a depth gate is for — so the decision wants the measured trade: how many stretches merge, and how much unsounded water that admits.
- **D5 — the berth's force:** hard where it may close a passage, or a cost preference that a narrow passage can outweigh?

### Settled by the agent, on the rule that a choice which changes nothing visible is the agent's (2026-09-20)

Each answer below is the one that leaves the requested behaviour exactly as asked. Each carries a **tripwire**: a measurement that, if it fires, sends the choice back to the user rather than being decided quietly.

- **D1 → the regulated zones only.** That is the request in its own words ("a distance buffer around the restricted zones"); extending the berth to the 300 m band's boundary or the coastline is a wider rule than the one asked for and would visibly change where every coastal route runs, so it waits for a word. Extending it later is one tuning value and one rebake, never a rewrite.
- **D2 → the comfort model is a lateral-acceleration cap, decided by the user on 2026-09-20: `route.turn.lateralAccelMps2`, shipped at 0.5 m/s² (about 0.05 g).** The cap fixes a turn's radius at a speed by construction, `R = v² / a` — about 120 m at 15 kn, 415 m at 28 kn, 13 m at 5 kn — so the original "10 s above 15 kn" rule is met where it was stated and answered everywhere else for free, and the older question of whether to apply a *time* floor at every speed dissolved with it. The value is a property read live, like every other route value, so tuning it changes the next route and never a bake. When no radius fits, the pass keeps the vertex and counts it rather than lengthening the path to find room: a detour is a change to the route the user asked for, and no one asked for one. **Tripwire:** if more than a small handful of vertices per route come out sharp, the geometry, not the policy, is what needs revisiting — that comes back with the count. **Corrected 2026-09-20:** this line read 1.5 m/s² with radii of 40 / 138 / 4 m; the sweep that followed re-chose the default on the boat's own inshore water (§12), and the code ships 0.5.
- **D3 → no length budget, measured instead.** A fillet lengthens by a fraction of a percent at these radii and the string-pull shortens, so the pair is expected to be length-neutral; a budget would only license a detour for comfort, which is the user's call and not needed yet. **Tripwire:** a route growing past a few percent goes back as a question, with the number.
- **D4 → NoData is unknown, and it is kept.** The requested behaviour is that the bay is water, and the bay is sealed by missing soundings rather than by shallow ones, so keeping unsounded cells is what delivers the request. **Tripwire:** the measurement first — how much water this admits and whether the stretches around the reported bay merge; if the unsounded water swept in passes a few percent of the area that routes today, it comes back as a question about how much unknown sea is acceptable.
- **D5 → a hard berth, measured before it is trusted.** Exact and free at search time, and the corridor is open water almost everywhere; the risk is a passage that a 50 m squeeze would close, which is a *measurement* first. **Tripwire:** the harness lists every passage narrower than 50 m; if one of them carries a route today, the berth for that zone becomes a cost instead of a wall, and that choice is the user's.

**What stays the user's, because it changes what is seen:** whether comfort may ever buy a detour; the value of the cap itself, now that it is a shipped property rather than a formula to argue about; and the two tripwires above, if they fire — the unsounded water D4 admits, and the narrow passage D5 might close.

**One thing to expect until Round 3 lands:** `route.turn.lateralAccelMps2` is set and clamped but **read by nothing yet** — the fillet that computes `R = v² / a` is Round 3, still unbuilt. The key is placed now because it is the settled decision, not because it has a consumer.

## 10. Round 0's own reading (2026-09-20, `feature/route`)

The instrument was built and run — [`RouteTrajectoryProbeTest`](app/src/test/java/ykws/android/maro/data/route/RouteTrajectoryProbeTest.kt:1), beside a `PrebakedRouteGeometry` now shared by the bake and the probe so both read one world. What it says, verbatim from its own output:

- **The routable water is the soundings' footprint, not the box.** Of 128,247 water cells in the box (5,130 km²), **95,754 have no depth reading at all** (74.7 %) — and the gate closes every one of them by design, which is 3,794 km² of the box that can never be routed. This is the fourth cause for missing water that §3's three candidates did not name, and it is the largest of them by an order of magnitude.
- **Inside the soundings, coverage is complete.** 33,398 water cells hold a node within 500 m: 26.0 % of all water but **102.8 % of the water the soundings cover** (over 100 % because nodes also stand just outside sounded cells). So the erosion, the connectivity pass and the refinement are not losing the sounded sea.
- **Two real but tiny artefacts.** Three single 200 m cells that hold 2.8 m, 5.4 m and 140.6 m of water and no node; and four single 200 m cells of land the soundings call water (5.0–40.7 m). Each is one cell wide, which is smaller than the scan's own resolution — named here, not yet called defects.
- **The metrics, which are rounds 1–3's baseline:** the long pair (104.34 km) is **413 vertices · 7,366° of total turn · 110° worst turn · 0 m minimum zone clearance**; a zone-hugging pair is 196 vertices with the same 0 m. So the line turns about 18° per vertex, takes single corners of 110°, and rounds zones with *no berth at all* — the berth of §7 is not a comfort question but the difference between a route that touches a zone's edge and one that gives it 25 m.
- **The bay is not yet explained, and the instrument is why.** Nothing in the scan looks like it: no water area the soundings reach is unmeshed, and no cluster of land holds a depth the soundings call sea — so if the bay is missing, it is either classified as land **and** shallower than 2.5 m throughout (which neither test reports) or it lies beyond the depth grid's edge. Both need the bay's own coordinates, which is the one input the probe cannot derive.

- **The window probe, run once at Golfe-Juan / Vallauris ±1.5 km** (43.555 N, 7.070 E — the first candidate for the name), prints a 25 m picture of that bay and finds **no hole**: 9,383 water cells the soundings cover, only 39 they do not, **no** land cell holding a soundable depth, and the mesh threaded through the water on the offshore spacing. So that bay is meshed and routable, and the reported one is somewhere else.

- **The reported bay, found — and it is not land, it is sealed.** At the user's own point (43.54560 N, 7.12520 E), ±1.5 km, nine probes settle it in one line each: the centre is **water** to the oracle and holds a **node 56 m away — in stretch 11**, while every surrounding point (W 22 m away, NW 7 m away, S, SE, SW — all deep, all sounded) sits in **stretch 0**. The bay is not missing from the mesh and the oracle does not call it land; it is a **component of its own**, cut off from the sea beside it, and the centre cell's depth reads **none** — no soundings at all where the mouth would be. That closes the loop with the search: the one-stretch rule re-resolves any aim inside the bay back into the boat's stretch, so from a boat the bay cannot be entered — which is exactly what "that bay is not part of the water" looks like from the helm. The likely mechanism is the gate reading an **unsounded cell as shallow**, so the triangles that would join the bay to the sea are dropped and the connectivity pass labels what remains separately; D4 below is therefore not "is a shallow bay in or out" but **what NoData means** — unknown, or shallow.

- **What Round 4 is now:** prove the mechanism by re-running the gate with `NaN` treated as *unknown* (kept) rather than as *shallow* (dropped), and check that the stretch count around 43.5456/7.1252 collapses from two to one; then decide the policy with the numbers in hand, and only then rebake.

## 11. Rounds 1–4 as run, and what each one taught (2026-09-20)

- **The search carries the turns now, not a visibility test.** The study's sampled string-pull is retired: a sample every 10 m cannot see a 25 m patch the gate closed, while a path on the mesh's own edges cannot leave covered water by construction. A turn penalty in the edge cost, plus a collinear merge, took the long pair from 413 to 263 vertices and its worst turn from 110° to 86°, at 104.26 km against 104.34 — eight hundredths of a percent shorter — and 124.2 minutes against 123.6. Nothing was traded for it.
- **The triangles ship.** The bake's 90,242 triangles are in the `.bin` so "inside the mesh" is exact rather than guessed: 3,125,343 → 3,840,928 bytes, +699 KiB, stated rather than hidden.
- **The fillet is correct and nearly inert, and that is a measurement, not a fault in the code.** At the shipped 1.5 m/s² the radius is 138 m at 28 kn and **233 of 236 corners stay sharp**; the D2 tripwire fired on its first run. The counter-intuitive part is worth stating plainly: a *lower* cap makes the fillet fit *less* often, because a lower cap means a wider radius. The cap therefore wants a sweep before a default is chosen, and the sweep is a measurement, not an opinion.
- **The berth's hard form holds 25 m and costs 13 % of the sounded water**, so it is off and the mechanism is to be redone as a clearance *penalty* in the search's cost — a preference the route can weigh rather than a wall it cannot pass. The soft attempt that was tried held nothing, so it needs its scaling fixed rather than being judged.
- **The gate's unknown-soundings change is far too broad as written:** it takes the mesh from 46,621 nodes and 3.84 MB to 111,497 and 9.55 MB, exhausts the 250,000-node budget and can produce a 71 km edge that spans the corridor — the reported bay is a pocket, and the change that admits it must be a pocket-sized one. Keeping a missing sounding **only where it reconnects stretches that are otherwise separate** is that change, and the probe can say whether the bay's own two stretches become one.
- **A later correction, and the lesson is the pass's own:** the same mesh rebaked read 263 vertices where 238 were measured before the berth work, at the same length and ETA within 0.1 %. Two consecutive bakes then proved byte-identical, so it was never drift: those two readings came from different code and parameters, the berth and turn work having landed between them. A reading compared across bakes needs the bake *and* the code named beside it.

### What the arc defect taught (2026-09-20, after the pipeline's review)

- **The fillet had never drawn a single curve, and a green suite said otherwise.** Its arc took a cutback of `R / tan(θ/2)` and a centre at `R / sin(θ/2)`; the true values are `R · tan(θ/2)` and `R / cos(θ/2)`, so the sweep it produced was ≈102° at a 30° corner, ≈106° at 60° and ≈48° at 120° — never the turn — and the guard refused the corner at every radius before the water test could run. Every test case sat at 90°, where the two forms are numerically identical: the suite passed over a pass that could not smooth.
- **Its fingerprint was in the counts and was read as a property.** `0 refused by water, 425 of 444 by "no arc"` is the shape of a geometry step that bails before the water is consulted, and it was reported — and accepted here — as a finding about the mesh. The lesson is general: a refusal count that never mentions the constraint the refusal claims is a bug report, not a measurement.
- **Corrected, and the readings move with it.** Sweeps of 30°, 60°, 90° and 120° now equal the turn; `no arc` is 0 everywhere against 425 of 444; the fillet draws at every corner the mesh allows — 0 sharp of 446 at the re-chosen default **0.5 m/s²**, 1 of 444 at 1.0. The geometry now has one home, `RouteTurnGeometry`, because three copies of it are what produced the wrong diagnosis in the first place — and the default the first sweep chose had been chosen while the fillet was inert.
- **The corridor pass now reports the number its own tripwire names.** The D4 tripwire is the *area* of unsounded water admitted, not nodes or stretches: it reads **0.0 km² against 1,228.5 km² that routes**, and the corridor's length is bounded and its longest unsounded run and minimum sounding are printed per corridor, sampled at the depth grid's own cell size rather than once at a midpoint.

## 12. What the device showed, and it is this pass's fault (2026-09-20)

- **The line is not straightened, and a substitution made here is why.** The study's string-pull was retired in favour of a turn *price* in the search — a price, on the report's own numbers, of six percent: 7,366° of total turning became 6,931°. Six percent is not a straight line, and the pass reported it as "the line straightened". That was wrong, and the device said so within minutes: wiggles dominate, corners are still corners, and the length barely moved.
- **The fillet draws, but only where an arc fits — and inshore, almost nothing fits.** At the default 0.5 m/s² the radius is 415 m at 28 kn, which is at home in open water and almost never in the 60 m-spaced coastal mesh. The sweep chose that default from the corridor-long offshore pair — **the wrong water for the boat that reported the problem**, which sails short, inshore and between zones. The cap and the pairs both need re-taking where the boat is.
- **The berth is a price of 2.0 and moves in-berth distance by 2.5 to 6.8 percent** — invisible on the water, which is exactly what "no hugging of the zones" describes. The row that trips its wire is 3.0, at +3.09 % of length, and the choice between paying that and paying nothing is the user's.
- **The enabler for the real fix has since landed:** the bake now ships its triangles, so a shortcut pass can test containment **exactly**, which is precisely what the retired sampled string-pull could not do. The study's own order — straighten the chain first, then round what is left — is the order to build, and the pass built it backwards.
- **What must be measured before it is believed:** the same numbers, on a short inshore route of the shape the boat actually asks for — vertices, total turn, worst corner, minimum clearance, length, ETA — because every reading this pass took came from a pair chosen for the corridor's extremes rather than for the water the complaint came from.

## 13. Why the 25 m berth is a choice, not a setting (2026-09-20)

**The request:** when a route goes around a regulated zone, keep it 25 m clear of the zone's edge.

**Why it is conditional, and this is the whole issue.** The mesh defines where a boat may be: a route runs on the water the mesh holds, and a zone's ring is a constraint the mesh respects. Where the water between a zone edge and whatever lies next to it — the shore, another zone — is **narrower than fifty metres**, no line can both pass and keep 25 m: the boat either comes closer than 25 m or it does not go. Around this coast that is not a rare case, because almost every zone touches the coastal band.

**Three mechanisms, each measured on the four inshore pairs:**

| mechanism | what it is | what it costs | where it fails |
|---|---|---|---|
| **Hard** — bake the zone ring outward by 25 m, so the mesh's own water begins 25 m outside | exact, and free at search time | it **deletes** the strip: 13 % of the sounded water overall, and it can sever a passage outright | wherever the strip was the only way through |
| **Price** — pay a penalty per metre inside the berth, water untouched | nothing deleted, no passage closed | a *preference*, not a guarantee: on two of the four inshore pairs it never lifted the line off the edge at twice, three times or five times the base price, and on one pair the dearer price made the exposure **worse** — 3.24 km inside the berth became 4.86 km with a 3.01 km unbroken run, for +1.68 % of length, because the search traded a longer berth run for a shorter line | wherever the alternative is a detour large enough to be worth the fine |
| **Hybrid** — bake it hard only where it costs no water, keep the price elsewhere | exact where it is free | a rebake, and two mechanisms to keep honest | where the water is narrow the line still cuts inside — the hybrid buys exactness only where it is free |

**Recommendation: the hybrid**, because it is the only one that makes a promise it can keep — 25 m wherever the water has the room — instead of either refusing water (hard) or merely asking nicely (price). **The strongest objection to it:** on exactly the water where the complaint arose, the hybrid's price half still lets the line run along the zone edge, so a user who asked for 25 m everywhere will still see the line on the edge; calling the result a berth everywhere would be a promise the geometry cannot keep, and the honest phrasing is "25 m where the water allows, a preference elsewhere".

**A second item, separate from the berth and also the user's:** the straightening shortens the drawn line by 6 to 23 %, but the trip times still describe the path the search priced rather than the line drawn, so the ETA now **overstates** by about 15 % on a long route. My recommendation is to make the times follow the line, since the figure should describe the line on the screen — but it changes what the trip cell shows, so it is not mine to change unasked.

### The hybrid as built, and the structural answer it produced (2026-09-20)

The user chose the hybrid. It is built and green, and its result is narrower than the idea — in a way that answers the berth question for good:

- **38 of 121 zones went hard, 83 priced.** Of the 38, only 21 cut anything (1.347 km² of the mesh's 1,228.45 km², **0.11 %**, so the 1 % tripwire is clear) and 17 had nothing in their strip to cut at all. The 83 priced ones are the answer's other half: **their cut severs the water** — 13 stretches became as many as 31 — because on this coast the coastal corridor *is* the sliver beside the zone.
- **Not one of the four inshore pairs moved**, by a metre, a kilometre or a minute. The zones that could safely be cut on that water are zones whose strip held no routable water, so the hybrid spent its exactness on the near-shore zones and changed nothing where the complaint came from.
- **So the berth's limit is geometry and not a weight**, and it can now be stated precisely: in the Antibes / Golfe-Juan / Cap d'Antibes water the passages that matter are between a zone's edge and the shore and are a few tens of metres wide. Cutting 25 m out of them severs them; pricing them moves nothing, because there is no other water to move to — measured at five times the base price, with the outcome on one pair *worse*. A 25 m berth there is not a setting left to find: it is water that does not exist.
- **What remains for the berth:** the close pass, as today, or forbidding those slivers outright — which would cut off coastal routing altogether. That is the honest choice, and it is a choice about whether the boat may pass close to a zone, not about a number.

**And the shortcut pass from the same day, for the record:** it drops 87–94 % of the drawn line's vertices and 84–99 % of its turning, and shortens it by 6–23 %, on the inshore pairs — the straightening the complaint asked for, built on the bake's triangles, which is the test the study's first string-pull lacked.

## 14. The line crosses the zones now, and it is the cost model being bypassed (2026-09-20, from the device)

**Why it happens.** The mesh's water *includes* a zone's interior, and that is the design: a regulated zone restricts speed, it does not remove water, and the bake resolves no limit — the limit is live at search time. A\* was therefore always free to cross a zone and paid the zone's limit when it did, going around exactly when going around was cheaper in time. The **shortcut pass tests containment and the boat's stretch and asks no price at all**, so it inherits the freedom and drops the cost: it cuts straight across a 5 kn zone the search had chosen to round. Nothing is illegal — a transit of a zone is permitted at its limit — but the route the search picked has been replaced by one it would not have picked, and the drawn line no longer carries the price that chose it.

**The rule the pass must obey.** Any pass over the chain must **re-price what it changes**: a candidate shortcut is admissible only when the time it implies — at the same limits and the same pace the search used — is **no worse than the chain it replaces**. "Around" then returns wherever around is faster, which is the search's own decision preserved, and "through" survives only where through really is faster.

**The stronger policy, if the user wants it.** Refuse any shortcut that enters a zone at all, whatever the clock says, unless the destination itself lies inside that zone. That makes "never cut a zone" an invariant rather than an outcome, at the price of routes longer than the strictly fastest one.

**Third instance of one pattern, and it is the lesson of the whole day.** Something geometric bypassed the priced search three times: the sampled visibility test (which could not see a hole the gate closed), the fillet's speed (derived from times that already carried the turn price), and now the shortcut (which asks no limit). The guard that would have caught all three is a single invariant, **asserted** rather than reviewed: *the drawn line's own cost must never exceed the price the plan was built on* — measured on every probe pair, the way the six metrics already are.

**And the ETA item is the same wound.** The trip figure still describes the path the search priced rather than the line drawn, so a crossing the search never chose shows up as a route that looks fast and is not. Making the times follow the drawn line and pricing every pass are one repair, done once.

## 15. Solutions proposed for the crossing (2026-09-20) — revised after review

The question asked was whether the zones' price can simply be raised until the line goes around, or whether a better algorithm is wanted. Both, and they are one list — P0 and P3 are needed whichever of P1 or P2 is chosen. **This section was revised on 2026-09-20 after an independent review; §17 carries the verdict, the six blocking findings it produced, and the one question it left open.**

- **P0 — make every pass cost-preserving (required, not optional).** The drawn line is produced by passes that today ask no price: the shortcut tests containment and stretch only, so it cuts a zone the search had priced. Every pass must re-price what it changes with the search's own model, and the invariant that catches this class for good is *the drawn line's own cost must never exceed the price the plan was built on*.
- **P0's invariant is stated against `actualSec`, and it lives in `buildSuccess()`.** The search's own key carries the berth's courtesy, so it and the clock the plan reports disagree by construction — a key-based invariant would sit permanently slack on one side and fire spuriously on the other. The comparable quantity is the **actual seconds** the plan reports, recomputed pass-local over the drawn chain at the limits in force. Its home is where the plan is assembled, **not the probe**: the probe is skipped unless a system property is set, so an invariant asserted there asserts nothing in a normal run.
- **P1 — an explicit zone-avoidance factor: yes, the price can be raised, and this is what that is.** Today a zone's price is only its legal limit, so a crossing is taken whenever it saves time. A factor `a` prices in-zone seconds at `a` times their legal cost, so a crossing must save more than `a` to survive: `route.zoneAvoidanceFactor`, default 2.0, swept on the inshore pairs to find where crossings stop. It is a **search-only** term — the trip figure is recomputed at legal speeds, so the deterrence never reaches the ETA — and **P0** applies, or the pass undoes it.
- **P1 never reaches the 300 m band, and is not worded as though it did.** The band is excluded from the priced speed layer by construction, and the line already runs 44 of 105 km inside it on the inshore pairs, so a factor that covered it would price the coastal corridor itself rather than the zones the complaint names. The earlier claim that one knob covers both is withdrawn.
- **P2 — a traversal rule instead of a price: the stronger answer, and my recommendation if the measurement allows it.** A zone's interior is simply **not traversable** in the search. Crossings stop being an outcome of a weight and become an invariant, nothing needs tuning, and the ETA needs no distortion because nothing was inflated.
- **P2's forbidden set is the priced speed layer, and only it** — mechanically one clause keyed on the reserved `FORBIDDEN_LIMIT_KN` sentinel, which is the seam already kept for exactly this. The band is never in that set.
- **P2's exception is per-zone on the node the search resolved, and it covers the start as well as the destination.** A boat already inside a zone when the route is armed must still be able to leave, so the exception is decided once per zone against the node actually resolved, and only for the zone that node lies in.
- **P2 binds the fillet and the shortcut too, and the measurement leads with the inshore pairs.** A traversal rule the search obeys and the passes ignore is P0's defect under a different name. What P2 costs is unchanged from the original wording: a route whose only way lies through a zone becomes impossible rather than expensive, so if no probe pair loses its route it is adopted, and if any does, P1 with a swept factor is the fallback.
- **P3 — the trip figure must follow the drawn line (required, already waiting).** The ETA describes the path the search priced, not the line on the screen, so every one of P1's inflations and P0's short-cuts shows up as a figure that does not match what is drawn. Recomputing the plan's time from its own drawn polyline at the limits in force is what makes P0's invariant and P1's factor both honest.
- **P3's limits come from the chain edge each drawn sub-leg was cut from, not from a live point query.** No point-wise band test exists to call, and the sampled predicate that would stand in for it is the shape that cost 10,893 ms in the shortcut pass. A sub-leg inheriting the limits of the edge it was cut from is exact on the band and free. **Its reach is wider than the trip cell:** the cell draws live pace while the confirm panel and the saved course read the plan's own leg times, so the recomputation lands in the plan's times.

**Weighing them.** P2 is the only one that answers "will it cross?" with a yes or a no rather than a threshold, so it is the better *policy*; P1 is the better *preference*, because it keeps a route that must cross and merely makes it pay. The strongest objection to P2 is the stranding case above, and the strongest objection to P1 is that a factor is a number nobody can feel — its only defence is the sweep, and a flat sweep means the crossings were never a pricing problem at all.

**Recommendation (2026-09-20).** P0 and P3 as prerequisites, then **P2 with a fallback**: a zone's interior is not traversable while a way around exists; where none exists, or the resolved start or destination lies inside that zone, the crossing is priced and taken as today **and the result says so** — a forced crossing becomes a state the plan can carry and report, rather than a normal success that happens to cross. That is the only arrangement that yields what the user asked for — "it must go around them" — with no number to feel and no way to strand a boat, and P1's factor drops to a tuning alternative rather than the mechanism.

The user's own arithmetic is what makes the fallback enough: a crossing that saves 20 % of the trip is turned around by a factor below 2, so it is never the *only* way — which is exactly the case the fallback is for. The strongest objection to the recommendation: on this coast the way around is often long, so the route will visibly lengthen precisely where crossing was the fast path, and on a short inshore hop that lengthening can be a large share of the trip. That is the trade the user has asked for, and it is their call to make.

## 16. What would make the whole study wrong

- **A wrong diagnosis of the bay.** Every proposal in §3 assumes the instrument will name the cause; if the bay is closed for a reason none of the three covers — a stretch-labelling accident, a box edge, an unclosed ring — then Round 4 is a different round.
- **A fillet that cannot fit where it matters.** In the corridor's real geometry, most sharp corners are sharp *because* a zone or the coast is close; if the constraint test refuses the radius nearly everywhere, P3 buys little and P4 carries the whole improvement.
- **A berth that costs routes.** If the harness finds a passage that matters inside 50 m, §7's hard buffer is the wrong shape of fix and D5 answers it with the soft one.

## 17. The review of §15, and what it changed (2026-09-20)

§15's recommendation — P0 and P3 as prerequisites, then P2 with a fallback — was put to an independent review before anything was built. **Verdict: revise.** The arrangement stands; its wording and the homes it named did not.

**Six blocking findings, all folded into §15 above.**

- The invariant was stated on the search's own key, which carries the berth's courtesy while the clock it must bound does not, so the two disagree by construction and the comparison is meaningless on the side that matters.
- Its named home was the probe, which skips unless a system property is passed — an invariant that runs only when someone remembers to ask asserts nothing.
- P2 excepted only the destination, so a boat whose **start** lay inside a zone would have been unable to leave it.
- The success result could not represent "the crossing was forced", so the fallback had nowhere to say it had been taken, and a forced crossing would have read as an ordinary route.
- P0's and P3's recomputation was written as a live point-wise band test that does not exist to call, and the sampled predicate standing in for it is the shape already measured at 10,893 ms.
- The P1 wording let one knob reach the 300 m band, which would have priced the coastal corridor — 44 of the line's 105 km on the inshore pairs — rather than the zones the complaint names.

**Should-fixes, also folded in.** The per-edge forbidden test is nearly free, because the per-node clearance cache is already populated and a zone ring is a mesh edge, so no edge crosses one without ending inside it. `FORBIDDEN_LIMIT_KN` is the seam already reserved for the rule, which makes P2 one clause rather than a new mechanism. Only `outerRing` is a constraint, so zone holes are not. A bridging corridor can be neutralised, which matters where a corridor's own water crosses a zone. The two-phase form could be a lexicographic cost, but the heap is keyed on one `Double`, so the fallback is expressed as a state rather than a second objective. And §9's D2 text named a default the code no longer ships — corrected there, above.

**The one question the review left open, and the reading taken here.** Whether P2's forbidden set is the priced speed layer alone — mechanical, exact, and what this revision assumes — or whether it should also cover zones the bake takes as constraints but nothing prices, which would need the model to carry a zone kind it does not have today. Taking the priced layer alone leaves a route free to enter a zone whose *entry* is regulated; taking entry prohibitions too forbids water the bake deliberately kept and makes the model grow a field. The priced layer is taken, and the objection is stated rather than buried.

## 18. P0, P3 and P2 as built, and the pass the invariant found (2026-09-20)

Built through one delegated pass, green, with nothing in the plan's goal adjusted. 563 tests pass, 11 of them new.

**Amended by §19 (2026-09-20):** the `#implement` pipeline reviewed this delivery and returned **revise** — two admissions charging no corner and an invariant able to throw. §19 records what the loopback changed. The numbers below are what that pass measured and are left as it read them.

- **P3 — one clock, one home.** [`RoutePlanTiming.drawnLegSeconds()`](app/src/main/java/ykws/android/maro/spatial/RoutePlanTiming.kt:57) reads a plan's seconds off its own drawn polyline, each leg at the limit it inherits from the chain edge it was cut from; a candidate spanning several edges takes the **most restrictive** of them, because a boat in that span is bound by the lowest limit in force. [`RouteFillet`](app/src/main/java/ykws/android/maro/spatial/RouteFillet.kt:112) now hands each output sub-leg the limit it was cut from, an arc's chords taking the more restrictive of the two legs. The remainder and the fallback read the **recomputed drawn** seconds and never the search's key, and the plan's pace is drawn length over drawn time.
- **P0 — and the invariant earned its keep on its first run.** The search accumulates real seconds separately from its heap key, the shortcut and the merge are admissible-only at the limits in force, and the invariant *the drawn line's own cost must never exceed the seconds the search accumulated* is asserted in `buildSuccess()`.
- **What it caught: the fillet, the pass the task had not named.** The first run breached on one pair — *352.21 s drawn against 350.94 s accumulated* — and the cause is structural rather than a slip: at a corner between a fast and a slow leg the **whole arc pays the slow limit**, so the arc is dearer than the corner it replaces by `R·θ/2·(1/v_slow − 1/v_fast)`. Applying the plan's own rule rather than loosening the invariant, [`arcNoWorse()`](app/src/main/java/ykws/android/maro/spatial/RouteSearch.kt:686) now refuses an arc dearer than its corner, reported as its own count (`sharpNoPrice`: 1 on inshore 2, 1 on inshore 3, 1 on zone 1, 2 on zone 3). **The lesson repeats the arc defect's:** a pass that bypasses the priced model is found by an asserted invariant, not by review, and this is the third instance in one feature.
- **P2 — adopted, and the swept factor is not needed.** A priced zone's interior returns the reserved sentinel and is not traversable; the 300 m band is never forbidden; the exception is per zone against the nodes the search resolved and covers **both ends**, so a boat already inside a zone can still leave it. The fallback is a **second priced run** — not a second objective, the heap being keyed on one `Double` — and the result carries the forced crossing with its zone names, read off the drawn line as a report rather than as a rule.
- **Adoption gate: 8 of 8 metric pairs routed, 0 lost, none forced.** P2 stands on the measurement the plan demanded, and P1's factor drops to a tuning alternative.
- **The four inshore pairs as they now read** — vertices · total turn · worst corner · min clearance · length · ETA, with the drawn figure against the priced one the old clock reported: inshore 1 **18 · 130° · 20° · 20 m · 4.18 km · 5.6 min (drawn 5.6 / priced 6.6, −15.23 %)**; inshore 2 **12 · 152° · 23° · 1 m · 4.16 km · 12.8 min (−2.91 %)**; inshore 3 **79 · 538° · 80° · 0 m · 6.58 km · 22.0 min (−5.11 %)**; inshore 4 **66 · 123° · 19° · 0 m · 4.05 km · 5.6 min (−5.09 %)**. The corridor pair reads 92 vertices · 281° · 52° · 91.16 km · 107.8 min against a priced 128.5, −16.13 %. So the ~15 % overstatement §13 recorded is closed: the ETA now describes the line on the screen.
- **One finding left standing, and it is named rather than hidden.** In the **fallback** run a shortcut may still clip a zone the chain did not cross, because P0 prices a candidate at the limits of the chain edges it replaces and knows nothing of a zone the chain avoided. The forbidden run closes that hole; the fallback reports the crossing it takes, which is the arrangement the plan asked for.
- **Choices taken, none of them visible.** The clock keeps the search's own turn model, so a drawn corner is charged the arc-minus-chord price and `legTimesSec` still sums to the duration; `routeTripFigure` keeps its live-pace branch, which an existing test pins, and what changed is the seconds it is handed; `speedLimitKn` was replaced by [`pricedZoneAt()`](app/src/main/java/ykws/android/maro/spatial/RoutePointQueries.kt:41) so the zone question has one home carrying both price and identity.
- **Nothing structural moved.** No bake, no `.bin`, no mesh-format change: the limits are live and the zones' shapes are untouched. `RouteMeshBuilder`, the serializer and `data/app-assets` are unmodified.

## 19. The pipeline's review, and the loopback it forced (2026-09-20)

The `#implement` pipeline ran over §18's delivery. Its Ask hop returned **verdict revise**, three findings blocking — two of them a crash path rather than a wrong number — and all are now closed. The suite is green at **566 tests, 0 failures** and the probe re-read unchanged on every pair.

- **The merge and the shortcut rotated a corner they never re-priced.** Both admissions compared the *legs* they replaced and not the turn at the corner they moved, while the drawn clock charges that corner at its new angle. The size of it: a one-degree shift at a 90° corner on a 28 kn leg is about **0.15 s**, five orders of magnitude above the `maxOf(1e-6, acc·1e-9)` slack the invariant carried. So the invariant named the right quantity and was not a proof — and a legitimate route could breach it.
- **Both now charge every turn they change** — the turns at the corners a candidate's span loses, and at the vertices it rotates — through one home, so **admission compares the quantity the drawn clock charges**. The merge charges both rotated corners rather than the survivor alone, the review having named only one of them, and `arcNoWorse()` now shares [`RoutePlanTiming.arcPriceSec()`](app/src/main/java/ykws/android/maro/spatial/RoutePlanTiming.kt:105) so the pass's last duplicated arithmetic is gone — it had been safe only because it over-charged. The slack was not loosened, the exact accounting having proved practical.
- **And a breach can no longer cost the user their route.** The `check` became a test: on breach `buildSuccess()` returns the **raw node chain** — no merge, no shortcut, no fillet — with a warning through a substitutable seam and a flag on the result. Neither `preview()` nor `recompute()` catches, so the old `IllegalStateException` would have taken the preview or crashed on an uncaught coroutine exception; the fallback costs smoothing, which is survivable, and never the route. The check itself is retained, so the flag stays a reading rather than a memory.
- **The crossing report is exact now.** It walked the drawn line at a 50 m step, so a leg at or under the step — every fillet chord — was sampled at its ends alone and a corner clip could fall between samples. Since the panel and the card both gate on "any names", an undetected clip would have presented a forced crossing as an ordinary route, which is precisely what §17's fourth blocking finding existed to prevent. Every leg is now walked in at least two intervals, each halved.
- **The fillet's refusals now name constraints that were consulted.** Price was asked before geometry and water, so a corner counted "no price" had never been water-tested nor geometrically built; the order is geometry → water → price, and only the **first** refusal is recorded. The counts moved accordingly — `sharpNoPrice` 1 → 0 and 2 → 1 on two pairs — which is the honest reading of what refused those corners, not a regression.
- **Removed as dead:** `forbiddenInterior()`, the fillet's whole leg-time redistribution together with the search's four per-node copies of the accumulated time, `RouteMeshContainment.containsIn()`, and `RoutePointQueries.distanceToCoastM()` with its three implementations.
- **The tests that would have passed on a revert were the real defect.** The shortcut's whole-span candidate is now refused *on price*, with the intermediate vertex kept; the band test's stub carries a zone, so a band-forbidden regression now loses the route or moves the line; the drawn-clock test asserts that no fallback fired. Three readings pinned nowhere were added — the fillet's sub-leg limits, the remainder consuming drawn seconds, and the crossing walk's sampling — and a breach is forced **through the public API** and asserted to return the five raw vertices with the warning and the flag.
- **The readings:** the four inshore pairs and the corridor pair keep their vertices, total turn, worst corner, clearance, length and ETA, and **the raw-chain fallback fired on 0 of 8 pairs** — so the corrected admissions appear sufficient in practice, which is now a measurement rather than a hope. No crossing is newly reported: the denser walk found none the old one missed on this water, and the new test is what proves it would have.
- **One hair moved, and it is on the record:** inshore 3's drawn-against-priced reads **−5.09 %** where §18 recorded −5.11 %, about 0.3 s of 1,320 — a merge the corrected rule now refuses. Its vertices, turning, worst corner, clearance, length and ETA are identical.
- **The cost of the repair, stated against itself:** the merge and the shortcut now compare three turn terms per candidate, so a knife-edge candidate can be refused that the old rule admitted, and this is where that hair comes from — the price of making admission and the drawn clock one quantity instead of two.
