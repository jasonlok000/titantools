package com.everyonesniche.titantools.quick

/**
 * Night mode's choices, pure so the checks can run them. One press turns on Night Light (warm screen), and if picked Extra dim
 * (Android's own, at a strength) and the dark theme. The phone's values from just before are saved, and the next press (or
 * the tool switched off) puts them back.
 */
object NightRule {
    const val NIGHT_LIGHT = "night_display_activated"
    const val EXTRA_DIM = "reduce_bright_colors_activated"
    const val DIM_LEVEL = "reduce_bright_colors_level"

    /** The phone's values before Night mode went on. Null: unknown, left alone. [darkTheme] is the theme's night mode. */
    data class Saved(val nightLight: Int?, val extraDim: Int?, val dimLevel: Int?, val darkTheme: Boolean?)

    data class Options(val extraDim: Boolean, val dimLevel: Int, val darkTheme: Boolean)

    /** Secure settings to set when turning on: Night Light, and Extra dim with its strength when picked. */
    fun onWrites(options: Options): List<Pair<String, Int>> {
        val writes = mutableListOf(NIGHT_LIGHT to 1)
        if (options.extraDim) {
            writes += DIM_LEVEL to options.dimLevel.coerceIn(0, 100)
            writes += EXTRA_DIM to 1
        }
        return writes
    }

    /** Secure settings to put back when turning off: each saved value that's known and valid. */
    fun offWrites(saved: Saved): List<Pair<String, Int>> {
        val writes = mutableListOf<Pair<String, Int>>()
        saved.nightLight?.takeIf { it in 0..1 }?.let { writes += NIGHT_LIGHT to it }
        saved.dimLevel?.takeIf { it in 0..100 }?.let { writes += DIM_LEVEL to it }
        saved.extraDim?.takeIf { it in 0..1 }?.let { writes += EXTRA_DIM to it }
        return writes
    }

    /** The dark theme to set when turning off: the saved one, only when Night mode changed it. */
    fun offTheme(saved: Saved, usedTheme: Boolean): Boolean? = if (usedTheme) saved.darkTheme else null

    val DIM_LEVELS = listOf(0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100)
}
