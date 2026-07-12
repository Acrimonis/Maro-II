package ykws.android.maro.data.model

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Spec for a single multi-select action button in the bottom action bar.
 *
 * @param id            Unique action identifier ("delete", "export", "pin", "unpin").
 * @param label         Display label on the action button.
 * @param icon          [ImageVector] icon for the button.
 * @param action        Lambda receiving the set of selected item IDs. Scaffold
 *                      auto-exits multiselect after firing.
 * @param enabled       Predicate receiving current selected IDs — controls
 *                      button dimming. Consumer captures items in closure to
 *                      inspect per-item state (e.g. pin status).
 * @param isDestructive When true, button tint switches to uiDashboardZoneDanger.
 */
data class MultiActionSpec(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val action: (Set<String>) -> Unit,
    val enabled: (Set<String>) -> Boolean = { it.isNotEmpty() },
    val isDestructive: Boolean = false
)
