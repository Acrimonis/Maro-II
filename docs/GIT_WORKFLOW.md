<!-- scope: reference -->

# Git Workflow

> Git **detail** — branch model, conflict policy, hard-rule notes. The hard rule and the command
> specs are single-sourced in `AGENTS.md` (Core Directives + §7b), which wins on any conflict.

## 🔴 ABSOLUTE RULE: NEVER write to develop or main — EVER

**Any** write operation on `develop` or `main` is **strictly forbidden** — this includes:
- `git push`/`git push --force`/`git push --force-with-lease`
- `git commit` directly on the branch
- `git revert` or any form of undo that writes
- `git merge` or `git rebase` targeting these branches

If a command would write to `develop` or `main`, the AI **must abort immediately**
and propose `#move new <name>` to create a fresh feature branch.

## Branch Model

| Branch | Source | Merges to | Purpose |
|--------|--------|-----------|---------|
| `main` | — | — | Tagged releases only |
| `develop` | `main` | `main` | Integration branch (no direct work) |
| `feature/*` | `develop` | `develop` | Isolated feature work |

## Commands

Command rows are single-sourced in `AGENTS.md` §7b; per-command detail lives in
[`docs/cmd_help_git.md`](cmd_help_git.md). This file keeps the branch model and conflict policy only.

## Conflict Policy

The resolution matrix is single-sourced in [`docs/cmd_help_git.md`](cmd_help_git.md) — GLOBAL_CONTEXT
routing rows merged and deduped, `FEAT_DSC_*` keep both sides, `AGENTS.md` flagged for manual review,
build files take the incoming version, source files follow feature ownership.

The `## OwnedFiles` mechanism this section once described is retired — only 2 of 25 features ever
declared it, and the live matrix replaced it.

`#merge` never writes `develop`/`main`; integration happens by pull request.
