import { Text } from "ink";

/** The size of explanations, as in TitanKeys: smaller than a row and muted, so it never looks like something to press. */
export const NOTE_SIZE = 26;

export function Note({ children, align }: { children: string; align?: "start" | "center" }) {
  return <Text size={NOTE_SIZE} tone="muted" align={align}>{children}</Text>;
}
