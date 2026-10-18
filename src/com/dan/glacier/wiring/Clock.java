package com.dan.glacier.wiring;

/**
 * Zeit der Szene. Gibt: die App ({@code SceneClock}). Nimmt: alle Packages. Die Laufzeit {@code t} läuft auch bei
 * stehender Uhr weiter (Wellen, Wolken und fallende Stücke bewegen sich), die Uhrzeit {@code epochMillis} folgt dem
 * Zeitraffer.
 */
public interface Clock {
    /** Laufzeit der Szene in s und letzter Schritt in s. */
    double t();
    double dt();

    /** Szenenzeit in ms seit 1970 (UTC). */
    double epochMillis();

    /** Ort in Grad (Mitte der Front des Perito Moreno; Süd und West negativ). */
    double latDeg();
    double lonDeg();
}
