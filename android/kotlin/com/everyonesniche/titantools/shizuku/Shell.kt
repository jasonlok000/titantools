package com.everyonesniche.titantools.shizuku

import com.everyonesniche.titantools.core.OldApps
import com.everyonesniche.titantools.light.VendorKeys
import com.everyonesniche.titantools.light.VendorReply

/**
 * Every command Titan Tools runs through Shizuku, each a fixed argument list with checked numbers. There is no way to run
 * anything else, and nothing typed or sensed goes into a command except a checked whole number.
 */
object Shell {
    const val MAX_LEVEL = 255
    private const val SERVICE = "/system/bin/service"

    /**
     * Sets the keyboard light to [level] (0 is off) at once, through the firmware's own call,
     * AguiFunctionalService.keyboardLightTest, which writes /sys/devices/platform/keypad_led/keyled_brightness. True when the
     * firmware took it.
     */
    fun light(level: Int): Boolean {
        require(level in 0..MAX_LEVEL) { "Light level out of range" }
        val result = RemoteProcess.run(4_000, arrayOf(SERVICE, "call", "agui_functional_service", "7", "s16", level.toString()))
        return VendorReply.supported(result.out)
    }

    /** Reads one of the firmware's key light settings (VendorKeys), or [VendorKeys.MISSING] when it has none. */
    fun readVendor(key: String): String? {
        require(key == VendorKeys.TIMEOUT || key == VendorKeys.TIMEOUT_BACKUP || key == VendorKeys.LED)
        val result = RemoteProcess.run(4_000, arrayOf(SERVICE, "call", "agui_common", "3", "s16", key, "s16", VendorKeys.MISSING))
        return VendorReply.string(result.out)
    }

    /**
     * Writes the firmware's key light timeout or level (VendorKeys), the same write as old NightKeys, and reads it back to
     * check. Only checked numbers go in.
     */
    fun writeVendor(key: String, value: String): Boolean {
        require(when (key) {
            VendorKeys.TIMEOUT -> VendorKeys.validTimeout(value)
            VendorKeys.LED -> VendorKeys.validLed(value)
            else -> false
        }) { "Not a key light setting" }
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
