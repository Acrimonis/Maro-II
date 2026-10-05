<!-- scope: feature -->
# Action-toast alignment — a centred message, bottom-right commands

Owner: **Ui_General** (the bottom band and the delete-undo snackbar). Surfaces: `SnackRow` in `MapScreen.kt`,
skinned by `MapBanner` (`MapControls.kt`).
Branch: `feature/menu-align`. Status: **implemented 2026-10-05**; the device pass is owed.

## 1. What was asked — the user's word, 2026-10-05

- **The action-row message reads vertically centred.**
- **The row's commands sit bottom-right.**

## 2. What follows

- **The row's own `verticalAlignment` moves** from `Alignment.Top` to `Alignment.CenterVertically`, so the
  message rides the row's centre ([`SnackRow`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:449)).
- **Each command is anchored to the row's bottom edge** — `SnackAction` gains a `modifier` parameter and the row
  passes `Modifier.align(Alignment.Bottom)`, which [`ConfirmActionButton`](../../app/src/main/java/ykws/android/maro/ui/components/ConfirmDialog.kt:337)
  already accepts. The `align` resolves in the `Row`'s own scope, so no wrapper is needed.
- **The consequence, named**: a two-line message and its commands no longer share a top edge — the buttons stay
  on the bottom edge while the text centres beside them. A single-line row is visually unchanged.
- **The superseded rule**: §5.7 of `docs/ui-component-guidelines.md` said the commands were "anchored to the
  top of the row rather than centred vertically"; that clause and the action-toasts plan's Outcome are rewritten
  and marked superseded.

## 3. What entered

- `ui/map/MapScreen.kt` — `SnackRow`'s `verticalAlignment` and its two `SnackAction` call sites; `SnackAction`
  gains `modifier`.
- `docs/ui-component-guidelines.md` §5.7 — the action-row interior bullet.
- `xTrack/Ui_General/261004_FEAT_PLN_Ui_General_action-toasts.md` — the supersede note.
- No new string, token, colour, dependency or control; `SnackAction` still reuses `ConfirmActionButton`'s
  compact `SECONDARY` face.

## 4. Verification

- **In reach:** `apk-build.bat` green; the bottom alignment is a plain `RowScope.align` on a control that
  already took a `Modifier`.
- **Not in reach:** whether a centred message beside bottom-edge commands reads right, and the two-line-wrap
  case over the map. The user runs this.
