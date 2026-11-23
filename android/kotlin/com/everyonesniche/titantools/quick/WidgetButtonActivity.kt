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
import com.everyonesniche.titantools.core.Prefs

/**
 * The "Widget button" launcher entry: shows the picked widget unseen, waits for it to draw, clicks the picked button (which
 * fires the widget's own action), and closes.
 */
class WidgetButtonActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private val host by lazy { WidgetButton.host(this) }
    private var view: AppWidgetHostView? = null
    private var tries = 0

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        @Suppress("DEPRECATION") overridePendingTransition(0, 0)
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        if (state != null) return close(null)
        if (!Prefs.on(this, Prefs.WIDGET_ON)) return close("Widget button is off. Turn it on in Titan Tools.")
        val id = WidgetButton.widgetId(this)
        val info = WidgetButton.info(this) ?: return close("Pick the widget again in Titan Tools.")
        host.startListening()
        val prefs = Prefs.of(this)
        val hostView = host.createView(applicationContext, id, info)
        view = hostView
        // Laid out at the size it was picked at, but not seen.
        hostView.alpha = 0f
        setContentView(FrameLayout(this).apply {
            addView(hostView, FrameLayout.LayoutParams(prefs.getInt(Prefs.WIDGET_WIDTH, 800), prefs.getInt(Prefs.WIDGET_HEIGHT, 300)))
        })
        main.postDelayed(::attempt, 150)
    }

    /** Looks for the picked button every 100 ms, up to 3 seconds, as the widget draws. */
    private fun attempt() {
        val hostView = view ?: return close(null)
        val prefs = Prefs.of(this)
        val target = if (hostView.childCount > 0 && hostView.width > 0)
            WidgetButton.find(hostView, prefs.getString(Prefs.WIDGET_PATH, null), prefs.getString(Prefs.WIDGET_SPOT, null)) else null
        if (target != null) {
            val pressed = try { WidgetButton.press(target) } catch (error: Exception) { Log.w("TitanTools", "Widget button", error); false }
            // A moment for the widget's action to start before the host goes.
            main.postDelayed({ close(if (pressed) null else "Pick the widget again in Titan Tools.") }, 400)
            return
        }
        if (++tries >= 30) return close("Pick the widget again in Titan Tools.")
        main.postDelayed(::attempt, 100)
    }

    private fun close(message: String?) {
        if (message != null) Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        try { host.stopListening() } catch (ignored: Exception) {}
        finishAndRemoveTask()
        @Suppress("DEPRECATION") overridePendingTransition(0, 0)
    }
}
