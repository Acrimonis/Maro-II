package ykws.android.maro.ui.map

import androidx.annotation.StringRes
import ykws.android.maro.R
import kotlin.random.Random

/**
 * 20-colour contrasting palette for user markers. Index 0-19. Returns ARGB int.
 *
 * **The rule is perceived difference, not a hue/lightness proxy** (v3, 2026-10-23). The proxy it replaced —
 * "40 degrees of hue or 0.10 of lightness" — let a near-black sit ΔE 13 from Black and let more than one entry
 * occupy a single hue family, which is what "no two swatches read alike" was meant to prevent.
 *
 * 1. Every pair clears **ΔE ≥ 25** in CIELAB (CIE76) on the shipped hexes.
 * 2. A hue family may carry more than one entry when the pair clears 1 on its own — the palette's deliberate
 *    tiers are Green/Light Green, Gold/Pale Yellow, Purple/Hot Magenta and Heavy Blue/Azure, each named here so a
 *    new twin has to be argued for rather than measured into existence.
 * 3. Nothing may read as water: an entry clears the sea fill and the depth ramp by ΔE ≥ 25. The ramp is a style
 *    value this repository does not hold, so the clause is checked on the map rather than asserted by a test —
 *    it is what keeps the two blues to a heavy navy and an azure, and no pale cyan.
 * 4. Vivid over muted when two candidates both satisfy 1–3.
 * 5. The eye is the final arbiter: a pair a user calls similar is replaced, and the reason is recorded here.
 *
 * The list is the single home for its own length — [of], [indexOf] and [randomIndex] take their bounds from it —
 * and a marker persists a `colorIndex`, so a re-hex keeps a marker's slot while a removed or reordered entry can
 * change what a stored index points at.
 */
object MarkerColors {
    private val palette = listOf(
        0xFF000000.toInt(), // Black
        0xFFFFD700.toInt(), // Gold
        0xFF2E7D32.toInt(), // Green
        0xFF76FF03.toInt(), // Lime
        0xFFFF6500.toInt(), // Orange
        0xFF616161.toInt(), // Grey
        0xFF7C4DFF.toInt(), // Vivid Violet
        0xFFD50000.toInt(), // Red
        0xFF00F58F.toInt(), // Spring Green
        0xFFCD00CD.toInt(), // Purple
        0xFF81C784.toInt(), // Light Green
        0xFFFFAB00.toInt(), // Amber
        0xFF42A5F5.toInt(), // Azure
        0xFF00838F.toInt(), // Cyan
        0xFFFF1AB0.toInt(), // Hot Magenta
        0xFFFFFFFF.toInt(), // White
        0xFFFFF59D.toInt(), // Pale Yellow
        0xFFFF5252.toInt(), // Coral
        0xFFA8A8A8.toInt(), // Silver
        0xFF0D47A1.toInt(), // Heavy Blue
    )

    /** Default colour used when [colorIndex] is null. */
    val default: Int = 0xFF90A4AE.toInt() // blue grey light

    /** Returns the colour for [colorIndex] (0-19), or [default] if null. */
    fun of(colorIndex: Int?): Int =
        if (colorIndex != null && colorIndex in palette.indices) palette[colorIndex] else default

    /** 20 colours for the picker grid. */
    val all: List<Int> get() = palette

    /** Returns a random index 0-19. */
    fun randomIndex(): Int = Random.nextInt(palette.size)

    /**
     * The palette index [color] stands at, or null when it is not a palette member — the settings rows
     * persist a raw ARGB, so a stored colour from outside the palette selects no swatch.
     */
    fun indexOf(color: Int): Int? = palette.indexOf(color).takeIf { it >= 0 }

    /**
     * The name of a palette colour, as a resource id so both locales carry it. The default marker name
     * reads it, and so does each swatch's accessibility description.
     */
    @StringRes
    fun nameResOf(colorIndex: Int): Int = when (colorIndex) {
        0 -> R.string.marker_color_name_black
        1 -> R.string.marker_color_name_gold
        2 -> R.string.marker_color_name_green
        3 -> R.string.marker_color_name_lime
        4 -> R.string.marker_color_name_orange
        5 -> R.string.marker_color_name_grey
        6 -> R.string.marker_color_name_vivid_violet
        7 -> R.string.marker_color_name_red
        8 -> R.string.marker_color_name_spring_green
        9 -> R.string.marker_color_name_purple
        10 -> R.string.marker_color_name_light_green
        11 -> R.string.marker_color_name_amber
        12 -> R.string.marker_color_name_azure
        13 -> R.string.marker_color_name_cyan
        14 -> R.string.marker_color_name_hot_magenta
        15 -> R.string.marker_color_name_white
        16 -> R.string.marker_color_name_pale_yellow
        17 -> R.string.marker_color_name_coral
        18 -> R.string.marker_color_name_silver
        19 -> R.string.marker_color_name_heavy_blue
        else -> R.string.marker_color_name_grey
    }

    /** Zone fill opacity (33% = ~84/255). */
    val ZONE_ALPHA: Int get() = (0.33 * 255).toInt()

    /** Proximity fill opacity (25% = ~64/255). */
    val PROXIMITY_ALPHA: Int get() = (0.25 * 255).toInt()
}
