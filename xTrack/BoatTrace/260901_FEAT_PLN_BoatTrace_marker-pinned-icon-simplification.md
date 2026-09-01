# BoatTrace — Marker `pinned` → `icon` simplification + filter/sort/emoji refinements

**Date:** 2026-09-01
**Status:** settled (Ask-reviewed, 6 amendments + discussion decisions)

## Goal

Remove the redundant `UserMarker.pinned` flag and derive "pinned" from `icon != null`, plus unify the marker filter/sort/type rendering.

## Migration

- Lives **inside `UserMarkerRepository`** (single choke point before `loadAll` decodes; both `File` and `Context` constructors).
- **Sidecar version file** in `markersDir` gates it (works for both constructors).
- Raw `JsonObject` pass, idempotent by key presence: for each entry, if `pinned` key present → `icon = if (pinned) icon ?: "📍" else null`, then **delete the `pinned` key**; single raw write.

## Model

- Drop constructor `pinned`; add body property `val pinned get() = icon != null`.
- Update `override val isPinned get() = pinned` accordingly (→ `icon != null`).
- Remove all `pinned = …` args: `saveMarker`, `updateMarker`, `addTempAutoMarker`, `mergeAutoMarkers`, `setMarkerIcon`, `AutoMarkerManager.createTemp`, `togglePin`.

## Pin action

- VM `togglePin(id, pinned: Boolean)` → `setMarkerIcon(id, if (pinned) icon ?: "📍" else null)`.
- Thread the boolean through `MapScreen` (~2369) and `MarkerDrawer` (~193).
- Wizard sets `icon = form.icon` (drop pinned arg).
- `MarkerOverlay`: `skipDots = icon != null && confirmed`; icon loop `marker.icon ?: continue`.

## Filter / sort / labels

- Geometry split: `ALL / PINS / CIRCLES / CORRIDORS` (Pin/Circle/Corridor). Keep hidden `"ZONES"` alias in `geometryMatches`; origin predicate bypasses on `CIRCLES`/`CORRIDORS`; `FilterAxisSpec.dependsOnValue` becomes `List<String>`.
- Origin sort: `compareByDescending { it.origin }` (Manual-first under default descending).
- Marker "pinned" axis → label "Icon", options "With icon"/"Without icon" (serialized keys unchanged).

## Type emojis (card representation only)

- `iconFor` (header glyph) and `typeIcon` (wizard name prefix) → **Pin 📍 / Circle 🎯 / Corridor 🛤️**.
- NOT stored as default: `icon` stays null unless picked manually or set by auto-marker confirmation.

## Verification

- Migration: legacy `pinned=true,icon=null` → 📍; `pinned=false,icon!=null` → icon cleared; idempotent.
- Pin/unpin card + pin-all/unpin-all honor the boolean.
- Geometry filter (4 options) + origin gating + origin sort Manual-first.
- Card header glyphs show 📍/🎯/🛤️.
- Build + E2E.
