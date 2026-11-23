import { useEffect } from "react";
import { Field, Screen, Section, Toggle, useSnapshot } from "ink";
import { open, presetName, ready, set, shortcutsOn, shizukuStopped, state, uninstall } from "../lib/titantools";
import { Note } from "../lib/note";

/** One card per tool: a switch with a plain status line, and its details behind one row. Shortcuts are one card of their own. */
export default function Home() {
  const result = useSnapshot(state);
  useEffect(() => void state.refresh(), []);
  if (result.status !== "ready") return <Screen title="Titan Tools" />;
  const s = result.data;
  const stopped = shizukuStopped(s);
  const needsSetup = !ready(s) && !stopped;
  const shortcuts = shortcutsOn(s);
  return <Screen title="Titan Tools" subtitle="Small tools for the Titan 2 Elite. Turn on the ones you want.">
    {stopped && <Section title="Shizuku stopped">
      <Note>Shizuku stops when the phone restarts. Start it again and your tools carry on.</Note>
      <Field label="Start Shizuku again" onPress={() => void open("shizukuApp")}>{""}</Field>
    </Section>}
    {needsSetup && <Section title="Setup">
      <Field label="Set up Titan Tools" href="/setup">To do</Field>
      <Note>Three quick steps with Shizuku, the app that lets Titan Tools change the light, the screen and keys.</Note>
    </Section>}

    <Section title="Keyboard light">
      <Toggle label="Light the keys" subtitle={s.light.status} value={s.light.on}
        onChange={on => void set("light_on", on)} />
      <Field label="When" href="/light">{presetName(s.light.preset)}</Field>
    </Section>

    <Section title="Hold Symbols">
      <Toggle label="Hold a letter for its symbol" subtitle={s.hold.status} value={s.hold.on}
        onChange={on => void set("hold_on", on)} />
      {s.hold.on && !s.hold.accessibility && !s.titanKeys.selected &&
        <Field label="Turn on in Accessibility" href="/hold">To do</Field>}
      <Field label="Hold time and more" href="/hold">{""}</Field>
    </Section>

    <Section title="Shortcuts">
      <Field label="Switch layout, rotate, flashlight and more" href="/shortcuts">{shortcuts ? `${shortcuts} on` : "None on"}</Field>
    </Section>

    {s.oldApps.length > 0 && <Section title="Remove the old apps">
      <Note>Titan Tools does what these did. Remove them so the two never run at once. Your keyboard light setting is put back first.</Note>
      {s.oldApps.map(app => <Field key={app.packageName} label={`Remove ${app.name}`}
        onPress={() => void uninstall(app.packageName)}>{app.running ? "Running" : ""}</Field>)}
    </Section>}

    <Section title="TitanKeys">
      {s.titanKeys.installed
        ? <>
          <Note>TitanKeys is installed. With TitanKeys as your keyboard, the light can follow your typing.</Note>
          <Field label="Open TitanKeys" onPress={() => void open("titanKeysApp")}>{""}</Field>
        </>
        : <>
          <Note>A keyboard made for the Titan 2 Elite: swipe on the keys, hold for symbols, typos fixed as you go.</Note>
          <Field label="Get TitanKeys" onPress={() => void open("titanKeysPage")}>{""}</Field>
        </>}
    </Section>
    <Note align="center">{`Titan Tools ${s.version}`}</Note>
  </Screen>;
}
