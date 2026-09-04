# Context Hydration — WorkflowImprovement — 2026-09-04

## State
Process simplification implemented on feature/process: WRITE-ONCE softened to 🟡 guideline, subfeatures replaced by sections (C1–C12), GLOBAL_CONTEXT Active Session Pointers replaced by an append-only Focus History stack (newest-first, cap 10), and MODE LOCK hardened with the no-bypass gate. 22 FEAT_DSC files migrated (active_subfeature stripped, ## Subfeatures → ## Sections); 153 legacy heading markers stripped. Docs/templates synced, cmd_help_sub.md retired, #doctor updated.

## Target Files
- `AGENTS.md` — Core Directives (MODE LOCK, WRITE-ONCE), §3, §7a, §7b
- `xTrack/GLOBAL_CONTEXT.md` — Focus History stack + WorkflowImprovement summary
- `docs/cmd_help*.md`, `.claude/skills/xtrack/references/templates.md`, `docs/GIT_WORKFLOW.md`
- `xTrack/*/FEAT_DSC_*.md` — 22 files migrated

## Next Step
Commit (requires explicit go-ahead). Then deep fold/trim of historical sections per feature via #bake (C8/C9).
