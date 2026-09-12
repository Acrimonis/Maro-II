# Performance — Hydration (2026-09-12 09:40 UTC)

## State — power-management Phase 1 COMPLETE and reviewed (uncommitted)

Branch **`feature/staying-alive`**. Phase 0 + Phase 1 of
`260912_FEAT_PLN_Performance_power-management-centralization.md` are implemented, building, reviewed by
Ask and audited by Debug.

- **New `data/power/`** — `PowerPolicy.kt` (framework-free, stateless: hold while speed is above the
  threshold **or** within the grace of the last touch; unknown speed holds, stale speed releases;
  additive master-vs-gate semantics) and `PowerKeeper.kt` (screen-channel scope: `StateFlow<PowerState>`,
  pushed settings/speed/touch inputs, recording observer, 5 s grace ticker, exemption query).
- **Settings** — movement-gate, threshold and grace keys plus `batteryOptimizationPrompted`; the
  `keepScreenOn` default contradiction resolved to `false` (no behaviour change); legacy
  `maro_battery_prefs` migrated in `init`.
- **UI** — System → Screen: toggle renamed to "Don't lock the phone while the app is open", expander
  "Release the screen when stopped" with the gate toggle, threshold slider (0.5–5 kn) and lock-delay
  slider (1–15 min). EN + FR complete.
- **Constants** — grace bounds/default, threshold and staleness bound live in `maro.properties`
  (`power.screen.*`) behind typed `AppConfig` accessors. `PowerPolicy` references neither properties nor
  prefs.
- **Validation** — 17 unit tests green; `apk-build.bat` SUCCESSFUL.

## Next step

1. **Cleanup pass (no behaviour change)** — D1 stale "5–30" range in the `SettingsManager` KDoc, D2 the
   ticker KDoc contradicting the code, D3 a dangling sentence in the `PowerKeeper` KDoc, D4 two dead
   imports in `MainActivity`, the MapScreen exemption-query gap, and the ticker nit.
2. **Device verification** (plan §11) — grace release at the 1-minute minimum, interaction reset at
   ~30 s, speed gate in demo mode, master-off regression, recording floor backgrounded.
3. **Two open decisions** — confirm the 5-minute default lock delay; and whether the 1 kn threshold
   reuses or deliberately diverges from `GpsLocationSource.MIN_SPEED_MPS` (plan §8).

## Warts / follow-ups

- Background process management is **untouched by design**: the service is still unconditional, so
  `PowerState.keepAlive` is computed but consumed by nobody until phase 3.
- Phases 2–4 stay gated on the Tasker reconciliation (global todo in `GLOBAL_CONTEXT.md`).
- `PowerKeeper` itself has no unit tests; the risk sits in stale release and grace expiry, covered only
  indirectly through the policy tests.

## Plans of record

- `xTrack/Performance/260912_FEAT_PLN_Performance_power-management-centralization.md`
