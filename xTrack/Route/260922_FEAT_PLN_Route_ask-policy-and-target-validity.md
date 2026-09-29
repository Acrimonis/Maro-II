<!-- scope: feature -->
# Route — the master requirement book (the route mode and the route it saves)

**Date:** 2026-09-22 · **Branch:** `feature/route-avoid-workflow` · **Status:** in design — rev 9, the **master requirement book**: every requirement of the route mode and of the **route** a saved line becomes, with the technical details settled (§7) and nothing open; R1–R43 shipped, and §1.3's R44–R74 built on 2026-09-28 by [`260928_FEAT_PLN_Route_ui-flow-and-candidate-routes.md`](260928_FEAT_PLN_Route_ui-flow-and-candidate-routes.md). The saved line was called a **trace** until 2026-09-23, when the pair became **Track** against **Route**; every section below carries the new word. Rev 8 (2026-09-24) rewrote the acquisition onto an **explicit action** with no timer and no automatic refresh — the change [`260924_FEAT_PLN_Route_acquisition-and-route-workflow.md`](260924_FEAT_PLN_Route_acquisition-and-route-workflow.md) specifies; rev 9 (2026-09-28) moves the mode's two **ends into the drawer**, arms the acquisition on that standing pair and gives the engine's candidates their own rows, superseding **R14** and **R16**.
**Note on the name:** opened as "the ask policy, the serial pipeline and target validity", now the master book of the whole delivery; the filename lags its content and a rename is the user's to authorise.
**Corrects:** the rule statements written into [`FEAT_DSC_Route.md`](FEAT_DSC_Route.md)'s three sections and the walk items it carries, where they disagree; the saved-route half likewise corrects the rule statements in [`../Tracks/FEAT_DSC_Tracks.md`](../Tracks/FEAT_DSC_Tracks.md) and the Tracks walk's 2026-09-22 level.
**The plans reference this file and never restate it:** the trace work's build order is [`../Tracks/260922_FEAT_PLN_Tracks_trace-flag-and-display.md`](../Tracks/260922_FEAT_PLN_Tracks_trace-flag-and-display.md) — the route mode's own build order having shipped on 2026-09-22 and been archived — so a plan points back here rather than restating a requirement, and a change to what the delivery does is a change here first.

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
| R14 | ~~**Stale means replaced, and the replaced routes form a ladder**~~ — **superseded 2026-09-28 by R65**: with no reroute the mode holds one answer per arming, so nothing inside it can replace a line; the two keys, the stale painting, its 20–80 % band and this rule left together, the polyline pool a candidate draws into staying | superseded |
| R15 | **The acquisition's status names the phase and its stage** — a short state word in the header's right corner, and the acquisition's **stage** — a closed set of `@StringRes` ids the engine publishes, one per pipeline boundary — **inside that same word**, `Acquiring (Search)…`, the sentence line under the divider carrying the refusals and the no-route word alone (**amended 2026-09-28**, the user's word: the stage rode that sentence line until then); **the stage rides one emission with the line the engine holds at that instant** (R43) | decided |
| R43 | **The engine publishes the line as it builds it** — at each pipeline boundary the same emission that carries the stage also carries the line the pipeline holds: null through corridor · grid · search, the raw cell chain at `PULL` and the pulled line at `SNAP`; the line has no consumer but the map, is cleared on every answer and every abort, and is never the plan | decided |
| R16 | ~~**The acquisition's outcomes**: **Acquire route** · **Confirm** · **Save track** · **Exit**~~ — **superseded 2026-09-28 by R50, R55 and R57**: arming is the trigger so no placement press exists, `Confirm` becomes `Select route`, `Acquire route` and `Exit` leave the grid, and the three that remain are **Save to track · Select route · Cancel** | superseded |
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
| R37 | **`allowSpeedColor` gates the Colours chip for a route** (amended 2026-09-29): a route is rendered with its speeds in colour only while the key is `true` **and** the Colours chip is on; at `false` — or with the chip off — a route is always drawn in its colour pair | decided |
| R38 | **`allowSpeedArrows` at `true`** lets the arrows be painted on a route as on any track; at `false` a route never shows them — a veto on the drawer's own option, the direction being the origin to the destination | decided |
| R39 | **A route's card shows three cells — Dist · Total · Avg — and no others**: Max, Idle and Nav are dropped, nothing replaces them, the three keep the card's own grid without a redesign, and Total and Avg read as **estimates** while Dist stays measured | decided |
| R40 | **A route's header carries the date and time of the route's generation and finalisation** — not of the save — and its point count, and **no end time** | decided |
| R41 | **Resume and merge do not apply to a route** — no resume action on its row, the bulk resume skips it, and it is not a candidate for a merge | decided |
| R42 | **The dead track-colour keys are removed with what nothing read** — the four `tracking.color.*` keys, the three unread `AppSettings` fields, their `BuildConfig` fields, their prefs keys and the stored preferences themselves; the route pair is read by a `propColor` helper, and a value it cannot read falls back to its sibling's default while the app **shows an error at start** | decided |

### 1.3 The drawer's pair, the candidates and the navigation phase (2026-09-28)

**R44–R74**, settled by the user's word on 2026-09-28 and specified by
[`260928_FEAT_PLN_Route_ui-flow-and-candidate-routes.md`](260928_FEAT_PLN_Route_ui-flow-and-candidate-routes.md)
— which carries the same rows as this pass's own record, while this table is their home. They supersede
**R14** and **R16** above.

**R75–R80** (2026-09-29) are the map's **route fan** — the square the Add Zone door left, its five
children and their enablement by phase — settled by the user's word of 2026-09-29 and specified by
[`260929_FEAT_PLN_Route_map-fan-add-route.md`](260929_FEAT_PLN_Route_map-fan-add-route.md). They amend
**R49 · R50 · R51 · R59 · R60 · R73** above, which are struck through in place rather than removed.

| # | Requirement |
|---|---|
| R44 | **The route's two ends are chosen in the drawer** — a Route section under Navigation holding a start selector, a destination selector and one action that arms the acquisition on the standing pair; the map supplies only the Marker-position entry, and only before the trigger |
| R45 | **The start selector offers the current position, the standing marker position and every marker flagged origin** — the current position reading the shipped anchor rule, led `route.anchor.leadSec` in GPS mode and unled in demo |
| R46 | **Marker position is where the map's own marker stands when the action is pressed** — the boat's own position in demo mode; in GPS mode the dragged map's own centre, falling back to the fix led 10 s where the map still sits on the boat; offered by both selectors |
| R47 | **One item shows at a time and the list rolls under a vertical drag** — the new shared component whose shape R70 carries |
| R48 | **The pair persists per navigation mode** — keyed on the mode the drawer already carries, so GPS and demo remember their own |
| R49 | **The section's action arms the acquisition with the stored pair**, and the route toggle does the same when no route is on. **Amended 2026-09-29 (R75, R80): the map's fan puts two more arming doors behind one anchor** — its **Route (auto)** and **Route** children, auto leading (**re-amended 2026-09-29** with the order, R77) — while the parent itself arms nothing, so the rule reads *two arming children behind one anchor* |
| R50 | **Nothing is placed on the map any more** — the acquisition opens computing, so **Acquire route** leaves the grid, and the map is handed back the moment the ends are read (R71). **Amended 2026-09-29 (R75): the fan's two arming children open it exactly as the drawer's action and the toggle do**, being doors onto the one trigger |
| R51 | **The toggle shows three faces** — off, acquiring, navigating — green with a pulsing red dot while the search runs, that dot being shared with every other toggle (R69). **Amended 2026-09-29 (R76): the fan's parent wears the same three faces**, the `route` mark worn in all three at the stack's icon colour — **re-amended 2026-09-29**, the user's word: no glyph in the fan wears a hue, so those faces are told apart by that same dot alone and the mode's green-and-blue read is the map's own toggle's |
| R52 | **The acquisition paints its progress as the engine publishes it** — R43's shipped overlay, no new path |
| R53 | **Two candidate lines are computed beside the settled one**, painted as it is at lower opacity — the pair **cumulative**: the zones' price dropped, then that price and the 300 m band's together |
| R54 | **Next/prev appears as candidates land and loops the set** — the selected line at full strength, the others dimmed |
| R55 | **The acquisition's controls are Save to track · Select route · Discard route** — the save writing the **selected** line, the one the panel's table describes, the save and the selection sharing **one row** with the discard full width beneath them (**amended 2026-09-28**: `Cancel` became the red `Discard route`, on `route_exit_discard`'s own key) |
| R56 | **Select route writes nothing and discards the rest** — it shows the selected line as the route, drops the other candidates, and enters navigation |
| R57 | **Discard route leaves the acquisition and turns the toggle off, asking nothing** — the red face, the act unchanged from the `Cancel` it replaced (rewritten 2026-09-28) |
| R58 | **The navigation phase closes the panel and shows the route clearly** — the toggle blue with the pulsing red dot, and nothing else route-specific on screen (R73) |
| R59 | **Leaving navigation raises the one dialog** — Save Route to Track, disabled while the route has its track · Continue route · Discard Route. **Amended 2026-09-29 (R79): the fan's own Discard is a fourth door, and it is the only one that asks nothing** |
| R60 | **Routing confirms before tracking** — the route's exit answers first, the tracking exit only after it. **Amended 2026-09-29 (R79): the fan's Discard leaves without asking**, so the dialog stands for the toggle's off and the back key alone |
| R61 | **Which passes run, and what keeps their result, is configuration rather than code** — one row in `maro.properties` naming the passes in run order with the prices each drops, beside the conditions that keep their result (§8 of the plan); the presence test is **already in code** (`zones.isNotEmpty()`, `routeAvoidZone300Enabled && world.bandWidthM > 0.0`), so that key makes an existing guard configurable rather than adding one |
| R62 | **A candidate's row states what it saves** — the absolute time and that time as a share of the settled trip's own clock, as `Route #2 saves 12 min (22 %)` |
| R63 | **A candidate is discarded unless it saves at least `minSavingPct` of the trip's own clock** — shipped at **15**, set below the budget's own 25 as a first working value, to be tuned from a device reading |
| R64 | **One key dims the lines drawn beside the plan** — the line a search is still building and the unpicked candidates share `route.dimmed.transparencyPct`, told apart by motion and replacement rather than by paleness |
| R65 | **The stale ladder and its code are removed** — `route.ladder.oldest.nb` and `route.ladder.latest.nb` with their fields and stored preferences, the stale painting and its 20–80 % band, and the staleness pathway they fed, all leaving with R14. The **polyline pool stays**: `RouteHost` keeps its lines attached once and mutated in place, and that pool is what a candidate line draws into — the removal takes the staleness and its band, never the drawing path |
| R66 | **A selection that stops resolving falls back to its list's first entry** — Current position for the start and Marker position for the destination, the row naming what it holds and the dead id leaving the store |
| R67 | **The standing info answers for the selected route** — `RouteSummaryBlock` is kept beside the new section and its rows follow the **selection**, not the settled answer |
| R68 | **A better alternative is reported as a status above those rows** — where a candidate that saves time stands, one line above the info names it with its own saving, the rows beneath it unmoved. **The drawer's half is withdrawn 2026-09-28** (D5): the trigger shuts the menu and the summary stands in the routing phase alone, so a drawer line naming a saving can no longer be reached — the panel's own candidate rows already say it (R62, R74), and the requirement now speaks for the panel alone |
| R69 | **One pulsing dot serves every toggle** — a red mark of the UI's own rather than the mode's colour, so the recording toggle and the route toggle's two phases wear the same one; `MapPulseDot` keeps the geometry and reads that single colour, and the refused crosshair keeps `route.target.color`, unrelated to it |
| R70 | **Each end is a dropdown** — **superseded 2026-09-28 by the user's word, and its roller retired with its component**: the shape this row introduced — a list one visible row tall, stepping on a vertical drag claimed inside its own bounds — never committed reliably in the hand, the marked entry and the committed one disagreeing, and its instrumented trace was cut short by the retirement rather than answered by it. Each end is chosen from a **`DropdownRow`**, the app's own dropdown in the `CustomSortField` shape, **labelled by its role** — `Origin` and `Destination` — with the value on the row's right. What the row settled stands: a list that may grow past two entries is a dropdown's job, `docs/ui-component-guidelines.md` §2.12 owns its spec, and R66's fallback to a list's first entry is unchanged |
| R71 | **The ends are read at the trigger and the map is handed back** — the pair is taken at the instant the action is pressed, after which pan and zoom are free and change nothing about the search; the camera hold and the demo-speed suspension the aim needed leave with the aim |
| R72 | **The rows print the engine's own figures** — a candidate's saving and duration are shown as the engine publishes them, the UI recomputing nothing and asserting no basis of its own; the faired-or-un-faired question belongs to the engine's finalization |
| R73 | **The navigation phase adds no surface** — selecting returns the app to the ordinary dashboard, the map carrying the line, the toggle's face and the exit dialog being the mode's whole presence, and the drawer's summary the only route reading and only when opened. **Neither door opens the drawer on arming** (**amended 2026-09-28**, the user's word: the map's square used to open it, and the acquisition now lands on the panel alone). **Nor does the fan add a surface** (**amended 2026-09-29**, R75, R77): its parent opens an arc and arms nothing, and its children mirror the panel's and the dialog's own outcomes rather than becoming a third place where "written" or "selected" is decided |
| R74 | **The acquisition's own surface is the route acquisition dashboard** — the panel in the dashboard slot carries the status, the candidate rows and the three actions, the drawer standing above it for the parameters rather than instead of it |
| R75 | **The map's route square becomes a fan, and the square it replaces leaves** (2026-09-29) — the `cm` control's **Add Zone** door and its `onAddZone` plumbing go, and a **route fan** takes the slot: a parent that **opens the arc** and arms nothing, its five children being the route's own actions. The layer fan keeps its six children and the zoom and menu squares are untouched |
| R76 | **The parent states the mode and never arms** — R51's three faces live on it, and the two fans fade for each other: each parent hides only while the **other** is open, standing while its own is (`anyFanOpen && !isExpanded`, the layer fan's own form), so neither vanishes the instant its own arc opens |
| R77 | **The five children, read top to bottom on the screen, gated on the phase** — **Route (auto)** and **Route** in **Idle alone**, auto leading (**re-amended 2026-09-29**, the user's word: the order top to bottom is `bolt` · `route` · `save` · `logout` · `cancel`, **and that order is the screen's reading, never the list's** — a left-facing fan puts index 0 at the arc's **bottom** (198°, index 4 landing at 342°), so the children list runs **bottom to top** and opens on `cancel`; an arming belongs to Idle, R65); **Save to track** wherever a line stands and is unwritten (it mirrors the panel's own face, R55/R57); **Save to track and exit** in the **Following** phase alone (inside the acquisition "exit" is the phase move Discard, and it is unasked, R63); **Discard** wherever a line can be left, from the search itself to a written route, wearing `cancel` since 2026-09-29 in place of `delete_forever` so the mark reads *stop the route* rather than *destroy*. The fan mirrors the panel's and the dialog's outcomes and decides neither |
| R78 | **The arc is momentary, and a disabled child is inert** — `toggleChildren = false`, no active badge and a press closes the fan; the children **ignore `isActive`** and read **enablement** instead, so no glyph is dimmed by the momentary fan's own `false`. `MapControlButton` gains `enabled` and `FanLayout` gains `enabledStates`, both defaulting so the layer fan and every other caller are untouched; a **disabled child draws the inactive alpha** (`buttonActionIconInactiveAlpha`, 0.25) **with its press suppressed** — a quarter-strength glyph cannot mean *off* here, because nothing in this fan toggles |
| R79 | **The unasked Discard is the exit dialog's own fourth door** — the fan's **Discard** leaves the acquisition or the followed route and **asks nothing**, being the shortcut surface for the dialog's two leaving outcomes; the dialog itself stands for the toggle's off and the back key (R59, R60), and the loss the unasked door can take is an **unwritten** line |
| R80 | **Route (auto) takes the settled line, and nothing waits for an offer** — it arms as **Route** does and selects the **settled** answer, index 0 of the drawn set, the instant it exists (`Choosing.plan != null`), through the panel's own selection, with the panel standing while the search runs and giving way when that selection lands (R58). The flag clears on the selection, on an end and on a new arming, and this child is **never the default** — the candidate rows exist so the user compares (R62, R74), and auto-pick is the deliberate refusal to |

| R81 | **The paint order is a rank over the whole list, and nothing is pinned** (2026-09-29) — `reorder` sorts every overlay by a rank derived from the same predicates the bands already use — base 0 · stored 1 · route 2 · live 3 · marker 4 — with a **stable** sort, so the base keeps registration order and each tier keeps the order its own writers left it; **the first entry is no longer treated as the map's basemap**, because this app's list holds none, and a list already in that order is left untouched. The rule exists because the first entry used to be pinned as if it were the tile, and it was the route's settled line: painted at index 0 under every base layer (the device's own log, 2026-09-29) |

## 2. The engine's interface

- **Two entry points, one per end:** `onDestinationPositionChanged(newPosition)` while the destination is being acquired, `onOriginPositionChanged(newPosition)` while a route is followed — and now also on the acquisition's own entry, each acquisition's anchor being told before its first search.
- **One validity question** for a point, answering a closed set of reason ids — used for the destination as it moves, and **on every acquisition entry** for the origin (R7), the fallback point being the one judged when the prediction is off water.
- **One veto, kept as a promise**, `isReadyToRecompute()` (R11): nothing calls it while no automatic recompute exists, and a future automatic mode is what it was written for.
- **One stage channel, new in rev 8**: the engine publishes the **stage** of a running search as a closed set of `@StringRes` ids — corridor, grid, search, pull, snap — nullable, cleared on every answer and on an abort, so the panel can say what is running without an engine holding a sentence.
- **A session, not a function:** an engine told that one end moved holds the other, which is where a cache may live.
- **Cancellation is the existing contract's**, and it is load-bearing: the previous call is cancelled when a new one starts (R4).
- **What the app owns:** when to ask (R2 — the user's own press), the anchor and its lead (R3), that only one call is open (R4), the drop of an answer nobody wants, and every sentence a user reads.

## 3. The interaction, state by state

**Acquiring the route.** **The ends are the drawer's, read at the trigger, and the mode opens computing** (R44, R49, R50, R71): the slot carries the comment line, the actions and — once a line stands — the data table, the candidate rows and the next/prev pair. Nothing is placed on the map and there is no placement press; the drawer stands above the panel for the parameters (R74); the camera is free and the demo speed is not suspended (R71); the toggle wears its **green** face with the pulsing dot (R51). `Save to track` writes the **selected** line and greys once that line has a track (R55).

| State | The slot's sentence | Actions |
|---|---|---|
| Armed, nothing acquired | "The route follows the ends chosen in the route section" | Save to track (disabled) · Select route (disabled) · Discard route |
| Destination refused | "Destination invalid: *[reason]*" + the red crosshair | Save to track (disabled) · Select route (disabled) · Discard route |
| Acquiring | nothing of its own — the **stage** rides the header's acquiring word, `Acquiring (Search)…` (R15) | Save to track (disabled) · Select route (disabled) · Discard route |
| A line stands | the four details + the pin checkbox + the candidate rows | Save to track · **Select route** · Discard route |
| A line stands and is written | the four details + the pin checkbox + the candidate rows | Save to track (disabled) · **Select route** · Discard route |

**Navigating.** Entered by `Select route`; left by the toggle or the back key, **each asking first** (R59, R60). **It adds no surface at all** (R58, R73): the ordinary dashboard returns, the map carries the line, and the toggle's blue face with the one exit dialog is the mode's whole presence. The camera follows the boat (R18).

| State | What is on screen | Actions |
|---|---|---|
| Route active | the ordinary dashboard · the line · the blue toggle | the exit dialog's three, raised by the toggle or the back key |
| The route is written | the same | the dialog's save disabled (R59) |

- **No automatic refresh remains:** nothing re-asks while a route is followed (R10, R12), so the trip figure's own age is the only reading that says the line is old.
- **The mode holds one answer per arming** (R65): with no reroute the two moves R17 named left with the panel the brief closes, so nothing inside a mode can replace a line and the acquisition is entered from Idle alone.
- **The trip figure describes the followed route** — the line the selection took.

## 4. What the map shows

- **The selected line at full strength**: one polyline and one destination pin, in the track band above the tracks and below the markers.
- **The candidates beside it** (R53, R54, R64): every line the engine offered that was not picked, drawn at the one shared dimming key `route.dimmed.transparencyPct` — told apart by motion and replacement rather than by paleness; stepping the selection moves the emphasis, never the drawing.
- **One pin serves the selected line**, standing at its own resolved destination.
- **A refused point still shows a bold red crosshair** on the aim ring, for both ends (R6, R27), with the `route.target.color` the shared dot deliberately does not use (R69).
- **The pulsing dot** every toggle wears has **one home**, [`MapPulseDot`](../../app/src/main/java/ykws/android/maro/ui/map/MapPulseDot.kt) — a red mark of the UI's own, `ui.map.pulse.dot`, read by the recording square and by the route square's two on-phases alike (R69).
- **A saved route is a route** (R29): drawn as its own role of the track renderer, with its own pair, transparency, stroke and count (R32–R36), never with the recorded tracks' pinned ladders (R34).

## 5. Saving, naming and the session's set

- **The session holds one route per arming** (R25, R65): with no reroute a mode produces one answer, so the drawing and the save read one collection rather than two that can drift, and a selection that takes a candidate writes that line (R56).
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
- **The rendering** (R34, R36, R37, R38): the route role is decided **before** the pinned one, so a pinned route takes its own pair and its own ladder whatever its pin says; the pair interpolates through the same two helpers the history pair uses, with the route set supplying the index and the total; the count bounds the route set alone while a pinned route escapes it; the speed-colour gate joins the Colours chip for a route — both must be on to band it — as the arrow gate joins the Arrows chip (R38), a route's direction being its origin to its destination, so no bearing has to be stored for the pass to have one.
- **The card and its refusals** (R39, R40, R41): three cells and two estimate labels; the header's date, point count and no end time; and **one predicate on the summary** read by both the row's resume and the bulk action, with merge dropping a route from its candidate set — a merged route would mix plan and measurement with nothing on screen saying so.

## 7. The technical details settled (the agent's)

- **The rule applied:** a choice that changes nothing a user can see is the agent's, so what the requirements leave to the implementer is decided here — and every value below is a **starting value in one file**, tunable without touching the shape of anything the user sees.
- **The anchor's lead** (R3): `route.anchor.leadSec=10`, bounded 0–60 — 0 is the plain live fix and a device that answers no course or speed reads as 0. The lead is a **horizon, not a latency budget**: it does not absorb the acquisition's own compute time, and it is read **once, on the acquisition's own entry edge, by one pure helper**, so no frame can move the anchor.
- **The lead's pace is the boat's own speed over ground**, not the set free-water pace: the quantity is where the boat *will be*, not where it might sail.
- **The removed keys** (R2, R10, R12): `route.ask.minTargetMoveM`, `route.ask.settleMs`, `route.refresh.intervalSec` and `route.refresh.offRouteM` leave with their accessors, bounds and KDocs — a key whose reader is gone is dead configuration, not a spare lever.
- ~~**The ladder's caps**~~ — **removed 2026-09-28** (R65): `route.ladder.oldest.nb`, `route.ladder.latest.nb`, the stale painting and its 20–80 % band left with R14, and the **polyline pool stayed**, being what a candidate line draws into.
- **The candidates' own keys** (R61, R63, R64): `route.avoid.candidate.passes`, `route.avoid.candidate.minSavingPct` and `route.avoid.candidate.skipAbsent` for the passes and their floor, and one renamed `route.dimmed.transparencyPct` for every line drawn beside the plan — the line a search is still building and the unpicked candidates alike, which is why the name is not the progress one.
- **The crosshair's drawing values** (R6, R27): `route.target.color=#FFD32F2F` — bold red, spelled `#AARRGGBB` as the line's own key is — with `route.target.widthDp=3` and `route.target.pulseMs=800`, the pulse taking the toggle dot's own 1 → 0.3 alpha shape.
- **The worker** (R4): one `Job` in `RouteViewModel`; an aim landing while it runs **cancels** it and starts the new ask rather than queueing; the pending slot keeps the newest aim alone; the standing plan is deliberately **not** cleared on abort.
- **The validity question's shape** (R6, R7): one `suspend` question for a point, answering `null` for a usable point or a `@StringRes` id from a closed set — shaped like `RouteUnavailableReason`, so no engine holds user-facing text.
- ~~**The couplings' wiring**~~ (R18, R20, R21) — **the couplings left 2026-09-28** (R71): the camera hold and the demo-mode pan-speed suspension existed to stop an *aiming* pan from recentring and from moving the boat, and with the ends read at the trigger neither has a subject left, so [`PanResumeTimer`](../../app/src/main/java/ykws/android/maro/ui/map/PanResumeTimer.kt) lost its route term and `NavigationViewModel` lost the demo suspension. R18 stands: the camera returns to the current fix in GPS mode and to the anchor coordinate in demo mode when `Select route` is pressed.
- **The drawing path**: the map objects are attached **once** and mutated in place — the polyline pool and the pin created at the host's own composition, their points, colour and transparency updated per answer — which is the shape the contour polylines already ship.
- **The route's own values** (R32, R33, R35, R36): `tracking.color.routeFrom` / `routeTo`, `tracking.transparency.routeFrom` / `routeTo`, `tracking.route.render.nb=5` (bounded 0–20 like its sibling), `track.width.route`, and the gates `tracking.route.allowSpeedColor=false` / `tracking.route.allowSpeedArrows=true`.
- **The `propColor` helper** (R42): it parses the app's live colour format where the family's `propInt` cannot carry an ARGB value; a value it cannot read falls back to its sibling's literal and the app **shows an error at start**, a silent fallback being the trap the finding is about.

## 8. What this contradicts in the corpus today

**Added 2026-09-28 (R44–R74):** the drawer's own route group had left in favour of a read-only summary, and §1.3 puts the mode's **parameters** back into it — a Route section under Navigation, with the summary kept beside it (R44, R67). The aim ring and its `route.target.*` values stay, being the refused end's own surface (R6, R69), while the camera hold and the demo suspension that served the aim leave (R71). The epic's `## Sections` text, its `## Docs` list and its `## Implemented` pointer are corrected with this pass, and `docs/ui-drawer-guidelines.md` gains the section's entry.

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
