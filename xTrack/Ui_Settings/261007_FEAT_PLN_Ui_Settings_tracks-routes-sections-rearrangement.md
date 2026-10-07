# Ui_Settings — the Tracks and Routes sections rearranged

**Status:** in design, all six decisions settled, nothing implemented
**Created:** 2026-10-07
**Owner:** Ui_Settings owns the Layers tab's surface; the values the rows write stay owned by Tracks and Route

---

## Request (the user's words, 2026-10-07)

1. Track's "Speed display" moves to a section at the top of "Tracks Appearance".
2. "Tracks Speed and Direction" becomes "Tracks and Speed Arrows Settings" and moves to its own section
   just above the export/import section.
3. "Routes Speed and Direction"'s content moves to a section at the top of "Routes Appearance" and uses
   the same control as Track's "Speed display".

## The shape as it stands (read from the file, not assumed)

One `SectionHeader` "Tracks and Routes" (`settings_section_tracks_and_routes`) over one `CardArea`, holding
five blocks in this order, all in `MapScreenSettingsOverlay.kt`:

| # | Block (its label key) | Holds |
|---|----------------------|-------|
| 1 | `Expander` "Tracks Appearance" (`settings_track_settings_label`) | `CardDescription` "How recorded tracks are drawn on the map", the not-pinned tracks count, the tracks transparency pair, the "Default Colors" heading with the past and pinned-track colour pairs |
| 2 | `Expander` "Tracks Speed and Direction" (`settings_tracks_direction_settings_label`) | `SubSectionHeader` "Speed Display" (`settings_tracks_speed_display_label`) + the two-chip `MultiSelectRow` (Arrows / Speed Colors) over `trackArrows` / `trackColours`; divider; "Arrow density" + `SegmentedRow`; divider; the arrow gap range; divider; the arrow speed range |
| 3 | `Expander` "Routes Appearance" (`settings_routes_appearance_label`) | `CardDescription` "How saved routes are drawn on the map", the not-pinned routes count, the route ladder, the pinned-route ladder, the route colour pair and the pinned-route pair |
| 4 | `Expander` "Routes Speed and Direction" (`settings_routes_speed_direction_label`) | `CardDescription` (the per-kind wording written this session) + two `ToggleRow`s, "Speed colours on routes" and "Arrows on routes" |
| 5 | — | `SectionDivider` + `CardDescription` "Export and import recorded tracks and routes" + the export/import button row |

## The target shape (the moves as asked)

| # | Block | Change |
|---|-------|--------|
| 1 | "Tracks Appearance" | gains the whole "Speed Display" sub-section at its top |
| 2 | "Routes Appearance" | gains a "Speed Display"-shaped sub-section at its top, the same two-chip control, over `routeSpeedArrows` / `routeSpeedColor` |
| 3 | the renamed block (old 2) | keeps the arrow density, the gap range and the speed range; its "Speed Display" sub-section has left for block 1; it stands just above the export/import block |
| 4 | export/import | unchanged |

## Assessment

- **The moved chips and the block they leave are not the same kind of setting.** The two-choices row is each
  kind's **own persisted on/off pair** (`trackArrows` / `trackColours`, and now `routeSpeedArrows` /
  `routeSpeedColor`); the density, the gap range and the speed range it leaves behind are **single shared
  values both kinds' chevrons read** through one spacing provider. So the renamed block is the arrows'
  *tuning*, kind-agnostic — naming it "Tracks ..." would claim a scope it does not have.
- **The route pair's swap kills two strings.** Replacing the two `ToggleRow`s with the chip row leaves
  `settings_routes_speed_color_label` ("Speed colours on routes") and `settings_routes_arrows_label`
  ("Arrows on routes") with no reader; they leave `values/` and `values-fr/` in the same edit.
- **"At the top" meets the description convention.** Every block opens with its `CardDescription` and the
  guidelines make that lead-in the card's own (§2.8). A sub-section placed above it breaks that order; placed
  below it, "top" means "first control". Both are shippable — the user picks which "top" they mean.
- **One fact, two homes.** The Route plan filed minutes ago proposes exactly this route-side control swap.
  It should be trimmed to its dot item so the swap lives here alone.
- **The block's own fate is a design question, not a move.** Today it is an `Expander` inside the one
  "Tracks and Routes" `CardArea`; "its own section" can mean either the same expander moved down, or a real
  `SectionHeader` + `CardArea` of its own with the export/import joining it.

## Decisions (settled 2026-10-07)

- **D1 — settled: "Speed Arrows Settings".** The block leaves "Tracks Speed and Direction" for a name that
  fits what it keeps — the arrows' spacing and tempering, which both kinds' chevrons read. The French pair
  takes the matching wording unless the user prefers the joined literal they first wrote.
- **D2 — settled: no new section.** The block stays the expander it already is and only moves above the
  export/import row inside the same "Tracks and Routes" card — no `SectionHeader`, no card of its own, so its
  content simply changes place.
- **D3 — settled: first control below the description.** Each moved "Speed Display" sub-section sits inside
  its block **below** that block's own one-line `CardDescription` and above every other control, which is the
  order the component guidelines already ask for.

## Decisions settled the same day — the rest

- **D4 — reuse "Speed Display" verbatim** for the routes sub-section so the two blocks read alike, keeping
  the route sentence as that sub-section's own description with its wording brought in line with the chips
  it now sits over.
- **D5 — the order is** Tracks Appearance, Routes Appearance, Speed Arrows Settings, then export/import.
- **D6 — Ui_Settings owns this surface work**, and the earlier Route plan has been trimmed to its dot item
  so the control swap keeps one home.

## Steps (when ordered)

1. Nothing left to confirm: all six decisions are settled.
2. Block 1: lift `SubSectionHeader` "Speed Display" + the `MultiSelectRow` out of the old block 2 into
   "Tracks Appearance" at D3's position.
3. Block 4: replace the two `ToggleRow`s with the same shape over the route pair, at D3's position inside
   "Routes Appearance", and delete the two dead labels from both locales after confirming no other reader.
4. Move the remainder of the old block 2 to just above the export/import block, renamed per D1, shaped per D2.
5. Tests: a case pinning that the tracks and routes chip rows expose their own kind's pair and nothing of the
   other's, and that the moved block still writes the shared arrow-tuning keys it wrote before.
6. `apk-build.bat` plus the scoped `ui.map` + `config` suite; the device look over the new section order
   stays the user's.

## Risks

- The rename and the moves touch only composition and string keys, but the Layers tab's scroll positions are
  session state; a section that moves a long way re-reads oddly on the next open.
- The old block 2's dividers and spacers assume a three-sub-section body; with the first sub-section gone the
  block opens on "Arrow density" and its leading divider must go with the move, not stay behind.

## Out of scope

- No change to any value, key or seed: the four axes and the arrow tuning keep exactly what they write today.
- No change to the "Tracks and Routes" section header, its description, the counts, the transparency pairs or
  the colour pairs.
- The export/import behaviour is untouched; only its neighbours move.
