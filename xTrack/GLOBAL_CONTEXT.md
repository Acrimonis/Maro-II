# Global Context — Routing Table

## Focus History
- [2026-09-11 12:41 UTC] Ui_Settings — row family normalization R1–R7 on feature/settings-menu-clean: R3–R5 (e954a48) + R6 (1e6a8d3) committed; R7 done and device-validated (Card-owned horizontal inset, 14 inset-only wrappers collapsed → Language picker inset fixed; SectionDivider drops its own inset; CategoryToggleGroup on ToggleRow.leadingIcon in RegulatedZoneComponents.kt; grep invariant = uiPaddingCardHorizontal exactly 2; build SUCCESS, no new warnings; file 2415→2014 lines); R8 docs+tracking pending; backlog logged for sub-section title colour (Option A) → xTrack/Ui_Settings/FEAT_HYD_Ui_Settings.md
- [2026-09-11 08:47 UTC] Ui_Settings — **phase 1 COMPLETE** on feature/settings-menu-clean: sentence-case 18sp section titles (SectionHeader `uppercase` removed), SettingsToggleRow → box-less ToggleRowContent, tab strip = M3 SecondaryScrollableTabRow + custom content-sized cells (18sp SemiBold, full-cell indicator, ui.font.tab.size token), and a unified spacing rhythm (section gap 24→14dp, title→card 8→6dp; Layers' 12dp card gaps were the outliers); guideline §1/§2.1/§2.3/§2.4/§2.5/§2.6/§2.9/§3/§4 rewritten + new §2.11 tab-strip spec; builds SUCCESS, validated on device; commit 0b01d1e + this pass → xTrack/Ui_Settings/FEAT_HYD_Ui_Settings.md
- [2026-09-11 06:13 UTC] Ui_Menu — session branch feature/settings-menu-clean created from origin/develop → xTrack/Ui_Menu/FEAT_HYD_Ui_Menu.md
- [2026-09-10 12:21 UTC] Ui_Settings — C12 OverlayLayer param collapse COMPLETE on feature/refact-C12: all six bundles (OverlayChrome 7, MenuOverlayData 12, SettingsOverlayData 5, TrackInfoOverlayData 4, TrackListOverlayData 3, MarkerListOverlayData 4) in new OverlayLayerParams.kt; OverlayLayer 89→60 params; 35-line destructure keeps the body unchanged; dead defaults + orphan imports removed; builds SUCCESS zero warnings every tier; Ask 8/8 + 8/8; ~30 stale doc anchors repaired; on-device functional test PASSED; 3 commits (ec57458, a000c18, 96259b5) awaiting push + PR → xTrack/Ui_Settings/FEAT_HYD_Ui_Settings.md
- [2026-09-10 06:02 UTC] Ui_Settings — MapScreen step-2 follow-up on feature/mapscreen-refactor: cleanup (8 seam composables → internal, dead onTrackMetadataChanged removed, redundant demo-feed guard removed, 17 unused imports) + 2 pre-existing warnings fixed (LocalLifecycleOwner → androidx.lifecycle.compose; always-true focus condition) + docs synced (maro-code.md MapScreen Decomposition, ui-drawer-guidelines SettingsOverlay path); build SUCCESS zero warnings; C12 OverlayLayer Tier-1 pending → xTrack/Ui_Settings/FEAT_HYD_Ui_Settings.md
- [2026-09-09 16:48 UTC] Ui_Settings — MapScreen orchestration-monolith refactor step 2 on feature/mapscreen-refactor: C1–C11 extracted (MapGpsFollow/MapTrackOverlay/MapMarker/MapService/MapDepthRaster/MapDialogHost/MapSnackbarHost/MapImportConflictHost seam files); MapScreen.kt 3506→~2560; Ask code-health review SOUND (commit 75ed009 = C1–C9, this commit = C10–C11); C12 OverlayLayer collapse pending (Tier 1 after smoke test) → xTrack/Ui_Settings/FEAT_HYD_Ui_Settings.md
- [2026-09-09 12:18 UTC] Ui_General — filters-link decoupling IMPLEMENTED on feature/filters-link: List/Map filter referentials + Link/LinkOff toggle; commits b4693df..48bb560; Ask reviewed; build SUCCESS → xTrack/Ui_General/FEAT_HYD_Ui_General.md
- [2026-09-09 10:06 UTC] Ui_Settings — MapScreen settings extraction: moved the Settings overlay subtree out of MapScreen.kt (5841→3417) into same-package MapScreenSettingsOverlay.kt; zero behavior change; build SUCCESS; committed b71a230 on feature/filters-link → xTrack/Ui_Settings/FEAT_HYD_Ui_Settings.md
- [2026-09-09 09:53 UTC] Ui_General — filters-link List/Map filter decoupling: design locked + Ask review passed; partial foundation committed on feature/filters-link b4693df (settings Option B no-versioning + marker map stream + Link/LinkOff icons); remaining UI split pending → xTrack/Ui_General/FEAT_HYD_Ui_General.md
- [2026-09-08 17:18 UTC] Ui_Menu — hamburger menu drawer; focus on feature/menu-render-upt → xTrack/Ui_Menu/FEAT_HYD_Ui_Menu.md

## Routing Map
| Keyword | Feature File |
|---|---|
| documentation, docs, readme, faq, setup, git_workflow, maro_architecture, plans | xTrack/Documentation/FEAT_DSC_Documentation.md |
| workflow, clinerules, xtrack, commands, memory, #doc, doccommands | xTrack/WorkflowImprovement/FEAT_DSC_WorkflowImprovement.md |
| zone300, 300, bande 300m, water-only | xTrack/Coastline/FEAT_DSC_Coastline.md |
| depth, bathymetry, depthmapping, baro, seafloor, soundings, litto3d, shom, emodnet | xTrack/DepthMapping/FEAT_DSC_DepthMapping.md |
| coastline, trait de côte, fourmigue, hazard, obstruction, balisage, danger_isole, aton, seamark, lighthouse, reef | xTrack/Coastline/FEAT_DSC_Coastline.md |
| dashboard, ui, layout, hud, display, screen | xTrack/Ui_Dashboard/FEAT_DSC_Ui_Dashboard.md |
| gps, gpsplugin, gps mode, demo mode, heading, course, compass, location, geolocation | xTrack/GPS/FEAT_DSC_GPS.md |
| mapdisplay, map display, map, layer, depth layer, color depth, orientation | xTrack/UI_Map/FEAT_DSC_UI_Map.md |
| performance, battery, gps-tune, adaptive, compass-gate, map-refresh, battery-drain | xTrack/Performance/FEAT_DSC_Performance.md |
| bake, baking, bake-script, bake-bat, apk-bake, apk-build, apk-deploy, deploy, prebake-pipeline, bake-env | xTrack/BakeNormalization/FEAT_DSC_BakeNormalization.md |
| depthsafety, depth-safety, danger-depth, shallow, grounding, isobar precision, isobath precision, depth alert, depth overlay, water-only | xTrack/DepthSafety/FEAT_DSC_DepthSafety.md |
| app-bak-flow, app-back-flow, back, back button, back handler, exit, double-back, press back, keep screen on, keep awake, screen-on, wakelock | xTrack/Ui_General/FEAT_DSC_Ui_General.md |
| filters-link, filter link, linked filters, decouple list map filter, map filter referential | xTrack/Ui_General/FEAT_DSC_Ui_General.md |
| settings, preferences, config, scroll, options | xTrack/Ui_Settings/FEAT_DSC_Ui_Settings.md |
| regulation, regulated zones, regulatedzone, regulation zone, speed zone, speed limit, anchoring, SHOM regulation, shom reg, maritime regulation, regulatory zone, réglementation maritime, zone réglementée, arrêté maritime, DIRM, cap d'antibes, lérins | xTrack/RegulatedZones/FEAT_DSC_RegulatedZones.md |
| arclayout, arc, arc-menu, layer-toggle, multi-btn, layer, toggle, fan-out | xTrack/ArcLayout/FEAT_DSC_ArcLayout.md |
| color, colour, color management, color-scheme, colors.properties, colour palette, colour scheme, colours, theme | xTrack/ColorManagement/FEAT_DSC_ColorManagement.md |
| zonetile, zone tile, zone info, zone ahead, zone cone, speed zone, speed zone display | xTrack/ZoneTile/FEAT_DSC_ZoneTile.md |
| boat, trace, trip, boat-trace, boat-tracing, track, recording, port-salis, journey | xTrack/BoatTrace/FEAT_DSC_BoatTrace.md |
| markers, pin, circle, corridor, usermarker, user marker, where am i | xTrack/Markers/FEAT_DSC_Markers.md |
| menu, menu drawer, hamburger, track drawer, position source, menu overlay | xTrack/Ui_Menu/FEAT_DSC_Ui_Menu.md |
| navigation, nav, heading arrow, direction arrow, speed arrow, cap arrow, course arrow, auto-show, auto show, redisplay on approach, zone reveal, demo auto-show | xTrack/Navigation/FEAT_DSC_Navigation.md |
| checkdev, dev branch, branch health, ahead behind, workflow hygiene | xTrack/CheckDev/FEAT_DSC_CheckDev.md |
| health, diagnostics, crash reporting, telemetry, memory monitoring | xTrack/Health/FEAT_DSC_Health.md |

## Feature Summaries

| Feature | One-Liner | Created | Modified | Status |
|---------|-----------|---------|----------|--------|
| **ColorManagement** | **Centralised colour palette — all tokens in colors.properties with alias interpolation, documented in color-scheme.md** | **2026-06-16 14:05** | **2026-09-04 22:36** | **active** |
| **Documentation** | **README, FAQs, setup guides, architecture docs, and plans cleanup** | **2026-06-11 06:42** | **2026-09-04 22:36** | **active** |
| WorkflowImprovement | xTrack #command system, git shortcuts, mode handoff protocol, hard rules enforcement, AGENTS.md trunk-to-leaf optimization, #merge hybrid strategy, spec consolidation, and process simplification (WRITE-ONCE guideline, section model, Focus History, Implemented pointer-index + per-feature bake sweep, live-section trim). Absorbed WorkflowAmbiguityFix 2026-06-28. | 2026-06-03 00:00 | 2026-09-04 22:36 | active |
| DepthMapping | Bathymetry / depth mapping from Litto3D, SHOM, EMODnet sources | 2026-05-10 00:00 | 2026-09-04 22:36 | active |
| Coastline | Coastline extraction, spatial indexing, isOnWater, hazard rings, unified data store; eastern bound extended to Menton (7.55°E), single region ID via BuildConfig | 2026-05-10 00:00 | 2026-06-23 07:08 | active |
| Ui_Dashboard | Main dashboard UI layout and HUD information display | 2026-05-15 00:00 | 2026-09-04 22:36 | active |
| GPS | GPS plugin with demo mode, heading/COG compass, geolocation, auto-follow spring-back hold | 2026-05-10 00:00 | 2026-09-04 22:36 | active |
| UI_Map | Map rendering, depth color layer, orientation overlay, boat marker offset; deterministic overlay z-order (tile→base→tracks→markers) via OverlayZOrder.reorder; marker filter drives map overlay (map renders from filtered list, panel auto-closes when filtered out); menu/fan open closes marker+track dashboard; list-context stacking removed (close returns to map, Prev/Next navigates) | 2026-05-10 00:00 | 2026-09-04 22:36 | active |
| Performance | Battery optimization, adaptive GPS tuning, compass gate, map refresh | 2026-05-20 00:00 | 2026-09-04 22:36 | active |
| BakeNormalization | APK bake/build/deploy pipeline and prebake data processing | 2026-06-01 00:00 | 2026-09-04 22:36 | active |
| DepthSafety | Danger depth alerts, shallow water grounding prevention, isobath precision | 2026-06-03 00:00 | 2026-09-04 22:36 | active |
| Ui_General | App-lifecycle UX: back-to-exit guard, keep-screen-on wakelock, edge-to-edge rendering, WindowInsets, list normalization (ListOverlayScaffold, sort, swipe-to-delete, per-type custom sort fields, localized EN+FR), list-detail navigation (scroll preservation, prev/next, exit conditions), drawer delete undo (snackbar + undo + reopen), notification lifecycle (foreground notification follows recording state, 3-choice exit dialog, service-owned recorder), landscape menu drawer (scroll-when-overflow, overscroll suppressed when content fits), top-left icons (GPS→tracking→land/water, GPS click-to-toggle, 🐾, idle dot red), menu drawer rows (Tracks/Markers captions, chevron opens first filtered/sorted item, disabled when none), screen-lock splash guard (📵 toggle, double-tap zoom, status.lock.* tokens); compact track/marker list cards (desc/comment lineHeight 14sp, reduced v-padding, header→title separator removed); drawer footer pinning + dynamic animated height (card-probe); drawer vertical rhythm (12dp card padding, header vertical padding 12dp, footer 10dp rhythm); landscape drawer/settings sizing (portrait width × ui.landscape.panel.widthScale 1.2, menu 75%); scrim matrix (0.50 menu/settings/lists, none detail+wizard, transparent fan/lock); dashboard close (back/menu/create close, fan keeps open); list-count-display (Track History title "· N" + menu Tracks/Markers counts left of chevron, live track excluded); marker-filter-remove-geometry (markers filter Geometry axis removed; icon/pinned/origin remain, origin ungated); track-filter-date-range (Track Date Range filter extended+reordered to 7 options Last week→Last 6 month with All last + default, THIS_YEAR dropped) | 2026-06-08 16:43 | 2026-09-07 15:36 | active |
| Ui_Settings | Settings page UI — 4 tabs; Re-display on approach section (global GPS/demo + per-type switches + always-visible knobs); per-zone proximity reveal; drawer Position mode + Auto-show zones master switch; tab navigation swipe disabled + per-page slide spacing fixed; Card/Expander/NestedCard structural refactor (SettingsSliderGroup/Row deprecated); SectionDivider normalization; GPS freq/recenter shared NestedCard; component/drawer/lists guideline consolidation; marker "Belongs to track" bottom row + chevron (list + detail drawer); global opacity→transparency normalization (0=opaque,100=invisible; "Border X% · Fill Y%" two-thumb) + low-depth 2-depth crash/start warning redesign; ui-*guidelines docs aligned; properties-normalization (ui-tokens→ui.properties wired into AppConfig with typed accessors, 6 keys re-homed across colors/ui/maro, settings composables tokenized); header-normalization (Settings overlay header → shared DrawerHeader, no actions; leftover 16dp header→tab-bar spacer removed; post-header spacing normalized to single 6dp gap across all drawers via ui.padding.header.vertical token; detail-drawer header fix: back button IconButton→32dp circle to stop min-size overflow clip, square top corners on Marker/Track drawers, ~6dp title→card gap; portrait Track drawer tiny-scroll fix via render-measure-resize Prev/Next footer; guideline §6/§12 + portrait drawer height floor updated; finalise-feature (all remaining sections closed: render-tweaks already-implemented, header-normalization merged PR #217, apply-on-close out-of-scope; guideline docs verified in sync); C12 OverlayLayer parameter-object collapse complete (six @Immutable bundles — OverlayChrome 7, MenuOverlayData 12, SettingsOverlayData 5, TrackInfoOverlayData 4, TrackListOverlayData 3, MarkerListOverlayData 4 — in new OverlayLayerParams.kt; 89→60 params; 35-line destructure leaves the body unchanged; dead list-state defaults + orphan imports removed; ~30 stale doc anchors repaired); row-family normalization R3-R5 (generic surface-free SegmentedRow + optional captions, GPS frequency picker, CardDescription + 6 migrations; R6 inline-row/dividers conversion (13 rows -> ToggleRow, ToggleRow.leadingIcon, 3 dividers -> SectionDivider); R7 padding normalization (Card-owned inset, 14 wrappers collapsed, SectionDivider inset dropped, CategoryToggleGroup on ToggleRow + leadingIcon) on feature/refact-C12 | 2026-06-09 15:28 | 2026-09-11 12:41 | active |
| **Navigation** | **Navigation aids — heading/speed arrow and direction line on map overlay; auto-show / re-display of regulated zones + 300 m band on approach (demo + GPS)** | **2026-06-10 08:40** | **2026-09-08 16:49** | **active** |
| **RegulatedZones** | **Maritime regulatory zones — multi-source normalization (SHOM INSPIRE + IGN Natura 2000), sealed classification, 8-category icon mapping, keyword-driven display logic** | **2026-06-11 18:00** | **2026-09-04 22:36** | **active** |
| **ArcLayout** | **Layer toggle arc menu — pure-Compose semicircle fan-out with layer toggles, FanLayout framework, fixed child centering using effectiveTheta=180/currentCount; control-stack button swap (Add Zone above fan)** | **2026-06-13 07:34** | **2026-09-06 21:19** | **active** |
| **ZoneTile** | **Zone information tiles and map overlay rendering — zone-ahead cone/line, speed zone display, ETA, zone state management** | **2026-06-17 09:45** | **2026-09-02 21:35** | **active** |
| **BoatTrace** | **Boat movement tracking: record tracks from GPS fixes, persist, display on map with configurable render layers, GPX export, track history UI, render preview indicator, idle-period marker snapshots (BoatMarker), auto-marker 🕐 pins at idle spots with transparency, service GPS re-arm + permission-missing dialog, service GPS sampling pinned to Main dispatcher (Looper fix), confirm before switching position source while recording; finalize durability (atomic save + transactional stop), data-derived end/idle/nav (simplified-points + gap-idle classification), stats recompute migration (schema 4); auto-marker cleanup hardening (recorder-owned AutoMarkerManager lifecycle, merged-marker keepability, ghost-pin fix); marker-track single reference (UserMarker.trackId + backfill + delete cascade); track export naming + import modes (Skip/Update/New); direction arrows (chevrons, speed-based density)** | **2026-06-15 21:43** | **2026-09-04 22:36** | **active** |
| **CheckDev** | **Dev-branch health monitoring — remote branch state, ahead/behind analysis, workflow hygiene validation** | **2026-06-20 11:42** | **2026-06-20 11:42** | **active** |
| **Health** | **Application health monitoring — diagnostics, crash reporting, memory/performance telemetry** | **2026-06-20 11:42** | **2026-06-20 11:42** | **active** |
| **Markers** | **User-defined map markers (Pin, Circle, Corridor) with sea-distance-gated proximity matching, percentage-based sort scoring, and on-demand "where am I?" query — icon/pin decoupling implemented (icon pure POI, pin real persisted flag mirroring tracks); pin-halo-rendering implemented (static halo ring pinned white/unpinned light-blue, corridor always-on line + under-line halo, selected-marker gold via selectedMarkerId, focus zoom-to-fit, MarkerAppearance/MarkerHalo split)** | **2026-06-22 11:52** | **2026-09-05 10:42** | **active** |
| **Ui_Menu** | **Hamburger menu drawer — position source, track recording, marker management sections; right-side sliding panel via OverlayLayer/DrawerSlot; track/marker action normalization (shared list/detail card, double-click inline edit, chevron tappable gutter, delete = swipe/header-trash); menu-render-upt (Settings-theme normalization) planned** | **2026-07-05 06:57** | **2026-09-09 06:52** | **active** |

## Global Rules
- Avoid PowerShell commands; use Windows CMD commands (e.g., `del` not `Remove-Item`, `dir` not `ls`).
- `adb.exe` is in the computer PATH — use `adb` directly without full path qualifier.
- **🔴 LOGCAT WORKFLOW:** Debug that needs on-device logcat → ask the user to deploy + perform the operation; fetch logcat only after the user tells you to. Do not deploy/capture on a device unprompted.
- Auto-refine rule wording for clarity and conciseness on `#rule` add.
- Use apk-build.bat to build APK (runs gradlew assembleDebug).
- **Git operations allowed on `#`-command invocation** — `#commit`, `#push`, `#merge`, `#checkout` are self-contained confirmations; the command invocation is the go-ahead. Never touch `develop`/`main` branches.
- **🔴 MODE LOCK: Do not switch to Code mode or invoke `#implement` pipeline without explicit user go-ahead (`#implement` tag or "go ahead" / "implement now").** Never suggest "ready for #implement" — it implies permission. Architect mode stays in Architect until user explicitly directs otherwise. See AGENTS.md Core Directives for full mode permissions.
- **🔴 QUESTIONS: Answer before acting.** A question is not an implicit implementation order. Answer it first, then wait for direction.
- **Auto-switch for commands:** When a task requires executing shell commands (git, gradlew, adb) and the current mode lacks terminal access, automatically `switch_mode("code")` to run the command, then switch back to the original mode.
- **🔴 PLAN FILE PLACEMENT: All `FEAT_PLN_*.md`, `FEAT_DOC_*.md`, and feature-scoped design files MUST be created in `xTrack/[Feature]/` — NEVER in `plans/`.** See AGENTS.md §7a. `plans/` is a legacy directory; new files go directly to the feature's xTrack subdirectory with proper naming.

## Global Todos
- [ ] Validate the intermittent Overpass-outage theory — confirm the coastline OSM fetch failures are transient (succeeded 13:52, failing ~16:52 on 2026-06-08), not a persistent network / cert / IPv6 block. Quick checks: retry `bake-coastline` later; `curl -sk https://overpass-api.de/api/status`; race other mirrors.
- [x] **Classify all `plans/*.md` by target feature (routing map)** — completed during Documentation feature
- [ ] **Change direction arrow color by speed compliance** — arrow in heading-ahead display (↑/↗→/→) should reflect speed-vs-limit ratio: green ≤ limit, orange ≤ limit×1.4, red > limit×1.4

## Cross-Reference Docs
Docs available via `#doc read [name]` from any feature. Fuzzy-resolve searches this table.

| Doc | Owner Feature | One-Liner |
|-----|---------------|-----------|
| `color-scheme.md` | ColorManagement | Color tokens, palette, alias chains |
| `material-icons-standalone-guide.md` | Ui_General | How to add Material Symbols icons as standalone ImageVector .kt files |

## Always-Loaded Context
These files are loaded into context at the start of every session to maximize the AI prefix-cache hit rate:
- `AGENTS.md` — canonical rulebook (all project rules + xTrack §7a/7b command spec)
- `xTrack/GLOBAL_CONTEXT.md` — this file (routing table, feature summaries, global todos, global rules)
- `.claude/skills/xtrack/SKILL.md` — skill dispatch map

## Global Instructions
- The xTrack `#`-command system is the canonical workflow. Use it for all feature tracking, todo/rule management, doc management, and session snapshots.
- On Turn 1: read GLOBAL_CONTEXT.md, match intent against Routing Map, open matching feature file + hydration. No match → ask scoping question.
- Route docs/key files/todos to correct feature scope. Keep feature files lean — `## Docs` for references, `## Key Files` for source paths.
- Reference docs are lazy-loaded per [`AGENTS.md` Lazy-Load Index](AGENTS.md).

