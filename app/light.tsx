import { Field, Screen, Section, SettingsChoices, Toggle, useSnapshot } from "ink";
import { clock, levelPercent, luxPercent, openSlider, presetName, set, state, type LightActivity, type LightPreset } from "../lib/titantools";
import { DescribedChoices } from "../lib/choices";
import { Note } from "../lib/note";

const presetIds: LightPreset[] = ["night_typing", "dark_field", "typing", "always_dark", "phone", "custom"];
/** One plain line for each preset: exactly when the keys light. */
const presetNotes: Record<LightPreset, string> = {
  night_typing: "Lit when it's dark and you're typing. Off the rest of the time.",
  dark_field: "Lit when it's dark and a text box is open.",
  typing: "Lit whenever you're typing, dark or not. Off the rest of the time.",
  always_dark: "Lit when it's dark and the screen is on.",
  phone: "Your phone's own key light settings. Titan Tools stays out of it.",
  custom: "Pick when, and what, below.",
};
const presets = presetIds.map(value => ({ value, label: presetName(value), note: presetNotes[value] }));

const activities: { value: LightActivity; label: string; note: string }[] = [
  { value: "screen", label: "Screen on", note: "Lit whenever the screen is on." },
  { value: "field", label: "Text box open", note: "Lit while a text box is open." },
  { value: "typing", label: "Typing", note: "Lit as you type, then off once Stay lit after runs out." },
];

const halfHours = Array.from({ length: 48 }, (_, i) => i * 30);

/** "Light the keys when [darkness] and [activity]", with presets on top. */
export default function Light() {
  const result = useSnapshot(state);
  if (result.status !== "ready") return <Screen title="Keyboard light" />;
  const { light } = result.data;
  const custom = light.preset === "custom";
  // Key presses light the keys for at least a second, so 0 is only there with the TitanKeys link.
  const stays = (light.activity === "typing" && light.keyPresses ? [1, 2, 5, 10, 30, 60] : [0, 1, 2, 5, 10, 30, 60]);
  const stay = stays.includes(light.staySeconds) ? light.staySeconds : 1;
  return <Screen title="Keyboard light" subtitle={light.status}>
    <Section title="Light the keys">
      <DescribedChoices<LightPreset> value={light.preset} options={presets} onChange={value => void set("light_preset", value)} />
    </Section>
    {custom && <Section title="When it's">
      <Note>The keys light if any of these is true.</Note>
      <Toggle label="A dark room" subtitle="Darker than Dark room below." value={light.darkRoom} onChange={on => void set("light_dark_room", on)} />
      <Toggle label="Night hours" subtitle={`${clock(light.nightFrom)} to ${clock(light.nightTo)}`} value={light.nightHours}
        onChange={on => void set("light_night_hours", on)} />
      <Toggle label="Dark theme on" subtitle="The phone's dark theme is on." value={light.darkTheme} onChange={on => void set("light_dark_theme", on)} />
      <Toggle label="Any time" subtitle="Day or night, bright or dark." value={light.anyTime} onChange={on => void set("light_any_time", on)} />
      {!light.darkRoom && !light.nightHours && !light.darkTheme && !light.anyTime && <Note>Pick at least one, or the keys never light.</Note>}
    </Section>}
    {custom && <Section title="And">
      <DescribedChoices<LightActivity> value={light.activity} options={activities} onChange={value => void set("light_activity", value)} />
      {light.activity !== "screen" && light.titanKeysKeyboard && !light.linkSeen &&
        <Note>Waiting for TitanKeys. Type something and it links up.</Note>}
      {light.activity === "typing" && light.keyPresses && !(light.titanKeysKeyboard && !light.linkSeen) &&
        <Note>Without TitanKeys, pressing a key counts as typing.</Note>}
    </Section>}
    {light.preset !== "phone" && light.nightTyping && <Note>While Night mode is on, the keys light only as you type, whatever you picked here. Change it in Night mode.</Note>}
    {light.preset !== "phone" && (light.activity !== "screen" || light.nightTyping) && <Section title="Stay lit after">
      <SettingsChoices value={String(stay)} options={stays.map(s => ({ value: String(s), label: `${s} seconds` }))}
        onChange={value => void set("light_stay_seconds", Number(value))} />
      <Note>How long the keys stay lit after you stop typing, or close the text box.</Note>
      {light.activity === "typing" && light.keyPresses && <Note>Without TitanKeys there's no 0: a key press keeps the light on for at least a second.</Note>}
    </Section>}
    {light.preset !== "phone" && <Section title="Brightness and darkness">
      <Field label="Brightness" onPress={() => void openSlider("level")}>{`${levelPercent(light.level)}%`}</Field>
      <Note>How bright the keys are when lit. Tap to slide it, and the keys light up so you can see.</Note>
      {light.darkRoom && <Field label="Dark room below" onPress={() => void openSlider("dark")}>{`${luxPercent(light.darkLux)}%`}</Field>}
      {light.darkRoom && <Note>{light.roomLux == null ? "Below this, the room counts as dark."
        : `Below this, the room counts as dark. Your room right now: ${luxPercent(light.roomLux)}%.`}</Note>}
    </Section>}
    {custom && light.nightHours && <Section title="Night from">
      <SettingsChoices value={String(light.nightFrom)} options={halfHours.map(m => ({ value: String(m), label: clock(m) }))}
        onChange={value => void set("light_night_from", Number(value))} />
    </Section>}
    {custom && light.nightHours && <Section title="Night until">
      <SettingsChoices value={String(light.nightTo)} options={halfHours.map(m => ({ value: String(m), label: clock(m) }))}
        onChange={value => void set("light_night_to", Number(value))} />
    </Section>}
    <Note>While this is on, Titan Tools runs the phone's key light. Turn it off and the phone's own settings come back.</Note>
  </Screen>;
}
