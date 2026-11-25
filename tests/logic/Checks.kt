import com.everyonesniche.titantools.core.OldApps
import com.everyonesniche.titantools.hold.HoldEngine
import com.everyonesniche.titantools.hold.HoldPause
import com.everyonesniche.titantools.hold.SymbolMap
import com.everyonesniche.titantools.light.LightLevels
import com.everyonesniche.titantools.light.LightPolicy
import com.everyonesniche.titantools.light.LightRule
import com.everyonesniche.titantools.light.LightRule.Link
import com.everyonesniche.titantools.light.VendorKeys
import com.everyonesniche.titantools.light.VendorReply
import com.everyonesniche.titantools.rotate.RotationRule
import com.everyonesniche.titantools.quick.NightRule
import com.everyonesniche.titantools.quick.WidgetList
import com.everyonesniche.titantools.quick.KillRule
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
    // Darkness: any of the picked ones.
    val room = LightRule.Darkness(darkRoom = true, nightHours = false, darkTheme = false, anyTime = false)
    val night = LightRule.Darkness(darkRoom = false, nightHours = true, darkTheme = false, anyTime = false)
    val theme = LightRule.Darkness(darkRoom = false, nightHours = false, darkTheme = true, anyTime = false)
    val any = LightRule.Darkness(darkRoom = false, nightHours = false, darkTheme = false, anyTime = true)
    val nothing = LightRule.Darkness(false, false, false, false)
    fun facts(roomDark: Boolean = false, minute: Int = 12 * 60, themeDark: Boolean = false) =
        LightRule.Facts(roomDark, minute, 20 * 60, 7 * 60, themeDark)
    check("dark room: dark", LightRule.dark(room, facts(roomDark = true)))
    check("dark room: bright", !LightRule.dark(room, facts()))
    check("night hours: 23:00", LightRule.dark(night, facts(minute = 23 * 60)))
    check("night hours: 06:59", LightRule.dark(night, facts(minute = 7 * 60 - 1)))
    check("night hours: 07:00 is day", !LightRule.dark(night, facts(minute = 7 * 60)))
    check("night hours: noon is day", !LightRule.dark(night, facts()))
    check("hours within a day", LightRule.inHours(600, 540, 1020) && !LightRule.inHours(1020, 540, 1020))
    check("equal hours: never", !LightRule.inHours(600, 600, 600))
    check("dark theme", LightRule.dark(theme, facts(themeDark = true)) && !LightRule.dark(theme, facts()))
    check("any time", LightRule.dark(any, facts()))
    check("none picked: never", !LightRule.dark(nothing, facts(roomDark = true, minute = 23 * 60, themeDark = true)))
    check("any of several", LightRule.dark(room.copy(nightHours = true), facts(minute = 22 * 60)))

    // Where the activity comes from.
    check("screen on: screen", LightRule.source(LightRule.Activity.Screen, true, true) == LightRule.Source.Screen)
    check("field, other keyboard: the input flag", LightRule.source(LightRule.Activity.Field, false, true) == LightRule.Source.InputFlag)
    check("field, TitanKeys with the link: the link", LightRule.source(LightRule.Activity.Field, true, true) == LightRule.Source.LinkField)
    check("field, TitanKeys, link quiet: screen on", LightRule.source(LightRule.Activity.Field, true, false) == LightRule.Source.Screen)
    check("typing, TitanKeys with the link: the link", LightRule.source(LightRule.Activity.Typing, true, true) == LightRule.Source.LinkTyping)
    check("typing, TitanKeys, link quiet: key presses", LightRule.source(LightRule.Activity.Typing, true, false) == LightRule.Source.FirmwareKeys)
    check("typing, other keyboard: key presses", LightRule.source(LightRule.Activity.Typing, false, true) == LightRule.Source.FirmwareKeys)

    // Lit or not.
    check("screen: dark and on lights", LightRule.lit(LightRule.Source.Screen, true, true, none, now, 5000))
    check("screen off always off", !LightRule.lit(LightRule.Source.Screen, true, false, none, now, 5000))
    check("not dark: off", !LightRule.lit(LightRule.Source.Screen, false, true, none, now, 5000))
    val typing = none.after(true, now - 1000, now)
    check("typing: lit within the stay", LightRule.lit(LightRule.Source.LinkTyping, true, true, typing, now, 5000))
    check("typing: off after the stay", !LightRule.lit(LightRule.Source.LinkTyping, true, true, typing, now + 4000, 5000))
    check("typing: a 0 stay follows the flag", LightRule.lit(LightRule.Source.LinkTyping, true, true, typing, now + 4000, 0))
    val stopped = typing.after(false, now, now)
    check("typing stopped, 0 stay: off", !LightRule.lit(LightRule.Source.LinkTyping, true, true, stopped, now + 1, 0))
    check("typing again relights", LightRule.lit(LightRule.Source.LinkTyping, true, true, stopped.after(true, now + 20_000, now + 20_000), now + 20_000, 2000))
    check("field via link: open", LightRule.lit(LightRule.Source.LinkField, true, true, typing, now + 60_000, 2000))
    check("field via link: closed, within the stay", LightRule.lit(LightRule.Source.LinkField, true, true, stopped, now + 1000, 2000))
    check("field via link: closed, after the stay", !LightRule.lit(LightRule.Source.LinkField, true, true, stopped, now + 2000, 2000))
    check("input flag: open", LightRule.lit(LightRule.Source.InputFlag, true, true, none, now, 2000, fieldOpen = true))
    check("input flag: closed within the stay", LightRule.lit(LightRule.Source.InputFlag, true, true, none, now, 2000, false, now - 1000))
    check("input flag: closed after the stay", !LightRule.lit(LightRule.Source.InputFlag, true, true, none, now, 2000, false, now - 2000))
    check("key presses: Titan Tools never lights itself", !LightRule.lit(LightRule.Source.FirmwareKeys, true, true, typing, now, 5000))
    check("key presses: the level while dark", LightRule.firmwareLevelOn(LightRule.Source.FirmwareKeys, true, true))
    check("key presses: 0 when not dark or screen off",
        !LightRule.firmwareLevelOn(LightRule.Source.FirmwareKeys, false, true) && !LightRule.firmwareLevelOn(LightRule.Source.FirmwareKeys, true, false))
    check("key presses: at least a second", LightRule.firmwareTimeout(0) == 1000L && LightRule.firmwareTimeout(5000) == 5000L)
    check("next change: the stay's end", LightRule.nextChange(LightRule.Source.LinkTyping, typing, now, 5000) == now + 4000)
    check("next change: the link going quiet", LightRule.nextChange(LightRule.Source.LinkTyping, typing, now + 10_000, 5000) == now - 1000 + LightRule.LINK_FRESH_MILLIS)
    check("next change: none without the link", LightRule.nextChange(LightRule.Source.Screen, none, now, 5000) == -1L)

    // Link.
    check("no link yet: not seen", !none.seen && typing.seen)
    check("link: alive at 9:59", LightRule.alive(stopped, now + 599_000))
    check("link: quiet at 10:00", !LightRule.alive(stopped, now + 600_000))
    check("older message ignored", stopped.after(true, now - 5000, now + 10) == stopped)
    check("a time from the future counts as now", none.after(true, now + 60_000, now).lastAt == now)
    check("a missing time counts as now", none.after(true, 0, now).lastAt == now)
    val opened = none.after(true, now - 500, now, "field")
    check("a field opening doesn't light Typing", !LightRule.lit(LightRule.Source.LinkTyping, true, true, opened, now, 2000))
    check("a field opening lights Text field open", LightRule.lit(LightRule.Source.LinkField, true, true, opened, now, 2000))
    check("kind typing lights Typing", LightRule.lit(LightRule.Source.LinkTyping, true, true, opened.after(true, now, now, "typing"), now, 2000))
    val home = none.after(true, now - 500, now, "home")
    check("the launcher's empty search is home", home.onHome && !LightRule.lit(LightRule.Source.LinkTyping, true, true, home, now, 2000))
    check("typing in it ends home", !home.after(true, now, now, "typing").onHome)
    check("a plain field isn't home", !opened.onHome)
    check("no kind (older TitanKeys) still lights Typing", LightRule.lit(LightRule.Source.LinkTyping, true, true, none.after(true, now - 500, now), now, 2000))
    check("while typing: any light", LightRule.presetLists(LightRule.Preset.Typing)?.second == LightRule.Activity.Typing &&
        LightRule.presetLists(LightRule.Preset.Typing)?.first?.anyTime == true)
    check("a message from before a clock change isn't alive", !LightRule.alive(typing, now - 10_000))

    // Presets.
    check("night typing: dark room and typing", LightRule.presetLists(LightRule.Preset.NightTyping) == Pair(room, LightRule.Activity.Typing))
    check("always in the dark: dark room and screen on", LightRule.presetLists(LightRule.Preset.AlwaysInTheDark) == Pair(room, LightRule.Activity.Screen))
    check("phone's own and custom set nothing",
        LightRule.presetLists(LightRule.Preset.PhonesOwn) == null && LightRule.presetLists(LightRule.Preset.Custom) == null)
    check("default preset", LightRule.Preset.of(null) == LightRule.Preset.AlwaysInTheDark && LightRule.Activity.of("x") == LightRule.Activity.Screen)
}

private fun adoption() {
    check("adopt: the phone's own 11 while Titan Tools wrote 24", VendorKeys.adopt("11", "24") == 11)
    check("adopt: the phone's own 11 while Titan Tools wrote 0", VendorKeys.adopt("11", "0") == 11)
    check("adopt: never 0", VendorKeys.adopt("0", "24") == null)
    check("adopt: Titan Tools' own value isn't adopted", VendorKeys.adopt("24", "24") == null)
    check("adopt: nothing written yet, nothing adopted", VendorKeys.adopt("100", null) == null)
    check("adopt: missing or garbage, nothing", VendorKeys.adopt(VendorKeys.MISSING, "24") == null && VendorKeys.adopt("300", "24") == null)
}

private fun vendorKeys() {
    val W = VendorKeys::Write
    val T = VendorKeys.TIMEOUT
    val L = VendorKeys.LED
    val M = VendorKeys.MISSING
    // Saving the phone's own values: the timeout from the firmware's backup when usable, else the timeout itself.
    check("save: owner's phone", VendorKeys.toSave("0", "-1", "100") == VendorKeys.Saved("-1", "100"))
    check("save: no usable backup, the timeout", VendorKeys.toSave("30000", M, "100") == VendorKeys.Saved("30000", "100"))
    check("save: missing keys are null", VendorKeys.toSave(M, M, M) == VendorKeys.Saved(null, null))
    check("save: garbage is null", VendorKeys.toSave("x", "y", "300") == VendorKeys.Saved(null, null))
    // Owning: the automatic light off, the timeout, the level.
    val I = VendorKeys.INTELLIGENT
    check("own, lit", VendorKeys.ownWrites("0", "100", "1", "-1", "64") == listOf(W(I, "0"), W(T, "-1"), W(L, "64")))
    check("own, off", VendorKeys.ownWrites("-1", "64", "0", "-1", "0") == listOf(W(L, "0")))
    check("own, key presses: the stay as timeout", VendorKeys.ownWrites("-1", "0", "0", "5000", "64") == listOf(W(T, "5000"), W(L, "64")))
    check("own: already right, nothing", VendorKeys.ownWrites("-1", "24", "0", "-1", "24").isEmpty())
    check("own: missing keys never block", VendorKeys.ownWrites(M, M, M, "-1", "24").isEmpty())
    check("own: unreadable keys never block", VendorKeys.ownWrites(null, null, null, "-1", "0").isEmpty())
    check("level value", VendorKeys.ledValue(true, 24) == "24" && VendorKeys.ledValue(false, 24) == "0")
    check("save: the automatic light too", VendorKeys.toSave("0", "-1", "100", "1") == VendorKeys.Saved("-1", "100", "1"))
    check("restore: the automatic light too",
        VendorKeys.restoreWrites(VendorKeys.Saved("-1", "100", "1"), "-1", "24", "0") == listOf(W(L, "100"), W(I, "1")))
    // Giving back.
    val saved = VendorKeys.Saved("-1", "100")
    check("restore: both back", VendorKeys.restoreWrites(VendorKeys.Saved("30000", "100"), "-1", "0") == listOf(W(T, "30000"), W(L, "100")))
    check("restore: owner's phone, only the level", VendorKeys.restoreWrites(saved, "-1", "64") == listOf(W(L, "100")))
    check("restore: nothing saved for a key, left alone", VendorKeys.restoreWrites(VendorKeys.Saved(null, null), "-1", "64").isEmpty())
    check("restore: missing now, left alone", VendorKeys.restoreWrites(saved, M, M).isEmpty())
    // Old NightKeys' leftover timeout, before Titan Tools ever owned the keys.
    check("legacy: missing timeout, nothing", VendorKeys.legacyRestore(M, "-1") == null)
    check("legacy: missing backup, nothing", VendorKeys.legacyRestore("0", M) == null)
    check("legacy: same, nothing", VendorKeys.legacyRestore("30000", "30000") == null)
    check("legacy: changed by NightKeys to 0, backup back", VendorKeys.legacyRestore("0", "-1") == "-1")
    check("legacy: left continuous, backup back", VendorKeys.legacyRestore("2147483647", "10000") == "10000")
    check("legacy: out of range backup, nothing", VendorKeys.legacyRestore("0", "-5") == null)
    check("valid levels", VendorKeys.validLed("0") && VendorKeys.validLed("255") && !VendorKeys.validLed("256") && !VendorKeys.validLed("-1"))
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
    check("the choices", LightLevels.CHOICES == listOf(11, 16, 24, 32, 48, 64, 96, 128, 160, 200, 255))
    check("11 is the dimmest that lights", LightLevels.valid(11) && !LightLevels.valid(8))
    check("an old 4 or 8 becomes 11", LightLevels.usable(4) == 11 && LightLevels.usable(8) == 11 && LightLevels.usable(24) == 24)
    check("no level outside 1-255", LightLevels.usable(0) == null && LightLevels.usable(300) == null)
    check("default is 64", LightLevels.DEFAULT == 64)
    check("old Low is 24", LightLevels.fromName("low") == 24)
    check("old Medium is 64", LightLevels.fromName("medium") == 64)
    check("old High is 160", LightLevels.fromName("high") == 160)
    check("unknown name is the default", LightLevels.fromName("bright") == 64 && LightLevels.fromName(null) == 64)
    check("first build's 8 is Low, 24", LightLevels.fromOldLevel(8) == 24)
    check("first build's 24 is Medium, 64", LightLevels.fromOldLevel(24) == 64)
    check("first build's 64 is High, 160", LightLevels.fromOldLevel(64) == 160)
    check("an adopted 100 shows as 96", LightLevels.nearest(100) == 96)
    check("a choice shows as itself", LightLevels.nearest(11) == 11)
    check("every choice fits the firmware", LightLevels.CHOICES.all(LightLevels::valid) && !LightLevels.valid(0) && !LightLevels.valid(256))
}

private fun rotation() {
    check("portrait flips to landscape", RotationRule.next(0) == 1)
    check("landscape flips to portrait", RotationRule.next(1) == 0 && RotationRule.next(3) == 0)
    check("upside down goes to portrait", RotationRule.next(2) == 0)
    check("labels", RotationRule.label(0) == "Portrait" && RotationRule.label(1) == "Landscape" && RotationRule.label(3) == "Landscape")
    val saved = RotationRule.Saved(autoRotate = 1, rotation = 0)
    check("off: auto-rotate back on after a flip", RotationRule.restoreWrites(saved, 0, 1) ==
        listOf("user_rotation" to 0, "accelerometer_rotation" to 1))
    check("off: nothing changed, nothing written", RotationRule.restoreWrites(saved, 1, 0).isEmpty())
    check("off: a locked phone stays locked", RotationRule.restoreWrites(RotationRule.Saved(0, 0), 0, 1) == listOf("user_rotation" to 0))
    check("off: nothing saved, nothing written", RotationRule.restoreWrites(RotationRule.Saved(null, null), 0, 1).isEmpty())
    check("off: a bad saved value is skipped", RotationRule.restoreWrites(RotationRule.Saved(5, 9), 0, 1).isEmpty())
}

private fun nightMode() {
    val options = NightRule.Options(extraDim = true, dimLevel = 40, darkTheme = false)
    check("on: night light, strength, extra dim", NightRule.onWrites(options) ==
        listOf(NightRule.NIGHT_LIGHT to 1, NightRule.DIM_LEVEL to 40, NightRule.EXTRA_DIM to 1))
    check("on: without extra dim, only night light", NightRule.onWrites(options.copy(extraDim = false)) == listOf(NightRule.NIGHT_LIGHT to 1))
    check("on: strength kept in range", NightRule.onWrites(options.copy(dimLevel = 150))[1] == (NightRule.DIM_LEVEL to 100))
    val saved = NightRule.Saved(nightLight = 0, extraDim = 0, dimLevel = 70, darkTheme = false)
    check("off: all back", NightRule.offWrites(saved) == listOf(NightRule.NIGHT_LIGHT to 0, NightRule.DIM_LEVEL to 70, NightRule.EXTRA_DIM to 0))
    check("off: unknown left alone", NightRule.offWrites(NightRule.Saved(null, null, null, null)).isEmpty())
    check("off: the theme only if Night mode set it", NightRule.offTheme(saved, true) == false && NightRule.offTheme(saved, false) == null)
}

private fun widgetList() {
    fun e(id: String, slot: Int = 0) = WidgetList.Entry(id, "Door $id", 12, "1Valet", "Fob", "0/1", "0.5,0.5", 800, 300, slot)
    val list = listOf(e("w1", 1), e("w2"))
    check("widgets: a slot holds one button", WidgetList.assignSlot(list, "w2", 1).map { it.slot } == listOf(0, 1))
    check("widgets: none frees the slot", WidgetList.assignSlot(list, "w1", 0).map { it.slot } == listOf(0, 0))
    check("widgets: a bad slot is none", WidgetList.assignSlot(list, "w2", 9).map { it.slot } == listOf(1, 0))
    check("widgets: for a slot", WidgetList.forSlot(list, 1)?.id == "w1" && WidgetList.forSlot(list, 2) == null && WidgetList.forSlot(list, 0) == null)
    check("widgets: add", WidgetList.upsert(list, e("w3")).size == 3)
    check("widgets: replace", WidgetList.upsert(list, e("w2", 3)).map { it.slot } == listOf(1, 3))
    check("widgets: remove", WidgetList.remove(list, "w1").map { it.id } == listOf("w2"))
    check("widgets: names", WidgetList.defaultName("1Valet", "Fob") == "1Valet: Fob" &&
        WidgetList.defaultName("Clock", "the button you picked") == "Clock")
}

private fun killApp() {
    val dump = """
      * Hist  #0: ActivityRecord{a1 u0 com.everyonesniche.titantools/.quick.KillActivity t9}
      * Hist  #0: ActivityRecord{b2 u0 com.everyonesniche.titantools.kill/com.everyonesniche.titantools.helper.Go t8}
      * Hist  #1: ActivityRecord{c3 u0 com.android.deskclock/.DeskClock t7}
      * Hist  #0: ActivityRecord{d4 u0 com.android.launcher3/.Launcher t1}
    """
    check("kill: the app in front, past Titan Tools' own", KillRule.frontApp(dump) == "com.android.deskclock")
    check("kill: nothing listed", KillRule.frontApp("nothing") == null)
    val launchers = setOf("com.android.launcher3")
    check("kill: a normal app can go", !KillRule.protected("com.android.deskclock", "com.titankeys", launchers, false, setOf("com.android.dialer")))
    check("kill: never the keyboard", KillRule.protected("com.titankeys", "com.titankeys", launchers, false, emptySet()))
    check("kill: never home, system UI, Shizuku, Titan Tools",
        listOf("com.android.launcher3", "com.android.systemui", "moe.shizuku.privileged.api", "com.everyonesniche.titantools",
            "com.everyonesniche.titantools.rotate").all { KillRule.protected(it, null, launchers, false, emptySet()) })
    check("kill: the phone app only during a call", KillRule.protected("com.android.dialer", null, launchers, true, setOf("com.android.dialer")) &&
        !KillRule.protected("com.android.dialer", null, launchers, false, setOf("com.android.dialer")))
    check("kill: Settings may go", !KillRule.protected("com.android.settings", null, launchers, false, emptySet()))
    check("kill: keyboard package", KillRule.keyboardPackage("com.titankeys/.keyboard.TitanKeysIme") == "com.titankeys" && KillRule.keyboardPackage(null) == null)
    check("kill: package names", KillRule.valid("com.android.deskclock") && !KillRule.valid("com.x; rm -rf") && !KillRule.valid("nodots"))
}

private fun nightTyping() {
    val A = LightRule.Activity.Screen
    check("night typing: on and Night mode on gives Typing", LightRule.effectiveActivity(A, true, true) == LightRule.Activity.Typing)
    check("night typing: Night mode off keeps the rule", LightRule.effectiveActivity(A, true, false) == A)
    check("night typing: switch off keeps the rule", LightRule.effectiveActivity(LightRule.Activity.Field, false, true) == LightRule.Activity.Field)
}

fun main() {
    nightTyping()
    killApp()
    widgetList()
    nightMode()
    rotation()
    levels()
    adoption()
    lightRule()
    vendorKeys()
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
