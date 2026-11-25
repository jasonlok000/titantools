import { Field, Screen, Section, SettingsChoices, Toggle, useSnapshot } from "ink";
import { open, set, state } from "../lib/titantools";
import { Note } from "../lib/note";

const holdTimes = [400, 500, 700, 1000];

/** Keys for actions (docs/key-remap-plan.md): the switch, the hold time, and a row per key with what it does. */
export default function Remap() {
  const result = useSnapshot(state);
  if (result.status !== "ready") return <Screen title="Keys for actions" />;
  const { remap } = result.data;
  const summary = (k: typeof remap.keys[number]) =>
    [k.hold && `Hold: ${k.hold.label}`, k.double && `Twice: ${k.double.label}`].filter(Boolean).join(" · ") || "Nothing";
  return <Screen title="Keys for actions"
    subtitle="Hold a letter to open an app or run an action; special buttons like Func1 can also be pressed twice. Only while you're not typing; a click stays a click.">
    <Section title="Keys for actions">
      <Toggle label="Keys run their actions" value={remap.on} onChange={on => void set("remap_on", on)} />
      {!remap.service && <Field label="Turn on Titan Tools in Accessibility" onPress={() => void open("accessibility")}>To do</Field>}
      <Note>Needs Titan Tools in Accessibility to see the keys. While a text field is open, keys are left alone.</Note>
      <Note>{remap.shizuku
        ? "With Shizuku, a key with an action waits until you let go: a click then goes through as usual."
        : "Without Shizuku, a key's first press still goes through before its action runs."}</Note>
    </Section>
    {remap.on && <Section title="Hold for">
      <SettingsChoices value={String(remap.holdMillis)} options={holdTimes.map(t => ({ value: String(t), label: `${t / 1000} seconds` }))}
        onChange={value => void set("remap_hold_millis", Number(value))} />
      <Note>How long to hold a key before its action runs. Pressing it twice means two presses within 0.3 seconds.</Note>
    </Section>}
    {remap.on && remap.buttons.length > 0 && <Section title="Special buttons: setup">
      <Note>{"Before a special button gets an action here, the phone's own setting for it should be Programmable key > None, or the phone acts on it too."}</Note>
      {remap.buttons.map(b => <Field key={b.label} label={b.label} onPress={() => void open("shortcutKeys")}>{b.ok ? "Ready" : "Set up"}</Field>)}
      {remap.buttons.filter(b => !b.ok).map(b => <Note key={`${b.label}-fix`}>{`${b.label}: now ${b.now}. To use it here, set ${b.fix}.`}</Note>)}
      <Note>Tap a button to open the phone's Shortcut keys page.</Note>
    </Section>}
    {remap.on && <Section title="Keys">
      {remap.keys.map(k => <Field key={k.code} label={k.name} href={`/remap/${k.code}`}>{summary(k)}</Field>)}
    </Section>}
  </Screen>;
}
