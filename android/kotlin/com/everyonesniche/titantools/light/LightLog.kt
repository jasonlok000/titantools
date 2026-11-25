package com.everyonesniche.titantools.light

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The keyboard light's decision log: each change, each decision not to change and why, firmware writes, failures and missed
 * triggers. Never typed content or key codes. Rolling: two files of up to [MAX_BYTES] in the app's external files folder, so
 * it can be read over adb:
 *
 *     adb shell cat /sdcard/Android/data/com.everyonesniche.titantools/files/light-log.1.txt /sdcard/Android/data/com.everyonesniche.titantools/files/light-log.txt
 *
 * Each line also goes to logcat under the tag TitanToolsLight.
 */
object LightLog {
    private const val MAX_BYTES = 512 * 1024L
    private const val TAG = "TitanToolsLight"
    private val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    @Volatile private var dir: File? = null

    fun init(context: Context) {
        if (dir == null) dir = context.applicationContext.getExternalFilesDir(null)
    }

    @Synchronized
    fun write(event: String, details: String = "") {
        val line = "${format.format(Date())} $event${if (details.isEmpty()) "" else " $details"}"
        Log.i(TAG, line)
        val folder = dir ?: return
        try {
            val file = File(folder, "light-log.txt")
            if (file.length() > MAX_BYTES) file.renameTo(File(folder, "light-log.1.txt"))
            file.appendText(line + "\n")
        } catch (error: Exception) {
            Log.w(TAG, "Couldn't write the light log", error)
        }
    }
}
