package com.everyonesniche.titantools.light

/**
 * When the keyboard light is on. All times are wall-clock milliseconds (System.currentTimeMillis), the clock TitanKeys stamps
 * its link messages with.
 *
 * - The link is alive when TitanKeys sent a typing message in the last 10 minutes. Then the light follows TitanKeys: on while
 *   it's dark and you type, and for 5 seconds after TitanKeys says typing stopped.
 * - Without the link (another keyboard, or TitanKeys before 3.1): on while it's dark and the screen is on, as NightKeys did.
 */
object LightRule {
    const val LINK_FRESH_MILLIS = 10 * 60_000L
    const val HOLD_OVER_MILLIS = 5_000L

    enum class Mode { FollowsTitanKeys, DarkAndScreenOn }

    /** What TitanKeys last said. [lastAt] is 0 before any message. */
    data class Link(val lastAt: Long = 0, val typing: Boolean = false, val stoppedAt: Long = 0) {
        /** The link after TitanKeys says [typing] at [at], seen at [now]. A message older than the last one is ignored. */
        fun after(typing: Boolean, at: Long, now: Long): Link {
            val time = if (at <= 0 || at > now) now else at
            if (time < lastAt) return this
            return Link(time, typing, if (typing) 0 else time)
        }
    }

    fun alive(link: Link, now: Long): Boolean = link.lastAt > 0 && now >= link.lastAt && now - link.lastAt < LINK_FRESH_MILLIS

    fun mode(link: Link, now: Long): Mode = if (alive(link, now)) Mode.FollowsTitanKeys else Mode.DarkAndScreenOn

    /** Typing now, or stopped less than [HOLD_OVER_MILLIS] ago. */
    fun typing(link: Link, now: Long): Boolean = link.typing || (link.stoppedAt > 0 && now - link.stoppedAt in 0 until HOLD_OVER_MILLIS)

    fun lit(dark: Boolean, screenOn: Boolean, link: Link, now: Long): Boolean {
        if (!dark || !screenOn) return false
        return when (mode(link, now)) {
            Mode.FollowsTitanKeys -> typing(link, now)
            Mode.DarkAndScreenOn -> true
        }
    }

    /** The next time the answer can change by itself (the hold-over ending or the link going stale), or -1 for none. */
    fun nextChange(link: Link, now: Long): Long {
        if (!alive(link, now)) return -1
        val stale = link.lastAt + LINK_FRESH_MILLIS
        val holdOver = if (!link.typing && link.stoppedAt > 0) link.stoppedAt + HOLD_OVER_MILLIS else -1
        return if (holdOver > now) minOf(holdOver, stale) else stale
    }
}
