package com.everyonesniche.titantools.quick

import com.everyonesniche.titantools.core.Prefs

/** The "Flashlight" launcher entry: each press turns the flashlight on or off. */
class FlashlightActivity : QuickActivity() {
    override fun act(done: (String?) -> Unit) {
        if (!Prefs.on(this, Prefs.FLASH_ON)) return done("Flashlight is off. Turn it on in TitanTools.")
        Torch.toggle(this) { done(it) }
    }
}
