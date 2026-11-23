package com.everyonesniche.titantools.light

/**
 * Old NightKeys kept the light on by changing the firmware's keyboard_brightness_timeout, and could leave it changed. Titan
 * Tools never touches it, but puts the firmware's own backup (keyboard_brightness_timeout_backup) back once if the two differ.
 * Either key missing or unreadable means there's nothing to do: it never blocks anything.
 */
object TimeoutMigration {
    const val MISSING = "__TITANTOOLS_MISSING__"

    /** The value to write back, or null when nothing should change. */
    fun restoreValue(current: String?, backup: String?): String? {
        if (current == null || backup == null || current == MISSING || backup == MISSING) return null
        if (!valid(current) || !valid(backup)) return null
        if (current.trim() == backup.trim()) return null
        return backup.trim()
    }

    /** A timeout the firmware understands: a whole number from -1 up to Int.MAX_VALUE. */
    fun valid(value: String): Boolean {
        val number = value.trim().toLongOrNull() ?: return false
        return number >= -1 && number <= Int.MAX_VALUE
    }
}
