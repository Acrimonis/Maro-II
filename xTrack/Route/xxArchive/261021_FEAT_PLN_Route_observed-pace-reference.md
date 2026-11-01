<!-- scope: feature -->
# Route — the `Any` pace's reference: the middle-half average of the boat's recent speeds

## Outcome

The `Any` stop (Settings `Now`, the drawer wheel `--Now--`, French `Live`) plans at **the average of the
middle half of the boat's speeds over a window** — 4 minutes by default, a Settings slider of 2–10 — so a
short slowdown or a stop never counts, a lasting change of speed is followed in about the window's own
half-width, and the pace is the same going up and going down. Time the window has no reading for counts as
the pace already shown, so a stop never moves it and a boat resuming at a new speed is followed as if it
never stopped. A **learned history** stands behind it — the boat's past trips, each kept as its cruising
speed, combined by a median weighted by each trip's open-water minutes — so a cold start plans on the boat's
own past rather than the shipped 25 kn, and the pace always names where it comes from (Live · Today · History
· Default).

**Date:** 2026-10-21 · **Status:** reopened 2026-10-22, the functional review closed the same day (D1–D15
decided), **built the same day** (see `## Review fixes`, every item ticked); the device pass is the user's ·
**Order:** the user's word — "explain", the `Any` challenge, "last relevant speed", "sustained", "weight recent
navigation", "plan this", "add to plan", `#impl`.

## The model

- **One reference value**: the middle-half average of the speeds the boat ran over the last W minutes
  (`PacePlateau`). The slowest quarter and the fastest quarter of the window's time are left out; what remains
  is averaged.
- **Source**: GPS speed-over-ground, GPS mode only — a demo drive feeds no live pace. A reading inside a
  speed zone or the 300 m band (`restricted`) or below the stop speed (4 kn, `route.pace.stopKn`) is
  **ignored, never counted as a slow moment**.
- **No hard gate**: a new speed filling under a quarter of the window has no effect, from three quarters on it
  is the pace, and in between the pace edges toward it in proportion.
- **A gap holds the pace**: the time the window has no reading for — a stop, a zone, a lost fix, the start of a
  trip — counts as the pace shown when that time began. At the start of a trip that is the **starting pace**:
  the day's saved live pace while its date is the trip's, else the learned history, else the shipped 25 kn.
- **`Any` resolves to the computed pace**: the wheel and the Settings slider carry the static word; the
  computed pace and its source read in the route search panel's header and in the Speed History section.
- **The history is a store of trips**, up to 15 of each kind (GPS and demo). Each trip is one cruising speed —
  the 90th percentile of its eligible readings, a trip's driving profile and not the conditions of one
  outing — with the open-water minutes its readings cover. The profile is the **median of the last N trips by
  start time, each weighted by its open-water minutes capped at 30** (`route.pace.tripWeightCapMin`), so a
  15-minute trip votes half of a 30-minute one. N only chooses the slice; nothing is trimmed to it.
- **Each completed trip folds in** on its own (a route save and a track not mostly on water never do), and the
  user's **Calculate** rebuilds the store from the tracks list as it stands filtered; a first launch with no
  history does the same once.

## The window average

- Readings are sorted by speed and **weighted by the time each covers**: the feed delivers a reading when the
  speed changes, not on a clock, so a reading covers the time up to the next one, capped at 10 s; a longer
  silence is a gap.
- The pace is the weighted mean of the cumulative 25 %–75 % band of that sorted list, the gap time entering at
  the pace shown when the gap began. The 25 % and the 10 s are code constants; **W is the only Settings
  dial**, the stop speed a config key.
- `PacePlateau` is the one home of the reduction (`observe`, `plateauKn`, `readingMs`); the view model only
  feeds it. No percentile, no recency ramp, no steadiness test, no tolerance band.

## Scenarios

The worked cases of `## Review fixes` § F stand as `PacePlateauTest`; in words:

1. **Wife's moment** — 30 kn, a 30-second dip to 20, back to 30. The dip is under a quarter of the window;
   the pace stays 30.
2. **Wife's sustained word** — 30 → 25 and stays. 30 at 1 min, 27.5 at 2, 25 from 3.
3. **A wave, a very short stop** — a few seconds near zero. Ignored; the pace is unchanged.
4. **Harbour crawl** — minutes at 3 kn inside the 300 m band. Ignored by the flag and the stop speed; the last
   cruise stands.
5. **Cold start** — no sail yet this trip. The day's pace, else the history, else 25 kn plans first, until
   this trip's readings fill a quarter of the window and the source reads Live.

## Open points

- The window is the one dial the user sees; the quarter trim and the 10 s cap are code constants, and the stop
  speed and the trip-weight cap are config keys with no row.
- **A gap after the cruise is established (decided and built 2026-10-22).** A trip that starts cold and stops
  after only two minutes of cruising had held its mid-transition value (27.5 between 25 and 30) until the next
  reading, because the gap held the pace *shown*. Once this trip's readings fill a quarter of the window (the
  Live threshold), a gap now counts at the **readings' own middle-half average** (30 here) instead; before
  that it counts as the pace shown, so the starting pace still carries a trip's first minutes. Every
  worked-case row of § F still holds (each stops after a settled pace); the cost is one more branch
  (`PacePlateau.gapPace`) and a pace that jumps from 27.5 to 30 when the boat stops at the quarter mark.

## Settings surface — Settings › Routing › Speed History

Decided 2026-10-22 (D2–D5, D8, D13, D15), built the same day. Every pace setting lives in one section,
**Speed History**, on the Routing tab after **Tuning** (where the Cruising speed row and its `Now` stop
stand). Its rows run from the pace in force, to how `Now` reads the boat, to what the history reads, to the
history's own result and its action:

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
   from the eligible tracks (G3). The button is **disabled from the press until the rebuild is done**, then
   re-enabled, and a short toast says how many trips it read — or that no track was eligible and the history is
   unchanged. The first-launch seed does the same work once, only when no profile exists (D8), and says
   nothing; nothing else runs it.

Eligible tracks are those the tracks-list filter shows, minus route-saved tracks and any track not mostly
on water (D5). The stop speed (`route.pace.stopKn`, 4) and the trip-weight cap
(`route.pace.tripWeightCapMin`, 30) are config keys with no row.

**Strings — both locales, through `@StringRes` ids** (French best effort, 2026-10-22). New keys, then the
two existing keys that changed:

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
| `settings_speed_history_calculated` *(plural)* | Speed History calculated from %d trip(s). | Historique de vitesse calculé à partir de %d trajet(s). |
| `settings_speed_history_none_eligible` | No eligible track — Speed History unchanged. | Aucun trajet éligible — historique de vitesse inchangé. |
| `route_header_pace_any_fmt` *(changed, F7)* | Now (%1$s, %2$s) | Live (%1$s, %2$s) |
| `settings_route_pace_desc` *(changed, C1)* | How fast your boat goes in open water — it sets the trip time and what the route counts as slow. At Now, the route plans at your boat's own pace. | À quelle vitesse votre bateau avance en eau libre — elle fixe la durée du trajet et ce que la route compte comme lent. Sur Live, la route planifie à la vitesse de votre bateau. |

The panel header then reads "Now (29 kn, Live)" / "Live (29 kn, Direct)". An apostrophe is escaped in the
resource file (`l\'historique`).

## Files

- `app/src/main/java/ykws/android/maro/data/route/PacePlateau.kt` — **new**: the window average (F1).
- `app/src/main/java/ykws/android/maro/data/route/RoutePace.kt` — now the pace's pure rules: `PaceSource`,
  `PaceReading`, the starting pace and the Live threshold; the quantile and the ramp are gone (F1, F7).
- `app/src/main/java/ykws/android/maro/data/route/SpeedHistory.kt`, `SpeedHistorySeeder.kt`,
  `SpeedHistoryProfile.kt` — **new** profile accessor: the trip store, the capped weighted median, the
  per-kind fold, the seed order, the trip reduction and its open-water minutes (F3, F4, A1, B1, G3, C2, C6).
- `app/src/main/java/ykws/android/maro/ui/map/SpeedHistoryActions.kt` — **new**: the seed candidates, the
  off-main-thread seed, and the settings after a Calculate, a fold and a saved day's pace.
- `app/src/main/java/ykws/android/maro/ui/map/PaceSourceLabel.kt` — **new**: the source's word.
- `app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt` — `observePace` feeds `PacePlateau`;
  `startTrip`, the day's pace and the source (F2, F6, F7).
- `app/src/main/java/ykws/android/maro/ui/map/RouteHost.kt` — the pace feed, GPS mode only, and a once-a-second
  re-read so a speed value that holds still still ages the window (F6).
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt`, `OverlayLayer.kt`, `NavigationViewModel.kt` — the
  bounded startup seed, the fold-in, the trip start and the day's pace write (A5, A6, D8, F6).
- `app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt`, `OverlayLayerParams.kt`,
  `MapOverlayData.kt` — the Speed History section above.
- `app/src/main/java/ykws/android/maro/ui/map/RouteConfirmPanel.kt` — the header's pace and source (F7).
- `app/src/main/java/ykws/android/maro/data/track/Track.kt`, `TrackRepository.kt`, `TrackRecorder.kt`,
  `TrackSample.kt`, `TrackMerger.kt`, `ui/map/MapServiceEffects.kt` — the demo flag, its summary projection
  and where the recorder sets it (G1).
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — the window, the switch, the trip
  store, the day's pace and the Calculate snapshot.
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt`, `app/src/main/assets/maro.properties` —
  `route.pace.windowMin`, `route.pace.stopKn`, `route.pace.tripWeightCapMin` added,
  `route.pace.recentWeightPct` removed.
- `app/src/main/res/values/strings.xml`, `app/src/main/res/values-fr/strings.xml` — the table above.
- Tests: `PacePlateauTest`, `RoutePaceTest`, `SpeedHistoryTest`, `SpeedHistorySeederTest`,
  `TrackDemoFlagTest`, `RoutePaceFeedTest`, `SpeedHistoryActionsTest`.

## Review fixes — 2026-10-22

A review of `ed18e5cd..2fffc8e5` read the code, not the plan, and found the shipped build parting from this
plan in the places below. **Built the same day (`#impl`).** Each item carries an id so a commit or a todo can
name it; `[ ]` open, `[x]` fixed. Items marked **D** change what the user sees; all fifteen were settled in
the plain-English functionality review of 2026-10-22.

### F — The model changes: a progressive window average replaces the 75th-percentile ramp (decided: D2, D6, D9–D12)

**Why.** The shipped reduction was a recency-weighted 75th percentile over the last ten minutes. Measured on
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
  the pace shown **when the gap began** (frozen at that moment — a gap counted at the continuously moving
  pace would feed the pace back into itself and break the table below), or, once this trip's readings fill a
  quarter of the window, at **the readings' own middle-half average**. The 25 % and the 10 s are code
  constants; **W is the only Settings dial**, the stop speed a config key.
- No percentile, no recency ramp, no steadiness test and no tolerance band.
- The class lives beside `RoutePace` (`PacePlateau`: `observe`, `plateauKn`, `readingMs`), is the one home of
  the reduction, and the view model only feeds it.

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
a value the boat never ran (27.5 between 30 and 25); a stop during it holds the readings' own average once the
  trip is Live, and the pace shown before that. The quarter
trim and the 10 s reading cap are fixed choices; the window is the one the user can see. A true
steadiness-based rule was set aside because it needs a tolerance for "steady" and a ramp shape, which are more
hidden dials, not fewer.

**Items.**

- [x] **F1** — `PacePlateau` (above) with its table of cases as tests; `RoutePace`'s quantile, ramp,
  `WINDOW_MS` and `MIN_SAMPLES` left (the trip reduction keeps its own `MIN_READINGS`).
- [x] **F2** — `RouteViewModel.observePace` feeds the class; the buffer, `lastPlateauKn`, the hard-coded 25 and
  the unused `setPaceKn` parameter are gone (this closes A2, A3, A4 and C5); `Any` reads the plateau, a
  numbered stop plans as set.
- [x] **F3** — a finished trip keeps its **cruising speed** (**D10**): the shipped 90th percentile of its
  eligible readings — outside zones and the 300 m band, at or above the stop speed — the history being a
  driving profile and not today's conditions; the seeder and the fold-in call the one function
  (`SpeedHistorySeeder.tripOf`), and each stored trip carries its open-water minutes (the time its eligible
  readings cover, each reading speaking for the interval to the next point, capped at the track gap
  threshold) and its demo flag (**D13**) beside its speed.
- [x] **F4** — the history combines its last N trips by a **median weighted by each trip's open-water
  minutes, capped** (**D11**; cap `route.pace.tripWeightCapMin`, default 30, config-only), so a trip of 15
  open-water minutes votes half of any trip of 30 or more; the recency weighting
  (`route.pace.recentWeightPct`, `AppConfig.routePaceRecentWeightPct` and the persisted setting) is removed
  (an orphaned stored preference is ignored, never migrated).
- [x] **F5** — the window is one config key (`route.pace.windowMin`, default 4) and, per **D2**, one Settings
  slider of 2–10 min (Speed History, row 2), its strings in both locales, replacing B2; the stop speed is one
  config key (`route.pace.stopKn`, default 4, **D6**) with no slider.
- [x] **F6 — The day's pace (D9).** The live pace is saved with its local date beside the other pace settings,
  written while its source is Live (at most every 10 s, once it has moved 0.1 kn, **the last Live value always
  saved at the window's end** — `saveDayPace`). It is the starting pace while its date is today's — read when
  a recorded trip starts (the recorder turning on) and pinned until it ends, so a trip past midnight keeps it;
  with no recorded trip running the date follows the calendar — otherwise the history is. Demo mode feeds no live pace (`RouteHost` passes no
  speed outside GPS mode), so a demo drive never writes it; Calculate does not clear it.
- [x] **F7 — The source (D15).** The pace carries its source as a value beside its number, produced where the
  pace is chosen and read by the panel header and the Speed History section: **Live** once this trip's
  eligible readings fill a quarter of the window — the point from which they can move the number, not the
  first reading, when the number is still the starting pace (decided 2026-10-22) — else **Today** while the
  day's saved pace stands, else **History** (any non-empty history, **D14**), else **Default**; demo mode
  never reads Live. The words, the "Pace now" line and the header's new format stand in
  `## Settings surface`'s table.

### A — Bugs

- [x] **A1 — Seeded window is in the wrong order.** The seed now orders the trips by start time, oldest
  first, and the later fold drops the oldest; pinned by a test fed newest-first.
- [x→F] **A2, A3, A4** — the harbour start overwriting the history with 25 kn, the plateau dying after ten idle
  minutes, and a drift reading counting as cruising are removed by F1/F2 and kept as acceptance cases.
- [x] **A5 — Seeding runs on the main thread.** The reduction runs on `Dispatchers.Default` inside
  `seedSpeedHistory`, and only the settings write returns to the caller.
- [x] **A6 — The startup seed can spin or give up.** It now waits, bounded (`SPEED_HISTORY_SEED_WAIT_MS`), on
  the speed-zone index and the loaded track list as flows and runs when both stand — the list arriving is the
  retry — and never overwrites a history that appeared meanwhile (**D8**).

### B — Parts from the plan (the plan stands, the code does not)

- [x] **B1 — N must be a live read.** The store keeps up to 15 of each kind (**G3**), the profile reads the
  last N, and the shown value is derived from the store (`AppSettings.speedHistoryProfileKn`) instead of
  persisted (**D3**: the store is never trimmed to N).
- [→F5] **B2** — the recent-weight slider is replaced by the pace-window slider.
- [x] **B3 — "Reset on" and "today at" always agree.** The Calculate snapshot (date and speed) is kept apart
  from the live profile (**D4**: two lines); a fold leaves the snapshot as it was.
- [x] **B4 — "Last N tracks" are chosen by edit time.** The seed orders by `startTimeMs` (**D7**).
- [x] **B5 — "Water tracks" are not checked.** The seed and the fold-in skip a track the summary's own
  position reading calls land (`TrackSummary.positionIsWater`, **D5**). The code check the plan asked for:
  the repository already classifies each track at its index pass, and a track nothing could classify counts
  as water by the track filter's own rule, so the history follows that one rule rather than a second
  classifier in the seed.

### G — The demo flag and the Include-demo setting (D13)

- [x] **G1 — The flag on the track.** `Track` and `TrackSummary` gain a `demo` boolean on a fresh proto number
  (22) with default `false`, so an old blob reads as GPS and an older build still reads a new one; the
  summary is built from the track and copies it. The recorder sets it when it takes a demo-mode sample (the
  one `TrackSample` path carries `isDemo`, set by the UI's demo feed and never by the service's GPS
  listener), so a trip that ever ran on the simulated boat is demo and a pure GPS trip never is; a merged
  track is demo when any source is.
- [x] **G2 — The export carries it with no exporter change.** The GPX blob is the whole `Track` protobuf, so
  the new field travels in `<maro:data>` and returns on import; a foreign GPX has no blob and builds a track
  with the default, GPS. A proto round-trip test pins it.
- [x] **G3 — The store keeps both kinds.** Up to 15 trip entries of each kind, each with speed, open-water
  minutes, start and the flag; the fold-in appends to its own kind and drops that kind's oldest; Calculate
  seeds up to 15 of each kind from the eligible tracks — never only N, so raising N after a Calculate still
  finds trips (**D3**).
- [x] **G4 — The setting.** One switch, **Include demo tracks**, default off, persisted beside the other pace
  settings in `SettingsManager`, in both locales through `@StringRes` ids, shown in the Speed History
  section. Off, the profile reads the last N GPS trips; on, the last N of both kinds together by start time
  (**D7**). Moving it, like moving N, recomputes the shown value from the store with no Calculate.

### C — Stale text and stale docs

- [x] **C1** — the Settings pace description read "At Any…" (EN) and "Sur Indifférent…" (FR); both now say
  Now / Live, as the table above.
- [x] **C2** — the `SpeedHistorySeeder` class note and the `RoutePace` note are rewritten with F1/F3.
- [x] **C3** — this plan's `## Settings surface` said Calculate is where the heavy work lives and never on a
  cold start, beside a startup seed; it now states one rule once, per **D8** (row 9).
- [x] **C4** — the `FEAT_DSC_Route` pointer no longer says "(in design)".
- [→F2] **C5** — closed by F2, which names it; its own entry was never written.
- [x] **C6** — `routeRestricted` no longer defaults to `{ _, _ -> false }`: the parameter of `OverlayLayer`
  and of the trip reduction is required.
- [x] **C7** — this plan's `## Outcome`, `## The model`, the gate and dials sections, `## Scenarios` and
  `## Open points` are rewritten to the window average.

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

- [x] **E1** — seed order newest-first, and the fold drops the oldest (A1): `SpeedHistoryTest`.
- [x] **E2** — `PacePlateauTest`: every row of the worked-cases table (F1), plus uneven reading spacing, the
  10 s reading cap, a reading under 4 kn ignored, gap time entering at the shown pace, and symmetry (a
  mirrored up/down case gives mirrored values).
- [x] **E3** — fold keeps 15 and the median reads the last N (B1); the snapshot and the live value diverge after
  a fold (B3): `SpeedHistoryTest`, `SpeedHistoryActionsTest`.
- [x] **E4** — water-share and start-time ordering (B4, B5): `SpeedHistoryActionsTest` over `seedCandidates`.
- [x] **E5** — the demo flag: a track without it reads as GPS, a blob round-trips it, and the profile moves
  at once when the switch moves, with 15 trips kept per kind and a Calculate seeding 15 of each (G1–G4):
  `TrackDemoFlagTest`, `SpeedHistoryTest`, `SpeedHistoryActionsTest`, `RoutePaceFeedTest`. The recorder's own
  sample path rests on `Track.markedFor` (tested) and the device — no recorder harness exists.
- [x] **E6** — the day's pace: a second trip the same date starts from the morning's pace, a trip on another
  date from the history, a trip crossing midnight keeps its pace, and a demo drive reads no live pace (F6):
  `RoutePaceFeedTest`. The write and its restart survival rest on `AppSettings.withDayPace` and the
  preference round-trip of the settings.
- [x] **E7** — the source and the thin history: each of the four sources is reported at its moment, Live only
  once this trip's readings fill a quarter of the window, a history of two trips with N at 5 reads as
  History, and an empty one reads as Default (F7, D14): `RoutePaceFeedTest`, `RoutePaceTest`.

### Order of work

Every decision is settled (D1–D15, the Live threshold of F7 included); the six steps ran in this order.

1. **The live pace** — F1, F2, F5 with E2.
2. **The trip history** — F3, F4, A1, B1, B3, B4, B5, C6 and G1–G4, with E1, E3, E4, E5.
3. **The day's pace and the source** — F6, F7 with E6, E7.
4. **The seeding** — A5, A6.
5. **The text** — C1, C2, C4, C7.
6. The full unit suite, `assembleDebug` and `#bake`.
