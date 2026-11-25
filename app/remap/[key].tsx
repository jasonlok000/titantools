import { useEffect, useState } from "react";
import { Field, Screen, Section, SettingsChoices, back, useRouteParams, useSnapshot } from "ink";
import { listApps, open, setRemap, state } from "../../lib/titantools";
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

/** One key's actions: what holding it does, and what pressing it twice does. */
export default function RemapKey() {
  const { key } = useRouteParams("/remap/[key]");
  const code = Number(key);
  const result = useSnapshot(state);
  const [apps, setApps] = useState<{ id: string; label: string }[]>([]);
  const [twice, setTwice] = useState(false);
  useEffect(() => void listApps().then(setApps), []);
  const k = result.status === "ready" ? result.data.remap.keys.find(x => x.code === code) : undefined;
  if (result.status !== "ready" || !k) return <Screen title="Key" />;
  const current = (twice && !k.letter ? k.double : k.hold)?.id ?? "";
  // Widget slots show their app: "Widget 1 – 1Valet".
  const widgets = [1, 2, 3, 4, 5].map(n => {
    const app = result.data.widgets.find(w => w.slot === n)?.app;
    return { value: `widget:${n}`, label: app ? `Widget ${n} – ${app}` : `Widget ${n}` };
  });
  const options = [...actions.slice(0, 7), ...widgets, ...actions.slice(7)];
  const pick = (action: string) => void setRemap(code, action, twice && !k.letter).then(() => back());
  return <Screen title={k.name} subtitle={k.letter ? `Hold: ${k.hold?.label ?? "nothing"}`
    : `Hold: ${k.hold?.label ?? "nothing"} · Twice: ${k.double?.label ?? "nothing"}`}>
    {!k.letter && <Section title="Phone setting">
      {k.native ? <Note>{k.native.ok ? `Ready: the phone has it as ${k.native.now}.`
        : `The phone has it as ${k.native.now}, so it acts on it too. Set ${k.native.fix} first.`}</Note>
        : <Note>{"If this is Func1 or Func2, set it to Programmable key > None in the phone's Shortcut keys first, or the phone acts on it too."}</Note>}
      {!(k.native?.ok) && <Field label="Open Shortcut keys" onPress={() => void open("shortcutKeys")}>{""}</Field>}
    </Section>}
    {!k.letter && <Section title="When">
      <SettingsChoices value={twice ? "twice" : "hold"} options={[{ value: "hold", label: "Held" }, { value: "twice", label: "Pressed twice" }]}
        onChange={value => setTwice(value === "twice")} />
    </Section>}
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
