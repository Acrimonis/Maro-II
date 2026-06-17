<!-- scope: reference -->

# Git Workflow

> Source of truth for all git rules. Upper layers (AGENTS.md, cmd_help_git.md,
> GLOBAL_CONTEXT.md) reference this file — edit rules here only.

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

## Quick Reference

- `#new [name]` — fetch `origin/develop`, create `feature/[name]` tracking it
- `#move new [name]` — stash → create from `origin/develop` → pop
- `#move [name]` — stash → switch (existing) → pop
- `#commit` — bake + add + commit (🚫 refuses on develop/main)
- `#push` — push current branch (🚫 refuses on develop/main)
- `#merge` — rebase onto `origin/develop` + force-push; AI auto-resolves conflicts via feature ownership (🚫 refuses on develop/main)

## AI-Assisted Conflict Resolution

`#merge` auto-resolves conflicts during rebase using feature ownership data from `FEAT_DSC_*.md`:

1. **Pre-rebase:** AI reads all features' `## OwnedFiles` sections to build a `path → feature` map.
2. **On conflict:** AI processes each conflicted file:
   - **Active feature's file** → keep `theirs` (feature branch wins)
   - **Another feature's file** → keep `ours` (develop wins)
   - **Unowned** → AI inspects hunk symbols against active feature scope
   - **Ambiguous** (binary, build config, overlapping edits) → flag for manual review
   - **xTrack tracking files** → dedupe Routing Map, merge subfeatures, update Active Pointer
3. **Report:** *"Resolved N files. M need manual attention: [list]"*
4. **User completes** remaining manual resolutions, `git rebase --continue`, then `#push`.

See `AGENTS.md §7b.15` for full spec.
