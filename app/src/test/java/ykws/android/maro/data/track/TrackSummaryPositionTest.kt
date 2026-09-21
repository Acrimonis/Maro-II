package ykws.android.maro.data.track

import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The position counts' storage contract, and the defect it was written for (2026-09-21).
 *
 * A count of zero is a legitimate count, and protocol buffers drop a field equal to its type's default —
 * so an unbiased zero came back as `0`, which is also what an absent field means: a wholly-land track read
 * as never classified, and the verdict turns never-classified into water. Every case below is a round trip,
 * because the defect lived in the round trip alone: the sampler was right and the summary in memory was
 * right; only the file lost the zero.
 *
 * The counts are therefore stored biased by one, and this class is where that convention is pinned — the
 * storage, the wire, and the two readings that must survive both.
 */
class TrackSummaryPositionTest {

    private val proto = ProtoBuf.Default

    /** Encode → decode, the way the index file is written and read. */
    private fun roundTrip(summary: TrackSummary): TrackSummary {
        val bytes = proto.encodeToByteArray(TrackSummary.serializer(), summary)
        return proto.decodeFromByteArray(TrackSummary.serializer(), bytes)
    }

    /** A summary carrying the counts a classifier would have written, stored biased as the repository does. */
    private fun summary(water: Int, land: Int, classified: Boolean = true) = TrackSummary(
        id = "t",
        name = "t",
        startTimeMs = 0L,
        waterPointCount = if (classified) water + 1 else 0,
        landPointCount = if (classified) land + 1 else 0
    )

    /** The reported case: a track that never left land, whose water count is therefore zero. */
    @Test
    fun `a wholly land track keeps its zero water count and reads as land`() {
        val decoded = roundTrip(summary(water = 0, land = 32))

        assertEquals(0, decoded.sampledWaterPoints ?: -1)
        assertEquals(32, decoded.sampledLandPoints ?: -1)
        assertTrue(decoded.positionClassified)
        assertFalse(decoded.positionIsWater)
    }

    /**
     * The mirror image, which is why this hid: an all-water track loses its land zero the same way, and
     * still answers water — the right answer arrived at by accident, on a count that was also corrupt.
     */
    @Test
    fun `a wholly water track keeps its zero land count and reads as water`() {
        val decoded = roundTrip(summary(water = 32, land = 0))

        assertEquals(32, decoded.sampledWaterPoints ?: -1)
        assertEquals(0, decoded.sampledLandPoints ?: -1)
        assertTrue(decoded.positionIsWater)
    }

    @Test
    fun `a mixed track keeps both counts`() {
        val decoded = roundTrip(summary(water = 11, land = 21))

        assertEquals(11, decoded.sampledWaterPoints ?: -1)
        assertEquals(21, decoded.sampledLandPoints ?: -1)
        assertFalse(decoded.positionIsWater)
    }

    /** The tie is the 2026-09-19 decision and is not this change's business, but it must survive it. */
    @Test
    fun `the tie reads as water`() {
        assertTrue(roundTrip(summary(water = 16, land = 16)).positionIsWater)
    }

    @Test
    fun `a summary with the sentinel stored reads as never classified and therefore water`() {
        val decoded = roundTrip(summary(water = 0, land = 0, classified = false))

        assertFalse(decoded.positionClassified)
        assertEquals(null, decoded.sampledWaterPoints)
        assertTrue(decoded.positionIsWater)
    }

    /**
     * The shape a pre-2026-09-21 index carries on the wire — the old `-1` sentinel in both fields. It
     * decodes as never classified, the honest reading of a file whose counts may be lying, which is what
     * lets the repository's pass re-sample it instead of trusting it.
     */
    @Test
    fun `a legacy summary carrying the old minus-one sentinel reads as never classified`() {
        val legacy = TrackSummary(
            id = "t",
            name = "t",
            startTimeMs = 0L,
            waterPointCount = -1,
            landPointCount = -1
        )

        val decoded = roundTrip(legacy)

        assertFalse(decoded.positionClassified)
        assertTrue(decoded.positionIsWater)
    }

    /**
     * The wire itself, because that is where the defect lived: a real zero count must occupy bytes, and
     * the sentinel must not. Two messages that differed only in a count used to be identical.
     */
    @Test
    fun `a real zero count reaches the wire where the sentinel leaves nothing`() {
        val classified = proto.encodeToByteArray(TrackSummary.serializer(), summary(water = 0, land = 32))
        val unclassified =
            proto.encodeToByteArray(TrackSummary.serializer(), summary(water = 0, land = 0, classified = false))

        assertFalse(classified.contentEquals(unclassified))
        assertTrue(classified.size > unclassified.size)
    }
}
