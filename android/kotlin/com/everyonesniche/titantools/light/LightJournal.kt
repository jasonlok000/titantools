package com.everyonesniche.titantools.light

import android.content.Context

/**
 * Written before the light goes on and cleared once it's off again, so a crash or a killed process is noticed and the light
 * set back to 0 the next time TitanTools runs ([LightRecovery]).
 */
object LightJournal {
    private fun prefs(context: Context) = context.getSharedPreferences("light_journal", Context.MODE_PRIVATE)

    fun lit(context: Context): Boolean = prefs(context).getBoolean("lit", false)

    fun markLit(context: Context) {
        check(prefs(context).edit().putBoolean("lit", true).commit()) { "Couldn't save the light's recovery note" }
    }

    fun markOff(context: Context) {
        prefs(context).edit().putBoolean("lit", false).commit()
    }
}
