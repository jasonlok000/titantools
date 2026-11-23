package com.everyonesniche.titantools.core

import android.content.Context
import android.content.SharedPreferences
import com.everyonesniche.titantools.light.LightLevels

/** Titan Tools' settings, all in one file. */
object Prefs {
    const val LIGHT_ON = "light_on"
    const val SCALE_ON = "scale_on"
    const val HOLD_ON = "hold_on"
    /** The brightness by name (LightLevels). The setting the screens send is still called "light_level". */
    const val LIGHT_LEVEL = "light_level"
    const val LIGHT_LEVEL_NAME = "light_level_name"
    const val LIGHT_DARK_LUX = "light_dark_lux"
    const val HOLD_MILLIS = "hold_millis"
    const val HOLD_VIBRATE = "hold_vibrate"
    const val HOLD_PASSWORDS = "hold_passwords"
    const val PHONE_DPI = "phone_dpi"
    const val TABLET_DPI = "tablet_dpi"
    const val TIMEOUT_MIGRATED = "timeout_migrated"
    /** The phone's own key light settings are saved, and Titan Tools owns them until the light tool is off (VendorKeys). */
    const val VENDOR_SAVED = "vendor_saved"
    const val SAVED_TIMEOUT = "saved_timeout"
    const val SAVED_LED = "saved_led"

    const val DEFAULT_LEVEL = 64
    /** Below this many lux it counts as dark (NightKeys' default). It counts as bright again above max(+3, ×1.5). */
    val DARK_LUX = listOf(3f, 10f, 30f)
    const val DEFAULT_DARK_LUX = 10f
    val HOLD_TIMES = listOf(300, 400, 500, 700)
    const val DEFAULT_HOLD_MILLIS = 400

    fun of(context: Context): SharedPreferences = context.getSharedPreferences("titantools", Context.MODE_PRIVATE)

    fun on(context: Context, key: String) = of(context).getBoolean(key, false)

    /** The brightness choice's name. A level stored by an earlier build moves over by its name (LightLevels). */
    fun levelName(context: Context): String {
        val prefs = of(context)
        prefs.getString(LIGHT_LEVEL_NAME, null)?.let { return LightLevels.name(it) }
        val name = if (prefs.contains(LIGHT_LEVEL)) LightLevels.nameFromOldLevel(prefs.getInt(LIGHT_LEVEL, 0)) else LightLevels.DEFAULT
        prefs.edit().putString(LIGHT_LEVEL_NAME, name).remove(LIGHT_LEVEL).commit()
        return name
    }

    fun level(context: Context): Int = LightLevels.level(levelName(context))

    fun darkLux(context: Context): Float = of(context).getFloat(LIGHT_DARK_LUX, DEFAULT_DARK_LUX).let { if (it in 0.5f..100f) it else DEFAULT_DARK_LUX }

    fun brightLux(dark: Float): Float = maxOf(dark + 3f, dark * 1.5f)

    fun holdMillis(context: Context): Int = of(context).getInt(HOLD_MILLIS, DEFAULT_HOLD_MILLIS).coerceIn(200, 1000)
}
