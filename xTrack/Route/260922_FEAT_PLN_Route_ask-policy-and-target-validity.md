<!-- scope: feature -->
# Route — the master requirement book (the route mode and the route it saves)

**Date:** 2026-09-22 · **Branch:** `feature/route-avoid-workflow` · **Status:** in design — rev 8, the **master requirement book**: every requirement of the route mode and of the **route** a saved line becomes, with the technical details settled (§7) and nothing open; nothing implemented. The saved line was called a **trace** until 2026-09-23, when the pair became **Track** against **Route**; every section below carries the new word. Rev 8 (2026-09-24) rewrote the acquisition onto an **explicit action** with no timer and no automatic refresh — the change [`260924_FEAT_PLN_Route_acquisition-and-route-workflow.md`](260924_FEAT_PLN_Route_acquisition-and-route-workflow.md) specifies.
**Note on the name:** opened as "the ask policy, the serial pipeline and target validity", now the master book of the whole delivery; the filename lags its content and a rename is the user's to authorise.
**Corrects:** the rule statements written into [`FEAT_DSC_Route.md`](FEAT_DSC_Route.md)'s three sections and the walk items it carries, where they disagree; the saved-route half likewise corrects the rule statements in [`../Tracks/FEAT_DSC_Tracks.md`](../Tracks/FEAT_DSC_Tracks.md) and the Tracks walk's 2026-09-22 level.
**The plans reference this file and never restate it:** the route mode's build order is [`260922_FEAT_PLN_Route_interaction-build.md`](260922_FEAT_PLN_Route_interaction-build.md) and the trace work's is [`../Tracks/260922_FEAT_PLN_Tracks_trace-flag-and-display.md`](../Tracks/260922_FEAT_PLN_Tracks_trace-flag-and-display.md); both point back here rather than restating a requirement, so a change to what the delivery does is a change here first.

## 1. The requirement book

`decided` = buildable as written; `open` = an answer is still owed (§9).

### 1.1 The route mode

| # | Requirement | Status |
|---|---|---|
| R1 | **The route is a straight line** — the placeholder draws one segment; no water, zone or berth is promised while it ships | decided |
| R2 | **Nothing is computed until the user asks** — the acquisition's own **Acquire route** action is the only trigger; no timer, no drag and no arming frame computes anything | decided |
| R3 | **Every entry into acquisition reads a fresh anchor from the boat's own position**, led by `route.anchor.leadSec` (default 10 s, bounded 0–60, `maro.properties` behind `AppConfig`); a predicted point that is not water falls back to the live fix, and demo mode — whose position is the map centre — takes no lead at all | decided |
| R4 | **A new ask aborts the one in flight and starts** — never two alive, never a queue; **one worker serves every ask**, and that exclusivity is pinned by test | decided |
| R5 | **A hang is assumed away** — no per-call ceiling; the risk is named, not mitigated | decided |
| R6 | **A refused destination shows a bold red crosshair** and the panel's sentence naming the reason, the outcomes staying hidden while no plan exists | decided |
| R7 | **The origin is judged on every entry into acquisition — never on a later search** — and asked of the fallback point when the predicted anchor is not water; a route already followed is never re-judged | decided |
| R8 | **The draft's entry point is `onDestinationPositionChanged(newPosition)`** | decided |
| R9 | **The following mode's entry point is `onOriginPositionChanged(newPosition)`** — told on each acquisition entry, and by nothing else | decided |
| R10 | **The automatic refresh is removed** — nothing re-asks while a route is followed; the engine's `isReadyToRecompute()` loses its only caller and stays as the seam's promise | decided |
| R11 | **`isReadyToRecompute()` is kept as the seam's promise, not a clock** — nothing calls it while no automatic recompute exists; the app owns when to ask, which is now the user's own press | decided |
| R12 | **The refresh's switch leaves with the gate** — no `Freeze`/`Resume` and no `Abort`, no route-owned control surviving the clock that gave it its subject | decided |
| R13 | **An acquisition that cannot answer changes nothing** — the standing line stays the front line, **nothing is staled**, and the panel's own sentence says no route answers (R6); the failure reaches the user nowhere else | decided |
| R14 | **Stale means replaced, and the replaced routes form a ladder**: the display keeps `route.ladder.oldest.nb` + `route.ladder.latest.nb` of them, drawn from **20 % opacity for the oldest to 80 % for the newest**; a line is superseded only by a new acquisition answer, so the ladder grows on the user's own press | decided |
| R15 | **The acquisition's status names the phase and its stage** — a short state word (`Acquiring…`, `Route active`) in the header's right corner, and the acquisition's **stage** — a closed set of `@StringRes` ids the engine publishes, one per pipeline boundary — on the sentence line under the divider | decided |
| R16 | **The acquisition's outcomes**: **Acquire route** · **Confirm** · **Save track** · **Exit** — `Acquire route` never disabled, `Confirm` enabled only while a plan stands, `Save track` disabled once the front route is written, `Exit` phase-aware (R23) | decided |
| R17 | **The following panel's actions**: `Save track` · `Reroute` · `New route` · `Exit` — the save takes the accent and is disabled once the front route is written; the other three are outlined | decided |
| R18 | **`Confirm` means following** — the toggle stays on, the line stays drawn, and the camera returns **in the same frame** to the current fix in GPS mode and to the anchor coordinate in demo mode | decided |
| R19 | **The toggle's two on-phases differ**: a plain active face while the destination is acquired, and the same face with the **recording toggle's own pulsing dot** while a route is followed — the same 10 dp dot at the same corner inset, pulsing 1 → 0.3 over 800 ms, in the route's own colour | decided |
| R20 | **The faked demo speed is suspended while the destination is being placed and released once a route is followed** — the phase owns it, not the mode | decided |
| R21 | **The acquisition holds the camera** — it joins the auto-follow hold so an aiming pan is not recentred on the boat | decided |
| R22 | **An acquisition that cannot answer shows no invalid-target panel** — R13 covers it | decided |
| R23 | **Leaving the following phase asks first, by any of its three doors** — the toggle's exit, the panel's own **Exit** and the back key raise the same **Save track and Exit** · **Continue** · **Discard route** dialog (words and order revised 2026-09-23: the affirmative first, the neutral stay, the loss last), whose save writes the **front route** and is **disabled when nothing is unwritten**; the all-scope scope option is **withdrawn** (2026-09-24) and the question of its return is parked as a todo on the feature; inside the **acquisition** the panel's Exit and the back key are **phase moves** — back to route mode when the acquisition was entered from a followed route, out of the mode otherwise — while the toggle's off asks the same dialog wherever a route stands behind or a line is acquired; leaving an acquisition standing on nothing asks nothing, leaving **cancels the call in flight**, and a failure arriving afterwards is not toasted | decided |
| R24 | **The four details** are start · destination · distance · ETA | decided |
| R25 | **The session's routes are the ladder, and a save writes one track per route** — the session keeps every route the mode produced, active or stale, and the route-to-track link lives in the mode's session rather than on the track; each save writes the **front route** under the route's own name, and a route already saved is **renamed, never rewritten** (unconditionally, a hand-renamed track included) | decided |
| R26 | **The freeze does not outlive the route** — it left with the gate (R12): the session holds no freeze at all | decided |
| R27 | **The two refused ends read the same** — a refused aim and a refused origin both show the bold red crosshair | decided |
| R28 | **The placeholder's own fiction is fixed** — **15 kn on every leg**, its leg times following from that speed, and its direction from the origin to the destination; the value is the placeholder's constant and goes when the engine does, so the free-water pace setting does not move a dummy route while the placeholder ships | decided |

### 1.2 The route — the track a saved route becomes

| # | Requirement | Status |
|---|---|---|
| R29 | **The flag is `route`** — `plannedCourse` becomes `route` in Kotlin, keeping `@ProtoNumber(19)`, off by default, and reaching `TrackSummary` as its own `@ProtoNumber(19)` so the lists can read it | decided |
| R30 | **The flag survives an export and an import** — carried by the extension blob for everything Maro writes, with no standard GPX element added, and a foreign file arriving unflagged | decided |
| R31 | **The lists gain one filter — All · Tracks · Routes** — the two words the field itself uses, in both locales, riding the link the track filters already have | decided |
| R32 | **A route colour pair is set in Settings**, from/to, through the app's ordinary colour control | decided |
| R33 | **A route opacity ladder is set in Settings**, from/to, like the history and pinned roles | decided |
| R34 | **A route renders the same pinned or not** — its colour pair and its opacity ladder are its own, and the pinned and unpinned ladders belong to recorded tracks and are never applied to a route | decided |
| R35 | **Routes have their own render count**, opening at 5 like its sibling and bounded the same way, and a **pinned** route is drawn outside it, the pin being what marks a route already saved | decided |
| R36 | **The route's stroke joins the stroke family** (`track.width.route`), and its two rendering gates are route-scoped, shipped as `tracking.route.allowSpeedColor=false` and `tracking.route.allowSpeedArrows=true` | decided |
| R37 | **`allowSpeedColor` at `true`** lets a route be rendered with its speeds in colour; at `false` a route is always drawn in its colour pair | decided |
| R38 | **`allowSpeedArrows` at `true`** lets the arrows be painted on a route as on any track; at `false` a route never shows them — a veto on the drawer's own option, the direction being the origin to the destination | decided |
| R39 | **A route's card shows three cells — Dist · Total · Avg — and no others**: Max, Idle and Nav are dropped, nothing replaces them, the three keep the card's own grid without a redesign, and Total and Avg read as **estimates** while Dist stays measured | decided |
| R40 | **A route's header carries the date and time of the route's generation and finalisation** — not of the save — and its point count, and **no end time** | decided |
| R41 | **Resume and merge do not apply to a route** — no resume action on its row, the bulk resume skips it, and it is not a candidate for a merge | decided |
| R42 | **The dead track-colour keys are removed with what nothing read** — the four `tracking.color.*` keys, the three unread `AppSettings` fields, their `BuildConfig` fields, their prefs keys and the stored preferences themselves; the route pair is read by a `propColor` helper, and a value it cannot read falls back to its sibling's default while the app **shows an error at start** | decided |

## 2. The engine's interface

- **Two entry points, one per end:** `onDestinationPositionChanged(newPosition)` while the destination is being acquired, `onOriginPositionChanged(newPosition)` while a route is followed — and now also on the acquisition's own entry, each acquisition's anchor being told before its first search.
- **One validity question** for a point, answering a closed set of reason ids — used for the destination as it moves, and **on every acquisition entry** for the origin (R7), the fallback point being the one judged when the prediction is off water.
- **One veto, kept as a promise**, `isReadyToRecompute()` (R11): nothing calls it while no automatic recompute exists, and a future automatic mode is what it was written for.
- **One stage channel, new in rev 8**: the engine publishes the **stage** of a running search as a closed set of `@StringRes` ids — corridor, grid, search, pull, snap — nullable, cleared on every answer and on an abort, so the panel can say what is running without an engine holding a sentence.
- **A session, not a function:** an engine told that one end moved holds the other, which is where a cache may live.
- **Cancellation is the existing contract's**, and it is load-bearing: the previous call is cancelled when a new one starts (R4).
- **What the app owns:** when to ask (R2 — the user's own press), the anchor and its lead (R3), that only one call is open (R4), the drop of an answer nobody wants, and every sentence a user reads.

## 3. The interaction, state by state

**Acquiring the destination.** The slot carries the comment line, the actions and, once a plan stands, the data table; the aim is the screen centre; the camera is held (R21); the toggle shows its plain active face. Nothing is computed until **Acquire route** is pressed (R2). `Save track` writes the **front line** — the newest answer — and greys once that line has a track (R16).

| State | The slot's sentence | Actions |
|---|---|---|
| Armed, nothing acquired | "Place the destination, then acquire the route" | Acquire route · Exit |
| Destination refused | "Destination invalid: *[reason]*" + the red crosshair | Acquire route · Exit |
| Acquiring | the **stage** (corridor · grid · search · pull · snap) | Acquire route · Exit |
| A plan stands | the four details + the pin checkbox | **Confirm** · Acquire route · Save track · Exit |
| The front route is written | the four details + the pin checkbox | **Confirm** · Acquire route · Save track (disabled) · Exit |

**Following.** Entered by `Confirm`; left by Exit or the toggle, **each asking first** (R23). Its panel **is** the dashboard slot's content, so the epic's "no separate route panel exists" is superseded there. The toggle carries the pulsing dot (R19); the camera follows the boat (R18).

| State | The slot shows | Actions |
|---|---|---|
| Route active | Route active · the four details · the pin | Save track · Reroute · New route · Exit |
| The front route is written | Route active · the four details · the pin | Save track (disabled) · Reroute · New route · Exit |

- **No automatic refresh remains:** nothing re-asks while a route is followed (R10, R12), so the trip figure's own age is the only reading that says the line is old.
- **The acquisition is re-enterable:** `Reroute` returns to it and fires one acquisition to the same destination from the fresh anchor (R3); `New route` is the same move with the destination cleared and **nothing computed** until `Acquire route` is pressed; both keep the session set, and `Exit` inside the acquisition moves back to the route that was standing.
- **Replacement is the only thing that stales a route** (R14): when a new answer is accepted, the line it replaces joins the ladder.
- **The trip figure describes the front route** — the newest accepted line.

## 4. What the map shows

- **The front route** at full opacity: one polyline and one destination pin, in the track band above the tracks and below the markers.
- **The stale ladder** (R14): the replaced routes stay drawn, oldest at **20 %** and newest at **80 %**, the display keeping `route.ladder.oldest.nb` + `route.ladder.latest.nb` of them.
- **One pin serves the front route**; a ladder line keeps no pin of its own.
- **A refused point shows a bold red crosshair** on the aim ring, for both ends (R6, R27).
- **The pulsing dot** on the toggle reuses the recording toggle's own treatment (R19) — and because that dot is `TrackStatusIcon`'s today, **the dot becomes one home** shared by the two toggles rather than two copies of four values.
- **A saved route is a route** (R29): drawn as its own role of the track renderer, with its own pair, ladder, stroke and count (R32–R36), never with the recorded tracks' pinned ladders (R34).

## 5. Saving, naming and the session's set

- **The set is the ladder** (R25): every route the session produced, the front one and its stale predecessors alike, so the drawing and the save read one collection rather than two that can drift.
- **A save writes the front route**, under the route's own name `Route <generated-and-finalised instant>` (R40); the `· n/N` suffix belonged to the withdrawn all-scope option (R23) and goes with it, so the naming has **one home and one shape**.
- **Nothing is written twice**: a route with a track is renamed (unconditionally, a track the user renamed by hand included), the route-to-track link living in the mode's session and read through one predicate.
- **The link is the session's** — a map in the mode's own state from route to the track it wrote, dying with the mode; the track's own schema is untouched, `TrackFromCourse`'s KDoc having deliberately refused such a field.
- **The instant that dates and names a track is the route's generation and finalisation**, not the save (R40) — one value feeding the header and the name.
- **The name's base is the Tracks feature's own auto-name** `yyyy-MM-dd HH:mm` ([`Track.kt`](../../app/src/main/java/ykws/android/maro/data/track/Track.kt:59)); the `Route ` prefix is a fixed token and not a localised string, because a name is data rather than UI text.
- **The objection to that naming, kept for the record:** a coordinate destination has no human name, so every route of a session looks alike, and a user wanting "the one that went round the cape" has only the map; a second line in the track's description (the destination's coordinates) would fix it, at the cost of writing more than a name.

## 6. The route's own detail

- **The flag and the index** (R29): the rename touches the property and its KDoc, its writer, and the two KDocs that name it by hand; the lists never load a track, so the flag is added to `TrackSummary` and projected in the repository's own summary pass. An index written before the field reads `false` — a missing bool decoding as off — so the **rebuild is forced rather than hoped for**: the index carries a version stamp and the field's introduction bumps it, and a step that added no stamp would leave the filter answering nothing.
- **The filter** (R31): one `FilterAxisSpec` keyed `route` with three string resources and one branch in `TrackSummary.matchesFilter`, its model being the position axis added last; a live track stays exempt as it is from the date and position axes; the map follows the list while the link is on, which is the shipped behaviour of every axis and reversible by the decoupling control; no marker axis is added, a marker being unable to be a route.
- **The keys and their taxonomy** (R32, R33, R35, R36): **a value joins its family and a route is a role inside those families** — `tracking.color.routeFrom`/`routeTo` beside `pastFrom`/`pastTo`, `tracking.transparency.routeFrom`/`routeTo` beside `from`/`to`, `tracking.route.render.nb` beside `tracking.render.nb`, and `track.width.route` beside `.live`, `.selected`, `.pinned`, `.history`. The objection, stated once: a route's values then live in three families, so "everything about a route" is a search rather than one prefix — accepted, because re-use beats a parallel subtree.
- **The chain every value rides:** a key in `maro.properties` → a `buildConfigField` → the `AppSettings` default → the user's override; the two gates are `AppSettings` booleans beside `trackArrows`, and the colour pair is injected through the `propColor` helper R42 names.
- **The rendering** (R34, R36, R37, R38): the route role is decided **before** the pinned one, so a pinned route takes its own pair and its own ladder whatever its pin says; the pair interpolates through the same two helpers the history pair uses, with the route set supplying the index and the total; the count bounds the route set alone while a pinned route escapes it; the speed-colour gate replaces the ramp for a route and the arrow gate vetoes the arrows — a route's direction being its origin to its destination, so no bearing has to be stored for the pass to have one.
- **The card and its refusals** (R39, R40, R41): three cells and two estimate labels; the header's date, point count and no end time; and **one predicate on the summary** read by both the row's resume and the bulk action, with merge dropping a route from its candidate set — a merged route would mix plan and measurement with nothing on screen saying so.

## 7. The technical details settled (the agent's)

- **The rule applied:** a choice that changes nothing a user can see is the agent's, so what the requirements leave to the implementer is decided here — and every value below is a **starting value in one file**, tunable without touching the shape of anything the user sees.
- **The anchor's lead** (R3): `route.anchor.leadSec=10`, bounded 0–60 — 0 is the plain live fix and a device that answers no course or speed reads as 0. The lead is a **horizon, not a latency budget**: it does not absorb the acquisition's own compute time, and it is read **once, on the acquisition's own entry edge, by one pure helper**, so no frame can move the anchor.
- **The lead's pace is the boat's own speed over ground**, not the set free-water pace: the quantity is where the boat *will be*, not where it might sail.
- **The removed keys** (R2, R10, R12): `route.ask.minTargetMoveM`, `route.ask.settleMs`, `route.refresh.intervalSec` and `route.refresh.offRouteM` leave with their accessors, bounds and KDocs — a key whose reader is gone is dead configuration, not a spare lever.
- **The ladder's caps** (R14): `route.ladder.oldest.nb=1` and `route.ladder.latest.nb=3`, so at most four stale lines stand beside the front one.
- **The crosshair's drawing values** (R6, R27): `route.target.color=#FFD32F2F` — bold red, spelled `#AARRGGBB` as the line's own key is — with `route.target.widthDp=3` and `route.target.pulseMs=800`, the pulse taking the toggle dot's own 1 → 0.3 alpha shape.
- **The worker** (R4): one `Job` in `RouteViewModel`; an aim landing while it runs **cancels** it and starts the new ask rather than queueing; the pending slot keeps the newest aim alone; the standing plan is deliberately **not** cleared on abort.
- **The validity question's shape** (R6, R7): one `suspend` question for a point, answering `null` for a usable point or a `@StringRes` id from a closed set — shaped like `RouteUnavailableReason`, so no engine holds user-facing text.
- **The couplings' wiring** (R18, R20, R21): the acquisition joins the hold [`PanResumeTimer`](../../app/src/main/java/ykws/android/maro/ui/map/PanResumeTimer.kt:55) already keeps for the wizard and the cards; the demo suspension becomes **phase-keyed** rather than toggle-keyed; and both are released at confirmation in the same frame, the centre returning to the current fix in GPS mode and to the anchor coordinate in demo mode.
- **The drawing path**: the map objects are attached **once** and mutated in place — the polyline pool and the pin created at the host's own composition, their points, colour and transparency updated per answer — which is the shape the contour polylines already ship.
- **The route's own values** (R32, R33, R35, R36): `tracking.color.routeFrom` / `routeTo`, `tracking.transparency.routeFrom` / `routeTo`, `tracking.route.render.nb=5` (bounded 0–20 like its sibling), `track.width.route`, and the gates `tracking.route.allowSpeedColor=false` / `tracking.route.allowSpeedArrows=true`.
- **The `propColor` helper** (R42): it parses the app's live colour format where the family's `propInt` cannot carry an ARGB value; a value it cannot read falls back to its sibling's literal and the app **shows an error at start**, a silent fallback being the trap the finding is about.

## 8. What this contradicts in the corpus today

- The epic said **no route panel exists** while a route is confirmed; §3 replaces that — the following phase's details and actions are the dashboard slot's own content, so nothing floats over the map.
- The epic says **only the toggle ends a route**; R17's Exit and R23's confirmation widen it.
- The epic's ask policy says **10 m** and a **settle**: R2 removes the timer and R3 replaces the trigger with the acquisition's own action, so the policy leaves whole.
- The epic's outcome names are the old four; R16 and R17 rename them, and the refresh's **Freeze/Abort** are gone (R12).
- `setRouteAiming` is keyed on the **toggle** ([`MapScreen`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1940)) where R20 wants the phase.
- [`PanResumeTimer`](../../app/src/main/java/ykws/android/maro/ui/map/PanResumeTimer.kt:46) holds the auto-follow resume for the drawer and for inspect and never for the route mode — the defect R21 fixes.
- The trip figure's **`stale` badge** ([`DashboardPanel`](../../app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt:363)) goes with R13, and its own **tap-to-recompute** goes with R12 — `Reroute` is the one door onto that act now.
- The two KDocs describing a leading-edge preview are rewritten with R2, and `routeAimPassed`'s first-aim arm — "the first aim of a session always passes" ([`RouteOverlay`](../../app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt:82)) — goes with R2.
- **The drawing path is the prerequisite** the ladder creates: the map objects are rebuilt on every state change today.
- **The four `tracking.color.*` keys never apply** — injected through a helper that cannot carry an ARGB value — and three of the fields they feed are read by nothing that draws; R42 removes both, with the stored preferences.
- **The Tracks epic's card, header and action rules** are amended by R39–R41; its own `plannedCourse` wording follows R29.

## 9. Open, and what is not built

- **Nothing is open in this book:** every requirement above is decided, and the build plans run from it.
- **Suggested, not built in this round:** **"Follow again"** where Resume sits for a recording — a route is the one stored thing a user could ask the app to sail. It waits because following a stored course needs the engine seam to accept a course rather than two ends, and the cost of the deferral is stated: with Resume and merge refused (R41), a route's card carries no forward-looking action at all.
- **Parked, elsewhere:** the Tracks walk's 2026-09-15 level still holds the eye's own value as a resume point; it is not part of this delivery.

## 10. What this file is not

- **Not a build plan:** no file list, no sequence, no acceptance numbers — it is the book the build plans are written from, and they point here rather than restating it.
- **Not the epics' rules:** they follow this file and are rewritten with it; they carry "stated and not yet written" until the work lands.
