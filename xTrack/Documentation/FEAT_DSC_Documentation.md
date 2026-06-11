---
name: Documentation
status: active
created: 2026-06-11 06:42
modified: 2026-06-11 07:46
active_subfeature: help
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

### help  [ ]  ← active

#### Todos
- [ ] Split `docs/cmd_help.md` into per-command detail files: `docs/cmd_help_[cmd].md`
- [ ] Update `AGENTS.md` `#help` reference to use the new split files
- [ ] Update `xTrack/WorkflowImprovement/FEAT_DSC_WorkflowImprovement.md` ## Docs to reference the split files

#### Rules

#### Key Files
- `docs/cmd_help.md` — summary reference table
- `docs/cmd_help_*.md` — per-command detail files
- `AGENTS.md` — §7b.8 Command Reference spec

## Todos

## Rules

## Key Files

## Docs
- `docs/FAQ.md` — Project FAQ
- `docs/SETUP.md` — Project setup guide
