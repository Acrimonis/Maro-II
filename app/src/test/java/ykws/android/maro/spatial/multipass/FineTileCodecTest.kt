package ykws.android.maro.spatial.multipass

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * **The fine tile's wire codec — the round-trip, and every way a stored entry must miss.**
 *
 * A decoded tile must be **byte-identical** to the built one for the same key, and every drift — a stamp
 * change, a truncation, another format version, a foreign body — must answer `null`, never a wrong tile.
 * The version byte and the whole key are what a `decode` checks before it trusts a body, so this pins the
 * store's correctness floor.
 */
class FineTileCodecTest {

    private val key = TileTestFixtures.baseKey()

    @Test
    fun aTileRoundTripsToTheSameFieldsAndArrays() {
        val tile = TileTestFixtures.tile(37, row = 3, col = 4)
        val decoded = FineTileCodec.decode(key, FineTileCodec.encode(key, tile))
        assertNotNull(decoded)
        val restored = decoded!!
        assertEquals(tile.tileRow, restored.tileRow)
        assertEquals(tile.tileCol, restored.tileCol)
        assertEquals(tile.tileCells, restored.tileCells)
        assertEquals(tile.rows, restored.rows)
        assertEquals(tile.cols, restored.cols)
        assertEquals(tile.latSouth, restored.latSouth, 0.0)
        assertEquals(tile.lonWest, restored.lonWest, 0.0)
        assertEquals(tile.cellSizeDegLat, restored.cellSizeDegLat, 0.0)
        assertEquals(tile.cellSizeDegLon, restored.cellSizeDegLon, 0.0)
        assertEquals(tile.cellM, restored.cellM, 0.0)
        assertEquals(tile.memberCount, restored.memberCount)
        assertArrayEquals(tile.state, restored.state)
        assertArrayEquals(tile.sourceCostFactor, restored.sourceCostFactor, 0.0)
        assertArrayEquals(tile.zoneLimitKn, restored.zoneLimitKn, 0.0)
        assertArrayEquals(tile.collarLimitKn, restored.collarLimitKn, 0.0)
        assertArrayEquals(tile.bandLimitKn, restored.bandLimitKn, 0.0)
        assertArrayEquals(tile.bandCollarLimitKn, restored.bandCollarLimitKn, 0.0)
        assertArrayEquals(tile.localIndex, restored.localIndex)
        assertEquals(tile.byteSize, restored.byteSize)
    }

    @Test
    fun aChangedStampIsAMissNotAStaleTile() {
        val bytes = FineTileCodec.encode(key, TileTestFixtures.tile(5))
        val drifted = key.copy(depthGenerationStamp = key.depthGenerationStamp + 1)
        assertNull("a moved stamp must be a miss, never the old tile", FineTileCodec.decode(drifted, bytes))
    }

    /** A tile stored before the gate's wall was measured carries the old wall: its format is a miss. */
    @Test
    fun anEntryOfTheFormerFormatIsAMiss() {
        val bytes = FineTileCodec.encode(key, TileTestFixtures.tile(5))
        bytes[8] = (FineTileCodec.FORMAT_VERSION - 1).toByte()
        assertNull("a former-format entry must miss", FineTileCodec.decode(key, bytes))
    }

    @Test
    fun aTruncatedEntryIsAMiss() {
        val bytes = FineTileCodec.encode(key, TileTestFixtures.tile(5))
        assertNull("a partial entry must miss", FineTileCodec.decode(key, bytes.copyOf(bytes.size / 2)))
    }

    @Test
    fun anotherFormatVersionIsAMiss() {
        val bytes = FineTileCodec.encode(key, TileTestFixtures.tile(5))
        // The version byte sits immediately after the 8-byte magic word.
        bytes[8] = (bytes[8] + 1).toByte()
        assertNull("another version must not be half-read", FineTileCodec.decode(key, bytes))
    }

    /**
     * **D7 — v1 is refused, never read.** v1 carried `sourceCostSec` and `baseCostSec`; v2 carries the
     * pace-free `sourceCostFactor` and no base. There is no migration: a v1 entry is a miss, so it is
     * discarded and rebuilt rather than mis-parsed as v2's shape.
     */
    @Test
    fun aVersionOneEntryIsRefusedNeverRead() {
        val bytes = FineTileCodec.encode(key, TileTestFixtures.tile(5))
        // Force the version byte back to v1 — the byte immediately after the 8-byte magic word.
        bytes[8] = 1
        assertNull("a v1 entry is a miss, never a stale tile", FineTileCodec.decode(key, bytes))
    }

    @Test
    fun aForeignOrGarbageBodyIsAMiss() {
        assertNull(FineTileCodec.decode(key, ByteArray(0)))
        assertNull(FineTileCodec.decode(key, "not a tile at all".toByteArray()))
    }

    @Test
    fun theEntryNameIsDeterministicAndMovesWithTheKey() {
        assertEquals(FineTileCodec.entryName(key), FineTileCodec.entryName(TileTestFixtures.baseKey()))
        assertNotEquals(
            "a different key must name a different entry",
            FineTileCodec.entryName(key),
            FineTileCodec.entryName(key.copy(tileRow = key.tileRow + 1))
        )
    }
}
