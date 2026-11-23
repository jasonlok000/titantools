package com.everyonesniche.titantools.light

/**
 * When the keyboard light is on. All times are wall-clock milliseconds (System.currentTimeMillis), the clock TitanKeys stamps
 * its link messages with.
 *
 * - "Dark and screen on" (the default): on while it's dark and the screen is on, as NightKeys did.
 * - "Dark and typing": follows TitanKeys over the link. On while it's dark and TitanKeys said typing within the last
 *   [stayMillis] (counted from its last message, typing or not: any "typing" relights at once). The link is alive while
 *   TitanKeys sent something in the last 10 minutes; when it goes quiet, the light falls back to "dark and screen on".
 */
object LightRule {
    const val LINK_FRESH_MILLIS = 10 * 60_000L
    const val DEFAULT_STAY_MILLIS = 5_000L

    enum class Mode { FollowsTitanKeys, DarkAndScreenOn }

    /** What TitanKeys last said. [lastAt] is 0 before any message. */
    data class Link(val lastAt: Long = 0, val typing: Boolean = false, val stoppedAt: Long = 0) {
        /** The link after TitanKeys says [typing] at [at], seen at [now]. A message older than the last one is ignored. */
        fun after(typing: Boolean, at: Long, now: Long): Link {
            val time = if (at <= 0 || at > now) now else at
            if (time < lastAt) return this
            return Link(time, typing, if (typing) 0 else time)
        }

        /** TitanKeys has sent at least one message: it has the link (3.1 or later). */
        val seen: Boolean get() = lastAt > 0
    }

    fun alive(link: Link, now: Long): Boolean = link.lastAt > 0 && now >= link.lastAt && now - link.lastAt < LINK_FRESH_MILLIS

    fun mode(wantTyping: Boolean, link: Link, now: Long): Mode =
        if (wantTyping && alive(link, now)) Mode.FollowsTitanKeys else Mode.DarkAndScreenOn

    /** TitanKeys said something less than [stayMillis] ago. */
    fun typing(link: Link, now: Long, stayMillis: Long): Boolean = link.lastAt > 0 && now - link.lastAt in 0 until stayMillis

    fun lit(dark: Boolean, screenOn: Boolean, wantTyping: Boolean, link: Link, now: Long, stayMillis: Long = DEFAULT_STAY_MILLIS): Boolean {
        if (!dark || !screenOn) return false
        return when (mode(wantTyping, link, now)) {
            Mode.FollowsTitanKeys -> typing(link, now, stayMillis)
            Mode.DarkAndScreenOn -> true
        }
    }

    /** The next time the answer can change by itself (the stay ending or the link going quiet), or -1 for none. */
    fun nextChange(wantTyping: Boolean, link: Link, now: Long, stayMillis: Long = DEFAULT_STAY_MILLIS): Long {
        if (!wantTyping || !alive(link, now)) return -1
        val quiet = link.lastAt + LINK_FRESH_MILLIS
        val stay = link.lastAt + stayMillis
        return if (stay > now) minOf(stay, quiet) else quiet
    }
}
