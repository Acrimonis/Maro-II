package ykws.android.maro.spatial.multipass

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
 *   [bandWidthM], [bandLimitKn] and [bandOutsideMarginM], the [bandExtraM], [zoneOutsideMarginM], the
 *   plan's fine-mask widths ([coastBandsM], [zoneRimM], [depthCollarM], [depthStepM]) and the [paceKn],
 *   because the base cost is `baseCostSec(cellM, paceKn)`.
 * - **The switches** — [bandEnabled], [zonesEnabled], [depthGateEnabled] and [pricesDepthBand], plus
 *   the [excludedZoneIdSet], because the rasterizer reads all five.
 * - **The zone set** — [zoneRings], the exact ring set the fill even-odd-fills, and [capLatNorth], the
 *   open-coast closure's own cap.
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
    /** The pace (kn) the base cost is computed at. */
    val paceKn: Double,
    /** The ids of the speed zones the user has excluded. */
    val excludedZoneIdSet: Set<String>,
    /** The speed-zone list's content stamp. */
    val zoneGenerationStamp: Long,
    /** The exact ring set the fill even-odd-fills — the rasterizer's own zone read. */
    val zoneRings: List<ZoneRing>
)
