---
name: Documentation
status: active
created: 2026-06-11 06:42
modified: 2026-06-11 08:30
active_subfeature: none
---

# Feature: Documentation

**Description:**
Cross-cutting project documentation — README, FAQs, setup guides, architecture docs, git workflow, and maintenance of docs/ directory. Ensures all plans and reference docs are properly organized and attached to the correct features.

## Subfeatures

### cleanup  [x]

#### Todos
- [x] List all `plans/*.md` files and classify by target feature
- [x] List all unattached `docs/*.md` files and classify by target feature
- [x] Attach or move plans/docs to their corresponding feature `## Docs` sections
- [x] Resolve ambiguous cases through discussion

#### Rules

#### Key Files

### help  [x]

#### Todos
- [x] Split `docs/cmd_help.md` into per-command detail files: `docs/cmd_help_[cmd].md`
- [x] Update `AGENTS.md` `#help` reference to use the new split files
- [x] Update `xTrack/WorkflowImprovement/FEAT_DSC_WorkflowImprovement.md` ## Docs to reference the split files

#### Rules

#### Key Files
- `docs/cmd_help.md` — summary reference table
- `docs/cmd_help_*.md` — per-command detail files
- `AGENTS.md` — §7b.8 Command Reference spec

### git rules  [ ]  ← active

#### Todos
- [x] Rewrite `docs/GIT_WORKFLOW.md` into compact token-optimized source of truth (~30 lines)
- [x] Update `AGENTS.md` §7b.8 to reference `docs/GIT_WORKFLOW.md` instead of inline rule
- [x] Update `docs/cmd_help_git.md` — compact, reference GIT_WORKFLOW.md
- [x] Update `docs/cmd_help.md` summary — compact git section, ref GIT_WORKFLOW.md
- [x] Update `xTrack/GLOBAL_CONTEXT.md` Global Rules — ref GIT_WORKFLOW.md

#### Rules
- `docs/GIT_WORKFLOW.md` is the single source of truth for git rules.
- Upper-layer files (AGENTS.md, cmd_help_*.md, GLOBAL_CONTEXT.md) only reference it.
- 🔴 Hard rule: `#merge`/`#push`/`#commit` refuse on `develop`/`main`.

#### Key Files
- `docs/GIT_WORKFLOW.md` — source of truth
- `docs/cmd_help_git.md` — compact command reference
- `AGENTS.md` §7b.8 — hard rule reference
- `xTrack/GLOBAL_CONTEXT.md` — Global Rules reference

## Todos

## Rules

## Key Files

## Docs
- `docs/FAQ.md` — Project FAQ
- `docs/SETUP.md` — Project setup guide
- `plans/git-protection-workflow.md` — Git protection enforcement flow design
