<!-- scope: feature -->
# Route — the exit dialog's question syntax

## Ask

Normalize the route dialogs' syntax from a **functional** point of view, taking into account whether the
route has been saved, and settle a **guideline** for how a dialog asks its question and how its buttons
answer it — the user's word of 2026-10-10. Discussion only; nothing here is implemented yet.

## What the dialog is today

- **One dialog, two raisers** (R59, R100): the toggle's off and the back key raise it under
  *Leave the Route mode?* — the followed route's own arrival cue raises the **same** doors under
  *You seem to have reached your destination*, a title-only prompt with no message line.
- **Three doors, always in that order**: `Save Route and exit` (PRIMARY) · `Continue Route` (SECONDARY) ·
  a red third door that reads **`Stop following`** while a track is followed for the route and
  **`Discard`** otherwise.
- **The affirmative door's enablement is state-dependent**: it is greyed unless the line is unwritten —
  `frontUnwritten = plan != null && !isRouteSaved(front) && followedTrackId == null`. A followed *saved*
  route is already a track, so its save door greys too.
- **The third door's label is state-dependent but its action is not**: both labels run the one
  `onDiscardRoute()`, which leaves through the two-phase deferred discard (R92).
- A saved route opened from the track card's resume slot reaches the same dialog and reads
  *Stop following* (§ Rules).

## The mismatch, stated functionally

- **One action, two words.** The same door names a *mode transition* while following (*Stop following*)
  and a *loss* otherwise (*Discard*), so the reader cannot learn one rule for the door.
- **The colour contradicts the cost.** The third door is DANGER in both cases, but when the route is
  already a track the press loses nothing.
- **A dead accent.** §5.6 places the accent on *the one enabled forward action at every instant*; on a
  saved route the accent sits on a disabled save while an enabled red door stands beside it.
- **The arrival cue borrows the exit's vocabulary.** At the destination the third door still offers
  *Discard*, which reads as an offer to throw the route away at the moment it is most meaningful — the
  reason the user's first instinct was a word of its own (*Leave Route*).
- **One key, two moments.** `route_exit_discard` also labels the acquisition panel's and the fan's
  disposal, which happen at a different moment from the exit dialog's ending.

## The normalization — settled 2026-10-10

One axis decides the doors: **is this line already written to a track?** (`isRouteSaved(plan)` or
`followedTrackId != null` — one fact, read once). Three doors stand at every state.

| State | Door 1 | Door 2 | Door 3 |
|---|---|---|---|
| Not written | `Save route and exit` — PRIMARY, enabled | `Continue` — SECONDARY | `Discard route` — DANGER |
| Already written | the save door — PRIMARY, **disabled** | `Continue` — SECONDARY | `Leave` — the accent, no loss |

- **The third door's word follows the cost, never the raiser** (the user's word of 2026-10-10):
  `Discard route` while the line is not yet a track, `Leave` once it already is — so the arrival cue
  carries no vocabulary of its own and changes only the title.
- **The save door keeps its place and disables** (the user's word): three doors at every state, the
  family's shape unchanged.
- **The accent follows the enabled forward outcome**, so on a written route it leaves the disabled save
  door and lands on `Leave`; nothing is lost there, so no door is red.
- **What "written" means**: a followed, unsaved line has no track until the save door writes one, so the
  fact is read on the line rather than on the mode.

## Open points

- **The guideline's reach** — the route dialogs alone, or every three-door dialog in the app, the
  recording exit dialog included.
- **The guideline's home** — `docs/ui-component-guidelines.md` §5.6, which already owns the button family
  and the door order.
- **The recording exit dialog** — named separately, outside this discussion's scope.

## The guideline to settle (home: `docs/ui-component-guidelines.md` §5.6)

- **The title asks one question in the user's voice and ends in `?`; the buttons answer it** — same noun,
  same verb, so a reader can pair each door with the question without reading the message.
- **Every door is a verb-first answer of one to three words**, naming the user's outcome, never a
  mechanism or an internal state (*Stop following* fails this; *Leave* passes).
- **Order is fixed in every dialog**: the forward outcome first, the stay second, the ending last.
- **The role follows the consequence, not the moment**: accent for the surface's forward outcome, neutral
  for staying or costing nothing, red only for what loses work — so a door that costs nothing is never red.
- **A door with no work is removed (or disabled), never renamed**, and a disabled door never holds the
  accent, because the accent marks the action the surface is inviting.
- **The loss door names what is lost** (*Discard route*, *Discard track*), never the mode transition.
- **A state-dependent label is a smell**: if a door must be renamed to stay honest, the doors differ per
  state and are modelled as such — one state, one resolved set.
- **An automatic raiser borrows the doors and brings only its own title**, because the doors are the
  user's answers rather than the raiser's.

## Superseded and still open

- The earlier order of 2026-10-10 — the three button labels (`Leave`, `Leave Route`, `Discard`) plus the
  mark's corner inset as a var at 0.25 of the dot's size — is **not implemented**. The dialog part is
  superseded by this discussion; the inset part is unrelated and still stands.
- The recording exit dialog is out of scope here: this is the route dialogs' syntax alone.

## Outcome

**Shipped 2026-10-10.** The question-and-answer rule lives in [`docs/ui-component-guidelines.md`](../../docs/ui-component-guidelines.md) §5.6 as its own block — the title in the user's voice, the doors answering it verb-first in the fixed forward → stay → ending order, the role following the cost, a door with no work disabled rather than renamed and never holding the accent, the loss door naming what is lost, and an automatic raiser bringing only its own title — with the recording exit dialog named the family's reference and the route exit dialog its two-state case.

**The route exit dialog is one dialog, two raisers (R59, R100), three doors at every state.** One axis decides them — *is this line already a track?* (`isRouteSaved(plan)` or `followedTrackId != null`, read once at the call site): while the line is **unwritten** `Save Route and exit` (accent, enabled) · `Continue` (secondary) · `Discard route` (red); once it is **written** the save door keeps its place and is **disabled**, the accent falls to the enabled forward outcome `Leave` (**not red**, because nothing is lost). The third door's word follows the **cost**, never the raiser, so the arrival cue carries no vocabulary of its own and changes only its title — the label *Leave Route* exists nowhere. The doors are one pure `routeExitDoors(written)` (label, role, enabled) in [`RouteExitDoors.kt`](../../app/src/main/java/ykws/android/maro/ui/map/RouteExitDoors.kt) with its own unit test, and [`MapDialogHost`](../../app/src/main/java/ykws/android/maro/ui/map/MapDialogHost.kt) only paints what it returns.

**Strings.** `route_exit_leave` is new (*Leave* / *Quitter*), `route_exit_continue` now reads *Continue* / *Continuer*, and `route_exit_discard` now reads *Discard route* / *Abandonner la route* for its three readers — the dialog's third door while unwritten, the acquisition panel's door and the fan's Discard child, each of which keeps its *discard the route* meaning. `route_exit_stop_following` retires with the two-state model, having had the dialog as its only reader.

**The recording exit dialog is verified, not rewritten** — its doors already read *Save track* accent · *Continue recording* outlined · *Discard track* red in the settled order, and the earlier shorten-to-*Discard* order is superseded by the loss door naming what is lost.

**The mark's corner inset became a ratio.** `ui.map.pulse.dot.inset.ratio` (0.25) holds the share of the disc's own size the mark is inset from its square's corner, so the placement scales with the disc instead of being a second number to keep in step; `MAP_PULSE_DOT_INSET` derives as `size × ratio` — 3 dp at the 12 dp disc — with `AppConfig` following the palette and `MapPulseDotTest` reading the shipped value. The device pass over the toggle squares stays owed.
