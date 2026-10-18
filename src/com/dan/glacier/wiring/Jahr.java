package com.dan.glacier.wiring;

import com.dan.season.Climate;
import com.dan.season.Year;

import java.time.LocalDate;
import java.util.Locale;

/**
 * Phase 11: das Jahr am Gletscher ({@code com.dan.season} mit dem Klima vor der Front) als {@link Season} für die
 * übrigen Packages. Je Bild bekommt es das Wetter des Augenblicks aus der Regie und gibt zurück:
 * <ul>
 * <li><b>Schnee am Ufer</b> (aus dem gewürfelten Jahr: im Juli liegt in manchen Wochen Schnee bis an den See);</li>
 * <li><b>Schneegrenze</b> auf den Hängen: wo das Monatsmittel (mit der Abweichung des Tags) bei 0,65 K je 100 m unter
 * +4,5 °C fällt (Abweichung der letzten zehn Tage) [G: so steht sie im Hochsommer bei rund 1 200 m, wie die
 * Gleichgewichtslinie des Gletschers];
 * liegt Schnee am Ufer, reicht sie bis an den See;</li>
 * <li><b>Schnee auf der Zunge</b> und in den Kronen nach der Schneegrenze, Laub und Lenga-Rot (über {@code forest});</li>
 * <li><b>Eis</b> in den flachen Buchten des Brazo Rico (Eisdicke und Grenze als Wassertiefe aus dem gewürfelten Jahr);</li>
 * <li><b>Schmelze</b> an der Wasserlinie (Kerbe) und <b>Kalbungsrate</b> nach der Jahreszeit;</li>
 * <li><b>Stau</b> des Brazo Rico als Jahresgang: in einem Jahr mit geschlossenem Eisdamm steigt der Arm ab Juni [G],
 * erst langsam (Winter, wenig Zufluss), im Frühjahr und Sommer schneller, bis der Damm im März bricht (der Bruch selbst
 * ist Phase 13). Ohne Damm 0.</li>
 * </ul>
 */
public final class Jahr implements Season {
    public final Climate klima = Climate.peritoMoreno();
    public final Year year = new Year(klima);
    /** Ein Jahr mit Eisdamm (der Brazo Rico staut sich); Vorgabe aus wie in den meisten Jahren [G]. */
    public volatile boolean dammJahr;
    /** Grenzen der Schneegrenze (m über dem See). */
    public static final double LINE_MAX = 1500, LAPSE = 0.0065, LINE_T = 4.5;
    /** Tag im Jahr, an dem der Damm schließt, und an dem er bricht (im Folgejahr) [G]. */
    public static final int CLOSE_DAY = 152, BREAK_DAY = 69;

    private volatile Year.State state;
    private volatile double snowLine = 1200, stau;
    private int doy = 16;

    public Jahr() {
        year.southern = true;
        year.seed = 2026;
    }

    /** Je Bild: Szenenzeit (s), Datum und Stunde, Wetter am See. */
    public void step(double sceneDt, LocalDate date, double hour, double airC, double precipMmH, double sunAltDeg, double oktas, double windMs) {
        doy = Math.min(365, date.getDayOfYear());
        Year.State s = year.step(sceneDt, date.getYear(), doy, hour, airC, precipMmH, sunAltDeg, oktas, windMs);
        state = s;
        // die Schneegrenze folgt dem Wetter der letzten zehn Tage, nicht dem einzelnen Tag
        double an = 0;
        for (int k = 0; k < 10; k++) an += year.anomaly(date.getYear(), doy - k);
        double t = klima.meanTemp(doy) + an / 10;
        double line = Math.max(0, Math.min(LINE_MAX, (t - LINE_T) / LAPSE));
        if (s.snowCover() > 0.15) line = 0;
        else line = Math.max(line, 60);   // ohne Schnee am Ufer bleibt der Saum frei
        snowLine = line;
        stau = dammJahr ? stauAt(doy) : 0;
    }

    /** Stau des Brazo Rico über dem Hauptsee (m) am Tag doy in einem Jahr mit Damm [G]. */
    public static double stauAt(int doy) {
        int d = doy >= CLOSE_DAY ? doy - CLOSE_DAY : doy + 365 - CLOSE_DAY;   // Tage seit dem Schließen
        int open = BREAK_DAY + 365 - CLOSE_DAY;                                // Tage bis zum Bruch
        if (d > open) return 0;                                                // gebrochen, bis der Damm wieder schließt
        // Zufluss: im Winter 4 cm am Tag, im Sommer 12 cm (Schmelze der Gletscher am Arm)
        double sum = 0;
        for (int i = 0; i < d; i++) {
            int day = (CLOSE_DAY + i - 1) % 365 + 1;
            double summer = 0.5 + 0.5 * Math.cos(2 * Math.PI * (day - 15) / 365.0);
            sum += 0.04 + 0.08 * summer;
        }
        return sum;
    }

    public Year.State state() { Year.State s = state; return s != null ? s : year.bare(doy); }

    /** Schneegrenze (m über dem See). */
    public double snowLine() { return snowLine; }
    /** Stau des Brazo Rico (m). */
    public double stau() { return stau; }

    /** Schnee auf der Zunge 0..1: voll, wenn die Schneegrenze unter 100 m liegt, nichts über 450 m. */
    public double glacierSnow() { return 0.85 * clamp((450 - snowLine) / 350); }
    /** Schnee in den Kronen 0..1 (der Wald reicht bis 700 m; Schnee im Wald unter der Schneegrenze nur aus dem Jahr). */
    public double forestSnow() { return Math.round(10 * Math.max(state().treeSnow(), 0.6 * clamp((350 - snowLine) / 300))) / 10.0; }   // in Zehnteln: der Wald backt nur bei Änderungen neu
    /** Schnee am Boden um das Auge 0..1. */
    public double groundSnow() { Year.State s = state(); return Math.round(20 * clamp(s.snowCover() * Math.min(1, s.snowDepth() / 0.03))) / 20.0; }

    /** Laub und Färbung der Lenga (aus {@code forest}, dessen Jahr auf der Nordhalbkugel zählt). */
    @Override public double foliage() { return lenga().foliage; }
    @Override public double autumn() { return lenga().autumn; }

    private static final com.dan.forest.Species LENGA = com.dan.forest.Species.lenga();
    private com.dan.forest.Season lenga() { return com.dan.forest.Season.of(LENGA, Wiring.northernDay(doy), 0); }
    @Override public double snowDepth() { return state().snowDepth(); }
    @Override public double iceReach() { return state().iceDepth(); }
    /** Schmelze an der Wasserlinie: das Wasser vor der Front ist im Sommer 2 bis 4 °C warm, im Winter um 1 °C [G]. */
    @Override public double melt() { return 0.25 + 0.75 * summer(doy); }
    @Override public double calvingRate() { return 0.6 + 0.8 * summer(doy); }

    static double summer(int doy) { return 0.5 + 0.5 * Math.cos(2 * Math.PI * (doy - 16) / 365.25); }
    static double clamp(double v) { return Math.max(0, Math.min(1, v)); }

    /** „Jahr: 16.1. · Luft 11,8 °C · Schneegrenze 1 120 m · kein Schnee am Ufer · Eis 0 cm · Lenga grün · Stau 0 m“ */
    public String describe() {
        Year.State s = state();
        double au = autumn(), fo = foliage();
        String lenga = fo < 0.2 ? "Lenga kahl" : au > 0.6 ? "Lenga rot" : au > 0.15 ? "Lenga färbt sich" : "Lenga grün";
        return String.format(Locale.GERMAN, "Jahr: Tag %d · Luft %.1f °C (Monatsmittel %.1f) · Schneegrenze %,.0f m · Schnee am Ufer %.0f cm · Eis im Brazo Rico %.0f cm bis %.1f m Tiefe · %s · Schmelze %.0f %%, Kalbung ×%.1f · Stau %s",
                doy, s.airTemp(), klima.meanTemp(doy), snowLine, s.snowDepth() * s.snowCover() * 100, s.ice() * 100, s.iceDepth(), lenga, melt() * 100, calvingRate(),
                dammJahr ? String.format(Locale.GERMAN, "%.1f m (Damm)", stau) : "0 m (kein Damm)");
    }
}
