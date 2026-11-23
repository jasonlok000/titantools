package com.everyonesniche.titantools.quick

import com.everyonesniche.titantools.core.Prefs

/** The "Night mode" launcher entry: each press turns Night mode on or off. */
class NightModeActivity : QuickActivity() {
    override fun act(done: (String?) -> Unit) {
        if (!Prefs.on(this, Prefs.NIGHT_ON)) return done("Night mode is off. Turn it on in Titan Tools.")
        NightMode.toggle(this) { done(it) }
    }
}
