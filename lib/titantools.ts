import { resource } from "ink";
import { callNative, onNativeMessage } from "ink/native";

export type LightActivity = "screen" | "field" | "typing";
export type LightPreset = "night_typing" | "always_dark" | "phone" | "custom";

export type State = {
  version: string;
  shizuku: { installed: boolean; running: boolean; allowed: boolean; everAllowed: boolean };
  light: {
    on: boolean; status: string; follows: boolean;
    /** The exact level, 1 to 255, and the choice nearest to it. They differ for a level adopted from the phone's own settings. */
    level: number; levelChoice: number; darkLux: number;
    /** "Light the keys when [darkness] and [activity]": the preset, the darkness choices (any of), the activity. */
    preset: LightPreset; activity: LightActivity;
    darkRoom: boolean; nightHours: boolean; darkTheme: boolean; anyTime: boolean;
    /** Night hours, in minutes from midnight. */
    nightFrom: number; nightTo: number;
    staySeconds: number;
    /** TitanKeys is the keyboard; Typing falls back to the firmware's key presses (no 0-second stay then). */
    titanKeysKeyboard: boolean; keyPresses: boolean;
    /** TitanKeys has sent a typing message (3.1 or later), and sent one in the last 10 minutes. */
    linkSeen: boolean; linkAlive: boolean;
  };
  scale: { on: boolean; status: string; phoneDpi: number; tabletDpi: number };
  hold: { on: boolean; status: string; accessibility: boolean; millis: number; vibrate: boolean; passwords: boolean };
  rotation: { on: boolean; status: string };
  flash: { on: boolean; lit: boolean };
  play: { on: boolean };
  /** Widget buttons: each a name, the widget's app, the picked button, its helper app slot (0 for none), and whether its widget is still there. */
  widgets: WidgetButton[];
  /** The helper apps, which Shortcut keys can pick: installed or not. */
  helpers: { packageName: string; label: string; installed: boolean }[];
  night: { on: boolean; active: boolean; extraDim: boolean; dimLevel: number; darkTheme: boolean };
  oldApps: { packageName: string; name: string; tool: "light" | "scale" | "hold"; running: boolean }[];
  titanKeys: { installed: boolean; selected: boolean };
};

/** The settings the screens can change, by their native names (Prefs.kt). */
export type Setting =
  | "light_on" | "scale_on" | "hold_on" | "rotation_on" | "flash_on" | "night_on" | "play_on" | "hold_vibrate" | "hold_passwords"
  | "light_level" | "light_dark_lux" | "light_stay_seconds" | "light_preset" | "light_activity"
  | "light_dark_room" | "light_night_hours" | "light_dark_theme" | "light_any_time" | "light_night_from" | "light_night_to"
  | "night_extra_dim" | "night_dim_level" | "night_dark_theme"
  | "hold_millis" | "phone_dpi" | "tablet_dpi";

/** The pages setup and the cards open. */
export type Page =
  | "shizukuStore" | "shizukuApp" | "developerOptions" | "aboutPhone" | "accessibility" | "appInfo" | "shortcutKeys"
  | "titanKeysPage" | "titanKeysApp" | "scaleNow";

const native = (operation: string, payload: object = {}) => callNative("titantools", operation, payload);

export const state = resource({
  key: () => ["titantools"],
  load: async (): Promise<State> => JSON.parse(await native("state")),
})();

// Shizuku coming or going, a tool's status changing, the window coming back: read it all again.
onNativeMessage("titantools-state", () => void state.refresh());

export async function set(key: Setting, value: boolean | number | string) {
  await native("set", { key, value });
  await state.refresh();
}

export const open = (target: Page) => native("open", { target });
export const requestShizuku = () => native("requestShizuku");
export const uninstall = (packageName: string) => native("uninstall", { packageName });

/** Every step of setup is done: Shizuku installed, running, and Titan Tools allowed in it. */
export const ready = (s: State) => s.shizuku.installed && s.shizuku.running && s.shizuku.allowed;

/** After a restart Shizuku stops: it was allowed before and is installed, but isn't running now. */
export const shizukuStopped = (s: State) => s.shizuku.installed && s.shizuku.everAllowed && !s.shizuku.running;

const presetNames: Record<LightPreset, string> = {
  night_typing: "Night typing", always_dark: "Always in the dark", phone: "Phone's own", custom: "Custom",
};
export const presetName = (preset: LightPreset) => presetNames[preset];

/** A time of day from minutes after midnight, as 20:00. */
export const clock = (minutes: number) =>
  `${String(Math.floor(minutes / 60)).padStart(2, "0")}:${String(minutes % 60).padStart(2, "0")}`;

/** How many of the Shortcuts are on, for the home card. */
export const shortcutsOn = (s: State) => [s.scale.on, s.rotation.on, s.flash.on, s.night.on, s.play.on].filter(Boolean).length + s.widgets.length;

export type WidgetButton = { id: string; name: string; app: string; button: string; slot: number; ok: boolean };

async function then<T>(work: Promise<T>) {
  await work;
  await state.refresh();
}
export const installHelper = (packageName: string) => native("installHelper", { packageName });
/** Opens the widget button setup: a new one without [id], else pick another button of that one. */
export const pickWidget = (id?: string) => native("widgetPick", { id: id ?? "" });
export const setWidgetSlot = (id: string, slot: number) => then(native("widgetSlot", { id, slot }));
export const renameWidget = (id: string, name: string) => then(native("widgetName", { id, name }));
export const removeWidget = (id: string) => then(native("widgetRemove", { id }));
/** Finds the button without pressing it ([dry]), or presses it. */
export const testWidget = (id: string, dry: boolean) => native("widgetTest", { id, dry });
