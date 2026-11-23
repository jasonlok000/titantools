import { resource } from "ink";
import { callNative, onNativeMessage } from "ink/native";

export type LightWhen = "screen" | "typing";

export type State = {
  version: string;
  shizuku: { installed: boolean; running: boolean; allowed: boolean; everAllowed: boolean };
  light: {
    on: boolean; status: string; follows: boolean;
    /** The exact level, 1 to 255, and the choice nearest to it. They differ for a level adopted from the phone's own settings. */
    level: number; levelChoice: number;
    darkLux: number; when: LightWhen; staySeconds: number;
    /** TitanKeys has sent a typing message (3.1 or later), and sent one in the last 10 minutes. */
    linkSeen: boolean; linkAlive: boolean;
  };
  scale: { on: boolean; status: string; phoneDpi: number; tabletDpi: number };
  hold: { on: boolean; status: string; accessibility: boolean; millis: number; vibrate: boolean; passwords: boolean };
  rotation: { on: boolean; status: string };
  oldApps: { packageName: string; name: string; tool: "light" | "scale" | "hold"; running: boolean }[];
  titanKeys: { installed: boolean; selected: boolean };
};

/** The settings the screens can change, by their native names (Prefs.kt). */
export type Setting =
  | "light_on" | "scale_on" | "hold_on" | "rotation_on" | "hold_vibrate" | "hold_passwords"
  | "light_level" | "light_dark_lux" | "light_when" | "light_stay_seconds" | "hold_millis" | "phone_dpi" | "tablet_dpi";

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

/** What the keyboard light follows, as the home screen's card and the light screen say it. */
export function lightWhenLabel(light: State["light"]): string {
  if (light.when === "screen") return "Dark and screen on";
  if (!light.linkSeen) return "Needs TitanKeys 3.1";
  if (!light.linkAlive) return "Dark and screen on (TitanKeys quiet)";
  return "Dark and typing";
}
