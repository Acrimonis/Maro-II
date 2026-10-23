<!-- scope: feature -->
# Markers — Archived Plans

Retired plans for the Markers feature. This folder is read only by `#archive`; every other
feature-summarising command excludes it. Row form and the digest floor live in
[`docs/cmd_help_archive.md`](../../../docs/cmd_help_archive.md:1).

| File | Created | Archived | Status | Summary | Tags | Superseded-by |
|------|---------|----------|--------|---------|------|---------------|
| `260928_FEAT_PLN_Markers_edit-return-and-selection-fallback.md` | 2026-09-28 | 2026-09-28 | superseded | Tombstone only — its body is the plan it moved to; filed rather than deleted on the user's word naming it in the archived set, against the archive page's abandon-delete rule. | `markers`, `ui` | `xTrack/Ui_General/260928_FEAT_PLN_Ui_General_edit-return-and-advance.md` |
| `261012_FEAT_PLN_Markers_routing-filter-group.md` | 2026-10-12 | 2026-10-12 | shipped | The marker filter's three routing axes under one Routing title; the shared-title rule and the retired axis keys. | `markers`, `filter`, `routing` | — |
| `261011_FEAT_PLN_Markers_marker-import-export.md` | 2026-10-11 | 2026-10-12 | shipped | GPX marker backup: a waypoint with a protobuf blob and a JSON mirror, the ZIP batch, the doors and the identity rule. | `markers`, `transfer`, `gpx` | — |
| `261011_FEAT_PLN_Markers_routing-only-flag.md` | 2026-10-11 | 2026-10-12 | shipped | The routing-weight-only flag: the model field, the one exclusion predicate, the wizard row, the dashed-and-hatched map cue and the six paint keys. | `markers`, `routing` | — |
| `261011_FEAT_PLN_Markers_focus-include-proximity.md` | 2026-10-11 | 2026-10-12 | shipped | The dashboard-open fit frames the body plus the proximity collar; the pin's zoom-in-only lock dropped. | `markers`, `focus` | — |
| `260929_FEAT_PLN_Markers_add-marker-trigger.md` | 2026-09-29 | 2026-10-12 | shipped | The shared create action at the head of both markers headers, and the vertical rule dividing it from the read chrome. | `markers`, `ui` | — |
| `260928_FEAT_PLN_Markers_route-role-flags.md` | 2026-09-28 | 2026-10-12 | shipped | A marker's two route roles, the Routing cost step's two-column layout and the cost wall. | `markers`, `routing`, `wizard` | — |
| `260928_FEAT_PLN_Markers_filter-validation-and-route-axis.md` | 2026-09-28 | 2026-10-12 | shipped | The marker filter's two independent route axes, the pinned report and the dead-code removal. | `markers`, `filter`, `routing` | — |
| `260925_FEAT_PLN_Markers_marker-tile-header-layout-discussion.md` | 2026-09-25 | 2026-10-12 | shipped | The marker tile header: the icon action leading, the glyph inline and the 2 dp icon-to-coordinate gap. | `markers`, `ui`, `list` | — |
| `260918_FEAT_PLN_Markers_focus-zoom-fractions.md` | 2026-09-18 | 2026-10-12 | shipped | Focus framing to a share of the smaller displayed dimension; the `marker.focus.*` keys and the pure helper. | `markers`, `focus` | — |
| `260905_FEAT_PLN_Markers_pin-halo-rendering.md` | 2026-09-05 | 2026-10-12 | shipped | The static halo ring as the pinned/unpinned cue, the corridor under-line and the `selectedMarkerId` gold. | `markers`, `rendering` | — |
| `260904_FEAT_PLN_Markers_icon-pin-decoupling.md` | 2026-09-04 | 2026-10-12 | shipped | Icon as a pure POI emoji and pin as a persisted boolean; the binary marker layer. | `markers`, `model`, `ui` | — |
| `260702_FEAT_PLN_Markers_auto-marker-proximity-fix.md` | 2026-07-02 | 2026-10-12 | shipped | Auto 🕐 markers take their proximity from `track.boatMarker.autoMarker.proximityM` (300 m). | `markers`, `matching` | — |
| `260622_FEAT_PLN_Markers_user-markers-design.md` | 2026-06-22 | 2026-10-12 | shipped | The original markers design: geometry, land-blocking, on-demand matching, creation UI and management. | `markers`, `design` | — |
| `260623_FEAT_PLN_Markers_create-zones-flow.md` | 2026-06-23 | 2026-10-12 | shipped | The wizard creation flow replacing the single form. | `markers`, `wizard` | — |
| `260623_FEAT_PLN_Markers_marker-proximity-wizard-normalization.md` | 2026-06-23 | 2026-10-12 | shipped | The proximity preview fix and the wizard flow normalization. | `markers`, `wizard`, `rendering` | — |
| `260624_FEAT_PLN_Markers_area-tap-and-wizard-buttons.md` | 2026-06-24 | 2026-10-12 | shipped | Area-based marker tap, text-pill wizard buttons, corridor caps and marker colour settings. | `markers`, `ui`, `rendering` | — |
| `260624_FEAT_PLN_Markers_markers-section-normalization.md` | 2026-06-24 | 2026-10-12 | shipped | The drawer's MARKERS section rebuilt as a card with its toggles and chevron. | `markers`, `ui`, `drawer` | — |
| `260625_FEAT_PLN_Markers_wizard-drawerslot-separation.md` | 2026-06-25 | 2026-10-12 | shipped | The wizard step extraction and the `DrawerSlot` abstraction. | `markers`, `wizard`, `ui` | — |
| `260625_FEAT_PLN_Markers_next-session-ui-polish.md` | 2026-06-25 | 2026-10-12 | shipped | The list text format, the colour picker, the edit icon and the viewing drawer redesign. | `markers`, `ui` | — |
| `260628_FEAT_PLN_Markers_icon-fixes.md` | 2026-06-28 | 2026-10-12 | shipped | The management icon picker fix, the timestamp display, the format unification and the duplicate imports. | `markers`, `icon` | — |
| `260630_FEAT_PLN_Markers_whereami-gaps.md` | 2026-06-30 | 2026-10-12 | shipped | The angular shadow over-blocking analysis and its fix direction. | `markers`, `matching`, `analysis` | — |
| `260701_FEAT_PLN_Markers_remove-proximity-range-gates.md` | 2026-07-01 | 2026-10-12 | shipped | Line-of-sight only: the distance gates removed and `ProximityMatch` renamed `LineOfSightMatch`. | `markers`, `matching` | — |
| `260701_FEAT_PLN_Markers_whereami-flow.md` | 2026-07-01 | 2026-10-12 | shipped | The `resolveMatch` flow and the `proximityOverrideM` lifecycle. | `markers`, `matching` | — |
| `260711_FEAT_PLN_Markers_marker-hilite-dual-outline-plan.md` | 2026-07-11 | 2026-10-12 | shipped | The marker highlight's dark under-stroke and gold dual outline. | `markers`, `rendering` | — |
| `260711_FEAT_PLN_Markers_track-hilite-dual-outline-plan.md` | 2026-07-11 | 2026-10-12 | shipped | The track highlight's dark under-stroke and gold core dual outline. | `markers`, `rendering`, `tracks` | — |
| `261010_FEAT_PLN_Markers_scaled-slider-control.md` | 2026-10-10 | 2026-10-12 | shipped | The ×10 range toggle and the shared `ScaledSliderRow` scaffold. | `markers`, `wizard`, `ui` | — |
| `260718_FEAT_PLN_Markers_multi-select-and-merge.md` | 2026-07-18 | 2026-10-23 | shipped | Multi-select delete/pin and auto-marker merge with keep-originals. | `markers`, `list`, `merge` | — |
| `260714_FEAT_PLN_Markers_auto-marker-dedup.md` | 2026-07-14 | 2026-10-23 | shipped | Proximity dedup in addTempAutoMarker behind track.boatMarker.autoMarker.dedupRadiusM (50 m). | `markers`, `matching` | — |
| `260703_FEAT_PLN_Markers_setting-markers.md` | 2026-07-03 | 2026-10-23 | shipped | The grouped Markers settings card, the Appearance expander, SHOW_PINNED removed and migration v5. | `markers`, `settings` | — |
| `260630_FEAT_PLN_Markers_whereami-zone-los.md` | 2026-06-30 | 2026-10-23 | shipped | Boundary sampling plus segmentIntersectsLand replacing the angular-shadow code, direct-line fast path kept. | `markers`, `matching` | — |
| `260630_FEAT_PLN_Markers_whereami-fixes.md` | 2026-06-30 | 2026-10-23 | superseded | The seven-fix angular-shadow plan, overtaken by the whereami-zone-los rewrite. | `markers`, `matching` | `260630_FEAT_PLN_Markers_whereami-zone-los.md` |
| `260630_FEAT_PLN_Markers_sort-scoring-v2.md` | 2026-06-30 | 2026-10-23 | shipped | sortScore (categoryBase plus typeWeight times percentage) with the zoneSize tie-breaker. | `markers`, `matching` | — |
| `260630_FEAT_PLN_Markers_sort-scoring.md` | 2026-06-30 | 2026-10-23 | superseded | The absolute-distance composite score, superseded by the percentage-based v2. | `markers`, `matching` | `260630_FEAT_PLN_Markers_sort-scoring-v2.md` |
| `260626_FEAT_PLN_Markers_marker-pin-tri-state.md` | 2026-06-26 | 2026-10-23 | superseded | The HIDDEN to SHOW_ALL to SHOW_PINNED cycle, reverted to binary by setting-markers. | `markers`, `ui` | `260703_FEAT_PLN_Markers_setting-markers.md` |
| `260626_FEAT_PLN_Markers_marker-pin.md` | 2026-06-26 | 2026-10-23 | shipped | UserMarker.pinned as a persisted boolean and the pin toggle, folded into setMarkerPinned. | `markers`, `model` | — |
| `260626_FEAT_PLN_Markers_icon.md` | 2026-06-26 | 2026-10-23 | shipped | The icon POI field, the embedded picker and IconPickerDialog; icon-implies-pinned later decoupled. | `markers`, `icon` | — |
| `260625_FEAT_PLN_Markers_debug-wia.md` | 2026-06-25 | 2026-10-23 | shipped | The per-call whereAmI debugger choice with ray segments, folding the WiaDebug logcat design. | `markers`, `matching` | — |
| `260625_FEAT_PLN_Markers_whereami-rework.md` | 2026-06-25 | 2026-10-23 | shipped | WhereAmIMatch/WhereAmIResult with depth-first leaves-first traversal and the 1 km fence. | `markers`, `matching` | — |
