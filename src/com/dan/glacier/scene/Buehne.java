package com.dan.glacier.scene;

/**
 * Die flache Bühne von Phase 1 (Platzhalter bis zum Gelände aus den Höhendaten in Phase 2), alle Maße [G] und nur zur
 * Ausrichtung: der See auf y = 0, die Front als gerade Wand bei x = 0 über 5 km (|z| ≤ 2 500 m), dahinter nach
 * Westen das Eis als Block von {@link Site#FRONT_HEIGHT} m bis 8 km aufwärts; östlich davor die Halbinsel Magallanes
 * als flache Landzunge mit ihrer Spitze {@link Site#STEG_DISTANCE} m vor der Front, nördlich von ihr der Canal de los
 * Témpanos, südlich der Brazo Rico. Dieselben Formen zeichnet {@code stage.frag}; die Kamera hält sich mit
 * {@link #height} darüber.
 */
public final class Buehne {
    private Buehne() { }

    /** Halbe Breite der Front und Länge des Eisblocks nach Westen (m). */
    public static final double HALF_FRONT = Site.FRONT_WIDTH / 2, ICE_WEST = Site.TONGUE;
    /** Halbinsel: Spitze bei x = TIP, halbe Breite an der Spitze und 3 km weiter östlich (m), Höhe über dem See (m). */
    public static final double TIP = Site.STEG_DISTANCE, TIP_HALF = 180, ROOT_HALF = 900, ROOT_X = 3000, LAND = 2;

    /** Liegt (x, z) im Eis? */
    public static boolean ice(double x, double z) { return x <= 0 && x >= -ICE_WEST && Math.abs(z) <= HALF_FRONT; }

    /** Liegt (x, z) auf der Halbinsel? Ihre Mittellinie biegt leicht nach Süden ab [G]. */
    public static boolean land(double x, double z) {
        if (x < TIP) return false;
        double u = Math.min(1, (x - TIP) / (ROOT_X - TIP));
        double half = TIP_HALF + (ROOT_HALF - TIP_HALF) * Math.sqrt(u), mid = 150 * u;
        // runde Spitze: ein Halbkreis mit dem Halbmesser der Spitze
        if (x < TIP + TIP_HALF) return Math.hypot(x - (TIP + TIP_HALF), z - mid) <= TIP_HALF;
        return Math.abs(z - mid) <= half;
    }

    /** Höhe der Bühne (m): Eis 74 m, Land 2 m, Wasser 0. */
    public static double height(double x, double z) { return ice(x, z) ? Site.FRONT_HEIGHT : land(x, z) ? LAND : 0; }

    /** „Canal de los Témpanos“, „Brazo Rico“, „Halbinsel Magallanes“, „Eis“ für einen Ort der Bühne. */
    public static String zone(double x, double z) {
        if (ice(x, z)) return "Eis";
        if (land(x, z)) return "Halbinsel Magallanes";
        return z < 0 ? "Canal de los Témpanos" : "Brazo Rico";
    }
}
