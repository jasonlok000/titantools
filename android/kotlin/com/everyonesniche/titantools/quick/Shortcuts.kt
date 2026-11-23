package com.everyonesniche.titantools.quick

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.everyonesniche.titantools.core.Prefs
import com.everyonesniche.titantools.rotate.RotateActivity

/** The launcher entries of the one-press tools, each there only while its tool is on. */
object Shortcuts {
    private val entries = listOf(
        Prefs.SCALE_ON to com.everyonesniche.titantools.scale.ScaleActivity::class.java,
        Prefs.ROTATION_ON to RotateActivity::class.java,
        Prefs.FLASH_ON to FlashlightActivity::class.java,
        Prefs.NIGHT_ON to NightModeActivity::class.java,
        Prefs.PLAY_ON to PlayPauseActivity::class.java,
        Prefs.WIDGET_ON to WidgetButtonActivity::class.java,
    )

    fun sync(context: Context) {
        for ((key, activity) in entries) {
            val state = if (Prefs.on(context, key)) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            val component = ComponentName(context, activity)
            if (context.packageManager.getComponentEnabledSetting(component) != state)
                context.packageManager.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
        }
    }
}
