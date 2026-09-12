<!-- scope: feature -->
# Command Flow — Rules & the Four Surviving Commands

**Feature:** WorkflowImprovement · **Date:** 2026-09-12 · **Branch:** `feature/wrKFl` · **Status:** reviewed — six walk resolutions applied, awaiting implementation
**Origin:** two walks over this session — eight items on the interaction flow, then seven on the Ask review's findings — resolved one point at a time and folded below. Items 1–6 of the second walk changed the design; item 7 found nothing to change.

## Problem

Two failures with one shared cause. The Output Contract's brevity rule — "minimum viable communication" — is an adjective with no unit, threshold or check, so it decayed the moment it was written. And the session's most-used interaction verbs — stepping through items, asking for an independent review, granting permission — had no names, so they were re-typed as prose every time.

## Part 1 — Rules (no registry cost)

### 1. Output Contract

```
## Output Contract

- **📏 Bullets are the unit.** One bullet = one idea, and a bullet stays within roughly two
  sentences — needing a third means it is two bullets. No limit on how many bullets a reply has.
- **🏗️ Long content lives in files.** No headings, no tables, no nested bullets in a reply; lists,
  tables and code that do not fit the cap belong in the file the reply points at.
- **🩹 Clarity exception.** A bullet may exceed the cap only to *contain* something — a list, a table,
  a code block. Padding prose never qualifies.
- **🎯 Answer first.** The first bullet is the answer, so a reader who stops after it has the result.
- **🔍 Item-by-item reviews.** List every item as a flat marker of under ten words, then expand only
  the active one into a full paragraph, so the reply stays the same size as the list shortens.
- **📋 Report only what changed.** If the tool output already answered the request, emit only `Done.`
  For multi-step changes, add an ELIJP — one or two plain sentences on purpose, jargon stripped.
  Recommendations state the strongest objection to themselves, or say none was found, and a question
  is asked only when its answer changes what happens next.
- **🎯 Answer only what was asked, then stop.** No next steps, no follow-ups. Once the interaction has
  reached natural conclusion you MAY add one future-direction bullet at the very end.
```

Three named **optional parts** — ELIJP, containment blocks, verification lists — because `#brief` subtracts exactly those, which is why this contract ships before that command. The objection and ask-threshold sentences are **not** optional parts: they ride the reporting bullet, so `#brief` never subtracts them.

The threshold is the whole test — it governs bare-command guessing ("in doubt ask"), unprompted questions and recommendation framing alike, so it needs no rule of its own.

### 2. Challenge instead of assuming

```
- **🎯 Challenge instead of assuming.** When a directive is ambiguous or merely implies approval, stop and ask rather than picking a reading and proceeding.
```

Deliberately **not** unified with MODE LOCK: MODE LOCK owns authorisation — may I start? — while this owns ambiguity and presentation of recommendations — what should happen next? Each states its own scope; neither claims to be the general form of the other.

### 3. Gate rule

```
- **🚦 A gate names its action.** A confirmation gate states the exact action it authorises, and if the proposal has moved since the question was asked the gate is re-asked rather than assumed.
```

This gives the walk's exit challenge, the session-close challenge and the "Asks before committing / pushing" rows one wording, generalising the latter without editing them.

### 4. The review cascade

`#review` resolves in order: (1) the live walk's active item; (2) the active feature's plan still in design; (3) the last `#implement` run's `## Target Files`, if that plan is implemented; (4) with no artefact at all, the live proposal — an in-place challenge. It prints "Reviewing X because Y" before starting. A challenge is deliberately weaker than a review, since the same agent argues against itself.

### 5. Walk state and exit rules

The walk lives in the feature file's `## Walk` section — **not** the hydration, which `#bake` regenerates — and carries its own date. Exhaustion closes it with a bullet summary of resolutions and anything dropped; any exit with points open triggers a challenge (close / resume / park, where parking promotes the open points into the digest's `## Outcome`); `#bake` snapshots but never clears it; an open walk is reported at session start and again before the session closes.

An open walk blocks both gates that would otherwise lose it: `#bake`'s fold, since the section is kept while it holds open points, and `#archive`'s retirement, where it joins the digest floor as a fourth gate carrying the same three exits. §7a states the fold criterion **once** — keep a section while it holds an open todo, a retained rule, a doc/key-file mapping *or an open walk* — and `cmd_help_bake.md`'s C12 becomes a pointer to §7a rather than a second statement of it.

No `#doctor` check: the read path already surfaces it, and a lint would be a second witness for something the first covers — the same reasoning that kept "challenge" out of the registry.

## Part 2 — The four surviving rows

| Command | Purpose | Forms |
|---|---|---|
| `#go` | Agree with the question currently open | bare = agree, and re-asks if the proposal moved since the question; `#go impl` = agree and run the pipeline |
| `#review [target]` | Ask hop over the resolved target, or an in-place challenge when none resolves | bare follows the cascade; a target is fuzzy-resolved |
| `#walk [source]` · `#next` · `#prev` · `#skip` · `#done` | Cursor over an enumerated set, expanding one item at a time | bare `#walk` takes the pending set; a name or ID starts there |
| `#brief` · `#full` | Output mode — subtracts the contract's three optional parts | bare prints the current mode; the mode is session-lived and `#focus` resets it |

Rows stay neighbour-length: check (k) is an existence lint — row ↔ page ↔ `cmd_help.md` line — never a wording diff, so a row carries the one-line action and its page is the sole full spec. The Purpose and Forms columns above are design notes and never travel to the registry.

## Part 3 — Dropped, and why

`#plan` failed the test "a command must add capability the context cannot imply": planning mode is implied by the feature in focus, and extend-versus-new is a judgement the agent already makes. Its residues become rules — the design/implemented phase test, and "extend the plan on continuation, start a new one on a context switch".

`#challenge` fails the same test and becomes rule 2, with the manual trigger preserved as the cascade's fourth fallback — which is also why it needs no `#ask` alias: that would name the mechanism rather than the job.

## Part 4 — Registry footprint

Four rows, each needing a §7b row, a `docs/cmd_help_*.md` page and a `cmd_help.md` line; check (k) enforces those three surfaces together. `#doctor` stays **a–r** — no new check, no range churn. `templates.md` gains the `## Walk` section. `#review` needs two guard lines — one in `cmd_help_implement.md`, one on its own page — so a running pipeline keeps ownership of its Ask hop and nothing else performs a review mid-run. `#brief`'s page owns the mode definition, including that it is session-lived and reset by `#focus`; the `#focus` row and the hydration are untouched.

## Part 5 — Ship order

1. Rules 1–5 — no rows, and fixes today's verbosity complaint immediately
2. `#go`
3. `#review`, with the guard lines
4. `#walk` and its four facets
5. `#brief` / `#full`, once the contract's optional parts exist

If only two commands ship, they are `#go` and `#review` — both wrap mechanics that already exist.

## Out of scope

The ~198-file plan backlog. Housekeeping: the archive pass is uncommitted, and mixing two change sets in one working tree makes both harder to review, so its commit lands before this work starts.
