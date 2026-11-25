package com.everyonesniche.titantools.light

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.everyonesniche.titantools.core.Link

/**
 * The link with TitanKeys. TitanKeys sends com.everyonesniche.titantools.action.TYPING to this package with a
 * boolean "typing" and a long "at" (System.currentTimeMillis). Only an app signed with the same key can send it: the receiver
 * needs com.everyonesniche.titantools.permission.LINK, a signature permission both apps declare.
 */
class LinkReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        if (!intent.hasExtra("typing")) return
        // "kind": "field" (a field opened) or "typing" (a key, a swipe or a touchpad touch); none from older TitanKeys.
        Link.received(context.applicationContext, intent.getBooleanExtra("typing", false), intent.getLongExtra("at", 0),
            intent.getStringExtra("kind"))
    }

    companion object {
        const val ACTION = "com.everyonesniche.titantools.action.TYPING"
    }
}
