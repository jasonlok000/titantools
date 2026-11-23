package com.everyonesniche.titantools.light

/**
 * The keyboard light's brightness: a level from 1 to 255, written to the firmware as is. The firmware's own lit keyboard
 * writes 24; 8 was too dim to see on the owner's phone and 255 is clearly bright. The screen offers [CHOICES]; a level
 * adopted from the phone's own settings (VendorKeys.adopt) is kept exactly, and shown as the nearest choice.
 */
object LightLevels {
    val CHOICES = listOf(4, 8, 11, 16, 24, 32, 48, 64, 96, 128, 160, 200, 255)
    const val DEFAULT = 64

    /** The earlier builds' named choices (version codes 3 and 4). */
    private val NAMED = mapOf("low" to 24, "medium" to 64, "high" to 160)

    fun valid(level: Int): Boolean = level in 1..255

    fun fromName(name: String?): Int = NAMED[name] ?: DEFAULT

    /** Version codes 1 and 2 kept the level itself: 8 low, 24 medium, 64 high, which move over by name. */
    fun fromOldLevel(level: Int): Int = fromName(when (level) {
        8 -> "low"
        24 -> "medium"
        64 -> "high"
        else -> null
    })

    /** The choice to show as picked for [level]: itself, or the nearest one. */
    fun nearest(level: Int): Int = CHOICES.minByOrNull { kotlin.math.abs(it - level) }!!
}
