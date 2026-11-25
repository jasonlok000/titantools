package com.everyonesniche.titantools.core

import android.content.Context
import com.everyonesniche.titantools.light.LightRule
import java.util.concurrent.CopyOnWriteArraySet

/** What TitanKeys last said over the link, kept across restarts so a restarted light service still knows. */
object Link {
    @Volatile private var link: LightRule.Link? = null
    private val listeners = CopyOnWriteArraySet<() -> Unit>()

    fun listen(listener: () -> Unit): () -> Unit {
        listeners += listener
        return { listeners -= listener }
    }

    fun current(context: Context): LightRule.Link = link ?: load(context).also { link = it }

    fun received(context: Context, typing: Boolean, at: Long, kind: String? = null) {
        val next = current(context).after(typing, at, System.currentTimeMillis(), kind)
        link = next
        context.getSharedPreferences("link", Context.MODE_PRIVATE).edit()
            .putLong("last_at", next.lastAt).putBoolean("typing", next.typing).putLong("stopped_at", next.stoppedAt).putLong("typed_at", next.typedAt).apply()
        listeners.forEach { it() }
    }

    private fun load(context: Context): LightRule.Link {
        val p = context.getSharedPreferences("link", Context.MODE_PRIVATE)
        val lastAt = p.getLong("last_at", 0)
        return LightRule.Link(lastAt, p.getBoolean("typing", false), p.getLong("stopped_at", 0), p.getLong("typed_at", lastAt))
    }
}
