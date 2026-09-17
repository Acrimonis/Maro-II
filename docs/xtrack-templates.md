<!-- scope: reference -->
# xTrack File Templates

> Sub-truth pointed from `AGENTS.md`'s Lazy-Load Index. Rules live in `AGENTS.md`; this file holds the
> file shapes only. Its former home, `.claude/skills/xtrack/references/templates.md`, was removed —
> adapters carry pointers, never content (§8a).

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

> State only — routing map, feature summaries, focus history, global todos, doc index. Rules and instructions live in `AGENTS.md`.

## Focus History
- [YYYY-MM-DD HH:mm UTC] [Feature] — [one-liner] → xTrack/[Feature]/FEAT_HYD_[Feature].md

## Routing Map

| Keyword | Feature File |
|---------|--------------|
| [keyword], [keyword] | FEAT_DSC_[Name].md |

## Feature Summaries

| Feature | One-Liner | Created | Modified | Status |
|---------|-----------|---------|----------|--------|
| [Name] | [One sentence purpose] | [YYYY-MM-DD HH:mm] | [YYYY-MM-DD HH:mm] | active |

## Global Todos

- [ ] [cross-cutting todo]

## Cross-Reference Docs
Docs available via `#doc read [name]` from any feature. Fuzzy-resolve searches this table.

| Doc | Owner Feature | One-Liner |
|-----|---------------|-----------|
| `[doc-name].md` | [Feature] | [one-liner] |
```

---

## `xTrack/[Feature]/FEAT_DSC_[Name].md`

A YAML front-matter header (machine-readable: status, dates) followed by
the prose body. The `one_liner` lives in the
`## Feature Summaries` table in `GLOBAL_CONTEXT.md`.

```markdown
---
name: [Name]
status: active        # active | paused | done
created: [YYYY-MM-DD HH:mm]
modified: [YYYY-MM-DD HH:mm]   # equals created on #track; bumped by #bake when modified
---

# Feature: [Name]

**Description:**
[Fuller description of the feature epic.]

## Sections

### [SectionName]

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

## Walk
**Level 1 — Date:** [YYYY-MM-DD] · **Source:** [pending set / named plan] · **Active:** [n]
- [ ] 1 · [item]
- [ ] 2 · [subject] — summary; child walk open

**Level 2 — Date:** [YYYY-MM-DD] · **Parent:** 2 · **Active:** [n]
- [ ] 1 · [sub-item]

[Exhaustion closes a level: one bullet summary naming resolutions and drops, written into the parent
item at level 2 and into the digest's `## Outcome` at level 1. A closed child renders as
`- [x] n · [subject] — child walk closed: [one-line resolution, drops named]`. Never cleared, never
trimmed, kept while any level is open — the fold and `#archive`'s retirement both wait.]

## Implemented
- [one-liner of what shipped] → [FEAT_PLN_* / FEAT_DOC_* pointer]
- [planless one-liner, no pointer]
```

`#bake` updates the `## Feature Summaries` table in `GLOBAL_CONTEXT.md`
from each feature's sections and `## Implemented`.
`## Docs` holds attached documentation (managed by `#doc attach`/`detach`);
`## Key Files` holds source file paths.

---

## `FEAT_HYD_[Feature].md`

**One file per feature** (created/overwritten on `#bake` of that feature), stored
at `xTrack/[Feature]/FEAT_HYD_[Feature].md`. A ~200-word micro-state summary so
the next session can resume cold. Keep it tight and transactional — not a changelog.

**This block is the shape's only statement.** The title form, the order of the header lines and the
section list below are the whole of it, and no second title or section list is used anywhere. `#bake`
rewrites the file whole, so a file written before a change here converges at its next bake, and one
whose last bake predates the `Directive trace:` line gains it then, carried forward from that run —
never back-filled by hand, the session it would describe having already ended.

```markdown
# Context Hydration — [Feature] — [YYYY-MM-DD]

**Last Bake:** [YYYY-MM-DD HH:mm UTC] — written by `#bake`; absence means never baked

**Directive trace:** [one sentence: which of the five covered action classes were met since the last
bake and whether any stopped. Written by `#bake` from the session and carried forward — a state, never
a changelog.]

## State
[2-4 sentences: what compiles, what's in progress, current statuses.]

## Target Files
- `path/to/file` — [why it's in play]

## Next Step
[The single most important next action.]
```

---

## `xTrack/[Feature]/xxArchive/INDEX.md`

One index per archive folder — the only file `#archive` reads, and the only entry point for retired
material. Cross-cutting retirements use `docs/xxArchive/INDEX.md` with the same columns.

```markdown
| File | Created | Archived | Status | Summary | Tags | Superseded-by |
|------|---------|----------|--------|---------|------|---------------|
| `[YYMMDD]_FEAT_PLN_[Feature]_[topic].md` | [YYYY-MM-DD] | [YYYY-MM-DD] | shipped | [one line: what it was and what came of it] | [tags] | — |
```

`Status` is one of `shipped` · `superseded` · `promoted`. Abandoned material is deleted, never filed
here.

---

## `FEAT_DOC_[Feature]_[name].md`

Feature-scoped reference documentation (created by `#doc create` when
"Feature-scoped" is chosen). Scope tag is always `feature`.

```markdown
<!-- scope: feature -->
# [Title]

[Content]
```

---

## `YYMMDD_FEAT_PLN_[Feature]_[topic].md`

Feature-scoped plan / design discussion file. **MUST be created in
`xTrack/[Feature]/`.** The `YYMMDD` prefix is the creation date (from git history or filesystem).
Scope tag is `feature`.

```markdown
<!-- scope: feature -->
# [Topic]

[Content]

## Outcome
[Appended once at completion: what actually shipped + deviations from plan.]
```
