package com.everyonesniche.titantools.light

/**
 * Reads the string out of `service call agui_common 3 …`'s reply, as NightKeys did (BridgeClient.decodeParcelString). The reply
 * is a dump of 32-bit words: a status word, the string's length, then two UTF-16 characters per word. Null when it isn't one.
 */
object VendorReply {
    private val word = Regex("(?i)(?<![0-9a-f])[0-9a-f]{8}(?![0-9a-f])")

    fun string(raw: String?): String? {
        if (raw == null) return null
        val words = word.findAll(raw.replace(Regex("(?i)0x[0-9a-f]+:"), " ")).mapNotNull { it.value.toLongOrNull(16) }.toList()
        if (words.size < 2) return null
        val length = words[1]
        if (length == 0xffffffffL || length < 0 || length > 4096) return null
        val out = StringBuilder()
        var remaining = length.toInt()
        var i = 2
        while (i < words.size && remaining > 0) {
            out.append((words[i] and 0xffff).toInt().toChar())
            remaining--
            if (remaining > 0) {
                out.append(((words[i] ushr 16) and 0xffff).toInt().toChar())
                remaining--
            }
            i++
        }
        return if (remaining == 0) out.toString() else null
    }

    /** `service call agui_functional_service 7 …` answers a parcel whose second word is 1 when the light took the level. */
    fun supported(raw: String?): Boolean {
        val words = word.findAll(raw ?: return false).map { it.value }.toList()
        return words.size >= 2 && words[0] == "00000000" && words[1].toLongOrNull(16) == 1L
    }
}
