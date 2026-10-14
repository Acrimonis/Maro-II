<!-- scope: feature -->
# The render count — 0..30 plus an All value

- **Feature:** Tracks (owns the path render family; `MapSelectionPolicy`'s cap is consumed here while
  UI_Map merely surfaces the sliders)
- **Date:** 2026-10-14
- **Status:** SHIPPED 2026-10-14 on `feature/tracks` (its pointer now stands in the feature's `## Implemented`)
- **Type:** user order (the count band widened, plus one uncapped All value)

## 1. The goal

The map's **Tracks** and **Routes** count sliders go from **0..20 to 0..30 plus one All value**. All
**removes the cap**: every not-pinned item of that kind is drawn. Pinned items were already uncapped
and stay so — the pin, not the count, is what exempts them.

## 2. The settled decision — the All fade denominator

Under All the newest-to-oldest fade must spread across all the **not-pinned items actually drawn** —
the recorded set for tracks, the route set for routes, each on its own. The fade denominator is
therefore **the drawn not-pinned set size**, never a raw cap: a raw `Int.MAX_VALUE` denominator would
collapse every line to the newest look.

The same denominator change applies whenever **fewer items exist than the configured count** — the
fade then spans what is drawn rather than leaving the ladder half-used. That is intended, not a side
effect. The route pass already read `routeList.size`; the recorded pass moves from the configured
count to `historyList.size`.

## 3. The one home for the bound

`AppConfig` holds the band's single home beside `pathCount`:

- `PATH_COUNT_MAX = 30` — the largest **countable** count, the top of the 0..30 band.
- `PATH_COUNT_ALL = 31` — the All sentinel, which is also the sliders' top position, so a value of
  0..30 is a count and nothing else is.
- `pathRenderCap(count)` — the one pure helper: All resolves to `Int.MAX_VALUE`, every other value to
  itself clamped into 0..`PATH_COUNT_MAX`.

Every clamp reads that home rather than spelling 20, 30 or 31 for itself: `AppConfig`'s `path.count`
load clamp, `SettingsManager`'s two load clamps, the two sliders, the map pass's
`nbToRender`/`routeNb`, `storedTrackSelection`'s two `cap =` arguments, and `TrackHistoryOverlay`'s
`renderCount`/`routeCount`.

## 4. The sliders

`valueRange = 0f..31f` with `steps = 30` — the 32 positions (0..31) all landing on integers. The
former `steps = 20` over `0f..20f` was off-by-one (spacing 20/21 over 21 grid lines) and is corrected
in passing. The value label reads `settings_count_all` at the sentinel and `"%d"` elsewhere.

## 5. Where All resolves, per surface

- **The policy (`MapSelectionPolicy` / `storedTrackSelection`)** — the `cap` argument goes through
  `pathRenderCap`; `take(cap)` already handles `Int.MAX_VALUE`, and the open card's one-id escape
  stays intact (a zero cap still draws the open item).
- **The map pass (`MapTrackOverlayEffects`)** — All resolves to `Int.MAX_VALUE` for the cap; the
  recorded fade's `total` becomes `historyList.size`.
- **The drawer preview (`TrackHistoryOverlay`)** — `renderCount`/`routeCount` resolve All to the drawn
  not-pinned set's own size, so nothing greys and the denominator spans the set; otherwise the value
  clamps into 0..30 as before.

## 6. File list

- `app/src/main/java/ykws/android/maro/config/AppConfig.kt` — the sentinel, the band's max and
  `pathRenderCap`; `path.count`'s KDoc and its load clamp
- `app/src/main/assets/maro.properties` — the `path.count` comment (the shipped value stays `5`)
- `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` — the two KDoc notes and the
  two load clamps
- `app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt` — both sliders and both
  value labels
- `app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt` — the two configured counts,
  `storedTrackSelection`'s two `cap =` arguments and the recorded fade's `total`
- `app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt` — `renderCount` / `routeCount`
- `app/src/main/res/values/strings.xml`, `app/src/main/res/values-fr/strings.xml` — the two descs to
  (0-30) and the new `settings_count_all` key
- `app/src/test/java/ykws/android/maro/data/model/MapSelectionPolicyTest.kt` — the uncapped-cap case
- `app/src/test/java/ykws/android/maro/config/PathRenderCapTest.kt` — the helper's own cases

## 7. Out of scope

Nothing beyond the above: no dependency, no new surface, no change to pinned semantics, and no change
to the counts' persistence keys.

## 8. Outcome

Shipped 2026-10-14 on `feature/tracks`: the band widened to 0..30 with the 31st position reading All,
the bound took one home in `AppConfig` and the fade's denominator became the drawn not-pinned set on
each kind. The scoped `ui.map` + `config` tests run green with the new `PathRenderCapTest` and the
uncapped `MapSelectionPolicyTest` case, `assembleDebug` is SUCCESSFUL, and the device pass is owed
over the two sliders, the All stop and the fade's span. An initial copy of this plan was written under
`xTrack/TracksImport/` before the feature's ownership was ruled; this copy under `xTrack/Tracks/` is
the plan of record, and the stray first copy's removal is owed.
