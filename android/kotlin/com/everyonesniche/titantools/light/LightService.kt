package com.everyonesniche.titantools.light

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.KeyguardManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Display
import com.everyonesniche.titantools.core.Installed
import com.everyonesniche.titantools.core.Link
import com.everyonesniche.titantools.core.OldApps
import com.everyonesniche.titantools.core.Prefs
import com.everyonesniche.titantools.core.Status
import com.everyonesniche.titantools.hold.HoldPause
import com.everyonesniche.titantools.shizuku.Shell
import com.everyonesniche.titantools.shizuku.Shizuku
import java.util.Calendar

/**
 * The keyboard light. Runs while its switch is on: "Light the keys when [darkness] and [activity]" (LightRule). It reads the
 * light sensor (NightKeys' LightPolicy), the clock, the dark theme, the screen, the link with TitanKeys and the phone's input
 * flag, and sets the firmware's own key light settings (VendorKeys) so its key presses keep the right level, with one direct
 * write at each change. The light goes to 0 when it's not dark, the screen goes off, the switch goes off, this service stops,
 * and after a crash ([LightJournal]). "Phone's own" hands the light back to the phone.
 *
 * Everything runs on one worker thread; the screen going off jumps to the front of its queue.
 */
class LightService : Service(), SensorEventListener {
    private lateinit var thread: HandlerThread
    private lateinit var worker: Handler
    private var sensors: SensorManager? = null
    private var light: Sensor? = null
    private var registered = false
    private var policy: LightPolicy? = null
    private var screenOn = false
    /** Titan Tools' own light is on (written straight to the light). */
    private var lit = false
    /** What the firmware's settings were last set to: "lit/level on/timeout", or null before the first time. */
    private var applied: String? = null
    private var vendorCheckedAt = 0L
    private var assertedAt = 0L
    private var level = Prefs.DEFAULT_LEVEL
    private var unsupported = false
    private var darkLux = Float.NaN
    private var settings = ""
    /** The phone's input flag (Text field open, other keyboards), and when it last closed. */
    private var fieldOpen = false
    private var fieldClosedAt = 0L
    private var handedBack = false
    private var lastNotification = ""
    private var stopListening: List<() -> Unit> = emptyList()
    /** For the decision log: the last decision written, the last link message seen, and a trigger waiting to be judged. */
    private var loggedDecision = ""
    private var seenLinkAt = -1L
    private var trigger: String? = null
    private var keyLoggedAt = 0L
    /** The light sensor's last reading while the screen is on (NaN before one), for the light screen's room indicator. */
    @Volatile var roomLux = Float.NaN
        private set
    private var roomPushedAt = 0L

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            worker.postAtFrontOfQueue { screenChanged() }
        }
    }

    private val evaluate = Runnable { evaluate() }

    override fun onCreate() {
        super.onCreate()
        instance = this
        LightLog.init(this)
        LightLog.write("service start")
        startForeground(NOTIFICATION, notification("Starting"), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        thread = HandlerThread("titantools-light").apply { start() }
        worker = Handler(thread.looper)
        sensors = getSystemService(SensorManager::class.java)
        light = sensors?.getDefaultSensor(Sensor.TYPE_LIGHT)
        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }, RECEIVER_NOT_EXPORTED)
        stopListening = listOf(
            Shizuku.listen { worker.post(evaluate) },
            Link.listen { worker.post(evaluate) },
        )
        worker.post {
            // The firmware's settings are checked after the first light reading, not before it.
            vendorCheckedAt = SystemClock.elapsedRealtime()
            rebuildPolicy()
            screenChanged()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Prefs.on(this, Prefs.LIGHT_ON)) {
            worker.post { shutDown() }
            return START_NOT_STICKY
        }
        // Settings may have changed: a new threshold starts the reading afresh, a new level applies at once. Nothing changed
        // (as when the service has only just started): nothing to do, so the light never flickers.
        worker.post {
            when {
                Prefs.darkLux(this) != darkLux -> {
                    rebuildPolicy()
                    screenChanged()
                }
                Prefs.level(this) != level -> {
                    level = Prefs.level(this)
                    applied = null
                    evaluate()
                }
                settingsKey() != settings -> evaluate()
            }
        }
        return START_STICKY
    }

    /** The dark theme changed: the darkness rule may have. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        worker.post(evaluate)
    }

    private fun settingsKey() = "${Prefs.on(this, Prefs.LIGHT_NIGHT_TYPING)}/${Prefs.on(this, Prefs.NIGHT_ACTIVE)}/${Prefs.preset(this)}/${Prefs.darkness(this)}/${Prefs.activity(this)}/${Prefs.staySeconds(this)}/" +
        "${Prefs.nightFrom(this)}/${Prefs.nightTo(this)}"

    private fun rebuildPolicy() {
        val dark = Prefs.darkLux(this)
        darkLux = dark
        level = Prefs.level(this)
        policy = LightPolicy(dark, Prefs.brightLux(dark), 0, 250).apply {
            setEnabled(true)
            setInteractive(screenIsOn())
        }
    }

    private fun screenIsOn(): Boolean {
        val power = getSystemService(PowerManager::class.java)
        val screen = getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
        return power?.isInteractive == true && screen?.state == Display.STATE_ON
    }

    private fun screenChanged() {
        val now = screenIsOn()
        val changed = now != screenOn
        screenOn = now
        if (changed) LightLog.write(if (now) "screen on" else "screen off", "locked=${locked()}")
        policy?.setInteractive(now)
        if (!now) roomLux = Float.NaN
        if (!now) unregisterSensor() else if (changed || !registered) registerSensor()
        evaluate()
    }

    private fun registerSensor() {
        val sensor = light ?: return
        if (!registered) registered = sensors?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL, 0, worker) == true
    }

    private fun unregisterSensor() {
        if (registered) sensors?.unregisterListener(this)
        registered = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        val value = event.values.firstOrNull() ?: Float.NaN
        policy?.sample(value, SystemClock.elapsedRealtime())
        // The light screen's room indicator follows the sensor: told when the reading moves by a tenth, at most twice a second.
        val shown = roomLux
        roomLux = value
        val now = SystemClock.elapsedRealtime()
        if (now - roomPushedAt >= 500 && (shown.isNaN() || kotlin.math.abs(value - shown) > maxOf(0.3f, shown * 0.1f))) {
            roomPushedAt = now
            com.everyonesniche.titantools.core.Status.changed()
        }
        evaluate()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    /** Until this time (elapsed), the brightness bar's preview owns the light; then the rules take it back. */
    private var previewUntil = 0L
    private val endPreview = Runnable {
        if (SystemClock.elapsedRealtime() < previewUntil) return@Runnable
        previewUntil = 0L
        LightLog.write("preview end", "back to the rules")
        if (!lit) try { Shell.light(0) } catch (ignored: Exception) {}
        applied = null
        evaluate()
    }

    /** The brightness bar is moving: light the keys at [value] now, even if the rules have them off, for a moment. */
    fun preview(value: Int) {
        if (!LightLevels.valid(value)) return
        worker.post {
            if (!screenOn || !Shizuku.allowed()) return@post
            if (previewUntil == 0L) LightLog.write("preview", "level=$value")
            previewUntil = SystemClock.elapsedRealtime() + PREVIEW_MILLIS
            try { Shell.light(value) } catch (error: Exception) { LightLog.write("FAIL preview", "${error.javaClass.simpleName}: ${error.message}") }
            worker.removeCallbacks(endPreview)
            worker.postDelayed(endPreview, PREVIEW_MILLIS)
        }
    }

    private fun locked(): Boolean = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true

    /** A key on the built-in keyboard went down (from the hold service): judged against the light for the log. */
    fun keyPressed() {
        worker.post {
            val now = SystemClock.elapsedRealtime()
            // One key a second is enough to judge; nothing about the key itself is kept.
            if (now - keyLoggedAt < 1_000L) return@post
            keyLoggedAt = now
            trigger = "key"
            evaluate()
        }
    }

    private fun themeDark(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private fun minuteOfDay(): Int = Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }

    /** Decides and applies, then says why on the home screen and in the notification. */
    private fun evaluate() {
        worker.removeCallbacks(evaluate)
        val policy = policy ?: return
        val now = System.currentTimeMillis()
        policy.tick(SystemClock.elapsedRealtime())
        settings = settingsKey()
        val link = Link.current(this)
        if (seenLinkAt >= 0 && link.lastAt != seenLinkAt) trigger = when {
            !link.typing -> trigger ?: "TitanKeys-stopped"
            link.typedAt == link.lastAt -> "TitanKeys-TYPING"
            else -> "TitanKeys-field"
        }
        seenLinkAt = link.lastAt
        val titanKeys = HoldPause.pausedFor(Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD))
        val darkness = Prefs.darkness(this)
        val nightTyping = Prefs.on(this, Prefs.LIGHT_NIGHT_TYPING) && Prefs.on(this, Prefs.NIGHT_ACTIVE)
        val activity = LightRule.effectiveActivity(Prefs.activity(this), Prefs.on(this, Prefs.LIGHT_NIGHT_TYPING), Prefs.on(this, Prefs.NIGHT_ACTIVE))
        val stayMillis = Prefs.staySeconds(this) * 1000L
        val source = LightRule.source(activity, titanKeys, LightRule.alive(link, now))
        val ready = Shizuku.allowed() && !Installed.blocked(this, OldApps.Tool.Light)
        val waiting = darkness.darkRoom && policy.reason() == LightPolicy.Reason.WAITING_FOR_SENSOR
        val dark = LightRule.dark(darkness, LightRule.Facts(policy.shouldLight(), minuteOfDay(), Prefs.nightFrom(this),
            Prefs.nightTo(this), themeDark()))

        var status: String
        var wantLit = false
        var wantLevel = false
        var poll = false
        when {
            darkness.darkRoom && light == null -> status = "Paused: this phone has no light sensor"
            !Shizuku.running() -> status = if (Shizuku.installed(this)) "Paused: start Shizuku" else "Paused: Shizuku isn't installed"
            !Shizuku.allowed() -> status = "Paused: allow Titan Tools in Shizuku"
            Installed.blocked(this, OldApps.Tool.Light) -> status = "Paused: NightKeys is still running"
            unsupported -> status = "Paused: the firmware didn't take the light"
            Prefs.preset(this) == LightRule.Preset.PhonesOwn -> status = "The phone's own key light"
            !screenOn -> status = "Off: screen off"
            // Until the first reading, nothing changes: a restarted service doesn't blink a lit keyboard.
            waiting -> status = if (lit) "Lit: reading the light" else "Off: reading the light"
            else -> {
                if (source == LightRule.Source.InputFlag && dark) {
                    poll = true
                    val open = try { Shell.inputOpen() } catch (error: Exception) { fieldOpen }
                    if (fieldOpen && !open) fieldClosedAt = now
                    fieldOpen = open
                }
                wantLit = LightRule.lit(source, dark, screenOn, link, now, stayMillis, fieldOpen, fieldClosedAt)
                wantLevel = wantLit || LightRule.firmwareLevelOn(source, dark, screenOn)
                status = when {
                    !dark -> if (darkness == LightRule.Darkness(true, false, false, false)) "Off: bright enough" else "Off: not dark"
                    source == LightRule.Source.FirmwareKeys -> "On while you press keys"
                    wantLit -> when (source) {
                        LightRule.Source.LinkTyping -> "Lit: typing"
                        LightRule.Source.LinkField, LightRule.Source.InputFlag -> "Lit: text field open"
                        else -> "Lit: dark and screen on"
                    }
                    source == LightRule.Source.LinkTyping -> "Off: not typing"
                    else -> "Off: no text field open"
                }
            }
        }

        if (Prefs.preset(this) == LightRule.Preset.PhonesOwn && ready) {
            // Hand the light back, as with the switch off.
            if (!handedBack) {
                if (lit) try { Shell.light(0) } catch (ignored: Exception) {}
                lit = false
                LightRecovery.release(this)
                LightJournal.markOff(this)
                handedBack = true
                applied = null
            }
        } else if (ready && !status.startsWith("Paused") && !waiting) {
            handedBack = false
            val timeout = if (source == LightRule.Source.FirmwareKeys) LightRule.firmwareTimeout(stayMillis).toString() else VendorKeys.OWNED_TIMEOUT
            apply(wantLit, wantLevel, timeout)
        }

        if (lit && screenOn && ready && SystemClock.elapsedRealtime() - assertedAt >= ASSERT_MILLIS) {
            // A safety net only, as the firmware keeps the level itself. It also picks up a level set in the phone's own
            // settings, which Titan Tools then uses instead of taking turns with it.
            assertedAt = SystemClock.elapsedRealtime()
            level = LightRecovery.own(this, true, level)
            try { Shell.light(level) } catch (error: Exception) {
                Log.w(TAG, "Couldn't write the light again", error)
                LightLog.write("FAIL rewrite light", "level=$level: ${error.javaClass.simpleName}: ${error.message}")
            }
        }
        // While Night mode makes it Typing, the card says so.
        if (nightTyping && !status.startsWith("Paused") && Prefs.preset(this) != LightRule.Preset.PhonesOwn) status = "Night mode: only while typing"
        logDecision(status, source, dark, wantLit, wantLevel, ready, link, now, nightTyping, activity)
        Status.light(status, source == LightRule.Source.LinkTyping || source == LightRule.Source.LinkField)
        updateNotification(Status.lightText)

        var next = LightRule.nextChange(source, link, now, stayMillis, fieldClosedAt).let { if (it < 0) Long.MAX_VALUE else it - now }
        val deadline = policy.nextDeadline()
        if (deadline >= 0) next = minOf(next, deadline - SystemClock.elapsedRealtime())
        if (poll) next = minOf(next, 1_000L)
        if (lit) next = minOf(next, ASSERT_MILLIS)
        else if (ready) next = minOf(next, VENDOR_CHECK_MILLIS)
        // Night hours start and end by the clock.
        if (darkness.nightHours) next = minOf(next, 60_000L)
        // While paused for Shizuku or NightKeys, look again now and then.
        if (status.startsWith("Paused")) next = minOf(next, 30_000L)
        if (next != Long.MAX_VALUE) worker.postDelayed(evaluate, next.coerceAtLeast(1))
    }

    /**
     * The decision log: a line whenever the decision or its reason changes, and one for each trigger (a key press or a
     * TitanKeys message) saying whether the light was on, and why not when it wasn't.
     */
    private fun logDecision(status: String, source: LightRule.Source, dark: Boolean, wantLit: Boolean, wantLevel: Boolean,
                            ready: Boolean, link: LightRule.Link, now: Long, nightTyping: Boolean, activity: LightRule.Activity) {
        val policy = policy
        val linkAge = if (link.lastAt > 0) "${(now - link.lastAt) / 1000}s" else "never"
        val details = "status=\"$status\" lit=$lit want=$wantLit level=${if (wantLevel) level else 0} dark=$dark " +
            "lux=${policy?.lux()} darkBelow=$darkLux sensor=${policy?.reason()} screen=$screenOn locked=${locked()} " +
            "night=${Prefs.on(this, Prefs.NIGHT_ACTIVE)} nightTyping=$nightTyping activity=${activity.id} source=$source " +
            "link=${LightRule.alive(link, now)} linkTyping=${link.typing} lastLink=$linkAge lastTyped=${if (link.typedAt > 0) "${(now - link.typedAt) / 1000}s" else "never"} stay=${Prefs.staySeconds(this)}s " +
            "shizuku=${Shizuku.running()}/${Shizuku.allowed()} ready=$ready unsupported=$unsupported field=$fieldOpen"
        val decision = "$status/$lit/$wantLit/$wantLevel/$dark/$source/$screenOn/$ready"
        if (decision != loggedDecision) {
            loggedDecision = decision
            LightLog.write("decide", details)
        }
        val what = trigger ?: return
        trigger = null
        when {
            lit -> LightLog.write("trigger $what", "lit")
            what == "TitanKeys-stopped" -> Unit
            // A field opening isn't typing: in Typing mode it's meant to stay off.
            what == "TitanKeys-field" && source == LightRule.Source.LinkTyping -> LightLog.write("trigger $what", "off: a field opened, not typing")
            source == LightRule.Source.FirmwareKeys && wantLevel -> LightLog.write("trigger $what", "firmware lights on keys at level $level")
            // Not dark or the screen off: off as meant, but still noted with the reason, in case the reading was wrong.
            !dark || !screenOn -> LightLog.write("trigger $what", "off: $details")
            else -> LightLog.write("MISS $what", "stayed off: $details")
        }
    }

    /**
     * Sets Titan Tools' light: the firmware's own settings first (VendorKeys: its stored level, which it writes on every key
     * press, the timeout and the automatic light off), then one direct write so it changes at once. Checked again every minute.
     */
    private fun apply(wantLit: Boolean, wantLevel: Boolean, timeout: String) {
        // The brightness preview has the light; endPreview hands it back.
        if (previewUntil != 0L) return
        val key = "$wantLit/$wantLevel/$timeout"
        val due = SystemClock.elapsedRealtime() - vendorCheckedAt > VENDOR_CHECK_MILLIS
        if (key == applied && !due) return
        vendorCheckedAt = SystemClock.elapsedRealtime()
        val levelWas = applied?.split("/")?.getOrNull(1) == "true"
        try {
            if (wantLevel) LightJournal.markLit(this)
            level = LightRecovery.own(this, wantLevel, level, timeout)
            if (wantLit && (!lit || key != applied)) {
                val took = Shell.light(level)
                LightLog.write("write light", "level=$level ${if (took) "ok" else "REFUSED by firmware"}")
                if (!took) {
                    // The firmware said no: this phone or firmware doesn't have the call. Never try to fight it.
                    unsupported = true
                    LightRecovery.own(this, false, level, timeout)
                    Shell.light(0)
                    LightJournal.markOff(this)
                    lit = false
                    applied = null
                    return
                }
                assertedAt = SystemClock.elapsedRealtime()
            } else if (!wantLit && (lit || (levelWas && !wantLevel))) {
                val took = Shell.light(0)
                LightLog.write("write light", "level=0 ${if (took) "ok" else "REFUSED by firmware"}")
            }
            lit = wantLit
            if (!wantLevel) LightJournal.markOff(this)
            applied = key
        } catch (error: Exception) {
            Log.w(TAG, "Couldn't set the keyboard light", error)
            LightLog.write("FAIL write light", "want=$wantLit level=$wantLevel: ${error.javaClass.simpleName}: ${error.message}")
            applied = null
        }
    }

    private fun shutDown() {
        LightLog.write("service off", "switch off")
        unregisterSensor()
        worker.removeCallbacksAndMessages(null)
        if (lit || LightJournal.lit(this)) try { Shell.light(0) } catch (ignored: Exception) {}
        lit = false
        LightJournal.markOff(this)
        // The tool is off: the phone's own key light settings go back, and the firmware is in charge again.
        LightRecovery.release(this)
        Status.light("Off", false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notification(text: String): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Keyboard light", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shows that the keyboard light is running, and why it's on or off."
            setShowBadge(false)
        })
        val open = packageManager.getLaunchIntentForPackage(packageName)
            ?.let { PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT) }
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(com.everyonesniche.titantools.res.R.drawable.titantools_notification)
            .setContentTitle("Keyboard light")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(text: String) {
        if (text == lastNotification) return
        lastNotification = text
        try {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification(text))
        } catch (ignored: Exception) {
        }
    }

    override fun onDestroy() {
        instance = null
        LightLog.write("service destroyed")
        stopListening.forEach { it() }
        try { unregisterReceiver(screenReceiver) } catch (ignored: Exception) {}
        unregisterSensor()
        worker.removeCallbacksAndMessages(null)
        worker.post {
            if (lit || LightJournal.lit(this)) {
                try {
                    LightRecovery.own(this, false, level)
                    Shell.light(0)
                    LightJournal.markOff(this)
                } catch (ignored: Exception) {}
            }
            thread.quitSafely()
        }
        Status.light("Off", false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        @Volatile var instance: LightService? = null
            private set
        private const val CHANNEL = "keyboard_light"
        private const val NOTIFICATION = 7
        private const val ASSERT_MILLIS = 5_000L
        private const val PREVIEW_MILLIS = 2_500L
        private const val VENDOR_CHECK_MILLIS = 60_000L
        private const val TAG = "TitanTools"

        /** Starts, refreshes or stops the service to match the switch. */
        fun sync(context: Context) {
            val on = Prefs.on(context, Prefs.LIGHT_ON)
            if (on) {
                try {
                    context.startForegroundService(Intent(context, LightService::class.java))
                } catch (error: Exception) {
                    Log.w(TAG, "Android wouldn't start the keyboard light", error)
                }
            } else if (instance != null) {
                context.startService(Intent(context, LightService::class.java))
            } else {
                LightRecovery.checkSoon(context)
            }
        }
    }
}
