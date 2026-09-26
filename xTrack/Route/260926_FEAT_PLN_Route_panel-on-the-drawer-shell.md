<!-- scope: feature -->
# Route — the panel on the shared drawer shell, and the offers' row finding its home

**Date:** 2026-09-26 · **Branch:** `feature/zones-avoid-fix` · **Status:** in design — nothing built, nothing outside this file touched · **Deferred 2026-09-26 on the user's word: the algorithm leads**, so this design and its review stand ready and its build waits behind the standoff design

**Asked for:** the deferred question of [`260926_FEAT_PLN_Route_speed-zone-standoff-alternatives.md`](260926_FEAT_PLN_Route_speed-zone-standoff-alternatives.md), re-assessed on the user's word of 2026-09-26 — *why not reuse the shell used for the marker and track selected dashboards, the marker wizard's shell, for the route panel too* — against the layout change that has since landed: PR #258 put the Markers wizard onto [`DrawerScaffold`](../../app/src/main/java/ykws/android/maro/ui/components/DrawerScaffold.kt:1), which is already the menu's, the marker drawer's, Where-Am-I's and both track drawers' frame. The route panel is the **last drawer off the shared shell**, and putting it on is what gives the offers' carousel a home that is content rather than an invented third place.

## 1. What ships today, measured against the change

| Question | What ships today | The change |
|---|---|---|
| What is the shell? | [`DrawerScaffold`](../../app/src/main/java/ykws/android/maro/ui/components/DrawerScaffold.kt:149) — a fixed [`DrawerHeader`](../../app/src/main/java/ykws/android/maro/ui/components/DrawerScaffold.kt:68) carrying the title, a back button and a trailing `headerActions` slot, a **weighted scroll host** (`scrollable`, `suppressOverscrollWhenFits`) and a **fixed `footer` slot** | The panel becomes one of its callers |
| How does a slot occupant use it? | Three precedents pass the same four flags — [`WizardDrawer.kt:103-107`](../../app/src/main/java/ykws/android/maro/ui/map/WizardDrawer.kt:103), [`MarkerDrawer.kt:210-215`](../../app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt:210) and [`OverlayLayer.kt:524-526`](../../app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:524) — `bottomAnchoredContent = true`, `wrapContent = !isLandscape`, `wrapContentMinHeight = if (isLandscape) 0.dp else <the portrait dashboard height>`, `statusBarsInset = isLandscape` | The panel passes the same four, its floor read from the same home the wizard's drawer already reads |
| What does the panel draw itself? | Its own frame ([`RouteConfirmPanel.kt:34-73`](../../app/src/main/java/ykws/android/maro/ui/map/RouteConfirmPanel.kt:34)): a header row — the phase's title left, its status word right — the phase's comment, a 0.5 dp divider, the stage on the sentence line, the [`StatCell`](../../app/src/main/java/ykws/android/maro/ui/components/StatCell.kt:1) 2×2 table, the notes, a second divider, [`RoutePinOption`](../../app/src/main/java/ykws/android/maro/ui/map/RouteConfirmPanel.kt:1) and the four-action grid bottom-anchored in a weighted scroll block | The header row becomes the shell's header, the scroll block its body, the pin and the actions its `footer` — **every block the shell already has** |
| Which phase composables carry it? | [`AcquiringPanel`](../../app/src/main/java/ykws/android/maro/ui/map/RouteConfirmPanel.kt:153) and [`RouteActivePanel`](../../app/src/main/java/ykws/android/maro/ui/map/RouteConfirmPanel.kt:247), branched by the one `when (state)` in [`RouteConfirmationPanel`](../../app/src/main/java/ykws/android/maro/ui/map/RouteConfirmPanel.kt:98) | Both keep their bodies and lose their frames |
| Is the panel still an exception? | The epic's `route-saving` rule calls it *the app's one exception to the dashboard's read-only habit* | Stale since the wizard became a drawer with a footer of [`ConfirmActionButton`](../../app/src/main/java/ykws/android/maro/ui/components/ConfirmActionButton.kt:1)s — the footer is the shell's, not an exception |
| Where is the offers' row? | Deferred: *inside the panel or a strip of its own in the dashboard slot* | On the shell it is **content**, a row of the body like a wizard step, and no third place has to be invented |

## 2. The change: the panel's anatomy mapped onto the shell's blocks

- **Header title** — the phase's own, exactly as today: `Route acquisition` · `Routing active`.
- **`headerActions`** — the phase's **status**, the corner reading the panel carries now (`Acquiring…` · `Route active`), on the wizard's dot-progress precedent for that slot.
- **The stage stays on the sentence line** — the panel's own KDoc is explicit that it rides the sentence and *never the header's corner* (R15), so the header's trailing slot is the status's and the stage does not move. This plan refused its own earlier reading here.
- **`onClose`** — **forced, not chosen**: `onClose` carries no default and [`DrawerHeader`](../../app/src/main/java/ykws/android/maro/ui/components/DrawerScaffold.kt:68) always draws its back button, so the panel gains a visible **fourth door** by construction. It is wired to the screen's `onExit` — the acquisition's phase move, the following phase's dialog (R23) — so all four doors reach one rule, and the epic's exits enumeration moves with it.
- **Content** — the comment, the divider, the stage sentence, the table, the notes and the second divider, in today's order.
- **`footer`** — `RoutePinOption` first, then the phase's four-action grid, so the pin keeps standing under the rule with the outcomes at the foot, which is the arrangement §5.8 states today.
- **The four flags** — copied from the three precedents rather than chosen: `bottomAnchoredContent = true`, `wrapContent = !isLandscape`, `wrapContentMinHeight = if (isLandscape) 0.dp else <the portrait dashboard height>`, `statusBarsInset = isLandscape`. The floor's constant and the `shape` a slot occupant passes are read from the wizard's own call at build time — this plan does not restate them.
- **What must not move** — the §5.6 roles, the accent naming the phase's one enabled forward action, the disabled face, the four-action grids, the 16 dp / 12 dp gutters, `StatCell`, `RoutePinOption`, and the strings: **no copy is added, none is retyped**.
- **The gutters are handed in, not inherited** — the shell's `contentPadding` defaults to 12 dp horizontal alone while §5.8 states the panel's 16 dp / 12 dp pair, so the panel passes its own rather than taking the default.
- **What the panel loses** — its frame, its own scroll block and its bottom-anchor arithmetic, all of them inside [`AcquiringPanel`](../../app/src/main/java/ykws/android/maro/ui/map/RouteConfirmPanel.kt:153) and [`RouteActivePanel`](../../app/src/main/java/ykws/android/maro/ui/map/RouteConfirmPanel.kt:247) — **which this plan has not read line by line**, so the build owes that read before it cuts anything: where the frame sits inside those two bodies is stated from the file's own KDoc rather than from its code.
- **The floor has two names today** — the wizard's drawer passes `portraitDashboardHeight` and the marker drawer `minPanelHeight` for the same slot occupant's floor; the panel reads whichever they resolve to, and a third name is not to be invented.

## 3. Build order

1. **The shell call** — `RouteConfirmationPanel` builds `DrawerScaffold` with the title, the status in `headerActions`, the content, the footer, its own `contentPadding` and the four flags; `onClose` wired to its own `onExit`.
2. **The two phase composables** — `AcquiringPanel` and `RouteActivePanel` keep their bodies, drop their frames, and hand the pin and the grid into the footer slot.
3. **The two call sites** — [`MapScreen.kt:2415`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2415) and `:2453`, one per orientation: the panel's signature is unchanged, so this is a compile-through rather than an edit unless the flags arrive as parameters.
4. **The panel's own KDoc** — the anatomy block rewritten onto the shell's blocks, with the stage's rule kept whole.
5. **The guidelines** — §5.8's table re-anchored: the header row becomes the shell's header, the pin · actions row its footer, the scroll block its body; the paragraph on the 16 dp / 12 dp gutters and the choosing phase's 2×2 grid is kept as it stands.
6. **The epic and its docs** — the `route-saving` *one exception* sentence re-stated, the exits enumeration in `destination-ui` read against the new back button, and this plan attached in the feature's `## Docs`.
7. **`apk-build.bat`** and the route-filtered suite green.

## 4. Test pins, and what no pin can reach

- **What a JVM suite can read**: the panel takes the same state and the same six callbacks, so the existing route state tests must stay green unchanged — the pass is a frame swap, and a signature that moved would be the failure to look for.
- **The disabled face**: a disabled `Save track` still wears §5.6's face in both phases — the state the panel already reads `frontSaved` from.
- **The back button** raises whatever `Exit` raised before: the phase move inside an acquisition, the dialog while a route is followed.
- **What no pin can reach**: the layout itself — the floor, the wrap, the footer's anchoring and the drag behaviour over the panel — because **the project carries no instrumentation harness**. These are the device's, on the user's phone, exactly as the wizard's own move was.

## 5. Open points for the review

- **What the back button means** — its presence is the shell's and is settled by construction, so only its target is open: the recommendation is the panel's own `Exit`, because every other drawer off this shell reaches its owner's rule the same way. The objection is real — it puts a route-ending control under the thumb in the mode where the map must stay aimable, and while a route is followed a mis-tap raises the exit dialog.
- **The pin's home** — the footer beside the actions, or the content above the second divider. Recommendation: the footer, which is today's arrangement and what the shell's `bottomAnchoredContent` is for.
- **Whether the offers' carousel row lands in this pass or the next** — recommendation: the next, so this pass is a frame swap with no new behaviour and the carousel's own build keeps its own review.
- **Landscape** — the precedents pass `statusBarsInset = isLandscape`; whether the route panel needs it is a reading of the slot's own host, taken at build time.

## 6. What stays out

- The offers' carousel itself, its rules and its build — [`260926_FEAT_PLN_Route_speed-zone-standoff-alternatives.md`](260926_FEAT_PLN_Route_speed-zone-standoff-alternatives.md) keeps them; this pass gives the row a home, it does not add the row.
- The four-action grids, the exit dialog and every string.
- The progressive-draw plan, and the standoff design's own build.

## 7. The review, 2026-09-26 — its findings and where each landed

Verdict **revise**, then build: the mapping is sound and the shell already carries every block this panel draws itself, but the plan understated one forced change, left one value unnamed and asserted one thing it had not read. This is a **self-review** of a plan written the same hour, so an independent pass should still take it.

- **F1 · The back button is not a choice** — `onClose` carries no default and `DrawerHeader` always draws the button, so the panel gains a fourth door whether or not one is wanted; the plan said *becomes* where the code says *must*. **Folded** into §2 and §5, the open point now covering the target alone.
- **F2 · The gutters needed a home** — the shell's `contentPadding` defaults to 12 dp horizontal alone while §5.8 states the panel's 16 dp / 12 dp pair, so the padding is handed in. **Folded** into §2 and §3.
- **F3 · One claim rested on a KDoc rather than on code** — what the two phase bodies lose is stated from the file's own account of its anatomy; the plan now says the build owes that read before it cuts. **Folded** into §2.
- **F4 · The floor has two names** — `portraitDashboardHeight` in the wizard's drawer and `minPanelHeight` in the marker drawer, for one slot occupant's floor; the plan forbids inventing a third. **Folded** into §2.
- **Verified against the files** — the shell's anatomy and its `footer`, `headerActions`, `wrapContent`, `wrapContentMinHeight` and `bottomAnchoredContent` parameters; the three slot-occupant precedents' four flags; the panel's anatomy and the stage's own rule; the two phase composables and the two `MapScreen` call sites; the epic's *one exception* sentence; §5.8's table.
- **Not verified, and owed at build time** — the two phase bodies line by line; what the floor's two names resolve to; the `shape` a slot occupant passes; and whether the route slot needs `statusBarsInset` in landscape.
