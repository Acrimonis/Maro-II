# Context Hydration — Ui_Settings — 2026-10-07

**Last Bake:** 2026-10-07 10:53 UTC — written by `#bake`
**Branch:** feature/tracks-rotes-norm — the path render engine's branch; this feature's part is the seed keys only, with a per-kind Settings split designed in the plan and not built

**Directive trace:** no covered action stopped. No dependency was added, no machine-shaped data file was opened, and no device was touched — no Settings UI changed, so nothing needed a device. Every claim about the code came from a file read or a build's own output; the run's figures — the 653-test scoped suite and the green `apk-build.bat` — are the commands' own output. This session's plan extension touched no Settings code, so it carries no figures of its own, and no git write ran before the commit that closes the session.

## State

**The Tracks/Routes appearance settings now seed from the unified `path.*` family.** The path render engine unifies tracks' and routes' line rendering behind one painter, so every value the Settings rows expose is a `path.*` key in `maro.properties`, read through the cascade `path.<kind>.<group>.<class>.<leaf>` (`AppConfig` + `PathProperties.kt`'s `pathKeyCandidates`). The rows read their seeds from `path.line.*` (widths, fades, colours, dash, the selection gold and the casing), `path.route.line.*` (the route kind's own pairs, widths and frozen dash), `path.gate.*` (the two route-scoped gates) and `path.pin.*` (the destination pin). No Settings UI, label or string changed — only where the seeds come from — so nothing here needs a device pass of its own.

**The user-visible change is the value a seed holds, never the row.** A row whose label, ladder or behaviour was untouched keeps it; the one consequence worth naming is that the three literals the file had kept hardcoded (the selection gold, the casing colour, the pin ring) are now keys, so they are configurable like every other path value.

**Designed, not built — the per-kind Settings split.** The plan's `## Speed and arrow display` section records the settled next step, and **no code for it exists**: every appearance and speed-and-direction row for tracks moves into a tracks block and for routes into a routes block, with **no shared control governing both kinds** and each row named for its own kind; the file keeps its global tier, read as the seed each kind starts from rather than as a switch. This is the surface that would reverse this bake's single-seed arrangement, so it stays in design until ordered.

**Open and recorded.** This feature owes no device pass for this pass. Named rather than lost: the four `path.*` colour values the settings expose (`path.line.color.from` / `.to`, `path.line.color.pinned.from` / `.to`) still reach the app as seeds only through `AppConfig`; the Settings rows read live values from `SettingsManager`, which is unchanged.

## Target Files

- `app/src/main/assets/maro.properties` — the `path.*` family, its banner and precedence, is the one home for both kinds' line rendering
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt` — the resolver and the seed fields the Settings rows fall back to; stale-key KDoc swept
- `app/src/main/java/ykws/android/maro/config/PathProperties.kt` — `PathKind`, `PathClass` and `pathKeyCandidates`
- `xTrack/Tracks/261007_FEAT_PLN_Tracks_path-render-engine.md` — the plan, its in-design speed/arrow section naming this feature as the per-kind rows' owner
- `docs/color-scheme.md`, `docs/ui-component-guidelines.md` — the `path.*` colour family and the reinforcement note

## Next Step

Nothing is owed here; the per-kind Settings split waits in the plan, in design, until it is ordered, and the feature's own open work is unchanged by this pass.
