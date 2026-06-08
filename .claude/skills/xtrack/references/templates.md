# xTrack File Templates

Templates for the xTrack memory stack. Fill placeholders in `[brackets]`. Use
ISO 8601 UTC dates (`YYYY-MM-DD`, no timestamps) from the environment — never
invent a date. On bootstrap (first tracking/focus command when `xTrack/` is
absent), create the directory and initialize `GLOBAL_CONTEXT.md`, the first
feature file, and `GLOBAL_TODOS.md`. Per-feature hydration files under
`xTrack/hydration/` are created lazily on first `#bake` of a feature.

---

## `xTrack/GLOBAL_CONTEXT.md`

```markdown
# Global Context — Routing Table

Root routing table and global state for this project's feature tracking.

## Active Session Pointers

- **Active Feature:** [Name | none]
- **Active Subfeature:** [name | none]
- **Last Updated:** [YYYY-MM-DD]
- **Last Bake:** [YYYY-MM-DD HH:mm | never]

## Routing Map

| Keyword | Feature File |
|---------|--------------|
| [keyword], [keyword] | FEATURE_SCOPE_[Name].md |

## Global Rules

- [global rule]

## Always-Loaded Context
These files are loaded into context at the start of every session to maximize the AI prefix-cache hit rate:
- `AGENTS.md` — canonical rulebook (all project rules + xTrack §7a/7b command spec)
- `.claude/skills/xtrack/SKILL.md` — skill dispatch map
- `.claude/skills/xtrack/references/fuzzy-resolve.md` — fuzzy lookup cascade
- `.claude/skills/xtrack/references/templates.md` — file templates
- `docs/cmd_help.md` — command reference summary
- `xTrack/GLOBAL_CONTEXT.md` — this file (routing table, active pointers, global rules)

## Global Instructions

- [optional cross-cutting instruction]
```

---

## `xTrack/FEATURE_SCOPE_[Name].md`

A YAML front-matter header (machine-readable: status, dates, counts) followed by
the prose body. `#features` reads only the front-matter; `#status` reads both.

```markdown
---
name: [Name]
status: active        # active | paused | done
created: [YYYY-MM-DD]
modified: [YYYY-MM-DD]   # equals created on #track; bumped by #bake when modified
active_subfeature: none
subs_total: 0
subs_done: 0
one_liner: [single concise sentence capturing the feature's purpose]
---

# Feature: [Name]

**Description:**
[Fuller description of the feature epic.]

## Subfeatures

### [SubName]  [ ]

#### Todos
- [ ] [todo]

#### Rules
- [rule]

#### Key Files
- `path/to/source` — [brief description]

#### Docs
- `docs/[name].md` — [brief description]

## Todos
- [ ] [parent-level todo]

## Rules
- [parent-level rule]

## Key Files
- `path/to/source` — [brief description]

## Docs
- `docs/[name].md` — [brief description]
```

Subfeature checkbox: `[ ]` open, `[x]` done. `#bake` recomputes `subs_total` /
`subs_done` in the front-matter from these checkboxes. `## Docs` holds attached
documentation (managed by `#doc attach`/`detach`); `## Key Files` holds source.

---

## `xTrack/GLOBAL_TODOS.md`

```markdown
# Global Todos

Cross-cutting todos not tied to a single feature. Easy to purge.

## Todos
- [ ] [todo]
```

---

## `xTrack/hydration/CONTEXT_HYDRATION_[Feature].md`

**One file per feature** (created/overwritten on `#bake` of that feature), so a
bake on one feature never clobbers another's resume state — safe under parallel
sessions. A ~200-word micro-state summary so the next session can resume cold.
Keep it tight and transactional — not a changelog.

```markdown
# Context Hydration — [Feature] — [YYYY-MM-DD]

**Active Subfeature:** [name | none]

## State
[2-4 sentences: what compiles, what's in progress, current statuses.]

## Target Files
- `path/to/file` — [why it's in play]

## Next Step
[The single most important next action.]
```
