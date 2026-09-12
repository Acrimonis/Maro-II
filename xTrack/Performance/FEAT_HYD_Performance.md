# Performance — Hydration (2026-09-12 09:58 UTC)

## State — power-management Phase 1 COMPLETE, reviewed, defect-fixed (fix uncommitted)

Branch **`feature/staying-alive`**. Phase 0 + Phase 1 of
`260912_FEAT_PLN_Performance_power-management-centralization.md` are implemented and committed as
`82cee3f`; the freshness fix on top of it is in the working tree, build-green.

- **`data/power/`** — `PowerPolicy.kt` (framework-free, stateless: hold while moving **or** within the
  grace of the last touch; unknown speed holds, stale releases; additive master-vs-gate semantics) and
  `PowerKeeper.kt` (screen channel: `StateFlow<PowerState>`, pushed inputs, recording observer, 5 s grace
  ticker, exemption query). Plus `SpeedFreshness.kt` — when a reading was last *genuinely* new.
- **Freshness fix** — `onSpeed` requires an `isNewReading` flag; GPS mode pairs the speed with
  `gpsStale`; demo mode is grace-governed (pan speed is not motion). A push is not a reading.
- **Settings** — movement-gate, threshold, grace and `batteryOptimizationPrompted` keys; `keepScreenOn`
  default resolved to `false`; legacy `maro_battery_prefs` migrated in `init`.
- **UI** — System → Screen: lock-phrased toggle plus an expander with the gate, threshold (0.5–5 kn) and
  lock-delay (1–15 min) controls. EN + FR complete.
- **Constants** — `maro.properties` (`power.screen.*`) behind typed `AppConfig` accessors.
- **Validation** — 23 unit tests green; `apk-build.bat` SUCCESSFUL.

## Next step

1. **Device retest** — grace release at the 1-minute minimum, one grace period after the last touch in
   demo mode; and the **master-off check** to rule an external screen-awake source (Developer options
   "Stay awake while charging") in or out of the original report.
2. **Commit the freshness fix**, then the outstanding cleanup pass (D1–D4 plus the MapScreen
   exemption-query gap and the ticker nit).
3. **Confirm** the 5-minute default lock delay.

## Warts / follow-ups

- Background process management is **untouched by design**: the service is unconditional, so
  `PowerState.keepAlive` is computed but consumed by nobody until phase 3.
- In GPS mode a lost fix now releases the screen after the 30 s bound — intended, and the first thing to
  verify on the device.
- Phases 2–4 stay gated on the Tasker reconciliation (global todo in `GLOBAL_CONTEXT.md`).
- `PowerKeeper` itself still has no unit tests; `SpeedFreshness` and `PowerPolicy` carry the tested logic.

## Plans of record

- `xTrack/Performance/260912_FEAT_PLN_Performance_power-management-centralization.md`
