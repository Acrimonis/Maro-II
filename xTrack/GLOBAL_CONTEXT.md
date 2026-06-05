# Global Context — Routing Table

## Active Session Pointers
- **Active Feature:** Zone300
- **Active Subfeature:** distancetocoast
- **Last Updated:** 2026-06-05T00:00:00.000Z
- **Last Bake:** 2026-06-05

## Routing Map
| Keyword | Feature File |
|---|---|
| workflow, clinerules, xtrack, commands, memory, #doc, doccommands | FEATURE_SCOPE_WorkflowImprovement.md |
| documentation, docs, readme, project | FEATURE_SCOPE_ProjectDocumentation.md |
| zone300, zone, 300 | FEATURE_SCOPE_Zone300.md |
| codereview, code review, review, quality, lint | FEATURE_SCOPE_CodeReview.md |
| depth, bathymetry, depthmapping, baro, seafloor, soundings, litto3d, shom, emodnet | FEATURE_SCOPE_DepthMapping.md |
| depth, bathymetry, depthmapping, seafloor, soundings, litto3d, shom, emodnet | FEATURE_SCOPE_DepthMapping.md |

## Global Rules
- Avoid PowerShell commands; use Windows CMD commands (e.g., `del` not `Remove-Item`, `dir` not `ls`).
- Auto-refine rule wording for clarity and conciseness on `#rule` add.
- Use apk-build.bat to build APK (runs gradlew assembleDebug).
- Personal-use app, not for distribution — data fetched at runtime / baked offline, not redistributed.
- Personal-use app, not for distribution — data is fetched at runtime, not bundled/redistributed.
