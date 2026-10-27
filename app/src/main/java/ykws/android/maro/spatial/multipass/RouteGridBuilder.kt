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

/** One fine tile's own side in degrees, south–north and west–east — the step the warm area grows by. */
internal data class FineTileSide(val latDeg: Double, val lonDeg: Double)

/**
 * **An end of the pair stands outside the region the world covers** — thrown by [RouteGridBuilder.buildGrid]
 * instead of clamping the end onto the grid's edge, so the engine answers the pair by name
 * ([ykws.android.maro.spatial.RouteReason.OFF_WATER]) rather than searching to the wrong point.
 */
internal class RouteOffWaterException : RuntimeException("an end of the pair stands outside the covered region")

/**
 * **One grid build** — the corridor → the harvest → the grid, **once** → the ends' discs → the
 * corner sets, lifted out of the pass pipeline so the context build and its instrument readers are a
 * named seat of their own.
 *
 * Everything it answers is **λ-free**: the grid stores limits, never prices, so the three rungs share
 * it and each rung prices its own pass. The [plan] is the algorithm's own injection point — the
 * rectangles the first walk uses — and it is the seat's one constructor argument.
 */
internal class RouteGridBuilder(private val plan: RouteGridPlan = UniformGridPlan) {

    /**
     * **The builder's own tile map, used when no process-level store is installed.** The builder lives as
     * long as the engine, so a second arming on the same pair takes the tiles the first one already built:
     * only the tiles a farther route newly needs are rastered. It is keyed on everything a tile's contents
     * move with ([`TileKey`]), so a rebuilt asset or a moved band is a different tile rather than a
     * stale one. Once the app edge installs a store ([`FineTileCache.install`]) every builder prefers that
     * one shared map instead, so an engine switch keeps the cache and the store has one reader and writer;
     * absent an install — a JVM test — each builder keeps this one, and no two fixtures share a tile.
     */
    private val ownTiles = FineTileMap()

    /**
     * **One grid build** — the corridor → the harvest → the grid, **once** → the ends' discs →
     * the corner sets. Everything λ-free: the grid stores limits, never prices, so the three rungs
     * share it and each rung prices its own pass. `null` where the corridor is empty.
     *
     * The pace is the engine's own and is asked at its call, like its instrument: [trace] is handed in
     * rather than owned here, so the seat never owns the emission sequence — and so is [checkCancelled],
     * the calling job's own cancel read, handed to the raster's plain loops.
     *
     * **An end outside the region is refused, never clamped.** The corridor is cut to the region's bounds,
     * and the end cells used to be clamped onto the grid's edge with it, so a pair with an end off the baked
     * water was searched to the wrong point in silence. It now throws [RouteOffWaterException], which the
     * engine answers as [ykws.android.maro.spatial.RouteReason.OFF_WATER].
     */
    internal suspend fun buildGrid(
        world: MultipassWorld,
        from: RoutePoint,
        to: RoutePoint,
        reach: Double,
        pace: Double,
        markerStrength: Double,
        trace: (() -> String) -> Unit = {},
        checkCancelled: () -> Unit = {}
    ): GridContext? {
        val region = world.regionBounds
        if (region != null && (!region.holds(from) || !region.holds(to))) {
            trace { "CORRIDOR reach=${fmt(reach)}m box=n/a offWater=true region=${boxText(region)}" }
            throw RouteOffWaterException()
        }
        val corridor = corridorBox(from, to, region, reach)
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
                world, from, to, box, regionSaturated, edges, openCoast, capLatNorth, tiles, pace,
                markerStrength, trace, checkCancelled
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
                "zones=[${zones.joinToString(", ") { "${it.name} ${fmt(it.routeLimitKn)}kn" }}] " +
                "cell=${fmt(cellM)}m margin=${fmt(marginM)}m outsideMargin=${fmt(zoneOutsideMarginM)}m"
        }
        // The grid takes the rings, the band and the **limits**: what a slow cell is, never what it costs
        // this time round. It is therefore **λ-free and built once per solve** — the price is the A*'s
        // own read, so a further pass costs one multiply per expansion and the band's price follows the
        // rung's own λ with every other slow source.
        val gridField = costField(
            world, cellM, pace, withZones = false, withBand = false, zones = emptyList(), lambda = 0.0,
            markerStrength = markerStrength
        )
        val priced = zones.map { z -> ZoneRing(z.outerRing, z.holes, z.routeLimitKn) }
        val bandSpec = bandLaw(world)
        val depthGateActive = AppConfig.routeAvoidDepthGateEnabled && world.depthReady
        val minDepthM = AppConfig.routeAvoidDepthGateMinM
        val gate = gateRuleOf(world, plan, depthGateActive, minDepthM)
        val grid = rasterize(
            box, cellM, pace, marginM, edges, openCoast, capLatNorth, gridField, priced,
            zoneOutsideMarginM = zoneOutsideMarginM, band = bandSpec,
            gate = gate, checkCancelled = checkCancelled
        )
        val startCell = grid.cellOf(from.latitude, from.longitude)
        val aimCell = grid.cellOf(to.latitude, to.longitude)
        val startStateBefore = grid.state(startCell.row, startCell.col)
        val aimStateBefore = grid.state(aimCell.row, aimCell.col)
        grid.forceFree(from.latitude, from.longitude)
        grid.forceFree(to.latitude, to.longitude)
        measureEndCell(grid, startCell, gate)
        measureEndCell(grid, aimCell, gate)
        openEndDisc(grid, world, from.toLatLng(), marginM, depthGateActive, minDepthM, gate = gate)
        openEndDisc(grid, world, to.toLatLng(), marginM, depthGateActive, minDepthM, gate = gate)
        trace {
            "GRID ${inventory(grid)} " +
                "start=${cellRead(world, grid, startCell, from, startStateBefore)} " +
                "aim=${cellRead(world, grid, aimCell, to, aimStateBefore)}"
        }
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
        bodyCornerSet(world, box, marginM, cellM) { _ -> cellM }?.let { sets.add(it) }
        // The clock's limit read over the corridor's own zones — the world's whole region would answer the
        // same for every point inside the box, at the cost of walking every ring of it per sample. They are
        // the world's zones in the box, **whatever the price switch says**: a switch prices water, it never
        // suspends a limit, so the clock's set is read off the world and never off the grid's gated `zones`.
        val limitAt = limitAtFor(world, ZoneSet(world.speedZonesIn(box)))
        // Named construction, never a positional 27th: the `windows` seam defaults to `null` here, which is
        // exactly this single-grid walk — `avoid`'s answer, cell for cell.
        return GridContext(
            world = world, from = from, to = to, box = box, edges = edges, openCoast = openCoast,
            capLatNorth = capLatNorth, cellM = cellM, fineCellM = fineCellM, marginM = marginM,
            zoneOutsideMarginM = zoneOutsideMarginM, pace = pace, markerStrength = markerStrength, grid = grid,
            startCell = startCell, aimCell = aimCell, start = start, aim = aim, sets = sets,
            limitAt = limitAt, zones = zones, priced = priced,
            depthGateActive = depthGateActive, minDepthM = minDepthM,
            regionSaturated = regionSaturated, plan = plan, gate = gate
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
     * The ends' **discs** land in every window that holds the end, each at that window's **own** cell, and
     * the corner-set radii are the **local** size each corner stands
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
        markerStrength: Double,
        trace: (() -> String) -> Unit,
        checkCancelled: () -> Unit = {}
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
            costField(
                world, cellM, pace, withZones = false, withBand = false, zones = emptyList(), lambda = 0.0,
                markerStrength = markerStrength
            )
        val priced = zones.map { z -> ZoneRing(z.outerRing, z.holes, z.routeLimitKn) }
        val bandSpec = bandLaw(world)
        // The fine layer (layer 1): **the windows over the band's own water**, never the corridor's span.
        // The mask keeps only the coastal ribbon, so the ribbon's own tiles are what is rastered — the same
        // cells the walk could reach, at a fraction of the allocation. With no band there is no mask to bound
        // the layer and **no fine layer at all**: the walk is the coarse grid alone, which is `avoid`'s shape.
        val coarseBox = family.coarse.snapOutward(box)
        // **The plan's own fine water** — the seam Phase 1 moved the cut onto. `evolutive` answers today's
        // coastal ribbon, byte for byte; `avoid` answers nothing and never takes this path. The
        // `bandSpec == null` short-circuit is gone: the walk's fine layer is the plan's answer, not the
        // band's.
        val depthGateActive = AppConfig.routeAvoidDepthGateEnabled && world.depthReady
        val minDepthM = AppConfig.routeAvoidDepthGateMinM
        val gate = gateRuleOf(world, plan, depthGateActive, minDepthM)
        val fineWater = plan.fineWater(
            FineWaterQuery(
                world = world, box = box, edges = edges, openCoast = openCoast,
                marginM = marginM, baseCellM = cellM
            )
        )
        val fineBoxes = if (fineWater.isEmpty) {
            emptyList()
        } else {
            fineWindowBoxes(world, family.fine, box, edges, openCoast, fineWater)
        }
        val fineMask = if (fineWater.isEmpty) null else fineWater.toMask()
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
            zoneOutsideMarginM = zoneOutsideMarginM, band = bandSpec,
            gate = gate, collarReachM = fineWater.depthCollarM, checkCancelled = checkCancelled
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
                priced, bandSpec, fineMask, edges, openCoast,
                minDepthM, depthGateActive, gate, fineWater.depthCollarM,
                fineWater, markerStrength, trace
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
                // **The fine layer is the coastal ribbon only.** A LAND cell there means "no fine water",
                // never the shore margin `forceFree` waives, so an end standing beyond the ribbon is left
                // land and `endCellAt` anchors it on the coarse cell instead of a manufactured one-cell
                // fine island. Layer 0 keeps its unconditional free — its LAND is the margin.
                if (window.layer != 0) {
                    val cell = window.grid.cellOf(end.latitude, end.longitude)
                    if (!window.grid.passable(cell.row, cell.col)) continue
                }
                window.grid.forceFree(end.latitude, end.longitude)
                measureEndCell(window.grid, window.grid.cellOf(end.latitude, end.longitude), gate)
                openEndDisc(
                    window.grid, world, end, marginM, depthGateActive, minDepthM, reachCellM = lattice.cellM,
                    gate = gate
                )
            }
        }
        // (e) Every count names its layer: a coarse cell is twenty-five fine ones, so the two inventories
        // are printed apart and never summed into one figure the engine cannot stand behind.
        trace {
            "GRID layer=coarse ${inventory(interiorGrid)} " +
                "start=${cellRead(world, interiorGrid, startCell, from, startStateBefore)} " +
                "aim=${cellRead(world, interiorGrid, aimCell, to, aimStateBefore)}"
        }
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
        bodyCornerSet(world, box, marginM, cellM) { p -> walk.cellSizeAt(p) }?.let { sets.add(it) }
        val limitAt = limitAtFor(world, ZoneSet(world.speedZonesIn(box)))
        return GridContext(
            world = world, from = from, to = to, box = box, edges = edges, openCoast = openCoast,
            capLatNorth = capLatNorth, cellM = cellM, fineCellM = fineCellM, marginM = marginM,
            zoneOutsideMarginM = zoneOutsideMarginM, pace = pace, markerStrength = markerStrength, grid = interiorGrid,
            startCell = startCell, aimCell = aimCell, start = from.toLatLng(), aim = to.toLatLng(),
            sets = sets, limitAt = limitAt, zones = zones, priced = priced,
            depthGateActive = depthGateActive, minDepthM = minDepthM,
            regionSaturated = regionSaturated, plan = plan,
            windows = walk, gate = gate
        )
    }

    /**
     * **One arm's fine window, assembled from the tile map.** The window's box is the plan's own merged
     * box, byte for byte as before — the tile geometry is only the cache's unit — so the walk's cells, its
     * offsets and every count stay unmoved. The dense grid opens entirely land and each covering tile
     * copies its members back: the coast sweep, the zone fill and the band write are the marking a hit
     * skips, and the assembled grid is exactly the water a direct rasterise would have produced.
     */
    private suspend fun fineWindowGrid(
        world: MultipassWorld,
        anchor: LatticeAnchor,
        fine: WalkLattice,
        fineBox: BBox,
        pace: Double,
        marginM: Double,
        zoneOutsideMarginM: Double,
        priced: List<ZoneRing>,
        bandSpec: BandLaw?,
        fineMask: FineMask?,
        edges: List<MultipassEdge>,
        openCoast: List<List<LatLng>>,
        minDepthM: Double,
        depthGateActive: Boolean,
        gate: GateRule?,
        collarReachM: Double,
        fineWater: FineWater,
        markerStrength: Double,
        trace: (() -> String) -> Unit
    ): MultipassGrid {
        val rows = ceil((fineBox.latNorth - fineBox.latSouth) / fine.cellSizeDegLat).toInt().coerceAtLeast(1)
        val cols = ceil((fineBox.lonEast - fineBox.lonWest) / fine.cellSizeDegLon).toInt().coerceAtLeast(1)
        val grid = MultipassGrid(
            fineBox.latSouth, fineBox.lonWest, fine.cellSizeDegLat, fine.cellSizeDegLon, rows, cols,
            fine.cellM, baseCostSec(fine.cellM, pace)
        )
        // The arm's own grid opens entirely land; every tile member restores the water the rasterizer wrote.
        grid.markAllLand()
        val windowRow = fine.rowOf(fineBox.latSouth)
        val windowCol = fine.colOf(fineBox.lonWest)
        val firstTileRow = Math.floorDiv(windowRow, FINE_TILE_CELLS)
        val lastTileRow = Math.floorDiv(windowRow + rows - 1, FINE_TILE_CELLS)
        val firstTileCol = Math.floorDiv(windowCol, FINE_TILE_CELLS)
        val lastTileCol = Math.floorDiv(windowCol + cols - 1, FINE_TILE_CELLS)
        // The process-level map once the app edge installed a store, else this builder's own — so an
        // engine switch keeps the cache while a storeless JVM test stays isolated (see `ownTiles`).
        val fineTiles = FineTileCache.shared ?: ownTiles
        for (tileRow in firstTileRow..lastTileRow) {
            for (tileCol in firstTileCol..lastTileCol) {
                val key = fineTileKey(
                    world, anchor, fine, tileRow, tileCol, marginM, zoneOutsideMarginM,
                    bandSpec, fineMask, fineWater, priced, minDepthM, depthGateActive, collarReachM, markerStrength
                )
                val tile = fineTiles.get(key) {
                    // Only a **miss** reaches here, so the count of these lines is the arm's own tile
                    // builds — the signal that a second arming reuses tiles rather than re-marking (D54).
                    trace { "TILE built row=$tileRow col=$tileCol" }
                    buildFineTile(
                        world, fine, tileRow, tileCol, marginM, zoneOutsideMarginM,
                        priced, bandSpec, fineMask, edges, openCoast, gate, collarReachM,
                        markerStrength
                    )
                }
                tile.writeInto(grid, windowRow, windowCol)
            }
        }
        return grid
    }

    /**
     * **The fine tiles the plan's own water covers over [box], each with the closure that builds it** — the
     * pre-warm's whole input, derived through the builder's own key ([fineTileKey]) and its own build
     * ([buildFineTile]) so a tile made from one of these requests is the very tile an arm over this water
     * asks for. It is the fine slice of [buildLayeredGrid]: the plan's two cells, the world's fixed anchor,
     * the priced zones in [box], the band law, the depth gate and the plan's own fine water — never an
     * invented context, because the key carries all of it and a second derivation would key a tile no arm
     * could ever reach.
     *
     * A tile's own [FineTileRequest.center] lets the caller order them own-ship outward. A single-grid plan
     * (`avoid`) has no fine layer and answers an empty list; so does a box whose plan keeps no fine water.
     */
    internal fun fineTilesOver(
        world: MultipassWorld,
        box: BBox,
        markerStrength: Double
    ): List<FineTileRequest> {
        val lattice = fineLatticeOf(world, box) ?: return emptyList()
        val fine = lattice.fine
        val anchor = lattice.anchor
        val cellM = lattice.coarseCellM
        val fineCellM = lattice.fineCellM
        val edges = world.segmentsIn(box)
        val openCoast = world.openCoastIn(box)
        val marginM = AppConfig.routeAvoidObstacleMarginM
        val zoneOutsideMarginM = AppConfig.routeAvoidSpeedZoneOutsideMarginM
        val zones = if (AppConfig.routeAvoidSpeedZoneEnabled) world.speedZonesIn(box) else emptyList()
        val priced = zones.map { z -> ZoneRing(z.outerRing, z.holes, z.routeLimitKn) }
        val bandSpec = bandLaw(world)
        val depthGateActive = AppConfig.routeAvoidDepthGateEnabled && world.depthReady
        val minDepthM = AppConfig.routeAvoidDepthGateMinM
        val gate = gateRuleOf(world, plan, depthGateActive, minDepthM)
        val fineWater = plan.fineWater(
            FineWaterQuery(
                world = world, box = box, edges = edges, openCoast = openCoast,
                marginM = marginM, baseCellM = cellM
            )
        )
        if (fineWater.isEmpty) return emptyList()
        val fineBoxes = fineWindowBoxes(world, fine, box, edges, openCoast, fineWater)
        val fineMask = fineWater.toMask()
        val requests = LinkedHashMap<TileKey, FineTileRequest>()
        for (fineBox in fineBoxes) {
            val rows = ceil((fineBox.latNorth - fineBox.latSouth) / fine.cellSizeDegLat).toInt().coerceAtLeast(1)
            val cols = ceil((fineBox.lonEast - fineBox.lonWest) / fine.cellSizeDegLon).toInt().coerceAtLeast(1)
            val windowRow = fine.rowOf(fineBox.latSouth)
            val windowCol = fine.colOf(fineBox.lonWest)
            val firstTileRow = Math.floorDiv(windowRow, FINE_TILE_CELLS)
            val lastTileRow = Math.floorDiv(windowRow + rows - 1, FINE_TILE_CELLS)
            val firstTileCol = Math.floorDiv(windowCol, FINE_TILE_CELLS)
            val lastTileCol = Math.floorDiv(windowCol + cols - 1, FINE_TILE_CELLS)
            for (tileRow in firstTileRow..lastTileRow) {
                for (tileCol in firstTileCol..lastTileCol) {
                    val key = fineTileKey(
                        world, anchor, fine, tileRow, tileCol, marginM, zoneOutsideMarginM,
                        bandSpec, fineMask, fineWater, priced, minDepthM, depthGateActive,
                        fineWater.depthCollarM, markerStrength
                    )
                    if (requests.containsKey(key)) continue
                    val inner = fineTileBox(fine, tileRow, tileCol)
                    val center = LatLng(
                        (inner.latSouth + inner.latNorth) / 2.0,
                        (inner.lonWest + inner.lonEast) / 2.0
                    )
                    requests[key] = FineTileRequest(key, center) {
                        buildFineTile(
                            world, fine, tileRow, tileCol, marginM, zoneOutsideMarginM,
                            priced, bandSpec, fineMask, edges, openCoast,
                            gate, fineWater.depthCollarM, markerStrength
                        )
                    }
                }
            }
        }
        return requests.values.toList()
    }

    /**
     * **One fine tile's own side, in degrees, over [box]** — the sides of the block [`fineTileBox`] lays
     * each tile out on, or `null` where the plan keeps no fine layer over [box] (a single-grid plan like
     * `avoid`, whose walk answers one tile).
     *
     * It exists so a caller can express growth in **whole tiles of the plan's own lattice** — the warm
     * area's outward rings ([`WarmArea`]) — by asking the arithmetic's one owner instead of re-deriving
     * `FINE_TILE_CELLS × the fine lattice's own cell` for itself, which a step change would silently leave
     * behind. The side is read off [`fineTileBox`] itself, so the two can never disagree about the block a
     * row and a column name.
     */
    internal fun fineTileSideOver(world: MultipassWorld, box: BBox): FineTileSide? {
        val fine = fineLatticeOf(world, box)?.fine ?: return null
        val block = fineTileBox(fine, 0, 0)
        return FineTileSide(block.latNorth - block.latSouth, block.lonEast - block.lonWest)
    }

    /**
     * The plan's own fine layer over [box]: the lattice its tiles are drawn on, the anchor it came from, and
     * the two cells the plan answered — the coarse one the fine water's query is made at, the fine one the
     * tiles are keyed on. `null` where the plan keeps no fine layer at all.
     */
    private fun fineLatticeOf(world: MultipassWorld, box: BBox): FineLattice? {
        val planTiles = plan.firstWalkGrid(box, AppConfig.routeAvoidGridCellM)
        if (planTiles.size <= 1) return null
        val cellM = planTiles.maxByOrNull { it.cellM }?.cellM ?: return null
        val fineCellM = planTiles.minByOrNull { it.cellM }?.cellM ?: return null
        val anchor = world.latticeAnchor ?: LatticeAnchor.wholeDegree(box)
        return FineLattice(LatticeFamily.of(anchor, cellM, fineCellM).fine, anchor, cellM, fineCellM)
    }

    /** One plan's fine layer over one box — see [`fineLatticeOf`]. */
    private data class FineLattice(
        val fine: WalkLattice,
        val anchor: LatticeAnchor,
        val coarseCellM: Double,
        val fineCellM: Double
    )

    /**
     * Rasterises one fixed tile block on the fine lattice — the marking the tile map caches.
     *
     * The block is rasterised over its own inner box, exactly the water the merged window holds, so a
     * tile and the window it replaces agree cell for cell.
     *
     * **The open coast is capped at the tile's own box north (D52).** The window the tile replaces closed
     * its coast at the corridor's cap; here it closes at the tile's, and the two agree on the block
     * because the tile's cap is north of that block: the even-odd fill seals every landward cell up to the
     * cap, so the cap's exact latitude never reaches the block, and the land the window sealed stays
     * sealed. The equivalence rests on that — a cap south of the block would move it.
     */
    private fun buildFineTile(
        world: MultipassWorld,
        fine: WalkLattice,
        tileRow: Int,
        tileCol: Int,
        marginM: Double,
        zoneOutsideMarginM: Double,
        priced: List<ZoneRing>,
        bandSpec: BandLaw?,
        fineMask: FineMask?,
        edges: List<MultipassEdge>,
        openCoast: List<List<LatLng>>,
        gate: GateRule?,
        collarReachM: Double,
        markerStrength: Double
    ): FineTile {
        val box = fineTileBox(fine, tileRow, tileCol)
        // **The collar halo.** The tile's BFS must see shallow water across a seam, so the raster is
        // grown by the collar's reach and only the inner block is kept — a member at the inner edge then
        // carries the distance to a shallow cell in the next tile.
        val halo = if (collarReachM > 0.0) ceil(collarReachM / fine.cellM).toInt().coerceAtLeast(1) else 0
        val grown = if (halo > 0) inflateBox(box, halo * fine.cellM) else box
        val capLatNorth = world.regionBounds?.latNorth ?: grown.latNorth
        // **The unit base (D4).** The tile's own field prices its markers at `1.0 × factor` and the grid
        // opens at `1.0`, so the raster's own source cost *is* the pace-free factor `extract` reads, and the
        // pace reaches no stored byte. The field is the tile's own (D5) — the depth gate's wall and the
        // marker price at the unit base — so one base governs a member's cost at this cell size, and the
        // berth distance a cached tile carries is the one an arm's fresh build writes. `paceKn` is
        // deliberately inert here: the base is overridden, so the frame never derives it.
        val tileField = tileCostField(world, baseCostSec = 1.0, markerStrength)
        val raster = rasterizeWindow(
            grown, fine, paceKn = 0.0, marginM = marginM, edges = edges, openCoast = openCoast,
            capLatNorth = capLatNorth, field = tileField, zones = priced,
            zoneOutsideMarginM = zoneOutsideMarginM, band = bandSpec,
            gate = gate, collarReachM = collarReachM,
            fineMask = fineMask, baseCostSecOverride = 1.0
        )
        return FineTile.extract(raster, tileRow, tileCol, FINE_TILE_CELLS, halo)
    }

    /** One tile's **inner** box on the anchored fine lattice — pure arithmetic on the anchor, never a corridor. */
    private fun fineTileBox(fine: WalkLattice, tileRow: Int, tileCol: Int): BBox {
        val sideLat = FINE_TILE_CELLS * fine.cellSizeDegLat
        val sideLon = FINE_TILE_CELLS * fine.cellSizeDegLon
        val latSouth = fine.latSouth + tileRow.toDouble() * sideLat
        val lonWest = fine.lonWest + tileCol.toDouble() * sideLon
        return BBox(latSouth, latSouth + sideLat, lonWest, lonWest + sideLon)
    }

    /** The key one tile is cached under — every value its rasterisation reads, per the plan's own list. */
    private fun fineTileKey(
        world: MultipassWorld,
        anchor: LatticeAnchor,
        fine: WalkLattice,
        tileRow: Int,
        tileCol: Int,
        marginM: Double,
        zoneOutsideMarginM: Double,
        bandSpec: BandLaw?,
        fineMask: FineMask?,
        fineWater: FineWater,
        priced: List<ZoneRing>,
        minDepthM: Double,
        depthGateActive: Boolean,
        depthCollarM: Double,
        markerStrength: Double
    ): TileKey {
        val berth = berthLawOf(plan, depthGateActive)
        return TileKey(
        anchorLatSouth = anchor.latSouth,
        anchorLonWest = anchor.lonWest,
        referenceLat = anchor.referenceLat,
        fineCellM = fine.cellM,
        tileRow = tileRow,
        tileCol = tileCol,
        tileCells = FINE_TILE_CELLS,
        capLatNorth = world.regionBounds?.latNorth
            ?: fineTileBox(fine, tileRow, tileCol).latNorth,
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
        bandEnabled = AppConfig.routeAvoidZone300Enabled,
        zoneOutsideMarginM = zoneOutsideMarginM,
        zonesEnabled = AppConfig.routeAvoidSpeedZoneEnabled,
        coastBandsM = fineWater.coastBandsM,
        excludedZoneIdSet = world.excludedZoneIdSet,
        zoneGenerationStamp = world.zoneGenerationStamp,
        zoneRings = priced,
        markers = world.routeMarkers(),
        markerStrength = markerStrength,
        markerPriceEnabled = AppConfig.routeMarkerEnabled,
        berthOuterM = berth?.outerM ?: 0.0,
        depthCollarM = depthCollarM,
        berthFloorM = berth?.floorM ?: 0.0
    )
}

    /**
     * **The end's own cell carries a measured gate distance.** The margin sweep paints a coast-side cell land
     * before the edge is seeded, and the seeding skips land, so an end `forceFree`d out of the margin kept the
     * `MAX_VALUE` an unseeded cell opens with — which the search's step guard reads as *proved clear* and so
     * never measured the step out of it. The cell is measured here exactly as a disc cell is: its centre's
     * distance to the traced edge within the seed reach, `MAX_VALUE` beyond it, which the guard may then
     * trust. A cell the seeding already wrote keeps its exact value.
     */
    private fun measureEndCell(grid: MultipassGrid, cell: CellIndex, gate: GateRule?) {
        if (gate == null) return
        if (grid.gateDistanceM(cell.row, cell.col) < Double.MAX_VALUE) return
        val centre = grid.center(cell.row, cell.col)
        grid.writeGateDistanceM(cell.row, cell.col, gate.edgeM(centre, gate.seedReachM(grid.cellM)))
    }

    /** A corridor box and whether the region's own bounds cut it short — the instrument's own reading. */
    private data class Corridor(val box: BBox, val clampedByRegion: Boolean)

    /** Whether [p] stands inside this box, its edges included — the region test the ends are held to. */
    private fun BBox.holds(p: RoutePoint): Boolean =
        p.latitude in latSouth..latNorth && p.longitude in lonWest..lonEast

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
     * reaches; this seat grows the coast's harvested segments by each reach, then merges the marked tiles
     * into maximal rectangles. Every reach's cut is a safe superset of its membership: over-coverage costs
     * memory and never an answer, while a missed tile would move one.
     */
    internal fun fineWindowBoxes(
        lattice: WalkLattice,
        box: BBox,
        edges: List<MultipassEdge>,
        openCoast: List<List<LatLng>>,
        reachM: Double
    ): List<BBox> = fineWindowBoxes(
        null, lattice, box, edges, openCoast,
        FineWater(coastReachesM = listOf(reachM), coastBandsM = listOf(0.0..reachM))
    )

    /** The plan's own fine water, tiled and merged — the cut over the coast reaches, then the merge. */
    internal fun fineWindowBoxes(
        world: MultipassWorld?,
        lattice: WalkLattice,
        box: BBox,
        edges: List<MultipassEdge>,
        openCoast: List<List<LatLng>>,
        fineWater: FineWater
    ): List<BBox> {
        val tiles = fineTileGrid(world, lattice, box, edges, openCoast, fineWater) ?: return emptyList()
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
            null, lattice, box, edges, openCoast,
            FineWater(coastReachesM = listOf(reachM), coastBandsM = listOf(0.0..reachM))
        ) ?: return emptyList()
        val out = ArrayList<BBox>(tiles.marked.size)
        for (key in tiles.marked.indices) {
            if (!tiles.marked[key]) continue
            out.add(tiles.tileBox(tiles.rowOf(key), tiles.colOf(key)))
        }
        return out
    }

    /**
     * **The fine lattice's marked tiles** — the tile grid every coast reach's grown geometry touches, as an
     * integer grid on [lattice]'s own lines. One home for the cut, so the merged windows and the unmerged
     * tiles they are measured against come from the same marks.
     */
    private fun fineTileGrid(
        world: MultipassWorld?,
        lattice: WalkLattice,
        box: BBox,
        edges: List<MultipassEdge>,
        openCoast: List<List<LatLng>>,
        fineWater: FineWater
    ): FineTileGrid? {
        val reaches = fineWater.coastReachesM.filter { it > 0.0 }
        val collarM = fineWater.depthCollarM
        if (reaches.isEmpty() && collarM <= 0.0) return null
        val cellM = lattice.cellM
        val maxReachM = maxOf(reaches.maxOrNull() ?: 0.0, collarM)
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
        // Pass 2 — the low-depth collar: every segment of the measured 3 m line marks the tiles within the
        // collar, so an offshore bank — and a rock no fine cell centre stands on — gets its own fine window
        // and fine 20 m cells.
        if (world != null && collarM > 0.0 && world.depthReady) {
            world.shallowEdges(AppConfig.routeAvoidDepthGateMinM).forEachSegmentIn(box) { aLat, aLon, bLat, bLon ->
                mark(LatLng(aLat, aLon), LatLng(bLat, bLon), collarM)
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
            "depth=${when {
                !sample.hasData -> "none"
                sample.source == ykws.android.maro.data.model.DepthSource.NONE -> "filled"
                else -> "${fmt(sample.depthM.toDouble())}m"
            }} " +
            "state=$stateBefore→${grid.state(cell.row, cell.col)} " +
            "limit=${fmt(grid.zoneLimitKn(cell.row, cell.col))}kn)"
    }
}

/**
 * **The body corner set of the costed markers whose price reaches [box]**, or `null` where none does —
 * the fourth set beside the coast, band and ring sets, read by the snap and the relocation alike. A priced
 * marker is kept only while `route.marker.enabled` prices it (a corner the proof can never pay for is noise);
 * a wall is kept whatever the switch says, since the wall stands whatever it says. Each corner stands
 * [marginM] off its body — the coast set's own standoff, so a wall's hug keeps the margin — and reaches a
 * bend within its marker's own collar plus the local cell ([cellAt]), the zone set's own shape.
 */
private fun bodyCornerSet(
    world: MultipassWorld,
    box: BBox,
    marginM: Double,
    cellM: Double,
    cellAt: (LatLng) -> Double
): CornerSet? {
    val priced = AppConfig.routeMarkerEnabled
    val markers = world.routeMarkers().filter { (it.isWall || priced) && it.reachBox().overlaps(box) }
    if (markers.isEmpty()) return null
    val corners = TangentCorners.bodyCorners(markers, marginM, cellM)
    if (corners.isEmpty()) return null
    val reach = HashMap<LatLng, Double>(corners.size * 2)
    for (c in corners) reach[c.point] = c.reachM
    return CornerSet(corners.map { it.point }, { p -> (reach[p] ?: 0.0) + cellAt(p) })
}
