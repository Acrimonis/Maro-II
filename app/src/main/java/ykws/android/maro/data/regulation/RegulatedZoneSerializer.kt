package ykws.android.maro.data.regulation

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf

/**
 * Serializer for [RegulatedZoneSet] — converts between Protobuf binary and structured form.
 *
 * Uses [Protobuf] for compact binary serialization with @ProtoNumber-annotated fields.
 *
 * Usage:
 * ```kotlin
 * val bytes = RegulatedZoneSerializer.serialize(zoneSet)
 * val restored = RegulatedZoneSerializer.deserialize(bytes)
 * ```
 */
@OptIn(ExperimentalSerializationApi::class)
object RegulatedZoneSerializer {

    /**
     * Shared [ProtoBuf] instance for binary serialization.
     */
    private val protoBuf = ProtoBuf

    /**
     * Serialize a [RegulatedZoneSet] into a [ByteArray].
     */
    fun serialize(data: RegulatedZoneSet): ByteArray =
        protoBuf.encodeToByteArray(RegulatedZoneSet.serializer(), data)

    /**
     * Deserialize a [RegulatedZoneSet] from a [ByteArray].
     *
     * @throws kotlinx.serialization.SerializationException if the bytes are
     *         malformed or incompatible with the current schema.
     */
    fun deserialize(bytes: ByteArray): RegulatedZoneSet =
        protoBuf.decodeFromByteArray(RegulatedZoneSet.serializer(), bytes)
}
