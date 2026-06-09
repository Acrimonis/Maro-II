---
name: xtrack
description: >-
  xTrack feature-tracking command system for this project. Use this skill
  WHENEVER the user types any "#"-prefixed command — #track, #focus, #bake,
  #todo, #rule, #doc, #context, #status, #help, #sub, and their variants (#focus
  sub, #focus out, #doc create/list/read/attach/detach/sync/audit, #todo global:/parent:,
  #rule global:/parent:) — or asks to track a feature, switch active focus,
  snapshot/bake session state, hydrate context from a prior session, or manage
  todos / rules / documentation. Trigger even on a bare "#focus" or "#todo",
  and when the user mentions a feature name in passing that may match a tracked
  feature. The skill reads and writes the existing xTrack/ and root-level FEAT_*
  files defined in AGENTS.md — it does not relocate or rename anything.
---

# xTrack — Feature Tracking Command System

This skill executes the `#`-prefixed command system defined in the project's
**`AGENTS.md`** (§ 7b). It is the operational engine for the **xTrack memory stack**:
a set of markdown files under `xTrack/` (and root-level `FEAT_*` files) that hold
feature epics, todos, rules, and transactional session state.

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
| `xTrack/GLOBAL_CONTEXT.md` | Root routing table, feature summaries, global todos, global instructions, active session pointers, global rules |
| `xTrack/FEAT_DSC_[name].md` | One feature epic — YAML front-matter (status, dates) + body (description, subfeatures, todos, rules, key files, docs) |
| `FEAT_HYD_[Feature].md` | Per-feature micro session transactional state at project root (created lazily on first `#bake` of a feature) |
| `FEAT_DOC_*.md` | Feature-scoped reference documentation |
| `FEAT_PLN_*.md` | Feature-scoped plan / design discussion files |
| `docs/**/*.md` | Cross-cutting reference documentation (recursive), each tagged `<!-- scope: ... -->` near the top |

**Bootstrap:** If `xTrack/` does not exist when any tracking or focus command is
issued, auto-create it and initialize `GLOBAL_CONTEXT.md` and the feature file.
Per-feature hydration files (`FEAT_HYD_*.md`) are created lazily on first `#bake`
of a feature.
Templates live in [references/templates.md](references/templates.md).

## Command dispatch

**The canonical command spec is `AGENTS.md` (§ 7b).** Read it there for the full
semantics, output formats, and edge cases. `references/commands.md` is deprecated
and will be removed — do not rely on it.
Quick map:

| Command | Action |
|---------|--------|
| `#context` | Lightweight orientation: active feature/sub + working path + subfeatures list |
| `#context list` | (alias `#features`) Compact feature table from GLOBAL_CONTEXT.md Feature Summaries, sorted by modified desc |
| `#focus [name]` | Pivot active feature; update pointers in GLOBAL_CONTEXT.md |
| `#focus sub [name]` | Set active subfeature; bare `#todo/#rule/#doc` now target it |
| `#focus out` | Clear active subfeature pointer |
| `#track [name]` | Create a new feature epic + routing-map row + summaries table row |
| `#sub [name]` | Add a subfeature to the active feature |
| `#sub` | List subfeatures with ← focused marker |
| `#bake` | Snapshot: update checkmarks, refresh summaries table + front-matter modified, write FEAT_HYD_ |
| `#todo [...]` | Three tiers (bare list / append / `target:` routing) |
| `#rule [...]` | Three tiers (bare list / append / `target:` routing) |
| `#status [name]` | Detailed single-feature dashboard (subfeature-scoped when focused) |
| `#status diff [name]` | Diff vs FEAT_HYD_ hydration baseline |
| `#doc [...]` | Documentation subsystem (create/list/read/attach/detach/sync/audit) → feature `## Docs`, FEAT_DOC_/FEAT_PLN_/docs/** |
| `#help` | Print `docs/cmd_help.md` as a code block |
| `#doctor` | Lint the xTrack stack for drift; `#doctor fix` auto-repairs the safe classes |

Cross-feature mentions (a non-active feature named in conversation) are
intercepted per the rules in AGENTS.md.

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

Use ISO 8601 UTC dates (`YYYY-MM-DD HH:mm`). Get the real current date from the
environment context — never invent one. Update a feature's `**modified:**` in
front-matter only when that feature was actually modified during the session.

## Operating discipline (from AGENTS.md)

- Be concise; answer the command directly, no unrequested expansion.
- Consolidate file edits — plan all changes to a file, then write once. Avoid
  write → check → rewrite cycles on the same file.
- Never auto-commit or push. Staging only when explicitly directed.
