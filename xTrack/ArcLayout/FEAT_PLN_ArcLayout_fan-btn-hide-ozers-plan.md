<!-- scope: feature -->
# Fan Button — Hide Other Controls Plan (Refined)

> Feature: [`ArcLayout`](xTrack/ArcLayout/FEAT_DSC_ArcLayout.md), subfeature `fan-migration`
> Branch: `feature/fan-btn-hide-ozers`

## Goal

When any fan button is expanded, hide all non-fan controls from the right-edge stack. When collapsed, restore them.

## Layout Sections

The control stack has 3 vertical sections (pinned by `Arrangement.SpaceBetween`):

1. **TOP** — `SettingsButton` (fixed, always present)
2. **MIDDLE** — dynamic list of controls (fans + regular buttons, centred)
3. **BOTTOM** — Zoom +/- (fixed, always present)

Non-fan controls in ALL three sections hide when a fan is expanded.

## Data Model

```kotlin
enum class ControlId {
    SETTINGS,
    LAYER_FAN,
    // FUTURE: add new control IDs here (fan or button)
    ZOOM
}

/**
 * @param id        Unique identifier for this control
 * @param isFan     If true, this control can expand (sets expandedFanId when tapped)
 * @param alwaysVisible  If true, this control never hides even when a fan is open
 *                       (reserved for the expanded fan itself — other controls use false)
 * @param section   Which vertical section: TOP, MIDDLE, BOTTOM
 * @param content   Composable. Receives isExpanded = (expandedFanId == this.id)
 */
enum class ControlSection { TOP, MIDDLE, BOTTOM }

data class ControlItem(
    val id: ControlId,
    val isFan: Boolean = false,
    val alwaysVisible: Boolean = false,
    val section: ControlSection,
    val content: @Composable (isExpanded: Boolean) -> Unit
)
```

### Building the list

```kotlin
val expandedFanId = remember { mutableStateOf<ControlId?>(null) }

val controls = listOf(
    // TOP — Settings (always first)
    ControlItem(
        id = ControlId.SETTINGS,
        section = ControlSection.TOP
    ) { SettingsButton(onClick = onOpenSettings) },

    // MIDDLE — dynamic controls go here
    ControlItem(
        id = ControlId.LAYER_FAN,
        isFan = true,
        section = ControlSection.MIDDLE
    ) { isExpanded ->
        FanLayout(
            config = config.copy(isOpen = isExpanded),
            onParentClick = {
                expandedFanId.value = if (isExpanded) null else ControlId.LAYER_FAN
            },
            ...
        )
    },

    // FUTURE: add more MIDDLE controls here (fans or buttons)

    // BOTTOM — Zoom (always last)
    ControlItem(
        id = ControlId.ZOOM,
        section = ControlSection.BOTTOM
    ) { Column { PlusIcon(); MinusIcon() } }
)
```

### Rendering

```kotlin
val anyFanOpen = expandedFanId.value != null

Column(
    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(...),
    verticalArrangement = Arrangement.SpaceBetween
) {
    // TOP section
    controls.filter { it.section == ControlSection.TOP }.forEach { item ->
        val visible = item.alwaysVisible || !anyFanOpen || expandedFanId.value == item.id
        AnimatedVisibility(visible = visible) {
            item.content(expandedFanId.value == item.id)
        }
    }

    // MIDDLE section — dynamic controls, centred
    Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        controls.filter { it.section == ControlSection.MIDDLE }.forEach { item ->
            val visible = item.alwaysVisible || !anyFanOpen || expandedFanId.value == item.id
            AnimatedVisibility(visible = visible) {
                item.content(expandedFanId.value == item.id)
            }
        }
    }

    // BOTTOM section
    controls.filter { it.section == ControlSection.BOTTOM }.forEach { item ->
        val visible = item.alwaysVisible || !anyFanOpen || expandedFanId.value == item.id
        AnimatedVisibility(visible = visible) {
            item.content(expandedFanId.value == item.id)
        }
    }
}
```

### Why sections?

`Arrangement.SpaceBetween` requires exactly the top/middle/bottom structure. If we just flattened all controls, Settings wouldn't stay pinned to the top and Zoom wouldn't stay pinned to the bottom when the middle section has varying height. Sections preserve the layout while keeping the control definitions dynamic.

### Adding a new control later

```kotlin
// Add one entry to the list in the MIDDLE section:
ControlItem(
    id = ControlId.SPEED_FAN,   // add to enum
    isFan = true,
    section = ControlSection.MIDDLE
) { isExpanded -> ... }
```

No layout changes, no visibility logic changes. The `anyFanOpen` gate still works because MIDDLE controls participate in the same `expandedFanId` check.

> Feature: [`ArcLayout`](xTrack/ArcLayout/FEAT_DSC_ArcLayout.md), subfeature `fan-migration`
> Branch: `feature/fan-btn-hide-ozers`

## Goal

When a fan button is expanded, hide all non-fan controls from the right-edge control stack. When fanned out, restore them.

## Current Layout

The control stack at [`MapScreen.kt:833`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:833) has 3 hardcoded children:

1. `SettingsButton` — top-pinned by `SpaceBetween`
2. `FanLayout` (layer toggle) — centred
3. `Column { PlusIcon(); MinusIcon() }` — bottom-pinned

## Proposed: Control-List Model

Replace hardcoded children with a list of `ControlItem` entries rendered in a loop. This lets us:

- Gate visibility on `anyFanOpen` with a single condition
- Add future fan buttons without modifying the hide/show logic
- Keep the `Arrangement.SpaceBetween` layout intact

### Design

```kotlin
enum class ControlId {
    SETTINGS,
    LAYER_FAN,      // current layer-toggle fan
    // FUTURE: SECOND_FAN,  etc.
    ZOOM
}

private data class ControlItem(
    val id: ControlId,
    val isFan: Boolean,
    val content: @Composable () -> Unit
)
```

### Control Stack (pseudocode)

```kotlin
// State: which fan is expanded, or null
val expandedFanId by remember { mutableStateOf<ControlId?>(null) }
val anyFanOpen = expandedFanId != null

val controls = remember(expandedFanId, ...) { listOf(
    ControlItem(SETTINGS, isFan = false) {
        SettingsButton(onClick = onOpenSettings)
    },
    ControlItem(LAYER_FAN, isFan = true) {
        FanLayout(
            config = config.copy(isOpen = expandedFanId == LAYER_FAN),
            onParentClick = {
                expandedFanId = if (expandedFanId == LAYER_FAN) null else LAYER_FAN
            },
            ...
        )
    },
    ControlItem(ZOOM, isFan = false) {
        Column { PlusIcon(); MinusIcon() }
    }
) }

Column(
    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(...),
    verticalArrangement = Arrangement.SpaceBetween
) {
    controls.forEach { control ->
        AnimatedVisibility(visible = control.isFan || !anyFanOpen) {
            control.content()
        }
    }
}
```

### Benefits

- **Single visibility gate**: `control.isFan || !anyFanOpen` — fans are always visible, non-fans hide when any fan is open
- **Scalable**: Adding a second fan button is one new `ControlItem` entry, no structural changes
- **No `AnimatedVisibility` wrappers per-control** — the `forEach` loop handles it uniformly

### Files to modify

| File | Change |
|------|--------|
| [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt) | Replace hardcoded Column children with `ControlItem` list + `AnimatedVisibility` loop. Introduce `expandedFanId: ControlId?` state. |

