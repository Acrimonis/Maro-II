<!-- scope: feature -->
# Route — reverse the followed route when it loses ground, and cue arrival

## Ask

While a route is followed, when the boat is **going away from the destination**, swap the route's
origin and destination in the behaviour of the route while following (the user's word, 2026-10-08).
**Amended 2026-10-09** on the user's own reading of the first landing: the trigger is not the boat's
heading but the route's **time-to-go loss**, and the overshoot past the destination is a separate
functional case answered by a **new arrival cue**, not by a reversal.

## Why the heading trigger was dropped

- The route already knows its own direction: `RoutePlan.splitAt` / `remainingFrom` project the boat
  onto the line, and that projection works in demo mode too — the user follows a route correctly
  there — so the boat's own position is enough.
- A heading is both fragile and wrong here: routes curve back on themselves, so a boat on a valid
  line can point away from the destination for long stretches; and demo has no course at all
  (`demoBearingDeg` is reserved and never set), so the heading rule was inert exactly where the user
  tested it.
- The time-to-go is a property of the route's own progression, so it survives curves and needs no
  course, no speed gate and no live GPS fix.

## Settled behaviour

- **In-place mirror, never a new search.** The followed `RoutePlan` reverses: `points` and
  `legTimesSec` reversed, `start` and `destination` exchanged, the resolved destination becoming the
  new start. The line's geometry and its clock are untouched, so the drawn water stays byte-identical
  and no lookup runs.
- **Trigger — the time-to-go loss, as a toggle.** While `Following`, the reading **toggles** once the
  trip figure's own time-to-go is `route.follow.swap.lossSec` worse than its own recent low; a fall or
  a flat reading is the ordinary progressing state and does nothing. Purely positional, so demo and
  GPS behave alike.
- **The low is what makes it honest.** The low is the smallest time-to-go inside a look-back of twice
  the loss, so the route turns the moment the loss accrues — fast on a real reversal — while a drift
  slower than half the route's own pace never counts.
- **Arrival is a cue, and a reversal outranks it.** When the time-to-go falls through
  `route.follow.arrival.etaSec` while still closing, the mode raises the exit dialog's own doors under
  a *reached your destination* prompt; a reversal while it is open closes it as a **no action**
  dismissal, and a fresh approach re-prompts. The flip is never gated by the threshold: what keeps it
  quiet at the destination is the time-to-go itself, flat while the projection is clamped to the
  route's end.
- **The route keeps its identity.** A mirrored route still saves, lists and reopens as the same
  route: the session's route-to-track link, `isRouteSaved` and the persisted end ids (R82) do not
  fork with the read direction.

## The trigger — the time-to-go loss

- Signal: the trip figure's own `remainingFrom(boat).durationSec` — the number the dashboard cell and
  the drawer band already show, read from the boat marker in demo and the GPS fix in GPS mode.
- `RouteViewModel` keeps a short timestamped history of that number, sampled per fix but **bucketed to
  about one a second** on `SystemClock` elapsed-realtime, so a demo pan's sixty-a-second centre updates
  do not churn it.
- The **low** is the smallest sample inside a look-back of twice `lossSec`; the toggle fires when
  `eta(now) - low >= lossSec`, and a delta below that is the ordinary progressing state. One pure
  function, `routeEtaToggle(...)`, in `RouteOverlay.kt` beside the feature's other pure rules.
- **The history clears at every flip** — the time-to-go jumps to the other end's value when the plan
  reverses, so the samples behind it belong to the other orientation; without the clear that stale low
  would make the jump read as a loss and turn the line straight back.
- **A debounce spaces the toggles**: after a flip no further flip for `route.follow.swap.debounceSec`,
  so a boat manoeuvring across one spot cannot chatter the reading.
- Values, each in `maro.properties` read through `AppConfig`, the file the source of truth, with the
  comment each carries:
  - `route.follow.swap.lossSec` **30** — how much time-to-go you must lose before the route turns
    around; small drifts do not count.
  - `route.follow.swap.debounceSec` **15** — how long the route leaves you alone after turning, so a
    bit of rocking does not turn it back.
  - `route.follow.arrival.etaSec` **60** — how close you must be, in minutes left, before the app asks
    whether you have arrived.
- The **look-back is derived, not a dial**: twice `lossSec`, so the loss must accrue within sixty
  seconds at the shipped value and the real gate is *losing ground at least half as fast as the route
  expects to gain it*. Written in `lossSec`'s own comment.
- The look-back also **drops samples older than its span**, so a background pause leaves no stale low
  behind and the mode resumes from the values it has.
- **A known limit**: the loss sizes the shortest route the flip can act on — a line whose whole
  remaining time never exceeds `lossSec` behind the boat cannot accrue it, so the flip is inert there
  and only the arrival cue speaks.

## The arrival cue

- Crossing: when `remainingFrom(boat).durationSec` falls through `route.follow.arrival.etaSec` (60 s)
  **as a new look-back low** — a genuine approach, not a reversal drifting back in — the mode raises
  the existing exit dialog (`MapDialogHost`) under a *you seem to have reached your destination*
  prompt, with the same doors — *Save Route to Track · Continue route · Discard Route*, or *Stop
  following* for a followed saved route.
- **One prompt per orientation, and per approach.** The cue is armed on entering `Following` **from
  above** the threshold and armed again by **every flip** — so the mirrored end behaves exactly like
  the first and both ends of the route ask — and it fires once on the crossing below the threshold
  that is a new low, so the same loss-versus-low test decides the flip and the prompt alike.
- It **disarms on firing** and re-arms whenever the time-to-go reads back above the threshold, so a
  boat that leaves and comes back down, or turns for the other end, legitimately re-prompts.
- **Objection, recorded:** arming on a flip lets the prompt fall on the first fix after a turn-back
  whenever the new goal already lies inside the minute — honest to the threshold, since the boat is
  within a minute of that end, but it can read as the prompt returning as you turn.
- **The answer decides only whether the route survives.** The doors that keep the route — *Continue
  route* and a dismissal (no action) — leave the mode `Following`; the doors that end it — *Save Route
  to Track* and *Discard Route* / *Stop following* — make the flip moot.
- **A reversal outranks the prompt.** If the loss accrues while the cue is open, the cue is closed as
  a **no action** dismissal and the flip proceeds; the prompt never blocks a reversal, and the flip is
  never gated by the threshold.
- **What keeps the flip quiet at the destination is the time-to-go, not a latch.** Past the
  destination `splitAt` clamps the projection to the route's end, so the number sits flat and cannot
  rise; the moment the boat re-enters the line it steps up and the loss accrues, and the toggle fires
  with no further consent.
- **A route armed inside the zone never prompts.** The **initial** arming needs a time-to-go **above**
  the threshold, so a pair whose whole line is under a minute — or a route taken up already inside the
  zone — raises no prompt at the outset; a flip arms it regardless, which is the symmetry above.
- **One surface, two raisers.** The prompt rides the same `routeExitRequested` dialog the toggle and
  the back key raise, so a raise while it stands is that one dialog with the user's own title, and the
  arrival raiser never stacks a second.
- **The prompt keeps the dialog's own shape** (`docs/ui-component-guidelines.md` §5.6): a short
  title-only prompt in the *you seem to have reached your destination* sense, `route_arrival_title`,
  no message line, the three doors unchanged — and the guidelines' dialog row gains the instance.
- **The cue is the mode's arrival.** R93's *fewer than two points remaining* case is not an arrival
  state but an empty remaining run — fewer than two points is no points — so the word stays the cue's
  alone.
- This turns the mode's *arrival carries no state and no cue* rule into a cue, so the concept's
  arrival line, the mode's flow and R99 move together.

## The mirror

- `RoutePlan.reversed(): RoutePlan` — one pure home on the plan: `points.reversed()`,
  `legTimesSec.reversed()`, `start`/`destination` exchanged, and `distanceM`, `durationSec`,
  `destinationMoved`, `computedAtMs`, `budgetUnmetZoneShare`, `forcedCrossingZoneNames` and
  `slowLimitSeconds` carried unchanged.
- The session link is keyed on the plan; the mirror must resolve to the **same** entry, so
  `isRouteSaved` reads true for a saved route's mirror and the exit dialog still reads
  *Stop following*.

## The detector's home

- `RouteViewModel` gains one feed, `onBoatFix(from)`, called from `MapScreen` with
  `routeBoatPosition`. While `Following` it reads the time-to-go, updates the history and the low and,
  on a flip, publishes `RouteState.Following(mirrored, followedTrackId)` with the running best, the
  seat and its freeze left untouched. Nothing re-arms and nothing is asked of an engine.
- The heading path is gone with it: no `courseDeg`/`speedKn` argument and no `routeLeadFix` coupling.

## The repaint and the reads

- `RouteHost` already remembers the split against the plan object, so a new mirrored instance
  redraws the split, the travelled/remaining runs and the casing; the pin, drawn from the plan's
  resolved destination, moves to the new destination in the same stroke.
- **At arrival the run is empty, and the covered line alone is drawn** (R93, inverted on the user's
  word of 2026-10-09). Fewer than two points remaining is **zero points** — nothing ahead — so slot 0
  and its casing stand down and the host's whole-line fallback goes; the travelled run then carries
  the line, its *only beside the remaining run* guard relaxed to admit the case where nothing ahead
  could be doubled. The route reads **done**: the whole line in the shared dimming, no bright
  remainder, and the line stays drawn as R93 promises. R93's arrival clause is rewritten to match —
  *fewer than two points remaining means an empty run, and the covered line alone is drawn* — with the
  two host comments that carried the old sentence, and a paint-level test pins the arrival face rather
  than the split's arithmetic.
- The trip figure (`routeTripFigure`) and the drawer band read `remainingFrom(boat)` off the
  mirrored plan, so the time-to-go and the distance face the new destination with no second
  arithmetic.
- `plannedEtaSeconds` reads `remainingFrom(plan.start)` — the whole-line time, unchanged by direction.

## Save, list and reopen — the added constraint

- The persisted end ids (R82) are a fact of the route **as armed**, not of the read direction: a
  route saved from a mirrored plan keeps the original `routeStartMarkerId` /
  `routeDestinationMarkerId`, so the track lists and reopens to the same route.
- `isRouteSaved`, the session's route-to-track link and the exit dialog's *Stop following* all read
  the route's identity, so the mirror never resurrects the save door.

## Docs and rules

- `FEAT_DSC_Route.md` — the concept's *no line is ever replaced from inside the mode* bullet admits
  the loss mirror; the *arrival carries no state and no cue* line becomes the arrival cue; the
  trip-follow bullet and R93 gain the mirrored read.
- **R99** is rewritten to the time-to-go loss toggle and the arrival cue; a new **R100** states the
  arrival cue and its reuse of the exit dialog's doors.
- `res/values/strings.xml` and `values-fr/strings.xml` gain the prompt's title under
  `route_arrival_title`, so no user-facing text is a literal.
- `docs/ui-component-guidelines.md` — the dialog row (§5.6) gains the arrival instance, a title-only
  prompt over the exit dialog's own doors; the patch lands with the implementation, the row describing
  shipped UI.
- Deleted, not left: `routeHeadingAway`, `angularOffDeg`, `route.follow.swap.deadBandDeg`,
  `route.follow.swap.minSpeedKn`, `route.follow.swap.hysteresisDeg` and the `routeLeadFix` feed
  coupling.

## Tests

- The loss: a series losing `lossSec` against its low toggles the reading; a falling or flat one holds
  it; a flip whose low is re-seeded does **not** turn straight back on the jump; and a drift slower
  than the look-back allows never toggles.
- The history buckets to about one sample a second, clears on entering `Following` and at every flip,
  drops samples older than the look-back, and refuses a second toggle inside the debounce.
- The arrival cue: a closing crossing raises the prompt once; a fresh approach re-prompts; a reversal
  while it is open closes it as no action and flips; a flat reading past the destination never flips;
  and a route armed inside the zone never prompts.
- The mirror and the identity, unchanged from the first landing.

## Verification

- `testDebugUnitTest` and `apk-build.bat` green.
- Owed device pass (R97): pan back along a followed line in demo to read the flip, and pan onto the
  destination to read the arrival cue; repeat on GPS water.

## Outcome

**First landing, 2026-10-08 — superseded in part.** The heading-away mirror shipped as **R99**: while a
route is followed the plan reversed in place when the boat's course passed `90° +
route.follow.swap.deadBandDeg` off the bearing to the armed destination, gated by
`route.follow.swap.minSpeedKn` and reverting inside `route.follow.swap.hysteresisDeg`, through the pure
`routeHeadingAway`, the one transform `RoutePlan.reversed` and the detector `RouteViewModel.onBoatFix`.
The mirror was in place only, and the route kept its identity across `isRouteSaved`, the session link
and the persisted R82 end ids. `testDebugUnitTest` and `apk-build.bat` green.

**Amended 2026-10-09 — in design, not yet implemented.** The heading trigger is dropped, faulty by
design in demo, where the user tested, and wrong on a curving route; the trigger becomes the
time-to-go loss. The overshoot past the destination becomes an arrival cue: a closing crossing below
`route.follow.arrival.etaSec` prompts once per approach — a boat that leaves and comes back down
legitimately re-prompts — and a reversal while the prompt is open closes it as a **no action**
dismissal and flips. The flip is never threshold-gated; the number's own flatness at the route's
clamped end is what keeps it quiet there.

**Reviewed 2026-10-09.** The symmetric *un-mirror on a fall* was a fault: the time-to-go falls as soon
as the boat progresses toward the end the line now points at, so it would have reverted the flip on
the next sample. The rule is a **toggle** — a fall is the ordinary state — with the low re-seeded at
the flip so the end-to-end jump cannot itself read as a loss.

**Settled 2026-10-09.** The window-and-rise pair was two dials for one behaviour — for a boat moving
steadily the loss is proportional to the window, so the pair only ever varied their ratio. They
collapse to **`route.follow.swap.lossSec` 30**, the loss measured from the recent low with the
look-back derived as twice it, beside **`route.follow.swap.debounceSec` 15** and
**`route.follow.arrival.etaSec` 60**; the prompt is a title-only dialog in the exit dialog's shape,
`route_arrival_title` in both locales, and the paint's *fewer than two points* case is named an empty
remaining run so the cue owns the word arrival. The mirror, the identity bridge and the config plumbing
stay; `routeHeadingAway`, `angularOffDeg`, the three heading keys and the `routeLeadFix` feed go.

**Reviewed again 2026-10-09.** The flip's re-seed is a **clear**, not only a low reset — the samples
behind a flip belong to the other orientation and would poison the minimum — the cue's *closing* is
pinned to a **new look-back low** so no second dial is invented for it, the retired heading keys keep
one home in Docs and rules, and the loss's own bound on the shortest route it can act on is recorded
as a known limit.

**Reviewed on device, 2026-10-09.** The arrival prompt proved **one-sided**: arming only from a
reading above the threshold, with a flip resetting it, left the mirrored end unable to ask whenever
the new goal already lay inside the minute. The flip now **arms** the cue, so both ends behave alike,
and the objection that it may prompt as you turn is recorded above.

**Amended on device, 2026-10-09.** The arrival paint read the opposite of its intent: the host
substituted the **whole line** for a remaining run under two points, so the trace jumped to full
strength at the destination. Fewer than two points is **zero points** — an empty run — so slot 0 and
its casing stand down, the travelled run carries the covered line in the shared dimming, and the route
reads done while it is still followed.
