package ykws.android.maro.spatial.multipass

import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.data.regulation.SpeedZone
import ykws.android.maro.spatial.SpatialOperations
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * The halo's own margin, in cells, on top of the collar's reach — the conservative payment that makes a
 * tile's depth bound match the equivalent window's (see [RouteGridBuilder.buildFineTile]).
 *
 * It is derived one step above the depth scan's own bound margin ([DEPTH_WALL_SCAN_MARGIN_CELLS]): a
 * tile's halo must **dominate** that margin, or the tile's [DepthWallScan] would clear a cell the
 * equivalent window's would scan. Tying the two by arithmetic names the dominance rather than leaving it
 * to two independently chosen constants.
 */
private const val HALO_MARGIN_CELLS = DEPTH_WALL_SCAN_MARGIN_CELLS + 2

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
     * **The arm's tile map, owned for the builder's life.** The builder lives as long as the engine, so a
     * second arming on the same pair takes the tiles the first one already built: only the tiles a farther
     * route newly needs are rastered. It is keyed on everything a tile's contents move with
     * ([`TileKey`]), so a rebuilt asset or a moved collar is a different tile rather than a stale one.
     */
    private val fineTiles = FineTileMap()

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
        val startStateBefore = grid.state(startCell.row, startCell.col)
        val aimStateBefore = grid.state(aimCell.row, aimCell.col)
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
     * berth **carve** runs on the window the walk **reads the end on** — the fine collar's grid where a
     * passable fine cell supersedes the interior's coarse cell, the interior's own otherwise — its reach
     * read at the end's **local** cell, and the corner-set radii are the **local** size each corner stands
     * on (Phase 6) — so the band's 20 m reaches the drawn points while a single grid's own `avoid` answers
     * stay cell for cell.
     *
     * The family is drawn on the world's **fixed anchor** ([`MultipassWorld.latticeAnchor`]), never on the
     * corridor: both layers share that one origin, so the same water carries the same lattice indices on
     * every arm (P4.1). A world that names no anchor falls back to a whole-degree origin, still corridor-free.
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
        // The world's fixed anchor where it has one; otherwise the **whole-degree** fallback (D16), which
        // floors the corridor's south-west to the degree. It is corridor-**free in effect**: any two
        // corridors in the same degree share the origin, so a corridor without a live world lays the same
        // cells as its neighbours and cannot fragment the fine layer's future tile cache. No runtime guard
        // stands on that: `theWholeDegreeFallbackIsOneOriginPerDegree` in `LatticeFamilyTest` **is** the
        // guard, pinning the shared origin for two corridors in one degree and the other-degree case beside.
        val anchor = world.latticeAnchor ?: LatticeAnchor.wholeDegree(box)
        val family = LatticeFamily.of(anchor, cellM, fineCellM)
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
        // **The plan's own fine water** — the seam Phase 1 moved the cut onto. `evolutive` answers today's
        // coastal ribbon, byte for byte; `avoid` answers nothing and never takes this path; `selective`
        // answers the union of its four collars. The `bandSpec == null` short-circuit is gone: the walk's
        // fine layer is the plan's answer, not the band's.
        val depthGateActive = AppConfig.routeAvoidDepthGateEnabled && world.depthReady
        val minDepthM = AppConfig.routeAvoidDepthGateMinM
        val depthBlockedAt = if (depthGateActive) depthBlockedAtOf(world, minDepthM) else null
        val fineWater = plan.fineWater(
            FineWaterQuery(
                world = world, box = box, edges = edges, openCoast = openCoast,
                marginM = marginM, baseCellM = cellM
            )
        )
        val fineBoxes = if (fineWater.isEmpty) {
            emptyList()
        } else {
            fineWindowBoxes(family.fine, box, edges, openCoast, fineWater, depthBlockedAt, zones)
        }
        val fineMask = if (fineWater.isEmpty) null else fineWater.toMask(depthBlockedAt, DepthBandLaw.stepM(fineCellM))
        val depthBand = if (plan.pricesDepthBand) depthBandOf(world, minDepthM, fineCellM) else null
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
            // The tile is the same water; the window is assembled from the cached tiles that cover it, so
            // the marking is paid once per tile and a second arming pays only the tiles it newly needs.
            val bandGrid = fineWindowGrid(
                world, anchor, family.fine, fineBox, pace, marginM, zoneOutsideMarginM,
                gridField, priced, bandSpec, fineMask, depthBand, edges, openCoast,
                minDepthM, depthGateActive, fineWater, trace
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
            "GRID layer=fine windows=${fineBoxes.size} cells=$fineCells collars=${fineWater.coastReachesM.size} " +
                "zoneRim=${fmt(fineWater.zoneRimM)}m depthCollar=${fmt(fineWater.depthCollarM)}m " +
                "cell=${fmt(fineCellM)}m ms=${fmt(msSince(fineStartNs))}"
        }
        val walk = WalkWindows.onLattice(family.layers, windows)
        val startCell = interiorGrid.cellOf(from.latitude, from.longitude)
        val aimCell = interiorGrid.cellOf(to.latitude, to.longitude)
        val startStateBefore = interiorGrid.state(startCell.row, startCell.col)
        val aimStateBefore = interiorGrid.state(aimCell.row, aimCell.col)
        for ((index, window) in windows.withIndex()) {
            val lattice = family.layers[window.layer]
            for (end in listOf(from.toLatLng(), to.toLatLng())) {
                if (!window.holds(lattice.rowOf(end.latitude), lattice.colOf(end.longitude))) continue
                window.grid.forceFree(end.latitude, end.longitude)
                openEndDisc(window.grid, world, end, marginM, depthGateActive, minDepthM, reachCellM = lattice.cellM)
            }
        }
        // Each end's carve reach follows the **local** cell it stands on, so a coastal berth is scanned at the
        // band's own step while an open-water one keeps the interior's (Phase 6). Under the walk's own
        // priority an end a passable fine cell supersedes is a **fine** cell, so the carve runs on that
        // end's own window and cell rather than the interior's; an ordinary end keeps the interior.
        val startReach = carveReachCells(marginM, walk.cellSizeAt(from.toLatLng()))
        val aimReach = carveReachCells(marginM, walk.cellSizeAt(to.toLatLng()))
        val startCarve =
            carveEndOf(walk, world, from.toLatLng(), startCell, marginM, startReach, depthGateActive, interiorGrid)
        val aimCarve =
            carveEndOf(walk, world, to.toLatLng(), aimCell, marginM, aimReach, depthGateActive, interiorGrid)
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
            regionSaturated = regionSaturated, depthBandActive = depthBand != null,
            windows = walk
        )
    }

    /**
     * **One arm's fine window, assembled from the tile map.** The window's box is the plan's own merged
     * box, byte for byte as before — the tile geometry is only the cache's unit — so the walk's cells, its
     * offsets and every count stay unmoved. The dense grid opens entirely land and each covering tile
     * copies its members back: the coast sweep, the zone fill, the depth collar's ring scan and the band
     * write are the marking a hit skips, and the assembled grid is exactly the water a direct rasterise
     * would have produced.
     */
    private suspend fun fineWindowGrid(
        world: MultipassWorld,
        anchor: LatticeAnchor,
        fine: WalkLattice,
        fineBox: BBox,
        pace: Double,
        marginM: Double,
        zoneOutsideMarginM: Double,
        gridField: RouteCostField,
        priced: List<ZoneRing>,
        bandSpec: BandLaw?,
        fineMask: FineMask?,
        depthBand: DepthBand?,
        edges: List<MultipassEdge>,
        openCoast: List<List<LatLng>>,
        minDepthM: Double,
        depthGateActive: Boolean,
        fineWater: FineWater,
        trace: (() -> String) -> Unit
    ): MultipassGrid {
        val rows = ceil((fineBox.latNorth - fineBox.latSouth) / fine.cellSizeDegLat).toInt().coerceAtLeast(1)
        val cols = ceil((fineBox.lonEast - fineBox.lonWest) / fine.cellSizeDegLon).toInt().coerceAtLeast(1)
        val grid = MultipassGrid(
            fineBox.latSouth, fineBox.lonWest, fine.cellSizeDegLat, fine.cellSizeDegLon, rows, cols,
            fine.cellM, baseCostSec(fine.cellM, pace)
        )
        // The arm's own grid opens entirely land; every tile member restores the water the rasterizer wrote.
        for (row in 0 until rows) for (col in 0 until cols) grid.markLand(row, col)
        val windowRow = fine.rowOf(fineBox.latSouth)
        val windowCol = fine.colOf(fineBox.lonWest)
        val firstTileRow = Math.floorDiv(windowRow, FINE_TILE_CELLS)
        val lastTileRow = Math.floorDiv(windowRow + rows - 1, FINE_TILE_CELLS)
        val firstTileCol = Math.floorDiv(windowCol, FINE_TILE_CELLS)
        val lastTileCol = Math.floorDiv(windowCol + cols - 1, FINE_TILE_CELLS)
        val haloCells = fineTileHaloCells(fine, fineMask, depthBand)
        for (tileRow in firstTileRow..lastTileRow) {
            for (tileCol in firstTileCol..lastTileCol) {
                val key = fineTileKey(
                    world, anchor, fine, tileRow, tileCol, haloCells, pace, marginM, zoneOutsideMarginM,
                    bandSpec, fineMask, fineWater, priced, minDepthM, depthGateActive
                )
                val tile = fineTiles.get(key) {
                    // Only a **miss** reaches here, so the count of these lines is the arm's own tile
                    // builds — the signal that a second arming reuses tiles rather than re-marking (D54).
                    trace { "TILE built row=$tileRow col=$tileCol halo=$haloCells" }
                    buildFineTile(
                        world, fine, tileRow, tileCol, haloCells, pace, marginM, zoneOutsideMarginM,
                        gridField, priced, bandSpec, fineMask, depthBand, edges, openCoast
                    )
                }
                tile.writeInto(grid, windowRow, windowCol)
            }
        }
        return grid
    }

    /**
     * Rasterises one fixed tile block on the fine lattice — the marking the tile map caches.
     *
     * The block is rasterised inside a **[halo]** at least the depth collar's own reach wide, and only
     * the inner block is kept. The reason is [DepthWallScan]'s own bound: it reads the gate's blocked
     * cells **from the grid it is built over**, so a tile whose south edge cut the block off from the
     * blocked cells just outside it would under-count its own collar and drop a row the equivalent window
     * keeps. The halo puts those cells back, and because the window itself always carries the collar's
     * neighbourhood (its cut is grown by the reach), the two agree cell for cell.
     *
     * **The open coast is capped at the tile's own outer-box north (D52).** The window the tile replaces
     * closed its coast at the corridor's cap; here it closes at the tile's, and the two agree on the inner
     * block because the tile's cap is north of that block: the even-odd fill seals every landward cell up
     * to the cap, so the cap's exact latitude never reaches the block, and the land the window sealed stays
     * sealed. The equivalence rests on that — a cap south of the block would move it.
     */
    private fun buildFineTile(
        world: MultipassWorld,
        fine: WalkLattice,
        tileRow: Int,
        tileCol: Int,
        haloCells: Int,
        pace: Double,
        marginM: Double,
        zoneOutsideMarginM: Double,
        gridField: RouteCostField,
        priced: List<ZoneRing>,
        bandSpec: BandLaw?,
        fineMask: FineMask?,
        depthBand: DepthBand?,
        edges: List<MultipassEdge>,
        openCoast: List<List<LatLng>>
    ): FineTile {
        val box = fineTileOuterBox(fine, tileRow, tileCol, haloCells)
        val capLatNorth = world.regionBounds?.latNorth ?: box.latNorth
        val raster = rasterizeWindow(
            box, fine, pace, marginM, edges, openCoast, capLatNorth, gridField, priced,
            zoneOutsideMarginM = zoneOutsideMarginM, band = bandSpec, fineMask = fineMask, depthBand = depthBand
        )
        return FineTile.extract(raster, tileRow, tileCol, FINE_TILE_CELLS, haloCells)
    }

    /** The halo, in cells, a tile must rasterise around its block so its depth bound sees the collar's wall. */
    private fun fineTileHaloCells(fine: WalkLattice, fineMask: FineMask?, depthBand: DepthBand?): Int {
        val maxRadiusM = widestDepthRadiusM(fineMask?.depthCollarM ?: 0.0, depthBand?.bandM ?: 0.0)
        if (maxRadiusM <= 0.0 || fine.cellM <= 0.0) return 0
        return ceil(maxRadiusM / fine.cellM).toInt() + HALO_MARGIN_CELLS
    }

    /** One tile's **inner** box on the anchored fine lattice — pure arithmetic on the anchor, never a corridor. */
    private fun fineTileBox(fine: WalkLattice, tileRow: Int, tileCol: Int): BBox {
        val sideLat = FINE_TILE_CELLS * fine.cellSizeDegLat
        val sideLon = FINE_TILE_CELLS * fine.cellSizeDegLon
        val latSouth = fine.latSouth + tileRow.toDouble() * sideLat
        val lonWest = fine.lonWest + tileCol.toDouble() * sideLon
        return BBox(latSouth, latSouth + sideLat, lonWest, lonWest + sideLon)
    }

    /** That box grown by [haloCells] on every side — the box the tile is actually rasterised over. */
    private fun fineTileOuterBox(fine: WalkLattice, tileRow: Int, tileCol: Int, haloCells: Int): BBox {
        val inner = fineTileBox(fine, tileRow, tileCol)
        if (haloCells <= 0) return inner
        val haloLat = haloCells * fine.cellSizeDegLat
        val haloLon = haloCells * fine.cellSizeDegLon
        return BBox(
            inner.latSouth - haloLat, inner.latNorth + haloLat,
            inner.lonWest - haloLon, inner.lonEast + haloLon
        )
    }

    /** The key one tile is cached under — every value its rasterisation reads, per the plan's own list. */
    private fun fineTileKey(
        world: MultipassWorld,
        anchor: LatticeAnchor,
        fine: WalkLattice,
        tileRow: Int,
        tileCol: Int,
        haloCells: Int,
        pace: Double,
        marginM: Double,
        zoneOutsideMarginM: Double,
        bandSpec: BandLaw?,
        fineMask: FineMask?,
        fineWater: FineWater,
        priced: List<ZoneRing>,
        minDepthM: Double,
        depthGateActive: Boolean
    ): TileKey = TileKey(
        anchorLatSouth = anchor.latSouth,
        anchorLonWest = anchor.lonWest,
        referenceLat = anchor.referenceLat,
        fineCellM = fine.cellM,
        tileRow = tileRow,
        tileCol = tileCol,
        tileCells = FINE_TILE_CELLS,
        capLatNorth = world.regionBounds?.latNorth
            ?: fineTileOuterBox(fine, tileRow, tileCol, haloCells).latNorth,
        depthGenerationStamp = world.depthGenerationStamp,
        coastlineGenerationStamp = world.coastlineGenerationStamp,
        emodnetCutoffM = world.emodnetCutoffM,
        obstacleMarginM = marginM,
        gateMinDepthM = minDepthM,
        gateMarginM = AppConfig.routeAvoidDepthGateMarginM,
        depthGateEnabled = depthGateActive,
        bandWidthM = world.bandWidthM,
        bandLimitKn = bandSpec?.limitKn ?: 0.0,
        bandOutsideMarginM = bandSpec?.outsideMarginM ?: 0.0,
        bandExtraM = AppConfig.routeSelectiveDepthBandExtraM,
        bandEnabled = AppConfig.routeAvoidZone300Enabled,
        pricesDepthBand = plan.pricesDepthBand,
        zoneOutsideMarginM = zoneOutsideMarginM,
        zonesEnabled = AppConfig.routeAvoidSpeedZoneEnabled,
        coastBandsM = fineWater.coastBandsM,
        zoneRimM = fineWater.zoneRimM,
        depthCollarM = fineWater.depthCollarM,
        depthStepM = fineMask?.depthStepM ?: 0.0,
        paceKn = pace,
        excludedZoneIdSet = world.excludedZoneIdSet,
        zoneGenerationStamp = world.zoneGenerationStamp,
        zoneRings = priced
    )

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
     * **The fine layer's windows — the plan's own water, tiled and merged.** The plan answers the cut
     * reaches and the collar widths; this seat grows the coast's harvested segments by each reach, marks
     * the zone-rim and depth-dilation collars on the one tile grid, then merges the marked tiles into
     * maximal rectangles. Every collar's cut is a safe superset of its membership: over-coverage costs
     * memory and never an answer, while a missed tile would move one.
     */
    internal fun fineWindowBoxes(
        lattice: WalkLattice,
        box: BBox,
        edges: List<MultipassEdge>,
        openCoast: List<List<LatLng>>,
        reachM: Double
    ): List<BBox> = fineWindowBoxes(
        lattice, box, edges, openCoast,
        FineWater(coastReachesM = listOf(reachM), coastBandsM = listOf(0.0..reachM))
    )

    /** The plan's own fine water, tiled and merged — the cut over the collars, then the merge. */
    internal fun fineWindowBoxes(
        lattice: WalkLattice,
        box: BBox,
        edges: List<MultipassEdge>,
        openCoast: List<List<LatLng>>,
        fineWater: FineWater,
        depthBlockedAt: ((LatLng) -> Boolean)? = null,
        zones: List<SpeedZone> = emptyList()
    ): List<BBox> {
        val tiles = fineTileGrid(lattice, box, edges, openCoast, fineWater, depthBlockedAt, zones)
            ?: return emptyList()
        return mergedRuns(tiles)
    }

    /**
     * The same marked tiles **one box each**, unmerged — the shape the windows took before the merge, and the
     * benchmark the merge is measured against. The test asks it for the tile count and the cells the tiles
     * hold, which the merged windows must reproduce exactly.
     */
    internal fun fineTileBoxes(
        lattice: WalkLattice,
        box: BBox,
        edges: List<MultipassEdge>,
        openCoast: List<List<LatLng>>,
        reachM: Double
    ): List<BBox> {
        val tiles = fineTileGrid(
            lattice, box, edges, openCoast,
            FineWater(coastReachesM = listOf(reachM), coastBandsM = listOf(0.0..reachM)), null, emptyList()
        ) ?: return emptyList()
        val out = ArrayList<BBox>(tiles.marked.size)
        for (key in tiles.marked.indices) {
            if (!tiles.marked[key]) continue
            out.add(tiles.tileBox(tiles.rowOf(key), tiles.colOf(key)))
        }
        return out
    }

    /**
     * **The fine lattice's marked tiles** — the tile grid every collar's grown geometry touches, as an
     * integer grid on [lattice]'s own lines. One home for the cut, so the merged windows and the unmerged
     * tiles they are measured against come from the same marks. The three marking passes share it: the
     * coast collars reuse the segment marking, the zone rim grows each ring edge, and the depth dilation
     * samples the gate's wall on the corridor's own coarse lattice.
     */
    private fun fineTileGrid(
        lattice: WalkLattice,
        box: BBox,
        edges: List<MultipassEdge>,
        openCoast: List<List<LatLng>>,
        fineWater: FineWater,
        depthBlockedAt: ((LatLng) -> Boolean)?,
        zones: List<SpeedZone>
    ): FineTileGrid? {
        val reaches = fineWater.coastReachesM.filter { it > 0.0 }
        val zoneRimM = fineWater.zoneRimM
        val depthProbe = depthBlockedAt
        val depthActive = fineWater.depthCollarM > 0.0 && depthProbe != null
        if (reaches.isEmpty() && zoneRimM <= 0.0 && !depthActive) return null
        val cellM = lattice.cellM
        val depthReachM = if (depthActive) fineWater.depthCollarM + cellM else 0.0
        val maxReachM = maxOf(
            reaches.maxOrNull() ?: 0.0,
            if (zoneRimM > 0.0) zoneRimM else 0.0,
            depthReachM
        )
        val sideCells = ceil(maxReachM / cellM).toInt().coerceAtLeast(1)
        val sideLat = lattice.cellSizeDegLat * sideCells
        val sideLon = lattice.cellSizeDegLon * sideCells
        val origin = lattice.snapOutward(box)
        val tilesDown = ceil((box.latNorth - origin.latSouth) / sideLat).toInt().coerceAtLeast(1)
        val tilesAcross = ceil((box.lonEast - origin.lonWest) / sideLon).toInt().coerceAtLeast(1)
        val (mPerDegLat, mPerDegLon) = lattice.metresPerDegree()
        val marked = BooleanArray(tilesDown * tilesAcross)
        // A segment's own box, grown by the reach, is what a tile must meet — a superset of the collar's
        // tiles, and a superset is the safe side of this cut.
        fun mark(a: LatLng, b: LatLng, reachM: Double) {
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
            for (row in firstRow..lastRow) for (col in firstCol..lastCol) marked[row * tilesAcross + col] = true
        }
        // Pass 1 — the coast collars, on the harvested segments, one pass per reach.
        for (reachM in reaches) {
            for (edge in edges) mark(edge.a, edge.b, reachM)
            for (coast in openCoast) {
                for (i in 0 until coast.size - 1) mark(coast[i], coast[i + 1], reachM)
            }
        }
        // Pass 2 — the zone-rim collar, on every ring edge of every priced zone.
        if (zoneRimM > 0.0) {
            for (zone in zones) {
                for (ring in buildList { add(zone.outerRing); addAll(zone.holes) }) {
                    for (i in 0 until ring.size - 1) mark(ring[i], ring[i + 1], zoneRimM)
                }
            }
        }
        // Pass 3 — the depth dilation: the gate's wall sampled on the corridor's own coarse lattice, each
        // blocked sample carried into the water within the collar's own width.
        if (depthActive && depthProbe != null) {
            val stepLat = cellM / mPerDegLat
            val stepLon = cellM / mPerDegLon
            var lat = box.latSouth + stepLat / 2.0
            while (lat <= box.latNorth) {
                var lon = box.lonWest + stepLon / 2.0
                while (lon <= box.lonEast) {
                    val p = LatLng(lat, lon)
                    if (depthProbe(p)) mark(p, p, depthReachM)
                    lon += stepLon
                }
                lat += stepLat
            }
        }
        return FineTileGrid(
            origin.latSouth, origin.lonWest, sideLat, sideLon, tilesDown, tilesAcross,
            box.latNorth, box.lonEast, marked
        )
    }

    /**
     * The marked tiles merged into maximal rectangles — each tile row's runs of marked columns, then the runs
     * of consecutive rows whose span is identical. Every rectangle is the **exact union** of the tiles it
     * spans: the runs partition each row and a rectangle is extended only over a matching span, so no cell is
     * added and none is dropped.
     */
    private fun mergedRuns(tiles: FineTileGrid): List<BBox> {
        val rectangles = ArrayList<Rect>()
        var open = HashMap<Int, Rect>()
        for (row in 0 until tiles.tilesDown) {
            val current = HashMap<Int, Rect>()
            var col = 0
            while (col < tiles.tilesAcross) {
                if (!tiles.marked[row * tiles.tilesAcross + col]) {
                    col++
                    continue
                }
                val first = col
                while (col + 1 < tiles.tilesAcross && tiles.marked[row * tiles.tilesAcross + col + 1]) col++
                val key = first * tiles.tilesAcross + col
                val extending = open[key]
                if (extending != null) {
                    extending.rowEnd = row
                    current[key] = extending
                } else {
                    val rect = Rect(row, row, first, col)
                    rectangles.add(rect)
                    current[key] = rect
                }
                col++
            }
            open = current
        }
        return rectangles.sortedWith(compareBy({ it.rowStart }, { it.colStart }))
            .map { tiles.mergedBox(it.rowStart, it.rowEnd, it.colStart, it.colEnd) }
    }

    /** One merged window in **tile** coordinates, inclusive — a run's own span, grown down the rows. */
    private class Rect(val rowStart: Int, var rowEnd: Int, val colStart: Int, val colEnd: Int)

    /** The marked-tile lattice: the tile sizes, the grid shape, and which tiles the ribbon marks. */
    private class FineTileGrid(
        private val originLatSouth: Double,
        private val originLonWest: Double,
        val sideLat: Double,
        val sideLon: Double,
        val tilesDown: Int,
        val tilesAcross: Int,
        private val capLatNorth: Double,
        private val capLonEast: Double,
        val marked: BooleanArray
    ) {
        /** The tile row a packed key carries. */
        fun rowOf(key: Int): Int = key / tilesAcross

        /** The tile column a packed key carries. */
        fun colOf(key: Int): Int = key % tilesAcross

        private fun tileLatSouth(row: Int): Double = originLatSouth + row * sideLat

        private fun tileLonWest(col: Int): Double = originLonWest + col * sideLon

        /** One tile's box, its far edges clamped to the corridor exactly as the windows have always been. */
        fun tileBox(row: Int, col: Int): BBox {
            val latSouth = tileLatSouth(row)
            val lonWest = tileLonWest(col)
            return BBox(
                latSouth, minOf(latSouth + sideLat, capLatNorth),
                lonWest, minOf(lonWest + sideLon, capLonEast)
            )
        }

        /** One rectangle's box — the tiles `[rowStart..rowEnd] × [colStart..colEnd]` bound as their own union. */
        fun mergedBox(rowStart: Int, rowEnd: Int, colStart: Int, colEnd: Int): BBox {
            val latSouth = tileLatSouth(rowStart)
            val lonWest = tileLonWest(colStart)
            return BBox(
                latSouth, minOf(tileLatSouth(rowEnd) + sideLat, capLatNorth),
                lonWest, minOf(tileLonWest(colEnd) + sideLon, capLonEast)
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
                when (grid.state(row, col)) {
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
            "state=$stateBefore→${grid.state(cell.row, cell.col)} " +
            "limit=${fmt(grid.zoneLimitKn(cell.row, cell.col))}kn)"
    }

    /**
     * **One end's berth carve, on the window the walk reads it on.** An end a passable fine cell
     * supersedes resolves to a fine cell, so the carve follows that end's own window and local cell;
     * an ordinary end — or one whose fine copy the walk does not hold — keeps the interior grid and the
     * interior's cell, cell for cell as before.
     */
    private fun carveEndOf(
        walk: WalkWindows,
        world: MultipassWorld,
        end: LatLng,
        coarseCell: CellIndex,
        marginM: Double,
        reachCells: Int,
        depthGateActive: Boolean,
        interior: MultipassGrid
    ): BerthCarve {
        // [coarseCell] is a cell of the interior grid's own local space; the walk reads lattice coordinates,
        // so translate it first — a no-op at the single grid's zero offset, the P4.1 case otherwise.
        val lattice = walk.latticeCell(interior, coarseCell.row, coarseCell.col)
        val slot = walk.slotOf(lattice.row, lattice.col)
        if (slot < 0) return carveEnd(world, interior, coarseCell, end, marginM, reachCells, depthGateActive)
        val window = walk.windowOf(slot)
        return carveEnd(world, window.grid, walk.localCell(slot), end, marginM, reachCells, depthGateActive)
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
