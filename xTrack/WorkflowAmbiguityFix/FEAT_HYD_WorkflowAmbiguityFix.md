# WorkflowAmbiguityFix — Hydration Snapshot

**Baked at:** 2026-06-20 14:51 UTC

## Session Summary

Clarified rule precedence between AGENTS.md §5 (absolute develop/main write ban) and §7b `#merge` command (ambiguous "rebase+force-push"). Root cause: `#merge` description implied direct manipulation of develop.

## Key Files Modified
- `AGENTS.md` — §5 reinforced ("override does not lift it"), §7b `#merge` redefined
- `docs/cmd_help_git.md` — `#merge` description aligned
- `xTrack/GLOBAL_CONTEXT.md` — WorkflowAmbiguityFix feature added
- `FEAT_DSC_WorkflowAmbiguityFix.md` — this file
- `FEAT_HYD_WorkflowAmbiguityFix.md` — this file

## Next Steps
- [ ] Build and verify no regressions
- [ ] Commit and push feature/yawf
