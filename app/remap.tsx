import { Field, Screen, Section, SettingsChoices, Toggle, useSnapshot } from "ink";
import { open, set, state } from "../lib/titantools";
import { Note } from "../lib/note";

const holdTimes = [400, 500, 700, 1000];

/** Keys for actions (docs/key-remap-plan.md): the switch, the hold time, and a row per key with what it does. */
export default function Remap() {
  const result = useSnapshot(state);
  if (result.status !== "ready") return <Screen title="Keys for actions" />;
  const { remap } = result.data;
  const summary = (k: typeof remap.keys[number]) => k.hold?.label ?? "Nothing";
  return <Screen title="Keys for actions"
    subtitle="Hold a letter to open an app or run an action. Only outside text boxes, and a quick press stays a quick press.">
    <Section title="Keys for actions">
      <Toggle label="Keys run their actions" value={remap.on} onChange={on => void set("remap_on", on)} />
      {!remap.service && <Field label="Turn on Titan Tools in Accessibility" onPress={() => void open("accessibility")}>To do</Field>}
      <Note>Titan Tools needs Accessibility to see the keys. In a text box, keys are left alone.</Note>
      <Note>{remap.shizuku
        ? "With Shizuku, a key with an action waits until you let go, so a quick press still types as usual."
        : "Without Shizuku, a held letter's first press still types before its action runs."}</Note>
    </Section>
    {remap.on && <Section title="Hold for">
      <SettingsChoices value={String(remap.holdMillis)} options={holdTimes.map(t => ({ value: String(t), label: `${t / 1000} seconds` }))}
        onChange={value => void set("remap_hold_millis", Number(value))} />
      <Note>How long to hold a letter before its action runs.</Note>
    </Section>}
    {remap.on && <Section title="Letters">
      {remap.keys.map(k => <Field key={k.code} label={k.name} href={`/remap/${k.code}`}>{summary(k)}</Field>)}
    </Section>}
  </Screen>;
}
