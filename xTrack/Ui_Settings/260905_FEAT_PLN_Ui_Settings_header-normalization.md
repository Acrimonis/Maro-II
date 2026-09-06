<!-- scope: feature -->

# Settings Header Normalization — migrate to shared `DrawerHeader`

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
- Remove the now-unused `Button`/`Icon`/`CircleShape`/`Spacer` header code and any imports that become unused
  (verify `Icons.AutoMirrored.Filled.ArrowBack` is still used elsewhere before removing its import).

### Step 2 — Update the drawer guideline
In [`docs/ui-drawer-guidelines.md`](docs/ui-drawer-guidelines.md:481) §12 "Not Migrated": remove the Settings row
("Has 24sp title, tab bar, HorizontalPager — different structure") since Settings now uses `DrawerHeader`.
Optionally note in §6 that Settings uses the shared `DrawerHeader`.

### Step 3 — Build + verify
- `apk-build.bat` → BUILD SUCCESSFUL.
- Manual: Settings header now shows 32dp back button + 17sp title, matching Menu/Track/Marker headers; tab bar
  and content below unchanged.

## Files Affected
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — Settings header → `DrawerHeader`.
- `docs/ui-drawer-guidelines.md` — remove Settings from §12 "Not Migrated".

## Notes
- The Settings overlay keeps its own tab bar + `HorizontalPager` below the header; only the header row is
  normalized. The overlay is not migrated to the full `DrawerScaffold` shell (it has a tab bar + footer, a
  different body structure) — only the header is shared.
