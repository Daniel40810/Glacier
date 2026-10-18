package com.dan.glacier.wiring;

/**
 * Die Höhe des Wassers als Summe (neu in Glacier): Windsee aus {@code ocean} plus die laufende Welle aus {@code wave}.
 * Gibt: die App (Phase 7–8, {@code ocean.heightAt + wave.heightAt}). Nimmt: buoyancy (Eisberge, Katamaran), spray,
 * ice (Schmelzkerbe), fracture (Auftrieb der Stücke).
 */
@FunctionalInterface
public interface Sea {
    /** Höhe der Oberfläche in m über dem Seespiegel bei (x, z) zur Laufzeit t (s). */
    double heightAt(double x, double z, double t);

    /** Geschwindigkeit des Wassers an der Oberfläche (m/s), {vx, vz}; ohne Angabe ruhend. */
    default double[] velocityAt(double x, double z, double t) { return new double[]{0, 0}; }
}
