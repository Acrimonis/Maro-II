<!-- scope: feature -->
# Reply shape, and the choices the agent owns

## Outcome

**Shipped 2026-09-17 on `feature/some-wflw`** — the 💬 `TECHNICAL CHOICE IS THE AGENT'S` rule and the reworded `📋 Report only what changed` bullet, both living in `AGENTS.md`. Deviations: the delegation rule shipped at 💬 rather than in the 🛑 draft below, and the blocker it left — `ASK, DON'T GUESS`'s first clause reading *a detail* — is still open in the feature's `## Todos`.

Two standing instructions the user gave on 2026-09-17, captured before either is written: a reply states
the problem and the fix in plain words rather than the mechanism, short, with an ELIJP and an ELI20 side
by side; and any technical choice that changes no behaviour is the agent's to make. This is the proposal,
the collisions it must not create, and the alternative that was weighed and rejected.

## 1. The two asks

- **Reply shape.** No technical detail. Conceptual issue, conceptual fix. Short, no verbosity, and the two registers in the same reply.
- **Delegated choices.** Every technical choice that does not change behaviour is arbitrated by the agent, judged on code health and performance, without asking.

## 2. Proposed wordings

### 2.1 Output Contract — one new bullet

```markdown
- **🎯 Issue and fix, not mechanics.** An issue is what breaks or costs the user; a fix is what changes
  for them; a mechanism is named only when it changes behaviour, or when a rule demands the evidence.
```

### 2.2 Output Contract — the report bullet reworded

- *Now:* `For multi-step changes, add an ELIJP — one or two plain sentences on purpose, jargon stripped.`
- *Proposed:* the same sentence, ending `— and an ELI20, the same thing in twenty words.`

### 2.3 The delegation rule — **superseded, shipped in another form**

```markdown
- **💬 TECHNICAL CHOICE IS THE AGENT'S: a choice that changes nothing the user can see is not asked about.**
  WHEN a decision touches the implementation of work already ordered → decide it, state it in one line, and
  judge it on code health and performance — or raise it as the one question when neither can be satisfied
  without a change this rule does not license. A new dependency stays §4's call, no file is deleted or
  renamed and nothing is committed on this rule, the write still waiting on `MODE LOCK`.
  WHEN the choice would change what the user sees → it is the user's, and `ASK, DON'T GUESS` applies.
```

- **Shipped 2026-09-17 in the shape below, not the 🛑 draft above.** The review showed that a 🛑 rule whose subject is *the agent decides* inverts the legend, which defines that tier as *the user decides*, and would outrank the boundaries it must lose to; the shipped form is 💬 and scoped to work already ordered, with the dependency carve-out, the no-deletion clause and the `MODE LOCK` boundary written in.
- **The one blocker left against it.** `⛔ ASK, DON'T GUESS`'s first clause reads *a detail* and outranks 💬, so it defeats the rule for exactly the unstated choice the delegation exists for; the fix is to scope that clause to *a fact*. Unfixed as of this note.
- **Its census effect.** 19 rules became 20, with 💬 at five.

## 3. What the wording must not break

- **Evidence rules keep their specifics.** Review findings, gate verdicts, verification lists and fingerprints carry the file and the line; the new bullet says so in its own clause rather than leaning on precedence.
- **Behaviour-side ambiguity still stops.** `ASK, DON'T GUESS` keeps the request half, and only implementation ambiguity becomes the agent's.
- **The ELI20 is not a third register.** It compresses the ELIJP and never summarises the whole reply, or the two would restate each other.

## 4. The alternative weighed, and why the recommendation is a mix

- **Rejected — a second conduct bullet for brevity.** The contract already says short, two sentences, answer first, no verbosity, and the drift happened anyway; rewording the existing bullet costs two lines instead of five and cannot be dismissed as yet another rule.
- **Kept — the delegation rule.** It pays in tokens rather than in form, since the questions it removes are the waste the user actually felt.
- **Objection.** A licence to decide can hide a behaviour change under a "technical" label; the guard is the rule's own second clause, and a stated choice is visible in one line where an unasked question is not.

## 5. Cost, and the one open question

- **About four lines** on a rulebook whose trim is parked at 2.4% — small, and the delegation half is the part that could *remove* words rather than add them. As shipped: net +8 lines, the report bullet's reword accounting for two of them.
- **Settled:** the delegation rule ships as its own 💬 bullet, not as a clause inside `MODE LOCK` — the review preferred the bullet, and the clause would have inherited 🛑 and with it the precedence problem the bullet avoids.

## 6. How it ships

- One write to `AGENTS.md` — the new bullet, the reworded report bullet, the delegation rule — then the tier census re-run to confirm the rule count moved by exactly one, and `#doctor` `(s)` confirming every marker is drawn from the legend.

## 7. Capture note

Written under the Explain/Discuss Gate's exception: the prompt ended in *discuss*, so this plan is the
discussion's only capture and no rule text was changed.
