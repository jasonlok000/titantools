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
 * Update notices: Titan Tools looks for a newer release itself, on every app open (at most every 10 minutes) and every 6 hours
 * in the background, and posts one notification per new version that opens its release page. The Updates switch turns the
 * checks off. After an update, the home screen shows what's new once.
 *
 * The feed is [FEED_URL]: GitHub's latest release ({"tag_name": "v1.2.0", "html_url": …}) or a version file on the website
 * ({"version": "1.2.0", "versionCode": 25, "url": …}). While the repo is private GitHub answers 404 and nothing is shown.
 */
object Updates {
    /** The one place to change where Titan Tools looks for updates. */
    const val FEED_URL = "https://api.github.com/repos/jasonlok000/titantools/releases/latest"
    private const val CHANNEL = "updates"
    private const val NOTIFICATION = 21
    private const val JOB = 2101
    private const val PERIOD_MILLIS = 6 * 60 * 60_000L
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
        val json = JSONObject(body)
        val version = json.optString("tag_name").ifEmpty { json.optString("version") }.removePrefix("v")
        val url = json.optString("html_url").ifEmpty { json.optString("url") }
        if (version.isEmpty() || !url.startsWith("https://")) return null
        return Release(version, json.optInt("versionCode", 0), url)
    }

    /** Checks now on a worker thread, unless checks are off or [fromApp] and one ran in the last 10 minutes. */
    fun checkSoon(context: Context, fromApp: Boolean, done: (() -> Unit)? = null) {
        val app = context.applicationContext
        schedule(app)
        if (!on(app)) {
            done?.invoke()
            return
        }
        val p = prefs(app)
        if (fromApp && System.currentTimeMillis() - p.getLong("checked_at", 0) < OPEN_GAP_MILLIS) return
        work.execute {
            try { check(app) } catch (error: Exception) { Log.i(TAG, "Update check failed: ${error.message}") }
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
                else -> { Log.i(TAG, "Update check: HTTP $code"); return }
            }
        } finally {
            connection.disconnect()
        }
        val release = available(context) ?: return
        val key = "${release.version}/${release.code}"
        if (p.getString("notified", null) == key) return
        notify(context, release)
        p.edit().putString("notified", key).apply()
        Status.changed()
    }

    private fun notify(context: Context, release: Release) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "A new version of Titan Tools is out."
        })
        val open = PendingIntent.getActivity(context, 0, Intent(Intent.ACTION_VIEW, Uri.parse(release.url)),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        try {
            manager.notify(NOTIFICATION, Notification.Builder(context, CHANNEL)
                .setSmallIcon(com.everyonesniche.titantools.res.R.drawable.titantools_notification)
                .setContentTitle("Titan Tools ${release.version} is out")
                .setContentText("Tap to open the release page and download it.")
                .setContentIntent(open)
                .setAutoCancel(true)
                .build())
        } catch (error: SecurityException) {
            Log.i(TAG, "Notifications are off for Titan Tools")
        }
    }

    /** Every 6 hours with any network while checks are on; none while they're off. */
    fun schedule(context: Context) {
        // Never worth a crash: at worst, checks only run when the app is opened.
        try { scheduleNow(context) } catch (error: Exception) { Log.w(TAG, "Couldn't schedule update checks", error) }
    }

    private fun scheduleNow(context: Context) {
        val jobs = context.getSystemService(JobScheduler::class.java) ?: return
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
