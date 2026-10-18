package com.dan.glacier.scene;

import com.dan.fracture.Fracture;
import com.dan.fracture.Piece;
import com.dan.fracture.Poly;
import com.dan.fracture.Shape;
import com.dan.fracture.Solid;
import com.dan.ice.FrontBlock;
import com.dan.ice.Glacier;
import com.dan.glacier.wiring.Cells;
import com.dan.glacier.wiring.Wiring;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;

/**
 * Die Kalbung (Werkbuch Glacier, Phase 7). Die Front bleibt die Wand aus {@code com.dan.ice}, bis die Regie einen
 * Abschnitt wählt: Dann wird dort ein Stück Front ({@link FrontBlock}, 70 bis 110 m breit, 60 bis 80 m tief) in Zellen
 * zerlegt und an {@code com.dan.fracture} gegeben. Ab da laufen zwei Uhren: die <b>Schmelzkerbe</b> frisst sich an der
 * Wasserlinie in die Front (die Zellen in ihr schmelzen weg), und das Eis ist an den Spalten <b>geschwächt</b>. Welches
 * Stück wann bricht, entscheidet die Spannung in der Engine, nicht die Regie. Drei Arten entstehen aus der Anlage:
 * <ul>
 * <li><b>Turm:</b> eine Spalte 14 bis 22 m hinter der Front bis 35 m tief, zwei Flanken; die Kerbe höhlt den Fuß aus,
 * der Turm kippt nach vorn.</li>
 * <li><b>Scheibe:</b> eine Spalte 4 bis 7 m hinter der Front über die ganze Höhe über Wasser; die Scheibe reißt ab und
 * sackt ins Wasser.</li>
 * <li><b>Fuß:</b> über Wasser ist schon gekalbt, unter Wasser steht der Fuß 20 bis 30 m vor; der Auftrieb biegt ihn
 * hoch, er bricht an der Wurzel und schießt als Eisberg auf.</li>
 * </ul>
 * Die Regie würfelt in Szenenzeit: im Sommer im Mittel {@link #rateSummer} Abbrüche je Stunde auf der ganzen Front, im
 * Winter {@link #rateWinter} [G]. Von Hand löst {@link #trigger} einen Abbruch aus, mit schneller Kerbe.
 * <p>
 * Fäden: {@link #step} läuft im Faden der Physik vor {@code fracture}; das Zerlegen eines Abschnitts (einige
 * Zehntelsekunden) in einem eigenen Faden. Wer zeichnet, liest {@link #sections()} und die Lagen der Stücke.
 */
public final class Kalbung {
    public enum Art {
        TURM("Turm"), SCHEIBE("Scheibe"), FUSS("Fuß");
        public final String label;
        Art(String l) { label = l; }
    }

    public enum Zustand { VORBEREITET, LAEUFT, GEBROCHEN, RUHT }

    /** Ein Abschnitt der Front als Körper. */
    public static final class Abschnitt {
        public final int nr;
        public final Art art;
        public final FrontBlock block;
        public final Solid solid;
        public final boolean fast;
        public volatile Zustand zustand = Zustand.VORBEREITET;
        /** Tiefe der Kerbe (m ins Eis), ihre Breite längs der Front (halb) und ihre Grenze. */
        public double notch, notchHalf, notchMax;
        /** Szenenzeit des Starts, erster Riss, erstes freies Stück (s; NaN: noch nicht). */
        public double started, firstCrack = Double.NaN, firstBreak = Double.NaN;
        /** Volumen und Zahl der Stücke, die hier frei wurden. */
        public double volume;
        public int pieces;
        /** Erwartetes Volumen des Hauptstücks (m³) und ob es gefallen ist. */
        public double expected;
        public boolean main;
        int logged = -1;
        double quiet;
        /** Mitte und Reichweite des erwarteten Hauptstücks (örtlich a, b, Höhe y; m) und die Ermüdung danach. */
        double mainA, mainB, mainY, mainR, fatigue;
        /** Was in den letzten Sekunden frei wurde {Zeit, Volumen}: ein Abbruch kann auch in vielen Stücken kommen. */
        final java.util.ArrayDeque<double[]> burst = new java.util.ArrayDeque<>();
        public int tired;

        Abschnitt(int nr, Art art, FrontBlock block, Solid solid, boolean fast) {
            this.nr = nr; this.art = art; this.block = block; this.solid = solid; this.fast = fast;
        }

        public String describe() {
            return String.format(Locale.GERMAN, "%d %s: %s · Kerbe %.1f m · %d Stücke, %,.0f m³", nr, art.label, zustand.name().toLowerCase(Locale.ROOT),
                    notch, pieces, volume);
        }
    }

    /**
     * Ein Eintrag im Protokoll: Szenenzeit, Art, Volumen, Ort, Zahl der Stücke, Vorwarnung (s); seit Phase 15 für die
     * Chronik auch die Höhe des Hauptstücks über dem See beim Lösen, ob von Hand (Auslöser) und der Körper des Abschnitts;
     * seit Phase 16 die Zeit der Physik, zu der das Hauptstück frei ist ({@code mainBreak}; vorher der erste Bruch
     * überhaupt, oft ein kleiner Brocken, der auf dem Eis liegen blieb – Befund Fallzeit 23 s).
     */
    public record Ereignis(double time, Art art, double volume, double x, double z, int pieces, double crackLead, double height, boolean hand, int solid, double mainBreak) { }

    public final Fracture world;
    public final Glacier glacier;
    private final DoubleSupplier lapse;
    private final IntSupplier dayOfYear;
    /** Abbrüche je Stunde auf der ganzen Front im Hochsommer und im Winter [G]. */
    public volatile double rateSummer = 4, rateWinter = 0.5;
    /** Kerbe: Meter je Szenenminute (Regie) und je Sekunde echter Zeit (von Hand). */
    public volatile double notchPerMinute = 0.6, notchFastPerSecond = 0.7;
    /** Die Regie würfelt Abbrüche; aus: nur von Hand. */
    public volatile boolean regie = true;
    /** Höchstens so viele Abschnitte gleichzeitig im Bau und Bruch; so viele ruhende bleiben stehen. */
    public int maxLive = 2, keepResting = 6;
    /** Zellen je Abschnitt auf „Mittel“ (die Qualität der Engine skaliert). */
    public int cells = 450;
    public final List<Ereignis> log = Collections.synchronizedList(new ArrayList<>());
    private final List<Abschnitt> sections = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final ConcurrentLinkedQueue<Object[]> wishes = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Abschnitt> ready = new ConcurrentLinkedQueue<>();
    private final ExecutorService builder;
    private final SplittableRandom rnd;
    private int next = 1;
    private final java.util.concurrent.atomic.AtomicInteger building = new java.util.concurrent.atomic.AtomicInteger();
    private volatile double sceneTime;
    private double nextEvent = Double.NaN;
    private final ArrayDeque<double[]> pieceOrigin = new ArrayDeque<>();

    public Kalbung(Fracture world, Glacier glacier, DoubleSupplier lapse, IntSupplier dayOfYear, long seed) {
        this.world = world;
        this.glacier = glacier;
        this.lapse = lapse;
        this.dayOfYear = dayOfYear;
        rnd = new SplittableRandom(seed * 7919 + 17);
        builder = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Kalbung-Zellen");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        });
    }

    public List<Abschnitt> sections() { return sections; }

    /** Szenenzeit seit dem Start der Kalbung (s). */
    public double sceneTime() { return sceneTime; }
    public int building() { return building.get(); }

    /** Sommer 1, Winter 0 (Südhalbkugel: Hochsommer Mitte Januar). */
    public double summer() {
        double d = dayOfYear.getAsInt();
        return 0.5 + 0.5 * Math.cos((d - 16) / 365.25 * 2 * Math.PI);
    }

    /** Abbrüche je Stunde jetzt. */
    public double rate() { return rateWinter + (rateSummer - rateWinter) * summer(); }

    /**
     * Ein Abbruch von Hand: Art und Ort (nächster Punkt der Front), schnelle Kerbe. NaN als Ort: dort, wo die Regie
     * würfeln würde.
     */
    public void trigger(Art art, double x, double z) { wishes.add(new Object[]{art, x, z, true}); }

    /** Art nach der Statistik [G]: Turm 50 %, Scheibe 35 %, Fuß 15 %. */
    Art drawArt() {
        double u = rnd.nextDouble();
        return u < 0.5 ? Art.TURM : u < 0.85 ? Art.SCHEIBE : Art.FUSS;
    }

    /** Ein Ort auf der Front, gleich verteilt nach der Länge. */
    /**
     * Seit Phase 10: die Regie bevorzugt die Front im Blick (Mitte, Halbmesser in m, Anteil 0..1). Gleichmäßig über die
     * ganze Front kalbt das meiste dort, wo weder Steg noch Boot es sehen und die Welle den Pegel nie erreicht.
     */
    /** Seit Phase 10: was mit dem letzten Wunsch geschah (ausgelöst oder warum nicht). */
    public volatile String note = "";
    public volatile double focusX = Double.NaN, focusZ = Double.NaN, focusRadius = 1200, focusShare = 0.7;

    double[] drawPlace() {
        double fx = focusX, fz = focusZ;
        if (!Double.isNaN(fx) && rnd.nextDouble() < focusShare)
            for (int k = 0; k < 40; k++) {
                double[] p = drawAnywhere();
                if (Math.hypot(p[0] - fx, p[1] - fz) <= focusRadius) return p;
            }
        return drawAnywhere();
    }

    private double[] drawAnywhere() {
        List<double[]> lines = glacier.frontLines();
        double total = glacier.frontLength();
        double s = rnd.nextDouble() * total;
        for (double[] p : lines)
            for (int k = 2; k < p.length; k += 2) {
                double l = Math.hypot(p[k] - p[k - 2], p[k + 1] - p[k - 1]);
                if (s <= l) return new double[]{p[k - 2] + (p[k] - p[k - 2]) * s / l, p[k - 1] + (p[k + 1] - p[k - 1]) * s / l};
                s -= l;
            }
        double[] p = lines.isEmpty() ? new double[]{0, 0} : lines.get(0);
        return new double[]{p[0], p[1]};
    }

    /**
     * Plan der Regie für eine Szenendauer (s) ohne Physik: Zeitpunkte und Arten (für die Prüfung „eine Stunde Sommer
     * bringt die Statistik“). Zufall wie in {@link #step}: Abstände nach Poisson mit der Rate.
     */
    public List<Object[]> plan(double seconds) {
        List<Object[]> out = new ArrayList<>();
        double t = 0, r = rate() / 3600;
        while (true) {
            t += -Math.log(1 - rnd.nextDouble()) / r;
            if (t > seconds) break;
            out.add(new Object[]{t, drawArt()});
        }
        return out;
    }

    // ------------------------------------------------------------------ Schritt

    /** Ein Schritt der Physik (dt echte Sekunden), vor {@code fracture.step}. */
    public void step(double dt) {
        double sdt = dt * Math.max(0, lapse.getAsDouble());
        sceneTime += sdt;
        // Wünsche von Hand und die Regie
        Object[] w;
        while ((w = wishes.poll()) != null) start((Art) w[0], (Double) w[1], (Double) w[2], (Boolean) w[3]);
        if (regie && sdt > 0) {
            if (Double.isNaN(nextEvent)) nextEvent = sceneTime + -Math.log(1 - rnd.nextDouble()) * 3600 / Math.max(1e-6, rate());
            if (sceneTime >= nextEvent) {
                nextEvent = sceneTime + -Math.log(1 - rnd.nextDouble()) * 3600 / Math.max(1e-6, rate());
                if (live() + building.get() < maxLive) start(drawArt(), Double.NaN, Double.NaN, false);
            }
        }
        // fertig zerlegte Abschnitte in die Welt
        Abschnitt a;
        while ((a = ready.poll()) != null) {
            building.decrementAndGet();
            world.add(a.solid);
            a.zustand = Zustand.LAEUFT;
            a.started = sceneTime;
            sections.add(a);
        }
        // die Uhren
        for (Abschnitt s : sections) {
            if (s.zustand != Zustand.LAEUFT && s.zustand != Zustand.GEBROCHEN) continue;
            if (s.notch < s.notchMax) {
                double grow = s.fast ? notchFastPerSecond * dt : notchPerMinute / 60 * sdt;
                double before = s.notch;
                s.notch = Math.min(s.notchMax, s.notch + grow);
                if (Math.floor(s.notch) != Math.floor(before)) melt(s);
            }
            // Ermüdung: ist die Kerbe am Ende und das Hauptstück steht noch, kriecht das Eis weiter [G] – alle 4 s
            // (Zeitraffer: 4 min) wird das Stück um 15 % schwächer, höchstens zwölfmal
            if (!s.main && s.notch >= s.notchMax && s.zustand == Zustand.LAEUFT && s.tired < 12) {
                s.fatigue += s.fast ? dt : sdt / 60;
                if (s.fatigue >= 2.5) {
                    s.fatigue = 0;
                    s.tired++;
                    double[] c = s.block.world(s.mainA, s.mainB);
                    s.solid.weaken(c[0], s.mainY, c[1], s.mainR, 0.85);
                }
            }
            if (s.solid.cracking() && Double.isNaN(s.firstCrack)) s.firstCrack = world.time();
            // ruhig: kein Riss, kein waches Stück aus diesem Abschnitt seit 20 s
            boolean busy = s.solid.cracking();
            if (!busy) for (Piece p : world.pieces()) if (p.source == s.solid.id && !p.asleep() && !p.handedOver()) { busy = true; break; }
            s.quiet = busy ? 0 : s.quiet + dt;
            // zur Ruhe kommt er, wenn die Kerbe am Ende ist und nichts mehr bricht oder fällt
            if (s.notch >= s.notchMax && (s.main || s.tired >= 12) && s.quiet > (s.zustand == Zustand.GEBROCHEN ? 20 : 60)) s.zustand = Zustand.RUHT;
        }
        // alte ruhende Abschnitte zurück an die Wand (der Gletscher fließt nach)
        int resting = 0;
        for (int i = sections.size() - 1; i >= 0; i--) {
            Abschnitt s = sections.get(i);
            if (s.zustand != Zustand.RUHT) continue;
            if (++resting > keepResting) { world.remove(s.solid); sections.remove(s); }
        }
    }

    /** Ein Stück ist frei (aus {@code fracture.onBreak}): dem Abschnitt zuschreiben. */
    public void broke(int pieceId, double volume) {
        for (Piece p : world.pieces()) {
            if (p.id != pieceId) continue;
            for (Abschnitt s : sections) {
                if (s.solid.id != p.source) continue;
                s.pieces++;
                // Teile eines Stücks, das beim Aufschlag zerbricht, sind schon gezählt
                if (p.parent() >= 0) return;
                s.volume += volume;
                // Hauptstück: ein Stück über einem Viertel des Erwarteten, oder binnen 4 s zusammen über 40 %
                double now = world.time(), sum = 0;
                s.burst.addLast(new double[]{now, volume});
                while (!s.burst.isEmpty() && s.burst.peekFirst()[0] < now - 4) s.burst.pollFirst();
                for (double[] q : s.burst) sum += q[1];
                if (!s.main && (volume > 0.25 * s.expected || sum > 0.4 * s.expected)) {
                    // das Hauptstück: die Uhr hält an, und was stehen bleibt, ist frisches Eis ohne Spalten [G]
                    s.main = true;
                    s.notchMax = s.notch;
                    double[] c = s.block.world(0, s.block.depth / 2);
                    s.solid.weaken(c[0], (s.block.yBottom + s.block.yTop) / 2, c[1], 400, 1.8);
                }
                if (Double.isNaN(s.firstBreak)) s.firstBreak = world.time();
                if (s.main && s.logged < 0) {
                    double[] c = p.position();
                    s.logged = log.size();
                    boolean one = volume > 0.25 * s.expected;
                    log.add(new Ereignis(sceneTime, s.art, one ? volume : sum, c[0], c[2], one ? 1 : s.burst.size(), Double.isNaN(s.firstCrack) ? 0 : world.time() - s.firstCrack,
                            c[1] - s.block.waterLevel, s.fast, s.solid.id, world.time()));
                } else if (s.logged >= 0 && s.logged < log.size()) {
                    Ereignis e = log.get(s.logged);
                    log.set(s.logged, new Ereignis(e.time(), e.art(), e.volume() + volume, e.x(), e.z(), e.pieces() + 1, e.crackLead(), e.height(), e.hand(), e.solid(), e.mainBreak()));
                }
                if (s.main) s.zustand = Zustand.GEBROCHEN;
                return;
            }
            return;
        }
    }

    private int live() {
        int k = 0;
        for (Abschnitt s : sections) if (s.zustand == Zustand.LAEUFT || s.zustand == Zustand.GEBROCHEN) k++;
        return k;
    }

    // ------------------------------------------------------------------ Anlage

    private void start(Art art, double x, double z, boolean fast) {
        if (Double.isNaN(x)) {
            double[] p = drawPlace();
            x = p[0];
            z = p[1];
        }
        double width = 70 + 40 * rnd.nextDouble(), depth = art == Art.FUSS ? 75 : 65;
        FrontBlock b = FrontBlock.near(glacier, x, z, width, depth, art == Art.FUSS ? 45 : 35);
        if (b == null) { note = String.format(Locale.GERMAN, "%s nicht ausgelöst: um (%.0f, %.0f) keine Front, die über die Breite des Abschnitts gerade genug ist", art.label, x, z); return; }
        // nicht in einen Abschnitt hinein, der noch läuft; einen ruhenden dort nimmt der neue an seine Stelle (Phase 10)
        for (Abschnitt s : new ArrayList<>(sections)) {
            if (Math.hypot(s.block.px - b.px, s.block.pz - b.pz) >= (s.block.width + b.width) / 2 + 5) continue;
            if (s.zustand == Zustand.RUHT) { world.remove(s.solid); sections.remove(s); continue; }
            note = String.format(Locale.GERMAN, "%s nicht ausgelöst: dort läuft noch Abschnitt %d", art.label, s.nr);
            return;
        }
        note = String.format(Locale.GERMAN, "%s ausgelöst bei (%.0f, %.0f)", art.label, b.px, b.pz);
        long key = rnd.nextLong();
        int nr = next++;
        building.incrementAndGet();
        builder.submit(() -> {
            try {
                ready.add(build(nr, art, b, fast, new SplittableRandom(key)));
            } catch (RuntimeException e) {
                System.err.println("Kalbung: Abschnitt " + nr + ": " + e);
                building.decrementAndGet();
            }
        });
    }

    /** Legt den Abschnitt an: Schwächen nach der Art, Zellen, Halt hinten, seitlich und unten (im Faden „Kalbung-Zellen“). */
    Abschnitt build(int nr, Art art, FrontBlock b, boolean fast, SplittableRandom r) {
        double wl = b.waterLevel, top = b.yTop - 2, hw = b.width / 2;
        double notchHalf, notchMax, expected, mA, mB, mY, mR;
        switch (art) {
            case TURM -> {
                double behind = 14 + 8 * r.nextDouble(), half = 12 + 8 * r.nextDouble(), a0 = (r.nextDouble() - 0.5) * 10;
                // die Spalte reicht bis an die Kerbe hinunter: ist die Kerbe durch, hält den Turm nur noch die Spalte
                double y0 = Math.min(top - 25, wl + 1);
                b.weaken(FrontBlock.Weakness.behind(behind, 1.5, 0.12, y0, top + 2, a0 - half, a0 + half));
                b.weaken(FrontBlock.Weakness.side(a0 - half, 1.5, 0.2, y0, top + 2, -20, behind));
                b.weaken(FrontBlock.Weakness.side(a0 + half, 1.5, 0.2, y0, top + 2, -20, behind));
                notchHalf = half + 4;
                notchMax = behind + 4;
                expected = 2 * half * behind * (top - wl - 2);
                mA = a0; mB = behind / 2; mY = (wl + top) / 2; mR = Math.max(half, (top - wl) / 2) + 4;
            }
            case SCHEIBE -> {
                double behind = 5 + 3 * r.nextDouble(), half = 15 + 10 * r.nextDouble(), a0 = (r.nextDouble() - 0.5) * 10;
                b.weaken(FrontBlock.Weakness.behind(behind, 1.2, 0.03, wl - 2, top + 2, a0 - half, a0 + half));
                b.weaken(FrontBlock.Weakness.side(a0 - half, 1.2, 0.1, wl - 2, top + 2, -20, behind));
                b.weaken(FrontBlock.Weakness.side(a0 + half, 1.2, 0.1, wl - 2, top + 2, -20, behind));
                notchHalf = half + 2;
                notchMax = behind + 6;
                expected = 2 * half * behind * (top - wl);
                mA = a0; mB = behind / 2; mY = (wl + top) / 2; mR = Math.max(half, (top - wl) / 2) + 2;
            }
            default -> {
                // der Fuß: unten frei (eine Spalte am Grund des Abschnitts [G]), hinten an der Wurzel gehalten; die Uhr ist
                // hier ein Schmelzkanal, der die Wurzel von oben nach unten aufzehrt
                double reach = 20 + 10 * r.nextDouble(), half = 18 + 8 * r.nextDouble();
                b.weaken(FrontBlock.Weakness.behind(reach, 2.0, 0.12, b.yBottom, wl - 4, -half, half));
                b.weaken(FrontBlock.Weakness.side(-half, 2.0, 0.04, b.yBottom, wl - 4, -20, reach));
                b.weaken(FrontBlock.Weakness.side(half, 2.0, 0.04, b.yBottom, wl - 4, -20, reach));
                notchHalf = half;
                notchMax = (wl - 4) - b.yBottom;
                expected = 2 * half * reach * notchMax;
                mA = 0; mB = reach / 2; mY = (b.yBottom + wl - 4) / 2; mR = Math.max(half, notchMax / 2) + 4;
            }
        }
        Cells cl = Wiring.cells(b);
        Solid s = world.prepare(shape(cl), world.cellsFor(cells), cl::density, cl::strength);
        // Halt: hinten und seitlich geht der Gletscher weiter, unten der Grund (oder tieferes Eis)
        s.support(c -> {
            double[] l = b.local(c[0], c[2]);
            return Math.abs(l[0]) > hw - 12 || l[1] > b.depth - 12;
        });
        final double reachF = art == Art.FUSS ? b.weaknessAt(0) : 0, halfF = notchHalf;
        s.anchor(f -> {
            if (f.ny > -0.5) return false;
            double[] c = f.centroid();
            if (art == Art.FUSS) {
                double[] l = b.local(c[0], c[2]);
                if (l[1] < reachF && Math.abs(l[0]) < halfF) return false;
            }
            return c[1] < b.yBottom + 0.5 || c[1] < glacier.bed(c[0], c[2]) + 3;
        }, 3);
        Abschnitt a = new Abschnitt(nr, art, b, s, fast);
        a.notchHalf = notchHalf;
        a.notchMax = notchMax;
        a.expected = expected;
        a.mainA = mA; a.mainB = mB; a.mainY = mY; a.mainR = mR;
        if (art == Art.FUSS) {
            // über Wasser ist vorn schon abgebrochen: der Fuß steht vor
            s.melt(c -> {
                double[] l = b.local(c[0], c[2]);
                return l[1] < reachF && Math.abs(l[0]) < halfF + 6 && c[1] > wl - 4;
            });
        }
        // erwartet ist, was an Eis im Bereich des Hauptstücks wirklich da ist (am Ufer ist der Fuß flach, der Turm
        // niedriger), höchstens die Schätzung aus den Maßen
        double have = 0, aHalf = notchHalf, bMax = 2 * mB;
        for (int i = 0; i < s.cells(); i++) {
            if (!s.attached(i)) continue;
            double[] c = s.center(i), l = b.local(c[0], c[2]);
            boolean y = art == Art.FUSS ? c[1] < wl - 4 : c[1] > wl - 2;
            if (y && Math.abs(l[0] - mA) < aHalf && l[1] < bMax) have += s.volume(i);
        }
        a.expected = Math.max(1000, Math.min(expected, have));
        return a;
    }

    /**
     * Die Kerbe: was an der Wasserlinie vor ihr liegt, schmilzt weg. Es schmilzt jede Zelle, die in das Band der Kerbe
     * hineinreicht (nicht nur die, deren Mitte darin liegt): sonst blieben Brücken aus Zellen darüber und darunter.
     */
    private void melt(Abschnitt s) {
        FrontBlock b = s.block;
        double wl = b.waterLevel, d = s.notch, h = s.notchHalf;
        Solid so = s.solid;
        if (s.art == Art.FUSS) {
            // Schmelzkanal an der Wurzel: ein 3 m breiter Schlitz bei b = Reichweite, von oben nach unten
            double reach = b.weaknessAt(0), y0 = wl - 4 - d;
            so.meltCells(i -> {
                double[] c = so.center(i);
                if (c[1] < y0 || c[1] > wl) return false;
                double[][] r = ranges(b, so.cell(i));
                // die Wurzel (b = Reichweite) und die beiden Flanken (a = ± halbe Breite), ein U aus Schlitzen
                boolean root = r[1][0] < reach + 1.5 && r[1][1] > reach - 1.5 && r[0][1] > -h && r[0][0] < h;
                boolean side = r[1][0] < reach && (r[0][0] < -h + 1.5 && r[0][1] > -h - 1.5 || r[0][0] < h + 1.5 && r[0][1] > h - 1.5);
                return root || side;
            });
            return;
        }
        double lo = wl - 1.5, hi = wl + 2;
        so.meltCells(i -> {
            Poly p = so.cell(i);
            double top = p.maxAbove(0, 1, 0, 0), bot = -p.maxAbove(0, -1, 0, 0);
            if (top < lo || bot > hi) return false;
            double[] c = so.center(i), l = b.local(c[0], c[2]);
            // die Kerbe ist in der Mitte tiefer als am Rand
            double depth = d * (1 - 0.4 * Math.pow(Math.abs(l[0]) / h, 2));
            return Math.abs(l[0]) < h && l[1] < depth;
        });
    }

    /** Bereich der Ecken einer Zelle in den örtlichen Achsen: {{a min, a max}, {b min, b max}}. */
    static double[][] ranges(FrontBlock b, Poly p) {
        double a0 = Double.POSITIVE_INFINITY, a1 = Double.NEGATIVE_INFINITY, b0 = Double.POSITIVE_INFINITY, b1 = Double.NEGATIVE_INFINITY;
        for (Poly.Face f : p.faces)
            for (int k = 0; k < f.p.length; k += 3) {
                double[] l = b.local(f.p[k], f.p[k + 2]);
                a0 = Math.min(a0, l[0]); a1 = Math.max(a1, l[0]);
                b0 = Math.min(b0, l[1]); b1 = Math.max(b1, l[1]);
            }
        return new double[][]{{a0, a1}, {b0, b1}};
    }

    /** Der Abschnitt als Umriss für {@code fracture}: Quader, Oberseite als Tangentialebene, Grund, innen das Eis. */
    static Shape shape(Cells c) {
        double[] bounds = c.bounds(), planes = c.planes();
        return new Shape() {
            @Override public double[] bounds() { return bounds.clone(); }
            @Override public void clip(Poly p, double x, double y, double z) {
                for (int k = 0; k + 3 < planes.length; k += 4) p.clip(planes[k], planes[k + 1], planes[k + 2], planes[k + 3], -1);
                // Oberseite als Tangentialebene, flach gehalten (steile Ebenen an Spalten gaben Splitter, Status_Phase7)
                double h = 4;
                double t = c.top(x, z), tx = (c.top(x + h, z) - c.top(x - h, z)) / (2 * h), tz = (c.top(x, z + h) - c.top(x, z - h)) / (2 * h);
                double sl = Math.hypot(tx, tz);
                if (sl > 0.35) { tx *= 0.35 / sl; tz *= 0.35 / sl; }
                p.clip(-tx, 1, -tz, t - tx * x - tz * z, -1);
                p.clip(0, -1, 0, -c.bottom(x, z), -1);
                // an der Wand abschneiden (die Zellen am Rand reichten sonst bis an den Quader vor der Front)
                double[] fr = c.front(x, z);
                if (fr != null) p.clip(fr[0], 0, fr[1], fr[2], -1);
            }
            @Override public boolean inside(double x, double y, double z) { return c.inside(x, y, z); }
        };
    }

    /** „Kalbung: 4,0 je Stunde (Sommer 100 %) · 1 im Bau · 2 laufen · 3 Abbrüche, 12 400 m³“ */
    public String describe() {
        double vol = 0;
        int n;
        synchronized (log) {
            n = log.size();
            for (Ereignis e : log) vol += e.volume();
        }
        return String.format(Locale.GERMAN, "Kalbung: %.1f je Stunde (Sommer %.0f %%)%s · %d im Bau · %d laufen · %d Abbrüche, %,.0f m³",
                rate(), summer() * 100, regie ? "" : " · Regie aus", building.get(), live(), n, vol) + (note.isEmpty() ? "" : " · " + note);
    }

    public void shutdown() { builder.shutdownNow(); }
}
