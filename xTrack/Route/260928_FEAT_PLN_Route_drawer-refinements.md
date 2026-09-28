<!-- scope: feature -->
# Route — the drawer's refinements: the roller, one sub-section head, a legible selected face, and the collapsible tracks options

**What this delivers.** A second pass over the Menu drawer's Navigation card, written from a device
reading taken 2026-09-28, after
[`260928_FEAT_PLN_Route_subsection-and-control-model.md`](260928_FEAT_PLN_Route_subsection-and-control-model.md)
shipped its eight steps (build green, `## Docs` pointer written). It moves six surfaces — the roller, the
selected face the two choice components share, the sub-section's head, the arm action's shape, the
Import/Export pair, and the tracks-rendering options. **No requirement is added**: R44–R74 stand as they
are, and nothing in the previous plan is re-opened beyond its own surfaces.

**Rev 2 (2026-09-28)** folds in the user's word on the roller and on the selected face.
**Rev 3 (2026-09-28)** closes §3's first two points as already answered by that word, leaving four.
**Rev 4 (2026-09-28)** closes two more with no word needed: the collapsible's state stays in the drawer that
owns it, an expansion being view state rather than a preference, and `ActionRow`'s fate follows step 6 —
its only call site is the Import/Export pair, so it dies with that move.
**Rev 9 (2026-09-28)** moves the Navigation card's **auto-show zones toggle below the Route sub-section**, on
the user's word: the card reads GPS mode → the summary (when it stands) → the Route block → auto-show at the
foot, that row's label, preference, callback and gate untouched. The drawer guidelines' own order sentence
moved with it.

**Rev 8 (2026-09-28)** corrects Rev 7, which had it backwards: **the head stays and the per-row labels go** —
`route_comment_ends` returns as the comment naming both fields (`Route origin and destination` / `Origine et
destination de la route`), the two dropdowns are **label-less**, each showing only its value on the right, and
`route_end_caption_start` / `route_end_caption_destination` are deleted from both locales as orphans. The rule
between the rows stays out, so the group reads: one divider, the comment, the two value-only rows, the arm
action.

**Rev 7 (2026-09-28)** is the last word on the sub-section's shape, and it removes more than it adds: **no
head and no rule** — each dropdown's own label (`Origin`, `Destination`) identifies its field, so
`route_comment_ends` lost its only reader and was deleted from both locales, and the `SectionDivider` between
the two rows went with it. What stands is one divider above the group, the two rows back to back, and the
arm action: **the rows are the head**.

**Rev 6 (2026-09-28)** closes the roller half of this plan by **retirement**, on the user's word: the wheel
never committed reliably, so it and its component are **gone** — steps 1 and 2 with them, step 1's
instrumentation cut short before its trace arrived — and each end is a `DropdownRow` labelled `Origin` /
`Destination`. The sub-section's head becomes one comment naming the group (`Route origin and destination`),
and R70 of the master book is superseded in the same pass.

**Rev 5 (2026-09-28)** records the build: steps 1 and 3 to 9 **delivered in one pass** — `apk-build.bat`
green and the whole unit suite green — with §3 emptied by the user's word and step 2 left open on the
device trace it waits for.

## 1. The reading, item by item, with what the code says today

- **The roller does not centre on the choice, and its highlight does not match the selection** (the user's
  word). Two mechanisms are live in [`RollerRow`](../../app/src/main/java/ykws/android/maro/ui/components/RollerRow.kt:1)
  and neither can be told apart by reading it: the marked row is chosen by `index`, which
  `remember(selected)` re-initialises from `options.indexOfFirst { it.first == selected }` — a lookup that
  **falls back to `0` when it misses**, so every miss highlights the first entry whatever the selection
  says — and the drag is claimed by
  `pointerInput(enabled, options.size, selectedIndex)`, whose **key changes the instant a selection
  commits**, which cancels `detectVerticalDragGestures` **without running `onDragEnd`** and leaves
  `dragging = true` with a **non-zero `carried`**, so the entries then paint between two rows. The
  diagnostics in step 1 are what settle which of the two is happening.
- **The selected face is not legible enough** — the user's word: **20 % alpha is too low** and the
  **check is not accented enough**. Today the **row** carries the only 1dp `ui.divider.color` outline and
  only the *cell* is tinted (`ui.select.container=#331565C0`), so nothing marks the selected cell's edge
  at all, and the glyph is a 16 dp `Icons.Filled.Check` in `ui.accent` — **blue on an accent-tinted
  ground**, which is where its contrast goes.
- **Two headings both say `Route` in one card** — the sub-section's head takes `settings_section_route`
  (`Route`, [`strings.xml:592`](../../app/src/main/res/values/strings.xml:592)) and the summary block above
  it heads itself with `route_trip_title`, also `Route`
  ([`strings.xml:650`](../../app/src/main/res/values/strings.xml:650)). While a route is followed the card
  prints the word twice, so a **comment naming the route's origin and destination** should stand in the
  header's place.
- **The arm action** reads `Compute route` ([`strings.xml:598`](../../app/src/main/res/values/strings.xml:598))
  and spans the full row.
- **Import/Export** are bare label + icon tap rows ([`ActionRow`](../../app/src/main/java/ykws/android/maro/ui/components/ActionRow.kt:1),
  promoted on 2026-09-28), not buttons.
- **`Display Tracks with:`** ([`strings.xml:316`](../../app/src/main/res/values/strings.xml:316)) is a
  static caption with the two chips under it, always open.

## 2. Steps

- [x] **1 · The roller is instrumented, and nothing about it is guessed** — one log tag, `RollerRow`,
  carrying the five facts that settle the two candidate mechanisms: on composition, `options.size`, the
  `selected` value, `selectedIndex` **before** the `coerceAtLeast(0)` and `index`; on the gesture
  detector's (re)start, the same key values plus `dragging` and `carried`, so a **restart during a drag
  is caught in the act**; on drag start and on each index change, `index` with `carried`; on drag end,
  `index`, `carried`, the marked label and whether the lift commits; and at the commit itself, the
  selection handed to `onSelect` beside the one it replaces. Tag and messages are log text, not UI text,
  so they stay literals (§1). **Build green, then the user deploys and captures the logcat** — the
  deployment and the run are the user's, and the fix waits on the trace. **Delivered 2026-09-28**: built
  as written, with the lookup split into `rawSelectedIndex` and the coerced `selectedIndex` so the
  pre-coerce value is visible; `apk-build.bat` green and the route-filtered suites green.
- [x] **2 · The roller is fixed** — **closed by retirement 2026-09-28**: the control was replaced by dropdowns before the trace arrived, so the fault has no subject; its component went with the retirement. From the trace alone: if the mark is on the wrong row, the index
  derivation; if it is the stale `dragging`/`carried`, the gesture key and an `onDragCancel` that resets
  both. The drag claim inside the row's bounds, the spring, the commit-on-lift and the signature stay as
  they are.
- [x] **3 · The selected face becomes legible** — **delivered 2026-09-28**: `ui.select.container` rose to
  the accent at **30 %** (`#4D1565C0`) in `colors.properties`, `AppConfig` and `color-scheme.md`, both
  components gained a **1dp `ui.accent` border on the chosen cell** and an 18dp check glyph in
  `ui.value.text`, and `SelectContainerPropertiesTest` pins the new alpha. `ui.select.container` gains opacity at its **one home**,
  above the 20 % the user's word rules out; the selected cell gains a **1dp `ui.accent` border of its own**
  (the parent's rounded clip shapes its outer end, so no new shape is needed); and the check glyph gains
  size **and** accent. Its home is the existing `ui.value.text`, whose own comment already reads **lighter
  accent-tinted blue for readable small text** — one token, no new key, the token being the agent's under
  the direction the word gave. Both `SegmentedRow` and `MultiSelectRow` follow, being one face
  (D4 of the previous plan); outlines, geometry, `selectableGroup()` / `toggleable` semantics and roles do
  not move.
- [x] **4 · One sub-section, headed by a comment** — **delivered 2026-09-28**: `settings_section_route`
  retired from both locales and `route_comment_ends` (`From %1$s to %2$s` / `De %1$s à %2$s`) heads the
  sub-section from the standing selection, printing no head at all where an end has no label. The bare `Route` title leaves the sub-section's head
  and a **comment naming the route's origin and destination** takes its place, its wording in **both
  locales**; the summary's own `Route` heading is de-duplicated in the same pass, so one card never prints
  the word twice.
- [x] **5 · The arm action** — **delivered 2026-09-28**: `route_action_arm` reads **`Route`** in both
  locales and the button takes the row's right half inside an `End`-arranged `Row`. The label becomes **`Route`**, and the button **takes the right half of the
  row** only. `ConfirmActionButton` already takes a `modifier`, and its own `fillMaxWidth()` resolves
  against the max it is handed, so `Modifier.fillMaxWidth(0.5f)` inside an `End`-arranged row lands at half
  width with no change to the shared component.
- [x] **6 · Import/Export become action buttons** — **delivered 2026-09-28**: both render through
  `ConfirmActionButton` in the outlined role, weighted halves 8dp apart, and `ActionRow.kt` is **deleted**
  with its two glyph imports. The pair renders through `ConfirmActionButton` in the
  **outlined role** side by side, the pair being neither the drawer's outcome nor a loss (§5.6), and two
  accent fills would break the one-per-surface rule. Consequences to accept with the change: the glyphs
  drop, the component having no icon slot, and **`ActionRow` loses its only call site** — its sole reader
  being this pair ([`MenuDrawerOverlay.kt:334`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:334)) —
  so it dies with the move and is parked only if the pair keeps its rows.
- [x] **7 · `Display Tracks with:` becomes collapsible** — **delivered 2026-09-28**: the caption became
  the `Expander`'s own label, the two chips moved into the `NestedCard` it reveals, and the open/closed
  state is a `rememberSaveable` in `MenuDrawerOverlay`. The caption and the two chips move inside an
  `Expander` + `NestedCard` (§2.3/§2.4), the settings tabs being the precedent; the open/closed state is
  the caller's — `MenuDrawerOverlay` holds it, an expansion being view state rather than a preference.
- [x] **8 · The documents move with the code** — **delivered 2026-09-28**: §2.7 / §2.7b (the border and the
  glyph), §5.9 (**four** tiers now, the bare label + icon tap row retired with `ActionRow`), §1's actions
  branch and `docs/ui-drawer-guidelines.md` §8 / §8a moved with the code, `docs/color-scheme.md` with the
  token. `docs/ui-component-guidelines.md` §2.7 / §2.7b (the
  border and the glyph), §2.15 (only if the roller changes shape), §5.9 (tiers 2 and 5) and
  `docs/ui-drawer-guidelines.md` §8a (the comment head, the half-width action, the collapsible);
  `docs/color-scheme.md` when the token's value moves.
- [x] **9 · Build** — **delivered 2026-09-28**: `apk-build.bat` **BUILD SUCCESSFUL** and
  `:app:testDebugUnitTest` green.

## 3. Open, awaiting the user's word

**Empty since 2026-09-28.** The last two points were answered by the user's word — `From %1$s to %2$s`
(`De %1$s à %2$s`) for the head's comment, with the summary keeping its `Route` title, and the **outlined**
Import/Export pair with the glyphs dropped — and the four before them went by Rev 3 and Rev 4, so nothing
here waits.

Closed by Rev 4, needing no word: **the collapsible's state**, held in the drawer that owns it, and
**`ActionRow`'s fate**, which step 6 settled as the consequence of its own change.

## 4. What this pass does not do

- **No requirement is added or amended** — the guideline documents describe the app as it stands and move
  with the code; R44–R74 are untouched.
- **The roller's over-drag past either end, its missing tap path, the trigger's coroutine read of the pair
  and the epic's `## Implemented` line** all stand exactly where the previous report left them.
- **Phase 6's device pass** stays owed and stays the user's.
