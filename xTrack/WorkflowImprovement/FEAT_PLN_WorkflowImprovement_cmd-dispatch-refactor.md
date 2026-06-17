<!-- scope: feature -->
# Command Lookup — Dispatched Architecture Refactor

## Problem

`#help [cmd]` lookup is **monolithic**: AGENTS.md §7b.8 hardcodes a flat command list and `docs/cmd_help_*.md` mappings. Adding/renaming/removing commands requires updating **3 places**: inline list, handler item, help file.

## Target: File-Dispatched Lookup

`#help [cmd]` fuzzy-resolves against `docs/cmd_help_*.md` **filenames** instead of an inline list. Remove the inline list from AGENTS.md.

## Implementation Steps

### 1. Rewrite AGENTS.md §7b.8

**Current** (74 words):
> `#help [cmd]`, fuzzy-resolve `[cmd]` against `(now, list, status, track, focus, sub, todo, rule, doc, bake, help, doctor, new, the-c-word, push, move, cherry, copy, rename, implement)` where `status diff` and `doc audit` are sub-commands, then read `docs/cmd_help_[cmd].md`. Sub-commands: `status diff` → `cmd_help_status.md`, `doc audit` → `cmd_help_doc_audit.md`.

**Target** (32 words, -57%):
> `#help [cmd]`: scan `docs/cmd_help_*.md` filenames, strip `cmd_help_` prefix + `.md` suffix, fuzzy-resolve `[cmd]` against remaining names, read matching file. Sub-commands read parent file.

### 2. Handle shared git help files

Git commands (`new`, `push`, `move`, `cherry`, `copy`, `rename`, `merge`, `commit`) all share `docs/cmd_help_git.md`. Rule: if no dedicated `docs/cmd_help_[cmd].md` exists, fall back to `docs/cmd_help_git.md`.

### 3. Create missing files

- `docs/cmd_help_implement.md` — new

### 4. Delete deprecated files

- `docs/cmd_help_doc_sync.md` — already deprecated

## Files Changed

| File | Change |
|---|---|
| `AGENTS.md` §7b.8 | Rewrite: remove inline list, filename-scan dispatch |
| `docs/cmd_help_implement.md` | Create |
| `docs/cmd_help_doc_sync.md` | Delete |

## Convention: How to Add / Modify a Command

### Adding a new command

1. Add a numbered handler item to AGENTS.md §7b (e.g., `17. **New Command...**`)
2. Create `docs/cmd_help_[command].md` with usage description
3. Add a one-line entry to `docs/cmd_help.md` reference table
4. ✅ No inline list to update — dispatch is filename-based

### Renaming a command

1. Rename the handler item header in AGENTS.md §7b
2. Rename `docs/cmd_help_[old].md` → `docs/cmd_help_[new].md`
3. Update the one-line entry in `docs/cmd_help.md` reference table
4. ✅ No inline list to update

### Removing a command

1. Remove the handler item from AGENTS.md §7b
2. Delete `docs/cmd_help_[cmd].md`
3. Remove the entry from `docs/cmd_help.md` reference table
4. ✅ No inline list to update

## Token Impact

- §7b.8: 74→32 words (-57%)
- Future commands: create help file + handler item only — no inline list
