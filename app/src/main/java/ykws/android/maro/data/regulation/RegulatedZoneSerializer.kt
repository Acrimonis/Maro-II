package ykws.android.maro.data.regulation

import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.json.Json

/**
 * Serializer for [RegulatedZoneSet] — converts between JSON binary and structured form.
 *
 * Uses [Json] with relaxed settings for forward-compatible deserialization.
 *
 * Usage:
 * ```kotlin
 * val bytes = RegulatedZoneSerializer.serialize(zoneSet)
 * val restored = RegulatedZoneSerializer.deserialize(bytes)
 * ```
 */
object RegulatedZoneSerializer {

    /**
     * Shared [Json] instance configured for binary serialization.
     *
     * - `ignoreUnknownKeys = true` — skip unrecognised fields on deserialization
     *   (forward compatibility).
     * - `encodeDefaults = false` — omit fields holding default values for a
     *   smaller payload.
     */
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    /**
     * Serialize a [RegulatedZoneSet] into a [ByteArray].
     */
    fun serialize(data: RegulatedZoneSet): ByteArray =
        json.encodeToByteArray(RegulatedZoneSet.serializer(), data)

    /**
     * Deserialize a [RegulatedZoneSet] from a [ByteArray].
     *
     * @throws kotlinx.serialization.SerializationException if the bytes are
     *         malformed or incompatible with the current schema.
     */
    fun deserialize(bytes: ByteArray): RegulatedZoneSet =
        json.decodeFromByteArray(RegulatedZoneSet.serializer(), bytes)
}
