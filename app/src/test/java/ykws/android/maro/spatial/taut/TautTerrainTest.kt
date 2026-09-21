package ykws.android.maro.spatial.taut

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.BoundingBox
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.model.RouteResult

/**
 * **The terrain kept for a box, read where it can be read** (§19.4, b1).
 *
 * The change is deliberately invisible — it removes work, not geometry — so the assertions here are about
 * the **pair** §19.4 names: the dossier's `terrainReused` beside the work that was not done, with the line
 * compared to a cold engine's so that "the same answer" is a reading rather than a claim. Two controls
 * stand beside it, because a flag that is always true would satisfy the first test alone: a corridor that
 * **leaves** the kept box must harvest, and a world whose **generation moved** must harvest, each of them
 * asserted to answer exactly what a cold engine answers all the same.
 *
 * Each test names the revert it catches, in the way this feature's assertion set does.
 */
class TautTerrainTest {

    private val berthM = 25.0
    private val shoreOffsetM = 50.0
    private val accel = 2.94
    private val longitudinal = 1.96
    private val cruiseKn = 28.0

    /** An island between the two ends, so the line has a bend in it and the answer is not a straight line. */
    private val island = TautTestWorld.rect(43.5450, 7.1150, 43.5570, 7.1250)

    /**
     * **Two coasts far outside every corridor, so the harvest's own extent reaches the sizing's clamp.**
     *
     * §19.5 C2 licences a reuse on the box a cold search could build from the rough guess, and that box is
     * the sizing's clamp (§16 step 2). On a world whose harvested shapes all lie *inside* the corridor the
     * kept box is smaller than that bound and no reuse is ever licensed — which is a property of the world
     * rather than of the cache, so it would hide what the tests below are reading. The real coast is not
     * that world: a coastline running past the corridor reaches the clamp on every side, and the kept box
     * is then the cold bound to the digit. These two rings are the smallest shape that reproduces it —
     * each is clipped away by the harvest and contributes no wall, and together they only widen what the
     * harvest was handed.
     */
    private val farNorth = TautTestWorld.rect(43.62, 7.00, 43.70, 7.25)
    private val farSouth = TautTestWorld.rect(43.38, 7.00, 43.50, 7.25)

    private fun world() = TautTestWorld(
        land = listOf(island, farNorth, farSouth),
        depthBox = BoundingBox(43.4500, 43.6500, 7.0000, 7.2500),
        coastalBandWidthM = 0.0
    )

    /** The island alone with the band switched on — the world the shared-scratch reading needs. */
    private fun bandWorld() = TautTestWorld(
        land = listOf(island),
        depthBox = BoundingBox(43.5200, 43.5800, 7.0800, 7.1600),
        coastalBandWidthM = 300.0
    )

    /** The engine under test, its numbers pinned by the test rather than read from a live file. */
    private fun engine(world: TautWorld) = TautRouteEngine(
        world = world,
        prepareWorld = null,
        berthM = { berthM },
        shoreOffsetM = { shoreOffsetM },
        lateralAccelMps2 = { accel },
        longitudinalAccelMps2 = { longitudinal },
        corridorGrowth = TautRouteEngine.DEFAULT_CORRIDOR_GROWTH,
        warn = {}
    )

    /** The island pair — the same water [TautSearchTest] bends its line on. */
    private val start = RoutePoint(43.5500, 7.1000)
    private val aim = RoutePoint(43.5500, 7.1400)

    private fun success(answer: RouteResult): RouteResult.Success {
        assertTrue("the pair has a route: $answer", answer is RouteResult.Success)
        return answer as RouteResult.Success
    }

    private fun details(answer: RouteResult.Success): TautRouteDetails =
        answer.details as? TautRouteDetails ?: error("the tracer's own dossier is missing")

    /**
     * **The pair §19.4 asks for: the flag, and the work that did not happen.**
     *
     * The first search of a fresh engine harvests (the flag is false, the world is asked for its coastline,
     * the harvest's milliseconds are spent); the second hand the same aim to the same engine reuses the
     * terrain (`terrainReused` true, `landReads` unmoved, a harvest of milliseconds) and answers the **same
     * line** — compared whole, and against a **cold** engine's on the same aim rather than only against the
     * first search, so a cache that answered something slightly different could not pass.
     *
     * The same aim is the case §19.5 C2's licence holds by construction: a cold search rebuilds the very
     * box the terrain was harvested for, so the kept box holds it exactly, and this test is the cache's
     * reading rather than the licence's — which the two tests below it are.
     *
     * Revert it catches: dropping the containment reuse makes the second search harvest, so `terrainReused`
     * and the unmoved `landReads` both fail; keeping a terrain whose key was ignored is caught by the two
     * controls below rather than here.
     */
    @Test
    fun `a kept terrain is reused by the second search and answers the same line`() = runBlocking {
        val testWorld = world()
        val engine = engine(testWorld)

        val first = success(engine.route(start, aim, cruiseKn))
        val firstDetails = details(first)
        val readsAfterFirst = testWorld.landReads
        assertTrue(
            "the first search harvests the corridor's terrain",
            !firstDetails.terrainReused
        )
        assertTrue(
            "and asks the world for its coastline — the harvest and the band both do",
            readsAfterFirst > 0
        )

        val second = success(engine.route(start, aim, cruiseKn))
        val secondDetails = details(second)
        println(
            "terrain reuse · first search: reused ${firstDetails.terrainReused}, harvest " +
                "${firstDetails.harvestMillis} ms, coastline reads $readsAfterFirst · second search: " +
                "reused ${secondDetails.terrainReused}, harvest ${secondDetails.harvestMillis} ms, " +
                "coastline reads ${testWorld.landReads}"
        )
        assertTrue(
            "the second search reuses the terrain the first kept",
            secondDetails.terrainReused
        )
        assertEquals(
            "and asks the world for no coastline at all — the harvest did not run",
            readsAfterFirst,
            testWorld.landReads
        )

        // The same line, on all three of the quantities a reader of the plan sees. The cold engine is a
        // second engine over the same world, so its own first search is a genuinely independent harvest.
        val cold = success(engine(testWorld).route(start, aim, cruiseKn))
        assertEquals("a cold engine on the same aim draws the same vertices", cold.points, second.points)
        assertEquals("at the same leg times", cold.legTimesSec, second.legTimesSec)
        assertEquals("over the same distance", cold.distanceM, second.distanceM, 0.0)
        assertEquals("and to the same second", cold.durationSec, second.durationSec, 0.0)
        assertTrue(
            "the line the comparison is made on really has a bend in it",
            second.points.size > 2
        )
    }

    /**
     * **The reuse rule is containment, not convenience** — the control the test above cannot be.
     *
     * A second pair whose own corridor lies **outside** the kept box must harvest afresh, and its answer
     * must still be exactly what a cold engine answers on that pair: a cache that handed back the first
     * terrain regardless would answer the second pair from another corridor's obstacles, which is the one
     * way this change could corrupt an answer.
     *
     * Revert it catches: replacing the containment test with "is there a kept terrain at all" makes
     * `terrainReused` true here, and the equality against the cold engine is what says the terrain actually
     * had to match the corridor.
     */
    @Test
    fun `a corridor leaving the kept box harvests afresh`() = runBlocking {
        val testWorld = world()
        val engine = engine(testWorld)
        engine.route(start, aim, cruiseKn)

        // South of the island: a corridor disjoint from the one just kept.
        val elsewhereStart = RoutePoint(43.5250, 7.0900)
        val elsewhereAim = RoutePoint(43.5250, 7.1050)
        val answer = success(engine.route(elsewhereStart, elsewhereAim, cruiseKn))
        assertTrue(
            "a corridor outside the kept box is not served by the kept terrain",
            !details(answer).terrainReused
        )
        val cold = success(engine(testWorld).route(elsewhereStart, elsewhereAim, cruiseKn))
        assertEquals("and the answer is a cold engine's own", cold.points, answer.points)
    }

    /**
     * **The invalidator: a world that moved takes its terrain with it.**
     *
     * Nothing here changes what the fake *answers* — its shapes are fixed once it is built — so the reading
     * is the flag alone: bumping the world's generation makes the next search harvest afresh rather than
     * answer from a terrain keyed on the world as it was. That is the rule §19.4 states as *bumped whenever
     * anything a route reads from this world changes*, read at the one point a test can move it.
     *
     * Revert it catches: ignoring `generation` in the terrain key leaves `terrainReused` true after the bump,
     * which is a kept coastline answering about one that no longer exists.
     */
    @Test
    fun `a moved world invalidates the kept terrain`() = runBlocking {
        val testWorld = world()
        val engine = engine(testWorld)
        engine.route(start, aim, cruiseKn)

        testWorld.generation += 1
        val afterTheBump = success(engine.route(start, aim, cruiseKn))
        assertTrue(
            "the bumped generation makes the next search harvest afresh",
            !details(afterTheBump).terrainReused
        )

        // And the terrain kept *after* the bump is reusable again, so the invalidation is a re-key rather
        // than the cache being switched off for good.
        val onceMore = success(engine.route(start, aim, cruiseKn))
        assertTrue(
            "and the terrain harvested for the new generation is kept in its turn",
            details(onceMore).terrainReused
        )
        assertEquals(
            "with the line unmoved across the invalidation",
            afterTheBump.points,
            onceMore.points
        )
    }

    /**
     * **The key's own reading, at the seam's own type** — the three shaping numbers a terrain may not be
     * reused across, and the one that may differ.
     *
     * A berth, a shore offset or a tolerance that changed is a different corridor's terrain; the box alone
     * is what containment replaces. It is asserted on [TautTerrainKey] directly because that is where the
     * rule lives, and because a rule read only through an engine is a rule a reader has to infer.
     */
    @Test
    fun `the key matches on the shaping numbers and the box is what containment replaces`() {
        val box = BoundingBox(43.53, 43.57, 7.08, 7.16)
        val key = TautTerrainKey(0, box, berthM, shoreOffsetM, berthM * TautObstacles.TOLERANCE_FRACTION)

        assertTrue("the same world and the same numbers shape the same terrain", key.shapedLike(key))
        assertTrue(
            "a bump of the world's generation is a different terrain",
            !key.shapedLike(key.copy(generation = 1))
        )
        assertTrue(
            "so is a different berth",
            !key.shapedLike(key.copy(berthM = berthM + 1.0))
        )
        assertTrue(
            "so is a different shore offset",
            !key.shapedLike(key.copy(shoreOffsetM = shoreOffsetM + 1.0))
        )
        assertTrue(
            "so is a different abstraction tolerance",
            !key.shapedLike(key.copy(toleranceM = key.toleranceM * 2.0))
        )
        assertTrue(
            "and a bigger box for the same water is the same terrain",
            key.shapedLike(key.copy(box = BoundingBox(43.50, 43.60, 7.05, 7.20)))
        )
    }

    /** The containment the reuse rests on, read on its own bounds — the terrain's half of the same rule. */
    @Test
    fun `a terrain holds a box only when the box lies inside it`() {
        val terrain = TautTerrain.of(
            world(), BoundingBox(43.530, 43.570, 7.080, 7.160), berthM, shoreOffsetM
        )
        assertTrue(
            "the box it was harvested for is held",
            terrain.holds(BoundingBox(43.530, 43.570, 7.080, 7.160))
        )
        assertTrue(
            "so is a corridor inside it",
            terrain.holds(BoundingBox(43.540, 43.560, 7.090, 7.150))
        )
        assertTrue(
            "a box reaching past one edge is not",
            !terrain.holds(BoundingBox(43.520, 43.560, 7.090, 7.150))
        )
        assertTrue(
            "and neither is one straddling it",
            !terrain.holds(BoundingBox(43.540, 43.580, 7.170, 7.190))
        )
        assertEquals(
            "the terrain's own box is the one it was asked for",
            BoundingBox(43.530, 43.570, 7.080, 7.160),
            terrain.box
        )
        assertEquals(
            "and with the band switched off there is no band to carry",
            null,
            terrain.band
        )
    }

    /**
     * **The band rides in the terrain**, and off the obstacles' own frame.
     *
     * It is the part of b1 that is easy to get wrong by halves: the band is a function of the box and of the
     * world like the rest of the corridor, so it is harvested with the corridor and kept with it, and it is
     * built on the frame the obstacles brought so the corridor keeps one projection. Read here as the
     * terrain carrying a band at all where the world has one, and as that band being over the obstacles'
     * frame rather than a second one invented beside it.
     */
    @Test
    fun `the band is harvested with the terrain, over the obstacles' own frame`() {
        val box = BoundingBox(43.530, 43.570, 7.080, 7.160)
        val banded = TautTerrain.of(
            TautTestWorld(
                land = listOf(island),
                depthBox = BoundingBox(43.5200, 43.5800, 7.0800, 7.1600),
                coastalBandWidthM = 300.0
            ),
            box,
            berthM,
            shoreOffsetM
        )
        val band = banded.band
        assertTrue("the world has a band, so the terrain carries one", band != null)
        val obstacles = banded.obstacles
        // The same frame, read through the one thing both answer in: a point resolved into metres and back
        // is the point itself, and the band's own spans are solved in those metres. The leg runs **110 m
        // south of the island's own edge**, so it stands inside the 300 m strip and the band has something
        // to answer about — a leg in open water would make the last assertion true for the wrong reason.
        val from = obstacles.frame.pt(LatLng(43.5440, 7.1100))
        val to = obstacles.frame.pt(LatLng(43.5440, 7.1300))
        val back = obstacles.frame.latLng(from)
        assertEquals("the frame round-trips a point", 43.5440, back.latitude, 1e-9)
        assertEquals("in both coordinates", 7.1100, back.longitude, 1e-9)
        val spans = band!!.spansOf(from, to)
        assertTrue(
            "the band answers a span over it — the strip the leg passes through",
            spans.isNotEmpty()
        )
        assertTrue(
            "and that span is a part of the leg rather than all of it",
            spans.first().from >= 0.0 && spans.last().to <= 1.0
        )
    }

    /** The fake's own reading only works if it is asked the way the harvest asks — whole shapes, counted. */
    @Test
    fun `the test world counts every coastline read`() {
        val testWorld = world()
        assertEquals("nothing read yet", 0, testWorld.landReads)
        val box = BoundingBox(43.530, 43.570, 7.080, 7.160)
        assertEquals("the fake answers the whole polyline", testWorld.land, testWorld.landPolylinesIn(box))
        assertEquals("and says it was asked", 1, testWorld.landReads)
        testWorld.landPolylinesIn(box)
        assertEquals("every ask is counted", 2, testWorld.landReads)
    }

    /**
     * **The shared scratch: two threads on one terrain answer what one thread answers** (§19.5 C1).
     *
     * A kept terrain is read by the search that owns it *and* by the cancelled, unjoined predecessor the
     * engine no longer waits for (§19.4), and the wall index's dedupe marks and the band's are scratch on
     * the instance. With a plain `stamp++` the two threads can be handed **one** stamp, and then one reads
     * the other's marks as its own and skips the segment that crosses the line, or the segment whose span
     * it was about to add — a wrong answer rather than a slow one.
     *
     * Both queries are chosen to be certain: a leg through the island's dilated wall, and a leg inside the
     * 300 m strip. Both threads run the very same pair, so a shared stamp lands on the same cells and the
     * damage is visible in the answer; every answer must be the single-threaded one.
     *
     * Revert it catches: `stamp++` in place of `AtomicInteger.incrementAndGet()` in the wall index or in
     * the band — this test was red on that revert and is green on the fix.
     */
    @Test
    fun `the shared terrain's scratch answers two threads what it answers one`() {
        val terrain = TautTerrain.of(
            bandWorld(), BoundingBox(43.530, 43.570, 7.080, 7.160), berthM, shoreOffsetM
        )
        val obstacles = terrain.obstacles
        val band = terrain.band ?: error("the world has a band")
        val acrossFirst = RoutePoint(43.5300, 7.1200)
        val acrossSecond = RoutePoint(43.5700, 7.1200)
        val alongFirst = obstacles.frame.pt(LatLng(43.5440, 7.1100))
        val alongSecond = obstacles.frame.pt(LatLng(43.5440, 7.1300))

        val crossing = obstacles.crossesWall(acrossFirst, acrossSecond)
        // `Span` carries no equality of its own, so the comparison is made on the numbers it holds —
        // comparing two `Span` lists by identity would fail on every call and read as a race that is not.
        val spans = band.spansOf(alongFirst, alongSecond).map { it.from to it.to }
        assertTrue("the pair really crosses the island's dilated wall", crossing)
        assertTrue("and the leg really stands inside the 300 m strip", spans.isNotEmpty())

        val threads = 2
        val rounds = 50_000
        val mismatches = AtomicInteger(0)
        val ready = CountDownLatch(threads)
        val go = CountDownLatch(1)
        val workers = (0 until threads).map {
            Thread {
                ready.countDown()
                go.await()
                repeat(rounds) {
                    val crossed = obstacles.crossesWall(acrossFirst, acrossSecond)
                    val found = band.spansOf(alongFirst, alongSecond).map { it.from to it.to }
                    if (crossed != crossing || found != spans) mismatches.incrementAndGet()
                }
            }
        }
        workers.forEach { it.start() }
        ready.await()
        go.countDown()
        workers.forEach { it.join() }
        println(
            "shared scratch · $threads thread(s) × $rounds round(s): ${mismatches.get()} answer(s) other " +
                "than the single-threaded one"
        )
        assertEquals("no interleaving may change an answer", 0, mismatches.get())
    }

    /**
     * **The licence's own reading: it holds the largest box a cold search could build** (§19.5 C2).
     *
     * A reuse hands the graph the kept box, so what must be licensed is not "the new rough corridor lies
     * inside the kept one" but "**every** box a cold search could build from that corridor lies inside it",
     * and the sizing's own clamp is exactly the widest of those. Read here at both ends: the licence
     * reaches past the guess on every side, and every answer the sizing can give for this guess — nothing
     * found, the guess itself, a shape reaching past the clamp, a shape inside it — lies within the licence.
     *
     * Revert it catches: licensing on the rough guess, where the clamped answer for a coast that runs past
     * the corridor puts the cold box outside the box the reuse was granted on.
     */
    @Test
    fun `the licence holds the largest box a cold search could build`() {
        val testWorld = world()
        val rough = TautGraph.corridorBox(testWorld, start, aim, 0, berthM)
        val licence = TautGraph.coldBoundBox(testWorld, rough, start, aim, berthM)

        assertTrue(
            "the licence reaches past the rough guess on every side",
            licence.latSouth < rough.latSouth && licence.latNorth > rough.latNorth &&
                licence.lonWest < rough.lonWest && licence.lonEast > rough.lonEast
        )
        val extents = listOf<BoundingBox?>(
            null,
            rough,
            BoundingBox(43.30, 43.80, 6.90, 7.40),
            BoundingBox(43.5480, 43.5520, 7.1180, 7.1220)
        )
        for (extent in extents) {
            val cold = TautGraph.sizedFromObstacles(testWorld, rough, extent, start, aim, berthM)
            assertTrue(
                "the licence holds the cold box for extent $extent",
                cold.latSouth >= licence.latSouth && cold.latNorth <= licence.latNorth &&
                    cold.lonWest >= licence.lonWest && cold.lonEast <= licence.lonEast
            )
        }
    }

    /**
     * **How often the licence holds along a drag** (§19.5 C2's second reading).
     *
     * One engine, one start and the aim walked along the drag: **inward**, from the far aim back towards
     * the boat, so each new corridor lies inside the last and the licence must hold; and **outward**, the
     * same aims in the other order, where the new corridor reaches past the kept one and it must not. A
     * licence that fired on the outward drag would be one that lets a reuse answer over a corridor narrower
     * than cold's, which is the blocker it exists to close.
     *
     * The rate is printed rather than asserted tight: a licence that rarely fires is itself a finding, and
     * this is where the finding is visible.
     */
    @Test
    fun `the licence's hit rate along a drag`() = runBlocking {
        val testWorld = world()
        val aims = (0..DRAG_AIMS).map { step ->
            RoutePoint(start.latitude, aim.longitude - DRAG_STEP_DEG * step)
        }
        val inward = reuseCount(testWorld, aims)
        val outward = reuseCount(testWorld, aims.reversed())
        println(
            "licence hit rate · inward (the aim dragged back towards the boat): $inward/${aims.size} · " +
                "outward (the aim pushed away): $outward/${aims.size}"
        )
        assertTrue("the licence holds on the inward drag", inward > 0)
        assertEquals("and never on the outward one", 0, outward)
    }

    /** One engine on one start, asked for each aim in turn — how many of the answers reused the terrain. */
    private suspend fun reuseCount(world: TautWorld, aims: List<RoutePoint>): Int {
        val engine = engine(world)
        var reused = 0
        for (draggedAim in aims) {
            val answer = engine.route(start, draggedAim, cruiseKn)
            if (answer is RouteResult.Success && details(answer).terrainReused) reused++
        }
        return reused
    }

    /**
     * **The generation's own rule: a replaced layer bumps, a repeat does not** (§19.5 C4).
     *
     * The contour's identity cache was the one thing keeping a depth refresh out of the invalidator, and it
     * covers the contour alone. The rule read here is identity for all three layers: first sight counts, a
     * repeat does not, and a **new object where an old one stood** counts exactly as loudly as a first
     * arrival — which is what a `DepthRepository.refreshDepth` does to the grid, and what the review found
     * the two invalidators disagreeing about.
     *
     * Revert it catches: counting only the layers the adapter itself loaded, which leaves a replaced grid
     * unbumped and a kept terrain answering about soundings that are no longer behind it.
     */
    @Test
    fun `a replaced layer bumps the generation and a repeat does not`() {
        val generation = TautTerrainGeneration()
        val coastline = Any()
        val zones = Any()
        val firstGrid = Any()

        val opened = generation.observe(coastline, zones, firstGrid)
        assertTrue("the layers' first sight is counted", opened > 0)
        assertEquals(
            "and a repeat of the same objects is not an event",
            opened,
            generation.observe(coastline, zones, firstGrid)
        )

        val refreshed = Any()
        val replaced = generation.observe(coastline, zones, refreshed)
        assertTrue("a new grid object where the old stood is an event", replaced > opened)
        assertEquals(
            "and its own repeat is not",
            replaced,
            generation.observe(coastline, zones, refreshed)
        )
        assertEquals(
            "a layer that has not landed neither counts nor clears what was counted",
            replaced,
            generation.observe(coastline, zones, null)
        )
        assertTrue(
            "and its arrival after an absence is an event",
            generation.observe(coastline, zones, Any()) > replaced
        )
    }

    private companion object {
        /** How many aims the drag reading walks, and how far apart they are (degrees of longitude). */
        const val DRAG_AIMS = 5
        const val DRAG_STEP_DEG = 0.002
    }
}
