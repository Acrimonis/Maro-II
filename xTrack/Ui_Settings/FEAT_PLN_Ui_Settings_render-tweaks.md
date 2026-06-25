# Ui_Settings — Render Tweaks Discussion

**Date:** 2026-06-25
**Context:** `#sub render tweaks` — card rendering adjustments per `docs/ui-component-guidelines.md`

---

## Current State

### Two-Level Card Surface Stack

Settings overlay background: `ui.settings.background=#1A1A2E` (dark navy, opaque)

| Level | Token | Hex | Alpha | Effective on Navy |
|---|---|---|---|---|
| Card #1 (top-level) | `ui.settings.card.background` | `#1AFFFFFF` | ~10.2% | `#33334D` |
| Card #2 (nested) | `ui.nested.card.bg` | `#0DFFFFFF` | ~5.1% | `#404054` (stacked on #1) |
| Nested border | `ui.nested.card.border` | `#40FFFFFF` | 25% | frames sub-region |

### Current Padding Tokens

| Context | Token | Value |
|---|---|---|
| Horizontal (all cards) | `ui.padding.card.horizontal` | 16dp |
| Standalone toggle row | `ui.padding.toggle.vertical` | 2dp |
| Grouped inline toggle | `ui.padding.grouped.toggle.vertical` | 2dp |
| Slider card | `ui.padding.slider.vertical` | 2dp |
| Nested content (compact) | `ui.padding.content.compact` | 4dp |
| Nested content (comfortable) | `ui.padding.content.comfortable` | 12dp |
| Expander header | `ui.padding.expander.vertical` | 8dp |
| Drawer card rows | `ui.padding.drawer.vertical` | 10dp |

**No `ui.padding.card.vertical` token exists** — cards rely on row-level 2dp padding for top/bottom spacing.

---

## Issue 1 — Card-Level Internal Padding Too Cramped

**Toggle/slider row padding stays at 2dp.** The cramped feel comes from the **card container itself** — there is no explicit vertical padding between the card's clipped edge and the first/last content row.

Currently, a grouped card's first row has only its own 2dp vertical padding from the card top edge:

```
┌─ Card (#1AFFFFFF, 12dp radius) ─────────────┐
│ ← 2dp → Toggle row              ← 2dp →    │  ← only 2dp breathing room
│              Spacer (8dp)                    │
│ ← 2dp → Toggle row              ← 2dp →    │
│ ← 2dp → Last row                ← 2dp →    │  ← only 2dp at bottom
└──────────────────────────────────────────────┘
```

The card surface "hugs" the content too tightly. Only `ui.padding.card.horizontal=16dp` exists for card-level spacing.

**Candidate fix:** Add a new token `ui.padding.card.vertical` applied as vertical padding on the card `Column` itself, outside the row-level padding. This gives breathing room at top and bottom of each card without touching row density.

Proposed value: **8dp** — matches `ui.spacing.header.bottom` (used for header-to-card gap), creating consistent vertical rhythm across the overlay.

```
┌─ Card (#14FFFFFF, 12dp radius) ─────────────┐
│ ← 8dp card vertical pad                      │  ← new breathing room
│ ← 2dp → Toggle row              ← 2dp →    │
│              Spacer (8dp)                    │
│ ← 2dp → Toggle row              ← 2dp →    │
│ ← 2dp → Last row                ← 2dp →    │
│ ← 8dp card vertical pad                      │  ← new breathing room
└──────────────────────────────────────────────┘
```

---

## Issue 2 — Card #1 Background Too Light

`ui.settings.card.background=#1AFFFFFF` (10.2% white) on `#1A1A2E` navy produces `#33334D` — a noticeably lighter block. The user wants it **darker** (closer to the navy base) but still distinguishable as a card surface.

**Candidate values (darker):**

| Hex Alpha | % White | Effective on #1A1A2E | Readability |
|---|---|---|---|
| `#14` | ~7.8% | `#2E2E49` | Visible, subtle |
| `#0F` | ~5.9% | `#292944` | Barely there |
| `#0D` | ~5.1% | `#272742` | Very subtle (same as current nested bg) |
| `#0A` | ~3.9% | `#242440` | Near-invisible |

**Recommendation:** `#14FFFFFF` (~8% white) — dark enough to feel integrated, light enough to distinguish card boundaries. This would make the effective card surface `#2E2E49` instead of `#33334D`.

---

## Issue 3 — 2nd Level Card Background (Nested)

### Current Design

```
┌─ Settings Overlay (#1A1A2E navy) ─────────────────────┐
│  ┌─ Card #1 (#1AFFFFFF = ~10% white) ──────────────┐  │
│  │  Toggle row                                       │  │
│  │  ┌─ Card #2 nested (#0DFFFFFF = 5% white) ────┐  │  │
│  │  │  border: #40FFFFFF (25% white)              │  │  │
│  │  │  Slider / text content                      │  │  │
│  │  └──────────────────────────────────────────────┘  │  │
│  └───────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

### How It Reads

The nested card (`#0DFFFFFF`, 5% white) sits on top of Card #1's effective surface. The stacking creates a subtle depth cue:

1. **Base:** Opaque navy `#1A1A2E`
2. **Card #1:** +10% white → `#33334D` — the "elevated" surface
3. **Card #2:** +5% white on #1 → `#404054` — the "inset" sub-region

The 25%-white border (`#40FFFFFF`) is the primary visual cue that Card #2 is a distinct region. The 5% fill is intentionally subtle — just enough to create depth without competing with the blue accent (`#1565C0`) used on toggles/sliders inside.

### Why Not `uiCardBackground` for Nested?

Per §2.4 of the guidelines: stacking two 10-15% white layers compounds to ~28% effective white, which washes out the `#1565C0` accent contrast. The 5% nested fill keeps the accent crisp.

### If Card #1 Darkens

If Card #1 moves from `#1A` (10%) → `#14` (8%), the nested card's 5% fill on top of the darker parent will be slightly more pronounced (greater relative contrast). The border at 25% white will do even more of the framing work. This is likely fine — the border is the primary nesting indicator; the fill is secondary depth.

---

## Tokens to Change (Summary)

| Token | Current | Proposed | Reason |
|---|---|---|---|
| `ui.padding.card.vertical` | _(missing)_ | **8dp** | Card-level top/bottom breathing room |
| `ui.settings.card.background` | `#1AFFFFFF` (10%) | `#14FFFFFF` (8%) | Too light / stands out |
| `ui.nested.card.bg` | `#0DFFFFFF` (5%) | _unchanged_ | 5% correct for 2nd level |
| `ui.nested.card.border` | `#40FFFFFF` (25%) | _unchanged_ | Frames nested region well |

Row-level padding (`ui.padding.toggle.vertical`, `ui.padding.grouped.toggle.vertical`, `ui.padding.slider.vertical`) all stay at **2dp**.
