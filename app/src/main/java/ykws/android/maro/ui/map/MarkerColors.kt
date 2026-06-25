package ykws.android.maro.ui.map

import kotlin.random.Random

/** 16-colour contrasting palette for user markers. Index 0-15. Returns ARGB int. */
object MarkerColors {
    private val palette = listOf(
        0xFFE53935.toInt(), // red
        0xFF1E88E5.toInt(), // blue
        0xFF43A047.toInt(), // green
        0xFFFB8C00.toInt(), // orange
        0xFF8E24AA.toInt(), // purple
        0xFF00ACC1.toInt(), // cyan
        0xFFF4511E.toInt(), // deep orange
        0xFF3949AB.toInt(), // indigo
        0xFF7CB342.toInt(), // light green
        0xFFFDD835.toInt(), // yellow
        0xFFD81B60.toInt(), // pink
        0xFF6D4C41.toInt(), // brown
        0xFF00897B.toInt(), // teal
        0xFF5E35B1.toInt(), // deep purple
        0xFFC0CA33.toInt(), // lime
        0xFF546E7A.toInt(), // blue grey
    )

    /** Default colour used when [colorIndex] is null. */
    val default: Int = 0xFF90A4AE.toInt() // blue grey light

    /** Returns the colour for [colorIndex] (0-15), or [default] if null. */
    fun of(colorIndex: Int?): Int =
        if (colorIndex != null && colorIndex in 0..15) palette[colorIndex] else default

    /** 16 colours for the picker grid. */
    val all: List<Int> get() = palette

    /** Returns a random index 0-15. */
    fun randomIndex(): Int = Random.nextInt(16)

    /** Zone fill opacity (33% = ~84/255). */
    val ZONE_ALPHA: Int get() = (0.33 * 255).toInt()

    /** Proximity fill opacity (25% = ~64/255). */
    val PROXIMITY_ALPHA: Int get() = (0.25 * 255).toInt()
}
