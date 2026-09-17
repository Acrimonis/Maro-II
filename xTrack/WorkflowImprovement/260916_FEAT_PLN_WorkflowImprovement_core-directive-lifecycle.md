<!-- scope: feature -->
# Keeping the Core Directives alive — maintain, reinforce, refresh

**Feature:** WorkflowImprovement · **Branch:** `feature/some-wflw` · **Started:** 2026-09-16 · **Status:** in design — the requirement is fixed, the mechanisms below are proposed, four decisions open

**Supersedes for this topic:** Parts B, C and §5.1 of [`260916_FEAT_PLN_WorkflowImprovement_walk-context-and-directive-reload.md`](260916_FEAT_PLN_WorkflowImprovement_walk-context-and-directive-reload.md:1), which now covers the `#walk` topic alone. The cache analysis in that plan's §4 and §4.2 remains the reference and is not repeated here.

---

## 1. The requirement

Keep the Core Directives — the 🔴 rules in `AGENTS.md` — alive at low cost: **maintained** as the situation changes, **reinforced** at the moment each one applies, and **refreshed** when the rulebook or the session has drifted. The constraint is explicit: not expensive in tokens, and not expensive in time.

## 2. Where the cost actually sits

- **A rule sitting in the prefix is already paid for.** `AGENTS.md` occupies the leading block and is cached, so a rule costs money only when the rulebook is *changed* or *re-sent*, never when it is merely present.
- **Three things generate the bill, in this order:** re-injecting the rulebook (a full-price trip over it plus a permanently wider window), printing or re-reading it on a cadence, and adding rules until each remaining rule weighs too little to bind.
- **Therefore the design rule is to pay at authoring time, not at run time.** A rule written so that it can be *checked* costs nothing thereafter; a rule written as prose must be re-derived on every occasion it applies.
- **The cheapest possible mechanism is a refusal.** A rule already living inside a command's own definition cannot be forgotten, because the command itself declines.

## 3. The three verbs

### 3.0 Step 0 — the tier legend, normalised to five categories

Numbered 0 because it precedes the three verbs and gates all of them: the registry assigns ids by tier, so an undefined tier poisons the registry, the triggers and the gates alike.

- **Nothing defines the glyphs.** A search over the whole tree finds no legend for the rulebook's symbols; the only legend in the repository is a plan's own for findings — [`260611_FEAT_PLN_GPS_loss-investigation.md`](../../xTrack/GPS/260611_FEAT_PLN_GPS_loss-investigation.md:161) reads `🔴 = issue found, 🟡 = potential concern, 🟢 = ok` — so the same glyphs mean something else in a plan than in the rulebook.
- **The real defect is one marker doing every job.** Thirteen rules wear the same 🔴, so it says *important* and nothing else: it cannot separate the rule that voids an action from the rule that asks for tidy docs. The remaining glyphs decorate a bold lead-in rather than categorise, and four rules carry no marker at all.
- **Five categories, ordered by what a breach costs** — each one a different enforcement mechanism, which is the test of whether a category is real rather than cosmetic.

| Tier | Meaning | Breach costs | Enforcement that exists |
|---|---|---|---|
| 🛑 AUTHORISATION | the user decides | the action is void — undo and report | refusal inside the command |
| ⛔ BOUNDARY | never cross the surface, never invent, never touch opaque data | a defect ships, or the user's time and tokens are spent for nothing | a predicate line at the action |
| 💬 CONDUCT | how the agent speaks and responds | the reply stops being usable, and trust goes | the Output Contract |
| 🧹 AUTHORSHIP | how the corpus and the delivery are written | the corpus rots | `#doctor` lint |
| 🟢 GUIDELINE | preferred, deviation allowed if stated | nothing | none |

- **Every rule maps to one category**, with the marker it wears today.

| Rule | Today | Tier |
|---|---|---|
| `PROTECTED-BRANCHES` | 🔴 | 🛑 |
| `GIT-WRITE-NEEDS-GO` | 🔴 | 🛑 |
| `NO-MODE-SWITCH` | 🔴 | 🛑 |
| `NO-GIT-OR-DEPLOY-PROMPTS` | 🔴 | 🛑 |
| `NO-SCOPE-CREEP` | ⛔ | ⛔ |
| `NO-ASSUMED-ARCHITECTURE` | 🔴 | ⛔ |
| `NO-BINARY-READS` | 🔴 | ⛔ |
| `NO-GIT-EDITOR` | 🔴 | ⛔ |
| `REPO-ROOT-IS-DOT` | 🔴 | ⛔ |
| `DEVICE-LOGCAT-ON-REQUEST` | 🪲 | ⛔ |
| `ANSWER-BEFORE-ACTING` | 🔴 | 💬 |
| `CHALLENGE-NOT-GUESS` | none | 💬 |
| `GATE-NAMES-ITS-ACTION` | none | 💬 |
| `EXPLAIN-DISCUSS-GATE` | none | 💬 |
| `DEFER-TO-JUDGEMENT` | none | 💬 |
| `ONE-HOME-PER-FACT` | 🔴 | 🧹 |
| `DESCRIBE-DONT-RESTATE` | 🔴 | 🧹 |
| `CONTEXT-IS-STATE-ONLY` | 🔴 | 🧹 |
| `PLAN-FILE-PLACEMENT` | 🔴 | 🧹 |
| `WRITE-ONCE` | 🟡 | 🟢 |

- **What the mapping exposes.** Four rules that behave as rules carry no marker at all — `CHALLENGE-NOT-GUESS`, `GATE-NAMES-ITS-ACTION`, the explain/discuss gate and the deference clause — which is a gap the glyph system hid rather than created.
- **A conflict rule falls out of the ordering.** When two rules compete the higher tier wins, and within a tier the more specific wins, so `PROTECTED-BRANCHES`' one-off "supersedes all other commands" sentence becomes the tier order instead of an exception written into one bullet.
- **What disappears.** Ten glyphs go — 📏 🏗️ 🩹 🎯 🔍 📋 🗣️ 🚦 🪲 🟡 — because each decorates a bold lead-in the reader already has, and the reply format keeps its own heading and needs none of them; 🎯's double use goes with them.
- **The tiers are characterised in `AGENTS.md` itself, and the characterisation splits in two.** The tier states the **force** — what a breach costs and what enforces it — written once in the legend; each rule states its own **scope** as a trigger, `when <X>, never <Y>`, written once on the rule. Nothing about a tier is repeated at rule level.
- **The block is about six lines under the title.** One line per tier naming glyph, meaning, consequence and enforcement; one clause for precedence — the higher tier wins, and the more specific wins within a tier; and one line stating that a rule's own trigger is its scope.
- **Two properties the block must have.** The rules stay readable without it, since each carries its own tier word, and `#doctor` gains a check that every rule wears a tier drawn from the legend — closing the undefined-tier hole permanently rather than by review.
- **What the legend must not do.** It must not restate a tier at each rule, and it cannot live in a separate file, since only `AGENTS.md` reaches the leading block (the walk plan's §4.2).
- **The labels are the weak part.** AUTHORISATION, BOUNDARY, CONDUCT and AUTHORSHIP are nouns; imperative names may recall better, and the labels are free to change — the five-way split and the mapping are the substance.
- **Cost is one-time and near-neutral.** Twelve glyphs leave and five tier words arrive, so the rulebook's length barely moves — and the words are greppable and lintable where the glyphs never were.
- **Objection — a rename fixes nothing by itself.** The tiers only bite where enforcement exists: 🛑 needs the refusals, ⛔ the predicate lines, 🧹 the lint, and 💬 has only the contract behind it, so naming categories without building those adds clarity and no force.
- **Objection — the taxonomy has boundary cases.** `NO-GIT-OR-DEPLOY-PROMPTS` reads as 💬 because the breach is a nagging sentence, yet goes to 🛑 because its subject is who decides; `NO-GIT-EDITOR` reads as an operational mechanic, yet goes to ⛔ because it forbids entering an interactive mode. The test applied throughout is whether the category changes what the agent does, not whether the rule's wording fits.
- **Shipped 2026-09-17 — the categorisation, and only that.** The legend block and every re-mark are in `AGENTS.md`, verified by a glyph census whose count is stated once, in the derived census below; 🔴, 🟡, 🪲 and 🚦 are gone and the `ABSOLUTE RULE:` and `(guideline)` phrases were trimmed with them. Deliberately not shipped: every part of the registry — no slug id, no `#doctor` check, no trigger index — and the Output Contract's subject glyphs, which the review pass resolved by declaration rather than by deletion, so 🎯 still leads two of its bullets.

### 3.1 Maintain — one home, an id, a trigger

- **One home.** The 🔴 list in `AGENTS.md` is the only place a Core Directive is written. Every other mention points at it by id or drops its copy, which is what ONE HOME PER FACT already requires and what its duplicates currently violate.
- **An id — a slug, never a number.** Uppercase kebab, two to five words, naming the **forbidden action** rather than the virtue, because the line it appears on is a check on an action. It makes the set countable, referenceable in one token and lintable, and it is what lets a later reader say `see NO-BINARY-READS` instead of restating the rule.
- **Why not `R1`, `R2`, …** A number needs a lookup: `gate R4 ok` tells neither the user nor the agent what was checked. Deleting a rule also forces a renumber, which breaks every reference and every logged line, while a slug never moves, is greppable in one search, and costs two or three extra tokens per mention — noise against the fifteen a gate line already spends.
- **The ids already half-exist.** Every absolute carries an informal label today — `SCOPE LOCK`, `MODE LOCK`, `ONE HOME PER FACT` — so normalising them into unique uppercase slugs **replaces the label** rather than adding a field, which keeps the rulebook's mass flat instead of growing it.
- **Convention.** Stable forever: never renumbered, never reused, a gap is fine once a rule is deleted; one id per rule, on its first line; a mistyped id must fail loudly — an unknown id refuses and lists the registry rather than matching fuzzily.
- **Rejected 2026-09-17 — the registry does not ship, and the tiers are enough (D-B).** The user's call: the five categories already state a rule's force, so a second naming layer would add mass without adding a fact, which is the reviewer's objection upheld rather than answered. What follows stays as the record of what was weighed — no id enters `AGENTS.md`, a check cites the headline a rule already has, and the table's rows are pre-consolidation besides.

| Tier | Rule | Proposed id |
|---|---|---|
| 🛑 | MODE LOCK — no Code switch without a go-ahead | `NO-MODE-SWITCH` |
| 🛑 | git writes and deploys are the user's call — run on the word, never raised | `GIT-WRITES-NEED-A-GO` |
| 🛑 | never write to `develop` or `main` | `PROTECTED-BRANCHES` |
| ⛔ | SCOPE LOCK — zero scope creep | `NO-SCOPE-CREEP` |
| ⛔ | NEVER ASSUME — no architecture outside scope | `NO-ASSUMED-ARCHITECTURE` |
| ⛔ | NO BINARY READS | `NO-BINARY-READS` |
| ⛔ | REPO ROOT is `.` | `REPO-ROOT-IS-DOT` |
| ⛔ | DEVICE EVIDENCE ON REQUEST — the user's device, the user's timing | `DEVICE-EVIDENCE-ON-REQUEST` |
| ⛔ | NO GIT EDITOR | `NO-GIT-EDITOR` |
| 💬 | QUESTIONS — answer before acting | `ANSWER-BEFORE-ACTING` |
| 💬 | challenge instead of assuming | `CHALLENGE-NOT-GUESS` |
| 💬 | a gate names its action | `GATE-NAMES-ITS-ACTION` |
| 💬 | defer to the user's judgement | `DEFER-TO-JUDGEMENT` |
| 💬 | the explain/discuss gate | `DISCUSS-GATE` |
| 🧹 | ONE HOME PER FACT | `ONE-HOME-PER-FACT` |
| 🧹 | CENTRALISE AND TRIM | `DESCRIBE-DONT-RESTATE` |
| 🧹 | PLAN FILE PLACEMENT | `PLAN-FILE-PLACEMENT` |
| 🧹 | GLOBAL_CONTEXT.md IS STATE-ONLY | `CONTEXT-IS-STATE-ONLY` |

- **Had it shipped, only 🟢 GUIDELINE would have earned no id.** The registry would have covered the four tiers that can require a check — 🛑 · ⛔ · 💬 · 🧹 — while 🟢 is preference, an id being a claim that the rule is worth gating. The decision above closes the question; the reasoning stays because it is what the user weighed.
- **A trigger, which survives the rejection.** Each rule reads `when <trigger>, never <action>`; the id half goes with the registry, and the trigger is the operative half: *never read `.bin`* is inert, *when a path ends in `.bin`/`.tif`/`.xyz`/`.nc`, never open it* fires.
- **Three maintenance operations, and the third is the important one.** Add a rule when a lesson is learned, demote one out of the 🔴 list when it belongs at its point of use, and **delete** one that never fires — a rulebook shrinks by evidence, not by argument.
- **The lint already exists; it gains one check — shipped 2026-09-17 as `(s)`.** [`#doctor`](../../docs/cmd_help_doctor.md:1) lints duplicate rules today; with the registry declined the new check is the tier one — *every rule bullet wears a tier glyph drawn from the legend* — and nothing else, the Output Contract's declared 💬 bullets being explicitly not faults.
- **Cost: zero at run time.** The id and trigger cost a few tokens at authoring time, and the deletions the process produces *save* tokens on every request.
- **Proposed reword — SCOPE LOCK's suggestion clause (2026-09-17, raised on the walk).** The user's revision splits the clause: mention an adjacent opportunity *at the time* when its impact is big — a refactor, a simplification, a bug or issue correction — and list the rest at the task's closure, in the summary presented, with no tracking unless asked. It answers the walk's open finding that the suggestion had no home, and adds the bar the current wording lacks, since "log it as a post-task suggestion" invites logging every speck while closure is never declared.
- **Evidence it was needed.** Over this session the prohibition held — nothing unrequested was implemented — but five adjacent findings went into the todo tracker and none reached a closure summary, because no closure was ever declared; the rule prevented the creep and lost the information.
- **The bar needs a test, not examples.** Refactor, simplification and bug correction are kinds of change rather than magnitudes: a one-line fix is trivial where a cross-cutting simplification is not. The checkable form is two questions — *would ignoring it leave the current delivery wrong or needing rework?* and *does it contradict something written in the repo?* Either yes means mention it now; both no means it waits.
- **The mention needs a shape and the list needs a guaranteed home.** One bullet under a plain label, no discussion and no plan, or the mention competes with the answer; and the list belongs in the task's own last message rather than a follow-up, which means a session that never declares closure lets its ideas die — acceptable for ideas, not for contradictions.
- **One amendment worth taking.** An idea may be listed and dropped, but a finding that the repo now states something false — today's §7a always-loaded claim is the example — should be surfaced as a contradiction so the user can decide, not filed among suggestions nobody reads.
- **Tier consequence.** As written the tweak puts a 💬 CONDUCT obligation inside a ⛔ BOUNDARY bullet, and the legend cannot mark two forces on one rule: either the reporting behaviour moves to the Output Contract, or the bullet carries the ⛔ prohibition alone.
- **Keep the teeth.** "Unrequested features are defects" is the sentence that makes the prohibition bite, and the revision drops it.
- **Shipped 2026-09-17 — the `SCOPE LOCK` reword, verbatim below, now at `AGENTS.md` lines 42 to 50.** Kept as one ⛔ bullet; the alternative, moving the reporting half to the Output Contract as a 💬 rule, was declined. It remains the cleaner shape by the legend's own one-force-per-marker rule, and it pays for that with a cross-reference from the prohibition to the behaviour it triggers.

```markdown
- **⛔ SCOPE LOCK: Zero scope creep.**
  IF the prompt doesn't explicitly request it → do NOT implement it — what the request necessarily
  implies is inside, an adjacent improvement is not.
  IF you spot an adjacent opportunity → never implement it. Mention it now, in one bullet, only when
  ignoring it would leave the delivery wrong or needing rework, or when it contradicts something
  written in the repo — a contradiction is named as one.
  Everything else goes in the task's closing message as a short list, the one exception to *Answer
  only what was asked, then stop*: no follow-up message, no tracking unless asked.
  Unrequested features are defects.
```

- **What each new clause buys.** The implies-clause stops the rule forbidding repairs its own request caused; the two tests make the mention bar checkable rather than a judgement about importance; the exception clause is load-bearing, since without it *Answer only what was asked, then stop* outranks the list and the suggestions vanish as they did today; and the closing-message home is the one the walk found missing.
- **`NEVER ASSUME` reviewed on the walk, 2026-09-17 — partly redundant, and it does not stand alone.** Its overlap is with `💬 Challenge instead of assuming`, since both end in "ask" and differ only in trigger. Three weaknesses: the headline frames *scope*, which is `SCOPE LOCK`'s vocabulary, while the body tests *prompt-reference*; the test exempts the dangerous case, because being asked about a component is no evidence its internals are known; and no remedy is named, leaving read, ask and hedge undecided.
- **Proposed wording — re-anchored on sourcing rather than on the prompt.**

```markdown
- **⛔ NEVER ASSUME: a claim about the system carries a source, or it is not made.**
  WHEN you describe a class, a flow, a signature or a behaviour → ground it in a file you read or in
  the user's words; with neither, say it is unknown and read it before it is used.
  What the prompt references is no exemption: being asked about a component does not make its
  details known.
```

- **The overlap resolves by trigger, not by deletion.** `NEVER ASSUME` governs claims about the system, `Challenge instead of assuming` governs the requirement and the approval, and stating the two triggers is cheaper than merging rules.
- **The slug follows the test.** `NO-ASSUMED-ARCHITECTURE` repeats the hole by naming architecture where the failures are details, while `NO-UNSOURCED-CLAIM` names what is actually checked.
- **Objection.** A sourcing requirement reads as forbidding synthesis, since an architectural answer would need a citation per sentence; it constrains the root of a claim rather than its depth, and anything built on one file read is sourced.
- **Remediation — recommended: re-anchor in place, in one write with `Challenge`.** `NEVER ASSUME` takes the sourcing headline, the exemption sentence and the named remedy (read it, or say it is unknown), while `Challenge instead of assuming` gains a two-clause amendment stating its own trigger; the two then read as complementary instead of duplicative, and the write is single because the ambiguity belongs to the pair.
- **Fallback, if churn matters more than framing.** Keep the headline and add only the exemption sentence and the remedy, which closes most of the hole while leaving the rule named after a frame it no longer uses.
- **Declined options, with reasons.** Merging into `SCOPE LOCK` puts the doing and the asserting halves in one bullet that passes eight lines; folding into `Challenge` demotes a ⛔ prohibition into 💬 conduct.
- **Knock-ons and verification.** The registry slug becomes `NO-UNSOURCED-CLAIM`, the walk's item 2 closes on the same resolution, and no `#doctor` check can lint prose, so the predicate is gate-able only by an emitted line later. The test afterwards is one question per claim — does it carry a source, or is it labelled unknown — and the proof it worked is that the rule covers the case it currently exempts.
- **Consolidation taken instead — one home for the family.** The user's call: fold `NEVER ASSUME` and `Challenge instead of assuming` into one ⛔ rule, placed directly after `SCOPE LOCK` so the two boundaries read together — don't do extra, don't assert extra — and delete both originals. The cross-references to MODE LOCK and QUESTIONS carry over so neither of those rules is orphaned.

```markdown
- **⛔ ASK, DON'T GUESS: an unknown is stated, never filled in.**
  WHEN a detail is not in the prompt and not in a file you read → answer with what you have and name
  the gap in one line, or read the source and cite it. Being asked about a component is no exemption,
  and neither is a plausible reconstruction.
  WHEN the request itself is ambiguous, or approval is merely implied → stop and ask, naming the
  action it authorises: authorisation stays with MODE LOCK, answer-first with QUESTIONS.
```

- **What it absorbs, and what it leaves alone.** Clause one is `NEVER ASSUME` re-anchored on sourcing; clause two is `Challenge`. `QUESTIONS` stays separate, its subject being an input rather than a gap — a question is answered, not derived — and `SCOPE LOCK` keeps the doing half.
- **Knock-ons of the consolidation.** The registry drops `CHALLENGE-NOT-GUESS` and gains `ASK-DONT-GUESS` in place of `NO-ASSUMED-ARCHITECTURE` and `NO-UNSOURCED-CLAIM`; and the walk's item 2 is the item that closes on it.
- **Cost, stated plainly.** One bullet now carries two triggers and a marker can state only one force, so this knowingly mixes them; the net line count is unchanged, and both halves fail the same way, which is why the merge is defensible where the earlier `SCOPE LOCK` merge was declined.
- **Shipped 2026-09-17, verbatim, at `AGENTS.md` lines 53 to 56**, validated against the draft: both originals deleted, the placement directly after `SCOPE LOCK` kept, and the MODE LOCK and QUESTIONS references intact. Two cosmetic artefacts are left for the task's closing list rather than raised here — a surviving double blank line after `SCOPE LOCK` and after `QUESTIONS`, and the rule's first clause collapsed into one over-long line carrying a double space.
- **A new overlap the consolidation created.**
- Clause two — *the request is ambiguous, or approval is merely implied* — now covers ground `MODE LOCK` already owns for mode switches, where the deleted `Challenge` rule was the narrower of the two. Item 3 of the walk settles which one keeps the implied-approval half.
- **Item 3 `MODE LOCK` discussed 2026-09-17 — the user's spirit statement reframes the rule.** *Every implementation is gated on an explicit order; with none, ask* — which makes the rule about **acting**, the mode switch being only its mechanism, and it also settles the overlap above: `MODE LOCK` owns *whether to act*, `ASK, DON'T GUESS` narrows to *what was asked is unclear*.
- **The git clause, scoped rather than general.** The git rule already grants the operation itself at `AGENTS.md:70`, so the missing piece is *entailment* — the edits an operation necessarily requires, `#merge`'s conflict resolution being the real case. A general "an explicit git action permits a switch to Code" would hand over the licence a mode represents, so a `#commit` would then authorise whatever is edited next; the scoped form authorises those edits and only those, the entitlement living for the operation and dying with it.

```markdown
- **🛑 MODE LOCK: implementation waits for an order.**
  WHEN the next step would change a file, a branch or a mode → it happens only on an explicit
  directive naming the action — "implement", "go ahead", "switch to Code" — never on a plan being
  approved, and never on a question.
  WHEN a `#`-command that necessarily entails edits is invoked — conflict resolution is the case —
  → those edits are authorised, and only those: the entitlement is scoped to the operation, not to
  the mode it needs.
  With no order, ask. When a discussion closes, state what was resolved and that nothing is
  outstanding — naming the points, never asserting their absence — and never name the next action:
  "ready for `#implement`" is the user's sentence to write.
```

- **The permitted close, added 2026-09-17.** The ban forbade a nudge without saying what may be said, which reads as a gag; the allowance is *state what was resolved and that nothing is outstanding*, in an evidenced form. Naming the points is the substance, since "all open points addressed" is a completeness claim no agent can substantiate, and the line between a state and a nudge is whether the next action is named — which is the one thing still forbidden.
- **Placement, decided by recommendation rather than by the tiers.** The allowance sits inside `MODE LOCK` because that is where a reader looks for permission, at the cost of one mixed-tier bullet; the tier-clean alternative is to move it to the Output Contract as 💬, which pays instead with a cross-reference from the rule to the format.
- **Shipped 2026-09-17 at `AGENTS.md` lines 58 to 65**, faithful to the draft with one gain — clause 1 keeps *never on a plan being approved* beside *never on a question*, which is the pair that bites. Two items did not land and are this rule's residue: clause two of `ASK, DON'T GUESS` still covers *approval is merely implied*, so it and `MODE LOCK`'s clause 1 state one prohibition twice; and `QUESTIONS` still carries *not an implicit implementation order* while clause 1 now says *never on a question*.
- **What the reword buys and risks.** *Implementation waits for an order* covers what the old title missed — edits made without ever leaving Architect — while *necessarily entails* stays a judgement, and a generous reading brings the leak back; it is still far narrower than a mode-wide permission, which is what the alternative amounts to.
- **Item 4 `git writes` reviewed 2026-09-17 — the read-only list is a closed enumeration that already misses its own workflow.** It permits `status`, `log`, `branch`, `diff` and `fetch`, while the documented merge pre-flight runs `git rev-list --count` at [`cmd_help_git.md:25`](../../docs/cmd_help_git.md:25), so the rule fails to cover a query the corpus itself performs. The fix is a principle with examples — *a query that changes nothing is always permitted*, the list as illustrations — which also reaches `show`, `blame`, `ls-files`, `remote -v` and `stash list`.
- **And the boundary with `MODE LOCK` needs one clause.** That rule owns the *edits* an operation entails, this one the *operations* themselves; without the split the two read as one instruction written twice, in the same tier, which is the shape the merge removed elsewhere.
- **Objection.** A principle invites the argument that some writing command is harmless; the counter is the mechanical test — does the working tree, the index or a ref change? — where a list is only ever as long as its last update.
- **Proposed wording, 2026-09-17.** It also gives the rule the name it lacks today, being the only 🛑 bullet without one, which leaves it awkward to cite and invisible to the registry.

```markdown
- **🛑 GIT WRITES NEED A GO-AHEAD: a command that changes the tree, the index or a ref waits for one.**
  WHEN it would change the tree, the index or a ref → it runs only on the user's explicit word:
  `add`, `commit`, `push`, `merge`, `rebase` and their kin, staging included — never stage
  preemptively, and a `new_task(Code)` subtask is no exemption.
  WHEN it changes nothing → it always runs, in every mode: `status`, `log`, `diff`, `fetch`,
  `rev-list`, and anything else read-only.
  WHEN a git `#`-command is invoked → the invocation is the go-ahead: execute it rather than asking
  again, asking the user to run it, or reading it as a request for one. `#commit`, `#push`, `#merge`
  and `#cherry` confirm the operation's scope and never re-litigate authorisation; `#new`, `#move`,
  `#move new` and `#rename` ask nothing.
  `MODE LOCK` owns the edits an operation entails; this rule owns the operation.
```

- **What changes and what is kept.** Clause 2 is the substantive change, a principle in place of a closed list that already misses `git rev-list`; clause 3 survives near-verbatim because the `#`-command exception and the confirm-versus-ask-nothing split are the tiered policy the git page and the command rows rest on; clause 4 states the `MODE LOCK` boundary, at the cost of the cross-reference that was declined once for `SCOPE LOCK`.
- **Shipped 2026-09-17 at `AGENTS.md` lines 72 to 81**, faithful to the draft: the name, the read-only principle with `rev-list` among its examples, and the boundary clause all landed, leaving one long line as cosmetic residue.
- **Item 5 `PUSH, COMMIT AND DEPLOY ARE USER-OWNED` reviewed 2026-09-17 — sound, with an apparent contradiction to close.** It governs *speech* where item 4 governs *execution*: it forbids soliciting rather than performing, which is why "never ask whether to push" and `#push`'s own "asks before pushing" do not conflict — one is solicitation, the other a scope confirmation on an invoked command. The two read as contradictory to a fresh reader, being four lines apart in the same file, and naming the distinction is the cheap fix.
- **And two clauses already have homes worth naming.** "Never list one as a next step" is the specific instance of `MODE LOCK`'s "never name the next action", and the device clause — *a `#`-command away from them, not a step for the agent to offer* — is the worked example that makes the general rule recognisable, so the pair should read as general and specific rather than as two rules that happen to agree.
- **Objection.** Both adjustments spend words on a contradiction nobody has suffered; the counter is that a rulebook which contradicts itself on one screen teaches its readers to skim.
- **Proposed wording, 2026-09-17 — the spirit as the headline and four verbs as the body.** The user's call was to make the rule clear, explicit and short, with *user-owned* stated as the spirit rather than implied by a list.

```markdown
- **🛑 PUSH, COMMIT AND DEPLOY ARE USER-OWNED: the agent never raises them.**
  WHEN they were not asked for → never ask, offer, list or remind, in any mode: no "want me to
  push?", no `apk-deploy.bat` or `apk-push.bat`, no note that commits are unpushed — a device check
  included, since that too is a `#`-command away from the user and not a step to offer.
  WHEN an invoked git `#`-command confirms → that is scope, not solicitation.
```

- **What the compression does.** Clause one lists the four verbs — ask, offer, list, remind — that the six-line original states in prose, and folds the device example into that list instead of giving it a paragraph; clause two resolves the apparent collision with `#push` and `#commit`; and the solicited-versus-unsolicited distinction now costs one line rather than a discussion.
- **Objection.** The cut drops *proposing any of them is a workflow violation*, and removing a stated consequence is how a rule quietly loses force; the counter is that consequences now live once in the legend, where 🛑 already says a breach voids the action.
- **Does it stand alone, and should it fold? — answered 2026-09-17.** The four-line version did **not** stand alone: its second clause repeated item 4, which already states that `#commit`, `#push` and `#merge` confirm scope and never re-litigate authorisation, so that clause is deleted and the rule becomes three lines with one job.
- **Neither fold is right.** Item 4 owns git *execution* and is already ten lines, and deploy is not git — `apk-deploy.bat` and `apk-push.bat` belong to the APK pipeline — so merging would misfile half the rule; the Output Contract owns reply shape, while this is a permission boundary, and the 🛑 row is where a reader looks for what must not be done.
- **Shipped 2026-09-17 at `AGENTS.md` lines 86 to 89**, three lines exactly as proposed, with no pointer added to the `#push` and `#commit` rows — their own "never proposed, never reminded" already resolves the apparent collision from inside the row.
- **Item 6 protected branches reviewed 2026-09-17 — three adjustments, one of them a consequence of the tiers.** The bullet's *"supersedes all other commands"* and *"user override does not lift it"* predate the legend, which now states precedence itself; what the rule should name instead is the **mechanism** — `#push`, `#commit` and `#merge` refuse on these branches — which is checkable and already true in §7b. Second, the override refusal needs its **reason** attached or it reads as an agent disobeying its user: the branches are shared, so the decision is not the user's alone to waive in conversation. Third, the **pull-request sentence is the constructive half** and stays, since it says what happens instead and stops an absolute prohibition reading as a wall.
- **Proposed wording for item 6.**

```markdown
- **🛑 PROTECTED BRANCHES: `develop` and `main` are never written to — by anyone, on any instruction.**
  WHEN the target is `develop` or `main` → no push, force-push, revert, direct commit or local merge,
  and the refusal is mechanical: `#push`, `#commit` and `#merge` decline rather than ask.
  The user cannot lift it in conversation, because the branches are shared and a bad write lands on
  everyone else's base; integration happens through a pull request.
```

- **Objection.** Naming the commands as the enforcement makes the rule only as strong as those rows, and one edited row would quietly weaken it; the counter is that a refusal which can be pointed at is stronger than a supremacy clause nobody can test.
- **Does it stand alone, and should it fold? — answered 2026-09-17.** It stands, and the obvious fold into `docs/GIT_WORKFLOW.md` must not happen: docs are lazy-loaded while a 🛑 rule has to sit in the always-loaded prefix before a git command is even considered. The §7b rows fail as a home for the mirror-image reason — a row is read at invocation, whereas this rule must also stop the agent proposing the operation, which happens earlier.
- **The real problem is three homes for one fact.** The prohibition is stated here, in the `#merge`, `#push` and `#commit` rows, and as GIT_WORKFLOW's Hard Rule; the trim is to give each home a distinct job — the rule carries the prohibition and the no-waiver clause, the rows carry the refusal, the doc carries the procedure.
- **Correction to this section's own earlier proposal.** *Supersedes all other commands* should stay in shortened form rather than being replaced by the mechanism alone, because it orders a **rule against the command registry**, which the legend's precedence cannot express — that orders rules against rules. Shortened to *no command overrides it*, it keeps the claim at half the weight.
- **A contradiction found while reviewing item 6, and swept the same day.** [`cmd_help_git.md`](../../docs/cmd_help_git.md:4) marked the git Hard Rule with 🔴, a glyph the legend had just retired, so the page pointed at a tier that no longer existed; the cleanup pass re-marked it 🛑.
- **Item 6 status: approved and written 2026-09-17.** The bullet carries the shortened no-command-overrides claim rather than the mechanism alone, and the clause answering `GIT WRITES`' invoked-`#`-command grant was added in the review pass — the item's open half is closed.
- **Item 7 `NO BINARY READS` reviewed 2026-09-17 — the same closed-list defect as item 4, now a pattern.** The rule names `.bin`, `.tif`, `.xyz` and `.nc` while `tools/litto3d_tiles/` and `data/app-assets/depth/` hold `.asc` rasters and their `.asc.gz` variants, which are exactly the opaque blobs the rule means. The fix repeats item 4's — state the class with the list as examples: *large spatial data files — `.bin`, `.tif`, `.xyz`, `.nc`, `.asc` and their kin — are opaque blobs.*
- **The pattern is worth folding once.** Two of the ten hard rules enumerate their scope, and both already fail against ground truth that was in the repository when they were written; a rule stateable as a class with examples should be stated that way, and an enumeration kept only where the list *is* the rule.
- **What item 7 gets right and keeps.** The three verbs — open, read, search — cover the tool surface including `search_files`, and the constructive half says where the facts live rather than only forbidding the file, which is what stops the rule reading as a prohibition on learning.
- **Objection.** A class invites the argument that a small text-shaped raster is readable after all; the counter is that the entry criterion is measurable — too large to read and machine-shaped rather than authored — and `.asc` is the proof that a text extension can still qualify.
- **Proposed wording, 2026-09-17 — only the scope clause changes.** The rule's own sentence about opaque blobs becomes the headline, which is the promotion that makes the extension list an example rather than the definition.

```markdown
- **⛔ NO BINARY READS: a spatial data file is an opaque blob.**
  WHEN it is machine-shaped and too large to read as text → never open, read or search it —
  `.bin`, `.tif`, `.xyz`, `.nc`, `.asc`, gzipped variants included. Read its metadata, or the
  code that parses it.
```

- **What is deliberately kept.** The three verbs cover the whole tool surface including `search_files`, and the constructive half is well aimed rather than decorative — the `.aux.xml` and `.prj` files beside those rasters are precisely the metadata it sends a reader to, so the rule tells you where the facts are instead of only forbidding the file.
- **The entry criterion carries the weight now.** *Machine-shaped and too large to read as text* is judgeable at the moment a file is met, where an extension list only ever speaks for the formats someone remembered; the objection that a small text raster is fair game is answered by the criterion itself, with `.asc` as the standing proof.
- **Applied 2026-09-17 — both outstanding writes landed.** Item 6's `PROTECTED BRANCHES` replaced the original bullet, with the double blank line above it collapsed in the same patch, and item 7's rewording landed with it. Neither carries a line anchor any more: the bullet names are stable, the line numbers were not.
- **A defect repaired in the same pass, and the lesson it carries.** The item-7 write had lost its leading list marker, so the rule was rendering as a continuation of the `CENTRALISE AND TRIM` paragraph rather than as a rule at all. The glyph census that validated the categorisation cannot catch that class, since it counts glyphs rather than list structure — which is what `#doctor`'s malformed-section check exists for, and the reason a review-by-reading still earns its place beside any census.
- **Cleanup phase applied, 2026-09-17 — the residue settled where meaning allowed.** `QUESTIONS` lost the sentence `MODE LOCK` already carries, and `ASK, DON'T GUESS` now points at `MODE LOCK` for implied approval instead of restating it; the stale glyphs were swept to zero across `docs/` and `AGENTS.md`, `GIT_WORKFLOW.md` was cut to the exit it alone owns, §7a's always-loaded claim now says what is true, and the cosmetic blank lines went with them.
- **Item 8 `REPO ROOT` reviewed 2026-09-17 — keep as it stands.** One line, unmistakable, and its failure mode is a wasted call rather than a shipped defect; the only gap is that it states no reason, and closing that would double its length for a mechanical rule whose mistake is already hard to make by accident. This is the walk's second keep, after item 1.
- **What item 8 exposes about the tier itself.** It guards *cost* rather than harm, which puts it beside `NO BINARY READS` — and the legend's ⛔ sentence reads *a breach ships a defect*, so either that sentence widens by a clause to include the price of a bad read, or those two rules belong a tier lower. Left as a finding rather than resolved, since it is a change to the legend rather than to a rule.
- **Item 9 `DEVICE LOGCAT WORKFLOW` reviewed 2026-09-17 — it collides with `PUSH, COMMIT AND DEPLOY`.** This rule instructs the agent to *ask the user to deploy*, while the 🛑 rule forbids offering a device check and calls `apk-deploy.bat` something the agent never raises. Both cannot hold: the tier order resolves the clash silently in the 🛑 rule's favour, which cancels the logcat workflow's ask entirely.
- **The fix is a condition rather than a deletion.** The ask is legitimate as an answer to the user's request and illegitimate as an offer, so the rule starts from *wait until the user asks for it*, which preserves the workflow and dissolves the conflict without touching the higher-tier rule.
- **And its name is the last one stating a subject rather than a force.** Every hard rule now leads with its force — `GIT WRITES NEED A GO-AHEAD`, `PROTECTED BRANCHES` — while `DEVICE LOGCAT WORKFLOW` names a topic, so the proposed headline is `DEVICE EVIDENCE ON REQUEST`.

```markdown
- **⛔ DEVICE EVIDENCE ON REQUEST: never capture the device unprompted.**
  WHEN a debug session needs on-device evidence → wait until the user asks for it, then ask them to
  deploy and perform the operation, and fetch the logcat only once they tell you to.
  The user drives the device; the agent pulls the evidence on command.
```

- **Objection.** The condition costs a clause and could be read as barring the agent from ever raising the need for evidence; the counter is that the closing sentence already assigns the two roles, and the alternative is a workflow rule a higher-tier rule silently cancels.
- **Item 10 `NO GIT EDITOR` reviewed 2026-09-17 — the substance stands and the wording needs nothing.** Two long lines, both carrying information a shorter version would drop: the flags, the two editors, and the scope of every mode, task and agent.
- **Its tier is the third instance of a pattern the walk has now established.** A wedged terminal is a cost rather than a released defect, exactly like `REPO ROOT`'s wasted read and `NO BINARY READS`' noise — so **three of the six ⛔ rules guard cost** while the legend's ⛔ line reads *a breach ships a defect*. The cheap fix is one clause in the legend — *or one that cannot be recovered cheaply* — rather than demoting three rules whose force is real.
- **A placement note to record rather than fix.** It is the only hard rule outside the Core Directives, sitting in §5 among pointers. Leaving it there is defensible, because §5 is where a git command is looked up, but it does mean hard rules live in two places.
- **Item 9 remains unresolvable until written.** It was jumped over by a named `#walk` source rather than closed, so the level cannot close cleanly while its rewording stands proposed and unwritten.
- **Recommendation on item 10, 2026-09-17 — fix the legend rather than the rule.** Three of the six ⛔ rules guard cost — a wedged terminal, a flooded context, a wasted root read — so one clause in the tier line repairs the mismatch for all three: *a breach ships a defect, or costs something that cannot be recovered cheaply*. Demoting them would spend real force to tidy a sentence.
- **Three precisions worth taking in the rule itself, at neutral length.** The agent does not *open* an editor, the command does; *re-run it with them* has no clear antecedent; and the triple scope sentence can be half its length. A light reword fixes all three without adding a line.
- **Placement left alone deliberately.** §5 is where a git command is looked up, and a line explaining where hard rules live would be meta-text that ages badly; the note is recorded in this plan rather than in the rulebook.
- **What would be wrong here.** Rewriting the rule to lead with its harm in the manner of `NO BINARY READS` — that rule needed it because its list was factually wrong, while this one is accurate and only imprecise.
- **Compounding directed, 2026-09-17 — `GIT WRITES AND DEPLOYS ARE THE USER'S CALL`.** The user's instruction folds `PUSH, COMMIT AND DEPLOY ARE USER-OWNED` into `GIT WRITES NEED A GO-AHEAD` so one rule owns the whole git discipline, and adds the point their sentence carries: when a commit, a push or a deploy happens is not the agent's concern, and its delivery is complete when the work is. The rule count falls to eighteen and the deploy clause keeps its home inside the compounded text.
- **The trade recorded rather than argued.** The two rules sat at different distances from the code — execution against speech — and a reader now meets both in one bullet; the compensation is that the read-only principle, the go-ahead, the solicitation ban and the delivery point can no longer drift apart.
- **Applied 2026-09-17, verbatim with two clauses restored.** The compounding landed with the `git add` staging nuance and the `MODE LOCK` boundary clause put back, both having been dropped from the draft — each carries content a review would otherwise call a deletion. The deploy rule is gone, so the count falls to eighteen.
- **Two seams the merge cost, recorded honestly.** The compounded rule is two lines longer than the two it replaced, because each fold needed a joining sentence; and the device phrase now stands inside it *and* inside `DEVICE EVIDENCE ON REQUEST`, so that subject has two homes until walk items 1 and 2 are settled — the clean exit being to delete the phrase from the compounded rule and let the device rule own its own subject.
- **Revision walk, item 1 — closed as riding on item 2.** The blocking finding that the device condition gates on *evidence* rather than on a *deployment* is now a question of which rule governs the act, which is exactly what item 2 decides; nothing further can be settled about the condition until the tier is chosen.
- **Revision walk, item 2 — recommend promotion to 🛑.** The rule waits for the user's word, which is the legend's own 🛑 description, so a ⛔ marker claims less force than the rule carries and lets the compounded 🛑 rule overrule it by default. Promotion lets the legend's second clause do the work — *the more specific wins within a tier* — with no exception clause written anywhere.
- **The alternative is one deletion and one behaviour change.** Removing the device phrase from the compounded rule gives the subject a single home, but it leaves the agent free to offer a device check generally, which contradicts the spirit the user stated for commit and push; the promotion, by contrast, adds no permission and only states the force the rule already had.
- **Tally after promotion.** Counted at the time and superseded by the next bullet; the census below is the only count this plan states.
- **Superseded 2026-09-17 by the user's direction — the ask disappears instead of changing tier.** The device rule becomes a *status* rather than a *request*: the agent says the build carrying the logcat is ready and stops, the user decides whether, when and how to deploy and test, and the only later move is fetching the logcat once told. With no ask there is nothing for the 🛑 ban to overrule, so the promotion above is no longer needed and one tier change is saved.
- **The distinction that carries the fix is request versus status.** *The build is ready* is a statement about an artefact; *can you deploy?* is a demand for an action, and the compounded rule's spirit only forbids the second — which is why the device rule can still speak at all under it.
- **One clause has to leave the compounded rule.** *A device check is the same case rather than a step to offer* would forbid the readiness line and gives the device subject a second home; deleting it restores ONE HOME PER FACT and makes the device rule the only place a device question lives.
- **Two boundaries stated inside the rule.** The readiness line must not read as the agent waiting on the user for its own completion — the work is done and the build merely available — and the user's *how* stays out of scope, so naming the logcat is permitted where prescribing adb steps or a test order is not.
- **Tier left at ⛔, deliberately.** With the clash gone the marker is accurate: the breach is a device touched unprompted or a demand chased after being answered, both defects, which is the ⛔ condition as the widened legend words it.

```markdown
- **⛔ DEVICE EVIDENCE ON REQUEST: the user's device, the user's timing.**
  Committing, deploying and validating are never the agent's concern: it does not ask, offer or track
  them, and its own work is finished when the change is.
  WHEN a debug session needs on-device evidence → the one exception: say the build carrying the logcat
  is ready, then stop, leaving the deployment and the test to the user.
  WHEN the user says it is deployed and the test has run → fetch the logcat, and nothing before that.
```

- **Objection.** Removing the phrase from the 🛑 rule hides the device case from anyone reading that rule alone; the counter is that it still names `apk-deploy.bat`, and the device question belongs where the device is discussed.
- **Revision walk, item 2 — closed by design, not by tier.** The redirection retired the promotion: with the device rule reporting readiness rather than asking, no two rules compete, so nothing needs ranking and the marker's force is no longer a live question; the promotion's tally arithmetic went with it, and no count is restated here.
- **Revision walk, item 3 — the widened clause misses the rules it was written for.** `A breach ships a defect, or costs something that cannot be recovered cheaply` fails on its own examples: a wasted read, a flooded context and a re-run terminal are all *cheap* to recover, which is why the qualifier excludes three of the six ⛔ rules. The sentence needs to name the cost rather than its recoverability.
- **Proposed replacement for the ⛔ line.**

```markdown
- ⛔ BOUNDARY — never cross the requested surface, never assert what was not given, never open opaque data. A breach ships a defect, or spends the user's time and tokens for nothing.
```

- **Why *for nothing* carries it.** The waste is what the tier covers — a read that teaches nothing, a session left wedged, a context filled with noise — and naming the waste keeps the clause from swallowing every mistake that costs a minute, which is the failure mode of a bare cost clause.
- **Objection.** Even worded that way the clause widens ⛔ beyond defects, and a tier defined by two unlike things is harder to apply than one; the counter is that three rules already sit there for exactly this reason, and the alternative is demoting rules whose force is real.
- **The tally this section has now stated twice is itself a finding.** Two different counts were written into the walk record on the same afternoon, which is what happens when arithmetic is kept in prose rather than derived from the file; the census belongs in one place, produced by a search, not restated per bullet.
- **Census derived 2026-09-17, re-derived after the review pass — one statement replacing the rest.** A search over the rule bullets returns **4 🛑 · 6 ⛔ · 4 💬 · 4 🧹 · 1 🟢 = 19 rules**, the fourth 🛑 being the dependency-approval clause the review found unmarked in §4. Three earlier counts were kept in prose — four 🛑 before the git pair merged, then eighteen, then nineteen — which is why anything needing this number should re-run that search rather than quote this line.
- **Challenge pass, then Ask, then one fix pass — 2026-09-17.** The challenge held the day's mechanism intent — traceable rules, a tier reload, cost only where it matters — and named three intents it did not hold: smallness, strict absoluteness, and one home. Ask re-derived the census, found no blocking item and twelve should-fix ones, and the fix pass settled them in one write: `PROTECTED BRANCHES` gained the no-`#`-command-overrides clause precedence alone needed, the device rule's *one exception* became *the logcat case* so nothing competes with the 🛑 rule, its restatement of that rule's spine is now marked deliberate, the legend declares the Output Contract's bullets 💬 with subject glyphs, §7a allows one marked restatement, §4's dependency clause wears the 🛑 it never had, the `#rule` row states that a tier name is matched exactly rather than by cascade, the derived `cmd_help.md` gained `all` and lost its stale line count, and this plan lost its duplicate counts and its drifted anchors. Deliberately left undone: the rulebook trim, which stays `D-E`, and any re-statement of the rules as true absolutes, which the ladder replaced on purpose.
- **`#rule` redesign proposed 2026-09-17 — the legend on bare, a tier as argument.** The user's call: remove the three-tier rule manager, let bare `#rule` print the legend, and let `#rule [fuzzy tier]` reload that tier's rules. It is cheaper than the id-based narrow reload this section argued for, since the five tiers already exist and are greppable — no registry and no new vocabulary — and it retires D-B for the reload half.
- **What it costs, precisely.** Four reference sites assume `#rule` files a rule: the §7a bullet naming `#rule global` as the Core-Directive path, the §7b `#todo` row mirroring it, the `#rule` row itself, and the C3/C5 targeting rules at `docs/cmd_help_bake.md:24` — plus a rewrite of `docs/cmd_help_rule.md`. Finite, not a sweep.
- **One capability dies with the manager — and it dies without a record of use, verified 2026-09-17.** A search for `#rule global`, `#rule add` and the section target returns eleven hits, every one of them *describing* the capability — two design plans, the walk plan's D5, this plan's own notes and an archived simplification doc — and none recording a rule that arrived that way. So the honest phrasing is that a rule learned mid-conversation had nowhere to go before and has nowhere to go now: the deletion removed a written capability rather than a practised one, and what still deserves recording is the path — a new rule arrives as the user's edit, with a plan line as the observation point.
- **Two clauses the command needs or it breaks.** `#brief` subtracts containment blocks, so a tier dump must be exempt or `#rule ⛔` prints nothing; and the output should name the tier and carry a one-line fingerprint so a reader knows what was loaded.
- **The word *enforce* is aspirational.** Printing a tier re-anchors attention and cannot enforce anything; enforcement remains the command refusals, and the gates are unbuilt.
- **Suggested §7b row.** `#rule` — bare prints the five-line tier legend; `[fuzzy tier]` reloads that tier's rules from `AGENTS.md` and prints them with a one-line fingerprint; explicit invocation only.
- **What it retires besides the registry.** Ids were also proposed for labelling gate lines, but the rules already carry names, so a line can read `gate NO BINARY READS ok` — the registry was unnecessary for that too.
- **The redesign's open edges, one line each, 2026-09-17.** *Whole-file reload* — resolved as `#rule all`, a stale verdict rather than a dump, the read being the reload. *Rule authoring* — recommended dead and deliberately so, with a new rule routed through a plan line and a hand edit, since the rulebook has one author and the command had no recorded use. *Granularity* — stay at tier, the cascade having already failed to match these names reliably. *Fingerprint* — keep the hash, the only element separating a reload from a re-print. *Dump shape* — headline plus trigger, the operative half, the prose staying in the file. *The command itself* — keep it: recency is the failure mechanism it addresses, and six lines is a cheap lever on it.
- **The point-of-use read is the complement, not the casualty.** `#rule all` refreshes the whole book on demand; the command-level read refreshes one rule at the moment it binds, and it is `AGENTS.md`'s own Lazy-Load pattern extended from docs to rules rather than a new mechanism.
- **Census derived 2026-09-17 — one statement replacing the rest.** A search over the rule bullets returns **3 🛑 · 6 ⛔ · 4 💬 · 4 🧹 · 1 🟢 = 18 rules**. The earlier claim of four 🛑 was wrong, counted before the git pair merged; anything needing the count should re-run that search rather than quote this line.

```markdown
- **🛑 GIT WRITES AND DEPLOYS ARE THE USER'S CALL: run them only on the word, and never raise them.**
  WHEN a git command would change the tree, the index or a ref → it runs only on the user's explicit
  word: `add`, `commit`, `push`, `merge`, `rebase` and their kin, staging included, and a
  `new_task(Code)` subtask is no exemption. A `#`-command is that word — `#commit`, `#push`, `#merge`
  and `#cherry` confirm scope alone and `#new`, `#move`, `#move new` and `#rename` ask nothing.
  WHEN a command changes nothing → it always runs, in every mode: `status`, `log`, `diff`, `fetch`,
  `rev-list`, and anything else read-only.
  WHEN push, commit, deploy or a device check was not asked for → never ask, offer, list or remind, in
  any mode: no "want me to push?", no `apk-deploy.bat` or `apk-push.bat`, no note that commits are
  unpushed.
  When those happen is not the agent's concern: its delivery is complete when the work is, and it
  never waits on, tracks or reports a commit, a push or a deploy.
```

- **Knock-on to the walk's own items 1 and 2.** With the device ask folded into the compounded rule, those two reduce to one question — whether `DEVICE EVIDENCE ON REQUEST` moves to 🛑 and wins by specificity, or carries the exception itself.
- **The one overlap left is a general and a specific.** *Never list one as a next step* is the instance of `MODE LOCK`'s *never name the next action*, which the legend already orders within a tier; keeping the specific is what makes the temptation recognisable, and the specific wins where they touch.
- **Residual risk of the deletion.** A reader who meets `#push`'s "Asks for confirmation" before item 4 could still wonder; if that matters more than three lines, a five-word pointer there costs less than the clause it replaces.

```markdown
- **🛑 PUSH, COMMIT AND DEPLOY ARE USER-OWNED: the agent never raises them.**
  WHEN they were not asked for → never ask, offer, list or remind, in any mode: no "want me to
  push?", no `apk-deploy.bat` or `apk-push.bat`, no note that commits are unpushed — a device check
  included, since that too is a `#`-command away from the user and not a step to offer.
```
- **Knock-on to record.** `QUESTIONS` already states that a question is not an order, so the two rules should say it once between them rather than in both.
- **Why the `#implement` ban stays — asked on the walk, 2026-09-17.** The objection is to the nudge rather than the information: *ready for `#implement`* is the agent telling the user what to do next, which makes implementation the default their silence can carry, and the clause therefore guards the moment before `MODE LOCK` bites — not acting without permission but manufacturing its appearance. It is also the cheapest clause to obey, banning one sentence while withholding no fact, since plan completeness is reportable and `#status` and `#review` answer on request.
- **The refinement, if it is taken.** Split the fact from the recommendation: state that a plan is complete with nothing outstanding, never phrase it as the action to take. A strict reading of the current clause would withhold the fact as well, which costs the user the most useful thing an agent often knows.
- **A tension to settle in the same write.** `SCOPE LOCK`'s new closing-list clause would admit a next-step note while this clause forbids that one instance; the legend's precedence already resolves it, 🛑 over ⛔, but relying on precedence silently is exactly the drift the tier order exists to make visible.

### 3.2 Reinforce — a check where the rule applies

- **Tier 1 — the refusal, at zero cost.** Rules that sit next to an action are enforced by the action's own definition: `#push`, `#commit` and `#merge` refuse on `develop`/`main`, `#bake` and `#archive` fire only on explicit invocation. The model never has to remember, and nothing is added to any prompt.
- **Tier 2 — a predicate at the point of action, about fifteen tokens, over five named classes.** Scoped 2026-09-17 to the actions no command can refuse: adding a dependency, opening a machine-shaped data file, starting work without an order, touching the device, and stating a claim about the code with no file read behind it. The line prints the rule's own headline and a verdict — `⛔ NO BINARY READS — ok`, or `— stopped` when it fails — so nothing new has to be named and no id is needed, and only those five pay.
- **Tier 3 — the audit, invoked rather than scheduled.** `#review` sweeps the session against the same five classes and reports any that ran without a verdict. It sits on `#review` and not on [`#doctor`](../../docs/cmd_help_doctor.md:4), whose remit is the xTrack stack's structure; it costs one pass over text already in context.
- **Tier 2 ships 2026-09-17 — the recommended option taken (D-D).** The five classes belong in the always-loaded zone, since a bake step cannot cite a lazily-loaded plan, so the legend section of `AGENTS.md` now names them; the line itself prints a rule's own headline and a verdict, and its self-reporting stays the price it pays.
- **Tier 3 ships with it, and it is the point.** Only the sweep can catch a gate line printed without a check, which is the failure tier 2's self-reporting invites; without tier 3 the line would be unfalsifiable and better dropped.
- **Never per turn.** Printing the rules, re-reading the file or restating the triggers every exchange is the expensive anti-pattern, and it buys only the salience the point-of-action line already delivers where it matters.
- **The honest weakness.** Tier 2 is self-reported: an agent that has already drifted can print `ok` without checking, so the line is evidence of intent rather than proof. Tiers 1 and 3 are the ones with teeth, and tier 3 is what keeps tier 2 honest.

### 3.3 Refresh — narrowly, and only on demand

- **Superseded 2026-09-17 by the shipped redesign.** The command now prints the legend on bare and reloads **one tier** on argument, so the id-shaped narrow reload below and the whole-file reload it sat beside are both designs the file no longer carries. The tier form turned out to be the cheaper idea: a tier is a partition that already exists, so it needs no registry, and 🛑 is three rules where the whole book is eighteen.
- **Narrow: `#rule [tier]`.** Re-anchoring one tier is the repair for drift in that part of the book — six rules at ⛔, four at 🧹 — and it costs a few dozen lines rather than a full-file pass.
- **Bare `#rule` is a display, not a reload.** It re-states six lines that already sit in the prefix, which is a reminder and not a refresh; whether a whole-file reload survives at all is now an open question rather than a designed one.
- **Retired: `#rule R<n>` and the whole-file reload.** The first needed ids the file does not carry, and the second cost a full-price trip over the rulebook plus a wider window for a return the tier form delivers at a fraction of it.
- **Change detection is the reload's only real value.** Nothing is forgotten when rules appear to slip — they are out-weighted — so the reload's honest return is *the file changed on disk*, plus a fresh copy nearest the next decision. A reload that reports *unchanged* is a useful answer and an expensive one.
- **Reload if the difference matters, not if the file changed.** Refreshing the whole rulebook to repair one drift is the most expensive possible answer to a cheap problem; §3.3's narrow form exists for exactly that.
- **Never re-inject into the leading block.** Rebuilding the prefix invalidates every cached token after the edit point and re-processes the whole conversation, which is the one way to turn a cheap refresh into a costly one.
- **How `all` decides staleness — specified 2026-09-17.** The session compares the file's rule count and a hash over its rule lines with the copy it has been following: equal means the held copy stands and the answer is *current*, unequal names the sections that moved. The per-tier form keeps its own count and hash over the lines it loaded, so the two share one function and differ only in scope — which is what the page now says.
- **The name is now a decision, not a given — and it is settled.** Replacing `#rule` orphaned four reference sites — §7a's `#rule global` clause, its §7b mirror, and the C3/C5 targeting rules on the bake page — and all four were re-pointed when the redesign shipped, so the command was kept and D-A closed. Nothing in this subsection is open: what is written above is the record of what was weighed, not a backlog.

- **Observed 2026-09-17 — a mid-session edit reaches the file, not the session.** `AGENTS.md` gained the tier legend and twenty re-marks while this session was running, and the snapshot the session still holds shows the pre-edit text, so the change is invisible until a reload or a new session. That is the reload's case made concretely rather than argued, and it sets its bar: the reload earns its cost only when the difference between the held text and the file actually matters.

- **Alternatives to a refresh command, kept in `AGENTS.md`'s own grain, 2026-09-17.** The user's question was what else achieves drift detection without disturbing the file's design. Three answers: (a) the legend check on bare `#rule`, six lines and one comparison, which catches tier-level drift only; (b) **point-of-use reads** — the command that needs a rule reads that rule's block when it is invoked, `#push` reading `PROTECTED BRANCHES` and `#commit` reading the git-writes rule — which is the Lazy-Load Index's own pattern extended from docs to rules and needs no new mechanism; (c) a whole-file fingerprint that prints the hash without the dump, which still pays the read.
- **Why the middle one is the strongest.** Drift matters at the instant a rule binds, and a point-of-use read repairs it there instead of at a moment the user must remember to ask; the cost is one small read per governed action, and the objection — friction on paths that already work — is answered by the fact that a silently stale rule is the failure this whole thread exists to reduce.
- **Correction to the cost premise, worth carrying.** The cached tokens are the prefix the session already holds, not a fresh copy: a re-read is billed once like any append, so the legend check is cheap because it reads six lines, not because the content is cached.

### 3.4 Tracking — derived, piggybacked, or external

- **Decided 2026-09-17 — one sentence, and its home is the file `#bake` already writes.** The user's call: a very small centralised trace, summarised in one sentence and included in any task summary. The old blocker was that [`#bake`](../../docs/cmd_help_bake.md:13) overwrites `FEAT_HYD_[Feature].md` on every run, which kills anything kept beside the summary; the way through is to let the writer be the overwriter — the hydration gains one `**Directive trace:**` line and `#bake` carries it forward, so the trace has one home, one writer and no second file.
- **What the sentence says, and what it refuses to say.** Which of the five covered classes were met since the last bake and whether any stopped; classes that were not met are not mentioned, and a session that met none writes *no covered action*. Per-action history stays out — it is a state, not a changelog, which keeps the hydration's role as a ~200-word resume point intact.
- **The transcript is still the raw medium.** Tier 2's lines and tier 1's refusals are written by the act of checking, so the sentence is derived from them rather than hand-kept: nothing extra is filed and nothing needs syncing.
- **Piggyback the audit on commands that already read session state.** `#review` and `#bake` run when the user asks for state, so the sweep reports *covered classes met versus verdicts emitted* and names the gaps; the marginal cost is a few lines in an output already produced, not a tracking system of its own.
- **What self-enforcement can and cannot be.** Tier 1 is mechanical: the command refuses and no self-report is involved. Tier 2 and the trace are self-reported, so they evidence intent rather than compliance, and the free pre-emit check on output rules — *count the bullets, rewrite any over two sentences* — sits in that same class: free, unverifiable, worth having anyway.
- **Objection.** A sentence derived from self-reported lines reports checks, not obedience — a session with no covered classes and one that skipped every check write the same thing. Only the classes the sweep saw can separate them, which is why it reports those and not just the verdicts it found.

### 3.5 The three halves, specified

- **Maintain — live.** A rule is added by an edit to `AGENTS.md` alone, demoted when it belongs at its point of use, and deleted only with evidence that it never fired, never by argument. [`#doctor`](../../docs/cmd_help_doctor.md:1) gains one check: every rule wears a tier drawn from the legend.
- **Reinforce — live as behaviour, in three tiers.** Tier 1 is the refusals inside `#push`, `#commit`, `#merge`, `#bake` and `#archive`; tier 2 is the five-class verdict line, whose classes are stated in `AGENTS.md`'s legend zone; tier 3 is the `#review` sweep over the same classes. The one-sentence trace lands in `FEAT_HYD_` at each bake.
- **Refresh — live.** `#rule` prints the legend bare, reloads one tier on argument, and answers `all` with a staleness verdict against the whole file.
- **What "specified" does not buy.** Only tier 1 is mechanical; the line, the sweep and the trace rest on the agent honouring them, which is why the trace is written by `#bake` rather than by the agent's memory.

### 3.6 The rulebook trim — candidates, first list

- **In scope by D-E, and its first output is a list rather than a cut.**
- **`§ 8c Summary Payload Format`** — three bullets defining what each mode's `switch_mode` reason carries, while the table above them already names what each mode reports on completion: candidate to fold into one pointer line.
- **`§ 7a Memory Stack`** — one long bullet holding the feature-file layout that [`docs/xtrack-templates.md`](../../docs/xtrack-templates.md:53) owns: candidate to reduce to a pointer, the template being the file shape's home.
- **`§ 9 APK pipeline`** — one bullet whose second half points at `README.md` and `docs/SETUP.md` for the bake-versus-build contract: candidate to shorten to the two pointers.
- **What a cut must not do.** No rule loses its force word, its trigger, or a fact it alone owns; a candidate is trimmed only where the fact is owned elsewhere, which is the ONE HOME test rather than a length test.
- **Objection.** All three candidates are pointer-shaped text an agent reads once per request, so the saving is small and real; the trim's claim is that it raises the weight of what survives, not that it shortens the reply.

## 4. Mechanism, cost and verb

| Mechanism | When it runs | Cost | Verb |
|---|---|---|---|
| Rule sitting in the prefix | every request | already paid, cache hit | maintain · reinforce |
| Refusal inside the command | when the action runs | zero | reinforce |
| Predicate line at the action | the five covered classes | ~15 tokens | reinforce |
| `#review` sweep | user-invoked | one pass over context already held | reinforce |
| One-sentence trace | each `#bake` | one hydration line | reinforce |
| `#rule [tier]` reload | a caught drift | a few dozen lines, appended | refresh |
| `#rule all` staleness verdict | drift suspected | full-price trip, verdict only | refresh |
| Triggers and the tier check | authoring time | a few tokens per rule | maintain |
| Echoes, logs, per-turn printing | ongoing | the anti-pattern | — |

## 5. What this keeps from the earlier plan

- **The reload, minus the replacement** — read, fingerprint, delta, narrow form and the no-system-slot constraint, all as specified in the earlier plan's §3.2.
- **The registry, declined outright (D-B)** — what survives of it is the trigger shape and the tier check, and the ids go with it, along with the echoes, the verdict log and the cap's surrounding machinery (see §6).
- **Tier 1 and tier 2**, with tier 2's self-reporting named rather than hidden, and **tier 3 rehomed to `#review`**.
- **The cap, still** — above roughly twenty 🔴 rules the per-rule share collapses, and a cap is the one number that stops this design from recreating what it fixes.

## 6. What is dropped, and why

- **The per-action violation log**, replaced by the one-sentence trace: a log kept beside the hydration is erased by the bake that writes it, so the trace is written *by* that bake instead — one line, derived from the session's own verdicts, with no second file to keep in step.
- **The registry and its ids**, declined by D-B rather than deferred: the tiers already name a rule's force, and a second naming layer was the mass the reviewer objected to.
- **The transcript sweep inside `#doctor`**, because that command lints the xTrack stack's structure, not conversations.
- **Per-rule echoes across the pages**, because each echo is another copy to keep in step and a second copy is what ONE HOME PER FACT exists to prevent. Cross-references by id are allowed; restatements are not.
- **The `#walk` material**, which is a separate topic and stays in its own plan.

## 7. Open decisions

- **D-A — the refresh command's name. Settled 2026-09-17:** `#rule` was kept, so no additive stem was needed and the four reference sites were repaired instead. `#rules` could never have been that name, since it resolves back to `#rule`.
- **D-B — whether the registry ships. Settled 2026-09-17: it does not.** The user's call — *no, the categories are enough*: the tiers already state a rule's force, ids would add a second naming layer, and the reviewer's objection is upheld rather than answered. A check cites a rule by its headline and the trigger shape survives alone.
- **D-C — whether the trace ships, and where it lives. Settled 2026-09-17: it does, as one sentence.** Very small, centralised, summarised in one sentence and included in any task summary; it lives in `FEAT_HYD_[Feature].md`, written and carried forward by `#bake`, the writer that used to be the blocker.
- **D-D — whether tier 2 ships. Settled 2026-09-17: it does.** The five classes are named in `AGENTS.md`'s legend zone and the verdict line ships with the `#review` sweep as the check that keeps it honest; the self-reporting weakness stands as the price rather than as an objection.
- **D-E — whether the rulebook trim is in scope. Settled 2026-09-17: it is.** No rule text was cut here — this pass encoded decisions — so the trim runs as its own task against the 250-line file, and its first move is naming candidates rather than deleting by feel.
