<!-- scope: feature -->
# `#archive sweep` — plan retirement by enumeration and recommendation

**Created:** 2026-09-19 · **Revised:** 2026-09-19 after review (§12) · **Feature:** WorkflowImprovement ·
**Status:** in design · **Branch:** `feature/archive`

## 1. Problem

Plans and attached docs accumulate in `xTrack/[Feature]/` and nothing disposes of them. The corpus computes
exactly one candidate class — a plan still attached in `## Docs` whose work already appears in
`## Implemented` — reported by `#bake` step 8 and nudged report-only by `#doctor`. Neither offers a
disposition, so nothing happens, and every other kind of stale file — superseded, orphaned,
never-executed — is invisible to both.

The goal is a feature-scoped spring cleaning: enumerate the plans **with their associated docs**, state
each one's description, relevance and status, recommend an action per row, and dispose of them without
opening thirty files by hand.

## 2. Scope

In:

- one new sub-command family on `#archive`: `sweep`, `sweep all`, `sweep [state]`
- the candidate predicate widened from one class to a state per file kind
- the enumeration unit: a plan together with its associated doc
- the enumeration and recommendation render, with a session decision log
- the three actions, with promotion folded into archiving
- one home for the predicate, `#bake` and `#doctor` pointing at it

Out:

- **deletion** — withdrawn in discussion. `#archive` moves material; it never erases. The clause that
  abandoned material is deleted rather than filed governs what must not be *filed*, so abandoned files
  are archived like anything else, where nothing reads them.
- any persisted sweep state: no cursor, no `## Walk` level, no index column
- an `audit` form: `#doctor` already owns index-versus-disk drift
- a standalone `promote` form: promotion is the first branch of the archive action
- moving a file from `sweep all`: the cross-feature pass reports, disposition stays per-feature

## 3. Decisions

- **D1** — `delete` is cut from the command surface; `sweep` is the only reserved word added.
- **D2** — the enumeration unit is a plan plus its associated doc; an unpaired doc is its own row.
- **D3** — the enumeration covers every plan and attached doc of the feature, date ascending, not only
  the candidates.
- **D4** — every row carries a description, its relevance and its status; the table is a containment
  block, so the ten-word marker cap does not apply to it.
- **D5** — a recommendation is printed on every row, while a gate opens only on rows whose recommendation
  is to act; a feature with thirty rows then costs one answer per row that needs one.
- **D6** — the actions are three: close or leave open points · mark as to follow up · archive. Only the
  third moves a file.
- **D7** — the follow-up mark is one feature `## Todos` line, `[section]:follow-up <file> — <condition>`,
  using the `[target]:[desc]` form §7b already gives `#todo`; no new store, and the open todo also keeps
  the owning section alive under §7a's keep-criterion.
- **D8** — promotion is folded into the archive action: where the file still defines current behaviour the
  rule goes to `## Rules` and the decisions to `FEAT_DOC_[Feature]_decisions.md` before the move, and the
  gate names that as part of the action rather than as a fourth option.
- **D9** — no cursor is persisted. The printed table is the state surface: the sweep re-renders it on each
  invocation and after each disposition, decided rows marked, and closes the render with a one-line
  decision log — `decided: A archived · B left · C follow-up` — which is session-lived and never written
  to a file.
- **D10** — the pending set is derived, not remembered: it is recomputed from disk (a moved file has left
  `## Docs`, a marked one has a new `## Todos` line) and then minus the session's decisions. Executed
  decisions are therefore durable by side effect; only a `leave` is volatile, which is Q1.
- **D11** — the sweep's real cost is one read per enumerated file. The feature file plus one citation grep
  produce the rows, their order and their relevance; the description and the recommendation need that file
  read, so a sweep is priced per file and `sweep [state]` exists to narrow the set before the reads.

## 4. Command surface

```
#archive sweep                    read-only scan of the active feature: every plan with its
                                  associated doc, date ascending, description, relevance,
                                  status and a recommendation per row
#archive sweep all                read-only report across every feature, counts only,
                                  stalest feature first — the order #list already owns,
                                  read bottom-up
#archive sweep shipped|superseded|orphaned|orphaned-doc
                                  narrow the enumeration to one state
```

`#archive`, `#archive [name]`, `#archive search [terms]` / `search all` and `#archive restore [name]` keep
their present behaviour. `sweep` joins `search` and `restore` as a reserved word matched exactly and
resolved before `[name]`, the same clause [`#rule`](AGENTS.md:194) carries, or a plan whose topic is
`sweep` would become unreachable. The detail stays on the archive page, so the entry point is
`#help archive` — `#help sweep` has no page stem to resolve.

## 5. Workflow

```
0 preflight   report an open walk level in the feature. The enumeration still runs; only the
              dispositions wait, and the challenge names the walk's three exits
1 scan        read the feature file — ## Docs, ## Implemented, sections, ## Walk — then one
              citation grep for the file names across the feature folder and its INDEX.md
2 pair        group each plan with the doc whose filename its text names; an unpaired doc
              stands alone. Resemblance is not evidence
3 classify    plans: shipped-gone · superseded · orphaned · live, where live means in design,
              cited by a live section, or holding an open todo — never proposed
              docs: referenced · orphaned-doc, a doc no live file cites
4 order       date ascending
5 render      the table; one expanded item carrying Why it is here, What closing it means and
              Open question, plus its recommendation and the objection to that recommendation;
              the decision-log line last
6 dispose     one row at a time on the user's word: close or leave open points · mark as to
              follow up · archive — every gate naming the file and the exact action
7 execute     archive = ## Outcome (which is what satisfies the digest floor), index row per
              file moved, ## Docs detach, ## Implemented pointer drop, move, promotion first
              where the qualifying question answers yes;
              follow-up = one ## Todos line; leave = nothing is written
8 re-render   the table with the decided row marked, then the decision log extended
```

The three exits need one clarification each, since only the third moves a file. *Leave*, including
leaving an open point open, writes nothing. *Mark as to follow up* writes the todo line only. *Archive*
writes the outcome, the index rows and the pointer changes, and is the only exit that promotes first; a
closed point is routed to a live home — a feature todo, a `## Rules` entry or the decisions doc — and an
explicitly dropped point is recorded in the plan's `## Outcome` and in the decision log.

Why the cursor lives in the session rather than a file: the sweep is one session's activity by
construction, its output is a table already in the conversation, and its effects are on disk. A `## Walk`
level cannot hold it, because §7b makes any open walk block `#archive`'s retirement and `#bake`'s fold —
the sweep would refuse to do the thing it exists for. `FEAT_HYD_` cannot hold it either: it is rewritten
whole by `#bake` and the walk page bars walk state there.

## 6. Candidate predicate — one home, two file kinds

The predicate is computed in three places today: `#bake` step 8, `#doctor`'s report-only nudge, and this
plan's sweep. `ONE HOME PER FACT` requires one statement, so **the sweep page owns the states and their
tests**; §7b's `#archive` row only names the sweep and the reserved word, because that row rides in the
always-loaded prefix-cache zone and §7a keeps it small. `docs/cmd_help_bake.md` step 8 and
`docs/cmd_help_doctor.md`'s nudge then point at the sweep instead of restating the test.

**Plan states:**

- **shipped-gone** — attached in `## Docs` and its work appears in `## Implemented`.
- **superseded** — the plan or its feature file states a later plan replaced it.
- **orphaned** — nothing in the feature file, its rules or its decisions doc cites the filename, and its
  work never reached `## Implemented`.
- **live** — in design, cited by a live section, or holding an open todo.

**Doc states:**

- **referenced** — a live section, rule or plan names it.
- **orphaned-doc** — no live file cites it.

**Association test** — a doc belongs to a plan when the plan's own text names the doc's filename. Shared
topic or neighbourhood is not evidence, and an unprovable pairing is reported as two rows rather than a
guessed pair (Q2).

## 7. Hydration and doc updates

**Stage A — before implementation.** This file is the only new artefact, in design until its pointer
reaches `## Implemented`.

**Stage B — at implementation, canonical first:**

1. `AGENTS.md` §7b — the `#archive` row gains the sweep forms and the reserved word, and nothing else.
2. `docs/cmd_help.md` — the derived printed view of §7b; its `#archive` line is brought into step in the
   same pass and never edited independently.
3. `docs/cmd_help_archive.md` — detail gains the sub-forms, the workflow, both state sets, the association
   test, the render, the decision log and the gates; it also owns the predicate tests.
4. `docs/cmd_help_bake.md` and `docs/cmd_help_doctor.md` — neither holds a predicate of its own; the doctor
   line keeps the corpus's single nudge and gains `#archive sweep` as the path, while the bake line stays a
   cross-reference, so the warning is never duplicated.
5. `docs/cmd_help_archive.md` — the abandoned-material clause is scoped to what it means: such material is
   not filed under a retirement status; no command erases it.
6. `xTrack/WorkflowImprovement/FEAT_DSC_WorkflowImprovement.md` — an `### archive-sweep` section while the
   work is open, with its Todos, Docs and Key Files sub-blocks, and the plan pointer in `## Docs`.

**Stage C — at `#bake`, never by hand:** `FEAT_HYD_WorkflowImprovement.md` is rewritten whole — State,
Target Files, Next Step and the `Directive trace:` sentence; the feature summary one-liner, the
front-matter `modified` and the `GLOBAL_CONTEXT.md` row follow the sections; the Focus History top entry is
rewritten to what shipped; the `archive-sweep` section folds into `## Implemented` once no todo, rule, doc
mapping or open walk keeps it alive.

**Stage D — retirement.** The plan leaves design when its pointer lands in `## Implemented`, and is then
itself a `#archive [name]` candidate, with `## Outcome` appended and an index row written. The first real
sweep subject should be the files this change retires.

`docs/xtrack-templates.md` changes after all: the INDEX `Status` enum is stated on the archive page alone,
gaining `dropped` for a plan closed without ever shipping, and the templates doc points at that page
instead of restating the three words. `docs/GIT_WORKFLOW.md` stays untouched, no git behaviour moving.

## 8. Gates and refusals

- The qualifying question stays: a file still defining current behaviour is refused unless the promotion
  half runs first.
- The digest floor is satisfied by the archive action's own `## Outcome` write — the page's refusal is met
  by the same step that retires the file, so no separate digest pass exists.
- An open walk level in the feature refuses disposition, never the enumeration.
- A plan whose own `## Walk` is open is refused by the existing fourth gate, with the walk's three exits
  named.
- No form of this command erases a file.
- `sweep all` moves nothing on any argument.

## 9. Target files

- `AGENTS.md` — §7b `#archive` row
- `docs/cmd_help_archive.md`, `docs/cmd_help.md`, `docs/cmd_help_bake.md`, `docs/cmd_help_doctor.md`
- `docs/xtrack-templates.md` — the enum moves to the archive page and this doc points at it
- `xTrack/WorkflowImprovement/FEAT_DSC_WorkflowImprovement.md` — the section, then `## Docs`, then
  `## Implemented`

## 10. Open questions — the walk set

None open. Q1, Q3 and Q4 closed in the walk on 2026-09-19 — §14 records each answer and the reason — and Q2
was resolved as agent-owned in §13; the level exhausted, so nothing was parked and nothing was dropped.

## 11. Verification

- The §7b row and the page agree on the four sub-forms and the reserved word, and `#help archive` is the
  entry point.
- No doc restates the predicate after the change: `#bake` step 8 and `#doctor`'s nudge point at the sweep.
- A plan whose topic matches a reserved word still resolves as a name.
- A dry run on one feature enumerates, pairs, classifies and recommends, and no file moves without a gate
  naming it.
- The walk gate is exercised: a feature holding an open level enumerates and refuses disposition.
- Stage D is exercised: this plan retires itself with an `## Outcome` and an index row.
- The INDEX enum is stated once, on the archive page, and covers shipped, superseded, promoted and dropped,
  with the templates doc pointing at it and restating nothing.

## 12. Review findings folded

The 2026-09-19 review of this plan returned 2 blocking findings, 8 should-fix and 4 notes.

- **B1, the missing advancement rule** — closed by D9 and D10: no cursor, the table re-rendered with the
  decided row marked, the pending set derived from disk plus the session log, and §5 now says why a
  `## Walk` level or `FEAT_HYD_` cannot hold it.
- **B2, docs outside the enumeration** — closed by D2 and §6: the unit is a plan with its associated doc,
  unpaired docs get their own rows, and each kind has its own states.
- **S1** — closed: the predicate moved out of the always-loaded row to the page.
- **S2** — closed: §8 states the action's `## Outcome` write is itself what satisfies the digest floor.
- **S3** — closed: a closed point's routing and an explicit drop's recording are named in §5.
- **S4** — closed: the follow-up mark's `[target]:[desc]` form is named in D7.
- **S5** — closed by D11, which deletes the two-call claim rather than deferring it: the cost is one read
  per enumerated file.
- **S6** — closed: §4 points at `#list` for the cross-feature order instead of restating it.
- **S7** — closed: `#help archive` is named as the entry point in §4 and §11.
- **S8** — closed: §7 stage B step 5 gives the clause its true scope.
- **N1** — closed: the reserved word carries the exact-match clause in §4.
- **N2** — closed: §11 verifies a reserved-word topic still resolving as a name.
- **N3** — closed: §11 exercises Stage D.
- **N4** — escalated rather than closed: it exposed that §7's "templates unchanged" claim was wrong, and
  the index vocabulary gap is now Q4.

## 13. Resolved as agent-owned

Each of these changes nothing the user can see, so per `TECHNICAL CHOICE IS THE AGENT'S` it was decided
and stated rather than asked:

- **Q2, the association evidence rule** — closed by rule, not preference: `ASK, DON'T GUESS` forbids
  asserting a pairing that was not given, so the plan's own text naming the doc's filename is the only
  admissible evidence, and no looser test is offered.
- **The reserved word's wording** — `sweep` is matched exactly and resolved before `[name]`, reusing the
  clause [`#rule`](AGENTS.md:194) already carries.
- **The filter's state names** — `shipped`, `superseded`, `orphaned`, `orphaned-doc`.
- **The decision log's form** — one line closing each render, `decided: A archived · B left · C follow-up`.
- **The templates-doc trigger** — the edit is scheduled only if Q4 adds a status word, so that doc stands
  untouched until Q4 is answered.
- **Stage B's write order and the verification list** — agent-side, and the §7b row stays at the sweep
  forms plus the reserved word, nothing more.

## 14. Resolved by walk

- **Q1, leave volatility — closed 2026-09-19 on the user's decision: volatile stands.** A `leave` writes
  nothing, so a left row returns at the next sweep; that gap is accepted because D7's follow-up action is
  the mark for anything worth remembering. D9 and D10 are unchanged, and the accepted cost is one re-read
  per left row, narrowed by `sweep [state]`.
- **Q3, where the nudge lives — closed 2026-09-19 on the user's decision: `#doctor` carries it.** The lint
  already reports a plan whose work is done as a retirement candidate, so it gains the words `#archive
  sweep` as the path; the bake keeps a cross-reference only, since a second nudge would duplicate the one
  warning the corpus already gives, and the warning then arrives while the tree's health is being looked
  at rather than at every session's end.
- **Q4, the index vocabulary — closed 2026-09-19 on the user's decision: `dropped` is added.** The enum
  becomes shipped · superseded · promoted · dropped and is stated on the archive page alone, with
  `docs/xtrack-templates.md` pointing at it; the page's abandoned-material clause then reads as that status
  rather than as a deletion no command performs.

## Outcome

Appended at completion — what shipped, and every deviation from the above.
