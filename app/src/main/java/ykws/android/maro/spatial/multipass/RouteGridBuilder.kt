package ykws.android.maro.spatial.multipass

import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.spatial.SpatialOperations
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * **One grid build** — the corridor → the harvest → the grid, **once** → the ends' berth carve → the
 * corner sets, lifted out of the pass pipeline so the context build and its instrument readers are a
 * named seat of their own.
 *
 * Everything it answers is **λ-free**: the grid stores limits, never prices, so the three rungs share
 * it and each rung prices its own pass. The [plan] is the algorithm's own injection point — the
 * rectangles the first walk uses — and it is the seat's one constructor argument.
 */
internal class RouteGridBuilder(private val plan: RouteGridPlan = UniformGridPlan) {

    /**
     * **One grid build** — the corridor → the harvest → the grid, **once** → the ends' berth carve →
     * the corner sets. Everything λ-free: the grid stores limits, never prices, so the three rungs
     * share it and each rung prices its own pass. `null` where the corridor is empty.
     *
     * The pace is the engine's own and is asked at its call, like its instrument: [trace] is handed in
     * rather than owned here, so the seat never owns the emission sequence.
     */
    internal suspend fun buildGrid(
        world: MultipassWorld,
        from: RoutePoint,
        to: RoutePoint,
        reach: Double,
        pace: Double,
        trace: (() -> String) -> Unit = {}
    ): GridContext? {
        val corridor = corridorBox(from, to, world.regionBounds, reach)
        if (corridor == null) {
            trace { "CORRIDOR reach=${fmt(reach)}m box=empty clampedByRegion=n/a" }
            return null
        }
        val box = corridor.box
        val regionSaturated = world.regionBounds != null && box == world.regionBounds
        trace {
            "CORRIDOR reach=${fmt(reach)}m box=${boxText(box)} clampedByRegion=${corridor.clampedByRegion} " +
                "regionSaturated=$regionSaturated"
        }
        val edges = world.segmentsIn(box)
        val openCoast = world.openCoastIn(box)
        val capLatNorth = world.regionBounds?.latNorth ?: box.latNorth
        // The plan hands back the walk's tiles. `avoid`'s plan answers one tile over the whole corridor at
        // one size and keeps the single-grid walk below **exactly** — the suite's own counts are the proof.
        // A plan answering more than one tile, the adaptive grid's two layers, takes the layered path, where
        // the interior tile stays the grid every single-grid read site describes and the band is a window.
        val tiles = plan.firstWalkGrid(box, AppConfig.routeAvoidGridCellM)
        if (tiles.size > 1) {
            return buildLayeredGrid(
                world, from, to, box, regionSaturated, edges, openCoast, capLatNorth, tiles, pace, trace
            )
        }
        val cellM = tiles.first().cellM
        val fineCellM = plan.fineCellM(cellM)
        // The walk's own ceiling, asked **before** anything is rastered: a corridor this wide dies in the
        // rasterizer rather than answering, so it is refused here — with its own trace line, and the engine
        // answers the line it already has.
        val singleLattice = WalkLattice.of(box, cellM)
        val singleCells = cellsOf(box, singleLattice.cellSizeDegLat, singleLattice.cellSizeDegLon)
        if (!withinBudget(singleCells)) {
            trace {
                "WALK refused cells=$singleCells ceiling=${AppConfig.routeWalkMaxCells} " +
                    "box=${boxText(box)} cell=${fmt(cellM)}m"
            }
            return null
        }
        val marginM = AppConfig.routeAvoidObstacleMarginM
        val zones = if (AppConfig.routeAvoidSpeedZoneEnabled) world.speedZonesIn(box) else emptyList()
        val zoneOutsideMarginM = AppConfig.routeAvoidSpeedZoneOutsideMarginM
        trace {
            "HARVEST edges=${edges.size} openCoast=${openCoast.size} band=${fmt(world.bandWidthM)}m " +
                "zones=[${zones.joinToString(", ") { "${it.name} ${fmt(it.speedLimitKn)}kn" }}] " +
                "cell=${fmt(cellM)}m margin=${fmt(marginM)}m outsideMargin=${fmt(zoneOutsideMarginM)}m"
        }
        // The grid takes the rings, the band and the **limits**: what a slow cell is, never what it costs
        // this time round. It is therefore **λ-free and built once per solve** — the price is the A*'s
        // own read, so a further pass costs one multiply per expansion and the band's price follows the
        // rung's own λ with every other slow source.
        val gridField = costField(world, cellM, pace, withZones = false, withBand = false, zones = emptyList(), lambda = 0.0)
        val priced = zones.map { z -> ZoneRing(z.outerRing, z.holes, z.speedLimitKn) }
        val bandSpec = bandLaw(world)
        val grid = rasterize(
            box, cellM, pace, marginM, edges, openCoast, capLatNorth, gridField, priced,
            zoneOutsideMarginM = zoneOutsideMarginM, band = bandSpec
        )
        val startCell = grid.cellOf(from.latitude, from.longitude)
        val aimCell = grid.cellOf(to.latitude, to.longitude)
        val startStateBefore = grid.cell(startCell.row, startCell.col).state
        val aimStateBefore = grid.cell(aimCell.row, aimCell.col).state
        grid.forceFree(from.latitude, from.longitude)
        grid.forceFree(to.latitude, to.longitude)
        val depthGateActive = AppConfig.routeAvoidDepthGateEnabled && world.depthReady
        val minDepthM = AppConfig.routeAvoidDepthGateMinM
        openEndDisc(grid, world, from.toLatLng(), marginM, depthGateActive, minDepthM)
        openEndDisc(grid, world, to.toLatLng(), marginM, depthGateActive, minDepthM)
        val carveReach = carveReachCells(marginM, cellM)
        val startCarve =
            carveEnd(world, grid, startCell, from.toLatLng(), marginM, carveReach, depthGateActive)
        val aimCarve = carveEnd(world, grid, aimCell, to.toLatLng(), marginM, carveReach, depthGateActive)
        val approaches = EndApproaches(startCarve.points, aimCarve.points)
        val refusals = PullRefusals()
        trace {
            "GRID ${inventory(grid)} " +
                "start=${cellRead(world, grid, startCell, from, startStateBefore)} " +
                "aim=${cellRead(world, grid, aimCell, to, aimStateBefore)}"
        }
        trace { carveLine("start", startCarve) }
        trace { carveLine("aim", aimCarve) }
        val start = from.toLatLng()
        val aim = to.toLatLng()
        val sets = ArrayList<CornerSet>(3)
        // A single grid answers one cell everywhere, so each radius is that cell's own — `avoid` is unmoved.
        sets.add(CornerSet(TangentCorners.corners(edges, openCoast, marginM), { _ -> cellM * 2.0 }))
        if (AppConfig.routeAvoidZone300Enabled && world.bandWidthM > 0.0) {
            val bandOffsetM = bandReachM(world.bandWidthM, AppConfig.routeAvoidZone300OutsideMarginM)
            sets.add(CornerSet(TangentCorners.corners(edges, openCoast, bandOffsetM), { _ -> bandOffsetM }))
        }
        if (zones.isNotEmpty()) {
            sets.add(
                CornerSet(
                    TangentCorners.ringCorners(zones, zoneOutsideMarginM),
                    { _ -> zoneOutsideMarginM + cellM }
                )
            )
        }
        val limitAt = limitAtFor(world)
        // Named construction, never a positional 27th: the `windows` seam defaults to `null` here, which is
        // exactly this single-grid walk — `avoid`'s answer, cell for cell.
        return GridContext(
            world = world, from = from, to = to, box = box, edges = edges, openCoast = openCoast,
            capLatNorth = capLatNorth, cellM = cellM, fineCellM = fineCellM, marginM = marginM,
            zoneOutsideMarginM = zoneOutsideMarginM, pace = pace, grid = grid,
            startCell = startCell, aimCell = aimCell, start = start, aim = aim, sets = sets,
            limitAt = limitAt, zones = zones, priced = priced, approaches = approaches,
            refusals = refusals, depthGateActive = depthGateActive, minDepthM = minDepthM,
            regionSaturated = regionSaturated
        )
    }

    /**
     * **The two-layer build** — the corridor rasterized as the interior at the coarse pair and the band at
     * the fine pair, both on **one lattice family**, carried as one walk of two windows.
     *
     * The **interior tile is the grid every single-grid read site still describes**: `GridContext.grid`,
     * its two end cells and its `cellM` are the interior's, so the engine's own readings and the forced
     * crossing probe keep their answers. The band is the second window, its membership the coast and the
     * depth gate dilated by one coarse cell — **the band's outer edge is the seam**.
     *
     * The ends' **discs** land in every window that holds the end, each at that window's **own** cell; the
     * berth **carve** runs on the interior, its reach read at the end's **local** cell, and the corner-set
     * radii are the **local** size each corner stands on (Phase 6) — so the band's 20 m reaches the drawn
     * points while a single grid's own `avoid` answers stay cell for cell.
     */
    private suspend fun buildLayeredGrid(
        world: MultipassWorld,
        from: RoutePoint,
        to: RoutePoint,
        box: BBox,
        regionSaturated: Boolean,
        edges: List<MultipassEdge>,
        openCoast: List<List<LatLng>>,
        capLatNorth: Double,
        tiles: List<GridTile>,
        pace: Double,
        trace: (() -> String) -> Unit
    ): GridContext? {
        val coarseTile = tiles.maxByOrNull { it.cellM }!!
        val fineTile = tiles.minByOrNull { it.cellM }!!
        val cellM = coarseTile.cellM
        val fineCellM = fineTile.cellM
        val family = LatticeFamily.of(box, cellM, fineCellM)
        val marginM = AppConfig.routeAvoidObstacleMarginM
        val zoneOutsideMarginM = AppConfig.routeAvoidSpeedZoneOutsideMarginM
        val zones = if (AppConfig.routeAvoidSpeedZoneEnabled) world.speedZonesIn(box) else emptyList()
        val gridField =
            costField(world, cellM, pace, withZones = false, withBand = false, zones = emptyList(), lambda = 0.0)
        val priced = zones.map { z -> ZoneRing(z.outerRing, z.holes, z.speedLimitKn) }
        val bandSpec = bandLaw(world)
        // The fine layer (layer 1): **the windows over the band's own water**, never the corridor's span.
        // The mask keeps only the coastal ribbon, so the ribbon's own tiles are what is rastered — the same
        // cells the walk could reach, at a fraction of the allocation. With no band there is no mask to bound
        // the layer and **no fine layer at all**: the walk is the coarse grid alone, which is `avoid`'s shape.
        val coarseBox = family.coarse.snapOutward(box)
        val fineReachM = fineWaterReachM(world, marginM, cellM)
        val fineBoxes =
            if (bandSpec == null) emptyList()
            else fineWindowBoxes(family.fine, box, edges, openCoast, fineReachM)
        // The walk's own ceiling, asked **before** anything is rastered: both layers' cells are pure
        // arithmetic off the boxes, and a walk over the ceiling is refused rather than attempted — the
        // grown retry then answers the line it already has instead of doubling into the heap.
        val coarseCells = cellsOf(coarseBox, family.coarse.cellSizeDegLat, family.coarse.cellSizeDegLon)
        val fineLayerCells =
            fineBoxes.sumOf { cellsOf(it, family.fine.cellSizeDegLat, family.fine.cellSizeDegLon) }
        if (!withinBudget(coarseCells + fineLayerCells)) {
            trace {
                "WALK refused cells=${coarseCells + fineLayerCells} coarse=$coarseCells " +
                    "fine=$fineLayerCells windows=${fineBoxes.size} " +
                    "ceiling=${AppConfig.routeWalkMaxCells} box=${boxText(box)}"
            }
            return null
        }
        // The interior window (layer 0): the whole corridor at the coarse pair.
        val interiorGrid = rasterizeWindow(
            coarseBox, family.coarse, pace, marginM, edges, openCoast, capLatNorth, gridField, priced,
            zoneOutsideMarginM = zoneOutsideMarginM, band = bandSpec
        )
        val fineStartNs = System.nanoTime()
        val windows = ArrayList<WalkWindow>(fineBoxes.size + 1)
        windows.add(
            WalkWindow(
                interiorGrid, family.coarse.rowOf(coarseBox.latSouth),
                family.coarse.colOf(coarseBox.lonWest), layer = 0
            )
        )
        var fineCells = 0
        for (fineBox in fineBoxes) {
            val bandGrid = rasterizeWindow(
                fineBox, family.fine, pace, marginM, edges, openCoast, capLatNorth, gridField, priced,
                zoneOutsideMarginM = zoneOutsideMarginM, band = bandSpec,
                bandMask = true, bandMaskWidthM = cellM
            )
            fineCells += bandGrid.rows * bandGrid.cols
            windows.add(
                WalkWindow(
                    bandGrid, family.fine.rowOf(fineBox.latSouth),
                    family.fine.colOf(fineBox.lonWest), layer = 1
                )
            )
            trace { "FINE window box=${boxText(fineBox)} cells=${bandGrid.rows}x${bandGrid.cols}" }
        }
        trace {
            "GRID layer=fine windows=${fineBoxes.size} cells=$fineCells reach=${fmt(fineReachM)}m " +
                "cell=${fmt(fineCellM)}m ms=${fmt(msSince(fineStartNs))}"
        }
        val walk = WalkWindows.onLattice(family.layers, windows)
        val startCell = interiorGrid.cellOf(from.latitude, from.longitude)
        val aimCell = interiorGrid.cellOf(to.latitude, to.longitude)
        val startStateBefore = interiorGrid.cell(startCell.row, startCell.col).state
        val aimStateBefore = interiorGrid.cell(aimCell.row, aimCell.col).state
        val depthGateActive = AppConfig.routeAvoidDepthGateEnabled && world.depthReady
        val minDepthM = AppConfig.routeAvoidDepthGateMinM
        for ((index, window) in windows.withIndex()) {
            val lattice = family.layers[window.layer]
            for (end in listOf(from.toLatLng(), to.toLatLng())) {
                if (!window.holds(lattice.rowOf(end.latitude), lattice.colOf(end.longitude))) continue
                window.grid.forceFree(end.latitude, end.longitude)
                openEndDisc(window.grid, world, end, marginM, depthGateActive, minDepthM, reachCellM = lattice.cellM)
            }
        }
        // Each end's carve reach follows the **local** cell it stands on, so a coastal berth is scanned at the
        // band's own step while an open-water one keeps the interior's (Phase 6).
        val startReach = carveReachCells(marginM, walk.cellSizeAt(from.toLatLng()))
        val aimReach = carveReachCells(marginM, walk.cellSizeAt(to.toLatLng()))
        val startCarve = carveEnd(world, interiorGrid, startCell, from.toLatLng(), marginM, startReach, depthGateActive)
        val aimCarve = carveEnd(world, interiorGrid, aimCell, to.toLatLng(), marginM, aimReach, depthGateActive)
        val approaches = EndApproaches(startCarve.points, aimCarve.points)
        val refusals = PullRefusals()
        // (e) Every count names its layer: a coarse cell is twenty-five fine ones, so the two inventories
        // are printed apart and never summed into one figure the engine cannot stand behind.
        trace {
            "GRID layer=coarse ${inventory(interiorGrid)} " +
                "start=${cellRead(world, interiorGrid, startCell, from, startStateBefore)} " +
                "aim=${cellRead(world, interiorGrid, aimCell, to, aimStateBefore)}"
        }
        trace { carveLine("start", startCarve) }
        trace { carveLine("aim", aimCarve) }
        // Phase 6: each corner's own reach is the **local** cell it stands on — fine where the water is the
        // band's, coarse in the open — so a bend near the coast moves at the 20 m contract's own scale.
        val sets = ArrayList<CornerSet>(3)
        sets.add(CornerSet(TangentCorners.corners(edges, openCoast, marginM), { p -> walk.cellSizeAt(p) * 2.0 }))
        if (AppConfig.routeAvoidZone300Enabled && world.bandWidthM > 0.0) {
            val bandOffsetM = bandReachM(world.bandWidthM, AppConfig.routeAvoidZone300OutsideMarginM)
            sets.add(CornerSet(TangentCorners.corners(edges, openCoast, bandOffsetM), { _ -> bandOffsetM }))
        }
        if (zones.isNotEmpty()) {
            sets.add(
                CornerSet(
                    TangentCorners.ringCorners(zones, zoneOutsideMarginM),
                    { p -> zoneOutsideMarginM + walk.cellSizeAt(p) }
                )
            )
        }
        val limitAt = limitAtFor(world)
        return GridContext(
            world = world, from = from, to = to, box = box, edges = edges, openCoast = openCoast,
            capLatNorth = capLatNorth, cellM = cellM, fineCellM = fineCellM, marginM = marginM,
            zoneOutsideMarginM = zoneOutsideMarginM, pace = pace, grid = interiorGrid,
            startCell = startCell, aimCell = aimCell, start = from.toLatLng(), aim = to.toLatLng(),
            sets = sets, limitAt = limitAt, zones = zones, priced = priced, approaches = approaches,
            refusals = refusals, depthGateActive = depthGateActive, minDepthM = minDepthM,
            regionSaturated = regionSaturated,
            windows = walk
        )
    }

    /** A corridor box and whether the region's own bounds cut it short — the instrument's own reading. */
    private data class Corridor(val box: BBox, val clampedByRegion: Boolean)

    /** The start-aim bounding box inflated by [reach], clamped to the region's bounds — a truncated box is accepted. */
    private fun corridorBox(from: RoutePoint, to: RoutePoint, bounds: BBox?, reach: Double): Corridor? {
        val midLat = (from.latitude + to.latitude) / 2.0
        val mPerDegLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
        val mPerDegLon = mPerDegLat * cos(Math.toRadians(midLat))
        val dLat = reach / mPerDegLat
        val dLon = reach / mPerDegLon
        val raw = BBox(
            min(from.latitude, to.latitude) - dLat,
            max(from.latitude, to.latitude) + dLat,
            min(from.longitude, to.longitude) - dLon,
            max(from.longitude, to.longitude) + dLon
        )
        val box = bounds?.let {
            BBox(
                max(raw.latSouth, it.latSouth),
                min(raw.latNorth, it.latNorth),
                max(raw.lonWest, it.lonWest),
                min(raw.lonEast, it.lonEast)
            )
        } ?: raw
        if (box.latSouth >= box.latNorth || box.lonWest >= box.lonEast) return null
        return Corridor(box, bounds != null && box != raw)
    }

    /**
     * The cells a box holds at one cell size — **the budget's own arithmetic**, and the number the
     * rasterizer will allocate: the frame's own ceiling, asked of a box before anything is built.
     */
    internal fun cellsOf(box: BBox, cellSizeDegLat: Double, cellSizeDegLon: Double): Int {
        if (cellSizeDegLat <= 0.0 || cellSizeDegLon <= 0.0) return Int.MAX_VALUE
        val rows = ceil((box.latNorth - box.latSouth) / cellSizeDegLat).toInt().coerceAtLeast(1)
        val cols = ceil((box.lonEast - box.lonWest) / cellSizeDegLon).toInt().coerceAtLeast(1)
        return rows.toLong().times(cols.toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    /**
     * Whether a walk holding [cells] stands inside the ceiling — the one comparison, so the guard is asked
     * in one place and a test can ask it with a ceiling of its own.
     */
    internal fun withinBudget(cells: Int, ceiling: Int = AppConfig.routeWalkMaxCells): Boolean =
        cells <= ceiling

    /**
     * The reach (m) the fine layer's own mask keeps water within — the band's own water plus the clearance's
     * dilation, so a window's cut can never fall short of the water the mask will keep. It is read by the cut
     * and by nothing else: one home for the ribbon's own width.
     */
    private fun fineWaterReachM(world: MultipassWorld, marginM: Double, cellM: Double): Double =
        bandReachM(world.bandWidthM, AppConfig.routeAvoidZone300OutsideMarginM) + marginM + cellM

    /**
     * **The fine layer's windows — the corridor's own coast, tiled, and nothing else.**
     *
     * The band's water is a ribbon within [reachM] of the coast, so the fine layer is rastered on the lattice
     * tiles that ribbon touches: the joined set holds every cell the mask will keep, and every cell a tile
     * gains outside the ribbon the mask paints land — **over-coverage costs memory and never an answer, while
     * a missed tile would move one**. Tiles rather than a chain per coast segment because the coast arrives as
     * thousands of short edges: a chain per edge would stack thousands of overlapping boxes over the same
     * water, where one tile grid holds each piece of coast once.
     *
     * The tiles stand on [lattice]'s own lines, so every window is a whole number of cells and two windows
     * that overlap share their cell centres exactly — the property the seam between them rests on.
     */
    internal fun fineWindowBoxes(
        lattice: WalkLattice,
        box: BBox,
        edges: List<MultipassEdge>,
        openCoast: List<List<LatLng>>,
        reachM: Double
    ): List<BBox> {
        if (reachM <= 0.0) return emptyList()
        val sideCells = ceil(reachM / lattice.cellM).toInt().coerceAtLeast(1)
        val sideLat = lattice.cellSizeDegLat * sideCells
        val sideLon = lattice.cellSizeDegLon * sideCells
        val origin = lattice.snapOutward(box)
        val tilesDown = ceil((box.latNorth - origin.latSouth) / sideLat).toInt().coerceAtLeast(1)
        val tilesAcross = ceil((box.lonEast - origin.lonWest) / sideLon).toInt().coerceAtLeast(1)
        val (mPerDegLat, mPerDegLon) = lattice.metresPerDegree()
        val marked = HashSet<Int>()
        // A segment's own box, grown by the reach, is what a tile must meet — a superset of the ribbon's
        // tiles, and a superset is the safe side of this cut.
        fun mark(a: LatLng, b: LatLng) {
            val south = minOf(a.latitude, b.latitude) - reachM / mPerDegLat
            val north = maxOf(a.latitude, b.latitude) + reachM / mPerDegLat
            val west = minOf(a.longitude, b.longitude) - reachM / mPerDegLon
            val east = maxOf(a.longitude, b.longitude) + reachM / mPerDegLon
            if (north < box.latSouth || south > box.latNorth) return
            if (east < box.lonWest || west > box.lonEast) return
            val firstRow = floor((south - origin.latSouth) / sideLat).toInt().coerceIn(0, tilesDown - 1)
            val lastRow = floor((north - origin.latSouth) / sideLat).toInt().coerceIn(0, tilesDown - 1)
            val firstCol = floor((west - origin.lonWest) / sideLon).toInt().coerceIn(0, tilesAcross - 1)
            val lastCol = floor((east - origin.lonWest) / sideLon).toInt().coerceIn(0, tilesAcross - 1)
            for (row in firstRow..lastRow) for (col in firstCol..lastCol) marked.add(row * tilesAcross + col)
        }
        for (edge in edges) mark(edge.a, edge.b)
        for (coast in openCoast) {
            for (i in 0 until coast.size - 1) mark(coast[i], coast[i + 1])
        }
        return marked.sorted().map { key ->
            val row = key / tilesAcross
            val col = key % tilesAcross
            val latSouth = origin.latSouth + row * sideLat
            val lonWest = origin.lonWest + col * sideLon
            BBox(
                latSouth, minOf(latSouth + sideLat, box.latNorth),
                lonWest, minOf(lonWest + sideLon, box.lonEast)
            )
        }
    }

    /** The grid's own inventory, counted once per solve for the instrument: the four tags' cell counts. */
    private fun inventory(grid: MultipassGrid): String {
        var land = 0
        var band = 0
        var zone = 0
        var free = 0
        for (row in 0 until grid.rows) {
            for (col in 0 until grid.cols) {
                when (grid.cell(row, col).state) {
                    MultipassCellState.LAND -> land++
                    MultipassCellState.BAND -> band++
                    MultipassCellState.ZONE -> zone++
                    MultipassCellState.FREE -> free++
                }
            }
        }
        return "cells=${grid.rows * grid.cols} land=$land band=$band zone=$zone free=$free"
    }

    /**
     * One end's own reads, cell by cell: the world's water/land answer, the depth it holds, the cell's
     * state and its zone limit — the four figures that separate a green ring from a barred cell.
     */
    private fun cellRead(
        world: MultipassWorld,
        grid: MultipassGrid,
        cell: CellIndex,
        at: RoutePoint,
        stateBefore: MultipassCellState
    ): String {
        val sample = world.depthAt(at.latitude, at.longitude)
        return "(r=${cell.row},c=${cell.col} isWater=${world.isWater(at.latitude, at.longitude)} " +
            "depth=${if (sample.hasData) "${fmt(sample.depthM.toDouble())}m" else "none"} " +
            "state=$stateBefore→${grid.cell(cell.row, cell.col).state} " +
            "limit=${fmt(grid.zoneLimitKn(cell.row, cell.col))}kn)"
    }

    /**
     * **The berth carve (F7), read off one end** — the scan, then the cells it opens, then the stretch
     * the pull will read.
     */
    private fun carveEnd(
        world: MultipassWorld,
        grid: MultipassGrid,
        cell: CellIndex,
        end: LatLng,
        marginM: Double,
        reachCells: Int,
        depthGateActive: Boolean
    ): BerthCarve {
        val found = carveBerth(
            world,
            grid.center(cell.row, cell.col),
            marginM,
            reachCells,
            depthGateActive,
            AppConfig.routeAvoidDepthGateMinM
        ) { steps, direction ->
            val row = cell.row + steps * direction.dRow
            val col = cell.col + steps * direction.dCol
            if (grid.inBounds(row, col)) grid.center(row, col) else null
        }
        val direction = found.direction ?: return found
        for (steps in 1 until found.stepsToWater) {
            grid.openCarve(
                cell.row + steps * direction.dRow,
                cell.col + steps * direction.dCol
            )
        }
        return found.copy(points = listOf(end, found.points.last()))
    }

    /** One end's carve, as the instrument reads it: the direction, the cells opened, the metres — or why not. */
    private fun carveLine(end: String, carve: BerthCarve): String {
        val direction = carve.direction
            ?: return "CARVE end=$end dir=none reason=${carve.reason} length=0.0m"
        val cells = (carve.stepsToWater - 1).coerceAtLeast(0)
        return "CARVE end=$end dir=${direction.label} cells=$cells length=${fmt(carve.lengthM)}m"
    }
}
