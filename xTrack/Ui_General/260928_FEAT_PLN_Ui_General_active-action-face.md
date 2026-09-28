<!-- scope: feature -->
# Ui_General — the active action's face: the accent marks what can be taken

**What this delivers.** One rule for the faces a button wears, and the code change that makes it true: an
action that **can be taken** is marked by the **accent** — filled when it is the surface's own outcome,
**outlined in the accent** when it is a neutral door — and an action that **cannot be taken** wears
`uiDividerColor` with a muted label. Today the neutral door and the disabled action are both drawn as a
faint outline, so the two states are told apart by the quietest signal the surface has.

**It changes no behaviour and adds no requirement.** The button family, its three roles and its call sites
are §5.6's own; this pass corrects the two sentences that describe the disabled face and the outline's
scope, and the one border colour that made the two faces near-twins.

**Rev 7 (2026-09-28)** settles the middle face, and it is a fill **with its own rim**: the background is the
accent at **50 %** (`ui.action.neutral.background` = `#801565C0`) and the button carries a **2 dp `ui.accent`
border at full opacity**, the primary's white bold label unchanged. The half-strength body states the rank
below the primary; the full-opacity rim — accent against the disabled face's 1 dp `uiDividerColor` — states
that the control can be taken, and a rim is what the borderless 66 % fill of Rev 6 lacked when it read as
another species. Docs and record moved with it.

**Rev 6 (2026-09-28)** is the last word on the face, and it is a fill: **the middle action is a full action
button whose background is the accent at 66 %** (`ui.action.neutral.background`), carrying the primary's own
white bold label and no border — the user's word being that every outline tried that day stayed too subdued.
The fill's weight states the rank (full accent, 66 %, then no fill at all for the disabled face), and 66 % is
nearly opaque, so the white label never rides on the map behind the translucent card. The 2 dp outline and
its rule are withdrawn, and §5.6, §5.9, `color-scheme.md` and §8 of the drawer guidelines moved with this
face.

**Rev 5 (2026-09-28)** is the user's word on the three things the day's faces left wrong, and it withdraws
two of this plan's own moves rather than adding a fourth face:

- **The neutral door is the accent outline again, at 2 dp**, its label bold `uiAccent` — colour *and* weight
  apart from the disabled face's 1 dp `uiDividerColor` — which makes a live door prominent without a second
  hue. `ui.action.tonal` is **deleted** with its accessor, its parse line and its `color-scheme.md` row,
  having lost its only reader.
- **One bar is the norm for any set of choices, multiple included:** Rev 4's separate pills are
  **withdrawn**, `MultiSelectRow` is again §2.7's connected control — the language selector's own shape —
  and the two controls differ in what they **announce**, never in what they draw.
- **The Tracks card's `Display Tracks with:` group stands open**, its collapse withdrawn the same day: a
  group reached every time is a sub-section, not a disclosure — the state, its `rememberSaveable` and the
  `Expander`/`NestedCard` imports leaving with it.

**Rev 2 (2026-09-28)** folds the challenge this plan was read against: the citation moves to the rule that
exists — `:682`, with `:681`'s silence named as the defect itself — the pair's mechanism is **read** rather
than assumed (`ListAction` dispatch, `OpenDocument`), the §5.9 step is stated as a no-op in wording, the one
question the gates cannot answer is named, and the Route plan that exposed the pair is pointed at.

**Rev 3 (2026-09-28)** re-faces the neutral door at the user's word: the accent **outline** read too faint on
a card, so `SECONDARY` wears a **body** instead — `ui.action.neutral.background`, the control family's own
navy one weight lighter, its label in `ui.accent`, no border — which tells a door from a *selection* by
**kind** (an opaque surface, not a tint that lets the map through) rather than by percentage, and leaves the
bare outline meaning the disabled face alone. One palette key, and §5.6's three sentences, §5.9's tier row,
`color-scheme.md` and §8 of the drawer guidelines moved with it.

## 1. The reading, with what the code says today

- **One renderer, four faces.** [`ConfirmActionButton`](../../app/src/main/java/ykws/android/maro/ui/components/ConfirmDialog.kt:322)
  draws `PRIMARY` and `DANGER` as filled buttons with white bold labels, `SECONDARY` as
  `OutlinedButton(onClick, modifier, shape)` whose label is `uiAccent` — and the **disabled** action as
  `OutlinedButton(enabled = false, border = BorderStroke(1.dp, uiDividerColor))` with an `uiTextMuted`
  label.
- **The near-twin is the defect.** `SECONDARY` passes no border of its own, so it takes Compose's M3
  default — a faint on-surface white — while the disabled face takes the app's own `uiDividerColor`.
  Those are two quiet members of one family, and it is the same family §2.7 already spends on a *neutral*
  segment's outline, so the outline alone cannot say which of the two an action is.
- **The rule fixes the disabled face's outline and never the enabled one.** §5.6
  ([`ui-component-guidelines.md:682`](../../docs/ui-component-guidelines.md:682)) states the whole disabled
  face — *"its label in `uiTextMuted` and its outline in `uiDividerColor`, and no accent surviving it"* —
  while the line that says what an *enabled* `SECONDARY` wears
  ([`:681`](../../docs/ui-component-guidelines.md:681)) stops at *"`OutlinedButton` with a `uiAccent`
  label"* and names no border. **The absence is in the rule, not in a token**: nothing forbids the accent
  outline and nothing requires one, which is why the defect is a hole rather than a wrong instruction.
- **And the outline's own definition is the disabled one's.** The role bullet
  ([`:693`](../../docs/ui-component-guidelines.md:693)) reads *"the outline is everything that neither
  writes nor loses"*, which covers the disabled face and, read strictly, denies the neutral door — a
  reading raised by the user's word on 2026-09-28.
- **What the pair actually does, read rather than assumed** (2026-09-28): Export is
  `ListAction.BatchExportGpx` over every track
  ([`OverlayLayer.kt:414`](../../app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:414)) and
  Import is `ListAction.ImportTracks` ([`:413`](../../app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:413))
  opening a system document ([`MapScreen.kt:1059`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1059),
  `ActivityResultContracts.OpenDocument`) — **neither act is the drawer's own work**, both being the track
  list's, which the drawer only opens a door onto. That is a steadier argument for the neutral role than
  the one this pass first wrote.
- **What ships today, in the drawer.** The Menu's Import/Export pair
  ([`MenuDrawerOverlay.kt:346`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:346))
  is the first neutral pair to wear the role, so it is the surface that shows both defects at once.
- **Where the rule lives.** §5.6 and §5.9 of [`ui-component-guidelines.md`](../../docs/ui-component-guidelines.md)
  are their one home; the `route-dialogs` section of [`FEAT_DSC_Ui_General.md`](FEAT_DSC_Ui_General.md)
  owns that doctrine, which is why this workstream belongs to this feature rather than to the drawer that
  exposed it.
- **What exposed it.** The Menu's Import/Export pair, shipped in the outlined role on 2026-09-28 by step 6
  of [`260928_FEAT_PLN_Route_drawer-refinements.md`](../Route/260928_FEAT_PLN_Route_drawer-refinements.md:79) —
  the first neutral action to wear the role, and the surface on which both defects are visible at once.

## 2. The rule

- **The accent marks what can be taken; the divider marks what cannot.** Filled for the surface's own
  outcome, **accent-outlined** for a neutral door, red for the action that withholds the work, and
  `uiDividerColor` with a muted label for an action that is disabled — one reading that never rests on a
  label's colour to carry a state.
- **The outline's scope is corrected with it:** a neutral door is *whatever is not the surface's own
  outcome and not a loss*, whether it writes elsewhere or not.
- **What it costs:** one `BorderStroke` in the shared component, one sentence of §5.6, one clause of
  §5.9. No token is added, no palette entry, no dependency.

## 3. Steps

- [x] **1 · The component** — **delivered 2026-09-28**: `ConfirmActionButton`'s `SECONDARY` branch takes an explicit
  `BorderStroke(1.dp, Color(AppConfig.uiAccent))`, so an enabled neutral door is outlined in the accent;
  the disabled branch is left exactly as it is, and the KDoc states the pair of faces.
- [x] **2 · §5.6** — **delivered 2026-09-28**: the role bullet gains the enabled/disabled split and the corrected outline scope; the
  disabled-face sentence keeps its *a greyed button promises nothing* reading and now says the
  divider-and-muted pair is the whole of the disabled signal.
- [x] **3 · §5.9** — **nothing to do, and left untouched**: tier 1's row **already** reads *accent-outlined* (2026-09-28), so the wording does not
  move and the retired-tier note stays with it: this pass makes the row true rather than restating it, and
  a reader following it to §5.6 finds the border named there for the first time.
- [x] **4 · `ui-drawer-guidelines.md` §8** — **delivered 2026-09-28**: the Import/Export bullet names the accent outline rather than
  "the outlined role".
- [x] **5 · Gates** — **delivered 2026-09-28**: `apk-build.bat` green and `:app:testDebugUnitTest` green. **The one question the
  gates cannot answer** is whether a 1dp `ui.accent` line reads as an outline against `uiCardBackground` —
  20 % white over the map — which is the same legibility question the selected-face pass answered for the
  tonal container; the device look is asked for **that and nothing else**.

**Rev 4 (2026-09-28)** answers the pair's last two objections with one model rather than a fourth face: the
action family is **one hue with four weights** — filled, **filled tonal**, outlined, text — and a connected
bar stops carrying independent flags, because **connected means one of them and apart means any of them**.

- **The pair is filled tonal:** `SECONDARY` becomes a filled `Button` on a new key `ui.action.tonal` — the
  accent at **25 %** (`#401565C0`) — with its label in `uiTextPrimary` and **bold**, exactly as `PRIMARY`'s
  is: one hue, one label, one weight down, so the two read as one family. The navy body
  (`ui.action.neutral.background`) is **deleted** with its accessor, its parse line and its `color-scheme.md`
  row, that hue having been the chrome family's rather than this one's.
- **The multi-select becomes chips:** `MultiSelectRow` keeps its signature and loses its connected bar — one
  **pill per flag**, 8 dp apart, `RoundedCornerShape(percent = 50)`, each `toggleable(Role.Checkbox)` with
  no `selectableGroup()`. Off is an outlined pill (1dp `ui.divider.color`) with an `uiTextMuted` label; on
  is the face the app already has for a taken choice — `ui.select.container`, a 1dp `uiAccent` border and a
  check glyph in `ui.value.text` — with its label in `uiTextPrimary`. **The fill is deliberately shared**
  with a `SegmentedRow`'s selected segment: shape and grouping carry the meaning, never the fill.
- **The docs owe the two sentences:** §2.7b's shape rule, §5.6's `SECONDARY` face and its disabled
  sentence, §5.9's tier-1 row and its *selections are not actions* bullet, §8 of
  `ui-drawer-guidelines.md`, and the `ui.select.container` comment, which now names a **chip** rather than a
  `MultiSelectRow` half.
- **What is not done:** the accent fill and its role in the drawer are untouched, no requirement is added,
  and the shared fill's boundary against a selection is stated once, in §5.9.

**Ask's findings, folded as a note rather than a fix** (2026-09-28): the tonal key is a **25 %-alpha** fill,
so unlike `PRIMARY`'s opaque `uiAccent` its ground is whatever the map puts behind the translucent card and
its white label can wash out over bright water — **if the device shows that, the key is made opaque by
blending the accent into the app's dark chrome, a muted navy-blue of the same hue, and never by changing the
hue or dimming the label.** Coverage: both `MultiSelectRow` call sites are chips now, the second being the
wizard's route-role pair in a ~44 %-wide column, where whether `Destination` still fits whole is the device
look's second question — content-hugging pills or shorter labels being the fix, not a return to the bar.
Code health: the rewrite is self-contained with its signature unchanged, and the equal-halves weighting is
the one judgement call (three flags would be thirds, which reads fine). The earlier hop's should-fix still
stands untouched: `MapScreen.kt:91`'s unused `OutlinedButton` import.

**Delivered 2026-09-28 in one `#implement` run.** The Ask hop covered the whole app and found no blocker:
`OutlinedButton` now exists in [`ConfirmDialog.kt`](../../app/src/main/java/ykws/android/maro/ui/components/ConfirmDialog.kt:329)
alone, so every outlined action in the tree — the ladder's dialogs, the route panel, the marker wizard's
footer and the drawer's Import/Export pair — wears the accent, and no surface is left on the old dim face.
Four hand-rolled filled `Button`s remain by design, being chrome and screen-specific controls the doctrine
does not reach ([`CoastlineMapView.kt:161`](../../app/src/main/java/ykws/android/maro/ui/map/CoastlineMapView.kt:161),
[`MapControlButton.kt:27`](../../app/src/main/java/ykws/android/maro/ui/map/MapControlButton.kt:27),
[`MapScreenSettingsOverlay.kt:1693`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:1693),
[`MarkerManagementOverlay.kt:218`](../../app/src/main/java/ykws/android/maro/ui/map/MarkerManagementOverlay.kt:218)).
Its code health: the two outline branches keep a state out of a role switch, which reads better than folding
them; the `1.dp` stroke is stated in both and can drift, a low optional lift to one constant. **Its one
should-fix, left open:** [`MapScreen.kt:91`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:91)
imports `OutlinedButton` and never uses it — dead since before this pass, and one line to remove.

**And re-faced the same day (Rev 3)**, the accent outline having proved too faint on the card: the neutral
door now wears a body, and steps 1, 2, 3 and 4 above are read with that in place — the face is
`ui.action.neutral.background` with a `uiAccent` label and no border, and the bare outline is the disabled
face's alone. Gates re-run green after it — **and Rev 4, above, replaced that body with the tonal accent
and turned the multi-select into chips**, both withdrawn faces staying in the record for their reasons.

## 4. What this pass does not do

- **It does not fill the Import/Export pair.** The accent stays one per surface: the pair is a neutral
  door, and two fills on one card is what that rule refuses — settled 2026-09-28 and re-opened by nobody.
- **It adds no test.** The project carries no instrumentation harness, so a colour a Compose button wears
  cannot be asserted from a unit test; the pin is §5.6's sentence, and the face itself is one device look.
- **It leaves the selections' accent border alone**: §5.9's *selections are not actions* still holds, and a
  card or row picked out of a list keeps its accent border, its check glyph and no fill.
- **No new token, no dependency, no device work, no git write.**

## 5. The objection, named

- **An accent outline is close to a selection's mark**, which is also an accent border with no fill. What
  separates them is everything else about them: an action is a **button** carrying a label, a selection is
  a **row or card** carrying a check glyph on its own surface, and no surface draws both in one row.
- **The alternative was declined:** giving a neutral action a tonal fill (the accent at 30 %,
  `ui.select.container`) would collide harder, that being literally the face a selected segment or chip
  already wears.
