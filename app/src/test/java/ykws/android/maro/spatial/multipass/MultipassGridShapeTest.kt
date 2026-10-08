package ykws.android.maro.spatial.multipass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **The flattened grid proved structurally — P3.2 of the selective-perf plan.**
 *
 * No JVM test can size an ART object honestly, so the byte figure belongs to a device pass (R97). What can
 * be pinned is the **shape** the flatten produces: the grid holds only primitives and primitive arrays, so
 * a position carries no per-cell object and no reference slot — the box and its array are gone. The state
 * is a `ByteArray` and the cost a `DoubleArray`, read through the scalar accessors rather than a boxed
 * cell. A declared-field scan is the assertion, because a field of the grid is exactly where an object
 * would have to live.
 */
class MultipassGridShapeTest {

    @Test
    fun everyGridFieldIsAPrimitiveOrAPrimitiveArray() {
        // Instance fields only: Kotlin's companion leaves a static `Companion` field, which is not per-cell
        // state. Every field that *is* per-grid must be a primitive or a primitive array.
        val offenders = MultipassGrid::class.java.declaredFields
            .filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }
            .filter { field ->
                val type = field.type
                !(type.isPrimitive || (type.isArray && type.componentType.isPrimitive))
            }
        assertTrue(
            "the grid carries no per-cell object and no object array — P3's own shape: " +
                offenders.map { "${it.name}:${it.type.simpleName}" },
            offenders.isEmpty()
        )
    }

    @Test
    fun theStateIsAByteArrayAndTheCostADoubleArray() {
        val fields = MultipassGrid::class.java.declaredFields.associateBy { it.name }
        assertEquals("the tag is one byte a cell", ByteArray::class.java, fields.getValue("cellState").type)
        assertEquals(
            "the cost is one double a cell",
            DoubleArray::class.java,
            fields.getValue("sourceCostSec").type
        )
    }
}
