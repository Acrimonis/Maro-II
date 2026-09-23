package ykws.android.maro.ui.map

import kotlin.random.Random

/**
 * 16-colour contrasting palette for user markers. Index 0-15. Returns ARGB int.
 *
 * The hues are chosen for contrast against the pale sea and land of the MAPNIK tiles, and no blue
 * slot is kept because the depth overlay already owns the blues — and because a hue near the water
 * reads as water rather than as a mark. The list deliberately spreads lightness so that two entries
 * sharing a hue — the greens, the cyans, the violets, the yellows — sit a wide tier apart, with the
 * bright entries earning their place through the marker's own halo and outline over water. The hues
 * stay vivid rather than muted, and any pair must clear either 40 degrees of hue or 0.10 of
 * lightness, so no two swatches in the picker can read alike.
 */
object MarkerColors {
    private val palette = listOf(
        0xFF000000.toInt(), // Black
        0xFFFFD700.toInt(), // Gold
        0xFF2E7D32.toInt(), // Green
        0xFFB39DDB.toInt(), // Light Violet
        0xFFFF6500.toInt(), // Orange
        0xFF616161.toInt(), // Grey
        0xFF4A148C.toInt(), // Dark Violet
        0xFFD50000.toInt(), // Red
        0xFF827717.toInt(), // Olive
        0xFFCD00CD.toInt(), // Purple
        0xFF81C784.toInt(), // Light Green
        0xFFFFAB00.toInt(), // Amber
        0xFFF48FB1.toInt(), // Pink
        0xFF00838F.toInt(), // Cyan
        0xFF880E4F.toInt(), // Maroon
        0xFFFFFFFF.toInt(), // White
    )

    /** Default colour used when [colorIndex] is null. */
    val default: Int = 0xFF90A4AE.toInt() // blue grey light

    /** Returns the colour for [colorIndex] (0-15), or [default] if null. */
    fun of(colorIndex: Int?): Int =
        if (colorIndex != null && colorIndex in palette.indices) palette[colorIndex] else default

    /** 16 colours for the picker grid. */
    val all: List<Int> get() = palette

    /** Returns a random index 0-15. */
    fun randomIndex(): Int = Random.nextInt(palette.size)

    /** Zone fill opacity (33% = ~84/255). */
    val ZONE_ALPHA: Int get() = (0.33 * 255).toInt()

    /** Proximity fill opacity (25% = ~64/255). */
    val PROXIMITY_ALPHA: Int get() = (0.25 * 255).toInt()
}
