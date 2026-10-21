<!-- scope: feature -->
# Route — the `Any` pace's reference: the last sustained free-water speed

## Outcome

The `Any` stop's observed pace is today the 75th percentile of the last ten wall-clock minutes of GPS
speed ([`RoutePace.WINDOW_MS`](../../app/src/main/java/ykws/android/maro/data/route/RoutePace.kt:27)),
which forgets a cruise the moment the boat idles and lets harbour crawl into the quantile. The discussion
settled a different model: **the reference is the most recently sustained free-water cruise speed, read as
an abstraction off the smoothed speed curve of the current leg, with no wall-clock expiry** — so a wife's
moment never counts, a sea change follows once it holds, and the value persists past idle. A **learned
history** of those earned speeds stands behind it, so a cold start plans on the boat's own past rather than
the shipped 25 kn.

**Date:** 2026-10-21 · **Status:** in design · **Order:** the user's word — "explain", the `Any` challenge,
"last relevant speed", "sustained", "weight recent navigation", "plan this".

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
- **Only earned references enter the history** — each sustained speed folds into a slow average, so a blip
  or a harbour crawl never teaches it; navigation is "as fast as makes sense", so the history learns the
  boat's real weather-and-mood cruise and the fishing-crawl objection is moot.
- **The history can be seeded from the last water tracks** — the five most recent recorded water tracks
  each reduce to one cruise speed by the same abstraction and average into the first learned value;
  route-saved tracks are excluded because their points are plan arithmetic, not navigated speed, and a
  derived speed is second-class wherever a stored `speedMps` is absent.

## The gate as a smoothed curve

- **Flatten** with a moving high percentile (median or 75th), recency-weighted by a linear ramp from 0 at
  the oldest sample to 1 at the newest, over a window of about 60 s.
- **A dip shorter than the window is a small weight share** and cannot cross the percentile alone — the
  wife's blip vanishes by construction, with no separate "ignore" rule.
- **A new level held long enough to outweigh the older plateau becomes the reference** — the duration test,
  expressed as weight instead of a timer.
- **The new reference is the plateau's central value**, not the spread around it.
- **A separation dead-band** (~2 kn) stops throttle jitter from constantly re-establishing the reference.
- **A relative cruise floor** (max of 5 kn and half the current reference) keeps harbour crawl out and a
  slow cruiser in.

## Dials — the user's call, because they change what the route shows

- The percentile level (robustness), the window length / weight timescale (lag versus moment), the dead-band,
  and the relative floor.

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

- The dial values (60 s, ±10 %, 2 kn, half-reference floor) are proposals, not settled.
- Whether the abstraction is a median or a high quantile, and how steep the recency ramp, follow the two
  dials moving together: more recency weight demands a higher percentile.
- How fast the history adapts — the EWMA's α, or a plain average capped at some reference count — sets how
  long an old habit lingers after the boat's behaviour changes.
