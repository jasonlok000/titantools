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
  ...[1, 2, 3, 4, 5].map(n => ({ value: `widget:${n}`, label: `Widget ${n}` })),
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
  const result = useSnapshot(state);
  const [apps, setApps] = useState<{ id: string; label: string }[]>([]);
  useEffect(() => void listApps().then(setApps), []);
  if (result.status !== "ready") return <Screen title={`Hold ${key.toUpperCase()}`} />;
  const current = result.data.remap.keys[key]?.id ?? "";
  const pick = (action: string) => void setRemap(key, action).then(() => back());
  return <Screen title={`Hold ${key.toUpperCase()}`} subtitle={result.data.remap.keys[key]?.label ?? "Nothing yet"}>
    <Section title="Action">
      <SettingsChoices value={current} options={actions} onChange={pick} />
      <Note>Wi-Fi, Bluetooth and mobile data can't be switched directly without Shizuku: Quick settings opens them.</Note>
    </Section>
    <Section title="Open an app">
      {apps.length === 0 ? <Note>Loading the apps…</Note>
        : <SettingsChoices value={current} options={apps.map(a => ({ value: a.id, label: a.label }))} onChange={pick} />}
    </Section>
  </Screen>;
}
