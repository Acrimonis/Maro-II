---
name: Tracks
status: active
created: 2026-06-15 21:43
modified: 2026-09-15 14:06
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
- [x] 3 · Selection cue once every rendered track is banded — closed 2026-09-15: the dark casing is removed on `feature/no-black-casing` and the widths became configuration the same afternoon, so the selected track is 12 px against the newest track's 10, on top of everything, gold in the two simple modes; the rim stays parked in the pinned-cue plan
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

- **Summary — closed 2026-09-15:** resolved by reading 4 — no dark stroke, and the map carries no new cue — with readings 1 and 2 void and reading 3 parked as the rim. The separation the casing used to carry moved into width, the per-type widths shipping the same afternoon, so the selected track is 12 px against the newest track's 10.

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
- **no-black-casing (2026-09-15, `feature/no-black-casing`)** — the selected track's 16f black casing is deleted: `SELECTED_CASING` and its uses on the gold and banded paths, the chevron casing in `TrackDirectionOverlay` with its stroke width and draw block, the `arrowCasingAppearance` field, and the banded path's now-unread `selected` flag; the per-type widths followed the same afternoon, ported from `2728c78` with the rim left behind — five `track.width.*` keys (12 / 12 / 10 / 8 / 6), the four width constants gone, the live line's nine sites and the gold core reading the keys, and the newest track derived from recency rather than the loop's index; `apk-build.bat` SUCCESS with the scoped track tests green and the three pre-existing reds untouched → `xTrack/Tracks/260914_FEAT_PLN_Tracks_pinned-cue-casing.md`
