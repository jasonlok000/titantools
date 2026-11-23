package com.everyonesniche.titantools.light

import android.content.Context
import android.util.Log
import com.everyonesniche.titantools.core.Installed
import com.everyonesniche.titantools.core.OldApps
import com.everyonesniche.titantools.core.Prefs
import com.everyonesniche.titantools.shizuku.Shell
import com.everyonesniche.titantools.shizuku.Shizuku
import java.util.concurrent.Executors

/**
 * Owns the firmware's key light settings while the Keyboard light tool is on, and gives them back when it's off (VendorKeys).
 * Also puts right what an earlier run left: a light left on, and old NightKeys' timeout. Never blocks anything.
 */
object LightRecovery {
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "titantools-recovery").apply { isDaemon = true } }

    /** Runs [check] off the main thread. */
    fun checkSoon(context: Context) {
        val app = context.applicationContext
        worker.execute { check(app) }
    }

    /** With the tool off and its service gone: the firmware's settings go back, and a light left on goes off. */
    fun check(context: Context) {
        if (!Shizuku.allowed()) return
        if (Prefs.on(context, Prefs.LIGHT_ON) || LightService.instance != null) return
        release(context)
        if (LightJournal.lit(context)) {
            try {
                Shell.light(0)
                LightJournal.markOff(context)
            } catch (error: Exception) {
                Log.w(TAG, "Couldn't turn a left-over light off", error)
            }
        }
    }

    /**
     * Makes the firmware keep the light at [level] when [lit] and off otherwise: saves the phone's own values once, then sets
     * the timeout to -1 and the stored level. A level someone set in the phone's own settings meanwhile is adopted as Titan
     * Tools' brightness rather than overwritten (VendorKeys.adopt). Returns the level to use: [level], or the adopted one.
     * Never while old NightKeys runs, which owns the timeout itself.
     */
    @Synchronized
    fun own(context: Context, lit: Boolean, level: Int): Int {
        if (Installed.oldRunning(context, OldApps.of(OldApps.Tool.Light))) return level
        var use = level
        try {
            val timeout = Shell.readVendor(VendorKeys.TIMEOUT)
            var led = Shell.readVendor(VendorKeys.LED)
            val prefs = Prefs.of(context)
            if (!prefs.getBoolean(Prefs.VENDOR_SAVED, false)) {
                val saved = VendorKeys.toSave(timeout, Shell.readVendor(VendorKeys.TIMEOUT_BACKUP), led)
                // Saved before the first change, so a crash right after can still put them back.
                check(prefs.edit().putBoolean(Prefs.VENDOR_SAVED, true).remove(Prefs.LED_WRITTEN)
                    .putString(Prefs.SAVED_TIMEOUT, saved.timeout).putString(Prefs.SAVED_LED, saved.led)
                    .putBoolean(Prefs.TIMEOUT_MIGRATED, true).commit()) { "Couldn't save the phone's key light settings" }
                Log.i(TAG, "Saved the phone's key light settings: timeout ${saved.timeout}, level ${saved.led}")
            } else {
                // Builds before version code 5 didn't note what they wrote: then it was the level, or 0.
                VendorKeys.adopt(led, prefs.getString(Prefs.LED_WRITTEN, null) ?: level.toString())?.let { adopted ->
                    Log.i(TAG, "The phone's own key light level changed to $adopted: Titan Tools uses it too")
                    Prefs.setLevel(context, adopted)
                    prefs.edit().putString(Prefs.LED_WRITTEN, adopted.toString()).commit()
                    use = adopted
                    com.everyonesniche.titantools.core.Status.changed()
                }
            }
            for (write in VendorKeys.ownWrites(timeout, led, lit, use)) {
                val done = Shell.writeVendor(write.key, write.value)
                Log.i(TAG, "${write.key} = ${write.value}: ${if (done) "done" else "didn't read back"}")
                if (write.key == VendorKeys.LED && done) {
                    led = write.value
                    prefs.edit().putString(Prefs.LED_WRITTEN, write.value).commit()
                }
            }
            // Already right, after a restart: still note it as Titan Tools' own value.
            if (prefs.getString(Prefs.LED_WRITTEN, null) == null && VendorKeys.validLed(led) && led!!.trim() == (if (lit) use.toString() else "0"))
                prefs.edit().putString(Prefs.LED_WRITTEN, led.trim()).commit()
        } catch (error: Exception) {
            Log.w(TAG, "Couldn't set the firmware's key light; the direct writes still work", error)
        }
        return use
    }

    /**
     * The tool is off: the phone's own key light settings go back, and the firmware is in charge again. Before Titan Tools
     * ever owned them, once: what old NightKeys left in the timeout goes back to the firmware's own backup.
     */
    @Synchronized
    fun release(context: Context) {
        val prefs = Prefs.of(context)
        if (Installed.oldRunning(context, OldApps.of(OldApps.Tool.Light))) return
        try {
            val timeout = Shell.readVendor(VendorKeys.TIMEOUT)
            if (prefs.getBoolean(Prefs.VENDOR_SAVED, false)) {
                val saved = VendorKeys.Saved(prefs.getString(Prefs.SAVED_TIMEOUT, null), prefs.getString(Prefs.SAVED_LED, null))
                var ok = true
                for (write in VendorKeys.restoreWrites(saved, timeout, Shell.readVendor(VendorKeys.LED))) {
                    val done = Shell.writeVendor(write.key, write.value)
                    Log.i(TAG, "Put ${write.key} back to ${write.value}: ${if (done) "done" else "didn't read back"}")
                    ok = ok && done
                }
                if (ok) prefs.edit().putBoolean(Prefs.VENDOR_SAVED, false).remove(Prefs.SAVED_TIMEOUT).remove(Prefs.SAVED_LED)
                    .remove(Prefs.LED_WRITTEN).commit()
            } else if (!prefs.getBoolean(Prefs.TIMEOUT_MIGRATED, false)) {
                VendorKeys.legacyRestore(timeout, Shell.readVendor(VendorKeys.TIMEOUT_BACKUP))?.let {
                    val done = Shell.writeVendor(VendorKeys.TIMEOUT, it)
                    Log.i(TAG, "Put keyboard_brightness_timeout back to its backup $it: ${if (done) "done" else "didn't read back"}")
                }
                prefs.edit().putBoolean(Prefs.TIMEOUT_MIGRATED, true).commit()
            }
        } catch (error: Exception) {
            Log.w(TAG, "Couldn't put the phone's key light settings back; it's tried again later", error)
        }
    }

    private const val TAG = "TitanTools"
}
