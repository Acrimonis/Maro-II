# Performance — Hydration (2026-09-12 10:57 UTC)

## State — power-management Phase 1 COMPLETE, reviewed, cleaned (cleanup uncommitted at bake time)

Branch **`feature/staying-alive`**. Phase 0 + Phase 1 of
`260912_FEAT_PLN_Performance_power-management-centralization.md` are implemented and committed as
`82cee3f` (feature) and `ba4c187` (freshness fix). The cleanup pass and the review-findings pass sit on
top, APK-green.

- **`data/power/`** — `PowerPolicy.kt` (framework-free, stateless: hold while moving **or** within the
  grace of the last touch; unknown speed holds, stale releases; additive master-vs-gate semantics),
  `PowerKeeper.kt` (screen channel: `StateFlow<PowerState>`, pushed inputs, recording observer, grace
  ticker gated on a live hold, exemption delegation), `SpeedFreshness.kt` (when a reading is genuinely
  new) and `BatteryExemption.kt` (the exemption question's single home, shared by all four trigger sites).
- **Settings** — movement-gate, threshold, grace and `batteryOptimizationPrompted` keys; `keepScreenOn`
  default `false`; legacy `maro_battery_prefs` migrated in `init`.
- **UI** — System → Screen: lock-phrased toggle plus an expander with the gate, threshold (0.5–5 kn) and
  lock-delay (1–15 min) controls. EN + FR complete.
- **Constants** — `maro.properties` (`power.screen.*`) behind typed `AppConfig` accessors.
- **Validation** — 23 power unit tests green; `apk-build.bat` SUCCESSFUL.

## Next step

1. **Device verification** (plan §11) — the one thing still unrun: grace release at the 1-minute minimum,
   one grace period after the last touch in demo mode; the master-off check that isolates an external
   screen-awake source (Developer options "Stay awake while charging") from our flag; recording floor.
2. **Confirm** the 5-minute default lock delay, chosen because 15 became the maximum under 1–15.
3. **Push and open the PR** — `#push` was invoked; the branch is otherwise ready.

## Warts / follow-ups

- Background process management is **untouched by design**: the service stays unconditional, so
  `PowerState.keepAlive` is computed but consumed by nobody until phase 3.
- `PowerKeeper.isExemptFromBatteryOptimizations()` has no caller by design — it is the documented seam
  for the phase-3 service lifecycle, not an oversight.
- Three **pre-existing** unit-test failures (`MarkerFilterMigrationTest` ×2, `RegulationAggregatorTest` ×1)
  fail at HEAD and block a green full-suite run; logged as a global todo.
- Phases 2–4 stay gated on the Tasker reconciliation.

## Plans of record

- `xTrack/Performance/260912_FEAT_PLN_Performance_power-management-centralization.md`
