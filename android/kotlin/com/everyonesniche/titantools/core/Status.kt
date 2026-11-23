package com.everyonesniche.titantools.core

import java.util.concurrent.CopyOnWriteArraySet

/** The tools' live status lines for the home screen, which follows them as they change. */
object Status {
    @Volatile var lightText = "Off"
        private set
    @Volatile var lightFollows = false
        private set
    private val listeners = CopyOnWriteArraySet<() -> Unit>()

    fun listen(listener: () -> Unit): () -> Unit {
        listeners += listener
        return { listeners -= listener }
    }

    fun changed() = listeners.forEach { it() }

    fun light(text: String, follows: Boolean) {
        if (text == lightText && follows == lightFollows) return
        lightText = text
        lightFollows = follows
        changed()
    }
}
