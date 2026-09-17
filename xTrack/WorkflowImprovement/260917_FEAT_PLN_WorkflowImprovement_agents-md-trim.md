<!-- scope: feature -->
# Trimming AGENTS.md — what is actually available

## Outcome

The trim the challenge asked for, measured rather than argued. `AGENTS.md` carries **252 content lines**
today, against 217 at the start of this session, and holds **19 tiered rules** across its Core Directives.
Three compressions are safe and reversible; the rest of the file resists trimming for reasons worth
recording, and the only lever with real mass is deleting or shortening *rules*, which needs evidence this
project does not yet collect. This plan ships the compressions and names the evidence path.

## 1. The measured baseline

- **252 content lines**, of which the Core Directives occupy 7 to 133 — the legend zone, the Output Contract and the 19 rules.
- **The legend zone is 17 lines** (7–23): five tier bullets, two prose paragraphs, the Output Contract declaration, the five-action-class paragraph.
- **§7b is 30 lines** (172–205): a preamble, a front-matter line, and 25 command rows.
- **The numbered sections are 25 lines** (135–170) and **§7a is 11 long bullets** — the densest prose in the file.
- **What grew this session:** the legend itself, the widened ⛔ line, the device rule's marked restatement, the no-`#`-command-overrides clause, §4's dependency rule, and the five-class paragraph.

## 2. The three compressions, with the exact text

- **§8c `Summary Payload Format` — 4 lines to 2.** The heading, the lead-in and three bullets become one
  lead-in and one bullet: *when calling `switch_mode`, the reason carries 1–3 bullets — Code: what was
  implemented, build status, files changed, deviations · Ask: scope covered, code-health observations ·
  Debug: root cause, evidence, fix recommendation.* The fact stays in its section; nothing migrates.
- **§9 `APK pipeline` — trim the second pointer.** The bullet ends *Command detail in `README.md`; the
  bake-vs-build contract lives in the BakeNormalization feature file*; the second clause restates
  `README.md`'s own pointer and goes, keeping the chain and the first pointer.
- **Legend zone — the Output Contract paragraph and the five-class paragraph merge into one.** Both say
  where the tiers do *not* apply — the contract's bullets and the classes that carry their own verdict
  line — so one paragraph of three lines replaces two of five.

- **Total: about 6 lines, 2.4% of the file.** Reversible in one `apply_diff` each.

## 3. What resists trimming, and why

- **§7a `Memory Stack` — rejected as a candidate.** It describes the file layout a cold session must know before it reads anything; the templates it points at are *lazy-loaded*, so trimming it to a pointer would charge every session a read for a basic fact. The §3.6 candidate list was too eager here.
- **§8c — compression, not migration.** Folding the payload shape into §8b's table would lengthen seven rows and save nothing; compressing it in place keeps the fact where a mode-aware reader looks.
- **The Lazy-Load Index — rejected.** Eleven rows pointing at lazily-read docs are the mechanism that keeps everything else out of the prefix; cutting it would put the content back.
- **§7b's rows — rejected.** Each is a command's contract, read at the moment that command is used; shortening them trades a one-time read for a runtime mistake.
- **The rules themselves — no cut, because no evidence.** Each of the 19 earns its line until something shows it never fires, and nothing in the repo records a firing.

## 4. The real lever, and why it waits

- **2.4% is not the smallness problem.** The challenge's finding was that the rulebook grew while getting clearer, and six lines do not answer it — the mass is 19 rules across 105 lines, each carrying its own trigger.
- **The lever with mass is deletion by evidence.** §3.1 already states the process: a rule is deleted when it never fires, never by argument. Two of the three new mechanisms make that measurable — the five-class verdict line records which classes were met, and the `**Directive trace:**` sentence carries the summary into `FEAT_HYD_` at each bake.
- **So the order is: compress now, delete later.** The compressions cost nothing and cannot mislead; a rule deletion without evidence would trade real force for a line count, which is the trade the tier legend exists to prevent.
- **What the evidence path needs to be run:** two or three sessions carrying trace lines, then one pass over them naming a class or a rule that never appeared. That pass is not scheduled here; it fires when a user asks for it.
- **Objection — the honest alternative is doing nothing.** A 2.4% cut changes the per-rule attention share by an amount no one can measure, so the compressions may be decoration; the counter is that they cost one write and remove a real duplication in the legend zone, and the only thing they can break is a sentence's shape, which a review reads.

## 5. How it ships

- **Step 1 — the three compressions**, one `apply_diff` on `AGENTS.md`, no rule text, no force word, no trigger touched.
- **Step 2 — the census check.** Re-run the rule search: the count must be unchanged by the trim, whatever it is when the cut is taken — 20 as of 2026-09-17 — proving the trim touched prose only.
- **Step 3 — `#doctor`'s tier check** acts as the regression guard, since a compression that swallowed a tier glyph is exactly the fault it now reports.
- **No file outside `AGENTS.md` is touched**, and `docs/cmd_help.md` needs no regeneration because no §7b row changes.
