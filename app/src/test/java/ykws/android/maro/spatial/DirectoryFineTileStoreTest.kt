package ykws.android.maro.spatial

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.spatial.multipass.FineTileCodec
import ykws.android.maro.spatial.multipass.TileTestFixtures
import java.io.File
import java.nio.file.Files

/**
 * **The disk store — a round-trip, and every miss-fallback: absent, corrupt, drifted, over-ceiling.**
 *
 * The store reads one file a tile through the codec, which verifies the version and the whole key, so a
 * partial, foreign or key-mismatched entry is a miss and is discarded rather than half-read. It runs on a
 * plain JVM `File` directory, so the fallbacks are pinned without a device.
 */
class DirectoryFineTileStoreTest {

    private val key = TileTestFixtures.baseKey()

    private fun newDir(): File = Files.createTempDirectory("fine_tiles").toFile()

    @Test
    fun saveThenLoadRoundTrips() = runBlocking {
        val dir = newDir()
        val store = DirectoryFineTileStore(dir)
        val tile = TileTestFixtures.tile(11)
        store.save(key, tile)

        val loaded = store.load(key)
        assertNotNull(loaded)
        assertArrayEquals(tile.state, loaded!!.state)
        assertArrayEquals(tile.sourceCostFactor, loaded.sourceCostFactor, 0.0)
        assertArrayEquals(tile.localIndex, loaded.localIndex)
        assertEquals(1, store.list().size)
        assertTrue("no temp file is left behind", dir.listFiles()!!.none { it.name.endsWith(".tmp") })
    }

    /**
     * **D10 — one `STORE` line per load and per save.** The store emits through its injected sink alone,
     * so a JVM test captures the readings the device would log without a device.
     */
    @Test
    fun oneStoreLineIsEmittedPerLoadAndPerSave() = runBlocking {
        val dir = newDir()
        val lines = mutableListOf<String>()
        val store = DirectoryFineTileStore(dir, trace = { lines += it })
        store.save(key, TileTestFixtures.tile(5))
        store.load(key)
        store.load(key.copy(depthGenerationStamp = key.depthGenerationStamp + 1))

        assertEquals("one save line", 1, lines.count { it.startsWith("STORE save") })
        assertEquals("one load line per load, hit or miss", 2, lines.count { it.startsWith("STORE load") })
        assertTrue("the served load reads as a hit", lines.any { it.contains("hit=true") })
        assertTrue("the absent key reads as a miss", lines.any { it.contains("hit=false") })
    }

    /**
     * **D10 — a load refreshes the entry's age.** A tile read now must outlive a tile written earlier and
     * never read, so oldest-first reclamation spares the water a live arm still needs.
     */
    @Test
    fun aLoadRefreshesTheEntrysAge() = runBlocking {
        val dir = newDir()
        val store = DirectoryFineTileStore(dir, maxBytes = Long.MAX_VALUE)
        val readKey = TileTestFixtures.baseKey(row = 0)
        val staleKey = TileTestFixtures.baseKey(row = 1)
        store.save(readKey, TileTestFixtures.tile(20, row = 0))
        store.save(staleKey, TileTestFixtures.tile(20, row = 1))
        // Make both provably old, then read the one whose entry's age must be pushed back to youngest.
        dir.listFiles()!!.forEach { it.setLastModified(1_000_000L) }
        store.load(readKey)

        val oldest = dir.listFiles()!!.minByOrNull { it.lastModified() }!!
        assertEquals(
            "the unread entry is the oldest, so the read one was refreshed past it",
            FineTileCodec.entryName(staleKey),
            oldest.name
        )
    }

    /**
     * **D10 — the trim's total is incremental.** Many saves each pay one adjustment, never a fresh
     * whole-directory sum, and the surviving set is still bounded by the ceiling.
     */
    @Test
    fun manySavesAreBoundedWithoutRescanningTheDirectory() = runBlocking {
        val dir = newDir()
        // One entry's real size, measured with an unbounded store, then start clean.
        DirectoryFineTileStore(dir, maxBytes = Long.MAX_VALUE)
            .save(TileTestFixtures.baseKey(row = 0), TileTestFixtures.tile(20, row = 0))
        val fileBytes = dir.listFiles()!!.maxOf { it.length() }
        dir.listFiles()!!.forEach { it.delete() }

        val store = DirectoryFineTileStore(dir, maxBytes = 4 * fileBytes)
        for (row in 0 until 40) {
            store.save(TileTestFixtures.baseKey(row = row), TileTestFixtures.tile(20, row = row))
            // A distinct age per save, so the oldest is unambiguous.
            dir.listFiles()!!.forEach { it.setLastModified(1_000_000L + row * 1_000L) }
        }

        assertTrue("the ceiling bounded the directory (${store.list().size} left)", store.list().size <= 4)
        assertEquals("the total was primed once, never re-summed per save", 1, store.directoryScans)
    }

    @Test
    fun aMissingEntryIsAMiss() = runBlocking {
        assertNull(DirectoryFineTileStore(newDir()).load(key))
    }

    @Test
    fun aCorruptFileIsDiscardedAndMisses() = runBlocking {
        val dir = newDir()
        val store = DirectoryFineTileStore(dir)
        store.save(key, TileTestFixtures.tile(5))
        val file = dir.listFiles()!!.single { !it.name.endsWith(".tmp") }
        file.writeBytes(byteArrayOf(1, 2, 3))

        assertNull("a truncated entry is a miss, never a half-read tile", store.load(key))
        assertTrue("and the bad entry is discarded", dir.listFiles()!!.none { it.name == file.name })
    }

    @Test
    fun aDriftedStampNamesAnotherEntryAndMisses() = runBlocking {
        val dir = newDir()
        val store = DirectoryFineTileStore(dir)
        store.save(key, TileTestFixtures.tile(5))
        val drifted = key.copy(depthGenerationStamp = key.depthGenerationStamp + 1)
        assertNull("a stamp change is a different key and therefore a miss", store.load(drifted))
    }

    @Test
    fun theDiskCeilingReclaimsTheOldestEntries() = runBlocking {
        val dir = newDir()
        val seed = DirectoryFineTileStore(dir, maxBytes = Long.MAX_VALUE)
        val keys = (0 until 5).map { row -> TileTestFixtures.baseKey(row = row) }
        for ((row, tileKey) in keys.withIndex()) seed.save(tileKey, TileTestFixtures.tile(20, row = row))

        // Give each entry a distinct age, oldest first, so the reclamation order is deterministic.
        dir.listFiles()!!.sortedBy { it.name }.forEachIndexed { index, file ->
            file.setLastModified(1_000_000L + index * 1_000L)
        }
        val fileBytes = dir.listFiles()!!.maxOf { it.length() }
        val bounded = DirectoryFineTileStore(dir, maxBytes = 2 * fileBytes)
        bounded.save(TileTestFixtures.baseKey(row = 99), TileTestFixtures.tile(20, row = 99))

        val remaining = bounded.list().size
        assertTrue("the disk ceiling reclaimed the oldest entries ($remaining left)", remaining <= 2)
    }
}
