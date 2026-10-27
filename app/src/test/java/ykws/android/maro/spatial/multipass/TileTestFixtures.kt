package ykws.android.maro.spatial.multipass

import ykws.android.maro.data.model.LatLng

/**
 * **The fine-tile layer's shared test fixtures** — one base `TileKey`, its per-field flip list, a tile and
 * the nested zone/marker shapes the key's canonical encoding and the wire codec are pinned against. Kept
 * in one home so the key's completeness, the codec's round-trip and the store's fallbacks all read the
 * same material.
 */
internal object TileTestFixtures {

    fun ring(limitKn: Double) = ZoneRing(
        outerRing = listOf(LatLng(43.510, 7.020), LatLng(43.510, 7.030), LatLng(43.520, 7.030)),
        holes = emptyList(),
        limitKn = limitKn
    )

    fun marker(step: Int, proximityM: Double = 500.0) = RouteMarker(
        "m", RouteMarkerGeometry.Circle(LatLng(43.5, 7.0), 100.0, proximityM), step = step, isWall = false
    )

    fun corridorMarker(step: Int = 3) = RouteMarker(
        "m",
        RouteMarkerGeometry.Corridor(LatLng(43.5, 7.0), LatLng(43.51, 7.01), widthM = 80.0, proximityM = 400.0),
        step = step,
        isWall = false
    )

    fun baseKey(row: Int = 1, col: Int = 2) = TileKey(
        anchorLatSouth = 43.0,
        anchorLonWest = 6.70,
        referenceLat = 43.5,
        fineCellM = 20.0,
        tileRow = row,
        tileCol = col,
        tileCells = FINE_TILE_CELLS,
        capLatNorth = 43.6,
        depthGenerationStamp = 1L,
        coastlineGenerationStamp = 2L,
        emodnetCutoffM = 2.0f,
        obstacleMarginM = 50.0,
        gateMinDepthM = 3.0,
        gateMarginM = 20.0,
        depthGateEnabled = true,
        berthOuterM = 50.0,
        depthCollarM = 170.0,
        bandWidthM = 300.0,
        bandLimitKn = 5.0,
        bandOutsideMarginM = 50.0,
        bandEnabled = true,
        zoneOutsideMarginM = 100.0,
        zonesEnabled = true,
        coastBandsM = listOf(0.0..100.0),
        zoneRings = listOf(ring(4.0)),
        markers = emptyList(),
        markerStrength = 1.0,
        markerPriceEnabled = true,
        berthFloorM = 10.0
    )

    /** Every keyed value, flipped once — one entry per declared field, mirroring `TileKeyTest`. */
    fun flips(base: TileKey): List<Pair<String, TileKey>> = listOf(
        "anchorLatSouth" to base.copy(anchorLatSouth = base.anchorLatSouth + 1.0),
        "anchorLonWest" to base.copy(anchorLonWest = base.anchorLonWest + 1.0),
        "referenceLat" to base.copy(referenceLat = base.referenceLat + 1.0),
        "fineCellM" to base.copy(fineCellM = base.fineCellM + 1.0),
        "tileRow" to base.copy(tileRow = base.tileRow + 1),
        "tileCol" to base.copy(tileCol = base.tileCol + 1),
        "tileCells" to base.copy(tileCells = base.tileCells + 1),
        "capLatNorth" to base.copy(capLatNorth = base.capLatNorth + 1.0),
        "depthGenerationStamp" to base.copy(depthGenerationStamp = base.depthGenerationStamp + 1),
        "coastlineGenerationStamp" to base.copy(coastlineGenerationStamp = base.coastlineGenerationStamp + 1),
        "emodnetCutoffM" to base.copy(emodnetCutoffM = base.emodnetCutoffM + 1f),
        "obstacleMarginM" to base.copy(obstacleMarginM = base.obstacleMarginM + 1.0),
        "gateMinDepthM" to base.copy(gateMinDepthM = base.gateMinDepthM + 1.0),
        "gateMarginM" to base.copy(gateMarginM = base.gateMarginM + 1.0),
        "depthGateEnabled" to base.copy(depthGateEnabled = !base.depthGateEnabled),
        "berthOuterM" to base.copy(berthOuterM = base.berthOuterM + 1.0),
        "depthCollarM" to base.copy(depthCollarM = base.depthCollarM + 1.0),
        "bandWidthM" to base.copy(bandWidthM = base.bandWidthM + 1.0),
        "bandLimitKn" to base.copy(bandLimitKn = base.bandLimitKn + 1.0),
        "bandOutsideMarginM" to base.copy(bandOutsideMarginM = base.bandOutsideMarginM + 1.0),
        "bandEnabled" to base.copy(bandEnabled = !base.bandEnabled),
        "zoneOutsideMarginM" to base.copy(zoneOutsideMarginM = base.zoneOutsideMarginM + 1.0),
        "zonesEnabled" to base.copy(zonesEnabled = !base.zonesEnabled),
        "coastBandsM" to base.copy(coastBandsM = listOf(0.0..200.0)),
        "zoneRings" to base.copy(zoneRings = listOf(ring(6.0))),
        "markers" to base.copy(markers = listOf(marker(3))),
        "markerStrength" to base.copy(markerStrength = base.markerStrength + 1.0),
        "markerPriceEnabled" to base.copy(markerPriceEnabled = !base.markerPriceEnabled),
        "berthFloorM" to base.copy(berthFloorM = base.berthFloorM + 1.0)
    )

    /** A tile of [members] members — the shape the codec and the store are pinned against. */
    fun tile(members: Int, row: Int = 0, col: Int = 0) = FineTile(
        tileRow = row, tileCol = col, tileCells = FINE_TILE_CELLS, rows = 1, cols = members,
        latSouth = 43.0, lonWest = 6.7, cellSizeDegLat = 1.8e-4, cellSizeDegLon = 2.5e-4,
        cellM = 20.0,
        state = ByteArray(members) { (it and 0x3).toByte() },
        sourceCostFactor = DoubleArray(members) { it * 1.5 },
        zoneLimitKn = DoubleArray(members) { it * 2.0 },
        collarLimitKn = DoubleArray(members) { it * 2.5 },
        bandLimitKn = DoubleArray(members) { it * 3.0 },
        bandCollarLimitKn = DoubleArray(members) { it * 3.5 },
        gateDistanceM = DoubleArray(members) { it * 4.0 },
        localIndex = IntArray(members) { it }
    )
}
