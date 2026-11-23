package com.everyonesniche.titantools.core

/** The three apps Titan Tools replaces. Pure, so tests can run it. */
object OldApps {
    enum class Tool(val id: String) { Light("light"), Scale("scale"), Hold("hold") }

    data class App(val tool: Tool, val packageName: String, val name: String)

    val all = listOf(
        App(Tool.Light, "com.everyonesniche.nightkeys", "NightKeys"),
        App(Tool.Scale, "dev.everyonesniche.scaletoggle", "Scale Toggle"),
        App(Tool.Hold, "com.jasonlok.titanhold", "Titan Hold Symbols"),
    )

    fun of(tool: Tool): App = all.first { it.tool == tool }

    /**
     * A tool waits while its old app is installed and running, so the two never act at once. Installed but idle is fine:
     * Scale Toggle only runs for a moment when pressed, so it never blocks.
     */
    fun blocks(tool: Tool, installed: Boolean, running: Boolean): Boolean = installed && running && tool != Tool.Scale

    /** The old apps still installed, in the order the home screen lists them. */
    fun installed(isInstalled: (String) -> Boolean): List<App> = all.filter { isInstalled(it.packageName) }
}
