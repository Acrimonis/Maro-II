package ykws.android.maro.spatial.taut

import java.util.concurrent.atomic.AtomicInteger
import ykws.android.maro.data.model.BoundingBox

/**
 * **What a corridor's terrain is keyed on** (§19.4) — the world's own generation, the box it was harvested
 * for, and the three numbers that shaped it.
 *
 * The key is split in two on purpose. [shapedLike] is the part that has to **match** for a kept terrain to
 * be reusable at all — a moved world, a changed berth, a changed shore offset or a changed abstraction
 * tolerance makes the kept geometry an answer about a different question. [box] is the part that does not
 * have to match: a new corridor lying **inside** the kept one is the whole reason a terrain is kept, and
 * `TautTerrain.holds` is the test that says so.
 *
 * The tolerance rides in the key although it is today a constant fraction of the berth: it is named in
 * §19.4's own list, and a key that omitted a number shaping the result would be a key that cannot be
 * trusted to invalidate one.
 */
internal data class TautTerrainKey(
    val generation: Int,
    val box: BoundingBox,
    val berthM: Double,
    val shoreOffsetM: Double,
    val toleranceM: Double
) {

    /** The shaping numbers alone — everything but the box, which is what containment replaces. */
    fun shapedLike(other: TautTerrainKey): Boolean =
        generation == other.generation && berthM == other.berthM &&
            shoreOffsetM == other.shoreOffsetM && toleranceM == other.toleranceM

    companion object {

        /**
         * **The one factory for the key** (§19.5 C6) — the engine asks for it and [TautTerrain.over]
         * builds it, and the tolerance is derived here rather than multiplied out in both places.
         *
         * A second spelling of the same five fields is the way a key drifts: one side gains a field, or
         * one side computes the tolerance from a different fraction, and the cache then answers about a
         * terrain it was not keyed on — a defect no test can name, because both sides would look right
         * alone.
         */
        fun of(
            generation: Int,
            box: BoundingBox,
            berthM: Double,
            shoreOffsetM: Double
        ): TautTerrainKey = TautTerrainKey(
            generation = generation,
            box = box,
            berthM = berthM,
            shoreOffsetM = shoreOffsetM,
            toleranceM = berthM * TautObstacles.TOLERANCE_FRACTION
        )
    }
}

/**
 * **A corridor's terrain, harvested once and kept for the box it belongs to** (§19.4, b1).
 *
 * A search's inputs divide cleanly. The **terrain** of a corridor — the obstacles ([TautObstacles], with
 * its walls, its corners and its index), the priced zone set ([TautZoneSet]) and the band ([TautBand]) —
 * is a function of the **box**, of the world and of the berth and shore offset, and of nothing else: it
 * does not depend on the aim, on the start, or on the pace. What is never kept is the aim's own vertex,
 * the pairs that touch it, the search or the line — those *are* the answer, and they are recomputed on
 * every move.
 *
 * So the rule for a drag is one containment test, with a matching key: the new corridor must lie inside
 * what a **cold search could have built from it**, not merely inside the box that was kept (§19.5 C2,
 * [`TautGraph.coldBoundBox`]). It removes the harvest from every search after the first, and what it
 * promises about the answer is **never narrower and never dearer** rather than "no input changed": the
 * graph is built over the kept box, so a reused corridor is a superset of cold's, and the acceptance is
 * that the line does not move.
 *
 * **The band is built off the obstacles' own frame**, not off a frame of its own: the corridor keeps one
 * projection for every measurement in the search, and a second frame would make two pieces of arithmetic
 * disagree about the same distance by a factor of `cos(latitude)`.
 *
 * Two rules stand between this and a wrong answer, and both are stated where they are enforced:
 * [TautWorld.generation] invalidates a kept entry, and the shared obstacles' memos are concurrent because
 * a cancelled search may still be reading them (`TautObstacles`' own note).
 */
internal class TautTerrain private constructor(

    /** The key this terrain was harvested under — see [TautTerrainKey]. */
    val key: TautTerrainKey,

    /** The corridor's obstacles: the walls, the corners a line bends on, and the index behind them. */
    val obstacles: TautObstacles,

    /** The priced zones whose own box meets the corridor, with their holes and their corners. */
    val zones: TautZoneSet,

    /** The band's own span source, or null when the band is switched off — the tests' own shape. */
    val band: TautBand?
) {

    /** The box this terrain was harvested for — the corridor the graph is then built over. */
    val box: BoundingBox get() = key.box

    /**
     * **Whether the whole of [inside] lies within this terrain's own box** — the containment half of the
     * reuse rule.
     *
     * Containment rather than equality is what makes the cache useful at all: a drag moves the aim by
     * metres while the corridor is kilometres wide, so the next box is usually inside the last one. What
     * is tested here is not the next *rough* corridor but the largest box a cold search could build from
     * it ([`TautGraph.coldBoundBox`], §19.5 C2): the licence's other half is the key's shaping numbers.
     *
     * The trade is stated rather than hidden: on a **strictly** contained box the graph is built over the
     * kept box, so the search is handed the larger corridor — its obstacles are the same ones, and every
     * vertex or wall outside the new corridor was already outside the corridor the terrain was sized from.
     */
    fun holds(inside: BoundingBox): Boolean =
        inside.latSouth >= box.latSouth && inside.latNorth <= box.latNorth &&
            inside.lonWest >= box.lonWest && inside.lonEast <= box.lonEast

    companion object {

        /**
         * The corridor's terrain: the obstacles harvested, the zones harvested, and the band over the
         * obstacles' own frame — the three things `TautGraph.build` used to gather at its own top.
         *
         * **This is the cold entrance, and today it is the tests' own** (§19.5 C5). The engine enters
         * through [over] instead, because the corridor is sized from the obstacles its harvest found and a
         * first search must not harvest the zones and the band for a box it is about to throw away — and
         * it is a test entrance rather than a production one because a harness reads a corridor the engine
         * has no reason to: a grown box probed from outside, a band read at fixed geometry. Both entry
         * points are named here and in [over] so a reader knows which one their own case is.
         *
         * [cancelCheck] is **asked between shapes of the obstacles, the zones and the band**, so an
         * abandoned harvest stops inside a stretch rather than after it — the engine hands down
         * `context.ensureActive()` (§19.5 C6).
         */
        fun of(
            world: TautWorld,
            box: BoundingBox,
            berthM: Double,
            shoreOffsetM: Double,
            cancelCheck: () -> Unit = {}
        ): TautTerrain = over(
            world = world,
            box = box,
            obstacles = TautObstacles.harvest(world, box, berthM, shoreOffsetM, cancelCheck),
            berthM = berthM,
            shoreOffsetM = shoreOffsetM,
            cancelCheck = cancelCheck
        )

        /**
         * The same terrain over obstacles the caller has **already harvested** — the engine's own entrance,
         * and the reason it exists is work rather than taste.
         *
         * **Its obligation: [obstacles] must have been harvested for [box].** The zones and the band are
         * harvested here, and both answer for the box they are handed; the obstacles answer for the box
         * *they* were harvested over, which is why [TautTerrain.box] is that same box rather than a second
         * one — the graph is built over it, the visibility test walks the walls the obstacles hold, and a
         * caller that passed a wider box with walls harvested for a narrower one would get a graph whose
         * obstacles stop short of its own corridor. The engine satisfies this by harvesting for exactly the
         * box it hands over, after the sizing (§16 step 2).
         *
         * The corridor is sized from the obstacles its harvest found (§16 step 2), so a box that grows is
         * harvested twice *before* anything else is known about it — exactly as it was before this step.
         * Entering through [of] there would harvest the zones and the band for the rough box and throw them
         * away with it, which is a cost b1 must not add to the cold search: taking the obstacles the sizing
         * already computed leaves a first search doing the work it always did, and every search after it
         * doing none of it.
         */
        fun over(
            world: TautWorld,
            box: BoundingBox,
            obstacles: TautObstacles,
            berthM: Double,
            shoreOffsetM: Double,
            /** Asked between shapes of the zones and the band too — see [of]. */
            cancelCheck: () -> Unit = {}
        ): TautTerrain {
            val frame = obstacles.frame
            return TautTerrain(
                key = TautTerrainKey.of(
                    generation = world.generation,
                    box = box,
                    berthM = berthM,
                    shoreOffsetM = shoreOffsetM
                ),
                obstacles = obstacles,
                zones = TautGraph.harvestZones(world, box, frame, cancelCheck),
                band = TautBand.of(world, frame, box, cancelCheck)
            )
        }
    }
}

/**
 * **The kept terrain's invalidator, counted over the layers' own identities** (§19.4, corrected in §19.5
 * C4).
 *
 * A terrain is stale the moment anything behind it moves, and what moves is a **layer**: reading the depth
 * grid's bundled asset replaces the grid object the soundings are read from, and a coastline index or a
 * zone index is built exactly once. The rule is therefore identity rather than a timer — a layer whose
 * object is not the one the generation last counted is a layer that moved, whether it arrived for the
 * first time or replaced the one before it — and it is a rule with a home of its own so that it can be
 * read directly: [`RouteSpatialAdapter`] hands it the three layers it holds, and the count it returns is
 * [`TautWorld.generation`], the value a kept terrain is keyed on.
 *
 * It is deliberately blind to the contour: the contour is derived from the grid and cached against the
 * grid's own identity, so a grid that moves brings its contour with it and the grid's own bump covers
 * both. That is the shape the review found *incomplete* — the contour self-healed while the terrain did
 * not — and this is the answer: the grid's own replacement now bumps as loudly as its first load.
 */
internal class TautTerrainGeneration {

    private val counter = AtomicInteger(0)
    private var coastline: Any? = null
    private var zones: Any? = null
    private var grid: Any? = null

    /** The generation as a search reads it. */
    val value: Int get() = counter.get()

    /**
     * Notes the three layers as they are now and returns the generation afterwards: **bumped once for
     * each layer whose identity moved**, and not at all for a repeat.
     *
     * A `null` identity is a layer that has not landed, and it neither counts nor clears what was counted
     * — so a layer that is absent at first sight and arrives later bumps on arrival, and a null read can
     * never be mistaken for a replacement.
     */
    fun observe(coastline: Any?, zones: Any?, grid: Any?): Int {
        if (moved(this.coastline, coastline)) {
            this.coastline = coastline
            counter.incrementAndGet()
        }
        if (moved(this.zones, zones)) {
            this.zones = zones
            counter.incrementAndGet()
        }
        if (moved(this.grid, grid)) {
            this.grid = grid
            counter.incrementAndGet()
        }
        return counter.get()
    }

    /** Whether a layer is a **different object** from the one already counted — never a content compare. */
    private fun moved(known: Any?, current: Any?): Boolean = current != null && current !== known
}
