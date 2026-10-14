package ykws.android.maro.spatial.multipass

import ykws.android.maro.data.model.LatLng
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

/**
 * **The identity of one fine tile — everything [`rasterizeWindow`] reads, and nothing else.**
 *
 * A tile may be reused across arms and across routes, so its key carries every value its contents move
 * with; a key that misses one leaves a stale limit cached and the line silently wrong. The fields fall
 * into five groups, in the doc's own order:
 *
 * - **The lattice** — the anchor ([anchorLatSouth], [anchorLonWest], [referenceLat]) and the fine cell
 *   ([fineCellM]). The tile grid is fixed on them, so a different anchor or cell is a different tile;
 *   [tileRow], [tileCol] and [tileCells] name which block of that grid.
 * - **The stamps** — [depthGenerationStamp], [coastlineGenerationStamp], [emodnetCutoffM] and
 *   [zoneGenerationStamp]: a re-baked asset, a moved cutoff or a rebuilt zone list must not leave
 *   coefficients cached.
 * - **The geometry and the law** — [obstacleMarginM], [gateMinDepthM], [gateMarginM], the band's
 *   [bandWidthM], [bandLimitKn] and [bandOutsideMarginM], the [bandExtraM], [zoneOutsideMarginM] and the
 *   plan's fine-mask widths ([coastBandsM], [zoneRimM], [depthCollarM], [depthStepM]). The **pace is
 *   deliberately absent**: the tile stores a pace-free factor and the base is re-applied from the target
 *   grid, so one tile serves every speed.
 * - **The switches** — [bandEnabled], [zonesEnabled], [depthGateEnabled] and [pricesDepthBand], plus
 *   the [excludedZoneIdSet], because the rasterizer reads all five.
 * - **The zone set** — [zoneRings], the exact ring set the fill even-odd-fills, and [capLatNorth], the
 *   open-coast closure's own cap.
 * - **The marker price** — [markers], the costed set itself; and the two values that scale and gate it,
 *   [markerStrength] and [markerPriceEnabled], which the rasterizer reads per cell although they live
 *   nowhere on a marker. A strength or switch move must therefore move the key like a marker move does.
 *
 * It is a data class so equality and hashing are the fields' own — the completeness a
 * `flippingEachKeyedValueChangesTheKey` test pins field by field.
 */
internal data class TileKey(
    /** The fixed anchor's south-west latitude. */
    val anchorLatSouth: Double,
    /** The fixed anchor's south-west longitude. */
    val anchorLonWest: Double,
    /** The latitude the lattice's metre→degree pair is derived at. */
    val referenceLat: Double,
    /** The fine cell size (m) — the tile grid's own spacing. */
    val fineCellM: Double,
    /** The tile's row on the fixed fine tile grid. */
    val tileRow: Int,
    /** The tile's column on the fixed fine tile grid. */
    val tileCol: Int,
    /** The block's side, in fine cells. */
    val tileCells: Int,
    /** The open-coast closure's landward cap latitude — the fill's own far edge. */
    val capLatNorth: Double,
    /** The depth grid's generation stamp. */
    val depthGenerationStamp: Long,
    /** The coastline's generation stamp. */
    val coastlineGenerationStamp: Long,
    /** The chart's EMODnet shallow cutoff (m) — the gate [MultipassWorld.depthAt] applies. */
    val emodnetCutoffM: Float,
    /** The obstacle clearance margin (m). */
    val obstacleMarginM: Double,
    /** The depth gate's minimum depth (m). */
    val gateMinDepthM: Double,
    /** The depth gate's own margin (m), the depth price band's inner half. */
    val gateMarginM: Double,
    /** Whether the 3 m depth gate is armed for this arm. */
    val depthGateEnabled: Boolean,
    /** The priced band's width (m). */
    val bandWidthM: Double,
    /** The 300 m band's own speed limit (kn), or 0.0 where the band is not priced. */
    val bandLimitKn: Double,
    /** The priced band's outside-margin width (m). */
    val bandOutsideMarginM: Double,
    /** The depth price band's extra width (m) beyond the gate's margin. */
    val bandExtraM: Double,
    /** Whether the 300 m band's price is armed. */
    val bandEnabled: Boolean,
    /** Whether the plan prices the shallow depth wall — the depth-band **write**'s own switch. */
    val pricesDepthBand: Boolean,
    /** A speed zone's outside-margin width (m). */
    val zoneOutsideMarginM: Double,
    /** Whether the speed-zone price is armed. */
    val zonesEnabled: Boolean,
    /** The coast-distance membership bands (m) the plan's fine mask keeps. */
    val coastBandsM: List<ClosedFloatingPointRange<Double>>,
    /** The zone-rim collar width (m), 0.0 where disabled. */
    val zoneRimM: Double,
    /** The depth-dilation collar width (m), 0.0 where disabled. */
    val depthCollarM: Double,
    /** The depth scan's own step (m). */
    val depthStepM: Double,
    /** The ids of the speed zones the user has excluded. */
    val excludedZoneIdSet: Set<String>,
    /** The speed-zone list's content stamp. */
    val zoneGenerationStamp: Long,
    /** The exact ring set the fill even-odd-fills — the rasterizer's own zone read. */
    val zoneRings: List<ZoneRing>,
    /**
     * **The costed markers this tile's rasterisation reads** — every Circle, Corridor or costed Pin, with
     * its geometry and its step / wall flag. The geometry carries the marker's own proximity, so a moved
     * ring moves the key beside a moved body. It is the key material itself, so a marker created, moved,
     * re-costed or deleted moves the key and no stale tile survives.
     */
    val markers: List<RouteMarker>,
    /**
     * **The marker price's strength** — the live `step × strength` factor [`markerFactorAt`] sums. It is
     * not a marker's own value, so it is keyed **here**: the rasterizer writes the scaled factor into a
     * cell, and a slider move that left the key standing would serve the old price from a cached tile.
     */
    val markerStrength: Double,
    /**
     * **Whether the marker price is armed** (`route.marker.enabled`). The rasterizer reads it, so a
     * toggled switch must move the key; the wall never reads it and stands either way.
     */
    val markerPriceEnabled: Boolean
) {

    /**
     * **The key's canonical, identity-free encoding — the byte string the store names an entry by and
     * verifies it with.** Every declared field is written, in declaration order, through a fixed
     * [DataOutputStream] shape, so two runs agreeing on the key's values produce identical bytes and no
     * `hashCode` — which could fold an *identity* hash into a component — can ever reach a file name.
     *
     * **The one component whose Kotlin order is not pinned is [excludedZoneIdSet]** (a `Set`, whose
     * iteration order is unspecified), so it is **sorted** here. Every other field is a primitive, an
     * ordered `List`, or a data class over ordered values, so no other component reaches an identity hash;
     * the nested [zoneRings] (holes included), the [coastBandsM] ranges and every [markers] geometry are
     * written field by field, because a key that omitted one would name a different tile than the
     * rasteriser built.
     *
     * It is the store's correctness floor: the entry carries these bytes and a load compares them, so a
     * name collision or a drift can only ever produce a **miss**, never a wrong tile. Pinned by
     * `TileKeyCanonicalTest`, which flips each field and asserts the bytes move.
     */
    fun canonicalBytes(): ByteArray {
        val buffer = ByteArrayOutputStream(512)
        val out = DataOutputStream(buffer)
        out.writeDouble(anchorLatSouth)
        out.writeDouble(anchorLonWest)
        out.writeDouble(referenceLat)
        out.writeDouble(fineCellM)
        out.writeInt(tileRow)
        out.writeInt(tileCol)
        out.writeInt(tileCells)
        out.writeDouble(capLatNorth)
        out.writeLong(depthGenerationStamp)
        out.writeLong(coastlineGenerationStamp)
        out.writeFloat(emodnetCutoffM)
        out.writeDouble(obstacleMarginM)
        out.writeDouble(gateMinDepthM)
        out.writeDouble(gateMarginM)
        out.writeBoolean(depthGateEnabled)
        out.writeDouble(bandWidthM)
        out.writeDouble(bandLimitKn)
        out.writeDouble(bandOutsideMarginM)
        out.writeDouble(bandExtraM)
        out.writeBoolean(bandEnabled)
        out.writeBoolean(pricesDepthBand)
        out.writeDouble(zoneOutsideMarginM)
        out.writeBoolean(zonesEnabled)
        out.writeInt(coastBandsM.size)
        for (band in coastBandsM) {
            out.writeDouble(band.start)
            out.writeDouble(band.endInclusive)
        }
        out.writeDouble(zoneRimM)
        out.writeDouble(depthCollarM)
        out.writeDouble(depthStepM)
        val excluded = excludedZoneIdSet.sorted()
        out.writeInt(excluded.size)
        for (id in excluded) out.writeUtf(id)
        out.writeLong(zoneGenerationStamp)
        out.writeInt(zoneRings.size)
        for (ring in zoneRings) out.writeZoneRing(ring)
        out.writeInt(markers.size)
        for (marker in markers) out.writeMarker(marker)
        out.writeDouble(markerStrength)
        out.writeBoolean(markerPriceEnabled)
        out.flush()
        return buffer.toByteArray()
    }
}

/** The marker geometry tags the canonical encoding writes — a variant a reader can switch on. */
private const val GEOMETRY_CIRCLE: Int = 1
private const val GEOMETRY_CORRIDOR: Int = 2

/** A length-prefixed UTF-8 string, so a name of any length round-trips without `writeUTF`'s 64 KiB cap. */
private fun DataOutputStream.writeUtf(value: String) {
    val bytes = value.toByteArray(Charsets.UTF_8)
    writeInt(bytes.size)
    write(bytes)
}

private fun DataOutputStream.writeLatLng(p: LatLng) {
    writeDouble(p.latitude)
    writeDouble(p.longitude)
}

private fun DataOutputStream.writeRing(ring: List<LatLng>) {
    writeInt(ring.size)
    for (p in ring) writeLatLng(p)
}

private fun DataOutputStream.writeZoneRing(ring: ZoneRing) {
    writeRing(ring.outerRing)
    writeInt(ring.holes.size)
    for (hole in ring.holes) writeRing(hole)
    writeDouble(ring.limitKn)
}

private fun DataOutputStream.writeMarker(marker: RouteMarker) {
    writeUtf(marker.id)
    when (val geometry = marker.geometry) {
        is RouteMarkerGeometry.Circle -> {
            writeByte(GEOMETRY_CIRCLE)
            writeLatLng(geometry.center)
            writeDouble(geometry.radiusM)
            writeDouble(geometry.proximityM)
        }
        is RouteMarkerGeometry.Corridor -> {
            writeByte(GEOMETRY_CORRIDOR)
            writeLatLng(geometry.p1)
            writeLatLng(geometry.p2)
            writeDouble(geometry.widthM)
            writeDouble(geometry.proximityM)
        }
    }
    writeInt(marker.step)
    writeBoolean(marker.isWall)
}
