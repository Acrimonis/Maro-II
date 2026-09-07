<!-- scope: feature -->

# Settings Header Normalization — migrate to shared `DrawerHeader`

> **Status:** plan approved after Ask review (2026-09-07). Review findings folded into Steps 1–2 below.

## Context

The Settings overlay header ([`MapScreen.kt:3313`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3313))
diverges from every other panel header. The Menu drawer, Track detail, and Marker detail headers all use the
shared [`DrawerHeader`](app/src/main/java/ykws/android/maro/ui/components/DrawerScaffold.kt:61)
(package `ykws.android.maro.ui.components`).

| Property | Settings (current) | `DrawerHeader` (canonical) |
|---|---|---|
| Back control | `Button` 48dp `CircleShape` | `IconButton` 32dp `CircleShape` |
| Back icon | 24dp | 18dp |
| Title font | 24sp Bold | 17sp Bold, maxLines 1 + ellipsis |
| Row min height | none | `heightIn(min 48dp)` |
| Row v-padding | none | 6dp |
| Row h-padding | 24dp | 24dp (default) |
| Right actions slot | none | yes (`actions`) |

**Ruling:** normalize Settings to the canonical `DrawerHeader` (right column).

## Design Discussion Outcome (2026-09-07)

Concern raised: the shared header must account for the presence/absence of a top-right action button (e.g. the
Menu title's right-aligned settings icon) and auto-size the title vertically against that control.

**Resolution:** the existing `DrawerHeader` already satisfies this. Its `Row` uses
`verticalAlignment = Alignment.CenterVertically`, so every child — the 32dp back button, the title, and any
injected `actions` (e.g. the Menu's 64dp settings gear) — is centered on the **same** vertical axis. The title is
therefore automatically vertically centered against the tallest control present. The only thing that varies with
action presence/size is the resulting row height (48dp with no action vs 76dp with the 64dp Menu gear); the title
stays centered in whichever height results.

**No title-sizing change is needed in `DrawerHeader`.** All drawer headers render through the same shared
composable; the only per-drawer difference is what each caller injects into the `actions` slot (Menu → settings
gear, Marker → delete, Track → action row, Settings → nothing). Normalizing Settings is purely a call-site swap.

## Plan

### Step 1 — Replace the inline Settings header with `DrawerHeader`
In [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3313) `SettingsOverlay`:
- Add import `ykws.android.maro.ui.components.DrawerHeader`.
- Replace the inline header `Row` (back `Button` 48dp + 24sp title) with:
  ```kotlin
  DrawerHeader(
      title = stringResource(R.string.settings_title),
      onClose = onDismiss,
  )
  ```
  (no `actions` — Settings header has none).
- Remove the now-unused header code and imports. **Ask-review finding:** after the swap BOTH `ArrowBack`
  (line 72) and `CircleShape` (line 69) become unused — each is used only in the Settings header (lines 3338 /
  3331). Remove both imports. Keep `Button`, `Icon`, `Icons`, `Spacer`, `FontWeight` — all still used elsewhere
  (dialogs at ~5077/5667/5740, `KeyboardArrowDown` at ~5354).
- **Spacing decision (Ask-review finding, revised 2026-09-07):** the current header sits in a Column with
  `.padding(vertical = 3.dp)` (line 3317) and was followed by `Spacer(16.dp)`. `DrawerHeader` brings its own 6dp
  vpad. **The 16dp spacer was a leftover from the old hand-rolled header and was REMOVED** — the tab bar now
  follows the header directly (the header's 6dp bottom padding provides the gap), matching the compact
  header-to-content spacing of `DrawerScaffold` drawers. Keep the outer 3dp padding.
- **`settings_back` string (Ask-review finding):** `DrawerHeader` uses `R.string.cd_close` for its back-button
  contentDescription, not `settings_back`. After the swap `settings_back` (en/fr) becomes orphaned and the back
  button's accessibility label changes from "Back/Retour" to "Close/Fermer". **Decision: leave the orphaned
  string in place** (harmless, not a compile error) — do not remove it in this pass to keep the change minimal.
  The label change to "Close/Fermer" is accepted (matches all other drawer headers).

### Step 2 — Update the drawer guideline
In [`docs/ui-drawer-guidelines.md`](docs/ui-drawer-guidelines.md):
- **§12 "Not Migrated"** (line ~368): remove Settings from the list — it now uses the shared `DrawerHeader`.
  Keep `ListOverlayScaffold` and `WizardDrawer` listed (they still have their own fixed-header structures).
- **§12 "Consumers" table** (line ~362): add a Settings row documenting the new consumer:
  `SettingsOverlay (MapScreen.kt)` | scrollable n/a (own tab bar + pager body) | headerActions: none | hPad 24.dp | statusBarsInset true.
  **Ask-review finding:** Settings consumes only the standalone `DrawerHeader`, not the full `DrawerScaffold`
  (the other three rows are genuine `DrawerScaffold` consumers). Add a footnote under the table clarifying that
  the Settings row applies the status-bar inset manually (`.windowInsetsPadding(statusBars)`) rather than via
  `DrawerScaffold`'s `statusBarsInset` parameter.
- **§6 "Header Tokens"** (line ~174): optionally note that Settings uses the shared `DrawerHeader` (title 17sp,
  back 32dp) — no longer the 24sp exception.

### Step 3 — Build + verify
- `apk-build.bat` → BUILD SUCCESSFUL.
- Manual: Settings header now shows 32dp back button + 17sp title, matching Menu/Track/Marker headers; tab bar
  and content below unchanged.

---

# Post-Header Spacing Normalization (2026-09-07)

## Context

After normalizing the Settings header, the header→first-content gap was found inconsistent across drawers:

| Drawer | Post-header extra | Total gap (incl. header 6dp) |
|---|---|---|
| Menu | `Spacer(20.dp)` | ~26dp |
| Marker detail | `contentPadding(top=6.dp)` + internal `Spacer(8.dp)` | ~20dp |
| Track detail (landscape) | `contentPadding(top=6.dp)` | ~12dp |
| Track detail (portrait) | `contentPadding(top=6.dp)` | ~12dp |
| Settings | none (tab bar follows header) | ~6dp |

**Ruling (Option 2):** normalize all drawers to the tightest gap — **6dp total**, which is exactly the shared
`DrawerHeader`'s own bottom `verticalPadding`. Drawers add **no extra** spacing after the header. The value is
expressed as **one common ui.properties token** so it is not scattered as hardcoded 20dp/6dp/0 across files.

> **Ask-review note (2026-09-07):** two additional edit sites were found beyond the initial draft — the portrait
> Track drawer (`OverlayLayer.kt:523`) and the Marker content's internal `Spacer(8.dp)` (`MarkerDrawer.kt:201`).
> The Marker internal spacer is part of the shared `MarkerDetailContent` (also used by the `MeasureHeight` probe),
> so removing it must be validated against the probe. See Step C.

## Plan

### Step A — Add the token + AppConfig accessor
- In [`app/src/main/assets/ui.properties`](app/src/main/assets/ui.properties) Padding group, add:
  `ui.padding.header.vertical=6dp` (drawer header top/bottom breathing room; bottom = post-header gap).
- In [`AppConfig.kt`](app/src/main/java/ykws/android/maro/config/AppConfig.kt): add `var uiPaddingHeaderVertical: Float = 6f`
  (Padding block ~line 505) + load `uiPaddingHeaderVertical = dp("ui.padding.header.vertical", uiPaddingHeaderVertical)`
  (~line 894).

### Step B — `DrawerHeader` / `DrawerScaffold` default reads the token
- In [`DrawerScaffold.kt`](app/src/main/java/ykws/android/maro/ui/components/DrawerScaffold.kt): change the
  `verticalPadding: Dp = 6.dp` default (line 73) and `headerVerticalPadding: Dp = 6.dp` default (line 150) to
  `= AppConfig.uiPaddingHeaderVertical.dp`. This makes the header's own 6dp the single source of the post-header gap.

### Step C — Remove per-drawer extra post-header spacing
- **Menu** ([`MenuDrawerOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:129)): remove the
  leading `Spacer(Modifier.height(20.dp))` at the top of the content.
- **Marker detail** ([`MarkerDrawer.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt:171)): change
  `contentPadding = PaddingValues(start = 12.dp, top = 6.dp, end = 12.dp)` → drop `top = 6.dp`.
  **Marker internal spacer:** `MarkerDetailContent` ([`MarkerDrawer.kt:201`](app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt:201))
  opens with `Spacer(Modifier.height(8.dp))`. This is shared with the `MeasureHeight` probe. **Decision (2026-09-07):
  REMOVE it** for full 6dp uniformity. Verify the `MeasureHeight` probe still measures correctly after removal.
- **Track detail — landscape** ([`OverlayLayer.kt`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:434)): drop
  `top = 6.dp` from `contentPadding`.
- **Track detail — portrait** ([`OverlayLayer.kt`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:523)): drop
  `top = 6.dp` from `contentPadding` (Ask-review finding — third site).
- **Settings** ([`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3319)): no change (already 6dp).

### Step D — Build + verify
- `apk-build.bat` → BUILD SUCCESSFUL.
- Manual: Menu, Marker, Track, Settings all show the same ~6dp gap between header and first content.

## Files Affected (this section)
- `app/src/main/assets/ui.properties` — new `ui.padding.header.vertical` token.
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt` — accessor + load.
- `app/src/main/java/ykws/android/maro/ui/components/DrawerScaffold.kt` — defaults read token.
- `app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt` — remove 20dp spacer.
- `app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt` — drop contentPadding top (+ internal 8dp spacer, pending decision).
- `app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt` — drop contentPadding top at BOTH landscape (434) and portrait (523) sites.
- `docs/ui-drawer-guidelines.md` — §6 note: post-header gap = header's 6dp `ui.padding.header.vertical`, no extra.

## Open Question (Marker internal spacer) — RESOLVED 2026-09-07
`MarkerDetailContent` ([`MarkerDrawer.kt:201`](app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt:201)) opens with
`Spacer(8.dp)` shared with the `MeasureHeight` probe. **User decision: REMOVE it** for full 6dp uniformity. Verify the
probe still measures correctly after removal.

## Files Affected
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — Settings header → `DrawerHeader`.
- `docs/ui-drawer-guidelines.md` — §12 "Not Migrated" + "Consumers" table; optional §6 note.

## Notes
- The Settings overlay keeps its own tab bar + `HorizontalPager` below the header; only the header row is
  normalized. The overlay is not migrated to the full `DrawerScaffold` shell (it has a tab bar + footer, a
  different body structure) — only the header is shared.
- The Menu's 64dp settings gear is intentionally larger than the 32dp back button (large tap target). It is
  out of scope for this pass; the shared header already centers the title against it correctly.
