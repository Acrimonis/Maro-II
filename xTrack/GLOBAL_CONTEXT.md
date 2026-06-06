# Global Context — Routing Table

## Active Session Pointers
- **Active Feature:** DepthMapping
- **Active Subfeature:** (none)
- **Last Updated:** 2026-06-06T00:00:00.000Z
- **Last Bake:** 2026-06-06T00:00:00.000Z

## Routing Map
| Keyword | Feature File |
|---|---|
| workflow, clinerules, xtrack, commands, memory, #doc, doccommands | FEATURE_SCOPE_WorkflowImprovement.md |
| documentation, docs, readme, project | FEATURE_SCOPE_ProjectDocumentation.md |
| zone300, zone, 300 | FEATURE_SCOPE_Zone300.md |
| codereview, code review, review, quality, lint | FEATURE_SCOPE_CodeReview.md |
| depth, bathymetry, depthmapping, baro, seafloor, soundings, litto3d, shom, emodnet | FEATURE_SCOPE_DepthMapping.md |
| depth, bathymetry, depthmapping, seafloor, soundings, litto3d, shom, emodnet | FEATURE_SCOPE_DepthMapping.md |
| coastline, trait de côte, fourmigue, hazard, obstruction, balisage, danger_isole, aton, seamark, lighthouse, reef | FEATURE_SCOPE_Coastline.md |

## Global Rules
- Avoid PowerShell commands; use Windows CMD commands (e.g., `del` not `Remove-Item`, `dir` not `ls`).
- Auto-refine rule wording for clarity and conciseness on `#rule` add.
- Use apk-build.bat to build APK (runs gradlew assembleDebug).
- Save and restore the current working folder in CONTEXT_HYDRATION.md on `#bake` and hydration.
- Personal-use app, not for distribution — all data is prebaked offline on the computer and bundled in the app; nothing is fetched or processed at runtime.
