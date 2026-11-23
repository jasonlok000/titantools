import { Screen, Section, SettingsChoices, useSnapshot } from "ink";
import { set, state, type Level } from "../lib/titantools";
import { Note } from "../lib/note";

const levels = [
  { value: "low" as const, label: "Low" },
  { value: "medium" as const, label: "Medium" },
  { value: "high" as const, label: "High" },
];

const darkness = [
  { value: "3", label: "Very dark" },
  { value: "10", label: "Dim" },
  { value: "30", label: "Evening indoors" },
];

export default function Light() {
  const result = useSnapshot(state);
  if (result.status !== "ready") return <Screen title="Keyboard light" />;
  const light = result.data.light;
  return <Screen title="Keyboard light" subtitle={light.status}>
    <Section title="Turns on">
      <Note>{light.follows
        ? "Following TitanKeys: in the dark, the light comes on while you type and goes off 5 seconds after."
        : "In the dark, the light stays on while the screen is on. With TitanKeys 3.1 or later as your keyboard, it follows your typing instead."}</Note>
    </Section>
    <Section title="Brightness">
      <SettingsChoices<Level> value={light.level} options={levels} onChange={value => void set("light_level", value)} />
    </Section>
    <Section title="Dark means">
      <SettingsChoices value={String(light.darkLux)} options={darkness} onChange={value => void set("light_dark_lux", Number(value))} />
      <Note>The light sensor is at the top of the screen. The light goes off again once it's a bit brighter.</Note>
    </Section>
  </Screen>;
}
