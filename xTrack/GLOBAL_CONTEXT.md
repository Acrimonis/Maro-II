# Global Context — Routing Table

## Active Session Pointers
- **Active Feature:** MapDisplay
- **Active Subfeature:** layer-lowdepth
- **Last Updated:** 2026-06-08
- **Last Bake:** 2026-06-08 20:23 (MapDisplay — low-depth warning overlay (configurable threshold slider 0.5–5.0 m, default 1.5, persisted) above the depth raster; "Keep phone on" → top of Power saving; Mercator offset fixed by latitude-banding BOTH depth + warning (addBandedOverlay, 8 strips). Merged feature/layout-lowdepth → develop: my bake land-mask was CONVERGENT with develop's DepthSafety/litto3d-shallow land-mask → resolved DepthZoneMask to develop's. KNOWN: pink warning laps ~½ cell onto land at 25 m granularity (todo: sub-cell test / vector clip). NEXT: decide bleed fix + on-device verify; reconcile MapDisplay/layer-lowdepth vs DepthSafety overlap)
- **Prev Bake:** 2026-06-08 19:16 (DepthSafety — bake-mask chosen as the water-only guard (per-cell runtime infeasible ~7 M cells); merged feature/litto3d-shallow → develop; planned branches: water-only, isobar-precision, danger-display, danger-alert)

## Routing Map
| Keyword | Feature File |
|---|---|
| workflow, clinerules, xtrack, commands, memory, #doc, doccommands | FEATURE_SCOPE_WorkflowImprovement.md |
| documentation, docs, readme, project | FEATURE_SCOPE_ProjectDocumentation.md |
| zone300, zone, 300 | FEATURE_SCOPE_Zone300.md |
| codereview, code review, review, quality, lint | FEATURE_SCOPE_CodeReview.md |
| depth, bathymetry, depthmapping, baro, seafloor, soundings, litto3d, shom, emodnet | FEATURE_SCOPE_DepthMapping.md |
| coastline, trait de côte, fourmigue, hazard, obstruction, balisage, danger_isole, aton, seamark, lighthouse, reef | FEATURE_SCOPE_Coastline.md |
| dashboard, ui, layout, hud, display, screen | FEATURE_SCOPE_Dashboard.md |
| ui, ui-thingies, layout, onwater, button | FEATURE_SCOPE_UiThingies.md |
| gps, gpsplugin, gps mode, demo mode, heading, course, compass, location, geolocation | FEATURE_SCOPE_GpsPlugin.md |
| mapdisplay, map display, map, layer, depth layer, color depth, orientation | FEATURE_SCOPE_MapDisplay.md |
| performance, battery, gps-tune, adaptive, compass-gate, map-refresh, battery-drain | FEATURE_SCOPE_Performance.md |
| isonwater, isonwateragain, iswater, waterland, raycast, crossing, point-in-polygon | FEATURE_SCOPE_isOnWaterAgain.md |
| bake, baking, bake-script, bake-bat, apk-bake, apk-build, apk-deploy, deploy, prebake-pipeline, bake-env | FEATURE_SCOPE_BakeNormalization.md |
| depthsafety, depth-safety, danger-depth, shallow, grounding, isobar precision, isobath precision, depth alert, depth overlay, water-only | FEATURE_SCOPE_DepthSafety.md |
| localisation, localization, i18n, locale, language, translation, strings, values-fr, stringresource | FEATURE_SCOPE_Localisation.md |
| app-bak-flow, app-back-flow, back, back button, back handler, exit, double-back, press back, keep screen on, keep awake, screen-on, wakelock | FEATURE_SCOPE_AppBakFlow.md |

## Global Rules
- Avoid PowerShell commands; use Windows CMD commands (e.g., `del` not `Remove-Item`, `dir` not `ls`).
- Auto-refine rule wording for clarity and conciseness on `#rule` add.
- Use apk-build.bat to build APK (runs gradlew assembleDebug).
- **No Côte/Bande or Earth/Water controls anywhere in the UI.** The "Côte" (generate coastline) and "Bande" (regenerate 300m band) buttons are completely removed from the app interface. The Earth/Water icon display in the dashboard is also removed. These exist as ViewModel methods only for programmatic/internal use.
- **Do not commit or push unless explicitly instructed.** Stage only when directed.
- **Do not commit or push directly to develop.** Always work through feature branches and merge via PR or explicit user instruction.

## Always-Loaded Context
These files are loaded into context at the start of every session to maximize the AI prefix-cache hit rate:
- `AGENTS.md` — canonical rulebook (all project rules + xTrack §7a/7b command spec)
- `.claude/skills/xtrack/SKILL.md` — skill dispatch map
- `.claude/skills/xtrack/references/fuzzy-resolve.md` — fuzzy lookup cascade
- `.claude/skills/xtrack/references/templates.md` — file templates
- `docs/cmd_help.md` — command reference summary
- `xTrack/GLOBAL_CONTEXT.md` — this file (routing table, active pointers, global rules)

## Global Instructions
- The xTrack `#`-command system is the canonical workflow. Use it for all feature tracking, todo/rule management, doc management, and session snapshots.
- On Turn 1 of any session: read GLOBAL_CONTEXT.md, match user intent against the Routing Map, open the corresponding feature file and its hydration file.
- Route docs, key files, and todos to the correct feature scope. Keep feature files lean — use ## Docs for references, ## Key Files for source paths.
