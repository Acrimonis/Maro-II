package ykws.android.maro.ui.color

import kotlin.math.roundToInt

/**
 * **A derived variant of a colour the user can pick** — the shared reinforcement helper.
 *
 * Where a colour needs reinforcing (a selected line's edge, an under-stroke) it is derived from the
 * base rather than given a key of its own, so the derivation follows whatever the user picked. The
 * share it is pushed toward black by is the app's one reinforcement lever, `ui.reinforce.darkenPct`.
 *
 * The helper is **pure and RGB-only**: the alpha is preserved untouched, each colour channel is scaled
 * by `1 − darkenPct/100` with rounding, and [darkenPct] is clamped to `0..100`. A black base is
 * therefore invariant under it, which is why the existing `#CC000000` under-strokes could adopt it
 * without changing a pixel.
 *
 * The **caller reads the lever**: a shared drawing holds no settings read of its own, so this function
 * takes the percentage as an argument rather than reaching for `AppConfig`.
 *
 * @param color      the base colour, ARGB.
 * @param darkenPct  how far to push the colour toward black, 0..100 (clamped).
 * @return the derived colour, alpha preserved.
 */
fun reinforcedColor(color: Int, darkenPct: Int): Int {
    val scale = 1.0 - darkenPct.coerceIn(0, 100) / 100.0
    val alpha = (color ushr 24) and 0xFF
    val red = (((color shr 16) and 0xFF) * scale).roundToInt()
    val green = (((color shr 8) and 0xFF) * scale).roundToInt()
    val blue = ((color and 0xFF) * scale).roundToInt()
    return (alpha shl 24) or (red shl 16) or (green shl 8) or blue
}
