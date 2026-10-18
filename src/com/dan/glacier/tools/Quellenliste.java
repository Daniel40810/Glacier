package com.dan.glacier.tools;

import com.dan.glacier.data.Daten;
import com.dan.glacier.data.Startdaten;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Phase 16: die Quellenliste {@code docs/QUELLEN.md} aus denselben Textdateien wie die Startdaten der Datenbank
 * ({@code data/quellen.txt}, {@code befunde.txt}, {@code ruptura.txt}). So steht dieselbe Liste im Schema GLACIER, in
 * {@code daten/} und auf GitHub. Aufruf (Run File) ohne Argumente schreibt {@code docs/QUELLEN.md}; die Prüfung
 * vergleicht die Datei mit {@link #text()}.
 */
public final class Quellenliste {
    private Quellenliste() { }

    public static final File DATEI = new File("docs/QUELLEN.md");

    public static String text() {
        List<Daten.Quelle> qs = Startdaten.quellen();
        List<Daten.Befund> bs = Startdaten.befunde();
        List<Daten.Ruptura> rs = Startdaten.rupturen();
        StringBuilder b = new StringBuilder();
        b.append("# Glacier – Quellen und Befunde\n\n");
        b.append("Erzeugt von `tools/Quellenliste` aus `src/com/dan/glacier/data/quellen.txt`, `befunde.txt` und `ruptura.txt`. Dieselben Zeilen stehen in den Tabellen ")
                .append("`GLC_QUELLE`, `GLC_BEFUND`, `GLC_BEFUND_QUELLE` und `GLC_RUPTURA`. Jeder Befund ist **belegt** (eine Quelle nennt es), **gemessen** ")
                .append("(aus Daten wie GLO-30 abgeleitet) oder **gestaltet** [G] (so gebaut, dass es glaubhaft ist, ohne Beleg).\n\n");
        b.append(String.format(Locale.GERMAN, "%d Quellen · %d Befunde (%d belegt, %d gemessen, %d gestaltet) · %d belegte Rupturas%n%n",
                qs.size(), bs.size(), zahl(bs, "BELEGT"), zahl(bs, "GEMESSEN"), zahl(bs, "GESTALTET"), rs.size()));
        b.append("## Quellen\n\n| Kurz | Art | Titel | Abgerufen | Wofür |\n|---|---|---|---|---|\n");
        for (Daten.Quelle q : qs)
            b.append("| ").append(q.kurz()).append(" | ").append(art(q.art())).append(" | ")
                    .append(q.url() == null || q.url().isBlank() ? esc(q.titel()) : "[" + esc(q.titel()) + "](" + q.url() + ")").append(" | ")
                    .append(q.abgerufen() == null ? "" : q.abgerufen().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))).append(" | ")
                    .append(esc(q.bemerkung() == null ? "" : q.bemerkung())).append(" |\n");
        b.append("\n## Befunde nach Thema\n");
        Map<String, StringBuilder> themen = new LinkedHashMap<>();
        for (Daten.Befund x : bs) {
            StringBuilder t = themen.computeIfAbsent(x.thema(), k -> new StringBuilder("\n### " + thema(k) + "\n\n| Gegenstand | Befund | Text | Quellen |\n|---|---|---|---|\n"));
            t.append("| ").append(esc(x.gegenstand())).append(" | ").append(befund(x.befund())).append(" | ").append(esc(x.text())).append(" | ")
                    .append(String.join(", ", x.quellen())).append(" |\n");
        }
        for (StringBuilder t : themen.values()) b.append(t);
        b.append("\n## Die belegten Rupturas (1917–2019)\n\n| Jahr | Datum | Stau (m) | Abfluss (m³/s) | Dauer (h) | Text | Quellen |\n|---|---|---|---|---|---|---|\n");
        for (Daten.Ruptura r : rs)
            b.append("| ").append(r.jahr()).append(r.nr() > 1 ? " (" + r.nr() + ")" : "").append(" | ").append(datum(r)).append(" | ")
                    .append(z(r.stauM())).append(" | ").append(z(r.abflussM3s())).append(" | ").append(z(r.dauerH())).append(" | ")
                    .append(esc(r.text())).append(" | ").append(String.join(", ", r.quellen())).append(" |\n");
        b.append("\nNicht aufgenommen sind Meldungen zu 1986 und 2022 (nur ein Reiseblog) und zum 29.8.2024 (Video, die Art des Ereignisses ist unklar).\n");
        return b.toString();
    }

    private static long zahl(List<Daten.Befund> bs, String art) { return bs.stream().filter(x -> art.equals(x.befund())).count(); }

    private static String datum(Daten.Ruptura r) {
        if (r.datum() == null) return "";
        return switch (r.genauigkeit() == null ? "TAG" : r.genauigkeit()) {
            case "JAHR" -> String.valueOf(r.datum().getYear());
            case "MONAT" -> r.datum().format(DateTimeFormatter.ofPattern("MM.yyyy"));
            default -> r.datum().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        };
    }

    private static String z(Double d) { return d == null ? "" : String.format(Locale.GERMAN, "%,.0f", d); }

    private static String art(String a) {
        return switch (a == null ? "" : a) {
            case "WEB" -> "Netz";
            case "BUCH" -> "Buch";
            case "ARTIKEL" -> "Artikel";
            case "DATEN" -> "Daten";
            case "MESSUNG" -> "Messung";
            default -> a == null ? "" : a;
        };
    }

    private static String befund(String b) {
        return switch (b) { case "BELEGT" -> "belegt"; case "GEMESSEN" -> "gemessen"; case "GESTALTET" -> "gestaltet [G]"; default -> b; };
    }

    private static String thema(String t) {
        return switch (t) {
            case "GELAENDE" -> "Gelände"; case "EIS" -> "Eis"; case "FRONT" -> "Front"; case "KALBUNG" -> "Kalbung"; case "WELLE" -> "Welle";
            case "RUPTURA" -> "Ruptura"; case "LEBEN" -> "Leben"; case "HIMMEL" -> "Himmel"; case "REGIE" -> "Regie";
            default -> t.charAt(0) + t.substring(1).toLowerCase(Locale.ROOT);
        };
    }

    private static String esc(String s) { return s == null ? "" : s.replace("|", "\\|").replace("\n", " "); }

    public static void main(String[] a) throws Exception {
        File f = a.length > 0 ? new File(a[0]) : DATEI;
        if (f.getParentFile() != null) f.getParentFile().mkdirs();
        Files.writeString(f.toPath(), text(), StandardCharsets.UTF_8);
        System.out.println("Quellenliste: " + f.getAbsolutePath());
    }
}
