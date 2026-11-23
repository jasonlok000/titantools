import { Field, Screen, Section, SettingsChoices, useSnapshot } from "ink";
import { open, set, state } from "../lib/titantools";
import { Note } from "../lib/note";

const phone = [
  { value: "0", label: "300 DPI (standard)" },
  { value: "320", label: "320 DPI" },
  { value: "344", label: "344 DPI (bigger)" },
];

const tablet = [
  { value: "0", label: "286 DPI (standard)" },
  { value: "270", label: "270 DPI" },
  { value: "250", label: "250 DPI (smaller)" },
];

export default function Scale() {
  const result = useSnapshot(state);
  if (result.status !== "ready") return <Screen title="Scale Toggle" />;
  const scale = result.data.scale;
  return <Screen title="Scale Toggle" subtitle="Each press switches between phone layout and tablet layout.">
    <Section title="Side button">
      <Note>In Shortcut keys, set the side button's long press to open the app called Scale Toggle. It's there while this tool is on.</Note>
      <Field label="Open Shortcut keys" onPress={() => void open("shortcutKeys")}>{""}</Field>
      {scale.on && <Field label="Switch now" onPress={() => void open("scaleNow")}>{""}</Field>}
    </Section>
    <Section title="Phone mode">
      <SettingsChoices value={String(scale.phoneDpi)} options={phone} onChange={value => void set("phone_dpi", Number(value))} />
    </Section>
    <Section title="Tablet mode">
      <SettingsChoices value={String(scale.tabletDpi)} options={tablet} onChange={value => void set("tablet_dpi", Number(value))} />
      <Note>Sizes take effect on the next switch.</Note>
    </Section>
  </Screen>;
}
