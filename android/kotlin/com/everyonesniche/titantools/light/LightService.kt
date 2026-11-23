package com.everyonesniche.titantools.light

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
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
import android.util.Log
import android.view.Display
import com.everyonesniche.titantools.core.Installed
import com.everyonesniche.titantools.core.Link
import com.everyonesniche.titantools.core.OldApps
import com.everyonesniche.titantools.core.Prefs
import com.everyonesniche.titantools.core.Status
import com.everyonesniche.titantools.shizuku.Shell
import com.everyonesniche.titantools.shizuku.Shizuku

/**
 * The keyboard light. Runs while its switch is on. It reads the light sensor (NightKeys' LightPolicy and thresholds), the
 * screen and the link with TitanKeys, decides with [LightRule], and sets the firmware's own stored level and timeout so its
 * key presses keep that level (VendorKeys), with one direct write at each change. The light goes to 0 when it's bright, the screen goes off, the switch goes off, this service
 * stops, and after a crash ([LightJournal]).
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
    private var lit = false
    /** When the firmware's settings were last checked, and when the lit level was last written straight to the light. */
    private var vendorCheckedAt = 0L
    private var assertedAt = 0L
    private var level = Prefs.DEFAULT_LEVEL
    private var unsupported = false
    private var lastNotification = ""
    private var stopListening: List<() -> Unit> = emptyList()

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            worker.postAtFrontOfQueue { screenChanged() }
        }
    }

    private val evaluate = Runnable { evaluate() }

    override fun onCreate() {
        super.onCreate()
        instance = this
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
            rebuildPolicy()
            screenChanged()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Prefs.on(this, Prefs.LIGHT_ON)) {
            worker.post { shutDown() }
            return START_NOT_STICKY
        }
        // Settings changed: new thresholds and level.
        worker.post {
            rebuildPolicy()
            if (lit) setLit(false)
            screenChanged()
        }
        return START_STICKY
    }

    private fun rebuildPolicy() {
        val dark = Prefs.darkLux(this)
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
        policy?.setInteractive(now)
        if (!now) {
            unregisterSensor()
            if (lit) setLit(false)
        } else if (changed || !registered) {
            registerSensor()
        }
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
        evaluate()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    /** Decides and applies, then says why on the home screen and in the notification. */
    private fun evaluate() {
        worker.removeCallbacks(evaluate)
        val policy = policy ?: return
        val now = System.currentTimeMillis()
        policy.tick(SystemClock.elapsedRealtime())
        val link = Link.current(this)
        val mode = LightRule.mode(link, now)
        val status: String
        var want = false
        when {
            light == null -> status = "Paused: this phone has no light sensor"
            !Shizuku.running() -> status = if (Shizuku.installed(this)) "Paused: start Shizuku" else "Paused: Shizuku isn't installed"
            !Shizuku.allowed() -> status = "Paused: allow Titan Tools in Shizuku"
            Installed.blocked(this, OldApps.Tool.Light) -> status = "Paused: NightKeys is still running"
            unsupported -> status = "Paused: the firmware didn't take the light"
            !screenOn -> status = "Off: screen off"
            else -> {
                val dark = policy.shouldLight()
                want = LightRule.lit(dark, screenOn, link, now)
                status = when {
                    want && mode == LightRule.Mode.FollowsTitanKeys -> "Lit: dark and typing"
                    want -> "Lit: dark and screen on"
                    policy.reason() == LightPolicy.Reason.WAITING_FOR_SENSOR -> "Off: reading the light"
                    !dark -> "Off: bright enough"
                    else -> "Off: dark, not typing"
                }
            }
        }
        val ready = Shizuku.allowed() && !Installed.blocked(this, OldApps.Tool.Light)
        if (want != lit) setLit(want)
        else if (ready && SystemClock.elapsedRealtime() - vendorCheckedAt > VENDOR_CHECK_MILLIS) {
            // In case the phone's own settings changed them: the firmware's key light settings, checked every minute.
            vendorCheckedAt = SystemClock.elapsedRealtime()
            LightRecovery.own(this, lit, level)
        }
        if (lit && screenOn && SystemClock.elapsedRealtime() - assertedAt >= ASSERT_MILLIS) {
            // A safety net only: the firmware keeps the level itself now.
            assertedAt = SystemClock.elapsedRealtime()
            try { Shell.light(level) } catch (error: Exception) { Log.w(TAG, "Couldn't write the light again", error) }
        }
        Status.light(if (want && !lit) "Paused: couldn't turn the light on" else status, mode == LightRule.Mode.FollowsTitanKeys)
        updateNotification(Status.lightText)

        var next = LightRule.nextChange(link, now).let { if (it < 0) Long.MAX_VALUE else it - now }
        val deadline = policy.nextDeadline()
        if (deadline >= 0) next = minOf(next, deadline - SystemClock.elapsedRealtime())
        if (lit) next = minOf(next, ASSERT_MILLIS)
        else if (ready) next = minOf(next, VENDOR_CHECK_MILLIS)
        // While paused for Shizuku or NightKeys, look again now and then.
        if (status.startsWith("Paused")) next = minOf(next, 30_000L)
        if (next != Long.MAX_VALUE) worker.postDelayed(evaluate, next.coerceAtLeast(1))
    }

    /**
     * Turns the light on at [level] or off: the firmware's own settings first (VendorKeys: its stored level, which it writes on
     * every key press, and timeout -1), then one direct write so it changes at once.
     */
    private fun setLit(on: Boolean) {
        vendorCheckedAt = SystemClock.elapsedRealtime()
        if (on) {
            try {
                LightJournal.markLit(this)
                LightRecovery.own(this, true, level)
                if (!Shell.light(level)) {
                    // The firmware said no: this phone or firmware doesn't have the call. Never try to fight it.
                    unsupported = true
                    LightRecovery.own(this, false, level)
                    Shell.light(0)
                    LightJournal.markOff(this)
                    return
                }
                lit = true
                assertedAt = SystemClock.elapsedRealtime()
            } catch (error: Exception) {
                Log.w(TAG, "Couldn't light the keyboard", error)
                lit = true
                setLit(false)
            }
        } else {
            lit = false
            try {
                LightRecovery.own(this, false, level)
                Shell.light(0)
                LightJournal.markOff(this)
            } catch (error: Exception) {
                Log.w(TAG, "Couldn't turn the keyboard light off", error)
            }
        }
    }

    private fun shutDown() {
        unregisterSensor()
        worker.removeCallbacksAndMessages(null)
        if (lit || LightJournal.lit(this)) {
            lit = true
            setLit(false)
        }
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
        stopListening.forEach { it() }
        try { unregisterReceiver(screenReceiver) } catch (ignored: Exception) {}
        unregisterSensor()
        worker.removeCallbacksAndMessages(null)
        val wasLit = lit
        worker.post {
            if (wasLit || LightJournal.lit(this)) {
                lit = true
                setLit(false)
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
