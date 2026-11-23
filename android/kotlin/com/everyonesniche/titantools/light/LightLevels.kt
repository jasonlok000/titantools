package com.everyonesniche.titantools.light

/**
 * The keyboard light's brightness choices, by name, and the level each writes to the firmware. 8 was too dim to see on the
 * owner's phone; the firmware's own lit keyboard writes 24, and 255 is clearly visible. Choices are kept by name, so a level
 * change keeps each person's choice.
 */
object LightLevels {
    val LEVELS = linkedMapOf("low" to 24, "medium" to 64, "high" to 160)
    const val DEFAULT = "medium"

    fun level(name: String?): Int = LEVELS[name] ?: LEVELS.getValue(DEFAULT)

    fun name(name: String?): String = if (name != null && name in LEVELS) name else DEFAULT

    /** Titan Tools 1.0.0 (version code 1 and 2) kept the level itself: 8 low, 24 medium, 64 high. */
    fun nameFromOldLevel(level: Int): String = when (level) {
        8 -> "low"
        24 -> "medium"
        64 -> "high"
        else -> DEFAULT
    }
}
