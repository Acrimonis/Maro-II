package ykws.android.maro.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.track.TrackSummary
import ykws.android.maro.ui.components.PendingDeletion

/**
 * The delete-normalization hiding rule (2026-10-10): a card's deferred deletion ([PendingDeletion] with
 * `hideFromMap = true`) leaves its stored item out of the map's drawn set while the snackbar waits, and
 * the item returns the moment the entry leaves the shared set; a list row's swipe
 * (`hideFromMap = false`) hides nothing, the map keeping the item the user swiped away in the list.
 *
 * Both halves of the expression are held here: [hiddenMapIdsOf]'s own decision and the same one home
 * the stored pass reads when it builds its summaries ([visibleStoredSummaries]). Pure and
 * device-free, exactly as the other gate tests beside it.
 */
class PendingDeletionHidingTest {

    private fun summary(id: String, live: Boolean = false) =
        TrackSummary(id = id, name = id, startTimeMs = 0L).apply { isLive = live }

    @Test
    fun `a card's deferred delete leaves the stored-tracks drawn set and returns when removed`() {
        val summaries = listOf(summary("t1"), summary("t2"))
        val pending = mutableListOf(PendingDeletion("t:t1", hideFromMap = true))

        assertEquals(setOf("t1"), hiddenMapIdsOf(pending, "t:"))
        assertEquals(
            "the hidden item leaves the drawn set",
            listOf("t2"),
            visibleStoredSummaries(summaries, hiddenMapIdsOf(pending, "t:")).map { it.id }
        )

        pending.removeAll { it.key == "t:t1" }
        assertEquals(
            "the item returns the moment the entry is removed",
            listOf("t1", "t2"),
            visibleStoredSummaries(summaries, hiddenMapIdsOf(pending, "t:")).map { it.id }
        )
    }

    @Test
    fun `a list row's swipe hides nothing on the map`() {
        val summaries = listOf(summary("t1"))
        val pending = listOf(PendingDeletion("t:t1", hideFromMap = false))

        assertTrue("a swipe never joins the hiding set", hiddenMapIdsOf(pending, "t:").isEmpty())
        assertEquals(
            listOf("t1"),
            visibleStoredSummaries(summaries, hiddenMapIdsOf(pending, "t:")).map { it.id }
        )
    }

    @Test
    fun `the hiding set answers its own kind's prefix`() {
        val pending = listOf(
            PendingDeletion("t:t1", hideFromMap = true),
            PendingDeletion("m:m1", hideFromMap = true),
            PendingDeletion("t:t2", hideFromMap = false)
        )

        assertEquals(setOf("t1"), hiddenMapIdsOf(pending, "t:"))
        assertEquals(setOf("m1"), hiddenMapIdsOf(pending, "m:"))
    }
}
