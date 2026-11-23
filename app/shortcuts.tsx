import { Field, Screen, Section, Toggle, useSnapshot } from "ink";
import { open, set, state } from "../lib/titantools";
import { Note } from "../lib/note";

/** One-press actions. Each one that's on shows up as an app to pick in the phone's Shortcut keys, for the side button or Sym/fn. */
export default function Shortcuts() {
  const result = useSnapshot(state);
  if (result.status !== "ready") return <Screen title="Shortcuts" />;
  const { scale, rotation, flash, night, play, widget } = result.data;
  return <Screen title="Shortcuts" subtitle="Turn one on, then pick it in Settings > Shortcut keys.">
    <Section title="Actions">
      <Toggle label="Switch layout" subtitle={scale.on ? scale.status.replace("On: long-press the side button", "Phone and tablet layout") : "Off"}
        value={scale.on} onChange={on => void set("scale_on", on)} />
      {scale.on && <Field label="Layout sizes" href="/scale">{""}</Field>}
      <Toggle label="Rotate" subtitle={rotation.status} value={rotation.on} onChange={on => void set("rotation_on", on)} />
      <Toggle label="Flashlight" subtitle={!flash.on ? "Off" : flash.lit ? "Light on" : "Light off"} value={flash.on}
        onChange={on => void set("flash_on", on)} />
      <Toggle label="Night mode" subtitle={!night.on ? "Off" : night.active ? "On" : "Ready"} value={night.on}
        onChange={on => void set("night_on", on)} />
      {night.on && <Field label="Night mode options" href="/night">{""}</Field>}
      <Toggle label="Play/Pause" subtitle={play.on ? "Ready" : "Off"} value={play.on} onChange={on => void set("play_on", on)} />
      <Toggle label="Widget button" subtitle={widget.picked ? `${widget.app}: ${widget.button}` : "Presses one button of any widget"}
        value={widget.on} onChange={on => void set("widget_on", on)} />
      {widget.on && <Field label={widget.picked ? "Pick another button" : "Pick a widget"}
        onPress={() => void open("widgetSetup")}>{""}</Field>}
      {widget.on && widget.picked && <Field label="Pick another widget" onPress={() => void open("widgetNew")}>{""}</Field>}
    </Section>
    <Section title="Shortcut keys">
      <Field label="Open Shortcut keys" onPress={() => void open("shortcutKeys")}>{""}</Field>
      <Note>Rotate and Night mode put your own settings back when you turn them off here.</Note>
    </Section>
  </Screen>;
}
