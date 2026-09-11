# Ui_Settings — Hydration (2026-09-11)

## State — session complete and merged

The session branch `feature/settings-menu-clean` is **merged into `develop` via PR #227** (`1c2f681`). Its final commits were `6ff95b3` (item A colours + stale-code/properties cleanup) and `b3483d0` (docs/tracking). Work then moved to a new branch **`feature/menu-twks`**, created from `origin/develop` — which already contains the merge, so the new branch has the row model.

Delivered this session:

- **Row family normalization R1–R8** — one row model across the overlay: renames (`ToggleRow`, `SliderRow`, `SegmentedRow`, `ColorRow`, `ColorPairRow`), new `RangeSliderRow` + `CardDescription`, 13 hand-rolled inline toggle rows and the hand-rolled dividers converted, `CategoryToggleGroup` (internal) rebuilt on `ToggleRow.leadingIcon`, and **container-owned horizontal inset** (`Card`/`NestedCard` own it; rows pad vertically only — grep invariant: `AppConfig.uiPaddingCardHorizontal` exactly 2 in the overlay). `SettingsFrequencyRow` deleted — GPS frequency became a 3-option picker with per-stop captions, the one deliberate behaviour change. Builds SUCCESS after every step; R3/R4 and R7 device-validated.
- **Item A — sub-section title colour** — `SubSectionHeader` and `SingleColorSubSection` titles promoted from `ui.settings.text.muted` to `ui.settings.text.primary` (16sp SemiBold) at 3 sites (7 call sites inherit); descriptions stay `secondary`, `CardDescription` stays `muted`. Hierarchy is now weight + spacing. Device-validated.
- **Stale-code/properties cleanup** — 127 unused imports, dead `Expander(labelStyle)` parameter, a dangling KDoc, 4 orphan property keys with 2 unused accessors.
- **Docs** — guidelines rewritten for the row model (§1 naming rule + container-owned inset, §2.0–§2.2, §2.6–§2.9, §4), then corrected again for the cleanup (§2.5 and §4 dropped the now-impossible `labelStyle` override; §2.10's popup title no longer claims "SubSectionHeader style"). `color-scheme.md` §7 text roles synced.

## Open

- Nothing open for Ui_Settings — follow-ups A–D are all closed and `### Tab finalization follow-ups` is folded.
- Carried backlog (documented, not implemented): popup section titles still use the dashboard `uiDashboardTextMuted` token (§2.10, a known token-scope wart); tokens referenced only from docs were kept rather than deleted; 2 unused `maro.properties` keys.
- Prior, still outstanding: `feature/refact-C12` holds 3 commits (`ec57458`, `a000c18`, `96259b5`) needing push + PR.

## Key Files

- `app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt` — overlay, 4 tabs, and the row primitives (`ToggleRow`, `SliderRow`, `RangeSliderRow`, `SegmentedRow`, `CardDescription`, `Card`, `NestedCard`, `SectionDivider`, `Expander`)
- `app/src/main/java/ykws/android/maro/ui/map/RegulatedZoneComponents.kt` — `CategoryToggleGroup`
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt` + `app/src/main/assets/ui.properties` / `colors.properties` — tokens
- `docs/ui-component-guidelines.md` — canonical UI rules; `docs/color-scheme.md` §7 — colour roles

## Plans of record

- `xTrack/Ui_Settings/260911_FEAT_PLN_Ui_Settings_row-naming-normalization.md` — R1–R8 (implemented)
- `xTrack/Ui_Settings/260911_FEAT_PLN_Ui_Settings_section-title-color.md` — item A (implemented)
- `xTrack/Ui_Settings/260911_FEAT_PLN_Ui_Settings_tab-finalization.md` — phase 1 (implemented)
