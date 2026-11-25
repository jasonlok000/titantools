package com.everyonesniche.titantools.quick

import android.app.UiModeManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.everyonesniche.titantools.core.Prefs
import com.everyonesniche.titantools.core.Status
import com.everyonesniche.titantools.shizuku.Shell
import com.everyonesniche.titantools.shizuku.Shizuku
import java.util.concurrent.Executors

/** Night mode: Night Light, and if picked Extra dim and the dark theme, on and off together through Shizuku (NightRule). */
object NightMode {
    private val work = Executors.newSingleThreadExecutor { r -> Thread(r, "titantools-night").apply { isDaemon = true } }
    private val main = Handler(Looper.getMainLooper())

    fun active(context: Context): Boolean = Prefs.on(context, Prefs.NIGHT_ACTIVE)

    /** The Extra dim strength: the one picked, else the phone's own (read through Shizuku), else 50. */
    fun dimLevel(context: Context): Int {
        val picked = Prefs.of(context).getInt(Prefs.NIGHT_DIM_LEVEL, -1)
        if (picked in 0..100) return picked
        return (if (Shizuku.allowed()) try { Shell.getSecure(NightRule.DIM_LEVEL) } catch (e: Exception) { null } else null)
            ?.takeIf { it in 0..100 } ?: 50
    }

    fun options(context: Context): NightRule.Options {
        val prefs = Prefs.of(context)
        return NightRule.Options(prefs.getBoolean(Prefs.NIGHT_EXTRA_DIM, true), dimLevel(context), prefs.getBoolean(Prefs.NIGHT_DARK_THEME, false))
    }

    private fun themeDark(context: Context): Boolean =
        context.getSystemService(UiModeManager::class.java)?.nightMode == UiModeManager.MODE_NIGHT_YES

    /** Turns Night mode on or off, waiting a moment for Shizuku. [done] gets what to say, on the main thread. */
    fun toggle(context: Context, done: (String) -> Unit) {
        val app = context.applicationContext
        work.execute {
            var waited = 0
            while (!Shizuku.running() && waited < 2500) {
                Thread.sleep(100)
                waited += 100
            }
            val message = when {
                !Shizuku.running() -> "Start Shizuku, then try again."
                !Shizuku.allowed() -> "Allow TitanTools in Shizuku first. Open TitanTools to set it up."
                active(app) -> if (turnOff(app)) "Night mode off" else "Couldn't turn Night mode off"
                else -> if (turnOn(app)) "Night mode on" else "Couldn't turn Night mode on"
            }
            main.post {
                done(message)
                Status.changed()
                // The keyboard light may follow Night mode ("Only while typing in Night mode"): look again at once.
                com.everyonesniche.titantools.light.LightService.sync(app)
            }
        }
    }

    private fun turnOn(context: Context): Boolean = try {
        val options = options(context)
        // The phone's values from just before, to put back on the next press.
        Prefs.of(context).edit().putBoolean(Prefs.NIGHT_SAVED, true)
            .putInt(Prefs.NIGHT_SAVED_LIGHT, Shell.getSecure(NightRule.NIGHT_LIGHT) ?: -1)
            .putInt(Prefs.NIGHT_SAVED_DIM, Shell.getSecure(NightRule.EXTRA_DIM) ?: -1)
            .putInt(Prefs.NIGHT_SAVED_LEVEL, Shell.getSecure(NightRule.DIM_LEVEL) ?: -1)
            .putBoolean(Prefs.NIGHT_SAVED_THEME, themeDark(context))
            .putBoolean(Prefs.NIGHT_USED_THEME, options.darkTheme).commit()
        for ((name, value) in NightRule.onWrites(options)) Shell.putSecure(name, value)
        if (options.darkTheme) Shell.uiNight(true)
        Prefs.of(context).edit().putBoolean(Prefs.NIGHT_ACTIVE, true).commit()
        true
    } catch (error: Exception) {
        Log.w(TAG, "Couldn't turn Night mode on", error)
        false
    }

    /** Puts back the values from before Night mode went on. */
    fun turnOff(context: Context): Boolean = try {
        val prefs = Prefs.of(context)
        if (prefs.getBoolean(Prefs.NIGHT_SAVED, false)) {
            val saved = NightRule.Saved(prefs.getInt(Prefs.NIGHT_SAVED_LIGHT, -1).takeIf { it >= 0 },
                prefs.getInt(Prefs.NIGHT_SAVED_DIM, -1).takeIf { it >= 0 }, prefs.getInt(Prefs.NIGHT_SAVED_LEVEL, -1).takeIf { it >= 0 },
                prefs.getBoolean(Prefs.NIGHT_SAVED_THEME, false))
            for ((name, value) in NightRule.offWrites(saved)) Shell.putSecure(name, value)
            NightRule.offTheme(saved, prefs.getBoolean(Prefs.NIGHT_USED_THEME, false))?.let(Shell::uiNight)
        }
        prefs.edit().putBoolean(Prefs.NIGHT_ACTIVE, false).putBoolean(Prefs.NIGHT_SAVED, false).commit()
        true
    } catch (error: Exception) {
        Log.w(TAG, "Couldn't turn Night mode off", error)
        false
    }

    /** The tool switched off while Night mode is on: the phone's values go back, once Shizuku is ready. */
    fun restoreSoon(context: Context) {
        val app = context.applicationContext
        work.execute {
            if (!Prefs.on(app, Prefs.NIGHT_ON) && active(app) && Shizuku.allowed()) {
                turnOff(app)
                Status.changed()
            }
        }
    }

    private const val TAG = "TitanTools"
}
