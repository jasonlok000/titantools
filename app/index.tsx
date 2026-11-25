import { useEffect } from "react";
import { Field, Screen, Section, Toggle, useSnapshot } from "ink";
import { open, openUpdate, presetName, ready, seenWhatsNew, set, shortcutsOn, shizukuStopped, state, uninstall } from "../lib/titantools";
import { whatsNew } from "../lib/whatsnew";
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
  const news = s.updates.whatsNew ? whatsNew(s.updates.whatsNew) : [];
  return <Screen title="Titan Tools" subtitle="Small tools for the Titan 2 Elite. Turn on what you want.">
    {s.updates.available && <Section title="Update available">
      <Field label={`Titan Tools ${s.updates.available} is out`} onPress={() => void openUpdate()}>Get it</Field>
      <Note>Titan Tools doesn't update itself. Get it opens the release page, where you download the new one.</Note>
    </Section>}
    {news.length > 0 && <Section title={`New in Titan Tools ${s.version}`}>
      {news.map(line => <Note key={line}>{line}</Note>)}
      <Field label="Got it" onPress={() => void seenWhatsNew()}>{""}</Field>
    </Section>}
    {stopped && <Section title="Shizuku stopped">
      <Note>Shizuku stops whenever the phone restarts. Start it again and your tools pick up where they were.</Note>
      <Field label="Start Shizuku again" onPress={() => void open("shizukuApp")}>{""}</Field>
    </Section>}
    {needsSetup && <Section title="Setup">
      <Field label="Set up Titan Tools" href="/setup">To do</Field>
      <Note>Three quick steps with Shizuku, the app that lets Titan Tools change the key light, the screen and the keys.</Note>
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

    <Section title="Keys for actions">
      <Field label="Hold a letter to open an app or run an action" href="/remap">
        {s.remap.on ? `${s.remap.keys.filter(k => k.hold).length} set` : "Off"}</Field>
    </Section>

    <Section title="Shortcuts">
      <Field label="Switch layout, rotate, flashlight and more" href="/shortcuts">{shortcuts ? `${shortcuts} on` : "None on"}</Field>
    </Section>

    {s.oldApps.length > 0 && <Section title="Remove the old apps">
      <Note>Titan Tools does what these did, so you can remove them. Two of the same tool fight each other. Your phone's key light setting is put back first.</Note>
      {s.oldApps.map(app => <Field key={app.packageName} label={`Remove ${app.name}`}
        onPress={() => void uninstall(app.packageName)}>{app.running ? "Running" : ""}</Field>)}
    </Section>}

    <Section title="TitanKeys">
      {s.titanKeys.installed && s.titanKeys.selected && s.light.linkAlive
        ? <>
          <Note>Working with TitanKeys. TitanKeys tells Titan Tools when you're typing, so the key light can come on only then, and hold-a-letter actions don't fire while you type.</Note>
          <Field label="Open TitanKeys" onPress={() => void open("titanKeysApp")}>{""}</Field>
        </>
        : s.titanKeys.installed
        ? <>
          <Note>{s.titanKeys.selected
            ? "TitanKeys is your keyboard. Type something with it and it links up, so the key light can come on only while you type, and hold-a-letter actions don't fire while you type."
            : "TitanKeys isn't your keyboard. Switch to it and the key light can come on only while you type, and hold-a-letter actions don't fire while you type."}</Note>
          <Field label="Open TitanKeys" onPress={() => void open("titanKeysApp")}>{""}</Field>
        </>
        : <>
          <Note>TitanKeys isn't installed. A keyboard made for the Titan 2 Elite. With it, the key light can come on only while you type, and hold-a-letter actions don't fire while you type.</Note>
          <Field label="Get it on Google Play" onPress={() => void open("titanKeysPage")}>{""}</Field>
        </>}
    </Section>
    <Section title="Updates">
      <Toggle label="Check for updates" subtitle="Every 3 hours, and whenever you open Titan Tools. While a new version is out, you get one notification a day."
        value={s.updates.on} onChange={on => void set("updates_on", on)} />
    </Section>
    <Note align="center">{`Titan Tools ${s.version}`}</Note>
  </Screen>;
}
