import { useEffect, useState } from "react";
import { Field, Screen, Section, SettingsChoices, TextInput, back, useRouteParams, useSnapshot } from "ink";
import { pickWidget, removeWidget, renameWidget, setWidgetSlot, state, testWidget } from "../../lib/titantools";
import { Note } from "../../lib/note";

const slots = [0, 1, 2, 3, 4, 5].map(n => ({ value: String(n), label: n ? `Widget ${n}` : "None" }));

/** One widget button: its name, its helper app slot, the picked button, a test, and remove. */
export default function WidgetPage() {
  const { id } = useRouteParams("/widget/[id]");
  const result = useSnapshot(state);
  const widget = result.status === "ready" ? result.data.widgets.find(w => w.id === id) : undefined;
  const [name, setName] = useState("");
  useEffect(() => { if (widget) setName(widget.name); }, [widget?.name]);
  if (!widget) return <Screen title="Widget button" />;
  return <Screen title={widget.name} subtitle={`${widget.app}: ${widget.button}`}>
    <Section title="Name">
      <TextInput label="Name" value={name} onChange={setName} placeholder="Door" />
      {name.trim() !== widget.name && name.trim() !== "" && <Field label="Save the name" onPress={() => void renameWidget(widget.id, name)}>{""}</Field>}
    </Section>
    <Section title="Helper app slot">
      <SettingsChoices value={String(widget.slot)} options={slots} onChange={value => void setWidgetSlot(widget.id, Number(value))} />
      <Note>The Widget app with this number presses this button. A slot holds one button.</Note>
    </Section>
    <Section title="Button">
      {!widget.ok && <Note>The widget is gone. Pick it again.</Note>}
      <Field label="Pick another button" onPress={() => void pickWidget(widget.id)}>{""}</Field>
      <Field label="Find it (no press)" onPress={() => void testWidget(widget.id, true)}>{""}</Field>
      <Field label="Press it now" onPress={() => void testWidget(widget.id, false)}>{""}</Field>
      <Field label="Remove" onPress={() => { void removeWidget(widget.id); back(); }}>{""}</Field>
    </Section>
  </Screen>;
}
