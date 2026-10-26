package ykws.android.maro.data.markers

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.MarkerGeometry
import ykws.android.maro.data.model.markers.UserMarker
import java.io.File
import java.io.IOException

class UserMarkerRevisionTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun onlySuccessfulWritesAdvanceTheRefreshRevision() = runBlocking {
        val repo = UserMarkerRepository(temp.root)
        File(temp.root, "user_markers.json").writeText("not json")
        repo.loadAll()
        assertEquals(0L, repo.revision.value)
        try { repo.saveAll(emptyList()); fail("unsafe empty overwrite must fail") } catch (_: IOException) { }
        assertEquals(0L, repo.revision.value)
        val marker = UserMarker("one", "one", MarkerGeometry.Pin(LatLng(43.5, 7.5)))
        repo.saveAll(listOf(marker))
        assertEquals(1L, repo.revision.value)
        assertEquals(listOf(marker), repo.loadAll())
        assertEquals(1L, repo.revision.value)
        repo.update(marker.copy(name = "updated"))
        assertEquals(2L, repo.revision.value)
    }
}
