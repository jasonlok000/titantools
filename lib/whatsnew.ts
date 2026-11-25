/**
 * What's new, shown once on the home screen after updating to a build. Keyed by version code; a build with no entry of its
 * own shows the newest entry at or below it. Add one for every build handed out.
 */
const notes: Record<number, string[]> = {
  21: [
    "Update notices: Titan Tools now looks for new versions and tells you, with a link to the release page.",
    "Keyboard light: brightness and dark room are smooth sliders in percent; moving brightness lights the keys so you can see it.",
    "Keyboard light: shows how light your room is right now, and every choice says when the keys light.",
  ],
};

export function whatsNew(code: number): string[] {
  const at = Object.keys(notes).map(Number).filter(c => c <= code).sort((a, b) => b - a)[0];
  return at === undefined ? [] : notes[at];
}
