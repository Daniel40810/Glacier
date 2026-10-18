package com.dan.glacier.wiring;

/**
 * Die Jahreszeit als Zahlen. Gibt: {@code season} mit dem Klima „Patagonien“ (Phase 11). Nimmt: ice (Schnee auf dem
 * Eis, Schmelze, Kalbungsrate), forest (Lenga-Rot), reed, ocean (Eis auf dem Brazo Rico), weather.
 */
public interface Season {
    /** Anteil des Laubs am Baum 0..1 und seine Färbung 0 = grün … 1 = rot (Lenga im April). */
    double foliage();
    double autumn();

    /** Schneehöhe in m auf ebenem Boden und Eisgrenze am Ufer (m vom Ufer in den See, 0 = offen). */
    double snowDepth();
    double iceReach();

    /** Schmelze an der Wasserlinie 0..1 (Sommer 1) und Kalbungsrate gegenüber dem Jahresmittel (Sommer über 1). */
    default double melt() { return 0.5; }
    default double calvingRate() { return 1; }
}
