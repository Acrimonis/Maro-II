<!-- scope: feature -->
# Route — the section under Navigation, the roller's row, and the app's action-versus-selection model

**What this delivers.** Three surfaces in one pass, in this order of blast radius: the Route section stops
being a top-level section with a card of its own and becomes a **sub-section inside the Navigation card**;
the roller's row **stops spending half its width on a title** so a marker's own name fits; and the app's
**actions and its selections stop wearing the same face** — the accent fill stays the action's, and a
selected state moves to a tonal container with a state marker, on Material 3's own division of labour.

**It adds no requirement, and it amends one only if that requirement already speaks.** The requirements'
home is [`260922_FEAT_PLN_Route_ask-policy-and-target-validity.md`](260922_FEAT_PLN_Route_ask-policy-and-target-validity.md)
and it still wins any conflict. **R44 needs no amendment**: it already reads "a Route section under
Navigation holding a start selector, a destination selector and one action", so the requirement was right
and the **build** rendered it as a top-level section — this pass corrects the code, not the book. **R70 is
sharpened only where its own line already names the row's title**, which cannot be read from here: that
line is `:101` of the book and it comes back truncated, so the build checks it first and either sharpens
the clause or leaves the caption to `docs/ui-component-guidelines.md` §2.15 alone — **one home, never
two**. The other two amendments are named in §2 and §7 and belong to the delivery: the drawer's summary
gate, narrowed to the routing phase, and R68's alternative line, stranded by that narrowing. It shares its
ground with [`260928_FEAT_PLN_Route_ui-flow-and-candidate-routes.md`](260928_FEAT_PLN_Route_ui-flow-and-candidate-routes.md),
which is the record of the run that built R44–R74; nothing there is re-opened.

**The action and selection rules are not Route's.** They belong to `docs/ui-component-guidelines.md`,
which is their one home, and the two components they touch — `SegmentedRow` and `MultiSelectRow` — are
shared. Route is where the collision was first seen, not where it is fixed.

## 1. Decisions taken (the user's word, 2026-09-28)

- **D1 — mimic Material 3.** The filled button keeps the **primary role** (this app's `ui.accent`), and a
  **selected** state takes a **tonal container** plus a **state marker** — M3 gives a selected FilterChip
  and a SegmentedButton's selected segment `secondaryContainer` and a check glyph, and gives the filled
  Button `primary` with no glyph. The app adopts that split rather than inventing one.
- **D2 — the Route section is a sub-section of Navigation.** The drawer's first section really is titled
  **Navigation** (`settings_section_position`, [`strings.xml:27`](../../app/src/main/res/values/strings.xml:27)),
  and the plan's own §2 already said the section joins the drawer *under Navigation*.
- **D3 — the roller's row drops its title** and a compact cue takes its place, so the value gets the row.
- **D4 — normalisation happens at the shared-component level**, so it reaches every call site without
  touching one. Verified: `SegmentedRow` is rendered at `MapScreenSettingsOverlay.kt:455`, `:1410` and
  `:1528`, and `MultiSelectRow` at `MenuDrawerOverlay.kt:310` and `RoutingCostStep.kt:77` — five call
  sites, two components, no hand-rolled twin anywhere in `app/src`.

- **D5 — the trigger closes the menu, and the menu carries no acquisition status.** The section's action
  arms and the drawer shuts with it, so the panel — its status, its candidate rows and its three actions —
  is what the user lands on; the acquiring word and the engine's stage stand there and nowhere else, and
  the summary stands in the **routing phase alone** (the user's word, 2026-09-28).
  **Amended 2026-09-28, later the same day: D5 generalises.** `armRouteMode` lost its `openDrawer` parameter,
  so the map's square stops opening the drawer too and the two doors behave alike — no door differs any more,
  and the panel is what a press lands on either way. See
  [`260928_FEAT_PLN_Route_acquisition-face-and-map-order.md`](260928_FEAT_PLN_Route_acquisition-face-and-map-order.md).

## 2. The Route section moves into the Navigation card, and the trigger closes the menu (D2, D5)

- **Today**: a top-level `SectionHeader(strings.settings_section_route)` at
  [`MenuDrawerOverlay.kt:192`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:192),
  its own `CardArea` at `:196`, and `RouteEndsSection` inside it (`:486`) — a full title and a full card.
- **Wanted**: the two rollers and the arm action live **inside the Navigation card**, under a
  `SubSectionHeader` — §2.9's 16sp SemiBold `uiTextPrimary` header, the rung for a titled sub-section
  inside a card ([`ui-component-guidelines.md:308`](../../docs/ui-component-guidelines.md:308)).
- **Order inside that card**: GPS mode and auto-show → the summary block where it already stands
  ([`MenuDrawerOverlay.kt:180`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:180),
  unchanged and still conditional) → `SectionDivider` → the sub-section header → the two rollers → the
  arm action. The section stands whether or not a route runs; what gates stays the summary.
- **`SubSectionHeader` is not shared yet** — it is `private` inside
  [`MapScreenSettingsOverlay.kt:1790`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:1790) —
  so it **moves to `ui/components`** beside `SectionHeader` and both callers read the one function.
  That extraction is the only structural change this workstream needs.
- `settings_section_route` either becomes the sub-section's title or is retired, and
  [`ui-drawer-guidelines.md:274`](../../docs/ui-drawer-guidelines.md:274) §8a is rewritten to match.
- **The trigger closes the menu** (D5): the section's action arms the acquisition and the drawer shuts with
  it, so the panel is what the user lands on. Today `arm()` sets `showTrackDrawer = true`
  ([`MapScreen.kt:1801`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1801)) — right for the
  map's square, where the drawer is shut and the arm opens it, and wrong from the drawer's own action,
  where it buries the outcomes behind the parameters. The fix arms **without** opening and closes the
  drawer on the trigger.
- **The acquisition carries no status in the menu**: the summary's gate drops the searching arm and reads
  the followed route ([`MapScreen.kt:2457`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2457)),
  so the acquiring word and the engine's stage stand on the panel alone. This **narrows** the gate finding
  the previous run left open rather than widening it.
- **The contradiction this creates, settled by the user's word:** R68's alternative line — a candidate's
  saving reported above the drawer's rows — is acquisition-time information, and **acquisition information
  lives in the dashboard alone**. With the menu shut on the trigger and the summary standing only while
  routing, nothing can reach that line; the panel's candidate rows already say it (R62, R74), so **R68's
  drawer half is withdrawn**, and the record step writes the withdrawal rather than proposing it.

## 3. The roller's row (D3)

- **Today** the row is a `Row` of two equal weights: the label takes half the drawer's width and the
  value the other half, so a marker's own name is ellipsised at half the panel
  ([`RollerRow.kt:122`](../../app/src/main/java/ykws/android/maro/ui/components/RollerRow.kt:122)).
- **The cue, compact**: the label becomes a **short fixed caption on the same line**, measured at its own
  width (`wrapContentWidth`) with a small gap, and the **value takes the rest of the row** — `From` / `To`
  in English and their own pair in French, replacing the `Start` / `Destination` keys, whose second word
  is what actually eats the space.
- **Unchanged**: the one-row-tall clipped viewport, the marked entry in accent and bold, the drag claimed
  inside the row's bounds, the spring on lift and the commit-on-lift. `RollerRow`'s signature is
  untouched, so the Route section is the only call site that changes a string.
- **The cue was the open point**, since the label was also the only thing telling the start roller from
  the destination one. The chosen answer is the caption rather than nothing, because the entries do not
  distinguish the ends — `Marker position` is offered by both (R46).
- **Objection, stated**: a caption still costs about thirty dp on a narrow landscape panel, and `From` /
  `To` is a shade less explicit than `Start` / `Destination`; the alternatives were a caption line above
  each roller (costs vertical space instead) and no cue at all (costs clarity).

## 4. The action model — a guidelines authority (D1)

- **What exists today is one family and no taxonomy.** §5.6
  ([`ui-component-guidelines.md:628`](../../docs/ui-component-guidelines.md:628)) owns the button family —
  `ConfirmAction(label, role, enabled, onClick)` rendered by `ConfirmActionButton` — with a role model
  worth keeping as the spine: accent = the surface's own forward outcome and only one per surface, red =
  what withholds the work, outline = everything that neither writes nor loses, order affirmative →
  neutral → destructive, and the disabled face is the outlined one.
- **§1's decision flow answers only input controls**
  ([`:13`](../../docs/ui-component-guidelines.md:13)): toggle, slider, segmented, multi-select, dropdown,
  range — no action at all. That branch is missing and is why five shapes live side by side.
- **The five shapes**, against which the authority is written:

| Tier | Shape | Where it stands | Home |
|---|---|---|---|
| 1 | accent filled, red filled, or accent outlined full-width button | dialogs, the route panel, the Route section's arm action | `ConfirmActionButton` |
| 2 | bare label + icon tap row, no surface | the menu's Import/Export pair ([`MenuDrawerOverlay.kt:332`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:332)) | none — hand-rolled |
| 3 | icon-only button, 40–48 dp | section headers (link, filter reset, gear), chevrons | `IconButton` |
| 4 | map status square acting as a button | GPS, tracking, lock, recenter | `MapToggleSquare` family |
| 5 | row-level action | swipe, chevron gutter, header trash, Undo / Clear / Select-all | per list |

- **Work**: a new authority entry (§5.9) naming those tiers and when each is used; **tier 2 promoted to a
  shared `ActionRow`** that the Import/Export pair and any later utility action draw; and §1's flow given
  its **actions branch**.
- **What is not touched**: the route panel's two grids keep their own orders, which §5.6 already fixes
  (R16, R17).

## 5. Actions versus selections (D1, D4)

- **The collision is exact.** [`SegmentedRow.kt:61`](../../app/src/main/java/ykws/android/maro/ui/components/SegmentedRow.kt:61)
  and [`MultiSelectRow.kt:56`](../../app/src/main/java/ykws/android/maro/ui/components/MultiSelectRow.kt:56)
  paint a **selected half** with `uiAccent` as a fill, and
  [`ConfirmDialog.kt:340`](../../app/src/main/java/ykws/android/maro/ui/components/ConfirmDialog.kt:340)
  paints the **primary action** with `uiAccent` as a fill: one fill, two meanings, both rounded rectangles
  with bold labels.
- **The rule that resolves it**: **the accent fill is reserved for an action** — one per surface, per
  §5.6 — and a **selection wears a marker instead of the surface's fill**, in one of the three permitted
  faces:

| Select face | Where it applies | Example shipped today |
|---|---|---|
| tonal container + check glyph | a connected group of choices (exclusive or independent) | the face §2.7 / §2.7b gain in this pass |
| the value in `ui.accent`, bold, no fill | a value read-out over a set the user steps | `RollerRow` ([`:159`](../../app/src/main/java/ykws/android/maro/ui/components/RollerRow.kt:159)) |
| accent border + check glyph, no fill | a card or row picked out of a list | `ListOverlayScaffold` ([`:336`](../../app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt:336), [`:886`](../../app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt:886)) |

- **One new token**, and one home per fact: `ui.select.container` in
  [`colors.properties`](../../app/src/main/assets/colors.properties) as the accent at a low alpha (the
  `ui.card.background=#33FFFFFF` shape already in that file), read through `AppConfig`, with its row added
  to the palette table in [`color-scheme.md:343`](../../docs/color-scheme.md:343). The check glyph takes
  `ui.accent`; the label keeps `ui.text.primary` bold, following §2.9's own precedent that headings and
  labels are white and hierarchy comes from weight and spacing.
- **The two component edits** (this is the whole trickle-down): a selected segment and an on half move
  their background from `uiAccent` to `ui.select.container` and gain a check glyph in `ui.accent`. The
  glyph needs the cell's content to become **one centred group** — an icon-then-label `Row` inside the
  weighted `Box`, not a glyph placed beside the existing `Text`
  ([`SegmentedRow.kt:58`](../../app/src/main/java/ykws/android/maro/ui/components/SegmentedRow.kt:58)) — or
  the label shifts off centre, and it carries **no `contentDescription`**, since the semantics already
  announce the state and a second announcement would say it twice. Their outlines, geometry,
  `selectableGroup()` / `toggleable` semantics and roles stay exactly as they are — the semantics were
  already right, and the visual now follows them.
- **Binary controls keep the accent**, deliberately and by the same standard: M3 gives a selected `Switch`
  **and** a selected `Checkbox` the `primary` role, so `ToggleRow`'s `uiAccent` thumb and track and
  `OptionRow`'s `checkedColor = uiAccent` checkbox are consistent rather than exceptions, and this pass
  touches neither. The rule added here is therefore narrower than "the accent fill is for actions": it is
  **the accent as a filled container on a group of choices** that is reserved, which is exactly what the
  two components give up.
- **What is named, not changed**: the list overlays' border-and-check idiom already is the M3 shape, so it
  is cited as the third permitted face and left alone.
- **Objection, stated and accepted**: the two component edits re-tune every settings tab (three segmented
  and two multi-select call sites) for a problem met in one drawer section. The narrow alternative —
  leave selections alone and make the action unmistakable — was declined, because it leaves one fill
  meaning two things, and the component-level change is what makes D4 true.

## 6. Strings and tokens

- **New**: the two short end captions — English `From` / `To`, French `De` / `Vers` — and the
  sub-section's title if `settings_section_route` is retired rather than re-used. The check glyph carries
  **no** `contentDescription`: it is decorative beside a label Compose already announces as selected.
- **Retired**: `route_end_label_start` / `route_end_label_destination` leave with the long words, and
  `settings_section_route` leaves if the sub-section takes a new title. Both locales are trimmed in the
  same pass — a key that exists in one locale and not the other is the defect this rule prevents.
- **Token**: `ui.select.container`, as §5. No other key moves.

## 7. Build order

1. **The token** — `ui.select.container` in `colors.properties`, the `AppConfig` accessor, its row in the
   `color-scheme.md` table, and **one test pinning the accessor's default** — the habit every other
   `AppConfig` value already follows, the fine-cell ratio being the precedent. Everything else in this pass
   is drawing and composition, so it is a device judgement rather than a test.
2. **The two components** — `SegmentedRow` and `MultiSelectRow` take the tonal container and the check
   glyph, their KDocs rewritten to state the rule they now follow.
3. **The roller's row** — the short caption with `wrapContentWidth`, the value taking the row, the two
   locales' captions. Nothing else in the control moves.
4. **The sub-section and the trigger** — `SubSectionHeader` extracted to `ui/components`, the Route section
   moved inside the Navigation card, the top-level header, its `CardArea` and its spacer deleted; the
   section's action arms **without** opening the drawer and **closes** it while the map's square keeps
   opening it; and the summary's gate narrows to the routing phase.
5. **`ActionRow`** — promoted from the Import/Export pair, **the one call site's two rows** re-rendered
   through it, geometry and tints unchanged.
6. **The guidelines** — the new §5.9 action authority, §1's actions branch, §2.7 / §2.7b updated to the
   new selected face, §2.15's row description, and `ui-drawer-guidelines.md` §8a rewritten. They move
   **with the code**, not with the plan, because they describe what the app is rather than what it will be.
7. **The record** — the master book gains **no R44 amendment**, because the requirement already says "under
   Navigation" and the build was what missed it; it gains an **R70 sharpening only where that row's own
   line names the row's title** — checked first, since `:101` comes back truncated and a patch against it
   fails the way the epic's `## Implemented` line did — and it **withdraws R68's drawer half** with the
   line that can no longer be reached
   ([`260922_FEAT_PLN_Route_ask-policy-and-target-validity.md`](260922_FEAT_PLN_Route_ask-policy-and-target-validity.md)).
   The epic gains its `## Docs` pointer to this plan beside its rules line
   ([`FEAT_DSC_Route.md`](FEAT_DSC_Route.md)). All of it moves **with the delivery**, which is where the
   previous plan's own R44–R74 landed.
8. **Build** — `apk-build.bat` green, the route-filtered suites green.

## 8. What this pass does not do

- **The roller's over-drag** — the drag still accumulates past either end and shows an empty row until
  the thumb lifts; named by the previous run and left standing, unasked.
- **The missing tap path on the roller** (the drag-only accessibility gap), **the trigger reading the pair
  inside a coroutine launch**, and **the epic's `## Implemented` line** — all three stand exactly where the
  previous report left them; the **summary's gate** is no longer among them, §2 having answered it by
  narrowing rather than widening.
- **Phase 6's device pass** stays owed and stays the user's.
- **No requirement is added**, no engine behaviour changes, and no git write, device touch or deploy
  happens in this pass.
- **No document other than this plan is written by the planning stage.** The guidelines describe the app
  **as it stands**, so they move with the code (step 6); the master book's two amended clauses and the
  epic's pointer move with the delivery (step 7); the hydration is `#bake`'s and wants nothing from here.
