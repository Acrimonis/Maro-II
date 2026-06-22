# Settings Page — UI Implementation Guidelines

> **Purpose:** Canonical reference for rendering any settings section in `MapScreen.kt`.
> Apply these rules when adding a new setting, refactoring an existing one, or reviewing settings UI.
> **Normative source:** This document reflects the actual implementation patterns found in `MapScreen.kt`.
> When the two disagree, the code wins — update this doc.

---

## 1. Component Hierarchy

### 1a. Section Headers

Two header levels exist:

| Component | Style | Usage |
|---|---|---|
| `SectionHeader(title)` | 17sp Bold accent, UPPERCASE, 1sp letter-spacing | Top-level tab sections (e.g. "DISPLAY", "NAVIGATION") |
| `SubSectionHeader(title, description?)` | 16sp SemiBold muted | Sub-groupings within a tab (e.g. "Layers", "Navigation") |

Both are followed by `Spacer(8.dp)` before the first card.

```kotlin
SectionHeader(title = stringResource(R.string.settings_section_display))
Spacer(Modifier.height(8.dp))

SubSectionHeader(title = "Layers", description = null)
Spacer(Modifier.height(8.dp))
```

### 1b. Toggle Settings (Standalone)

Use `SettingsToggleRow(label, description, checked, onCheckedChange)` for any standalone boolean on/off setting. It provides its own card (RoundedCornerShape 12dp, cardBg, padding 16×6dp).

```kotlin
SettingsToggleRow(
    label = "Coastline",
    description = "Show coastline overlay on the map",
    checked = settings.coastlineVisible,
    onCheckedChange = { on -> onUpdateSettings { it.copy(coastlineVisible = on) } }
)
```

> **Note:** `SettingsToggleRow` is for **standalone** use only (with 12dp spacers between cards).
> Do NOT nest it inside a grouped card — see §1d.

### 1c. Slider Settings (Standalone)

Use `SettingsSliderRow(label, description, valueLabel, value, range, steps, onValueChange)` for standalone slider settings. It wraps `SliderRowContent` inside `SettingsSliderGroup` (card, 12dp radius, cardBg, padding 16×6dp).

```kotlin
SettingsSliderRow(
    label = "Map refresh",
    description = "Frames per second",
    valueLabel = "%d fps".format(settings.mapRefreshFps),
    value = settings.mapRefreshFps.toFloat(),
    valueRange = 1f..60f,
    steps = 58,
    onValueChange = { v -> onUpdateSettings { it.copy(mapRefreshFps = v.roundToInt()) } }
)
```

### 1d. Grouped Card Pattern (Toggle + Collapsible Sub-Settings)

When a toggle controls a group of related sub-settings, or when multiple related toggles belong together, use a **single outer card** with **inline toggle rows** (NOT `SettingsToggleRow`).

```
Column(cardBg, 12dp radius) {
    Row(fillMaxWidth, 16×12dp pad, SpaceBetween) {
        Column(weight=1f) {
            Text(label, 16.sp, Medium, primary)
            Text(description, 13.sp, muted)
        }
        Spacer(16.dp)
        Switch(checked, onCheckedChange, …)
    }

    Spacer(8.dp)                         ← between toggles

    Row(fillMaxWidth, 16×12dp pad, SpaceBetween) {
        Column(weight=1f) { … }
        Spacer(16.dp)
        Switch(…)
    }

    if (checked) {
        Spacer(8.dp)                     ← before expander
        Box(padding h=16dp) {
            SettingsExpander("… settings") {
                Spacer(8.dp)             ← expander header → content
                SettingsSliderGroup {     ← pre-wrapped; no extra card needed
                    SliderRowContent(…)
                    SliderRowDivider()
                    SliderRowContent(…)
                }
            }
        }
    }
}
```

> **🔴 CRITICAL: Do NOT nest `SettingsToggleRow` inside a grouped card.**
> `SettingsToggleRow` already IS a card (12dp radius, cardBg). Putting it inside another card
> creates nested rounded rectangles (double-wrapping). Build toggle rows **inline** with a plain
> `Row(Text + Switch)` inside the grouped card.

**Inline toggle row template:**

```kotlin
Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
) {
    Column(Modifier.weight(1f)) {
        Text("Tracks", fontSize = 16.sp, fontWeight = FontWeight.Medium,
             color = ComposeColor(AppConfig.uiSettingsTextPrimary))
        Text("Show recorded tracks", fontSize = 13.sp,
             color = ComposeColor(AppConfig.uiSettingsTextMuted))
    }
    Spacer(Modifier.width(16.dp))
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = ComposeColor(AppConfig.uiSettingsAccent),
            checkedTrackColor = ComposeColor(AppConfig.uiSettingsAccent).copy(alpha = 0.4f),
            uncheckedThumbColor = ComposeColor(AppConfig.uiSettingsTextMuted),
            uncheckedTrackColor = ComposeColor(AppConfig.uiSettingsSwitchTrackInactive)
        )
    )
}
```

**Reference implementations in `MapScreen.kt`:**
- Zone 300 alert grouped card (2 toggles + expander)
- Speed zone alert grouped card (4 toggles)
- Tracks grouped card (1 toggle + expander with inner content card)
- Low-depth warning grouped card (1 toggle + expander with sliders)
- GPS position source grouped card (1 toggle + expander with sliders)

### 1e. Expander Labels

`SettingsExpander` default label style: `uiSettingsTextPrimary`, 14sp, Medium.
**No per-call-site `labelStyle` overrides.** All expanders look the same.

```kotlin
SettingsExpander(
    label = "Detection thresholds",
    expanded = expanded,
    onToggle = { expanded = !expanded }
) { … }
```

### 1f. Inside-Expander Content

Two cases, depending on what the expander contains:

**Case 1 — Sliders:** Use `SettingsSliderGroup` directly (it already provides cardBg + 12dp radius + 16×6dp padding). Do NOT wrap in an extra card.

```kotlin
SettingsExpander("GPS tuning", …) {
    Spacer(Modifier.height(8.dp))
    SettingsSliderGroup {
        SliderRowContent(…)
    }
}
```

**Case 2 — Other content (text, color swatches, range sliders):** Wrap in a `Column(cardBg, RoundedCornerShape 12.dp)` with appropriate padding:

```kotlin
SettingsExpander("Track settings", …) {
    Spacer(Modifier.height(4.dp))    // ← 4dp for inner content cards
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ComposeColor(AppConfig.uiSettingsCardBackground))
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        // sub-section content
    }
}
```

> For text-heavy content, `vertical = 12.dp` padding is also valid (see regulated zones "Regulation info" expander).

### 1g. Between Cards

| Context | Spacing |
|---|---|
| Between top-level cards (standalone `SettingsToggleRow` or `SettingsSliderRow`) | `Spacer(12.dp)` |
| Between major tab sections (different `SectionHeader`s) | `Spacer(24.dp)` |
| `SectionHeader` / `SubSectionHeader` → first card | `Spacer(8.dp)` |
| Between inline toggle rows inside a grouped card | `Spacer(8.dp)` |
| Last toggle/expander in grouped card → end of card | No explicit spacer (last element's padding handles it) |
| Before expander inside grouped card (after toggle/divider) | `Spacer(8.dp)` |
| Expander header → `SettingsSliderGroup` content | `Spacer(8.dp)` |
| Expander header → inner `Column` content card | `Spacer(4.dp)` |

---

## 2. Spacing Tokens (Canonical)

| Context | Value |
|---|---|
| Top-level card spacing | `Spacer(12.dp)` |
| Major section spacing | `Spacer(24.dp)` |
| SectionHeader / SubSectionHeader → first card | `Spacer(8.dp)` |
| Toggle row vertical pad (standalone `SettingsToggleRow`) | `6.dp` |
| Toggle row vertical pad (inline inside grouped card) | `12.dp` |
| Slider card vertical pad (`SettingsSliderGroup`) | `6.dp` |
| Inner content card vertical pad (text/swatches) | `4.dp` |
| Inner content card vertical pad (text-heavy) | `12.dp` |
| Expander header row vertical pad | `8.dp` |
| Expander header → `SettingsSliderGroup` | `Spacer(8.dp)` |
| Expander header → inner content card | `Spacer(4.dp)` |
| Between inline toggles in grouped card | `Spacer(8.dp)` |
| Before expander in grouped card | `Spacer(8.dp)` |
| Sub-section divider (`Box(1.dp)`) | `Spacer(6.dp)` above and below |
| Comment → control (slider) | `0.dp` (flush) |
| ColorSwatchRow/PairRow internal vertical pad | `2.dp` |
| Between color rows (swatch→swatch) | `0.dp` |
| Horizontal padding on cards | `16.dp` |
| Card corner radius | `12.dp` |

---

## 3. Font Tokens

| Role | Font |
|---|---|
| Section title (`SectionHeader`) | `uiSettingsAccent`, 17sp, Bold, UPPERCASE, 1sp letter-spacing |
| Section title (`SubSectionHeader`) | `uiDashboardTextMuted`, 16sp, SemiBold |
| Toggle/expander label | `uiSettingsTextPrimary`, 14sp, Medium |
| Inline toggle label (inside grouped card) | `uiSettingsTextPrimary`, 16sp, Medium |
| Toggle description | `uiSettingsTextMuted`, 13sp |
| Sub-section title (inside content card) | `uiSettingsTextPrimary`, 14sp, Medium |
| Comment/explanation text | `uiSettingsTextMuted`, 12sp |
| Value display (right-aligned) | `uiSettingsAccent`, 16sp, Bold |
| Range label (e.g. "Newest X% – Oldest Y%") | `uiSettingsAccent`, 14sp, Bold |

> **Note:** `SettingsToggleRow` internally uses 16sp Medium for its label — this differs from the
> expander label token. If this is unified in the future, update here.

---

## 4. Value Display Convention

- **Slider values:** Description text left-aligned, current value right-aligned in accent Bold. No extra label — the right-aligned number IS the value display.
- **Range values:** Comment text first (muted 12sp), value label second (accent 14sp Bold, `textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()`), then control. Value sits **between** comment and control.
- **Toggle values:** Switch position IS the value. No additional label.
- **Color values:** Swatch IS the value. No hex label needed.

### Number of Tracks Pattern (inline Row + Slider)

When a slider sits inside a content card that already has a section title, use inline `Row(desc + value) + Slider` — NOT `SliderRowContent` which has its own label slot:

```kotlin
Text("Number of tracks", 14.sp, Medium, primary)          // section title
Row(fillMaxWidth, SpaceBetween, CenterVertically) {
    Text("Recent tracks to render (0-20)", 13.sp, muted, Modifier.weight(1f))
    Text("%d".format(nb), 16.sp, Bold, accent)
}
Slider(value, 0f..20f, steps=20, …)
```

---

## 5. Divider Rules

- **Between sub-sections inside a content card:** `Box(1.dp, divider color)` with `Spacer(6.dp)` above and below.
- **Between inline toggle rows in a grouped card:** `Spacer(8.dp)` — a gap, not a visible rule.
- **Do NOT** use visible `Box(0.5.dp)` dividers between top-level cards — use `Spacer(12.dp)`.

---

## 6. Anti-Patterns (Do NOT Do)

- ❌ `SettingsToggleRow` inside a grouped card — creates nested rounded rectangles (double-wrapping)
- ❌ Custom `Column(cardBg) { Row { Text + Switch } }` when a standalone `SettingsToggleRow` suffices
- ❌ Per-call `labelStyle = TextStyle(…)` on `SettingsExpander`
- ❌ `Box(0.5.dp)` dividers between top-level cards
- ❌ `SliderRowContent(label="", …)` — use inline Row+Slider when no label needed
- ❌ Double-wrapping: card inside card inside card
- ❌ `Spacer(8.dp)` between color swatch rows (use 0dp, tighten internal padding instead)

---

## 7. Checklist — Adding a New Setting

- [ ] Is it a standalone toggle? Use `SettingsToggleRow`. Done.
- [ ] Is it a standalone slider? Use `SettingsSliderRow`. Done.
- [ ] Is it a toggle + collapsible sub-settings? Use grouped card pattern (§1d).
- [ ] Are there multiple related toggles? Use grouped card with inline toggle rows (§1d).
- [ ] Are toggle rows inside a grouped card built inline (`Row(Text + Switch)`)? Not `SettingsToggleRow`?
- [ ] Are sub-settings sliders in one expander? Use `SettingsExpander` → `Spacer(8.dp)` → `SettingsSliderGroup` (§1f).
- [ ] Are sub-settings non-slider content? Use `SettingsExpander` → `Spacer(4.dp)` → inner content card (§1f).
- [ ] Are values displayed? Follow value convention (§4).
- [ ] Are spacings correct? Cross-check with tokens (§2).
- [ ] Are fonts correct? Cross-check with tokens (§3).
- [ ] No custom `labelStyle` on `SettingsExpander`? (§1e)
- [ ] No double-wrapped cards? (§6)
- [ ] No `SettingsToggleRow` nested in a grouped card? (§1d, §6)
