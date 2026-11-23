package com.everyonesniche.titantools.hold

/** Hold Symbols steps aside while TitanKeys is the keyboard: TitanKeys does holds itself, and two would type twice. */
object HoldPause {
    private val titanKeys = listOf("com.titankeys/", "com.titankeys.debug/")

    /** [defaultInputMethod] is Settings.Secure.DEFAULT_INPUT_METHOD, such as "com.titankeys/.keyboard.TitanKeysIme". */
    fun pausedFor(defaultInputMethod: String?): Boolean =
        defaultInputMethod != null && titanKeys.any { defaultInputMethod.startsWith(it) }
}
