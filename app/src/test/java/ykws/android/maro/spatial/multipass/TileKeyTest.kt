package ykws.android.maro.spatial.multipass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import ykws.android.maro.data.model.LatLng

/**
 * **The tile key's completeness, pinned field by field.**
 *
 * The key is only as sound as the audit of the rasterizer: a keyed value omitted means a moved setting
 * leaves a stale limit cached and the line silently wrong. This test flips **every** keyed value and
 * asserts the key moves, and it counts the key's declared fields against the flip list, so a field added
 * later cannot slip through unnoticed.
 */
class TileKeyTest {

    private fun ring(limitKn: Double) = ZoneRing(
        outerRing = listOf(LatLng(43.510, 7.020), LatLng(43.510, 7.030), LatLng(43.520, 7.030)),
        holes = emptyList(),
        limitKn = limitKn
    )

    private fun baseKey() = TileKey(
        anchorLatSouth = 43.0,
        anchorLonWest = 6.70,
        referenceLat = 43.5,
        fineCellM = 20.0,
        tileRow = 1,
        tileCol = 2,
        tileCells = FINE_TILE_CELLS,
        capLatNorth = 43.6,
        depthGenerationStamp = 1L,
        coastlineGenerationStamp = 2L,
        emodnetCutoffM = 2.0f,
        obstacleMarginM = 50.0,
        gateMinDepthM = 3.0,
        gateMarginM = 20.0,
        depthGateEnabled = true,
        bandWidthM = 300.0,
        bandLimitKn = 5.0,
        bandOutsideMarginM = 50.0,
        bandExtraM = 25.0,
        bandEnabled = true,
        pricesDepthBand = true,
        zoneOutsideMarginM = 100.0,
        zonesEnabled = true,
        coastBandsM = listOf(0.0..100.0),
        zoneRimM = 100.0,
        depthCollarM = 100.0,
        depthStepM = 20.0,
        paceKn = 25.0,
        excludedZoneIdSet = emptySet(),
        zoneGenerationStamp = 3L,
        zoneRings = listOf(ring(4.0))
    )

    /** Every keyed value, flipped once — one entry per declared field. */
    private fun flips(base: TileKey): List<Pair<String, TileKey>> = listOf(
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
        "bandWidthM" to base.copy(bandWidthM = base.bandWidthM + 1.0),
        "bandLimitKn" to base.copy(bandLimitKn = base.bandLimitKn + 1.0),
        "bandOutsideMarginM" to base.copy(bandOutsideMarginM = base.bandOutsideMarginM + 1.0),
        "bandExtraM" to base.copy(bandExtraM = base.bandExtraM + 1.0),
        "bandEnabled" to base.copy(bandEnabled = !base.bandEnabled),
        "pricesDepthBand" to base.copy(pricesDepthBand = !base.pricesDepthBand),
        "zoneOutsideMarginM" to base.copy(zoneOutsideMarginM = base.zoneOutsideMarginM + 1.0),
        "zonesEnabled" to base.copy(zonesEnabled = !base.zonesEnabled),
        "coastBandsM" to base.copy(coastBandsM = listOf(0.0..200.0)),
        "zoneRimM" to base.copy(zoneRimM = base.zoneRimM + 1.0),
        "depthCollarM" to base.copy(depthCollarM = base.depthCollarM + 1.0),
        "depthStepM" to base.copy(depthStepM = base.depthStepM + 1.0),
        "paceKn" to base.copy(paceKn = base.paceKn + 1.0),
        "excludedZoneIdSet" to base.copy(excludedZoneIdSet = setOf("excluded")),
        "zoneGenerationStamp" to base.copy(zoneGenerationStamp = base.zoneGenerationStamp + 1),
        "zoneRings" to base.copy(zoneRings = listOf(ring(6.0)))
    )

    @Test
    fun flippingEachKeyedValueChangesTheKey() {
        val base = baseKey()
        for ((name, altered) in flips(base)) {
            assertNotEquals("flipping $name must change the key", base, altered)
        }
    }

    @Test
    fun everyDeclaredKeyFieldHasAFlipCase() {
        val declared = TileKey::class.java.declaredFields.count {
            !java.lang.reflect.Modifier.isStatic(it.modifiers)
        }
        assertEquals(
            "a new keyed field must gain a flip case in this test, or a moved setting could cache stale water",
            declared,
            flips(baseKey()).size
        )
    }
}
