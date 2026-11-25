import { Field, Screen, Section, Toggle, useSnapshot } from "ink";
import { openSlider, set, state } from "../lib/titantools";
import { Note } from "../lib/note";


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
      <Field label="Extra dim strength" onPress={() => void openSlider("dim")}>{`${night.dimLevel}%`}</Field>
      <Note>How much Extra dim darkens the screen. Tap to slide it; while Night mode is on, it changes as you go.</Note>
    </Section>}
    {result.data.light.on && result.data.light.preset !== "phone" && <Section title="Keyboard light">
      <Toggle label="With Night mode on: only while typing" value={result.data.light.nightTyping}
        onChange={on => void set("light_night_typing", on)} />
      <Note>While Titan Tools' Night mode is on, the keys light only as you type, whatever you picked on the Keyboard light screen.</Note>
    </Section>}
  </Screen>;
}
