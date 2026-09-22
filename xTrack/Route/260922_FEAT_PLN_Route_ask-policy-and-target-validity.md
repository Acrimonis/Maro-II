<!-- scope: feature -->
# Route — the master requirement book (the route mode and the trace it saves)

**Date:** 2026-09-22 · **Branch:** `feature/route-dummy` · **Status:** in design — rev 7, the **master requirement book**: every requirement of the route mode and of the **trace** a saved route becomes, with the technical details settled (§7) and nothing open; nothing implemented.
**Note on the name:** opened as "the ask policy, the serial pipeline and target validity", now the master book of the whole delivery; the filename lags its content and a rename is the user's to authorise.
**Corrects:** the rule statements written into [`FEAT_DSC_Route.md`](FEAT_DSC_Route.md)'s three sections and the walk items it carries, where they disagree; the trace half likewise corrects the rule statements in [`../Tracks/FEAT_DSC_Tracks.md`](../Tracks/FEAT_DSC_Tracks.md) and the Tracks walk's 2026-09-22 level.
**The plans reference this file and never restate it:** the route mode's build order is [`260922_FEAT_PLN_Route_interaction-build.md`](260922_FEAT_PLN_Route_interaction-build.md) and the trace work's is [`../Tracks/260922_FEAT_PLN_Tracks_trace-flag-and-display.md`](../Tracks/260922_FEAT_PLN_Tracks_trace-flag-and-display.md); both point back here rather than restating a requirement, so a change to what the delivery does is a change here first.

## 1. The requirement book

`decided` = buildable as written; `open` = an answer is still owed (§9).

### 1.1 The route mode

| # | Requirement | Status |
|---|---|---|
| R1 | **The route is a straight line** — the placeholder draws one segment; no water, zone or berth is promised while it ships | decided |
| R2 | **Nothing is computed until the user drags** — no ask on the arming frame | decided |
| R3 | **The ask gate is a fixed 25 m move on the ground and a 300 ms settle**, `maro.properties` keys behind `AppConfig` | decided |
| R4 | **A new ask aborts the one in flight and starts** — never two alive, never a queue; **one worker serves the asks and the refresh alike**, and that sharing is pinned by test | decided |
| R5 | **A hang is assumed away** — no per-call ceiling; the risk is named, not mitigated | decided |
| R6 | **A refused destination shows a bold red crosshair** and the panel's sentence naming the reason, the outcomes staying hidden while no plan exists | decided |
| R7 | **The origin is judged once, when the mode is armed, and never on a refresh** | decided |
| R8 | **The draft's entry point is `onDestinationPositionChanged(newPosition)`** | decided |
| R9 | **The following mode's entry point is `onOriginPositionChanged(newPosition)`** | decided |
| R10 | **The app owns the ask side of the refresh**: its gate is time-based and/or distance-from-route, both keys, and the engine's `isReadyToRecompute()` may only **veto** — say no while it is unable — never trigger | decided |
| R11 | **`isReadyToRecompute()` is a veto, not a clock** — the app's gate decides when to ask, the engine answers whether it can take the call | decided |
| R12 | **The refresh has a switch** — `Freeze`/`Resume`, its label naming the action it offers, and **Abort** offered instead while a refresh runs | decided |
| R13 | **A failed refresh changes nothing** — the standing line stays the front line, **nothing is staled**, and the failure reaches the user as a toast carrying the engine's reason; the trip figure's old `stale` badge goes with it | decided |
| R14 | **Stale means replaced, and the replaced routes form a ladder**: the display keeps `route.ladder.oldest.nb` + `route.ladder.latest.nb` of them, drawn from **20 % opacity for the oldest to 80 % for the newest** | decided |
| R15 | **The dashboard carries a status** in the info area while a refresh runs, with the `Re-Computing Route…` sentence | decided |
| R16 | **The draft's outcomes**: **Route** · **Save as Track and Route** · **Save as Track and Exit** · **Cancel**, with one pin checkbox governing the saves | decided |
| R17 | **The following panel's actions**: `Freeze/Resume` · `Save as Track` · `Exit`, with **Abort** replacing the first while a refresh runs | decided |
| R18 | **`Route` means following** — the toggle stays on, the line stays drawn, and the camera returns **in the same frame** to the current fix in GPS mode and to the origin coordinate in demo mode | decided |
| R19 | **The toggle's two on-phases differ**: a plain active face while the destination is chosen, and the same face with the **recording toggle's own pulsing dot** while a route is followed — the same 10 dp dot at the same corner inset, pulsing 1 → 0.3 over 800 ms, in the route's own colour | decided |
| R20 | **The faked demo speed is suspended while the destination is being placed and released once a route is followed** — the phase owns it, not the mode | decided |
| R21 | **The draft holds the camera** — it joins the auto-follow hold so an aiming pan is not recentred on the boat | decided |
| R22 | **A refresh that cannot answer shows no invalid-target panel** — R13 covers it | decided |
| R23 | **Leaving the following phase asks first, by either door** — the toggle's exit **and** the panel's own **Exit** raise the same Continue · End without saving · End and save dialog, its save carrying the last/all scope, because that Exit can save the front route only; **leaving the draft asks nothing**; leaving **cancels the call in flight**, and a failure arriving afterwards is not toasted | decided |
| R24 | **The four details** are start · destination · distance · ETA | decided |
| R25 | **The session's routes are the ladder, and the save writes them once each** — the all-scope save writes every route the session produced, active or stale, named `Route <generated-and-finalised instant> · n/N` in creation order, the option disabled while fewer than two routes exist; a route already saved is **renamed, never rewritten** (unconditionally, a hand-renamed track included), the already-saved ones are pinned as the set is saved, saving all twice writes nothing the second time, and the route-to-track link lives in the mode's session rather than on the track | decided |
| R26 | **Freeze does not outlive the route** — it is the session's own state, forgotten when the mode ends | decided |
| R27 | **The two refused ends read the same** — a refused aim and a refused origin both show the bold red crosshair | decided |
| R28 | **The placeholder's own fiction is fixed** — **15 kn on every leg**, its leg times following from that speed, and its direction from the origin to the destination; the value is the placeholder's constant and goes when the engine does, so the free-water pace setting does not move a dummy route while the placeholder ships | decided |

### 1.2 The trace — the track a saved route becomes

| # | Requirement | Status |
|---|---|---|
| R29 | **The flag is `trace`** — `plannedCourse` becomes `trace` in Kotlin, keeping `@ProtoNumber(19)`, off by default, and reaching `TrackSummary` as its own `@ProtoNumber(19)` so the lists can read it | decided |
| R30 | **The flag survives an export and an import** — carried by the extension blob for everything Maro writes, with no standard GPX element added, and a foreign file arriving unflagged | decided |
| R31 | **The lists gain one filter — All · Tracks · Traces** — the two words the field itself uses, in both locales, riding the link the track filters already have | decided |
| R32 | **A trace colour pair is set in Settings**, from/to, through the app's ordinary colour control | decided |
| R33 | **A trace opacity ladder is set in Settings**, from/to, like the history and pinned roles | decided |
| R34 | **A trace renders the same pinned or not** — its colour pair and its opacity ladder are its own, and the pinned and unpinned ladders belong to recorded tracks and are never applied to a trace | decided |
| R35 | **Traces have their own render count**, opening at 5 like its sibling and bounded the same way, and a **pinned** trace is drawn outside it, the pin being what marks a route already saved | decided |
| R36 | **The trace's stroke joins the stroke family** (`track.width.trace`), and its two rendering gates are trace-scoped, shipped as `tracking.trace.allowSpeedColor=false` and `tracking.trace.allowSpeedArrows=true` | decided |
| R37 | **`allowSpeedColor` at `true`** lets a trace be rendered with its speeds in colour; at `false` a trace is always drawn in its colour pair | decided |
| R38 | **`allowSpeedArrows` at `true`** lets the arrows be painted on a trace as on any track; at `false` a route never shows them — a veto on the drawer's own option, the direction being the origin to the destination | decided |
| R39 | **A route's card shows three cells — Dist · Total · Avg — and no others**: Max, Idle and Nav are dropped, nothing replaces them, the three keep the card's own grid without a redesign, and Total and Avg read as **estimates** while Dist stays measured | decided |
| R40 | **A route's header carries the date and time of the route's generation and finalisation** — not of the save — and its point count, and **no end time** | decided |
| R41 | **Resume and merge do not apply to a route** — no resume action on its row, the bulk resume skips it, and it is not a candidate for a merge | decided |
| R42 | **The dead track-colour keys are removed with what nothing read** — the four `tracking.color.*` keys, the three unread `AppSettings` fields, their `BuildConfig` fields, their prefs keys and the stored preferences themselves; the trace pair is read by a `propColor` helper, and a value it cannot read falls back to its sibling's default while the app **shows an error at start** | decided |

## 2. The engine's interface

- **Two entry points, one per phase:** `onDestinationPositionChanged(newPosition)` while the destination is being chosen, `onOriginPositionChanged(newPosition)` while a route is followed.
- **One validity question** for a point, answering a closed set of reason ids — used for the destination as it moves, and **once at arming** for the origin, never on a refresh (R7).
- **One veto**, `isReadyToRecompute()` (R11): the app's gate asks it before firing a refresh, and a "no" only delays.
- **A session, not a function:** an engine told that one end moved holds the other, which is where a cache may live.
- **Cancellation is the existing contract's**, and it is load-bearing: the previous call is cancelled when a new one starts (R4).
- **What the app owns:** when to ask (R2, R3), the refresh's two thresholds (R10), that only one call is open (R4), the drop of an answer nobody wants, and every sentence a user reads.

## 3. The interaction, state by state

**Choosing the destination.** The slot carries the sentence and the actions; the aim is the screen centre; the camera is held (R21); the toggle shows its plain active face.

| State | The slot's sentence | Actions |
|---|---|---|
| Armed, nothing asked | "Drag the map to the destination" | Cancel |
| Destination refused | "Destination invalid: *[reason]*" + the red crosshair | Cancel |
| Computing | "Computing Route…" | Cancel |
| Traced | the four details + the pin checkbox | **Route** · **Save as Track and Route** · **Save as Track and Exit** · **Cancel** |

**Following.** Entered by `Route` or `Save as Track and Route`; left by Exit or the toggle, **each asking first** (R23). Its panel **is** the dashboard slot's content, so the epic's "no separate route panel exists" is superseded there. The toggle carries the pulsing dot (R19); the camera follows the boat (R18).

| State | The slot shows | Actions |
|---|---|---|
| Routing active | Routing active · the four details · the status | Freeze/Resume · Save as Track · Exit |
| Re-computing | "Re-Computing Route…" · the four details · the status | **Abort** · Save as Track · Exit |
| Frozen | Routing active · the four details · the status reads frozen | Resume · Save as Track · Exit |

- **The refresh cycle:** the app's gate asks `isReadyToRecompute()` when its time and/or distance thresholds say the moment may have come; on yes it fires `onOriginPositionChanged(newPosition)`, aborting whatever is in flight (R4); the status and the sentence say "Re-Computing Route…"; **a failed answer changes nothing on the map** and arrives as a toast (R13); **Abort** cancels the refresh alone; nothing about the origin is validated (R7).
- **Replacement is the only thing that stales a route** (R14): when a new answer is accepted, the line it replaces joins the ladder.
- **Freeze** stops the gate's clock and nothing else: the trip figure keeps counting down on the standing line, the toggle stays on, the status says frozen, and it is forgotten when the mode ends (R26).
- **The trip figure describes the front route** — the newest accepted line.

## 4. What the map shows

- **The front route** at full opacity: one polyline and one destination pin, in the track band above the tracks and below the markers.
- **The stale ladder** (R14): the replaced routes stay drawn, oldest at **20 %** and newest at **80 %**, the display keeping `route.ladder.oldest.nb` + `route.ladder.latest.nb` of them.
- **One pin serves the front route**; a ladder line keeps no pin of its own.
- **A refused point shows a bold red crosshair** on the aim ring, for both ends (R6, R27).
- **The pulsing dot** on the toggle reuses the recording toggle's own treatment (R19) — and because that dot is `TrackStatusIcon`'s today, **the dot becomes one home** shared by the two toggles rather than two copies of four values.
- **The failure toast** (R13) is the app's own snackbar surface: themed, queued with the app's other messages, and already there.
- **A saved route is a trace** (R29): drawn as its own role of the track renderer, with its own pair, ladder, stroke and count (R32–R36), never with the recorded tracks' pinned ladders (R34).

## 5. Saving, naming and the session's set

- **The set is the ladder** (R25): every route the session produced, the front one and its stale predecessors alike, so the drawing and the save read one collection rather than two that can drift.
- **The all-scope save writes one track per route**, named `Route <generated-and-finalised instant> · n/N` in creation order, and the option is disabled while the session holds fewer than two routes — with one route, the draft's own save already does it.
- **Nothing is written twice**: a route with a track is renamed into the set (unconditionally, a track the user renamed by hand included) and the already-saved ones are pinned as the set is saved, so the pin marks what is already stored. Saving all twice therefore writes nothing the second time.
- **The link is the session's** — a map in the mode's own state from route to the track it wrote, dying with the mode; the track's own schema is untouched, `TrackFromCourse`'s KDoc having deliberately refused such a field.
- **The instant that dates and names a track is the route's generation and finalisation**, not the save (R40) — one value feeding the header and the name.
- **The name's base is the Tracks feature's own auto-name** `yyyy-MM-dd HH:mm` ([`Track.kt`](../../app/src/main/java/ykws/android/maro/data/track/Track.kt:59)); the `Route ` prefix is a fixed token and not a localised string, because a name is data rather than UI text.
- **The objection to that naming, kept for the record:** a coordinate destination has no human name, so every route of a session looks alike apart from its index, and a user wanting "the one that went round the cape" has only the map; a second line in the track's description (the destination's coordinates) would fix it, at the cost of writing more than a name.

## 6. The trace's own detail

- **The flag and the index** (R29): the rename touches the property and its KDoc, its writer, and the two KDocs that name it by hand; the lists never load a track, so the flag is added to `TrackSummary` and projected in the repository's own summary pass. An index written before the field reads `false` — a missing bool decoding as off — so the **rebuild is forced rather than hoped for**: the index carries a version stamp and the field's introduction bumps it, and a step that added no stamp would leave the filter answering nothing.
- **The filter** (R31): one `FilterAxisSpec` keyed `trace` with three string resources and one branch in `TrackSummary.matchesFilter`, its model being the position axis added last; a live track stays exempt as it is from the date and position axes; the map follows the list while the link is on, which is the shipped behaviour of every axis and reversible by the decoupling control; no marker axis is added, a marker being unable to be a trace.
- **The keys and their taxonomy** (R32, R33, R35, R36): **a value joins its family and a trace is a role inside those families** — `tracking.color.traceFrom`/`traceTo` beside `pastFrom`/`pastTo`, `tracking.transparency.traceFrom`/`traceTo` beside `from`/`to`, `tracking.trace.render.nb` beside `tracking.render.nb`, and `track.width.trace` beside `.live`, `.selected`, `.pinned`, `.history`. The objection, stated once: a trace's values then live in three families, so "everything about a trace" is a search rather than one prefix — accepted, because re-use beats a parallel subtree.
- **The chain every value rides:** a key in `maro.properties` → a `buildConfigField` → the `AppSettings` default → the user's override; the two gates are `AppSettings` booleans beside `trackArrows`, and the colour pair is injected through the `propColor` helper R42 names.
- **The rendering** (R34, R36, R37, R38): the trace role is decided **before** the pinned one, so a pinned route takes its own pair and its own ladder whatever its pin says; the pair interpolates through the same two helpers the history pair uses, with the trace set supplying the index and the total; the count bounds the trace set alone while a pinned trace escapes it; the speed-colour gate replaces the ramp for a trace and the arrow gate vetoes the arrows — a route's direction being its origin to its destination, so no bearing has to be stored for the pass to have one.
- **The card and its refusals** (R39, R40, R41): three cells and two estimate labels; the header's date, point count and no end time; and **one predicate on the summary** read by both the row's resume and the bulk action, with merge dropping a trace from its candidate set — a merged route would mix plan and measurement with nothing on screen saying so.

## 7. The technical details settled (the agent's)

- **The rule applied:** a choice that changes nothing a user can see is the agent's, so what the requirements leave to the implementer is decided here — and every value below is a **starting value in one file**, tunable without touching the shape of anything the user sees.
- **The ask keys** (R3): `route.ask.minTargetMoveM=25` — the number that already ships, moving home rather than changing — and `route.ask.settleMs=300`, joining `route.freeWaterPaceKn` · `route.line.*` · `route.pin.*` in `maro.properties` behind `AppConfig`.
- **The refresh gate's keys** (R10): `route.refresh.intervalSec=30` **or** `route.refresh.offRouteM=100`, read in the app, with the engine's veto asked on each firing.
- **The ladder's caps** (R14): `route.ladder.oldest.nb=1` and `route.ladder.latest.nb=3`, so at most four stale lines stand beside the front one.
- **The crosshair's drawing values** (R6, R27): `route.target.color=#FFD32F2F` — bold red, spelled `#AARRGGBB` as the line's own key is — with `route.target.widthDp=3` and `route.target.pulseMs=800`, the pulse taking the toggle dot's own 1 → 0.3 alpha shape.
- **The worker** (R4): one `Job` in `RouteViewModel`; an aim landing while it runs **cancels** it and starts the new ask rather than queueing; the pending slot keeps the newest aim alone; the standing plan is deliberately **not** cleared on abort; and the refresh fires its call on that same worker, the sharing pinned by test.
- **The validity question's shape** (R6, R7): one `suspend` question for a point, answering `null` for a usable point or a `@StringRes` id from a closed set — shaped like `RouteUnavailableReason`, so no engine holds user-facing text.
- **The couplings' wiring** (R18, R20, R21): the draft joins the hold [`PanResumeTimer`](../../app/src/main/java/ykws/android/maro/ui/map/PanResumeTimer.kt:55) already keeps for the wizard and the cards; the demo suspension becomes **phase-keyed** rather than toggle-keyed; and both are released at confirmation in the same frame, the centre returning to the current fix in GPS mode and to the origin coordinate in demo mode.
- **The drawing path**: the map objects are attached **once** and mutated in place — the polyline pool and the pin created at the host's own composition, their points, colour and transparency updated per answer — which is the shape the contour polylines already ship.
- **The trace's own values** (R32, R33, R35, R36): `tracking.color.traceFrom` / `traceTo`, `tracking.transparency.traceFrom` / `traceTo`, `tracking.trace.render.nb=5` (bounded 0–20 like its sibling), `track.width.trace`, and the gates `tracking.trace.allowSpeedColor=false` / `tracking.trace.allowSpeedArrows=true`.
- **The `propColor` helper** (R42): it parses the app's live colour format where the family's `propInt` cannot carry an ARGB value; a value it cannot read falls back to its sibling's literal and the app **shows an error at start**, a silent fallback being the trap the finding is about.

## 8. What this contradicts in the corpus today

- The epic said **no route panel exists** while a route is confirmed; §3 replaces that — the following phase's details and actions are the dashboard slot's own content, so nothing floats over the map.
- The epic says **only the toggle ends a route**; R17's Exit and R23's confirmation widen it.
- The epic's ask policy says **10 m**; R3 fixes 25 m. Its **outcome names** are the old four; R16 renames them.
- `setRouteAiming` is keyed on the **toggle** ([`MapScreen`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1940)) where R20 wants the phase.
- [`PanResumeTimer`](../../app/src/main/java/ykws/android/maro/ui/map/PanResumeTimer.kt:46) holds the auto-follow resume for the drawer and for inspect and never for the route mode — the defect R21 fixes.
- The trip figure's **`stale` badge** ([`DashboardPanel`](../../app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt:363)) goes with R13.
- The two KDocs describing a leading-edge preview are rewritten with R2 and R3, and `routeAimPassed`'s first-aim arm — "the first aim of a session always passes" ([`RouteOverlay`](../../app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt:82)) — goes with R2, the ask trigger living in [`RouteHost`](../../app/src/main/java/ykws/android/maro/ui/map/RouteHost.kt:175) rather than in the overlay.
- **The drawing path is the prerequisite** the ladder creates: the map objects are rebuilt on every state change today.
- **The four `tracking.color.*` keys never apply** — injected through a helper that cannot carry an ARGB value — and three of the fields they feed are read by nothing that draws; R42 removes both, with the stored preferences.
- **The Tracks epic's card, header and action rules** are amended by R39–R41; its own `plannedCourse` wording follows R29.

## 9. Open, and what is not built

- **Nothing is open in this book:** every requirement above is decided, and the two build plans run from it.
- **Suggested, not built in this round:** **"Follow again"** where Resume sits for a recording — a route is the one stored thing a user could ask the app to sail. It waits because following a stored course needs the engine seam to accept a course rather than two ends, and the cost of the deferral is stated: with Resume and merge refused (R41), a route's card carries no forward-looking action at all.
- **Parked, elsewhere:** the Tracks walk's 2026-09-15 level still holds the eye's own value as a resume point; it is not part of this delivery.

## 10. What this file is not

- **Not a build plan:** no file list, no sequence, no acceptance numbers — it is the book the two build plans are written from, and they point here rather than restating it.
- **Not the epics' rules:** they follow this file and are rewritten with it; they carry "stated and not yet written" until the work lands.
