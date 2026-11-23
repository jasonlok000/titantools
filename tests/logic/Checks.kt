import com.everyonesniche.titantools.core.OldApps
import com.everyonesniche.titantools.hold.HoldEngine
import com.everyonesniche.titantools.hold.HoldPause
import com.everyonesniche.titantools.hold.SymbolMap
import com.everyonesniche.titantools.light.LightLevels
import com.everyonesniche.titantools.light.LightPolicy
import com.everyonesniche.titantools.light.LightRule
import com.everyonesniche.titantools.light.LightRule.Link
import com.everyonesniche.titantools.light.TimeoutMigration
import com.everyonesniche.titantools.light.VendorReply
import com.everyonesniche.titantools.scale.ScaleMath
import com.everyonesniche.titantools.scale.ScaleSettings
import com.everyonesniche.titantools.scale.WmOutput
import kotlin.system.exitProcess

private var passed = 0
private val failures = mutableListOf<String>()

private fun check(name: String, ok: Boolean) {
    if (ok) passed++ else failures += name
}

private fun lightRule() {
    val now = 1_800_000_000_000L
    val none = Link()
    check("no link: dark and screen on lights", LightRule.lit(true, true, none, now))
    check("no link: bright stays off", !LightRule.lit(false, true, none, now))
    check("no link: screen off stays off", !LightRule.lit(true, false, none, now))
    check("no link: mode", LightRule.mode(none, now) == LightRule.Mode.DarkAndScreenOn)

    val typing = none.after(true, now - 1000, now)
    check("typing: link alive", LightRule.alive(typing, now))
    check("typing: follows TitanKeys", LightRule.mode(typing, now) == LightRule.Mode.FollowsTitanKeys)
    check("typing in the dark lights", LightRule.lit(true, true, typing, now))
    check("typing but bright stays off", !LightRule.lit(false, true, typing, now))
    check("typing but screen off stays off", !LightRule.lit(true, false, typing, now))

    val stopped = typing.after(false, now, now)
    check("hold-over: still lit 4.9 s after", LightRule.lit(true, true, stopped, now + 4_900))
    check("hold-over: off at 5 s", !LightRule.lit(true, true, stopped, now + 5_000))
    check("hold-over: next change at its end", LightRule.nextChange(stopped, now + 1000) == now + 5_000)
    check("link: alive at 9:59", LightRule.alive(stopped, now + 599_000))
    check("link: stale at 10:00", !LightRule.alive(stopped, now + 600_000))
    check("stale link: back to dark and screen on", LightRule.lit(true, true, stopped, now + 600_000))
    check("stale link: no next change", LightRule.nextChange(stopped, now + 600_000) == -1L)
    check("typing: next change is the link going stale", LightRule.nextChange(typing, now) == now - 1000 + LightRule.LINK_FRESH_MILLIS)

    check("older message ignored", stopped.after(true, now - 5000, now + 10) == stopped)
    val future = none.after(true, now + 60_000, now)
    check("a time from the future counts as now", future.lastAt == now)
    val zero = none.after(true, 0, now)
    check("a missing time counts as now", zero.lastAt == now && LightRule.alive(zero, now))
    check("a message from before a clock change isn't alive", !LightRule.alive(typing, now - 10_000))
}

private fun migration() {
    check("missing timeout: nothing", TimeoutMigration.restoreValue(TimeoutMigration.MISSING, "-1") == null)
    check("missing backup: nothing", TimeoutMigration.restoreValue("0", TimeoutMigration.MISSING) == null)
    check("unreadable: nothing", TimeoutMigration.restoreValue(null, "-1") == null)
    check("same: nothing", TimeoutMigration.restoreValue("30000", "30000") == null)
    check("changed by NightKeys to 0: backup back", TimeoutMigration.restoreValue("0", "-1") == "-1")
    check("left continuous: backup back", TimeoutMigration.restoreValue("2147483647", "10000") == "10000")
    check("garbage backup: nothing", TimeoutMigration.restoreValue("0", "abc") == null)
    check("out of range backup: nothing", TimeoutMigration.restoreValue("0", "-5") == null)
    check("too big backup: nothing", TimeoutMigration.restoreValue("0", "4294967296") == null)

    // While the tool is on, Titan Tools owns the light: timeout 0, the phone's value saved as the backup if there's none.
    val take = TimeoutMigration::takeWrites
    check("take: backup kept, timeout to 0", take("-1", "-1") == listOf(TimeoutMigration.Write(false, "0")))
    check("take: no backup, saved first", take("30000", TimeoutMigration.MISSING) ==
        listOf(TimeoutMigration.Write(true, "30000"), TimeoutMigration.Write(false, "0")))
    check("take: unusable backup, saved first", take("-1", "abc") ==
        listOf(TimeoutMigration.Write(true, "-1"), TimeoutMigration.Write(false, "0")))
    check("take: already 0, nothing", take("0", "-1").isEmpty())
    check("take: already 0 and no backup, nothing (never saves 0)", take("0", TimeoutMigration.MISSING).isEmpty())
    check("take: missing timeout never blocks", take(TimeoutMigration.MISSING, "-1").isEmpty())
    check("take: unreadable timeout never blocks", take(null, null).isEmpty())
    check("release: owned 0 goes back to -1", TimeoutMigration.restoreValue("0", "-1") == "-1")
    check("release after take: back to the saved value", TimeoutMigration.restoreValue("0", "30000") == "30000")
}

private fun vendorReplies() {
    check("reads 0", VendorReply.string("Result: Parcel(\t00000000 00000001 00000030  '........0...')") == "0")
    check("reads -1", VendorReply.string("Result: Parcel(\t00000000 00000002 0031002d 00000000 '........-.1.....')") == "-1")
    check("reads 100", VendorReply.string("Result: Parcel(\t00000000 00000003 00300031 00000030 '........1.0.0...')") == "100")
    check("null string", VendorReply.string("Result: Parcel(00000000 ffffffff '........')") == null)
    check("light call supported", VendorReply.supported("Result: Parcel(\t00000000 00000001   '........')"))
    check("light call refused", !VendorReply.supported("Result: Parcel(\t00000000 00000000   '........')"))
    check("light call error", !VendorReply.supported("Result: Parcel(ffffffb6 '....')"))
}

private fun holdPause() {
    check("TitanKeys pauses", HoldPause.pausedFor("com.titankeys/.keyboard.TitanKeysIme"))
    check("TitanKeys Debug pauses", HoldPause.pausedFor("com.titankeys.debug/com.titankeys.keyboard.TitanKeysIme"))
    check("SwiftKey doesn't", !HoldPause.pausedFor("com.touchtype.swiftkey/com.touchtype.KeyboardService"))
    check("a look-alike doesn't", !HoldPause.pausedFor("com.titankeysfake/.Ime"))
    check("none doesn't", !HoldPause.pausedFor(null))
}

private fun oldApps() {
    check("NightKeys running blocks the light", OldApps.blocks(OldApps.Tool.Light, installed = true, running = true))
    check("NightKeys installed but idle doesn't", !OldApps.blocks(OldApps.Tool.Light, installed = true, running = false))
    check("not installed doesn't", !OldApps.blocks(OldApps.Tool.Hold, installed = false, running = true))
    check("old Hold Symbols on blocks holds", OldApps.blocks(OldApps.Tool.Hold, installed = true, running = true))
    check("Scale Toggle never blocks", !OldApps.blocks(OldApps.Tool.Scale, installed = true, running = true))
    val installed = OldApps.installed { it == "com.jasonlok.titanhold" || it == "com.everyonesniche.nightkeys" }
    check("lists installed old apps in order", installed.map { it.name } == listOf("NightKeys", "Titan Hold Symbols"))
    check("old package ids", OldApps.all.map { it.packageName } ==
        listOf("com.everyonesniche.nightkeys", "dev.everyonesniche.scaletoggle", "com.jasonlok.titanhold"))
}

private fun reused() {
    // Scale Toggle 1.2.0: phone 300 DPI, tablet 286 DPI, at the owner's 1076 x 1200.
    val defaults = ScaleMath.Presets.DEFAULTS
    check("phone mode goes to tablet", ScaleMath.next(0, ScaleMath.widthDp(1076, 1200, 300), 300, defaults) == ScaleMath.COMPACT)
    check("tablet mode goes to phone", ScaleMath.next(0, ScaleMath.widthDp(1076, 1200, 286), 286, defaults) == ScaleMath.LARGE)
    check("tablet is 286 DPI", ScaleMath.density(1076, 1200, ScaleMath.COMPACT, defaults) == 286)
    check("phone is 300 DPI", ScaleMath.density(1076, 1200, ScaleMath.LARGE, defaults) == 300)
    check("every offered size pair is valid", listOf(0, 320, 344).all { p -> listOf(0, 270, 250).all { t -> ScaleSettings.problem(p, t) == null } })
    check("wm density override", WmOutput.density("Physical density: 400\nOverride density: 286\n") == 286)
    check("wm size", WmOutput.size("Physical size: 1076x1200\n").contentEquals(intArrayOf(1076, 1200)))

    // Hold Symbols 0.3.1: S types 4 when held, s when tapped.
    val out = StringBuilder()
    val engine = HoldEngine(object : HoldEngine.Output {
        override fun tap(keyCode: Int) { out.append('a' + (keyCode - HoldEngine.KEYCODE_A)) }
        override fun hold(keyCode: Int) { out.append(SymbolMap.defaults().forKeyCode(keyCode)) }
    })
    engine.threshold(400)
    val s = HoldEngine.KEYCODE_A + ('s' - 'a')
    check("hold: S down held back", engine.down(s, 1, 1000, false, false, true))
    engine.up(s, 1, 1100, false)
    check("hold: S down again held back", engine.down(s, 1, 2000, false, false, true))
    engine.up(s, 1, 2500, false)
    check("hold: tap s, hold 4", out.toString() == "s4")

    // NightKeys' policy: on below 10 lux, off above 15.
    val policy = LightPolicy(10f, 15f, 0, 250)
    policy.setEnabled(true)
    policy.setInteractive(true)
    policy.sample(5f, 0)
    check("policy: dark lights", policy.shouldLight())
    policy.sample(12f, 100)
    check("policy: between thresholds stays lit", policy.shouldLight())
    policy.sample(20f, 200)
    policy.tick(500)
    check("policy: bright turns off after 250 ms", !policy.shouldLight())
}

private fun levels() {
    check("low is 24", LightLevels.level("low") == 24)
    check("medium is 64", LightLevels.level("medium") == 64)
    check("high is 160", LightLevels.level("high") == 160)
    check("default is medium", LightLevels.level(null) == 64 && LightLevels.name("bright") == "medium")
    check("old low (8) stays low", LightLevels.nameFromOldLevel(8) == "low")
    check("old medium (24) stays medium", LightLevels.nameFromOldLevel(24) == "medium")
    check("old high (64) stays high", LightLevels.nameFromOldLevel(64) == "high")
    check("unknown old level is medium", LightLevels.nameFromOldLevel(5) == "medium")
    check("every level fits the firmware", LightLevels.LEVELS.values.all { it in 1..255 })
}

fun main() {
    levels()
    lightRule()
    migration()
    vendorReplies()
    holdPause()
    oldApps()
    reused()
    if (failures.isNotEmpty()) {
        failures.forEach { println("FAIL $it") }
        println("${failures.size} failed, $passed passed")
        exitProcess(1)
    }
    println("All $passed checks passed")
}
