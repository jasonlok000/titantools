package com.everyonesniche.titantools.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.everyonesniche.titantools.light.LightService
import com.everyonesniche.titantools.scale.ScaleActivity

/** After a restart or an update, the keyboard light starts again if its switch is on. It waits for Shizuku by itself. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        LightService.sync(context)
        ScaleActivity.sync(context)
    }
}
