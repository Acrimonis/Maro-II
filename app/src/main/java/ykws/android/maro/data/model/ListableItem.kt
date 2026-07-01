package ykws.android.maro.data.model

/** Common interface for listable items (markers and tracks). */
interface ListableItem {
    val id: String
    val title: String
    val description: String
    val createdAtEpochMs: Long
    val updatedAtEpochMs: Long
    val isPinned: Boolean
}
