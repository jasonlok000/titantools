package com.everyonesniche.titantools.core

import android.content.Context
import android.provider.Settings
import com.everyonesniche.titantools.shizuku.Shell
import com.everyonesniche.titantools.shizuku.Shizuku

/** What's on the phone: the old apps, TitanKeys, and whether an old app is running right now. */
object Installed {
    const val TITANKEYS = "com.titankeys"

    fun has(context: Context, packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (error: Exception) {
        false
    }

    fun oldApps(context: Context): List<OldApps.App> = OldApps.installed { has(context, it) }

    private val runningCache = HashMap<String, Pair<Long, Boolean>>()

    /**
     * True while an old app is doing its job: NightKeys' light service runs, or the old Hold Symbols is on in Accessibility.
     * NightKeys needs Shizuku to check; without it, it counts as running, so nothing doubles up. Cached for 20 seconds.
     */
    fun oldRunning(context: Context, app: OldApps.App): Boolean {
        if (!has(context, app.packageName)) return false
        return when (app.tool) {
            OldApps.Tool.Hold -> (Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: "")
                .contains("${app.packageName}/")
            OldApps.Tool.Scale -> false
            OldApps.Tool.Light -> synchronized(runningCache) {
                val now = System.currentTimeMillis()
                runningCache[app.packageName]?.let { (at, running) -> if (now - at < 20_000) return running }
                val running = if (!Shizuku.allowed()) true else try { Shell.oldAppRunning(app) } catch (error: Exception) { true }
                runningCache[app.packageName] = now to running
                running
            }
        }
    }

    /** Forgets cached answers, as after the person comes back from uninstalling an app. */
    fun forget() = synchronized(runningCache) { runningCache.clear() }

    /** The old app blocks [tool] right now. */
    fun blocked(context: Context, tool: OldApps.Tool): Boolean {
        val app = OldApps.of(tool)
        val installed = has(context, app.packageName)
        return OldApps.blocks(tool, installed, installed && oldRunning(context, app))
    }
}
