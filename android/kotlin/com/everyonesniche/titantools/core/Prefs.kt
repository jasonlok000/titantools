package com.everyonesniche.titantools.core

import android.content.Context
import android.content.SharedPreferences
import com.everyonesniche.titantools.light.LightLevels

/** Titan Tools' settings, all in one file. */
object Prefs {
    const val LIGHT_ON = "light_on"
    const val SCALE_ON = "scale_on"
    const val HOLD_ON = "hold_on"
    const val ROTATION_ON = "rotation_on"
    /** The phone's own auto-rotate and rotation, saved when Rotation was turned on, put back when it's off. */
    const val ROTATION_SAVED = "rotation_saved"
    const val ROTATION_SAVED_AUTO = "rotation_saved_auto"
    const val ROTATION_SAVED_ROTATION = "rotation_saved_rotation"
    /** The brightness, 1 to 255 (LightLevels). Earlier builds kept "light_level" (a number) or "light_level_name". */
    const val LIGHT_LEVEL = "light_level"
    const val LIGHT_LEVEL_NAME = "light_level_name"
    const val LIGHT_LEVEL_VALUE = "light_level_value"
    const val LIGHT_DARK_LUX = "light_dark_lux"
    /** "screen" (dark and screen on) or "typing" (dark and typing, over the TitanKeys link). */
    const val LIGHT_WHEN = "light_when"
    const val LIGHT_STAY_SECONDS = "light_stay_seconds"
    /** The level Titan Tools last wrote to the firmware's keyboard_led_brightness, to tell the phone's own changes apart. */
    const val LED_WRITTEN = "led_written"
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

    const val DEFAULT_LEVEL = LightLevels.DEFAULT
    /** Below this many lux it counts as dark (NightKeys' default 10). It counts as bright again above max(+3, ×1.5). */
    val DARK_LUX = listOf(1f, 3f, 5f, 10f, 20f, 30f, 50f, 100f)
    const val DEFAULT_DARK_LUX = 10f
    val STAY_SECONDS = listOf(2, 5, 10, 30, 60)
    const val DEFAULT_STAY_SECONDS = 5
    val HOLD_TIMES = listOf(300, 400, 500, 700)
    const val DEFAULT_HOLD_MILLIS = 400

    fun of(context: Context): SharedPreferences = context.getSharedPreferences("titantools", Context.MODE_PRIVATE)

    fun on(context: Context, key: String) = of(context).getBoolean(key, false)

    /** The brightness, 1 to 255. A choice stored by an earlier build moves over: by its name, then to its number. */
    fun level(context: Context): Int {
        val prefs = of(context)
        if (prefs.contains(LIGHT_LEVEL_VALUE)) return prefs.getInt(LIGHT_LEVEL_VALUE, DEFAULT_LEVEL).takeIf(LightLevels::valid) ?: DEFAULT_LEVEL
        val level = when {
            prefs.contains(LIGHT_LEVEL_NAME) -> LightLevels.fromName(prefs.getString(LIGHT_LEVEL_NAME, null))
            prefs.contains(LIGHT_LEVEL) -> LightLevels.fromOldLevel(prefs.getInt(LIGHT_LEVEL, 0))
            else -> DEFAULT_LEVEL
        }
        prefs.edit().putInt(LIGHT_LEVEL_VALUE, level).remove(LIGHT_LEVEL_NAME).remove(LIGHT_LEVEL).commit()
        return level
    }

    fun setLevel(context: Context, level: Int) {
        if (LightLevels.valid(level)) of(context).edit().putInt(LIGHT_LEVEL_VALUE, level).commit()
    }

    /** The light follows typing in TitanKeys, rather than staying on while it's dark and the screen is on. */
    fun wantTyping(context: Context): Boolean = of(context).getString(LIGHT_WHEN, "screen") == "typing"

    fun staySeconds(context: Context): Int = of(context).getInt(LIGHT_STAY_SECONDS, DEFAULT_STAY_SECONDS).takeIf { it in STAY_SECONDS } ?: DEFAULT_STAY_SECONDS

    fun darkLux(context: Context): Float = of(context).getFloat(LIGHT_DARK_LUX, DEFAULT_DARK_LUX).let { if (it in 0.5f..200f) it else DEFAULT_DARK_LUX }

    fun brightLux(dark: Float): Float = maxOf(dark + 3f, dark * 1.5f)

    fun holdMillis(context: Context): Int = of(context).getInt(HOLD_MILLIS, DEFAULT_HOLD_MILLIS).coerceIn(200, 1000)
}
