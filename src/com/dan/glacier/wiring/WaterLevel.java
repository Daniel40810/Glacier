package com.dan.glacier.wiring;

/**
 * Wasserspiegel in m über dem mittleren Spiegel des Lago Argentino (y = 0 der Szene). Der See hat keine Tide; der
 * Brazo Rico steigt hinter dem Eisdamm bis zu 30 m über den Hauptsee (Stau, Phase 11 und 13). Gibt: die App nach Jahr
 * und Regie. Nimmt: ocean, wave, ice (Schmelzkerbe), buoyancy, weathering, Kamera.
 */
public interface WaterLevel {
    /** Spiegel des Hauptsees (Canal de los Témpanos) in m. */
    double level();

    /** Spiegel des Brazo Rico in m (ohne Stau gleich dem Hauptsee). */
    default double brazoRico() { return level(); }
}
