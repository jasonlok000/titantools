package com.everyonesniche.titantools.quick

import android.app.Activity
import android.appwidget.AppWidgetHostView
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast

/**
 * Presses a widget button: shows its widget unseen, waits for it to draw its buttons (a Glance widget draws a loading layout
 * first), clicks the picked one (which fires the widget's own action), and closes. Opened by a helper app's slot ([EXTRA_SLOT]),
 * an Android shortcut or the list ([EXTRA_ENTRY]). With [EXTRA_DRY] it only says what it would press.
 */
class WidgetButtonActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private val host by lazy { WidgetButton.host(this) }
    private var view: AppWidgetHostView? = null
    private var entry: WidgetList.Entry? = null
    private var dry = false
    private var tries = 0

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        @Suppress("DEPRECATION") overridePendingTransition(0, 0)
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        if (state != null) return close(null)
        dry = intent.getBooleanExtra(EXTRA_DRY, false)
        val list = WidgetButton.all(this)
        val slot = intent.getIntExtra(EXTRA_SLOT, 0)
        val picked = if (slot > 0) WidgetList.forSlot(list, slot) else list.firstOrNull { it.id == intent.getStringExtra(EXTRA_ENTRY) }
        if (picked == null) return close(if (slot > 0) "Widget $slot is empty. Pick a button in TitanTools." else "Pick the widget again in TitanTools.")
        entry = picked
        val info = WidgetButton.info(this, picked) ?: return close("Pick the widget again in TitanTools.")
        host.startListening()
        val hostView = host.createView(applicationContext, picked.widgetId, info)
        view = hostView
        // Laid out at the size it was picked at, but not seen.
        hostView.alpha = 0f
        setContentView(FrameLayout(this).apply { addView(hostView, FrameLayout.LayoutParams(picked.width, picked.height)) })
        main.postDelayed(::attempt, 150)
    }

    /** Looks for the picked button every 100 ms, up to 6 seconds, as the widget draws. */
    private fun attempt() {
        val hostView = view ?: return close(null)
        val picked = entry ?: return close(null)
        val target = if (WidgetButton.hasPressable(hostView)) WidgetButton.find(hostView, picked.path, picked.spot) else null
        if (target != null) {
            if (dry) return close("Found ${WidgetButton.describe(target)} (${WidgetButton.pressKind(target)}). Not pressed.")
            val pressed = try { WidgetButton.press(target) } catch (error: Exception) { Log.w(TAG, "Widget button", error); false }
            Log.i(TAG, "Widget button ${picked.name}: ${if (pressed) "pressed" else "couldn't press"} (${WidgetButton.pressKind(target)})")
            // A moment for the widget's action to start before the host goes.
            val said = if (picked.slot > 0) "Widget ${picked.slot} – ${picked.app}" else picked.name
            main.postDelayed({ close(if (pressed) said else "Pick the widget again in TitanTools.") }, 600)
            return
        }
        if (++tries >= 60) return close("Pick the widget again in TitanTools.")
        main.postDelayed(::attempt, 100)
    }

    private fun close(message: String?) {
        com.everyonesniche.titantools.core.Feedback.say(this, message)
        main.removeCallbacksAndMessages(null)
        try { host.stopListening() } catch (ignored: Exception) {}
        finishAndRemoveTask()
        @Suppress("DEPRECATION") overridePendingTransition(0, 0)
    }

    companion object {
        const val EXTRA_ENTRY = "entry"
        const val EXTRA_SLOT = "slot"
        const val EXTRA_DRY = "dry"
        private const val TAG = "TitanTools"
    }
}
