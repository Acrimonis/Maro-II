<!-- scope: feature -->
# UI_Map — the toggle squares' colour normalization

## Ask

Normalize the map's toggle squares onto one constant colour set, the fill saying what the square is
doing and the pulse dot saying what its data is worth — the user's word of 2026-10-09. Duplication
between the fill and the dot is accepted deliberately.

## The constant set — five, all already in the palette

- **Pale** — `semantic.inactive` as the shared surface's own fill: the square is off.
- **Amber** — `semantic.caution`: the square is still getting its data.
- **Blue** — `semantic.info`: the square is on and doing what it is for. **Nominal is blue by house
  taste** — the accent and the sea theme are the same blue — so blue is a deliberate theme choice
  rather than a semantic claim, and no square has to earn it.
- **Green** — `semantic.compliant`: the square is on and standing by, not working.
- **Red** — `semantic.danger`: the thing the square needs is not there.

No new colour is introduced, and no square invents one.

## The two channels

- **The fill** answers what the square is doing: off, getting its data, working, standing by, or
  without the thing it needs. It paints a semantic token at the shared `ui.map.surface.active.alpha`
  with the glyph whole, or the shared pale fill with the glyph dimmed when off — the shared divider
  border untouched on every square, active or off.
- **The dot is the square's data mark, and every mode square except the recenter wears one** — GPS,
  tracking, inspect, route and the lock alike — because each is a mode rather than a one-off action:
  **green when the data is real or complete, amber when it is partial, red when there is none**. It is
  absent on the off square, on the recenter and on the reading squares, which is what tells a reading
  from a control at a glance.
- **The dot's beat stays the shared mark's** — its geometry, its ratio top-right inset and its period
  remain one home, each an accessor over its own `ui.map.pulse.dot.*` setting, and only its colour is the
  caller's.

## The weights

- **The tile is always a tile.** The shared translucent white the family already owns is painted first
  — the very fill the inactive face wears — and the state colour is laid over it at the shared subdued
  tint (`ui.map.surface.active.alpha`), so the square never becomes a window onto the map and its
  apparent weight stops depending on what lies beneath it.
- **The dot is fully saturated.** It paints its state colour at full strength, so it always stands off
  the tinted tile and reads as the crisp mark against the square's softer colour — which is what makes
  the smaller channel legible in the first place.
- **The beat's floor is 33 %**: the pulse keeps its one geometry and its period but its body never fades
  below `0.33`. The old 1 → 0.3 range belonged to a dot that meant only *live*; the mark's own ring is
  what lets the body dip past the first pass's 60 % — and past the 50 % the ring's own pass settled —
  without the state going faint at the bottom of the beat. The floor is its own `ui.map.pulse.dot.floor`
  setting, read through `MapPulseDot`, already the beat's one home.
- **The mark wears a ring and sits inset by its own ratio**: a `ui.map.pulse.dot.ring.width` (1 dp) stroke
  in the mark's own colour at full strength, drawn inside the disc's `ui.map.pulse.dot.size` (12 dp) and
  outside the beat — only the body fades — so the state keeps a crisp edge under it. The square paints the
  mark itself, from its own top-right corner inset by `ui.map.pulse.dot.inset.ratio` of the disc's size
  (0.25, 3 dp), rather than from the padded content box; the marks outside the row keep their own placement.
- **The palette follows**: the alphas baked into the semantic tokens stop mattering to the square,
  which reads the token's colour and applies its own two weights — the subdued tint for the tile, none
  for the dot.

## The table

- **GPS** — off (DEMO) pale, no dot · acquiring (ACQUIRING, WEAK) amber fill, **red** dot · dead
  reckoning (ESTIMATING) amber fill, **red** dot, an estimate being neither real nor complete · a fix
  held, whether the boat is moving or still, blue fill, **green** dot — IDLE carries the healthy face,
  the receiver's eased cadence being no part of what the square reports · fix lost (STALE) red fill,
  **red** dot.
- **Tracking** — off pale, no dot · recording (on and moving) blue fill, green dot · on while the boat
  is still green fill, green dot.
- **Land/water** — never off: **blue** over water (the nominal reading), **red** over land; **no dot**,
  which is how a reading is told apart.
- **Inspect** — off pale, no dot; armed blue fill, **green** dot.
- **Route** — off pale, no dot · searching amber fill, **amber** dot, a provisional line being partial
  · following blue fill, green dot. An unanswered search keeps the amber face; the refusal itself
  stays in the status line. The user's line colour stays on the line alone.
- **Lock** — off pale, no dot; locked blue fill, **green** dot.
- **Recenter** — appears while the follow is paused: blue fill, no dot, an action rather than a mode.

## Two classes of square

- **Mode squares** — GPS, tracking, inspect, route, lock: the fill says what the square is doing and
  the dot says what its data is worth; with every mode dotted, the dot doubles as the on-screen answer
  to *is this a control?*
- **Reading squares** — land/water: never off, the reading as the fill, no dot.

**Out of scope**: the bottom-left zone tags, which are readings of the maritime zones the boat is in,
painted in each zone kind's own category colour and neither toggles nor surfaces this work touches.

## Settled decisions

- **The dot's green means real or complete** — which is why a track that is on while the boat is
  still, an armed inspect and a locked screen all wear green: nothing about their data is missing.
- **Green in the fill means standing by**; the two greens share one token and one hue, so the fill and
  the dot can both be green on the same square for two different reasons — the tracking square while
  the boat is still is the one place that happens.
- **The GPS square has no idle face**: a fix held while the boat is still reads exactly like a healthy
  fix, so STALE is the only way a good position is ever lost.
- **Nominal is blue by theme**, so a blue square says *on and nothing wrong* and the icon carries the
  rest.
- **Dead reckoning is amber with a red dot; a lost fix is red with a red dot**, since a guess is still
  a reading while a lost fix is none.
- **The route's refusal is not a face**: the toggle is never dead by design, so the square stays amber
  while the search has not answered and green once it follows.
- **The zone tags are out of scope.**
- **The beat's floor is 33 %; the mark wears a 1 dp full-strength ring and the square places it at its own
  corner inset by its own `ui.map.pulse.dot.inset.ratio` of the disc's size** (the marks outside the row keep
  their own placement); the recenter
  carries **no dot**, and the tracking square's two greens are **accepted for now** — one hue at two
  weights, which the weights themselves separate.
- **The tint is settled at 0.5** (2026-10-09, the gentle set): the family's white under the colour makes
  every tile paler than it looked before the white base sat beneath it, so `ui.map.surface.active.alpha`
  softened from 0.65 to **0.5**. The two off knobs moved with it — the base white from 66 % to **55 %**
  (`#8CFFFFFF`), still painted under every face, and the inactive content alpha from 0.75 to **0.6**.

## The palette's own changes

- **One tint weight and one saturated dot**: the square's two weights become the family's own settings,
  so no status token carries a weight the square then discards — the token holds a colour, and the
  surface decides how softly it is worn and how sharply the dot repeats it.
- Delete the four off-state tokens nothing reads: `status.gps.demo`, `status.tracking.off`,
  `status.lock.off`, `status.earthWater.inactive` — off is the shared pale square.
- `status.earthWater.water` becomes `semantic.info` (the nominal blue) and `.land` becomes
  `semantic.danger`, so the pair reads water nominal, land hazard.
- `status.gps.estimating` — the one status colour with no key — gains one, and it takes the acquiring
  amber, since dead reckoning is an acquiring face now.
- `status.tracking.*` is renamed to the states it carries, since the swap makes *healthy* the idle
  case no longer; its dot pair is reconciled with `ui.map.pulse.dot`, today a second red for the map's
  own mark.

## Rejected, on the record

- **A binary fill with the dot carrying the state alone** — rejected: it would make a 12 dp disc that
  fades to 30 % the only channel for a state, where a glance at the whole square is what the row is
  for.
- **Green as "good"** — rejected: green in the fill means *standing by*, and the good readings are
  blue, so the row reads *working or not working* rather than *good or bad*.
- **A saturated edge on every active tile** — considered on 2026-10-09 and **set aside for now**: it
  would state the state three times on one square, and a row of saturated rims over a busy map is
  louder than the fill and the dot already are. The divider border stays as it is, and the idea is
  recorded here rather than dropped silently.

## Docs and tests

- `docs/color-scheme.md` and the map-surface section of `docs/ui-component-guidelines.md` carry the
  five-colour table, the two channels and the two classes.
- A unit test pins each square's resolved face and dot colour per state, the shape of the row's own
  `TopToggleControlTest`, and the mark's own geometry — its floor, its inset as a ratio of the size and the
  `ui.map.pulse.dot.*` keys — read from the shipped palette.

## Outcome

**Shipped 2026-10-09.** Every square of the top-left row resolves through one pure home,
`MapToggleFace.kt` — `TopToggleFace` carrying a fill and an optional dot, with `gpsFace`, `trackingFace`,
`earthWaterFace`, `inspectFace`, `routeFace`, `lockFace` and `recenterFace` reading only `AppConfig` —
and `MapSurface`/`MapPulseDot` paint what a resolver returns. `MapSurface` now paints the family's white
base under the state colour at `ui.map.surface.active.alpha` (0.65 as shipped, then **0.5** from the
2026-10-09 values pass); `MapPulseDot` takes its colour as a parameter and beats with the mark's 33 % floor,
while `rememberPulseAlpha` keeps its 30 % default for the non-toggle caller (the trip border); and the
mark's callers outside the row sit on `ui.map.pulse.dot`. The route square stopped reading the user's line
colour — `RouteToggleButton`'s `lineColor` parameter and its call site are retired.

**Six readerless palette keys were deleted, not four** — this plan's four off-tokens plus
`status.tracking.alpha.active` and `status.lock.alpha.active`, on the user's word, each verified to carry
no `AppConfig` property, parser line or reader. `status.earthWater.water`/`.land` were re-pointed to
`semantic.info`/`semantic.danger`, `status.gps.estimating` gained the key it never had on the acquiring
amber, the GPS healthy and idle keys were re-pointed to the nominal blue, and the tracking pair renamed to
the states it carries (`status.tracking.recording`/`.idle`), its dot pair retired onto `ui.map.pulse.dot`.
`AppConfig` follows the palette for every added, changed, renamed and deleted key, parsers included.

Tests: `MapToggleFaceTest` (17) pins each square's resolved fill and dot per state,
`MapTogglePaletteTest` (5) reads the shipped `colors.properties` for the six deletions, the new and
renamed keys, and the three surface weights, and `TopToggleControlTest` (4) stays green. `apk-build.bat` green and the scoped `ui.map` +
`config` unit run green with no new red. `docs/color-scheme.md` and the map-surface section of
`docs/ui-component-guidelines.md` carry the five-colour table, the two channels and the two classes.

**Amended the same day, on the user's word:** the mark gained a **1 dp ring** in its own colour at full
strength, drawn inside the shipped 12 dp and outside the beat so only the body fades — which is what lets
the floor drop from 60 % first to **50 %** and, on the values pass below, to **33 %**. The row squares
stopped placing the mark themselves: the face's dot now rides `MapSurfaceFace`,
`TopToggleFace.toSurfaceFace()` resolves it, and `MapToggleSquare` paints it from the square itself, at its
own top-right corner inset by the mark's one inset constant, `MAP_PULSE_DOT_INSET`
rather than from the padded content box. `MenuDrawerOverlay`'s two marks and the fan's keep their own
placement and gain the ring by being the same component, and the row's `MapToggleDot` helper is retired.
`MapPulseDotTest` pins the floor, the ratio inset and the geometry keys so none of them can drift
silently.

**Amended once more the same day — the mark's whole geometry became settings:** the disc size, the beat
floor, the period and the ring width each left the code for `ui.map.pulse.dot.size` (12), `.floor` (0.33),
`.ms` (555) and `.ring.width` (1) in `colors.properties`, each with `AppConfig` following the palette and
`MapPulseDot` reading it through its own `get()` accessor — the top-right inset then derived as half the
size (6 dp), and only `MAP_PULSE_GENERIC_FLOOR` (30 %, the non-toggle caller's floor) left a code constant.
`MapPulseDotTest` now reads the shipped palette for the floor, the inset and the four keys.

**Amended again on 2026-10-10 — the corner inset became a ratio:** the space between the mark and its
square's top-right corner left the code for `ui.map.pulse.dot.inset.ratio` (0.25) in `colors.properties`, so
`MAP_PULSE_DOT_INSET` derives as `size × ratio` — 3 dp at the 12 dp disc — and the placement scales with the
disc instead of being a second number to keep in step. `AppConfig` follows the palette
(`uiMapPulseDotInsetRatio`) and `MapPulseDotTest` reads the shipped value; the settled period also moved to
**555 ms** in the palette, with `AppConfig`'s default and the test following it.

**Owed:** the device pass over the five faces, the dot's colours, the mark's new ring and inset, and the
three surface weights as now settled — base `#8CFFFFFF` (55 %), inactive content alpha 0.6, active 0.5.
