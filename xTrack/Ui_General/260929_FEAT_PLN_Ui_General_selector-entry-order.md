# Plan — the selector's entry order

**Feature:** Ui_General · **Date:** 2026-09-29 · **Status:** in design — the discussion is captured here and
nothing has been written to code. Opened out of the dropdown work of the same day (§7–§9 of
`260929_FEAT_PLN_Ui_General_dropdown-wheel.md`), on the user's word: **the fixed entries stay at the top, the
rest read alphabetically on their title, case ignored.**

## 1. The list in question

- **Only one selector mixes the two kinds today** — the route's two ends, built by
  [`routeEndOptions()`](../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:4351): a *fixed* half
  (`CurrentPosition` · `MarkerPosition` for the start, `MarkerPosition` for the destination) wearing the
  `-- %s --` dress of `route_end_fixed_fmt`, and a *flagged* half — the markers carrying `routeOrigin` or
  `routeDestination`, labelled with the marker's own name.
- The settings route-algorithm selector and the language selector are **all fixed**, so the rule changes
  nothing there; the control itself sorts nothing, drawing the labels in the order it is handed.
- **The fixed half's order is load-bearing and stays as declared**: `RouteSummaryData`'s own contract calls the
  start selector's first entry the fallback, and [`RouteEndSelection.firstEntry()`](../app/src/main/java/ykws/android/maro/data/route/RouteEndSelection.kt:57)
  is what answers it — `CurrentPosition` for the start, `MarkerPosition` for the destination. The rule
  therefore fixes *where* the fixed half sits, never reorders it.
- **The input is the screen's displayed marker list, not the store's.** `routeMarkers` collects
  `MarkersViewModel.markers`, which the view model keeps as the list's own **filtered and sorted** view
  (`_allMarkers → filter → sort → _markers`), so the flagged half arrives in whatever the user's list sort is
  — newest first under the default `CREATED`, alphabetical already when they have chosen Title — and only the
  markers passing the list's own filter are offered at all. Both facts are the population this plan changes.

## 2. Candidates

| # | Order for the flagged half | vs the app | Verdict |
|---|---------------------------|------------|---------|
| A | `String.CASE_INSENSITIVE_ORDER` at the call site | deterministic, but a **second** alphabetical rule in the app | rejected |
| B | **the lists' own title rule, lifted into one shared comparator** | one home for "alphabetical on title" | **chosen** |
| C | a locale `Collator` | accents ordered as a French reader expects, but device-dependent and untestable | rejected |

- **Why A is out**: the app already has a title order — [`ListSortOrder`](../app/src/main/java/ykws/android/maro/data/model/ListSortOrder.kt:49)'s
  `ListSortField.TITLE` sorts on `title.dropWhile { !it.isLetterOrDigit() }.lowercase()`. A second rule would
  put the same marker in two different places in two surfaces, and a leading emoji or dash (which the drop
  handles) would sort by codepoint here and by the first real letter there.
- **Why C is out**: it is the *same single edit* — the one comparator both readers take — so adopting it would
  silently re-order **every list in the app**, not just the selector, and it would make the order
  device-dependent, placing two markers differently under two locales with no unit test able to pin it. That is
  a decision about the app's alphabetical order as a whole, which §4 puts to the user's word instead of
  deciding it inside this ask.

## 3. The chosen design (B)

1. **One comparator, one home.** The title rule becomes a pure, named **ascending** comparator beside the sort
   fields — the lambda at `ListSortOrder.kt:49` moved, not copied — and both readers take it: the lists'
   `TITLE` field, as `compareBy(titleOrder) { it.title }`, and the ends' flagged half. The point of the choice
   is **agreement**, not a claim that both surfaces are alphabetical: the marker list carries this rule
   whenever the user's sort is Title — `CREATED` being the default state — and the selector then places the
   same names in the same order that list does, where a second rule would disagree with it. It is the ascending
   base the lists reverse for their direction, and the selector takes it as it stands.
2. **The builder sorts only the flagged half** — `routeEndOptions` keeps `fixed + flagged` and passes the
   flagged list through the shared comparator, so the fixed half stays first, in its declared order, and the
   fallback is untouched.
3. **Stability, stated.** Two names differing only in case compare equal, so the sort must be stable and the
   list's own order is the tie-break — Kotlin's `sortedWith` is stable, and the plan says so rather than
   leaving it to luck. The comparator never touches the fixed half, which is what lets the `-- %s --` dress
   stand beside a rule whose first act is to drop leading non-letters.
4. **The doctrine.** §2.12 of `docs/ui-component-guidelines.md` gains the mixed-list clause — fixed entries
   first in their declared order, everything else by the shared title order — beside the bullet that already
   owns the two kinds of entry; §2.10 and the wheel are untouched by this plan.
5. **Tests, and the extraction they need to exist at all.** `routeEndOptions` is a private `@Composable`
   resolving its labels through `stringResource`, so the ends' order has no home a plain unit test can reach as
   it stands: **the ordering is extracted as a pure helper beside `MarkerRouteFlags`** — the projection that
   exists for exactly this reason, keeping `eligibleMarkerIds` pure. Pinned then: the comparator's own cases
   (a leading `--`, an emoji, a mixed-case pair, and that it is the *ascending* form), the ends' order — the
   fixed half first in its declared order, then the shared order — and the Compose-only remainder (label
   resolution, the dress) named as read-verified rather than tested.
6. **Out of scope, deliberately**: the wheel and the box, the eligibility rule (`eligibleMarkerIds`), the
   marker list's own sort, the `-- %s --` dress, and the two all-fixed selectors.

## 4. Open questions

- **Where the rule lives.** The contract is §2.12's (the control's), while the entries are the Route feature's
  own — R44–R49 in its master book, the row that already governs the ends. *Recommended*: §2.12 carries the
  rule, and the Route master book's end row gains a one-line pointer, so the ends' reader is not sent looking.
- **Which order among the fixed entries.** Kept as declared — the start's `Current position` then
  `Marker position`, the destination's single entry — because the first entry *is* the fallback. Alphabetical
  order is for the flagged half alone.
- **Accents.** Following the lists' rule (case folded by `lowercase()`, accents left in codepoint order) means
  a name like `Étang` sorts after `Zeta` — in the selector exactly as in the marker list whenever that list's
  sort is Title. That is the price of agreement, and the alternative is not a selector-shaped patch: swapping
  the one comparator for a locale `Collator` re-orders **every list in the app**, per device locale, with no
  test able to pin it. It is the user's call, and this plan does not make it on its own.

## 5. The review (2026-09-29)

Reviewing this plan because it is the plan in design — the walk is closed, the last `#implement` run's Target
Files are the wheel's own, and this draft is the live proposal. Read against the code it names, not against its
own description.

Verdict: **revise** — the fault, the list and the chosen shape hold; two claims failed as written and every
finding is folded above.

- **High — the parity claim was overstated.** §3.1 read as if the marker list were alphabetical; it carries
  the title rule **only when the user's sort is Title**, `CREATED` being the default. **Folded**: §3.1 now
  claims agreement rather than a shared alphabetical order, and names the switch that brings it about.
- **High — the accents alternative was understated.** §2 and §4 spoke of a `Collator` as though it fixed the
  two surfaces "at once". It is the *same* comparator the lists take, so adopting it re-orders every list in
  the app, per locale, and reaches far past this ask. **Folded** in both places, the recommendation staying
  the user's.
- **Medium — the direction was left unstated.** `applySort` reverses its base when `descending`; the selector
  has no direction and must take the **ascending** form. **Folded** into §3.1 rather than left to the
  implementer to rediscover.
- **Medium — the tests could not have been written as asked.** `routeEndOptions` is a private composable, so
  §3.5 now names the pure extraction (beside `MarkerRouteFlags`, the projection that already exists for this
  purpose) without which the ends' order has no home to be pinned in.
- **Low — the dress and the comparator never meet, and the plan did not say so**: the fixed half is never
  sorted, which is what lets `-- %s --` stand beside a rule that drops leading non-letters. **Folded** into
  §3.3.
- **Low — "the screen's order" was vague.** §1 now says the marker store's order, as this screen collects it.
- **Verified rather than assumed**, since the design leans on each: `UserMarker.title` and `Track.title` are
  both the item's own name, so one comparator over the title serves the lists and the selector; the other two
  selectors really are all fixed, so the rule changes nothing there; the lists' `TITLE` base is the ascending
  form and direction is applied by `reversed()`; and the fixed half's first-entry-is-the-fallback contract
  holds in `RouteEndSelection.firstEntry`, so "fixed entries stay on top" preserves it rather than merely
  tidying.

## 6. The ignored-prefix list — the sort reads configuration (2026-09-29)

**The requirement, extended by the user's word:** the alphabetical rule ignores a configured set of leading
words — `La`, `Le`, `Les` — so `Le Port` files under P and `Les Sables` under S while `Léman` keeps its L. The
set is a value, not code.

### 6.1 Where the value lives

- `app/src/main/assets/maro.properties` is the app's source of truth for values, and it already carries a
  **comma-separated list read case-insensitively** — `regulatedZones.filteredTypes=ENVIRONMENTAL,FISHING_PROHIBITED,OTHER`,
  its own comment saying "comma-separated, case-insensitive" — so the new key takes that shape rather than
  inventing one.
- **The key, settled by the user's word (2026-09-29)**: `title.sort.ignoredPrefixes=La,Le,Les`. The literal
  `title list sort ignored` is dropped, and the taxonomy stays coherent end to end — the file's dotted
  lowercase form, with the accessor, the backing field and the parse all reading `titleSort*`, the key
  standing under the same kind of group comment the file's other families carry, and the read trimming a
  `La, Le, Les` value as readily as the shipped compact one.
- The key's **comment goes in the file**, as each key's does, and the **code's default mirrors the shipped
  value** — the habit `app/src/test/java/ykws/android/maro/config/` enforces across its property suites, the
  file being the home and the Kotlin default following it.

### 6.2 The rule, as the user pinned it (2026-09-29)

- **A character-for-character prefix, and a whitespace after it.** The title is filtered only when it **starts
  with** one of the configured values *and* the character right after that value is **whitespace**; when both
  hold, the sort key begins at the **first character of the next word**, the whitespace between them consumed.
- **No folding of any kind inside that test.** The comparison is on the title's own characters, so `Léman` is
  not a `Le` title — its second character is `é` — and `Leman` is not one either, the character after `Le`
  being `m` rather than whitespace. Two different refusals, both wanted, and neither needs a word-boundary
  argument.
- **Case is the one axis the test folds**, because the sort already ignores case: the configured words are
  lowercased once and compared against the title's own prefix lowercased, so `LE PORT` is filtered and `LA`,
  `la` and `La` are one value. Accents are not folded, and no collation enters.
- **A bare `Le` is never filtered** — there is no next word — which is why the pinned condition needs no
  separate "never empty the key" clause: the whitespace requirement is that clause.
- **The dress is not stripped first, deliberately.** The test reads the title as it stands, so `«Le» Port` is
  not filtered at all — its first character is not `L` — and today's own leading-non-letter drop stays where
  it is, transforming whichever string the test left behind.
- **One prefix, not a stack**: the remainder begins at the next word and is not tested again, so
  `Les Les Sables` loses one `Les`.
- **The pipeline, in order**: test the title (starts with a value, whitespace next) → the candidate is what
  follows that value and its run of whitespace, or the title itself when the test fails → then today's own
  transform on the candidate, the leading non-letters dropped and the result lowercased, which is the key.

### 6.3 Where the code goes

1. **`maro.properties`** — the key with its comment, beside the other sort-shaped values.
2. **`AppConfig`** — a **pure parse** in the file's own shape, at the visibility its precedent uses —
   `parseHeatmapFamilies` is reachable from the `config` test package, where the property suites live — with
   `split`-and-`trim`-then-`filter` the shape that precedent and `parseCandidatePasses` follow; plus the
   backing field and accessor with `private set`, defaulted to the shipped `La,Le,Les`.
3. **`ListSortOrder.kt`** — the key becomes `titleSortKey(title, ignoredPrefixes)`: **pure, the set as a
   parameter**, so a test varies it without touching `AppConfig`; the shared `titleOrder` comparator composes
   it with `AppConfig`'s parsed value. The lists' `TITLE` field and the ends' flagged half already read that
   comparator (§3.1), so this reaches both surfaces with nothing else moving.
4. **The doctrine** — `docs/ui-lists-guidelines.md` carries the sort state, the field table and the shared sort
   logic (`## Sort State`, "Shared sort logic"), so the prefix clause lands there; §2.12 **points at it**
   rather than restating the rule, which keeps one home for one rule.

### 6.4 What the user sees — and what does not move

- `Le Port` and `Les Sables` file under P and S **in the marker list sorted on Title and in the route-ends
  selector alike**, and `Léman` is untouched.
- **"Ignored" is the sort key's business only**: no label loses its article, and the fixed half's
  `-- %s --` dress is never compared — said because "ignored" reads like "hidden".
- **Dropping a prefix makes equal keys on purpose** — `Le Port` and `Port` now sort as one word — so ties stop
  being the odd case; §3.3's stable order decides them, in the list's own order, rather than anything
  invented here.
- Untouched: the direction toggle and its `reversed()` base, the custom sort fields, the `CREATED` default,
  the ties rule itself, and the comparator's per-comparison cost, which is what it is today — precomputing
  keys is not this pass.

### 6.5 Open points, for the user's word

- **Settled by the user's word (2026-09-29)**: the key is `title.sort.ignoredPrefixes`, and the set reaches
  **both surfaces** through the one shared rule — the selector alone would put the two alphabetical orders
  back into disagreement, which is the split §3.1 chose the shared comparator to prevent.
- **Which words ship** — `La`, `Le`, `Les` as given; the plan adds none of `Un`, `Une`, `Des`, `The` on its
  own. An apostrophe article is out of this rule's reach by the condition itself — `L'Île` carries no
  whitespace after the `L'` — so `L'` is not a value the rule could honour, and the plan says so rather than
  pretending otherwise.
- **Which markers are offered at all** — the §7 review's finding, left standing rather than quietly changed:
  the ends read the *displayed* list, so a marker its filter hides cannot be chosen as an end today. If the
  ends should read `allMarkers` instead, that is a second change and the user's call.

### 6.6 Tests

- **The pure `titleSortKey`**, which is also the first test the title rule will ever have — `ListSortOrder`
  has none today: `Le Port` → `port`; `Le   Port` (a run of spaces) → `port`; `LA PORTE` → `porte` (the case
  folded); `Léman` → `léman`, unchanged, the accent refusing the test; `Leman` → `leman`, unchanged, no
  whitespace after `Le`; `Les Sables` with only `Le` configured → unchanged, which is why `Les` must be
  listed; `Le` alone → `le`, unchanged, there being no next word; `Les Les Sables` → `les sables`, one drop
  only; `«Le» Port` → `le» port`, untouched by this rule, the test reading the title as it stands; an empty
  or blank ignored list → today's key.
- **The property pairing**, in the `config` package's habit: the shipped key is present in the real
  `maro.properties` and equal to `AppConfig`'s default, so a misspelling on either side fails rather than
  leaving the default standing.
- **Not reachable here, and named**: the two surfaces' rendering and the ordering the user sees, which stay
  with the device pass this plan's §3.5 already owes.

## 7. The review of §6 (2026-09-29)

Reviewing this plan because it is the plan in design and its §6 arrived after §5's review — so this pass reads
§6, and what §6 moved in §1, against the code rather than against the plan's own words.

Verdict: **revise** — the pinned rule is stated correctly and every part of §6 survives; §1's description of
the input did not, and one consequence of the rule was left unnamed. Both folded above.

- **High — §1 described the input wrongly.** `routeMarkers` collects `MarkersViewModel.markers`, and that
  field is the view model's **filtered and sorted** view (`_allMarkers → filter → sort → _markers`), not a
  store order: the flagged half arrives in the user's own list sort, newest first under the default `CREATED`
  and already alphabetical when the list is on Title. **Folded**: §1 now says what the screen actually hands
  over, and carries the second half of that fact with it — only the markers passing the list's filter are
  offered at all.
- **Medium — the equal-key consequence was missing.** Ignoring a prefix is *designed* to collapse `Le Port`
  and `Port` into one key, so ties become ordinary and §3.3's stability is what decides them. **Folded** as
  its own bullet in §6.4, where the reader meets it.
- **Medium — a behaviour to name rather than fix quietly.** Because the input is the displayed list, a marker
  the list's filter hides cannot be chosen as a route end today. **Folded** into §6.5 as the user's call,
  not changed here.
- **Low — a modifier was asserted without being read.** §6.3.2 called its parse `internal` by naming
  `parseHeatmapFamilies` as the precedent; what this pass verified is that the precedent is *reachable from
  the `config` test package*. **Folded**: the plan now says the visibility the precedent uses.
- **Low — the doctrine home was named without its section.** §6.3.4 said `ui-lists-guidelines.md` "is the
  lists' home for the title rule". It does carry the sort state, the field table and the shared sort logic,
  under `## Sort State` and "Shared sort logic". **Folded** as the citation.
- **Verified by this pass, since §6 leans on each**: the file's comma-separated precedent and its
  case-insensitive comment; the `config` package's habit of holding a shipped key against the code's default;
  `UserMarker.title` and `Track.title` being the item's own name; and `MarkersViewModel.markers` being the
  filtered-and-sorted view — which is what turned the first finding up.

## 8. The code review (2026-09-29 — the `#implement` run's Ask hop)

Reviewing the run's Target Files because they are §3's and §6's change set, read as they stand: `ListSortOrder.kt`,
`RouteEndSelection.kt`, `MapScreen.kt`, `AppConfig.kt`, `maro.properties`, the three new tests, §2.12 of
`ui-component-guidelines.md` and the section `ui-lists-guidelines.md` gained.

Verdict: **ship**.

- **Verified rather than assumed.** `titleOrder` reads `AppConfig` **per comparison** — `compareBy`'s selector
  being deferred — so the words are not frozen at class-init before `init(context)` runs, which is the one
  shape that would have left the key ignoring the file. `String.textAfterLeading`'s two refusals are the
  user's conditions to the letter: `regionMatches(ignoreCase = true)` makes `Léman` fail on its second
  character with the locale's own folding kept out, and the whitespace test makes `Leman` fail. The matched
  branch hands its remainder to the same drop-non-letters-then-lowercase transform the unmatched branch uses,
  so the key is one rule rather than two, and the fixed half is never handed to a comparator at all.
  `routeEndOptions`' label map is injective, so `mapNotNull` can drop nothing silently.
- **Low — the punctuation between the word and the next one** is dropped by the older leading-non-letter
  transform rather than by §6.2's own step. Harmless, and how the key has always read a title; recorded so a
  later reader does not take it for the rule slipping.
- **Low — `routeEndEntries`'s `order` parameter is a seam**, which its KDoc says out loud: the rule's one home
  is `titleOrder`, and a production caller passing its own comparator would be a second home. No change — the
  seam is what makes the order testable without a screen, and the only call site takes the default.
- **Low — the first run's failure was not this change's.** `RouteAvoidEngineTest`'s corridor budget case
  failed once while a Gradle daemon started, then passed alone and in the full re-run; that class reads named
  keys of the shipped file and the key this pass added is not among them. Recorded rather than re-run away,
  because a suite that failed once and passed twice deserves the note.
- **The Code hop's declared deviations are the plan's own semantics**: the parse trims and drops blanks with
  the case folded at the comparison instead of lowercasing the words up front — the same rule, without the
  locale's case folding — and the lists' direction is asserted beside the key cases, one case more than §6.6
  listed.
