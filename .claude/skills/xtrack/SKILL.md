---
name: xtrack
description: >-
  xTrack feature-tracking command system for this project. Use this skill
  WHENEVER the user types any "#"-prefixed command — #track, #focus, #bake,
  #todo, #rule, #doc, #list, #status, #help, #sub, and their variants (#sub
  focus, #sub out, #doc create/list/read/attach/detach, #todo global:/parent:,
  #rule global:/parent:) — or asks to track a feature, switch active focus,
  snapshot/bake session state, hydrate context from a prior session, or manage
  todos / rules / documentation. Trigger even on a bare "#focus" or "#todo",
  and when the user mentions a feature name in passing that may match a tracked
  feature. The skill reads and writes the existing xTrack/ and docs/ files
  defined in AGENTS.md — it does not relocate or rename anything.
---

# xTrack — Feature Tracking Command System

This skill executes the `#`-prefixed command system defined in the project's
**`AGENTS.md`** (§ 7b). It is the operational engine for the **xTrack memory stack**:
a set of markdown files under `xTrack/` (and `docs/`) that hold feature epics,
todos, rules, and transactional session state.

`AGENTS.md` (§ 7a/7b) is the source of truth. This skill mirrors its behavior — it
never relocates, renames, or restructures files. If anything here conflicts with
`AGENTS.md`, `AGENTS.md` wins; flag the discrepancy to the user.

## When this fires

The user types a `#`-command, or expresses intent that maps to one (track a
feature, pivot focus, bake/snapshot, manage todos/rules/docs, hydrate context).
Match the command, then follow the detailed spec.

## The memory stack (file layout — do not change)

| File | Role |
|------|------|
| `xTrack/GLOBAL_CONTEXT.md` | Root routing table, global instructions, active session pointers, global rules |
| `xTrack/FEATURE_SCOPE_[name].md` | One feature epic (status, dates, description, subfeatures, todos, rules, key files) |
| `xTrack/CONTEXT_HYDRATION.md` | Micro session transactional state (created lazily on first `#bake`) |
| `xTrack/GLOBAL_TODOS.md` | Cross-cutting todos, easy to purge |
| `docs/*.md` | Documentation, each tagged `<!-- scope: ... -->` on line 1 |

**Bootstrap:** If `xTrack/` does not exist when any tracking or focus command is
issued, auto-create it and initialize `GLOBAL_CONTEXT.md`, the feature file, and
`GLOBAL_TODOS.md`. `CONTEXT_HYDRATION.md` is created lazily on first `#bake`.
Templates live in [references/templates.md](references/templates.md).

## Command dispatch

Read [references/commands.md](references/commands.md) for the full spec of each
command — it carries the exact field semantics, output formats, and edge cases.
Quick map:

| Command | Action |
|---------|--------|
| `#list` | Status dashboard: compact table of all features, sorted by Last Modified desc |
| `#focus [name]` | Pivot active feature; update pointers in GLOBAL_CONTEXT.md |
| `#track [name]` | Create a new feature epic + routing-map row |
| `#sub [name]` | Add a subfeature to the active feature |
| `#sub focus [name]` | Set active subfeature; bare `#todo/#rule/#doc` now target it |
| `#sub out` | Clear active subfeature pointer |
| `#bake` | Snapshot: update checkmarks + Last Modified, write CONTEXT_HYDRATION.md |
| `#todo [...]` | Three tiers (bare list / append / `target:` routing) |
| `#rule [...]` | Three tiers (bare list / append / `target:` routing) |
| `#status [name]` | Detailed single-feature dashboard |
| `#doc [...]` | Documentation subsystem (create/list/read/attach/detach) |
| `#help` | Print `docs/cmd_help.md` as a code block |

Cross-feature mentions (a non-active feature named in conversation) are
intercepted per the rules in commands.md.

## Fuzzy name resolution

All name lookups (`#focus`, `#track`, `#sub`, `#status`, `#doc`, mentions) use
the resolution cascade, applied in order:

1. **Exact (case-insensitive)** → accept silently.
2. **Substring** (query inside a candidate or vice versa) — unique match →
   accept silently. Multiple substring matches → treat as ambiguous, confirm.
3. **Closest by typo distance** — when nothing matches above, pick the nearest
   candidate by approximate edit distance. If it is an obvious near-miss (one or
   two characters off), confirm with "did you mean *X*?". If several are
   similarly close, present the top few and ask. If nothing is close, reject.

Outcomes: **accept** (proceed), **confirm** (ask before mutating any file), or
**reject** (no usable match — offer `#track` for features, `#help` for commands,
`#doc list` for docs). Detailed gate guidance is in
[references/fuzzy-resolve.md](references/fuzzy-resolve.md).

## Dates

Use ISO 8601 UTC dates (`YYYY-MM-DD`). Get the real current date from the
environment context — never invent one. Update a feature's `**Last Modified:**`
only when that feature was actually modified during the session.

## Operating discipline (from AGENTS.md)

- Be concise; answer the command directly, no unrequested expansion.
- Consolidate file edits — plan all changes to a file, then write once. Avoid
  write → check → rewrite cycles on the same file.
- Never auto-commit or push. Staging only when explicitly directed.
