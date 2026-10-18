package com.dan.glacier.wiring;

/** Licht von Himmel, Sonne und Mond. Gibt: {@code sky} (Phase 3). Nimmt: ocean, cloud, ice, rain, die Szene. */
public interface Lighting {
    /** Richtung zur Sonne und zum Mond (Einheitsvektoren der Szene). */
    double[] sunDir();
    double[] moonDir();

    /** Farbe und Stärke der Sonne, des Mondes und des Himmels (linear, RGB). */
    double[] sunColor();
    double[] moonColor();
    double[] skyAmbient();

    /** Dunst als Auslöschung je m (Sicht). */
    double haze();
}
