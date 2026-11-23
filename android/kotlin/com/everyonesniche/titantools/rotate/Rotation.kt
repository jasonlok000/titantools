package com.everyonesniche.titantools.rotate

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Display
import com.everyonesniche.titantools.core.Prefs
import com.everyonesniche.titantools.core.Status
import com.everyonesniche.titantools.shizuku.Shell
import com.everyonesniche.titantools.shizuku.Shizuku
import java.util.concurrent.Executors

/**
 * Rotation: flips the screen between portrait and landscape, locked, from the "Rotate" launcher entry (which the side
 * button can open). The phone's own auto-rotate and rotation are saved when the tool is turned on and go
 * back when it's off.
 */
object Rotation {
    private val work = Executors.newSingleThreadExecutor { r -> Thread(r, "titantools-rotation").apply { isDaemon = true } }
    private val main = Handler(Looper.getMainLooper())

    /** The screen's rotation now (Surface.ROTATION_0..3). */
    fun current(context: Context): Int =
        context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: 0

    fun label(context: Context): String = RotationRule.label(current(context))

    private fun readSystem(context: Context, name: String): Int? = try {
        Settings.System.getInt(context.contentResolver, name)
    } catch (error: Exception) {
        null
    }

    /** Turning the tool on or off: saves the phone's own values, or puts them back. Shows the launcher entry only while on. */
    fun switched(context: Context, on: Boolean) {
        val app = context.applicationContext
        val prefs = Prefs.of(app)
        if (on && !prefs.getBoolean(Prefs.ROTATION_SAVED, false)) {
            val editor = prefs.edit().putBoolean(Prefs.ROTATION_SAVED, true)
            readSystem(app, Settings.System.ACCELEROMETER_ROTATION)?.let { editor.putInt(Prefs.ROTATION_SAVED_AUTO, it) }
            readSystem(app, Settings.System.USER_ROTATION)?.let { editor.putInt(Prefs.ROTATION_SAVED_ROTATION, it) }
            editor.commit()
        }
        sync(app)
        if (!on) work.execute { restore(app) }
    }

    /** With the tool off: the phone's own auto-rotate and rotation go back, once Shizuku is ready. Kept to try again if not. */
    fun restore(context: Context) {
        val prefs = Prefs.of(context)
        if (Prefs.on(context, Prefs.ROTATION_ON) || !prefs.getBoolean(Prefs.ROTATION_SAVED, false) || !Shizuku.allowed()) return
        val saved = RotationRule.Saved(
            prefs.getInt(Prefs.ROTATION_SAVED_AUTO, -1).takeIf { it >= 0 },
            prefs.getInt(Prefs.ROTATION_SAVED_ROTATION, -1).takeIf { it >= 0 },
        )
        try {
            Shell.fixedToUserRotation(false)
            val writes = RotationRule.restoreWrites(saved, readSystem(context, Settings.System.ACCELEROMETER_ROTATION),
                readSystem(context, Settings.System.USER_ROTATION))
            for ((name, value) in writes) {
                Shell.putRotationSetting(name, value)
                Log.i(TAG, "Rotation off: $name back to $value")
            }
            prefs.edit().putBoolean(Prefs.ROTATION_SAVED, false).remove(Prefs.ROTATION_SAVED_AUTO).remove(Prefs.ROTATION_SAVED_ROTATION).commit()
        } catch (error: Exception) {
            Log.w(TAG, "Couldn't put the phone's rotation back; it's tried again later", error)
        }
    }

    fun restoreSoon(context: Context) {
        val app = context.applicationContext
        work.execute { restore(app) }
    }

    /** The "Rotate" launcher entry exists only while the tool is on. */
    fun sync(context: Context) = com.everyonesniche.titantools.quick.Shortcuts.sync(context)

    /**
     * Flips between portrait and landscape, locked. Waits a moment for Shizuku, whose binder arrives as the process starts.
     * [done] gets "Portrait", "Landscape" or what went wrong, on the main thread.
     */
    fun flip(context: Context, done: (String) -> Unit) {
        val app = context.applicationContext
        if (!Prefs.on(app, Prefs.ROTATION_ON)) {
            done("Rotation is off. Turn it on in Titan Tools.")
            return
        }
        work.execute {
            var waited = 0
            while (!Shizuku.running() && waited < 2500) {
                Thread.sleep(100)
                waited += 100
            }
            val message = when {
                !Shizuku.running() -> "Start Shizuku, then try again."
                !Shizuku.allowed() -> "Allow Titan Tools in Shizuku first. Open Titan Tools to set it up."
                else -> try {
                    // The rotation asked for, not the screen's: an app that insists on portrait could hold the screen upright.
                    val chosen = readSystem(app, Settings.System.USER_ROTATION)
                        .takeIf { readSystem(app, Settings.System.ACCELEROMETER_ROTATION) == 0 } ?: current(app)
                    val target = RotationRule.next(chosen)
                    Shell.lockRotation(target)
                    RotationRule.label(target)
                } catch (error: Exception) {
                    Log.w(TAG, "Couldn't rotate", error)
                    "Couldn't rotate: ${error.message ?: error.javaClass.simpleName}".take(120)
                }
            }
            main.post {
                done(message)
                Status.changed()
            }
        }
    }

    private const val TAG = "TitanTools"
}
