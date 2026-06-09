# Global Context — Routing Table

## Active Session Pointers
- **Active Feature:** WorkflowImprovement
- **Active Subfeature:** none
- **Last Updated:** 2026-06-09
- **Last Bake:** 2026-06-09 14:52 (WorkflowImprovement — xTrack reorganization: all 55 FEAT_* files moved into 16 feature subdirectories; AGENTS.md, cmd_help.md, GLOBAL_CONTEXT.md, 7 FEAT_DSC_ ## Docs sections, taxonomy table all updated)

## Routing Map
| Keyword | Feature File |
|---|---|
| workflow, clinerules, xtrack, commands, memory, #doc, doccommands | xTrack/WorkflowImprovement/FEAT_DSC_WorkflowImprovement.md |
| documentation, docs, readme, project | xTrack/ProjectDocumentation/FEAT_DSC_ProjectDocumentation.md |
| zone300, zone, 300 | xTrack/Zone300/FEAT_DSC_Zone300.md |
| codereview, code review, review, quality, lint | xTrack/CodeReview/FEAT_DSC_CodeReview.md |
| depth, bathymetry, depthmapping, baro, seafloor, soundings, litto3d, shom, emodnet | xTrack/DepthMapping/FEAT_DSC_DepthMapping.md |
| coastline, trait de côte, fourmigue, hazard, obstruction, balisage, danger_isole, aton, seamark, lighthouse, reef | xTrack/Coastline/FEAT_DSC_Coastline.md |
| dashboard, ui, layout, hud, display, screen | xTrack/Dashboard/FEAT_DSC_Dashboard.md |
| ui, ui-thingies, layout, onwater, button | xTrack/UiThingies/FEAT_DSC_UiThingies.md |
| gps, gpsplugin, gps mode, demo mode, heading, course, compass, location, geolocation | xTrack/GpsPlugin/FEAT_DSC_GpsPlugin.md |
| mapdisplay, map display, map, layer, depth layer, color depth, orientation | xTrack/MapDisplay/FEAT_DSC_MapDisplay.md |
| performance, battery, gps-tune, adaptive, compass-gate, map-refresh, battery-drain | xTrack/Performance/FEAT_DSC_Performance.md |
| isonwater, isonwateragain, iswater, waterland, raycast, crossing, point-in-polygon | xTrack/isOnWaterAgain/FEAT_DSC_isOnWaterAgain.md |
| bake, baking, bake-script, bake-bat, apk-bake, apk-build, apk-deploy, deploy, prebake-pipeline, bake-env | xTrack/BakeNormalization/FEAT_DSC_BakeNormalization.md |
| depthsafety, depth-safety, danger-depth, shallow, grounding, isobar precision, isobath precision, depth alert, depth overlay, water-only | xTrack/DepthSafety/FEAT_DSC_DepthSafety.md |
| localisation, localization, i18n, locale, language, translation, strings, values-fr, stringresource | xTrack/Localisation/FEAT_DSC_Localisation.md |
| app-bak-flow, app-back-flow, back, back button, back handler, exit, double-back, press back, keep screen on, keep awake, screen-on, wakelock | xTrack/AppBakFlow/FEAT_DSC_AppBakFlow.md |

## Feature Summaries

| Feature | One-Liner | Created | Modified | Status |
|---------|-----------|---------|----------|--------|
| WorkflowImprovement | xTrack #command system, trigger syntax, fuzzy matching, bootstrap, and memory bake lifecycle | 2026-06-03 00:00 | 2026-06-09 14:52 | active |
| ProjectDocumentation | Project documentation, README, FAQs, setup guides, and architecture overview | 2026-05-15 00:00 | 2026-06-03 00:00 | active |
| Zone300 | 300m zone generation from coastline with water-only constraint | 2026-05-20 00:00 | 2026-06-03 00:00 | active |
| CodeReview | Code quality review, linting, and structural improvements | 2026-06-01 00:00 | 2026-06-05 00:00 | active |
| DepthMapping | Bathymetry / depth mapping from Litto3D, SHOM, EMODnet sources | 2026-05-10 00:00 | 2026-06-05 00:00 | active |
| Coastline | Coastline extraction, spatial indexing, isOnWater determination | 2026-05-10 00:00 | 2026-06-05 00:00 | active |
| Dashboard | Main dashboard UI layout and HUD information display | 2026-05-15 00:00 | 2026-06-03 00:00 | active |
| UiThingies | UI widgets, buttons, and interactive elements | 2026-05-20 00:00 | 2026-06-03 00:00 | active |
| GpsPlugin | GPS plugin with demo mode, heading/COG compass, geolocation | 2026-05-10 00:00 | 2026-06-03 00:00 | active |
| MapDisplay | Map rendering, depth color layer, orientation overlay | 2026-05-10 00:00 | 2026-06-03 00:00 | active |
| Performance | Battery optimization, adaptive GPS tuning, compass gate, map refresh | 2026-05-20 00:00 | 2026-06-05 00:00 | active |
| isOnWaterAgain | Water/land determination via raycasting and point-in-polygon | 2026-05-25 00:00 | 2026-06-05 00:00 | active |
| BakeNormalization | APK bake/build/deploy pipeline and prebake data processing | 2026-06-01 00:00 | 2026-06-05 00:00 | active |
| DepthSafety | Danger depth alerts, shallow water grounding prevention, isobath precision | 2026-06-03 00:00 | 2026-06-05 00:00 | active |
| Localisation | i18n/localization, French translations, string resources | 2026-06-03 00:00 | 2026-06-03 00:00 | active |
| AppBakFlow | Back button handling, double-back-to-exit, keep-screen-on/wakelock | 2026-06-03 00:00 | 2026-06-03 00:00 | active |

## Global Rules
- Avoid PowerShell commands; use Windows CMD commands (e.g., `del` not `Remove-Item`, `dir` not `ls`).
- Auto-refine rule wording for clarity and conciseness on `#rule` add.
- Use apk-build.bat to build APK (runs gradlew assembleDebug).
- **Do not commit or push unless explicitly instructed.** Stage only when directed.
- **Do not commit or push directly to develop.** Always work through feature branches and merge via PR or explicit user instruction.

## Global Todos
- [ ] Validate the intermittent Overpass-outage theory — confirm the coastline OSM fetch failures are transient (succeeded 13:52, failing ~16:52 on 2026-06-08), not a persistent network / cert / IPv6 block. Quick checks: retry `bake-coastline` later; `curl -sk https://overpass-api.de/api/status`; race other mirrors.

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
