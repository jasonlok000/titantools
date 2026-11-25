package com.everyonesniche.titantools.hold

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.view.KeyEvent
import com.everyonesniche.titantools.core.Prefs
import com.everyonesniche.titantools.quick.TriggerActivity
import org.json.JSONObject

/**
 * Hold a letter for an action (docs/key-remap-plan.md), the simple version: only while you aren't typing, long holds only,
 * taps untouched; the action opens an app, runs one of Titan Tools' own actions, or a system action (Home, Back …). No Shizuku.
 *
 * Titan Tools can't re-send a key without Shizuku, so the press goes on to the app as usual; when the key is still down after
 * the hold time (its repeats come), the action runs and the repeats and the release are taken.
 *
 * Saved as a JSON object, key code → action id: "app:<package>", "do:<TriggerActivity action>", "widget:<1-5>" or
 * "global:<home|back|recents|notifications|quick|lock|screenshot>".
 */
object KeyRemap {
    const val ON = "remap_on"
    const val MAP = "remap_map"
    const val HOLD = "remap_hold_millis"
    val HOLD_TIMES = listOf(400, 500, 700, 1000)
    const val DEFAULT_HOLD = 500

    val GLOBALS = linkedMapOf(
        "home" to ("Home" to AccessibilityService.GLOBAL_ACTION_HOME),
        "back" to ("Back" to AccessibilityService.GLOBAL_ACTION_BACK),
        "recents" to ("Recent apps" to AccessibilityService.GLOBAL_ACTION_RECENTS),
        "notifications" to ("Notifications" to AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS),
        "quick" to ("Quick settings" to AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS),
        "lock" to ("Lock screen" to AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN),
        "screenshot" to ("Screenshot" to AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT),
    )

    fun on(context: Context): Boolean = Prefs.of(context).getBoolean(ON, false)

    fun holdMillis(context: Context): Int = Prefs.of(context).getInt(HOLD, DEFAULT_HOLD).takeIf { it in HOLD_TIMES } ?: DEFAULT_HOLD

    fun map(context: Context): Map<Int, String> {
        val json = try { JSONObject(Prefs.of(context).getString(MAP, null) ?: "{}") } catch (error: Exception) { JSONObject() }
        return json.keys().asSequence().mapNotNull { k -> k.toIntOrNull()?.let { it to json.getString(k) } }.toMap()
    }

    /** Sets or clears ([action] empty) the action of letter key [keyCode]. */
    fun set(context: Context, keyCode: Int, action: String) {
        require(keyCode in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z) { "Only letter keys" }
        require(action.isEmpty() || valid(action)) { "Unknown action" }
        val next = map(context).toMutableMap()
        if (action.isEmpty()) next.remove(keyCode) else next[keyCode] = action
        val json = JSONObject()
        next.forEach { (k, v) -> json.put(k.toString(), v) }
        Prefs.of(context).edit().putString(MAP, json.toString()).apply()
    }

    fun valid(action: String): Boolean = when {
        action.startsWith("app:") -> action.length > 4
        action.startsWith("do:") -> action.removePrefix("do:") in TriggerActivity.ACTIONS
        action.startsWith("widget:") -> action.removePrefix("widget:").toIntOrNull() in 1..5
        action.startsWith("global:") -> action.removePrefix("global:") in GLOBALS
        else -> false
    }

    fun label(context: Context, action: String): String = when {
        action.startsWith("app:") -> try {
            val info = context.packageManager.getApplicationInfo(action.removePrefix("app:"), 0)
            "Open ${context.packageManager.getApplicationLabel(info)}"
        } catch (error: PackageManager.NameNotFoundException) { "Open an app that's gone" }
        action.startsWith("do:") -> TriggerActivity.ACTIONS[action.removePrefix("do:")] ?: action
        action.startsWith("widget:") -> "Widget ${action.removePrefix("widget:")}"
        action.startsWith("global:") -> GLOBALS[action.removePrefix("global:")]?.first ?: action
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
            else -> false
        }
    } catch (error: Exception) {
        false
    }
}
