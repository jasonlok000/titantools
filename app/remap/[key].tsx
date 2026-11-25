import { useEffect, useState } from "react";
import { Screen, Section, SettingsChoices, back, useRouteParams, useSnapshot } from "ink";
import { listApps, setRemap, state } from "../../lib/titantools";
import { Note } from "../../lib/note";

/** Titan Tools' own actions and the system's, by the ids KeyRemap.kt runs. */
const actions = [
  { value: "", label: "Nothing" },
  { value: "do:flashlight", label: "Flashlight" },
  { value: "do:playpause", label: "Play/Pause" },
  { value: "do:night", label: "Night mode" },
  { value: "do:rotate", label: "Rotate" },
  { value: "do:layout", label: "Switch layout" },
  { value: "do:kill", label: "Kill app" },
  { value: "global:home", label: "Home" },
  { value: "global:back", label: "Back" },
  { value: "global:recents", label: "Recent apps" },
  { value: "global:notifications", label: "Notifications" },
  { value: "global:quick", label: "Quick settings (Wi-Fi, Bluetooth, data)" },
  { value: "global:lock", label: "Lock screen" },
  { value: "global:screenshot", label: "Screenshot" },
];

/** One letter's hold action: an action, or an app to open. */
export default function RemapKey() {
  const { key } = useRouteParams("/remap/[key]");
  const code = Number(key);
  const result = useSnapshot(state);
  const [apps, setApps] = useState<{ id: string; label: string }[]>([]);
  useEffect(() => void listApps().then(setApps), []);
  const k = result.status === "ready" ? result.data.remap.keys.find(x => x.code === code) : undefined;
  if (result.status !== "ready" || !k) return <Screen title="Letter" />;
  const current = k.hold?.id ?? "";
  // Widget slots show their app: "Widget 1 – 1Valet".
  const widgets = [1, 2, 3, 4, 5].map(n => {
    const app = result.data.widgets.find(w => w.slot === n)?.app;
    return { value: `widget:${n}`, label: app ? `Widget ${n} – ${app}` : `Widget ${n}` };
  });
  const options = [...actions.slice(0, 7), ...widgets, ...actions.slice(7)];
  const pick = (action: string) => void setRemap(code, action).then(() => back());
  return <Screen title={`Hold ${k.name}`} subtitle={k.hold?.label ?? "Nothing yet"}>
    <Section title="Action">
      <SettingsChoices value={current} options={options} onChange={pick} />
      <Note>Wi-Fi, Bluetooth and mobile data can't be switched directly without Shizuku: Quick settings opens them.</Note>
    </Section>
    <Section title="Open an app">
      {apps.length === 0 ? <Note>Loading the apps…</Note>
        : <SettingsChoices value={current} options={apps.map(a => ({ value: a.id, label: a.label }))} onChange={pick} />}
    </Section>
  </Screen>;
}
