<!-- scope: feature -->
# Plan: `#help [cmd]` Extension

## Goal

Extend the `#help` command so `#help doc`, `#help track`, etc. display a detailed, human-readable explanation of that command's functionality — not just the one-liner from the reference table.

## Design

### 1. File: `docs/cmd_help.md` — Add detailed sections

Append `## <cmd-name>` sections **after** the existing reference table. Each section contains a full prose explanation of the command, its variants, aliases, and usage examples.

**Section headers** use the full command name with or without `#` prefix:
- `## #doc` — explains bare `#doc`, `#doc list`, `#doc create`, `#doc read`, `#doc attach`, `#doc detach`
- `## #track` — explains feature creation, YAML front-matter, description prompt, routing row
- `## #features` / `## list` — explains the dashboard, sort order, active marker
- `## #feature` — current context orientation block
- `## #focus` — switching active feature
- `## #sub` — subfeature CRUD, focus/out, scope-aware commands
- `## #todo` — 3-tier scope-aware todo management
- `## #rule` — 3-tier scope-aware rule management
- `## #bake` — memory snapshot, hydration, lifecycle
- `## #help` — itself (and `#help [cmd]`)
- `## #doctor` — lint checks, fix, auto-repairable vs report-only
- `## #status` — single-feature dashboard

**Section content style** (example for `## #doc`):
```
## #doc

Manage documentation attached to a feature.

Subcommands:
  [no param] Display docs attached to the active scope
  list       Scan docs/**/*.md and print a scope-grouped table
  create     Create docs/name.md and prompt for a scope tag
  read       Fuzzy-resolve name and load the doc into AI context
  attach     Link an existing doc to the active feature's ## Docs
  detach     Unlink a doc from the active feature's ## Docs

Bare `#doc` with a focused subfeature shows that subfeature's docs only.
`#doc attach` without a name shows a pick-list of unattached docs.
Scope tags: core, onboarding, feature, reference, archived.
```

### 2. File: `AGENTS.md` §7b.8 — Update the `#help` command spec

**Current (line 76):**
```
  8. **Command Reference:** If the user states `#help`, read and print the contents of `docs/cmd_help.md` as a code block. This file must be kept up to date whenever xTrack commands are added, renamed, or removed.
```

**New behavior:**
```
  8. **Command Reference:** If the user states `#help` (bare), read and print the entire contents of `docs/cmd_help.md` as a code block. If the user states `#help [cmd]`, fuzzy-resolve `[cmd]` against the known command list (features, feature, status, track, focus, sub, todo, rule, doc, bake, help, doctor, list — where `list` is an alias for `features`), then find the matching `## <cmd-name>` section in `docs/cmd_help.md` and print only that section's content as a code block. If no match is found, print the full reference table and suggest the closest match. `docs/cmd_help.md` must be kept up to date whenever xTrack commands are added, renamed, or removed.
```

### 3. Fuzzy-resolution mapping

The command name lookup supports these inputs:

| Input | Section | Notes |
|-------|---------|-------|
| `features`, `list` | `## #features` | `list` is an alias |
| `feature` | `## #feature` | Singular |
| `status` | `## #status` | |
| `track` | `## #track` | |
| `focus` | `## #focus` | |
| `sub` | `## #sub` | Also matches `sub focus`, `sub out` |
| `todo` | `## #todo` | |
| `rule` | `## #rule` | |
| `doc`, `docs` | `## #doc` | |
| `bake` | `## #bake` | |
| `help` | `## #help` | |
| `doctor` | `## #doctor` | |

Fuzzy resolution follows the existing protocol (§7b): exact → substring → edit distance ≈1–2 confirm → reject.

## Implementation Steps

| # | Step | Files | Description |
|---|------|-------|-------------|
| 1 | Write detailed sections | `docs/cmd_help.md` | Append `## <cmd>` sections after the reference table, one per command |
| 2 | Update AGENTS.md spec | `AGENTS.md` | Rewrite §7b.8 to define `#help [cmd]` behavior |
| 3 | Verify | — | Run `#help doc`, `#help help`, `#help features` and confirm output |

## Files Changed

- `docs/cmd_help.md` — append ~12 detailed command sections
- `AGENTS.md` — update line 76 (§7b.8)

## Out of Scope

- Creating separate files per command (too heavy; single-file sections suffice)
- Interactive `#help` with pagination (not needed for AI-consumed output)
- Multilingual help text

