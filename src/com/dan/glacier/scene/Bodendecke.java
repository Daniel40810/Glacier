package com.dan.glacier.scene;

/**
 * Phase 2: wo die Bodendecke aus {@code com.dan.ground} (Biom „Patagonien“) um das Auge wächst [G]. Unter den
 * Südbuchen Laubstreu und Seggen; auf den offenen Hängen Horstgras in Flecken (das hohe zeichnet {@code com.dan.reed});
 * auf der Moräne neben dem Eis Kies, Steine und Blöcke, kaum Pflanzen; am Ufer Kiesel und nasse Seggen. Auf Eis und
 * im Wasser nichts.
 */
public final class Bodendecke implements com.dan.ground.Site {
    private final Terrain t;
    private final Vegetation v;

    public Bodendecke(Terrain t, Vegetation v) { this.t = t; this.v = v; }

    @Override public float height(double x, double z) { return (float) t.height(x, z); }

    @Override public void cover(double x, double z, float[] o) {
        byte s = t.sourceAt(x, z);
        double y = t.height(x, z);
        if (s == Terrain.EIS || s == Terrain.SEE || y < 0.3) { java.util.Arrays.fill(o, 0); return; }
        double forest = v.forest(x, z), steppe = Vegetation.map(v.steppe, x, z), moraine = Vegetation.map(v.moraine, x, z);
        double rock = Vegetation.map(v.rock, x, z), wet = Vegetation.map(v.guindo, x, z) + Vegetation.map(v.grass, x, z);
        double patch = Vegetation.fbm(x / 13.0 + 2.3, z / 13.0 - 8.1);
        double shore = Vegetation.smooth(3.0, 0.5, y);
        o[0] = (float) ((forest * 0.6 + steppe * 0.4 + 0.15) * (1 - moraine * 0.8) * (1 - rock * 0.7));
        o[1] = (float) ((1 - forest) * (1 - moraine) * Math.max(0, patch * 1.4 - 0.8) * 0.6);
        o[2] = (float) Math.min(1, 0.12 + moraine * 0.9 + shore * 0.5 + rock * 0.5);
        o[3] = (float) Math.min(1, forest * 0.7 + moraine * 0.3);
        o[4] = (float) Math.min(1, wet + shore * 0.6);
    }
}
