---
name: UiTweaks
status: active
created: 2026-06-09 21:05
modified: 2026-06-09 21:16
active_subfeature: none
---

# Feature: UiTweaks

**Description:**
Tracking non-functional UI adjustments — visual polish, colour tuning, layout
refinement, and other front-end tweaks that don't add new capabilities but
improve the look, feel, or usability of the app.

## Subfeatures
### dashboard-padding  [x]
Changed panel edge horizontal padding from 12dp → 9dp for tighter layout.

#### Todos
- [x] Change `DashboardPanel` outer padding `horizontal = 12.dp` → `9.dp`

### dashboard-unit-size  [ ]  (rolled back — didn't work visually)
Separate the unit from the value in DashboardCard so the number auto-sizes
larger. Attempted but rolled back — the layout didn't render correctly on device.

#### Todos
- [ ] (revisit later with different approach)
