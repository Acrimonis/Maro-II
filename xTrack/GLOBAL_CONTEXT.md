# Global Context — Routing Table

## Active Session Pointers
- **Active Feature:** isOnWaterAgain
- **Active Subfeature:** none
- **Last Updated:** 2026-06-08
- **Last Bake:** 2026-06-08 (isOnWaterAgain — mainland-primary water/land classifier + coastline data cleaning + capOpenEnds band-spike fix; rebaked nice-frejus.bin; tests green)

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
| isonwater, isonwateragain, iswater, waterland, raycast, crossing, point-in-polygon | FEATURE_SCOPE_isOnWaterAgain.md |

## Global Rules
- Avoid PowerShell commands; use Windows CMD commands (e.g., `del` not `Remove-Item`, `dir` not `ls`).
- Auto-refine rule wording for clarity and conciseness on `#rule` add.
- Use apk-build.bat to build APK (runs gradlew assembleDebug).
- **No Côte/Bande or Earth/Water controls anywhere in the UI.** The "Côte" (generate coastline) and "Bande" (regenerate 300m band) buttons are completely removed from the app interface. The Earth/Water icon display in the dashboard is also removed. These exist as ViewModel methods only for programmatic/internal use.
