---
name: WorkflowImprovement
status: active
created: 2026-06-03 00:00
modified: 2026-09-19 09:31
---

# Feature: WorkflowImprovement

**Description:**
Improving the xTrack workflow and command system (canonicalized in AGENTS.md) — trigger syntax, templates, lifecycle protocols, and bootstrap logic.

## Sections
### gitting-it

Git command shortcuts: #new / #commit / #push / #move / #cherry·#copy / #rename / #merge — canonical in AGENTS.md §7b + docs/cmd_help_git.md. Prompts are tiered: `#commit`, `#push`, `#merge` and `#cherry` ask before acting, while branch operations (`#new`, `#move`, `#move new`, `#rename`) do not. `#merge` runs pre-flight → classify → auto-select → confirm → push + PR, and none of them auto-executes — the no-git-write rule holds in every mode. Since 2026-09-17 the exit is complete: a command refused on a protected branch finishes on the new branch once the move is done, and bare `#move` picks from the local branch list.

#### Todos
- [ ] On-device / real-repo verification of all git shortcuts — deferred, low priority

#### Key Files
- `docs/cmd_help_git.md` — git shortcut detail
- `xTrack/Documentation/260610_FEAT_PLN_Documentation_git-move-command.md` — #move/#cherry design

## Todos
- [ ] **Decide the rule lifecycle's remaining questions** — the trim's cut: the plan is drafted and measured, and the cut is parked at 2.4% of the file. Closed 2026-09-17: the reload's name, the registry of ids (declined), the trace's home (one sentence in `FEAT_HYD_`, carried by `#bake`), and the gate line (ships, over five classes named in `AGENTS.md`). Detail in `xxArchive/INDEX.md` §7 and `260917_FEAT_PLN_WorkflowImprovement_agents-md-trim.md`.
- [ ] **Settle the review's residual findings** — the ⛔ two-force reading, the plan's drifted anchors and duplicate blocks, and the stale live records naming retired rules; the 2026-09-17 review pass took the anchors and the stale records, and the `#rule` walk closed the same day, so what remains here is the ⛔ two-force reading alone.
- [ ] **Rescope `ASK, DON'T GUESS`'s first clause from *a detail* to *a fact*** — the one blocking finding against the 💬 delegation rule: at ⛔ it outranks that rule and so defeats it for exactly the unstated choice the delegation exists for. Detail in `xxArchive/INDEX.md` §2.3.
- [ ] **Decide `#doctor` check (t)** — report-only lint that the adapters (`CLAUDE.md`, `.clinerules/`, `.claude/skills/xtrack/`) carry nothing but a pointer; parked 2026-09-13 after the adapter clean-up, when the content was removed but no guard was added. Re-lettered from (s) on 2026-09-17, which the rulebook tier check took.
- [ ] **Post-merge reconcile xTrack/ across branches** — deferred. Procedure documented in FEAT_DSC; execute when first cross-branch xTrack conflict occurs.
- `AGENTS.md` is the canonical rulebook and directly writable; edit without prompting (`.clinerules/`/`CLAUDE.md` are pointers).

## Key Files
- `AGENTS.md` — canonical rules incl. § 7a/7b xTrack; `.clinerules/`/`CLAUDE.md` are adapters
- `docs/cmd_help.md` — command reference summary + per-command sections
- `docs/cmd_help_git.md` — git workflow shortcuts
- `xTrack/GLOBAL_CONTEXT.md` — state only: routing map, feature summaries, focus history, global todos, doc index (rules live in `AGENTS.md`)
- `docs/xtrack-templates.md` — file templates (sub-truth pointed from `AGENTS.md`'s Lazy-Load Index)

## Docs
- `xTrack/WorkflowImprovement/260917_FEAT_PLN_WorkflowImprovement_agents-md-trim.md` — the rulebook trim: the measured 252-line baseline, three safe compressions, the rejected candidates and the evidence path that replaces trimming prose
- `xTrack/WorkflowImprovement/260916_FEAT_PLN_WorkflowImprovement_walk-context-and-directive-reload.md` — the `#walk` plan: the evaluating set, the documented `Closed` semantics now shipped on the page, axes A1/A2/A4 still in design, and the cache analysis in §4
- `docs/cmd_help.md` — derived printed view of §7b
- `docs/cmd_help_now.md` — #now / #list detail
- `docs/cmd_help_status.md` — #status / #status diff detail
- `docs/cmd_help_track.md` — #track detail
- `docs/cmd_help_focus.md` — #focus / #focus [section] detail
- `docs/cmd_help_todo.md` — #todo detail
- `docs/cmd_help_rule.md` — #rule detail
- `docs/cmd_help_doc.md` — #doc detail
- `docs/cmd_help_bake.md` — #bake detail
- `docs/cmd_help_help.md` — #help detail
- `docs/cmd_help_doctor.md` — #doctor detail
- `docs/cmd_help_archive.md` — #archive detail (xxArchive tier, digest floor, retirement candidates)
- `docs/cmd_help_list.md` — #list dashboard detail
- `docs/cmd_help_git.md` — git workflow shortcuts detail
- `docs/cmd_help_doc_audit.md` — #doc audit detail
- `docs/cmd_help_doc_update.md` — #doc update detail
- `docs/cmd_help_the-c-word.md` — #the-c-word help stub
- `docs/GIT_WORKFLOW.md` — Git workflow conventions
- `xTrack/WorkflowImprovement/260609_FEAT_PLN_WorkflowImprovement_feat-summary-layer.md` — FEAT_ summary layer token optimization discussion
- `xTrack/WorkflowImprovement/260609_FEAT_PLN_WorkflowImprovement_xtrack-reorg.md` — xTrack FEAT_* file reorganization implementation spec
- `xTrack/WorkflowImprovement/260620_FEAT_PLN_WorkflowImprovement_hard-rules-enforcement.md` — Hard rules enforcement discussion
- `xTrack/WorkflowImprovement/260617_FEAT_PLN_WorkflowImprovement_merge-conflict-resolution.md` — AI-assisted #merge conflict resolution spec
- `xTrack/WorkflowImprovement/260617_FEAT_PLN_WorkflowImprovement_cmd-dispatch-refactor.md` — Command lookup dispatch refactor plan
- `xTrack/WorkflowImprovement/260617_FEAT_PLN_WorkflowImprovement_newtask-delegation.md` — new_task delegation: Architect → Code without mode switch
- `xTrack/Documentation/260610_FEAT_PLN_Documentation_git-merge-command.md` — Git merge command design
- `xTrack/WorkflowImprovement/260529_FEAT_PLN_WorkflowImprovement_phase2-prompt-for-ai.md` — Phase 2 protobuf binary cache for coastline data; the subject belongs to Coastline and is flagged for re-homing
- `xTrack/WorkflowImprovement/260603_FEAT_PLN_WorkflowImprovement_subfeature-context.md` — subfeature context, the pre-sections implementation plan
- `xTrack/WorkflowImprovement/260608_FEAT_PLN_WorkflowImprovement_help-cmd-extension.md` — the `#help [cmd]` extension: filename scan and fuzzy resolve, now shipped
- `xTrack/WorkflowImprovement/260608_FEAT_PLN_WorkflowImprovement_xtrack-review.md` — xTrack system review: spec fragmentation, orphan docs and cache gaps
- `xTrack/WorkflowImprovement/260618_FEAT_PLN_WorkflowImprovement_implemented-section-format.md` — the `## Implemented` section format decision
- `xTrack/WorkflowImprovement/260904_FEAT_PLN_WorkflowImprovement_bake-sweep.md` — per-feature `#bake` sweep: fold done sections, checks C8 and C9
- `xTrack/WorkflowImprovement/260904_FEAT_PLN_WorkflowImprovement_implemented-migration.md` — migrating existing `## Implemented` sections to the pointer index
- `xTrack/WorkflowImprovement/260904_FEAT_PLN_WorkflowImprovement_implemented-pointer-index.md` — implemented-as-pointer-index
- `xTrack/WorkflowImprovement/260911_FEAT_PLN_WorkflowImprovement_global-context-rules-migration.md` — Global Context rules migrated into `AGENTS.md`
- `xTrack/WorkflowImprovement/260917_FEAT_PLN_WorkflowImprovement_session-review-fixes.md` — session-review fixes: the bake refresh, the git gate wording, one hydration shape
- `xTrack/WorkflowImprovement/260919_FEAT_PLN_WorkflowImprovement_archive-sweep.md` — `#archive sweep`: enumeration, recommendation and disposition, in design

## Walk
**Level 1 — Date:** 2026-09-17 · **Source:** the contentious points left by the `#rule` redesign — six questions of scope, granularity and cost · **Closed:** 2026-09-17 — exhausted, all six taken
- [x] 1 · Does a whole-file reload survive — closed: it survives as `#rule all`, which reads the file and reports a stale verdict rather than a dump; the read is itself the reload
- [x] 2 · Rule authoring lost its only home — closed: nothing was lost. The eleven hits that describe the capability never record a use, so a rule arrives as the user's edit to `AGENTS.md`, and the deletion removed a written capability rather than a practised one
- [x] 3 · Is a tier the right granularity — closed: a tier stays the unit, being a partition that already exists and needs no registry — the one D-B declined
- [x] 4 · The fingerprint's content — closed: *tier · rule count · short hash of the rule lines loaded*, unchanged, and `all` applies the same function to the whole file
- [x] 5 · The dump's shape and its cost — closed: a rule's headline with its trigger and no prose, costing the lines each tier already holds
- [x] 6 · A re-anchor that cannot enforce — closed: the command keeps its name and its limit. It re-anchors attention; enforcement stays with the refusals and the five-class verdict line

- Resolutions: all six closed by recommendation, none dropped — the whole-file read survived as a verdict, authoring was verified unused before its capability died, the tier stayed the granularity, the fingerprint was kept and extended to `all`, the dump was fixed to headline-plus-trigger, and the command's inability to enforce was accepted as its scope rather than counted as a defect.

**Level 1 — Date:** 2026-09-17 · **Source:** the `#implement` Ask hop's findings on the three edits it reviewed — 2 blocking, 7 should-fix, 6 note — ordered by severity · **Closed:** 2026-09-17 — the two blocking findings closed by design, the eleven below fixed or dismissed under the user's standing mandate
- [x] 1 · Blocking: the device condition gates on evidence — closed 2026-09-17: the collision now sits inside one rule, and its outcome is item 2's decision
- [x] 2 · Blocking: the device rule's force is authorisation — closed 2026-09-17: resolved by design rather than by tier, since the rule now reports readiness instead of asking, leaving no clash for ranking to settle
- [x] 3 · The shipped ⛔ clause excludes the cheap costs — closed 2026-09-17: replaced with *a breach ships a defect, or spends the user's time and tokens for nothing*, which names the waste rather than judging its recoverability, and lands with the device rule and the compounded-rule deletion in one write
- [x] 4 · ⛔ two forces — dismissed: the clause is breach-scoped, so it describes the consequence of breaking a ⛔ rule rather than a class of subjects, which leaves WRITE-ONCE on 🟢 where its force is preference
- [x] 5 · `those flags` — fixed: *re-run it that way*, the antecedent removed rather than clarified
- [x] 6 · The headline's assertion — fixed: *never leave a git command waiting on an interactive editor* states the duty instead of a fact, and *interactive* is back
- [x] 7 · `--no-edit` — fixed: it now reads as suppressing the editor, not as passing a message
- [x] 8 · Live records — fixed: the feature's stale walk todo was replaced, the closed levels kept as history
- [x] 9 · Plan rows — fixed: the two git rows merged into one, and the device row renamed
- [x] 10 · Double space — fixed
- [x] 11 · Stale live state — fixed: the todo naming the device logcat workflow is gone; the walk item lists stay as the record of what was reviewed
- [x] 12 · Anchors and census — fixed: the plan carries no line anchors, and one derived census statement replaces the counts it had accumulated
- [x] 13 · The git page — fixed: it now points at `AGENTS.md` for the rules rather than at the doc that delegates back to it
- [x] 14 · The `SCOPE LOCK` echo — fixed: its closing list now names findings and never a next action, which is what `MODE LOCK` requires of it

- Resolutions: nine findings fixed, one dismissed with its reason, and the two blocking ones closed by design rather than by ranking. Dropped: nothing. The level's lasting output is the ⛔ sentence that now names the waste, and the census line that replaced four contradictory counts.

**Level 1 — Date:** 2026-09-17 · **Source:** the ten hard-tier rules as reloaded from `AGENTS.md` — four 🛑 AUTHORISATION and six ⛔ BOUNDARY, each item carrying its tier · **Closed:** 2026-09-17 — exhausted past item 10, with item 9 carried open
- [x] 1 · ⛔ SCOPE LOCK — closed 2026-09-17: the suggestion clause reworded and shipped — the mention bar made checkable, contradictions always named, the closure list as the one exception to *Answer only what was asked*, and the teeth kept
- [x] 2 · ⛔ ASK, DON'T GUESS — closed 2026-09-17: `NEVER ASSUME` and `Challenge instead of assuming` consolidated into one rule at `AGENTS.md:53`, both originals deleted, and the MODE LOCK and QUESTIONS references carried over rather than orphaned
- [x] 3 · 🛑 MODE LOCK — closed 2026-09-17: reworded to *implementation waits for an order*, the git entailment clause scoped to the operation, and the permitted close added; the `ASK` clause-two overlap and the duplicated `QUESTIONS` sentence stay open
- [x] 4 · 🛑 GIT WRITES NEED A GO-AHEAD — closed 2026-09-17: reworded with a name it never had, the read-only list became a principle with examples, and the `MODE LOCK` boundary was stated
- [x] 5 · 🛑 PUSH, COMMIT AND DEPLOY ARE USER-OWNED — closed 2026-09-17: cut from six lines to three, the duplicate confirmation clause deleted, and the four verbs — ask, offer, list, remind — made the whole of the prohibition
- [x] 6 · 🛑 protected branches — closed 2026-09-17: written, carrying the shortened no-command-overrides claim, with the clause answering `GIT WRITES`' invoked-`#`-command grant added in the review pass that followed
- [x] 7 · ⛔ NO BINARY READS — closed 2026-09-17: reworded to the class statement with the extension list as examples, and the applied text repaired — it had lost its list marker, so the rule was rendering as a continuation of the paragraph above
- [x] 8 · ⛔ REPO ROOT — closed 2026-09-17: kept unchanged, being one line whose failure mode is a wasted call rather than a shipped defect; the tier question it raised is recorded instead
- [x] 9 · ⛔ DEVICE EVIDENCE ON REQUEST — closed 2026-09-17: shipped as a status rather than an ask, so the clash with the 🛑 git rule dissolved by design, and the restatement of that rule's spine is marked deliberate in the rule itself
- [x] 10 · ⛔ NO GIT EDITOR — closed 2026-09-17: kept as it stands, the third review to end in keep; its three wording precisions and the legend clause it argues for are recorded in the plan rather than written

- Resolutions: all ten items closed — five reworded and shipped during the walk, two kept unchanged, one class statement whose lost list marker was repaired, and one pair consolidated. Item 9 and item 10's wording landed afterwards through the `#implement` pipeline, whose Ask hop then returned **revise**: 2 blocking, 7 should-fix, 6 note. Dropped: nothing. Both blocking findings were closed afterwards — the device rule no longer asks, so its collision with the 🛑 git rule dissolved rather than needing a rank to settle it, and its force at ⛔ became accurate once nothing competed with it.

**Level 1 — Date:** 2026-09-17 · **Source:** the same ten rules, drawn before the legend landed and before the reload · **Closed:** 2026-09-17 — restarted at the user's instruction once the categorical update and the reload were in
- [x] 1 · SCOPE LOCK — kept unchanged: the ⛔ it already wore became the defined BOUNDARY tier, so no glyph work was needed
- [x] 2 · NEVER ASSUME — reviewed; three adjustments proposed and none folded yet — the doing-versus-asserting split with SCOPE LOCK, a citation predicate replacing the unverifiable prompt test, and the boundary that the rule never licenses ignoring what was given
- [ ] 3 · MODE LOCK — no Code switch without a go-ahead
- [ ] 4 · git writes need an explicit go-ahead
- [ ] 5 · push, commit and deploy are user-owned
- [ ] 6 · never write to `develop` or `main`
- [ ] 7 · NO BINARY READS
- [ ] 8 · REPO ROOT is `.`
- [ ] 9 · DEVICE LOGCAT WORKFLOW
- [ ] 10 · NO GIT EDITOR

- Resolutions: item 1 needed no change at all, its marker having become a real tier; item 2's review produced three proposed adjustments, all still open. Dropped: nothing — the level was restarted rather than exhausted, so items 3 to 10 passed into the new level above unchanged. The 💬 CONDUCT and 🧹 AUTHORSHIP rules remain a follow-on set for neither level, since neither tier voids an action or ships a defect unseen.

**Level 1 — Date:** 2026-09-13 · **Source:** pending set — open todos plus carry-overs from the 2026-09-13 review · **Closed:** 2026-09-13
- [x] 1 · Bake page states the walk challenge at the fold
- [x] 2 · Check (k)'s wording versus the terse-row rationale
- [x] 3 · Bare `#brief` mode query in row and page
- [ ] 4 · Decide `#doctor` check (s) — adapter content lint
- [x] 5 · Trim the `.clinerules` rationale to a pointer
- [ ] 6 · Deferred pair — git-shortcut verification, post-merge `xTrack/` reconcile

- Resolutions: the bake page now carries the walk's three-exit challenge · check (k) reads existence-never-wording · the bare `#brief` query closed as no-change, the Forms column being design notes · the clinerules adapter is a pointer with its rationale removed. Dropped: item 4 parked by `#skip`, its `#doctor` check (s) decision carried by the feature todo, and item 6 left unstepped, its two members already deferred elsewhere.

**Level 1 — Date:** 2026-09-19 · **Source:** the three open points of `260919_FEAT_PLN_WorkflowImprovement_archive-sweep.md` after its non-functional points were resolved in its §13 · **Closed:** 2026-09-19 — exhausted, all three taken
- [x] 1 · Q1 — closed 2026-09-19: volatile stands, a leave writes nothing, D9 and D10 unchanged; the accepted cost is one re-read per left row, narrowed by `sweep [state]`
- [x] 2 · Q3 — closed 2026-09-19: the nudge lives in `#doctor`, which already reports retirement candidates and gains the sweep's name; the bake keeps a cross-reference only, so no second copy of the warning exists
- [x] 3 · Q4 — closed 2026-09-19: `dropped` joins the enum as the fourth status word, stated once on the archive page with `docs/xtrack-templates.md` pointing at it

- Resolutions: all three taken by the user's decision — a leave writes nothing and D9 with D10 stand unchanged; the one nudge stays in `#doctor` and the bake keeps a cross-reference; the INDEX gains `dropped` for a plan closed without ever shipping, which also fixes how the page's abandoned-material clause reads. Dropped: nothing — the level exhausted with every item ticked.

## Implemented

- **session-review fixes — the bake refreshes focus, the map icon routes, the git gates speak scope, one hydration shape (2026-09-17, `feature/hide-law`)** — four findings raised by reviewing the hide-land-water-icon session against the rulebook, all shipped in one pass. `#bake` now carries the duty of rewriting the top Focus History entry to what shipped, since `#focus` pushes an intent and an implemented session otherwise leaves the stack describing a plan that no longer exists: the §7a bullet and the §7b row state it, `docs/cmd_help_bake.md` gains it as its own step with the rest renumbered, and the derived view follows. `#commit` and `#push` stopped saying "asks before" and now name what they confirm — the staged set and the message, the branch and the remote — so §7b and the git page agree with the Core Directive that the invocation is the go-ahead and only the scope is confirmed. `GLOBAL_CONTEXT.md`'s Ui_Settings routing row gained the words where nothing routed to the new capability, its residual `ui` ambiguity named as the cascade's rather than a routing gap. And the FEAT_HYD_ shape became a single statement: `docs/xtrack-templates.md` declares its block the whole of it, the bake page points there instead of restating it, and files written before it converge at their next bake rather than having a `Directive trace:` invented for a session that has ended → `xTrack/WorkflowImprovement/260917_FEAT_PLN_WorkflowImprovement_session-review-fixes.md`

- **reply shape and delegated choices (2026-09-17, `feature/some-wflw`)** — the rulebook now says how a report reads and who decides an invisible choice. The `📋 Report only what changed` bullet was reworded rather than added to: a report gives the problem and the fix in plain words, never the mechanism, with an ELIJP and its twenty-word ELI20. A new 💬 rule, `TECHNICAL CHOICE IS THE AGENT'S`, makes a choice that changes nothing the user can see the agent's to make, scoped to work already ordered, with a new dependency left to §4, no deletion or rename licensed and no commit, the write still waiting on `MODE LOCK`. The brief page folds the twenty-word form into the ELIJP part, keeping its three-part list closed. The Ask hop returned one blocking finding and fifteen record-hygiene items: the blocking one is that `ASK, DON'T GUESS`'s first clause reads *a detail*, which at ⛔ outranks the new 💬 rule and so defeats the delegation; it is unfixed and is the next step. Census moved 19 → 20 with 💬 at five.

- **the halves made live, plus their review's fixes (2026-09-17, `feature/some-wflw`)** — the `#implement` pipeline ran on the three pending page changes. `#doctor` gained `(s)`, the check that every rule bullet wears a tier glyph, with the letter range moving on its row and in the derived view; `#review` gained the sweep facet that reports the five covered classes run without a verdict line; and `docs/cmd_help_walk.md` gained the documented `Closed` semantics and the one rendering rule, with a single clause added to the `#walk` row. The Ask hop returned no blocking finding and five should-fix, all applied: the check's predicate now names its domain and says *tier glyph*, the derived `#walk` line carries the closed-level clause, `#brief` exempts the walk cursor and the review sweep, the sweep binds the challenge form too, and the doctor page's opening now covers the rulebook. Corrected with them: the `#doctor` letter reserved for the adapter lint moved to `(t)`, the plan's `a–r` parenthetical went so the range keeps one home, and the walk plan's withdrawn `Closed`-level check was marked as withdrawn rather than left standing

- **rule-command walk closed and the halves specified (2026-09-17, `feature/some-wflw`)** — the walk over the `#rule` redesign's six contentious points closed by recommendation, and what the six settled was written: the three halves specified in the lifecycle plan at §3.5, tier 2 shipped against the five classes now named in `AGENTS.md`'s legend zone — which closes the *ship call still open* clause in the entry below — `#rule all`'s staleness comparison defined on its page and in §3.3, the bare `#move` picker kept and specified in the §7b rows and the git page, the git document's exit completed so a refused command finishes after the move, and the trim's first candidate list drafted at §3.6

- **rule-lifecycle decisions encoded (2026-09-17, `feature/some-wflw`)** — the open decisions were answered and written into the design: the registry of ids is declined, the tiers naming a rule's force being enough; the trace ships as one `**Directive trace:**` sentence in `FEAT_HYD_[Feature].md`, written and carried forward by `#bake`, the writer that used to be the blocker; tier 2 is scoped to the five classes no command can refuse — dependency adds, machine-shaped data reads, unordered starts, device touches and unsourced claims — with its ship call still open; and the rulebook trim is in scope as its own task. Written with them: the git exit now completes the command it refused once the move is done, and the hydration shape gained its trace line in the bake page and the template doc

- **core-directive challenge and review pass (2026-09-17, `feature/some-wflw`)** — a challenge over the tier day held its mechanism intent — traceable rules, tier reload, cost where it matters — and named three it did not hold: smallness, strict absoluteness, one home. Ask then re-derived the census and returned no blocking finding and twelve should-fix, settled in a single write: `PROTECTED BRANCHES` gained the no-`#`-command-overrides clause precedence needed, the device rule's *one exception* became *the logcat case* so nothing competes with the 🛑 rule, its restatement of the git rule's spine is marked deliberate, the legend declares the Output Contract's bullets 💬 with subject glyphs, §7a permits one marked restatement, §4's dependency clause wears the 🛑 it never had, the `#rule` row states a tier name is matched exactly rather than by cascade, the derived `cmd_help.md` gained `all` and lost its stale line count, and the plan's duplicate counts and drifted anchors went. `D-A` closed, and `D-B` was settled the same day when the registry was declined; the rulebook trim stays open as `D-E`

- **rule-command-redesign (2026-09-17, `feature/some-wflw`)** — the three-tier rule manager was removed and `#rule` rebuilt around what already exists: bare prints the tier legend, `[fuzzy tier]` reloads that tier's rules from `AGENTS.md` with a fingerprint, explicit invocation only — which retires the proposed registry for the reload half, tiers being a partition that needs no ids. Repaired with it: the §7b `#rule` row, the `#todo` row's mirror, §7a's now-false `#rule global` clause, `docs/cmd_help_rule.md` rewritten, criteria C3 and C5 on the bake page and a section path on the focus page now naming `#todo` alone, and the derived `cmd_help.md` collapsed to one line. The pipeline's Ask hop returned revise with 0 blocking and 8 should-fix, chief among them that the page's own alias examples cannot resolve under §7b's cascade and that the fingerprint is named without being defined

- **core-directive-tiering (2026-09-17, `feature/some-wflw`)** — the rulebook gained a five-tier legend (🛑 AUTHORISATION · ⛔ BOUNDARY · 💬 CONDUCT · 🧹 AUTHORSHIP · 🟢 GUIDELINE) stating each tier's force and the precedence between them, and every rule was re-marked from the retired 🔴 scheme; the walk over the ten hard-tier rules closed with `SCOPE LOCK` reworded by the user, `NEVER ASSUME` and `Challenge` consolidated into `⛔ ASK, DON'T GUESS`, `MODE LOCK` reframed on acting with a permitted close, `GIT WRITES NEED A GO-AHEAD` named with a read-only principle, `PUSH, COMMIT AND DEPLOY` cut to three lines, the protected-branch rule renamed with its refusal mechanism, `NO BINARY READS` restated as a class whose marker defect was repaired, `REPO ROOT` kept unchanged, and the logcat workflow renamed to `DEVICE EVIDENCE ON REQUEST`; a cleanup pass trimmed two duplicated instructions, swept the retired glyphs to zero across `docs/` and `AGENTS.md`, cut `GIT_WORKFLOW.md` to the exit it alone owns and corrected §7a's always-loaded claim; the `#implement` pipeline's Ask hop then returned revise with two blocking findings — the device rule's collision moved rather than dissolved, and its force belonging on 🛑 — alongside seven should-fix items the review named; the review pass that followed closed both, the device rule having stopped asking, and put the rule count at nineteen by marking §4's dependency clause

- **BoatTrace → Tracks feature rename (2026-09-14, `feature/track-speed`, `322f4cc`)** — 56 paths moved by `git mv` out of `xTrack/BoatTrace/` into `xTrack/Tracks/` with the exact PascalCase token replaced 142 times across the live tree plus four closing edits found by verification; the routing row (now carrying the `tracks` keyword), the summary label and both focus-history paths were updated, and `docs/maro-code.md`, the `TrackViewModel` KDoc path and the `maro.properties` comment swept clean — `AGENTS.md` and `docs/cmd_help_git.md` gained the clause that a git `#`-command is its own explicit go-ahead

- **adapter clean-up — adapters carry pointers, never content (2026-09-13, `feature/wrKFl`)** — the 182-line template file moved to `docs/xtrack-templates.md` and `.claude/skills/xtrack/references/` was deleted so `SKILL.md` stands alone as a pointer; AGENTS.md's Lazy-Load Index gained the row, the feature's `## Key Files`, its hydration and the walk-stack plan were re-pointed, and the `.clinerules` rule adapter was stripped of its rationale
- **walk-stack — depth-1 child walks (2026-09-13, `feature/wrKFl`)** — a walk descends once into its active item on a bold `Level 2` line carrying its own date and a `Parent:` pointer, closing by exhaustion with resolutions and drops written into the parent; `#done` was retired across the §7b row, the walk page and the `cmd_help.md` line, moving the close-or-park challenge to the two gates; §7a's keep-criterion reads any level, C12 echoes it, C13 exempts walk items and the fixture lives in `docs/xtrack-templates.md`
- **command-flow review remediation (2026-09-13, `feature/wrKFl`)** — the plan's stale status flipped to shipped with an `## Outcome`; rule 3's undefined "final release of the action" clause deleted; the walk-reporting rule given a home in §7a's Turn 1 Protocol with its untriggerable close half dropped; `#bake`'s walk handling stated on its page; the plan lifecycle — extend versus new, and in-design meaning the pointer is absent from `## Implemented` — written into §7a; the bake page gained the walk's three-exit challenge and check (k) was reworded as existence, never wording

- **command-flow rules + four commands (2026-09-12, `feature/wrKFl`)** — the Output Contract gained a countable unit (`📏 Bullets are the unit`), the containment rule and the `🗣️ Recommendations argue against themselves` bullet, retiring "minimum viable communication"; rule 2 scopes ambiguity to MODE LOCK and QUESTIONS, rule 3 makes a gate name its action and re-ask when the proposal moved; four §7b rows shipped with pages — `#go` (Pipeline group), `#review` (cascade plus the pipeline guard in `cmd_help_implement.md`), `#walk` with `#next` / `#prev` / `#skip` / `#done`, the `## Walk` template and the §7a/C12 single statement, and `#brief` / `#full` with the mode definition and the three optional parts; `#doctor` unchanged at a–r
- **first real `#archive` pass (2026-09-12, `feature/wrKFl`)** — six candidates retired as seven files (core-directives-promotion, merge-strategy, trunk-to-leaf, planning, both agents-md-optimization plans, process-simplification) into `xTrack/WorkflowImprovement/xxArchive/`, each with an appended `## Outcome`, plus an `INDEX.md` of seven rows, five `## Docs` detachments and five `## Implemented` pointer drops; checks q–r verified clean; the second gate then retired `docs-integrity` and `archive-lifecycle` the same day, taking the index to nine rows

- **command delta on feature open (2026-09-12, `feature/wrKFl`)** — `#focus`, `#track` and Turn 1 (once a feature resolves) print the newest (max 3) WorkflowImprovement `## Implemented` entries that add or change a command; the rule is stated once as a §7a bullet, referenced from the two §7b rows, and mirrored in `docs/cmd_help_focus.md` / `docs/cmd_help_track.md` / `docs/cmd_help.md` — no new state, `#doctor` unchanged (a–r)
- **archive lifecycle + `#archive` command (2026-09-12, `feature/wrKFl`)** — plan lifecycle defined (Active → Digest → `xxArchive/`; shipped / superseded / promoted exits, abandonment routed to deletion); design migrated to its own plan per P7 so no copy remains; `AGENTS.md` gained the `xxArchive/` never-read rule (§7a) and the `#archive` §7b row (explicit invocation only) in one write; `#doctor` extended to checks a–r with index↔disk drift, the inverse-leak check and a report-only retirement nudge; new `docs/cmd_help_archive.md`; `templates.md` gained the `INDEX.md` schema; `#bake` reports retirement candidates
- **docs & rulebook integrity pass (2026-09-12, `feature/wrKFl`)** — registry single-sourced (AGENTS.md §7b normative; `cmd_help.md` stamped derived; `GIT_WORKFLOW.md` demoted to git detail, `## OwnedFiles` mechanism retired), `#checkout` de-registered and `#list` given its missing page, `#bake` explicit-only with `#commit` offering a stale-bake first (a `Last Bake` stamp added to the FEAT_HYD template), tiered git confirmation policy, `plans/` and `docs/oZer/` deleted (3 plan re-homes incl. the new **Tasker** feature, 5 research re-homes, 2 deletions), README pointer-ised with GDAL moved into SETUP, MARO_ARCHITECTURE plus seven docs audited, `#doctor` extended to checks a–p
- **GLOBAL_CONTEXT.md rules migration (2026-09-11)** — GLOBAL_CONTEXT.md reduced to state-only (`## Global Rules`, `## Global Instructions`, `## Always-Loaded Context` removed after bullet-by-bullet triage, not bulk deletion); rules consolidated into AGENTS.md — new §9 Environment & Tooling, QUESTIONS directive, MODE LOCK anti-`#implement` clause, §7a state-only invariant, `#rule global` retargeted to Core Directives; downstream sync (`cmd_help_rule`, `cmd_help_doctor` lint, `templates.md`, `cmd_help_implement` dead pointer) → `xTrack/WorkflowImprovement/260911_FEAT_PLN_WorkflowImprovement_global-context-rules-migration.md`
- **hard rules — Core Directives promotion (2026-06-20)** — 10 rules to prefix-cache zone, §5 git ops hardened, #doctor check (j)
- **AGENTS.md token optimization (2026-06-20)** — 207→149 lines, §7b collapsed to table
- **Workflow management cleanup pass (2026-06-28)** — 8 git commands canonical, MODE LOCK clarified, #help filename-scan, WorkflowAmbiguityFix absorbed
- **#merge hybrid strategy (2026-06-28)** — pre-flight + trivial/non-trivial classification + auto-select rebase/merge
- **AGENTS.md trunk-to-leaf (2026-06-28)** — §5 merged into Core Directives, §§1-4 condensed, Lazy-Load Index added
- **Process simplification (2026-09-04)** — WRITE-ONCE guideline, subfeatures→sections, Focus History stack
- **Rule enforcement fix (2026-09-05)** — Roo Code `.roo/rules/agents-source-of-truth.md` forces read+enforce of AGENTS.md at every session (project-committed, not plugin-config); deprecated `.clinerules` removed. AGENTS.md stays single authored source.
- **#doc list attachment column** — `#doc list` gained an "Attached to" column
- **xTrack system review** — spec fragmentation, orphan docs, cache gaps fixed (#doc sync/audit/diff)
- **AGENTSmdNormalization** — AGENTS.md canonical rulebook + adapters + #doctor lint
- **normalize commands** — command-set rationalization (Option C)
- **planning** — Zero-Piecemeal Writes discussion exception

