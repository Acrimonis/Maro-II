# Settings Page — UI Implementation Guidelines

> **Purpose:** Canonical reference for rendering any settings section in `MapScreen.kt`.
> Apply these rules when adding a new setting, refactoring an existing one, or reviewing settings UI.

---

## 1. Component Hierarchy

### 1a. Top-Level Sections
Every logical group of settings gets a `SubSectionHeader(title, description?)` followed by `Spacer(8.dp)`.

```kotlin
SubSectionHeader(title = "Navigation", description = null)
Spacer(Modifier.height(8.dp))
```

### 1b. Toggle Settings
Use `SettingsToggleRow(label, description, checked, onCheckedChange)` for any boolean on/off setting. It provides its own card (RoundedCornerShape 12dp, cardBg, padding 16×6dp).

```kotlin
SettingsToggleRow(
    label = "Coastline",
    description = "Show coastline overlay on the map",
    checked = settings.coastlineVisible,
    onCheckedChange = { on -> onUpdateSettings { it.copy(coastlineVisible = on) } }
)
```

### 1c. Slider Settings
Use `SettingsSliderRow(label, description, valueLabel, value, range, steps, onValueChange)` for slider settings. It wraps `SliderRowContent` inside `SettingsSliderGroup` (card, 12dp radius, padding 16×6dp).

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
When a toggle controls a group of related sub-settings, use a **single outer card** containing the toggle row and collapsible content — matching the "alert settings" pattern.

```
Column(cardBg, 12dp radius) {
    Row(toggle, 12dp pad)      ← toggle row with label+description+switch
    if (checked) {
        Box(0.5dp divider)
        Box(padding h=16dp) {
            SettingsExpander("... settings") {
                Spacer(4dp)
                Column(cardBg, 12dp radius, 4dp pad) {   ← inner content card
                    // sub-settings content
                }
            }
        }
    }
}
```

**Do NOT** nest `SettingsToggleRow` inside a custom card. Either use `SettingsToggleRow` standalone OR build the toggle inline inside a grouped card — never both.

**Toggle row inside grouped card:**
```kotlin
Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
) {
    Column(Modifier.weight(1f)) {
        Text("Tracks", 16.sp, Medium, primary)
        Text("Show recorded tracks", 13.sp, muted)
    }
    Spacer(16.dp)
    Switch(checked, onCheckedChange, ...)
}
```

### 1e. Expander Labels
`SettingsExpander` default label style: `uiSettingsTextPrimary`, 14sp, Medium. **No per-call-site `labelStyle` overrides.** All expanders look the same.

```kotlin
SettingsExpander(
    label = "Opacity",
    expanded = expanded,
    onToggle = { expanded = !expanded }
) { ... }
```

### 1f. Inside-Expander Content Cards
Content inside expanders goes in a `Column(cardBg, RoundedCornerShape 12.dp, padding 16×4dp)`:

```kotlin
Column(
    modifier = Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .background(ComposeColor(AppConfig.uiSettingsCardBackground))
        .padding(horizontal = 16.dp, vertical = 4.dp)
) {
    // sub-section content
}
```

### 1g. Between Cards
- Between top-level cards: `Spacer(Modifier.height(12.dp))`
- Inside grouped card, between expander header and inner card: `Spacer(Modifier.height(4.dp))`
- At bottom of grouped card (after all sub-content): `Spacer(Modifier.height(8.dp))`

---

## 2. Spacing Tokens (Canonical)

| Context | Value |
|---|---|
| Top-level card spacing | `Spacer(12.dp)` |
| SubSectionHeader → first card | `Spacer(8.dp)` |
| Toggle row vertical pad (standalone `SettingsToggleRow`) | `6.dp` |
| Toggle row vertical pad (inline inside grouped card) | `12.dp` |
| Slider card vertical pad (`SettingsSliderGroup`) | `6.dp` |
| Inner content card vertical pad | `4.dp` |
| Expander header row vertical pad | `8.dp` |
| Expander header → content card | `Spacer(4.dp)` |
| Sub-section divider (`Box(1.dp)`) | `Spacer(6.dp)` above and below |
| Sub-card bottom spacer (inside grouped card) | `Spacer(8.dp)` |
| Comment → control (slider) | `0.dp` (flush) |
| ColorSwatchRow/PairRow internal vertical pad | `2.dp` |
| Between color rows (swatch→swatch) | `0.dp` |
| Horizontal padding on cards | `16.dp` |
| Card corner radius | `12.dp` |

---

## 3. Font Tokens

| Role | Font |
|---|---|
| Section title (`SubSectionHeader`) | `uiDashboardTextMuted`, 16sp, SemiBold |
| Toggle/expander label | `uiSettingsTextPrimary`, 14sp, Medium |
| Toggle description | `uiSettingsTextMuted`, 13sp |
| Sub-section title (inside card) | `uiSettingsTextPrimary`, 14sp, Medium |
| Comment/explanation text | `uiSettingsTextMuted`, 12sp |
| Value display (right-aligned) | `uiSettingsAccent`, 16sp, Bold |
| Range label (e.g. "Newest X% – Oldest Y%") | `uiSettingsAccent`, 14sp, Bold |

---

## 4. Value Display Convention

- **Slider values:** Description text left-aligned, current value right-aligned in accent Bold. No extra label — the right-aligned number IS the value display.
- **Range values:** Comment text first (muted 12sp), value label second (accent 14sp Bold, `textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()`), then control. Value sits **between** comment and control.
- **Toggle values:** Switch position IS the value. No additional label.
- **Color values:** Swatch IS the value. No hex label needed.

### Number of Tracks Pattern (inline Row + Slider)
When a slider sits inside a card that already has a section title, use inline `Row(desc + value) + Slider` — NOT `SliderRowContent` which has its own label slot:

```kotlin
Text("Number of tracks", 14.sp, Medium, primary)       // section title
Row(fillMaxWidth, SpaceBetween, CenterVertically) {
    Text("Recent tracks to render (0-20)", 13.sp, muted, Modifier.weight(1f))
    Text("%d".format(nb), 16.sp, Bold, accent)
}
Slider(value, 0f..20f, steps=20, ...)
```

---

## 5. Divider Rules

- **Between sub-sections inside a card:** `Box(1.dp, divider color)` with `Spacer(6.dp)` above and below.
- **Between toggle rows in grouped card:** `Box(0.5dp, divider color)` with NO extra spacers (matches alert-settings pattern).
- **Between expander items in grouped card:** `Box(0.5dp, divider color)` with NO extra spacers.
- **Do NOT** use `Box(0.5.dp)` dividers between top-level cards — use `Spacer(12.dp)`.

---

## 6. Anti-Patterns (Do NOT Do)

- ❌ Custom `Column(cardBg) { Row { Text + Switch } }` when `SettingsToggleRow` already exists
- ❌ `SettingsToggleRow` inside a custom card — pick one or the other
- ❌ Per-call `labelStyle = TextStyle(...)` on `SettingsExpander`
- ❌ `Box(0.5.dp)` dividers between top-level cards
- ❌ `SliderRowContent(label="", ...)` — use inline Row+Slider when no label needed
- ❌ Double-wrapping: card inside card inside card
- ❌ `Spacer(8.dp)` between color swatch rows (use 0dp, tighten internal padding instead)

---

## 7. Checklist — Adding a New Setting

- [ ] Is it a toggle? Use `SettingsToggleRow`. Done.
- [ ] Is it a slider? Use `SettingsSliderRow`. Done.
- [ ] Is it a toggle + collapsible sub-settings? Use grouped card pattern (§1d).
- [ ] Are sub-settings in one expander? Use single `SettingsExpander` + inner card (§1f).
- [ ] Are values displayed? Follow value convention (§4).
- [ ] Are spacings correct? Cross-check with tokens (§2).
- [ ] Are fonts correct? Cross-check with tokens (§3).
- [ ] No custom `labelStyle` on `SettingsExpander`? (§1e)
- [ ] No double-wrapped cards? (§6)
- [ ] Bottom spacer present in grouped card? (§1g)
