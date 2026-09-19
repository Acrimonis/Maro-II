<!-- scope: feature -->
# `#walk` context-sensitivity + directive reload command — design

**Feature:** WorkflowImprovement · **Branch:** `feature/some-wflw` · **Started:** 2026-09-16 · **Status:** in design — Part A alone since 2026-09-17; D1, D2 and D5 are superseded by the shipped `#rule` redesign, recorded in the lifecycle plan

**Split 2026-09-16 — this plan now covers the `#walk` topic alone.** The rule lifecycle it grew — the reload, the registry and the enforcement tiers — is restated against the user's stated requirement in [`260916_FEAT_PLN_WorkflowImprovement_core-directive-lifecycle.md`](xxArchive/260916_FEAT_PLN_WorkflowImprovement_core-directive-lifecycle.md:1), which supersedes Parts B, C and §5.1 below. §4 and §4.2 here remain the cache reference for both plans.

Two independent changes to the command set, packaged as one plan because both live in `AGENTS.md` §7b and both touch the same three surfaces: the §7b row, a `cmd_help_*` page, and the derived `docs/cmd_help.md`.

---

## 1. Current behaviour — what exists today

| Surface | Where | What it says today |
|---|---|---|
| `#walk` row | [`AGENTS.md`](../../AGENTS.md:1) §7b | Cursor over an enumerated set, one item expanded at a time; exhaustion closes it. State in the feature file's `## Walk`; an open walk blocks `#bake`'s fold and `#archive`'s retirement. |
| `#walk` page | [`docs/cmd_help_walk.md`](../../docs/cmd_help_walk.md:1) | Pending set = *"the active feature's open todos and unimplemented plan items, in ship order"*. Level 1 / Level 2 stack, three exits, gates and read path. |
| `#rule` row | [`AGENTS.md`](../../AGENTS.md:1) §7b | **Currently registered and slated for replacement** — bare = list, `[desc]` = append, `[target]:[desc]` = `global` / `parent` / feature / section. |
| `#rule` page | [`docs/cmd_help_rule.md`](../../docs/cmd_help_rule.md:1) | Detail of the three tiers; `global` writes to `AGENTS.md` Core Directives. Becomes the reload page under D1. |
| Derived list | [`docs/cmd_help.md`](../../docs/cmd_help.md:14) | Three `#rule` lines under `Track:`. This file is a *derived* view of §7b — regenerate, never hand-edit. |
| Walk fixture | [`docs/xtrack-templates.md`](../../docs/xtrack-templates.md:100) | The `## Walk` block shape, both levels, plus the exhaustion/fold note. |
| Cursor partner | [`docs/cmd_help_review.md`](../../docs/cmd_help_review.md:6) | Bare `#review` resolves to the walk's active item — review and stepping share one cursor. |
| Reload mandate | [`.clinerules/rules/agents-source-of-truth.md`](../../.clinerules/rules/agents-source-of-truth.md:3) | Adapter, pointer only: *"Read it in full at the start of every session… If AGENTS.md has changed since session start, reload it before acting."* The reload already has a written trigger but no command. |

---

## 2. Part A — `#walk` stops being literal

### 2.1 Diagnosis

The "pending set" is defined as a **mechanical concatenation**: open todos ∪ unimplemented plan items, ordered by "ship order". Nothing evaluates whether a member is still real, still wanted, still owned by this feature, or whether two members are the same job written twice. The 2026-09-16 Code hop that ran the feature open is the evidence: the walk section held a **Closed** level with two unticked items, and the cursor reported exactly that — a closed level resumed literally, items reported as "dropped rather than stepped", with the two survivors then re-appearing as carried todos. The section itself is *correct* per its spec; the spec is what is literal.

Three distinct faults hide under "too literal":

- **Set construction** — concatenation without dedup, ranking, or a liveness check. A todo that echoes a plan item appears twice; an item already shipped in the tree still appears because its checkbox was never ticked.
- **Open-state reading** — a level marked `Closed`, or one whose items are all ticked, is still read as a resumable cursor rather than as a signal to build a fresh pending set.
- **Rendering** — the reply is a flat bullet list with one expanded item and no stable visual anchor for *where the cursor is*; nothing states why an item is in the set, so the user cannot tell a live item from a stale one. This is the "improve the format of the answers" half of the request.

### 2.2 Design axes

- **A1 · Evaluate, do not concatenate.** Build the pending set by judgement, per item: dedupe todo/plan-item pairs that describe one job; drop items already satisfied in the tree; rank by dependency (a blocker outranks a polish item); state the owning surface. Each surviving item carries a short *why it is here* clause.
- **A2 · Liveness probe at open.** Check each candidate against the repo before it enters the set — does the file the item names still exist, does the feature's `## Implemented` already claim it — and mark the item `ready` · `blocked` · `stale`. Nothing enters the cursor on a stale marker without being named as such.
- **A3 · Documented `Closed` semantics, never a guard.** A level marked `Closed` is one closed by decision, and the corpus keeps resume points inside such levels — [`FEAT_DSC_Tracks.md`](../../xTrack/Tracks/FEAT_DSC_Tracks.md:289) holds a `Closed:` level whose unticked item is *the point to resume from*. So an open landing on a closed level reports it in one line, names any unticked item as a parked point with its resume condition, and asks before resuming that point; otherwise it builds a fresh set. It never resumes the level silently and never refuses the parked point — the first drops work, the second breaks the case the marker was invented for.
- **A4 · Context-aware active item.** The cursor opens on the item the conversation is actually about when the user's last exchange names its subject, falling back to ordinal order otherwise. Numbered markers stay stable so `Parent:` pointers and `#prev`/`#next` cannot break.
- **A5 · Rendered shape.** One expanded item, headed and visually separated, carrying `Why it is here`, `What closing it means`, `Open question` where a gate exists; the remainder as flat numbered markers with the cursor position marked; a footer line naming the parked level beneath if any. Bounded — the expanded block is the only prose, and it stays under the Output Contract's bullet cap.

### 2.3 Consequences to carry

- The set becomes **run-dependent**: two opens on the same feature can legitimately produce different sets. §7b and the page must say so, or the walk looks non-deterministic against its own spec.
- The rendering shape must be stated once and referenced — A5 touches how every `#walk` reply reads, and it interacts with `#brief`, which subtracts containment blocks and verification lists but must not silently strip the cursor position.
- `#review`'s bare form resolves to the active item, so A4 changes what bare `#review` targets. The two pages must be re-read against each other.
- A2 and A3 make the walk *do work at open*. That cost is bounded to a handful of file reads and must not become a full repo sweep.

---

## 3. Part B — the directive reload command

### 3.1 The collision — **D1 resolved: `#rule` is replaced**

`#rule` **already exists** and is shipped: bare lists rules, `[desc]` appends, `[target]:[desc]` routes to a scope. The request — `#rule`/`#rules`/`#rul`/`#rulz` as *"force a reload of the main directive from AGENTS.md"* — collided with it, and the alias set could not be made to work while both meanings shared the family, under the fuzzy-resolve cascade in §7b (exact → substring → edit-distance, stop on first unique match):

| Alias | Resolution | Verdict |
|---|---|---|
| `#rule` | Exact — takes the stem, so it **is** the reload | Was the scope-aware rule manager; replaced by decision |
| `#rules` | Substring `rule` ⊂ `rules` → unique → `#rule` | Works as a spelling of the reload, no registration needed |
| `#rul` | Substring of `rule` alone once no second stem exists → unique | Works — the collision only existed while two stems existed |
| `#rulz` | Edit-distance match on `rule` | Works |

**Decision (2026-09-16): replace the current `#rule` with the reload.** Bare `#rule` — and therefore `#rules`, `#rul`, `#rulz` — reloads the directives. No new stem is registered, so `rule` stays the only stem in its family and the cascade stays unambiguous; the options that kept the old semantics (a `reload` facet, or a separate `#reload` stem) are dropped.

**Consequence to settle — D5.** The replaced command carried three capabilities: listing the rules bound at a scope, appending a rule, and routing one to `global` / `parent` / feature / section — the last being the only path that writes a Core Directive. **Listing folds into the reload naturally**, since showing the directives *is* reading them and the fingerprint makes the read verifiable. **Authoring does not fold**, and must either be retained as a facet (`#rule add [text]` / `#rule add [target]:[text]` — one extra word, aliases unaffected) or be deleted outright as unused.

### 3.2 What a reload actually does — decision D2

- **Append, never re-inject.** The reload reads the file into the conversation as a tool result. It must **not** rewrite the always-loaded slot that AGENTS.md occupies — see §4 for why that is the only version that is actually expensive.
- **Proof the reload happened.** Print a fingerprint (line count + content hash) and the load timestamp, so a no-op is visibly a no-op. Without it the command is indistinguishable from compliance theatre.
- **Delta, not silence.** Compare the freshly loaded text against the version the session has been following: name the sections that changed. This is where the written mandate in [`.clinerules/rules/agents-source-of-truth.md`](../../.clinerules/rules/agents-source-of-truth.md:3) finally gets teeth — *"if AGENTS.md has changed since session start, reload it before acting"* becomes an executable, evidenced action.
- **Divergence callout.** If a Core Directive changed and the session already acted against the old wording, say so. A silent reload hides exactly the case the command exists to catch.
- **A salience tool, not a memory tool.** Nothing is erased when rules appear to slip — see §4.1 — so the reload's job is to re-weight the rulebook toward the next decision, which is why it must land at the tail of the context rather than somewhere the model already holds.
- **Scope of the reload (D2).** AGENTS.md alone, as requested — or AGENTS.md **plus** [`xTrack/GLOBAL_CONTEXT.md`](../../xTrack/GLOBAL_CONTEXT.md:1), the other always-loaded file, which is state and drifts faster than the rulebook. AGENTS.md-only is the literal request; the pair is the more useful unit.
- **Explicit invocation only**, like `#bake` and `#archive` — never fired automatically, never offered as a next step.

---

## 4. The cache question — answered

*I force a reload of AGENTS.md and the file has not changed — does it hit the cache?*

- **No. The appended copy is a miss, even though its text already exists higher up.** DeepSeek's context cache is keyed on the **token prefix** of the request, not on content identity: a repeated block at a new position extends the sequence past every cached prefix, so nothing cached can cover it.
- **What stays cheap:** everything before the appended block — the whole prior conversation, the original AGENTS.md copy included — matches a cached prefix and is billed at the cache-hit rate. The reload never re-charges the rulebook's first copy.
- **What does not:** the freshly appended rulebook is billed once at the full input price. DeepSeek levies no cache-write surcharge, so there is no further penalty — the copy simply lands in the prefix and is a hit from the next turn onwards.
- **The second-order cost is the real one.** The duplicate stays resident for the rest of the session, so every later turn carries it — cheap per turn, but it raises the floor of the window and shortens the stretch before compaction, and compaction is the one mechanism that genuinely deletes rules (see §4.1).
- **The genuinely expensive variant to avoid.** Rewriting the always-loaded slot rebuilds the **prefix itself**: every token after the edit point misses, so the whole conversation re-processes at full price rather than one block. A tool-read append can never do that; a system-slot re-injection always does. **Design constraint, not a preference.**
- **The honest ledger:** reloading an unchanged file costs one full-price pass over the rulebook and permanently widens the context, buying recency and change-detection in return. Cheap in money, not in window.

### 4.1 Why a cached rule still loses — mechanisms

- **Cache and attention are different stages.** Prefix caching saves the server from recomputing tokens; it does not change what the model conditions on, so a cached rule is present and processed exactly as an uncached one. The failure is at output selection — the rule is in the input and outvoted when the next token is chosen.
- **The nearest signal wins.** Behaviour tracks the most recent content: the user's latest message, the open task, and strongest of all the assistant's own previous reply, which reads as a worked example of how to write. A dense paragraph teaches density better than any rule forbidding it, which is how a session complies in form while drifting in spirit.
- **Countable rules invite the letter over the spirit.** A bound the model can measure — *two sentences per bullet* — is satisfied while the readability it exists to protect is not: the bullets stay at two sentences and get denser. Any rule stated as a number gets optimised as a number.
- **Stated rules need a trigger; gates do not.** A prohibition is re-derived only if something at decision time recalls it, so an absolute rule competes with task momentum at exactly the wrong moment. A binary check that can fail and force a rewrite — the `#doctor` lint, a walk gate, a pre-emit count — fires whether or not the model remembers.
- **Duplicate and stale statements dilute.** Rules restated on a `cmd_help_*` page can drift from §7b, and locality means the nearer copy wins in practice, which is why ONE HOME PER FACT and CENTRALISE AND TRIM exist — a contradiction is followed by whichever copy is closer, never by whichever is canonical.
- **A long rulebook lowers the per-rule share.** Rules compete for one fixed budget of attention: the more there are, the less each one weighs, so trimming AGENTS.md raises the weight of what survives more reliably than any command can.
- **Compaction is the one real deletion.** If a session is summarised or trimmed, rules held only in the transcript can be genuinely dropped — the only failure mode a reload actually repairs.
- **Countermeasures, least to most durable.** Trim the rulebook; put each absolute rule one line long at its point of use through the Lazy-Load Index; convert absolutes into binary pre-emit checks; and use the reload as a pre-work attention reset, never a remedy applied after drift — it buys recency and change-detection, at one full-price trip over the rulebook plus a wider window (§4).
- **Objection — mechanism reasoning, not instrumentation.** No agent can observe its own attention weights, so this is a hypothesis with a plausible story per failure rather than a measured cause. The falsifiable test is behavioural: if these are the real mechanisms, a shorter rulebook plus pre-emit checks should reduce the failures, and a reload alone should not.

**Strongest objection to building this at all:** if AGENTS.md has not changed, `#rule` is token cost with a placebo effect, and it risks training the belief that rules only bind after an invocation. The mitigation is the fingerprint and delta — a reload reporting *unchanged* is a useful answer, and one reporting *changed since session start* is the only case where the command earns its keep. If the real complaint is that rules slip mid-session, the durable fix is a smaller rulebook, not a command.

### 4.2 Is AGENTS.md prefix-keyed today — verified, and how to keep it there

- **Yes, today.** The full AGENTS.md text reaches the model inside the request's leading instruction block, ahead of the first conversational turn, which is exactly the position a prefix cache covers, and nothing in the workspace appends it later.
- **The always-loaded *pair* is a half-truth.** [`GLOBAL_CONTEXT.md`](../../xTrack/GLOBAL_CONTEXT.md:1) does not arrive at all — Turn 1 had to read it — so §7a's claim that both files sit in the prefix-cache zone describes intent, not the current wiring. Either the second file is wired in or the claim is corrected; leaving it as written is precisely the stale-copy drift §4.1 warns about.
- **The injection is not in version control.** [`.clinerules/rules/agents-source-of-truth.md`](../../.clinerules/rules/agents-source-of-truth.md:1) is a pointer, [`.vscode/settings.json`](../../.vscode/settings.json:1) and [`.claude/settings.local.json`](../../.claude/settings.local.json:1) carry no instructions, and the repo has no `.roo/` — so whatever injects AGENTS.md lives in the client's own custom-instructions or mode config, outside the repository and unverifiable from inside a session.
- **Rule one — the leading block must be byte-identical turn to turn.** No timestamps, no generated header, no rule appended into it: a changed leading block invalidates the cache from that byte onward and re-processes the entire conversation at full price.
- **Rule two — keep dynamic content at the tail.** Environment details, the clock and incidental context must stay appended after the conversation, as they do today; moving any of them into the leading block converts a per-turn hit into a per-turn miss.
- **Rule three — never edit AGENTS.md mid-session if the prefix matters.** One edited character in the leading block costs a full re-process, the same bill the reload's system-slot variant would incur — and the reason the reload reads rather than re-injects.
- **Verification is provider-side.** The cost meter in the environment block only totals spend and is suggestive at best; the authoritative signal is the provider's cache-hit token counter in the API response, which this client does not surface, so caching cannot be confirmed from inside the session.

---

## 5. Part C — keeping track of the absolute rules

Suggested shape, not yet decided. The problem it answers is §4.1's: absolutes written as prose in a long rulebook lose to whatever is nearest, so the tracker's job is to make each absolute **invocable, checkable and countable** — never to restate it more often.

- **Give every absolute rule a stable id.** `R1`, `R2`, … or a slug such as `NO-PUSH-MAIN`, `NO-BINARY-READS`, `NO-MODE-SWITCH`. An id is what makes the set countable, lintable and referenceable from anywhere in one token, instead of a paragraph repeated in three places.
- **Every rule reads `id — when <trigger>, never <action>`.** The trigger is the part that matters, because recognition happens at decision time: *never read `.bin`* is inert, while *when a path ends in `.bin`/`.tif`/`.xyz`/`.nc`, never open it* fires.
- **Keep the registry inside `AGENTS.md`, not in a new file.** §4.2 established that only AGENTS.md is actually wired into the leading block, so a new file would sit outside the prefix until someone re-wires the client — the one placement guaranteed not to work.
- **Reserve the 🔴 tier for irreversible or harmful actions.** Git writes, deletions, binary reads, mode switches, deploys — not style. Style belongs to the Output Contract and is better enforced as a check, because a countable rule sitting in the 🔴 tier spends weight it cannot defend.
- **One echo per rule, at its point of use, by id.** The registry is the source; a page that needs the rule nearby carries `see R4` plus at most one clause, since a second full restatement is a duplicate by definition.
- **Convert what can be checked into a gate that must report.** Before a git write, a protected-path write, a binary read or a mode switch, the agent emits the gate's verdict — the pattern `#push` and `#commit` already use by refusing on `develop`/`main`. A gate that can fail and force a rewrite is the only mechanism in §4.1 that does not depend on remembering.
- **Print the trigger list when work starts.** Turn 1 and `#focus` already print a command delta; the same slot can carry the compact `id · trigger` registry, a few lines, so the absolutes are re-anchored at the start of a session rather than only at its top.
- **Let `#rule` print the registry.** With the reload taking the name, `#rule` means *reload the file and show what binds you* — which retires D5's listing question instead of answering it separately, and gives the user a way to see the absolutes at any moment.
- **Log violations, because compliance is the only measurable part.** One line per caught breach — `id · what happened · the gate that would have caught it` — kept in the feature hydration. Rules that keep appearing get promoted to gates; rules that never appear get deleted, which shrinks the registry by evidence instead of by argument.
- **Cap the registry.** Above roughly twenty absolutes the per-rule share collapses again, so the cap forces merging or demotion into the Lazy-Load Index — the one number that stops this design from recreating the problem it fixes.

**Objection.** The registry adds a surface, an id vocabulary, echoes and a log — structure that can *increase* rule mass and worsen the weight-per-rule failure it targets. Its mitigation is the cap plus a log whose job is deletion as much as addition, and the honest limit is that the registry makes nothing obey: its value is referenceability and linting, while the gate and its emitted verdict carry the compliance.

### 5.1 Detecting drift from a 🔴 and enforcing it — three tiers by cost

The cost of a check is decided by **when** it runs, not by what it reads: a per-turn check pays on every turn, an action-shaped check pays only when a covered action occurs, and a check built into the action's own path pays nothing at all. Order the mechanisms on that line, cheapest first.

- **Tier 1 — the guard lives in the action path, and costs nothing.** A rule that sits next to a command is enforced by the command's own refusal, the way `#push`, `#commit` and `#merge` refuse on `develop`/`main` today. Extend that shape to protected paths, deletions and deploy: the model never has to remember, and the marginal token cost is zero.
- **Tier 2 — one line at the moment of action, roughly fifteen tokens.** A compact **trigger index** in the leading block maps action classes to ids — `git write → R1 R2`, `binary path → R5`, `mode switch → R3`, `new dependency → R7`. Before a covered action the agent emits `gate R1 ok` or `gate R1 fail — stopped` beside the tool call, so a rare action pays a line while most turns pay nothing.
- **Tier 3 — a batched audit, once per session and never per turn.** `#doctor`, or a facet of `#review`, sweeps the transcript's tool calls against the trigger index and reports every class that ran without its verdict. It costs one pass over text already in context, on demand.
- **Write the check as a predicate over the action, not a prohibition to recall.** *Is the target `develop`/`main`?*, *does the path end in `.bin`/`.tif`/`.xyz`/`.nc`?*, *is this a mode switch?* — deterministic tests a few tokens wide. Recalling a prohibition is both more expensive and less reliable than testing the action's own arguments.
- **Prefer impossible or visible over memorable.** A rule turned into a refusal costs nothing per turn, one turned into an emitted verdict costs a line, and only a rule that can be neither stays a pure prohibition — so that residue should be kept as short as possible.
- **Reload narrowly when a drift is caught.** Re-injecting the whole rulebook costs a full-price pass over it plus a permanently wider window (§4), so `#rule R4` re-anchoring one rule at roughly twenty tokens is the cheap repair, and the full `#rule` stays the session-level reset.
- **Never pay per turn for any of it.** Printing the registry, re-reading AGENTS.md or restating the trigger index every exchange is where the cost explodes, and it buys only the salience the tier-2 line already delivers at the right moment.
- **Objection.** Tiers 1 and 3 are real enforcement; tier 2 is self-reported, and a model that has already drifted can emit `gate R1 ok` without checking — making the line evidence of intent rather than proof of compliance. Its defence is that it is cheap enough to be honour-kept and auditable afterwards, and its failure mode is exactly what tier 3 exists to catch.

---

## 6. Target files

| File | Change |
|---|---|
| [`AGENTS.md`](../../AGENTS.md:1) §7b | `#walk` row rewritten for the evaluating set + rendering shape. The `#rule` row is out of scope here — the redesign shipped it as bare / tier / `all` |
| [`AGENTS.md`](../../AGENTS.md:1) §7a | Walk keep-criterion and open-level gate wording stay; the closed-level guard (A3) is stated once here or on the page, never both |
| [`docs/cmd_help_walk.md`](../../docs/cmd_help_walk.md:1) | Set construction (A1/A2), closed-level guard (A3), context-aware active item (A4), rendered shape (A5) |
| [`docs/cmd_help_rule.md`](../../docs/cmd_help_rule.md:1) | **Rewritten as the reload page** — what it loads, the fingerprint, the delta, the no-system-slot constraint, explicit-invocation-only, and D5's outcome for the old tiers |
| [`docs/cmd_help.md`](../../docs/cmd_help.md:14) | Regenerated derived view — `#walk` line and the `#rule` block; never hand-edited |
| [`docs/xtrack-templates.md`](../../docs/xtrack-templates.md:100) | Only if A3/A5 change the `## Walk` block shape or add a status marker |
| [`docs/cmd_help_review.md`](../../docs/cmd_help_review.md:6) | Re-read against A4 — bare `#review` resolves to the active item |
| [`docs/cmd_help_brief.md`](../../docs/cmd_help_brief.md:1) | Guard that brief mode cannot strip the cursor position or the fingerprint |
| [`docs/cmd_help_doctor.md`](../../docs/cmd_help_doctor.md:1) | The proposed "`Closed` walk level carrying unticked items" check is **withdrawn 2026-09-17** — it would contradict A3 and flag the corpus's own resume point as a fault. The reload/derived-view check is unmet and needs a new home |
| [`AGENTS.md`](../../AGENTS.md:1) top section | Part C's registry — declined 2026-09-17, no rule carrying an id; the legend zone does carry the five action classes tier 2 covers |
| [`xTrack/WorkflowImprovement/FEAT_HYD_WorkflowImprovement.md`](../../xTrack/WorkflowImprovement/FEAT_HYD_WorkflowImprovement.md:1) | Part C's violation log — superseded by the one-sentence `**Directive trace:**` line `#bake` writes and carries forward |

---

## 7. How this ships

Nothing here is code. Every command and rule is markdown the assistant is handed at the start of each request or reads on demand, so implementing this means editing text files in a fixed order and then behaving differently at runtime — no build, no app change, nothing in `app/`.

- **Step 0 — decide.** D2, D5, D6 and D7 fix the registry's contents and how far the tiers go; nothing is written before they are answered, because §7b is single-sourced and a half-changed registry leaves echoes pointing at ids that do not exist.
- **Step 1 — the registry, in `AGENTS.md`.** One new top section holding each rule as `id — when <trigger>, never <action>`, the action classes it covers, and the cap. This is the file the client already injects into the leading block, so it is the only step that changes what the assistant is handed up front rather than what it reads.
- **Step 2 — the echoes.** Every place that restates a rule today reduces to `see R<n>` plus at most one clause, so the rulebook loses duplicates instead of gaining a second copy of itself.
- **Step 3 — `#rule`, on the reload page.** [`docs/cmd_help_rule.md`](../../docs/cmd_help_rule.md:1) is rewritten: what it loads, the fingerprint it prints, the delta against what the session has been following, the registry display, and the narrow `#rule R<n>` repair. The §7b row is replaced in the same pass.
- **Step 4 — `#walk`, on its page.** [`docs/cmd_help_walk.md`](../../docs/cmd_help_walk.md:1) gains set construction, the liveness probe, the closed-level guard and the rendered shape; the §7b row and the §7a clause follow in the same write.
- **Step 5 — the derived view.** [`docs/cmd_help.md`](../../docs/cmd_help.md:14) is regenerated from §7b, never hand-edited, so the two cannot drift apart.
- **Step 6 — the audits.** [`docs/cmd_help_doctor.md`](../../docs/cmd_help_doctor.md:1) gains its checks: the reload registered and the derived view in step, a `Closed` walk level carrying unticked items, every registry rule carrying an id and a trigger, and the once-per-session sweep reporting covered action classes that ran without a gate verdict.
- **Step 7 — the runtime behaviour, which is not a file edit.** From then on the assistant prints the trigger list when a session opens, one gate verdict line before a covered action, and the fingerprint plus delta on `#rule`. These are the parts with teeth, and they hold only because each is written as a check with a visible output rather than as a prohibition.
- **Step 8 — register the plan** in the feature's `## Docs` and log the work items as feature todos, so the plan stops being invisible to `#doc list`.
- **What cannot be implemented from here.** The injection of AGENTS.md into the leading block is configured in the client, not the repository (§4.2), so wiring `GLOBAL_CONTEXT.md` into that same slot — or correcting §7a's claim that it already is — is either a client-side action or a documentation fix, since no file in this repo can make it prefix-keyed.
- **Honest limit.** Only tier 1 is mechanical, because a refusal lives inside the command's own definition. Tiers 2 and 3 depend on the assistant honouring the gate and on the audit actually being run, so this buys a far higher chance of compliance, not a guarantee.

---

## 8. Open decisions

- **D1 — reload command shape: RESOLVED** — the current `#rule` is replaced by the reload; bare `#rule` and its spellings `#rules` · `#rul` · `#rulz` all reload the directives.
- **D2 — reload scope:** AGENTS.md alone, or AGENTS.md + `GLOBAL_CONTEXT.md` as the always-loaded pair.
- **D5 — the replaced command's authoring half: RESOLVED — deleted.** A search for `#rule global`, `#rule add` and the section target returned eleven hits that describe the capability and none that record a use, so the append and routing half went with the command rather than surviving as a facet; listing is what `#rule` now is.
- **D6 — whether the absolute-rule registry ships with this pass or follows it.** It is the stronger lever of the two, but it restructures the top of AGENTS.md and every echo site, which may justify its own plan.
- **D7 — how far up the cost ladder to ship in tier terms:** tier 1 only, tiers 1–2, or all three, given that tier 2 is self-reported and tier 3 is what keeps it honest.
- **D3 — strictness of A1/A2:** whether the walk may refuse an item outright or may only annotate it `stale` and let the user step it anyway.
- **D4 — whether the rendered shape (A5) is binding for every step** or only for the open of a level.
- **D8 — the review's fork:** drop Parts B and C, keep Part B additively under a non-colliding name, fix the four verified defects and re-review, or re-review before deciding at all.

---

## 9. Review — the Ask hop, 2026-09-16

Independent review by Ask mode, at the user's request, against the plan as it stood after §7. **Verdict: revise — do not implement as written.**

- **Shape.** 11 blocking, 18 should-fix and 9 note findings, each carrying a file:line pointer and a named falsifier or settling check; report-only, no file edited and no fix applied.
- **Confirmed sound.** §1's quotations of the shipped text match the pages, §7's client-side limit is correct, and the self-objection bullets are honest rather than decorative.
- **Part A's diagnosis is misattributed.** The bare `#walk` behaviour behind the 2026-09-16 Code hop is the *documented resume path* — with a stack already open, the cursor resumes its top level — so the fault named in §2.1 is not the fault that occurred.
- **A3 contradicts the corpus (verified 2026-09-16).** [`FEAT_DSC_Tracks.md`](../../xTrack/Tracks/FEAT_DSC_Tracks.md:289) holds a level marked `**Closed:** 2026-09-15` whose item 2 is unticked and *parked as the point to resume from*, so a closed-level guard would refuse a resume point the stack deliberately keeps. The marker reads as closed-by-decision, not never-resume, and neither [`docs/cmd_help_walk.md`](../../docs/cmd_help_walk.md:1) nor [`docs/xtrack-templates.md`](../../docs/xtrack-templates.md:100) defines it as a prohibition.
- **Part B orphans four reference sites (verified 2026-09-16).** Replacing `#rule` leaves [`AGENTS.md`](../../AGENTS.md:130)'s `#rule` `global` clause — the only path that writes a Core Directive — and its [`§7b`](../../AGENTS.md:147) mirror dangling, plus the C3/C5 targeting rules at [`docs/cmd_help_bake.md`](../../docs/cmd_help_bake.md:24) that name `#rule` as a section-forming command.
- **Part C's log has no valid home (verified 2026-09-16).** [`docs/cmd_help_bake.md`](../../docs/cmd_help_bake.md:13) creates or overwrites `FEAT_HYD_[Feature].md` on every bake, so the violation log cannot live there, and the plan names no other home.
- **Tier 3 is outside `#doctor`'s remit (verified 2026-09-16).** [`docs/cmd_help_doctor.md`](../../docs/cmd_help_doctor.md:4) lints the xTrack stack's structure — routing rows, sections, registry presence across files, archive drift. A transcript sweep belongs to `#review`, whose cascade already resolves a walk item, a plan in design or the live proposal.
- **The reviewer's alternative.** Drop Parts B and C, keep a documented `Closed` semantics plus one rendering rule from Part A, and take the rulebook trim §4.1 already names as the cheaper structural answer. Whether to drop outright is the user's call, and nothing here presumes it.
- **Unaffected by the verdict.** §4's cache answer, §4.2's prefix findings and §7's sequencing remain valid whichever way the fork goes, and the §7a always-loaded half-truth is independent of all three parts.
