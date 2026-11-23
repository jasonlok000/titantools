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
        migrateTimeout(context)
    }

    /**
     * Old NightKeys changed keyboard_brightness_timeout and may have left it so. Once, with NightKeys no longer running, the
     * firmware's own backup goes back. Missing keys or any failure: nothing happens, and nothing waits for it.
     */
    @Synchronized
    fun migrateTimeout(context: Context) {
        val prefs = Prefs.of(context)
        if (prefs.getBoolean(Prefs.TIMEOUT_MIGRATED, false)) return
        val nightKeys = OldApps.of(OldApps.Tool.Light)
        if (Installed.oldRunning(context, nightKeys)) return
        try {
            val value = TimeoutMigration.restoreValue(Shell.readTimeout(), Shell.readTimeoutBackup())
            if (value != null) {
                val ok = Shell.writeTimeout(value)
                Log.i(TAG, "Put keyboard_brightness_timeout back to its backup $value: ${if (ok) "done" else "didn't read back"}")
            }
            prefs.edit().putBoolean(Prefs.TIMEOUT_MIGRATED, true).apply()
        } catch (error: Exception) {
            Log.w(TAG, "Couldn't check keyboard_brightness_timeout; it doesn't matter", error)
        }
    }

    private const val TAG = "TitanTools"
}
