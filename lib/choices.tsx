import { Button, Stack } from "ink";
import { Note } from "./note";

export type DescribedChoice<Value extends string> = { value: Value; label: string; note: string };

/** Like ink's SettingsChoices, with a one-line description under each choice saying what it does. */
export function DescribedChoices<Value extends string>({ options, value, onChange }:
  { options: readonly DescribedChoice<Value>[]; value: Value; onChange: (value: Value) => void }) {
  return <Stack gap={40}>
    {options.map(option => <Stack key={option.value} gap={8}>
      <Button selected={option.value === value} onPress={() => onChange(option.value)}>{option.label}</Button>
      <Note>{option.note}</Note>
    </Stack>)}
  </Stack>;
}
