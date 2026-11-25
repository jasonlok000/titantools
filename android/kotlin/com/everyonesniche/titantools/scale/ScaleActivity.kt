package com.everyonesniche.titantools.scale

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import com.everyonesniche.titantools.core.Prefs
import com.everyonesniche.titantools.shizuku.Shell
import com.everyonesniche.titantools.shizuku.Shizuku
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Scale Toggle, as in 1.2.0: opening it is the action. It switches between phone mode (300 DPI) and tablet mode (286 DPI),
 * shows "Phone mode" or "Tablet mode" and closes. It has its own launcher entry while the tool is on, so the side button's
 * long press can open it.
 */
class ScaleActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private var finished = false
    private var stopListening: (() -> Unit)? = null
    private val waitForShizuku = Runnable { complete("Start Shizuku, then try again.") }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        @Suppress("DEPRECATION") overridePendingTransition(0, 0)
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        window.setLayout(1, 1)
        setContentView(View(this))
        if (state != null || !busy.compareAndSet(false, true)) {
            close()
            return
        }
        if (!Prefs.on(this, Prefs.SCALE_ON)) {
            complete("Scale Toggle is off. Turn it on in Titan Tools.")
            return
        }
        if (Shizuku.running()) start()
        else {
            // Shizuku hands its binder over as this process starts: give it a moment.
            stopListening = Shizuku.listen { if (Shizuku.running()) start() }
            main.postDelayed(waitForShizuku, 2500)
        }
    }

    private var started = false

    private fun start() {
        if (started || finished) return
        started = true
        main.removeCallbacks(waitForShizuku)
        stopListening?.invoke()
        if (!Shizuku.allowed()) {
            complete("Allow Titan Tools in Shizuku first. Open Titan Tools to set it up.")
            return
        }
        work.execute {
            val message = try {
                val prefs = getSharedPreferences("scale_state", MODE_PRIVATE)
                val store = object : ScaleController.Store {
                    override fun lastWidth() = prefs.getInt("last_successful_width", 0)
                    override fun original(density: Int) {
                        if (!prefs.contains("original_density")) prefs.edit().putInt("original_density", density).commit()
                    }
                    override fun save(width: Int, density: Int) {
                        check(prefs.edit().putInt("last_successful_width", width).putInt("last_successful_density", density).commit())
                    }
                }
                val target = ScaleController({ args -> Shell.wm(*args) }, store, {}, {}, presets(this)).toggle()
                val sizes = com.everyonesniche.titantools.core.Prefs.of(this)
                if (target == ScaleMath.COMPACT) "Tablet layout (${sizes.getInt(ScaleSettings.TABLET_DPI, 0).takeIf { it > 0 } ?: ScaleMath.TABLET_DENSITY} DPI)"
                else "Phone layout (${sizes.getInt(ScaleSettings.PHONE_DPI, 0).takeIf { it > 0 } ?: ScaleMath.PHONE_DENSITY} DPI)"
            } catch (error: ScaleController.Failure) {
                Log.e("TitanTools", "Scale Toggle failed at ${error.code}", error)
                "Couldn't switch: ${error.cause?.message ?: error.code}".take(120)
            } catch (error: Exception) {
                Log.e("TitanTools", "Scale Toggle failed", error)
                "Couldn't switch: ${error.message ?: error.javaClass.simpleName}".take(120)
            }
            main.post { complete(message) }
        }
    }

    private fun complete(message: String?) {
        if (finished) return
        finished = true
        main.removeCallbacks(waitForShizuku)
        stopListening?.invoke()
        com.everyonesniche.titantools.core.Feedback.say(this, message)
        busy.set(false)
        close()
    }

    private fun close() {
        finishAndRemoveTask()
        @Suppress("DEPRECATION") overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        main.removeCallbacks(waitForShizuku)
        stopListening?.invoke()
        if (!finished) {
            finished = true
            if (!started) busy.set(false)
        }
        super.onDestroy()
    }

    companion object {
        private val busy = AtomicBoolean(false)
        private val work = Executors.newSingleThreadExecutor()

        /** The DPI for each mode: anything unreadable or invalid falls back to the defaults. */
        fun presets(context: Context): ScaleMath.Presets = try {
            val prefs = Prefs.of(context)
            ScaleMath.Presets(prefs.getInt(Prefs.PHONE_DPI, 0), prefs.getInt(Prefs.TABLET_DPI, 0))
        } catch (error: RuntimeException) {
            ScaleMath.Presets.DEFAULTS
        }

        /** Shows the "Scale Toggle" launcher entry, which the side button can open, only while the tool is on. */
        fun sync(context: Context) {
            // No launcher entry any more (the helper apps replace it): an entry an earlier build turned off comes back.
            val state = PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
            val component = ComponentName(context, ScaleActivity::class.java)
            if (context.packageManager.getComponentEnabledSetting(component) != state)
                context.packageManager.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
        }

        /** The density now, as "phone" or "tablet", or null when it can't be read. */
        fun currentMode(): String? = try {
            val size = WmOutput.size(Shell.wm("size"))
            val density = WmOutput.density(Shell.wm("density"))
            if (ScaleMath.widthDp(size[0], size[1], density) >= ScaleSettings.TABLET_LAYOUT_DP) "tablet" else "phone"
        } catch (error: Exception) {
            null
        }
    }
}
