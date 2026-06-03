# ProjectDocumentation
**Status:** active
**Created:** 2026-06-03
**Description:** Project documentation — README rationalization, scoped sub-docs, documentation architecture.
**One-liner:** README rationalization, scoped sub-docs with scope tags, and #doc command system

## Subfeatures
- [x] README split into 4 scoped sub-docs (SETUP, GIT_WORKFLOW, MARKER_SIZING, FAQ)
- [x] Scope tag convention (core / onboarding / feature / reference) as HTML comments
- [x] README as documentation index with scope legend
- [x] Corrected project structure tree in README
- [x] Delete root-level GitHub and SSH.md (contents folded into docs/SETUP.md)
- [ ] #doc commands (list, read, attach, detach) — queued as follow-up #track

## Todos
- [x] Delete GitHub and SSH.md (needs code mode — architect can't delete)
- [ ] #track DocCommands for #doc list/read/attach/detach
- [x] Add a gate to not implement anything when a prompt ends with "explain" or "discuss"

## Rules
- Keep `docs/cmd_help.md` up to date whenever xTrack commands are added, renamed, or removed.

## Key Files
- `README.md` — core project context + documentation index
- `docs/SETUP.md` — scope:onboarding
- `docs/GIT_WORKFLOW.md` — scope:reference
- `docs/MARKER_SIZING.md` — scope:feature
- `docs/FAQ.md` — scope:reference
- `GitHub and SSH.md` — pending deletion (folded into SETUP.md)

## Notes
Scope tag convention: `<!-- scope: core|onboarding|feature|reference -->` as first line of each doc.
#doc commands (list/read/attach/detach) designed and agreed, queued for follow-up #track.
