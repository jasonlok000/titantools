# Titan Tools

One app for three small tools on the Unihertz Titan 2 Elite, in place of NightKeys, Scale Toggle and Hold Symbols:

- **Keyboard light**: lights the keys in the dark. While it's on it owns the firmware's key light settings
  (`keyboard_led_brightness` = its level or 0, `keyboard_brightness_timeout` = -1), so the firmware's own key presses keep that
  level, and writes the light directly at each change (`service call agui_functional_service 7 s16 <level>`). The phone's own
  values are saved first and go back when the tool is off. With TitanKeys 3.1 or
  later as the keyboard it follows your typing; otherwise it stays on while it's dark and the screen is on.
- **Scale Toggle**: a long press of the side button switches between phone layout (300 DPI) and tablet layout (286 DPI).
- **Hold Symbols**: hold a letter to type the symbol printed on it. It waits while TitanKeys is the keyboard, which does holds itself.

All three work through [Shizuku](https://shizuku.rikka.app/). The app's setup walks through it.

## The link with TitanKeys

TitanKeys sends an explicit broadcast to `com.everyonesniche.titantools`: action `com.everyonesniche.titantools.action.TYPING`,
boolean extra `typing`, long extra `at` (System.currentTimeMillis()), with `sendBroadcast(intent, LINK)`. Both apps declare and
use the signature permission `com.everyonesniche.titantools.permission.LINK`. A message in the last 10 minutes means the link is
alive: the light is on while it's dark and you type, and for 5 seconds after typing stops.

## Build

In WSL (atlas-build, as dev): `bash /mnt/x/TitanTools/tools/build.sh [check|release]`. It builds in `~/build/titantools` with
TitanKeys' patched Ink and the NightKeys signing key. `bash tests/logic/run.sh` runs the logic checks on the JVM.
