import { Screen, Section, SettingsChoices, Toggle, useSnapshot } from "ink";
import { set, state } from "../lib/titantools";
import { Note } from "../lib/note";

const strengths = [0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100];

/** Night mode's options: one press turns on Night Light, and these with it. */
export default function Night() {
  const result = useSnapshot(state);
  if (result.status !== "ready") return <Screen title="Night mode" />;
  const { night } = result.data;
  return <Screen title="Night mode" subtitle="One press turns on Night Light, and these with it. The next press puts it all back.">
    <Section title="With Night Light">
      <Toggle label="Extra dim" value={night.extraDim} onChange={on => void set("night_extra_dim", on)} />
      <Toggle label="Dark theme" value={night.darkTheme} onChange={on => void set("night_dark_theme", on)} />
    </Section>
    {night.extraDim && <Section title="Extra dim strength">
      <SettingsChoices value={String(Math.round(night.dimLevel / 10) * 10)}
        options={strengths.map(v => ({ value: String(v), label: v === 0 ? "0, least" : v === 100 ? "100, most" : String(v) }))}
        onChange={value => void set("night_dim_level", Number(value))} />
      <Note>Takes effect the next time Night mode goes on.</Note>
    </Section>}
  </Screen>;
}
