<!-- scope: feature -->
# Subfeature Context — Implementation Plan

**Status:** Approved
**Created:** 2026-06-03T13:16:00.000Z

## Summary

Subfeatures (currently flat `- [ ] name` checkboxes) are promoted to full subsections within the parent feature file, with their own todos, rules, and key files. A new focus mechanism allows drilling into a subfeature, making it the active scope for all tracking commands.

---

## File Structure Change

### Before (current)
```markdown
## Subfeatures
- [ ] trace
- [ ] rendering
```

### After (new)
```markdown
## Subfeatures

### trace  [-]
One-liner or design notes here.

#### Todos
- [ ] implement KDTree coastline index

#### Rules
- Must use memory-mapped I/O

#### Key Files
- app/src/main/java/.../CoastlineRepository.kt

### rendering  [ ]
...
```

### New header field
```markdown
**Active Subfeature:** trace
```
Absent or empty when no subfeature is focused.

---

## Command Changes

### New commands

| Command | Action |
|---|---|
| `#sub focus [name]` | Fuzzy-resolve subfeature name, set `**Active Subfeature:**` pointer |
| `#sub out` | Clear the pointer, return to parent feature scope |

### Scope-aware bare commands

| Bare command | Subfeature focused | No subfeature focused |
|---|---|---|
| `#sub` | Lists subfeatures, active marked `← focused` | Lists subfeatures (no marker) |
| `#todo` | Lists subfeature's `#### Todos` | Lists parent's `## Todos` |
| `#rule` | Lists subfeature's `#### Rules` | Lists parent's `## Rules` |
| `#doc` | Lists subfeature's `#### Key Files` | Lists parent's `## Key Files` |
| `#status` | Shows parent + expands active subfeature details | Shows parent + all subfeatures collapsed |

### Scope-aware write commands

| Command | Subfeature focused | No subfeature focused |
|---|---|---|
| `#todo [desc]` | Appends to subfeature `#### Todos` | Appends to parent `## Todos` |
| `#rule [desc]` | Appends to subfeature `#### Rules` | Appends to parent `## Rules` |
| `#doc attach [name]` | Appends to subfeature `#### Key Files` | Appends to parent `## Key Files` |

### Target hierarchy (for `#todo`, `#rule`, `#doc attach`)

| Target | Where it writes |
|---|---|
| *(bare, no target)* | Active scope (subfeature if focused, else parent feature) |
| `parent:` | One level up — parent feature if sub focused, global if feature focused |
| `[feature]:` | That feature's active scope (its subfeature if it has one, else parent) |
| `global:` | `GLOBAL_TODOS.md` / `## Global Rules` |

### Unchanged commands

`#track`, `#focus [name]`, `#sub [name]`, `#bake`, `#list`, `#help`, `#doc list`, `#doc create`, `#doc read`, `#doc detach`

---

## Auto-Completion Rule

When all `#### Todos` under a subfeature are `[x]`, the subfeature's H3 checkbox auto-toggles to `[x]` during `#bake` or `#status`.

---

## Implementation Steps

1. Update `.clinerules` — add subfeature focus/out commands, scope-aware rules, and `parent:` target to the command reference section
2. Update [`docs/cmd_help.md`](docs/cmd_help.md) — reflect new commands and scope-aware behavior
3. Migrate existing flat subfeatures in all `FEATURE_SCOPE_*.md` files to the new subsection format (backward-compatible: `- [ ] name` → `### name  [ ]`)

