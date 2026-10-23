<!-- scope: review -->
# Batch four: the recorder's clock, the app's words (2026-10-23)

> On `feature/clock-and-copy`, stacked on the validation branch; CI compiles and tests every push,
> so what follows was written knowing the machine would check it. Paths are relative to
> `app/src/main/java/ykws/android/maro/`.

## 1. The items

| Finding | Fix | Files |
|---|---|---|
| O3 Recorder intervals on the wall clock | Every *interval* the recorder measures — the two geofence debounces, the stale-fix timeout, the dedup window, the idle clock and its flush, the elapsed ticker — reads an injectable monotonic clock (`monotonicNowMs`, `SystemClock.elapsedRealtime` by default), so a wall-clock step from a GPS or network time resync at sea can no longer stretch or collapse a decision. Timestamps a track stores (start, point offsets, idle session starts, finalize time, marker stamps) stay on the wall clock, which is what they mean. The lifecycle tests drive a real monotonic clock, since the platform's answers 0 on the JVM. | `data/track/TrackRecorder.kt`, `app/src/test/…/TrackRecorderLifecycleTest.kt` |
| O3, continued (review notes) | A track's saved total is prior span plus monotonic session time; each point's offset is the session's base (0, or the wall distance from the track's start taken once at resume) plus monotonic time since the session began; the idle timeline no longer reads idle across a GAP seam, so a resume gap is never counted as sitting still. A lifecycle test drives the clock by hand and checks offsets and totals; a unit test pins the seam rule. | `data/track/TrackRecorder.kt`, `data/track/TrackStats.kt`, two tests |
| O6 Cleartext allowance | The per-domain cleartext allowance for the tile server and three Overpass mirrors is gone; both speak HTTPS, so it protected nothing and opened a downgrade path on a shared marina network. The config now forbids cleartext outright. | `app/src/main/res/xml/network_security_config.xml` |
| U24 Hard-coded English | See §2. | |
| U23 Terminology drift | See §2. | |
| U25 Grammar and plurals | See §2. | |
| U20, U27 Colour-only breach | See §2. | |
| U28 Font-scale clipping | See §2. | |

## 2. Copy and dashboard cues

| Finding | Fix | Files |
|---|---|---|
| U24 Hard-coded English | Twenty literals moved to resources in both locales: the dashboard's "open water", "land" and "→ shore"; the track-rendering header and its Delete description; the two "Unknown" fallbacks; the marker's default name; the regulated-zone tag's "300 m Zone" and knots; the nine zone-category labels (now `@StringRes` on the item); the drawer's duration formats. `DepthViewModel.progressTitle()` had no caller and is replaced by `progressCounts` for a future composable to format. | `ui/map/DashboardPanel.kt`, `OverlayLayer.kt`, `MapScreen.kt`, `MarkersViewModel.kt`, `DepthViewModel.kt`, `RegulatedZoneComponents.kt`, `MenuDrawerOverlay.kt`, both `strings.xml` |
| U23 Terminology drift | One term per concept per locale, values only, keys unchanged: EN "300 m zone" (was 300m Zone / 300 m band / 300M), `NM` for nautical miles; FR `nd` for knots everywhere (was nds / nœuds / kn), « trace » for a recorded track everywhere (was parcours / piste / trajet), « zone des 300 m » (was bande), `NM`. Deliberately kept: FR « trajet » where it means a route's journey. | both `strings.xml` |
| U25 Grammar and plurals | "Last 2 month" and its siblings pluralised; "%d selected" and "Merge %d tracks" are `<plurals>` at their callers; two Settings labels in sentence case. | `ui/components/ListOverlayScaffold.kt`, `ui/map/TrackHistoryOverlay.kt`, both `strings.xml` |
| U20, U27 Colour-only breach | The speed card's subtitle says "over limit" (FR « au-dessus de la limite ») beside the limit when the speed exceeds it, so the breach is read, not only seen as a colour. | `ui/map/DashboardPanel.kt`, both `strings.xml` |
| U28 Font-scale clipping | `AutoSizeValue` divides its dp-derived fit by the system font scale before applying it as sp, with the 14 sp floor kept, so a large accessibility font no longer clips the dashboard value. | `ui/map/DashboardPanel.kt` |

## 3. Left out of this batch, deliberately

The Back-handler ladder and the screen and ViewModel split still wait for a Compose test harness;
the settings diet, onboarding and a daylight theme wait for product decisions; engine memory work
waits for a device measurement; the exported Tasker receiver stays as it is because Tasker cannot
hold a custom permission.
