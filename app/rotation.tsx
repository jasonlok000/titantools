import { Field, Screen, Section, useSnapshot } from "ink";
import { open, state } from "../lib/titantools";
import { Note } from "../lib/note";

export default function RotationPage() {
  const result = useSnapshot(state);
  if (result.status !== "ready") return <Screen title="Rotation" />;
  return <Screen title="Rotation" subtitle="Each press flips the screen between portrait and landscape, and keeps it there.">
    <Section title="Quick Settings">
      <Note>Pull down Quick Settings, tap the pencil and add the Rotation tile. It's there while this tool is on.</Note>
    </Section>
    <Section title="Side button">
      <Note>In Shortcut keys, set a press of the side button to open the app called Rotate.</Note>
      <Field label="Open Shortcut keys" onPress={() => void open("shortcutKeys")}>{""}</Field>
    </Section>
    <Note>Turning this off puts back your own auto-rotate setting.</Note>
  </Screen>;
}
