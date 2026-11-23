package com.everyonesniche.titantools.quick

import android.app.Activity
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.everyonesniche.titantools.core.Status

/**
 * Sets up a widget button: pick a widget with Android's own picker, let it set itself up if it asks to, then show it here and
 * tap the button wanted. A cover over the widget takes that tap, so it only records the button and never presses it. Widgets
 * made with Glance draw a loading layout first; taps wait until the real one has come.
 * Extras: [EXTRA_ENTRY] to pick another button of an existing one; none to add a new one.
 */
class WidgetSetupActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private var pendingId = -1
    private var entryId: String? = null
    private var picked: View? = null
    private lateinit var result: TextView
    private lateinit var done: Button
    private var hostView: AppWidgetHostView? = null
    private var loaded = false
    private val host by lazy { WidgetButton.host(this) }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.decorView.setBackgroundColor(Color.BLACK)
        entryId = intent.getStringExtra(EXTRA_ENTRY)
        if (state != null) {
            pendingId = state.getInt("pending", -1)
            return
        }
        val entry = WidgetButton.get(this, entryId)
        if (entry != null && WidgetButton.info(this, entry) != null) show(entry.widgetId) else pick()
    }

    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        out.putInt("pending", pendingId)
    }

    private fun pick() {
        pendingId = host.allocateAppWidgetId()
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingId), PICK)
        } catch (error: Exception) {
            host.deleteAppWidgetId(pendingId)
            message("This phone has no widget picker.")
        }
    }

    @Deprecated("Activity results")
    override fun onActivityResult(request: Int, code: Int, data: Intent?) {
        @Suppress("DEPRECATION") super.onActivityResult(request, code, data)
        val id = pendingId
        if (code != RESULT_OK) {
            if (id >= 0) host.deleteAppWidgetId(id)
            finish()
            return
        }
        val info = AppWidgetManager.getInstance(this).getAppWidgetInfo(id)
        when {
            info == null && request == PICK -> {
                // The picker didn't bind it: ask Android once (the user taps Allow).
                val provider = data?.getParcelableExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, ComponentName::class.java)
                if (provider == null) { host.deleteAppWidgetId(id); message("Couldn't add that widget."); return }
                @Suppress("DEPRECATION")
                startActivityForResult(Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider), BIND)
            }
            info == null -> { host.deleteAppWidgetId(id); message("Couldn't add that widget.") }
            info.configure != null && request != CONFIGURE -> try {
                host.startAppWidgetConfigureActivityForResult(this, id, 0, CONFIGURE, null)
            } catch (error: Exception) {
                show(id)
            }
            else -> show(id)
        }
    }

    private fun dp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics).toInt()

    private fun text(value: String, size: Float, colour: Int) = TextView(this).apply {
        text = value
        setTextColor(colour)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        setPadding(0, dp(8f), 0, dp(8f))
    }

    private fun message(value: String) {
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24f), dp(48f), dp(24f), dp(24f))
            addView(text(value, 18f, CREAM))
            addView(Button(this@WidgetSetupActivity).apply { text = "Close"; setOnClickListener { finish() } })
        })
    }

    /** Shows the widget under a cover that records the tapped button. */
    private fun show(id: Int) {
        val manager = AppWidgetManager.getInstance(this)
        val info = manager.getAppWidgetInfo(id) ?: return message("Couldn't add that widget.")
        host.startListening()
        val width = resources.displayMetrics.widthPixels - dp(48f)
        // Tall enough for the whole widget (1Valet's fob button sits below its name), at most 60% of the screen.
        val height = maxOf(info.minHeight, dp(320f)).coerceAtMost(resources.displayMetrics.heightPixels * 3 / 5)
        val view = host.createView(applicationContext, id, info)
        hostView = view
        val frame = FrameLayout(this)
        frame.addView(view, FrameLayout.LayoutParams(width, height))
        val cover = View(this)
        cover.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP && loaded) {
                val target = WidgetButton.viewAt(view, event.x.toInt(), event.y.toInt())?.let { WidgetButton.pressTarget(it) ?: it }
                if (target != null) {
                    picked = target
                    target.isPressed = false
                    result.text = if (WidgetButton.pressable(target)) "Picked: ${WidgetButton.describe(target)}"
                        else "That part of the widget doesn't do anything. Tap a button."
                    done.isEnabled = WidgetButton.pressable(target)
                }
            }
            true
        }
        frame.addView(cover, FrameLayout.LayoutParams(width, height))
        result = text("Loading the widget…", 16f, MUTED)
        done = Button(this).apply {
            text = "Done"
            isEnabled = false
            setOnClickListener { save(id, width, height) }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24f), dp(48f), dp(24f), dp(24f))
            addView(text("Pick the button", 26f, ORANGE))
            addView(frame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(16f)
            })
            addView(result)
            addView(done)
            addView(Button(this@WidgetSetupActivity).apply {
                text = "Pick another widget"
                setOnClickListener { host.stopListening(); pick() }
            })
        })
        waitForWidget(view, 0)
    }

    /** Waits for something pressable, up to 8 seconds: a Glance widget's real layout comes after its loading one. */
    private fun waitForWidget(view: AppWidgetHostView, tries: Int) {
        if (WidgetButton.hasPressable(view) || tries >= 40) {
            loaded = true
            result.text = if (WidgetButton.hasPressable(view)) "Tap the button you want. It won't be pressed now."
                else "This widget has no buttons Titan Tools can find. Tap the part you want anyway."
            return
        }
        main.postDelayed({ waitForWidget(view, tries + 1) }, 200)
    }

    private fun save(id: Int, width: Int, height: Int) {
        val view = hostView ?: return
        val target = picked ?: return
        val r = WidgetButton.bounds(view, target)
        val info = AppWidgetManager.getInstance(this).getAppWidgetInfo(id)
        val app = info?.provider?.packageName?.let {
            try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(it, 0)).toString() } catch (e: Exception) { it }
        } ?: "A widget"
        val button = WidgetButton.describe(target)
        val list = WidgetButton.all(this)
        val old = list.firstOrNull { it.id == entryId }
        // A button that changed widget gives the old widget back, unless another button uses it.
        if (old != null && old.widgetId != id && list.none { it.id != old.id && it.widgetId == old.widgetId })
            try { host.deleteAppWidgetId(old.widgetId) } catch (ignored: Exception) {}
        val freeSlot = (1..WidgetList.SLOTS).firstOrNull { slot -> list.none { it.slot == slot } } ?: 0
        val entry = WidgetList.Entry(old?.id ?: WidgetButton.newId(this), old?.name ?: WidgetList.defaultName(app, button), id, app, button,
            WidgetButton.path(view, target), "${r.exactCenterX() / width},${r.exactCenterY() / height}", width, height, old?.slot ?: freeSlot)
        WidgetButton.save(this, WidgetList.upsert(list, entry))
        Status.changed()
        finish()
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        try { host.stopListening() } catch (ignored: Exception) {}
        super.onDestroy()
    }

    companion object {
        const val EXTRA_ENTRY = "entry"
        private const val PICK = 1
        private const val BIND = 2
        private const val CONFIGURE = 3
        private val ORANGE = Color.parseColor("#EF804E")
        private val CREAM = Color.parseColor("#FFF1E6")
        private val MUTED = Color.parseColor("#99918A")
    }
}
