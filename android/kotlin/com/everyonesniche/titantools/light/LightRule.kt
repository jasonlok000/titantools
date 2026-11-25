package com.everyonesniche.titantools.light

/**
 * When the keyboard light is on: "Light the keys when [darkness] and [activity]". Pure, so the checks can run it. Times are
 * wall-clock milliseconds (System.currentTimeMillis), the clock TitanKeys stamps its link messages with.
 *
 * Darkness, any of: a dark room (the light sensor), night hours, the phone's dark theme, or any time.
 * Activity, one of:
 * - Screen on.
 * - Text field open: with TitanKeys as the keyboard, its link says when it's shown (TitanKeys keeps the phone's own input
 *   flag at 1 everywhere, so the flag can't be used then); with any other keyboard, the phone's debug.sf.agui_input_status.
 * - Typing: with TitanKeys as the keyboard and its link alive, its typing messages (keys and swipes); otherwise the
 *   firmware's own key presses, which light the keys for the stay time by themselves (VendorKeys).
 * Then the light stays on for the stay time after the activity ends. The screen off always means off.
 */
object LightRule {
    const val LINK_FRESH_MILLIS = 10 * 60_000L

    /**
     * What TitanKeys last said. [lastAt] is 0 before any message. [typedAt] is the last message that was real typing (a key, a
     * swipe or a touchpad touch): newer TitanKeys says kind "field" when a field only opened, which Typing ignores. Older
     * TitanKeys sends no kind, and every message counts as typing, as before.
     */
    data class Link(val lastAt: Long = 0, val typing: Boolean = false, val stoppedAt: Long = 0, val typedAt: Long = lastAt,
                    val homeAt: Long = 0) {
        /** The link after TitanKeys says [typing] at [at], seen at [now]. A message older than the last one is ignored. */
        fun after(typing: Boolean, at: Long, now: Long, kind: String? = null): Link {
            val time = if (at <= 0 || at > now) now else at
            if (time < lastAt) return this
            val real = typing && kind != KIND_FIELD && kind != KIND_HOME
            return Link(time, typing, if (typing) 0 else time, if (real) time else typedAt, if (kind == KIND_HOME) time else homeAt)
        }

        /**
         * The field is the home launcher's own empty search box (TitanKeys says kind "home"), and nothing has been typed in it
         * since: keys for actions still work there.
         */
        val onHome: Boolean get() = homeAt > 0 && homeAt >= typedAt

        /** TitanKeys has sent at least one message: it has the link. */
        val seen: Boolean get() = lastAt > 0
    }

    const val KIND_FIELD = "field"
    const val KIND_HOME = "home"

    fun alive(link: Link, now: Long): Boolean = link.lastAt > 0 && now >= link.lastAt && now - link.lastAt < LINK_FRESH_MILLIS

    data class Darkness(val darkRoom: Boolean, val nightHours: Boolean, val darkTheme: Boolean, val anyTime: Boolean)

    /** The facts the darkness choices are checked against. Minutes count from midnight. */
    data class Facts(val roomDark: Boolean, val minuteOfDay: Int, val nightFrom: Int, val nightTo: Int, val themeDark: Boolean)

    /** [minute] is in the hours from [from] to [to], which may run past midnight. Equal ends: never. */
    fun inHours(minute: Int, from: Int, to: Int): Boolean = when {
        from == to -> false
        from < to -> minute in from until to
        else -> minute >= from || minute < to
    }

    fun dark(d: Darkness, f: Facts): Boolean = d.anyTime || (d.darkRoom && f.roomDark) ||
        (d.nightHours && inHours(f.minuteOfDay, f.nightFrom, f.nightTo)) || (d.darkTheme && f.themeDark)

    enum class Activity(val id: String) {
        Screen("screen"), Field("field"), Typing("typing");

        companion object {
            fun of(id: String?): Activity = entries.firstOrNull { it.id == id } ?: Screen
        }
    }

    /**
     * The activity in use: Typing while "Only while typing in Night mode" is on and TitanTools' Night mode is on, else the
     * chosen one.
     */
    fun effectiveActivity(chosen: Activity, nightTyping: Boolean, nightModeOn: Boolean): Activity =
        if (nightTyping && nightModeOn) Activity.Typing else chosen

    /** Where the activity is read from, given the keyboard and the link. */
    enum class Source { Screen, LinkField, LinkTyping, InputFlag, FirmwareKeys }

    fun source(activity: Activity, titanKeysKeyboard: Boolean, linkAlive: Boolean): Source = when (activity) {
        Activity.Screen -> Source.Screen
        // With TitanKeys, the link is the only way to know; while it's quiet, screen on.
        Activity.Field -> if (!titanKeysKeyboard) Source.InputFlag else if (linkAlive) Source.LinkField else Source.Screen
        Activity.Typing -> if (titanKeysKeyboard && linkAlive) Source.LinkTyping else Source.FirmwareKeys
    }

    /** The firmware lights on key presses for at least a second: the shortest stay it can do. */
    const val FIRMWARE_MIN_STAY_MILLIS = 1000L

    /**
     * TitanTools' own light is on. With [Source.FirmwareKeys] it never is: the firmware lights the keys on presses, and
     * TitanTools only gives it the level while it's dark ([firmwareLevelOn]). [fieldOpen] and [fieldClosedAt] are the input
     * flag's reading and when it last went from open to closed.
     */
    fun lit(source: Source, dark: Boolean, screenOn: Boolean, link: Link, now: Long, stayMillis: Long,
            fieldOpen: Boolean = false, fieldClosedAt: Long = 0): Boolean {
        if (!dark || !screenOn) return false
        return when (source) {
            Source.Screen -> true
            Source.LinkField -> link.typing || (link.stoppedAt > 0 && now - link.stoppedAt in 0 until stayMillis)
            Source.LinkTyping -> (link.typedAt > 0 && now - link.typedAt in 0 until stayMillis) || (stayMillis == 0L && link.typing && link.typedAt == link.lastAt)
            Source.InputFlag -> fieldOpen || (fieldClosedAt > 0 && now - fieldClosedAt in 0 until stayMillis)
            Source.FirmwareKeys -> false
        }
    }

    /** With the firmware lighting on key presses: it gets the level while it's dark and the screen is on, else 0. */
    fun firmwareLevelOn(source: Source, dark: Boolean, screenOn: Boolean): Boolean = source == Source.FirmwareKeys && dark && screenOn

    /** The firmware's timeout for key presses: the stay, at least a second. */
    fun firmwareTimeout(stayMillis: Long): Long = maxOf(FIRMWARE_MIN_STAY_MILLIS, stayMillis)

    /** The next time the answer can change by itself (a stay ending or the link going quiet), or -1 for none. */
    fun nextChange(source: Source, link: Link, now: Long, stayMillis: Long, fieldClosedAt: Long = 0): Long {
        val times = mutableListOf<Long>()
        if (link.lastAt > 0) times += link.lastAt + LINK_FRESH_MILLIS
        when (source) {
            Source.LinkTyping -> times += link.typedAt + stayMillis
            Source.LinkField -> if (link.stoppedAt > 0) times += link.stoppedAt + stayMillis
            Source.InputFlag -> if (fieldClosedAt > 0) times += fieldClosedAt + stayMillis
            else -> Unit
        }
        return times.filter { it > now }.minOrNull() ?: -1
    }

    /** The light screen's presets. "Phone's own" hands the light back; "Custom" keeps the lists as they are. */
    enum class Preset(val id: String) {
        NightTyping("night_typing"), DarkField("dark_field"), Typing("typing"), AlwaysInTheDark("always_dark"), PhonesOwn("phone"), Custom("custom");

        companion object {
            fun of(id: String?): Preset = entries.firstOrNull { it.id == id } ?: AlwaysInTheDark
        }
    }

    /** The darkness and activity a preset sets, or null for one that sets none. */
    fun presetLists(preset: Preset): Pair<Darkness, Activity>? = when (preset) {
        Preset.NightTyping -> Darkness(darkRoom = true, nightHours = false, darkTheme = false, anyTime = false) to Activity.Typing
        Preset.AlwaysInTheDark -> Darkness(darkRoom = true, nightHours = false, darkTheme = false, anyTime = false) to Activity.Screen
        Preset.DarkField -> Darkness(darkRoom = true, nightHours = false, darkTheme = false, anyTime = false) to Activity.Field
        Preset.Typing -> Darkness(darkRoom = false, nightHours = false, darkTheme = false, anyTime = true) to Activity.Typing
        Preset.PhonesOwn, Preset.Custom -> null
    }
}
