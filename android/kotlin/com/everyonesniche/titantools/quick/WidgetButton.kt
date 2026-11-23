package com.everyonesniche.titantools.quick

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import com.everyonesniche.titantools.core.Prefs
import org.json.JSONArray
import org.json.JSONObject

/**
 * Widget buttons: buttons of any app's widgets (such as a door unlock widget that has no shortcut of its own), pressed from a
 * helper app or an Android shortcut. Titan Tools hosts each widget itself, remembers which of its views was picked, and clicks
 * that view, which fires the widget's own action. The list itself is [WidgetList].
 */
object WidgetButton {
    const val HOST_ID = 4711
    private const val LIST = "widget_buttons"

    fun host(context: Context): AppWidgetHost = TitanWidgetHost(context.applicationContext, HOST_ID)

    // The list, kept as JSON in Titan Tools' settings.

    fun all(context: Context): List<WidgetList.Entry> {
        migrate(context)
        val raw = Prefs.of(context).getString(LIST, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                WidgetList.Entry(o.getString("id"), o.optString("name"), o.getInt("widgetId"), o.optString("app"), o.optString("button"),
                    o.optString("path").ifEmpty { null }, o.optString("spot").ifEmpty { null }, o.optInt("width", 800),
                    o.optInt("height", 300), o.optInt("slot", 0))
            }
        } catch (error: Exception) {
            emptyList()
        }
    }

    fun save(context: Context, list: List<WidgetList.Entry>) {
        val array = JSONArray()
        for (e in list) array.put(JSONObject().put("id", e.id).put("name", e.name).put("widgetId", e.widgetId).put("app", e.app)
            .put("button", e.button).put("path", e.path ?: "").put("spot", e.spot ?: "").put("width", e.width).put("height", e.height)
            .put("slot", e.slot))
        Prefs.of(context).edit().putString(LIST, array.toString()).commit()
    }

    fun get(context: Context, id: String?): WidgetList.Entry? = all(context).firstOrNull { it.id == id }

    /** Build 9 kept one widget button in single settings: it becomes the list's first, in slot Widget 1. */
    private fun migrate(context: Context) {
        val prefs = Prefs.of(context)
        if (prefs.contains(LIST) || !prefs.contains(Prefs.WIDGET_ID)) return
        val app = prefs.getString(Prefs.WIDGET_APP, null) ?: "A widget"
        val button = prefs.getString(Prefs.WIDGET_BUTTON, null) ?: ""
        val entry = WidgetList.Entry("w1", WidgetList.defaultName(app, button), prefs.getInt(Prefs.WIDGET_ID, -1), app, button,
            prefs.getString(Prefs.WIDGET_PATH, null), prefs.getString(Prefs.WIDGET_SPOT, null), prefs.getInt(Prefs.WIDGET_WIDTH, 800),
            prefs.getInt(Prefs.WIDGET_HEIGHT, 300), 1)
        save(context, listOf(entry))
        prefs.edit().remove(Prefs.WIDGET_ID).remove(Prefs.WIDGET_APP).remove(Prefs.WIDGET_BUTTON).remove(Prefs.WIDGET_PATH)
            .remove(Prefs.WIDGET_SPOT).remove(Prefs.WIDGET_WIDTH).remove(Prefs.WIDGET_HEIGHT).commit()
    }

    fun newId(context: Context): String {
        val taken = all(context).map { it.id }.toSet()
        var n = 1
        while ("w$n" in taken) n++
        return "w$n"
    }

    /** Removes a button, and gives its widget back. */
    fun remove(context: Context, id: String) {
        val entry = get(context, id) ?: return
        if (all(context).none { it.id != id && it.widgetId == entry.widgetId })
            try { host(context).deleteAppWidgetId(entry.widgetId) } catch (ignored: Exception) {}
        save(context, WidgetList.remove(all(context), id))
    }

    fun info(context: Context, entry: WidgetList.Entry) = AppWidgetManager.getInstance(context).getAppWidgetInfo(entry.widgetId)

    // Finding and pressing the button.

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

    /** A view can be pressed: it's clickable, or it's an item of a list (Glance's lazy lists), which its list handles. */
    fun pressable(view: View): Boolean = view.isClickable || view.parent is AdapterView<*>

    /** The deepest view under the point (x, y in [root]'s coordinates), preferring one that can be pressed. */
    fun viewAt(root: View, x: Int, y: Int): View? {
        var best: View? = null
        var bestPressable: View? = null
        fun walk(view: View) {
            if (view.visibility != View.VISIBLE) return
            if (!bounds(root, view).contains(x, y)) return
            best = view
            if (pressable(view)) bestPressable = view
            if (view is ViewGroup) for (i in 0 until view.childCount) walk(view.getChildAt(i))
        }
        walk(root)
        return bestPressable ?: best
    }

    /** True once the widget has drawn something that can be pressed: Glance draws a loading layout first. */
    fun hasPressable(root: View): Boolean {
        if (root.width == 0) return false
        fun walk(view: View): Boolean {
            if (view !== root && view.visibility == View.VISIBLE && pressable(view)) return true
            if (view is ViewGroup) for (i in 0 until view.childCount) if (walk(view.getChildAt(i))) return true
            return false
        }
        return walk(root)
    }

    /** A view's bounds in [root]'s coordinates. */
    fun bounds(root: View, view: View): Rect {
        val a = IntArray(2)
        val b = IntArray(2)
        root.getLocationInWindow(a)
        view.getLocationInWindow(b)
        return Rect(b[0] - a[0], b[1] - a[1], b[0] - a[0] + view.width, b[1] - a[1] + view.height)
    }

    /** What to call the picked view: its description or text, else "the button you picked". */
    fun describe(view: View): String {
        view.contentDescription?.takeIf { it.isNotBlank() }?.let { return it.toString() }
        if (view is android.widget.TextView && !view.text.isNullOrBlank()) return view.text.toString()
        if (view is ViewGroup) for (i in 0 until view.childCount) {
            val found = describe(view.getChildAt(i))
            if (found != NO_NAME) return found
        }
        return NO_NAME
    }

    const val NO_NAME = "the button you picked"

    /** Finds the picked view in a freshly shown widget: by its place if that can be pressed, else by the point where it was. */
    fun find(hostView: AppWidgetHostView, path: String?, spot: String?): View? {
        path?.let { follow(hostView, it) }?.let { pressTarget(it)?.let { target -> return target } }
        val (fx, fy) = spot?.split(",")?.mapNotNull { it.toFloatOrNull() }?.takeIf { it.size == 2 }?.let { it[0] to it[1] } ?: return null
        return viewAt(hostView, (fx * hostView.width).toInt(), (fy * hostView.height).toInt())?.let(::pressTarget)
    }

    /** What would be pressed: the view itself or its nearest pressable parent. */
    fun pressTarget(view: View): View? {
        var v: View? = view
        while (v != null) {
            if (pressable(v)) return v
            v = v.parent as? View
        }
        return null
    }

    /**
     * Presses [view] the way a tap would: a list item through its list (Glance's lazy lists send their item's fill-in intent
     * that way), anything else through its own click, which sends the widget's PendingIntent.
     */
    fun press(view: View): Boolean {
        val target = pressTarget(view) ?: return false
        val list = target.parent as? AdapterView<*>
        if (list != null) {
            val position = list.getPositionForView(target)
            if (position != AdapterView.INVALID_POSITION) return list.performItemClick(target, position, list.getItemIdAtPosition(position))
        }
        return target.performClick()
    }

    /** How a picked view would be pressed, for the test row: "list item", "click", or "nothing pressable". */
    fun pressKind(view: View): String {
        val target = pressTarget(view) ?: return "nothing pressable"
        return if (target.parent is AdapterView<*>) "list item" else "click"
    }
}
