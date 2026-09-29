<!-- scope: feature -->
# Add-marker trigger — the shared create action in the two markers headers

**Feature:** Markers · **Branch:** `feature/add-marker-trigger` (cut from `origin/develop` by `#new`, 2026-09-29)
**Status:** shipped 2026-09-29, then **amended** the same session — the create action moved from the trailing
group's end to its head. The epic's `## Implemented` pointer is the source of implemented-ness (§7a), not this
header.

## 1. Why

The right-hand map control stack's Add Zone button is being repurposed to another action, so creation loses
its fastest door. That button is a bare [`MapControlButton`](../../app/src/main/java/ykws/android/maro/ui/map/MapControlButton.kt:22)
carrying `AddLocationAltIcon` ([`MapScreen.kt:4082`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:4082)),
standing directly above the layer fan in the `cm` slot; it calls `onAddZone(mapCenter)`
([`MapScreen.kt:2382`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2382)) → `startWizard(initialPos = mapCenter)`.

What reaches creation today, and what the change does to each:

| Door | Where | Today | After |
|------|-------|-------|-------|
| Map control stack button | `MapScreen.kt:4082` | one tap, at the map centre | **repurposed away**, by the companion change still to come |
| Empty list's filled button | [`MarkerManagementOverlay.kt:218`](../../app/src/main/java/ykws/android/maro/ui/map/MarkerManagementOverlay.kt:218) → `onCreateFirst` (`MapScreen.kt:3308`) | only while the list is empty | untouched |
| Marker card's `Edit` | [`MarkerDrawer.kt:311`](../../app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt:311) | edit only, never a create | untouched |
| Markers **list** header | `ListOverlayScaffold`'s `headerActions` slot | **no create affordance at all** once the list has items | **the shared create action, at the head of the trailing group** |
| Hamburger menu's **MARKERS** header | [`MenuDrawerOverlay.kt:373`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:373) | none | **the same action, the same way** |

So a populated list has no create affordance, the menu never had one, and the map is losing its own. This plan
gives both markers headers the same control and writes the pair's shape down once.

## 2. Decisions

**D1 — The list is the primary host.** It is the markers' own surface, and its slot already exists:
`headerActions` is declared at [`ListOverlayScaffold.kt:517`](../../app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt:517),
rendered where the cluster begins at [`ListOverlayScaffold.kt:689`](../../app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt:689), and **passed by nobody**
until this change — verified by reading both call sites, [`MarkerManagementOverlay.kt:184`](../../app/src/main/java/ykws/android/maro/ui/map/MarkerManagementOverlay.kt:184) and
[`TrackHistoryOverlay.kt:378`](../../app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt:378). The tracks list cannot be disturbed, as it passes no slot.

**D2 — The create control is the trailing group's first child, immediately after the section label.** *(Reversed
by the user's word, 2026-09-29: the plan first called it the cluster's last child, that build ran, and the
position was sent back to the head — the label's own adjacency is the better reading of "the markers' create",
and the outer edge is where the read-chrome group's own term belongs.)* In the list it needs **no reorder at
all**: the slot already renders first ([`ListOverlayScaffold.kt:689`](../../app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt:689)), so the build's move of that call to the
end is **reverted** and the row returns to `label · create · rule · link · filter · sort · reset`. In the menu
the same position is the **head** of `SectionHeader`'s `trailing` slot
([`SectionHeader.kt:27`](../../app/src/main/java/ykws/android/maro/ui/components/SectionHeader.kt:27)), whose title takes the remaining width — so the action sits left of the
link, filter, sort and reset icons there too.

**D3 — A vertical rule divides the create action from the chrome group.** The chrome group is link · filter ·
reset in both headers, all of it shaping *how the list is read*; create is a surface action. Shape: the app's
own vertical-rule idiom — `uiDividerHeight` of width in `uiDividerColor` with `uiDividerGap` either side,
which is 1 dp and 6 dp today ([`AppConfig.kt:1197`](../../app/src/main/java/ykws/android/maro/config/AppConfig.kt:1197)), and which is exactly how
[`SectionRow.kt:60`](../../app/src/main/java/ykws/android/maro/ui/components/SectionRow.kt:60) draws the rule dividing its two card sections
([`SectionRow.kt:49`](../../app/src/main/java/ykws/android/maro/ui/components/SectionRow.kt:49), its KDoc stating the rule at `:33`); the literal
1.dp of [`SegmentedRow.kt:66`](../../app/src/main/java/ykws/android/maro/ui/components/SegmentedRow.kt:66) and
[`MultiSelectRow.kt:57`](../../app/src/main/java/ykws/android/maro/ui/components/MultiSelectRow.kt:57) is that same width inside a segmented control.
**The rule takes a fixed dp length, not a fill.** `SectionRow`'s rule stretches because that row declares
`.height(IntrinsicSize.Min)` ([`SectionRow.kt:48`](../../app/src/main/java/ykws/android/maro/ui/components/SectionRow.kt:48)); neither header row does, and a
`fillMaxHeight` rule there would read the incoming maximum instead — the list's row would grow to its parent's
allowance. The length is the number the component carries — **24 dp** as built — the same in both hosts. **In
the amended order the rule now trails the button** rather than preceding it: `create · rule · chrome group`.

**D4 — The create action owns its own rule, and the scaffold only reorders.** The earlier preference — a
nullable `headerActions` slot with the scaffold drawing separator-plus-content — was withdrawn once the menu
became the second host: `SectionHeader`'s `trailing` slot is not the list scaffold's, and one control "the same
way" in two hosts means one implementation that carries its own rule. **With D2 reversed the scaffold changes
not at all**: its parameter stays non-null, its call stays where it always was, and the separator stays out of
its contract.

**D5 — One control, one home.** The shared composable in `ui/components`
([`MarkerCreateAction`](../../app/src/main/java/ykws/android/maro/ui/components/MarkerCreateAction.kt:1), the name the plan proposed and the build kept) draws a
40 dp icon-only button tinted `ButtonColors.icon` like its neighbours followed by the rule: the accent belongs
to a surface's own filled action, and an accent-tinted glyph would compete with the empty state's filled
`Create First Marker`. It takes only its click handler and reads its own `contentDescription` from one key, so
the two hosts cannot drift. Its glyph is the standalone vector
[`ui/icons/AddLocationAlt.kt`](../../app/src/main/java/ykws/android/maro/ui/icons/AddLocationAlt.kt:13) drawn directly — not the map's
[`AddLocationAltIcon()`](../../app/src/main/java/ykws/android/maro/ui/map/FanIconComponents.kt:140) wrapper, which stays the fan icons' own — so the
shared component leans on one map-package object (`ButtonColors`, already imported by the scaffold at
[`ListOverlayScaffold.kt:108`](../../app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt:108)) rather than two. That edge is not new:
[`ConfirmDialog.kt:54`](../../app/src/main/java/ykws/android/maro/ui/components/ConfirmDialog.kt:54) already reaches into `ui/map`. The button is the app's rank-2
shape — "an icon-only button belonging to a header or a card's chrome"
([`ui-component-guidelines.md:843`](../../docs/ui-component-guidelines.md:843), §5.9's tier table; not §5.6, which is `ConfirmDialog`)
— so no new component species is introduced.

**D6 — The menu's MARKERS header hosts the same action the same way** (the user's word, 2026-09-29, overturning
this plan's earlier D6, which kept the menu navigation-only). The action joins the head of that header's
`trailing` slot — left of the link · filter · reset group — and **outside** the
`if (markerFilterAxes.isNotEmpty())` gate ([`MenuDrawerOverlay.kt:374`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:374)), so creation never
disappears with the filters. The `Manage markers` row's trailing cluster is still left alone: its chevron means
*open the first marker* ([`MenuDrawerOverlay.kt:433`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:433)), and a create action must not sit beside a
navigate-in arrow.

**D7 — Both hosts reuse the existing create path, closing their own surface first.** The list's button calls
`onCreateFirst`, which already closes the overlay and seeds the wizard at the current map centre
(`MapScreen.kt:3308`); the menu's calls the same `onCreateFirst` the overlay layer already receives
([`OverlayLayer.kt:172`](../../app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:172)), wrapped as `{ onDismissMenu(); onCreateFirst() }`
beside the chevron's own pattern ([`OverlayLayer.kt:384`](../../app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:384)) — so `MapScreen` needs no change for the
menu host, as built. The order is the point: the menu is a panel over the map rather than a slot occupant, and
the wizard takes the selected-item dashboard's slot, so the menu must be dismissed before the wizard opens.
The same call closes whatever card stands, which is R1's own behaviour.

**D8 — No `ControlId` entry, no fan change.** The map stack is left exactly as it is: `ControlId`'s row is
untouched, and the layer fan beside the button stays at its declared six children
([`MapScreen.kt:4093`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:4093)), so nothing is smuggled into `FanConfig`.

## 3. Steps

**The build as it ran, 2026-09-29** (all done, and its two position-specific items now reversed):

1. `res/values/strings.xml` + `res/values-fr/strings.xml` — `cd_create_marker`, both locales. Done.
2. `ui/components/MarkerCreateAction.kt` — the shared control. Done. **Its child order is the amendment's.**
3. `ListOverlayScaffold.kt` — the `headerActions()` call moved to the cluster's end. Done, then **reverted** by
   the amendment: the call belongs where it was, and the comment added with the move goes with it.
4. `MarkerManagementOverlay.kt` — the action passed into the slot, wired to `onCreateFirst`. Done, unchanged by
   the amendment.
5. `MenuDrawerOverlay.kt` — the same action in the MARKERS `SectionHeader` trailing slot, outside the
   filter-axes gate, behind `onCreateMarker`. Done. **Its position inside the slot is the amendment's.**
6. `OverlayLayer.kt` — `{ onDismissMenu(); onCreateFirst() }`. Done, unchanged by the amendment.
7. Both docs consolidated, pointing at the component's KDoc. Done. **Their order sentence is the amendment's.**
8. `apk-build.bat` and the marker suites. Green. **To be re-run by the amendment.**
9. Device pass, the user's, still owed.

**The amendment, 2026-09-29** (the reversal of D2 — the same control, the same way, one position along):

1. `MarkerCreateAction.kt` — swap the two children so the button precedes the rule, in the amended order
   `create · rule · chrome group`, and restate the order rule in its KDoc accordingly; it is the rule's one
   home, so nothing else may carry it.
2. `ListOverlayScaffold.kt` — put the `headerActions()` call back at the head of the cluster and drop the
   comment that says it renders last; the parameter and the separator's ownership are untouched (D4).
3. `MenuDrawerOverlay.kt` — the action moves to the head of the `trailing` slot, still outside the
   filter-axes gate and still behind `onCreateMarker`.
4. `docs/ui-lists-guidelines.md` (§Header Row Icons) and `docs/ui-component-guidelines.md`'s menu sentence —
   their order wording follows the component, and neither restates what the KDoc owns.
5. Rebuild with `apk-build.bat` and re-run the marker suites; the tracks list and the menu's Tracks header are
   re-checked as unaffected.
6. The device pass stays the user's, and the narrow-width check (F10) is unchanged by the reversal — the
   cluster's width is the same set of controls in a different order.

## 4. Rejected

- **The trailing group's last child (the build's first position)** — the user's face for the action is the head,
  beside the label that names what is created; the outer edge belongs to the read-chrome group's term, reset.
  This was the plan's own earlier D2, reversed by the user's word.
- **Keeping the menu navigation-only** — the plan's earlier D6, overturned earlier the same session: the menu
  header is a markers surface too, and the same control there costs the list one tap less.
- **The `Manage markers` row's chevron cluster** — creation beside a navigate-in arrow (D6).
- **A FAB** — Material's default answer for a create-first list, but no `FloatingActionButton` exists anywhere
  under `app/src/main`, and the list is a full-screen drawer built on a documented scaffold; the header action
  is the analogue that fits.
- **A nullable `headerActions` slot with the scaffold drawing the separator** — withdrawn for the two-host
  shape (D4).
- **A `fillMaxHeight` rule** — it needs an intrinsic-sized row, which neither header has (D3).
- **The layer fan** — at its declared capacity of six children (D8).

## 5. Open

- The rule's **length in dp** is signed off at the built **24 dp**; thickness (1 dp) and side gap (6 dp) are
  tokened and precedent-set by `SectionRow`.
- The list header row's floor: the cluster carries the rule's 1 dp, its two 6 dp gaps and a 40 dp button on top
  of controls whose link toggle and reset are fixed at 40 dp
  ([`ListOverlayScaffold.kt:694`](../../app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt:694), `:716`) while the section label is a plain `Text`
  with no weight or ellipsis to yield ([`ListOverlayScaffold.kt:686`](../../app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt:686)). The narrow-width measure is
  what decides whether anything has to give; the reversal does not change the arithmetic.
- The create glyph's tint: the recommendation is the neighbours' colour (D5); an accent glyph is the one
  alternative worth a look on the device.
- A `VerticalRule` primitive extracted from `SectionRow` would give all three vertical rules one home; with two
  header rules and one section rule the count does not yet justify it, and `SectionRow` is left alone.
- The name `MarkerCreateAction` reads against the corpus's `ConfirmAction` / `ConfirmActionButton` split — the
  plan proposed it and the build kept it.
- `onCreateFirst` still says "first" while now serving three callers; the plan left the rename to the
  implementation and the implementation declined it.
- The map has no long-press gesture at all (only the double-tap recenter at
  [`MapControls.kt:275`](../../app/src/main/java/ykws/android/maro/ui/map/MapControls.kt:275)), so "hold the map to drop a marker" stands
  unclaimed. It is a new gesture on the pan detector rather than a new button and is **not** part of this plan.

## Review — 2026-09-29

Self-review in the challenge-shaped form: the reviewer wrote the plan, so this reads against it rather than as
independent confirmation.

### First pass

- **F1 · corrected — the separator's tokens were misread.** D3 called `uiDividerHeight` "the horizontal rule's
  thickness" and hedged a hairline; it is **1 dp** ([`AppConfig.kt:1197`](../../app/src/main/java/ykws/android/maro/config/AppConfig.kt:1197)), and `SectionRow` already draws a
  **vertical** rule at that width with `uiDividerGap` (6 dp) either side
  ([`SectionRow.kt:49`](../../app/src/main/java/ykws/android/maro/ui/components/SectionRow.kt:49),
  [`SectionRow.kt:60`](../../app/src/main/java/ykws/android/maro/ui/components/SectionRow.kt:60)). The item is cheaper than written, and its gap needs
  no device pass.
- **F2 · corrected — §5 repeated the same false claim.** The thickness and the gap are tokened; the one
  genuinely open value is the rule's length.
- **F3 · left to the implementation — `onCreateFirst` becomes a misleading name.** It now serves two hosts'
  buttons as well as the empty state. A rename is invisible to the user and would touch three files; it is not
  worth a plan step, and the build kept the name.
- **F4 · corrected — the plan was not registered.** The epic's `## Docs` now carries it and the front-matter
  date moved with it.
- **F5 · checked, no change — the multiselect branch.** The list's cluster sits inside `if (!isMultiSelectMode)`
  ([`ListOverlayScaffold.kt:680`](../../app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt:680)), so the create button leaves with the rest of
  the row while multiselect is on, which is the wanted behaviour.
- **F6 · the user's word reopened D6 and simplified D4 and D5.** With the menu's MARKERS header as a second
  host, the rule travels with the control instead of with the scaffold, one shared composable serves both, and
  the menu host needs no `MapScreen` change because `onCreateFirst` is already in the overlay layer's scope.

### Second pass — after the menu host

- **F7 · checked and dismissed — the dependency direction.** The worry was that a `ui/components` action
  reaching `ButtonColors` in `ui/map` would invert layering. It does not: the scaffold imports it already
  ([`ListOverlayScaffold.kt:108`](../../app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt:108)) and [`ConfirmDialog.kt:54`](../../app/src/main/java/ykws/android/maro/ui/components/ConfirmDialog.kt:54) already
  reaches into `ui/map`. One refinement followed instead: the action draws the standalone vector rather than the
  map's `AddLocationAltIcon()` wrapper, so the fan's icon file keeps its single caller (D5).
- **F8 · corrected — the rule's length was written as a taste call, and it is a layout fact.** A
  `fillMaxHeight` rule reads the incoming maximum; `SectionRow` gets away with stretching only because its row
  is `.height(IntrinsicSize.Min)` ([`SectionRow.kt:48`](../../app/src/main/java/ykws/android/maro/ui/components/SectionRow.kt:48)), and neither header row declares it.
- **F9 · corrected — the steps were in an unbuildable order.** The shared component reads the new CD key, so
  the strings step led nowhere at position six; it became step 1.
- **F10 · raised as a watch item — the list row's floor.** The cluster's minimum grows by about 53 dp, and the
  section label has no weight or ellipsis, so the row has a floor it cannot shrink below; the narrowest
  supported width joins the device pass.
- **F11 · checked, no change — the menu's dismissal order.** The wrapper dismisses the menu before creating,
  which the chevron's own pattern already does; it matters because the menu is a panel over the map while the
  wizard takes the dashboard slot.

### Third pass — the build's Ask hop, verdict **ship**

- **Coverage: pass, item for item.** One composable with exactly two host call sites, the menu's copy outside
  the filter-axes gate, the rule a fixed length and never a fill, the glyph the standalone vector, the wiring
  dismissing before creating with `MapScreen` unchanged, and the tracks list verified unaffected by reading
  `TrackHistoryOverlay.kt:378`'s empty slot.
- **F10 at build time: no worse than assumed.** The cluster grew by exactly the projected 53 dp and no more,
  and the label wraps rather than ellipsising — the row degrades by squeezing controls, not by cutting text.
- **S · corrected — the list doc restated what the component owns, and disagreed with it**, naming the chrome
  group as "filter, sort and reset" where the KDoc says link · filter · reset. Its sentence now keeps only what
  the list's own surface owns.
- **S · corrected — the doc's pointer landed on an `import`** (`MarkerCreateAction.kt:20`); it targets the order
  rule in the KDoc now.
- **S · corrected — this plan's own citation mispointed**: it credited §5.6 at `:841`, where the rank-2 row is
  §5.9's tier table at `:843` and §5.6 is `ConfirmDialog`.
- **The Ask hop's own objection:** the strongest case against shipping was that the only reference a future
  caller reads restated and contradicted its one home — a defect one doc sentence away from fixed, which is why
  the two should-fixes were folded rather than parked.

### Amendment — the user's word, 2026-09-29

- **D2 reversed, and it supersedes the third pass's position item.** The create action belongs at the head of
  the trailing group, beside the section label that names what it creates; the outer edge stays the read-chrome
  group's term. The build's last-child rendering, the third pass's coverage of it, and the plan's own earlier
  reasoning for the outer edge all stand superseded by that word.
- **The reversal makes the change smaller, not larger:** `ListOverlayScaffold` returns to its unmodified order
  (D4's "the scaffold only reorders" becomes "the scaffold does not change"), and only the component's two
  children, the menu's position inside its slot, and the two docs' order wording move.
- **What does not change:** the rule's shape and 24 dp length, the glyph and tint, the wiring, the gate, the
  row's width floor, and every F-item above that is not about position.
- **The record debt the reversal creates:** the epic's `## Implemented` entry describes the build's position and
  must be corrected in the same pass, and the Outcome below must state the final state rather than the first.

## Outcome

Shipped 2026-09-29 on `feature/add-marker-trigger`, from the Ask hop's **ship** verdict, then amended the same
session by the user's word — the action moved from the trailing group's end to its head.

- **As built, in the plan's original order:** `cd_create_marker` in both locales; the shared
  [`MarkerCreateAction`](../../app/src/main/java/ykws/android/maro/ui/components/MarkerCreateAction.kt:1) (rule + 40 dp icon-only button, its KDoc owning the
  order rule); `ListOverlayScaffold`'s slot call moved to the cluster's end; `MarkerManagementOverlay` passing
  the action to `onCreateFirst`; `MenuDrawerOverlay`'s MARKERS header taking the same action outside its
  filter-axes gate behind `onCreateMarker`; `OverlayLayer` feeding it as `{ onDismissMenu(); onCreateFirst() }`;
  both docs consolidated pointing at the KDoc.
- **The amendment's final state:** the button precedes the rule, the list's slot call is back at the head of the
  cluster, the menu's action sits at the head of its `trailing` slot, both docs state the amended order and
  point at the KDoc, and the epic's `## Implemented` entry carries the amended position.
- **`MapScreen.kt` needed no change**, as D7 predicted, and the empty state, multiselect branch, map stack,
  `ControlId`, `FanConfig`, tracks list and wizard steps are untouched.
- **The rule is 24 dp**, carried as a file-private const — under the 28 dp glyph band so it reads as a divider
  in a control row, and well under the 40 dp buttons so it never drives the row's height.
- **Deviations:** the position reversal above, which is the user's change of mind rather than a build deviation;
  otherwise none in substance, with three comment-level touches beyond the plan's letter.
- **Build and tests:** `apk-build.bat` BUILD SUCCESSFUL with the APK packaged; the marker-filtered unit run
  reported 7 suites, 51 tests, 0 failures — a regression guard only, since no JVM suite can reach a Compose
  composable, so the new control's proof is the build and the device.
- **Owed, the user's:** the device pass over both hosts — the empty and populated list at 360 dp portrait, in
  landscape and at the narrowest supported width, the 24 dp rule against the 40 dp icons, the accent
  alternative for the tint, and the create landing at the map centre.
