package ykws.android.maro.ui.map

import ykws.android.maro.data.model.markers.UserMarker
import ykws.android.maro.spatial.WhereAmIMatch

// ─────────────────────────────────────────────────────────────────────────────
// The card walk policy — Android-free rules both cards share (plan §1, §3, §4)
//
// The two cards live in different state owners: the marker advance in MarkersViewModel and the track
// one in the screen over TrackViewModel, so no single view-model helper can serve both. What *can*
// have one home is the walk itself — its ordering, the world a card walks and the world its return
// reopens on — and the door an edit entered by: all pure, so all are unit-tested without a device,
// beside the other pure policies (scopeClosed, the inspect rules).
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The walk-advance ordering rule (plan §3), as the ordered candidates rather than one answer: given
 * the id that just departed, the ordered world the card walks and the set of ids to exclude, answer
 * **everything below the gap, nearest first, then everything above it, nearest first**.
 *
 * [advanceAfterDeparture] is this list's head, and the two share this one home, so a caller able to
 * try each candidate in turn — the track delete, whose open may fail on a candidate whose geometry
 * cannot be loaded — takes the whole list instead of the head, and a dead candidate no longer
 * swallows the advance.
 *
 * [excluded] carries what the caller already knows is on its way out (the pending deletions, keyed
 * and un-prefixed by the caller) plus, at every call site, the departed id itself; an id already
 * spent is therefore never stepped onto. A caller with nothing pending passes [excluded] as its own
 * default — the empty set — which is exactly the case the undo path and the startup orphan cleanup
 * are. A [departedId] the world never held yields the empty list, the close.
 */
internal fun advanceCandidatesAfterDeparture(
    departedId: String,
    world: List<String>,
    excluded: Set<String> = emptySet()
): List<String> {
    val from = world.indexOf(departedId)
    if (from < 0) return emptyList()
    return world.subList(from + 1, world.size).filterNot { it in excluded } +
        world.subList(0, from).asReversed().filterNot { it in excluded }
}

/**
 * The walk-advance ordering rule (plan §3): given the id that just departed, the ordered world the
 * card walks, and the set of ids to exclude, answer the id to land on — **the next item, else the
 * previous, else none**.
 *
 * The three results are the three endings of every advance in the app: a deletion, or an edit that
 * leaves the item out of its world, steps onto the neighbour below the gap and only falls back to the
 * one above it when nothing remains below; a world that holds no other item, or a [departedId] the
 * world never held, yields `null` — the close.
 *
 * Pure and Android-free: the same function answers the marker advance in
 * [MarkersViewModel.reconcileOpenCard] and the track advance in the screen's delete path, so the two
 * cards cannot disagree about what "the next item" means.
 */
internal fun advanceAfterDeparture(
    departedId: String,
    world: List<String>,
    excluded: Set<String> = emptySet()
): String? = advanceCandidatesAfterDeparture(departedId, world, excluded).firstOrNull()

/**
 * The world a card reads for its marker (the edit-return plan §3, §4; the family plan §3): a
 * list-opened card reads the list world; an inspect-opened one the map-filtered world its ladder was
 * ranked from, whose departure test is the map filter; and a **map-opened** one the map's own source
 * of truth — a click on the map seats a single item whose standing is not the filter's business, so it
 * is read unfiltered and only a genuine deletion removes it (2026-09-28).
 *
 * The collections are handed in rather than reached for: the two readers live in different state
 * owners — the card's own render in [MarkerDrawer], the resolve-and-close in [MarkersViewModel] — and
 * each reads its own flows, so naming the choice here is what stops the two from drifting apart.
 */
internal fun cardWalkWorld(
    source: DrawerSource,
    listWorld: List<UserMarker>,
    mapWorld: List<UserMarker>,
    mapSourceWorld: List<UserMarker>
): List<UserMarker> = when (source) {
    DrawerSource.LIST -> listWorld
    DrawerSource.MAP -> mapSourceWorld
    DrawerSource.INSPECT, DrawerSource.MENU -> mapWorld
}

/**
 * The world a card's reopen walks (plan §4): its own source, unless that source is the inspect ladder
 * and the screen could not re-seat the cursor on it — an inspect-sourced card with no cursor carries a
 * walk that can never move, both pills coming back disabled, so it drops to the map world, which is
 * the very collection the ladder was ranked from ([cardWalkWorld]).
 *
 * One home for the expression both of its callers used to spell out: the drawer's own delete advance
 * (`advanceMarkerCardFrom`) and the editor's own return.
 */
internal fun cardWalkSource(source: DrawerSource, cursorReSeated: Boolean): DrawerSource =
    if (source == DrawerSource.INSPECT && !cursorReSeated) DrawerSource.MAP else source

/**
 * Whether a card's Prev/Next pills read at their ends, and whether they are drawn at all — the
 * greying decision the marker card's pills render, kept pure so every kind of card is unit-tested
 * without a device (plan §2).
 */
internal data class CardStepEnds(
    val shows: Boolean,
    val atFirst: Boolean,
    val atLast: Boolean
)

/**
 * The greying decision (plan §2): **every door of the item's-list kind greys an end at the index the
 * card lands on** — the management list's panel and the menu chevron alike, both walking an ordered
 * set the state layer steps through, so a menu card at either end reads exactly as the panel's does.
 * The inspect ladder hands over its own ends (a step lands only where the frozen pass knows a target),
 * and an inspect card whose cursor was lost with its open in flight reads as at both ends rather than
 * enabled and dead. A click on the map seats a single item, whose pills are not drawn at all ([shows]).
 *
 * [walkAtFirst] and [walkAtLast] are the ladder's own ends, both null when there is no ladder.
 */
internal fun cardStepEnds(
    source: DrawerSource,
    selectedCount: Int,
    selectedIndex: Int,
    walkAtFirst: Boolean?,
    walkAtLast: Boolean?
): CardStepEnds {
    val isListWalk = source == DrawerSource.LIST || source == DrawerSource.MENU
    val noInspectWalk = source == DrawerSource.INSPECT && walkAtFirst == null
    return CardStepEnds(
        shows = selectedCount > 1,
        atFirst = walkAtFirst ?: (noInspectWalk || (isListWalk && selectedIndex == 0)),
        atLast = walkAtLast ?: (noInspectWalk || (isListWalk && selectedIndex == selectedCount - 1))
    )
}

/**
 * What a write does with the card it may have moved (plan §4) — the decision [MarkersViewModel]'s
 * reconcile takes, kept pure so every ending is unit-tested without a device.
 */
internal sealed interface CardReconcile {
    /** Nothing to do: no card is open, or the card's item still stands in the world it walks. */
    data object Hold : CardReconcile

    /** Move the card onto the neighbour the ordering rule answered. */
    data class Advance(val nextId: String) : CardReconcile

    /** Close the card: its item is gone and the world holds nothing left to land on. */
    data object Close : CardReconcile
}

/**
 * The reconcile decision (plan §3, §4): given whether a Viewing card is open, the id it shows, whether
 * the world it walks still holds that id, its source and the neighbour the ordering rule answered and
 * that world confirms, answer hold, advance or close.
 *
 * An inspect-opened card is the one source that closes rather than advances: its frozen ladder and
 * cursor are the screen's own state, so the state layer closes what it cannot show and leaves the
 * re-seat to that screen's own advance.
 */
internal fun cardReconcile(
    cardOpen: Boolean,
    selectedId: String?,
    worldHoldsSelected: Boolean,
    source: DrawerSource,
    advanceTo: String?
): CardReconcile = when {
    !cardOpen || selectedId == null || worldHoldsSelected -> CardReconcile.Hold
    source == DrawerSource.INSPECT -> CardReconcile.Close
    advanceTo != null -> CardReconcile.Advance(advanceTo)
    else -> CardReconcile.Close
}

/**
 * Whether a wizard ending hands the card back or closes the drawer (plan §4).
 *
 * Only an edit entered from the marker card ([WizardDoor.CARD]) returns: the card stayed behind the
 * wizard inside the same drawer state, so a save and a cancel alike restore it with its selection
 * untouched. A create has no card to return to, and an edit entered from the management list
 * ([WizardDoor.LIST]) keeps the ending that door has always had, the drawer closing.
 */
internal fun wizardEditReturnsToCard(door: WizardDoor, isEdit: Boolean): Boolean =
    door == WizardDoor.CARD && isEdit

/**
 * The marker a whereAmI match carries, whichever arm it is — the match set is the fourth card world
 * (plan §3), and both its arms hold a marker, so the card's render lookup, its editor's resolution and
 * the recording's own snapshot read it through this rather than a `when` at each site.
 */
internal fun WhereAmIMatch.matchedMarker(): UserMarker = when (this) {
    is WhereAmIMatch.ZoneMatch -> marker
    is WhereAmIMatch.LineOfSightMatch -> marker
}

// ─────────────────────────────────────────────────────────────────────────────
// The track card's door, and the referential it closes on (plan §4)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Which door opened the track card, mirroring the marker drawer's [DrawerSource]: the world its walk
 * reads, and therefore which referential write closes it. The track half needs its own enum because the
 * two cards live in different state owners — the marker source in [MarkersViewModel], this one on the
 * screen's own `TrackDrawerState`.
 */
internal enum class TrackCardSource {
    /** A list door — the management list, the track history, a delete-undo: the list world closes it. */
    LIST,

    /**
     * The menu chevron (plan §4): a door of the item's-list kind hands over its list, so the card walks
     * the menu's map-referential set — and a map-filter write closes it.
     */
    MENU,

    /** The spy pick or an armed tap: the frozen ladder, stepped by the cursor, closes on a map write. */
    INSPECT
}

/**
 * R2 for the track card, the twin of [scopeClosed] (plan §4): given the door the card was opened by and
 * the two referentials its change landed in, answer whether that change closes it.
 *
 * A list-opened card walks the list world, so the list filter, sort or reset closes it; the menu chevron
 * and the spy card both walk a map-referential set, so a map-filter write closes them — and the list's
 * own write does not, unless the link carried it into the map world as well (the caller passes both
 * flags, exactly as the marker half does).
 */
internal fun trackScopeClosed(
    source: TrackCardSource,
    inListWorld: Boolean,
    inMapWorld: Boolean
): Boolean = when (source) {
    TrackCardSource.LIST -> inListWorld
    TrackCardSource.MENU, TrackCardSource.INSPECT -> inMapWorld
}
