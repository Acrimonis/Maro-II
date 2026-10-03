package ykws.android.maro.ui.components

import androidx.compose.ui.unit.Dp

/**
 * R5's coercion: the live map band for an open bottom dashboard — the panel's measured height held
 * within `[base, ceiling]`, the floor R2 states and the map strip R5 protects. A ceiling below the
 * floor is itself floored at the base, so the range is always valid.
 *
 * It is a neutral shared home (W5): its only readers are `MapScreen` and the unit test, the shared
 * frame [DrawerScaffold] itself never calling it (W14).
 */
internal fun bandHeightFor(base: Dp, measured: Dp, ceiling: Dp): Dp =
    measured.coerceIn(base, ceiling.coerceAtLeast(base))
