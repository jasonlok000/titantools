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
        require(key == VendorKeys.TIMEOUT || key == VendorKeys.TIMEOUT_BACKUP || key == VendorKeys.LED || key == VendorKeys.INTELLIGENT)
        val result = RemoteProcess.run(4_000, arrayOf(SERVICE, "call", "agui_common", "3", "s16", key, "s16", VendorKeys.MISSING))
        return VendorReply.string(result.out)
    }

    /**
     * Writes the firmware's key light timeout or level (VendorKeys), the same write as old NightKeys, and reads it back to
     * check. Only checked numbers go in.
     */
    fun writeVendor(key: String, value: String): Boolean {
        require(VendorKeys.valid(key, value)) { "Not a key light setting" }
        RemoteProcess.run(4_000, arrayOf(SERVICE, "call", "agui_common", "4", "s16", key, "s16", value.trim()))
        return readVendor(key) == value.trim()
    }

    /** Text field open: the phone's own flag, 1 while a keyboard is shown in a field (not with TitanKeys, which keeps it at 1). */
    fun inputOpen(): Boolean = RemoteProcess.run(3_000, arrayOf("/system/bin/getprop", "debug.sf.agui_input_status")).out.trim() == "1"

    /** Night mode's settings: Night Light, Extra dim and its strength. */
    fun putSecure(name: String, value: Int) {
        require((name in listOf("night_display_activated", "reduce_bright_colors_activated") && value in 0..1) ||
            (name == "reduce_bright_colors_level" && value in 0..100)) { "Not a night mode setting" }
        RemoteProcess.run(5_000, arrayOf("/system/bin/settings", "put", "secure", name, value.toString()))
    }

    /** Reads one of Night mode's secure settings, or null when it has none. */
    fun getSecure(name: String): Int? {
        require(name in listOf("night_display_activated", "reduce_bright_colors_activated", "reduce_bright_colors_level"))
        return RemoteProcess.run(4_000, arrayOf("/system/bin/settings", "get", "secure", name)).out.trim().toIntOrNull()
    }

    /** Night mode's dark theme: "yes" or "no". */
    fun uiNight(on: Boolean) {
        RemoteProcess.run(5_000, arrayOf("/system/bin/cmd", "uimode", "night", if (on) "yes" else "no"))
    }

    /** Kill app: the activity history, top first (only its "Hist" lines, so the output stays small). */
    fun activityHistory(): String = RemoteProcess.run(6_000,
        arrayOf("/system/bin/sh", "-c", "/system/bin/dumpsys activity activities | /system/bin/grep -E 'Hist +#[0-9]+: ActivityRecord'")).out

    /** Kill app: closes [packageName] completely. Checked against KillRule first; only a valid package name goes in. */
    fun forceStop(packageName: String) {
        require(com.everyonesniche.titantools.quick.KillRule.valid(packageName)) { "Not a package" }
        RemoteProcess.run(6_000, arrayOf("/system/bin/am", "force-stop", packageName))
    }

    /** Rotation: locks the screen at [rotation] (0 portrait, 1 landscape, 2 and 3 upside down), turning auto-rotate off. */
    fun lockRotation(rotation: Int) {
        require(rotation in 0..3) { "Not a rotation" }
        RemoteProcess.run(5_000, arrayOf("/system/bin/wm", "user-rotation", "lock", rotation.toString()))
        // Most apps ask for portrait, and Android follows the app over a locked rotation, so the screen stayed upright (owner's
        // phone, 2026-11-22). Fixed to the user's rotation, the screen turns whatever the app asks; portrait hands it back.
        fixedToUserRotation(rotation != 0)
    }

    /** Whether the screen follows the locked rotation even against what the app in front asks for. */
    fun fixedToUserRotation(on: Boolean) {
        RemoteProcess.run(5_000, arrayOf("/system/bin/wm", "fixed-to-user-rotation", if (on) "enabled" else "default"))
    }

    /** Rotation, turned off: puts back the phone's own auto-rotate (0 or 1) or locked rotation (0 to 3). */
    fun putRotationSetting(name: String, value: Int) {
        require((name == "accelerometer_rotation" && value in 0..1) || (name == "user_rotation" && value in 0..3)) { "Not a rotation setting" }
        RemoteProcess.run(5_000, arrayOf("/system/bin/settings", "put", "system", name, value.toString()))
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
