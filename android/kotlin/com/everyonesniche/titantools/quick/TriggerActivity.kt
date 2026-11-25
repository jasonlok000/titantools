package com.everyonesniche.titantools.quick

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.everyonesniche.titantools.rotate.RotateActivity
import com.everyonesniche.titantools.scale.ScaleActivity

/**
 * Where the helper apps (Rotate, Flashlight, Widget 1 …) and TitanTools' Android shortcuts land. Only apps signed with the
 * same key can open it (com.everyonesniche.titantools.permission.TRIGGER). It passes the action on and closes, with no screen.
 */
class TriggerActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        @Suppress("DEPRECATION") overridePendingTransition(0, 0)
        if (state == null) target(this, intent)?.let { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        finish()
        @Suppress("DEPRECATION") overridePendingTransition(0, 0)
    }

    companion object {
        const val EXTRA_ACTION = "action"
        const val EXTRA_SLOT = "slot"
        const val EXTRA_ENTRY = "entry"

        /** The one-press actions, by the names the helper apps and shortcuts use, and their labels. */
        val ACTIONS = linkedMapOf("layout" to "Switch layout", "rotate" to "Rotate", "flashlight" to "Flashlight",
            "night" to "Night mode", "playpause" to "Play/Pause", "kill" to "Kill app")

        fun target(context: Context, intent: Intent): Intent? = when (intent.getStringExtra(EXTRA_ACTION)) {
            "layout" -> Intent(context, ScaleActivity::class.java)
            "rotate" -> Intent(context, RotateActivity::class.java)
            "flashlight" -> Intent(context, FlashlightActivity::class.java)
            "night" -> Intent(context, NightModeActivity::class.java)
            "playpause" -> Intent(context, PlayPauseActivity::class.java)
            "kill" -> Intent(context, KillActivity::class.java)
            "widget" -> Intent(context, WidgetButtonActivity::class.java)
                .putExtra(WidgetButtonActivity.EXTRA_SLOT, intent.getIntExtra(EXTRA_SLOT, 0))
                .putExtra(WidgetButtonActivity.EXTRA_ENTRY, intent.getStringExtra(EXTRA_ENTRY))
            else -> null
        }

        /** The intent a shortcut opens for [action], or for widget button [entry]. */
        fun shortcutIntent(context: Context, action: String, entry: String? = null): Intent =
            Intent(context, TriggerActivity::class.java).setAction(Intent.ACTION_VIEW).putExtra(EXTRA_ACTION, action)
                .apply { if (entry != null) putExtra(EXTRA_ENTRY, entry) }
    }
}
