package com.everyonesniche.titantools.quick

/**
 * The widget buttons, as many as wanted: each a name, a hosted widget, and the picked button in it. Each may sit in one of
 * the helper apps' slots, Widget 1 to Widget 5, which press it. Pure, so the checks can run it.
 */
object WidgetList {
    const val SLOTS = 5

    data class Entry(
        val id: String,
        val name: String,
        val widgetId: Int,
        val app: String,
        val button: String,
        /** The button's place in the widget (child indexes, "0/2/1"), and its middle as fractions of the widget ("0.5,0.4"). */
        val path: String?,
        val spot: String?,
        /** The size the widget was shown at when the button was picked. */
        val width: Int,
        val height: Int,
        /** The helper app slot, 1 to 5, or 0 for none. */
        val slot: Int = 0,
    )

    /** Adds [entry], or replaces the one with its id. */
    fun upsert(list: List<Entry>, entry: Entry): List<Entry> =
        if (list.any { it.id == entry.id }) list.map { if (it.id == entry.id) entry else it } else list + entry

    fun remove(list: List<Entry>, id: String): List<Entry> = list.filter { it.id != id }

    /** Puts [id] in [slot] (0 for none). A slot holds one button: whichever had it before loses it. */
    fun assignSlot(list: List<Entry>, id: String, slot: Int): List<Entry> {
        val wanted = if (slot in 1..SLOTS) slot else 0
        return list.map {
            when {
                it.id == id -> it.copy(slot = wanted)
                wanted != 0 && it.slot == wanted -> it.copy(slot = 0)
                else -> it
            }
        }
    }

    fun forSlot(list: List<Entry>, slot: Int): Entry? = if (slot in 1..SLOTS) list.firstOrNull { it.slot == slot } else null

    /** A name for a new button: the widget's app and the button, as "1Valet: Unlock". */
    fun defaultName(app: String, button: String): String = if (button.isBlank() || button == "the button you picked") app else "$app: $button"
}
