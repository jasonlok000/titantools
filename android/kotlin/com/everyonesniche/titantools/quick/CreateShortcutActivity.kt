package com.everyonesniche.titantools.quick

import android.app.Activity
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Bundle
import android.util.TypedValue
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.everyonesniche.titantools.res.R

/**
 * Titan Tools' Android shortcuts, for the places that take one (such as some Shortcut keys slots): pick an action or a widget
 * button, and the shortcut opens it through [TriggerActivity].
 */
class CreateShortcutActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24f), dp(40f), dp(24f), dp(24f))
            setBackgroundColor(Color.BLACK)
        }
        list.addView(row("Titan Tools shortcut", 24f, ORANGE, null))
        for ((action, label) in TriggerActivity.ACTIONS) list.addView(row(label, 20f, CREAM) { done(action, null, label, ICONS[action]!!) })
        for (entry in WidgetButton.all(this)) list.addView(row(entry.name, 20f, CREAM) { done("widget", entry.id, entry.name, R.mipmap.widget_icon) })
        setContentView(ScrollView(this).apply { setBackgroundColor(Color.BLACK); addView(list) })
    }

    private fun dp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics).toInt()

    private fun row(label: String, size: Float, colour: Int, onPress: (() -> Unit)?) = TextView(this).apply {
        text = label
        setTextColor(colour)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        setPadding(0, dp(14f), 0, dp(14f))
        if (onPress != null) setOnClickListener { onPress() }
    }

    private fun done(action: String, entry: String?, label: String, icon: Int) {
        val info = ShortcutInfo.Builder(this, "titantools-$action-${entry ?: "main"}")
            .setShortLabel(label)
            .setIcon(Icon.createWithResource(this, icon))
            .setIntent(TriggerActivity.shortcutIntent(this, action, entry))
            .build()
        setResult(RESULT_OK, getSystemService(ShortcutManager::class.java).createShortcutResultIntent(info))
        finish()
    }

    companion object {
        private val ORANGE = Color.parseColor("#EF804E")
        private val CREAM = Color.parseColor("#FFF1E6")
        private val ICONS = mapOf("layout" to R.mipmap.scale_toggle_icon, "rotate" to R.mipmap.rotate_icon,
            "flashlight" to R.mipmap.flashlight_icon, "night" to R.mipmap.night_icon, "playpause" to R.mipmap.play_icon,
            "kill" to R.mipmap.kill_icon)
    }
}
