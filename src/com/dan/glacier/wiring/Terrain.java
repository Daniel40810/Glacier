package com.dan.glacier.wiring;

/**
 * Das Gelände: Höhe und Boden am Ort. Gibt: das Gelände aus den Höhendaten (Phase 2), der Grund unter Wasser und
 * Eis modelliert [G]. Nimmt: ice (Grund unter dem Gletscher), wave (Tiefe, Ufer), forest, ground, river, reed, Kamera.
 */
public interface Terrain {
    /** Höhe des Bodens (Fels, Moräne, Seegrund) in m über dem Seespiegel am Ort (x, z). */
    double height(double x, double z);

    /** Ob am Ort Wasser steht und wie tief es ist (m, 0 = trocken). */
    double waterDepth(double x, double z);

    /** Neigung des Bodens als Normale (Einheitsvektor). */
    default double[] normal(double x, double z) {
        double e = 1, hx = height(x + e, z) - height(x - e, z), hz = height(x, z + e) - height(x, z - e);
        double nx = -hx / (2 * e), nz = -hz / (2 * e), l = Math.sqrt(nx * nx + 1 + nz * nz);
        return new double[]{nx / l, 1 / l, nz / l};
    }
}
