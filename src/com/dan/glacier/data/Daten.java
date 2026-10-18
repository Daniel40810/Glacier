package com.dan.glacier.data;

import com.dan.glacier.scene.SceneClock;
import com.dan.glacier.scene.Site;
import com.dan.store.Zahl;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Die Datensätze, die {@link Speicher} liest und schreibt (Phase 15, Werkbuch Abschnitt 8, F23–F36 „alle A“). Je
 * Tabelle ein Record mit den Spalten, die die App braucht; ID, ANGELEGT und GEAENDERT führt die Datenbank selbst. Lagen
 * stehen in Szenenmetern (x nach Osten, z nach Süden); Länge und Breite rechnet {@link #lonLat} (F25). Die Szenenzeit
 * ist eine {@link LocalDateTime} in Ortszeit ART (UTC−3, ohne Sommerzeit; F24), in der Datenbank ein TIMESTAMP.
 */
public final class Daten {
    private Daten() { }

    /** HERKUNFT: mitgeliefert (stellt der Einrichter wieder her) oder eigen (fasst er nie an). */
    public static final String MITGELIEFERT = "MITGELIEFERT", EIGEN = "EIGEN";
    /** BEFUND: belegt, gemessen oder gestaltet [G]. */
    public static final String BELEGT = "BELEGT", GEMESSEN = "GEMESSEN", GESTALTET = "GESTALTET";
    /** Kalbung: Auslöser (F27). */
    public static final String REGIE = "REGIE", HAND = "HAND", KARTE = "KARTE", REGIEBUCH = "REGIEBUCH";
    /** Eisberg: wie er endet (F28). */
    public static final String OFFEN = "OFFEN", GESCHMOLZEN = "GESCHMOLZEN", GESTRANDET = "GESTRANDET", ZERBROCHEN = "ZERBROCHEN", AUS_DER_SZENE = "AUS_DER_SZENE";
    /** Front: Art der Linie (F26). */
    public static final String F_GELAENDE = "GELAENDE", F_MODELL = "MODELL", F_SITZUNG = "SITZUNG", F_MERKEN = "MERKEN", F_BELEGT = "BELEGT";

    private static final DateTimeFormatter ZEIT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    /** „TURM“ → „Turm“, „FUSS“ → „Fuß“. */
    public static String artLabel(String art) {
        return switch (art) { case "TURM" -> "Turm"; case "SCHEIBE" -> "Scheibe"; case "FUSS" -> "Fuß"; default -> art; };
    }

    /** {lon, lat} eines Szenenpunkts (x, z). */
    public static double[] lonLat(double x, double z) {
        double[] ll = Site.latLon(x, z);
        return new double[]{ll[1], ll[0]};
    }

    /** Szenenpunkt {x, z} aus Länge und Breite. */
    public static double[] xz(double lon, double lat) { return Site.xz(new double[]{lat, lon}); }

    /** Die Szenenzeit aus Millisekunden seit 1970 (die Uhr der Szene) in Ortszeit ART. */
    public static LocalDateTime szenenzeit(double epochMs) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli((long) Math.floor(epochMs)), SceneClock.ZONE);
    }

    /** „16.01.2026 15:00“ */
    public static String text(LocalDateTime t) { return t == null ? "" : ZEIT.format(t); }

    /**
     * Eine Geometrie in Szenenmetern: PUNKT, LINIE, MULTILINIE (mehrere Linien, etwa die Frontlinien) oder FLAECHE.
     * Für MULTILINIE stehen die Teile in {@link #teile}, sonst ein Teil.
     */
    public record Geom(String art, List<double[][]> teile) {
        public Geom { teile = List.copyOf(teile); }
        public static Geom punkt(double x, double z) { return new Geom("PUNKT", List.<double[][]>of(new double[][]{{x, z}})); }
        public static Geom linie(double[][] p) { return new Geom("LINIE", List.<double[][]>of(p)); }
        public static Geom multilinie(List<double[][]> p) { return new Geom("MULTILINIE", p); }
        public static Geom flaeche(double[][] p) { return new Geom("FLAECHE", List.<double[][]>of(p)); }
        public double[][] xz() { return teile.get(0); }

        private static double[][] ll(double[][] xz) {
            double[][] r = new double[xz.length][];
            for (int i = 0; i < xz.length; i++) r[i] = lonLat(xz[i][0], xz[i][1]);
            return r;
        }

        /** Als WKT in Länge und Breite (com.dan.store.Wkt; MULTILINESTRING hier). */
        public String wkt() {
            return switch (art) {
                case "PUNKT" -> { double[] p = lonLat(xz()[0][0], xz()[0][1]); yield com.dan.store.Wkt.punkt(p[0], p[1]); }
                case "LINIE" -> com.dan.store.Wkt.linie(ll(xz()));
                case "FLAECHE" -> com.dan.store.Wkt.flaeche(ll(xz()));
                default -> {
                    StringBuilder b = new StringBuilder("MULTILINESTRING (");
                    for (int i = 0; i < teile.size(); i++) {
                        String l = com.dan.store.Wkt.linie(ll(teile.get(i)));
                        b.append(i > 0 ? ", " : "").append(l.substring(l.indexOf('(')));
                    }
                    yield b.append(')').toString();
                }
            };
        }

        /** Die Punkte als Text „ART:x z;x z|x z;…“ (Szenenmeter, cm) für die Dateien. */
        public String text() {
            StringBuilder b = new StringBuilder(art).append(':');
            for (int t = 0; t < teile.size(); t++) {
                if (t > 0) b.append('|');
                double[][] p = teile.get(t);
                for (int i = 0; i < p.length; i++)
                    b.append(i > 0 ? ";" : "").append(Zahl.text(Math.round(p[i][0] * 100) / 100.0)).append(' ').append(Zahl.text(Math.round(p[i][1] * 100) / 100.0));
            }
            return b.toString();
        }

        public static Geom ausText(String t) {
            if (t == null || t.isBlank()) return null;
            int c = t.indexOf(':');
            List<double[][]> teile = new java.util.ArrayList<>();
            for (String teil : t.substring(c + 1).split("\\|")) {
                String[] ps = teil.split(";");
                double[][] p = new double[ps.length][];
                for (int i = 0; i < ps.length; i++) {
                    String[] q = ps[i].strip().split(" ");
                    p[i] = new double[]{Zahl.lies(q[0]), Zahl.lies(q[1])};
                }
                teile.add(p);
            }
            return new Geom(t.substring(0, c), teile);
        }

        /** Aus WKT in Länge und Breite (wie {@code SDO_UTIL.TO_WKTGEOMETRY} es liefert) zurück in Szenenmeter. */
        public static Geom ausWkt(String wkt) {
            if (wkt == null || wkt.isBlank()) return null;
            String w = wkt.strip();
            String art = w.startsWith("POINT") ? "PUNKT" : w.startsWith("POLYGON") ? "FLAECHE" : w.startsWith("MULTILINESTRING") ? "MULTILINIE" : "LINIE";
            List<double[][]> teile = new java.util.ArrayList<>();
            String[] stuecke = art.equals("MULTILINIE") ? w.substring(w.indexOf('(') + 1, w.lastIndexOf(')')).split("\\)\\s*,\\s*\\(") : new String[]{w};
            for (String s : stuecke) {
                double[][] ll = com.dan.store.Wkt.punkte(s);
                int n = art.equals("FLAECHE") && ll.length > 3 ? ll.length - 1 : ll.length;
                double[][] p = new double[n][];
                for (int i = 0; i < n; i++) p[i] = Daten.xz(ll[i][0], ll[i][1]);
                teile.add(p);
            }
            return new Geom(art, teile);
        }

        /** Länge aller Linien (m, in der Ebene der Szene). */
        public double laenge() {
            double s = 0;
            for (double[][] p : teile) for (int i = 0; i + 1 < p.length; i++) s += Math.hypot(p[i + 1][0] - p[i][0], p[i + 1][1] - p[i][1]);
            return s;
        }

        /** Fläche (m², nach Gauß; nur FLAECHE). */
        public double flaecheM2() {
            double[][] p = xz();
            double a = 0;
            for (int i = 0; i < p.length; i++) { double[] u = p[i], v = p[(i + 1) % p.length]; a += u[0] * v[1] - v[0] * u[1]; }
            return Math.abs(a) / 2;
        }
    }

    /** Eine Quelle (GLC_QUELLE). */
    public record Quelle(String kurz, String titel, String url, String art, LocalDate abgerufen, String bemerkung) { }

    /** Ein Befund (GLC_BEFUND) mit den Kurzzeichen seiner Quellen. */
    public record Befund(String thema, String gegenstand, String befund, String text, List<String> quellen) {
        public Befund { quellen = List.copyOf(quellen); }
    }

    /** Eckdaten des Geländes (GLC_GELAENDE); dateien: Rolle → {Dateiname, SHA-256}. */
    public record Gelaende(String name, Geom ausschnitt, double ursprungLon, double ursprungLat, double nordGrad, double zelleM, int zellen,
                           double seespiegelM, LocalDate dgmVon, LocalDate dgmBis, Map<String, String[]> dateien, String quelle, String bemerkung) {
        public Gelaende { dateien = new LinkedHashMap<>(dateien); }
    }

    /** Eine Front als Linie (GLC_FRONT, F26). */
    public record Front(long id, LocalDateTime szenenzeit, LocalDateTime gueltigBis, String art, String name, Geom linie, double laengeM, double vorstossM,
                        Double flaecheDiffM2, Double hoeheMittelM, String quelle, String bemerkung, String herkunft) { }

    /** Eine Kalbung (GLC_KALBUNG, F27). riss darf null sein. */
    public record Kalbung(long id, LocalDateTime szenenzeit, int jahrestag, String art, String ausloeser, double x, double y, double z, Geom riss,
                          double volumenM3, double masseT, int stuecke, double hoeheM, Double fallzeitS, double vorwarnungS, Double welleStegM,
                          Long seed, String regiebuch) { }

    /** Ein Eisberg (GLC_EISBERG, F28): nr ist die Nummer in der Sitzung; weg in Szenenmetern {x, z} je Punkt. */
    public record Eisberg(int nr, Long kalbungId, Long rupturaLaufId, LocalDateTime geburt, double geburtX, double geburtZ, double volumenStartM3,
                          double volumenM3, double tiefgangM, double freibordM, int kenterungen, List<double[]> weg, double x, double z, double gierGrad,
                          long formSeed, LocalDateTime ende, String endeArt) {
        public Eisberg { weg = List.copyOf(weg); }
        /** Länge des Wegs (m). */
        public double wegM() {
            double s = 0;
            for (int i = 1; i < weg.size(); i++) s += Math.hypot(weg.get(i)[0] - weg.get(i - 1)[0], weg.get(i)[1] - weg.get(i - 1)[1]);
            return s;
        }
    }

    /** Eine belegte Ruptura (GLC_RUPTURA, F29). */
    public record Ruptura(int jahr, int nr, LocalDate datum, String genauigkeit, Double stauM, Double abflussM3s, Double dauerH, String text, List<String> quellen) {
        public Ruptura { quellen = List.copyOf(quellen); }
        /** „14.03.2004“, „02.1966“ oder „1917“ */
        public String datumText() {
            return switch (genauigkeit) {
                case "TAG" -> DateTimeFormatter.ofPattern("dd.MM.yyyy").format(datum);
                case "MONAT" -> DateTimeFormatter.ofPattern("MM.yyyy").format(datum);
                default -> Integer.toString(datum.getYear());
            };
        }
    }

    /** Ein Lauf der Ruptura in der App (GLC_RUPTURA_LAUF): Zeitpunkte je Abschnitt (null: nicht erreicht). */
    public record RupturaLauf(long id, Integer jahr, LocalDateTime beginn, LocalDateTime durchbruch, LocalDateTime tunnel, LocalDateTime rinne,
                              LocalDateTime bruecke, LocalDateTime einsturz, LocalDateTime ende, String phase, double stauMaxM, double abflussMaxM3s,
                              double volumenM3, double brueckeM3, double groesstesM3, int stuecke, Geom damm, Geom rinneLinie, String regiebuch) { }

    /**
     * Eine Wetterlage (GLC_WETTERLAGE): je {@code Weather.Var} ein Wert oder NaN (die Dünung bleibt NaN, der Lago
     * Argentino hat keine). Die Wolkenart steht als Zahl wie in der Regie, in der Tabelle als Name.
     */
    public record Wetterlage(String name, Double beaufort, String phase, double[] werte, String monate, String beschreibung, String herkunft) {
        public Wetterlage { werte = werte.clone(); }
        @Override public String toString() { return name; }
    }

    /** Ein Regiebuch als ganzer Text (Format von {@code scene.Regie.parse}). */
    public record Regiebuch(String name, String text, String beschreibung, String datei, String herkunft) {
        @Override public String toString() { return name; }
    }

    /** Ein Lauf eines Regiebuchs (GLC_REGIELAUF) mit dem Blatt als PNG (oder null). */
    public record Regielauf(String name, LocalDateTime beginn, LocalDateTime ende, boolean vollstaendig, double bilderMittel, double bilderMin, double gpuMs,
                            int fotos, String qualitaet, byte[] blatt, String blattDatei) { }

    /** Ein Stützpunkt einer Kamerafahrt (GLC_STUETZPUNKT); Szenenmeter und Grad. */
    public record Stuetzpunkt(int nr, double zeitS, double x, double y, double z, double gier, double nick, double fov) { }

    /** Eine Kamerafahrt (GLC_FAHRT). */
    public record Fahrt(String name, Integer taste, String art, boolean schleife, Double dauerS, String beschreibung, String herkunft, List<Stuetzpunkt> punkte) {
        public Fahrt { punkte = List.copyOf(punkte); }
        @Override public String toString() { return name; }
    }

    /**
     * Eine Ansicht (GLC_ANSICHT): fest (Tasten 1–8) oder gemerkt (Taste V) mit Zustand und Vorschau (PNG oder null).
     * kamera = {x, y, z, Gier, Nick, Bildwinkel}.
     */
    public record Ansicht(long id, String name, Integer taste, String art, double[] kamera, Double augeUeberGrund, boolean unterWasser,
                          LocalDateTime szenenzeit, String zustand, byte[] vorschau, String herkunft) {
        @Override public String toString() { return name; }
    }

    /** Ein benanntes Preset eines Package (GLC_PAKET_PRESET mit GLC_PAKET_WERT). */
    public record PaketPreset(String paket, String klasse, String name, String beschreibung, String herkunft, Map<String, String> felder) {
        public PaketPreset { felder = new LinkedHashMap<>(felder); }
        @Override public String toString() { return name; }
    }

    /** Die letzte Sitzung für „Weiter wie zuletzt“. */
    public record Sitzung(long id, long beginnMs, Long endeMs, boolean sauber, String zustand, Long zustandMs, String regiebuch, Double regieSekunde,
                          LocalDateTime szenenzeit, Integer eisberge, String rupturaPhase) { }

    /** Was das Startbild und der Block DATEN über den Stand zeigen. */
    public record Stand(int fronten, int kalbungen, int eisberge, int rupturen, int laeufe, int regiebuecher, int regielaeufe, int wetterlagen, int fahrten,
                        int ansichten, int presets, int befunde, int quellen, Sitzung letzte) { }

    /** Eine Zeile des Blocks CHRONIK und der Säulenkarte: eine Kalbung kurz. */
    public record KalbungKurz(long id, LocalDateTime szenenzeit, String art, String ausloeser, double x, double z, double volumenM3, Double welleStegM) {
        @Override public String toString() {
            return String.format(java.util.Locale.GERMAN, "%s %s %,.0f m³%s", DateTimeFormatter.ofPattern("HH:mm").format(szenenzeit),
                    artLabel(art), volumenM3,
                    welleStegM == null ? "" : String.format(java.util.Locale.GERMAN, " · %.2f m", welleStegM));
        }
    }
}
