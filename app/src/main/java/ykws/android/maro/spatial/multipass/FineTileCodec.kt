package ykws.android.maro.spatial.multipass

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest

/**
 * **The fine tile's wire codec — one home for the stored field list, the format version and the key.**
 *
 * An entry is a magic word, a **format version byte**, the key's [canonical bytes][TileKey.canonicalBytes]
 * and then the tile's own fields and arrays. The key is stored **whole** and compared on load, so a name
 * collision, a format drift or a stamp change can only ever answer a **miss** — never a stale or wrong
 * tile. The format is versioned so a future shape change is a different byte, and a `decode` of another
 * version returns `null` rather than half-reading it.
 *
 * The codec is pure Kotlin (no Android type), so it lives with the tile types and the store seam; the
 * Android file access is `DirectoryFineTileStore`'s job.
 */
internal object FineTileCodec {

    /**
     * The on-disk format's own version. An entry of another version is discarded, never read — there is
     * **no migration**: a v1 entry is a miss, because v2 carries the pace-free [FineTile.sourceCostFactor]
     * where v1 carried `sourceCostSec` and `baseCostSec`. The key no longer names a pace, so a v1 file is
     * also **orphaned rather than addressed**, and the disk trim reclaims it by age.
     */
    const val FORMAT_VERSION: Int = 2

    /** Eight bytes that say "a fine-tile entry, this codec's shape" before the version byte. */
    private const val MAGIC: Long = 0x4D41524F54494C45L // "MAROTILE"

    private val HEX = "0123456789abcdef".toCharArray()

    /**
     * **The stored entry's name for [key]** — a SHA-256 digest of its canonical bytes, hex-encoded. It is
     * deterministic and identity-free by construction (the digest is taken over
     * [TileKey.canonicalBytes] and never over the key's `hashCode`), so the same key names the same file
     * on every run.
     */
    fun entryName(key: TileKey): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.canonicalBytes())
        val hex = StringBuilder(digest.size * 2)
        for (byte in digest) {
            val value = byte.toInt() and 0xFF
            hex.append(HEX[value ushr 4]).append(HEX[value and 0xF])
        }
        return hex.toString()
    }

    /** Encodes [tile] under [key] — the versioned, key-verified entry the store writes. */
    fun encode(key: TileKey, tile: FineTile): ByteArray {
        val buffer = ByteArrayOutputStream(512)
        val out = DataOutputStream(buffer)
        out.writeLong(MAGIC)
        out.writeByte(FORMAT_VERSION)
        val keyBytes = key.canonicalBytes()
        out.writeInt(keyBytes.size)
        out.write(keyBytes)
        out.writeInt(tile.tileRow)
        out.writeInt(tile.tileCol)
        out.writeInt(tile.tileCells)
        out.writeInt(tile.rows)
        out.writeInt(tile.cols)
        out.writeDouble(tile.latSouth)
        out.writeDouble(tile.lonWest)
        out.writeDouble(tile.cellSizeDegLat)
        out.writeDouble(tile.cellSizeDegLon)
        out.writeDouble(tile.cellM)
        val members = tile.memberCount
        out.writeInt(members)
        out.write(tile.state)
        for (value in tile.sourceCostFactor) out.writeDouble(value)
        for (value in tile.zoneLimitKn) out.writeDouble(value)
        for (value in tile.collarLimitKn) out.writeDouble(value)
        for (value in tile.bandLimitKn) out.writeDouble(value)
        for (value in tile.bandCollarLimitKn) out.writeDouble(value)
        for (value in tile.depthPriceCoef) out.writeDouble(value)
        for (value in tile.localIndex) out.writeInt(value)
        out.flush()
        return buffer.toByteArray()
    }

    /**
     * The tile [bytes] carries **for [key]**, or `null` on a miss of any kind: a foreign magic, another
     * format version, a stored key that is not [key]'s own, or a truncated body. A `null` is always safe —
     * the caller rebuilds — which is the whole point of storing and comparing the key.
     */
    fun decode(key: TileKey, bytes: ByteArray): FineTile? = try {
        val input = DataInputStream(ByteArrayInputStream(bytes))
        if (input.readLong() != MAGIC) {
            null
        } else if (input.readByte().toInt() != FORMAT_VERSION) {
            null
        } else {
            val keyLength = input.readInt()
            if (keyLength < 0) {
                null
            } else {
                val storedKey = ByteArray(keyLength)
                input.readFully(storedKey)
                if (!storedKey.contentEquals(key.canonicalBytes())) null else readTile(input)
            }
        }
    } catch (failure: Exception) {
        null
    }

    private fun readTile(input: DataInputStream): FineTile? {
        val tileRow = input.readInt()
        val tileCol = input.readInt()
        val tileCells = input.readInt()
        val rows = input.readInt()
        val cols = input.readInt()
        val latSouth = input.readDouble()
        val lonWest = input.readDouble()
        val cellSizeDegLat = input.readDouble()
        val cellSizeDegLon = input.readDouble()
        val cellM = input.readDouble()
        val members = input.readInt()
        if (members < 0) return null
        val state = ByteArray(members)
        input.readFully(state)
        return FineTile(
            tileRow = tileRow, tileCol = tileCol, tileCells = tileCells, rows = rows, cols = cols,
            latSouth = latSouth, lonWest = lonWest,
            cellSizeDegLat = cellSizeDegLat, cellSizeDegLon = cellSizeDegLon,
            cellM = cellM,
            state = state,
            sourceCostFactor = input.readDoubles(members),
            zoneLimitKn = input.readDoubles(members),
            collarLimitKn = input.readDoubles(members),
            bandLimitKn = input.readDoubles(members),
            bandCollarLimitKn = input.readDoubles(members),
            depthPriceCoef = input.readDoubles(members),
            localIndex = IntArray(members) { input.readInt() }
        )
    }

    private fun DataInputStream.readDoubles(count: Int): DoubleArray {
        val values = DoubleArray(count)
        for (i in 0 until count) values[i] = readDouble()
        return values
    }
}
