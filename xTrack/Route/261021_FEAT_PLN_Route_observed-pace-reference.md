<!-- scope: feature -->
# Route — the `Any` pace's reference: the last sustained free-water speed

## Outcome

> **Superseded in part (2026-10-22):** the live model and the history are redecided — see `## Review fixes` § D; this section is rewritten under C7.

The `Any` stop's observed pace is today the 75th percentile of the last ten wall-clock minutes of GPS
speed ([`RoutePace.WINDOW_MS`](../../app/src/main/java/ykws/android/maro/data/route/RoutePace.kt:27)),
which forgets a cruise the moment the boat idles and lets harbour crawl into the quantile. The discussion
settled a different model: **the reference is the most recently sustained free-water cruise speed, read as
an abstraction off the smoothed speed curve of the current leg, with no wall-clock expiry** — so a wife's
moment never counts, a sea change follows once it holds, and the value persists past idle. A **learned
history** — a sliding window of the last N trip speeds reduced by a recency-weighted median — stands behind
it, so a cold start plans on the boat's own past rather than the shipped 25 kn.

**Date:** 2026-10-21 · **Status:** reopened 2026-10-22; the functional review closed the same day (D1–D15 decided), the review fixes open, nothing built · **Order:** the user's word — "explain", the `Any` challenge,
"last relevant speed", "sustained", "weight recent navigation", "plan this", "add to plan".

## The model

> **Superseded in part (2026-10-22):** the live model and the history are redecided — see `## Review fixes` § D; this section is rewritten under C7.

- **One reference value**: the last sustained free-water cruise speed. Full weight to the newest
  established speed, the older one dropped outright — recency by replacement, never by decay.
- **Source**: GPS speed-over-ground; restricted samples (inside a speed zone or the 300 m band) are dropped
  first, so the curve is the navigated free speed alone, never the forced limits.
- **The abstraction**: flatten the leg's speed curve, then read its plateau as the current cruise speed.
- **No expiry**: the reference stands until a newer sustained speed establishes, the learned history stands
  in until the first one does, and the shipped 25 kn is the floor beneath both.
- **A stop is ignored, never a leg end** — a near-zero reading adds no sample, so the last viable plateau
  sticks; the history applies only when there is no live track (demo mode or no fix), never because the boat
  paused.
- **The cold start reads the history** — with no current reference yet, the learned history stands in, and
  only the very first sail, or a missing memory, falls back to the shipped 25 kn.
- **`Any` resolves to the computed speed and is renamed to `-- Now --`** — the wheel and the Settings
  slider carry the static word, and the dynamic computed speed reads in the dashboard route search display
  (the panel header) and the Speed History section.
- **The history is a sliding window of the last N trip speeds** — each completed trip reduces to one cruise
  speed and enters the window, the oldest falls off past N, and the number is the window's **recency-weighted
  median**, so one abnormal trip cannot move it; navigation is "as fast as makes sense", so the history
  learns the boat's real weather-and-mood cruise.
- **A stored trip reduces differently from the live leg** — the live leg keeps the recency ramp because it
  reads *now*, while a finished trip reduces to a **plain 90th percentile** of its speed points — the
  typical open-water cruising speed, the burst sitting in the top tenth and nudging the plateau a touch over
  the fast band — GAP seams, restricted points and idle gaps excluded, stored `speedMps` preferred and
  derived speeds second-class: one trip is one number, not a curve.
- **The seed is the user's Calculate action** — it rebuilds the window from the last N water tracks under
  the tracks-list filter, route-saved tracks excluded because their points are plan arithmetic, not
  navigated speed, and a derived speed second-class wherever a stored `speedMps` is absent; the filter is
  one-shot, read only at the moment Generate runs.
- **Each completed trip folds into the window** — a saved trip appends its cruise speed and drops the oldest
 past N, so the history builds itself without a Calculate press; Calculate only re-seeds the window from the
 filtered list.

## The gate as a smoothed curve

> **Superseded in part (2026-10-22):** the percentile and the recency ramp give way to the progressive window average — see `## Review fixes` § F.

- **Flatten** with a moving 75th percentile, recency-weighted by a linear ramp from 0 at the oldest sample
  to 1 at the newest; the span adapts to the curve — no fixed window.
- **A dip too short to be a real level is a small weight share** and cannot cross the percentile alone —
  the wife's blip vanishes by construction, with no separate "ignore" rule and no dead-band.
- **The current cruise speed is the plateau of the smoothed curve** — read off the flattened curve live
  while samples flow, and the last viable plateau held through a stop, so a new level becomes the reference
  the moment the curve settles on it and a blip never does.
- **The `restricted` flag is the sole "not cruising" guard** — a live reading inside the 300 m band or a
  speed zone is dropped before smoothing, and the seed's per-point check is wired, so no floor is needed.

## Dials — the user's call, because they change what the route shows

> **Superseded in part (2026-10-22):** the percentile and the recency ramp give way to the progressive window average — see `## Review fixes` § F.

- The percentile (75th) and the recent-sailing weight (the recency ramp, 0–100 %) are the two remaining
  knobs; the window, the dead-band and the floor are all read off the smoothed curve and the restricted flag,
  not set.

## Settings surface — Settings › Routing › Speed History

Decided 2026-10-22 (D2–D5, D8, D13, D15). Every pace setting lives in one section, **Speed History**, on the
Routing tab after **Tuning** (where the Cruising speed row and its `Now` stop stand). Its rows run from the
pace in force, to how `Now` reads the boat, to what the history reads, to the history's own result and its
action:

1. **Pace now** — a status line, "Pace now: 29 kn · Live": the pace `Now` plans at and its source (D15, F7),
   shown whatever stop the Cruising speed row holds.
2. **Pace window** — a slider of 2–10 min, default 4 (D2, F5): how long a change must last before `Now`
   follows it.
3. *(divider)*
4. **Tracks to look back** — a slider of 1–15, default 5 (D3, B1): a live read over the stored trips, so
   moving it re-slices the profile at once and nothing is thrown away.
5. **Include demo tracks** — a switch, default off (D13, G4): moving it recomputes the shown value at once.
6. *(divider)*
7. **"Speed history reset on [date] to [speed]"** — the last Calculate's snapshot, kept apart (D4, B3).
8. **"Speed History today at [speed]"** — that snapshot plus every trip folded since (D4). With no history
   yet, the one line "No history yet — press Calculate." stands for 7 and 8.
9. **Calculate Speed History** — the existing button, right-aligned: rebuilds up to 15 trips of each kind
   from the eligible tracks (G3). The first-launch seed does the same work once, only when no profile exists
   (D8); nothing else runs it.

Eligible tracks are those the tracks-list filter shows, minus route-saved tracks and any track not mostly
on water (D5). The stop speed (`route.pace.stopKn`, 4) and the trip-weight cap
(`route.pace.tripWeightCapMin`, 30) are config keys with no row.

**Strings — both locales, through `@StringRes` ids** (French best effort, 2026-10-22). New keys, then the
two existing keys that change:

| Key | English | French |
|---|---|---|
| `settings_speed_history_pace_now_fmt` | Pace now: %1$s · %2$s | Vitesse actuelle : %1$s · %2$s |
| `route_pace_source_live` | Live | Direct |
| `route_pace_source_today` | Today | Du jour |
| `route_pace_source_history` | History | Historique |
| `route_pace_source_default` | Default | Par défaut |
| `settings_speed_history_window_label` | Pace window | Fenêtre de vitesse |
| `settings_speed_history_window_desc` | How long a change of speed must last before Now follows it: under a quarter of the window is ignored, from three quarters on it is followed in full. | Combien de temps un changement de vitesse doit durer avant que Live le suive : moins d'un quart de la fenêtre est ignoré, à partir des trois quarts il est suivi entièrement. |
| `settings_speed_history_window_value_fmt` | %1$d min | %1$d min |
| `settings_speed_history_demo_label` | Include demo tracks | Inclure les trajets démo |
| `settings_speed_history_demo_desc` | Let trips recorded in demo mode count in the history. | Compter dans l'historique les trajets enregistrés en mode démo. |
| `route_header_pace_any_fmt` *(changes, F7)* | Now (%1$s, %2$s) | Live (%1$s, %2$s) |
| `settings_route_pace_desc` *(changes, C1)* | How fast your boat goes in open water — it sets the trip time and what the route counts as slow. At Now, the route plans at your boat's own pace. | À quelle vitesse votre bateau avance en eau libre — elle fixe la durée du trajet et ce que la route compte comme lent. Sur Live, la route planifie à la vitesse de votre bateau. |

The panel header then reads "Now (29 kn, Live)" / "Live (29 kn, Direct)". An apostrophe is escaped in the
resource file (`l\'historique`).

## Files

- `app/src/main/java/ykws/android/maro/data/route/PacePlateau.kt` — **new**: the window average (F1).
- `app/src/main/java/ykws/android/maro/data/route/RoutePace.kt` — the quantile and the ramp leave (F1).
- `app/src/main/java/ykws/android/maro/data/route/SpeedHistory.kt`, `SpeedHistorySeeder.kt` — the trip
  reduction, the store of each kind, the weighted median, the seed order, start-time ordering and the water
  share (F3, F4, A1, B1, B4, B5, G3, C2, C6).
- `app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt` — `observePace` feeds `PacePlateau`; the
  day's pace and the source (F2, F6, F7).
- `app/src/main/java/ykws/android/maro/ui/map/RouteHost.kt` — the pace feed, GPS mode only (F6).
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`, `OverlayLayer.kt` — the seed, the fold-in and
  the startup seed (A5, A6, D8).
- `app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt` — the Speed History section
  above.
- `app/src/main/java/ykws/android/maro/ui/map/RouteConfirmPanel.kt` — the header's pace and source (F7).
- `app/src/main/java/ykws/android/maro/data/track/Track.kt`, `TrackRepository.kt`, `TrackRecorder.kt`,
  `TrackRecordingService.kt` — the demo flag, its summary projection and where the recorder sets it (G1).
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — the window, the switch, the
  day's pace and the Calculate snapshot.
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt`, `app/src/main/assets/maro.properties` —
  `route.pace.windowMin`, `route.pace.stopKn`, `route.pace.tripWeightCapMin` added,
  `route.pace.recentWeightPct` removed.
- `app/src/main/res/values/strings.xml`, `app/src/main/res/values-fr/strings.xml` — the table above.

## Scenarios

> **Superseded in part (2026-10-22):** the percentile and the recency ramp give way to the progressive window average — see `## Review fixes` § F.

1. **Wife's moment** — 30 kn, a ten-second complaint, back to 30. The dip is a sixth of the window, below
   the percentile; ignored, the reference stays 30.
2. **Wife's sustained word** — 30 → 25 and stays. The 25 fills the recent window and outweighs the old
   plateau; after the ramp it becomes the reference.
3. **A wave, a very short stop** — a few seconds near zero. Too small a share; smoothed away, the reference
   is unchanged.
4. **Harbour crawl** — minutes at 3 kn inside the 300 m band. Dropped by the restricted flag, never a
   candidate; the last cruise stands.
5. **Cold start** — no sail yet this trip. The learned history plans first, and only a first-ever launch
   without memory falls back to the shipped 25 kn, until the first sustained run establishes.

## Open points

> **Superseded in part (2026-10-22):** the percentile and the recency ramp give way to the progressive window average — see `## Review fixes` § F.

- The percentile is the 75th, settled; the smoothing span, dead-band and floor are decided by the curve and
  the `restricted` flag, not set.
- The recent-sailing weight is a 0–100 % slider, the share given to the newest sailing, default 60 %.
- The count is a 1–15 slider, default 5; the filter is settled — the seed reads the tracks list's filtered
  content as it stands.
- The `Now` stop reads *Live* in French; the dynamic value reads in the dashboard route search
  display.
- The source split is settled and the fold-in is wired — live 75th percentile with recency, trip plain 90th
  percentile, window weighted median, each saved trip appending its 90th percentile.
- The stored-track zone/band exclusion is **wired** — `isRouteRestricted` drops the points, forwarded
  through `seedSpeedHistory` to the Calculate, the startup import and the fold-in, the startup seed waiting on
  the speed-zone index.

## Review fixes — 2026-10-22

A review of `ed18e5cd..2fffc8e5` read the code, not the plan, and found the shipped build parting from this
plan in the places below. **Status reopened.** Each item carries an id so a commit or a todo can name it;
`[ ]` open, `[x]` fixed. Items marked **D** change what the user sees; all fifteen were settled in the
plain-English functionality review of 2026-10-22.

### F — The model changes: a progressive window average replaces the 75th-percentile ramp (decided: D2, D6, D9–D12)

**Why.** The shipped reduction is a recency-weighted 75th percentile over the last ten minutes. Measured on
that code, a real slowdown is followed only after 6–7.5 minutes while a speed-up is followed after about 2,
because the 75th percentile deliberately reads the *fast* pace. The functional requirement is symmetric:
the pace shown is a plateau that is representative of the boat, whatever the cause — worse sea, a passenger
asking to slow, the skipper speeding up alone — and a short slowdown to look at something, or a stop to pick
something out of the water, never counts. A hard gate ("a new speed counts after exactly N minutes") was
considered and set aside: it is arbitrary, it flips the pace in one step, and a slowdown just over the gate
counts in full. The model below has no gate: the pace edges toward a new speed in proportion to how much of
the recent window that speed fills, and the older speeds keep their weight until they age out.

**The rule, in plain words.**

- The pace looks back over a **window W** (4 min by default, a Settings slider of 2–10 min — **D2**) and is
  the **average of the middle half** of the speeds in it — the slowest quarter and the fastest quarter of the
  window are left out.
- A new speed that fills **less than a quarter of the window** (under 1 min at W = 4) has **no effect**: a
  30-second look, a short slowdown, a wave.
- From a quarter to three quarters of the window (1 to 3 min) the pace **edges toward** the new speed in
  proportion, and from three quarters on it **is** the new speed. It works the same going up and going down.
- A reading is **ignored, never counted as a slow moment**, when it is inside a speed zone or the 300 m band
  (the `restricted` flag) or below the stop speed (**D6**, 4 kn — the boat idles at about 3.2 kn).
- **Time the window has no reading for** — a stop, a zone, a lost fix, a long idle, the start of a trip — is
  counted as the pace the app already shows; at the start of a trip that is the **starting pace**: the day's
  saved live pace (**D9**), else the learned history, else the shipped 25 kn. So a stop never moves the pace,
  and a boat resuming at a new speed is followed in the same 1–3 minutes as one that never stopped.

**How it is computed (one pure class, no Android).**

- Readings are sorted by speed and **weighted by the time each one covers**, because the feed delivers a
  reading when the speed value changes, not on a clock; the time one reading covers is capped at 10 s and a
  longer silence is a gap.
- The pace is the weighted mean of the cumulative 25 %–75 % band of that sorted list, the gap time entering at
  the shown pace. The 25 % and the 10 s are code constants; **W is the only Settings dial**, the stop speed a
  config key.
- No percentile, no recency ramp, no steadiness test and no tolerance band.
- The class lives beside `RoutePace` (`PacePlateau`: `observe`, `plateauKn`), is the one home of the
  reduction, and the view model only feeds it.

**Worked cases with W = 4 min — these are the acceptance tests (E2).**

| Case | Pace shown |
|---|---|
| 30 kn, 30 s at 20 kn, back to 30 kn | 30 throughout |
| 30 kn, stopped 1 min 20 s, back to 30 kn | 30 throughout |
| 30 kn, then 25 kn for good | 30 at 1 min, 27.5 at 2 min, 25 from 3 min |
| 25 kn, then 30 kn for good | 25 at 1 min, 27.5 at 2 min, 30 from 3 min |
| 30 kn, 2 min at 20 kn, back to 30 kn | sags to 25 by the end of the slowdown, back at 30 about 3 min after it |
| 30 kn, 10 min stopped, resumed at 30 kn | 30 throughout |
| 30 kn, 10 min stopped, resumed at 25 kn | 30 for the first minute, 27.5 at 2 min, 25 from 3 min |
| 30 kn, 20 min inside zones and the 300 m band | 30 throughout |
| a harbour start, then open water at 32 kn | the starting pace for the first minute, edging to 32, 32 from 3 min |
| no history, no live reading | 25 kn |

**Objections kept on the record.** The average of the middle half is the *typical* speed, not the *fast* one,
so an ETA reads a little longer than the 75th percentile gave; this is the representativeness asked for, and a
settled plan point (the 75th) is reversed on the user's word (**D12**). During a transition the pace can read
a value the boat never ran (27.5 between 30 and 25). The quarter trim and the 10 s reading cap are fixed
choices; the window is the one the user can see. A true steadiness-based rule was set aside because it needs a
tolerance for "steady" and a ramp shape, which are more hidden dials, not fewer.

**Items.**

- [ ] **F1** — `PacePlateau` (above) with its table of cases as tests; `RoutePace`'s quantile, ramp,
  `WINDOW_MS` and `MIN_SAMPLES` leave once nothing reads them.
- [ ] **F2** — `RouteViewModel.observePace` feeds the class; the buffer, `lastPlateauKn`, the hard-coded 25 and
  the unused `setPaceKn` parameter go (this closes A2, A3, A4 and C5); the live-or-history switch stays.
- [ ] **F3** — a finished trip keeps its **cruising speed** (**D10**): the shipped 90th percentile of its
  eligible readings — outside zones and the 300 m band, at or above the stop speed — the history being a
  driving profile and not today's conditions; the seeder and the fold-in call the one function, and each
  stored trip carries its open-water minutes (the time its eligible readings cover) and its demo flag
  (**D13**) beside its speed.
- [ ] **F4** — the history combines its last N trips by a **median weighted by each trip's open-water
  minutes, capped** (**D11**; cap `route.pace.tripWeightCapMin`, default 30, config-only), so a trip of 15
  open-water minutes votes half of any trip of 30 or more; the recency weighting (`route.pace.recentWeightPct`,
  `AppConfig.routePaceRecentWeightPct` and the persisted setting) is removed (an orphaned stored preference is
  ignored, never migrated).
- [ ] **F5** — the window is one config key (`route.pace.windowMin`, default 4) and, per **D2**, one Settings
  slider of 2–10 min (Speed History, row 2), its strings in both locales, replacing B2; the stop speed is one config key
  (`route.pace.stopKn`, default 4, **D6**) with no slider.
- [ ] **F6 — The day's pace (D9).** The live pace is saved with its local date beside the other pace settings,
  written while its source is Live. It is the starting pace while its date is today's, and through the trip
  that wrote it even past midnight; otherwise the history is. Demo mode feeds no live pace (`RouteHost`
  passes no speed outside GPS mode), so a demo drive never writes it; Calculate does not clear it.
- [ ] **F7 — The source (D15).** The pace carries its source as a value beside its number, produced where the
  pace is chosen and read by the panel header and the Speed History section: **Live** once this trip's
  eligible readings fill a quarter of the window — the point from which they can move the number, not the
  first reading, when the number is still the starting pace (decided 2026-10-22) — else **Today** while the
  day's saved pace stands, else **History** (any non-empty history, **D14**), else **Default**; demo mode
  never reads Live. The words, the "Pace now" line and the header's new format stand in
  `## Settings surface`'s table.

### A — Bugs

- [ ] **A1 — Seeded window is in the wrong order.** `seedSpeedHistory` sorts newest-first and the window is read
  oldest-first, so a seeded window is reversed: with a weighted median it leaned on the oldest trips, and with
  the median of F4 the later fold still drops the *newest* trip instead of the oldest. Fix: reverse to
  oldest-first before `seed`, pinned by a test fed newest-first.
- [x→F] **A2, A3, A4** — the harbour start overwriting the history with 25 kn, the plateau dying after ten idle
  minutes, and a drift reading counting as cruising are removed by F1/F2 and kept as acceptance cases.
- [ ] **A5 — Seeding runs on the main thread.** `LaunchedEffect` and `rememberCoroutineScope` dispatch to Main
  while the loop runs a coast and zone query per point over up to 15 tracks. Fix: the reduction runs on
  `Dispatchers.Default`, and only the settings write returns to the caller.
- [ ] **A6 — The startup seed can spin or give up.** It polls `speedZoneIndex` with no bound, and fires once on
  whatever `summaries` held, so an unloaded list ends it for the session. Fix: wait on the index and the loaded
  track list as flows with a bound, and retry when the list arrives (**D8**).

### B — Parts from the plan (the plan stands, the code does not)

- [ ] **B1 — N must be a live read.** The plan keeps 15 trip speeds and lets N only trim how many the median
  reads. The code trims the stored window to N on fold and on seed and never recomputes the shown value when N
  moves. Fix: store up to 15 of each kind (**G3**), read the last N, derive the shown value from the store
  instead of persisting it (**D3**: the store is never trimmed to N).
- [→F5] **B2** — the recent-weight slider is replaced by the pace-window slider.
- [ ] **B3 — "Reset on" and "today at" always agree.** Each fold overwrites the reset date and value. Fix: keep
  the Calculate snapshot (date and speed) apart from the live value (**D4**: two lines).
- [ ] **B4 — "Last N tracks" are chosen by edit time.** A rename bumps `updatedAtEpochMs`. Fix: order by
  `startTimeMs` (**D7**).
- [ ] **B5 — "Water tracks" are not checked.** A drive or a walk feeds the history. Fix: skip a track whose
  water share is not the majority, from the summary's own water and land counts (**D5**). A track never
  classified (its counts are `0`, the "biased by one" case) is classified from its own points by the seed,
  which already queries the coast per point; to confirm in code before it is built.

### G — The demo flag and the Include-demo setting (D13)

- [ ] **G1 — The flag on the track.** `Track` and `TrackSummary` gain a `demo` boolean on a fresh proto number
  with default `false`, so an old blob reads as GPS and an older build still reads a new one; the summary is
  built from the track and copies it. The recorder sets it when it takes a demo-mode sample, so a trip that
  ever ran on the simulated boat is demo and a pure GPS trip never is (a technical choice, to confirm against
  the recorder's sample path).
- [ ] **G2 — The export carries it with no exporter change.** The GPX blob is the whole `Track` protobuf, so
  the new field travels in `<maro:data>` and returns on import; a foreign GPX has no blob and builds a track
  with the default, GPS. A round-trip test pins it.
- [ ] **G3 — The store keeps both kinds.** Up to 15 trip entries of each kind, each with speed, open-water
  minutes and the flag; the fold-in appends to its own kind and drops that kind's oldest; Calculate seeds up
  to 15 of each kind from the eligible tracks — never only N, so raising N after a Calculate still finds
  trips (**D3**).
- [ ] **G4 — The setting.** One switch, **Include demo tracks**, default off, persisted beside the other pace
  settings in `SettingsManager`, in both locales through `@StringRes` ids, shown in the Speed History section.
  Off, the profile reads the last N GPS trips; on, the last N of both kinds together by start time (**D7**).
  Moving it, like moving N, recomputes the shown value from the store with no Calculate.

### C — Stale text and stale docs

- [ ] **C1** — the Settings pace description still reads "At Any…" (EN) and "Sur Indifférent…" (FR); the
  replacement texts stand in `## Settings surface`'s table.
- [ ] **C2** — the `SpeedHistorySeeder` class note says "recency-weighted 75th percentile"; both it and the
  `RoutePace` note are rewritten with F1/F3.
- [x] **C3** — this plan's `## Settings surface` said Calculate is where the heavy work lives and never on a
  cold start, beside a startup seed; it now states one rule once, per **D8** (row 9).
- [ ] **C4** — the `FEAT_DSC_Route` pointer says "(in design)" while this plan says implemented; both follow the
  review's end.
- [→F2] **C5** — closed by F2, which names it; its own entry was never written.
- [ ] **C6** — `routeRestricted` defaults to `{ _, _ -> false }`, silently turning the exclusion off if a
  caller forgets it; the default goes and the parameter is required.
- [ ] **C7** — this plan's `## Outcome`, `## The model`, `## The gate as a smoothed curve`, `## Dials`,
  `## Scenarios` and `## Open points` describe the percentile and the ramp; each is rewritten to the window
  average when F is built. `## Settings surface` and `## Files` already follow the decisions (2026-10-22).

### D — Decisions for the plain-English review

- **D1 — A fixed pace always wins. DECIDED yes (2026-10-22).** Before this branch an observed pace overrode a
  numeric setting once enough samples existed; now only `Now` reads the boat. No code change.
- **D2 — The pace window W (replaces the recency weight). DECIDED (2026-10-22).** How far back the pace
  looks: 4 minutes by default (a change under a quarter of it is ignored, a lasting one is fully followed at
  three quarters), as a Settings slider of 2–10 min. The "newest counts ×N" slider and the 2-minute hard gate
  are both withdrawn.
- **D3 — What N means. DECIDED (2026-10-22).** Moving the slider re-slices a store of 15 (the plan): N only
  chooses how many of the stored trips the profile reads, nothing is thrown away, raising N brings the older
  trips back, and the shown value is recomputed from the store. The store's size is negligible.
- **D4 — What the two status lines mean. DECIDED (2026-10-22).** Two lines (the plan): "Reset on" is the
  snapshot of the last Calculate (its date and speed), kept apart; "today" is that plus every trip since, so
  the user sees how the profile has drifted from the reference.
- **D5 — Land tracks. DECIDED (2026-10-22).** Skipped by water share alone (a track that is not mostly on
  water never enters the history), not left to the user's tracks-list filter, which still shapes what
  Calculate reads.
- **D6 — What a stop is. DECIDED (2026-10-22).** The speed below which a reading is ignored: 4 kn, because the
  boat idles at about 3.2 kn and is small and fast, so nothing it cruises at sits near the line; one config
  key, not a Settings slider.
- **D7 — "Last" means. DECIDED (2026-10-22).** The most recent *trip* by its start time, not the most
  recently *edited*, so a rename or a comment never changes the profile.
- **D8 — The startup seed. DECIDED (2026-10-22).** Keep it: a first launch plans on the past with no button
  press. It runs only when no profile exists yet, never overwrites one, runs off the main thread, and waits
  (bounded) for the track list and the speed-zone index, retrying when the list arrives (A5, A6).
- **D9 — The pace through a gap. DECIDED (2026-10-22).** It stays through a stop, a zone and a lost fix (gap
  time counts as the shown pace) and **holds for the day**: sea state and load usually last an outing, so a
  second trip after lunch starts from the morning's live pace, not from the profile. The day is the device's
  local calendar date, checked only when a new trip starts (never mid-trip, so a trip past midnight keeps its
  pace), and the last live pace is saved with that date so it survives an app restart; a new trip on another
  date starts from the profile.
- **D10 — A finished trip's number. DECIDED (2026-10-22).** The trip's cruising speed, the shipped 90th
  percentile of its eligible readings, not the live average: the live pace matches the conditions of the
  moment, the history is the user's driving profile across trips, and the live pace takes over after the
  first minutes. A trip that never cruises still casts a low vote; the weighting of D11 limits it.
- **D11 — Combining the trips. DECIDED (2026-10-22).** A median of the last N weighted by each trip's
  open-water minutes, capped at 30 (config-only): a 15-minute trip votes half of a 30-minute or 45-minute
  one, and most real trips tie, so the rule shows only for short trips. The cap is a fixed number the user
  cannot see in Settings.
- **D12 — The middle-half average reads the typical speed, not the fast one. DECIDED yes (2026-10-22).** An
  ETA reads a little longer than the 75th percentile gave, and during a transition the pace can read a value
  the boat never ran; that is the representativeness wanted.
- **D13 — Demo tracks. DECIDED (2026-10-22).** A demo-mode trip is a simulated boat, not the user's driving.
  Each track carries a **demo flag**; a track without it is read as GPS (so an old demo track, which the data
  cannot tell apart, counts as GPS), and an imported foreign GPX is GPS. A Settings switch **Include demo
  tracks** (default off) decides whether demo trips enter the profile, and moving it re-derives the shown
  value from the store at once, no Calculate. The store therefore keeps up to 15 trips **of each kind**, so
  turning demo off never leaves a thin window. The flag is exported with the track.
- **D14 — A thin history. DECIDED (2026-10-22).** The profile uses whatever trips exist: fewer than N is
  read as it stands, and only an empty history falls to the shipped 25 kn. One odd trip can therefore be the
  whole profile until more arrive.
- **D15 — The pace shows its source. DECIDED (2026-10-22).** Wherever the pace reads (the route search
  panel's header and the Speed History section) it names where the number comes from: **Live** (this trip's
  own readings), **Today** (the saved pace of the day, no reading yet this trip), **History** (the profile) or
  **Default** (the shipped 25 kn). The words are user-facing strings in both locales. "Live" switches on once
  this trip's readings fill a quarter of the window, about a minute at W = 4 (F7).

### E — Tests that pin the above

- [ ] **E1** — seed order newest-first, and the fold drops the oldest (A1).
- [ ] **E2** — `PacePlateau`: every row of the worked-cases table (F1), plus uneven reading spacing, the
  10 s reading cap, a reading under 4 kn ignored, gap time entering at the shown pace, and symmetry (a
  mirrored up/down case gives mirrored values).
- [ ] **E3** — fold keeps 15 and the median reads the last N (B1); the snapshot and the live value diverge after
  a fold (B3).
- [ ] **E4** — water-share and start-time ordering (B4, B5).
- [ ] **E5** — the demo flag: a track without it reads as GPS, an export round-trips it, and the profile moves
  at once when the switch moves, with 15 trips kept per kind and a Calculate seeding 15 of each (G1–G4).
- [ ] **E6** — the day's pace: a second trip the same date starts from the morning's pace, a trip on another
  date from the history, a trip crossing midnight keeps its pace, the saved value survives a restart, and a
  demo drive never writes it (F6).
- [ ] **E7** — the source and the thin history: each of the four sources is reported at its moment, Live only
  once this trip's readings fill a quarter of the window, a history
  of two trips with N at 5 reads as History, and an empty one reads as Default (F7, D14).

### Order of work

Every decision is settled (D1–D15, the Live threshold of F7 included).

1. **The live pace** — F1, F2, F5 with E2.
2. **The trip history** — F3, F4, A1, B1, B3, B4, B5, C6 and G1–G4, with E1, E3, E4, E5.
3. **The day's pace and the source** — F6, F7 with E6, E7.
4. **The seeding** — A5, A6.
5. **The text** — C1, C2, C4, C7.
6. The full unit suite, `assembleDebug` and `#bake`.
