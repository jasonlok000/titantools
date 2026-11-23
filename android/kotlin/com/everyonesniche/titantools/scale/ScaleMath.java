package com.everyonesniche.titantools.scale;

/**
 * Two modes, each pinned to an exact logical density: phone mode (300 DPI since
 * 1.2.0; earlier versions aimed for 500 dp) and tablet mode (286 DPI). LARGE and
 * COMPACT are the stored mode tokens for phone and tablet mode, NOT promises about
 * the resulting Configuration.smallestScreenWidthDp. Since 1.1.0 advanced users may
 * replace either density with an exact DPI.
 */
public final class ScaleMath {
    public static final int LARGE = 500;
    public static final int COMPACT = 599;
    public static final int PHONE_DENSITY = 300;
    public static final int TABLET_DENSITY = 286;
    // 1.0.3/1.0.4 tablet density, still recognized as tablet mode after an upgrade.
    private static final int LEGACY_TABLET_DENSITY = 287;
    private static final int LEGACY_COMPACT = 600;
    private ScaleMath() {}

    /** Exact DPI chosen by the user for each mode; 0 keeps the default. */
    public static final class Presets {
        public static final Presets DEFAULTS = new Presets(0, 0);
        public final int phoneDpi, tabletDpi;
        public Presets(int phoneDpi, int tabletDpi) {
            String problem = ScaleSettings.problem(phoneDpi, tabletDpi);
            if (problem != null) throw new IllegalArgumentException(problem);
            this.phoneDpi = phoneDpi; this.tabletDpi = tabletDpi;
        }
        public int phone() { return phoneDpi > 0 ? phoneDpi : PHONE_DENSITY; }
        public int tablet() { return tabletDpi > 0 ? tabletDpi : TABLET_DENSITY; }
    }

    public static boolean isPreset(int value) { return value == LARGE || value == COMPACT; }

    public static int next(int savedWidth, int actualWidth, int actualDensity) {
        return next(savedWidth, actualWidth, actualDensity, Presets.DEFAULTS);
    }

    public static int next(int savedWidth, int actualWidth, int actualDensity, Presets p) {
        // Prefer the true applied density over either a rounded dp readout or
        // persistent state (e.g. after manual testing, an upgrade or process death).
        if (actualDensity == p.tablet()) return LARGE;
        if (actualDensity == p.phone()) return COMPACT;
        if (actualDensity == LEGACY_TABLET_DENSITY) return LARGE;
        // Phone mode before 1.2.0 was about 500 dp wide.
        if (Math.abs(actualWidth - LARGE) <= 2) return COMPACT;
        // Recognize nearby old tablet settings, including 1.0.2's 600-dp mode.
        if (Math.abs(actualWidth - COMPACT) <= 2
                || Math.abs(actualWidth - LEGACY_COMPACT) <= 2) return LARGE;
        if (savedWidth == LARGE) return COMPACT;
        if (savedWidth == COMPACT || savedWidth == LEGACY_COMPACT) return LARGE;
        return LARGE;
    }

    public static int density(int widthPixels, int heightPixels, int targetDp) {
        return density(widthPixels, heightPixels, targetDp, Presets.DEFAULTS);
    }

    public static int density(int widthPixels, int heightPixels, int targetDp, Presets p) {
        if (!isPreset(targetDp)) throw new IllegalArgumentException("Unknown scale preset");
        if (widthPixels < 320 || heightPixels < 320 || widthPixels > 16384 || heightPixels > 16384)
            throw new IllegalArgumentException("Invalid default display dimensions");
        // At the owner's 1076x1200, 287 DPI is 599.9 dp: Android rounds it to sw=600,
        // but Developer options and apps that measure width themselves see 599.
        // 286 DPI is 601.96 dp, clearly tablet everywhere, and was tested by the owner.
        // Phone mode: 300 DPI (about 574 dp at 1076 px) unless the user chose another.
        return targetDp == COMPACT ? p.tablet() : p.phone();
    }

    public static int widthDp(int widthPixels, int heightPixels, int density) {
        if (density <= 0) throw new IllegalArgumentException("Invalid display density");
        return (int) ((long) Math.min(widthPixels, heightPixels) * 160L / density);
    }
}
