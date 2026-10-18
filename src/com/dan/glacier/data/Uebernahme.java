package com.dan.glacier.data;

import com.dan.store.Ablage;
import com.dan.store.Protokoll;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Übernahme aus den Dateien in die Datenbank (F33 A): die Chronik (Fronten, Kalbungen, Eisberge, Läufe der Ruptura und
 * der Regiebücher) und die eigenen Regiebücher, Ansichten und Presets. Die Nummern der Sitzungen aus den Dateien gibt es
 * in der Datenbank nicht; die Zeilen kommen ohne Sitzung hinein. Danach heißen die CSV der Chronik {@code *.uebernommen},
 * damit sie kein zweites Mal kommen; die übrigen Dateien bleiben.
 */
public final class Uebernahme {
    private Uebernahme() { }

    /** Was übernommen wurde, als Text („12 Kalbungen, 30 Eisberge …“). */
    public static String uebernehmen(DateiSpeicher von, Speicher nach) throws Ablage.Fehler {
        int fr = 0, ka = 0, ei = 0, lf = 0, rl = 0, rb = 0, an = 0, pr = 0;
        for (Daten.Front f : von.alleFronten()) { nach.front(f, null); fr++; }
        Map<Long, Long> kid = new HashMap<>();
        for (Daten.Kalbung k : von.kalbungenAlle()) { kid.put(k.id(), nach.kalbung(k, null)); ka++; }
        Map<Long, Long> lid = new HashMap<>();
        for (Daten.RupturaLauf l : von.rupturaLaeufe()) {
            long neu = nach.rupturaLauf(new Daten.RupturaLauf(0, l.jahr(), l.beginn(), l.durchbruch(), l.tunnel(), l.rinne(), l.bruecke(), l.einsturz(), l.ende(), l.phase(),
                    l.stauMaxM(), l.abflussMaxM3s(), l.volumenM3(), l.brueckeM3(), l.groesstesM3(), l.stuecke(), l.damm(), l.rinneLinie(), l.regiebuch()), null);
            lid.put(l.id(), neu);
            lf++;
        }
        // Eisberge: die Nummer muss ohne Sitzung eindeutig bleiben (Sitzung × 100 000 + Nummer)
        for (Object[] o : von.eisbergeAlle()) {
            Long s = (Long) o[0];
            Daten.Eisberg e = (Daten.Eisberg) o[1];
            int nr = (int) Math.min(Integer.MAX_VALUE, (s == null ? 0 : s) * 100_000L + e.nr());
            nach.eisberg(new Daten.Eisberg(nr, e.kalbungId() == null ? null : kid.get(e.kalbungId()), e.rupturaLaufId() == null ? null : lid.get(e.rupturaLaufId()),
                    e.geburt(), e.geburtX(), e.geburtZ(), e.volumenStartM3(), e.volumenM3(), e.tiefgangM(), e.freibordM(), e.kenterungen(), e.weg(), e.x(), e.z(),
                    e.gierGrad(), e.formSeed(), e.ende(), e.endeArt()), null);
            ei++;
        }
        for (Daten.Regielauf l : von.regielaeufe()) {
            byte[] png = null;
            if (l.blattDatei() != null && !l.blattDatei().isEmpty()) {
                try { png = java.nio.file.Files.readAllBytes(new File(l.blattDatei()).toPath()); } catch (Exception x) { /* ohne Blatt */ }
            }
            nach.regielauf(new Daten.Regielauf(l.name(), l.beginn(), l.ende(), l.vollstaendig(), l.bilderMittel(), l.bilderMin(), l.gpuMs(), l.fotos(), l.qualitaet(), png,
                    l.blattDatei()), null);
            rl++;
        }
        for (Daten.Regiebuch r : von.regiebuecher()) if (Daten.EIGEN.equals(r.herkunft())) { nach.speichern(r); rb++; }
        for (Daten.Ansicht a : von.ansichten()) if (Daten.EIGEN.equals(a.herkunft())) { nach.speichern(von.ansicht(a.id()), null); an++; }
        for (Daten.PaketPreset p : von.presets(null, null)) if (Daten.EIGEN.equals(p.herkunft())) { nach.speichern(p); pr++; }
        List<String> umbenannt = new ArrayList<>();
        for (File f : von.chronikDateien()) {
            if (!f.isFile()) continue;
            File neu = new File(f.getPath() + ".uebernommen");
            for (int i = 2; neu.exists(); i++) neu = new File(f.getPath() + "." + i + ".uebernommen");
            if (f.renameTo(neu)) umbenannt.add(f.getName());
        }
        String t = String.format(java.util.Locale.GERMAN, "%d Fronten, %d Kalbungen, %d Eisberge, %d Läufe der Ruptura, %d Läufe der Regiebücher, %d Regiebücher, %d Ansichten, %d Presets",
                fr, ka, ei, lf, rl, rb, an, pr);
        Protokoll.info("Übernahme", t + " aus " + von.wo() + " nach " + nach.wo() + (umbenannt.isEmpty() ? "" : "; umbenannt: " + String.join(", ", umbenannt)));
        return t;
    }

    /** Ob es in den Dateien etwas zu übernehmen gibt. */
    public static boolean etwasDa(DateiSpeicher d) {
        for (File f : d.chronikDateien()) if (f.isFile()) return true;
        return false;
    }
}
