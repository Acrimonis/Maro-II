<!-- scope: feature -->
# Git protection — incorporating the exit's two remaining halves

## Outcome

Incorporating what [`260611_FEAT_PLN_Documentation_git-protection-workflow.md`](260611_FEAT_PLN_Documentation_git-protection-workflow.md)
still contributes to the current workflow, and retiring it. Two facts survive: what happens **after** a
refused command's move, and how a bare `#move` chooses its branch. Both are workflow detail the file
today does not carry. Everything else in that plan is contradicted by what shipped on 2026-09-17, and the
plan retires rather than being rewritten.

## 1. Incorporated — the refused command resumes after the move

The gap: `GIT_WORKFLOW.md` states the abort and the `#move new <name>`, and stops there, so it is undecided
whether a `#push` refused on `develop` is spent or renewed once the user has moved. One sentence settles it,
and it agrees with the git rule's own premise — the invocation is the go-ahead.

Proposed replacement for the exit paragraph at [`docs/GIT_WORKFLOW.md:12`](../../docs/GIT_WORKFLOW.md:12):

```markdown
What this file adds is the exit: if a command would write to `develop` or `main`, abort immediately and
create a fresh feature branch (`#move new <name>`) rather than working out a way to do it — then, once the
move is done, complete the command that was refused on the new branch without asking again: the invocation
was the go-ahead, and the move changes the target rather than the entitlement.
```

- **Objection.** Completing the operation unprompted could push a branch the user created for something else; the counter is that only the *refused* command resumes, and nothing else is inferred from the move.

## 2. Incorporated — bare `#move` picks from local branches

The gap: [`#move`](../../AGENTS.md:188) requires a branch name and has no defined bare behaviour, while the plan already specified the missing half — list local branches newest first, let the user pick. The plan's other half, a prompt prefilled with `feature/`, is the sibling of `#move new`.

- **Row:** `#move [branch]` — Stash → switch → pop (existing branch); bare = list local branches newest first and pick.
- **Page:** [`docs/cmd_help_git.md:14`](../../docs/cmd_help_git.md:14) gains `Bare = list local branches, newest first, pick one.`, and `#move new` gains `bare ⇒ prompt for a name, prefilled feature/.`
- **Why the pair is the whole of the plan's question.** *New branch or existing branch?* — the plan asked the agent to put that choice to the user, and the two commands already are that choice; each was missing one half of the prompt.

- **Objection.** The picker branches a one-line contract on a command nobody has called awkward; the counter is three words in the row and a page line, against an undefined bare invocation that today would simply fail.

## 3. Disposed — everything else in the 260611 plan

- **Its Rule, reversed.** It required `docs/GIT_WORKFLOW.md` to carry a Hard Rule section forbidding direct pushes; that file now says the prohibition is single-sourced in `AGENTS.md` and is not restated ([line 10](../../docs/GIT_WORKFLOW.md:10)), which CENTRALISE AND TRIM requires.
- **Its Where-to-Enforce table, stale.** Every ✅ row rests on retired glyphs, its `GLOBAL_CONTEXT.md` row is now forbidden by `GLOBAL_CONTEXT.md IS STATE-ONLY`, and its one ❌ names a build-time layer this stack does not have.
- **Its open question, dead.** Option (b) is a rule kept in `GLOBAL_CONTEXT.md`, and `#rule global` no longer exists to write that option.
- **Its reasoning, kept in substance.** (b) was preferred because `#doctor` could lint the rule; that argument is already the reason the tier check sits in `#doctor`, so nothing needs migrating for it.

## 4. Retirement

- **Mechanism:** `#archive 260611_FEAT_PLN_Documentation_git-protection-workflow.md` — explicit invocation, since retirement is not the agent's to fire.
- **Digest floor:** *Superseded 2026-09-17 — its exit's completion step and its `#move` picker were incorporated into `GIT_WORKFLOW.md` and the git command page; its doc-level Hard-Rule premise was reversed by CENTRALISE AND TRIM, and its `#rule global` option went with that command.*
- **Not verified:** whether `FEAT_DSC_Documentation.md` lists this plan in `## Docs` or its `## Implemented`, and whether `GLOBAL_CONTEXT.md`'s routing carries a row for it. Both were left unread rather than assumed; the archive pass re-points them if they exist.

## 5. Cost

- Three files gain text, one plan retires, no rule is added and no tier changes: two lines in `GIT_WORKFLOW.md`, one page line and one row field, and one archive index row.
