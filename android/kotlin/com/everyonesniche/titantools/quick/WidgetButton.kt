package com.everyonesniche.titantools.quick

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import com.everyonesniche.titantools.core.Prefs

/**
 * Widget button: one button of any app's widget (such as a door unlock widget that has no shortcut of its own), pressed from
 * a launcher entry. Titan Tools hosts the widget itself, remembers which of its views was picked, and clicks that view, which
 * fires the widget's own action. One widget button for now.
 */
object WidgetButton {
    const val HOST_ID = 4711

    fun host(context: Context) = AppWidgetHost(context.applicationContext, HOST_ID)

    fun widgetId(context: Context): Int = Prefs.of(context).getInt(Prefs.WIDGET_ID, -1)

    fun appName(context: Context): String? = Prefs.of(context).getString(Prefs.WIDGET_APP, null)

    fun buttonName(context: Context): String? = Prefs.of(context).getString(Prefs.WIDGET_BUTTON, null)

    /** The picked widget, still bound to Titan Tools, or null. */
    fun info(context: Context) = widgetId(context).takeIf { it >= 0 }?.let { AppWidgetManager.getInstance(context).getAppWidgetInfo(it) }

    /** Forgets the widget, and gives its id back. */
    fun forget(context: Context) {
        val id = widgetId(context)
        if (id >= 0) try { host(context).deleteAppWidgetId(id) } catch (ignored: Exception) {}
        Prefs.of(context).edit().remove(Prefs.WIDGET_ID).remove(Prefs.WIDGET_APP).remove(Prefs.WIDGET_BUTTON)
            .remove(Prefs.WIDGET_PATH).remove(Prefs.WIDGET_SPOT).commit()
    }

    /** A view's place in the widget: child indexes from the host view down, as "0/2/1". */
    fun path(root: View, target: View): String? {
        val steps = mutableListOf<Int>()
        var view = target
        while (view !== root) {
            val parent = view.parent as? ViewGroup ?: return null
            steps += parent.indexOfChild(view)
            view = parent
        }
        return steps.reversed().joinToString("/")
    }

    fun follow(root: View, path: String): View? {
        var view = root
        if (path.isEmpty()) return view
        for (step in path.split("/")) {
            val index = step.toIntOrNull() ?: return null
            view = (view as? ViewGroup)?.getChildAt(index) ?: return null
        }
        return view
    }

    /** The deepest view under the point (x, y in [root]'s coordinates), preferring one that can be clicked. */
    fun viewAt(root: View, x: Int, y: Int): View? {
        var best: View? = null
        var bestClickable: View? = null
        fun walk(view: View) {
            if (view.visibility != View.VISIBLE) return
            val r = bounds(root, view)
            if (!r.contains(x, y)) return
            best = view
            if (view.isClickable) bestClickable = view
            if (view is ViewGroup) for (i in 0 until view.childCount) walk(view.getChildAt(i))
        }
        walk(root)
        return bestClickable ?: best
    }

    /** A view's bounds in [root]'s coordinates. */
    fun bounds(root: View, view: View): Rect {
        val a = IntArray(2)
        val b = IntArray(2)
        root.getLocationInWindow(a)
        view.getLocationInWindow(b)
        return Rect(b[0] - a[0], b[1] - a[1], b[0] - a[0] + view.width, b[1] - a[1] + view.height)
    }

    /** What to call the picked view on the Shortcuts screen: its description or text, else "the button you picked". */
    fun describe(view: View): String {
        view.contentDescription?.takeIf { it.isNotBlank() }?.let { return it.toString() }
        if (view is android.widget.TextView && !view.text.isNullOrBlank()) return view.text.toString()
        if (view is ViewGroup) for (i in 0 until view.childCount) {
            val child = view.getChildAt(i)
            child.contentDescription?.takeIf { it.isNotBlank() }?.let { return it.toString() }
            if (child is android.widget.TextView && !child.text.isNullOrBlank()) return child.text.toString()
        }
        return "the button you picked"
    }

    /** Finds the picked view in a freshly shown widget: by its place, else by the point at the middle of where it was. */
    fun find(hostView: AppWidgetHostView, path: String?, spot: String?): View? {
        path?.let { follow(hostView, it) }?.let { if (it.isClickable || it !is ViewGroup) return it }
        val (fx, fy) = spot?.split(",")?.mapNotNull { it.toFloatOrNull() }?.takeIf { it.size == 2 }?.let { it[0] to it[1] } ?: return null
        return viewAt(hostView, (fx * hostView.width).toInt(), (fy * hostView.height).toInt())
    }

    /** Clicks [view]: its own click first, else the nearest clickable parent. */
    fun press(view: View): Boolean {
        var v: View? = view
        while (v != null) {
            if (v.isClickable && v.performClick()) return true
            v = v.parent as? View
        }
        return false
    }
}
