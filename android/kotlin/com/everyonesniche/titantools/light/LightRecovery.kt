package com.everyonesniche.titantools.light

import android.content.Context
import android.util.Log
import com.everyonesniche.titantools.core.OldApps
import com.everyonesniche.titantools.core.Prefs
import com.everyonesniche.titantools.core.Installed
import com.everyonesniche.titantools.shizuku.Shell
import com.everyonesniche.titantools.shizuku.Shizuku
import java.util.concurrent.Executors

/** Puts things right that an earlier run may have left: a light left on, and old NightKeys' timeout. Never blocks anything. */
object LightRecovery {
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "titantools-recovery").apply { isDaemon = true } }

    /** Runs [check] off the main thread. */
    fun checkSoon(context: Context) {
        val app = context.applicationContext
        worker.execute { check(app) }
    }

    fun check(context: Context) {
        if (!Shizuku.allowed()) return
        // A light left on by a crash: off, unless the light service is running and owns it.
        if (LightJournal.lit(context) && LightService.instance == null) {
            try {
                Shell.light(0)
                LightJournal.markOff(context)
            } catch (error: Exception) {
                Log.w(TAG, "Couldn't turn a left-over light off", error)
            }
        }
        if (!Prefs.on(context, Prefs.LIGHT_ON) && LightService.instance == null) releaseTimeout(context)
    }

    /**
     * While the Keyboard light tool is on, Titan Tools owns the light: keyboard_brightness_timeout is 0, so the firmware never
     * lights the keys on a key press by itself (with -1 it lit them and never turned them off). The phone's own value is saved
     * in keyboard_brightness_timeout_backup first, if that has none. Missing keys or any failure: nothing happens, and nothing
     * waits for it. Never while old NightKeys runs, which owns the timeout itself.
     */
    @Synchronized
    fun takeTimeout(context: Context) {
        if (Installed.oldRunning(context, OldApps.of(OldApps.Tool.Light))) return
        try {
            val current = Shell.readTimeout()
            val writes = TimeoutMigration.takeWrites(current, Shell.readTimeoutBackup())
            for (write in writes) {
                val ok = if (write.backup) Shell.writeTimeoutBackup(write.value) else Shell.writeTimeout(write.value)
                Log.i(TAG, "${if (write.backup) "Saved the phone's key light timeout as the backup" else "Key light timeout set to"} " +
                    "${write.value}: ${if (ok) "done" else "didn't read back"}")
                if (!ok) return
            }
            if (current != null && current != TimeoutMigration.MISSING && TimeoutMigration.valid(current))
                Prefs.of(context).edit().putBoolean(Prefs.TIMEOUT_OWNED, true).putBoolean(Prefs.TIMEOUT_MIGRATED, true).commit()
        } catch (error: Exception) {
            Log.w(TAG, "Couldn't take keyboard_brightness_timeout over; the light still works", error)
        }
    }

    /**
     * With the tool off, the firmware gets its key light back: the backup goes back into keyboard_brightness_timeout. Only
     * when Titan Tools took it over, or once for what old NightKeys left (also after Titan Tools' data is cleared).
     */
    @Synchronized
    fun releaseTimeout(context: Context) {
        val prefs = Prefs.of(context)
        if (!prefs.getBoolean(Prefs.TIMEOUT_OWNED, false) && prefs.getBoolean(Prefs.TIMEOUT_MIGRATED, false)) return
        if (Installed.oldRunning(context, OldApps.of(OldApps.Tool.Light))) return
        try {
            val value = TimeoutMigration.restoreValue(Shell.readTimeout(), Shell.readTimeoutBackup())
            if (value != null) {
                val ok = Shell.writeTimeout(value)
                Log.i(TAG, "Put keyboard_brightness_timeout back to its backup $value: ${if (ok) "done" else "didn't read back"}")
                if (!ok) return
            }
            prefs.edit().putBoolean(Prefs.TIMEOUT_OWNED, false).putBoolean(Prefs.TIMEOUT_MIGRATED, true).commit()
        } catch (error: Exception) {
            Log.w(TAG, "Couldn't put keyboard_brightness_timeout back; it's tried again later", error)
        }
    }

    private const val TAG = "TitanTools"
}
