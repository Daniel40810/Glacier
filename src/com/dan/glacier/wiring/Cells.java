package com.dan.glacier.wiring;

/**
 * Ein Stück Eis als Körper, den {@code fracture} in Zellen zerlegt (neu in Glacier, Phase 7). Gibt: {@code ice}
 * ({@code FrontBlock}: ein Abschnitt der Front, Spalten schwächen ihn). Nimmt: {@code fracture} über die Kalbung der
 * App. Nur Zahlen: {@code fracture} kennt kein Eis, sondern Umriss, Dichte und Festigkeit.
 */
public interface Cells {
    /** Hülle {x0, y0, z0, x1, y1, z1} (m). */
    double[] bounds();

    /** Ebenen des Quaders {nx, ny, nz, d, …} (n·p ≤ d innen). */
    double[] planes();

    /** Oberseite und Unterseite des Eises über (x, z) (m, y). */
    double top(double x, double z);

    double bottom(double x, double z);

    /**
     * Die Front als senkrechte Ebene bei (x, z): {nx, nz, d}, Eis ist, wo nx·x + nz·z ≤ d (null: keine Front in der
     * Nähe). Seit Phase 7, Befund Status_Phase7: Zellen am Rand reichen sonst bis an den Quader, also vor die Wand.
     */
    double[] front(double x, double z);

    /** Liegt der Punkt im Eis des Abschnitts? */
    boolean inside(double x, double y, double z);

    /** Dichte der Zellen 0..1 (1 = am feinsten: an der Front, an der Wasserlinie). */
    double density(double x, double y, double z);

    /** Festigkeit als Faktor (1 = Gletschereis; Spalten und Schwächen der Regie darunter). */
    double strength(double x, double y, double z);
}
