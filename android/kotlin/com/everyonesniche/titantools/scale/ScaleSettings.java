package com.everyonesniche.titantools.scale;

/** Advanced DPI settings: validation and wording, free of Android UI for local tests. */
public final class ScaleSettings {
    public static final String PREFS = "scale_settings";
    public static final String PHONE_DPI = "phone_dpi";
    public static final String TABLET_DPI = "tablet_dpi";
    public static final int MIN_DPI = 200;
    public static final int MAX_DPI = 480;
    /** Many apps switch to their tablet layout at this smallest width. */
    public static final int TABLET_LAYOUT_DP = 600;
    private ScaleSettings() {}

    /** Returns null when the pair is usable; 0 means "default" for either mode. */
    public static String problem(int phoneDpi, int tabletDpi) {
        String range = rangeProblem(phoneDpi, tabletDpi);
        if (range != null) return range;
        int phone = phoneDpi > 0 ? phoneDpi : ScaleMath.PHONE_DENSITY;
        int tablet = tabletDpi > 0 ? tabletDpi : ScaleMath.TABLET_DENSITY;
        if (tablet >= phone) return "Tablet mode needs a lower DPI than phone mode (" + phone + ").";
        return null;
    }

    private static String rangeProblem(int phoneDpi, int tabletDpi) {
        if (phoneDpi != 0 && (phoneDpi < MIN_DPI || phoneDpi > MAX_DPI))
            return "Phone mode DPI must be between " + MIN_DPI + " and " + MAX_DPI + ".";
        if (tabletDpi != 0 && (tabletDpi < MIN_DPI || tabletDpi > MAX_DPI))
            return "Tablet mode DPI must be between " + MIN_DPI + " and " + MAX_DPI + ".";
        return null;
    }

    /** Parses a typed DPI; returns -1 for anything that is not a whole number. */
    public static int parse(String text) {
        if (text == null) return -1;
        String s = text.trim();
        if (s.isEmpty() || s.length() > 4) return -1;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) < '0' || s.charAt(i) > '9') return -1;
        return Integer.parseInt(s);
    }

    /** Short preview such as "602 dp wide · tablet layout". */
    public static String preview(int widthPixels, int heightPixels, int dpi) {
        if (dpi < MIN_DPI || dpi > MAX_DPI) return "Enter " + MIN_DPI + "–" + MAX_DPI;
        int dp = ScaleMath.widthDp(widthPixels, heightPixels, dpi);
        return dp + " dp wide · " + (dp >= TABLET_LAYOUT_DP ? "tablet layout" : "phone layout");
    }
}
