---
name: Tracks
status: active
created: 2026-06-15 21:43
modified: 2026-09-17 10:03
---

# Feature: Tracks

**Description:**
Trace the boat's movement (position, speed) during active navigation. One trace = one 'track' (Port Salis → Port Salis). Point capture suspends when stationary via `isStill()` gate, but the recording state stays ON. Tracks persisted as protobuf binary and recallable — their polylines display on the map overlay.

## Sections

### verification

#### Todos
- [ ] Build + deploy to device
- [ ] E2E: Enable tacking -> leave Port Salis -> verify auto-start + real-time map trace
- [ ] E2E: Stop sailing -> verify pause -> sail again -> verify resume
- [ ] E2E: Return to Port Salis + stop -> verify auto-finalize + appears in tack history
- [ ] E2E: Open tack history -> tap tack -> verify trace renders on map
- [ ] E2E: Swipe-to-delete tack -> confirm dialog -> verify removed from list + file system
- [ ] E2E: Manual Start/Stop from tack drawer -> verify state matches auto-detection
- [ ] E2E: Export GPX -> copy to computer -> open in QGIS/Google Earth -> verify track/speed/course
- [ ] E2E: Verify settings persistence of tack fields across app restart
- [ ] E2E live-track-paint-regression: fresh recording paints the active line point-by-point (demo + GPS); resume still paints; GAP seam stays dashed; no duplicate history copy when a Map filter is applied
- [ ] E2E resume-confirm-backup: sheet appears from list + both dashboard cards; checkbox checked by default; backup written only when ticked (new card, hidden on map, unpinned, no marker links); recording continues on the original; Cancel changes nothing
- [ ] E2E ramp-step-recut: the 4–13 kn range holds its shade on a jittering fix; the 13–40 kn gradation reads smooth on a fast run; and family 9 paints above 32 kn, where the ramp used to stop
- [ ] E2E track-position-filter: a mixed track under each of the three values; the tie reads as water; an offshore track beyond the baked region reads water; the live recording shows under every value

### track-list

#### Todos
- [ ] Review and refine track card layout per design spec
- [ ] Verify swipe-to-delete, inline snackbar, undo animations
- [ ] Verify inline editing (auto-focus, field-switch commit, back-to-revert)
- [ ] Verify human-readable formatting (comma decimal, durations)
- [ ] Verify compact padding and flush-left stats grid
- [ ] E2E: Create test tracks, verify all card fields display correctly

#### Rules
- Track list UI must follow 260618_FEAT_PLN_Tracks_TrackList_Design.md spec
- Styling must match Settings overlay patterns (AppConfig tokens)

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt`

#### Docs
- `xTrack/Tracks/260618_FEAT_PLN_Tracks_TrackList_Design.md` — track list UI design and 26 requirements

### auto-marker-cleanup

Recorder-owned `AutoMarkerManager` for deterministic 🕐 IDLE_AUTO lifecycle; merged-marker keepability, ghost-pin fix, finalize fallback; startup cleanup scoped to crash orphans only.

#### Todos
- [ ] Deploy + E2E verify cleanup scenarios

#### Docs
- `xTrack/Tracks/260831_FEAT_PLN_Tracks_auto-marker-cleanup.md` — cleanup hardening plan

### marker-track-nav

Deferred — cross-navigation between track detail and marker detail via `>` links, with a minimal `OverlayBackStack`.

#### Docs
- `xTrack/Tracks/260831_FEAT_PLN_Tracks_marker-track-nav.md` — plan

### marker-export-import

Track export hardening (unique names, Windows-safe sanitization) + import modes (single GPX Skip/Update/New, ZIP silent skip) shipped; marker export/import pending.

#### Docs
- `xTrack/Tracks/260831_FEAT_PLN_Tracks_marker-export-import.md` — plan

## Rules
- Feature-scoped plans go in `xTrack/[Feature]/YYMMDD_FEAT_PLN_[Feature]_[topic].md`.
- Track points only recorded while speed > 2.5 kn; OFF→ON via geofence exit (10s debounce) or manual Start.
- Internal storage: Protobuf binary (kotlinx-serialization-protobuf), not JSON. Export: GPX 1.1.
- Recording lifecycle: ON state persists through stationary; only point capture suspends via `isStill()`.
- 30s periodic checkpoint save; stats accumulated in-memory, written at finalize.
- Swipe-to-delete on TrackHistoryOverlay with snackbar undo.

## Docs
- `xTrack/Tracks/260618_FEAT_PLN_Tracks_TrackList_Design.md` — track list UI requirements
- `xTrack/Tracks/FEAT_DOC_Tracks_decisions.md` — comprehensive decisions record (7 categories, 40+ decisions)
- `xTrack/Tracks/260620_FEAT_PLN_Tracks_gps-line-acquisition.md` — GPS point acquisition
- `xTrack/Tracks/260620_FEAT_PLN_Tracks_gps-background.md` — persistent foreground service
- `xTrack/Tracks/260622_FEAT_PLN_Tracks_spike-rejection-v2.md` — spike rejection v2
- `xTrack/Tracks/260618_FEAT_PLN_Tracks_adaptive-isstill.md` — adaptive stillness detection
- `xTrack/Tracks/260622_FEAT_PLN_Tracks_pinned-tracks.md` — pinned tracks
- `xTrack/Tracks/260717_FEAT_PLN_Tracks_tracks-paint-order.md` — tracks paint order
- `xTrack/Tracks/260911_FEAT_PLN_Tracks_live-track-paint-regression.md` — live-track paint regression: diagnosis + fix (evidence, fix options, follow-ups)
- `xTrack/Tracks/260911_FEAT_PLN_Tracks_resume-confirm-backup.md` — resume confirmation sheet + optional backup copy (decisions D1–D7, verified constraints, follow-ups)
- `xTrack/Tracks/260914_FEAT_PLN_Tracks_selected-track-speed-heatmap.md` — selected-track speed heatmap: banded ramp, declared tick scale, persisted eye toggle, the count key removed and the scale foot (sections 16–19)
- `xTrack/Tracks/260914_FEAT_PLN_Tracks_render-modes.md` — render modes: the three-way switch, the banded scope, the fade, the store, the two surfaces and the eye's scoped override (sections 1–6)
- `xTrack/Tracks/260915_FEAT_PLN_Tracks_ramp-alpha-ceiling-removal.md` — ramp alpha: the coreAlpha ceiling removed, a band's alpha being the track's own fade alone
- `xTrack/Tracks/260915_FEAT_PLN_Tracks_carry-window-removal.md` — speed resolution: the carry window removed, leaving stored-then-derived and a neutral seam
- `xTrack/Tracks/260914_FEAT_PLN_Tracks_pinned-cue-casing.md` — the selection cue: the dark casing removed, and the rim parked with the alpha-stacking finding and its three candidate shapes
- `xTrack/Tracks/260916_FEAT_PLN_Tracks_legend-collapse-toggle.md` — speed-scale collapse toggle: two faces on one anchor, the persisted `trackLegendExpanded` flag, and the untouched gate (D1–D6)
- `xTrack/ColorManagement/260916_FEAT_PLN_ColorManagement_map-surface-normalization.md` — owned by ColorManagement: one painting path and one property block for every map surface, the legend card and the collapsed square included (D1–D6)

- `xTrack/Tracks/260917_FEAT_PLN_Tracks_render-axes-split.md` — the render axes: the menu's three-way switch split into two independent chips, the lossless migration of the retired triple, the twin box and its four valid combinations

## Walk
**Level 1 — Date:** 2026-09-14 · **Source:** the 260914 selected-track speed heatmap plan's open points and implementation steps — the feature's older device-E2E todos are excluded, being a verification backlog rather than plan items · **Active:** 14 · **Closed:** 2026-09-14
- [x] 1 · Arrow colour inside the banded line (D4a) — child walk closed: arrows take the local band colour
- [x] 2 · Scope and legend (D5) — child walk closed: scope is the selected track only, the live line untouched; legend carried to item 8
- [x] 3 · Confirm the owning feature is Tracks — child walk closed: a section of Tracks, not a new feature
- [x] 4 · Settings surface — child walk closed: no UI in this pass, the mode and ramp load from `maro.properties`
- [x] 5 · Band mapping as a pure, unit-tested function — child walk closed: `ui/map/TrackSpeedHeatmap.kt` plus its test
- [x] 6 · Wire banded appearances into the selected-track branch — child walk closed: one dispatcher, two self-contained paths
- [x] 7 · Keep arrows when enabled, spacing rules untouched — child walk closed: an optional colour resolver, spacing rules untouched
- [x] 8 · Legend, if item 2 keeps it — child walk closed: a compact map strip, only while heatmap mode has a selected track
- [x] 9 · Fold the review findings R1–R10 into the plan before any code — child walks closed: eye toggle in the detail header, session-wide mode
- [x] 10 · Fold the Ask-review findings A1–A14: queued fixes applied, A7, A9 and A10 decided
- [x] 11 · Fold the second Ask pass B1–B17 — carrier stated, legend anchored bottom-left, validation policy withdrawn
- [x] 12 · Implement the change on feature/track-speed — shipped: twelve tests green, apk-build SUCCESS, no high defect from the Ask hop
- [x] 13 · Remediate the Ask hop's findings — two resolved by removal, five parked by decision, five carried into the next hop (plan §17, §18)
- [x] 14 · Device E2E on a real track — closed as **dropped**, not deferred: the build is green, but the run is not this session's work
- [x] 15 · Plan Outcome and feature-file pointers — Outcome extended, the Implemented entry added

- **Summary — closed 2026-09-14:** the source ran to its end — the selected-track speed heatmap shipped on `feature/track-speed` over three `#implement` passes, the two review hops folded, and the plan's Outcome extended with ramp v5, the declared scale and the persisted mode.
- Item 14 closed as **dropped** rather than parked, which exhausts the level and moves its open points out of the walk: the unrun device E2E and the five §18 residues are promoted into the plan's `## Outcome`.
- Its subject continues in the level below, which walks the render-mode generalization requested in the same session.

**Level 2 — Date:** 2026-09-14 · **Parent:** 1 · **Active:** 1 · **Closed:** 2026-09-14
- [x] 1 · Local band colour — chosen: each arrow takes the colour of the segment it sits on
- [ ] 2 · Constant contrast colour — dropped with the choice
- [ ] 3 · Under-stroke per arrow — dropped with the choice

- Resolutions: arrows carry the local band colour, reading as part of the speed profile. Dropped: the constant-contrast and per-arrow under-stroke alternatives; the first loses the arrow-as-speed cue, the second adds up to 2000 strokes on the overlay rebuild path.

**Level 2 — Date:** 2026-09-14 · **Parent:** 2 · **Active:** 1 · **Closed:** 2026-09-14
- [x] 1 · Global mode over whichever track is selected — confirmed: the mode governs the selected track
- [x] 2 · Live recording line — excluded, no behaviour change there
- [ ] 3 · Legend — carried out to item 8 rather than resolved here

- Resolutions: heatmap mode governs the selected track only, the live recording path untouched. Dropped: extending the mode to the live line, which would need its own incremental banding rather than a reuse of the stored-track branch. Carried: the legend, now item 8.

**Level 2 — Date:** 2026-09-14 · **Parent:** 3 · **Active:** 1 · **Closed:** 2026-09-14
- [x] 1 · A new section of Tracks — chosen: one owner for the selected-track render branch
- [ ] 2 · A new tracked feature — dropped with the choice

- Resolutions: the heatmap ships as a section of Tracks, keeping the render branch, its arrows and its settings under one owner. Dropped: a new tracked feature, which would have split one render path across two feature files.

**Level 2 — Date:** 2026-09-14 · **Parent:** 4 · **Active:** 3 · **Closed:** 2026-09-14
- [ ] 1 · One segmented row in the Tracks settings section — dropped: no settings UI in this pass
- [ ] 2 · Segmented row plus sliders for the 5 and 10 kn edges — dropped with the surface
- [x] 3 · No UI at all — chosen: the mode and the ramp are `maro.properties` keys

- Resolutions: no Settings surface in this pass — the selected-track rendering mode and the whole ramp are read from `maro.properties`, so changing them is a file edit plus a rebuild, and a `track.heatmap.mode` key carries the choice. Dropped: the segmented row and the band-edge sliders.

**Level 2 — Date:** 2026-09-14 · **Parent:** 5 · **Active:** 1 · **Closed:** 2026-09-14
- [x] 1 · A new file `ui/map/TrackSpeedHeatmap.kt` — chosen, with its own unit test
- [ ] 2 · Beside `computeTrackPolylineAppearance` in `MapScreen.kt` — dropped with the choice

- Resolutions: the band mapping ships as `ui/map/TrackSpeedHeatmap.kt` with `TrackSpeedHeatmapTest`, mirroring `DepthColorRamp` and its own file, so `MapScreen.kt` does not grow. Dropped: placing it in `MapScreen.kt` beside the existing appearance factory.

**Level 2 — Date:** 2026-09-14 · **Parent:** 6 · **Active:** 1 · **Closed:** 2026-09-14
- [x] 1 · One dispatcher plus two self-contained paths — chosen: no logic replicated, each path whole
- [ ] 2 · Inline the mode branch at both sites — dropped: replication rejected

- Resolutions: the selected track's appearance list comes from a single dispatcher that hands off to two self-contained path builders — today's gold casing-and-core pair, and the heatmap's casing plus banded core — so the mode is decided once and the branch is not copied to the two call sites. The effect's key list gains the mode and the ramp values so a rebuild with edited properties re-renders. Dropped: inlining the branch at both sites.

**Level 2 — Date:** 2026-09-14 · **Parent:** 7 · **Active:** 1 · **Closed:** 2026-09-14
- [x] 1 · A colour resolver on the overlay — chosen: one chevron per anchor in its local band, casing once beneath
- [ ] 2 · One colour for every arrow — dropped: it contradicts the local-colour decision

- Resolutions: the overlay gains an optional resolver — null keeps today's appearance iteration, so every non-selected track draws exactly as now, while heatmap mode paints one chevron per anchor in that anchor's band with the dark casing chevron once beneath. Spacing is untouched: `spacingPxForSpeed` and the UNIFORM/SPEED density rules stay as they are. Dropped: one colour for every arrow.

**Level 2 — Date:** 2026-09-14 · **Parent:** 8 · **Active:** 1 · **Closed:** 2026-09-14
- [x] 1 · A compact map legend strip — chosen: ticks at 5, 10 and the top, only while a track is selected in heatmap mode
- [ ] 2 · No legend in this pass — dropped with the choice

- Resolutions: the legend ships as a compact map strip with ticks at 5, 10 and the window top, drawn only while heatmap mode is on and a track is selected. Dropped: documenting the thresholds in `maro.properties` alone — a ramp whose edges sit at 5 and 10 kn cannot be read without a visible scale.

**Level 2 — Date:** 2026-09-14 · **Parent:** 9 · **Active:** 2 · **Closed:** 2026-09-14
- [x] 1 · Restore one minimal control — chosen, but sited on the selected track's detail view, not in Settings
- [ ] 2 · Re-word the Request line — dropped: the control makes the original wording true again

- Resolutions: R1 closes with an eye toggle in the selected track's header — reviewed since to sit in `OverlayLayer`'s drawer header, left of the trash — moving one session-wide mode that defaults to `maro.properties`; item 4's decision stands untouched because the control is not a Settings surface. Dropped: re-wording the Request line.

**Level 2 — Date:** 2026-09-14 · **Parent:** 9 · **Active:** 1 · **Closed:** 2026-09-14
- [x] 1 · One session-wide mode — chosen: the eye moves the mode itself, the file key being the start-up default
- [ ] 2 · Per-track memory — dropped: one state is simpler and the legend explains a single ramp

**Level 2 — Date:** 2026-09-14 · **Parent:** 13 · **Active:** 1 · **Closed:** 2026-09-14
- [x] 1 · Crowd rule basis — resolved by removal: the label-drop rule goes, so the basis question is moot
- [ ] 2 · A NaN tick position parses through into a canvas offset
- [ ] 3 · The twelve-row cap is untested beside its tested family sibling
- [ ] 4 · The bar's top is free file text, so a bad last row shortens it silently
- [x] 5 · The label-drop priority — void: every row now prints its label, overlaps accepted
- [ ] 6 · The landscape inset diff is still unmeasured
- [ ] 7 · The assumeTrue guard has no IDE fallback and can skip silently
- [ ] 8 · The shipped file against section 16 — the tie, and whether it stays a gate
- [ ] 9 · The mode token: the file writes heatmap, the parser accepts only speed
- [ ] 10 · No marks inside the colour bar — the labels carry position alone
- [ ] 11 · The eye toggle persists, reversing section 5's no-persistence non-goal
- [ ] 12 · Remove the label-drop rule — every table row prints, overlaps accepted

- **Summary — closed 2026-09-14:** 1 and 5 resolved by removal, since §18 deletes the rule they were about; 2, 3, 4, 6 and 7 parked by decision, all of them unreachable without a malformed file and each recorded in §18's own list; 8, 9, 10, 11 and 12 carried into the next hop, recorded in plan §17 and §18.

- Resolutions: the eye toggle moves one session-wide mode, starting from `track.heatmap.mode`, with nothing persisted and no per-track state; the legend remains the readout of that single state. Dropped: per-track memory and per-track persistence. Folded with it: R2–R10 corrected the plan's body, so the band count, the ramp domain, the core alpha, the neutral tint, the carry window, the legend's file, the shared derivation, the z-lift cost, the seam rule and the verification criteria are now stated rather than implied.

**Level 1 — Date:** 2026-09-14 · **Source:** the same session's request to generalize the shipped speed heatmap to every track — the render-mode triple toggle, the two surfaces it lands on, and the retirement of the menu's direction toggle · **Active:** 3 · **Closed:** 2026-09-15
- [x] 1 · Tri-state semantics and the arrow axis — resolved: the mode owns the arrows, three readings dropped
- [x] 2 · Banded scope: which tracks take the ramp, and the live line — resolved: history and pinned both band, the live line unchanged; pinned legibility carried into item 3
- [x] 3 · Selection cue once every rendered track is banded — closed 2026-09-15: the selection is width, stacking and opacity — 12 px against the newest track's 10, drawn above every other track and at full alpha, with a casing of its own width beneath it — while the rim stays parked in the pinned-cue plan
- [x] 4 · One persisted mode: the store, the migration and the file key — resolved: one non-null field defaulting to Simple, no migration, the properties key dropped
- [x] 5 · Settings surface: the Colours block becoming Default Colors, with no mode row — resolved: the inner heading is renamed, the expander label kept
- [x] 6 · Menu section: retire the direction toggle, add Tracks rendering — resolved: the switch takes the retired row's slot, the live card moves to the head
- [x] 7 · Arrow-density controls after the merge — resolved: the expander is untouched
- [x] 8 · Legend trigger once no track need be selected — resolved: drawn whenever the mode is Heat Map
- [x] 9 · Ramp meets the transparency settings — the fade each stored track keeps — resolved: the fade multiplies the band alpha
- [x] 10 · Per-track ramp cost, for up to 20 history plus pinned tracks — closed 2026-09-15 by its child: the rebuild keys follow the paths that can read them rather than the stored mode, the cache is dropped by decision, and the owed device run carries the measurement
- [x] 11 · Strings EN and FR, and the dead direction strings removed — resolved: Colours replaces Heat Map as the option label, two strings deleted
- [x] 12 · Drawer-header eye toggle: keep as a flip, or retire — resolved: it stays, scoped to the selected track
- [x] 13 · Plan file, then feature-file pointers once it lands — plan filed 2026-09-14 at `xTrack/Tracks/260914_FEAT_PLN_Tracks_render-modes.md`, and both pointers landed with the change itself; the level stays open on its two parked items

**Level 2 — Date:** 2026-09-14 · **Parent:** 1 · **Active:** 1 · **Closed:** 2026-09-14
- [x] 1 · One tri-state owns both axes — chosen: Simple is the default colours with no arrows, Dir and Speed keeps those colours and adds arrows, Heat Map bands the strokes and colours each arrow by its band
- [ ] 2 · Dir and Speed hard-wires the density — dropped: the arrow density row and both range sliders stay as they are
- [ ] 3 · Two surfaces by design — dropped: Settings and the menu carry the same three options
- [ ] 4 · Axes stay separate — dropped: the mode owns the arrows, so the ramp always draws them

- Resolutions: the render mode is the single owner of strokes and arrows — Simple is the default-colour rendering with no arrows, Dir and Speed keeps those colours and adds the arrows, Heat Map bands the strokes and colours each arrow by the band it sits on. `tracksDirectionVisible` retires with its stored `true` migrating to Dir and Speed, while the density control and both arrow range sliders survive untouched. Dropped: hard-wiring speed-based density, letting the two surfaces diverge, and keeping the arrows as an independent axis — the last knowingly gives up the one combination the choice forbids, a heat map with the arrows switched off.

**Level 2 — Date:** 2026-09-14 · **Parent:** 2 · **Active:** 1 · **Closed:** 2026-09-14
- [x] 1 · History and pinned banded — chosen: the mode governs both stored-track loops, and the live recording line keeps its own appearance
- [ ] 2 · History, pinned and the live line — dropped: the ramp is not rebuilt on the append path
- [ ] 3 · History only — dropped: pinned tracks would read as a second visual language
- [ ] 4 · History and pinned banded — dropped: subsumed by the choice, which already bands pinned

- Resolutions: the render mode governs the two stored-track loops — history and pinned — inside the loop that exists today, so each stored track is banded once per rebuild; the live recording line keeps its appearance, since banding it would move a ramp rebuild onto the append path. The pinned amber gradient is given up in heat map mode, colour being the speed reading there, which carries one open point into item 3: with colour spent, how a pinned track stays legible is a cue question, not a colour question. Dropped: banding the live line, and a history-only scope.

**Level 2 — Date:** 2026-09-14 · **Parent:** 3 · **Active:** 4 · **Closed:** 2026-09-15
- [ ] 1 · Casing only, selection — **void 2026-09-15**: it shipped, and it is the reading the removal undid
- [ ] 2 · Gold survives selection — **void 2026-09-15**: it needs the ramp to cover the unselected only, which D2 forbids
- [ ] 3 · Casing carries both cues — **parked 2026-09-15**: it is the rim, and the rim is parked in the pinned-cue plan with the alpha-stacking finding and the three shapes that would repair it
- [x] 4 · No map cue — chosen 2026-09-15: with the casing gone, selection and pinned identity are expressed outside the map, by the z-lift, the gold interior in the two simple modes, the drawer header and the pin

- **Summary — closed 2026-09-15:** resolved by reading 4 — the map carries no new cue for the other tracks — with readings 1 and 2 void and reading 3 parked as the rim; the selection reads by width, opacity and its own casing.

- **Resolutions:** the selected track keeps its z-lift and its gold interior where the ramp is not painting, and loses the 16f black casing; the rim, its alpha boost and the per-type width table stay parked, each with the reason it did not ship.

**Level 2 — Date:** 2026-09-14 · **Parent:** 4 · **Active:** 1 · **Closed:** 2026-09-14
- [x] 1 · One non-null stored mode defaulting to Simple — chosen: a single `TrackRenderMode` field in `AppSettings` beside its key constant, default SIMPLE, no migration and no properties key
- [ ] 2 · Nullable, with the file as fallback — dropped: a second default source is what the cold-start defect was made of
- [ ] 3 · Keep `track.heatmap.mode` and its tokens — dropped with the key
- [ ] 4 · Derive the mode from the two legacy booleans — dropped: the store could then express combinations the toggle cannot

- Resolutions: the mode is one non-null field in `AppSettings`, persisted under its own key and defaulting to SIMPLE; neither `speed_heatmap` nor `track_direction_visible` is read any more, so an install that held either returns to the default — the accepted consequence of going back to default rather than migrating. `track.heatmap.mode` goes with them, which leaves one default (the code constant) and one owner of the current value (the store); the ramp keeps its `track.heatmap.*` prefix even though the mode now covers three renderings, renaming it touching the baked file and the parser for no behaviour change. Dropped: a nullable mode reading the file as a fallback, keeping the mode key, and deriving the mode from the two booleans.

**Level 2 — Date:** 2026-09-14 · **Parent:** 5 · **Active:** 1 · **Closed:** 2026-09-14
- [x] 1 · Inner heading renamed only — chosen: the Colours heading and its description become Default Colors, naming the rendering used outside Heat Map, while the expander keeps its Track rendering label
- [ ] 2 · Expander renamed — dropped: the expander also holds the track count and both transparency ranges
- [ ] 3 · Expander dissolved — dropped with the rename, which makes the split unnecessary

- Corrected 2026-09-14 before anything was chosen: the three-way switch belongs to the menu, not to Settings, so the four readings that assumed a Settings switch are void and are replaced by these three. The mode row is no longer this item's subject — item 6 carries it, as a "Tracks rendering" label that takes the place of the "Show dir & speed" row.

- Resolutions: the Settings structure stands as it is and only the inner Colours heading is renamed Default Colors, its description naming those colours as the rendering used outside Heat Map; the expander keeps its Track rendering label. Dropped: renaming the expander itself, since it also carries the track count and both transparency ranges, which govern all three modes and are not colours, and dissolving the expander into separate headings, which the rename makes pointless. The label change is one string pair, EN and FR, and it lands with item 11.

**Level 2 — Date:** 2026-09-14 · **Parent:** 6 · **Active:** 1 · **Closed:** 2026-09-14
- [x] 1 · The retired toggle's slot — chosen: the "Tracks rendering" caption and its three-way row replace the "Show dir & speed" row in place, the dividers unchanged
- [ ] 2 · Top of the Tracks card — dropped: the live recording block holds that place
- [ ] 3 · Stacked rows instead of a segmented control — dropped: the three options fit one segmented row

- Resolutions: the Tracks rendering section replaces the "Show dir & speed" row in its own slot, carrying the three-way switch Simple | Dir & Speed | Heat Map under one caption, and the Tracks card's order is settled as the live card, then the track list row with its count and chevron, then the three-way switch, then the Import and Export pair — which moves the live block to the head of the card from its present place after the import pair. Dropped: heading the card with the switch, and stacked rows, the three labels fitting one segmented row at drawer width. The switch reads and writes the single stored mode, so the menu and the map cannot disagree.

**Level 2 — Date:** 2026-09-14 · **Parent:** 7 · **Active:** 1 · **Closed:** 2026-09-14
- [x] 1 · Untouched — chosen: the Track Speed and Direction expander keeps its label and all three controls
- [ ] 2 · Retitled and described — dropped with the choice
- [ ] 3 · Retitled, dimmed in Simple — dropped with the choice

- Resolutions: the arrow expander ships exactly as it is — label, density choice, gap range and speed range unchanged — so nothing in Settings states that Simple draws no arrows. The cost is accepted: a user can move those controls while Simple is current and see nothing change, and the menu switch is the only thing that explains why. Dropped: retitling the expander, and dimming its controls in Simple.

**Level 2 — Date:** 2026-09-14 · **Parent:** 8 · **Active:** 1 · **Closed:** 2026-09-14
- [x] 1 · Whenever the mode is Heat Map — chosen: the legend is drawn whenever tracks render as a heat map, selection no longer a condition
- [ ] 2 · Unchanged — dropped with the choice
- [ ] 3 · Then dismissed — dropped with the choice
- [ ] 4 · Its own control — dropped with the choice

- Resolutions: the legend is drawn whenever the mode is Heat Map, so the ramp's key stands for the whole time the ramp is in use and the old selected-track conjunction goes with the mode itself. Dropped: keeping the selection condition, an auto-dismiss, and a show-or-hide control on the strip.

**Level 2 — Date:** 2026-09-14 · **Parent:** 9 · **Active:** 1 · **Closed:** 2026-09-14
- [x] 1 · The fade multiplies the bands — chosen: each band's alpha is the track's own fade value, newest to oldest for history and the pinned range for pinned, times the ramp's core alpha
- [ ] 2 · The ramp ignores the fade — dropped: it would strip the only cue left once colour reads speed
- [ ] 3 · The fade replaces the ramp's alpha — dropped: one alpha would govern instead of two, at the price of the core-alpha key meaning nothing for stored tracks

- Resolutions: the banded stroke meets the transparency settings the way every other rendering does — the colour arrives from the ramp and the alpha is the track's own fade, so a history track fades newest to oldest and a pinned one fades across its own range, with the ramp's core alpha multiplied in. Dropped: ignoring the fade, which would leave every stored track at one opacity and take away the cue item 3 was parked on, and letting the fade replace the core alpha, which would make that key meaningless for stored tracks. Cost accepted: two alphas multiply, so the heaviest fade values desaturate the bands and the oldest tracks read their speed least sharply.

**Level 2 — Date:** 2026-09-14 · **Parent:** 10 · **Active:** 3 · **Closed:** 2026-09-15
- [x] 1 · Trim the rebuild keys — resolved 2026-09-15: the trim follows the path, not the stored mode, so a group is keyed whenever any track can take a path that reads it — the ramp when Colours or the eye bands, the arrows when the mode is not Simple or the eye bands, the colours when the mode is not Colours or the eye unbinds — which leaves the normal-case groups exactly as they are
- [ ] 2 · Trim and cache — **parked 2026-09-15** by decision, no cache for now: the reading stands as written and is the point to resume from if the owed device run shows a stall
- [x] 3 · Trim, then measure — chosen 2026-09-15: the uncached path ships as item 1 left it, the owed device run is the measurement, and a cache returns only as a follow-up if that run stalls

- **Resumed and closed 2026-09-15:** reading 1 taken with the path-derived amendment, reading 2 parked so no cache is built, reading 3 chosen so the device run already owed is the measurement. The trim could no longer stay keyed on the stored mode, whose arrow group goes missing while the mode is Simple and the eye bands the selected track.

- **Summary — closed 2026-09-15:** resolved by reading 1 amended (a key group is present whenever any track can take a path that reads it — the ramp for Colours or an eye-band, the arrows for anything but Simple or an eye-band, the colours for anything but Colours or an eye-unband) and reading 3 (uncached, measured on the owed run); parked: reading 2's band cache, which returns only if that run stalls. The session's measurement still stands: per rebuild the banded path stays linear in the points of the rendered tracks, since each band builds only its own geometry, while the object and polyline counts rise by a constant factor, and nothing new bounds the track count — the not-pinned slider and the unconditional pinned render being what bounded it before.

**Level 2 — Date:** 2026-09-14 · **Parent:** 11 · **Active:** 1 · **Closed:** 2026-09-14
- [x] 1 · The third option is renamed — chosen: the switch reads Simple | Dir & Speed | Colours in English and Simple | Dir & Vitesse | Couleurs in French, while the ramp keeps its own name in the plan and the properties
- [ ] 2 · Heat Map stays the option label — dropped with the choice
- [ ] 3 · The label differs between locales — dropped: one control, one meaning

- Resolutions: the switch carries three option labels — Simple, Dir & Speed, Colours, in French Simple, Dir & Vitesse, Couleurs — under a caption reading Tracks rendering and Rendu des traces, while the ramp keeps its Heat Map name everywhere the code and the properties refer to it. The Settings colours heading becomes Default Colors and Couleurs par défaut, its description naming those colours as the rendering used outside Colours. The two retired strings are deleted from both locales: "Show dir & speed" and the unreferenced "Show tracks direction on map". Dropped: keeping Heat Map as the option label, and letting one control's label mean different things in the two locales.

**Level 2 — Date:** 2026-09-14 · **Parent:** 12 · **Active:** 1 · **Closed:** 2026-09-14
- [ ] 1 · Retire the eye — dropped with the choice
- [x] 2 · Keep it as a Colours shortcut — chosen with an amendment: its effect is scoped to the selected track, so it never moves the mode every other track renders by
- [ ] 3 · Keep it, landing on Simple — dropped with the choice
- [ ] 4 · Replace it with the switch — dropped with the choice

**Level 1 — Date:** 2026-09-15 · **Source:** the pending set — the outlines plan's open items, the controls pass's outcomes, and the feature's open todos · **Active:** 6
- [x] 1 · The legend gate — closed 2026-09-15: the scale shows when the focused track's fill is the ramp, so a selection the eye has flipped away hides it and no selection hides it whatever the mode
- [x] 2 · The casing offset on the tempered core — closed 2026-09-15, then **superseded the same day**: the reading was argued again and settled the other way, the border belonging to the selection and so reading the line's own width, with the superseded argument and its cost kept in the outlines plan §2.1
- [x] 3 · Which widths are canonical — closed 2026-09-15: the file is, so every code default follows it — widths, casing, tempering, spacing and the ramp alike — and the comments are corrected with them
- [x] 4 · The casing weight — closed 2026-09-15: 22, set by hand and to be adjusted later, which is a 4 px rim a side over the shipped 14 px core; the file and the code default move together
- [x] 5 · Device look — closed 2026-09-15: the line, its dominance over the older tracks and the arrow sizes all read right, and the scale's visibility is the one thing wrong
- [x] 6 · Scale visibility — closed 2026-09-15: the gate asks whether a banded stroke is painted on the map, so unselecting in Colours keeps the scale while the eye's value with nothing selected hides it; built as the plan's four-parameter predicate over a painted-id set covering history and pinned, with all seven cases asserted
- [x] 7 · Scale rendering — closed 2026-09-15, then **reopened as item 14**: the shape it closed on took the whole row's width and a background token of its own, which the device rejected
- [x] 8 · The apex hairline — **dropped 2026-09-15**: cosmetic to the point of invisible
- [x] 9 · `### verification` backlog — the section's own eleven device and E2E items
- [x] 10 · `### track-list` verification — the section's six items
- [x] 11 · `### auto-marker-cleanup` — its deploy and E2E check
- [x] 12 · The scale's chrome — closed 2026-09-15: the strip is one toggle button wide with its left edge on the row's own gutter, its card paints what a disabled toggle paints, the `ui.legend.background` token is gone from all four of its files, and §5's lows went with it; `apk-build.bat` SUCCESS with 126 scoped tests green, and the Ask hop's two remaining nits — a comment that repeats the superseded arithmetic and the test's hand-mirrored outer wiring — sit in the outlines plan §5

- **Corrected 2026-09-15:** this level was rewritten by hand after an agent pass recorded closures nobody made — a device look marked done, the legend's chrome marked done, and the casing fixed at 18 against the 22 settled here — so those lines are gone, and the correction is recorded rather than quietly tidied.

- Resolutions: the eye keeps its place in the drawer header and its business is the selected track alone — it flips that one track's rendering and leaves the mode that governs every other stored track untouched, so the menu switch stays the only writer of the mode. Dropped: retiring the eye, letting it land on Simple, and replacing it with the switch. One point is carried into item 13, since item 4 stopped reading the legacy key the eye used to write: the eye now needs a value of its own, and the plan must say where it lives and whether it persists.

## Implemented

- **Data model** — `Track`/`TrackPoint` protobuf, `TrackSummary` index, relative `timeOffsetSec`
- **Recorder** — OFF⇄ON state machine, geofence auto-detect, speed gate, orphan recovery
- **Persistence** — `TrackRepository` protobuf CRUD, 30s checkpoints, GPX 1.1 export
- **Map rendering** — active + history polylines, transparency/color gradient, overlay diff
- **Layer toggle** — `TrackLayerIcon` in FanLayout
- **TrackViewModel** — `StateFlow` bridge, LRU cache, sorted list
- **UI** — `TrackStatusIcon`, `MenuDrawerOverlay`, `TrackHistoryOverlay`, `LiveTrackCard`
- **Settings** — unified tracking section, HSV pickers, transparency semantics
- **Stop detection** — `AdaptiveGpsPolicy` position-only → `xTrack/Tracks/260618_FEAT_PLN_Tracks_adaptive-isstill.md`
- **Settings fix (2026-06-20)** — 6 tracking fields persisted, opacity naming fix
- **mtrack-setting-opacity (2026-06-20)** — transparency naming + inverted semantics
- **gps-line-acquisition (2026-06-20)** — removed `PASSIVE_PROVIDER` listener
- **gps-background (2026-06-20)** — foreground service rewrite → `xTrack/Tracks/260620_FEAT_PLN_Tracks_gps-background.md`
- **Demo track visibility (2026-06-21)** — off-by-one fix + `gpsMode` bypass
- **spike-rejection-v2 (2026-06-22)** — four-gate algorithm → `xTrack/Tracks/260622_FEAT_PLN_Tracks_spike-rejection-v2.md`
- **pinned-tracks (2026-06-22)** — pin icon + `pinned` proto field → `xTrack/Tracks/260622_FEAT_PLN_Tracks_pinned-tracks.md`
- **track-list-render-indicator (2026-06-24)** — `computeTrackPolylineAppearance()` shared utility
- **idle-time-tracking (2026-06-28)** — `idleDurationSec` accumulator
- **populate-track-info (2026-07-05)** — auto title/description from `whereAmI()`
- **resume-track (2026-07-12)** — resume finalized track as live recording
- **merge-tracks (2026-07-12)** — `TrackMerger` utility
- **checkmark-bottom-right (2026-07-14)** — badge position fix
- **notif-lifecycle-hardening (2026-07-14)** — tap-to-open, post-kill, recording-aware exit
- **tracks-paint-order (2026-07-17)** — newest-on-top + highlight-to-top → `xTrack/Tracks/260717_FEAT_PLN_Tracks_tracks-paint-order.md`
- **idle-reconciliation (2026-08-15)** — unified compound idle predicate
- **track-direction-arrows (2026-09-02)** — chevron overlay, density settings
- **marker-track-link** — `UserMarker.trackId` single back-reference + backfill + delete cascade → `xTrack/Tracks/260831_FEAT_PLN_Tracks_marker-track-link.md`
- **gps-recording-regression** — service GPS sampling pinned to Main dispatcher (Looper fix)
- **gps-switch-confirm** — confirm before switching position source while recording
- **live-track-paint-regression (2026-09-11)** — live polyline was never created after the C3/C4 seam extraction (creation effect read a frozen parameter through `snapshotFlow`); creation re-keyed on recorder state, append/trailing made self-healing, `isLive` excluded from the map-resolve path → `xTrack/Tracks/260911_FEAT_PLN_Tracks_live-track-paint-regression.md`
- **resume-confirm-backup (2026-09-11)** — resuming a stored track now asks first: `ResumeConfirmSheet` with a default-checked backup box; confirm writes a hidden, unpinned copy (fresh UUID, suffixed name, marker links stay on the original) then resumes the original; wired on the list card (early dismiss dropped) and both dashboard cards, gated by `isRecording` → `xTrack/Tracks/260911_FEAT_PLN_Tracks_resume-confirm-backup.md`
- **selected-track-speed-heatmap (2026-09-14)** — the selected track renders as a banded speed ramp in place of the gold highlight: `track.heatmap.familyN.*` colours on per-family draw steps, `track.heatmap.scaleTicks` positions printed with labels free to differ from them, nothing drawn inside the bar, and a drawer-header eye toggle whose choice persists with the key as the first-run default → `xTrack/Tracks/260914_FEAT_PLN_Tracks_selected-track-speed-heatmap.md`
- **speed-heatmap-v5-and-persistence (2026-09-14)** — the third `#implement` pass on the entry above: six ramp families at 5/7/12/15/35/70 each on its own draw step, the label-drop rule removed so every tick row prints its own text, `track.heatmap.familyCount` removed so the file's `familyN.*` keys end the ramp, `track.heatmap.scaleMinKn` (2 kn) as the bar's foot to the table's last position, `heatmap` accepted as the mode token with `speed` as its alias, and the eye's stored choice now actually read at cold start — the settings flow seeded from the snapshot the view-model had already loaded → `xTrack/Tracks/260914_FEAT_PLN_Tracks_selected-track-speed-heatmap.md`
- **track-render-modes (2026-09-14)** — the heatmap generalized to every stored track under one three-way switch: `TrackRenderMode` with one persisted `AppSettings.trackRenderMode` (default Simple, the two legacy keys and `track.heatmap.mode` dropped), one `trackRenderPlan()` dispatching the plain, gold and banded paths across both stored-track loops, the banded path taking the track's own fade (D8) and width (D11), a mode-aware rebuild key list, the menu's Tracks rendering section replacing the "Show dir & speed" row with the live block at the card's head, the Settings heading renamed Default Colors, the legend keyed on the mode alone, and the drawer eye scoped to the selected track as a session-only override; `apk-build.bat` SUCCESS with 48 scoped tests green and two Ask-hop mediums open (the eye's override also rewrites the arrow decision, and the arrow rebuild keys are absent while the mode is Simple with the eye banding) → `xTrack/Tracks/260914_FEAT_PLN_Tracks_render-modes.md`
- **ramp-alpha-ceiling-removal (2026-09-15)** — R4's 0.9 ceiling is gone: a band's alpha is the track's own fade alone, and `track.heatmap.coreAlpha` leaves the key list, the `AppConfig` parse and the `HeatmapRamp` type, the two heatmap test classes retuned with it; `apk-build.bat` SUCCESS, 63 heatmap tests run, the three pre-existing `HeatmapRampPropertiesTest` reds untouched → `xTrack/Tracks/260915_FEAT_PLN_Tracks_ramp-alpha-ceiling-removal.md`
- **carry-window-removal (2026-09-15)** — `track.heatmap.carryMaxSec` and its logic are gone: a point's speed is the stored value or a derivation from its neighbour, else null, with the carry state, the window parameter and the Colours rebuild key removed and the properties key deleted; `apk-build.bat` SUCCESS, `TrackSpeedHeatmapTest` 32/32 green, the same three reds untouched → `xTrack/Tracks/260915_FEAT_PLN_Tracks_carry-window-removal.md`
- **track-widths (2026-09-15, `feature/no-black-casing`)** — the per-type widths are configuration, ported from `2728c78` with the rim left behind: five `track.width.*` keys (12 / 14 / 10 / 8 / 6 — the selected width at 14, as the file holds it) with their `AppConfig` accessors, the four width constants gone, the live line's nine sites and the gold core reading the keys, the newest track derived from recency rather than the loop's index, and the five values in the rebuild key list; `apk-build.bat` SUCCESS with the scoped track tests green and the three pre-existing reds untouched → `xTrack/Tracks/260914_FEAT_PLN_Tracks_pinned-cue-casing.md`
- **selection-rendering (2026-09-15, `feature/no-black-casing`)** — the selected track is opaque in all three modes and carries its casing again with the legacy `#CC000000` laid under whichever path the selection took and sharing the track's title, and the code's width defaults were aligned with the shipped file; the casing then widened to `track.width.selected.casing` — 24 at the time, 22 since the 2026-09-15 walk item 4, which is what the file holds — and the chevrons were put back in proportion — length 2.5 × the core under a ceiling from the widest stored width, coloured stroke 0.5 × the core, and the rim drawn **outside-only** by shifting the dark V outward by half the casing's excess over the core at the coloured stroke's width, its arms extended to the coloured tips — with the gold path crossing the resolver seam so a selection carries its chevron casing in Dir and Speed and in Colours; `apk-build.bat` SUCCESS with 110 `ui.map` tests green and the pre-existing reds untouched → `xTrack/Tracks/260914_FEAT_PLN_Tracks_pinned-cue-casing.md`
- **controls-separated (2026-09-15, `feature/no-black-casing`)** — arrows now follow the stored mode alone, so the drawer eye picks only the selected track's fill; the eye's value persists on the selection (`track_selection_banded`, written on the first tap, read through `contains()` so an untouched install still mirrors the mode); the legend gained the eye as a trigger; `track.arrow.scaleKnee=10` with `track.arrow.temper=0.5` now temper the chevron multiples above the knee, leaving the newest, pinned and oldest classes at their own core; and `track.direction.maxSpacingDp` rose to 400, already inside the settings slider's 4–640 range; `apk-build.bat` SUCCESS with the scoped run at 125 tests and six pre-existing drift reds → `xTrack/Tracks/260914_FEAT_PLN_Tracks_pinned-cue-casing.md`
- **selection-rim-and-legend-gate (2026-09-15, `feature/no-black-casing`)** — the outlines plan's three open items shipped through the `#implement` pipeline: the arrowhead's dark rim now reads the line's own width (`chevronCasingOffset(casing.strokeWidth, metrics.strokeWidth)`, 4 px at the shipped pair against the tempered reading's 5, with the nine comments that argued the superseded reference rewritten and the two documented figures recomputed to 1 px and ~1.8 px); the legend gate became `storedOnMap && (mode == HEATMAP || (selectionOpen && eyeOverride == true))`, keyed on a painted-id set hoisted out of the effect and covering history **and** pinned, so unselecting in Colours keeps the scale, with `TrackRenderModePathTest` rewritten onto the seven cases and the old legend-equals-selection equivalence deleted; and the strip's chrome arrived — one home for the 44 dp square and the 6 dp gutter, read by the row, the locked mirror and the strip (194 dp, 244 dp with the recenter button), its content start-aligned and its card on the new `ui.legend.background` token (`#4D16213E`) in place of the text-scrim family; `apk-build.bat` SUCCESS on the first attempt, 126 scoped `ui.map` + `config` tests green with no red in scope, and the Ask hop's seven lows recorded in the plan's §5 → `xTrack/Tracks/260914_FEAT_PLN_Tracks_pinned-cue-casing.md`
- **legend-chrome-and-lows (2026-09-15, `feature/no-black-casing`)** — the outlines plan's §2.3 and §5 landed as one hop, and both correct the entry above: the strip is one toggle button wide (`TOP_TOGGLE_SQUARE`, 44 dp) and starts on the row's own 6 dp gutter, so its two vertical edges are the leftmost button's two edges and the recenter button never enters the arithmetic (`toggleRowCount`, whose only reader the width term was, deleted with it), its card paints what a disabled toggle paints — `semantic.inactive` at `status.gps.alpha.dimmed`, ~10 % white rather than the token's 20 % — with `ui.legend.background` gone from `colors.properties`, `AppConfig` and `color-scheme.md`, its padding 6 dp a side and its labels one line each; the six live lows followed: the strip's KDoc now keys the banded-stroke rule, the gate's painted-set read became a keyed `derivedStateOf`, a track counts as painted only where a stroke actually landed, `TOP_TOGGLE_ROW_HEIGHT` reads `TOP_TOGGLE_SQUARE`, the three comments that read as before the change are corrected (the tempering note in `maro.properties`, `temperedCore`'s and the `uiTextScrim` KDoc's claimed `#80000000`), and one `bandedStrokeOnMap` now serves both `MapScreen` and `TrackRenderModePathTest`; `apk-build.bat` SUCCESS on the first attempt with 126 scoped `ui.map` + `config` tests green and no red in scope → `xTrack/Tracks/260914_FEAT_PLN_Tracks_pinned-cue-casing.md`
- **strip-second-pass (2026-09-15, `feature/no-black-casing`)** — corrects two figures in the entry above and finishes the strip. Its card composites to **white at 50 %**, not the ~10 % that entry states, `copy(alpha = …)` replacing the token's own 20 % rather than multiplying it — and the button it matches is the GPS off-state alone, since the GPS icon alphas its glyph while tracking and lock alpha their whole box and so land twice as faint. Its padding became asymmetric: 6 dp at the start, where the bar must hold the button's edge, and 2 dp at the end, where the rest is slack, which lifted the label box from 12 dp to 16 dp against a widest shipped label of about 11 dp. Its labels moved off pure white to `uiTextSecondary` (`#FF78909C`), white on a white-at-50 % card being illegible once the dark scrim went. And the gate's wiring collapsed into one `legendVisibleFor` overload that production and the test both call, so which argument goes where can no longer drift; `apk-build.bat` SUCCESS with 126 scoped `ui.map` + `config` tests green → `xTrack/Tracks/260914_FEAT_PLN_Tracks_pinned-cue-casing.md`
- **disabled-toggle-background (2026-09-15, `feature/no-black-casing`)** — one property now governs every disabled background: `ui.button.disabled.background.alpha=0.33` read through `AppConfig.buttonDisabledBackgroundAlpha` by the GPS DEMO, tracking OFF and lock OFF boxes and the legend's card, so all four composite to `semantic.inactive` at 33 %, i.e. white at 33 %, with the three glyphs left at 0.50 — the DEMO branch's bare `0.25f`, the strip's `0.25f` and the two 0.50 dimmed keys that the tracking and lock branches copied all go, and the Ask hop's 0.25 × 0.25 model is corrected in the comments: a background's weight is its own alpha, `.alpha(contentAlpha)` sitting after `.background(...)` and dimming the glyph alone. `status.lock.alpha.dimmed` and `status.tracking.alpha.dimmed` were retired with their properties, loader lines and comments once each proved dead (the moving branch was the only reader), while `status.gps.alpha.dimmed` stays at 0.50 for `RegulatedZoneIconProvider`'s informational zone icons, its KDoc and key comment re-pointed to name that surviving reader; `color-scheme.md` gained the new key's row, its DEMO row moved onto it and its dimmed row lost the DEMO claim, and the DEMO comment's false "the icon itself does not change" clause and the strip's background comment were rewritten onto the model; `apk-build.bat` SUCCESS on the first attempt with 126 scoped `ui.map` + `config` tests green → `xTrack/Tracks/260914_FEAT_PLN_Tracks_pinned-cue-casing.md`
- **disabled-surface-text-pair (2026-09-15, `feature/no-black-casing`)** — corrects the two figures in the entry above: the fill's key now reads **0.66**, hand-tuned in the file, and `AppConfig`'s default was realigned to it from 0.33, so the composite is white at 66 % rather than 33 %. The surface gained its text side — `ui.button.disabled.text.color` as `${ui.text.secondary}` and `ui.button.disabled.text.bold=true`, read through `AppConfig.buttonDisabledTextColor` and `buttonDisabledTextBold` — and both cards that draw text on that fill take the pair: the speed-scale's tick labels and the map's zone-info line, which was `uiTextPrimary` at 9 sp until the pass before. The scale's measured line box now receives the drawn weight rather than a hardcoded bold, so its end insets keep matching the face; `color-scheme.md` gained the pair's rows beside the alpha's and its alpha row moved to 0.66, the guidelines' status-icon recipe was re-pointed from the retired `status.*.alpha.dimmed` 0.50 pattern to the trio, and the strip's comment that still argued the retired token was replaced; `apk-build.bat` SUCCESS with 126 scoped `ui.map` + `config` tests green → `xTrack/Tracks/260914_FEAT_PLN_Tracks_pinned-cue-casing.md`
- **map-chrome-families (2026-09-16, `feature/no-black-casing`)** — the toggle row's rendering and the legend's surface became configuration, in two families over one shared fill. The five squares read `ui.map.toggle.square`, `.corner.radius` and `.icon.size` from a single home; the row's off-state fill is `ui.map.toggle.inactive.background`, aliasing `ui.map.surface.inactive` (`#A8FFFFFF`), with the glyph dim at `ui.map.toggle.inactive.icon.alpha`; and the active tint is `ui.map.toggle.active.background.alpha`, which replaces `status.gps/tracking/lock.alpha.active` for the row — only the GPS key survives, for the regulated-zone icons that read it. The legend reads `ui.map.overlay.*` for its fill, text colour and a 100–900 weight that replaces the bold boolean, its label size, corner radius, padding, border and the gap below the row, and the `ui.button.disabled.*` trio from the pass before is gone. Open from the Ask hop, each recorded rather than fixed here: the uniform 6 dp padding returns the label box to 12 dp where the 2026-09-15 pass had lifted it to 16; the glyph dim is read by three boxes, not the four the keys and docs claim; `EarthWaterIcon`'s inactive arm is dead code whose call site hardcodes `isActive = true`; two `status.*.alpha.active` keys and the four state colours plus `ui.text.scrim` now have no reader; and the zone line's `lineHeight` is the last type literal → `xTrack/Tracks/260914_FEAT_PLN_Tracks_pinned-cue-casing.md`
- **legend-face-remediation (2026-09-16, `feature/no-black-casing`)** — the `#implement` Ask findings on the entry below were closed in two Code hops, the first correcting that entry rather than extending it: the face predicate went outright instead of losing its gate parameter, since its `legendAllowed` arm was dead behind the branch's own guard and the two cases pinning it asserted a state the wiring cannot produce, so the scoped run drops from 128 to 126 with `TrackRenderModePathTest` back at its original 15 and the branch reading `appSettings.trackLegendExpanded` inside the existing guard; the two arms now share one hoisted `legendAnchor` value rather than repeating the chain, the collapsed square's KDoc names the GPS DEMO branch as its model instead of claiming a row-wide rendering, and the branch comment says the arms differ only in width; every token comment and doc row that still counted four toggle boxes — both `colors.properties` comments, `AppConfig`'s accessor KDoc, two `color-scheme.md` rows and the guidelines paragraph — now names the five readers; `apk-build.bat` SUCCESS on both hops with no behaviour and no palette key touched, F2 left accepted as recorded and F5 left as a post-task suggestion → `xTrack/Tracks/260916_FEAT_PLN_Tracks_legend-collapse-toggle.md`
- **legend-collapse-toggle (2026-09-16, `feature/no-black-casing`)** — the speed scale became one control with two faces on one anchor: the card keeps its geometry and takes the tap as its own collapse target, and the collapsed face is `LegendToggleButton` — one 44 dp square on the row's own recipe (`ui.map.toggle.square` / `.corner.radius` / `.icon.size` over `ui.map.toggle.inactive.background`, the dim applied to the glyph alone) with no active state, since the card being on screen *is* active. The glyph is the ⏱ stopwatch written `\u23F1\uFE0F` — the U+FE0F selector being what asks for the colour emoji, U+23F1 alone being `Emoji_Presentation=No` — swapped after the pass from 🕛 (U+1F55B), which was always colour but read as noon; `apk-build.bat` SUCCESS again in 3 s on that swap. The state is one non-null `AppSettings.trackLegendExpanded` (`track_legend_expanded`, default true, so an untouched install shows the card) written through `updateSettings`, and the display rule is untouched — `legendVisibleFor`, `legendVisibleForState` and `bandedStrokeOnMap` carry no new parameter and no body change — with `legendFaceExpanded` pinning the two faces beside the gate and two cases added to `TrackRenderModePathTest` over its original ones; the scoped `ui.map` + `config` run is green at 128 tests, with the Ask hop's two mediums (the predicate's gate arm dead at its guarded call site, and the card's tap surface with its merged semantics) and four lows recorded in the plan's §10 → `xTrack/Tracks/260916_FEAT_PLN_Tracks_legend-collapse-toggle.md`
- **legend-label-flex (2026-09-16, `feature/no-black-casing`)** — the device report on the scale's values was a break, not a wrap: with `softWrap = true` and `maxLines = 1` Compose split a two-digit label **inside the digit pair** and kept one line, so a tick could print only its first character. `softWrap = false` is the fix, and the label column now takes `weight(1f)` on a full-width row so the values are centred in the whole space between the bar and the card's right inner edge instead of packed at the bar's edge, with the text taking `fillMaxWidth` so its alignment has a box. The bar, gap spacer, tick offsets, label measurement and height arithmetic are unchanged, the prefixed `@Composable`'s dead parameters stay untouched, and the surface-owned card geometry is [ColorManagement's](xTrack/ColorManagement/260916_FEAT_PLN_ColorManagement_map-surface-normalization.md); `apk-build.bat` SUCCESS with the scoped run steady at 126. The label's 12 dp ceiling does **not** move with this — two digits measure 11.2–11.4 dp against the 12 the 44 dp card leaves, so anything above a font scale of about 1.06 still clips and real room means widening the card, narrowing the bar or shrinking the gap → `xTrack/Tracks/260916_FEAT_PLN_Tracks_legend-collapse-toggle.md`
- **render-axes-split (2026-09-17, `feature/twks-props`)** — the menu's Tracks rendering switch became the twin box: two chips, **Arrows** and **Colours**, each on or off by itself under the caption "Display Tracks with:", so where the retired triple gave three mutually exclusive states the pair gives four, the new one being the ramp without chevrons. `TrackRenderMode` and `menu_render_mode_*` are gone outright — enum, key constant, parse, strings and both test names — and the store became two non-null booleans, `track_arrows` (default off) and `track_colours` (default on), with the retired `track_render_mode` read once at cold start and erased in the same edit that writes the pair, so an install that held `SIMPLE`, `DIR_SPEED` or `HEATMAP` keeps exactly the look it had; `trackRenderPlan`, `selectionBandedAfterTap` and the legend gate read the two flags while the drawer eye keeps its shipped scope (the selection's fill, the legend still raised by any banded stroke), and `SegmentedRow` moved to `ui/components` beside the new `MultiSelectRow`, documented as §2.7/§2.7b with the choice tree updated. `apk-build.bat` SUCCESS on the first attempt, the scoped `ui.map` + `config` run at 130 tests with `TrackRenderFlagsPathTest` (14 cases) and `TrackRenderStringsTest` replacing the two classes named after the enum, and the only five reds the pre-existing `maro.properties`-versus-`AppConfig` drift from `f2531e4`; the device pass over the four combinations, the legend gate and the migrated keys is owed, as is pinning the migration's pure mapping → `xTrack/Tracks/260917_FEAT_PLN_Tracks_render-axes-split.md`
- **render-axes-split second pass (2026-09-17, `feature/twks-props`)** — corrects the entry above where it says the drawer eye "keeps its shipped scope", on two points asked for the same afternoon. The header toggle wears the standalone `Speed` speedometer of `ykws.android.maro.ui.icons` in place of `Visibility`, so the control that flips one track's ramp speaks the same family as the speed scale's collapsed face, and it keeps the accent-versus-inactive **tint** readout a colour emoji could never carry; and the legend gate no longer keys "any banded stroke" — `legendVisibleFor` is now `storedOnMap && (if (selectionOpen) (eyeOverride ?: trackColours) else trackColours)`, so with a track open the scale follows *that* track's fill: flipping the selection gold hides it while other painted tracks stay banded, an eye-banded selection still raises it with `Colours` off, and with nothing selected the old fallback stands. The three KDocs that framed the control and the gate were re-argued with it (`TrackDrawerHeaderActions`, `legendVisibleFor`, `TrackSpeedLegend`'s visibility note); `apk-build.bat` SUCCESS and the scoped run steady at 130 with only the same five pre-existing reds, the flipped case pinned in `TrackRenderFlagsPathTest`, and the already-owed device pass now covering the new gate and glyph as well → `xTrack/Tracks/260917_FEAT_PLN_Tracks_render-axes-split.md`
- **ramp-step-recut (2026-09-19, `feature/someMui`)** — the nine families keep their ranges and their colours while their draw steps are re-cut for a jitter-free low range and a smooth high one: 1.0 across families 1 to 8 and 2.0 for family 9, so 0–13 kn drops from 19 painted shades to 7 while 13–70 kn rises from 17 to 38, about 47 in all, and the same ramp is left in place at 9–12 kn where the trade was counted. `HEATMAP_MAX_FAMILIES` rose 8 → 9, a bound the file had already outgrown — family 9 was never read, everything above 32 kn painted family 8's saturated top, and the block header beside the keys still said eight — and `AppConfig`'s default ramp plus both heatmap test classes were reconciled with the shipped grid, rewriting the properties test onto the file's ceilings, steps and colours and moving `TrackSpeedHeatmapTest`'s cap contract to family 9, which closed the two properties reds and left the known set at four. `apk-build.bat` SUCCESS with 213 scoped `ui.map` + `config` tests and exactly those four reds; the device pass over the ramp's jitter and its high-speed gradation is owed → `xTrack/Tracks/260919_FEAT_PLN_Tracks_ramp-step-recut.md`
- **track-position-filter (2026-09-19, `feature/someMui`)** — the tracks list gained a third filter axis, **All / On water / On land**, with a track holding both classified by the larger sampled count and an unclassifiable one counting as water: the counts ride `TrackSummary` at `@ProtoNumber(17/18)` behind a `-1` sentinel and the rule itself sits on the type (`positionIsWater`, water winning the tie), the pure `classifyTrackPosition()` samples exactly 32 points on an even spread with GAP markers skipped and the region checked against a neutral `RegionBounds`, and the sampling runs inside the index rebuild — which already decodes every track — carrying a summary's counts over for an unchanged `updatedAtEpochMs` so a single save never re-samples the library, with one guarded pass of its own for an index that is read rather than rebuilt and no cache for a sample the coastline cannot answer for; the water test is injected from MapScreen once the coastline is ready, through a new containment accessor that never calls open sea land beyond 6 NM; `TrackPositionTest` 9 cases green and `apk-build.bat` SUCCESS, the axis label left hardcoded English like its three siblings, and the repository's pass itself still without a test → `xTrack/Tracks/260919_FEAT_PLN_Tracks_position-filter.md`
