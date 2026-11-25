package com.everyonesniche.titantools.quick

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log

/**
 * The helper apps: one tiny app per action (and five widget slots), which the phone's Shortcut keys can pick, since it picks
 * apps rather than shortcuts. Each opens [TriggerActivity] and closes. Their APKs come inside TitanTools (assets/helpers),
 * and Android asks once per app before installing one.
 */
object HelperApps {
    data class Helper(val packageName: String, val label: String, val asset: String)

    val all: List<Helper> = listOf(
        Helper("com.everyonesniche.titantools.layout", "TT - Switch layout", "layout"),
        Helper("com.everyonesniche.titantools.rotate", "TT - Rotate", "rotate"),
        Helper("com.everyonesniche.titantools.flashlight", "TT - Flashlight", "flashlight"),
        Helper("com.everyonesniche.titantools.night", "TT - Night mode", "night"),
        Helper("com.everyonesniche.titantools.playpause", "TT - Play/Pause", "playpause"),
        Helper("com.everyonesniche.titantools.kill", "TT - Kill app", "kill"),
    ) + (1..WidgetList.SLOTS).map { Helper("com.everyonesniche.titantools.widget$it", "TT - Widget $it", "widget$it") }

    fun installed(context: Context, helper: Helper): Boolean = try {
        context.packageManager.getPackageInfo(helper.packageName, 0)
        true
    } catch (error: Exception) {
        false
    }

    /** Installs a helper from TitanTools' own copy. Android shows its install question; the result comes to [Result]. */
    fun install(context: Context, packageName: String) {
        val helper = all.firstOrNull { it.packageName == packageName } ?: throw IllegalArgumentException("Not a helper app")
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply { setAppPackageName(packageName) }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            context.assets.open("helpers/${helper.asset}.apk").use { input ->
                session.openWrite("${helper.asset}.apk", 0, -1).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val status = PendingIntent.getBroadcast(context, id, Intent(context, Result::class.java),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            session.commit(status.intentSender)
        }
    }

    /** The installer's answer: when Android needs the person to confirm, it opens its question. */
    class Result : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    @Suppress("DEPRECATION")
                    val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                    context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                PackageInstaller.STATUS_SUCCESS -> com.everyonesniche.titantools.core.Status.changed()
                else -> Log.w("TitanTools", "Helper install: ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}")
            }
        }
    }
}
