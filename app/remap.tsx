import { Field, Screen, Section, SettingsChoices, Toggle, useSnapshot } from "ink";
import { open, set, state } from "../lib/titantools";
import { Note } from "../lib/note";

const letters = "abcdefghijklmnopqrstuvwxyz".split("");
const holdTimes = [400, 500, 700, 1000];

/** Hold a letter for an action (docs/key-remap-plan.md): the switch, the hold time, and a row per letter. */
export default function Remap() {
  const result = useSnapshot(state);
  if (result.status !== "ready") return <Screen title="Hold keys for actions" />;
  const { remap } = result.data;
  return <Screen title="Hold keys for actions"
    subtitle="Hold a letter to open an app or run an action. Only while you're not typing; a tap stays a tap.">
    <Section title="Hold keys">
      <Toggle label="Hold a letter for its action" value={remap.on} onChange={on => void set("remap_on", on)} />
      {!remap.service && <Field label="Turn on Titan Tools in Accessibility" onPress={() => void open("accessibility")}>To do</Field>}
      <Note>Needs Titan Tools in Accessibility to see the keys. In a text field, or while TitanKeys is typing, keys are left alone.</Note>
    </Section>
    {remap.on && <Section title="Hold for">
      <SettingsChoices value={String(remap.holdMillis)} options={holdTimes.map(t => ({ value: String(t), label: `${t / 1000} seconds` }))}
        onChange={value => void set("remap_hold_millis", Number(value))} />
      <Note>How long to hold a letter before its action runs.</Note>
    </Section>}
    {remap.on && <Section title="Letters">
      {letters.map(letter => <Field key={letter} label={letter.toUpperCase()} href={`/remap/${letter}`}>
        {remap.keys[letter]?.label ?? "Nothing"}</Field>)}
    </Section>}
  </Screen>;
}
