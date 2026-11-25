import { Field, Screen, Section, Toggle, useSnapshot } from "ink";
import { installHelper, open, pickWidget, set, state } from "../lib/titantools";
import { Note } from "../lib/note";

/**
 * One-press actions. The phone's Shortcut keys picks apps, so each action has a tiny helper app (installed from here), and
 * Titan Tools also offers Android shortcuts for the places that take them.
 */
export default function Shortcuts() {
  const result = useSnapshot(state);
  if (result.status !== "ready") return <Screen title="Shortcuts" />;
  const { scale, rotation, flash, night, play, kill, widgets, helpers } = result.data;
  const slotName = (slot: number) => slot ? `Widget ${slot}` : "No slot";
  return <Screen title="Shortcuts" subtitle="Turn one on, install its little app, then pick that app in Settings > Shortcut keys.">
    <Section title="Actions">
      <Toggle label="Switch layout" subtitle={scale.on ? "Phone and tablet layout" : "Off"}
        value={scale.on} onChange={on => void set("scale_on", on)} />
      {scale.on && <Field label="Layout sizes" href="/scale">{""}</Field>}
      <Toggle label="Rotate" subtitle={rotation.status} value={rotation.on} onChange={on => void set("rotation_on", on)} />
      <Toggle label="Flashlight" subtitle={!flash.on ? "Off" : flash.lit ? "Light on" : "Light off"} value={flash.on}
        onChange={on => void set("flash_on", on)} />
      <Toggle label="Night mode" subtitle={!night.on ? "Off" : night.active ? "On" : "Ready"} value={night.on}
        onChange={on => void set("night_on", on)} />
      {night.on && <Field label="Night mode options" href="/night">{""}</Field>}
      <Toggle label="Play/Pause" subtitle={play.on ? "Ready" : "Off"} value={play.on} onChange={on => void set("play_on", on)} />
      <Toggle label="Kill app" subtitle={kill.on ? "Fully closes the app you're in" : "Off"} value={kill.on}
        onChange={on => void set("kill_on", on)} />
    </Section>
    <Section title="Widget buttons">
      {widgets.map(w => <Field key={w.id} label={w.name} href={`/widget/${w.id}`}>{w.ok ? slotName(w.slot) : "Pick again"}</Field>)}
      <Field label="Add a widget button" onPress={() => void pickWidget()}>{""}</Field>
      <Note>Presses one button on any app's widget. Put it in a slot, then pick that slot's app in Shortcut keys.</Note>
    </Section>
    <Section title="Apps for Shortcut keys">
      {helpers.map(h => <Field key={h.packageName} label={h.label}
        onPress={h.installed ? undefined : () => void installHelper(h.packageName)}>{h.installed ? "Installed" : "Install"}</Field>)}
      <Note>Android asks once per app. If Shortcut keys wants a shortcut instead of an app, pick Titan Tools' shortcuts.</Note>
      <Field label="Open Shortcut keys" onPress={() => void open("shortcutKeys")}>{""}</Field>
    </Section>
  </Screen>;
}
