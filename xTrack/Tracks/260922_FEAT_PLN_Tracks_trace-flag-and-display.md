<!-- scope: feature -->
# Trace flag and its display — the field, the two lists, the keys, the colour pair and the card

A route saved from the Route feature is to be distinguishable from a recorded journey. It becomes a **trace**: a
kind flagged on the track, filterable in the lists, drawn as a **role** of the track renderer with its own colour
pair, its own opacity ladder, its own stroke, its own render count and its own two gates on the speed-derived
rendering — and read on its own terms in the track card, which shows what a plan can honestly report and refuses
the actions that assume a measurement. This plan is the requirement set and the mechanism for that work, written
against the code as it stands on 2026-09-22, with nothing left open in it.

## 1. What already exists, so the build does not rebuild it

- **The fact has a home.** [`Track.trace`](../../app/src/main/java/ykws/android/maro/data/track/Track.kt:58) at
  `@ProtoNumber(19)` is written by [`TrackFromCourse`](../../app/src/main/java/ykws/android/maro/data/track/TrackFromCourse.kt:96) alone, and
  its own KDoc calls it "the only thing that distinguishes a saved route". A trace **is** that fact, so no new
  field is added and no new wire number is spent.
- **The lists share one axis set.** [`trackFilterAxes()`](../../app/src/main/java/ykws/android/maro/data/model/ListFilter.kt:132)
  drives the drawer's `FilterControl` and the track history overlay alike, so a single `FilterAxisSpec` reaches
  both lists by construction — and the drawer's two filter controls and their link toggle govern it unchanged.
- **The round trip is already carried.** [`GpxExporter`](../../app/src/main/java/ykws/android/maro/data/track/GpxExporter.kt:61) writes a
  base64 protobuf of the **whole** `Track` into the file's `<extensions>`, and the importer rebuilds from that
  blob — so the flag survives Maro→Maro with no work.
- **The colour controls ship.** [`ColorRow`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:1871),
  [`ColorPairRow`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:324) and the shared
  [`ColorPickerDialog`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:1916) already serve the active, past,
  pinned and halo colours.
- **The saved route already carries its own figures.** [`TrackFromCourse.build`](../../app/src/main/java/ykws/android/maro/data/track/TrackFromCourse.kt:82)
  writes the six summary values from the plan's own numbers, and its `createdAtMs` is documented as "the instant
  the save happened, which dates and names the track" — the one field whose meaning changes for a route (§8).
- **The role chain is one shape:** a key in `maro.properties` → a `buildConfigField` in
  [`app/build.gradle.kts`](../../app/build.gradle.kts:104) → the `AppSettings` default
  ([`SettingsManager.kt`](../../app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt:289)) → the user's override in
  prefs. Every trace value below rides it.
- **Four dead keys and three unused fields sit in that chain, and they go with this work.** The four
  `tracking.color.*` keys are injected with `propInt`, whose `toIntOrNull()` drops any value above `Int.MAX` and
  whose `coerceIn(0, 100)` would clamp one that fits, so they never apply — and `trackingColorHistory`,
  `trackingColorHistoryEnd` and `trackingColorPinned` are read by nothing that draws. The trace pair is injected
  through a `propColor` helper instead, and the keys and fields are removed (§6, §10).
- **The two engines that searched the water are already gone**, with their bake, their proto, their artifact and
  their tests, removed in this branch's earlier delivery and held in the two documents `xTrack/Route/` carries —
  so nothing here retires an algorithm and no step hunts for one.

## 2. The requirements are not here

- Every requirement of the trace work lives in the master book, [`../Route/260922_FEAT_PLN_Route_ask-policy-and-target-validity.md`](../Route/260922_FEAT_PLN_Route_ask-policy-and-target-validity.md), as **R29 to R42**: the flag and its index, the export, the filter, the colour pair, the opacity ladder, the stroke, the count, the two gates and their senses, the card, the header, the two refusals, and the removals.
- The mechanism below is this plan's own and is written against that book — where the two disagree, the book wins and the disagreement is a defect in this file.

## 3. The flag and the index

- **The rename is the whole of the field's change:** the property and its KDoc in
  [`Track.kt`](../../app/src/main/java/ykws/android/maro/data/track/Track.kt:55), its writer
  [`TrackFromCourse.kt:96`](../../app/src/main/java/ykws/android/maro/data/track/TrackFromCourse.kt:96), and the two KDocs that name it
  by hand — [`RouteResult.kt`](../../app/src/main/java/ykws/android/maro/data/model/RouteResult.kt:32) and
  [`TrackViewModel.kt`](../../app/src/main/java/ykws/android/maro/data/track/TrackViewModel.kt:242). The number, the default and every
  reader's behaviour stay as they are.
- **The flag must reach the summary, because the lists never load a track.** Both lists work from
  [`TrackSummary`](../../app/src/main/java/ykws/android/maro/data/track/Track.kt:85), so the flag is added there as
  `@ProtoNumber(19)` (18 is `landPointCount`) — the same shape the position axis needed for its two counters, and
  what makes both the card and the action rules answerable without loading points.
- **The projection is one line where the index is built.** [`TrackRepository`](../../app/src/main/java/ykws/android/maro/data/track/TrackRepository.kt:306)
  already opens every track in that pass to write the summary, so `trace = track.trace` rides a loop that is
  already reading the track.
- **An index written before the field exists reads `false`**, a missing bool decoding as off — so the filter would
  answer nothing until the index is rebuilt. **One rebuild of the summary index on the upgrade that introduces
  the field**, through the pass that already exists, is the answer; a sentinel for "never classified" would need a
  meaning of its own and buys nothing here — and the rebuild is **forced rather than hoped for**: the index
  carries a version stamp and the field's introduction bumps it, so the pass runs once on the first launch after
  the update, and a step that added no stamp would leave the filter answering nothing.

## 4. The filter axis

- **One `FilterAxisSpec`** keyed `trace` with its label and three option labels as string resources in both
  locales (`filter_option_all` already exists): **All** (default) · **Tracks** · **Traces**, plus one branch in
  [`TrackSummary.matchesFilter`](../../app/src/main/java/ykws/android/maro/data/model/ListFilter.kt:69), whose `else -> true` leaves
  any other axis inert.
- **Its model is the last axis added:** [`260919_FEAT_PLN_Tracks_position-filter.md`](260919_FEAT_PLN_Tracks_position-filter.md)
  carries the same shape — the dropdown declaration, the predicate branch, the summary-side field that makes the
  axis answerable at list time, and the linked-map note below.
- **The link is the shipped one and no per-axis code is written for it.** The drawer carries a filter control for
  the list, a second for the map and one toggle between them; while they are linked a list choice writes the map's
  filter too, so choosing **Traces** also governs what the map draws, and unlinked the list's choice is its own.
- **A live track stays exempt**, as it already is from the date and position axes: a recording in progress is not
  a trace and must not vanish from the list mid-journey.
- **No marker axis is added**: a marker cannot be a trace, so `markerFilterAxes()` gains nothing.

## 5. Survival through an export

- **Maro→Maro needs nothing added to the standard elements.** The blob of §1 is the carrier, read by
  [`GpxImporter`](../../app/src/main/java/ykws/android/maro/data/track/GpxImporter.kt:21) and written by the exporter whose precedent is
  [`260714_FEAT_PLN_Tracks_gpx-extension-roundtrip.md`](260714_FEAT_PLN_Tracks_gpx-extension-roundtrip.md).
- **A foreign file cannot carry the flag, and its default is the one wanted:** a `.gpx` from another app has no
  blob, so `buildForeignTrack` builds a track whose `trace` is `false` by the field's own default.
- **Nothing is written for the third-party case** — a decision taken on consistency with the app's own behaviour:
  the blob is how Maro already moves its own data, and GPX's standard `<type>` element would buy only a partial
  promise, since a tool that drops the element loses the flag anyway. The loss is the known cost and is named
  rather than hidden: a file edited elsewhere comes back as an ordinary track, its name, points and times intact,
  and its kind gone.

## 6. The keys, and the taxonomy they obey

- **The rule: a value joins its family, and a trace is a role inside those families.** The colour pair joins the
  colour pairs, the opacity pair joins the transparency pairs, the stroke joins the strokes, the count joins the
  counts, and only the two gates take a name of their own. The objection, stated once: a trace's values then live
  in three families, so "everything about a trace" is a search rather than a single prefix — accepted, because
  re-use beats a parallel subtree and every editor of track rendering already reads those families.
- **`tracking.color.traceFrom` / `tracking.color.traceTo`** — the colour pair, matching `tracking.color.pastFrom` /
  `pastTo`, the shipped precedent for a user-editable pair. `from` is the newest trace, `to` the oldest,
  interpolated across the trace set as the history pair is across the historical one.
- **`tracking.transparency.traceFrom` / `tracking.transparency.traceTo`** — the opacity ladder, matching
  `tracking.transparency.from` / `to`, in the app's convention (0 = opaque, 100 = invisible).
- **`tracking.trace.render.nb`** — the trace render count, default 5 and bounded like its sibling
  [`tracking.render.nb`](../../app/src/main/assets/maro.properties:201), which goes on counting stored tracks alone.
- **`track.width.trace`** — the stroke, joining `track.width.live`, `.selected`, `.newest`, `.pinned`, `.history`.
- **`tracking.trace.allowSpeedColor=false`** and **`tracking.trace.allowSpeedArrows=true`** — the two gates, the
  trace-scoped versions of the drawer's **Colours** and **Arrows** switches, whose ramp already replaces
  `trackingColorPast*` and `trackingColorPinned*` wherever a stored track is drawn
  ([`SettingsManager.kt`](../../app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt:264)). A route's
  direction is its origin to its destination, so the arrow pass has one without a bearing being stored on a
  planned vertex, and nothing is filled at save for the arrows to point the right way.
- **The gates live as `AppSettings` booleans beside `trackArrows`**, their shipped defaults injected from the keys
  above like every other value in the family: the file holds the default, `AppSettings` holds the live value, and
  a user-facing chip for traces would cost nothing later.
- **The colour pair is injected through a new `propColor` helper** that reads the app's live colour format
  (`#AARRGGBB`, as `route.line.color` and `map.navigation.line.color` already use), because the family's
  `propInt` cannot carry an ARGB value (§1). The four keys it replaces are removed in the same pass, with the
  three `AppSettings` fields, their `BuildConfig` fields and their prefs keys that nothing painted from, and the
  dead preferences are **cleaned from an installed app** rather than left behind. A value `propColor` cannot read
  falls back to its sibling's literal and the app **shows an error at start**, since a silent fallback is the trap
  this finding is about.
- Each remaining key carries its reader, its bounds and its KDoc, and no value is decided twice.

## 7. The rendering and the rows that configure it

- **The trace pair joins the Colours group of the Tracks section**, where
  [`ColorPairRow`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:324) already serves past and pinned
  tracks and a [`ColorRow`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:319) the active one; both
  swatches open the shared picker.
- **The opacity ladder re-uses the pinned pair's control** — the range slider that already writes
  [`trackingTransparencyPinnedNewest`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:298) and `Oldest` —
  writing `trackingTransparencyTraceNewest` / `TraceOldest`.
- **The render count gets its own row, "Nb of routes to render"**, beside the existing render-count row and
  bounded the same way.
- **The guideline's rule the new rows obey:** the tappable swatch **is** the whole control, never a text action
  beside it, and its `contentDescription` carries the row's own label
  ([`ui-component-guidelines.md`](../../docs/ui-component-guidelines.md:172)). Whether the shipped
  [`ColorSwatchButton`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:1793) needs a label argument for
  that is verified at build time.
- **The pin never changes a trace's rendering.** The appearance is chosen in
  [`MapTrackOverlayEffects`](../../app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt:202), where a history track takes
  the past pair and a pinned one the pinned pair; a **trace is decided first and takes its own pair and its own
  ladder whatever its pin says**, so there is no pinned variant of either value and the pinned pass never claims
  a trace. The one thing the pin does change is the count: a pinned trace is drawn whatever the count says, which
  is what lets the pin mark a route already saved.
- **Everything else is re-use.** The pair interpolates through the same
  [`computeTrackPolylineAppearance`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:430) and
  [`trackFadeAlpha`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:416), with the trace set supplying the index and
  the total in place of the history set's; the count bounds the trace set alone; the stroke is the same stroke at
  `track.width.trace`; and no helper is forked for a role the machinery already draws.
- **A per-trace colour stays out of scope:** `trackColorArgb` keeps its two writers, the merger and the importer,
  and is read by nothing that draws.

## 8. The card, its header, and the actions a route refuses

- **The stats grid keeps its structure and loses three cells.** It is a 3-column × 2-row grid today — Total · Nav
  · Avg over Dist · Idle · Max ([`TrackHistoryOverlay`](../../app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt:702)) —
  and for a route the same three-column shape carries **Dist · Total · Avg** alone: no cell is added, none is
  invented, and nothing else in the card moves.
- **Two of the three read as estimates, and the words carry that, not the arithmetic.** Total is the plan's
  allotted time and Avg the pace it was priced with, so each takes a label saying so in both locales, while Dist
  needs no marker: a route's length is measured off its own line and is as true as a recording's.
- **The header carries the route's own instant.** [`TrackFromCourse.build`](../../app/src/main/java/ykws/android/maro/data/track/TrackFromCourse.kt:55)'s
  `createdAtMs` is the save instant today, and it both dates and names the track — so a route is given the instant
  of its **creation** instead, which leaves the header showing a date and time that belong to the plan, the name
  following the same stamp, and the point count beside them with **no end time**.
- **The point count is the plan's own vertex count**, the legs plus the start, which is what the card already
  counts from the summary and what `TrackFromCourse` builds into the polyline.
- **Resume is refused in one home, not in two guards.** The row's resume button is already conditional —
  `summary.endTimeMs != null && !isRecording`
  ([`TrackHistoryOverlay`](../../app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt:561)) — and the bulk bar reaches the same
  action by its own path ([`OverlayLayer`](../../app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:758)), so the refusal is a
  single predicate on the summary that both readers consult, rather than a clause added twice and left to drift.
- **Merge is refused as a candidacy rather than as an action.** A route is a line between two points, not a leg
  of a journey, and merging one with a recording would produce a track whose speeds are half plan and half
  measurement with nothing on screen saying so — so the merge's own candidate set excludes a trace, which also
  keeps the action consistent with the field's meaning.
- **What a route keeps, unchanged:** distance, date, comment, pin, delete, export, navigate-to, the row's tap and
  the card's own edit fields — none of them assumes a measurement, so none of them changes.

## 9. Suggested, not built in this round

- **"Follow again" where Resume sits for a recording** — a route is the one stored thing a user could ask the app
  to sail, and the action would sit exactly where a recording offers Resume, which a route refuses. It is
  **recorded as a suggestion and deliberately left out of this work**, because following a stored course needs the
  engine seam to accept a course rather than two ends, which is a question of its own and belongs to the Route
  feature's seam rather than to a card.
- The cost of leaving it out is stated rather than hidden: a route's card carries no forward-looking action at
  all once Resume and merge are refused, so the row is a read-only record until the seam can be handed a course.

## 10. The steps, in the order they land — each with the work it owns

1. **The flag and the index** (R29) — `data/track/`: `Track.kt` for the rename, its KDoc and `TrackSummary`'s own
   proto 19; `TrackRepository.kt` for the projection and for the rebuild it forces, **by bumping the index's
   version stamp** (§6); `TrackViewModel.kt` for the KDoc that names the flag. No string.
2. **The axis** (R31) — `data/model/`'s `ListFilter.kt` for the `trace` key and its predicate branch, with the
   live-track exemption; the axis label and its three options (**All · Tracks · Traces**) in
   `res/values/strings.xml` and `res/values-fr/strings.xml`.
3. **The keys and their readers** (R32, R33, R35, R36, R42) — `app/src/main/assets/maro.properties`,
   `app/build.gradle.kts` for the `propColor` helper and the new fields with the four dead ones gone, `config/`'s
   `AppConfig.kt`, and `data/settings/`'s `SettingsManager.kt` — whose four new values join their siblings while
   the three unread fields, their prefs keys and the stored preferences leave in the same pass (§1).
4. **The rendering** (R34, R37, R38) — `ui/map/`'s `MapTrackOverlayEffects.kt` for the trace role decided before
   the pinned one, and `MapScreen.kt`'s appearance helper for the pair, the ladder and the stroke.
5. **The Settings rows** (R32, R33, R35, R36) — `ui/map/`'s `MapScreenSettingsOverlay.kt`: the colour pair, the
   opacity range, the count row and the two gates, each label a `@StringRes` in both locales.
6. **The card and the refusals** (R39, R40, R41) — `ui/map/`'s `TrackHistoryOverlay.kt` for the three cells, the
   header and the resume guard, `OverlayLayer.kt` for the bulk action and the merge's candidates, and the two
   estimate labels in both locales; the creation stamp is the Route plan's save step, which lands first by the
   order stated there.
7. **The hand-off** (R29) — `data/track/`'s `TrackFromCourse.kt` writes the renamed flag, the marker question
   closing on the axis being its face (R31), and the Route epic's rule losing its clause rather than gaining one.
8. **Tests** — the behaviours of §12, each landing with the step that introduces it and in the test package of the
   subject it reads, the round trip through the blob with the flag set among them.

## 11. The files it touches

- **`data/track/`** — `Track.kt`: the flag's rename with its KDoc, and the flag added to `TrackSummary` as proto
  19. `TrackFromCourse.kt`: writes the renamed flag and takes the route's creation instant. `TrackRepository.kt`:
  the projection in the index pass, and the one rebuild that makes an existing index answer.
- **`data/model/`** — `ListFilter.kt`: the `trace` axis in the track axis set and its branch in the predicate.
- **`data/settings/`** — `SettingsManager.kt`: the four new values beside their siblings, and the three unread
  fields with their prefs keys removed.
- **`config/`** — `AppConfig.kt`: the readers for the trace keys the build script does not carry.
- **The build script** — `app/build.gradle.kts`: the `propColor` helper, the new `buildConfigField`s, and the four
  dead colour fields removed with the rows that injected them.
- **The properties** — `app/src/main/assets/maro.properties`: the trace keys added, the four dead keys removed.
- **`ui/map/`** — `MapTrackOverlayEffects.kt`: the trace role decided before the pinned one.
  `MapScreenSettingsOverlay.kt`: the colour pair, the opacity range, the count row and the two booleans.
  `TrackHistoryOverlay.kt`: the card's three cells with their two labels, its header, and the resume guard.
  `OverlayLayer.kt`: the bulk resume's predicate and the merge's candidate set.
- **The strings** — `res/values/strings.xml` and `res/values-fr/strings.xml`: the axis label and its three
  options, the two estimate labels, the count row, the two gates, and whatever the removals retire.
- **The tests** — the test source set, in the package matching each subject: the axis and the refusal predicate,
  the stamp and the projection, the rendering role, and the card's cells.

## 12. Verification

- **What the tests pin:** the axis both ways on a summary, a live track exempt from it, the projection in the
  repository pass, a pinned trace drawing as an unpinned one, each gate honoured where it is read, the pair and
  the ladder interpolating over the trace set alone, the count bounding the trace set alone, the refusal
  predicate read from both of its callers, the creation stamp dating and naming a saved route, the blob round trip
  with the flag set, and a foreign import defaulting to off.
- **The build is green twice:** `gradlew :app:compileDebugKotlin` clean, then `apk-build.bat` and
  `:app:testDebugUnitTest` green with no failure.
- **The device pass stays the user's** and is the one thing this plan cannot settle: a route's look on the map
  beside a recording, the card's three cells and its header, the filter's three values, and the count's effect on
  how many routes are drawn.

## 13. What this plan is not

- **Not a Route plan:** the requirements are the master book's, [`../Route/260922_FEAT_PLN_Route_ask-policy-and-target-validity.md`](../Route/260922_FEAT_PLN_Route_ask-policy-and-target-validity.md); the Route feature's own build order — shipped on 2026-09-22 and since archived — sequenced the Route work first, because the two
  share `TrackFromCourse.kt` and `MapScreen.kt`: its save writes the flag this
  plan renames.
- **Not a corpus entry yet:** the epic's `## Docs` pointer and the walk items land with the build, so this file
  stays the plan in design until its pointer appears in the feature's `## Implemented`.
