package ykws.android.maro.spatial.multipass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **The hybrid tile proved structurally — the sparse form of P4.2's §2.**
 *
 * No JVM test can size an ART object honestly, so the byte figure belongs to a device pass. What can be
 * pinned is the **stored shape**: the tile's declared instance fields are only primitives and primitive
 * arrays — a `ByteArray` tag beside six `DoubleArray`s and an `IntArray` of local indices — so no
 * per-cell box and no dense box is materialised to hold a member. A declared-field scan is the
 * assertion, on the same shape as [`MultipassGridShapeTest`].
 *
 * **The claim is bounded on purpose.** A declared-field scan sees the tile's own storage and nothing
 * else: a transient boxed allocator inside a method would pass it unseen, and a future reference field
 * that is **not** per-cell would be rejected falsely, so such a field belongs off the tile.
 */
class FineTileShapeTest {

    @Test
    fun everyTileFieldIsAPrimitiveOrAPrimitiveArray() {
        val offenders = FineTile::class.java.declaredFields
            .filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }
            .filter { field ->
                val type = field.type
                !(type.isPrimitive || (type.isArray && type.componentType.isPrimitive))
            }
        assertTrue(
            "the tile carries no per-cell object and no object array: " +
                offenders.map { "${it.name}:${it.type.simpleName}" },
            offenders.isEmpty()
        )
    }

    @Test
    fun theMemberStorageIsTheForeseenParallelArrays() {
        val fields = FineTile::class.java.declaredFields.associateBy { it.name }
        assertEquals("the tag is one byte a member", ByteArray::class.java, fields.getValue("state").type)
        assertEquals(
            "the source cost is one double a member",
            DoubleArray::class.java,
            fields.getValue("sourceCostSec").type
        )
        assertEquals(
            "the local index is one int a member",
            IntArray::class.java,
            fields.getValue("localIndex").type
        )
        for (name in listOf(
            "zoneLimitKn", "collarLimitKn", "bandLimitKn", "bandCollarLimitKn", "depthPriceCoef"
        )) {
            assertEquals("$name is one DoubleArray a member", DoubleArray::class.java, fields.getValue(name).type)
        }
    }
}
