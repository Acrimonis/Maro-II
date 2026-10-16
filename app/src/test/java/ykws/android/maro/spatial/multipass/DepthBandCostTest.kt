package ykws.android.maro.spatial.multipass

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.DepthSample
import ykws.android.maro.data.model.DepthSource
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.spatial.Units
import kotlin.math.PI
import kotlin.math.abs

/**
 * **The depth-valued band: one law, three readers, and no geometry anywhere.**
 *
 * The band prices a cell by the **depth under it** — `priceSecPerM × λ × max(0, softDepth − depth)` — so
 * it has no distance index, no radial scan and nothing that the resolution of the cell could distort.
 * Four things have to agree about it, and this pins each: the law alone ([depthBandPriceSecAt]), the
 * field's guard ([RouteCostField.softPriceSecPerMAt] at the pass's λ, reporting its own
 * [PriceArm.DEPTH]), the rasterizer's λ-free per-cell write ([MultipassGrid.depthBandSec], which the A\*
 * scales), and the fine tile that carries the same value to a later arm.
 *
 * The fixture the plan's phase 2 named lives here too, stated on the ladder's own **Coastal rung (λ 1)**
 * — no shipped rung is 0-aversion, so the retired wording is gone — and the control beside it shows the
 * same walk taking the shallow straight line with the band off.
 */
class DepthBandCostTest {

    private val paceKn = 25.0
    private val paceMps = Units.knotsToMps(paceKn)
    private val mPerDegLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0

    /** The band as the shipped plan answers it: the gate's minimum plus the plan's reach, at its gradient. */
    private val softDepthM = AppConfig.routeAvoidDepthGateMinM + AppConfig.routeEvolutiveDepthReachM
    private val band = DepthBand(softDepthM, AppConfig.routeEvolutiveDepthPriceSecPerM)

    // ── The plan's own answer ──────────────────────────────────────────────

    /**
     * **The reach is read as metres of depth above the wall, never as a planimetric standoff.** The outer
     * edge is the gate's own minimum plus the plan's key, and the gradient is the plan's other key: a
     * geometry-free law can express a depth and nothing else, and this is where that reading is pinned.
     */
    @Test
    fun thePlanReadsTheReachAsDepthAboveTheGatesOwnMinimum() {
        val answered = requireNotNull(EvolutiveGridPlan.depthBand()) {
            "evolutive's plan must price a depth band while the gate is armed"
        }

        assertEquals(
            "the band's outer edge is the gate's minimum plus the plan's reach",
            AppConfig.routeAvoidDepthGateMinM + AppConfig.routeEvolutiveDepthReachM,
            answered.softDepthM,
            1e-12
        )
        assertEquals(
            "and its gradient is the plan's own per-metre key",
            AppConfig.routeEvolutiveDepthPriceSecPerM,
            answered.secPerM,
            1e-12
        )
    }

    // ── The law's own arithmetic ───────────────────────────────────────────

    /**
     * **Full price at the wall's edge, zero at and beyond the outer edge, monotone in between.** A cell
     * standing on the gate's own minimum pays the whole band width; the price vanishes exactly where the
     * band's law says it ends and never comes back; and every metre shallower costs more than the last.
     */
    @Test
    fun thePriceIsFullAtTheWallAndZeroAtTheBandsOuterEdge() {
        assertEquals(
            "a cell on the wall's own edge pays the full band width",
            (softDepthM - AppConfig.routeAvoidDepthGateMinM) * band.secPerM,
            depthBandPriceSecAt(band, AppConfig.routeAvoidDepthGateMinM),
            1e-12
        )
        assertEquals(
            "the outer edge itself prices nothing",
            0.0, depthBandPriceSecAt(band, softDepthM), 0.0
        )
        assertEquals("and every metre beyond it too", 0.0, depthBandPriceSecAt(band, softDepthM + 40.0), 0.0)

        var previous = 0.0
        for (depthM in listOf(softDepthM, 24.0, 20.0, 15.0, 10.0, 6.0, AppConfig.routeAvoidDepthGateMinM)) {
            val price = depthBandPriceSecAt(band, depthM)
            assertTrue("the price never falls as the water shallows (at $depthM m)", price >= previous)
            previous = price
        }
        assertTrue("and the ramp actually rises", previous > 0.0)
    }

    /**
     * **An unsurveyed cell is unknown, not shallow.** The gate reads a NoData sounding as not below its
     * threshold, so the band follows the same reading: `NaN` prices nothing rather than inventing a wall
     * under a cell nobody has surveyed.
     */
    @Test
    fun anUnknownDepthIsNeverPriced() {
        assertEquals("a NoData reading is not a sounding", 0.0, depthBandPriceSecAt(band, Double.NaN), 0.0)
    }

    /**
     * **With no extra reach the band is inert, and that is arithmetic rather than a special case.** Its
     * outer edge then stands on the wall itself, so every cell the gate lets through is already at or
     * beyond it — the price is zero on the water the walk may use, and the line returns to today's.
     */
    @Test
    fun aBandWithNoExtraReachPricesNothingTheGateLetsThrough() {
        val inert = DepthBand(AppConfig.routeAvoidDepthGateMinM, AppConfig.routeEvolutiveDepthPriceSecPerM)

        assertEquals("the outer edge is the gate's own minimum", 0.0, depthBandPriceSecAt(inert, 3.0), 0.0)
        assertEquals("water at it", 0.0, depthBandPriceSecAt(inert, 12.0), 0.0)
        assertEquals("deeper water", 0.0, depthBandPriceSecAt(inert, 60.0), 0.0)
        assertEquals("and an unknown cell", 0.0, depthBandPriceSecAt(inert, Double.NaN), 0.0)
    }

    // ── The field's own arm ────────────────────────────────────────────────

    /**
     * **The guard prices the law at the pass's λ, reports [PriceArm.DEPTH], and declares a zero
     * clearance.** The zero is the honest floor — a depth-valued price may change wherever the water
     * does, so no span over it is provable — and it is what the priced walk pays for.
     */
    @Test
    fun theGuardPricesTheLawAtThePassesCursorAndNamesItsOwnArm() {
        val world = ShelfWorld(shallowBox = box, unknownBox = null)
        val at = LatLng(box.latSouth + 5e-4, box.lonWest + 5e-4)
        val field = costField(
            world, 20.0, paceKn, withZones = false, withBand = false, zones = emptyList(),
            lambda = 1.0, plan = EvolutiveGridPlan
        )

        assertEquals(
            "the per-metre rate is the law over the cell that priced it",
            depthBandPriceSecAt(band, 3.0) / 20.0,
            field.softPriceSecPerMAt(at),
            1e-12
        )
        assertEquals("a priced point reports one arm", 1, field.armPricesAt(at).size)
        assertEquals("and that arm is DEPTH", PriceArm.DEPTH, field.armPricesAt(at).first().first)
        assertEquals(
            "the band's honest clearance proves no group at all",
            0.0, field.priceClearanceM(at), 0.0
        )
        assertTrue("the field declares a price in force", field.hasSoft)
        assertTrue("and names the band", field.hasDepthBand)
    }

    /** **λ scales it, as it scales every slow source** — the Coastal rung prices a fifth of the Detour's. */
    @Test
    fun theCursorScalesTheBandLikeEveryOtherSlowSource() {
        val world = ShelfWorld(shallowBox = box, unknownBox = null)
        val at = LatLng(box.latSouth + 5e-4, box.lonWest + 5e-4)
        val coastal = costField(
            world, 20.0, paceKn, withZones = false, withBand = false, zones = emptyList(),
            lambda = 1.0, plan = EvolutiveGridPlan
        )
        val detour = costField(
            world, 20.0, paceKn, withZones = false, withBand = false, zones = emptyList(),
            lambda = 5.0, plan = EvolutiveGridPlan
        )

        assertEquals(
            "λ 5 leans five times as deep as the Coastal rung's λ 1",
            5.0 * coastal.softPriceSecAt(at), detour.softPriceSecAt(at), 1e-12
        )
        assertTrue("and the law is what it scales", coastal.softPriceSecAt(at) > 0.0)
    }

    // ── The grid's own write ───────────────────────────────────────────────

    /**
     * **The rasterizer writes the λ-free price per cell, and never into the base.** A grid is shared by
     * the three rungs, so what it carries is the law without a cursor; and the price is an absolute time,
     * so no cell's own source cost may absorb it — the pace would scale it with the base.
     */
    @Test
    fun theGridCarriesTheLawFreeOfAnyCursorAndNeverInTheBase() {
        // One patch of unsurveyed water inside the corridor, so the NoData reading is seen on a cell.
        val unknownBox = boxAt(43.50055, 43.50075, 7.00115, 7.00145)
        val world = ShelfWorld(shallowBox = box, unknownBox = unknownBox)
        val field = costField(
            world, 20.0, paceKn, withZones = false, withBand = false, zones = emptyList(),
            lambda = 0.0, plan = EvolutiveGridPlan
        )
        val grid = rasterize(box, 20.0, paceKn, 0.0, emptyList(), emptyList(), box.latNorth, field)

        var priced = 0
        var unknown = 0
        for (row in 0 until grid.rows) {
            for (col in 0 until grid.cols) {
                if (!grid.passable(row, col)) continue
                val centre = grid.center(row, col)
                val stored = grid.depthBandSec(row, col)
                assertEquals(
                    "the cell stores the law at its own centre's depth, with no cursor applied",
                    depthBandPriceSecAt(band, world.depthMAt(centre)),
                    stored,
                    1e-12
                )
                assertEquals(
                    "and the base never absorbs it",
                    grid.baseCostSec, grid.sourceCostSec(row, col), 1e-12
                )
                if (stored > 0.0) priced++
                if (inBox(unknown = unknownBox, center = centre)) unknown++
            }
        }
        assertTrue("the shallow shelf is priced somewhere", priced > 0)
        assertTrue("and the unsurveyed patch is never", unknown > 0)
    }

    // ── `avoid` is untouched ───────────────────────────────────────────────

    /**
     * **`avoid` prices no depth band, in the plan, the field, or the grid.** Its plan answers none, its
     * field has no reader and no DEPTH arm, and a grid rastered from that field carries a zero on every
     * cell — so its line, its clock and its report are exactly what they were.
     */
    @Test
    fun avoidAnswersNoBandAtAll() {
        val world = ShelfWorld(shallowBox = box, unknownBox = null)
        val at = LatLng(box.latSouth + 5e-4, box.lonWest + 5e-4)
        val field = costField(
            world, 20.0, paceKn, withZones = false, withBand = false, zones = emptyList(),
            lambda = 5.0, plan = UniformGridPlan
        )
        val grid = rasterize(box, 20.0, paceKn, 0.0, emptyList(), emptyList(), box.latNorth, field)

        assertEquals("`avoid`'s plan answers no band", null, UniformGridPlan.depthBand())
        assertFalse("so its field names none", field.hasDepthBand)
        assertEquals("and prices nothing at a shallow cell", 0.0, field.softPriceSecAt(at), 0.0)
        assertTrue("no arm is reported either", field.armPricesAt(at).isEmpty())
        for (row in 0 until grid.rows) {
            for (col in 0 until grid.cols) {
                assertEquals(
                    "and no cell of its grid carries a band price",
                    0.0, grid.depthBandSec(row, col), 0.0
                )
            }
        }
    }

    // ── The Coastal rung's own fixture ─────────────────────────────────────

    /**
     * **A short deeper detour beats a shallow straight line at the Coastal rung (λ 1).**
     *
     * A vertical wall's shortest crossing runs through a shallow shelf six cells long, and the shelf's
     * water is priced at the law's own maximum — the gate's edge, so a cell there pays the whole band
     * width. Two rows up the water is deep and free. At the ladder's Coastal rung the six priced cells
     * cost more than the four extra steps that avoid them, so the line leaves the shelf; with the band
     * silent the same walk takes the shallow crossing, and the detour stays a **short** one.
     */
    @Test
    fun aShortDeeperDetourBeatsTheShallowCrossingAtTheCoastalRung() = runTest {
        val cellM = 20.0
        val shelfPriceSec = depthBandPriceSecAt(band, AppConfig.routeAvoidDepthGateMinM)

        /** A wall at column 4 spanned at rows 1, 3 and 5, with the shallow shelf over 2..4 × 1..8. */
        fun grid(): MultipassGrid {
            val g = MultipassGrid(
                latSouth = 0.0, lonWest = 0.0, cellSizeDegLat = cellM / mPerDegLat,
                cellSizeDegLon = cellM / mPerDegLat, rows = 7, cols = 9, cellM = cellM,
                baseCostSec = cellM / paceMps
            )
            for (row in 0 until 7) {
                if (row == 1 || row == 3 || row == 5) continue
                g.markLand(row, 4)
            }
            for (row in 2..4) {
                for (col in 1..8) g.applyDepthBandSec(row, col, shelfPriceSec)
            }
            return g
        }

        val silent = MultipassSearch.search(grid(), CellIndex(3, 0), CellIndex(3, 8), paceMps, depthK = 0.0)
        val coastal = MultipassSearch.search(grid(), CellIndex(3, 0), CellIndex(3, 8), paceMps, depthK = 1.0)

        val silentRow = silent.path!!.first { it.col == 4 }.row
        val coastalRow = coastal.path!!.first { it.col == 4 }.row
        assertEquals("with the band silent the shortest crossing is the shallow one", 3, silentRow)
        assertTrue(
            "at the Coastal rung the line crosses the wall clear of the shelf (row $coastalRow)",
            coastalRow != 3
        )
        assertTrue("and it stays a short detour, two rows at most", abs(coastalRow - 3) <= 2)
        assertTrue("the band's seconds reach the walk", coastal.costSec > silent.costSec)
    }

    // ── The tile carries the same price ────────────────────────────────────

    /**
     * **A cached tile and a fresh build price one band identically.** The tile's field writes the same
     * λ-free price, the extract stores it per member, and `writeInto` restores it unchanged — a pace
     * change moves the member's own cost (the base it multiplies) and never the band's seconds.
     */
    @Test
    fun aTilesStoredBandPriceIsTheLawAndSurvivesWriteInto() {
        val world = ShelfWorld(shallowBox = box, unknownBox = null)
        val lattice = WalkLattice.of(box, 20.0)
        val tileCells = 8
        val tileField = tileCostField(world, baseCostSec = 1.0, markerStrength = 1.0, plan = EvolutiveGridPlan)
        val raster = rasterizeWindow(
            box, lattice, paceKn = paceKn, marginM = 0.0, edges = emptyList(), openCoast = emptyList(),
            capLatNorth = box.latNorth, field = tileField, baseCostSecOverride = 1.0
        )
        val tile = FineTile.extract(raster, tileRow = 0, tileCol = 0, tileCells = tileCells, haloCells = 0)
        val armBase = 5.0
        val arm = MultipassGrid(
            latSouth = raster.latSouth, lonWest = raster.lonWest,
            cellSizeDegLat = raster.cellSizeDegLat, cellSizeDegLon = raster.cellSizeDegLon,
            rows = tileCells, cols = tileCells, cellM = raster.cellM, baseCostSec = armBase
        )

        tile.writeInto(arm, windowRowOffset = 0, windowColOffset = 0)

        var priced = 0
        for (member in tile.localIndex.indices) {
            val local = tile.localIndex[member]
            val row = local / tileCells
            val col = local % tileCells
            val centre = raster.center(row, col)
            val expected = depthBandPriceSecAt(band, world.depthMAt(centre))
            assertEquals(
                "the tile stores the law's own λ-free price at the member's centre",
                expected, tile.depthBandSec[member], 1e-12
            )
            assertEquals(
                "and restores it verbatim into the arm's grid",
                tile.depthBandSec[member], arm.depthBandSec(row, col), 1e-12
            )
            assertEquals(
                "while the member's own cost is still its pace-free factor times the arm's base",
                armBase * tile.sourceCostFactor[member], arm.sourceCostSec(row, col), 1e-12
            )
            if (expected > 0.0) priced++
        }
        assertTrue("the shallow part of the block is priced", priced > 0)
    }

    // ── Fixtures ───────────────────────────────────────────────────────────

    private val box = BBox(43.50, 43.5016, 7.00, 7.0024)

    private fun boxAt(latSouth: Double, latNorth: Double, lonWest: Double, lonEast: Double) =
        BBox(latSouth, latNorth, lonWest, lonEast)

    private fun inBox(unknown: BBox?, center: LatLng): Boolean =
        unknown != null &&
            center.latitude in unknown.latSouth..unknown.latNorth &&
            center.longitude in unknown.lonWest..unknown.lonEast

    /**
     * **A ready world with three depths.** The gate's own minimum inside [shallowBox] (so the law pays its
     * whole band width there and the cell still passes the gate), `NoData` inside [unknownBox], and 20 m
     * everywhere else. No coastline is harvested and no 300 m band stands, so the depth band is the only
     * price this world carries.
     */
    private class ShelfWorld(
        private val shallowBox: BBox,
        private val unknownBox: BBox?
    ) : MultipassWorld {
        override val coastlineReady: Boolean get() = true
        override val depthReady: Boolean get() = true
        override val regionBounds: BBox? get() = null
        override val bandWidthM: Double get() = 0.0
        override fun segmentsIn(box: BBox): List<MultipassEdge> = emptyList()
        override fun openCoastIn(box: BBox): List<List<LatLng>> = emptyList()
        override fun isWater(latitude: Double, longitude: Double): Boolean = true
        override fun distanceToCoastM(latitude: Double, longitude: Double): Double = Double.MAX_VALUE
        override fun depthAt(latitude: Double, longitude: Double): DepthSample = when {
            inRange(unknownBox, latitude, longitude) -> DepthSample.NONE
            inRange(shallowBox, latitude, longitude) ->
                DepthSample(3.0f, DepthSource.LITTO3D, 90, true)
            else -> DepthSample(20.0f, DepthSource.LITTO3D, 90, true)
        }

        /** The depth the router reads, spelled exactly as the field reads it — this world's own probe. */
        fun depthMAt(p: LatLng): Double {
            val sample = depthAt(p.latitude, p.longitude)
            return if (sample.hasData && !sample.depthM.isNaN()) sample.depthM.toDouble() else Double.NaN
        }

        private fun inRange(box: BBox?, latitude: Double, longitude: Double): Boolean =
            box != null &&
                latitude in box.latSouth..box.latNorth &&
                longitude in box.lonWest..box.lonEast
    }
}
