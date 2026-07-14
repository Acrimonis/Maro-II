package ykws.android.maro.data.model

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Sub-action displayed as a [DropdownMenu] item when a [MultiActionSpec] has
 * [subActions] defined.
 *
 * @param id     Unique sub-action identifier.
 * @param label  Display label in the dropdown.
 * @param action Lambda receiving the set of selected item IDs.
 */
data class MultiActionSubSpec(
    val id: String,
    val label: String,
    val action: (Set<String>) -> Unit
)

/**
 * Spec for a single multi-select action button in the bottom action bar.
 *
 * @param id             Unique action identifier ("delete", "export", "pin").
 * @param label          Display label on the action button.
 * @param icon           [ImageVector] icon for the button.
 * @param action         Lambda receiving the set of selected item IDs. Scaffold
 *                       auto-exits multiselect after firing. Defaults to no-op
 *                       when [subActions] are used instead.
 * @param enabled        Predicate receiving current selected IDs — controls
 *                       button dimming. Consumer captures items in closure to
 *                       inspect per-item state (e.g. pin status).
 * @param isDestructive  When true, button tint switches to uiDashboardZoneDanger.
 * @param confirmMessage When non-null, a [ConfirmDialog] is shown before firing
 *                       [action]. Only fires on confirm.
 * @param subActions     When non-empty, a [DropdownMenu] is shown instead of
 *                       firing [action] directly. Each item fires its own lambda.
 */
data class MultiActionSpec(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val action: (Set<String>) -> Unit = {},
    val enabled: (Set<String>) -> Boolean = { it.isNotEmpty() },
    val isDestructive: Boolean = false,
    val confirmMessage: String? = null,
    val confirmContent: (@androidx.compose.runtime.Composable (Set<String>, () -> Unit, () -> Unit) -> Unit)? = null,
    val subActions: List<MultiActionSubSpec> = emptyList()
)
