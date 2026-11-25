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
 * Keys for actions, kept simple like Key Mapper (owner, 2026-11-24): hold a letter, A to Z, to open an app or run an action,
 * only while no text field is open. No special buttons and no double presses.
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

    fun on(context: Context): Boolean = Prefs.of(context).getBoolean(ON, false)

    fun holdMillis(context: Context): Int = Prefs.of(context).getInt(HOLD, DEFAULT_HOLD).takeIf { it in HOLD_TIMES } ?: DEFAULT_HOLD

    /** The letters' actions. Anything saved for another key by builds 29–40 is left out. */
    fun map(context: Context): Map<Int, String> {
        val json = try { JSONObject(Prefs.of(context).getString(MAP, null) ?: "{}") } catch (error: Exception) { JSONObject() }
        return json.keys().asSequence().mapNotNull { k -> k.toIntOrNull()?.takeIf { it in LETTERS }?.let { it to json.getString(k) } }.toMap()
    }

    fun name(keyCode: Int): String = ('A' + (keyCode - KeyEvent.KEYCODE_A)).toString()

    /** Sets or clears ([action] empty) letter [keyCode]'s hold action. */
    fun set(context: Context, keyCode: Int, action: String) {
        require(keyCode in LETTERS) { "Only letter keys" }
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
        } catch (error: PackageManager.NameNotFoundException) { "The app is gone. Pick another." }
        action.startsWith("do:") -> TriggerActivity.ACTIONS[action.removePrefix("do:")] ?: action
        action.startsWith("widget:") -> {
            val slot = action.removePrefix("widget:").toIntOrNull() ?: 0
            val app = com.everyonesniche.titantools.quick.WidgetButton.all(context).firstOrNull { it.slot == slot }?.app
            if (app.isNullOrBlank()) "Widget $slot" else "Widget $slot – $app"
        }
        action.startsWith("global:") -> GLOBALS[action.removePrefix("global:")]?.first ?: action
        else -> action
    }

    /** Runs [action] from the Accessibility service. False when it couldn't. */
    fun run(service: AccessibilityService, action: String): Boolean = try {
        when {
            action.startsWith("app:") -> service.packageManager.getLaunchIntentForPackage(action.removePrefix("app:"))?.let {
                service.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                com.everyonesniche.titantools.core.Feedback.say(service, label(service, action).replaceFirst("Open ", "Opened "))
                true
            } ?: false
            action.startsWith("do:") -> TriggerActivity.target(service, Intent().putExtra(TriggerActivity.EXTRA_ACTION, action.removePrefix("do:")))
                ?.let { service.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true } ?: false
            action.startsWith("widget:") -> TriggerActivity.target(service, Intent().putExtra(TriggerActivity.EXTRA_ACTION, "widget")
                .putExtra(TriggerActivity.EXTRA_SLOT, action.removePrefix("widget:").toInt()))
                ?.let { service.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true } ?: false
            action.startsWith("global:") -> GLOBALS[action.removePrefix("global:")]?.let {
                val done = service.performGlobalAction(it.second)
                // Home, Back and screenshot speak for themselves; the panels and the lock get a word.
                if (done && action.removePrefix("global:") in listOf("notifications", "quick", "recents")) com.everyonesniche.titantools.core.Feedback.say(service, it.first)
                done
            } ?: false
            else -> false
        }
    } catch (error: Exception) {
        false
    }
}
