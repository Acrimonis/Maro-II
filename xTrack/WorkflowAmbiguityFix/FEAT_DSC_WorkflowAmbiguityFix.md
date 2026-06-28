---
name: WorkflowAmbiguityFix
status: done
created: 2026-06-20 14:51
modified: 2026-06-28 13:51
active_subfeature: none
---

# Feature: WorkflowAmbiguityFix

**Description:**
Eliminate ambiguity between AGENTS.md rules — specifically the `#merge` command (§7b) conflicting with the absolute `develop`/`main` write ban (§5). Reinforce rule precedence so no agent can misinterpret.

## Resolution (2026-06-28)
Absorbed into **WorkflowImprovement**. All concerns addressed in the WorkflowImprovement "hard rules" and "Core Directives promotion" passes:
- §5 hardened with absolute develop/main write ban + "override does not lift it" language
- §7b `#merge` redefined as pull develop→feature, push feature, PR link
- `docs/cmd_help_git.md` aligned
- GLOBAL_CONTEXT.md synced

## Subfeatures

## Todos
- [x] Update AGENTS.md §5 — "override does not lift it" language
- [x] Update AGENTS.md §7b — `#merge` = pull develop→feature, push feature, PR link
- [x] Update docs/cmd_help_git.md — `#merge` description alignment
- [x] Update GLOBAL_CONTEXT.md — routing/summary for WorkflowAmbiguityFix
- [x] Create FEAT_DSC and FEAT_HYD files

## Rules
- §5 absolute rule supersedes ALL other commands — `#merge`, `#implement`, user "override" included
- `#merge` merges `develop` INTO feature branch (sync), never the reverse
- Integration to `develop`/`main` is via GitHub PR only

## Key Files
- `AGENTS.md`
- `docs/cmd_help_git.md`
- `xTrack/GLOBAL_CONTEXT.md`

## Docs
