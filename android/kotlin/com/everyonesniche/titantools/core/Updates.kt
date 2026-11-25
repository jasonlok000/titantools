package com.everyonesniche.titantools.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Update notices, in your face because TitanTools never updates by itself: it looks for a newer release on every app open (at
 * most every 10 minutes) and every 3 hours in the background. While one is out, a silent heads-up notification opening its
 * release page comes back once a day until you update, and the home screen shows a banner. The Updates switch turns the
 * checks off. After an update, the home screen shows what's new once.
 *
 * The feed is [FEED_URL]: GitHub's newest release, pre-releases included ([{"tag_name": "v1.2.0", "html_url": â€¦}) or a version file on the website
 * ({"version": "1.2.0", "versionCode": 25, "url": â€¦}). While the repo is private GitHub answers 404 and nothing is shown.
 */
object Updates {
    /** The one place to change where TitanTools looks for updates. */
    const val FEED_URL = "https://api.github.com/repos/jasonlok000/titantools/releases?per_page=1"
    /** Heads-up but silent. A new id, as a channel's importance can't be raised once made (the first "updates" was default). */
    private const val CHANNEL = "updates_heads_up"
    private const val REPEAT_MILLIS = 24 * 60 * 60_000L
    private const val NOTIFICATION = 21
    private const val JOB = 2102
    private const val OLD_JOB = 2101
    private const val PERIOD_MILLIS = 3 * 60 * 60_000L
    private const val OPEN_GAP_MILLIS = 10 * 60_000L
    private const val TAG = "TitanTools"
    private val work = Executors.newSingleThreadExecutor { r -> Thread(r, "titantools-updates").apply { isDaemon = true } }

    data class Release(val version: String, val code: Int, val url: String)

    private fun prefs(context: Context) = context.getSharedPreferences("updates", Context.MODE_PRIVATE)

    fun on(context: Context): Boolean = Prefs.of(context).getBoolean(Prefs.UPDATES_ON, true)

    private fun installed(context: Context): Pair<String, Int> {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return (info.versionName ?: "0") to info.longVersionCode.toInt()
    }

    /** -1, 0 or 1 as [a] is older, the same or newer than [b], by their dotted numbers. */
    fun compare(a: String, b: String): Int {
        val x = a.trim().removePrefix("v").split('.', '-').map { it.toIntOrNull() ?: 0 }
        val y = b.trim().removePrefix("v").split('.', '-').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val c = (x.getOrElse(i) { 0 }).compareTo(y.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        return 0
    }

    fun newer(release: Release, version: String, code: Int): Boolean {
        val c = compare(release.version, version)
        return c > 0 || (c == 0 && release.code > code)
    }

    /** A newer release found by the last check, or null. */
    fun available(context: Context): Release? {
        val p = prefs(context)
        val release = Release(p.getString("version", null) ?: return null, p.getInt("code", 0), p.getString("url", null) ?: return null)
        val (version, code) = installed(context)
        return release.takeIf { on(context) && newer(it, version, code) }
    }

    /** Parses either feed shape; null when it isn't one. */
    fun parse(body: String): Release? {
        // GitHub's release list, newest first (pre-releases included, which releases/latest leaves out), or one object.
        val text = body.trim()
        val json = if (text.startsWith("[")) org.json.JSONArray(text).optJSONObject(0) ?: return null else JSONObject(text)
        val version = json.optString("tag_name").ifEmpty { json.optString("version") }.removePrefix("v")
        val url = json.optString("html_url").ifEmpty { json.optString("url") }
        if (version.isEmpty() || !url.startsWith("https://")) return null
        return Release(version, json.optInt("versionCode", 0), url)
    }

    /** Checks now on a worker thread, unless checks are off or [fromApp] and one ran in the last 10 minutes. */
    fun checkSoon(context: Context, fromApp: Boolean, done: (() -> Unit)? = null) {
        val app = context.applicationContext
        com.everyonesniche.titantools.light.LightLog.init(app)
        schedule(app)
        if (!on(app)) {
            done?.invoke()
            return
        }
        val p = prefs(app)
        if (fromApp && System.currentTimeMillis() - p.getLong("checked_at", 0) < OPEN_GAP_MILLIS) return
        work.execute {
            try { check(app) } catch (error: Exception) { com.everyonesniche.titantools.light.LightLog.write("update check failed", "${error.javaClass.simpleName}: ${error.message}") }
            done?.invoke()
        }
    }

    private fun check(context: Context) {
        val p = prefs(context)
        p.edit().putLong("checked_at", System.currentTimeMillis()).apply()
        val connection = URL(FEED_URL).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.setRequestProperty("Accept", "application/vnd.github+json, application/json")
        connection.setRequestProperty("User-Agent", "TitanTools")
        p.getString("etag", null)?.let { connection.setRequestProperty("If-None-Match", it) }
        try {
            when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_NOT_MODIFIED -> Unit
                HttpURLConnection.HTTP_OK -> {
                    val release = parse(connection.inputStream.bufferedReader().use { it.readText() }) ?: return
                    p.edit().putString("version", release.version).putInt("code", release.code).putString("url", release.url)
                        .putString("etag", connection.getHeaderField("ETag")).apply()
                }
                else -> { com.everyonesniche.titantools.light.LightLog.write("update check", "HTTP $code"); return }
            }
        } finally {
            connection.disconnect()
        }
        val release = available(context)
        com.everyonesniche.titantools.light.LightLog.write("update check", "newest ${p.getString("version", null)}, installed ${installed(context).first}: ${if (release != null) "newer" else "up to date"}")
        if (release == null) return
        val key = "${release.version}/${release.code}"
        if (p.getString("notified", null) == key && System.currentTimeMillis() - p.getLong("notified_at", 0) < REPEAT_MILLIS) return
        com.everyonesniche.titantools.light.LightLog.write("update notice", "${release.version} (${release.code})")
        notify(context, release)
        p.edit().putString("notified", key).putLong("notified_at", System.currentTimeMillis()).apply()
        Status.changed()
    }

    private fun notify(context: Context, release: Release) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) com.everyonesniche.titantools.light.LightLog.write("update notice blocked", "notifications are off for TitanTools; the home screen banner still shows")
        manager.deleteNotificationChannel("updates")
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Updates", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "When a new TitanTools is out. Once a day until you update."
            setSound(null, null)
            enableVibration(false)
        })
        val open = PendingIntent.getActivity(context, 0, Intent(Intent.ACTION_VIEW, Uri.parse(release.url)),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        try {
            manager.notify(NOTIFICATION, Notification.Builder(context, CHANNEL)
                .setSmallIcon(com.everyonesniche.titantools.res.R.drawable.titantools_notification)
                .setContentTitle("TitanTools ${release.version} is out")
                .setContentText("Tap to get it from the release page.")
                .setContentIntent(open)
                .setAutoCancel(true)
                .build())
        } catch (error: SecurityException) {
            com.everyonesniche.titantools.light.LightLog.write("update notice blocked", "notifications are off for TitanTools")
        }
    }

    /** Every 3 hours with any network while checks are on; none while they're off. */
    fun schedule(context: Context) {
        // Never worth a crash: at worst, checks only run when the app is opened.
        try { scheduleNow(context) } catch (error: Exception) { Log.w(TAG, "Couldn't schedule update checks", error) }
    }

    private fun scheduleNow(context: Context) {
        val jobs = context.getSystemService(JobScheduler::class.java) ?: return
        jobs.cancel(OLD_JOB)
        if (!on(context)) {
            jobs.cancel(JOB)
            return
        }
        if (jobs.getPendingJob(JOB) != null) return
        jobs.schedule(JobInfo.Builder(JOB, ComponentName(context, Job::class.java))
            .setPeriodic(PERIOD_MILLIS)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPersisted(true)
            .build())
    }

    /** What's new: the version code to show it for once, or 0. A first install, or the first build with this, shows none. */
    fun whatsNew(context: Context): Int {
        val (_, code) = installed(context)
        val seen = prefs(context).getInt("seen_code", 0)
        if (seen == 0) {
            prefs(context).edit().putInt("seen_code", code).apply()
            return 0
        }
        return if (seen < code) code else 0
    }

    fun seenWhatsNew(context: Context) {
        prefs(context).edit().putInt("seen_code", installed(context).second).apply()
    }

    class Job : JobService() {
        override fun onStartJob(params: JobParameters): Boolean {
            checkSoon(this, fromApp = false) { jobFinished(params, false) }
            return true
        }

        override fun onStopJob(params: JobParameters): Boolean = true
    }
}
