package ykws.android.maro.spatial.multipass

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.DepthSample
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.data.regulation.SpeedZone
import ykws.android.maro.spatial.SpatialOperations
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min

/**
 * **The price walk's own reads** — the fine midpoints it keeps, the groups it prices from one reading
 * behind a declaration the sources make, and the reads it spares.
 *
 * The coarsening is arithmetic, not approximate: a proved group's product is *identically* the fine sum
 * it replaces, so every fixture here pins the **equality** of the returned double and the **saving** in
 * reads. The cases where the proof must fail are pinned too — a band's edge, a hole's edge — because a
 * mis-declared boundary moves the drawn line rather than crashing.
 */
class AvoidPriceWalkTest {

    /** `route.avoid.obstacle.marginM` as it ships — the walk's own fine step is half of it. */
    private val marginM = 25.0

    /** The coarse step these walks are handed — `avoid`'s own cell, eight times the fine step. */
    private val coarseStepM = 100.0

    /** The fine step the `avoid` plan walks at: `clearanceStep(25.0)` = 12.5 m. */
    private val fineStepM = MultipassPull.clearanceStep(marginM)

    private val paceKn = 28.0
    private val lambda = 2.0
    private val collarM = 100.0
    private val bandWidthM = 300.0
    private val bandOutsideMarginM = 50.0
    private val fraction = 0.66
    private val fullSec = 60.0

    /**
     * **The equivalence, exactly.** A constant price whose source declares its boundary far away: the
     * coarsened walk returns today's own double and pays strictly fewer reads. One test, two
     * assertions — the proof rather than a proxy for it.
     */
    @Test
    fun theCoarsenedPriceWalkReturnsTheSameDoubleFromFewerReads() {
        val start = LatLng(CHORD_LAT, east(0.0))
        val aim = LatLng(CHORD_LAT, east(1600.0))
        var fineReads = 0
        var coarseReads = 0

        val fine = MultipassPull.softPriceSec(
            start, aim, marginM, fineStepM, flatField { fineReads++ }
        )
        val coarse = MultipassPull.softPriceSec(
            start, aim, marginM, coarseStepM, flatField { coarseReads++ }
        )

        assertEquals("the coarsened walk returns today's own double, not a near miss", fine, coarse, 0.0)
        assertTrue("and it pays strictly fewer price reads", coarseReads < fineReads)
        assertTrue("the fine walk is not vacuous", fineReads > 0)
    }

    /**
     * **The prefix is asserted, not inferred.** Its identity follows from the chord's by construction,
     * but the walk subtracts two prefix entries to get a span, so the arrays themselves are pinned.
     */
    @Test
    fun theCoarsenedPrefixEqualsTodaysPrefix() {
        val path = listOf(
            LatLng(CHORD_LAT, east(0.0)),
            LatLng(CHORD_LAT, east(700.0)),
            LatLng(CHORD_LAT + 0.002, east(1400.0))
        )

        val fine = MultipassPull.softPricePrefix(path, marginM, fineStepM, flatField {})
        val coarse = MultipassPull.softPricePrefix(path, marginM, coarseStepM, flatField {})

        assertArrayEquals("the coarsened prefix is today's prefix", fine, coarse, 0.0)
    }

    /**
     * **An unproved group keeps today's reads and today's verdict.** The chord runs along the band's
     * collar, 340 m off the coast, so its declaration `min(|d − 300|, |d − 350|)` is 10 m — far short of
     * a group's 50 m half-length. Every group is refused, every fine midpoint is read, and the pulled
     * line is the one today's walk draws.
     */
    @Test
    fun anUnprovedGroupAlongTheBandsEdgeKeepsTodaysReadsAndVerdict() {
        val start = LatLng(CHORD_LAT, east(0.0))
        val mid = LatLng(CHORD_LAT, east(800.0))
        val aim = LatLng(CHORD_LAT, east(1600.0))
        val path = listOf(start, mid, aim)
        var fineReads = 0
        var coarseReads = 0

        val fineSum = MultipassPull.softPriceSec(start, aim, marginM, fineStepM, bandEdgeField { fineReads++ })
        val coarseSum = MultipassPull.softPriceSec(start, aim, marginM, coarseStepM, bandEdgeField { coarseReads++ })

        assertEquals("the band's edge keeps today's sum", fineSum, coarseSum, 0.0)
        assertEquals("and every fine midpoint is still read", fineReads, coarseReads)
        assertTrue("the walk is not vacuous: the collar is priced", coarseSum > 0.0)

        val fineLine = MultipassPull.pull(path, start, aim, marginM, fineStepM, fineStepM, bandEdgeField {})
        val coarseLine = MultipassPull.pull(path, start, aim, marginM, coarseStepM, coarseStepM, bandEdgeField {})
        assertEquals("and the chord's verdict is the one today's walk gives", fineLine, coarseLine)
    }

    /**
     * **A shallow patch inside a group is still refused.** The price half is coarsened (its source
     * proves every group) while the depth gate is a **step**, tested at every mark by
     * [`legClearCause`]. A walk that let the price's proof skip the gate's test would sail the patch.
     */
    @Test
    fun aShallowPatchInsideAGroupIsStillRefused() {
        val start = LatLng(CHORD_LAT, east(0.0))
        val mid = LatLng(CHORD_LAT, east(800.0))
        val aim = LatLng(CHORD_LAT, east(1600.0))
        val path = listOf(start, mid, aim)
        val field = RouteCostField(
            listOf(
                RouteCostSource.Hard(distanceAt = { _ -> Double.MAX_VALUE }),
                depthGateSource(3.0) { p -> if (shallowPatch(p)) 2.0 else Double.NaN },
                RouteCostSource.Soft(
                    priceSec = { 1.0 },
                    tag = MultipassCellState.ZONE,
                    clearanceAt = { CLEAR_OF_ANY_BOUNDARY_M }
                )
            )
        )

        val pulled = MultipassPull.pull(path, start, aim, marginM, coarseStepM, coarseStepM, field)

        assertEquals("a shallow patch the coarse marks step over is still refused", path, pulled)
    }

    /**
     * **A hole's edge is a boundary like any other.** The chord runs through a holed ring's own hole, so
     * its arm changes twice across the hole's vertical edges. The declaration names every ring, so the
     * groups straddling those edges keep their fine reads — and with them the sum is exactly today's,
     * while the walks away from the edges are still proved.
     *
     * The control pins the review's defect: a declaration built from the shipped collar read alone walks
     * the outer ring and is blind to the hole, so it proves a group straddling the hole and misprices it.
     */
    @Test
    fun aHoleEdgeKeepsTheFineReadsAndIsNeverProved() {
        val start = LatLng(CHORD_LAT, east(0.0))
        val aim = LatLng(CHORD_LAT, east(1600.0))
        var fineReads = 0
        var coarseReads = 0

        val fine = MultipassPull.softPriceSec(start, aim, marginM, fineStepM, holeField { fineReads++ })
        val coarse = MultipassPull.softPriceSec(start, aim, marginM, coarseStepM, holeField { coarseReads++ })

        assertEquals("the hole's edge keeps today's sum exactly", fine, coarse, 0.0)
        val steps = ceil(SpatialOperations.haversine(start, aim) / fineStepM).toInt()
        val perGroup = (coarseStepM / fineStepM).toInt()
        val groups = (steps + perGroup - 1) / perGroup
        assertTrue("groups away from the edges are proved", coarseReads < fineReads)
        assertTrue("while the groups across the hole keep their own reads", coarseReads > groups)

        val blind = MultipassPull.softPriceSec(start, aim, marginM, coarseStepM, holeField(blindToHoles = true) {})
        assertTrue(
            "a declaration blind to the hole would prove a group straddling it and move the sum",
            fine != blind
        )
    }

    /** **A proved group pays no hard read.** The flag the price walk discards is proven discarded. */
    @Test
    fun aProvedGroupPaysNoHardRead() {
        val start = LatLng(CHORD_LAT, east(0.0))
        val aim = LatLng(CHORD_LAT, east(1600.0))
        var blockedTests = 0
        val field = RouteCostField(
            listOf(
                RouteCostSource.Hard(blockedAt = { blockedTests++; false }, distanceAt = { Double.MAX_VALUE }),
                RouteCostSource.Soft(
                    priceSec = { fullSec },
                    tag = MultipassCellState.ZONE,
                    clearanceAt = { CLEAR_OF_ANY_BOUNDARY_M }
                )
            )
        )

        MultipassPull.softPriceSec(start, aim, marginM, coarseStepM, field)

        assertEquals("the price walk never asks the walls' blocked test", 0, blockedTests)
        field.evaluate(start)
        assertEquals("while evaluate is the price body plus the hard loop, so the counter works", 1, blockedTests)
    }

    /**
     * **The zone declaration itself.** The outer ring, the holes and the collar edge are all named: a
     * point on the outer ring's collar edge declares 0, and the hole's own edge does too — the boundary
     * the shipped collar read, which walks the outer ring alone, cannot see.
     */
    @Test
    fun theZoneDeclarationNamesTheCollarEdgeAndTheHole() {
        val zones = listOf(holedZone)

        val onCollarEdge = LatLng(CHORD_LAT, east(800.0 - OUTER_HALF_X_M - collarM))
        assertEquals(
            "a point on the outer ring's collar edge declares no boundary",
            0.0,
            speedZonePriceClearanceM(zones, emptySet(), onCollarEdge.latitude, onCollarEdge.longitude, collarM),
            1.0
        )

        val onHoleEdge = LatLng(CHORD_LAT, east(800.0 - HOLE_HALF_X_M))
        assertEquals(
            "and the hole's own edge is a boundary like any other",
            0.0,
            speedZonePriceClearanceM(zones, emptySet(), onHoleEdge.latitude, onHoleEdge.longitude, collarM),
            1.0
        )

        val holeCentre = LatLng(CHORD_LAT, east(800.0))
        val declared = speedZonePriceClearanceM(zones, emptySet(), holeCentre.latitude, holeCentre.longitude, collarM)
        val outerOnly = ringDistanceM(holedZone.outerRing, holeCentre)
        assertTrue("the hole is seen, so its edge is nearer than the outer ring's", declared < outerOnly)
    }

    /**
     * **The span proof's own shape: every read but one.** A constant price whose boundary is declared
     * far away lets the recursion prove the **whole chord** from one clearance read at its midpoint, so
     * the coarsened walk returns today's own double and pays a single price read where the fine walk
     * reads every midpoint — the group walk's own saving, taken at the whole chord's scale.
     */
    @Test
    fun aProvedSpanSavesEveryReadButOne() {
        val start = LatLng(CHORD_LAT, 7.00)
        val aim = LatLng(CHORD_LAT + 1595.0 / M_PER_DEG_LAT, 7.00)
        var fineReads = 0
        var coarseReads = 0

        val fine = MultipassPull.softPriceSec(start, aim, marginM, fineStepM, flatField { fineReads++ })
        val coarse = MultipassPull.softPriceSec(start, aim, marginM, coarseStepM, flatField { coarseReads++ })

        assertEquals("the span proof returns today's own double, not a near miss", fine, coarse, 0.0)
        assertTrue("the fine walk is not vacuous", fineReads > 0)
        assertEquals("a proved span pays one price read whatever its length", 1, coarseReads)
        assertEquals("so the saving is every read but one", fineReads - 1, fineReads - coarseReads)
    }

    /**
     * **A proved span reads exactly twice and prices zero.** Open water whose arm is 0 but whose source
     * still declares a boundary far away: the recursion proves the whole chord, so the walk pays one
     * clearance read and one price read at the midpoint, and its sum is exactly 0.
     */
    @Test
    fun aProvedSpanReadsExactlyTwiceAndPricesZero() {
        val start = LatLng(CHORD_LAT, east(0.0))
        val aim = LatLng(CHORD_LAT, east(1600.0))
        var reads = 0
        val field = RouteCostField(
            listOf(
                RouteCostSource.Soft(
                    priceSec = { reads++; 0.0 },
                    tag = MultipassCellState.ZONE,
                    clearanceAt = { reads++; CLEAR_OF_ANY_BOUNDARY_M }
                )
            )
        )

        val sum = MultipassPull.softPriceSec(start, aim, marginM, coarseStepM, field)

        assertEquals("the proved span's sum is exactly 0", 0.0, sum, 0.0)
        assertEquals("and it reads exactly twice: one proof, one price", 2, reads)
    }

    /**
     * **The floor holds.** A chord at or under twice the price step never reaches the span proof: it
     * takes the group path, so a short chord's counts are today's — one prove and one price per proved
     * group, and never a single whole-chord read.
     */
    @Test
    fun aChordAtTheFloorTakesTheGroupPathAndPaysNoExtraRead() {
        val start = LatLng(CHORD_LAT, east(0.0))
        val aim = LatLng(CHORD_LAT, east(150.0))
        var fineReads = 0
        var coarseReads = 0

        val fine = MultipassPull.softPriceSec(start, aim, marginM, fineStepM, countingField { fineReads++ })
        val coarse = MultipassPull.softPriceSec(start, aim, marginM, coarseStepM, countingField { coarseReads++ })

        val steps = ceil(SpatialOperations.haversine(start, aim) / fineStepM).toInt()
        val k = floor(coarseStepM / fineStepM).toInt()
        val groups = (steps + k - 1) / k
        assertTrue(
            "the fixture is at or under the floor",
            steps <= floor(2.0 * coarseStepM / fineStepM).toInt()
        )
        assertEquals("the group path returns today's own double", fine, coarse, 0.0)
        assertEquals("and pays the group walk's own reads, never one whole-chord read", groups * 2, coarseReads)
    }

    /**
     * **The boundary is a boundary.** A chord whose midpoint declaration stands one metre under half
     * the chord's length is **split** rather than proved — so the walk reads each half's midpoint, not
     * the whole chord's alone, and still returns today's own double.
     */
    @Test
    fun aSpanJustUnderHalfItsLengthIsSplitNotProved() {
        val start = LatLng(CHORD_LAT, east(0.0))
        val aim = LatLng(CHORD_LAT, east(1600.0))
        var reads = 0
        // The chord's midpoint declares a boundary 700 m away — under its 800 m half-length — while a
        // half's midpoint, 400 m along the chord, stands ~806 m from that same boundary, well clear of
        // its 400 m half-length. So the top span is refused and its two halves prove.
        val boundary = LatLng(north(700.0), east(800.0))
        val field = RouteCostField(
            listOf(
                RouteCostSource.Soft(
                    priceSec = { reads++; fullSec },
                    tag = MultipassCellState.ZONE,
                    clearanceAt = { p -> reads++; SpatialOperations.haversine(p, boundary) }
                )
            )
        )

        val sum = MultipassPull.softPriceSec(start, aim, marginM, coarseStepM, field)
        val fine = MultipassPull.softPriceSec(start, aim, marginM, fineStepM, flatField {})

        assertEquals("the split still returns today's own double", fine, sum, 0.0)
        assertEquals(
            "the whole chord was not priced from one read: its two halves were, each proof and price",
            5, reads
        )
    }

    /**
     * **The seam the Phase 4 collapse hid.** The phase's whole suite was green while the engine handed
     * the pull the *fine* cell, because every fixture threaded its own step and the runner's own choice
     * was never read. The step is now a named pure function of the walk, so this pins what the runner
     * hands the pull on the `evolutive` pair: the walk's **interior** cell, never the band's fine one.
     */
    @Test
    fun theRunnerHandsThePriceWalkTheInteriorCellNeverTheFineOne() {
        val walk = twoLayerWalk()
        val runner = RoutePassRunner()

        assertEquals(
            "the price step is the walk's interior cell — 100 m, the interior's own",
            100.0, runner.priceStepFor(walk), 0.0
        )
        val fine = walk.windows!!.cellSizeM(1)
        assertEquals("while the band's local cell is the fine 20 m", 20.0, fine, 0.0)
        assertTrue("so a collapse to the fine cell would be visible", runner.priceStepFor(walk) != fine)

        val single = GridWalk(walk.grid, CellIndex(0, 0), CellIndex(1, 1), 100.0)
        assertEquals("a single-grid walk answers its own cell", 100.0, runner.priceStepFor(single), 0.0)
    }

    /**
     * **The two-layer fixture the collapse cannot return through.** On the same `evolutive` pair the
     * interior step groups and spares reads, while the band's local step collapses the quotient to one
     * interval a group and reads every midpoint — the defect, and its fix, on one fixture.
     */
    @Test
    fun aTwoLayerWalkGroupsItsPriceWhereTheFineStepCouldNot() {
        val walk = twoLayerWalk()
        val interiorStep = RoutePassRunner().priceStepFor(walk)
        val localStep = walk.windows!!.cellSizeM(1)

        val start = LatLng(CHORD_LAT, 7.00)
        val aim = LatLng(CHORD_LAT + 1595.0 / M_PER_DEG_LAT, 7.00)
        var interiorReads = 0
        var localReads = 0

        val interiorSum = MultipassPull.softPriceSec(start, aim, marginM, interiorStep, flatField { interiorReads++ })
        val localSum = MultipassPull.softPriceSec(start, aim, marginM, localStep, flatField { localReads++ })

        assertEquals("the two steps price the same water identically", interiorSum, localSum, 0.0)
        assertTrue("the interior step groups and spares reads", interiorReads < localReads)
        assertTrue("while the fine local step reads every midpoint, exactly as before", localReads > 0)
    }

    /**
     * **The ring collar's own walk-level chord** — the test Phase 4's review left owed: the collar had a
     * declaration test but no chord. A chord running through the collar keeps today's fine reads (its
     * declaration never reaches a group's half-length), while a chord deep inside the ring proves its
     * groups from the ring's own distance.
     */
    @Test
    fun aRingCollarChordKeepsItsFineReadsAndADeepRingChordGroups() {
        // The outer ring spans east(150)..east(1450); its south edge is at south(445), so this chord sits
        // 20 m inside the 100 m collar.
        val collarStart = LatLng(south(465.0), east(200.0))
        val collarAim = LatLng(south(465.0), east(1400.0))
        var collarFine = 0
        var collarCoarse = 0
        val collarFineSum =
            MultipassPull.softPriceSec(collarStart, collarAim, marginM, fineStepM, holeField { collarFine++ })
        val collarCoarseSum =
            MultipassPull.softPriceSec(collarStart, collarAim, marginM, coarseStepM, holeField { collarCoarse++ })
        assertEquals("the collar keeps today's sum exactly", collarFineSum, collarCoarseSum, 0.0)
        assertEquals("and every fine midpoint is still read", collarFine, collarCoarse)
        assertTrue("the collar's water is priced, so the walk is not vacuous", collarFineSum > 0.0)

        // 200 m north of the chord: inside the ring, 89 m from the hole's own edge and 245 m from the
        // outer ring, so the declaration clears a group's 50 m half-length and the groups are proved.
        val deepStart = LatLng(north(200.0), east(400.0))
        val deepAim = LatLng(north(200.0), east(1200.0))
        var deepFine = 0
        var deepCoarse = 0
        MultipassPull.softPriceSec(deepStart, deepAim, marginM, fineStepM, holeField { deepFine++ })
        MultipassPull.softPriceSec(deepStart, deepAim, marginM, coarseStepM, holeField { deepCoarse++ })
        assertTrue("deep inside the ring the declaration proves groups", deepCoarse < deepFine)
        assertTrue("and the ring water is priced", deepFine > 0)
    }

    /**
     * **The shipped `costField` declaration closure** — the second test Phase 4's review left owed: every
     * fixture hand-rolls its own declaration, so the one that ships was the one the suite never asked.
     * This builds the field through `costField` and reads its declaration, exercising both arms — the
     * band's two circles and the ring's own edge.
     */
    @Test
    fun theShippedCostFieldClosureDeclaresTheBandsCirclesAndTheRings() {
        setSpeedZoneSwitch(true)
        val world = BandWorld(bandM = bandWidthM, coastLat = CHORD_LAT, zones = listOf(holedZone))
        val field = costField(
            world, cellM = 50.0, pace = paceKn, withZones = true, withBand = true,
            zones = listOf(holedZone), lambda = lambda
        )

        // 310 m off the coast: inside the band's 300 m circle by 10 m, and far from the ring.
        val inBand = LatLng(CHORD_LAT + 310.0 / M_PER_DEG_LAT, east(0.0))
        assertEquals(
            "the band's own circle at 300 m is named",
            min(abs(310.0 - bandWidthM), abs(310.0 - bandReachM(bandWidthM, bandOutsideMarginM))),
            field.priceClearanceM(inBand),
            1e-6
        )
        assertTrue("and the closure's band arm prices that point", field.softPriceSecAt(inBand) > 0.0)

        // On the ring's own south edge: the closure's zone arm names it, at distance 0.
        val onRingEdge = LatLng(south(445.0), east(800.0))
        assertEquals(
            "the ring's own edge is named too",
            0.0,
            field.priceClearanceM(onRingEdge),
            1e-6
        )
    }

    @After
    fun restoreSpeedZoneSwitch() {
        setSpeedZoneSwitch(false)
    }

    private fun setSpeedZoneSwitch(value: Boolean) {
        val field = AppConfig::class.java.getDeclaredField("routeAvoidSpeedZoneEnabled")
        field.isAccessible = true
        field.setBoolean(AppConfig, value)
    }

    // ── Fixtures ──────────────────────────────────────────────────────────────

    /** A constant price whose source declares its nearest boundary far past any group's half-length. */
    private fun flatField(counting: () -> Unit): RouteCostField = RouteCostField(
        listOf(
            RouteCostSource.Soft(
                priceSec = { counting(); fullSec },
                tag = MultipassCellState.ZONE,
                clearanceAt = { CLEAR_OF_ANY_BOUNDARY_M }
            )
        )
    )

    /**
     * A constant price counting **both** the reads a proved group pays: its declaration
     * ([RouteCostSource.Soft.priceClearanceM]) and its price ([RouteCostField.softPriceSecAt]), so a
     * proved group's `k − 2` saving is read net of what proving it costs.
     */
    private fun countingField(counting: () -> Unit): RouteCostField = RouteCostField(
        listOf(
            RouteCostSource.Soft(
                priceSec = { counting(); fullSec },
                tag = MultipassCellState.ZONE,
                clearanceAt = { counting(); CLEAR_OF_ANY_BOUNDARY_M }
            )
        )
    )

    /** The `evolutive` pair: an interior grid at 100 m and a band window at 20 m, one two-layer walk. */
    private fun twoLayerWalk(): GridWalk {
        val family = LatticeFamily.of(BBox(43.45, 43.55, 6.95, 7.05), coarseCellM = 100.0, fineCellM = 20.0)
        val coarseGrid = MultipassGrid(
            family.coarse.latSouth, family.coarse.lonWest,
            family.coarse.cellSizeDegLat, family.coarse.cellSizeDegLon,
            2, 2, family.coarse.cellM, baseCostSec(family.coarse.cellM, paceKn)
        )
        val fineGrid = MultipassGrid(
            family.fine.latSouth, family.fine.lonWest,
            family.fine.cellSizeDegLat, family.fine.cellSizeDegLon,
            10, 10, family.fine.cellM, baseCostSec(family.fine.cellM, paceKn)
        )
        val windows = WalkWindows.onLattice(
            family.layers,
            listOf(WalkWindow(coarseGrid, 0, 0, layer = 0), WalkWindow(fineGrid, 0, 0, layer = 1))
        )
        return GridWalk(coarseGrid, CellIndex(0, 0), CellIndex(1, 1), family.coarse.cellM, windows)
    }

    /**
     * The band's own law off a synthetic straight coast 340 m from the chord: the collar arm where the
     * coast distance sits between the width and the reach, and the declaration those two circles imply.
     */
    private fun bandEdgeField(counting: () -> Unit): RouteCostField = RouteCostField(
        listOf(
            RouteCostSource.Soft(
                priceSec = { p ->
                    counting()
                    bandPriceAt(bandWidthM, bandOutsideMarginM, fullSec, fraction, bandDistanceM(p))
                },
                tag = MultipassCellState.BAND,
                clearanceAt = { p ->
                    val d = bandDistanceM(p)
                    min(abs(d - bandWidthM), abs(d - bandReachM(bandWidthM, bandOutsideMarginM)))
                }
            )
        )
    )

    /** The holed ring's own price and declaration — the declaration optionally blind to the holes. */
    private fun holeField(
        blindToHoles: Boolean = false,
        counting: () -> Unit = {}
    ): RouteCostField {
        val zones = listOf(holedZone)
        return RouteCostField(
            listOf(
                RouteCostSource.Soft(
                    priceSec = { p ->
                        counting()
                        val interior = strictestLimitKnAt(zones, emptySet(), p.latitude, p.longitude)
                        val collar = speedZoneCollarLimitKnAt(zones, emptySet(), p.latitude, p.longitude, collarM)
                        zonePriceAtLimits(50.0, paceKn, interior ?: 0.0, collar ?: 0.0, lambda, fraction)
                    },
                    tag = MultipassCellState.ZONE,
                    clearanceAt = { p ->
                        if (blindToHoles) {
                            // The shipped collar read's own blindness: the outer ring alone.
                            val d = ringDistanceM(holedZone.outerRing, p)
                            min(d, abs(d - collarM))
                        } else {
                            speedZonePriceClearanceM(zones, emptySet(), p.latitude, p.longitude, collarM)
                        }
                    }
                )
            )
        )
    }

    /** The coast is a straight line 340 m south of the chord, long enough to be a perpendicular foot. */
    private fun bandDistanceM(p: LatLng): Double =
        SpatialOperations.pointToSegmentDistance(p, COAST_A, COAST_B)

    /** A patch of 2 m water, 30 m across, centred on the path's middle — one coarse interval's width. */
    private fun shallowPatch(p: LatLng): Boolean =
        abs(p.latitude - CHORD_LAT) <= 15.0 / M_PER_DEG_LAT &&
            abs(p.longitude - east(800.0)) <= 15.0 / M_PER_DEG_LON

    private fun ringDistanceM(ring: List<LatLng>, p: LatLng): Double {
        if (ring.size < 2) return Double.MAX_VALUE
        var nearest = Double.MAX_VALUE
        for (i in 0 until ring.size - 1) {
            nearest = min(nearest, SpatialOperations.pointToSegmentDistance(p, ring[i], ring[i + 1]))
        }
        return nearest
    }

    /** A world with one straight coast parallel to the chord — the shipped closure's own input. */
    private class BandWorld(
        private val bandM: Double,
        private val coastLat: Double,
        private val zones: List<SpeedZone> = emptyList()
    ) : MultipassWorld {
        private val mPerDegLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0

        override val coastlineReady = true
        override val depthReady = false
        override val bandWidthM = bandM
        override val regionBounds: BBox? = null

        override fun segmentsIn(box: BBox): List<MultipassEdge> = emptyList()
        override fun openCoastIn(box: BBox): List<List<LatLng>> = emptyList()
        override fun isWater(latitude: Double, longitude: Double) = true
        override fun distanceToCoastM(latitude: Double, longitude: Double): Double =
            abs(latitude - coastLat) * mPerDegLat
        override fun depthAt(latitude: Double, longitude: Double) = DepthSample.NONE
        override fun speedZonesIn(box: BBox): List<SpeedZone> = zones
        override fun zoneLimitKnAt(latitude: Double, longitude: Double): Double? =
            strictestLimitKnAt(zones, emptySet(), latitude, longitude)
    }

    private companion object {

        /** The chord's own parallel, so every fixture offsets in metres without a projection. */
        private const val CHORD_LAT = 43.50

        /** A declared boundary this far away is outside every group the fixture walks. */
        private const val CLEAR_OF_ANY_BOUNDARY_M = 10_000.0

        /** The outer ring's own half-width, and the hole's: 324 m wide and 222 m tall. */
        private const val OUTER_HALF_X_M = 650.0
        private const val HOLE_HALF_X_M = 162.0
        private const val HOLE_HALF_Y_M = 111.0

        private val M_PER_DEG_LAT = SpatialOperations.EARTH_RADIUS_M * PI / 180.0

        private val M_PER_DEG_LON = M_PER_DEG_LAT * cos(Math.toRadians(CHORD_LAT))

        /** A longitude [m] metres east of the chord's origin. */
        private fun east(m: Double): Double = 7.00 + m / M_PER_DEG_LON

        /** A latitude [m] metres south of the chord's parallel. */
        private fun south(m: Double): Double = CHORD_LAT - m / M_PER_DEG_LAT

        /** A latitude [m] metres north of the chord's parallel. */
        private fun north(m: Double): Double = CHORD_LAT + m / M_PER_DEG_LAT

        private val COAST_A = LatLng(south(340.0), east(-500.0))
        private val COAST_B = LatLng(south(340.0), east(2500.0))

        /**
         * The holed speed zone: an outer ring 1300 m wide and 890 m tall centred on the chord's own
         * midpoint, with a hole 324 m wide and 222 m tall in the middle of it.
         */
        private val holedZone = SpeedZone(
            "z", "Cap", 5.0,
            ring(800.0, OUTER_HALF_X_M, 445.0),
            listOf(ring(800.0, HOLE_HALF_X_M, HOLE_HALF_Y_M))
        )

        /** A closed rectangle in the chord's own metric frame, centred [centreXM] metres east. */
        private fun ring(centreXM: Double, halfXM: Double, halfYM: Double): List<LatLng> = listOf(
            LatLng(south(halfYM), east(centreXM - halfXM)),
            LatLng(south(halfYM), east(centreXM + halfXM)),
            LatLng(north(halfYM), east(centreXM + halfXM)),
            LatLng(north(halfYM), east(centreXM - halfXM)),
            LatLng(south(halfYM), east(centreXM - halfXM))
        )
    }
}
