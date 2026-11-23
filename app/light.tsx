import { Screen, Section, SettingsChoices, useSnapshot } from "ink";
import { set, state, type LightWhen } from "../lib/titantools";
import { Note } from "../lib/note";

const levels = [4, 8, 11, 16, 24, 32, 48, 64, 96, 128, 160, 200, 255];
const levelLabel = (level: number) => level === 4 ? "4, dimmest" : level === 255 ? "255, brightest" : String(level);

const darkness = [1, 3, 5, 10, 20, 30, 50, 100];
const stays = [2, 5, 10, 30, 60];

export default function Light() {
  const result = useSnapshot(state);
  if (result.status !== "ready") return <Screen title="Keyboard light" />;
  const { light, titanKeys } = result.data;
  const typing = light.when === "typing";
  const whenOptions: { value: LightWhen; label: string }[] = [{ value: "screen", label: "Dark and screen on" }];
  if (titanKeys.installed || typing) whenOptions.push({ value: "typing", label: "Dark and typing" });
  return <Screen title="Keyboard light" subtitle={light.status}>
    <Section title="Light when">
      <SettingsChoices<LightWhen> value={light.when} options={whenOptions} onChange={value => void set("light_when", value)} />
      {typing && !light.linkSeen && <Note>Needs TitanKeys 3.1 or later as your keyboard. Until then, it's dark and screen on.</Note>}
      {typing && light.linkSeen && !light.linkAlive &&
        <Note>TitanKeys has been quiet for 10 minutes, so it's dark and screen on for now.</Note>}
      <Note>While this is on, Titan Tools runs the phone's own key light. Its settings go back when you turn this off.</Note>
    </Section>
    {typing && <Section title="Stay lit after typing">
      <SettingsChoices value={String(light.staySeconds)} options={stays.map(s => ({ value: String(s), label: `${s} seconds` }))}
        onChange={value => void set("light_stay_seconds", Number(value))} />
    </Section>}
    <Section title="Brightness">
      <SettingsChoices value={String(light.levelChoice)} options={levels.map(l => ({ value: String(l), label: levelLabel(l) }))}
        onChange={value => void set("light_level", Number(value))} />
      {light.level !== light.levelChoice && <Note>{`Now ${light.level}, set in the phone's own settings.`}</Note>}
    </Section>
    <Section title="Dark below">
      <SettingsChoices value={String(light.darkLux)} options={darkness.map(d => ({ value: String(d), label: `${d} lux` }))}
        onChange={value => void set("light_dark_lux", Number(value))} />
      <Note>The light sensor is at the top of the screen. The light goes off again once it's a bit brighter.</Note>
    </Section>
  </Screen>;
}
