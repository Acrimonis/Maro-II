<!-- scope: feature -->
# Air and Water Temperature — Functional Decisions

Current functional decisions only. Each revision overwrites the previous one. Implementation design is out of scope here.

Vocabulary: the **temperature tile** is the square shown on the map. The cache is made of **cells** arranged in **grid A** and **grid B**; the centre of a cell is a **sample point**. "Layer" is reserved for the Layers tab.

## Sources

- Air: Open-Meteo Weather API, Météo-France AROME model, in its seamless form (AROME blended with the coarser Météo-France models, so a point the fine grid misses still answers).
  - Request: `https://api.open-meteo.com/v1/forecast?latitude={LAT}&longitude={LON}&current=temperature_2m&models=meteofrance_arome_seamless`
  - Spatial granularity about 1.3 km; model refresh every 3 hours with hourly output steps.
- Water: Open-Meteo Marine API, Météo-France SMOC / Copernicus CMEMS baseline.
  - Request: `https://marine-api.open-meteo.com/v1/marine?latitude={LAT}&longitude={LON}&current=sea_surface_temperature`
  - Spatial granularity about 8 km; model refresh every 12 to 24 hours with hourly output steps.
- The app is non-commercial, so the free tier applies. Free tier, no API key, limits per IP: 600 requests/min, 5,000/hour, 10,000/day, 300,000/month.
- Units: °C, rounded to whole degrees.

## Reference point

- Values are for the marker position, in both GPS and free modes. The marker is the map centre: the boat while the map follows it, the viewpoint once the map has been dragged.
- A drag therefore changes the nearest sample point. The tile re-reads the cache at once, and a fetch, when one is due, waits until the marker has stood still for 0.75 s (`maro.properties`).
- The marker always has a position: the map centre is never empty. It is seeded from the saved viewport (Cannes by default), follows the boat, dead-reckons from the last course and speed when the fix is lost, and then stays on the last known position. The temperature tile reads that same position, so it needs no fallback of its own.
- Water or land, for the marker and for sample points, uses the containment test without the 6 NM limit. When the answer is unknown (no coastline loaded), the position counts as land.

## Temperature tile

- Square tile at the bottom left of the map, placed above the column of zone tiles and icons.
- Shows the temperature in °C, in a font close to the speed-limit tile's number; the tile alone has narrower side padding, 4 dp on the left and 1 dp on the right, so the reading fits (`temperature.tile.fontSp`, `temperature.tile.paddingStart`, `temperature.tile.paddingEnd` in `maro.properties`).
- Two views: air and water.
- The air view is green (`status.temperature.air` in `colors.properties`, the compliant green); the water view takes the isOnWater icon colour. The tile background is more opaque than the other map squares (`temperature.tile.alpha` in `maro.properties`), so the green stays strong.
- By default the tile shows water when the marker is on water and air when it is on land. There is no smoothing at the shoreline.
- A view chosen by tapping is sticky: it stays, with no timeout, for the rest of the current run of the app, until the next tap. A restart returns to the default view. If the tile was toggled to air while on water, air stays displayed. On land only air can show; the sticky choice applies again once the marker is back on water.
- Water view applies only when isOnWater is true. When isOnLand, the tile shows air only and tapping does nothing.
- Tapping the tile switches between the air and water views when water applies.
- A long press on the tile forces a refresh: all cached values are marked as expired and the remembered empty answers are cleared, and the nearest sample point of each source is fetched once, ignoring any back-off for that one fetch. With a connection, a request already in flight is dropped and asked afresh at once; with none, it is left to finish. The press answers at once with a vibration and a short toast ("Refreshing temperatures…", in English and French), and the old value stays in the subdued font until the new value arrives. If the new attempt fails it is reported again (see Refresh), and with no connection the press says so instead of waiting.
- A double tap does nothing.

## Refresh

- There is no distance gate. The nearest sample point (see Cache) replaces it.
- A fetch for a source runs when the nearest sample point in that source's grids has no cached value or an expired one.
- There is no fetch interval. A source is asked when the nearest sample point has no value or an expired one, and once the marker has stood still for 0.75 s, so a drag or the boat crossing into a new cell fetches at the settle. A cached answer, empty sea answers included, ends the asking; a failure backs off (below).
- A stationary marker refreshes a source when the value of its nearest sample point expires.
- Any failed call, for air and for water, is reported in a toast: the source, the HTTP status when there is one (429 and 5xx included) and the API's own reason, in English and French. One toast per streak of failures of a source, so the back-off retries stay silent; a new one follows after a success in between, or after a long press. A failure is never remembered as an empty answer.
- A failed fetch is not asked again until its back-off ends. Consecutive failures back off 1, 2, then 5 min and stay at 5 min until one succeeds. No fetch is attempted while offline.
- Water is fetched only when the marker is on water.
- Trying several water sample points is one lookup: they are tried one after another, and trying stops at the first value.

## Settings

- A section named "Air/Water Temperature" sits at the bottom of the Layers tab.
- A switch turns the feature on or off. Off stops all fetching and hides the temperature tile; the cache is kept and follows the same expiry rules; it is on by default.
- A line crediting the weather data to Open-Meteo.com, in English and French.

## Cache

- Each source (air, water) has its own grid A and grid B of square cells. Grid A is anchored at the homeport; grid B is the same grid offset by half a cell side on both axes.
- The homeport is fixed at build time.
- A grid-A cell's four corners are grid-B sample points, and the reverse.
- Each sample point, in either grid, is a fetch point and stores one value of its source with its fetch time.
- The cache persists across app restarts, bounded by its expiry times. A cache written by a build with different cell sizes is discarded.
- Expiry, from `maro.properties`:
  - air: 1 h (the model steps hourly),
  - water: 12 h (the sea model refreshes every 12 to 24 h).
- Air cells: side 1 km, grid offset 500 m, from `maro.properties`.
- Water cells: side 8 km, grid offset 4 km, from `maro.properties`. The nearest sample point is at most 4 km away.

## Value shown on the temperature tile

- Each view shows the value of the sample point nearest to the marker in that source's grids, across grid A and grid B. For air the nearest sample point is at most 500 m away.
- For water, sample points are tried in order of distance from the marker, skipping any whose centre is on land (the local isOnWater test, no request). The first one that returns a value is used, trying at most 10 sample points.
- A sample point's answer, empty or not, is remembered until its water expiry, so an empty one is not asked again.
- The nearest sample point is re-evaluated on every position update, which costs nothing when read from the cache.

## Stale states

- A value past its expiry is still shown, in a subdued font, until a fresh value replaces it.
- A water value taken from a sample point other than the nearest one, because that one is on land or returned nothing, is shown like any other value: subdued only once it has expired.
- Either view shows a dash when it has no value to show: no connection or an API failure with nothing cached, or, for water, all 10 tried sample points returning nothing. An expired cached value is shown in the subdued font instead of a dash.
- While the new nearest sample point has no value yet, the previous value stays shown in the subdued font until its fetch answers.

## Text

- All labels (view names, placeholders, settings text) live in `strings.xml` in English and French.

## Open questions

None.

## Implemented

Shipped on `feature/temperature` : the temperature tile above the zone-icon stack, the two-grid cache with the nearest-sample-point rule, the Open-Meteo client, the refresh rules, the sticky view, the long-press refresh with its toast and vibration, the error toasts, and the Air/Water Temperature section at the bottom of the Layers tab. The unit tests and the debug APK build pass; a run on the device was reported fine.

Where it lives: `data/temperature/` (grid, cache, client, engine), `ui/map/TemperatureViewModel.kt`, `ui/map/TemperatureTile.kt`, `ui/map/TemperatureViewChoice.kt` (the pure tap and view rules), the `temperature.*` block of `maro.properties` and `status.temperature.air` in `colors.properties`. `MapSurface` and `MapToggleSquare` gained an optional long press and optional left and right padding; the other squares keep their defaults. The code map entry is in `docs/maro-code.md`.

Changes since the first build:
- The air request uses `meteofrance_arome_seamless`; the plain model returned nothing.
- The air view is green; the tile is more opaque than the other squares (`temperature.tile.alpha`).
- The pulsing dot and its busy state were removed: the answer is immediate.
- There is no fetch interval any more (setting, slider, saved key, property): a new nearest sample point is simply an uncached one, and the settle, the cache and the back-off cover what it did.
- A long press during a request in flight (with a connection) drops that request and asks again at once, so the press starts a fresh request.
- A back-off now runs from the failure, not from the start of the request, and the 30 s re-check goes through the settle like a move.
- The reading is 24 sp with 4 dp on the left and 1 dp on the right.
- Every failed call is reported in a toast, air and water, once per streak; a long press retries and reports again, or says there is no connection.
- A fresh water value taken from a neighbouring on-water sample point is no longer subdued.
- The tap and view rules moved to pure functions with their own tests.
- A water square's land-or-sea answer is asked once and remembered, and the candidate list is kept per position, so a pan no longer re-tests up to 40 square centres per frame.
- The water window widens (40, 80, 160 nearest centres) while land keeps fewer than 10 on water; its sizes and the stationary re-check period are `maro.properties` keys (`temperature.water.window.*`, `temperature.recheckS`).
- An air answer with no temperature, and any reply that is not Open-Meteo's JSON, is a failure (backs off, reported), not a remembered blank. Only a well-formed sea answer with no value is quietly empty.
- One notices stream (refresh begun, problem) feeds the toasts; a new toast replaces the one on screen.
- The reading is set in dp-sized text, deaf to the system font scale, and shrinks for a value longer than three characters ("-10°").
- The cache file is replaced whole or not at all (Android's `AtomicFile`), written by one writer in the order the snapshots were taken.

- The settings section lost its "Refresh now" button and text; the long press on the tile is the one way to force a refresh. It is now the on/off switch and the Open-Meteo credit.

Findings settled without code:
- A lost GPS position needs no fallback: the map centre the tile reads is never empty (see Reference point).

Open findings:
- A Problem toast raised while the tile is not composed is lost (the stream has no replay); the engine reports a streak once.
- A water failure ends the lookup burst at that sample point, so a point the sea model refuses with an error, rather than an empty reply, stops the search there. The toast shows the API's reason if it happens.
- No test covers the memo's size guard, the notices stream or the tile text.
- Routed: the feature is tracked as `Ozers` (keyword `temperature` routes here).
