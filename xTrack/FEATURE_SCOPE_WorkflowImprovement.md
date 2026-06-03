# WorkflowImprovement
**Status:** active
**Created:** 2026-06-03
**Last Modified:** 2026-06-03T11:02:43.929Z
**Description:** Improving the .clinerules Section 7 xTrack workflow and command system — trigger syntax, templates, lifecycle protocols, and bootstrap logic.
**One-liner:** xTrack #command system, trigger syntax, fuzzy matching, bootstrap, and memory bake lifecycle

## Subfeatures
- [x] Trigger phrase syntax redesign (colon-delimited commands)
- [x] Feature file template (Status/Created/Description/Subfeatures/Key Files/Notes)
- [x] Turn 1 protocol with intent gate + scope question
- [x] Memory Bake event-driven triggers (closing phrases, task wind-down, #bake)
- [x] Hash-prefix command dispatcher (#) with fuzzy fallback
- [x] xTrack bootstrap logic (auto-create on first `track:`)
- [x] Active focus pivot ambiguity with cross-feature intercept + #todo + #instruction
- [x] #-only command system (colon-delimited triggers removed)
- [x] #instruction command for context attachments
- [x] Bare #todo/#instruction list mode
- [x] Smart subfeature nesting for #todo
- [x] Bare `#doc` — show active feature's Key Files (scoped, not global)
- [x] #doc create [name] — create doc in docs/, prompt for scope tag
- [x] #doc list — scan docs/*.md, display filename + scope tag + heading
- [x] #doc read [name] — hydrate doc into AI context
- [x] #doc attach [name] — add doc; bare = prompt pick-list
- [x] #doc detach [name] — remove doc; bare = prompt pick-list

## Todos
- [ ] Point 6: Compress #doc sub-commands to compact reference table (deferred)
- [x] Point 7: Define bare `#focus` behavior (prompt pick-list)
- [x] Point 8: Trim Section 3 provider-specific cache details
- [x] Point 9: Reorder .clinerules sections by session frequency + extract spatial to docs/

## Rules
- Keep `docs/cmd_help.md` up to date whenever xTrack commands are added, renamed, or removed.
- Do not prompt to edit/save `.clinerules`; you have direct access to it.


## Key Files
- `.clinerules` — Section 7 rules being improved

## Notes
<!-- blockers, design decisions, context for next session -->
Items 1-4 applied to .clinerules. Items 5-6 remain to be discussed. Hash-prefix dispatcher and #bake command now active.
