<!-- scope: feature -->

# MapScreen settings-subtree extraction (code health) — step 1

## Context

[`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:387) is ~5,841 lines and bundles
three independent concerns: (1) the MapScreen orchestration monolith (~387–2700), (2) the entire Settings
overlay subtree (~3280–5700), and (3) small sheets/helpers. The Settings overlay subtree belongs to the
Ui_Settings domain but physically lives inside the map screen file.

## Goal (Option 1 — first move)

Mechanically extract the Settings overlay subtree out of `MapScreen.kt` into dedicated settings files, as
its own low-risk refactor commit, BEFORE the filters-link wiring. Zero behavior change. This shrinks
`MapScreen.kt` by roughly 40% and gives the follow-up filters-link and monolith-refactor passes smaller,
clearer files to work in.

Order agreed with the user: (1) this settings extraction, (2) finish filters-link wiring, (3) refactor the
MapScreen orchestration monolith later.

## Scope

Move the settings overlay subtree into a new file in the SAME package (`ykws.android.maro.ui.map`) so no
import or package churn is needed, e.g. `MapScreenSettingsOverlay.kt`:

- `SettingsOverlay` (~`3280`)
- `LayersSettings` (~`3405`)
- `NavigationSettings` (~`4239`)
- `PositionSettings` (~`4576`)
- `SystemSettings` (~`4779`)
- Shared settings-only helpers: `SectionHeader` (~`5089`), `SettingsLanguageRow` (~`5107`),
  `SettingsToggleRow` (~`5148`), `SliderRowContent` (~`5192`), `SectionDivider` (~`5244`),
  `SubSectionHeader` (~`5258`), `SingleColorSubSection` (~`5285`), `ColorSwatchButton` (~`5345`),
  `Card` (~`5358`), `NestedCard` (~`5372`), `Expander` (~`5390`), `ColorSwatchRow` (~`5440`),
  `ColorPickerDialog` (~`5494`), `ColorSwatchPairRow` (~`5563`), `SettingsFrequencyRow` (~`5632`)

Rules:
- Keep the same package so `internal`/`private` visibility and all existing references stay valid.
- Keep `SettingsOverlay`'s signature and parameters unchanged so the `MapScreen` callsite is untouched
  apart from nothing (same package — no import change).
- Move only composables used exclusively by the settings subtree; anything referenced by MapScreen or the
  map content stays put. A compile pass is the audit.
- Do NOT change logic, strings, colors, layout, or behavior in any moved composable.

## Implementation steps

1. Read `MapScreen.kt` from ~`3278` (SettingsOverlay doc) to end of `SystemSettings`/helpers and identify
   the exact contiguous movable block plus any interleaved shared helpers; confirm each moved symbol is
   not referenced elsewhere in `MapScreen.kt` outside the settings subtree.
2. Create `app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt` (same package) and move
   the subtree verbatim; adjust `private`→`internal` only where a moved composable is called from another
   moved composable in a way that requires it (private is fine within one file).
3. Delete the moved block from `MapScreen.kt`.
4. Build via `apk-build.bat`; confirm the settings drawer renders and behaves identically to before.
5. Commit as its own refactor commit (no behavior change).

## Files Affected

- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` (remove settings subtree, ~2,400 lines removed)
- `app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt` (new, same package, moved code)

## Verification

- `MapScreen.kt` line count reduced by ~2,300–2,500 with no logic change.
- Settings drawer (4 tabs: display/navigation/position/system + sub-sections) renders and edits settings
  identically.
- No import/package edits needed anywhere outside the two files (same-package move).
- Build SUCCESS.

## Ask-review notes (locked for implementation)

1. `SettingsOverlay` is `internal` ([`MapScreen.kt:3280`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3280)) and called only from `OverlayLayer.kt:739` (same package) — keep it `internal`; no import change.
2. The four page composables (`LayersSettings`/`NavigationSettings`/`PositionSettings`/`SystemSettings`) are
   `private` and called only from `SettingsOverlay` — keep them `private` in the new file.
3. Any shared helper still referenced by code that stays in `MapScreen.kt` (e.g. `RecordingExitSheet`,
   `ImportConflictSheet`, `MapContent`) must be `internal` in the new file (same package → trivial).
4. `RecordingExitSheet`/`ImportConflictSheet` (~5701+) are MapScreen-owned and REMAIN in `MapScreen.kt` —
   cut the move just before them.
5. Confirm the contiguous cut boundaries by reading ~3278 to ~5700 during implementation; the function
   inventory shows only settings composables there, but validate no MapScreen-owned top-level symbol is
   interleaved.
6. Same-package move means zero import/package churn; a move to a dedicated settings package can be
   considered later during the monolith refactor.

## Out of scope

- Refactoring the MapScreen orchestration monolith (~387–2700) — deferred to a later step after
  filters-link lands.
- The filters-link wiring (already planned; executed after this refactor commit).
- Any behavioral or visual change.

## Branch note

Apply on `feature/filters-link` as its own commit before the filters-link wiring, or on a dedicated branch
via `#new` if the user prefers separate history. Current refactor is on the filters-link branch.
