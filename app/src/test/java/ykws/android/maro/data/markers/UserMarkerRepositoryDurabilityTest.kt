package ykws.android.maro.data.markers

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.MarkerGeometry
import ykws.android.maro.data.model.markers.UserMarker
import java.io.File
import java.io.IOException

/**
 * The marker store's durability: a corrupt main file is answered from the `.bak` the previous save
 * left, and a store nothing can read is never overwritten with the empty list that failure returned.
 *
 * Each case opens a fresh repository on the same folder for the read, so the answer comes from disk
 * and not from the writer's in-memory copy.
 */
class UserMarkerRepositoryDurabilityTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val repo get() = UserMarkerRepository(temp.root)

    private val mainFile get() = File(temp.root, "user_markers.json")

    private fun marker(id: String) = UserMarker(
        id = id,
        name = id,
        geometry = MarkerGeometry.Pin(LatLng(43.50, 7.10)),
        createdAtEpochMs = 1_700_000_000_000L
    )

    @Test
    fun aCorruptMainFileIsAnsweredFromTheBackup() = runBlocking {
        val two = listOf(marker("m-1"), marker("m-2"))
        val writer = repo
        writer.saveAll(two)
        writer.saveAll(two) // the second write is what backs the first one up

        mainFile.writeBytes(byteArrayOf(0x00, 0x7B, 0x22, 0x1F, 0x7F, 0x00))

        assertEquals(two, repo.loadAll())
    }

    @Test
    fun anUnreadableStoreRefusesTheEmptyWriteButTakesARealOne() = runBlocking {
        mainFile.writeBytes(byteArrayOf(0x00, 0x7B, 0x22, 0x1F, 0x7F, 0x00))
        val reader = repo

        assertTrue(reader.loadAll().isEmpty())
        assertThrows(IOException::class.java) { runBlocking { reader.saveAll(emptyList()) } }

        reader.saveAll(listOf(marker("m-1")))
        assertEquals(listOf(marker("m-1")), repo.loadAll())
    }
}
