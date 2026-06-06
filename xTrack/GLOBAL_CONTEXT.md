# Global Context — Routing Table

## Active Session Pointers
- **Active Feature:** Zone300
- **Active Subfeature:** drawZone
- **Last Updated:** 2026-06-06
- **Last Bake:** 2026-06-06

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
- Personal-use app, not for distribution — data fetched at runtime / baked offline, not redistributed.
- Personal-use app, not for distribution — data is fetched at runtime, not bundled/redistributed.
- Long-response ELI16 recap: after any consequential action, if the response exceeds ~500 words, end it with a short ELI16 bullet summary of the key points and their impact.
