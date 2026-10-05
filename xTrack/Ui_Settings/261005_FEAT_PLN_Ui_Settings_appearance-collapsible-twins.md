# Ui_Settings — Tracks/Routes appearance collapsibles read as twins (2026-10-05)

## Purpose

The Layers tab's **Tracks and Routes** card carries two appearance collapsibles — the
**Tracks Appearance** expander (`settings_track_settings_label`, key `track_rendering`) and the
**Routes Appearance** expander (`settings_routes_appearance_label`, key `routes_appearance`) — whose
section order (count, not-pinned ladder, pinned ladder, colours) already matched but whose copy did
not. This pass aligns every caption, description and title-case so the two read as twins.

## Change table — exact old → new

### Expanders

| Key | Old (EN) | New (EN) |
|---|---|---|
| `settings_routes_appearance_label` | Route appearance | Routes Appearance |
| `settings_routes_speed_direction_label` | Route speed and direction | Routes Speed and Direction |

FR: `settings_routes_appearance_label` **Apparence des itinéraires** → **Rendu des itinéraires**
(mirrors the tracks expander's **Rendu des traces**). `settings_routes_speed_direction_label` was
already **Vitesse et direction des itinéraires** and is unchanged.

### New description on the tracks expander

| Key | New (EN) | New (FR) |
|---|---|---|
| `settings_track_appearance_desc` | How recorded tracks are drawn on the map | Comment les traces enregistrées sont dessinées sur la carte |

Drawn as the muted `CardDescription` at the top of the tracks `NestedCard`, mirroring
`settings_routes_appearance_desc` on the routes side.

### Row captions

| Key | Old (EN) | New (EN) |
|---|---|---|
| `settings_routes_count_label` | Nb of routes to render | Not-pinned routes to render |
| `settings_routes_count_desc` | This count limits non-pinned routes only (0-20). A pinned route always renders. | This count limits non-pinned routes only (0-20). Pinned routes always render. |
| `settings_transparency_label` | Not-pinned transparency | Not-pinned tracks transparency |
| `settings_route_transparency_label` | Routes transparency | Not-pinned routes transparency |
| `settings_pinned_transparency_label` | Pinned transparency | Pinned tracks transparency |
| `settings_pinned_route_transparency_label` | Pinned routes transparency | *(unchanged)* |

FR: `settings_routes_count_label` **Itinéraires à afficher** → **Itinéraires non épinglés à afficher**;
`settings_routes_count_desc` **…Un itinéraire épinglé s'affiche toujours.** → **…Les épinglés
s'affichent toujours.**; `settings_transparency_label` **Transparence des non épinglées** →
**Transparence des traces non épinglées**; `settings_route_transparency_label` **Transparence des
itinéraires** → **Transparence des itinéraires non épinglés**; `settings_pinned_transparency_label`
**Transparence des épinglées** → **Transparence des traces épinglées**.

### Ladder descriptions — one shape

| Key | New (EN) |
|---|---|
| `settings_transparency_desc` | Fade across the not-pinned tracks, newest to oldest. 0% = opaque, 100% = invisible. |
| `settings_route_transparency_desc` | Fade across the not-pinned routes, newest to oldest. 0% = opaque, 100% = invisible. |
| `settings_pinned_transparency_desc` | Fade across pinned tracks only, independent of the not-pinned range. 0% = opaque, 100% = invisible. |
| `settings_pinned_route_transparency_desc` | Fade across pinned routes only, independent of the not-pinned range. 0% = opaque, 100% = invisible. |

FR mirrors: **Dégradé sur les traces non épinglées, du plus récent au plus ancien. 0% = opaque, 100%
= invisible.** / the same for **les itinéraires non épinglés**; **Dégradé sur les traces épinglées
uniquement, indépendant de la plage non épinglée. 0% = opaque, 100% = invisible.** / the same for
**les itinéraires épinglés**.

### Colours

| Key | Old (EN) | New (EN) |
|---|---|---|
| `settings_colors_label` | Default Colors | Default colours |
| `settings_colors_desc` | The colours drawn while Colours is off. Past tracks: color gradient from newest (From) to oldest (To). Pinned tracks: amber/orange gradient. | The colours drawn while Colours is off. Past tracks: gradient from newest (From) to oldest (To). Pinned tracks: their own gradient. |

| Key | New (EN) | New (FR) |
|---|---|---|
| `settings_routes_colors_desc` | The colours drawn while Colours is off. Routes: gradient from newest (From) to oldest (To). Pinned routes: their own gradient. | Les couleurs utilisées lorsque Couleurs est désactivé. Itinéraires : dégradé du plus récent (De) au plus ancien (Vers). Itinéraires épinglés : leur propre dégradé. |

FR: `settings_colors_desc` reworded to **…Traces passées : dégradé du plus récent (De) au plus ancien
(Vers). Traces épinglées : leur propre dégradé.** `settings_colors_label` **Couleurs par défaut** is
unchanged. The routes colours block now reads `settings_routes_colors_desc`; the tracks block keeps
`settings_colors_desc`.

## Files touched

- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-fr/strings.xml`
- `app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt` — the new tracks `CardDescription` and the routes colours description swap
- `app/src/test/java/ykws/android/maro/ui/map/TrackRenderStringsTest.kt` — the two new keys pinned, `Default colours` assertion
- `xTrack/Ui_Settings/FEAT_DSC_Ui_Settings.md`

## Outcome

No setting's behaviour changed — copy, one new description line and two new locale keys only.
`apk-build.bat` SUCCESSFUL with no new warning naming a touched file; `gradlew
:app:testDebugUnitTest` at 928 tests completed / 1 failed — the pre-existing, unrelated
`RouteAvoidEngineTest > theFineCellRatioShipsAtFortyPercentOfTheCoarseCell` — so 927 green, matching
the baseline. Device pass owed.
