package com.dan.glacier.scene;

import com.dan.weather.Scenario;
import com.dan.weather.Weather;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Phase 14: die Regiebücher (F14 A) als Text mit Blättern wie in Haithabu. Die drei mitgelieferten stehen in
 * {@code regie/*.txt} neben dieser Klasse. Ein Regiebuch ist eine Folge von Stichworten in echten Sekunden seit dem
 * Start: Datum und Uhrzeit, Zeitraffer, Wetter, Ansichten und Fahrten, die Bootsfahrt, Kalbungen von Hand, die Ruptura,
 * Sternbilder, Fotos und am Ende ein Blatt mit allen Fotos in docs/status. Was ein Stichwort tut, führt die App über
 * {@link Ctx} aus; so läuft dasselbe Regiebuch auch ohne Bild (Prüfung).
 * <ul>
 * <li><b>Ein Sommertag an der Front</b> (16.1.2026, 6 bis 22 Uhr, Zeitraffer 60): Seenebel am Morgen, Kalbungen über den
 * Tag, der Katamaran fährt, auf dem Eis, durch die Spalte, vom Berg, unter Wasser, abends Licht von Westen in den
 * Spalten. 16 Minuten, zehn Fotos.</li>
 * <li><b>Die Ruptura</b> (4.3.2027, die Uhr steht): der Stau, der Tunnel, die Decke, die Brücke, der Einsturz, der
 * Abfluss, mit dem eigenen Zeitraffer der Ruptura. Fünfeinhalb Minuten, neun Fotos.</li>
 * <li><b>Südhimmel</b> (19.7.2026, 20:30 bis 23:30, Zeitraffer 30): eine klare Winternacht mit zunehmendem Mond über dem
 * Eis, Kreuz des Südens und Magellansche Wolken, ein Bruch im Dunkeln. Sechs Minuten, sechs Fotos.</li>
 * </ul>
 * Gestaltet [G]: Abläufe, Zeiten, Blickpunkte; der Mond am 19.7.2026 (36 %, im Nordwesten über dem Eis) aus
 * {@code com.dan.sky}.
 */
public final class Regie {

    /** Was ein Regiebuch braucht; die App führt es aus (ohne Bild: die Prüfung). */
    public interface Ctx {
        void time(LocalDate date, double hour);
        void lapse(double x);
        void weather(Scenario s);
        /** Freie Regie ab einer Lage, dazu Wind von Hand (Bft, woher), NaN = von der Regie. */
        void weatherFree(Weather.Phase p, double bft, double fromDeg);
        void view(String name);
        void fahrt(String name);
        /** Die Bootsfahrt an Bord des Katamarans; aussteigen beendet die Mitfahrt. */
        void boot();
        void aussteigen();
        /** Ein Abbruch von Hand an der Front im Blick (Turm, Scheibe, Fuß). */
        void kalbung(Kalbung.Art art);
        void kalbungRegie(boolean on);
        /** Die Ruptura (Jahr mit Damm, Stau, Tunnel, Brücke, Einsturz, Abfluss). */
        void ruptura();
        void damm(boolean on);
        void figuren(boolean on);
        void capture(String caption);
        void sheet(String name);
        void say(String text);
    }

    /** Ein Stichwort: Sekunde (echte Zeit seit dem Start), Text, Handlung. */
    public record Cue(double t, String text, Consumer<Ctx> action) { }

    /**
     * Ein Regiebuch: Name, Beschreibung, Länge (s), Stichworte (nach der Zeit geordnet), der Text, aus dem es gelesen
     * ist, die Stichworte als Zeilen und der Wetterverlauf (oder null).
     */
    public record Book(String name, String about, double length, List<Cue> cues, String text, List<Zeile> zeilen, Scenario weather) {
        @Override public String toString() { return name; }
    }

    // ------------------------------------------------------------------ Ablauf

    /** Phase 16: bekommt den Text jedes Stichworts (der Kinomodus macht Untertitel daraus); null = niemand. */
    public volatile java.util.function.Consumer<String> beiText;

    private volatile Book book;
    private double t;
    private int next;
    private volatile String status = "";
    private int fired;

    /** Regiebuch beginnen (das erste Stichwort sofort). */
    public void start(Book b, Ctx c) {
        book = b;
        t = 0;
        next = 0;
        fired = 0;
        status = b.name() + " beginnt";
        step(0, c);
    }

    /**
     * Ein Regiebuch an der Stelle t (s) fortsetzen, ohne die Stichworte davor noch einmal auszuführen (für
     * „Weiter wie zuletzt“ in Phase 15).
     */
    public void fortsetzen(Book b, double t0) {
        book = b;
        t = Math.max(0, t0);
        next = 0;
        fired = 0;
        while (next < b.cues().size() && b.cues().get(next).t() <= t) { next++; fired++; }
        status = b.name() + " geht weiter";
    }

    public void stop() {
        Book b = book;
        book = null;
        if (b != null) status = b.name() + " angehalten";
    }

    public boolean running() { return book != null; }

    public Book book() { return book; }

    public double time() { return t; }

    /** Wie viele Stichworte schon ausgeführt sind. */
    public int fired() { return fired; }

    /** Um dt echte Sekunden weiter, fällige Stichworte ausführen. */
    public void step(double dt, Ctx c) {
        Book b = book;
        if (b == null) return;
        t += Math.max(0, dt);
        while (next < b.cues().size() && b.cues().get(next).t() <= t) {
            Cue q = b.cues().get(next++);
            if (!q.text().isEmpty()) {
                status = String.format(Locale.GERMAN, "%s · %s", b.name(), q.text());
                java.util.function.Consumer<String> h = beiText;
                if (h != null) h.accept(q.text());   // Phase 16: Untertitel im Kino
            }
            try { q.action().accept(c); } catch (RuntimeException e) { status = b.name() + " · Fehler bei „" + q.text() + "“: " + e.getMessage(); }
            fired++;
        }
        if (t >= b.length() && next >= b.cues().size()) {
            book = null;
            status = b.name() + " ist zu Ende (" + fired + " Stichworte)";
        }
    }

    /** „Ein Sommertag an der Front · 10 Uhr: der Katamaran legt ab · 4:10 von 16:00“ */
    public String describe() {
        Book b = book;
        if (b == null) return status.isEmpty() ? "kein Regiebuch" : status;
        return String.format(Locale.GERMAN, "%s · %d:%02d von %d:%02d", status, (int) t / 60, (int) t % 60, (int) b.length() / 60, (int) b.length() % 60);
    }

    // ------------------------------------------------------------------ die Regiebücher (als Text)

    /** Die drei mitgelieferten Regiebücher aus den Textdateien neben dieser Klasse (regie/*.txt). */
    public static final String[] DATEIEN = {"sommertag.txt", "ruptura.txt", "suedhimmel.txt"};

    public static List<Book> all() { return List.of(sommertag(), ruptura(), suedhimmel()); }

    public static Book byName(String n) {
        for (Book b : all()) if (b.name().equals(n)) return b;
        return null;
    }

    /** Ein Sommertag an der Front: 6 bis 22 Uhr im Zeitraffer 60 (eine Stunde je Minute). */
    public static Book sommertag() { return mitgeliefert(DATEIEN[0]); }

    /** Die Ruptura: die Uhr steht, die Ruptura hat ihren eigenen Zeitraffer. */
    public static Book ruptura() { return mitgeliefert(DATEIEN[1]); }

    /** Südhimmel: 20:30 bis 23:30 im Zeitraffer 30. */
    public static Book suedhimmel() { return mitgeliefert(DATEIEN[2]); }

    /** Der Text eines mitgelieferten Regiebuchs. */
    public static String text(String datei) {
        try (java.io.InputStream in = Regie.class.getResourceAsStream("regie/" + datei)) {
            if (in == null) throw new IllegalStateException("Regiebuch regie/" + datei + " fehlt im Build");
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Regiebuch regie/" + datei + " nicht lesbar: " + e.getMessage(), e);
        }
    }

    private static Book mitgeliefert(String datei) { return parse(text(datei)); }

    /** Die Befehle der Stichworte (klein geschrieben im Text). */
    public static final List<String> BEFEHLE = List.of("zeit", "raffer", "wetter", "wetter_frei", "ansicht", "fahrt", "boot", "aussteigen", "kalbung",
            "kalbung_regie", "ruptura", "damm", "figuren", "foto", "blatt", "sage");

    /** Ein Stichwort, wie es im Text und in der Datenbank steht: Sekunde, Befehl, Argumente, Text fürs HUD. */
    public record Zeile(double sekunde, String befehl, String argumente, String text) { }

    /**
     * Liest ein Regiebuch aus Text. Kopfzeilen: {@code Regiebuch:}, {@code Beschreibung:}, {@code Länge:}
     * (m:ss), {@code Wetter: hh:mm | Zeitraffer} mit folgenden {@code Schritt:}-Zeilen im Format von
     * {@link Scenario#parse}. Dann je Zeile ein Stichwort: {@code m:ss befehl argumente | Text}. Zeilen zur selben Zeit
     * ohne Text gehören zum Stichwort davor. Fehler nennen die Zeile.
     */
    public static Book parse(String text) {
        String name = null, about = "";
        double length = Double.NaN;
        String wetterKopf = null;
        StringBuilder steps = new StringBuilder();
        List<Zeile> zeilen = new ArrayList<>();
        int no = 0;
        for (String raw : text.split("\\R")) {
            no++;
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String low = line.toLowerCase(Locale.ROOT);
            try {
                if (low.startsWith("regiebuch:")) name = line.substring(10).strip();
                else if (low.startsWith("beschreibung:")) about = line.substring(13).strip();
                else if (low.startsWith("länge:")) length = sekunden(line.substring(6).strip());
                else if (low.startsWith("wetter:")) wetterKopf = line.substring(7).strip();
                else if (low.startsWith("schritt:")) steps.append(line).append('\n');
                else zeilen.add(zeile(line));
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("Zeile " + no + ": " + e.getMessage() + " („" + line + "“)");
            }
        }
        if (name == null || name.isEmpty()) throw new IllegalArgumentException("Es fehlt die Zeile „Regiebuch: Name“");
        if (zeilen.isEmpty()) throw new IllegalArgumentException("Das Regiebuch hat keine Stichworte");
        Scenario sc = null;
        if (wetterKopf != null) {
            String[] w = wetterKopf.split("\\|");
            String beginn = w[0].strip(), raffer = w.length > 1 ? w[1].strip() : "1";
            sc = Scenario.parse("Szenario: " + name + "\nBeginn: " + beginn + "\nZeitraffer: " + raffer + "\n" + steps);
        } else if (steps.length() > 0) throw new IllegalArgumentException("Schritte ohne Zeile „Wetter: hh:mm | Zeitraffer“");
        zeilen.sort(java.util.Comparator.comparingDouble(Zeile::sekunde));
        List<Cue> cues = new ArrayList<>();
        for (int i = 0; i < zeilen.size(); ) {
            Zeile z = zeilen.get(i);
            List<Zeile> grp = new ArrayList<>(List.of(z));
            int k = i + 1;
            while (k < zeilen.size() && zeilen.get(k).sekunde() == z.sekunde() && zeilen.get(k).text().isEmpty()) grp.add(zeilen.get(k++));
            final Scenario wsc = sc;
            List<Consumer<Ctx>> acts = new ArrayList<>();
            for (Zeile g : grp) acts.add(handlung(g, wsc, name));
            cues.add(new Cue(z.sekunde(), z.text(), x -> { for (Consumer<Ctx> a : acts) a.accept(x); }));
            i = k;
        }
        if (Double.isNaN(length)) length = cues.get(cues.size() - 1).t() + 1;
        return new Book(name, about, length, cues, text, zeilen, sc);
    }

    /** „1:48“, „0:04“, „96“ oder „1:36.5“ → Sekunden. */
    static double sekunden(String s) {
        int c = s.indexOf(':');
        if (c < 0) return Double.parseDouble(s);
        return Integer.parseInt(s.substring(0, c)) * 60 + Double.parseDouble(s.substring(c + 1));
    }

    /** Sekunden → „m:ss“ (mit Zehnteln, wenn nötig). */
    public static String zeit(double s) {
        int m = (int) (s / 60);
        double r = s - m * 60;
        return Math.abs(r - Math.rint(r)) < 1e-6 ? String.format(Locale.ROOT, "%d:%02d", m, (int) Math.rint(r)) : String.format(Locale.ROOT, "%d:%04.1f", m, r);
    }

    static Zeile zeile(String line) {
        String cmd = line, txt = "";
        int bar = line.indexOf(" | ");
        if (bar >= 0) { cmd = line.substring(0, bar).strip(); txt = line.substring(bar + 3).strip(); }
        String[] p = cmd.split("\\s+", 3);
        if (p.length < 2) throw new IllegalArgumentException("Zeit und Befehl erwartet");
        double t = sekunden(p[0]);
        String b = p[1].toLowerCase(Locale.ROOT);
        if (!BEFEHLE.contains(b)) throw new IllegalArgumentException("unbekannter Befehl „" + p[1] + "“, bekannt sind " + String.join(", ", BEFEHLE));
        return new Zeile(t, b, p.length > 2 ? p[2].strip() : "", txt);
    }

    /** Was ein Befehl tut; prüft die Argumente schon beim Lesen. */
    static Consumer<Ctx> handlung(Zeile z, Scenario sc, String buch) {
        String a = z.argumente();
        switch (z.befehl()) {
            case "zeit": {
                String[] p = a.split("\\s+");
                if (p.length != 2) throw new IllegalArgumentException("zeit TT.MM.JJJJ hh:mm erwartet");
                String[] d = p[0].split("\\.");
                LocalDate date = LocalDate.of(Integer.parseInt(d[2]), Integer.parseInt(d[1]), Integer.parseInt(d[0]));
                double h = sekunden(p[1]) / 60;
                return x -> x.time(date, h);
            }
            case "raffer": { double v = Double.parseDouble(a); return x -> x.lapse(v); }
            case "wetter": {
                if (sc == null) throw new IllegalArgumentException("wetter ohne Zeile „Wetter:“ im Kopf");
                return x -> x.weather(sc);
            }
            case "wetter_frei": {
                String[] p = a.split("\\s+");
                Weather.Phase ph = Weather.Phase.valueOf(p[0].toUpperCase(Locale.ROOT));
                double bft = p.length > 1 && !p[1].equals("-") ? Double.parseDouble(p[1]) : Double.NaN;
                double deg = p.length > 2 && !p[2].equals("-") ? Double.parseDouble(p[2]) : Double.NaN;
                return x -> x.weatherFree(ph, bft, deg);
            }
            case "ansicht": { need(a, "ansicht Name"); return x -> x.view(a); }
            case "fahrt": { need(a, "fahrt Name"); return x -> x.fahrt(a); }
            case "boot": return Ctx::boot;
            case "aussteigen": return Ctx::aussteigen;
            case "kalbung": {
                String k = a.toLowerCase(Locale.ROOT);
                Kalbung.Art art = k.startsWith("t") ? Kalbung.Art.TURM : k.startsWith("s") ? Kalbung.Art.SCHEIBE : k.startsWith("f") ? Kalbung.Art.FUSS : null;
                if (art == null) throw new IllegalArgumentException("kalbung Turm|Scheibe|Fuß erwartet");
                return x -> x.kalbung(art);
            }
            case "kalbung_regie": { boolean on = an(a, "kalbung_regie"); return x -> x.kalbungRegie(on); }
            case "ruptura": return Ctx::ruptura;
            case "damm": { boolean on = an(a, "damm"); return x -> x.damm(on); }
            case "figuren": { boolean on = an(a, "figuren"); return x -> x.figuren(on); }
            case "foto": { need(a, "foto Unterschrift"); return x -> x.capture(a); }
            case "blatt": { String n = a.isEmpty() ? buch : a; return x -> x.sheet(n); }
            case "sage": { need(a, "sage Text"); return x -> x.say(a); }
            default: throw new IllegalArgumentException(z.befehl());
        }
    }

    private static boolean an(String a, String befehl) {
        String v = a.strip().toLowerCase(Locale.ROOT);
        if (v.equals("an")) return true;
        if (v.equals("aus")) return false;
        throw new IllegalArgumentException(befehl + " an|aus erwartet");
    }

    private static void need(String a, String form) { if (a.isEmpty()) throw new IllegalArgumentException(form + " erwartet"); }

    /** Ein Regiebuch als Text aus Kopf, Wetter und Stichworten (für die Datenbank, Phase 15). */
    public static String toText(String name, String about, double length, String wetter, List<Zeile> zeilen) {
        StringBuilder b = new StringBuilder();
        b.append("Regiebuch: ").append(name).append('\n');
        if (about != null && !about.isEmpty()) b.append("Beschreibung: ").append(about).append('\n');
        b.append("Länge: ").append(zeit(length)).append('\n');
        if (wetter != null && !wetter.isBlank()) b.append(wetter.strip()).append('\n');
        for (Zeile z : zeilen)
            b.append(zeit(z.sekunde())).append(' ').append(z.befehl()).append(z.argumente().isEmpty() ? "" : " " + z.argumente())
                    .append(z.text().isEmpty() ? "" : " | " + z.text()).append('\n');
        return b.toString();
    }

    /** Der Wetterblock eines Texts („Wetter:“ und „Schritt:“-Zeilen) oder null. */
    public static String wetterBlock(String text) {
        StringBuilder b = new StringBuilder();
        for (String raw : text.split("\\R")) {
            String l = raw.strip().toLowerCase(Locale.ROOT);
            if (l.startsWith("wetter:") || l.startsWith("schritt:")) b.append(raw.strip()).append('\n');
        }
        return b.length() == 0 ? null : b.toString();
    }
}
