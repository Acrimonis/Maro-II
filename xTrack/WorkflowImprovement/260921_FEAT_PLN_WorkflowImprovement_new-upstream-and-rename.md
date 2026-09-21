<!-- scope: feature -->
# `#new`'s upstream and `#rename` — the branch carries its own name

**Date:** 2026-09-21 · **Status:** shipped 2026-09-21 — rows, page, derived view and feature sentence updated, `GIT_WORKFLOW.md` checked and left alone, nothing committed · **Feature:** WorkflowImprovement, section `gitting-it` · **Docs affected:** `AGENTS.md` §7b and its Core Directives, `docs/cmd_help_git.md`, `docs/cmd_help.md`, `docs/cmd_help_rule.md`, `docs/GIT_WORKFLOW.md` (checked)

## 1. The requirement

- `#new [branch]` goes on creating `feature/[branch]` from `origin/develop`, but the new branch must not end up tracking `origin/develop` — its upstream is to be its own name.
- `#rename [topic]` renames the current branch to `feature/[topic]` and updates its upstream to the new name: `#rename dash-toast` on `feature/whatever` yields `feature/dash-toast`, tracking `origin/feature/dash-toast`.
- There is no `#git` command group; the git commands are their own `#` rows, so the form to document is `#rename dash-toast` alone.
- The mechanics are the agent's to decide (§3); the requirement was asked about only where it was in doubt, and the one point raised — whether `#move new` moves with `#new` — is decided: `--no-track` on every creation site, 2026-09-21.

## 2. The defect, as measured

| Evidence | What it shows |
|---|---|
| `git branch -vv` read this session | `feature/other-routing [origin/develop: ahead 1]` and `feature/fix-settings-tab [origin/develop: behind 3]` — two branch creations with the shared base as their upstream |
| the same read | `feature/performancE [origin/feature/performancE]` — the branch that was pushed with `-u` is the only one tracking its own name |
| `git status --short --branch` after `git checkout -b feature/whatever origin/develop` | `## feature/whatever...origin/develop`, i.e. the documented creation command sets the upstream to the base |
| `git branch -vv` after `git push -u origin feature/other-routing` | the upstream becomes `origin/feature/other-routing` — so the fix is the missing `-u` at creation time, not the push |

**A correction to what was said in discussion:** a bare `git push` on such a branch is *refused* under git's default `push.default = simple` rather than silently writing to `origin/develop`; the silent version needs `push.default = upstream` or `matching`. **Half of that was measured 2026-09-21** on the scratch repository: neither `push.default` nor `push.autoSetupRemote` is set, so `simple` is in force, and a branch with no upstream is refused with *the current branch feature/one has no upstream branch*, printed beside the exact command that fixes it; the mismatch wording for a branch whose existing upstream names something else stays reasoned rather than measured.

## 3. The mechanics — decided

- **T1 — `#new` creates without an upstream.** `git fetch origin develop`, then `git checkout --no-track -b feature/[name] origin/develop`. No upstream exists until the first push, so `git status` reads plainly `## feature/[name]`, and git's own refusal names the fix if a bare `git push` is typed.
- **T1a — the upstream is set by `-u`, never by the act of pushing.** A bare `git push` under `push.default = simple` refuses — *the current branch feature/[name] has no upstream branch* — and prints `git push --set-upstream origin feature/[name]`; `-u` (or `--set-upstream`) is what writes `branch.feature/[name].remote` and `.merge` and makes the branch track `origin/feature/[name]`. `push.autoSetupRemote = true` (git 2.37+) would make a bare push do it, which is exactly why the commands pass `-u` explicitly rather than depend on a config and a version.
- **T2 — `#push` is where the upstream becomes real.** `git push -u origin <current-branch>`: the command already names the branch, and `-u` now sets the upstream to that name; without that flag the branch would stay untracked and every later bare push would keep failing. It goes on refusing `develop` and `main`.
- **T3 — the recreate offer clears the old upstream.** Option 1 force-creates with `git checkout --no-track -B feature/[name] origin/develop` — the flag order matters, since `-B --no-track` does not run: `--no-track` is read as the branch name (measured on git 2.29.2) — and `-B` does not clear the branch's config section, so the command then runs `git branch --unset-upstream feature/[name]`, reading its *has no upstream information* failure — exit 128 with a `fatal:` prefix — as nothing to clear. The flag is mandatory for a second reason, measured 2026-09-21 against a branch deliberately carrying a different upstream: `--no-track` keeps what the branch carried, a flagless `-B` re-points it at the start point, and git's closing line names the *surviving* upstream, so a flagless recreate both re-installs `origin/develop` and reports success while doing it.
- **T4 — `#rename [topic]`.** The target is `feature/[topic]`, with `feature/` added when the caller omits it and the name taken as given when it already carries the prefix. The move is `git branch -m feature/[old] feature/[new]`; git renames the branch's config section with the ref, and nothing is stashed because a ref move touches neither the index nor the worktree.
- **T4a — the upstream after a rename, decided per case.** An upstream that does not name the new branch is cleared, so no branch is ever left pointing at `origin/develop`; and when the *old* name is published — the probe being `git ls-remote --heads origin feature/[old]`, which exits 0 whether or not the branch exists, so presence is read from a non-empty output rather than from the code (measured 2026-09-21) — the rename is carried to the origin: `git push -u origin feature/[new]`, then `git push origin --delete feature/[old]`, then `git fetch --prune origin` to drop the stale remote-tracking ref. The deletion happens only on an answer, and the question names it — it closes any open pull request whose head that branch is.
- **T5 — bare `#rename`** reports the current branch and the topic it implies and writes nothing.
- **T6 — the refusals.** The current branch is `develop` or `main` (a local rename of a protected ref is refused mechanically); the target equals the current name; the target exists locally (report the commits it holds, then refuse — the caller retries with a free name; there is no recreate offer here, a rename having no meaning to restore); the target exists on origin (refuse, since the push would adopt or clash with a branch that is not ours).
- **T7 — no branch to rename** (detached HEAD): report and stop.
- **T8 — `--no-track` on every creation site, decided 2026-09-21.** `#move new` creates `feature/[branch]` from `origin/develop` exactly as `#new` did and takes the same flag, as does `#new`'s recreate path, so no site that cuts a branch leaves it tracking the base it was cut from. The sibling's separate gap — that `#move new` fails on an existing branch where `#new` reports and offers — is named in §7 and not closed here.

## 4. Documents to touch

- `AGENTS.md` §7b — the `#new`, `#push`, `#move new` and `#rename` rows, plus the `#new` row's carve-out wording if T3 adds a step.
- `docs/cmd_help_git.md` — the `#new` block (its "checkout `-b feature/[branch_name]` tracking it" line and the recreate offer), the `#push` line, the `#move new` block, the `#rename` line, and the preamble that lists which commands ask nothing: T4a adds one carve-out.
- `docs/GIT_WORKFLOW.md` — **to check, not asserted:** it has not been read for this plan, and if it states how a branch is cut, named or published, it moves with the rows above.
- `docs/cmd_help.md` — a derived printed view of §7b, so it follows that table rather than being edited on its own.
- `xTrack/WorkflowImprovement/FEAT_DSC_WorkflowImprovement.md` — the `gitting-it` section's own tier sentence, which today says branch operations ask nothing, and its `#### Docs` pointer to this plan.

## 5. Verification

- On a **scratch repository**, following the precedent set by the 2026-09-19 `#new` change: creation shows no upstream; a bare `git push` refuses with git's own suggestion; `#push`'s `-u` form sets `origin/feature/[name]`; `#move new` and `#new`'s recreate path both leave no upstream behind on a branch that had one; an unpublished rename touches no remote and ends with no upstream; a published rename ends tracking `origin/feature/[new]` with `origin/feature/[old]` gone; a rename with a dirty worktree leaves the worktree untouched.
- Assert the end state rather than assume it: `git branch -vv` after every path, since git's own handling of the config section across `-m` and `-B` is what the commands must not rely on blindly.
- On the **real repository**, the three branches that already carry `origin/develop` are *not* retro-fixed by this change — they correct themselves at their next `#push` — and the `gitting-it` todo about real-repo verification of the git shortcuts stays open.

## 6. Risks and objections

- **Against T1:** a bare `git push` fails until `#push` runs, on a branch with no upstream; that friction is the point — the failure names the exact command to type — but anyone who pushes by hand meets it.
- **Against T4a:** deleting the old remote branch closes an open pull request on it, and a wrong deletion cannot be undone from the local repository. The mitigation is the asking step and the ordering, push-new-before-delete-old, so nothing is lost while the new name is already published.
- **Against T3's tolerance:** `git branch --unset-upstream` fails when there is no upstream, so the command must read that failure as success rather than as an error to report.
- **Against the whole design:** an interrupted rename can leave the local name changed and the upstream still naming the old branch, so the command reports the end state instead of claiming success.

## 7. Findings — named, not in scope

- A renamed branch leaves its old name written into focus entries and hydrations, which are log lines about a past session rather than live facts; they stay as written unless the user wants a bake to note the rename.
- The standing global todo — `#new` should warn before switching on a dirty tree — is a different question, and this change does not close it.
- The `#new` offer's other options (a different branch name, abort) are untouched, and the dirty-tree report measured on 2026-09-19 stands.
- `#move new` still fails on an existing branch, where `#new` reports the commits it would lose, is published and offers recreate, another name or abort — the sibling gap the 2026-09-19 change left named and this one does not close.
- Both dated Ui_Settings records that described a branch bound to `origin/develop` as the shape `#new` produces were corrected in the same pass, one clause each, dating that shape to before 2026-09-21 — so no live record still teaches it.

## Outcome

**Shipped 2026-09-21 on `feature/whatever`** through the `#implement` pipeline — a Code hop that measured every path on a scratch repository before touching a document, an Ask hop that returned no blocking finding, and the Architect hop that closed its should-fix items. Nothing was committed.

- **What shipped.** `AGENTS.md` §7b's `#new`, `#push`, `#move new` and `#rename` rows carry §3's mechanics; the Core Directives' ask-nothing sentence carries both carve-outs; and the protected-branch rule's list of mechanical decliners gained `#rename`, which is the one place the Ask hop found the rulebook under-inclusive for the boundary this change introduces. `docs/cmd_help_git.md` holds the detail — the flagless cut with its upstream left to `#push`, the recreate's effect with its `--unset-upstream` step, `-u` on the push, and the rename's move, upstream cases, published path and refusals — with the preamble naming the carve-out, and `docs/cmd_help_rule.md` following the decliner list. `docs/cmd_help.md` follows §7b, and `docs/GIT_WORKFLOW.md` was checked and left alone, stating the branch model and delegating the rows.
- **The measurement is the evidence of record.** On a scratch repository under the temp directory, `git branch -vv` asserted after every path: creation leaves the config section unset; a bare push refuses with *the current branch has no upstream branch* and prints the command that fixes it; `push -u` writes `remote` and `merge`; `-m` moves the config section with the ref but keeps its old `merge` ref, which is what makes the clear step load-bearing; a published rename ends on the new name with the old remote branch deleted; a dirty worktree survives `-m` unchanged; and `push.default` is unset, so `simple` is in force.
- **The one deviation, and it came from the measurement.** §3's T3 sketched a command that does not run, corrected above; the docs state the recreate's effect rather than a command line, which is how the broken form reached no shipped page.
- **The premises were then measured, closing two of the three.** The `--no-track` form was probed against a branch deliberately carrying a *different* upstream and kept it, so the clear step is load-bearing rather than defensive; the dirty-tree carry-across was reproduced on the new command form; the flagless `-B` was measured re-pointing the upstream at the start point, which is the second reason the flag is mandatory; `--unset-upstream` reports exit 128 with a `fatal:` prefix when there is nothing to clear, and 0 silently when it clears; and `ls-remote` exits 0 for a branch that does not exist, so the published probe reads its output rather than its code. Still unproven: the failure's *version attribution*, the string being the second pass's report rather than a re-checked fact, though the failure itself was observed in the first pass's own output.
- **Named, not closed.** `#move new` still fails on an existing branch where `#new` reports and offers. `docs/cmd_help.md`'s stale pointer was corrected in the same pass: it now names `AGENTS.md` §7b as the rules' home and `GIT_WORKFLOW.md` as the branch model, which is what that file says of itself — a hand correction inside a file stamped as derived, licensed because that one prose stub is part of the page's own frame rather than a row §7b holds.
