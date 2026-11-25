import { useEffect, useState } from "react";
import { Field, Screen, Section, SettingsChoices, TextInput, useSnapshot } from "ink";
import { open, set, state } from "../lib/titantools";
import { Note } from "../lib/note";

/** The range Titan Tools accepts for a custom size (ScaleSettings). 0 is the phone's own size. */
const MIN = 200;
const MAX = 480;

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

/** One mode's size: a preset, or Custom, a typed number from 200 to 480. */
function Size({ title, setting, saved, presets, fallback }: {
  title: string; setting: "phone_dpi" | "tablet_dpi"; saved: number; presets: { value: string; label: string }[]; fallback: number;
}) {
  const isPreset = presets.some(p => p.value === String(saved));
  const [custom, setCustom] = useState(!isPreset);
  const [text, setText] = useState(isPreset ? "" : String(saved));
  const [problem, setProblem] = useState<string | null>(null);
  useEffect(() => { if (!isPreset) { setCustom(true); setText(String(saved)); } }, [saved]);
  const save = (dpi: number) => {
    setProblem(null);
    set(setting, dpi).catch((error: unknown) => setProblem(error instanceof Error ? error.message : String(error)));
  };
  const typed = Number(text.trim());
  const valid = text.trim() !== "" && Number.isInteger(typed) && typed >= MIN && typed <= MAX;
  return <Section title={title}>
    <SettingsChoices value={custom ? "custom" : String(saved)} options={[...presets, { value: "custom", label: "Custom" }]}
      onChange={value => {
        if (value === "custom") { setCustom(true); return; }
        setCustom(false);
        save(Number(value));
      }} />
    {custom && <TextInput label="DPI" value={text} onChange={value => { setText(value); setProblem(null); }} placeholder={String(fallback)} />}
    {custom && text.trim() !== "" && !valid && <Note>{`Enter a whole number from ${MIN} to ${MAX}.`}</Note>}
    {custom && valid && typed !== saved && <Field label={`Save ${typed} DPI`} onPress={() => save(typed)}>{""}</Field>}
    {problem && <Note>{problem}</Note>}
    <Note>{`Now ${saved || fallback} DPI. Lower numbers fit more on the screen.`}</Note>
  </Section>;
}

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
    <Size title="Phone mode" setting="phone_dpi" saved={scale.phoneDpi} presets={phone} fallback={300} />
    <Size title="Tablet mode" setting="tablet_dpi" saved={scale.tabletDpi} presets={tablet} fallback={286} />
    <Note>The phone's own size is 300 DPI. Tablet mode must be lower than phone mode. Sizes take effect on the next switch.</Note>
  </Screen>;
}
