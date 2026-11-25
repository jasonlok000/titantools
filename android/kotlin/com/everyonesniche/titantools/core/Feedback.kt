package com.everyonesniche.titantools.core

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.everyonesniche.titantools.hold.Haptic

/**
 * What an action did, said and felt (owner, 2026-11-24): a short toast ("Flashlight on", "Portrait", "Opened Maps"), and a
 * vibration in TitanKeys' families that tells on from off: Confirm when something turned on, a light tick when it turned off,
 * one firmer tick for anything else. Silent while the phone's touch feedback is off (Haptic).
 */
object Feedback {
    private val TICK = listOf(17L)
    private val BOUNDARY = listOf(30L)
    private val main = Handler(Looper.getMainLooper())

    /** On, off, or neither, read from the message's own words. */
    fun state(message: String): Boolean? = when {
        message.endsWith(" on") || message == "Playing" -> true
        message.endsWith(" off") || message == "Paused" -> false
        else -> null
    }

    fun say(context: Context, message: String?) {
        if (message.isNullOrBlank()) return
        val app = context.applicationContext
        main.post {
            Toast.makeText(app, message, Toast.LENGTH_SHORT).show()
            // A failure or a hint ("Couldn't …", "Turn it on …") gets no vibration: only things that happened do.
            if (message.startsWith("Couldn't") || message.contains("Turn it on") || message.contains("first") || message.startsWith("Can't")) return@post
            Haptic.play(app, when (state(message)) { true -> Haptic.CONFIRM; false -> TICK; null -> BOUNDARY })
        }
    }
}
