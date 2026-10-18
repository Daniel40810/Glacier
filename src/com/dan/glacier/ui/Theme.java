package com.dan.glacier.ui;

import com.dan.fstyle.FTheme;

import java.awt.Color;

/**
 * Die Farben der Oberfläche aus FTheme (Bedienelemente in FTheme-Farben, die Szene hat ihren eigenen Look; aus
 * Haithabu). Liefert das Thema keine dunklen Farben, nimmt die App ihre Nacht über dem Lago Argentino, damit der Rand um
 * die Szene nicht hell aufleuchtet. Dazu die Farben des Werkbuchs: Eis, Spaltenblau, Gletschersee, Fels, Lenga-Rot.
 */
public final class Theme {
    private Theme() { }

    public static final Color NACHT = new Color(11, 18, 25), EIS = new Color(138, 211, 236), SPALTE = new Color(58, 143, 199),
            SEE = new Color(61, 177, 165), FELS = new Color(167, 149, 130), LENGA = new Color(210, 96, 47), SCHNEE = new Color(241, 246, 249);

    public static Color bg() { return pick(FTheme::getBackground, NACHT, Role.DARK); }
    public static Color surface() { return pick(FTheme::getSurface, new Color(18, 27, 36), Role.DARK); }
    public static Color ink() { return pick(FTheme::getText, new Color(230, 236, 240), Role.LIGHT); }
    public static Color muted() { return pick(FTheme::getTextMuted, new Color(143, 162, 174), Role.LIGHT); }
    public static Color accent() { return pick(FTheme::getAccent, EIS, Role.ANY); }
    public static Color status() { return new Color(8, 13, 18); }
    public static Color line() { return new Color(36, 51, 64); }

    private enum Role { DARK, LIGHT, ANY }

    private static Color pick(java.util.function.Function<FTheme, Color> f, Color fallback, Role role) {
        try {
            Color c = f.apply(FTheme.getInstance());
            if (c == null) return fallback;
            int lum = (c.getRed() * 3 + c.getGreen() * 6 + c.getBlue()) / 10;
            if (role == Role.DARK && lum > 70) return fallback;
            if (role == Role.LIGHT && lum < 120) return fallback;
            return c;
        } catch (RuntimeException | Error e) {
            return fallback;
        }
    }
}
