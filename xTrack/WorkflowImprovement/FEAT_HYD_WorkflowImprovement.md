# Context Hydration — WorkflowImprovement — 2026-06-28 14:23

**Active Subfeature:** none

## State
Two passes completed on feature/workflow:

1. **Cleanup pass** — 8 git commands added to AGENTS.md §7b, MODE LOCK clarified (Architect=shell+git ops), WorkflowAmbiguityFix absorbed, lingering todos closed, templates.md/dcmd_help.md synced.

2. **#merge hybrid strategy** — Pre-flight analysis classifies merge as trivial (direct shell) or non-trivial (yes or #implement pipeline). Auto-selects rebase vs merge based on commit count, overlap, and push status. Conflict resolution strategy per file type. Docs: AGENTS.md §7b, docs/cmd_help_git.md (full rewrite), docs/cmd_help.md one-liner.

## Target Files
- `AGENTS.md` — §7b #merge updated, all git commands present
- `docs/cmd_help_git.md` — full hybrid spec
- `docs/cmd_help.md` — #merge one-liner
- `xTrack/WorkflowImprovement/FEAT_PLN_WorkflowImprovement_merge-strategy.md` — full design doc

## Next Step
User direction. Branch feature/workflow ready for commit.
