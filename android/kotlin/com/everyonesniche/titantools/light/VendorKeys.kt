package com.everyonesniche.titantools.light

/**
 * The firmware's own key light (KeyboardLightController): on every key press it writes its stored `keyboard_led_brightness`
 * to the light as is, and `keyboard_brightness_timeout` says when it turns it off (-1: never; 0: it writes 0 at once). Fighting
 * it from outside blanked or relit the keys on every press. So while the Keyboard light tool is on, Titan Tools owns both keys
 * and lets the firmware do the work:
 * - timeout "-1", so nothing turns the light off by itself;
 * - led brightness = the chosen level while the light should be on, "0" while it should be off. A key press then writes
 *   exactly what Titan Tools wants.
 * Each key's own value is saved once before the first change, never a value Titan Tools wrote, and both go back when the tool
 * is off. A missing or unreadable key is left alone: it never blocks anything. Pure, so the checks can run it.
 */
object VendorKeys {
    const val MISSING = "__TITANTOOLS_MISSING__"
    const val TIMEOUT = "keyboard_brightness_timeout"
    const val TIMEOUT_BACKUP = "keyboard_brightness_timeout_backup"
    const val LED = "keyboard_led_brightness"
    /** The timeout while Titan Tools owns the light: the firmware never turns it off. */
    const val OWNED_TIMEOUT = "-1"

    /** The phone's own values, saved before Titan Tools changed anything. Null for a key that was missing. */
    data class Saved(val timeout: String?, val led: String?)

    data class Write(val key: String, val value: String)

    /** A timeout the firmware understands: a whole number from -1 up to Int.MAX_VALUE. */
    fun validTimeout(value: String?): Boolean {
        val number = value?.trim()?.toLongOrNull() ?: return false
        return number >= -1 && number <= Int.MAX_VALUE
    }

    /** A light level: a whole number from 0 to 255. */
    fun validLed(value: String?): Boolean = value?.trim()?.toIntOrNull()?.let { it in 0..255 } == true

    /**
     * What to save before taking over. The timeout comes from the firmware's own backup when it has a usable one (old NightKeys
     * and earlier Titan Tools builds left the timeout itself changed), else from the timeout. Null fields: missing keys.
     */
    fun toSave(timeout: String?, timeoutBackup: String?, led: String?): Saved = Saved(
        timeout = when {
            validTimeout(timeoutBackup) -> timeoutBackup!!.trim()
            validTimeout(timeout) -> timeout!!.trim()
            else -> null
        },
        led = if (validLed(led)) led!!.trim() else null,
    )

    /** The writes that make the firmware keep the light at [level] when [lit], and off otherwise. Only what differs. */
    fun ownWrites(timeout: String?, led: String?, lit: Boolean, level: Int): List<Write> {
        val writes = mutableListOf<Write>()
        if (validTimeout(timeout) && timeout!!.trim() != OWNED_TIMEOUT) writes += Write(TIMEOUT, OWNED_TIMEOUT)
        val want = if (lit) level.toString() else "0"
        if (validLed(led) && led!!.trim() != want) writes += Write(LED, want)
        return writes
    }

    /**
     * A level someone set in the phone's own settings while Titan Tools owns the key light: the stored level isn't what Titan
     * Tools last wrote. It's adopted as Titan Tools' brightness instead of being overwritten, so the two never take turns.
     * Never 0, and never before Titan Tools has written a level itself. Null: nothing to adopt.
     */
    fun adopt(led: String?, lastWritten: String?): Int? {
        if (lastWritten == null || !validLed(led)) return null
        val level = led!!.trim().toInt()
        if (level == 0 || led.trim() == lastWritten.trim()) return null
        return level
    }

    /** The writes that put the phone's own values back. */
    fun restoreWrites(saved: Saved, timeout: String?, led: String?): List<Write> {
        val writes = mutableListOf<Write>()
        if (saved.timeout != null && validTimeout(timeout) && timeout!!.trim() != saved.timeout) writes += Write(TIMEOUT, saved.timeout)
        if (saved.led != null && validLed(led) && led!!.trim() != saved.led) writes += Write(LED, saved.led)
        return writes
    }

    /**
     * Before any Titan Tools ownership: old NightKeys could leave the timeout changed. The firmware's own backup goes back
     * once, or null when nothing should change.
     */
    fun legacyRestore(timeout: String?, timeoutBackup: String?): String? {
        if (!validTimeout(timeout) || !validTimeout(timeoutBackup)) return null
        if (timeout!!.trim() == timeoutBackup!!.trim()) return null
        return timeoutBackup.trim()
    }
}
