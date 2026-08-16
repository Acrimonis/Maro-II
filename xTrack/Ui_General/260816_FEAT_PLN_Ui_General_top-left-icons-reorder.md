# Top-left status icons — reorder + GPS always visible

**Feature:** Ui_General
**Status:** plan
**Created:** 2026-08-16 08:15 UTC
**Branch:** feature/GPS-ui

## Goal

Reorder the top-left status icons to **GPS → Tracking → Land/Water**, and show the
GPS icon in demo mode (grayed out / disabled).

## Current layout

[`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2678) top Row
(left → right): `EarthWaterIcon` (🌊/🏔️) → `TrackStatusIcon` (🚤) →
`GpsStatusIcon` (📡, only `if (appSettings.gpsMode)`) → `RecenterButton` (📍, GPS-only).

## Changes

1. `MapScreen.kt` top Row:
   - `GpsStatusIcon` first, **always rendered** — remove the `if (appSettings.gpsMode)`
     guard. `gpsIconState` already derives `DEMO` when `!gpsMode`, so the gray 📡 shows.
   - `TrackStatusIcon` second.
   - `EarthWaterIcon` third.
   - `RecenterButton` unchanged — last, still `if (gpsMode && autoFollowSuppressed)`.
2. `TrackStatusIcon.kt` — replace 🚤 emoji (line 98) with 🐾 paw prints (U+1F43E),
   and update the KDoc (line 33) accordingly.
3. KDoc touch-up in `MapScreen.kt` (`GpsStatusIcon` "left of EarthWaterIcon" → leftmost).

## Decision

🐾 paw prints (U+1F43E) for the tracking icon (chosen 2026-08-16).

## Portrait constraint

Single shared Row — both orientations affected identically; no orientation branch.
