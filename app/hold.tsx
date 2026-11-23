import { Field, Screen, Section, SettingsChoices, Toggle, useSnapshot } from "ink";
import { open, set, state } from "../lib/titantools";
import { Note } from "../lib/note";

const times = [
  { value: "300", label: "Short" },
  { value: "400", label: "Medium" },
  { value: "500", label: "Long" },
  { value: "700", label: "Longer" },
];

export default function Hold() {
  const result = useSnapshot(state);
  if (result.status !== "ready") return <Screen title="Hold Symbols" />;
  const { hold, titanKeys } = result.data;
  return <Screen title="Hold Symbols" subtitle="Hold a letter key to type the symbol printed on it. Tap it for the letter.">
    {titanKeys.selected && <Note>TitanKeys is your keyboard and does holds itself, so this waits. It works again when you pick another keyboard.</Note>}
    <Section title="Accessibility">
      <Field label="Turn on in Accessibility" onPress={() => void open("accessibility")}>{hold.accessibility ? "On" : "Off"}</Field>
      {!hold.accessibility && <>
        <Note>Find Titan Tools: Hold Symbols in the list and turn it on. If Android says it's restricted, open App info, tap ⋮, choose Allow restricted settings, then try again.</Note>
        <Field label="Open App info" onPress={() => void open("appInfo")}>{""}</Field>
      </>}
    </Section>
    <Section title="Hold time">
      <SettingsChoices value={String(hold.millis)} options={times} onChange={value => void set("hold_millis", Number(value))} />
    </Section>
    <Section title="More">
      <Toggle label="Buzz when a symbol is typed" value={hold.vibrate} onChange={on => void set("hold_vibrate", on)} />
      <Toggle label="Also in password fields" subtitle="Off at first: mistakes there are hard to see." value={hold.passwords}
        onChange={on => void set("hold_passwords", on)} />
    </Section>
  </Screen>;
}
