---
name: ColorManagement
status: active
created: 2026-06-16 14:05
modified: 2026-06-16 16:09
active_subfeature: none
---

# Feature: Color Management

**Description:**
Centralised management of all colour tokens in the Maro-II app. Colours live in
`colors.properties` with `${key}` alias interpolation supported by `AppConfig`.
Every colour change must be reflected in `docs/color-scheme.md`.

## Subfeatures

### Colour Taxonomy & Structure  [ ]

Track the canonical colour taxonomy and ensure every new colour follows the naming convention.

#### Todos
- [ ] Maintain the taxonomy in `docs/color-scheme.md` as the single source of truth
- [ ] Audit new colours for naming convention compliance

#### Key Files
- `app/src/main/assets/colors.properties`
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt`
- `docs/color-scheme.md`

### Alias Interpolation  [x]

The `${key}` resolver in `AppConfig.init()` allows properties to reference each other.

#### Todos
- [x] Implement `${key}` regex resolver in `AppConfig.init()`
- [x] Wire green entries to `${ui.dashboard.status.success}`
- [x] Wire overlay low-depth to `${ui.dashboard.status.error}`

#### Key Files
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt`

### Color Scheme Documentation  [ ]

`docs/color-scheme.md` is the canonical visual reference with swatches and alias chains.

#### Todos
- [ ] When adding/modifying a colour: update the corresponding table row
- [ ] Swatches must use 20×20 px inline HTML spans
- [ ] Aliased tokens must show `→ source.key = #HEX`

#### Key Files
- `docs/color-scheme.md`
- `app/src/main/assets/colors.properties`

## Rules

- ALL colours go in `colors.properties` — never hardcoded
- Use `${key}` aliases when a colour is shared
- After ANY colour change, update `docs/color-scheme.md`
- Run `gradlew assembleDebug` after colour changes

## Key Files
- `app/src/main/assets/colors.properties`
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt`
- `app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt`
- `docs/color-scheme.md`

## Docs
- `docs/color-scheme.md`

## Colour Modification Prompt

When asked to modify a colour in the Maro-II app, follow this checklist:

1. **Find the colour** in `app/src/main/assets/colors.properties` using its property key
2. **Update the value** (hex, or use `${alias}` for shared colours)
3. **Update the default** in `app/src/main/java/ykws/android/maro/config/AppConfig.kt` if the property is loaded into a field
4. **Update `docs/color-scheme.md`**: change the hex value, swatch, and alias chain
5. **Check aliases**: if the colour has `${...}` references, verify cascading properties still resolve correctly
6. **Build**: `gradlew assembleDebug`
7. **Deploy**: `adb install -r app/build/outputs/apk/debug/app-debug.apk && adb shell monkey -p ykws.android.maro 1`
