# xTrack Command Reference

Full per-command spec, ported from `.clinerules` section 7b. Every name lookup
uses the fuzzy cascade — see [fuzzy-resolve.md](fuzzy-resolve.md). Templates for
new files are in [templates.md](templates.md).

**Scope targeting:** When a subfeature is focused (`**Active Subfeature:**` set
in the feature file header), bare `#todo`, `#rule`, `#doc`, and `#doc attach`
target that subfeature's `####` subsection. Otherwise they target the parent
feature's `##` section.

---

## `#features` — Status Dashboard

Parse `xTrack/GLOBAL_CONTEXT.md` for all registered features. Read each feature
file to extract `**One-liner:**`, `**Created:**`, `**Last Modified:**`, and the
subfeature completion count (X/Y done). Print a compact table: feature name,
one-liner, created, last modified, subfeature ratio. Sort by `**Last Modified:**`
descending (newest first). Do **not** expand subfeature checklists (that is
`#status`). Highlight the active feature row (bold name + `← active`). Display
the current working folder at the top of the output.

## `#focus [name]` — Active Focus Pivot

Fuzzy-resolve `[name]` against existing features. Open that feature file, update
the `Active Session Pointers` section in `GLOBAL_CONTEXT.md`, and constrain
operational context to that epic. Bare `#focus` (no name): list existing
features and prompt the user to pick one.

## `#track [name]` — New Epic Feature

Fuzzy-resolve against existing features. If no match, create
`xTrack/FEATURE_SCOPE_[name].md` from the feature template (must include
`**Status:**`, `**Created:**`, `**Last Modified:**`, `**Description:**`,
`**One-liner:**`; `**Last Modified:**` initially equals `**Created:**`). Open
`GLOBAL_CONTEXT.md` and append a keyword-to-path row to the Routing Map table.
Derive `**One-liner:**` from the description (one concise sentence). If the input
is a descriptive phrase rather than PascalCase, derive a clean feature name and
confirm before creating.

## `#sub [name]` — Task Decomposition

Fuzzy-resolve against existing subfeatures in the active feature file. If no
match, locate the active feature file, find or initialize the `## Subfeatures`
block, and insert the subfeature as an H3 (`### name  [ ]`) with nested
`#### Todos`, `#### Rules`, `#### Key Files`. Bare `#sub`: list subfeatures with
`← focused` on the active one.

### `#sub focus [name]` — Subfeature Focus

Fuzzy-resolve `[name]` against subfeatures in the active feature. On match, set
`**Active Subfeature:** [name]` in the feature file header. No match: list
subfeatures and ask to pick or `#sub [name]` to create.

### `#sub out` — Subfeature Exit

Clear the `**Active Subfeature:**` pointer. Subsequent commands return to the
parent feature level.

## `#bake` — Memory Bake

Execute a snapshot:
1. Update active checkmarks inside the current feature file.
2. Update `**Last Modified:**` to the current ISO 8601 UTC date — **only if the
   feature was actually modified this session** (subfeature toggled, todo/rule
   added, etc.).
3. Create or overwrite `xTrack/CONTEXT_HYDRATION.md` with a ~200-word micro-state
   summary (active feature, statuses, target files, next step) — see template.
4. Prompt the user to clear the workspace.

Also triggered by closing phrases ("done", "goodbye", "end", "that's all") or
task completion as the conversation winds down.

## `#todo` — Todo Tracking (three tiers)

1. **Bare `#todo`** — list todos for the active scope (subfeature if focused,
   else parent feature).
2. **`#todo [description]`** — fuzzy-match the description against existing
   subfeatures; if matched, offer to nest under that subfeature, else append
   `- [ ] [description]` to the active scope's `#### Todos` (subfeature) or
   `## Todos` (parent).
3. **`#todo [target]: [description]`** —
   - `target` = `global` → append to `xTrack/GLOBAL_TODOS.md` `## Todos`.
   - `target` = `parent` → append one level up (parent feature if sub focused;
     global if feature focused).
   - otherwise fuzzy-resolve `target` against features and write to that
     feature's active scope.
   - Unrecognized → ask: (a) `#track` it, (b) use global, (c) cancel.

## `#rule` — Context Rules (three tiers)

1. **Bare `#rule`** — list rules for the active scope.
2. **`#rule [text]`** — append to the active scope's `#### Rules` (subfeature) or
   `## Rules` (parent).
3. **`#rule [target]: [text]`** —
   - `target` = `global` → append to `GLOBAL_CONTEXT.md` `## Global Rules`.
   - `target` = `parent` → append one level up.
   - otherwise fuzzy-resolve `target` against features.
   - Unrecognized → ask: (a) `#track` it, (b) use global, (c) cancel.

## `#status` — Active Feature Status

**Bare `#status`** — read the active feature from `GLOBAL_CONTEXT.md`, open that
feature file, and print a detailed single-feature dashboard: feature name +
one-liner, full subfeature checklist (expand the active subfeature's nested
todos/rules/key-files if one is focused), all parent-level todos, all
parent-level rules. **`#status [name]`** — fuzzy-resolve `[name]` and print the
same dashboard for that feature. If all subfeatures, todos, and rules are
complete, append `All clear. #bake to snapshot.`

## `#feature` — Show Current Focus

Display the currently active feature and subfeature (if any). Reads `GLOBAL_CONTEXT.md`
to get the active feature, then reads that feature file to check for an active
subfeature pointer. Output: current working folder, feature name + one-liner,
and subfeature name if focused. Useful for quick context confirmation without
opening files.

## `#help` — Command Reference

Read and print the contents of `docs/cmd_help.md` as a code block. Keep that file
up to date when xTrack commands are added, renamed, or removed.

## Cross-Feature Mention Intercept

If the user mentions a feature name (not the active one) in conversation,
fuzzy-resolve against existing features and intercept:
- **Match:** (a) switch focus, (b) add a todo to that feature, (c) bake current
  session + restart fresh on that feature, (d) ignore.
- **No match:** (a) create as new feature, (b) add as todo to current feature,
  (c) add as subfeature to current feature, (d) ignore.

---

## `#doc` — Documentation Subsystem

All `#doc` commands fuzzy-resolve with confirmation when ambiguous.

- **Bare `#doc`** — read the active scope's `#### Key Files` (subfeature if
  focused, else `## Key Files` of parent) and display attached docs. Scoped to
  active context, unlike `#doc list`.
- **`#doc create [name]`** — create `docs/[name].md`, prompt for a scope tag:
  (a) core, (b) onboarding, (c) feature, (d) reference. Write `<!-- scope: [chosen] -->`
  as line 1 and `# [name]` as the title on line 3.
- **`#doc list`** — scan `docs/*.md`, read line 1 of each for the
  `<!-- scope: ... -->` tag, print a table: filename → scope → first heading
  (line 3). Include `README.md` as an implicit `scope: core` entry.
- **`#doc read [name]`** — fuzzy-resolve against `docs/` filenames (`.md`
  optional). Read the file into context. Print confirmation: filename, scope,
  line count.
- **`#doc attach [name]`** — fuzzy-resolve against `docs/*.md`, add the filename
  to the active feature's `## Key Files` with a brief description. Skip if
  present. Bare `#doc attach`: run `#doc list` and prompt which to attach.
- **`#doc detach [name]`** — fuzzy-resolve against `docs/*.md`, remove from the
  active feature's `## Key Files`. Bare `#doc detach`: show Key Files and prompt
  which to detach.
