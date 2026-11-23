package com.everyonesniche.titantools.scale;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Strictly reads Android wm output; never treats an error or missing value as a default. */
public final class WmOutput {
    private WmOutput() {}
    public static int[] size(String text) {
        int[] physical = matchSize(text, "Physical");
        int[] override = matchSize(text, "Override");
        int[] chosen = override != null ? override : physical;
        if (chosen == null || chosen[0] < 320 || chosen[1] < 320 || chosen[0] > 16384 || chosen[1] > 16384)
            throw new IllegalArgumentException("Unrecognized wm size output");
        return chosen;
    }
    private static int[] matchSize(String s, String label) {
        Matcher m = Pattern.compile("(?m)^\\s*"+label+" size:\\s*(\\d+)x(\\d+)\\s*$").matcher(s);
        if (!m.find()) return null;
        return new int[] {Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))};
    }
    public static int density(String text) {
        Integer physical = matchDensity(text, "Physical"), override = matchDensity(text, "Override");
        Integer chosen = override != null ? override : physical;
        if (chosen == null || chosen < 72 || chosen > 8192) throw new IllegalArgumentException("Unrecognized wm density output");
        return chosen;
    }
    public static boolean hasOverride(String text) { return matchDensity(text, "Override") != null; }
    private static Integer matchDensity(String s, String label) {
        Matcher m = Pattern.compile("(?m)^\\s*"+label+" density:\\s*(\\d+)\\s*$").matcher(s);
        return m.find() ? Integer.valueOf(m.group(1)) : null;
    }
}
