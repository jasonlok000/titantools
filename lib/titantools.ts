import { resource } from "ink";
import { callNative, onNativeMessage } from "ink/native";

export type Level = "low" | "medium" | "high";

export type State = {
  version: string;
  shizuku: { installed: boolean; running: boolean; allowed: boolean; everAllowed: boolean };
  light: { on: boolean; status: string; follows: boolean; level: Level; darkLux: number };
  scale: { on: boolean; status: string; phoneDpi: number; tabletDpi: number };
  hold: { on: boolean; status: string; accessibility: boolean; millis: number; vibrate: boolean; passwords: boolean };
  oldApps: { packageName: string; name: string; tool: "light" | "scale" | "hold"; running: boolean }[];
  titanKeys: { installed: boolean; selected: boolean };
};

/** The settings the screens can change, by their native names (Prefs.kt). */
export type Setting =
  | "light_on" | "scale_on" | "hold_on" | "hold_vibrate" | "hold_passwords"
  | "light_level" | "light_dark_lux" | "hold_millis" | "phone_dpi" | "tablet_dpi";

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
