# Global Context — Routing Table

## Active Session Pointers
- **Active Feature:** Ui_General
- **Active Subfeature:** notification-lifecycle
- **Last Updated:** 2026-08-15 14:13
- **Last Bake:** 2026-08-15 14:13 (Ui_General — notification-lifecycle: notification follows recording state, 3-choice exit dialog, service-owned recorder, startup NPE fix)
- **Branch:** feature/notification

## Routing Map
| Keyword | Feature File |
|---|---|
| | documentation, docs, readme, faq, setup, git_workflow, maro_architecture, plans | xTrack/Documentation/FEAT_DSC_Documentation.md |
| | workflow, clinerules, xtrack, commands, memory, #doc, doccommands | xTrack/WorkflowImprovement/FEAT_DSC_WorkflowImprovement.md |
| | zone300, zone, 300 | xTrack/Zone300/FEAT_DSC_Zone300.md |
| | depth, bathymetry, depthmapping, baro, seafloor, soundings, litto3d, shom, emodnet | xTrack/DepthMapping/FEAT_DSC_DepthMapping.md |
| | coastline, trait de côte, fourmigue, hazard, obstruction, balisage, danger_isole, aton, seamark, lighthouse, reef | xTrack/Coastline/FEAT_DSC_Coastline.md |
| | dashboard, ui, layout, hud, display, screen | xTrack/Ui_Dashboard/FEAT_DSC_Ui_Dashboard.md |
| | gps, gpsplugin, gps mode, demo mode, heading, course, compass, location, geolocation | xTrack/GPS/FEAT_DSC_GPS.md |
| | mapdisplay, map display, map, layer, depth layer, color depth, orientation | xTrack/UI_Map/FEAT_DSC_UI_Map.md |
| | performance, battery, gps-tune, adaptive, compass-gate, map-refresh, battery-drain | xTrack/Performance/FEAT_DSC_Performance.md |
| | bake, baking, bake-script, bake-bat, apk-bake, apk-build, apk-deploy, deploy, prebake-pipeline, bake-env | xTrack/BakeNormalization/FEAT_DSC_BakeNormalization.md |
| | depthsafety, depth-safety, danger-depth, shallow, grounding, isobar precision, isobath precision, depth alert, depth overlay, water-only | xTrack/DepthSafety/FEAT_DSC_DepthSafety.md |
| | app-bak-flow, app-back-flow, back, back button, back handler, exit, double-back, press back, keep screen on, keep awake, screen-on, wakelock | xTrack/Ui_General/FEAT_DSC_Ui_General.md |
| | settings, preferences, config, scroll, options | xTrack/Ui_Settings/FEAT_DSC_Ui_Settings.md |
| | regulation, regulated zones, regulatedzone, regulation zone, speed zone, speed limit, anchoring, SHOM regulation, shom reg, maritime regulation, regulatory zone, réglementation maritime, zone réglementée, arrêté maritime, DIRM, cap d'antibes, lérins | xTrack/RegulatedZones/FEAT_DSC_RegulatedZones.md |
| | arclayout, arc, arc-menu, layer-toggle, multi-btn, layer, toggle, fan-out | xTrack/ArcLayout/FEAT_DSC_ArcLayout.md |
| | color, colour, color management, color-scheme, colors.properties, colour palette, colour scheme, colours, theme | xTrack/ColorManagement/FEAT_DSC_ColorManagement.md |
| | zonetile, zone tile, zone info, zone ahead, zone cone, speed zone, speed zone display | xTrack/ZoneTile/FEAT_DSC_ZoneTile.md |
| | zone300speed, 300m badge, speed badge, 300m speed | xTrack/Zone300SpeedBadge/FEAT_DSC_Zone300SpeedBadge.md |
| | boat, trace, trip, boat-trace, boat-tracing, track, recording, port-salis, journey | xTrack/BoatTrace/FEAT_DSC_BoatTrace.md |
| | workflow, rules, ambiguity, merge, agents, gitops | xTrack/WorkflowAmbiguityFix/FEAT_DSC_WorkflowAmbiguityFix.md |
| | markers, pin, circle, corridor, usermarker, user marker, where am i | xTrack/Markers/FEAT_DSC_Markers.md |
| | menu, menu drawer, hamburger, track drawer, position source, menu overlay | xTrack/Ui_Menu/FEAT_DSC_Ui_Menu.md |

## Feature Summaries

| Feature | One-Liner | Created | Modified | Status |
|---------|-----------|---------|----------|--------|
| | **ColorManagement** | **Centralised colour palette — all tokens in colors.properties with alias interpolation, documented in color-scheme.md** | **2026-06-16 14:05** | **2026-06-17 14:39** | **active** |
| | **Documentation** | **README, FAQs, setup guides, architecture docs, and plans cleanup** | **2026-06-11 06:42** | **2026-07-17 13:30** | **active** |
| | WorkflowImprovement | xTrack #command system, git shortcuts, mode handoff protocol, hard rules enforcement, AGENTS.md trunk-to-leaf optimization, #merge hybrid strategy, and spec consolidation. Absorbed WorkflowAmbiguityFix 2026-06-28. | 2026-06-03 00:00 | 2026-06-28 16:10 | active |
| | Zone300 | 300m zone generation from coastline with water-only constraint | 2026-05-20 00:00 | 2026-06-03 00:00 | active |
| | DepthMapping | Bathymetry / depth mapping from Litto3D, SHOM, EMODnet sources | 2026-05-10 00:00 | 2026-06-10 12:13 | active |
| | Coastline | Coastline extraction, spatial indexing, isOnWater, hazard rings, unified data store; eastern bound extended to Menton (7.55°E), single region ID via BuildConfig | 2026-05-10 00:00 | 2026-06-23 07:08 | active |
| | Ui_Dashboard | Main dashboard UI layout and HUD information display | 2026-05-15 00:00 | 2026-06-15 08:29 | active |
| | GPS | GPS plugin with demo mode, heading/COG compass, geolocation | 2026-05-10 00:00 | 2026-08-08 15:24 | active |
| | UI_Map | Map rendering, depth color layer, orientation overlay, boat marker offset | 2026-05-10 00:00 | 2026-06-14 19:39 | active |
| | Performance | Battery optimization, adaptive GPS tuning, compass gate, map refresh | 2026-05-20 00:00 | 2026-06-05 00:00 | active |
| | BakeNormalization | APK bake/build/deploy pipeline and prebake data processing | 2026-06-01 00:00 | 2026-06-05 00:00 | active |
| | DepthSafety | Danger depth alerts, shallow water grounding prevention, isobath precision | 2026-06-03 00:00 | 2026-06-05 00:00 | active |
| | Ui_General | App-lifecycle UX: back-to-exit guard, keep-screen-on wakelock, edge-to-edge rendering, WindowInsets, list normalization (ListOverlayScaffold, sort, swipe-to-delete, per-type custom sort fields, localized EN+FR), list-detail navigation (scroll preservation, prev/next, exit conditions), drawer delete undo (snackbar + undo + reopen), notification lifecycle (foreground notification follows recording state, 3-choice exit dialog, service-owned recorder) | 2026-06-08 16:43 | 2026-08-15 14:13 | active |
| | Ui_Settings | Settings page UI, persistence, widgets, and UX enhancements | 2026-06-09 15:28 | 2026-06-18 19:10 | active |
| | **Navigation** | **Navigation aids — heading/speed arrow and direction line on map overlay** | **2026-06-10 08:40** | **2026-06-11 11:55** | **active** |
| | **RegulatedZones** | **Maritime regulatory zones — multi-source normalization (SHOM INSPIRE + IGN Natura 2000), sealed classification, 8-category icon mapping, keyword-driven display logic** | **2026-06-11 18:00** | **2026-06-12 22:37** | **active** |
| | **ArcLayout** | **Layer toggle arc menu — pure-Compose semicircle fan-out with layer toggles, FanLayout framework, fixed child centering using effectiveTheta=180/currentCount** | **2026-06-13 07:34** | **2026-06-14 14:50** | **active** |
| | **ZoneTile** | **Zone information tiles and map overlay rendering — zone-ahead cone/line, speed zone display, ETA, zone state management** | **2026-06-17 09:45** | **2026-06-17 09:45** | **active** |
| | **Zone300SpeedBadge** | **300m zone speed limit badge integrated into regulated zone icon stack as highest-priority SPEED_LIMIT entry** | **2026-06-14 17:42** | **2026-06-14 17:42** | **done** |
| | **BoatTrace** | **Boat movement tracking: record tracks from GPS fixes, persist, display on map with configurable render layers, GPX export, track history UI, render preview indicator, idle-period marker snapshots (BoatMarker), auto-marker 🕐 pins at idle spots with transparency** | **2026-06-15 21:43** | **2026-08-08 16:01** | **active** |
| | **CheckDev** | **Dev-branch health monitoring — remote branch state, ahead/behind analysis, workflow hygiene validation** | **2026-06-20 11:42** | **2026-06-20 11:42** | **active** |
| | **Health** | **Application health monitoring — diagnostics, crash reporting, memory/performance telemetry** | **2026-06-20 11:42** | **2026-06-20 11:42** | **active** |
| | **WorkflowAmbiguityFix** | **Eliminate ambiguity between AGENTS.md rules — #merge direction, §5 reinforcement, command doc alignment. Absorbed into WorkflowImprovement 2026-06-28.** | **2026-06-20 14:51** | **2026-06-28 13:51** | **done** |
| | **Markers** | **User-defined map markers (Pin, Circle, Corridor) with sea-distance-gated proximity matching, percentage-based sort scoring, and on-demand "where am I?" query — 14/14 subfeatures done; 7 fixes + sort v2 implemented** | **2026-06-22 11:52** | **2026-06-30 10:25** | **active** |
| | **Ui_Menu** | **Hamburger menu drawer — position source, track recording, marker management sections; right-side sliding panel via OverlayLayer/DrawerSlot** | **2026-07-05 06:57** | **2026-07-18 05:54** | **active** |

## Global Rules
- Avoid PowerShell commands; use Windows CMD commands (e.g., `del` not `Remove-Item`, `dir` not `ls`).
- `adb.exe` is in the computer PATH — use `adb` directly without full path qualifier.
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
| | `color-scheme.md` | ColorManagement | Color tokens, palette, alias chains |
| | `settings-page-guidelines.md` | Ui_Settings | Settings page UI patterns and layout rules |
| | `material-icons-standalone-guide.md` | Ui_General | How to add Material Symbols icons as standalone ImageVector .kt files |

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
