package com.everyonesniche.titantools.quick

import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telecom.TelecomManager
import android.util.Log
import com.everyonesniche.titantools.core.Prefs
import com.everyonesniche.titantools.shizuku.Shell
import com.everyonesniche.titantools.shizuku.Shizuku
import java.util.concurrent.Executors

/**
 * Kill app: closes the app in front completely (force-stop, through Shizuku), then goes to the home screen. Never the keyboard,
 * the home screen, the system UI, Shizuku, TitanTools, or the phone app during a call (KillRule).
 */
class KillActivity : QuickActivity() {
    override fun act(done: (String?) -> Unit) {
        if (!Prefs.on(this, Prefs.KILL_ON)) return done("Kill app is off. Turn it on in TitanTools.")
        val keyboard = KillRule.keyboardPackage(Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD))
        val launchers = packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            .map { it.activityInfo.packageName }.toSet()
        val mode = getSystemService(AudioManager::class.java)?.mode
        val inCall = mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION || mode == AudioManager.MODE_RINGTONE
        val phoneApps = setOfNotNull(getSystemService(TelecomManager::class.java)?.defaultDialerPackage, "com.android.dialer",
            "com.google.android.dialer", "com.android.server.telecom", "com.android.incallui")
        work.execute {
            var waited = 0
            while (!Shizuku.running() && waited < 2500) {
                Thread.sleep(100)
                waited += 100
            }
            val message = when {
                !Shizuku.running() -> "Start Shizuku, then try again."
                !Shizuku.allowed() -> "Allow TitanTools in Shizuku first. Open TitanTools to set it up."
                else -> try {
                    val target = KillRule.frontApp(Shell.activityHistory())
                    when {
                        target == null || target in launchers -> "Nothing to close"
                        KillRule.protected(target, keyboard, launchers, inCall, phoneApps) -> "Can't close ${name(target)}"
                        else -> {
                            Shell.forceStop(target)
                            Log.i("TitanTools", "Kill app closed $target")
                            main.post { goHome() }
                            "Closed ${name(target)}"
                        }
                    }
                } catch (error: Exception) {
                    Log.w("TitanTools", "Kill app", error)
                    "Couldn't close the app"
                }
            }
            main.post { done(message) }
        }
    }

    private fun name(packageName: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
    } catch (error: Exception) {
        packageName
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private companion object {
        val work = Executors.newSingleThreadExecutor()
        val main = Handler(Looper.getMainLooper())
    }
}
