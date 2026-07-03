# Plan: Drawer Header Fixed-on-Scroll Normalization

**Feature:** Ui_General → `tweak drawer`
**Branch:** `feature/ui-drawer`
**Created:** 2026-07-03

## Problem

Two drawer composables in [`MarkerDrawer.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt) wrap the `← [title]` header inside a `.verticalScroll()` column — when content is tall, the header scrolls off-screen. The user loses the dismiss button and context.

[`MenuDrawerOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt) has no scroll at all, but its header is structurally embedded in a flat column — if content ever grows, it would suffer the same bug.

The correct pattern already exists in two places:
- [`ListOverlayScaffold`](app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt:402-507) — `Column(fillMaxSize)` → fixed header Row → `LazyColumn(fillMaxSize)`
- [`WizardDrawer`](app/src/main/java/ykws/android/maro/ui/map/WizardDrawer.kt:103-154) — `Column(fillMaxSize)` → `WizardTopBar` (fixed) → `Box(weight(1f))` scrollable body

## Design

### New shared component: `DrawerScaffold`

Extract a reusable scaffold in `ui/components/DrawerScaffold.kt` that provides:

1. **`DrawerHeader`** — promoted from `private` in MarkerDrawer.kt to `@Composable` internal in the new file. Same canonical tokens from [`ui-drawer-guidelines.md` §6](docs/ui-drawer-guidelines.md:187): 32dp `IconButton(CircleShape, uiSettingsSwitchTrackInactive)` + `ArrowBack(18dp)` + `Spacer(16dp)` + `Text(17sp, Bold)` + optional actions slot.

2. **`DrawerScaffold`** — the fixed-header + scrollable-body structure:

```kotlin
@Composable
fun DrawerScaffold(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    headerActions: @Composable RowScope.() -> Unit = {},
    headerHorizontalPadding: Dp = 24.dp,   // canonical default
    headerVerticalPadding: Dp = 3.dp,       // canonical default
    scrollable: Boolean = true,
    shape: Shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
    content: @Composable ColumnScope.() -> Unit
)
```

**Structure:**
```
Box(fillMaxSize, clip(shape), background(uiSettingsBackground), modifier)
  └─ Column(fillMaxSize)
       ├─ DrawerHeader(title, onClose, headerActions, hPad, vPad)  ← FIXED
       └─ Box(Modifier.weight(1f).fillMaxWidth())                   ← scroll host
            └─ if (scrollable) Column(verticalScroll) { content() }
               else content()
```

Padding defaults: `24.dp` horizontal matches the canonical header pattern in the guidelines and MenuDrawer/ListOverlayScaffold. MarkerDrawer currently uses `12.dp` — it will override via the parameter.

### Consumers

| Consumer | File | scrollable | headerActions | hPad |
|----------|------|:---:|---|---|
| MarkerDrawer ViewingContent | [`MarkerDrawer.kt:120`](app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt:120) | true | edit + delete + icon buttons | 12.dp |
| MarkerDrawer MatchResultContent | [`MarkerDrawer.kt:422`](app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt:422) | true | none | 12.dp |
| MenuDrawerOverlay | [`MenuDrawerOverlay.kt:68`](app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:68) | false | Settings gear button | 24.dp |
| `ListOverlayScaffold` | [`ListOverlayScaffold.kt:369`](app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt:369) | N/A | Uses own header Row (section label + sort/filter controls between header and list) | 24.dp |

**ListOverlayScaffold** is NOT migrated — it has additional controls (section label, sort/filter) between the header and the `LazyColumn` that don't fit the simple scaffold. Its header is already correctly fixed.

**WizardDrawer** is NOT migrated — it uses `WizardTopBar` (step dots, different layout) and `WizardButtonRow` at the bottom.

**Settings** is NOT migrated — it has a 24sp title, tab bar, and `HorizontalPager`.

## Files

### New
- `app/src/main/java/ykws/android/maro/ui/components/DrawerScaffold.kt` — `DrawerHeader` + `DrawerScaffold`

### Modified
- [`app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt)
  - Remove `private fun DrawerHeader` (lines 564-602)
  - Remove outer `Box(fillMaxSize, clip, bg)` wrapper (lines 97-102) — each content composable now owns its shape via `DrawerScaffold`
  - Restructure `ViewingContent`: outer `Box` → `DrawerScaffold(title = marker.name, scrollable = true, headerHorizontalPadding = 12.dp, headerActions = { edit+delete+icon }, contentPadding = PaddingValues(horizontal = 12.dp))`, body = info card + prev/next
  - Restructure `MatchResultContent`: outer `Box` → `DrawerScaffold(title = "Where Am I?", scrollable = true, headerHorizontalPadding = 12.dp, contentPadding = PaddingValues(horizontal = 12.dp))`, body = section header + match rows
- [`app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt)
  - Outer `Box` → `DrawerScaffold(title = "Maro II", scrollable = false, statusBarsInset = true, headerActions = { Settings gear }, contentPadding = PaddingValues(horizontal = 24.dp))`, body = position source + tracks + markers sections
- [`docs/ui-drawer-guidelines.md`](docs/ui-drawer-guidelines.md)
  - Add §12: `DrawerScaffold` API, usage guide, migration notes from ad-hoc patterns
  - Update §4 (Drawer Composable Contract) to reference `DrawerScaffold` as the preferred foundation for new drawers
  - Update §6 (Header Tokens) to note the `DrawerHeader` composable location

### Review Findings (Ask mode)

Three layout-preservation concerns identified and addressed:

1. **Horizontal padding loss** — content area loses outer Column's `padding(horizontal)` when header is extracted. → Added `contentPadding` parameter.
2. **Status bar insets on MenuDrawer** — `.windowInsetsPadding(statusBars)` must stay after `.background()`. → Added `statusBarsInset` boolean parameter.
3. **MarkerDrawer outer Box redundant** — the `Box(fillMaxSize, clip, bg)` wrapping both content variants is replaced by each `DrawerScaffold`'s own outer Box. Shape moves to per-call `shape` parameter.

### Final DrawerScaffold API

```kotlin
@Composable
fun DrawerScaffold(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    headerActions: @Composable RowScope.() -> Unit = {},
    headerHorizontalPadding: Dp = 24.dp,
    headerVerticalPadding: Dp = 3.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp),
    scrollable: Boolean = true,
    statusBarsInset: Boolean = false,
    shape: Shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
    content: @Composable ColumnScope.() -> Unit
)
```

## Risks

| # | Risk | Mitigation |
|---|------|-----------|
| 1 | **Content loses horizontal padding** — outer Column padding no longer applies. | `contentPadding` parameter per consumer: 12dp (MarkerDrawer), 24dp (MenuDrawer). |
| 2 | **MenuDrawer status bar insets lost** — outer Box replaced. | `statusBarsInset = true` appends `.windowInsetsPadding(statusBars)` after background, matching original modifier order. |
| 3 | **DrawerHeader actions slot type change** — nullable `() -> Unit` → non-null `RowScope.() -> Unit`. | Empty default means no conditional. All call sites currently wrap actions in `Row`; `RowScope` receiver removes the need for the wrapper. |
| 4 | **MarkerDrawer outer Box removed** — previously clipped both content variants. | Each `DrawerScaffold` call provides its own `shape` matching `panelShape` (landscape vs portrait). |

## Todos

- [ ] Create `DrawerScaffold.kt` — `DrawerHeader` + `DrawerScaffold` composables
- [ ] Restructure `MarkerDrawer.ViewingContent` to use `DrawerScaffold`
- [ ] Restructure `MarkerDrawer.MatchResultContent` to use `DrawerScaffold`
- [ ] Remove `private fun DrawerHeader` from `MarkerDrawer.kt`
- [ ] Restructure `MenuDrawerOverlay` to use `DrawerScaffold`
- [ ] Update `docs/ui-drawer-guidelines.md` — §12 + §4 + §6 updates
- [ ] BUILD SUCCESSFUL
