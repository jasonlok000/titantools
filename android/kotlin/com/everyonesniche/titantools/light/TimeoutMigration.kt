package com.everyonesniche.titantools.light

/**
 * The firmware's own key light: on every key press it lights the keys at its stored level, and keyboard_brightness_timeout
 * says when it turns them off again (-1: never). While the Keyboard light tool is on, Titan Tools owns the light: the timeout
 * is 0, so the firmware never lights the keys by itself, after the phone's own value is saved in
 * keyboard_brightness_timeout_backup (if the firmware hasn't a usable one already). When the tool is off, the backup goes back.
 * Either key missing or unreadable means there's nothing to do: it never blocks anything.
 */
object TimeoutMigration {
    const val MISSING = "__TITANTOOLS_MISSING__"
    const val OWNED = "0"

    /** A write to one of the two keys. */
    data class Write(val backup: Boolean, val value: String)

    /**
     * What taking the light over needs: save the phone's value as the backup when there's no usable backup, then the timeout
     * to 0. Empty when the timeout is missing, unreadable or already 0.
     */
    fun takeWrites(current: String?, backup: String?): List<Write> {
        if (current == null || current == MISSING || !valid(current)) return emptyList()
        if (current.trim() == OWNED) return emptyList()
        val writes = mutableListOf<Write>()
        if (backup == null || backup == MISSING || !valid(backup)) writes += Write(backup = true, value = current.trim())
        writes += Write(backup = false, value = OWNED)
        return writes
    }

    /** The value to put back when the tool lets go of the light, or null when nothing should change. */
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
