# xTrack File Templates

Templates for the xTrack memory stack. Fill placeholders in `[brackets]`. Use
ISO 8601 UTC dates from the environment — never invent a date. On bootstrap
(first tracking/focus command when `xTrack/` is absent), create the directory
and initialize `GLOBAL_CONTEXT.md`, the first feature file, and `GLOBAL_TODOS.md`.
`CONTEXT_HYDRATION.md` is created lazily on first `#bake`.

---

## `xTrack/GLOBAL_CONTEXT.md`

```markdown
# xTrack — Global Context

Root routing table and global state for this project's feature tracking.

## Routing Map

| Keyword(s) | Feature File |
|------------|--------------|
| [keyword], [keyword] | FEATURE_SCOPE_[Name].md |

## Active Session Pointers

- **Active Feature:** [Name | none]
- **Active Subfeature:** [name | none]
- **Last Bake:** [YYYY-MM-DD | never]

## Global Rules

- [global rule]

## Global Instructions

- [optional cross-cutting instruction]
```

---

## `xTrack/FEATURE_SCOPE_[Name].md`

```markdown
# Feature: [Name]

**Status:** [Active | Paused | Done]
**Created:** [YYYY-MM-DD]
**Last Modified:** [YYYY-MM-DD]
**One-liner:** [single concise sentence capturing the feature's purpose]
**Active Subfeature:** [name | none]

**Description:**
[Fuller description of the feature epic.]

## Subfeatures

### [SubName]  [ ]

#### Todos
- [ ] [todo]

#### Rules
- [rule]

#### Key Files
- `path/to/file` — [brief description]

## Todos
- [ ] [parent-level todo]

## Rules
- [parent-level rule]

## Key Files
- `path/to/file` — [brief description]
```

Subfeature checkbox: `[ ]` open, `[x]` done. Toggle on `#bake` when complete.

---

## `xTrack/GLOBAL_TODOS.md`

```markdown
# Global Todos

Cross-cutting todos not tied to a single feature. Easy to purge.

## Todos
- [ ] [todo]
```

---

## `xTrack/CONTEXT_HYDRATION.md`

Created/overwritten on `#bake`. A ~200-word micro-state summary so the next
session can resume cold. Keep it tight and transactional — not a changelog.

```markdown
# Context Hydration — [YYYY-MM-DD]

**Active Feature:** [Name]
**Active Subfeature:** [name | none]

## State
[2-4 sentences: what compiles, what's in progress, current statuses.]

## Target Files
- `path/to/file` — [why it's in play]

## Next Step
[The single most important next action.]
```
