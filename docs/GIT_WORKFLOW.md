<!-- scope: reference -->

# Git Workflow

> Source of truth for all git rules. Upper layers (AGENTS.md, cmd_help_git.md,
> GLOBAL_CONTEXT.md) reference this file — edit rules here only.

## 🔴 Hard Rule: NEVER push to develop or main

Direct commits, pushes, or merges on `develop` or `main` are **forbidden**.
If attempted, the AI **must abort** and propose `#move`:
- **New branch?** → prompt with `feature/` prepopulated
- **Existing?** → list branches by last commit date descending

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
- `#merge` — rebase onto `origin/develop` + force-push (🚫 refuses on develop/main)
