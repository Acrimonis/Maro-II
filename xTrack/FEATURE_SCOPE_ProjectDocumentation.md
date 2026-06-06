---
name: ProjectDocumentation
status: active
created: 2026-06-03
modified: 2026-06-03
active_subfeature: none
subs_total: 0
subs_done: 0
one_liner: README rationalization, scoped sub-docs with scope tags, and #doc command system
---

# Feature: ProjectDocumentation

**Description:**
Project documentation — README rationalization, scoped sub-docs, documentation architecture.

## Subfeatures

## Todos

## Rules

## Key Files

## Docs
- `README.md` — scope:core — project context + documentation index
- `docs/SETUP.md` — scope:onboarding
- `docs/GIT_WORKFLOW.md` — scope:reference
- `docs/MARKER_SIZING.md` — scope:feature
- `docs/FAQ.md` — scope:reference
- `GitHub and SSH.md` — scope:archived (folded into docs/SETUP.md; pending deletion)

## Notes
Scope tag convention: `<!-- scope: core|onboarding|feature|reference|archived -->` as line 1 of each doc.
#doc subsystem (list/read/attach/detach) now implemented — see AGENTS.md § 7b.11.
