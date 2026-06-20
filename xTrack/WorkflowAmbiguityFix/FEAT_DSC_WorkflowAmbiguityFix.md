---
name: WorkflowAmbiguityFix
status: active
created: 2026-06-20 14:51
modified: 2026-06-20 14:51
active_subfeature: none
---

# Feature: WorkflowAmbiguityFix

**Description:**
Eliminate ambiguity between AGENTS.md rules — specifically the `#merge` command (§7b) conflicting with the absolute `develop`/`main` write ban (§5). Reinforce rule precedence so no agent can misinterpret.

## Subfeatures

## Todos
- [ ] Update AGENTS.md §5 — "override does not lift it" language
- [ ] Update AGENTS.md §7b — `#merge` = pull develop→feature, push feature, PR link
- [ ] Update docs/cmd_help_git.md — `#merge` description alignment
- [ ] Update GLOBAL_CONTEXT.md — routing/summary for WorkflowAmbiguityFix
- [ ] Create FEAT_DSC and FEAT_HYD files

## Rules
- §5 absolute rule supersedes ALL other commands — `#merge`, `#implement`, user "override" included
- `#merge` merges `develop` INTO feature branch (sync), never the reverse
- Integration to `develop`/`main` is via GitHub PR only

## Key Files
- `AGENTS.md`
- `docs/cmd_help_git.md`
- `xTrack/GLOBAL_CONTEXT.md`

## Docs
