package com.everyonesniche.titantools.hold;

/** Hold symbol per letter, stored as 26 characters in Titan row order. A space disables that key. */
public final class SymbolMap {
    public static final String LETTERS = "QWERTYUIOPASDFGHJKLZXCVBNM";
    public static final String DEFAULT = "0123()_-+@*456/:#'\"789?!,.";
    private final char[] byLetter = new char[26]; // index: letter - 'A'

    private SymbolMap(String rowOrder) {
        for (int i = 0; i < 26; i++) byLetter[LETTERS.charAt(i) - 'A'] = rowOrder.charAt(i);
    }
    public static SymbolMap defaults() { return new SymbolMap(DEFAULT); }
    /** Invalid or missing data falls back to the default map rather than guessing. */
    public static SymbolMap parse(String stored) {
        if (stored == null || stored.length() != 26) return defaults();
        for (int i = 0; i < 26; i++) {
            char c = stored.charAt(i);
            if (c != ' ' && !usable(c)) return defaults();
        }
        return new SymbolMap(stored);
    }
    public static boolean usable(char c) {
        return !Character.isWhitespace(c) && !Character.isISOControl(c) && !Character.isSurrogate(c);
    }
    /** @return the symbol for an Android KEYCODE_A..KEYCODE_Z, or 0 when none. */
    public char forKeyCode(int keyCode) {
        if (!HoldEngine.isLetter(keyCode)) return 0;
        char c = byLetter[keyCode - HoldEngine.KEYCODE_A];
        return c == ' ' ? 0 : c;
    }
    public char forLetter(char letter) {
        int i = Character.toUpperCase(letter) - 'A';
        return i < 0 || i >= 26 || byLetter[i] == ' ' ? 0 : byLetter[i];
    }
    public String serialize() {
        StringBuilder b = new StringBuilder(26);
        for (int i = 0; i < 26; i++) b.append(byLetter[LETTERS.charAt(i) - 'A']);
        return b.toString();
    }
    /** Copy with one letter changed; c == 0 or ' ' disables that letter. */
    public SymbolMap with(char letter, char c) {
        StringBuilder b = new StringBuilder(serialize());
        int i = LETTERS.indexOf(Character.toUpperCase(letter));
        if (i < 0) return this;
        b.setCharAt(i, c == 0 || !usable(c) ? ' ' : c);
        return new SymbolMap(b.toString());
    }
}
