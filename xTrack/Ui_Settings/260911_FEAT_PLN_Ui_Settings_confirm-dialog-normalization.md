<!-- scope: feature -->

# Confirmation dialog normalization — one `ConfirmDialog` on the overlay ladder

- **Feature:** Ui_Settings
- **Branch:** feature/tracks-recording
- **Date:** 2026-09-11
- **State:** implemented on the working tree (uncommitted). **P2 geometry & motion implemented** — flush bottom edge,
  navigation-bar inset inside the panel, rounded top corners, open-bottom accent border, 450 ms panel slide, dead lift
  token removed. **P1 closed (2026-09-11)** — resolved by giving `ConfirmDialog` its **own** full-screen scrim and
  painting it from a ladder-level `ConfirmRequestHost` composed above `OverlayLayer`: the dialog composites above every
  drawer and the map, so no surface sits above the dim. **P4 (2026-09-11)** — all scrims are hard on/off toggles and the
  ladder scrim yields while any `ConfirmDialog` is visible, so dims never stack. The `LocalDialogDismiss` registry, the
  `activeDialogDismiss` parameter and the dialog-first `scrimDismiss` branch are **deleted** (superseded text below is
  retained as record). Build green.

## Why

Confirmation UI was three systems: `ModalBottomSheet` (full width, flush to the bottom, no orientation awareness),
`AlertDialog` for deletes and the merge configuration, and the app's own drawer panels which *do* adapt to landscape.
In landscape the sheets stretched edge to edge: two half-screen buttons, long text lines, and a shape that
contradicted the app's left-column landscape language. `ConfirmSheet.kt` is deleted; every confirmation is now one
component.

## Locked decisions (user-approved 2026-09-11)

- **D1** One component — `ConfirmDialog`, in `ui/components` — built on the app's overlay ladder (scrim +
  `DrawerSlot`-style panel + measure/animate). No framework sheet, no platform dialog window.
- **D2** Semantics modelled on the record-in-progress dialog: title, message, an optional content slot, and an
  ordered action list with roles. **Cancel is optional — it appears only where dismissal unambiguously means
  "abort, nothing happens"**; where it appears it is the last button in the stack and calls the same lambda as
  scrim/back. Where dismissing has a side effect (recovery saves the checkpoint) no Cancel is offered.
- **D3** The content slot carries per-dialog extras: checkbox rows (resume backup, merge keep-originals) and the
  merge name field.
- **D4** Geometry (revised 2026-09-11): width = `min(maxWidth, maxHeight)` — the device's portrait width — used
  identically in **both** orientations, no cap token; **flush on the bottom edge, no lift**; the navigation-bar inset
  is applied *inside* the panel (content padding) so the surface itself reaches the edge; height **wraps its
  content** and scrolls; IME offset retained. Superseded the earlier "lifted by `ui.dialog.bottom.lift`" decision —
  see P2.
- **D5 Dimming normalized — one token, two owners (revised 2026-09-11).** `ui.scrim.alpha` (the inline `0.50f`
  extracted) is the single dim token, read by **two** layers that never both paint: the ladder scrim (`OverlayLayer`)
  serves drawers/settings/wizard only, and `ConfirmDialog` paints **its own** full-screen `ui.scrim.alpha` layer beneath
  its own panel. The dialog is raised by drawer-hosted surfaces as a `ConfirmRequest` and painted by the ladder-level
  `ConfirmRequestHost`, composed above `OverlayLayer`, so the dim — and the panel — sit above every drawer and the map.
  **Revised again 2026-09-11 (P4):** both scrims are **hard on/off toggles** (no fade) and the ladder scrim **yields**
  while any `ConfirmDialog` is visible, so the two dims never stack.
  > **Superseded (2026-09-11, pre-fix):** the original D5 said `ConfirmDialog` "paints no scrim of its own and only
  > registers its dismiss lambda" through `LocalDialogDismiss`, with the ladder scrim suppressed while a dialog was
  > registered. That design was abandoned — see P1 (closed). The `LocalDialogDismiss`/`activeDialogDismiss` plumbing
  > no longer exists.
- **D6** Migration set (verified inventory, 2026-09-11): the recording exit sheet (reference), resume, import
  conflict, GPS source-switch while recording, the shared `ConfirmSheet` and **both** `ListOverlayScaffold`
  confirmation paths (`confirmMessage` → batch delete, `confirmContent` → merge), plus the `AlertDialog`
  confirmations: merge configuration via `MultiActionSpec.confirmContent` and orphan-checkpoint recovery.
  `MarkerColorPickerDialog` and `IconPickerDialog` are pickers, out of scope; single-item track/marker deletion stays
  dialog-free (swipe/trash + undo snackbar).
- **D7** Boundary: OS-consent prompts stay `AlertDialog` — background location, GPS permission, battery
  optimisation and the `MainActivity` battery prompt.

## Component API

```kotlin
enum class ConfirmActionRole { PRIMARY, SECONDARY, DANGER }
data class ConfirmAction(val label: String, val role: ConfirmActionRole = PRIMARY, val onClick: () -> Unit)

@Composable
fun ConfirmDialog(
    title: String,
    visible: Boolean,
    onDismiss: () -> Unit,
    message: String? = null,
    options: (@Composable ColumnScope.() -> Unit)? = null,   // checkbox rows / text field
    actions: List<ConfirmAction>   // caller-provided, rendered in order; a Cancel, when used, is passed last
)
```

Matches the shipped component ([`ConfirmDialog.kt:59`](app/src/main/java/ykws/android/maro/ui/components/ConfirmDialog.kt:59),
[`:107`](app/src/main/java/ykws/android/maro/ui/components/ConfirmDialog.kt:107)). `actions` renders full-width
stacked buttons, primary = accent filled, secondary = outlined, danger = red filled, 8 dp apart.

## Scrim ownership & modality (final rule — 2026-09-11)

`ConfirmDialog` **owns its own full-screen scrim** and its own dismiss lambda. Drawer-hosted surfaces
(merge, batch delete) do not compose the dialog inside the drawer — they raise a `ConfirmRequest`
through `LocalConfirmDialogHost`, and the screen paints it with `ConfirmRequestHost`
([`MapScreen.kt:1978`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1978)), a sibling
composed **after** `OverlayLayer` in the same `Box`. The dialog therefore composites above every
drawer and above the map, so its scrim dims and touch-blocks the whole screen while its panel stays
on top — with no dependency on the source drawer's geometry.

The overlay ladder's scrim (`OverlayLayer`) serves **drawers/settings/wizard only**, is a **hard on/off
toggle** (no fade), and **yields while any `ConfirmDialog` is visible** (`dialogScrimActive`), so the
two dim layers never stack (P4). The `activeDialogDismiss` parameter, the `LocalDialogDismiss` registry,
the dialog-first `scrimDismiss` branch and the `MapScreen` provider remain **deleted** — the yield is a
plain visibility boolean, not a dismiss registry.

### Superseded diagnostic record (pre-fix, 2026-09-11)

> ⚠️ **SUPERSEDED — retained as history only.** The analysis below describes the pre-fix single-scrim
> design (`LocalDialogDismiss` registry, dialog-first `scrimDismiss` branch, the dialog raising the
> ladder scrim). That plumbing has since been deleted; the final rule above replaces it. Line
> references are stale.

The registry was: `MapScreen` owned one `MutableState` ([`MapScreen.kt:960`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:960)),
provided it through `LocalDialogDismiss` once for the whole screen ([`:971`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:971)),
and `ConfirmDialog` registered while visible ([`ConfirmDialog.kt:124`](app/src/main/java/ykws/android/maro/ui/components/ConfirmDialog.kt:124)).
`OverlayLayer` received the same state and folded it into `showScrim`
([`OverlayLayer.kt:228`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:228)).

The defect was purely **paint order**: the scrim was item 1 of the ladder Box
([`OverlayLayer.kt:237`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:237)), while items 2–7 were the
drawers (Wizard [`:264`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:264), Menu
[`:315`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:315), Marker + Where-Am-I
[`:375`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:375), TrackInfo (no scrim)
[`:444`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:444), TrackHistory
[`:661`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:661), MarkerManagement
[`:712`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:712), Settings
[`:746`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:746)). Anything composed inside a drawer, or any
drawer left open behind a ladder-level dialog, therefore sat **above** the only dim layer.

Audit of every call site:

| Dialog | Call site | Above the scrim | Dim + input blocking |
|---|---|---|---|
| Merge | [`TrackHistoryOverlay.kt:306`](app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt:306) — inside ladder item 5 | the drawer that hosts it | **map dimmed and blocked; the hosting drawer surface sits above the single dim layer, so it stays visually undimmed with its controls tappable** — paint-order inference, **not device-verified** |
| Batch delete | [`ListOverlayScaffold.kt:854`](app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt:854), used by TrackHistory ([`:371`](app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt:371)) and MarkerManagement ([`MarkerManagementOverlay.kt:183`](app/src/main/java/ykws/android/maro/ui/map/MarkerManagementOverlay.kt:183)) | the hosting drawer (items 5, 6) | same as merge |
| Import conflict | [`MapImportConflictHost.kt:65`](app/src/main/java/ykws/android/maro/ui/map/MapImportConflictHost.kt:65), hosted at [`:1904`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1904), opened from a list drawer at [`:682`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:682) | the source list drawer stays open | map dimmed; drawer interactive |
| Resume | [`MapScreen.kt:1925`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1925), opened at [`:1711`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1711); the drawer closes only in the Resume action, [`:1964`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1964) | the source list drawer stays open | map dimmed; drawer interactive |
| GPS source switch | [`MapDialogHost.kt:165`](app/src/main/java/ykws/android/maro/ui/map/MapDialogHost.kt:165), wired from the menu drawer at [`:713`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:713) | the menu drawer stays open | map dimmed; drawer interactive |
| Exit / stop recording | [`MapDialogHost.kt:74`](app/src/main/java/ykws/android/maro/ui/map/MapDialogHost.kt:74), [`:84`](app/src/main/java/ykws/android/maro/ui/map/MapDialogHost.kt:84) | nothing (back press / dashboard) | correct |
| Recovery, bg location, GPS permission, battery | [`MapDialogHost.kt:96`](app/src/main/java/ykws/android/maro/ui/map/MapDialogHost.kt:96), [`:113`](app/src/main/java/ykws/android/maro/ui/map/MapDialogHost.kt:113), [`:138`](app/src/main/java/ykws/android/maro/ui/map/MapDialogHost.kt:138), [`:180`](app/src/main/java/ykws/android/maro/ui/map/MapDialogHost.kt:180) | nothing (the three `AlertDialog`s are platform windows and unaffected) | correct |

Two knock-on effects of the same ordering (**both superseded** — the dialog-first `scrimDismiss` branch no longer
exists):

- The dialog-first branch of `scrimDismiss` ([`OverlayLayer.kt:242-255`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:242))
  is unreachable for the merge/batch-delete pair in portrait: the TrackHistory drawer is
  `fillMaxWidth()` + `fillMaxHeight()` ([`OverlayLayer.kt:662-670`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:662)),
  so no scrim pixel is ever exposed.
- Latent registry clobber: [`ConfirmDialog.kt:124-127`](app/src/main/java/ykws/android/maro/ui/components/ConfirmDialog.kt:124)
  clears the shared registry on dispose when `visible`, so a dialog that hides can null the registration of a
  different, still-visible dialog. Not reachable with today's mutually exclusive hosts; guard it when touched.

## P1 — Scrim/modality correction (CLOSED 2026-09-11)

Requirement (D5): the dim must cover **every** drawer surface and block its input while staying below the panel.

**Resolution taken — neither A nor B.** `ConfirmDialog` now **owns its own scrim** and is painted by the ladder-level
`ConfirmRequestHost` above `OverlayLayer`, so the dim (and the panel) sits above every drawer and the map by
construction. The ladder scrim reverted to drawer/settings/wizard only, and the whole
`LocalDialogDismiss` / `activeDialogDismiss` / dialog-first-`scrimDismiss` mechanism was deleted. Merge, batch delete
and marker batch delete were hoisted to the ladder host as `ConfirmRequest`s; their source drawer stays open behind
them.

> **Superseded options (retained as history):**
> - **Option A (recommended, smallest)** — emit the single scrim slot *after* the drawer items while a dialog was
>   registered, and *before* them otherwise.
> - **Option B (structural)** — hoist the drawer-scoped dialogs (merge, batch delete) out of the drawer subtree to
>   ladder level.
>
> Both are moot: the dialog now carries its own scrim, and the host hoist subsumes B's drawer-scoped intent without
> moving their state ownership.

## P2 — Geometry & motion revision (implemented 2026-09-11)

All six items below are **done** and the build is green; the line references in this list predate the P2 pass.

1. **Flush bottom, no lift** — drop `liftDp` from the panel slot
   ([`ConfirmDialog.kt:141-147`](app/src/main/java/ykws/android/maro/ui/components/ConfirmDialog.kt:141)); keep the IME
   offset so the merge name field is never covered.
2. **Navigation-bar inset inside the panel** — pad the content `Column`
   ([`ConfirmDialog.kt:153-158`](app/src/main/java/ykws/android/maro/ui/components/ConfirmDialog.kt:153)) by
   `WindowInsets.navigationBars`, so the buttons clear the gesture bar while the surface reaches the edge.
3. **Shape** — `RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)`
   ([`ConfirmDialog.kt:150`](app/src/main/java/ykws/android/maro/ui/components/ConfirmDialog.kt:150)); a flush panel
   with rounded bottom corners would show the scrim through the notches.
4. **Slower pull-up** — add an optional animation parameter to `DrawerSlot`
   ([`DrawerSlot.kt:47`](app/src/main/java/ykws/android/maro/ui/map/DrawerSlot.kt:47)) that **defaults to today's
   behaviour** (spring 350 enter + 80 ms fade, tween 150 exit — [`DrawerSlot.kt:76`](app/src/main/java/ykws/android/maro/ui/map/DrawerSlot.kt:76),
   [`:89`](app/src/main/java/ykws/android/maro/ui/map/DrawerSlot.kt:89)), so no existing drawer changes; `ConfirmDialog`'s
   **panel** passes ~450 ms slide in both directions, with the duration noted in a comment. (The dialog scrim no longer
   shares this window — see **P4**.)
5. **Height clamp** — `maxPanelHeight` ([`ConfirmDialog.kt:134`](app/src/main/java/ykws/android/maro/ui/components/ConfirmDialog.kt:134))
   must keep subtracting the IME and the navigation-bar inset now that the lift term is gone, and still coerce to a
   usable minimum; content-height wrapping plus scrolling must survive with the panel flush to the bottom.
6. **Dead token removal** — delete `ui.dialog.bottom.lift`
   ([`ui.properties:108-109`](app/src/main/assets/ui.properties:108)), the declaration
   ([`AppConfig.kt:509`](app/src/main/java/ykws/android/maro/config/AppConfig.kt:509)) and the parse
   ([`AppConfig.kt:902`](app/src/main/java/ykws/android/maro/config/AppConfig.kt:902)). Keep `ui.scrim.alpha`
   ([`ui.properties:106`](app/src/main/assets/ui.properties:106), [`AppConfig.kt:508`](app/src/main/java/ykws/android/maro/config/AppConfig.kt:508)).

## P3 — Docs (APPLIED 2026-09-11)

- [`ui-drawer-guidelines.md`](docs/ui-drawer-guidelines.md) §1 dialog-layer paragraph, §3 surfaces table and the scrim
  formula/behaviour: now describe the **dialog-owned scrim**, the ladder `ConfirmRequestHost` composited above every
  drawer, and the drawer/settings/wizard-only ladder scrim. `LocalDialogDismiss` / `activeDialogDismiss` references
  removed.
- [`ui-component-guidelines.md`](docs/ui-component-guidelines.md) §5.6: dialog geometry (portrait width, flush bottom,
  rounded top only, nav inset inside, IME, wrap + scroll), the **open-bottom accent border**, the **450 ms** motion,
  scrim ownership, action roles and the optional-Cancel rule; `ui.scrim.alpha` kept, and no `ui.dialog.bottom.lift`
  row.

> **Superseded note:** this section originally flagged the drawer-guideline claims as *false* because the pre-fix
> design was assumed to be single-owner. That assessment was reversed once the dialog was given its own scrim; the
> docs now match shipped behaviour.

## P4 — Scrim on/off + no stacking (implemented 2026-09-11)

All scrims are **hard on/off toggles** — no fade. The ladder scrim and the dialog scrim can never both
be on: the ladder scrim is suppressed while any `ConfirmDialog` is visible.

- [`ui/map/OverlayLayerParams.kt`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayerParams.kt) — new
  `OverlayChrome.dialogScrimActive: Boolean = false` (the default keeps other call sites compiling).
- [`ui/map/OverlayLayer.kt`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt) — the scrim is a plain
  `if (showScrim)` (no `DrawerSlot`); `showScrim = (…drawers/settings/wizard…) && !dialogScrimActive`.
- [`ui/components/ConfirmDialog.kt`](app/src/main/java/ykws/android/maro/ui/components/ConfirmDialog.kt) — the scrim is
  a plain `if (visible)`; the **panel** keeps its 450 ms `DrawerSlot` slide; the `ConfirmDialogAnimMs` KDoc corrected.
- [`ui/map/MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt) — `dialogScrimActive =
  anyConfirmDialogOpen || confirmDialogHost.request != null` (covers the hoisted merge / batch-delete host too).
- Build `gradlew.bat assembleDebug` SUCCESSFUL.

> **Accepted trade-off:** with no fade, the dialog scrim vanishes the instant `visible` goes false while the panel is
> still sliding out, and the ladder dim returns immediately — approved as "on/off as appropriate", no fade coordination.

## Layout & behaviour spec (final, revised 2026-09-11)

**Structure, top → bottom:** title (18 sp bold) → message (14 sp `uiTextPrimary`) → optional content slot (checkbox
rows / text field) → divider (0.5 dp `uiDividerColor`) → actions stacked full width, 8 dp apart → 16 dp bottom
padding, plus the navigation-bar inset inside the panel. This mirrors the recording dialog's metrics.

**Geometry:** width = the device's portrait width, `min(maxWidth, maxHeight)`, identical in both orientations,
horizontally centred, no configured cap; **flush on the bottom edge, no lift**; navigation-bar inset applied inside
the panel as content padding; height wraps content; content scrolls when it exceeds the available height (IME and
nav-bar insets subtracted from the clamp); IME offset retained in both orientations.

**Actions:** caller-provided, rendered in order, roles `PRIMARY` / `SECONDARY` / `DANGER`. **Cancel is optional** and
belongs only where dismissal is unambiguously "abort, nothing happens"; when present it is the bottom-most button and
calls `onDismiss`. Recovery gets none because dismissing it saves.

**Dismissal:** outside tap, back and the caller's `onDismiss` all run the same lambda, and that lambda may carry a
side effect — the recovery dialog saves the checkpoint on dismiss and must keep doing so.

**Animation:** the panel slides up + fades in over ~450 ms (approximately 1.8× the drawer slide), mirrored on exit,
via the `DrawerSlot` parameter; every other `DrawerSlot` caller keeps the current spring/tween defaults. The dialog
scrim is a hard on/off toggle (P4).

**Dim:** one token — `ui.scrim.alpha` (0.50) — **two owners, never both on**. The ladder scrim (`OverlayLayer`) serves
drawers/settings/wizard only and **yields while any `ConfirmDialog` is visible**; `ConfirmDialog` paints its **own**
full-screen `ui.scrim.alpha` layer beneath its own panel, so the dim (and the panel) covers every drawer and the map
while the dialog is visible. Both are hard on/off toggles (no fade), so the layers never stack — the dialog's scrim
simply replaces the drawer's.

**Accessibility:** title announced as a heading; each action a button; a checkbox row exposes one toggleable target.

## Implemented (2026-09-11)

- [`ui/components/ConfirmDialog.kt`](app/src/main/java/ykws/android/maro/ui/components/ConfirmDialog.kt) — new single confirmation component:
  `ConfirmActionRole` / `ConfirmAction` / `ConfirmDialog`, `ConfirmRequest` / `ConfirmDialogHostState` /
  `LocalConfirmDialogHost` / `ConfirmRequestHost`, options slot, own scrim layer, `BackHandler`; portrait-width panel
  flush on the bottom edge, rounded top corners, open-bottom accent border, 450 ms pull-up.
- [`ui/components/ConfirmSheet.kt`](app/src/main/java/ykws/android/maro/ui/components/ConfirmSheet.kt) — deleted.
- **Tokens** — `ui.scrim.alpha` (0.50, the inline literal extracted; renamed from `ui.dialog.scrim` because drawers
  and dialogs share it) with `AppConfig` accessors; `ui.dialog.bottom.lift` was added, then removed with P2.
- **Migrations** — recording exit, stop recording, resume (backup checkbox), import conflict, batch delete, GPS
  source-switch, merge (name field + keep-originals) and orphan recovery (dismiss saves; no Cancel).
- **Guard + scrim** — the double-back exit guard excludes any open confirmation; the dialog owns its own dim layer and
  the ladder scrim reverted to drawers/settings/wizard only (`LocalDialogDismiss`/`activeDialogDismiss` plumbing
  deleted).
- **Scrim toggles (P4, 2026-09-11)** — both scrims are hard on/off layers (no fade); the ladder scrim yields while any
  `ConfirmDialog` is visible (`OverlayChrome.dialogScrimActive`), so dims never stack.
- **Docs + strings** — EN/FR keys for the previously hardcoded labels; both UI guideline docs and `maro-code.md` updated.
- Build `gradlew.bat assembleDebug` SUCCESSFUL after every pass.

## No-regression checklist

- Outside tap, back and Cancel dismiss every migrated dialog; the double-back exit guard never fires while one is
  open — flag [`MapScreen.kt:967`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:967), guard
  [`:998`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:998).
- **A dialog opened from a drawer (merge, batch delete, resume, import conflict, GPS switch) dims and blocks that
  drawer — not just the map (P1, closed: the dialog owns its scrim and is hosted above every drawer).**
- **No drawer surface is left both undimmed and interactive while a dialog is up (P1, closed).**
- Danger styling and parameterised delete messages survive verbatim.
- Merge keeps: default name, `keepOriginals = true`, `Set<String>` payload, name field.
- Checkbox defaults unchanged (resume backup checked, merge keep-originals checked).
- Body scrolls when a message overflows; the panel stays flush to the bottom edge; the IME never covers the merge
  field; buttons clear the gesture bar.
- No drawer's animation timing changes (the `DrawerSlot` duration parameter defaults to today's specs). The ladder
  scrim is intentionally a hard toggle (no fade) — see P4.
- The recording dialog is visually unchanged except the decided geometry/shape/motion revision.

## Verification (device pass, both orientations)

Width equals the portrait width in both; panel flush to the bottom edge with square bottom corners and buttons clear
of the gesture bar; height hugs content and scrolls; the dialog's **own** 0.5 dim with no drawer open, with a drawer
open, and with a dialog **above** a drawer (merge, batch delete); outside tap / back / Cancel; double-back never exits
behind a dialog; merge text entry usable with the keyboard up; the slide is visibly slower than a drawer's (450 ms);
no other drawer animates differently.

## Per-dialog regression matrix

| Dialog | Before | Extras | Dismissal that must survive |
|---|---|---|---|
| Recording exit | sheet: Save (accent) / Continue (outlined) / Discard (red), hardcoded labels | — | scrim + back; **no Cancel** |
| Stop recording | same sheet | — | scrim + back; **no Cancel** |
| Resume | sheet: Cancel beside Resume | backup checkbox (checked) | scrim + back + Cancel (bottom of the stack) |
| Import conflict | sheet: Duplicate (accent) / Override (red) / Cancel (outlined) | — | scrim + back + Cancel (order unchanged) |
| Batch delete | `ConfirmSheet` (destructive) with Cancel in a row | — | scrim + back + Cancel (last); confirm runs the action then exits multiselect |
| GPS source switch | `ConfirmSheet` | — | scrim + back; confirm applies the mode, dismiss clears pending |
| Merge | `AlertDialog` with hardcoded container/labels | name field + keep-originals checkbox (checked) | scrim + back + Cancel; confirm calls the merge then exits multiselect |
| Orphan recovery | `AlertDialog`: Continue + Save | — | **dismiss = save**, back = save, Save = save, Continue = resume |

## Files

- `ui/components/ConfirmDialog.kt` — panel geometry, shape, open-bottom accent border, motion, **own scrim**, `ConfirmRequestHost`
- `ui/map/DrawerSlot.kt` — optional animation parameter (defaults unchanged)
- `ui/map/OverlayLayer.kt` + `ui/map/OverlayLayerParams.kt` — scrim to hard toggle + `dialogScrimActive` yield; `config/AppConfig.kt` + `assets/ui.properties` — dead token removal
- `ui/map/TrackHistoryOverlay.kt`, `ui/components/ListOverlayScaffold.kt`, `ui/map/MapScreen.kt` — raise merge/batch-delete via `ConfirmRequest`; ladder `ConfirmRequestHost`
- `docs/ui-drawer-guidelines.md`, `docs/ui-component-guidelines.md`

## Open

- ~~**P1 A vs B**~~ — **CLOSED (2026-09-11).** Resolved by giving `ConfirmDialog` its own scrim and painting it from
  the ladder-level `ConfirmRequestHost` above `OverlayLayer` (see P1). Options A/B are moot; the
  `LocalDialogDismiss`/`activeDialogDismiss` plumbing is deleted.
- Whether the merge/batch-delete dialogs should also close their source drawer on open — **settled: no.** The source
  drawer stays open behind the panel (the merge's `exitMultiselect` relies on it).
- ~~Guard the registry clobber in `ConfirmDialog`'s dispose path.~~ **CLOSED** — no shared registry remains; the
  dialog owns its own dismiss lambda.
- **Dialog host uniformity (observation — no fix recommended).** Only merge / batch delete are raised through
  `ConfirmRequestHost`; recording exit/stop, orphan recovery, import conflict and resume are painted directly by
  `MapDialogHost` / `MapImportConflictHost` / `MapScreen`. Every one of them still composites **above** the drawers and
  the map because they are siblings composed after `OverlayLayer` — the outcome is correct, only the mechanism
  differs. Uniformising them onto `ConfirmRequest` would move side effects (recovery save-on-dismiss, merge IME /
  keyboard, double-back guard) for no user-visible gain, so it is **not** recommended. Optional future cleanup: a thin
  `LadderConfirmDialogsHost` that merely groups the direct hosts so ladder placement is stated in one place.
