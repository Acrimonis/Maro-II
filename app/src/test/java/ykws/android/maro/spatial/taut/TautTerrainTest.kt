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
 * **And the graph kept beside that terrain** (§19.6), read the same way and with controls of its own: the
 * second search's build examines the two ends' rows alone — `2 · vertices − 3` pairs against a cold
 * build's `N(N−1)/2`, over the same edges — and answers a **cold** engine's own line. A pace the base was
 * not priced at must rebuild it, a moved world must rebuild it with the terrain, and the licence itself is
 * read at its own type. A last reading covers the one place the corner set depends on the ends at all: an
 * aim resolved off the water onto a corner, which a cold build dedupes away and a reuse must too.
 *
 * The keep is read as a **flag** on the dossier ([`TautRouteDetails.graphReused`]) rather than inferred
 * from the pair count, and `cornerCount` is the corners **standing as vertices** rather than the harvest's
 * raw count — so the two can be read beside `vertexCount` instead of argued about. And the licence's own
 * zone half — the zone an edge enters and the berth fraction it earns — needs a world that carries one:
 * every other reading here stands on water with no zones in it, which is why the first test below the
 * controls builds its own.
 *
 * Each test names the revert it catches, in the way this feature's assertion set does — and where a
 * reading stands on one code path both entrances walk, it says what the reading cannot be moved by
 * instead, that revert having been run and the test having stayed green on it.
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

    /**
     * **A 5 kn harbour the island pair's aim stands inside** — the world the licence's zone half needs.
     *
     * It is the zone [TautSearchTest] reads the drawn clock on, at the same coordinates, so the pair is
     * known to answer on run two with the crossing priced rather than forbidden.
     */
    private val harbour = TautZoneShape(
        id = "harbour",
        name = "Harbour 5 kn",
        limitKn = 5.0,
        outerRing = TautTestWorld.rect(43.5400, 7.1330, 43.5600, 7.1480)
    )

    /** The island pair's own water, with a priced zone on it — no other reuse reading here has one. */
    private fun zoneWorld() = TautTestWorld(
        land = listOf(island),
        zones = listOf(harbour),
        depthBox = BoundingBox(43.5200, 43.5800, 7.0800, 7.1600),
        coastalBandWidthM = 0.0
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

    /**
     * **The graph's own reading: the rows alone, and the same line** (§19.6).
     *
     * The first search of a fresh engine builds the base — the corner harvest and the quadratic scan — and
     * the second, on the same terrain and the same pace, examines the two ends' rows alone. The pair
     * counts are the two halves of the step in one unit each: `N(N−1)/2` for the cold build and
     * `2 · vertices − 3` for the reuse, with the same edges behind them, because a reuse that dropped a
     * corner's edges would be a graph the search could not walk the way it walked the first.
     *
     * The line is compared to a **cold engine's** on the same aim and not only to the first search's, so a
     * cache answering something slightly different could not pass on the strength of its own history.
     *
     * Revert it catches: ignoring the licence, where the second search pays the corner scan again and the
     * pair count says so; and rebuilding the ends' rows without the corners, or the corners without them,
     * where the edge count and the line both move.
     */
    @Test
    fun `a kept graph serves the second search and answers the same line`() = runBlocking {
        val testWorld = world()
        val engine = engine(testWorld)

        val first = success(engine.route(start, aim, cruiseKn))
        val second = success(engine.route(start, aim, cruiseKn))
        val firstDetails = details(first)
        val secondDetails = details(second)
        val vertices = firstDetails.vertexCount
        println(
            "kept graph · first search: $vertices vertex(es), ${firstDetails.edgeCount} edge(s), " +
                "${firstDetails.candidatePairs} pair(s), ${firstDetails.cornerCount} corner(s), kept " +
                "${firstDetails.graphReused}, graph ${firstDetails.graphMillis} ms · second search: " +
                "${secondDetails.vertexCount} vertex(es), ${secondDetails.edgeCount} edge(s), " +
                "${secondDetails.candidatePairs} pair(s), ${secondDetails.cornerCount} corner(s), kept " +
                "${secondDetails.graphReused}, graph ${secondDetails.graphMillis} ms"
        )
        // The flag is the keep's own reading rather than something the pair count implies — a build that
        // rebuilt and happened to examine the same rows would be caught here and not by the count alone.
        assertTrue("the first search built its own graph", !firstDetails.graphReused)
        assertTrue("and the second kept the graph the first built", secondDetails.graphReused)
        // Exact on this pair, and the premise is the reading's own: neither end stands on a corner, so the
        // base's scan is the whole of N(N−1)/2. An end that does stand on one is still scanned at the base
        // — the base is built without knowing either end — and the cold figure stands a row above this.
        assertEquals(
            "the first search pays the quadratic scan",
            vertices * (vertices - 1) / 2,
            firstDetails.candidatePairs
        )
        assertEquals(
            "the second examines the two ends' rows alone",
            2 * vertices - 3,
            secondDetails.candidatePairs
        )
        assertEquals(
            "over the same edges, so nothing was dropped with the scan",
            firstDetails.edgeCount,
            secondDetails.edgeCount
        )
        assertEquals(
            "and with no end standing on a corner every harvest corner is a vertex of its own",
            vertices - 2,
            secondDetails.cornerCount
        )

        val cold = success(engine(testWorld).route(start, aim, cruiseKn))
        val coldDetails = details(cold)
        assertTrue("a cold engine builds rather than reuses", !coldDetails.graphReused)
        assertEquals("and keeps the same vertices", vertices, coldDetails.vertexCount)
        assertEquals("and the same corner count", secondDetails.cornerCount, coldDetails.cornerCount)
        assertEquals("draws the same line", cold.points, second.points)
        assertEquals("at the same leg times", cold.legTimesSec, second.legTimesSec)
        assertEquals("over the same distance", cold.distanceM, second.distanceM, 0.0)
        assertEquals("and to the same second", cold.durationSec, second.durationSec, 0.0)
        assertTrue("the line the comparison is made on really has a bend in it", second.points.size > 2)
    }

    /**
     * **The licence's second half: the pace the edges were priced at** (§19.6).
     *
     * The terrain's key does **not** carry the pace — a corridor's obstacles and zones do not depend on
     * how fast the boat goes — so a search at another pace is one whose terrain is reused and whose graph
     * must not be: the kept edges' prices are **times**, and answering at the new pace with the old ones
     * would hand the search seconds belonging to a boat that is not there. Read at both ends: the pair
     * count is a cold build's own, and the line is a cold engine's at the new pace.
     *
     * Revert it catches: a licence that reads the terrain alone — the pair count falls to the rows, and the
     * line is the old pace's arithmetic answered at the new one.
     */
    @Test
    fun `a pace the base was not priced at rebuilds the kept graph`() = runBlocking {
        val testWorld = world()
        val engine = engine(testWorld)
        engine.route(start, aim, cruiseKn)

        val slowerKn = cruiseKn / 2.0
        val atTheNewPace = success(engine.route(start, aim, slowerKn))
        val graphDetails = details(atTheNewPace)
        val vertices = graphDetails.vertexCount
        assertTrue("the terrain itself is still the kept one", graphDetails.terrainReused)
        assertTrue("and the graph is not, having been priced at another pace", !graphDetails.graphReused)
        assertEquals(
            "and the graph is rebuilt rather than answered at the old prices",
            vertices * (vertices - 1) / 2,
            graphDetails.candidatePairs
        )
        val cold = success(engine(testWorld).route(start, aim, slowerKn))
        assertEquals("a cold engine at that pace draws the same line", cold.points, atTheNewPace.points)
        assertEquals("at the same leg times", cold.legTimesSec, atTheNewPace.legTimesSec)
        assertEquals("and to the same second", cold.durationSec, atTheNewPace.durationSec, 0.0)
    }

    /**
     * **The invalidator reaches the graph too** (§19.6, on §19.4's own rule).
     *
     * A world that moved takes its terrain with it, and the graph rides the terrain's identity: the kept
     * base was priced on corners harvested from a coastline that no longer stands, so the bumped
     * generation must rebuild both. The pair count is what says the base was rebuilt rather than answered
     * against the bump, and the line is a cold engine's all the same.
     */
    @Test
    fun `a moved world rebuilds the kept graph with the terrain`() = runBlocking {
        val testWorld = world()
        val engine = engine(testWorld)
        engine.route(start, aim, cruiseKn)

        testWorld.generation += 1
        val afterTheBump = success(engine.route(start, aim, cruiseKn))
        val bumpedDetails = details(afterTheBump)
        assertTrue("the bumped generation harvests the terrain afresh", !bumpedDetails.terrainReused)
        assertTrue("and the graph is rebuilt with it rather than kept", !bumpedDetails.graphReused)
        val vertices = bumpedDetails.vertexCount
        assertEquals(
            "and rebuilds the graph with it",
            vertices * (vertices - 1) / 2,
            bumpedDetails.candidatePairs
        )
        val cold = success(engine(testWorld).route(start, aim, cruiseKn))
        assertEquals("with the line unmoved across the invalidation", cold.points, afterTheBump.points)
    }

    /**
     * **The licence read at the store's own type** — what it compares, and what it refuses to.
     *
     * Identity on the terrain *is* the box, the zones and the world's generation, so a terrain harvested
     * again for the same box at the same numbers is still a different answer and is refused; the pace is
     * the second half because the edges' prices are times. Read here so the rule is a value rather than
     * something a reader has to infer from a search's pair count.
     */
    @Test
    fun `the graph licence matches on the terrain and the pace`() {
        val testWorld = world()
        val box = TautGraph.corridorBox(testWorld, start, aim, 0, berthM)
        val terrain = TautTerrain.of(testWorld, box, berthM, shoreOffsetM)
        val base = TautGraphBase.of(testWorld, terrain, cruiseKn)

        assertTrue("the same terrain at the same pace is the licence", base.licenses(terrain, cruiseKn))
        assertTrue(
            "the same water harvested again for the same box is not the same terrain",
            !base.licenses(TautTerrain.of(testWorld, box, berthM, shoreOffsetM), cruiseKn)
        )
        assertTrue(
            "and neither is the same terrain at another pace",
            !base.licenses(terrain, cruiseKn + 1.0)
        )
    }

    /**
     * **The one place the corner set depends on the ends** (§19.6): an aim off the water.
     *
     * The aim here stands on the island's own rock, so the pin's rule resolves it to the nearest corner
     * the corridor's water allows — a corner of the obstacles, and therefore a corner the kept base holds
     * too. A cold build adds its two ends first and dedupes against them, so that corner is **not** a
     * vertex of its own; a reuse that kept it would answer with one vertex and one edge more than cold has.
     *
     * The pair is the shape a drag has — the same start, the aim pulled back inside the corridor already
     * kept — so what licenses the reuse is the containment §19.5 C2 states rather than an accident of two
     * ends landing on one box. The pair count is the reading that says so: the rows alone, where a cold
     * build of this pair examines those rows *plus* the corner scan.
     *
     * Revert it catches — **measured 2026-09-21**: reporting the harvest's own raw `cornerCount`, the
     * end-dedupe left on the count, fails the `vertexCount − 2` assertion below. What it does **not** catch
     * is the end-dedupe itself: `over` is the one path both entrances walk, so a reuse that kept the corner
     * would lose it to cold as well and the two graphs would still agree — which is why the count, and not
     * the comparison, is where this reading bites.
     */
    @Test
    fun `an aim resolved onto a kept corner is not a vertex of its own`() = runBlocking {
        val testWorld = world()
        val engine = engine(testWorld)
        engine.route(start, aim, cruiseKn)

        val offWaterAim = RoutePoint(43.5500, 7.1200)
        val warmAnswer = success(engine.route(start, offWaterAim, cruiseKn))
        val coldAnswer = success(engine(testWorld).route(start, offWaterAim, cruiseKn))
        val warmDetails = details(warmAnswer)
        val coldDetails = details(coldAnswer)
        println(
            "kept graph · off-water aim: warm ${warmDetails.vertexCount} vertex(es), " +
                "${warmDetails.edgeCount} edge(s), ${warmDetails.candidatePairs} pair(s) · cold " +
                "${coldDetails.vertexCount} vertex(es), ${coldDetails.edgeCount} edge(s), " +
                "${coldDetails.candidatePairs} pair(s) · destination moved " +
                "${warmAnswer.destinationMoved}"
        )
        assertTrue("the aim was resolved onto the water rather than kept", warmAnswer.destinationMoved)
        assertTrue("and the corridor's terrain was the kept one", warmDetails.terrainReused)
        assertTrue("and the graph itself was the kept one", warmDetails.graphReused)
        assertEquals(
            "the kept corner standing on the resolved aim is not a vertex of its own",
            coldDetails.vertexCount,
            warmDetails.vertexCount
        )
        assertEquals(
            "so it is not counted among the graph's corners either — cold and warm agree",
            coldDetails.cornerCount,
            warmDetails.cornerCount
        )
        assertEquals(
            "which is the harvest's own count minus the corner the end took its place from",
            warmDetails.vertexCount - 2,
            warmDetails.cornerCount
        )
        assertEquals("so the edges are the same edges", coldDetails.edgeCount, warmDetails.edgeCount)
        assertEquals(
            "and the kept graph answered the ends' rows alone",
            2 * warmDetails.vertexCount - 3,
            warmDetails.candidatePairs
        )
        assertEquals("drawing a cold engine's line", coldAnswer.points, warmAnswer.points)
        assertEquals("to the same second", coldAnswer.durationSec, warmAnswer.durationSec, 0.0)
    }

    /**
     * **The reused graph's zone half, read on water that carries a zone** (§19.6).
     *
     * Every other reuse reading in this file stands on a world with no zones in it, so the licence's zone
     * half — the zone an edge enters and the berth fraction it earns — is pinned nowhere: the base copies
     * those two fields from the edge it holds, and a copy that re-derived them, dropped them or copied one
     * array and re-derived the other would move a figure no zone-free world can show. Here the aim stands
     * **inside a 5 kn harbour**, so run two answers, an edge really enters the interior and an edge along
     * the ring really earns a berth fraction.
     *
     * The reading is the graph rather than the line: the kept graph and a cold build over the **same
     * terrain** are walked pair by pair, and every edge they share must carry the same zone, the same berth
     * fraction, the same price and the same length. The two counts printed at the end are what say the
     * comparison is not vacuous — a world where nothing entered and nothing earned would satisfy it
     * trivially.
     *
     * **What it reads, and what it cannot** — measured 2026-09-21: with `over`'s kept-edge append made to
     * carry `-1` and `0.0` in the zone's and the berth's place, this test stays **green**, the two counts
     * below still supplied by the ends' rows. The copy is one path both entrances walk — `build` is `of`
     * and `over` — so no revert inside it can move a comparison between them: what is pinned here is that
     * a zone-carrying terrain reaches both entrances with the same zone, fraction, price and length, and
     * not that a defect in the copy would be caught.
     */
    @Test
    fun `a reused graph carries a cold build's zones and berth fractions`() {
        val testWorld = zoneWorld()
        val box = TautGraph.corridorBox(testWorld, start, aim, 0, berthM)
        val terrain = TautTerrain.of(testWorld, box, berthM, shoreOffsetM)
        val base = TautGraphBase.of(testWorld, terrain, cruiseKn)

        val cold = TautGraph.build(testWorld, terrain, start, aim, cruiseKn)
        val warm = base.over(testWorld, start, aim, reused = true)

        assertEquals("the same vertices", cold.vertices, warm.vertices)
        assertEquals("the same edge count", cold.edgeCount, warm.edgeCount)
        assertEquals(
            "and the kept graph read the ends' rows alone",
            2 * warm.vertices.size - 3,
            warm.candidatePairs
        )

        val vertexCount = warm.vertices.size
        var zoneEdges = 0
        var marginEdges = 0
        for (a in 0 until vertexCount) {
            for (b in a + 1 until vertexCount) {
                val coldEdge = cold.edgeBetween(a, b)
                val warmEdge = warm.edgeBetween(a, b)
                assertTrue(
                    "the pair ($a, $b) is an edge in both graphs or in neither",
                    (coldEdge < 0) == (warmEdge < 0)
                )
                if (coldEdge < 0) continue
                assertEquals("the zone the edge enters", cold.zoneOf(coldEdge), warm.zoneOf(warmEdge))
                assertEquals(
                    "its berth fraction",
                    cold.berthFraction(coldEdge),
                    warm.berthFraction(warmEdge),
                    0.0
                )
                assertEquals("its price", cold.priceSec(coldEdge), warm.priceSec(warmEdge), 0.0)
                assertEquals("its length", cold.lengthM(coldEdge), warm.lengthM(warmEdge), 0.0)
                if (warm.zoneOf(warmEdge) >= 0) zoneEdges++
                if (warm.berthFraction(warmEdge) > 0.0) marginEdges++
            }
        }
        println(
            "reused graph · zone-carrying world: $vertexCount vertex(es), ${warm.edgeCount} edge(s), " +
                "$zoneEdges entering the zone, $marginEdges earning a berth fraction"
        )
        assertTrue("the world really carries a zone — an edge enters its interior", zoneEdges > 0)
        assertTrue("and an edge really earns a berth fraction", marginEdges > 0)
    }

    /**
     * **The ends' rows are cancellable between them, not only the scan before them** (§19.2 item 4).
     *
     * `over` promises a check **per row**; one check before its loops would leave the start's row, the
     * aim's row and the kept edges' copy uninterruptible, which is the abort latency the promise exists to
     * bound. The reader is the promise itself: a check that throws on its third ask must be reached, and a
     * build that asked once and then assembled in full would never throw.
     *
     * Revert it catches — **measured 2026-09-21**: with the three checks moved back out of the loops the
     * build asks once, `abandoned` is false and this test is red; with them in place it is green.
     */
    @Test
    fun `the ends' rows are cancellable between them`() {
        val testWorld = world()
        val box = TautGraph.corridorBox(testWorld, start, aim, 0, berthM)
        val terrain = TautTerrain.of(testWorld, box, berthM, shoreOffsetM)
        val base = TautGraphBase.of(testWorld, terrain, cruiseKn)

        var asks = 0
        val abandoned = try {
            base.over(testWorld, start, aim) {
                asks++
                if (asks >= 3) throw IllegalStateException("abandoned")
            }
            false
        } catch (expected: IllegalStateException) {
            true
        }
        assertTrue("the build asked the caller between its rows ($asks ask(s))", abandoned)
        assertTrue("and asked more than the one check standing before its loops", asks >= 3)
    }

    private companion object {
        /** How many aims the drag reading walks, and how far apart they are (degrees of longitude). */
        const val DRAG_AIMS = 5
        const val DRAG_STEP_DEG = 0.002
    }
}
