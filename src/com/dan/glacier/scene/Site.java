package com.dan.glacier.scene;

/**
 * Der Ort der Szene: der Perito-Moreno-Gletscher am Lago Argentino, Nationalpark Los Glaciares, Santa Cruz,
 * Argentinien (F1 A). Echter Maßstab (F2 A): 1 Einheit = 1 m, x nach Osten, y nach oben, Nord = −z. Ursprung in der
 * Mitte der Front auf dem mittleren Seespiegel. Lage nach SRID 8307 (WGS84).
 * <p>
 * Seit Phase 2 kommt der Ursprung aus den Höhendaten (Copernicus GLO-30): die Spitze der Front an der Halbinsel
 * Magallanes, wo Canal de los Témpanos und Brazo Rico sich trennen. Die Szene ist eine transversale
 * Mercator-Abbildung um den Ursprung ({@link #SCENE}). Die Maße stammen aus dem Werkbuch, Abschnitt 2.
 */
public final class Site {
    private Site() { }

    /**
     * Ursprung der Szene: die Spitze der Front, wo die Zunge an die Halbinsel Magallanes stößt, 50°28′23″ S,
     * 73°02′13″ W. Phase 2 hat sie aus den Höhendaten bestimmt ({@code tools.GelaendeImport}); in Phase 1 lag sie
     * geschätzt [G] rund 840 m weiter südwestlich.
     */
    public static final double LAT = -50.4731944, LON = -73.0370145;
    /** Der Gletscher als Ganzes, wie er angegeben wird: 50°30′ S, 73°08′ W. */
    public static final double GLACIER_LAT = -50.5, GLACIER_LON = -(73 + 8 / 60.0);
    /** El Calafate (78 km östlich): Bezug für Klima und Uhrzeit. */
    public static final double CALAFATE_LAT = -50.3379, CALAFATE_LON = -72.2648;
    public static final int SRID = 8307;
    /** Mittlerer Erdradius in m und der wirksame Radius für die Sicht mit normaler Lichtbrechung (k = 0,13). */
    public static final double EARTH_R = 6_371_000, EARTH_R_OPTICAL = EARTH_R / (1 - 0.13);

    /** Der Gletscher: Fläche (km²), Länge (km), Breite der Front (m). */
    public static final double AREA_KM2 = 250, LENGTH_KM = 30, FRONT_WIDTH = 5000;
    /** Die Front: Höhe über dem Wasser im Mittel (m), Eis insgesamt (m), also der Fuß rund 100 m unter dem Spiegel. */
    public static final double FRONT_HEIGHT = 74, ICE_THICKNESS = 170, FRONT_DRAFT = ICE_THICKNESS - FRONT_HEIGHT;
    /** Spiegel des Lago Argentino über dem Meer (m), wie ihn Copernicus GLO-30 setzt (Phase 2; das Werkbuch schätzte 185 m); y = 0 der Szene. */
    public static final double LAKE_LEVEL_ASL = 178.5;
    /** Stau des Brazo Rico vor einer Ruptura: bis zu 30 m über dem Hauptsee; Abfluss beim Einsturz bis 8 000 m³/s. */
    public static final double BRAZO_RICO_MAX_STAU = 30, RUPTURA_FLOW = 8000;
    /** Seitenlänge des Geländes der Szene (F3 A: 2048 Zellen zu 6,25 m) und die Zunge aufwärts bis zum Eisfall (m). */
    public static final double EXTENT = 12_800, TONGUE = 8000;
    /** Abstand der Stege von der Front (m): Phase 2 findet sie 300 m vor der Spitze auf der Halbinsel ({@code steg.*} im Gelände). */
    public static final double STEG_DISTANCE = 400;
    /** Dichte von Gletschereis und Seewasser (kg/m³): 9 Zehntel eines Eisbergs liegen unter Wasser. */
    public static final double ICE_DENSITY = 900, WATER_DENSITY = 1000;

    /** Absenkung der Oberfläche in d m Entfernung durch die Erdkrümmung: d²/2R. */
    public static double curvatureDrop(double d) { return d * d / (2 * EARTH_R); }

    /** Entfernung der Kimm für ein Auge h m über dem Wasser, mit normaler Lichtbrechung (m). */
    public static double horizon(double h) { return Math.sqrt(2 * EARTH_R_OPTICAL * Math.max(0, h)); }

    /** Tempo einer langen Welle im Flachwasser der Tiefe h (m): √(g·h). */
    public static double waveSpeed(double depth) { return Math.sqrt(9.81 * Math.max(0, depth)); }

    /** Die Abbildung der Szene: transversale Mercator mit Maßstab 1 um die Mitte der Front (Nordwert = −z). */
    public static final Geo.Tm SCENE = Geo.local(LAT, LON);

    /** Breite und Länge (Grad) eines Szenenpunkts. */
    public static double[] latLon(double x, double z) { return SCENE.inverse(x, -z); }

    /** Szenenpunkt (x, z) aus Breite und Länge (Grad, als {lat, lon}). */
    public static double[] xz(double[] latLon) { double[] en = SCENE.forward(latLon[0], latLon[1]); return new double[]{en[0], -en[1]}; }

    /** Breite eines Punkts auf der Nord-Süd-Achse durch den Ursprung, Länge eines Punkts auf der Ost-West-Achse. */
    public static double lat(double z) { return latLon(0, z)[0]; }
    public static double lon(double x) { return latLon(x, 0)[1]; }

    /** „50°28′40″ S · 73°02′50″ W“ */
    public static String dms(double lat, double lon) {
        return dms(lat, lat >= 0 ? "N" : "S") + " · " + dms(lon, lon >= 0 ? "E" : "W");
    }

    private static String dms(double v, String h) {
        v = Math.abs(v);
        int d = (int) v;
        double mm = (v - d) * 60;
        int m = (int) mm;
        int s = (int) Math.round((mm - m) * 60);
        if (s == 60) { s = 0; m++; }
        if (m == 60) { m = 0; d++; }
        return String.format("%d°%02d′%02d″ %s", d, m, s, h);
    }
}
