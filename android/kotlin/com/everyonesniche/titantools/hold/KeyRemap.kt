package com.everyonesniche.titantools.hold

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.view.KeyEvent
import com.everyonesniche.titantools.core.Prefs
import com.everyonesniche.titantools.quick.TriggerActivity
import org.json.JSONArray
import org.json.JSONObject

/**
 * Keys for actions (docs/key-remap-plan.md): hold a key, or press it twice, to open an app, run one of Titan Tools' actions or
 * a system action. Only while you aren't typing. Any key the Accessibility key filter gets can have them: the letters, Back,
 * Sym, fn, and keys added by pressing them (the side key, Recents …).
 *
 * Without Shizuku the first press can't be held back and sent later, so it goes to the app as usual; with Shizuku it's held
 * back and sent again when it turns out to be a plain click (HoldService.remap).
 *
 * Saved as JSON objects, key code → action id, one for holds and one for double presses: "app:<package>",
 * "do:<TriggerActivity action>", "widget:<1-5>" or "global:<home|back|recents|notifications|quick|lock|screenshot>".
 */
object KeyRemap {
    const val ON = "remap_on"
    const val MAP = "remap_map"
    const val DOUBLE = "remap_double"
    const val EXTRA_KEYS = "remap_extra_keys"
    const val HOLD = "remap_hold_millis"
    val HOLD_TIMES = listOf(400, 500, 700, 1000)
    const val DEFAULT_HOLD = 500
    /** The second press of a double press comes within this. */
    const val DOUBLE_MILLIS = 300L

    /** The keys offered without adding them: the letters, then Back, Sym and fn. */
    val SPECIALS = listOf(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_SYM, KeyEvent.KEYCODE_FUNCTION)
    val LETTERS = (KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z).toList()

    val GLOBALS = linkedMapOf(
        "home" to ("Home" to AccessibilityService.GLOBAL_ACTION_HOME),
        "back" to ("Back" to AccessibilityService.GLOBAL_ACTION_BACK),
        "recents" to ("Recent apps" to AccessibilityService.GLOBAL_ACTION_RECENTS),
        "notifications" to ("Notifications" to AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS),
        "quick" to ("Quick settings" to AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS),
        "lock" to ("Lock screen" to AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN),
        "screenshot" to ("Screenshot" to AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT),
    )

    /**
     * The phone's own shortcut functions (com.agui.shortcutsettings' ShortcutFunctionReceiver, exported without a permission):
     * a broadcast of com.agui.action.SHORTCUT_FUNCTION with "shortcut_function" set to the function's name. Read from the
     * phone's app on 2026-11-24: Shortcut_function11 toggles Mini mode (agui_mini_mode_enable).
     *
     * Not offered: com.agui.action.SHORTCUT_FUNCTION is a protected broadcast, so only the system may send it (even adb's
     * shell user is refused, 2026-11-24). Kept empty so a saved "phone:" action from build 31 just does nothing.
     */
    val PHONE = linkedMapOf<String, Pair<String, String>>()
    private const val PHONE_PACKAGE = "com.agui.shortcutsettings"
    private const val PHONE_ACTION = "com.agui.action.SHORTCUT_FUNCTION"

    fun on(context: Context): Boolean = Prefs.of(context).getBoolean(ON, false)

    fun holdMillis(context: Context): Int = Prefs.of(context).getInt(HOLD, DEFAULT_HOLD).takeIf { it in HOLD_TIMES } ?: DEFAULT_HOLD

    private fun read(context: Context, name: String): Map<Int, String> {
        val json = try { JSONObject(Prefs.of(context).getString(name, null) ?: "{}") } catch (error: Exception) { JSONObject() }
        return json.keys().asSequence().mapNotNull { k -> k.toIntOrNull()?.let { it to json.getString(k) } }.toMap()
    }

    fun map(context: Context): Map<Int, String> = read(context, MAP)
    fun doubles(context: Context): Map<Int, String> = read(context, DOUBLE).filterKeys { it !in LETTERS }

    /** Keys added by pressing them. */
    fun extraKeys(context: Context): List<Int> {
        val json = try { JSONArray(Prefs.of(context).getString(EXTRA_KEYS, null) ?: "[]") } catch (error: Exception) { JSONArray() }
        return (0 until json.length()).map { json.getInt(it) }
    }

    fun addKey(context: Context, keyCode: Int) {
        if (keyCode in LETTERS || keyCode in SPECIALS || keyCode in extraKeys(context) || !usable(keyCode)) return
        Prefs.of(context).edit().putString(EXTRA_KEYS, JSONArray(extraKeys(context) + keyCode).toString()).apply()
    }

    /** All the keys the screen lists. */
    fun keys(context: Context): List<Int> = LETTERS + SPECIALS + extraKeys(context)

    /** Never the power or volume keys: the phone needs them. */
    fun usable(keyCode: Int): Boolean = keyCode in 1..400 && keyCode != KeyEvent.KEYCODE_POWER &&
        keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN && keyCode != KeyEvent.KEYCODE_UNKNOWN

    fun name(keyCode: Int): String = when (keyCode) {
        in LETTERS -> ('A' + (keyCode - KeyEvent.KEYCODE_A)).toString()
        KeyEvent.KEYCODE_BACK -> "Back"
        KeyEvent.KEYCODE_SYM -> "Sym"
        KeyEvent.KEYCODE_FUNCTION -> "fn"
        KeyEvent.KEYCODE_HOME -> "Home"
        KeyEvent.KEYCODE_APP_SWITCH -> "Recents"
        else -> KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_").lowercase().replace('_', ' ')
            .replaceFirstChar { it.uppercase() }
    }

    /** Sets or clears ([action] empty) the [double] press or hold action of [keyCode]. */
    fun set(context: Context, keyCode: Int, action: String, double: Boolean) {
        require(usable(keyCode)) { "Not a key that can have an action" }
        require(action.isEmpty() || valid(action)) { "Unknown action" }
        // Letters are hold-only (owner): double presses are for special buttons like Func1.
        require(!double || keyCode !in LETTERS || action.isEmpty()) { "Letters only have holds" }
        val next = (if (double) doubles(context) else map(context)).toMutableMap()
        if (action.isEmpty()) next.remove(keyCode) else next[keyCode] = action
        val json = JSONObject()
        next.forEach { (k, v) -> json.put(k.toString(), v) }
        Prefs.of(context).edit().putString(if (double) DOUBLE else MAP, json.toString()).apply()
    }

    fun valid(action: String): Boolean = when {
        action.startsWith("app:") -> action.length > 4
        action.startsWith("do:") -> action.removePrefix("do:") in TriggerActivity.ACTIONS
        action.startsWith("widget:") -> action.removePrefix("widget:").toIntOrNull() in 1..5
        action.startsWith("global:") -> action.removePrefix("global:") in GLOBALS
        action.startsWith("phone:") -> action.removePrefix("phone:") in PHONE
        else -> false
    }

    fun label(context: Context, action: String): String = when {
        action.startsWith("app:") -> try {
            val info = context.packageManager.getApplicationInfo(action.removePrefix("app:"), 0)
            "Open ${context.packageManager.getApplicationLabel(info)}"
        } catch (error: PackageManager.NameNotFoundException) { "Open an app that's gone" }
        action.startsWith("do:") -> TriggerActivity.ACTIONS[action.removePrefix("do:")] ?: action
        action.startsWith("widget:") -> {
            val slot = action.removePrefix("widget:").toIntOrNull() ?: 0
            val app = com.everyonesniche.titantools.quick.WidgetButton.all(context).firstOrNull { it.slot == slot }?.app
            if (app.isNullOrBlank()) "Widget $slot" else "Widget $slot – $app"
        }
        action.startsWith("global:") -> GLOBALS[action.removePrefix("global:")]?.first ?: action
        action.startsWith("phone:") -> PHONE[action.removePrefix("phone:")]?.first ?: action
        else -> action
    }

    /** Runs [action] from the Accessibility service. False when it couldn't. */
    fun run(service: AccessibilityService, action: String): Boolean = try {
        when {
            action.startsWith("app:") -> service.packageManager.getLaunchIntentForPackage(action.removePrefix("app:"))
                ?.let { service.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true } ?: false
            action.startsWith("do:") -> TriggerActivity.target(service, Intent().putExtra(TriggerActivity.EXTRA_ACTION, action.removePrefix("do:")))
                ?.let { service.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true } ?: false
            action.startsWith("widget:") -> TriggerActivity.target(service, Intent().putExtra(TriggerActivity.EXTRA_ACTION, "widget")
                .putExtra(TriggerActivity.EXTRA_SLOT, action.removePrefix("widget:").toInt()))
                ?.let { service.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true } ?: false
            action.startsWith("global:") -> GLOBALS[action.removePrefix("global:")]?.let { service.performGlobalAction(it.second) } ?: false
            action.startsWith("phone:") -> PHONE[action.removePrefix("phone:")]?.let {
                service.sendBroadcast(Intent(PHONE_ACTION).setPackage(PHONE_PACKAGE).putExtra("shortcut_function", it.second))
                true
            } ?: false
            else -> false
        }
    } catch (error: Exception) {
        false
    }
}
