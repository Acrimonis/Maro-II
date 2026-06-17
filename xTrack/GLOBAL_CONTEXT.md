# Global Context — Routing Table

## Active Session Pointers
- **Active Feature:** ColorManagement
- **Active Subfeature:** none
- **Last Updated:** 2026-06-17
- **Last Bake:** 2026-06-17 14:39 (Centralized all colors into colors.properties — fixed 46 hardcoded ComposeColor.White refs, synced 12 stale AppConfig defaults, consolidated zone.properties into maro.properties, added isobar width/color tokens)
- **Branch:** feature/more-colors
## Routing Map
| Keyword | Feature File |
|---|---|
| documentation, docs, readme, faq, setup, git_workflow, maro_architecture, plans | xTrack/Documentation/FEAT_DSC_Documentation.md |
| workflow, clinerules, xtrack, commands, memory, #doc, doccommands | xTrack/WorkflowImprovement/FEAT_DSC_WorkflowImprovement.md |
| zone300, zone, 300 | xTrack/Zone300/FEAT_DSC_Zone300.md |
| depth, bathymetry, depthmapping, baro, seafloor, soundings, litto3d, shom, emodnet | xTrack/DepthMapping/FEAT_DSC_DepthMapping.md |
| coastline, trait de côte, fourmigue, hazard, obstruction, balisage, danger_isole, aton, seamark, lighthouse, reef | xTrack/Coastline/FEAT_DSC_Coastline.md |
| dashboard, ui, layout, hud, display, screen | xTrack/Ui_Dashboard/FEAT_DSC_Ui_Dashboard.md |
| gps, gpsplugin, gps mode, demo mode, heading, course, compass, location, geolocation | xTrack/GPS/FEAT_DSC_GPS.md |
| mapdisplay, map display, map, layer, depth layer, color depth, orientation | xTrack/UI_Map/FEAT_DSC_UI_Map.md |
| performance, battery, gps-tune, adaptive, compass-gate, map-refresh, battery-drain | xTrack/Performance/FEAT_DSC_Performance.md |
| bake, baking, bake-script, bake-bat, apk-bake, apk-build, apk-deploy, deploy, prebake-pipeline, bake-env | xTrack/BakeNormalization/FEAT_DSC_BakeNormalization.md |
| depthsafety, depth-safety, danger-depth, shallow, grounding, isobar precision, isobath precision, depth alert, depth overlay, water-only | xTrack/DepthSafety/FEAT_DSC_DepthSafety.md |
| app-bak-flow, app-back-flow, back, back button, back handler, exit, double-back, press back, keep screen on, keep awake, screen-on, wakelock | xTrack/Ui_General/FEAT_DSC_Ui_General.md |
| settings, preferences, config, scroll, options | xTrack/Ui_Settings/FEAT_DSC_Ui_Settings.md |
| regulation, regulated zones, regulatedzone, regulation zone, speed zone, speed limit, anchoring, SHOM regulation, shom reg, maritime regulation, regulatory zone, réglementation maritime, zone réglementée, arrêté maritime, DIRM, cap d'antibes, lérins | xTrack/RegulatedZones/FEAT_DSC_RegulatedZones.md |
| arclayout, arc, arc-menu, layer-toggle, multi-btn, layer, toggle, fan-out | xTrack/ArcLayout/FEAT_DSC_ArcLayout.md |
| color, colour, color management, color-scheme, colors.properties, colour palette, colour scheme, colours, theme | xTrack/ColorManagement/FEAT_DSC_ColorManagement.md |
| zonetile, zone tile, zone info, zone ahead, zone cone, speed zone, speed zone display | xTrack/ZoneTile/FEAT_DSC_ZoneTile.md |
| zone300speed, 300m badge, speed badge, 300m speed | xTrack/Zone300SpeedBadge/FEAT_DSC_Zone300SpeedBadge.md |

## Feature Summaries

| Feature | One-Liner | Created | Modified | Status |
|---------|-----------|---------|----------|--------|
| **ColorManagement** | **Centralised colour palette — all tokens in colors.properties with alias interpolation, documented in color-scheme.md** | **2026-06-16 14:05** | **2026-06-17 14:39** | **active** |
| **Documentation** | **README, FAQs, setup guides, architecture docs, and plans cleanup** | **2026-06-11 06:42** | **2026-06-11 10:36** | **active** |
| WorkflowImprovement | xTrack #command system, git shortcuts, #now/#list rename, trigger syntax, fuzzy matching, bootstrap, and memory bake lifecycle | 2026-06-03 00:00 | 2026-06-17 14:28 | active |
| Zone300 | 300m zone generation from coastline with water-only constraint | 2026-05-20 00:00 | 2026-06-03 00:00 | active |
| DepthMapping | Bathymetry / depth mapping from Litto3D, SHOM, EMODnet sources | 2026-05-10 00:00 | 2026-06-10 12:13 | active |
| Coastline | Coastline extraction, spatial indexing, isOnWater determination | 2026-05-10 00:00 | 2026-06-05 00:00 | active |
| Ui_Dashboard | Main dashboard UI layout and HUD information display | 2026-05-15 00:00 | 2026-06-15 08:29 | active |
| GPS | GPS plugin with demo mode, heading/COG compass, geolocation | 2026-05-10 00:00 | 2026-06-11 14:00 | active |
| UI_Map | Map rendering, depth color layer, orientation overlay, boat marker offset | 2026-05-10 00:00 | 2026-06-14 19:39 | active |
| Performance | Battery optimization, adaptive GPS tuning, compass gate, map refresh | 2026-05-20 00:00 | 2026-06-05 00:00 | active |
| BakeNormalization | APK bake/build/deploy pipeline and prebake data processing | 2026-06-01 00:00 | 2026-06-05 00:00 | active |
| DepthSafety | Danger depth alerts, shallow water grounding prevention, isobath precision | 2026-06-03 00:00 | 2026-06-05 00:00 | active |
| Ui_General | App-lifecycle UX: back-to-exit guard, keep-screen-on wakelock, edge-to-edge rendering and WindowInsets management | 2026-06-08 16:43 | 2026-06-17 13:37 | active |
| Ui_General | App-lifecycle UX: back-to-exit guard, keep-screen-on wakelock, edge-to-edge rendering and WindowInsets management | 2026-06-08 16:43 | 2026-06-16 21:18 | active |
| Ui_Settings | Settings page UI, persistence, widgets, and UX enhancements | 2026-06-09 15:28 | 2026-06-09 19:42 | active |
| **Navigation** | **Navigation aids — heading/speed arrow and direction line on map overlay** | **2026-06-10 08:40** | **2026-06-11 11:55** | **active** |
| **RegulatedZones** | **Maritime regulatory zones — multi-source normalization (SHOM INSPIRE + IGN Natura 2000), sealed classification, 8-category icon mapping, keyword-driven display logic** | **2026-06-11 18:00** | **2026-06-12 22:37** | **active** |
| **ArcLayout** | **Layer toggle arc menu — pure-Compose semicircle fan-out with layer toggles, FanLayout framework, fixed child centering using effectiveTheta=180/currentCount** | **2026-06-13 07:34** | **2026-06-14 14:50** | **active** |
| **ZoneTile** | **Zone information tiles and map overlay rendering — zone-ahead cone/line, speed zone display, ETA, zone state management** | **2026-06-17 09:45** | **2026-06-17 09:45** | **active** |
| **Zone300SpeedBadge** | **300m zone speed limit badge integrated into regulated zone icon stack as highest-priority SPEED_LIMIT entry** | **2026-06-14 17:42** | **2026-06-14 17:42** | **done** |

## Global Rules
- Avoid PowerShell commands; use Windows CMD commands (e.g., `del` not `Remove-Item`, `dir` not `ls`).
- `adb.exe` is in the computer PATH — use `adb` directly without full path qualifier.
- Auto-refine rule wording for clarity and conciseness on `#rule` add.
- Use apk-build.bat to build APK (runs gradlew assembleDebug).
- **NO auto git commit/push** — `git add` only when directed; wait for user to say "commit" or "push" before executing those commands (per AGENTS.md §5).
- **Do not switch to Code mode to implement until explicitly instructed.** Discuss/design in Architect mode first, wait for user's go-ahead before switching.

## Global Todos
- [ ] Validate the intermittent Overpass-outage theory — confirm the coastline OSM fetch failures are transient (succeeded 13:52, failing ~16:52 on 2026-06-08), not a persistent network / cert / IPv6 block. Quick checks: retry `bake-coastline` later; `curl -sk https://overpass-api.de/api/status`; race other mirrors.
- [x] **Classify all `plans/*.md` by target feature (routing map)** — completed during Documentation feature
- [ ] **Change direction arrow color by speed compliance** — arrow in heading-ahead display (↑/↗→/→) should reflect speed-vs-limit ratio: green ≤ limit, orange ≤ limit×1.4, red > limit×1.4

## Always-Loaded Context
These files are loaded into context at the start of every session to maximize the AI prefix-cache hit rate:
- `AGENTS.md` — canonical rulebook (all project rules + xTrack §7a/7b command spec)
- `xTrack/GLOBAL_CONTEXT.md` — this file (routing table, feature summaries, global todos, global rules)
- `.claude/skills/xtrack/SKILL.md` — skill dispatch map

## Global Instructions
- The xTrack `#`-command system is the canonical workflow. Use it for all feature tracking, todo/rule management, doc management, and session snapshots.
- On Turn 1 of any session: read GLOBAL_CONTEXT.md, match user intent against the Routing Map, open the corresponding feature file and its hydration file.
- Route docs, key files, and todos to the correct feature scope. Keep feature files lean — use ## Docs for references, ## Key Files for source paths.
- `docs/cmd_help.md`, `references/fuzzy-resolve.md`, and `references/templates.md` are lazy-loaded on `#help` only — not loaded every session.
