package com.everyonesniche.titantools.core

import android.content.Context
import android.content.SharedPreferences
import com.everyonesniche.titantools.light.LightLevels
import com.everyonesniche.titantools.light.LightRule

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
    /** Version code 5's choice: "screen" or "typing". Moves over to the activity. */
    const val LIGHT_WHEN = "light_when"
    /** "Light the keys when [darkness] and [activity]" (LightRule): the preset, the darkness choices, the activity. */
    const val LIGHT_PRESET = "light_preset"
    const val LIGHT_DARK_ROOM = "light_dark_room"
    const val LIGHT_NIGHT_HOURS = "light_night_hours"
    const val LIGHT_DARK_THEME = "light_dark_theme"
    const val LIGHT_ANY_TIME = "light_any_time"
    const val LIGHT_NIGHT_FROM = "light_night_from"
    const val LIGHT_NIGHT_TO = "light_night_to"
    const val LIGHT_ACTIVITY = "light_activity"
    /** Only while typing in Night mode: Typing while Titan Tools' Night mode is on, whatever the rule. */
    const val LIGHT_NIGHT_TYPING = "light_night_typing"
    const val FLASH_ON = "flash_on"
    const val PLAY_ON = "play_on"
    const val KILL_ON = "kill_on"
    /** Update checks (Updates), on unless turned off. */
    const val UPDATES_ON = "updates_on"
    /** Widget button: the hosted widget, its app, the picked button, where it is, and the size it was picked at. */
    const val WIDGET_ON = "widget_on"
    const val WIDGET_ID = "widget_id"
    const val WIDGET_APP = "widget_app"
    const val WIDGET_BUTTON = "widget_button"
    const val WIDGET_PATH = "widget_path"
    const val WIDGET_SPOT = "widget_spot"
    const val WIDGET_WIDTH = "widget_width"
    const val WIDGET_HEIGHT = "widget_height"
    /** Night mode: the tool, whether it's on now, its options, and the phone's values from before it went on. */
    const val NIGHT_ON = "night_on"
    const val NIGHT_ACTIVE = "night_active"
    const val NIGHT_EXTRA_DIM = "night_extra_dim"
    const val NIGHT_DIM_LEVEL = "night_dim_level"
    const val NIGHT_DARK_THEME = "night_dark_theme"
    const val NIGHT_SAVED = "night_saved"
    const val NIGHT_SAVED_LIGHT = "night_saved_light"
    const val NIGHT_SAVED_DIM = "night_saved_dim"
    const val NIGHT_SAVED_LEVEL = "night_saved_level"
    const val NIGHT_SAVED_THEME = "night_saved_theme"
    const val NIGHT_USED_THEME = "night_used_theme"
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
    const val SAVED_INTELLIGENT = "saved_intelligent"

    const val DEFAULT_LEVEL = LightLevels.DEFAULT
    /** Below this many lux it counts as dark (NightKeys' default 10). It counts as bright again above max(+3, ×1.5). */
    /** The dark-room slider's range (log-scaled on the screen); earlier builds offered only a list inside it. */
    val DARK_LUX_RANGE = 0.5f..200f
    const val DEFAULT_DARK_LUX = 10f
    val STAY_SECONDS = listOf(0, 1, 2, 5, 10, 30, 60)
    const val DEFAULT_STAY_SECONDS = 5
    val HOLD_TIMES = listOf(300, 400, 500, 700)
    const val DEFAULT_HOLD_MILLIS = 400

    fun of(context: Context): SharedPreferences = context.getSharedPreferences("titantools", Context.MODE_PRIVATE)

    fun on(context: Context, key: String) = of(context).getBoolean(key, false)

    /** The brightness, 1 to 255. A choice stored by an earlier build moves over: by its name, then to its number. */
    fun level(context: Context): Int {
        val prefs = of(context)
        if (prefs.contains(LIGHT_LEVEL_VALUE)) return LightLevels.usable(prefs.getInt(LIGHT_LEVEL_VALUE, DEFAULT_LEVEL)) ?: DEFAULT_LEVEL
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

    fun preset(context: Context): LightRule.Preset {
        val prefs = of(context)
        if (!prefs.contains(LIGHT_PRESET) && prefs.getString(LIGHT_WHEN, null) == "typing")
            return LightRule.Preset.Custom
        return LightRule.Preset.of(prefs.getString(LIGHT_PRESET, null))
    }

    fun darkness(context: Context): LightRule.Darkness {
        val prefs = of(context)
        return LightRule.Darkness(prefs.getBoolean(LIGHT_DARK_ROOM, true), prefs.getBoolean(LIGHT_NIGHT_HOURS, false),
            prefs.getBoolean(LIGHT_DARK_THEME, false), prefs.getBoolean(LIGHT_ANY_TIME, false))
    }

    /** The activity. Version code 5's "Dark and typing" moves over as Typing. */
    fun activity(context: Context): LightRule.Activity {
        val prefs = of(context)
        if (!prefs.contains(LIGHT_ACTIVITY) && prefs.getString(LIGHT_WHEN, null) == "typing") return LightRule.Activity.Typing
        return LightRule.Activity.of(prefs.getString(LIGHT_ACTIVITY, null))
    }

    /** Night hours, in minutes from midnight: 20:00 to 07:00 unless changed. */
    fun nightFrom(context: Context): Int = of(context).getInt(LIGHT_NIGHT_FROM, 20 * 60).takeIf { it in 0 until 1440 } ?: (20 * 60)
    fun nightTo(context: Context): Int = of(context).getInt(LIGHT_NIGHT_TO, 7 * 60).takeIf { it in 0 until 1440 } ?: (7 * 60)

    fun staySeconds(context: Context): Int = of(context).getInt(LIGHT_STAY_SECONDS, DEFAULT_STAY_SECONDS).takeIf { it in STAY_SECONDS } ?: DEFAULT_STAY_SECONDS

    fun darkLux(context: Context): Float = of(context).getFloat(LIGHT_DARK_LUX, DEFAULT_DARK_LUX).let { if (it in DARK_LUX_RANGE) it else DEFAULT_DARK_LUX }

    fun brightLux(dark: Float): Float = maxOf(dark + 3f, dark * 1.5f)

    fun holdMillis(context: Context): Int = of(context).getInt(HOLD_MILLIS, DEFAULT_HOLD_MILLIS).coerceIn(200, 1000)
}
