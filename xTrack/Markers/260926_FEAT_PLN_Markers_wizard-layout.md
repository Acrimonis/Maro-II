<!-- scope: feature -->
# Marker wizard — one shell with the detail drawers, sections per the settings guidelines

Raised 2026-09-26: the marker wizard should read as a mix of the selected-item drawer — the
bottom-weighted card the marker and track details share — and the settings card sections, instead
of the bespoke frame each step draws for itself. `DrawerScaffold` is that shell and is adopted. What
follows is the review, the target anatomy and the six decisions, all settled on 2026-09-26.

## What the wizard does today

- `app/src/main/java/ykws/android/maro/ui/map/WizardDrawer.kt` hand-builds its surface: a `Column`
  (fillMaxSize, `drawerShape` clip, `uiBackground`) holding `WizardTopBar`, a `weight(1f)` content
  `Box` with an `AnimatedContent` step swap, and `WizardButtonRow`.
- `app/src/main/java/ykws/android/maro/ui/markers/wizard/WizardTopBar.kt` re-draws the shared header
  by hand — 32dp circle, 18dp `ArrowBack`, 17sp Bold title, 16dp gap — adding a 6dp dot-progress row
  and 24dp/12dp padding. Its title is the literal `R.string.wizard_title_create`, so an edit session
  reads with the create wording.
- Every step draws its own card: `SliderStep` (8×4dp outer, `uiCardBackground`, 12dp radius, its own
  16dp inset), `TypeSelectStep` (two cards — a hand-rolled segmented row and an icon row),
  `PositionStep` (one card, vertically centred), `TextInputStep` (one card, 15sp label).
- Nothing in the wizard uses `CardArea`, `SectionHeader` or `SectionDivider`, so "sections inside the
  card" has no home there today.
- Every step also fills the space it is given: `AnimatedContent` carries `fillMaxSize()` at
  `WizardDrawer.kt:132`, and `TypeSelectStep:64`, `PositionStep:46` and `TextInputStep:75` fill again
  inside it. Under the wrap-content frame that is the one mechanical trap — a filling step pushes the
  panel towards the scroll ceiling instead of leaving it at the card's height — so the re-cut has to
  drop the fills with the shells.

## The shell the other drawers share

`app/src/main/java/ykws/android/maro/ui/components/DrawerScaffold.kt` is that shell: `MarkerDrawer`
opens it at `:201` and `:322`, the track drawers at `OverlayLayer.kt:519` and `:635`, the menu drawer
at `MenuDrawerOverlay.kt:160`. Its parameters cover every difference between them — `headerActions`,
`headerHorizontalPadding`, `headerVerticalPadding`, `contentPadding`, `scrollable`,
`suppressOverscrollWhenFits`, `bottomAnchoredContent`, `wrapContent`, `wrapContentMinHeight`,
`statusBarsInset`, `shape`, `footer`.

- Wrap mode sizes the panel to its content, bottom-aligned, floored at `wrapContentMinHeight`; the
  body scrolls only when it exceeds `maxHeight - header - footer`, and `bottomAnchoredContent` is
  ignored there by construction (`DrawerScaffold.kt:188`).
- Non-wrap mode fixes the header, gives the body `weight(1f)`, and bottom-aligns the content column
  when `bottomAnchoredContent` is set (`DrawerScaffold.kt:278`).
- The header is `DrawerHeader` — 32dp circle, 18dp `ArrowBack`, 17sp Bold title, 16dp gap, an
  `actions` slot on the right, `heightIn(min = 48.dp)`.

## Target anatomy

- One `DrawerScaffold` for the wizard: title, `onClose`, the dot progress in `headerActions`, the step
  content in the body, the three action buttons in `footer`. No shell change is required for the title
  or the dots: `title` is a parameter re-read on every recomposition, and `headerActions` is a
  `RowScope` lambda, so both redraw from state — the marker viewer already updates its title as
  Prev/Next walks the selection (`MarkerDrawer.kt:202`). A `@Composable` title slot would only be
  justified if the header had to hold something a `String` cannot express.
- The step swap keeps its `AnimatedContent` and its forward/back slide, moving from the deleted
  `weight(1f)` Box into the scaffold's body, and its fills go so the body measures the card rather than
  the ceiling.
- Each step's content becomes a `CardArea` stack — rows of the settings family, `SectionDivider` between
  sections, `uiSpacingSectionGap` (14dp) between cards, the container owning the 16dp inset — with the
  body's `contentPadding` at the marker card's `PaddingValues(start = 12.dp, end = 12.dp)` and
  `suppressOverscrollWhenFits` read from that same card. `SliderStep` stops drawing a surface and becomes
  the wizard's use of the shared slider row.
- The card density moves with it: the Tight 8×4 the wizard's sliders use today gives way to `CardArea`'s
  16×8, and `ui-component-guidelines.md:50`'s Tight row, which names wizard sliders, is amended in the
  same pass.
- The footer's three buttons are the shared action button, so the row's own padding and gap are the
  footer's to state, the accent resting on the one enabled forward action.
- Portrait: wrap-content with `wrapContentMinHeight = portraitDashboardHeight`, matching the marker
  card. Landscape: non-wrap, `bottomAnchoredContent = true`, `statusBarsInset = true`, full left
  column.

## Decisions

- **P1 — portrait frame. Settled 2026-09-26:** the wizard takes the same behaviour as the track and
  marker selected dashboards — wrap-content in portrait floored at `portraitDashboardHeight`, and in
  landscape non-wrap with `bottomAnchoredContent = true`. Its accepted cost is a step taller than
  the card scrolling where it centres today, and the map's reserved area moving with the card.
- **P2 — the slot. Settled with P1:** the `.height(portraitDashboardHeight)` that
  `OverlayLayer.kt:333` puts on the wizard slot is removed, because wrap mode needs the full screen
  as its bounded parent; the floor moves into `wrapContentMinHeight` and the keyboard offset stays.
  The marker detail drawer's slot is the precedent — it carries no height in portrait.
- **P3 — dot progress. Settled 2026-09-26:** it stays, at the top right of the title row — which is
  exactly `DrawerScaffold`'s `headerActions`, a `RowScope` slot at the header row's trailing edge.
  The title keeps its `weight(1f)`, so a long name ellipsises before the dots; the accepted cost is
  width, a corridor's eight steps drawing about 76dp (8 × 6dp + 7 × 4dp) beside the title.
- **P4 — title. Settled 2026-09-26:** the header reads "Create Marker" while creating and gains an edit
  twin while editing, chosen from the mode the wizard already holds — `startWizard(initialType, …)`
  clears `editingMarkerId` and `startWizard(markerId)` sets it. The create key exists
  (`wizard_title_create`); the edit twin is new and carries `Edit Marker` with `Modifier le repère` — the
  copy picked 2026-09-26, the definite article marking the known marker being edited.
  `WizardTopBar`'s hardcoded title goes with the header it belongs to.
- **P5 — footer. Settled 2026-09-26, extended the same day:** the three hand-rolled pills go, and
  Previous, Next and Finish become the app's shared action button — `ConfirmActionButton` over a
  `ConfirmAction(label, role, enabled, onClick)` — so the active and dead renderings are one
  implementation rather than a surface's own. Nothing is extracted to get there: `ConfirmAction`,
  `ConfirmActionRole` and `ConfirmActionButton` already live in `ui/components/ConfirmDialog.kt`, whose
  KDoc calls the button the app's only rendering of an action, and the route panel is the shipped
  precedent of a non-dialog surface hosting it. The roles follow the family and that precedent — the
  accent sits on the one enabled forward action, Next or Finish on the last step, the others SECONDARY,
  and `enabled = false` carries the dead face the component already draws. The button's own geometry
  governs the footer's height, so the 48dp row reading no longer applies. The rule at
  `docs/ui-component-guidelines.md:594`, which names `ConfirmAction` inside the dialog section, is
  generalised to the family and to every surface that hosts the button in the same pass — code and doc
  landing together, since this file defers to the code.
- **P6 — shared primitives. Settled 2026-09-26:** `NestedCard`, `Expander` and `SliderRow` leave
  `MapScreenSettingsOverlay.kt` (`:1917`, `:1935`, `:1710`) for `ui/components`, beside `CardArea`,
  `SectionHeader` and the other shared rows, and the wizard's steps reuse them instead of growing rows
  of their own. The settings overlay keeps its behaviour and changes only where the three live.
  Dropped: the wizard's own thin rows, so "the same guidelines" is held by one implementation rather
  than by resemblance.

## Work order

1. Put the wizard inside `DrawerScaffold` and delete the hand-built header.
2. Move `NestedCard`, `Expander` and `SliderRow` from `MapScreenSettingsOverlay.kt` into
   `ui/components`, then point the settings overlay at them so nothing is duplicated.
3. Re-cut the step contents onto the `CardArea` section stack, drop the fills the wrap cannot survive,
   and delete the bespoke card shells.
4. Replace `WizardButtonRow` with the shared action button in the footer — the accent on the one enabled
   forward action, the rest SECONDARY, `enabled` carrying the dead face — delete the hand-rolled pills
   and their alpha dimming, and let the file go with them if nothing else reads it.
5. Amend the clauses the change overtakes — `ui-drawer-guidelines.md` §6 and §12, which name the wizard
   as unmigrated; `ui-component-guidelines.md` §2.2, which describes `SliderStep`'s own surface; §2.0's
   Tight density row; and §5.6's dead-button rule, generalised out of `ConfirmAction` to the family and to
   every surface hosting the button, the wizard's footer joining the route panel's.
6. Build and the scoped marker tests; the device pass stays the user's.

## Review findings after the first build — 2026-09-26

Two defects the user saw on the panel, both traced to code rather than to the frame's design.

- **The footer floats in portrait, and the content with it.** `DrawerScaffold`'s wrap branch stacks the
  header, the body and the footer from the **top** of a column that is only floored by
  `wrapContentMinHeight` (`DrawerScaffold.kt:196`–`251`): when the card is shorter than the floor the
  slack opens *below* the footer, so the buttons sit high instead of on the panel's bottom edge. The
  wizard is the first wrap drawer to carry a footer, which is why nothing showed it before. The fix
  belongs in the scaffold's wrap branch — the footer needs the slack above it, not below — and the
  marker and track cards, having no footer, are untouched by it.
- **One step is two cards where it should be one card with two sections.** `TypeSelectStep` draws a
  `CardArea` for the type segments, a `uiSpacingSectionGap` and a second `CardArea` for the icon row,
  but the section gap is the distance **between cards**; within one card, sections are separated by
  `SectionDivider()` (`ui-component-guidelines` §2.3/§2.6). Every other step is already a single card,
  so the type step is the one to fold — one `CardArea`, the segments and the icon row as its two
  sections, the divider between them.

- **P7 — the frame's weighting. Settled 2026-09-26 by the user's word:** the frame stays as it is — the
  dashboard floor in portrait, the full left column in landscape — and everything inside it stacks at
  the frame's **bottom**: the step's card sits directly above the footer, and the slack opens between
  the header and the card. That is the non-wrap branch's existing behaviour (`bottomAnchoredContent`,
  `DrawerScaffold.kt:278`); the wrap branch has to match it, which is the one change the footer's
  floating position needs.

- **P7a — the landscape text field. Revised 2026-09-26: the track drawer's behaviour is the target.**
  Editing a field in the track card in landscape pushes the content to the top while the buttons ride
  above the keyboard, and the wizard is left underneath the keyboard — the user's word is that this is
  the better behaviour. It is nothing more than the platform's own pan: the track card sets no
  `softInputMode`, so the window pans the focused field into view. The wizard deviates twice — it sets
  `SOFT_INPUT_ADJUST_NOTHING` on text steps (`WizardDrawer.kt:88`) and it lifts the whole portrait panel
  by the IME height (`keyboardOffsetDp`, `OverlayLayer.kt:337`). Adopting the track's behaviour means
  dropping the override; the point to settle with it is the portrait offset, since pan and offset
  together move the panel twice, so portrait either keeps the offset and pans only in landscape, or
  gives the offset up and lets the pan be the one mechanism everywhere.
- **P7a implemented 2026-09-26 — the second reading:** the pan became the only mechanism. The wizard's
  `ADJUST_NOTHING` override, its `isTextStep` flag, the activity lookup, the `findActivity` helper and
  their imports are deleted, and the portrait slot no longer lifts the panel by `keyboardOffsetDp`
  (`imeHeightDp` stays, because the scrim reads it). `MainActivity` already declares
  `windowSoftInputMode="adjustPan"`, so the override was the deviation rather than the default. Its cost
  is named rather than hidden: pan moves the window, so the map travels with the panel during an edit —
  exactly what the track card's fields already do.
- **P8 — the field's rendering. Raised 2026-09-26, open.** The wizard's Title and Description fields are
  bordered `OutlinedTextField`s with colours of their own; the track card's and the marker card's editors
  are transparent `TextField`s that read as the text they replace — no container, no indicators, the name
  15sp SemiBold `uiTextPrimary`, the comment 13sp `uiTextMuted`, cursor `uiTextPrimary`, `ImeAction.Done`
  (`TrackHistoryOverlay.kt:625`, `:676`; `MarkerManagementOverlay.kt:370`, `:414`). The user's word is
  that the latter is better and that the wizard should render that way. One implementation is the natural
  follow-through — a shared inline field the three surfaces read — with the open point that a bare field
  carries no label, so the wizard must decide what replaces its label line and its placeholder.
- **P8 implemented 2026-09-26:** `TextInputStep` now draws that transparent `TextField` — no container,
  no indicators, 15sp SemiBold `uiTextPrimary` text, cursor `uiTextPrimary`, a muted placeholder,
  `Next` on the name and `Done` on the description — with the label line kept above it and the
  placeholder kept inside, which is the reading taken for the open point. The shared primitive is not
  extracted yet: the three surfaces render alike and the wizard's label line is the single difference, so
  a shared control would have to carry that line as a parameter of its own before it is worth it.
  **Select-all on focus, settled 2026-09-26 by the user's word:** the field selects its whole text
  whenever focus arrives — so arriving on the step and tapping an unfocused field both put the value
  under the caret — and it remembers the previous state so a later re-gain selects again, while a tap
  that lands while the field already holds focus still places the caret. The variant that would select
  on every tap is declined: it would make caret placement impossible.
