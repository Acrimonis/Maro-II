# 260923 · Tracks — the word `trace` leaves, and the pair is **Track** against **Route**

**Status:** shipped 2026-09-23, uncommitted — one Code hop and a second closing the Ask hop's should-fixes; `apk-build.bat` SUCCESS and the full suite green at 591 tests. The user's own words: *trace should go, it needs to be Track vs Route*. **Deviations:** §6's premise was half-stale — the spec already shipped the `"ROUTES"` option id while the matcher still read `"TRACES"`, so that axis option had matched nothing until this pass made the two agree — and three test classes were renamed, not four. **Left as the record:** the shipped plan `260922_FEAT_PLN_Tracks_trace-flag-and-display.md` and every `## Implemented` entry, which record what shipped under the word of the day.

## 1. The rule

The app stores two kinds of planned or recorded line, and they have two names:

- a **Track** — a journey the recorder wrote, whose speeds are measurements;
- a **Route** — a line the route mode planned and saved, whose speeds are what the engine intended.

The word **trace** named the second one and is now gone from the code, the keys, the labels and the living docs. What does not change: the flag's two homes on `Track` and `TrackSummary` keep `@ProtoNumber(19)` (a rename is not a renumber), the pin stays the track's own persisted field, and no shape of the settings moves.

## 2. The flag and the domain

| Today | After | Sites |
|---|---|---|
| `Track.trace` | `Track.route` | [`Track.kt`](../../app/src/main/java/ykws/android/maro/data/track/Track.kt:60) `@ProtoNumber(19) val route: Boolean = false` |
| `TrackSummary.trace` | `TrackSummary.route` | [`Track.kt:131`](../../app/src/main/java/ykws/android/maro/data/track/Track.kt:131), with `resumeAllowed get() = !route && endTimeMs != null` and `mergeCandidates`'s `!it.route` |
| `trace = true` at the save | `route = true` | [`TrackFromCourse.kt:107`](../../app/src/main/java/ykws/android/maro/data/track/TrackFromCourse.kt:107) |
| the index projection and the two summary builders | `route` | [`TrackRepository.kt:340`](../../app/src/main/java/ykws/android/maro/data/track/TrackRepository.kt:340), [`OverlayLayer.kt:512`](../../app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:512) and `:605` |
| the prose that calls it a trace | *route* | the KDocs in [`TrackViewModel.kt:242`](../../app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt:242), [`RouteResult.kt:32`](../../app/src/main/java/ykws/android/maro/data/model/RouteResult.kt:32), [`StatCell.kt:22`](../../app/src/main/java/ykws/android/maro/ui/components/StatCell.kt:22) |

## 3. The rendering role

| Today | After | Site |
|---|---|---|
| `TrackRenderPath.TRACE` | `TrackRenderPath.ROUTE` | [`MapTrackOverlayEffects.kt:872`](../../app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt:872) and its readers |
| `traceTrackRenderPlan(…)` | `routeTrackRenderPlan(…)` | `:538` |
| `storedTrackWidth(trace = …)` | `storedTrackWidth(route = …)` | `:446` |
| `trackRenderPlan(trace = …)` | `trackRenderPlan(route = …)` | `:662` |
| `StoredTrackSets.traces` | `StoredTrackSets.routes` | `:467`, filled by `storedTrackSets` |
| `storedTrackSelection(traceNb = …)` | `routeNb = …` | `:507` |
| `traceIds`, `traceSpeedColour`, `traceSpeedArrows` | `routeIds`, `routeSpeedColour`, `routeSpeedArrows` | `:735`, `:761`, `:805`, and their callers in [`MapScreen.kt:2458`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2458) |
| `traceRenderNb`, `trackingColorTrace*`, `trackingTransparencyTrace*`, `traceSpeedColor`, `traceSpeedArrows` | `routeRenderNb`, `trackingColorRoute*`, `trackingTransparencyRoute*`, `routeSpeedColor`, `routeSpeedArrows` | [`SettingsManager.kt`](../../app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt) throughout, incl. the key constants and their prefs strings |
| the accent map's `traceCount`/`drawnTraces`/`traceAccent` and the two panels | `routeCount`/`drawnRoutes`/`routeAccent` | [`TrackHistoryOverlay.kt`](../../app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt:169) |

## 4. The values

- `maro.properties`: `tracking.trace.render.nb` → `tracking.route.render.nb`, `tracking.color.traceFrom/traceTo` → `tracking.color.routeFrom/routeTo`, `tracking.transparency.traceFrom/traceTo` → `tracking.transparency.routeFrom/routeTo`, `tracking.trace.allowSpeedColor/allowSpeedArrows` → `tracking.route.allowSpeedColor/allowSpeedArrows`, `track.width.trace` → `track.width.route` — **value for value**, the comments' prose with them.
- `app/build.gradle.kts`: `TRACKING_TRACE_RENDER_NB`, `TRACKING_TRACE_ALLOW_SPEED_COLOR/ARROWS`, `TRACKING_COLOR_TRACE_FROM/TO`, `TRACKING_TRANSPARENCY_TRACE_FROM/TO` → the same names with `ROUTE`, each still reading its own renamed key.
- `AppConfig`: `trackWidthTraceDp` → `trackWidthRouteDp`, its loader line and its KDoc.

## 5. The strings

- The EN parentheticals go: `settings_traces_count_desc` (`routes (traces)`), `settings_trace_transparency_label` (`Routes (traces) transparency`), `settings_trace_transparency_desc`, `settings_color_trace_tracks` (`Routes (traces)`) — each reads **routes** alone.
- The resource names carrying the word are renamed in both locales: `filter_axis_trace` → `filter_axis_kind` (its label stays `Kind` · `Nature`), `settings_traces_*` → `settings_routes_*`, `settings_trace_transparency_*` → `settings_route_transparency_*`, `settings_color_trace_tracks` → `settings_color_routes`.
- The FR text needs no rewriting — it already says **Itinéraires** for a route and **Traces** for a track, which is right French — with one consistency fix: `filter_option_routes` reads `Itinéraires`, the word the settings' own rows use, rather than `Routes`.
- **Nothing else in either locale moves.**

## 6. The filter's own ids

`ListFilter`'s axis key `"trace"` → `"route"`, its option ids `"TRACES"` → `"ROUTES"` (`"TRACKS"` and `"ALL"` unchanged), and `FilterAxisSpec(key = "route", labelResId = R.string.filter_axis_kind)`.

**Named consequence:** the filter is persisted (`track_list_filter` · `track_map_filter` hold `ListFilter.format`). A stored filter carrying the old key or id no longer matches, so a Kind axis set before the change reads **All** afterwards — the other axes of that filter still apply. **Rejected:** a legacy read in `ListFilter.parse`; it would be a branch outliving its reason, for a loss this small.

**Named consequence, the second:** the renamed prefs *strings* mean the route colour pair, its opacity ladder, its render count and its two gates return to their defaults on the first launch after the change, exactly as any absent key does.

## 7. The tests

`TrackTraceTest` → `TrackRouteTest`, `TrackTraceRoleTest` → `TrackRouteRoleTest`, `TrackRepositoryTraceIndexTest` → `TrackRepositoryRouteIndexTest`; the renamed parameters, method names and the prose in those files; [`TrackOutlineTest`](../../app/src/test/java/ykws/android/maro/ui/map/TrackOutlineTest.kt), [`TrackRenderFlagsPathTest`](../../app/src/test/java/ykws/android/maro/ui/map/TrackRenderFlagsPathTest.kt), [`TrackRenderStringsTest`](../../app/src/test/java/ykws/android/maro/ui/map/TrackRenderStringsTest.kt) (its expected key list follows the renamed resources), [`TrackFromCourseTest`](../../app/src/test/java/ykws/android/maro/data/track/TrackFromCourseTest.kt) and [`RouteEngineSeamTest`](../../app/src/test/java/ykws/android/maro/ui/map/RouteEngineSeamTest.kt). No assertion's *meaning* changes: the same behaviours, renamed.

## 8. The living records that move

- **The Route book, §1.2 and R29–R42** ([`260922_FEAT_PLN_Route_ask-policy-and-target-validity.md`](../Route/260922_FEAT_PLN_Route_ask-policy-and-target-validity.md:46)) — the section's title and every requirement's wording, both locales' keys included. These are the feature's requirements, not a record, so they are rewritten.
- **The epic's living rules** ([`FEAT_DSC_Tracks.md`](FEAT_DSC_Tracks.md) and [`FEAT_DSC_Route.md`](../Route/FEAT_DSC_Route.md)) — the sentences that name the flag, the filter axis and the settings rows.
- [`docs/ui-component-guidelines.md:682`](../../docs/ui-component-guidelines.md:682) — `the track and trace cards' grids` reads `the track and route cards'`.
- **Not moved:** the shipped plan [`260922_FEAT_PLN_Tracks_trace-flag-and-display.md`](260922_FEAT_PLN_Tracks_trace-flag-and-display.md) and the epic's `## Implemented` entries — they record what shipped under the word of the day, and its pointer stays valid. The `#9` palette key names of that pass are internal to it.
- **Left to the next `#bake`:** `GLOBAL_CONTEXT.md`'s Tracks and Route summaries and the hydration — the root document is state, and the bake writes it.

## 9. Verification

`apk-build.bat` SUCCESS with no new warning, and the **full** `gradlew.bat testDebugUnitTest` green — a rename reaches shared names, so the scoped run would not be evidence. A pre-existing red, if one appears that the previous run also carried, is named as pre-existing rather than fixed here.
