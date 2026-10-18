package com.dan.glacier.wiring;

/**
 * Ein Einschlag eines Körpers ins Wasser oder auf den Boden (neu in Glacier). Gibt: {@code fracture.onImpact}
 * (Phase 7–8). Nimmt: wave (Quelle aus dem verdrängten Volumen), spray (Schwall), soundscape (Aufschlag).
 */
@FunctionalInterface
public interface Impact {
    /** Ort (m), verdrängtes Volumen in diesem Schritt (m³), Tempo senkrecht zur Fläche (m/s), ob ins Wasser. */
    void impact(double x, double y, double z, double volumeM3, double speed, boolean water);
}
