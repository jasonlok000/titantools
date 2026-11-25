# Key remapping: plan (owner, 2026-11-24)

**Goal:** hold a key to launch an app or run an action, reliably, without Shizuku. It replaces Key Mapper (buggy with TitanKeys,
hard to set up with Shizuku) and the phone's own key remapping (only triggers on the launcher's main page, not with the owner's
launcher).

## Rules from the owner

- **Only when TitanKeys isn't active**: no text field focused and no keyboard window (the bar isn't showing). While you type,
  every key stays TitanKeys'.
- **Long hold only.** A tap is never touched. Keep it simple and focused.
- **Actions:** launch an app, or change a setting. No key-to-key remapping.
- **The helper apps go** (TT - Switch layout, Rotate, Flashlight, Night mode, Play/Pause, Kill app, Widget 1–5). They exist so
  the stock remap had something to launch; a hold can run those actions natively.

## How

- **Where:** HoldService, TitanTools' Accessibility service, already filters keys (`onKeyEvent`). No Shizuku is needed to see
  keys, launch apps from an Accessibility service, or run its global actions.
- **Is TitanKeys active?** Both checks, either one means "active, do nothing":
  1. `getWindows()` has a `TYPE_INPUT_METHOD` window (the keyboard or its bar is on screen), and
  2. the focused node (`findFocus(FOCUS_INPUT)`) is editable.
  TitanKeys' existing TYPING broadcast (the LINK) can add a third signal if those miss a case.
- **A hold without Shizuku:** TitanTools can't re-send a key it swallowed (that needs Shizuku's KeyInjector), so it must not
  swallow the press. The press goes on to the app as now; when the key is still down after the hold time (repeats arrive), Titan
  Tools takes the repeats and the release and runs the action. **Check first on the phone** what the launcher does with that first
  press (it may open its search with the letter, which then hides behind the app being opened, or not).
- **Actions:** open an app (any launchable app, picked from a list); flashlight (CameraManager.setTorchMode); play/pause (media
  key through AudioManager); rotate and scale (as today); Night mode and the widget slots (TitanTools' own); Back, Home, Recents,
  notifications, lock screen, screenshot (Accessibility global actions); ringer and Do Not Disturb (needs Do Not Disturb access,
  one tap); brightness (Modify system settings, one tap).
  **Can't without Shizuku:** switching Wi-Fi, Bluetooth, mobile data or airplane mode directly; for Wi-Fi, Bluetooth and data
  open the quick panel instead.
- **Settings screen:** a list of letter keys, each with an action, and the hold time. Off by default.

## Done when

On the phone, with the owner's launcher: holding a mapped letter on the home screen runs its action every time; a tap still does
what it did; in any text field (and with the bar showing) nothing changes; TitanTools works with Shizuku off.
