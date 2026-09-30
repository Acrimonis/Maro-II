package ykws.android.maro.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.config.AppConfig

/**
 * The alphabetical rule, as the user pinned it (2026-09-29): a configured word is dropped only when the title
 * **starts with** it, character for character, and **whitespace follows** — the key then beginning at the
 * first character of the next word. The comparator the lists and the route ends' selector read is the
 * shipped `title.sort.ignoredPrefixes`, held here as `AppConfig`'s own default.
 */
class ListSortOrderTest {

    private val shipped = AppConfig.titleSortIgnoredPrefixes

    @Test
    fun `a leading configured word is dropped and the key begins at the next word`() {
        assertEquals("port", titleSortKey("Le Port", shipped))
        assertEquals("port", titleSortKey("Le   Port", shipped))
        assertEquals("sables", titleSortKey("Les Sables", shipped))
        assertEquals("porte", titleSortKey("LA PORTE", shipped))
    }

    @Test
    fun `the test is character for character, so an accent and an unseparated word keep their letter`() {
        // `Léman` fails on its second character; `Leman` fails on the whitespace condition, the character
        // after `Le` being `m`. Two different refusals, both wanted.
        assertEquals("léman", titleSortKey("Léman", shipped))
        assertEquals("leman", titleSortKey("Leman", shipped))
        // `Les` claims nothing for `Le`: only a configured word is dropped.
        assertEquals("les sables", titleSortKey("Les Sables", listOf("Le")))
    }

    @Test
    fun `a title that is the word itself has no next word to sort on`() {
        assertEquals("le", titleSortKey("Le", shipped))
    }

    @Test
    fun `the title is read as it stands, and one word is dropped, not a stack`() {
        assertEquals("«le» port", titleSortKey("«Le» Port", shipped))
        assertEquals("les sables", titleSortKey("Les Les Sables", shipped))
    }

    @Test
    fun `leading punctuation stays and sorts by its own codepoint`() {
        assertEquals("--- la salis ---", titleSortKey("--- La Salis ---", shipped))
        assertTrue(
            "a leading dash files ahead of every letter",
            titleSortKey("--- La Salis ---", shipped) < titleSortKey("Aiguille", shipped)
        )
    }

    @Test
    fun `an empty set leaves every title as it stands`() {
        assertEquals("le port", titleSortKey("Le Port", emptyList()))
        assertEquals("port", titleSortKey("Port", emptyList()))
    }

    @Test
    fun `the lists sort on the shared rule, either way`() {
        val items = listOf(StubItem("Le Port"), StubItem("Aiguille"), StubItem("Étang"))
        val ascending = ListSortState(field = ListSortField.TITLE, descending = false)
            .applySort(items) { null }
            .map { it.title }
        // `Le Port` files under P beside `Port`, and the accent keeps its codepoint order — the lists' own
        // rule, which this field follows rather than replacing.
        assertEquals(listOf("Aiguille", "Le Port", "Étang"), ascending)
        assertEquals(
            ascending.reversed(),
            ListSortState(field = ListSortField.TITLE, descending = true)
                .applySort(items) { null }
                .map { it.title }
        )
    }

    /** The least a `ListableItem` can be: the TITLE field reads the title alone. */
    private class StubItem(override val title: String) : ListableItem {
        override val id: String get() = title
        override val description: String get() = ""
        override val createdAtEpochMs: Long get() = 0L
        override val updatedAtEpochMs: Long get() = 0L
        override val isPinned: Boolean get() = false
    }
}
