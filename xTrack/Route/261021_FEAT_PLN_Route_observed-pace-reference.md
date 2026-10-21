<!-- scope: feature -->
# Route — the `Any` pace's reference: the last sustained free-water speed

## Outcome

The `Any` stop's observed pace is today the 75th percentile of the last ten wall-clock minutes of GPS
speed ([`RoutePace.WINDOW_MS`](../../app/src/main/java/ykws/android/maro/data/route/RoutePace.kt:27)),
which forgets a cruise the moment the boat idles and lets harbour crawl into the quantile. The discussion
settled a different model: **the reference is the most recently sustained free-water cruise speed, read as
an abstraction off the smoothed speed curve of the current leg, with no wall-clock expiry** — so a wife's
moment never counts, a sea change follows once it holds, and the value persists past idle. A **learned
history** — a sliding window of the last N trip speeds reduced by a recency-weighted median — stands behind
it, so a cold start plans on the boat's own past rather than the shipped 25 kn.

**Date:** 2026-10-21 · **Status:** in design · **Order:** the user's word — "explain", the `Any` challenge,
"last relevant speed", "sustained", "weight recent navigation", "plan this", "add to plan".

## The model

- **One reference value**: the last sustained free-water cruise speed. Full weight to the newest
  established speed, the older one dropped outright — recency by replacement, never by decay.
- **Source**: GPS speed-over-ground; restricted samples (inside a speed zone or the 300 m band) are dropped
  first, so the curve is the navigated free speed alone, never the forced limits.
- **The abstraction**: flatten the leg's speed curve, then read its plateau as the current cruise speed.
- **No expiry**: the reference stands until a newer sustained speed establishes, the learned history stands
  in until the first one does, and the shipped 25 kn is the floor beneath both.
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
 reads *now*, while a finished trip reduces to a **plain median** of its speed points, GAP seams and idle
 gaps excluded, stored `speedMps` preferred and derived speeds second-class: one trip is one number, not a
 curve.
- **The seed is the user's Calculate action** — it rebuilds the window from the last N water tracks under
  the tracks-list filter, route-saved tracks excluded because their points are plan arithmetic, not
  navigated speed, and a derived speed second-class wherever a stored `speedMps` is absent; the filter is
  one-shot, read only at the moment Generate runs.
- **Each completed trip folds into the window** — a saved trip appends its cruise speed and drops the oldest
 past N, so the history builds itself without a Calculate press; Calculate only re-seeds the window from the
 filtered list.

## The gate as a smoothed curve

- **Flatten** with a moving 75th percentile, recency-weighted by a linear ramp from 0 at the oldest sample
  to 1 at the newest; the span adapts to the curve — no fixed window.
- **A dip too short to be a real level is a small weight share** and cannot cross the percentile alone —
  the wife's blip vanishes by construction, with no separate "ignore" rule and no dead-band.
- **The current cruise speed is the plateau of the smoothed curve** — read off the flattened curve live on
  every sample, never a frozen value, so a new level becomes the reference the moment the curve settles on
  it and a blip never does.
- **The `restricted` flag is the sole "not cruising" guard** — a reading inside the 300 m band or a speed
  zone is dropped before smoothing, and the history seed runs the same band/zone check per point, so no
  floor is needed.

## Dials — the user's call, because they change what the route shows

- The percentile (75th) and the recent-sailing weight (the recency ramp, 0–100 %) are the two remaining
  knobs; the window, the dead-band and the floor are all read off the smoothed curve and the restricted flag,
  not set.

## Settings surface — System / Navigation

- **Track count** — a slider from 1 to 15, default 5; the N is a **live read** over the stored trip
  speeds, so moving the slider re-slices the history instantly, no Generate needed.
- **The store keeps up to 15 trip speeds** — the top of the slider, so raising N never loses an older trip;
  N only trims how many the weighted median reads, while a completed trip appends and drops the oldest past
  15.
- **Filter** — a filter icon opening the full filter popup, default **empty** (all tracks), **persisted**,
  read only at the moment Generate runs, which owns the *content* while N owns the *size*; the section also
  shows how many tracks the filter selects.
- **Calculate Speed History** — rebuilds the window from the last N filtered tracks; the heavy work lives
  here, never on a cold start.
- **"Speed history reset on [date] to [speed]"** — the last Generate's snapshot.
- **"Speed History today at [speed]"** — the window's current value, every fold since Generate included.
- The action title, both status lines and the pace stop's `-- Now --` label are user-facing strings in both
  locales, read through `@StringRes` ids.

## Files

- `app/src/main/java/ykws/android/maro/data/route/RoutePace.kt` — the window/quantile today; becomes the
  smoothed-curve reference.
- `app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt` — `observePace` feeds it; the fallback
  wiring stays.
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt` — `routePaceForSearchKn` unchanged; the
  fallback's one home.
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — the persisted learned value,
  seeded and updated beside the other pace settings.
- `app/src/main/java/ykws/android/maro/data/track/` — the track store read to seed the history.

## Scenarios

1. **Wife's moment** — 30 kn, a ten-second complaint, back to 30. The dip is a sixth of the window, below
   the percentile; ignored, the reference stays 30.
2. **Wife's sustained word** — 30 → 25 and stays. The 25 fills the recent window and outweighs the old
   plateau; after the ramp it becomes the reference.
3. **A wave, a very short stop** — a few seconds near zero. Too small a share; smoothed away, the reference
   is unchanged.
4. **Harbour crawl** — minutes at 3 kn. Below the relative floor, never a candidate; the last cruise stands.
5. **Cold start** — no sail yet this trip. The learned history plans first, and only a first-ever launch
   without memory falls back to the shipped 25 kn, until the first sustained run establishes.

## Open points

- The percentile is the 75th, settled; the smoothing span, dead-band and floor are decided by the curve and
  the `restricted` flag, not set.
- The recent-sailing weight is a 0–100 % slider, the share given to the newest sailing, default 60 %.
- The filter's default is **empty** and the chosen value is **persisted**; the count is a 1–15 slider,
  default 5.
- The `Now` stop reads *Live* in French; the dynamic value reads in the dashboard route search
  display.
- The stored-track reduction is the **plain median**, no recency — the live leg keeps its 75th percentile,
  the trip reads its middle value, and the window keeps its weighted median.
- The trip fold-in is unwired — each saved trip must append its cruise speed and drop the oldest past N.
- The stored-track zone/band exclusion (the per-point lookup a saved track lacks) is the remaining heavy
  piece.
