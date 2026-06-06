# Global Context — Routing Table

## Active Session Pointers
- **Active Feature:** UiThingies
- **Active Subfeature:** none
- **Last Updated:** 2026-06-06
- **Last Bake:** 2026-06-06 (UiThingies — all 3 subfeatures done, isWater/distanceToShore persisted)

## Routing Map
| Keyword | Feature File |
|---|---|
| workflow, clinerules, xtrack, commands, memory, #doc, doccommands | FEATURE_SCOPE_WorkflowImprovement.md |
| documentation, docs, readme, project | FEATURE_SCOPE_ProjectDocumentation.md |
| zone300, zone, 300 | FEATURE_SCOPE_Zone300.md |
| codereview, code review, review, quality, lint | FEATURE_SCOPE_CodeReview.md |
| ui, ui-thingies, layout, onwater, button | FEATURE_SCOPE_UiThingies.md |

## Global Rules
- Avoid PowerShell commands; use Windows CMD commands (e.g., `del` not `Remove-Item`, `dir` not `ls`).
- Auto-refine rule wording for clarity and conciseness on `#rule` add.
- Use apk-build.bat to build APK (runs gradlew assembleDebug).
