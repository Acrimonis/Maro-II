package ykws.android.maro.spatial.multipass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **The flattened grid proved structurally — P3.2 of the selective-perf plan.**
 *
 * No JVM test can size an ART object honestly, so the byte figure belongs to a device pass (R97). What can
 * be pinned is the **stored shape** the flatten produces: the grid's declared instance fields are only
 * primitives and primitive arrays, so the boxed cell and its reference array are gone and the per-cell state
 * is a `ByteArray` beside the `DoubleArray`s. A declared-field scan is the assertion, because a field of the
 * grid is exactly where a *stored* object would have to live.
 *
 * **The claim is bounded on purpose.** A declared-field scan sees the grid's own storage and nothing else: a
 * **transient** boxed allocator inside a method would pass it unseen, so the absence of a per-cell object is
 * proved for the fields and not for the runtime. And it is a rule about **cell state**, not about every
 * reference: a future reference field that is **not** per-cell — a shared cache, a listener — would be
 * rejected falsely, so such a field belongs off the grid rather than whitelisted here.
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
            fields.getValue("cellSourceCostSec").type
        )
    }
}
