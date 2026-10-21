# Context Hydration — Route — 2026-10-21

**Last Bake:** 2026-10-21 20:14 UTC — written by `#bake` on `feature/whatEver` (cut from `origin/develop` by the user's `#new whatEver`); nothing of this branch is committed before this bake.

**Directive trace:** no covered action class was met. No code changed — the session was discussion, one plan file written on the user's "plan this", and the git write on the user's own `#commit`. No dependency was added, no binary file was read, the device was never touched, and every claim about the code came from a file read.

## State

**The `Any` pace's observed-speed source is redesigned in a plan (2026-10-21).** The discussion settled a new model: the reference becomes the last sustained free-water cruise speed, read off the recency-weighted smoothed speed curve of the current leg, with no wall-clock expiry — a wife's moment never counts, a sea change follows once it holds, and the value persists past idle. A **learned history** of earned speeds stands behind it, seeded from the last five water tracks, and the shipped 25 kn stays only as the first-ever floor. Nothing implemented — captured in [`261021_FEAT_PLN_Route_observed-pace-reference.md`](261021_FEAT_PLN_Route_observed-pace-reference.md).

**Open:** the four dials (percentile level, window/weight timescale, dead-band, relative floor), the history's adaptation rate, and the settings surface the user is still discussing — a System/Navigation section with a track-count look-back, the tracks-list filter, a Calculate action and two status lines.

## Target Files

- `app/src/main/java/ykws/android/maro/data/route/RoutePace.kt` — the window/quantile today; becomes the smoothed-curve reference
- `app/src/main/java/ykws/android/maro/ui/map/RouteViewModel.kt` — `observePace` feeds it
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt` — `routePaceForSearchKn` and the fallback
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — the persisted learned value
- `app/src/main/java/ykws/android/maro/data/track/` — the track store read to seed the history
- `xTrack/Route/261021_FEAT_PLN_Route_observed-pace-reference.md` — the design

## Next Step

Settle the settings surface (the System/Navigation section), then the four dials, before any implementation.
