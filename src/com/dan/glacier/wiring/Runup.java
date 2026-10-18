package com.dan.glacier.wiring;

/**
 * Eine Welle läuft am Ufer auf (neu in Glacier). Gibt: {@code wave.onRunup} (Phase 8). Nimmt: weathering (nasse Stege
 * und Felsen), soundscape (Rauschen am Ufer), die App (Kurve der Wellenhöhe am Steg).
 */
@FunctionalInterface
public interface Runup {
    /** Ort am Ufer (m), Auflaufhöhe über dem Ruhespiegel (m), Laufzeit (s). */
    void runup(double x, double z, double heightM, double t);
}
