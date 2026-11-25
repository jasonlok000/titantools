import { useEffect, useState } from "react";
import { Field, Screen, Section, TextInput, useSnapshot } from "ink";
import { open, set, state } from "../lib/titantools";
import { Note } from "../lib/note";

/** The sizes the phone uses by itself (ScaleMath), and the range Titan Tools accepts (ScaleSettings). */
const PHONE_DEFAULT = 300;
const TABLET_DEFAULT = 286;
const MIN = 200;
const MAX = 480;

/** One mode's size: a number to type, saved with a button; empty means the phone's own size. */
function SizeInput({ title, setting, saved, fallback }: { title: string; setting: "phone_dpi" | "tablet_dpi"; saved: number; fallback: number }) {
  const [text, setText] = useState("");
  const [problem, setProblem] = useState<string | null>(null);
  useEffect(() => setText(saved ? String(saved) : ""), [saved]);
  const typed = text.trim() === "" ? 0 : Number(text.trim());
  const valid = text.trim() === "" || (Number.isInteger(typed) && typed >= MIN && typed <= MAX);
  const changed = typed !== saved;
  const save = () => {
    setProblem(null);
    set(setting, typed).catch((error: unknown) => setProblem(error instanceof Error ? error.message : String(error)));
  };
  return <Section title={title}>
    <TextInput label="DPI" value={text} onChange={value => { setText(value); setProblem(null); }} placeholder={String(fallback)} />
    {!valid && <Note>{`Enter a whole number from ${MIN} to ${MAX}, or leave it empty for ${fallback}.`}</Note>}
    {valid && changed && <Field label={`Save ${typed || fallback} DPI`} onPress={save}>{""}</Field>}
    {problem && <Note>{problem}</Note>}
    <Note>{`Now ${saved || fallback} DPI. Leave it empty for the standard ${fallback}. Lower numbers fit more on the screen.`}</Note>
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
    <SizeInput title="Phone mode" setting="phone_dpi" saved={scale.phoneDpi} fallback={PHONE_DEFAULT} />
    <SizeInput title="Tablet mode" setting="tablet_dpi" saved={scale.tabletDpi} fallback={TABLET_DEFAULT} />
    <Note>{`The phone's own size is ${PHONE_DEFAULT} DPI. Tablet mode must be lower than phone mode. Sizes take effect on the next switch.`}</Note>
  </Screen>;
}
