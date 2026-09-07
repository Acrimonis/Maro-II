# Ui_Settings — Hydration (2026-09-07 10:09)

## State
Settings page UI, persistence (SharedPreferences), settings widgets, and UX. Latest work (2026-09-07): the **properties-normalization** plan was implemented on `feature/settings-misc` (build green, no commit), followed by a full leftover-extraction + cleanup pass that resolved all Ask-review follow-ups: (A1) 7 inline control-group labels migrated 14sp→16sp `uiFontToggleSize` + the "🚤 Boat length" label in `BoatSizeSlider` (RegulatedZoneComponents.kt:398) also migrated; (A2/B4) `BoatSizeSlider` dead non-nested branch deleted; (A3) added `ui.spacing.label.control` token + wired shared widgets + guideline §3; (A4) `uiFontToggleSize` fallback default fixed 14f→16f; (B1) pruned 8 dead tokens from AppConfig.kt + ui.properties; (B2) fixed stale doc refs to pruned tokens; (B6) corrected stale `zone.properties` doc comment. Final Ask review verdict: COMPLETE/ACCEPTABLE — build green, 0 pruned-token refs, ui.properties↔AppConfig token consistency confirmed, no control-group label left at 14.sp in a NestedCard. Prior work (transparency normalization + low-depth 2-depth redesign, Card/Expander/NestedCard refactor, guideline consolidation, marker "Belongs to track") remains implemented. **`header-normalization` plan remains pending (not implemented).**

## Target Files
- `app/src/main/assets/ui.properties` (renamed from `ui-tokens.properties`) — UI tokens (spacing/padding/radius/font/divider/nested-card)
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt` — loads maro→ui→colors; typed UI-token accessors
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — settings composables consume ui.properties tokens
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — transparency fields, low-depth crash/start depth fields
- `app/src/main/java/ykws/android/maro/ui/map/LowDepthWarningBitmap.kt` — two-depth alpha ramp (crash → start)
- `app/src/main/java/ykws/android/maro/ui/map/MarkerHalo.kt` / `MapOverlayRenderer.kt` — transparency % → alpha
- `docs/ui-component-guidelines.md`, `docs/color-scheme.md` — ui.properties + re-homed keys

## Next Step
Open the pending `header-normalization` plan (260905, not yet implemented), then the `render-tweaks` todo and deferred `settings apply on close` section. BUILD SUCCESSFUL.
