package com.dan.glacier.wiring;

/**
 * Ein Riss, der läuft (neu in Glacier). Gibt: {@code fracture.onCrack} (Phase 7). Nimmt: soundscape (Donner aus dem
 * Weg des Risses), ice (der Riss wird sichtbar, bevor das Stück bricht).
 */
@FunctionalInterface
public interface Crack {
    /** Weg des Risses als Punkte {x, y, z, x, y, z, …} (m), freigesetzte Energie (J), ob das Stück damit frei ist. */
    void crack(double[] path, double energyJ, boolean broken);
}
