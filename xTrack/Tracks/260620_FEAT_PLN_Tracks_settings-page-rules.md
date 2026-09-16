# Settings Page Rendering Rules — AI Implementation Prompt

## Context
MapScreen.kt settings page (~lines 2200-2800) built from ad-hoc composables. Apply uniform rendering rules across ALL settings sections (not just Tracking).

## Rules

### R1 — Component Consistency
- **Toggle rows**: ALWAYS use `SettingsToggleRow(label, desc, checked, onCheckedChange)`. No custom Row+Switch builds.
- **Slider rows**: ALWAYS use `SettingsSliderRow(label, desc, valueLabel, value, range, steps, onChange)`. Already wraps in `SettingsSliderGroup` card.
- **Sub-cards inside expanders**: wrap in `SettingsSliderGroup {}` (provides RoundedCornerShape 12dp, cardBg, padding 16×12dp).
- **Expander labels**: single style: `uiSettingsTextPrimary`, 14sp, FontWeight.Medium. Remove all per-call-site `labelStyle` overrides.
- **Color swatch rows**: vertical padding = 8dp (not 4dp).

### R2 — Spacing
- Between top-level cards: `Spacer(Modifier.height(12.dp))` (already standard).
- Between sub-cards inside expanders: `Spacer(Modifier.height(8.dp))`.
- Replace 0.5dp hairline `Box` dividers with `Spacer(8.dp)`.
- Between expander header and its content: `Spacer(Modifier.height(8.dp))` (already standard).

### R3 — Value Display
- Slider values: right-aligned, accent color, 16sp Bold (already standard in `SliderRowContent`). Formalize as `AppConfig.uiSettingsValue` token.
- Toggle values: switch position IS the value — no extra label.
- Color values: swatch IS the value — no hex label needed.
- Range/opacity labels: explanatory `Text()` in `uiSettingsTextMuted 12sp` ABOVE the control.

### R4 — Font Tokens (TBD, define later)
- `uiSettingsLabel` — primary label font (16sp Medium)
- `uiSettingsDescription` — secondary description (13sp)
- `uiSettingsValue` — current value display (16sp Bold accent)
- `uiSettingsExplanation` — helper text below controls (12sp muted)
- Expander header — use `uiSettingsPrimary` 14sp Medium (not `uiDashboardTextMuted`)

## Implementation Order
1. Fix `ColorSwatchRow` vertical padding: 4dp → 8dp
2. Fix `ColorSwatchPairRow` vertical padding: 4dp → 8dp
3. Replace Tracking section custom toggle Row with `SettingsToggleRow`
4. Normalize all `SettingsExpander` label styles (remove overrides, set default to 14sp Medium `uiSettingsTextPrimary`)
5. Replace 0.5dp hairline dividers with `Spacer(8.dp)` throughout settings
6. Add `uiSettingsValue` AppConfig token (if needed)
7. Build: assembleDebug
