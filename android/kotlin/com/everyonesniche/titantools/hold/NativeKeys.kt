package com.everyonesniche.titantools.hold

import android.content.Context
import android.provider.Settings
import android.view.KeyEvent

/**
 * What the phone itself does with its special buttons (its Shortcut keys page, com.agui.shortcutsettings), read from
 * Settings.System and never changed. Each button is either a "Programmable key" (<name>_programmable_key_enable = 1, acting as
 * <name>_programmable_key_function: 0 None, 1 Ctrl, 2 Sym, 5 Tab, 7 Shift, 8 Alt …) or "Shortcut settings"
 * (<name>_shortcut_key_enable = 1: the phone opens its own apps on a press, a long press or a double press).
 *
 * For a button to have Titan Tools' actions without the phone acting too, it should be Programmable key > None.
 */
object NativeKeys {
    data class Button(val name: String, val label: String)

    val BUTTONS = listOf(Button("func1", "Func1 (side key)"), Button("func2", "Func2"), Button("back", "Back"),
        Button("fn", "fn"), Button("sym", "Sym"), Button("shift_r", "Right Shift"))

    private val FUNCTIONS = mapOf(0 to "None", 1 to "Ctrl", 2 to "Sym", 5 to "Tab", 7 to "Shift", 8 to "Alt")

    /** The button a key code belongs to, where it's known; null for a key added by pressing it (func1/func2 until learned). */
    fun buttonFor(keyCode: Int): Button? = when (keyCode) {
        KeyEvent.KEYCODE_BACK -> BUTTONS[2]
        KeyEvent.KEYCODE_FUNCTION -> BUTTONS[3]
        KeyEvent.KEYCODE_SYM -> BUTTONS[4]
        KeyEvent.KEYCODE_SHIFT_RIGHT -> BUTTONS[5]
        else -> null
    }

    private fun get(context: Context, key: String): Int? = try {
        Settings.System.getInt(context.contentResolver, key)
    } catch (error: Settings.SettingNotFoundException) { null }

    data class State(val label: String, val ok: Boolean, val now: String, val fix: String?)

    /** The button's setting now, whether it's ready for Titan Tools' actions, and what to set if not. */
    fun state(context: Context, button: Button): State? {
        val programmable = get(context, "${button.name}_programmable_key_enable")
        val shortcut = get(context, "${button.name}_shortcut_key_enable")
        if (programmable == null && shortcut == null) return null
        val function = get(context, "${button.name}_programmable_key_function") ?: 0
        val now = when {
            shortcut == 1 -> "Shortcut settings (the phone opens its own apps)"
            programmable == 1 -> "Programmable key > ${FUNCTIONS[function] ?: "function $function"}"
            else -> "the phone's default"
        }
        val ok = programmable == 1 && function == 0 && shortcut != 1
        val name = button.label.substringBefore(" (")
        return State(button.label, ok, now, if (ok) null else "$name key > Programmable key > None")
    }
}
