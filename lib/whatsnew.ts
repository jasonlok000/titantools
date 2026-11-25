/**
 * What's new, shown once on the home screen after updating to a build. Keyed by version code; a build with no entry of its
 * own shows the newest entry at or below it. Add one for every build handed out.
 */
const notes: Record<number, string[]> = {
  41: [
    "Hold a letter for an action, on the home screen and in apps. With Shizuku the letter never types.",
    "Every action says what it did, like Flashlight on or Portrait, and feels different for on and off.",
    "Scale Toggle: pick a preset size or type your own.",
  ],
  29: [
    "Keys for actions: hold a letter to open an app or run an action, like Key Mapper.",
    "Keyboard light: new modes, In the dark while a text box is open, and While typing (or swiping). Typing now means keys and swipes, not a box opening.",
    "A new version now also shows a message when you open Titan Tools.",
  ],
  23: [
    "Update notices: Titan Tools now looks for new versions and tells you, with a link to the release page.",
    "Keyboard light: brightness and dark room are in percent; tap one to slide it, and brightness lights the keys so you can see it.",
    "Keyboard light: shows how light your room is right now, and every choice says when the keys light.",
  ],
};

export function whatsNew(code: number): string[] {
  const at = Object.keys(notes).map(Number).filter(c => c <= code).sort((a, b) => b - a)[0];
  return at === undefined ? [] : notes[at];
}
