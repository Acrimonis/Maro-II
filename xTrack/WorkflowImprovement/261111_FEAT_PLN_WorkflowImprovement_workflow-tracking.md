<!-- scope: feature -->
# Workflow tracking — what is used, what recurs, what can go

**Status:** v1 built 2026-11-11 — four Ask findings applied, the rest kept below for the review once tracking has accumulated · **Created:** 2026-11-11 ·
**Branch:** `feature/workflow-tracking`

## 1. Goal

Record how the workflow is actually used, so it can be pruned and grown from evidence:

- **Commands** — which `#`-commands, sub-commands and aliases run, how often, on how many branches.
- **Flows** — recurring process work that is *not* a command yet (archive plans with decisions promoted,
  fetch a device logcat and split it per rung, replay a device line through the harness, …). A flow that
  keeps recurring is a command candidate; a command nobody runs is a removal candidate.
- **Rules** — which gates and refusals fire (stage 2, §9).

Constraints: two branches writing the record at once must merge with no conflict; the record must stay
small however long it runs; each fact has one home.

## 2. Pipeline

```
capture ──► spool ──► flush ──► raw/YYMM.md ──► compound ──► digest.md ──► report
 (hook)    (.git/,    (#commit,  (open months,   (closed      (one block   (#doctor usage,
           untracked)  #bake)     keyed blocks)   month, raw   per month)   script output)
                                                  deleted)
```

- **One script does every mechanical step** — `tools/usage-log.ps1` with `-Capture`, `-Flush`, `-Slugs`,
  `-Compound`, `-Report`, `-Backfill`. PowerShell parses JSON natively, so no `jq` and no new dependency;
  CRLF by `.gitattributes`.
- **The agent never reads the record.** It reads the script's output — the report table, the slug list —
  so the token cost stays flat while the record grows. Its only authored input is the flow lines (§6).

## 3. Storage

```
xTrack/WorkflowImprovement/usage/
  raw/2611.md      open month — keyed blocks of raw lines, append-only
  raw/2612.md
  digest.md        closed months — one counted block per month, append-only
```

`.gitattributes`: `xTrack/WorkflowImprovement/usage/** merge=union`.

### 3.1 Raw — keyed blocks

```
# Workflow usage — raw 2611
## 261111-1629 · workflow-tracking
261111 16:31 · cmd · #focus · WorkflowImprovement
261111 16:40 · cmd · #new · WorkflowImprovement
## 261112-0910 · some-other-feature
261112 09:12 · cmd · #bake · Route
## 261111-1629 · workflow-tracking
261111 17:05 · flow · harness-replay · Route · device line replayed through the approach pass
```

- **Block key = branch creation time + branch topic** (`## YYMMDD-HHmm · <topic>`). The creation time comes
  from the branch's reflog (`Created from …` entry); a branch with no such entry takes its first-capture
  time. The report groups by key, never by file position.
- **Every write is a new keyed block appended at end of file.** A line is never added to an existing block,
  and nothing is edited. A key repeats once per flush — one line per commit or bake.
- Lines carry the full stamp, so a line keeps its month whichever file it lands in (§7.3).

### 3.2 Digest — counted months

```
# Workflow usage — digest
## 2610 · 3f9a1c2e · 261203-0915
cmd · #bake · 41 · 7 · 261030
cmd · #doc list · 2 · 1 · 261004
flow · harness-replay · 5 · 2 · 261028 · device line replayed through the approach pass
```

- Key: line month · hash of the raw lines consumed · compound time. Line: kind · name · count ·
  distinct branches that month · last use · (flows) latest gist.
- Blocks are **additive** per month, and blocks with the **same hash are one** — so a month compounded on two
  branches at once is counted once, while late lines compounded later add to it.
- Size: about one line per command and slug in use per month — a few hundred lines a year, read only by the script.

### 3.3 Merge safety — measured 2026-11-11 on scratch repos

- Concurrent new blocks merged clean by **rebase** and by **merge** (both `#merge` paths); the same merge
  without the attribute conflicted.
- A line appended to an *existing* block was filed under another branch's key by a rebase (union puts
  `develop`'s side first); the same case written as a fresh keyed block stayed correctly keyed — the reason
  for the block-per-write rule.
- Block order after a union is not chronological, and a blank line common to both sides is kept once —
  so the script sorts by key and stamp, and the format has no blank separators.
- **GitHub residue:** `#merge` syncs `origin/develop` locally before the PR, so the PR is clean then. If
  another PR lands afterwards, GitHub runs its own merge — whether it runs merge drivers is unverified;
  on a reported conflict, re-running `#merge` clears it locally.

## 4. Line shapes

```
raw    <YYMMDD HH:mm> · cmd  · <#command>[ <sub-command>] · <Feature>
raw    <YYMMDD HH:mm> · flow · <flow-slug> · <Feature> · <≤12-word gist>
raw    <YYMMDD HH:mm> · rule · <RULE NAME> · <Feature> · <fired|refused|aborted>          (stage 2)
digest <kind> · <name> · <count> · <branches> · <last YYMMDD>[ · <latest gist>]
```

- Nothing is stored twice: the branch lives in the key only, the command list in `AGENTS.md` §7b only,
  the month in the stamp only. No source column — Roo's command lines and Claude's are the same fact.
- `sub-command` is recorded only for a keyword the §7b row or its page names (`#doc list`, `#status diff`,
  `#move new`, `#rule all`, `#doctor fix`, `#archive search`, `#go impl`). A free argument — a feature name,
  a branch — is never recorded, so counts group by usage and the record leaks nothing.
- An alias is recorded as typed (`#copy`, `#features`, `#context`), so an unused alias is visible on its own.
- `Feature` is the top Focus History entry at capture time.

## 5. Capture and flush

### 5.1 Capture — Claude Code hook into a local spool

- `UserPromptSubmit` hook in the committed `.claude/settings.json` runs `usage-log.ps1 -Capture`. Verify
  the payload field names at implement time.
- Scans **every line** of the prompt (commands are not always first — this plan's own `#new` came on a
  prompt's last line). A token counts only when it matches a §7b command or alias, read from `AGENTS.md`
  at run time, so a markdown `# heading` or an issue `#401` is never counted.
- Writes to `$(git rev-parse --git-common-dir)/maro-usage/spool.log`: untracked by construction, unaffected
  by branch switches and `#move`'s stash, invisible to `#new`'s dirty-tree report and `#commit`'s staged set.
- A command is recorded under the branch it was typed on — `#new x` typed on the old branch belongs there.

### 5.2 Flush — `#commit` and `#bake`

- `usage-log.ps1 -Flush` groups the spool by branch, appends one keyed block per branch to the current
  month's raw file, empties the spool, then runs `-Compound` (§7).
- `#commit` flushes before it stages, so the block rides in the commit and shows in the confirmed staged set.
- `#bake` passes its flow lines (§6) to the same flush, so they land in the current branch's block in one write.
- Roo has no hook: its bake passes the session's command lines too, taken from the user's words it already
  lists in the Directive trace. Under Claude Code the bake passes none, so nothing is counted twice.

## 6. Flows — what `#bake` writes

- **Scope (D5): process only.** A flow is a step that would be done the same way on a different feature —
  archive, review, device pass, harness run, logcat split, bake-commit-push. The feature work itself
  (implementing a routing change) is never a flow; it already lives in `## Implemented`.
- **Reuse before mint — what makes flows compound.** The bake runs `-Slugs` (the slugs in use, with counts,
  from digest and raw) and reuses one whenever the work matches. It mints a new slug only when nothing fits:
  kebab-case, verb first (`archive-promote-decisions`, `device-logcat-split`, `harness-replay`). The record
  is the slug registry — no second list.
- One line per distinct flow per bake, the gist at most twelve words. A recurring *sequence* of commands is
  a flow (`bake-commit-push` → alias candidate); a single command run is not.
- **Slug drift** is corrected by an alias line, never an edit (the record is append-only):
  `alias · harness-rerun → harness-replay`, written by the bake when the report flags a near-duplicate and
  the user confirms; the script folds aliases at read time.
- A session that never bakes still has its commands (spool, flushed at the next commit) but no flow lines.

## 7. Compounding

### 7.1 When

At every flush, the script compounds each raw month **at least one full month closed** (`raw/2609.md` once
the current month is 2611): the one-month grace lets branches cut that month reach `develop` first.

### 7.2 Safety check — mechanical, before deleting a raw month

- No local branch and no `origin/*` branch not yet merged into `origin/develop` has changed `raw/YYMM.md`
  since its merge-base with `origin/develop`.
- Pass → the digest block is appended, the raw file deleted, both in the flush's commit. A branch that never
  touched the file merges the deletion as a one-sided change. Two branches deleting the same file is clean.
- Fail → the month stays raw, retried at the next flush; the report names the month and the blocking branch.

### 7.3 Late lines — the one manual edge

A branch that only lived on another clone and never pushed can still carry lines for a compounded month; its
`#merge` meets a modify/delete conflict. Resolution, mechanical: keep the deletion and re-flush that branch's
own added lines (its diff against the merge-base) into the current raw file. Their full stamps keep their
month, so the next compounding adds them as a separate block (different hash).

## 8. Report — `#doctor usage` (D1)

`usage-log.ps1 -Report` reads digest + open raw months and prints one compact table the agent relays:

| Section | Rule |
|---|---|
| **Removal candidates** | a command, sub-command or alias with no use in the trailing 90 days (D2) |
| **Low use** | 1–2 uses in the trailing 90 days — watch, not remove |
| **Command candidates** | a flow slug with ≥3 uses on ≥2 branches in the trailing 90 days (D2) |
| **Alias candidates** | a command sequence flow above the same threshold |
| **Silent guards** | 🛑 / ⛔ rules and refusal paths that never fired — reported as working, never as removal (D6) |
| **Slug drift** | near-duplicate slugs (edit distance) awaiting an alias line |
| **Compounding** | months pending, and the branch blocking each |

- Removal and command lists appear only once the record spans 90 days; the backfill (D3) makes that day one.
- Distinct branches across months are summed from the monthly counts, so a branch spanning two months counts
  twice — accepted, the threshold being a nudge, not a verdict.

## 9. Rules — stage 2 (D4)

Deferred until two closed months are digested. The bake will then pass one `rule` line per covered action
class met and per refusal or abort, derived from the Directive trace it already writes — the trace stays the
prose home, the lines its counted form. Until then the Silent-guards section lists the 🛑 / ⛔ rules with no
data rather than claiming they never fired.

## 10. Backfill (D3) — commands only, mechanical

- `usage-log.ps1 -Backfill` scans the local Claude Code transcripts (37 sessions, 2026-06-06 → 2026-11-11 on
  this machine) for user prompts, extracts only tokens matching today's §7b commands and aliases, and appends
  **digest blocks** directly — counts, no raw lines, no prompt text. One run, then never again.
- No flow backfill: slugs invented after the fact would seed the registry with guesses that every later bake
  then reuses.
- Commands since renamed or retired count only under today's names; the old names are reported once as
  "historic, unmatched" and not written.

## 11. Integration points

- `docs/xtrack-templates.md` — the raw, digest and line shapes and the never-append-to-a-block rule (one home).
- `docs/cmd_help_bake.md` — the slug step and the flush; `docs/cmd_help_git.md` — `#commit` flushes before staging.
- `docs/cmd_help_doctor.md` + §7b `#doctor` row — the `usage` facet.
- `.gitattributes`, `.claude/settings.json`, `tools/usage-log.ps1`.
- `AGENTS.md` §7a — one bullet naming `usage/` and that only the script reads it.
- `#doctor` checks (j) orphan docs and (o) `## Docs` vs disk skip `usage/`, as they skip `xxArchive/`; every
  feature-summarising command excludes it.

## 12. Decisions — recommended

| # | Decision | Recommendation | Strongest objection |
|---|---|---|---|
| D1 | Report home | `#doctor usage` — workflow health is `#doctor`'s subject, and the command list does not grow to measure itself | `#doctor` is lint; a usage table is analytics, so the page mixes two kinds of output |
| D2 | Thresholds | trailing 90 days; command candidate ≥3 uses on ≥2 branches; removal = 0 uses; 1–2 = watch | 90 days hides a seasonal command (a yearly cleanup) — it lands on the removal list while still needed |
| D3 | Backfill | commands only, from transcripts, straight into digest blocks | Roo sessions are absent, so a Roo-only command reads as unused |
| D4 | Rules | after two digested months | the 🛑 / ⛔ question you raised waits two months for data |
| D5 | Flow scope | process steps only, by the "same on another feature" test | the test is the agent's judgment, so borderline steps split both ways |
| D6 | Guards | 🛑 / ⛔ rules and refusals never on the removal list | a guard that no longer applies is never flagged and stays forever |

## 13. Rejected

- A file per branch — a branch per feature means too many files.
- One shared log without union — conflict on every concurrent append (measured).
- Appending into an existing block — misfiled by rebase (measured).
- The branch key on every raw line — works, but duplicates the key and empties the blocks of meaning.
- Appending to one's own block when it is not yet in the merge-base — saves one key line per flush at the cost
  of a merge-base probe on every write.
- A yearly file kept whole — grows without bound and keeps raw and counted forms side by side.
- Keeping raw months after digesting — two homes for one fact.
- A counter table — every increment collides.
- A section in `FEAT_DSC_` — same collision, and it pollutes the feature file.
- The agent computing the report — its token cost grows with the record.

## 14. Strongest objection

The script becomes real code with real edge cases — reflog parsing, the merge-base safety check, the
modify/delete resolution — for what is a measurement aid; a bug there can silently drop or double counts.
Mitigated by keeping every step re-runnable from the raw months still open and by hashing digest blocks, not
prevented. Second: a hand edit appending into an existing raw block reintroduces the rebase misfile — the rule
has only the template doc to enforce it.

## Implemented

v1 shipped 2026-11-11 on `feature/workflow-tracking`: §2-§8 and §10 as written, in `tools/usage-log.ps1`, `.claude/settings.json`, `.gitattributes` and the doc edits of §11.

Deviations:
- No separate *alias candidates* row in the report (§8) - sequence-flow detection is not built; alias lines are accepted and folded.
- The removal list is marked PROVISIONAL when a month inside the window has no data: the transcripts for 2608-2609 had expired, so the backfill (§10) holds 2606 and 2610-2611 only.
- `#doctor usage` is a facet, not a new check letter; checks (j) and (o) are told to skip `usage/` on the doctor page only - not yet exercised.
- `/tools/` is git-ignored (the other tools files were force-added), so `tools/usage-log.ps1` was added with `git add -f`; the ignore line is untouched.
- The block key's time comes from the reflog and is remembered per clone in the spool dir; a key mismatch only splits a branch's blocks, which the report groups by topic.

The hook's `prompt` payload field was confirmed live (this session's `#bake` landed in the spool). Ask hop (2026-11-11): no blocking finding; eight should-fix. **Applied the same day (marked APPLIED):** the hook path, the fail-closed check, the ordinal hash and the lines file's home. **Kept for the review once tracking has accumulated:** every other item, including the notes.
- APPLIED - `.claude/settings.json` - the script path was relative to the cwd; now `$CLAUDE_PROJECT_DIR` (tested through bash from another cwd; the shell Claude Code runs hooks in is unverified).
- APPLIED - `Get-RawBlockers` - a git failure read as "no blocker" and the raw month was deleted; it now fails closed (`Get-GitStrict`). Testing it also found that PowerShell 5.1 under `Stop` raises on any native stderr line even with `2>$null`, so both git helpers now run under `Continue`.
- `Read-Digest` - same-hash blocks are skipped at compound time only; dedupe on read by (month, hash, row).
- APPLIED - Compound hash - `Sort-Object` was culture-dependent; it now sorts ordinally.
- Compound safety - no fetch, so `origin/develop` and other clones' branches can be stale.
- `-Report` - reaches the dry run by dynamic scope and dumps whole blocks; pass it as a parameter, one line per due month.
- APPLIED - `cmd_help_bake.md` - the lines file had no home (`git add -A` would stage it); it is now `lines.txt` in the spool dir, consumed by the flush.
- APPLIED - `cmd_help_git.md` - the flush now follows the develop/main refusal.
Notes: prose mentions count as uses (the `#implement` 48 vs `#commit` 38 reads as mentions); unparseable and `rule` lines are dropped silently before a month is deleted; a squash-merged branch holds its month forever; `.claude/settings.json` sits against 8a's pointer-only adapter rule; the always-loaded AGENTS bullet restates the templates doc; `Invoke-Backfill` is dead weight after its one run; `$SubMap` mirrors §7b.

Second Ask hop (the four fixes): one claimed blocking finding, not reproduced - that `Get-GitStrict` returns a one-element `$null` array when git prints nothing, so every unrelated unmerged branch would hold the month. A native command with no output yields an empty result (`@(f).Count` is 0; only an explicit `$null` gives 1), and a scratch test with an unrelated unmerged branch compounded as it should. One should-fix applied: a `lines.txt` that yields nothing is now deleted too, and the script header names the default. Kept for the review: `lines.txt` is shared by every worktree of the clone, so a leftover from an aborted bake would be flushed under the next branch; a capture landing between the spool read and its removal is lost; the hook's shell is assumed bash (`$CLAUDE_PROJECT_DIR` would not expand under cmd or PowerShell and the hook would fail silently).

Open: no real flush or compound has run on this repo; stage 2 (rules, D4) waits for two digested months.
