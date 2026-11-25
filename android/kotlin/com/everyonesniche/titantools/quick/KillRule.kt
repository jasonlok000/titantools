package com.everyonesniche.titantools.quick

/** Kill app's choices, pure so the checks can run them. */
object KillRule {
    private val record = Regex("""ActivityRecord\{\S+ u\d+ ([A-Za-z0-9_.]+)/""")
    private val validPackage = Regex("""[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+""")

    /**
     * The app in front, from `dumpsys activity activities`' history lines, top first: the first one that isn't TitanTools
     * or one of its helpers (whose screenless activities are on top while Kill app runs). Null when there's none.
     */
    fun frontApp(dump: String): String? = dump.lineSequence()
        .mapNotNull { record.find(it)?.groupValues?.get(1) }
        .firstOrNull { !it.startsWith(TITAN_TOOLS) }

    fun valid(packageName: String): Boolean = validPackage.matches(packageName)

    /** What never gets closed: the keyboard (closing it turns its Accessibility off), the home screen, the system UI, Shizuku, TitanTools and its helpers, and the phone app during a call. */
    fun protected(packageName: String, keyboard: String?, launchers: Set<String>, inCall: Boolean, phoneApps: Set<String>): Boolean =
        packageName == keyboard || packageName in launchers || packageName == "com.android.systemui" ||
            packageName == "moe.shizuku.privileged.api" || packageName.startsWith(TITAN_TOOLS) ||
            packageName == "android" || (inCall && packageName in phoneApps)

    /** The keyboard's package, from Settings.Secure.DEFAULT_INPUT_METHOD ("com.titankeys/.keyboard.TitanKeysIme"). */
    fun keyboardPackage(defaultInputMethod: String?): String? = defaultInputMethod?.substringBefore('/')?.takeIf { it.isNotEmpty() }

    private const val TITAN_TOOLS = "com.everyonesniche.titantools"
}
