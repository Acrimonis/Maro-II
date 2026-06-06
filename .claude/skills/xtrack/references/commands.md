# xTrack Command Reference

> **Canonical:** `AGENTS.md` § 7b. This file elaborates with examples and edge
> cases; it must never contradict `AGENTS.md`.

Full per-command spec, mirroring `AGENTS.md` § 7b. Every name lookup
uses the fuzzy cascade — see [fuzzy-resolve.md](fuzzy-resolve.md). Templates for
new files are in [templates.md](templates.md).

**Scope targeting:** When a subfeature is focused (`active_subfeature` set in the
feature's front-matter), bare `#todo`, `#rule`, `#doc`, and `#doc attach` target
that subfeature's `####` subsection. Otherwise they target the parent feature's
`##` section.

---

## `#features` — Status Dashboard  (alias: `#list`)

Parse `xTrack/GLOBAL_CONTEXT.md` for all registered features. Read each feature
file's **YAML front-matter only** (not the body) to extract `one_liner`,
`created`, `modified`, `status`, and `subs_done`/`subs_total`. Print a compact
table: feature name, one-liner, created, modified, subfeature ratio, status. Sort
by `modified` descending (newest first). Do **not** expand subfeature checklists
(that is `#status`). Highlight the active feature row (bold name + `← active`).

## `#feature` — Current Context

Print a lightweight "where am I" block (not the full `#status` dashboard): the
active feature (name + `status` + `one_liner`), the `active_subfeature`, and the
**working context** — cwd, git branch (`git branch --show-current`), worktrees
(`git worktree list`), and `Last Bake`. `#features` lists all; `#status` is the
full single-feature dashboard.

## `#focus [name]` — Active Focus Pivot

Fuzzy-resolve `[name]` against existing features. Open that feature file, update
the `Active Session Pointers` section in `GLOBAL_CONTEXT.md`, and constrain
operational context to that epic. Bare `#focus` (no name): list existing
features and prompt the user to pick one.

## `#track [name]` — New Epic Feature

Fuzzy-resolve against existing features. If no match, create
`xTrack/FEATURE_SCOPE_[name].md` from the feature template — a YAML front-matter
header (`name`, `status: active`, `created`, `modified` = `created`,
`active_subfeature: none`, `subs_total: 0`, `subs_done: 0`, `one_liner`) then
`**Description:**` and the section skeleton. Open `GLOBAL_CONTEXT.md` and append a
keyword-to-path row to the Routing Map table. Derive `one_liner` from the
description (one concise sentence). If the input
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
1. Update subfeature checkmarks inside the current feature file.
2. In the front-matter, recompute `subs_total`/`subs_done` from the checkboxes,
   and set `modified` to the current `YYYY-MM-DD` — **only if the feature was
   actually modified this session** (subfeature toggled, todo/rule added, etc.).
3. Create or overwrite the **per-feature** hydration file
   `xTrack/hydration/CONTEXT_HYDRATION_[Feature].md` with a ~200-word micro-state
   summary (state, target files, next step) — see template; update
   `**Last Bake:**` in `GLOBAL_CONTEXT.md`.
4. Prompt the user to clear the workspace.

Per-feature hydration means baking one feature never clobbers another's resume
state (safe under parallel sessions). Also triggered by closing phrases ("done",
"goodbye", "end", "that's all") or task completion as the conversation winds down.

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
feature file (front-matter + body), and print a detailed single-feature dashboard:
name + one_liner + status/dates, full subfeature checklist (expand the active
subfeature's nested todos/rules/key-files/docs if one is focused), all
parent-level todos, rules, and docs. **`#status [name]`** — fuzzy-resolve `[name]`
and print the same dashboard for that feature. If all subfeatures and todos are
complete, append `All clear. #bake to snapshot.`

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

All `#doc` commands fuzzy-resolve with confirmation when ambiguous. Docs attach to
a feature's `## Docs` section (subfeature `#### Docs` when focused) — distinct from
`## Key Files`, which lists source files.

- **Bare `#doc`** — read the active scope's `## Docs` (subfeature's `#### Docs` if
  focused) and display the attached docs.
- **`#doc create [name]`** — create `docs/[name].md`, prompt for a scope tag:
  (a) core, (b) onboarding, (c) feature, (d) reference, (e) archived. Write
  `<!-- scope: [chosen] -->` as line 1 and `# [name]` as the title on line 3.
- **`#doc list`** — scan `docs/**/*.md` (recursive); read the first ~5 lines of
  each for the `<!-- scope: ... -->` tag and the first `#` heading (tolerant of a
  leading blank line/BOM). Print a table grouped by scope: relative path → scope →
  heading. Include `README.md` as an implicit `scope: core` entry.
- **`#doc read [name]`** — fuzzy-resolve against `docs/**` filenames (`.md`
  optional). Read the file into context. Print confirmation: filename, scope,
  line count.
- **`#doc attach [name]`** — fuzzy-resolve against `docs/**/*.md`, add the relative
  path to the active feature's `## Docs` with a brief description. Skip if present.
  Bare `#doc attach`: run `#doc list` and prompt which to attach.
- **`#doc detach [name]`** — fuzzy-resolve against `docs/**/*.md`, remove from the
  active feature's `## Docs`. Bare `#doc detach`: show `## Docs` and prompt which to
  detach.

---

## `#doctor` — Repository Doctor

Lint the xTrack stack for drift; print findings grouped by severity. `#doctor fix`
auto-repairs the safe classes and reports the rest; `#bake` runs the auto-fix
subset first.

Checks:
- Duplicate / overlapping Routing Map rows.
- Duplicate or near-duplicate rules (global or per-feature).
- Stale `active_subfeature` (front-matter names a non-focused or non-existent subfeature).
- Non-`YYYY-MM-DD` dates.
- Front-matter `subs_total`/`subs_done` mismatching actual checkboxes.
- `status` vs completion (all subfeatures `[x]` but `status` ≠ `done`).
- Malformed sections (e.g. a non-`###` line directly under `## Subfeatures`).
- Orphan docs (in `docs/**`, attached to no feature, not `README`).
- Routing rows → missing files, or feature files with no routing row.

**Auto-fixable** (`#doctor fix`): dedupe routing rows, normalize dates, recompute
counts, reset stale `active_subfeature`. **Report-only:** malformed sections,
orphan docs, status / rule-dedupe judgment calls.
