# Titan Tools

Small tools for the Unihertz Titan 2 Elite, in one app. Turn on the ones you want.

- **Keyboard light**: lights the keys when it's dark, or only while you type. With TitanKeys as your keyboard, it knows when you're typing or swiping.
- **Hold Symbols**: hold a letter to type the symbol printed on it. It steps aside while TitanKeys is your keyboard, which does holds itself.
- **Keys for actions**: hold a letter, A to Z, outside text boxes, to open an app or run an action. With Shizuku, a quick press still types as usual. Without Shizuku, a held letter's first press still types before its action runs.
- **Scale Toggle**: a long press of the side button flips between phone layout (300 DPI) and tablet layout.
- **Shortcuts**: switch layout, rotate, flashlight, Night mode, Play/Pause, Kill app and widget buttons, for the phone's Shortcut keys.

It takes the place of NightKeys, Scale Toggle and Titan Hold Symbols. If you have those, Titan Tools offers to remove them.

Most tools work through [Shizuku](https://shizuku.rikka.app/). The app's setup walks you through it in three steps.

## Download

Get the latest APK from [releases](https://github.com/jasonlok000/titantools/releases/latest). Titan Tools tells you when a new version is out.

## How the keyboard light works

While it's on, Titan Tools runs the phone's own key light settings (`keyboard_led_brightness` = its level or 0, `keyboard_brightness_timeout` = -1), so key presses keep that level, and it writes the light directly at each change (`service call agui_functional_service 7 s16 <level>`). The phone's own values are saved first and go back when you turn it off.

## The link with TitanKeys

TitanKeys sends an explicit broadcast to `com.everyonesniche.titantools`: action `com.everyonesniche.titantools.action.TYPING`, boolean extra `typing`, long extra `at` (System.currentTimeMillis()), and string extra `kind`, with `sendBroadcast(intent, LINK)`. `kind` says what the message is about: `field` (a text box opened), `typing` (a key, a swipe or a touchpad touch) or `home` (the home launcher's own empty search box, which doesn't count as typing). Older TitanKeys sends no `kind`. Both apps declare and use the signature permission `com.everyonesniche.titantools.permission.LINK`. A message in the last 10 minutes means the link is alive. The light then follows Keyboard light's settings (the keys stay lit 5 seconds after you stop typing, unless you change Stay lit after), and hold-a-letter actions don't fire while you type.

## Build

In WSL (atlas-build, as dev): `bash /mnt/x/TitanTools/tools/build.sh [check|release]`. It builds in `~/build/titantools` with TitanKeys' patched Ink and the NightKeys signing key. `bash tests/logic/run.sh` runs the logic checks on the JVM.
