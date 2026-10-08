package ykws.android.maro.spatial.multipass

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.DepthSample
import ykws.android.maro.data.model.DepthSource
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.data.regulation.SpeedZone
import ykws.android.maro.spatial.LandRingOrientation
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.spatial.Units
import java.io.File
import java.util.Properties
import kotlin.math.abs

/**
 * **The `selective` plan's own fixtures** — the seam Phase 1 moved and the byte-identical `evolutive`
 * windows it must not move, the four collars Phase 2 marks, the collar membership Phase 3 keeps, and the
 * one depth law Phase 4 makes the search and the guard share.
 */
class RouteSelectivePlanTest {

    private val corridor = BBox(43.500, 43.680, 7.000, 7.080)

    private val fineCellM = 20.0

    private val lattice = WalkLattice.of(corridor, fineCellM)

    /** A coast running the corridor's own length near its western side, as both harvested shapes. */
    private val coastPoints = listOf(
        LatLng(43.501, 7.004), LatLng(43.546, 7.006), LatLng(43.590, 7.008),
        LatLng(43.635, 7.010), LatLng(43.679, 7.012)
    )

    private val edges =
        listOf(MultipassEdge(coastPoints[0], coastPoints[1], LandRingOrientation.OPEN_COAST))

    private val openCoast = listOf(coastPoints.subList(1, coastPoints.size))

    private val builder = RouteGridBuilder(EvolutiveGridPlan)

    private fun cellCount(boxes: List<BBox>): Int =
        boxes.sumOf { builder.cellsOf(it, lattice.cellSizeDegLat, lattice.cellSizeDegLon) }

    // ── Phase 1 — the seam, and the windows that must not move ─────────────

    @Test
    fun theEvolutivePlanAnswersTodaysCoastRibbon() {
        val world = TestWorld(bandWidthM = 300.0)
        val answer = EvolutiveGridPlan.fineWater(
            FineWaterQuery(world, corridor, edges, openCoast, marginM = 50.0, baseCellM = 100.0)
        )

        val cutReach = bandReachM(300.0, AppConfig.routeAvoidZone300OutsideMarginM) + 50.0 + 100.0
        assertEquals("the cut reach is the one fineWaterReachM derived", listOf(cutReach), answer.coastReachesM)
        assertEquals(1, answer.coastBandsM.size)
        assertEquals("the band starts at the coast", 0.0, answer.coastBandsM.first().start, 1e-9)
        assertEquals(
            "and ends at margin + one coarse cell — the old bandMask's own radius",
            150.0,
            answer.coastBandsM.first().endInclusive,
            1e-9
        )
        assertEquals("evolutive answers no zone rim", 0.0, answer.zoneRimM, 1e-9)
        assertEquals("evolutive answers no depth collar", 0.0, answer.depthCollarM, 1e-9)
    }

    @Test
    fun thePlansAnswerReproducesTheWindowCutByteForByte() {
        val world = TestWorld(bandWidthM = 300.0)
        val answer = EvolutiveGridPlan.fineWater(
            FineWaterQuery(world, corridor, edges, openCoast, marginM = 50.0, baseCellM = 100.0)
        )
        val cutReach = answer.coastReachesM.first()

        val fromReach = builder.fineWindowBoxes(lattice, corridor, edges, openCoast, cutReach)
        val fromPlan = builder.fineWindowBoxes(lattice, corridor, edges, openCoast, answer, null, emptyList())

        assertEquals("the seam's plan answer cuts the same windows", fromReach, fromPlan)
        assertTrue("and the ribbon marks some windows", fromPlan.isNotEmpty())
    }

    @Test
    fun aPlanAnsweringNothingCutsNoWindows() {
        assertTrue(
            "an empty plan answer is no fine layer at all",
            builder.fineWindowBoxes(lattice, corridor, edges, openCoast, FineWater(), null, emptyList()).isEmpty()
        )
    }

    // ── Phase 2 — the four collars, one width key each ─────────────────────

    @Test
    fun theShoreAndBandOuterCoastCollarsMarkGrowingStrips() {
        val shore = FineWater(coastReachesM = listOf(100.0), coastBandsM = listOf(0.0..100.0))
        val bandOuter = FineWater(coastReachesM = listOf(350.0), coastBandsM = listOf(250.0..350.0))

        val shoreCells = cellCount(builder.fineWindowBoxes(lattice, corridor, edges, openCoast, shore, null, emptyList()))
        val bandCells =
            cellCount(builder.fineWindowBoxes(lattice, corridor, edges, openCoast, bandOuter, null, emptyList()))

        assertTrue("the shore collar marks cells", shoreCells > 0)
        assertTrue("the wider band-outer collar marks strictly more ($bandCells of $shoreCells)", bandCells > shoreCells)
    }

    @Test
    fun theZoneRimCollarMarksAZoneWithNoCoastBesideIt() {
        val zone = SpeedZone("z", "Zone", 5.0, rectangle(LatLng(43.55, 7.04), 300.0, 300.0), emptyList())

        val withRim = builder.fineWindowBoxes(
            lattice, corridor, emptyList(), emptyList(), FineWater(zoneRimM = 100.0), null, listOf(zone)
        )
        val withNone = builder.fineWindowBoxes(
            lattice, corridor, emptyList(), emptyList(), FineWater(), null, listOf(zone)
        )

        assertTrue("a zone rim in open water still marks windows", withRim.isNotEmpty())
        assertTrue("and with no collar the zone marks nothing", withNone.isEmpty())
    }

    @Test
    fun theDepthDilationCollarMarksTheShallowWallsNeighbourhood() {
        val wall = LatLng(43.560, 7.030)
        val probe: (LatLng) -> Boolean = { p ->
            abs(p.latitude - wall.latitude) < 1e-3 && abs(p.longitude - wall.longitude) < 1e-3
        }

        val boxes = builder.fineWindowBoxes(
            lattice, corridor, emptyList(), emptyList(), FineWater(depthCollarM = 100.0), probe, emptyList()
        )

        assertTrue("the shallow wall's collar marks windows", boxes.isNotEmpty())
    }

    // ── Phase 3 — the collar membership, kept by the window mask ───────────

    @Test
    fun aZoneRimInOpenWaterStaysFineWater() {
        val zone = ZoneRing(rectangle(LatLng(43.55, 7.04), 300.0, 300.0), emptyList(), 5.0)
        val mask = FineMask(coastBandsM = emptyList(), zoneRimM = 100.0)

        val grid = rasterizeWindow(
            corridor, lattice, paceKn = 25.0, marginM = 50.0,
            edges = emptyList(), openCoast = emptyList(), capLatNorth = corridor.latNorth,
            zones = listOf(zone), fineMask = mask
        )

        // A cell 350 m east of the ring's centre is 50 m outside the boundary — inside the rim collar.
        val rimPoint = SpatialOperations.pointAlongBearing(43.55, 7.04, 90.0, 350.0)
        val rimCell = grid.cellOf(rimPoint.latitude, rimPoint.longitude)
        assertTrue(
            "the zone rim in open water is fine water",
            grid.cell(rimCell.row, rimCell.col).passable
        )
        val farCell = grid.cellOf(corridor.latSouth + 5e-4, corridor.lonWest + 5e-4)
        assertFalse(
            "water far from every collar is painted land",
            grid.cell(farCell.row, farCell.col).passable
        )
    }

    // ── Phase 4 — the one depth law ────────────────────────────────────────

    @Test
    fun theGridAndTheGuardPriceOnePointIdentically() {
        val wallLat = 43.560
        val probe: (LatLng) -> Boolean = { p -> p.latitude <= wallLat }
        val cellM = 20.0
        val band = DepthBand(DepthBandLaw.bandM(), DepthBandLaw.stepM(cellM), probe)
        val k = 4.0

        val grid = rasterize(
            BBox(43.555, 43.565, 7.030, 7.050), cellM, 25.0, 0.0,
            emptyList(), emptyList(), 43.565, depthBand = band
        )
        val world = TestWorld(bandWidthM = 0.0, shallowBelowLat = wallLat)
        val guard = costField(
            world, cellM, 25.0, withZones = false, withBand = false, zones = emptyList(),
            lambda = k, withDepthBand = true
        )

        var checked = 0
        for (r in 0 until grid.rows) {
            for (c in 0 until grid.cols) {
                val coef = grid.depthPriceCoef(r, c)
                if (coef <= 0.0) continue
                checked++
                val centre = grid.center(r, c)
                val guardCoef = DepthBandLaw.coefAt(centre, probe, DepthBandLaw.stepM(cellM), DepthBandLaw.bandM())
                assertEquals("the grid stores the law's own coefficient", guardCoef, coef, 1e-9)
                assertEquals(
                    "and the guard prices that point identically",
                    DepthBandLaw.priceSec(cellM, coef, k),
                    guard.evaluate(centre).softCostSec,
                    1e-9
                )
            }
        }
        assertTrue("the wall's band carries priced cells", checked > 0)
    }

    @Test
    fun aZeroAversionPassIgnoresTheBand() {
        val probe: (LatLng) -> Boolean = { p -> p.latitude <= 43.560 }

        assertEquals(
            "at λ = 0 the band costs nothing",
            0.0,
            DepthBandLaw.priceSec(20.0, 1.0, 0.0),
            1e-12
        )
        val world = TestWorld(bandWidthM = 0.0, shallowBelowLat = 43.560)
        val guard = costField(
            world, 20.0, 25.0, withZones = false, withBand = false, zones = emptyList(),
            lambda = 0.0, withDepthBand = true
        )
        var priced = 0
        for (r in 0 until 40) {
            for (c in 0 until 40) {
                val p = LatLng(43.556 + r * 2e-4, 7.032 + c * 2e-4)
                if (DepthBandLaw.coefAt(p, probe, 20.0, DepthBandLaw.bandM()) > 0.0 &&
                    guard.evaluate(p).softCostSec > 0.0
                ) {
                    priced++
                }
            }
        }
        assertEquals("a 0-aversion pass never prices the wall", 0, priced)
    }

    @Test
    fun aDeeperDetourWinsWhenThePricedGapIsShort() = runBlocking {
        val mPerDegLat = SpatialOperations.EARTH_RADIUS_M * kotlin.math.PI / 180.0
        val cellM = 100.0
        val dLat = cellM / mPerDegLat
        val dLon = dLat
        val paceMps = Units.knotsToMps(25.0)

        // A vertical wall at column 4, spanned only at rows 1, 3 and 5: the straight crossing at row 3 is
        // the shortest, and its neighbourhood is the priced band.
        fun grid(): MultipassGrid {
            val g = MultipassGrid(0.0, 0.0, dLat, dLon, rows = 7, cols = 9, cellM = cellM, baseCostSec = cellM / paceMps)
            for (r in 0 until 7) {
                if (r == 1 || r == 3 || r == 5) continue
                g.markLand(r, 4)
            }
            for (r in 2..4) {
                for (c in 3..5) g.applyDepthPriceCoef(r, c, 1.0)
            }
            return g
        }

        val free = MultipassSearch.search(grid(), CellIndex(3, 0), CellIndex(3, 8), paceMps, depthK = 0.0)
        val priced = MultipassSearch.search(grid(), CellIndex(3, 0), CellIndex(3, 8), paceMps, depthK = 50.0)

        val freeRow = free.path!!.first { it.col == 4 }.row
        val pricedRow = priced.path!!.first { it.col == 4 }.row

        assertEquals("with no aversion the short crossing at row 3 wins", 3, freeRow)
        assertTrue("a priced wall sends the line around it (crossed at row $pricedRow)", pricedRow != 3)
        assertTrue("and the priced walk costs more than the free one", priced.costSec > free.costSec)
    }

    // ── Phase 5 — the portable union cache ─────────────────────────────────

    @Test
    fun theMaskCacheReusesOnTheSameKeyAndRecomputesOnAWidthChange() {
        SelectiveMaskCache.clear()
        var computes = 0
        // The key names exactly what the plan's union reads: the band's width and the four collar widths.
        val key = SelectiveMaskCache.Key(
            bandWidthM = 300.0, shoreCollarM = 100.0, bandCollarM = 100.0, zoneRimM = 100.0, depthCollarM = 100.0
        )

        val first = SelectiveMaskCache.getOrCompute(key) { computes++; FineWater(coastReachesM = listOf(1.0)) }
        val second = SelectiveMaskCache.getOrCompute(key) { computes++; FineWater(coastReachesM = listOf(2.0)) }
        assertEquals("the same key computes once", 1, computes)
        assertEquals(first, second)

        val moved = key.copy(shoreCollarM = 200.0)
        val third = SelectiveMaskCache.getOrCompute(moved) { computes++; FineWater(coastReachesM = listOf(3.0)) }
        assertEquals("a width change is a new union", 2, computes)
        assertEquals(listOf(3.0), third.coastReachesM)
        SelectiveMaskCache.clear()
    }

    // ── Phase 6 — the shipped record ───────────────────────────────────────

    @Test
    fun theShippedPropertiesCarryTheSelectiveKeysAndKeepAvoidAsTheDefault() {
        val props = shippedProperties()

        assertEquals("the selective coarse cell ships", "100", props.getProperty("route.selective.grid.cellM"))
        assertEquals("the selective fine cell ships", "20", props.getProperty("route.selective.grid.fineCellM"))
        assertEquals("the shore collar ships", "100", props.getProperty("route.selective.shore.collarM"))
        assertEquals("the band-outer collar ships", "100", props.getProperty("route.selective.band.collarM"))
        assertEquals("the zone-rim collar ships", "100", props.getProperty("route.selective.zone.rimM"))
        assertEquals("the depth collar ships", "100", props.getProperty("route.selective.depth.collarM"))
        assertEquals("the depth extra ships", "25", props.getProperty("route.selective.depth.bandExtraM"))
        assertEquals("the depth per-metre price ships", "0.05", props.getProperty("route.selective.depth.priceSecPerM"))
        assertEquals("selective is never the default", "avoid", props.getProperty("route.engine.id"))
    }

    // ── The two fixtures the review named ──────────────────────────────────

    /**
     * **The `selective` plan's own union, read from the seam.** `fineWater` answers the four collars'
     * geography, the plan's one home of the four reaches: the shore strip 0–`shoreCollarM`, the
     * band-outer strip straddling the band's width, the zone-rim width and the depth-collar width. The
     * widths are the shipped `route.selective.*` keys, so the assertion is cache-independent.
     */
    @Test
    fun theSelectivePlanAnswersTheFourCollarsUnion() {
        SelectiveMaskCache.clear()
        val world = TestWorld(bandWidthM = 300.0)
        val answer = SelectiveGridPlan.fineWater(
            FineWaterQuery(world, corridor, edges, openCoast, marginM = 50.0, baseCellM = 100.0)
        )
        val shore = AppConfig.routeSelectiveShoreCollarM
        val half = AppConfig.routeSelectiveBandCollarM / 2.0

        assertEquals(
            "the shore strip and the band-outer strip are the two coast reaches",
            listOf(shore, 300.0 + half), answer.coastReachesM
        )
        assertEquals(
            "and the two membership bands are the shore and the band's outer straddle",
            listOf(0.0..shore, (300.0 - half)..(300.0 + half)), answer.coastBandsM
        )
        assertEquals("the zone-rim width is the plan's key", AppConfig.routeSelectiveZoneRimM, answer.zoneRimM, 1e-9)
        assertEquals(
            "the depth-collar width is the plan's key",
            AppConfig.routeSelectiveDepthCollarM, answer.depthCollarM, 1e-9
        )
        SelectiveMaskCache.clear()
    }

    /**
     * **The window mask keeps `evolutive`'s coast ribbon and nothing else.** Phase 3 generalised the
     * rasterizer's band mask to a collar-membership predicate; for `evolutive` that predicate must
     * reproduce the old coast-only mask exactly — the water within `marginM + baseCellM` of the coast
     * stays fine, the water beyond is land — or the fine layer and the land disagree about the coast.
     */
    @Test
    fun theWindowMaskReproducesEvolutivesCoastRibbon() {
        val world = TestWorld(bandWidthM = 300.0)
        val marginM = 50.0
        val baseCellM = 100.0
        val answer = EvolutiveGridPlan.fineWater(
            FineWaterQuery(world, corridor, edges, openCoast, marginM = marginM, baseCellM = baseCellM)
        )
        val mask = answer.toMask(null, 0.0)
        val grid = rasterizeWindow(
            corridor, lattice, paceKn = 25.0, marginM = marginM,
            edges = edges, openCoast = openCoast, capLatNorth = corridor.latNorth,
            fineMask = mask
        )
        // The old bandMask's own radius: the margin plus one coarse cell, measured east into open water.
        val ribbonM = marginM + baseCellM
        val anchor = coastPoints[2]
        val within = SpatialOperations.pointAlongBearing(anchor.latitude, anchor.longitude, 90.0, ribbonM - 20.0)
        val beyond = SpatialOperations.pointAlongBearing(anchor.latitude, anchor.longitude, 90.0, ribbonM + 200.0)
        val withinCell = grid.cellOf(within.latitude, within.longitude)
        val beyondCell = grid.cellOf(beyond.latitude, beyond.longitude)

        assertTrue("water inside the ribbon stays fine", grid.cell(withinCell.row, withinCell.col).passable)
        assertFalse("water beyond the ribbon is painted land", grid.cell(beyondCell.row, beyondCell.col).passable)
    }

    // ── Fixtures ───────────────────────────────────────────────────────────

    private fun shippedProperties(): Properties {
        val file = System.getProperty("maro.repoDir")
            ?.let { File(it, "app/src/main/assets/maro.properties") }
            ?.takeIf { it.isFile }
            ?: File("src/main/assets/maro.properties")
        return Properties().apply { file.inputStream().use { load(it) } }
    }

    /** A closed rectangle of half-side [halfM] (m) centred on [centre], as a ring's own vertices. */
    private fun rectangle(centre: LatLng, halfM: Double, halfY: Double): List<LatLng> {
        val mPerDegLat = SpatialOperations.EARTH_RADIUS_M * kotlin.math.PI / 180.0
        val mPerDegLon = mPerDegLat * kotlin.math.cos(Math.toRadians(centre.latitude))
        val dLat = halfY / mPerDegLat
        val dLon = halfM / mPerDegLon
        return listOf(
            LatLng(centre.latitude - dLat, centre.longitude - dLon),
            LatLng(centre.latitude - dLat, centre.longitude + dLon),
            LatLng(centre.latitude + dLat, centre.longitude + dLon),
            LatLng(centre.latitude + dLat, centre.longitude - dLon),
            LatLng(centre.latitude - dLat, centre.longitude - dLon)
        )
    }

    /** A ready world; [shallowBelowLat] makes every sample south of that latitude read under the gate. */
    private class TestWorld(
        override val bandWidthM: Double = 0.0,
        private val shallowBelowLat: Double = Double.NaN
    ) : MultipassWorld {
        override val coastlineReady: Boolean get() = true
        override val depthReady: Boolean get() = true
        override val regionBounds: BBox? get() = null
        override fun segmentsIn(box: BBox): List<MultipassEdge> = emptyList()
        override fun openCoastIn(box: BBox): List<List<LatLng>> = emptyList()
        override fun isWater(latitude: Double, longitude: Double): Boolean = true
        override fun distanceToCoastM(latitude: Double, longitude: Double): Double = Double.MAX_VALUE
        override fun depthAt(latitude: Double, longitude: Double): DepthSample =
            if (!shallowBelowLat.isNaN() && latitude <= shallowBelowLat) {
                DepthSample(2.0f, DepthSource.LITTO3D, 90, true)
            } else {
                DepthSample(20.0f, DepthSource.LITTO3D, 90, true)
            }
    }
}
