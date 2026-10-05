<!-- scope: feature -->
# Quick access to the two route settings — a wheel each, side by side

Owner: **Ui_Menu** (the drawer surface). Content owner: **Route** (both settings are the mode's).
Branch: `feature/menu-live-cards`.
Status: **implemented 2026-10-04.** The range, the snap, the card's three sub-sections, the pair **and the
pair's width rule** (§2, §3, §9) are in the tree; `apk-build.bat` is green. **§10's component boundary is
settled and unbuilt.**

## 1. What existed — the facts

- **Cruising speed** was `AppSettings.routeFreeWaterPaceKn`, seeded from `route.freeWaterPaceKn` = **28** and
  defaulted to 28 in `AppConfig`, whose bounds pair was **3 … 40**; Settings drew it as a `SliderRow` over
  those bounds in whole knots.
- **Driving preference** is `AppSettings.routeSlowWaterAversion`, a cursor over **three stops** whose λ are
  5 / 2.5 / 0, printed as **Fun / Balanced / Fast** — the ladder's index 0 is the most-fun end — the stored
  value snapping to the nearest rung.
- **The pace a route plans at is not always the setting**: `RouteViewModel.paceKn` returns the set pace *or
  the boat's own observed pace once it has evidence*.
- **The ends' control is the shape to copy**: `DropdownRow` paints the selected entry's word in a bordered box
  and opens its list **as the wheel in a popup**, where a tap commits and the drag only scrolls and snaps.
- **The control cannot hug its content**: its root is a `Column(fillMaxWidth)`, its box a `Row(fillMaxWidth)`
  with the value at `weight(1f)`, so the box always fills the width it is handed and the arrow is pushed to
  that box's far right.
- **What the two boxes must hold**: the pace's words run `5 kn` … `35 kn` (`settings_route_pace_value_fmt`),
  the preference's are `Fast` / `Balanced` / `Fun` (`Rapide` / `Équilibré` / `Plaisir` in French). The pace's
  own longest word is therefore the pair's **shortest** content, and the preference's the longest.
- **One resolution the pair inherits**: a value the options do not carry leaves the box painting the **first**
  option while the stored value stays.

## 2. The decisions — the user's word, 2026-10-04

- A section giving **quick access** to the two settings, so neither needs the Settings page.
- Both **bind to the settings** — `routeFreeWaterPaceKn` and `routeSlowWaterAversion` — so the quick access
  is a second **door** onto those values and never a second home; the speed box shows the **set** pace, not
  the boat's fitted one.
- The control is the **ends' own**: a `DropdownRow` per setting, its list opening as the wheel in its popup.
  **No rule is widened** and §2.15 stands as written.
- The range becomes **5 … 35 kn in 5-knot steps**, for **both the settings and the wheel** — seven entries.
- The **default becomes 25**, and the setting **snaps onto the grid** wherever it is loaded or written: the
  nearest stop wins, so a stored 28 lands on **30**, an old 3 on 5 and an old 40 on 35.
- **The card's header becomes `Routing`** (`settings_tab_routing`), where the drawer's first section was
  titled Navigation (`settings_section_position_source`) while holding routing content.
- **Three sub-sections in that one card, in this order**: **Origin and destination** → **Cruising speed and
  driving preference** → **the live card, when applicable**. Each of the first two is headed by a very short
  comment and set off by a `SectionDivider`.
- **The pace's box is fixed to its longest string and never ellipsises** (the user's word, 2026-10-04, the rule
  settling after moving twice): the left box takes the width of its widest entry — `35 kn` — and holds it
  whatever the row does, so **none of its seven stops can be cut**; the **preference's box is the elastic
  side**, holding the row's remainder and trimming on its one line when the row is short.

## 3. What follows from those decisions

- **The range's one home first**: `AppConfig`'s bounds pair becomes 5 and 35, and its readers follow — the
  `maro.properties` default and the comment beside it, the Settings slider's `steps` and `valueRange`,
  `SettingsManager`'s clamp on load and `RoutePace`'s on the fitted pace.
- **The snap belongs where the bounds already clamp** — the loader and `SettingsManager` — so one rule moves
  a stored value onto the grid and no control has to: that is also what removes the first-option misreading
  the pair inherits, since a value off the grid can then no longer be stored.
- **The ladder's inverse and its words become one home**: `routeRungLambdaOf(index)` beside `routeRungIndex`
  (with `routeRungLambda` delegating to it) and `routeRungLabelRes(index)`, so the drawer's wheel and the
  Settings slider read one mapping instead of two `when`s.
- **The width split is the caller's, measured, and the control answers for itself** — `dropdownBoxWidth(words)`,
  added beside `DropdownRow`, gives what a box needs at its own content (its 12dp padding, its 8dp arrow gap,
  its arrow and its 1dp rim, read from the control's own constants), and the pair splits the line with it: the
  **pace's** box at exactly that width, the **preference's** box at the row's remainder.
- **The measurement merges the theme's own text style** — `LocalTextStyle.current` — exactly as the box's own
  `Text` resolves its style. A style spelled from the size and the weight alone measures without the theme's
  **letter spacing**, so it came out short by about half a point a character: a width cut to that answer
  ellipsised the very word it was measured for. The drawer's `rememberLabelColumnWidth` carried the same fault
  from the same session and takes the same merge.
- **The explicit width is what makes the rule hold**: a weightless `Row` measures its *first* child against the
  whole line and hands the remainder to the second, which happens to give this shape — but only the pace's own
  `Modifier.width` keeps it true whatever either box happens to measure.
- **The width is a capability of the pair, not of `DropdownRow`** (the user's word, 2026-10-04): the pair is its
  own composable, `DropdownPairRow`, whose `DropdownPairWidth.Content` / `.Remainder` parameters set each side;
  `DropdownRow` itself still takes no width, its value `Text` ellipsising on one line, so an elastic side
  degrades to `Équilibré…` rather than clipping or widening the row.
- **A floor is part of the rule**: at the smallest font scale the two words cannot both fit, and it is the
  **preference** that trims — the pace's word standing whole by construction, not by luck.
- **The drawer writes again**: two settings, where the `Route` button's removal had left it carrying nothing
  but the ends' own selections. The parameters travel in the same bundle the ends' selections take.
- **The Settings tab keeps its place** and its rows; it takes the new range and grid with everything else.

## 4. Settled, and how

- **The section's home and title** — a card of its own is what the ends already inhabit; the header becomes
  `Routing`, and the pair is a sub-section of that card under a short comment, the shape the ends wear.
- **Whether the range change ships alone or with the section** — both ship in this pass.
- **Which side keeps its width** — settled twice, and the later word wins: the **right** is protected and the
  **left** absorbs the shortfall (§2, last bullet).

## 5. What entered

- **Values**: the default 25 in `maro.properties` and `AppConfig`, the bounds pair 5 / 35, the 5-knot grid
  (`ROUTE_FREE_WATER_PACE_STEP_KN`, `ROUTE_FREE_WATER_PACE_STOPS_KN`), `snapFreeWaterPaceKn`, the slider's
  `steps`, and the snap in the properties loader and in `SettingsManager`.
- **The drawer**: the `Routing` header, the two comments, `RouteQuickAccessSection`, and four new
  `RouteSummaryData` fields (`paceKn`, `onPaceSelect`, `preference`, `onPreferenceSelect`) with their
  `MapScreen` wiring through `viewModel.updateSettings`.
- **Strings**: `route_comment_quick_access` (new, both locales), `route_comment_ends` shortened to
  `Origin and destination`, and the two controls' names reusing `settings_route_pace_label` /
  `settings_route_preference_label`.
- **The component**: `ui/components/DropdownPairRow.kt` — `DropdownPairRow` with its `DropdownField` side type
  and its `DropdownPairWidth` capability, owning its 8dp gap and its vertical padding; `dropdownBoxWidth(words)`
  stays the `DropdownRow.kt` companion, where the box's own metrics live.
- **Docs**: §2.12 of the component guidelines carries the pair's bullet — measured split, never weighted — and
  the drawer's §8a is rewritten as the Routing card, its three sub-sections and the priority the pace yields.
- No dependency, no colour, no new token; the wheel's geometry is the shared policy's.

## 6. Steps — as run

1. Bounds to 5 and 35, the default to 25, and the snap added in `AppConfig` and at both clamps.
2. The Settings slider's `steps` off the grid, and its preference label read from `routeRungLabelRes`.
3. The card's header renamed and its sub-sections reordered, with the two short comments.
4. The pair added, bound through the bundle to the settings.
5. Rebuilt — `gradlew assembleDebug`, BUILD SUCCESSFUL.

## 7. Verification

- **In reach:** the build, green; the values read from one home each — `AppConfig` for the bounds, the grid
  and the snap, the ladder for a rung's λ and word.
- **Not in reach:** whether a content-hugged left box beside a protected right box reads as one pair of
  fields, whether a seven-entry wheel feels right where the ends' carry three to five, and whether 25 kn is
  the right default. The user runs this.

## 8. Out of scope, named so it is not smuggled in

- The Settings tab's rows stay where they are: this is a second door onto the same values, not a move.
- The route engine, `RoutePace`'s fitting and the preference's λ are untouched beyond the range they clamp to.
- The live blocks, the sub-cards and the ends' own entries are untouched.
- **The ends' two boxes stay full-width equal rows**: the dynamic split is the quick-access pair's rule, not a
  new default for `DropdownRow`'s other callers.

## 9. The width rule, as built

- **The pair's row in `RouteQuickAccessSection`** gives the pace's box `Modifier.width(dropdownBoxWidth(...))`
  — its widest entry — and the preference's `weight(1f)`, so the pace stands fixed and whole for all seven
  stops while the preference absorbs the row and trims when it must.
- **The squeeze-the-pace arithmetic is gone**: `BoxWithConstraints`, the `minOf(...)` and the import that
  carried them were removed with the correction, so nothing in the row can narrow the pace's box any more.
- **The pair is a component of its own** — extracted on the user's word the same day: `DropdownPairRow` in
  `ui/components/DropdownPairRow.kt`, the width a settable capability (`Content` fixed to a side's longest
  option, `Remainder` elastic) and `DropdownField` carrying a side's inputs; the drawer passes two fields only.
- **The measurement's style is the theme's own, merged** — the first cut of this rule still ellipsised the
  pace's word, because a style spelled from the size and the weight measures without the theme's letter
  spacing; both measurement sites now merge `LocalTextStyle.current`, and the 2dp term stands as the rounding
  slack it always was.
- **`dropdownBoxWidth`'s 2dp rim term stays, and is now named for what it is** — the tolerance that keeps the
  widest entry from rounding into an ellipsis, which is exactly what a box that must never trim cannot risk.
- **The consequence, named rather than left to be discovered**: the pace's box is the narrow one and the
  preference's the roomy one, so the emptiness the equal halves showed on the left simply sits on the right.
- **The comment above the pair, §8a's bullet on it and §2.12's own bullet** all state the fixed-pace /
  elastic-preference rule, and none of them speaks of the pace ellipsising.
- **The edge that remains is the preference's**: a line narrower than the pace's fixed box plus the 8dp gap
  leaves its `weight(1f)` almost nothing, so its word ellipsises to a sliver — reachable only at an absurd
  width or font scale, and the pace's own word still standing whole there.

## 10. The component boundary — settled 2026-10-04, unbuilt

**What the extraction left**: `DropdownPairRow` composes `DropdownRow` and calls `dropdownBoxWidth(words)`,
which stays in `DropdownRow.kt` because the box's metric constants are private there. The capability is right;
**where it is implemented is not**, and the user's word of 2026-10-04 raises it as new requirement material.

**Finding 1 — one component reaches into another's file.** The pair's `Content` width is answered by a
function that belongs to the box, in the box's file, published `internal` only so a second file can call it.
Nothing in either file says so: a reader of `DropdownPairRow.kt` sees a call, and a reader of `DropdownRow.kt`
sees a function with no caller in sight. Two components, one unstated dependency.

**Finding 2 — the box's chrome is one fact stated twice.** `DropdownRow` draws padding 12dp, an 8dp gap, a
24dp arrow and a 1dp rim; `dropdownBoxWidth` sums those same four numbers to predict what the drawing will be.
The control's own KDoc already makes the opposite claim for the other axis — its height "is that padding's
consequence, not a number" — while the width is a number spelled outside the layout that produces it. Any
change to the box's chrome (a leading icon, a different pad, a larger arrow) silently moves the drawing and
leaves the prediction behind, and the symptom is the defect already fixed here twice: a fixed box that
ellipsises the word it was measured for.

**Finding 3 — the value's style is the same kind of duplicate.** The box's `Text` resolves
`LocalTextStyle.current` merged with the size and the weight; the measurement repeats that merge by hand. This
is exactly what bit the pace's box on the first build — the measurement's copy had no letter spacing — and the
copy is still there, one edit away from drifting again.

**Settled 2026-10-04 — the direction is the self-contained one** (the user's word): each control owns its own
facts in its own file, so **the box becomes the component that answers for itself** and nothing predicts a
layout it does not draw. Code health decides it: the same fact in two places has already cost three fixes on
this one pair.

**The target shape.**

- **`ui/components/DropdownBox.kt` — the box, and nothing else.** Its surface (the bars' base, `uiRadiusCard`
  behind the accent rim), the value `Text`, the `KeyboardArrowDown`, the box's own metric constants, the value
  style it resolves — `LocalTextStyle` merged with the size and the weight — and **`dropdownBoxWidth(words)`,
  the box's own answer for its content's width**, all in the one file. The style the `Text` draws with and the
  style the width is measured in are then the same value, read once.
- **The properties are the component's, the behaviour is the environment's** (the user's word, 2026-10-04, and
  the rule this shape exists to keep): the box's padding, its arrow, its rim, its style and the width its own
  word needs are **its own facts, private to it**, and what a caller hands it is the **behaviour it must have**
  — take the width you are given, or take the width your longest option needs. No caller handles a dp, an arrow
  or a padding, and none re-derives what the box already knows about itself.
- **`ui/components/DropdownRow.kt` — the labelled field.** The label, the description, the anchor, the popup and
  the wheel, composing `DropdownBox` for its surface; it keeps no metric of the box's own and no width
  arithmetic, and it passes the caller's behaviour down unchanged.
- **`ui/components/DropdownPairRow.kt` — the pair.** Two label-less rows, each side given a **behaviour** of its
  own (`DropdownPairWidth`, whose `Content` side is answered by the box itself), so the drawer's call site still
  states no width. It depends on components, on no file's internals.

**The invariants it buys** — one home per fact: the chrome draws and predicts from one set of constants, the
value style is resolved once and read by the drawing and the measurement alike, and the pair's width capability
has exactly one implementation to keep honest.

**Rejected on the way.** **(A) Documenting the reach into `DropdownRow.kt`** — free, but it leaves the
predict-and-draw pair of facts live, which is the defect this settles. **(C) Letting intrinsic layout measure
the box** — the ideal end state, and it would delete the arithmetic outright, but it needs the box's `Text` to
stop being `weight(1f)`, since a weighted child contributes nothing to an intrinsic measurement and the box
would then ask for no room for its own word; that changes how the box behaves when a caller hands it less width
than its value needs. Named, not taken.

**Built 2026-10-04** (the `#implement` pipeline's Code hop), along the steps this section listed:

1. `ui/components/DropdownBox.kt` created — the surface, the four metric constants, the value style
   (`boxValueStyle`, the one statement of it) and `dropdownBoxWidth`, all in the one file.
2. `DropdownSizing` added there (`Fill` / `Content`) and carried by `DropdownRow` as its `sizing` parameter, the
   field turning the behaviour into the width the box answered for itself; the pair's `DropdownPairWidth` is
   translated into it, so neither a caller nor the pair hands over a number.
3. `DropdownRow` repointed at `DropdownBox` for its surface, keeping the label, the description, the anchor, the
   popup and the wheel; the pair composes the field rather than the box, so **§2.4's depth rule is untouched**.
4. Rebuilt green (`apk-build.bat`), and §2.12 and §2.16 restated on the box's own home.

**What it touches**: `ui/components/DropdownBox.kt` (new), `DropdownRow.kt`, `DropdownPairRow.kt` and the two
guideline sections; the drawer is untouched unless a name changes.

**Out of scope here, named so it is not smuggled in**: `rememberLabelColumnWidth` in the drawer is the same
shape — it predicts a `Text`'s width rather than measuring a layout — fixed for the style but still predicting;
a shared `TextWidth` helper beside the box's own would serve both, and that is a separate decision.

## 11. The chrome, trimmed (2026-10-04)

The pair's two words did not both fit: a box's chrome — its horizontal padding, its arrow gap, its arrow and
its rim — is paid **twice** in one row, and at the drawer's width the sum left the preference's own word short,
so it trimmed the moment the pace's measurement was corrected for the theme's letter spacing. Both breaches the
user reported — the second field cutting its word, the first wearing more chrome than its word needs — are that
one number.

**The lever is the box's own, and it is taken**: `FIELD_PAD_HORIZONTAL_DP` 12 → **8**, `FIELD_ARROW_GAP_DP`
8 → **4**, `DROPDOWN_PAIR_GAP_DP` 8 → **4** — 28dp freed across the pair, which is what lets both words stand
whole in English and in French. Every dropdown wears the same 8dp field, the box's chrome being the box's own
fact; the arrow stays the icon's 24dp, the vertical padding stays the bar cells' and the 2dp rim term stays as
the rounding slack that keeps a fixed word from rounding into the ellipsis it must never wear.

## Outcome

Implemented 2026-10-04: the pace setting moves on a 5-knot grid from 5 to 35 with 25 as its default and the
nearest stop as its snap, the Settings slider follows it, and the drawer's first card — now titled Routing —
holds the ends, the quick access to the pace and the preference, and the live summary when it has something to
say. The pair's width rule then moved twice on the user's word and settled on the pace's box **fixed to its
longest string** — none of its seven stops can be cut — with the preference's box the elastic side, built off
`dropdownBoxWidth` beside the control. The pair was then **extracted as its own composable**
(`DropdownPairRow`, §2.16 of the component guidelines) with the width as a settable parameter, and the
measurement was fixed to merge the theme's own text style. `apk-build.bat` green.

**Superseded 2026-10-05.** §2's last bullet and §9's fixed-pace / elastic-preference split were replaced by the
**proportional** split — each box taking a row share matching its own longest word — on `feature/menu-align`;
see `261005_FEAT_PLN_Ui_Menu_menu-align.md`. The pair's default widths (`Content` / `Remainder`) are unchanged.
