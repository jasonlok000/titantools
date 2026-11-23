import { Screen, Section, SettingsChoices, Toggle, useSnapshot } from "ink";
import { clock, set, state, type LightActivity, type LightPreset } from "../lib/titantools";
import { Note } from "../lib/note";

const presets: { value: LightPreset; label: string }[] = [
  { value: "night_typing", label: "Night typing" },
  { value: "always_dark", label: "Always in the dark" },
  { value: "phone", label: "Phone's own" },
  { value: "custom", label: "Custom" },
];

const activities: { value: LightActivity; label: string }[] = [
  { value: "screen", label: "Screen on" },
  { value: "field", label: "Text field open" },
  { value: "typing", label: "Typing" },
];

const levels = [4, 8, 11, 16, 24, 32, 48, 64, 96, 128, 160, 200, 255];
const levelLabel = (level: number) => level === 4 ? "4, dimmest" : level === 255 ? "255, brightest" : String(level);
const darkness = [1, 3, 5, 10, 20, 30, 50, 100];
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
      <SettingsChoices<LightPreset> value={light.preset} options={presets} onChange={value => void set("light_preset", value)} />
      {light.preset === "phone" && <Note>The phone's own key light settings are back in charge.</Note>}
    </Section>
    {custom && <Section title="When it's">
      <Toggle label="A dark room" value={light.darkRoom} onChange={on => void set("light_dark_room", on)} />
      <Toggle label="Night hours" subtitle={`${clock(light.nightFrom)} to ${clock(light.nightTo)}`} value={light.nightHours}
        onChange={on => void set("light_night_hours", on)} />
      <Toggle label="Dark theme on" value={light.darkTheme} onChange={on => void set("light_dark_theme", on)} />
      <Toggle label="Any time" value={light.anyTime} onChange={on => void set("light_any_time", on)} />
      {!light.darkRoom && !light.nightHours && !light.darkTheme && !light.anyTime && <Note>Pick at least one, or the keys never light.</Note>}
    </Section>}
    {custom && <Section title="And">
      <SettingsChoices<LightActivity> value={light.activity} options={activities} onChange={value => void set("light_activity", value)} />
      {light.activity === "typing" && light.keyPresses &&
        <Note>Without the TitanKeys link (TitanKeys 3.1 as your keyboard), key presses light the keys.</Note>}
    </Section>}
    {light.preset !== "phone" && light.activity !== "screen" && <Section title="Stay lit after">
      <SettingsChoices value={String(stay)} options={stays.map(s => ({ value: String(s), label: `${s} seconds` }))}
        onChange={value => void set("light_stay_seconds", Number(value))} />
      {light.activity === "typing" && light.keyPresses && <Note>No 0 with key presses: they keep the light on at least a second.</Note>}
    </Section>}
    {light.preset !== "phone" && <Section title="Brightness">
      <SettingsChoices value={String(light.levelChoice)} options={levels.map(l => ({ value: String(l), label: levelLabel(l) }))}
        onChange={value => void set("light_level", Number(value))} />
      {light.level !== light.levelChoice && <Note>{`Now ${light.level}, set in the phone's own settings.`}</Note>}
    </Section>}
    {light.preset !== "phone" && light.darkRoom && <Section title="Dark room below">
      <SettingsChoices value={String(light.darkLux)} options={darkness.map(d => ({ value: String(d), label: `${d} lux` }))}
        onChange={value => void set("light_dark_lux", Number(value))} />
    </Section>}
    {custom && light.nightHours && <Section title="Night from">
      <SettingsChoices value={String(light.nightFrom)} options={halfHours.map(m => ({ value: String(m), label: clock(m) }))}
        onChange={value => void set("light_night_from", Number(value))} />
    </Section>}
    {custom && light.nightHours && <Section title="Night until">
      <SettingsChoices value={String(light.nightTo)} options={halfHours.map(m => ({ value: String(m), label: clock(m) }))}
        onChange={value => void set("light_night_to", Number(value))} />
    </Section>}
    <Note>While this is on, Titan Tools runs the phone's own key light. Its settings go back when you turn this off.</Note>
  </Screen>;
}
