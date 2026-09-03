package ykws.android.maro.ui.map

import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel

/** Session-scoped holder for Settings expander open/closed state. Cleared when the app exits. */
class SettingsViewModel : ViewModel() {
    /** Key = stable expander id; value = expanded (default collapsed). */
    val expanderStates = mutableStateMapOf<String, Boolean>()

    fun isExpanded(id: String): Boolean = expanderStates[id] ?: false
    fun setExpanded(id: String, expanded: Boolean) { expanderStates[id] = expanded }
}
