package com.vandam.ink

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.ViewTreeObserver
import com.everyonesniche.titantools.core.Installed
import com.everyonesniche.titantools.core.Link
import com.everyonesniche.titantools.light.LightLevels
import com.everyonesniche.titantools.light.LightRule
import com.everyonesniche.titantools.core.OldApps
import com.everyonesniche.titantools.core.Prefs
import com.everyonesniche.titantools.core.Status
import com.everyonesniche.titantools.hold.HoldPause
import com.everyonesniche.titantools.hold.HoldService
import com.everyonesniche.titantools.light.LightRecovery
import com.everyonesniche.titantools.light.LightService
import com.everyonesniche.titantools.quick.NightMode
import com.everyonesniche.titantools.quick.Shortcuts
import com.everyonesniche.titantools.quick.Torch
import com.everyonesniche.titantools.quick.WidgetButton
import com.everyonesniche.titantools.quick.WidgetSetupActivity
import com.everyonesniche.titantools.rotate.Rotation
import com.everyonesniche.titantools.scale.ScaleActivity
import com.everyonesniche.titantools.scale.ScaleSettings
import com.everyonesniche.titantools.shizuku.Shizuku
import java.util.concurrent.Executors
import kotlin.math.min
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject

/**
 * Ink's screens keep the same size in phone mode and tablet mode (Scale Toggle changes the display density), as TitanKeys'
 * do: a fixed density for the screen's width, and font size 1.
 */
internal fun appBaseContext(base: Context): Context {
    val metrics = base.resources.displayMetrics
    if (metrics.widthPixels <= 0) return base
    val shorter = if (metrics.heightPixels > 0) min(metrics.widthPixels, metrics.heightPixels) else metrics.widthPixels
    val configuration = Configuration().apply {
        fontScale = 1f
        densityDpi = (shorter / 688.6f * DisplayMetrics.DENSITY_DEFAULT).roundToInt()
    }
    return base.createConfigurationContext(configuration)
}

internal fun appUserScale(@Suppress("UNUSED_PARAMETER") context: Context): Float = 1f
internal fun appAccent(@Suppress("UNUSED_PARAMETER") context: Context): Int = 0
internal fun appPressHaptic(@Suppress("UNUSED_PARAMETER") view: android.view.View): Boolean = false
internal fun appBackAtRoot(@Suppress("UNUSED_PARAMETER") activity: MainActivity): Boolean = false

internal fun createAppNativeAdapters(activity: MainActivity): Map<String, NativeAdapter> = mapOf("titantools" to TitanToolsAdapter(activity))

/** Everything the screens ask of the phone: the tools' state and switches, and the pages setup opens. */
private class TitanToolsAdapter(private val activity: MainActivity) : NativeAdapter, android.app.Application.ActivityLifecycleCallbacks {
    private val main = Handler(Looper.getMainLooper())
    private val work = Executors.newSingleThreadExecutor { r -> Thread(r, "titantools-state").apply { isDaemon = true } }
    private val stops = mutableListOf<() -> Unit>()
    private val observer = object : ContentObserver(main) {
        override fun onChange(selfChange: Boolean) = changed()
    }
    private val focus = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
        if (hasFocus) {
            // Back from Shizuku, Settings or an uninstall: look at everything again.
            Installed.forget()
            LightRecovery.checkSoon(activity)
            Rotation.restoreSoon(activity)
            NightMode.restoreSoon(activity)
            changed()
        }
    }

    init {
        Shizuku.init(activity)
        stops += Shizuku.listen {
            LightRecovery.checkSoon(activity)
            Rotation.restoreSoon(activity)
            changed()
        }
        stops += Status.listen { changed() }
        for (setting in listOf(Settings.Secure.DEFAULT_INPUT_METHOD, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES))
            activity.contentResolver.registerContentObserver(Settings.Secure.getUriFor(setting), false, observer)
        activity.window.decorView.viewTreeObserver.addOnWindowFocusChangeListener(focus)
        activity.application.registerActivityLifecycleCallbacks(this)
        LightService.sync(activity)
        ScaleActivity.sync(activity)
        Shortcuts.sync(activity)
        Torch.ready(activity)
        LightRecovery.checkSoon(activity)
        Rotation.restoreSoon(activity)
    }

    override fun onActivityDestroyed(target: android.app.Activity) {
        if (target !== activity) return
        stops.forEach { it() }
        activity.contentResolver.unregisterContentObserver(observer)
        activity.application.unregisterActivityLifecycleCallbacks(this)
    }

    override fun onActivityCreated(target: android.app.Activity, state: android.os.Bundle?) = Unit
    override fun onActivityStarted(target: android.app.Activity) = Unit
    override fun onActivityResumed(target: android.app.Activity) = Unit
    override fun onActivityPaused(target: android.app.Activity) = Unit
    override fun onActivityStopped(target: android.app.Activity) = Unit
    override fun onActivitySaveInstanceState(target: android.app.Activity, state: android.os.Bundle) = Unit

    private fun changed() = activity.emit(JSONObject().put("type", "titantools-state").toString())

    override fun execute(requestId: Long, operation: String, payload: String, complete: NativeResultHandler) {
        val data = try { JSONObject(payload) } catch (error: Exception) { JSONObject() }
        when (operation) {
            // State reads talk to Shizuku, so they run off the main thread.
            "state" -> work.execute {
                try {
                    complete(NativeResult.Success(state().toString()))
                } catch (error: Exception) {
                    complete(NativeResult.Failure(NativeErrorKind.UNEXPECTED, error.message ?: "Couldn't read the state", true))
                }
            }
            else -> try {
                run(operation, data)
                complete(NativeResult.Success("null"))
            } catch (error: Exception) {
                complete(NativeResult.Failure(NativeErrorKind.UNEXPECTED, error.message ?: "Something went wrong. Try again.", false))
            }
        }
    }

    private fun run(operation: String, data: JSONObject) {
        when (operation) {
            "set" -> set(data.getString("key"), data.get("value"))
            "requestShizuku" -> if (Shizuku.running() && !Shizuku.deniedForGood()) Shizuku.requestPermission() else open("shizukuApp")
            "open" -> open(data.getString("target"))
            "uninstall" -> uninstall(data.getString("packageName"))
            else -> throw IllegalArgumentException("Unknown operation: $operation")
        }
    }

    private fun set(key: String, value: Any) {
        val editor = Prefs.of(activity).edit()
        when (key) {
            Prefs.LIGHT_ON, Prefs.SCALE_ON, Prefs.HOLD_ON, Prefs.ROTATION_ON, Prefs.FLASH_ON, Prefs.NIGHT_ON, Prefs.PLAY_ON, Prefs.WIDGET_ON,
            Prefs.HOLD_VIBRATE, Prefs.HOLD_PASSWORDS, Prefs.NIGHT_EXTRA_DIM, Prefs.NIGHT_DARK_THEME -> editor.putBoolean(key, value as Boolean)
            // Editing a list makes the light's rule Custom.
            Prefs.LIGHT_DARK_ROOM, Prefs.LIGHT_NIGHT_HOURS, Prefs.LIGHT_DARK_THEME, Prefs.LIGHT_ANY_TIME ->
                editor.putBoolean(key, value as Boolean).putString(Prefs.LIGHT_PRESET, LightRule.Preset.Custom.id)
            Prefs.LIGHT_ACTIVITY -> editor.putString(key, LightRule.Activity.of(value as String).id)
                .putString(Prefs.LIGHT_PRESET, LightRule.Preset.Custom.id)
            Prefs.LIGHT_PRESET -> {
                val preset = LightRule.Preset.of(value as String)
                editor.putString(key, preset.id)
                LightRule.presetLists(preset)?.let { (d, a) ->
                    editor.putBoolean(Prefs.LIGHT_DARK_ROOM, d.darkRoom).putBoolean(Prefs.LIGHT_NIGHT_HOURS, d.nightHours)
                        .putBoolean(Prefs.LIGHT_DARK_THEME, d.darkTheme).putBoolean(Prefs.LIGHT_ANY_TIME, d.anyTime)
                        .putString(Prefs.LIGHT_ACTIVITY, a.id)
                }
            }
            Prefs.LIGHT_NIGHT_FROM, Prefs.LIGHT_NIGHT_TO -> editor.putInt(key, ((value as Number).toInt() / 30 * 30).coerceIn(0, 1410))
            Prefs.NIGHT_DIM_LEVEL -> editor.putInt(key, (value as Number).toInt().coerceIn(0, 100))
            Prefs.LIGHT_LEVEL -> editor.putInt(Prefs.LIGHT_LEVEL_VALUE, (value as Number).toInt().takeIf(LightLevels::valid) ?: LightLevels.DEFAULT)
                .remove(Prefs.LIGHT_LEVEL).remove(Prefs.LIGHT_LEVEL_NAME)
            Prefs.LIGHT_WHEN -> editor.putString(key, if (value == "typing") "typing" else "screen")
            Prefs.LIGHT_STAY_SECONDS -> editor.putInt(key, (value as Number).toInt().takeIf { it in Prefs.STAY_SECONDS } ?: Prefs.DEFAULT_STAY_SECONDS)
            Prefs.LIGHT_DARK_LUX -> editor.putFloat(key, (value as Number).toFloat().takeIf { it in Prefs.DARK_LUX } ?: Prefs.DEFAULT_DARK_LUX)
            Prefs.HOLD_MILLIS -> editor.putInt(key, (value as Number).toInt().takeIf { it in Prefs.HOLD_TIMES } ?: Prefs.DEFAULT_HOLD_MILLIS)
            Prefs.PHONE_DPI, Prefs.TABLET_DPI -> {
                val dpi = (value as Number).toInt()
                val prefs = Prefs.of(activity)
                val phone = if (key == Prefs.PHONE_DPI) dpi else prefs.getInt(Prefs.PHONE_DPI, 0)
                val tablet = if (key == Prefs.TABLET_DPI) dpi else prefs.getInt(Prefs.TABLET_DPI, 0)
                ScaleSettings.problem(phone, tablet)?.let { throw IllegalArgumentException(it) }
                editor.putInt(key, dpi)
            }
            else -> throw IllegalArgumentException("Unknown setting: $key")
        }
        check(editor.commit()) { "Couldn't save that" }
        when (key) {
            Prefs.LIGHT_ON, Prefs.LIGHT_LEVEL, Prefs.LIGHT_DARK_LUX, Prefs.LIGHT_WHEN, Prefs.LIGHT_STAY_SECONDS, Prefs.LIGHT_PRESET,
            Prefs.LIGHT_DARK_ROOM, Prefs.LIGHT_NIGHT_HOURS, Prefs.LIGHT_DARK_THEME, Prefs.LIGHT_ANY_TIME, Prefs.LIGHT_ACTIVITY,
            Prefs.LIGHT_NIGHT_FROM, Prefs.LIGHT_NIGHT_TO -> LightService.sync(activity)
            Prefs.SCALE_ON -> ScaleActivity.sync(activity)
            Prefs.ROTATION_ON -> Rotation.switched(activity, value as Boolean)
            Prefs.FLASH_ON, Prefs.PLAY_ON -> Shortcuts.sync(activity)
            Prefs.WIDGET_ON -> {
                Shortcuts.sync(activity)
                // Turned on with no widget yet: pick one now.
                if (value == true && WidgetButton.info(activity) == null) open("widgetSetup")
            }
            Prefs.NIGHT_ON -> {
                Shortcuts.sync(activity)
                if (value == false) NightMode.restoreSoon(activity)
            }
        }
        changed()
    }

    private fun open(target: String) {
        val intent = when (target) {
            "shizukuStore" -> Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${Shizuku.PACKAGE}"))
            "shizukuDownload" -> Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/RikkaApps/Shizuku/releases/latest"))
            "shizukuApp" -> activity.packageManager.getLaunchIntentForPackage(Shizuku.PACKAGE)
                ?: Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${Shizuku.PACKAGE}"))
            "developerOptions" -> Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            "aboutPhone" -> Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)
            "accessibility" -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            "appInfo" -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${activity.packageName}"))
            "shortcutKeys" -> shortcutKeys()
            "scaleNow" -> Intent(activity, ScaleActivity::class.java)
            "widgetSetup" -> Intent(activity, WidgetSetupActivity::class.java)
            "widgetNew" -> Intent(activity, WidgetSetupActivity::class.java).putExtra(WidgetSetupActivity.EXTRA_NEW, true)
            "titanKeysPage" -> Intent(Intent.ACTION_VIEW, Uri.parse("https://everyones-niche.com/titankeys/"))
            "titanKeysApp" -> activity.packageManager.getLaunchIntentForPackage(Installed.TITANKEYS)
                ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://everyones-niche.com/titankeys/"))
            else -> throw IllegalArgumentException("Unknown page: $target")
        }
        try {
            activity.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (error: ActivityNotFoundException) {
            when (target) {
                "shizukuStore", "shizukuApp" -> open("shizukuDownload")
                "developerOptions" -> open("aboutPhone")
                else -> activity.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    /** The phone's Shortcut keys page, where the side button's long press is set (as TitanKeys finds it). */
    private fun shortcutKeys(): Intent {
        val packages = activity.packageManager
        packages.getLaunchIntentForPackage(SHORTCUT_KEYS)?.let { return it }
        val entry = Intent(Intent.ACTION_MAIN).setClassName(SHORTCUT_KEYS, "$SHORTCUT_KEYS.ui.EntryAppActivity")
        if (packages.resolveActivity(entry, 0)?.activityInfo?.exported == true) return entry
        packages.queryIntentActivities(Intent("com.android.settings.action.EXTRA_SETTINGS").setPackage(SHORTCUT_KEYS), 0)
            .firstOrNull { it.activityInfo.exported }?.activityInfo?.let { return Intent(Intent.ACTION_MAIN).setClassName(it.packageName, it.name) }
        return Intent(Settings.ACTION_SETTINGS)
    }

    /** Android's own uninstall prompt for one of the old apps. NightKeys' old timeout is put back first, if it can be. */
    private fun uninstall(packageName: String) {
        val app = OldApps.all.firstOrNull { it.packageName == packageName } ?: throw IllegalArgumentException("Not an old app")
        if (app.tool == OldApps.Tool.Light) LightRecovery.checkSoon(activity)
        activity.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun holdServiceOn(): Boolean {
        val mine = ComponentName(activity, HoldService::class.java)
        val list = Settings.Secure.getString(activity.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return list.split(':').any { ComponentName.unflattenFromString(it) == mine }
    }

    private fun state(): JSONObject {
        val prefs = Prefs.of(activity)
        val installed = Shizuku.installed(activity)
        val running = Shizuku.running()
        val allowed = running && Shizuku.allowed()
        val ready = allowed
        val shizukuPause = when {
            !installed -> "Paused: Shizuku isn't installed"
            !running -> "Paused: start Shizuku"
            !allowed -> "Paused: allow Titan Tools in Shizuku"
            else -> null
        }
        val defaultIme = Settings.Secure.getString(activity.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)

        val lightOn = prefs.getBoolean(Prefs.LIGHT_ON, false)
        val lightStatus = when {
            !lightOn -> "Off"
            LightService.instance == null -> shizukuPause ?: "Starting"
            else -> Status.lightText
        }

        val scaleOn = prefs.getBoolean(Prefs.SCALE_ON, false)
        val scaleStatus = when {
            !scaleOn -> "Off"
            shizukuPause != null -> shizukuPause
            else -> "On: long-press the side button"
        }

        val holdOn = prefs.getBoolean(Prefs.HOLD_ON, false)
        val accessibility = holdServiceOn()
        val holdStatus = when {
            !holdOn -> "Off"
            HoldPause.pausedFor(defaultIme) -> "Paused: TitanKeys does holds"
            Installed.oldRunning(activity, OldApps.of(OldApps.Tool.Hold)) -> "Paused: the old Hold Symbols is still on"
            shizukuPause != null -> shizukuPause
            !accessibility -> "Paused: turn it on in Accessibility"
            else -> HoldService.live?.pauseReason() ?: "On: hold a letter for its symbol"
        }

        val level = Prefs.level(activity)
        val link = Link.current(activity)
        val now = System.currentTimeMillis()
        val darkness = Prefs.darkness(activity)
        val lightActivity = Prefs.activity(activity)
        val titanKeysKeyboard = HoldPause.pausedFor(defaultIme)
        val source = LightRule.source(lightActivity, titanKeysKeyboard, LightRule.alive(link, now))
        val old = JSONArray()
        for (app in Installed.oldApps(activity)) {
            old.put(JSONObject().put("packageName", app.packageName).put("name", app.name).put("tool", app.tool.id)
                .put("running", ready && Installed.oldRunning(activity, app)))
        }
        return JSONObject()
            .put("version", activity.packageManager.getPackageInfo(activity.packageName, 0).versionName)
            .put("shizuku", JSONObject().put("installed", installed).put("running", running).put("allowed", allowed)
                .put("everAllowed", Shizuku.everAllowed(activity)))
            .put("light", JSONObject().put("on", lightOn).put("status", lightStatus).put("follows", lightOn && Status.lightFollows)
                .put("level", level).put("levelChoice", LightLevels.nearest(level)).put("darkLux", Prefs.darkLux(activity).toDouble())
                .put("preset", Prefs.preset(activity).id).put("activity", lightActivity.id)
                .put("darkRoom", darkness.darkRoom).put("nightHours", darkness.nightHours).put("darkTheme", darkness.darkTheme)
                .put("anyTime", darkness.anyTime).put("nightFrom", Prefs.nightFrom(activity)).put("nightTo", Prefs.nightTo(activity))
                .put("staySeconds", Prefs.staySeconds(activity)).put("titanKeysKeyboard", titanKeysKeyboard)
                .put("keyPresses", source == LightRule.Source.FirmwareKeys)
                .put("linkSeen", link.seen).put("linkAlive", LightRule.alive(link, now)))
            .put("scale", JSONObject().put("on", scaleOn).put("status", scaleStatus)
                .put("phoneDpi", prefs.getInt(Prefs.PHONE_DPI, 0)).put("tabletDpi", prefs.getInt(Prefs.TABLET_DPI, 0)))
            .put("hold", JSONObject().put("on", holdOn).put("status", holdStatus).put("accessibility", accessibility)
                .put("millis", Prefs.holdMillis(activity)).put("vibrate", prefs.getBoolean(Prefs.HOLD_VIBRATE, true))
                .put("passwords", prefs.getBoolean(Prefs.HOLD_PASSWORDS, false)))
            .put("rotation", JSONObject().put("on", prefs.getBoolean(Prefs.ROTATION_ON, false))
                .put("status", if (!prefs.getBoolean(Prefs.ROTATION_ON, false)) "Off" else shizukuPause ?: Rotation.label(activity)))
            .put("flash", JSONObject().put("on", prefs.getBoolean(Prefs.FLASH_ON, false)).put("lit", Torch.on))
            .put("play", JSONObject().put("on", prefs.getBoolean(Prefs.PLAY_ON, false)))
            .put("widget", JSONObject().put("on", prefs.getBoolean(Prefs.WIDGET_ON, false))
                .put("picked", WidgetButton.info(activity) != null)
                .put("app", WidgetButton.appName(activity) ?: "").put("button", WidgetButton.buttonName(activity) ?: ""))
            .put("night", JSONObject().put("on", prefs.getBoolean(Prefs.NIGHT_ON, false)).put("active", NightMode.active(activity))
                .put("extraDim", prefs.getBoolean(Prefs.NIGHT_EXTRA_DIM, true)).put("dimLevel", NightMode.dimLevel(activity))
                .put("darkTheme", prefs.getBoolean(Prefs.NIGHT_DARK_THEME, false)))
            .put("oldApps", old)
            .put("titanKeys", JSONObject().put("installed", Installed.has(activity, Installed.TITANKEYS))
                .put("selected", HoldPause.pausedFor(defaultIme)))
    }

    override fun cancel(requestId: Long) = Unit

    companion object {
        private const val SHORTCUT_KEYS = "com.agui.shortcutsettings"
    }
}
