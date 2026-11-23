package com.everyonesniche.titantools.shizuku

import com.everyonesniche.titantools.core.OldApps
import com.everyonesniche.titantools.light.TimeoutMigration
import com.everyonesniche.titantools.light.VendorReply

/**
 * Every command Titan Tools runs through Shizuku, each a fixed argument list with checked numbers. There is no way to run
 * anything else, and nothing typed or sensed goes into a command except a checked whole number.
 */
object Shell {
    const val MAX_LEVEL = 255
    private const val SERVICE = "/system/bin/service"
    private const val TIMEOUT = "keyboard_brightness_timeout"
    private const val TIMEOUT_BACKUP = "keyboard_brightness_timeout_backup"
    /** How long one keep-lit loop runs before it ends by itself: 200 rounds of half a second. */
    const val LOOP_MILLIS = 100_000L

    /**
     * Sets the keyboard light to [level] (0 is off) through the firmware's own call, AguiFunctionalService.keyboardLightTest,
     * which writes /sys/devices/platform/keypad_led/keyled_brightness. True when the firmware took it.
     */
    fun light(level: Int): Boolean {
        require(level in 0..MAX_LEVEL) { "Light level out of range" }
        val result = RemoteProcess.run(4_000, arrayOf(SERVICE, "call", "agui_functional_service", "7", "s16", level.toString()))
        return VendorReply.supported(result.out)
    }

    /**
     * Something in the firmware sets the light back to 0 about a second after each write, so a lit keyboard is written again
     * every half second. One shell loop does it, and ends by itself after [LOOP_MILLIS] so a crash can't leave it running;
     * the light service starts a new one before then. Destroy the returned process to stop it.
     */
    internal fun keepLit(level: Int): RemoteProcess {
        require(level in 1..MAX_LEVEL) { "Light level out of range" }
        val script = "i=0; while [ \$i -lt 200 ]; do $SERVICE call agui_functional_service 7 s16 $level >/dev/null 2>&1; " +
            "sleep 0.5; i=\$((i+1)); done"
        return RemoteProcess.start(arrayOf("/system/bin/sh", "-c", script))
    }

    fun readTimeout(): String? = readVendor(TIMEOUT)
    fun readTimeoutBackup(): String? = readVendor(TIMEOUT_BACKUP)

    private fun readVendor(key: String): String? {
        require(key == TIMEOUT || key == TIMEOUT_BACKUP)
        val result = RemoteProcess.run(4_000, arrayOf(SERVICE, "call", "agui_common", "3", "s16", key, "s16", TimeoutMigration.MISSING))
        return VendorReply.string(result.out)
    }

    /**
     * The firmware's key light timeout: 0 while the Keyboard light tool owns the light, the backup when it lets go
     * (TimeoutMigration). The same write as old NightKeys, read back to check.
     */
    fun writeTimeout(value: String): Boolean = writeVendor(TIMEOUT, value)

    /** Saves the phone's own timeout before Titan Tools sets it to 0, when the firmware has no backup of it. */
    fun writeTimeoutBackup(value: String): Boolean = writeVendor(TIMEOUT_BACKUP, value)

    private fun writeVendor(key: String, value: String): Boolean {
        require(key == TIMEOUT || key == TIMEOUT_BACKUP)
        require(TimeoutMigration.valid(value)) { "Not a timeout" }
        RemoteProcess.run(4_000, arrayOf(SERVICE, "call", "agui_common", "4", "s16", key, "s16", value.trim()))
        return readVendor(key) == value.trim()
    }

    /** Scale Toggle's display commands: wm size, wm density, wm density <120–8192>, wm density reset. */
    fun wm(vararg args: String): String {
        val ok = (args.size == 1 && (args[0] == "size" || args[0] == "density")) ||
            (args.size == 2 && args[0] == "density" && (args[1] == "reset" || args[1].toIntOrNull()?.let { it in 120..8192 } == true))
        require(ok) { "Unsupported display command" }
        val result = RemoteProcess.run(6_000, arrayOf("/system/bin/wm", *args))
        if (result.out.contains("SecurityException") || result.err.contains("SecurityException"))
            throw SecurityException("Android refused the display command")
        return result.out
    }

    /** True while one of the old apps has a service running (NightKeys' light service, say). */
    fun oldAppRunning(app: OldApps.App): Boolean {
        val result = RemoteProcess.run(5_000, arrayOf("/system/bin/dumpsys", "activity", "services", app.packageName))
        return result.out.lineSequence().any { it.contains("ServiceRecord{") && it.contains(app.packageName) }
    }
}
