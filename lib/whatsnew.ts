/**
 * What's new, shown once on the home screen after updating to a build. Keyed by version code; a build with no entry of its
 * own shows the newest entry at or below it. Add one for every build handed out.
 */
const notes: Record<number, string[]> = {
  42: [
    "Clearer words all through TitanTools.",
    "Keys for actions also leave number, phone and date boxes alone.",
    "Keyboard light: a room right at your dark setting now counts as dark.",
  ],
  41: [
    "Hold a letter for an action, on the home screen or in apps. With Shizuku, the letter doesn't type.",
    "Every action tells you what it did, like Flashlight on or Portrait, and on and off buzz differently.",
    "Scale Toggle: pick a preset size or type your own.",
  ],
  29: [
    "Keys for actions: hold a letter to open an app or run an action.",
    "Keyboard light: two new choices, In the dark while a text box is open, and While typing (or swiping). Typing now means pressing or swiping, not just a text box opening.",
    "A new version now also shows up when you open TitanTools.",
  ],
  23: [
    "Update notices: TitanTools now checks for new versions and tells you, with a link to the release page.",
    "Keyboard light: brightness and dark room are in percent now. Tap one to slide it, and brightness lights the keys as you go.",
    "Keyboard light: shows how bright your room is right now, and every choice says when the keys light.",
  ],
};

export function whatsNew(code: number): string[] {
  const at = Object.keys(notes).map(Number).filter(c => c <= code).sort((a, b) => b - a)[0];
  return at === undefined ? [] : notes[at];
}
