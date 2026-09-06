<!-- scope: feature -->

# Settings Card Structure Refactor — Card / Expander / NestedCard

## Context

The settings UI has grown several overlapping structural composables with unclear roles:
`SettingsSliderGroup` (slider-specific, both standalone and nested variants), `SettingsSliderRow`,
`SettingsExpander`, `SectionDivider`, plus inline `Column(uiCardBackground)` Main cards and inline nested
`Column(0x0DFFFFFF)` boxes. The roles are conflated and the naming is inconsistent.

## Target model (user-confirmed)

A settings section is structured as:

```
Card (20% white, rounded)          ← the section surface
└─ Expander (collapsible row)      ← tap to expand/collapse
   └─ NestedCard (5% white + border) ← appears when expanded; holds the section's controls
      └─ controls (any type), optionally split into sections by SectionDivider
```

- **`Card`** — Main card surface (20% white `uiCardBackground`, 12dp radius). Holds a section's description,
  expanders, and/or standalone controls.
- **`Expander`** — collapsible disclosure row (no box of its own). Expanding reveals a `NestedCard`.
- **`NestedCard`** — the 5% white (`0x0DFFFFFF` + `0x40FFFFFF` border) container shown when an `Expander` is
  open. Holds ANY controls (toggles, sliders, RangeSliders, text), optionally split into sections by
  `SectionDivider`.
- **`SectionDivider`** — divider between sections inside a `NestedCard` (or between blocks on a `Card`).

## Nomenclature (Scheme B — generic, no prefix)

| New composable | Role | Replaces |
|---|---|---|
| `Card` | Main card surface | inline `Column(uiCardBackground)` |
| `Expander` | collapsible disclosure row | `SettingsExpander` |
| `NestedCard` | 5% container on expand | `SettingsSliderGroup(nested=true)` (generalized) |
| `SectionDivider` | section divider | `SectionDivider` (unchanged name) |

Deprecated/removed: `SettingsSliderGroup` (both variants), `SettingsSliderRow`. Sliders render as plain
controls (`SliderRowContent`) directly on a `Card` (standalone) or inside a `NestedCard` (expander content).

## Implementation steps

### Step 1 — Introduce the structural composables
In `MapScreen.kt` (or a shared settings-components file):
- Add `Card(content)` — wraps content in `Column(fillMaxWidth, clip(12dp), background(uiCardBackground),
  padding(vertical = 8.dp))`.
- Rename `SettingsExpander` → `Expander` (keep its behavior/state via `SettingsViewModel.expanderStates`).
- Add `NestedCard(content)` — wraps content in `Column(fillMaxWidth, clip(12dp), background(0x0DFFFFFF),
  border(1dp, 0x40FFFFFF, 12dp), padding(16dp, 12dp))`. Generalizes `SettingsSliderGroup(nested=true)`.
- Keep `SectionDivider` (already canonical).

### Step 2 — Migrate ALL expander content to `NestedCard`
Every `Expander` reveals a `NestedCard`. Migrate all 12 expanders, covering three nested-surface forms:
- 8 sites using `SettingsSliderGroup(nested=true)` (marker halo 3801, auto markers 3921, zone300 4071,
  low-depth 4162, emodnet 4213, redisplay 4453, GPS tuning 4647, stop thresholds 4727).
- 3 sites using inline `Column(0x0DFFFFFF)` (track_rendering 3463, track_direction 3656, reg_info 3990).
- 1 site with NO nested surface (`reg_categories` 4033) — **gains a `NestedCard`** (user ruling: normalize).

Replace each with the `NestedCard` composable; render controls (sliders, toggles, RangeSliders, text) as plain
sections separated by `SectionDivider`. Preserve mixed content and any `SubSectionHeader`/`SectionDivider` that
currently sit inside the nested box. Remove `SettingsSliderRow(nested=true)` wrappers (use bare `SliderRowContent`).

### Step 3 — Migrate Main cards to `Card`
Replace inline `Column(uiCardBackground)` section boxes with the `Card` composable. Also group the **Screen
section's 3 standalone controls** (keep-screen-on, debug-rays, FPS) into **one `Card` with 3 sections** separated
by `SectionDivider` (user ruling).

### Step 4 — Remove deprecated composables
Delete `SettingsSliderGroup` and `SettingsSliderRow` definitions and all remaining call sites. Standalone
sliders render as `SliderRowContent` directly on a `Card`. (The FPS slider is re-homed into the grouped Screen
`Card` in Step 3, so no standalone slider-card path remains.)

### Step 5 — Update guidelines
Update `docs/ui-component-guidelines.md` to the new nomenclature and model:
- §2.0 Card Surface Primitive → `Card`.
- §2.3 Grouped Card → `Card` + `Expander` + `NestedCard`.
- §2.4 Inception rule → `Card → Expander → NestedCard → controls`.
- §2.4a/§2.4b → `NestedCard` (single uniform container for all controls).
- Remove `SettingsSliderGroup`/`SettingsSliderRow` references.
- Update `FEAT_DSC_Ui_Settings.md` pointer if needed.

## Files Affected
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` (structural composables + ~10 expander sites + Main cards)
- `docs/ui-component-guidelines.md`
- possibly `xTrack/Ui_Settings/FEAT_DSC_Ui_Settings.md`

## Verification
- Build via `apk-build.bat`.
- Manual: settings renders identically in structure (Card → Expander → NestedCard); no visual regression.
- Grep: no remaining `SettingsSliderGroup`/`SettingsSliderRow`/`SettingsExpander` references.

## Notes
- This is a large refactor; do it in batches (per expander), building after each batch.
- The `Settings*` control-row composables (`SettingsToggleRow`, `SliderRowContent`, `SettingsFrequencyRow`,
  `SectionHeader`, `SubSectionHeader`) are NOT renamed in this pass (scope = the 4 structural composables).
