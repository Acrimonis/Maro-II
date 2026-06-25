# Drawer UI Guidelines

> **Purpose:** Canonical reference for rendering any drawer/panel surface in Maro II.
> **Created:** 2026-06-24 — normalisation pass (I1–I6).
> **Scope:** Menu slide panel (MenuDrawerOverlay), TrackHistoryOverlay, WizardDrawer, MarkerDrawer.

---

## 1. Surfaces

| Surface | File | Orientation | Entry |
|---------|------|-------------|-------|
| Menu slide panel | `MenuDrawerOverlay.kt` | Slides from right | Hamburger button |
| Track history | `TrackHistoryOverlay.kt` | Full-screen overlay | "Manage Tracks" row |
| Wizard | `WizardDrawer.kt` | Replaces dashboard | "New marker" / Edit |
| Marker viewer | `MarkerDrawer.kt` | Slides from bottom (portrait) / left (landscape) | Boat tap / marker tap |

---

## 2. Header Tokens (I1–I3)

All drawer headers share these tokens:

| Token | Value |
|-------|-------|
| Back button widget | `IconButton` (never `Button`) |
| Back button size | 32dp, `CircleShape` |
| Back button background | `uiSettingsSwitchTrackInactive` |
| Back icon size | 18dp |
| Back icon tint | `uiSettingsTextPrimary` |
| Title font | 17sp, Bold, `uiSettingsTextPrimary` |
| Back→title spacer | `16dp` |
| Header horizontal padding | 24dp (menu slide panel, track history); 12dp (wizard, marker viewer) |
| Header vertical padding | 6dp (wizard, marker viewer); 8dp (menu slide panel); 3dp (track history) |

```kotlin
// Canonical header pattern
Row(
    modifier = Modifier.fillMaxWidth()
        .padding(horizontal = 24.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    IconButton(
        onClick = onClose,
        modifier = Modifier.size(32.dp)
            .clip(CircleShape)
            .background(ComposeColor(AppConfig.uiSettingsSwitchTrackInactive))
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Close",
            tint = ComposeColor(AppConfig.uiSettingsTextPrimary),
            modifier = Modifier.size(18.dp)
        )
    }
    Spacer(Modifier.width(16.dp))
    Text(
        text = title,
        color = ComposeColor(AppConfig.uiSettingsTextPrimary),
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold
    )
}
```

---

## 3. Decision Log

| # | Date | Decision | Rationale |
|---|------|----------|-----------|
| I1 | 2026-06-24 | `Button` → `IconButton` for back arrow | `IconButton` is purpose-built for icon-only controls: circular ripple, no elevation. `Button` needed `CircleShape` + `contentPadding=0` workarounds. |
| I2 | 2026-06-24 | Back button size → 32dp | Settings page and all other app back buttons use 32dp. Menu slide panel and track history were outliers at 48dp. |
| I3 | 2026-06-24 | Title font → 17sp Bold, middle ground | Wizard title ("Create Marker — Step 3 of 7") is longer than other drawer titles; 24sp would overflow on narrow portrait screens. 17sp matches the existing `SectionHeader` token. |
| I6 | 2026-06-24 | Two card padding densities | Menu slide panel uses wide 16×10dp (simple toggle/nav rows benefit from breathing room). Data-dense cards (track history, wizard, marker viewer) use tight 8×4dp to keep content compact. |

---

## 4. Section Headers in Drawers

Drawer content sections use the same `SectionHeader` token as settings:

| Token | Value |
|-------|-------|
| Color | `uiSettingsAccent` |
| Font | 17sp, Bold, UPPERCASE, 1sp letter-spacing |
| Spacer before first card | `8.dp` |

```kotlin
Text(
    text = "MARKER DETAILS",
    color = ComposeColor(AppConfig.uiSettingsAccent),
    fontSize = 17.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = 1.sp
)
Spacer(Modifier.height(8.dp))
```

Subsection headers below the drawer title follow `Spacer(12.dp)` after the header row.

---

## 5. Card Pattern

Two padding densities depending on card content type:

| Density | Padding | Use for |
|---------|---------|---------|
| **Wide** | 16×10dp | Menu slide panel — simple toggle/nav rows with single controls |
| **Tight** | 8×4dp | Data-dense cards — track history stats grid, wizard sliders, marker details |

Shared tokens across both densities:

| Token | Value |
|-------|-------|
| Background | `uiCardBackground` |
| Corner radius | 12dp |
| Between cards | `Spacer(8.dp)` |

```kotlin
// Wide (menu slide panel)
Column(
    modifier = Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .background(Color(AppConfig.uiCardBackground))
        .padding(horizontal = 16.dp, vertical = 10.dp)
) { /* simple rows */ }

// Tight (data-dense cards)
Column(
    modifier = Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .background(Color(AppConfig.uiCardBackground))
        .padding(horizontal = 8.dp, vertical = 4.dp)
) { /* dense content */ }
```

**Row minimum height:** Rows with text + control use `Modifier.heightIn(min = 48.dp)`.

**Panel background:** `uiSettingsBackground` — use plain `Box`/`Column` with `.background()`, not `ModalDrawerSheet`.

---

## 6. Row Types

| Row type | Pattern | Example |
|----------|---------|---------|
| Setting | Label + inline control (Switch) | GPS mode toggle |
| Navigation | Label + trailing chevron (→) | "Manage Tracks" |
| Content | Text / sliders / stats inside card | Marker details, live stats |
