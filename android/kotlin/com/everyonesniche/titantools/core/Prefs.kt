package com.everyonesniche.titantools.core

import android.content.Context
import android.content.SharedPreferences

/** Titan Tools' settings, all in one file. */
object Prefs {
    const val LIGHT_ON = "light_on"
    const val SCALE_ON = "scale_on"
    const val HOLD_ON = "hold_on"
    const val LIGHT_LEVEL = "light_level"
    const val LIGHT_DARK_LUX = "light_dark_lux"
    const val HOLD_MILLIS = "hold_millis"
    const val HOLD_VIBRATE = "hold_vibrate"
    const val HOLD_PASSWORDS = "hold_passwords"
    const val PHONE_DPI = "phone_dpi"
    const val TABLET_DPI = "tablet_dpi"
    const val TIMEOUT_MIGRATED = "timeout_migrated"

    /** The keyboard light's levels: the firmware's own lit keyboard writes 24. */
    val LEVELS = mapOf("low" to 8, "medium" to 24, "high" to 64)
    const val DEFAULT_LEVEL = 24
    /** Below this many lux it counts as dark (NightKeys' default). It counts as bright again above max(+3, ×1.5). */
    val DARK_LUX = listOf(3f, 10f, 30f)
    const val DEFAULT_DARK_LUX = 10f
    val HOLD_TIMES = listOf(300, 400, 500, 700)
    const val DEFAULT_HOLD_MILLIS = 400

    fun of(context: Context): SharedPreferences = context.getSharedPreferences("titantools", Context.MODE_PRIVATE)

    fun on(context: Context, key: String) = of(context).getBoolean(key, false)

    fun level(context: Context): Int = of(context).getInt(LIGHT_LEVEL, DEFAULT_LEVEL).let { if (it in 1..255) it else DEFAULT_LEVEL }

    fun darkLux(context: Context): Float = of(context).getFloat(LIGHT_DARK_LUX, DEFAULT_DARK_LUX).let { if (it in 0.5f..100f) it else DEFAULT_DARK_LUX }

    fun brightLux(dark: Float): Float = maxOf(dark + 3f, dark * 1.5f)

    fun holdMillis(context: Context): Int = of(context).getInt(HOLD_MILLIS, DEFAULT_HOLD_MILLIS).coerceIn(200, 1000)
}
