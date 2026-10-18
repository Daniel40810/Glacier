package com.dan.glacier.camera;

import com.dan.glacier.scene.Terrain;

import java.util.List;
import java.util.Locale;

/**
 * Die Ansichten auf den Tasten 1 bis 8 (F15 A; die 8 seit Phase 13). Seit Phase 2 stehen sie im echten Gelände (GLO-30): der Steg aus dem
 * Import (gelaende.properties, 300 m vor der Spitze auf der Halbinsel Magallanes), die Südflanke vom Ufer der
 * Brazo Rico, das Boot im Canal de los Témpanos, der Berg nordöstlich über dem Kanal [G]. „Auf dem Eis“ steht auf der
 * weißen Zunge (das Eis selbst kommt in Phase 5), „Unter Wasser“ folgt mit den Eisbergen in Phase 9. Die Augenhöhe gilt
 * über Grund, über dem See über dem Wasserspiegel.
 */
public final class Ansichten {
    /** Eine Ansicht: Taste, Name, Lage (x, z), Blickrichtung (Grad von Nord), Neigung (Grad nach unten), Augenhöhe über Grund, ab Phase. */
    public record Ansicht(int key, String name, double x, double z, double heading, double pitch, double eyeAboveGround, int phase) {
        @Override public String toString() { return key + " " + name; }
    }

    public static final List<Ansicht> ALLE = List.of(
            new Ansicht(1, "Vom Steg", 209, -216, 224, -2, 2.3, 1),   // seit Phase 10 auf dem Balkon (Deck 0,6 m über dem Hang)
            new Ansicht(2, "Südflanke", 0, 1800, 331, -2, 6, 1),
            new Ansicht(3, "Vom Boot", -300, -900, 195, -2, 3, 1),
            new Ansicht(4, "Auf dem Eis", -1500, 0, 90, 3, 1.7, 1),
            new Ansicht(5, "Brazo Rico", 400, 600, 330, -4, 6, 1),   // seit Phase 13 am Ufer der Halbinsel, Blick auf den Damm

            new Ansicht(6, "Vom Berg", 2500, -2000, 231, 12, 30, 1),
            new Ansicht(7, "Unter Wasser", 120, 0, 270, 8, -6, 9),
            new Ansicht(8, "Die Brücke", 209, -216, 267, -4, 2.3, 13));   // Phase 13: vom Balkon des Stegs auf die Brücke der Ruptura

    private static volatile Terrain terrain = Terrain.flat();

    /** Das Gelände, auf dem die Ansichten stehen (Renderer beim Start). */
    public static void use(Terrain t) { if (t != null) terrain = t; }

    /** Grund unter (x, z): Gelände, über dem See der Wasserspiegel. */
    /** Phase 5: Oberfläche des Gletschers (NaN, wo kein Eis ist); setzt die App, sobald der Gletscher steht. */
    public static volatile java.util.function.DoubleBinaryOperator iceSurface;
    /** Phase 9: Ort der Ansicht „Unter Wasser“ {x, z, Blick, Augenhöhe} vor der Front mit tiefem Wasser; null: noch keiner. */
    public static volatile double[] underwater;

    public static double ground(double x, double z) {
        java.util.function.DoubleBinaryOperator ice = iceSurface;
        if (ice != null) {
            double g = ice.applyAsDouble(x, z);
            if (!Double.isNaN(g)) return g;
        }
        Terrain t = terrain;
        double y = t.height(x, z);
        return t.sourceAt(x, z) == Terrain.SEE ? Math.max(0, y) : y;
    }

    /** Lage (x, z, Blick) der Ansicht; der Steg kommt aus dem Import, wenn das Gelände gemessen ist. */
    static double[] where(Ansicht a) {
        Terrain t = terrain;
        if (a.key() == 1 && t.measured()) return new double[]{t.steg[0], t.steg[1], t.steg[3]};
        if (a.key() == 8 && t.measured()) return new double[]{t.steg[0], t.steg[1], a.heading()};
        return new double[]{a.x(), a.z(), a.heading()};
    }

    /** Ob die Ansicht schon einen Ort hat („Unter Wasser“ erst mit den Eisbergen in Phase 9). */
    public static boolean ready(Ansicht a, int phase) { return a.phase() <= phase; }

    public static Ansicht byName(String name) {
        for (Ansicht a : ALLE) if (a.name().equalsIgnoreCase(name) || a.name().replace(" ", "").equalsIgnoreCase(name)) return a;
        return null;
    }

    public static Ansicht byKey(int key) {
        for (Ansicht a : ALLE) if (a.key() == key) return a;
        return null;
    }

    /** Stellt die Kamera auf die Ansicht; false, wenn sie in dieser Phase noch keinen Ort hat. */
    public static boolean apply(Ansicht a, Camera c, int phase) {
        if (!ready(a, phase)) return false;
        c.stopFahrt();
        // Phase 9: unter Wasser vor der Front, wo es tief ist (setzt die App, sobald die Welle steht)
        double[] u = underwater;
        c.diving = a.key() == 7;
        if (a.key() == 7 && u != null) {
            c.place(u[0], u[3], u[1], u[2], a.pitch());
            return true;
        }
        double[] w = where(a);
        c.place(w[0], ground(w[0], w[1]) + a.eyeAboveGround(), w[1], w[2], a.pitch());
        return true;
    }

    /** „4 Auf dem Eis (Eis, 99 m über dem See)“ */
    public static String describe(Ansicht a, int phase) {
        if (!ready(a, phase)) return a + " (folgt in Phase " + a.phase() + ")";
        double[] w = where(a);
        return String.format(Locale.GERMAN, "%s (%s, %.0f m über dem See)", a, zone(w[0], w[1]), ground(w[0], w[1]) + a.eyeAboveGround());
    }

    /** „Eis“, „See“, „Land“ unter (x, z). */
    public static String zone(double x, double z) {
        return switch (terrain.sourceAt(x, z)) { case Terrain.EIS -> "Eis"; case Terrain.SEE -> "See"; case Terrain.FELS -> "Land"; default -> "Rahmen"; };
    }

    private Ansichten() { }
}
