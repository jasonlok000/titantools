import { useEffect } from "react";
import { Field, Screen, Section, back, useSnapshot } from "ink";
import { open, requestShizuku, state } from "../lib/titantools";
import { Note } from "../lib/note";

const status = (done: boolean) => done ? "Done" : "To do";

/**
 * One setup for every tool: install Shizuku, start it, allow Titan Tools. Each step ticks itself as it's done, and opens the
 * right place.
 */
export default function Setup() {
  const result = useSnapshot(state);
  useEffect(() => void state.refresh(), []);
  if (result.status !== "ready") return <Screen title="Set up" />;
  const { installed, running, allowed } = result.data.shizuku;
  return <Screen title="Set up" subtitle="Titan Tools works through Shizuku, a free app. Make each step say Done.">
    <Section title="Steps">
      <Field label="1. Install Shizuku" onPress={() => void open("shizukuStore")}>{status(installed)}</Field>
      <Field label="2. Start Shizuku" onPress={() => void open("shizukuApp")}>{status(running)}</Field>
      {installed && !running && <>
        <Note>In Shizuku, start it with Wireless debugging and follow its steps. You need Wi-Fi and Developer options.</Note>
        <Field label="Open Developer options" onPress={() => void open("developerOptions")}>{""}</Field>
        <Note>No Developer options? Open About phone and tap Build number seven times.</Note>
        <Field label="Open About phone" onPress={() => void open("aboutPhone")}>{""}</Field>
      </>}
      <Field label="3. Allow Titan Tools" onPress={() => void requestShizuku()}>{status(allowed)}</Field>
      {running && !allowed && <Note>Tap it, then tap Allow in Shizuku's question.</Note>}
    </Section>
    {allowed && <Section title="All set">
      <Note>Turn on the tools you want. After a restart, start Shizuku again: Titan Tools reminds you.</Note>
      <Field label="Back to the tools" onPress={() => back()}>{""}</Field>
    </Section>}
  </Screen>;
}
