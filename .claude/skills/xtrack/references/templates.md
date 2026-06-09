# xTrack File Templates

Templates for the xTrack memory stack. Fill placeholders in `[brackets]`. Use
ISO 8601 UTC dates (`YYYY-MM-DD HH:mm`) from the environment — never
invent a date. On bootstrap (first tracking/focus command when `xTrack/` is
absent), create the directory and initialize `GLOBAL_CONTEXT.md` and the first
feature file. Per-feature hydration files (`FEAT_HYD_*.md`) are created lazily
on first `#bake` of a feature.

---

## `xTrack/GLOBAL_CONTEXT.md`

```markdown
# Global Context — Routing Table

## Active Session Pointers
- **Active Feature:** [Name | none]
- **Active Subfeature:** [name | none]
- **Last Updated:** [YYYY-MM-DD]
- **Last Bake:** [YYYY-MM-DD HH:mm | never] ([Feature] — [brief note])

## Routing Map

| Keyword | Feature File |
|---------|--------------|
| [keyword], [keyword] | FEAT_DSC_[Name].md |

## Feature Summaries

| Feature | One-Liner | Created | Modified | Status |
|---------|-----------|---------|----------|--------|
| [Name] | [One sentence purpose] | [YYYY-MM-DD HH:mm] | [YYYY-MM-DD HH:mm] | active |

## Global Rules

- [global rule]

## Global Todos

- [ ] [cross-cutting todo]

## Always-Loaded Context
These files are loaded into context at the start of every session to maximize the AI prefix-cache hit rate:
- `AGENTS.md` — canonical rulebook (all project rules + xTrack §7a/7b command spec)
- `xTrack/GLOBAL_CONTEXT.md` — this file (routing table, feature summaries, global todos, global rules)
- `.claude/skills/xtrack/SKILL.md` — skill dispatch map

## Global Instructions
- [optional cross-cutting instruction]
```

---

## `xTrack/FEAT_DSC_[Name].md`

A YAML front-matter header (machine-readable: status, dates) followed by
the prose body. `one_liner` and subfeature completion live in the
`## Feature Summaries` table in `GLOBAL_CONTEXT.md`.

```markdown
---
name: [Name]
status: active        # active | paused | done
created: [YYYY-MM-DD HH:mm]
modified: [YYYY-MM-DD HH:mm]   # equals created on #track; bumped by #bake when modified
active_subfeature: none
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
- `FEAT_DOC_[Feature]_[name].md` — [brief description]

## Todos
- [ ] [parent-level todo]

## Rules
- [parent-level rule]

## Key Files
- `path/to/source` — [brief description]

## Docs
- `FEAT_DOC_[Feature]_[name].md` — [brief description]
```

Subfeature checkbox: `[ ]` open, `[x]` done. `#bake` updates the
`## Feature Summaries` table in `GLOBAL_CONTEXT.md` from these checkboxes.
`## Docs` holds attached documentation (managed by `#doc attach`/`detach`);
`## Key Files` holds source file paths.

---

## `FEAT_HYD_[Feature].md`

**One file per feature** (created/overwritten on `#bake` of that feature), stored
at project root — so a bake on one feature never clobbers another's resume state
(safe under parallel sessions). A ~200-word micro-state summary so the next
session can resume cold. Keep it tight and transactional — not a changelog.

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

---

## `FEAT_DOC_[Feature]_[name].md`

Feature-scoped reference documentation (created by `#doc create` when
"Feature-scoped" is chosen, or by `#doc sync`). Scope tag is always `feature`.

```markdown
<!-- scope: feature -->
# [Title]

[Content]
```

---

## `FEAT_PLN_[Feature]_[topic].md`

Feature-scoped plan / design discussion file (migrated from `plans/*.md` or
created by `#doc create` when "Feature-scoped" is chosen with a plan topic).
Scope tag is `feature`.

```markdown
<!-- scope: feature -->
# [Topic]

[Content]
```
