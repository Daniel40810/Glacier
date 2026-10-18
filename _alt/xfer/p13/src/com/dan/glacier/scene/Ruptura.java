package com.dan.glacier.scene;

import com.dan.fracture.Fracture;
import com.dan.fracture.Piece;
import com.dan.fracture.Poly;
import com.dan.fracture.Solid;
import com.dan.ice.Glacier;
import com.dan.ice.IceDam;
import com.dan.glacier.wiring.Cells;
import com.dan.glacier.wiring.Wiring;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.DoubleSupplier;

/**
 * Die Ruptura (Werkbuch Glacier, Phase 13). Erreicht die Front die Halbinsel Magallanes, liegt an ihrer Spitze ein
 * Eisdamm ({@link IceDam}) im Spülsaum zwischen Gletscher und Ufer, und der Brazo Rico hat keinen Abfluss mehr: sein
 * Spiegel steigt über den Hauptsee (Stau). Dann findet das Wasser den Weg an der Sohle: ein Tunnel frisst sich von der
 * Seite des Brazo Rico unter dem Eis durch (Sickern), bricht zum Canal de los Témpanos durch (Durchbruch), das warme
 * Wasser weitet ihn (Tunnel), bis die Decke zu dünn wird und in Stücken in die Rinne fällt (Rinne). Über der Rinne
 * bleibt die Brücke stehen, wo das Eis am dicksten ist; sie stürzt zuletzt (Einsturz), dann läuft der Brazo Rico leer
 * (Abfluss). Belegt sind Stau bis 30 m, Tunnel, Brücke, Einsturz und Abfluss bis 8 000 m³/s; Form, Zeiten und die
 * Fläche des Brazo Rico sind gestaltet [G].
 * <p>
 * Der Damm ist ein Körper für {@code fracture} wie ein Abschnitt der Kalbung: das Wasser schmilzt Zellen aus (der
 * Tunnel), was dann bricht und fällt, entscheidet die Spannung. Das Wasser rechnet die App: Durchfluss durch den Tunnel
 * nach Torricelli mit Beiwert, höchstens {@link Site#RUPTURA_FLOW}; der Brazo Rico sinkt mit Durchfluss durch Fläche;
 * der Tunnel weitet sich mit der Wurzel des Durchflusses (Wärme des Wassers) [G]. Der Abfluss ist eine Quelle für
 * {@code wave} im Kanal, Gischt an der Mündung, Rauschen für {@code soundscape}; Stücke in der Rinne treibt die
 * Strömung hinaus ({@code fracture.current}).
 * <p>
 * Uhren: der Damm und das Wasser laufen in Szenenzeit; im Regiebuch „Die Ruptura“ mit eigenem Zeitraffer je Abschnitt
 * („Wochen in zehn Minuten“), sonst mit dem Zeitraffer der Uhr. Brüche, Fallen und die Welle rechnen in echter Zeit.
 * {@link #step} läuft im Faden der Physik vor {@code fracture}; gelesen wird aus jedem Faden.
 */
public final class Ruptura {
    public enum Phase {
        AUS("kein Damm"), DAMM("der Damm steht, der Brazo Rico staut sich"), STAU("der Stau steigt"), SICKERN("das Wasser frisst einen Tunnel"),
        TUNNEL("Durchbruch, der Tunnel weitet sich"), RINNE("die Decke stürzt in die Rinne"), BRUECKE("die Brücke steht allein"),
        ABFLUSS("die Brücke ist gefallen, der Brazo Rico läuft leer"), RUHE("vorbei");
        public final String label;
        Phase(String l) { label = l; }
        public boolean running() { return ordinal() >= STAU.ordinal() && ordinal() <= ABFLUSS.ordinal(); }
    }

    /** Ein Eintrag im Protokoll: Szenenzeit seit Beginn (s), Stau (m), Durchfluss (m³/s), Text. */
    public record Ereignis(double time, double stau, double flow, String text) {
        @Override public String toString() {
            return String.format(Locale.GERMAN, "%s · Stau %.1f m · %,.0f m³/s · %s", dauer(time), stau, flow, text);
        }
    }

    // ------------------------------------------------------------------ Ort und Maße [G]

    /** Die Rinne an der Spitze der Front: vom Brazo Rico (Süden) längs des Spülsaums zum Canal de los Témpanos. */
    public static final double[] RINNE = {20, 55, 55, -10, 90, -110, 88, -250, 62, -370, 42, -455};
    /** Quer zur Rinne: bis 80 m in den Gletscher, bis 165 m zum Ufer; vor den Mündungen 8 m. */
    public static final double Q_MIN = -80, Q_MAX = 165, OUT = 8;
    /** Krone des Damms über dem Hauptsee (m), Rauigkeit, Ufer ab (m), tiefster Fuß (m) [G]. */
    public static final double CROWN = 36, ROUGH = 3, SHORE = 32, FOOT = -30;
    /** Die Brücke: längs der Rinne (m, dem Steg gegenüber), halbe Länge, um so viel höher (m) [G]. */
    public static final double BRIDGE_S = 280, BRIDGE_HALF = 32, BRIDGE_CROWN = 8;
    /** Fläche des Brazo Rico mit dem Brazo Sur (m²) [G: die Szene zeigt davon 15 km²]. */
    public static final double BRAZO_AREA = 70e6;
    /** Ein Ort mitten im Brazo Rico (für die Füllung der Maske). */
    public static final double[] BRAZO_SEED = {3000, 2200};
    /** Höchster Stau des Regiebuchs und sein Anfang (m über dem Hauptsee) [G]. */
    public static final double STAU_MAX = 25, STAU_START = 21;
    /** Tunnel: Halbmesser beim Sickern, ab dem die Decke fällt, größter (m) [G]; unter der Brücke weitet er sich langsamer. */
    public static final double R_SEEP = 3, R_ROOF = 12, R_MAX = 22, BRIDGE_SLOW = 0.72;
    /** Querschnitt des Tunnels: senkrechte Wände bis so viel mal den Halbmesser über der Sohle, darüber ein Gewölbe [G]. */
    public static final double WALL = 0.5;
    /** Beiwert des Tunnels (Torricelli) und Weitung: Meter je Stunde bei 1 000 m³/s, mit der Wurzel des Durchflusses [G]. */
    public static final double CD = 0.5, EROSION = 0.9;
    /** Sickern: Meter je Tag, die der Tunnel vom Brazo Rico zum Kanal vorankommt [G]. */
    public static final double SEEP_PER_DAY = 80;

    /** Zeitraffer des Regiebuchs je Abschnitt (Szenensekunden je echter Sekunde): „Wochen in zehn Minuten“. */
    public static double pace(Phase p) {
        return switch (p) {
            case STAU -> 69_120;      // 24 Tage in 30 s
            case SICKERN -> 12_800;   // 6,6 Tage in 45 s
            case TUNNEL -> 900;       // rund 15 h in 60 s
            case RINNE -> 150;        // die Decke fällt: 2½ min je Sekunde
            case BRUECKE -> 60;       // die Brücke: eine Minute je Sekunde
            case ABFLUSS -> 3600;     // drei Tage in rund 70 s
            default -> 0;
        };
    }

    // ------------------------------------------------------------------ Zustand

    public final Fracture world;
    public final Terrain terrain;
    public final Glacier glacier;
    public final IceDam dam;
    private final DoubleSupplier clockLapse;
    private volatile Solid solid;
    private volatile Phase phase = Phase.AUS;
    private volatile double stau, flow, progress, radius, sceneTime, shownPace;
    /** Das Regiebuch läuft (eigener Zeitraffer) – sonst die Uhr. */
    public volatile boolean regiebuch;
    private volatile boolean building, wantDam;
    private volatile double yearStau;
    private double meltedR = -1, meltedP = -1, fatigue, inBridge, bridgeCells;
    private int tired, bridgeTired, forced;
    private double sourceClock, sprayClock;
    private final ConcurrentLinkedQueue<Solid> ready = new ConcurrentLinkedQueue<>();
    private final ExecutorService builder;
    public final List<Ereignis> log = Collections.synchronizedList(new ArrayList<>());
    /** Zellen auf „Mittel“ (die Qualität der Engine skaliert). */
    public int cells = 1300;
    /** Die Maske des Brazo Rico (1: Wasser des gestauten Arms), Raster wie das Gelände. */
    private volatile byte[] mask;
    private volatile int maskVersion;
    /** Was die Rinne der App zu tun gibt: Quelle der Welle, Gischt (Faden der Physik schreibt, Bild liest). */
    public final ConcurrentLinkedQueue<double[]> sources = new ConcurrentLinkedQueue<>(), splashes = new ConcurrentLinkedQueue<>();
    /** Wann die Brücke fiel (Szenenzeit; NaN: noch nicht), ihr Volumen und das größte Stück. */
    public volatile double bridgeFell = Double.NaN, bridgeVolume, largest;
    public volatile int fallen;
    private volatile String note = "";

    public Ruptura(Fracture world, Terrain terrain, Glacier glacier, DoubleSupplier clockLapse, long seed) {
        this.world = world;
        this.terrain = terrain;
        this.glacier = glacier;
        this.clockLapse = clockLapse;
        dam = damm(terrain, glacier::covers, seed);
        builder = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Ruptura-Zellen");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        });
        builder.submit(() -> {
            try { mask = brazoMask(terrain, dam, glacier::covers); maskVersion++; }
            catch (RuntimeException e) { System.err.println("Ruptura: Maske " + e); }
        });
    }

    /** Der Damm an der Spitze der Front (auch ohne Gletscher, etwa für den Wald: dann gilt die Herkunft des Geländes). */
    public static IceDam damm(Terrain t, IceDam.Cover cover, long seed) {
        return new IceDam(t::height, cover, RINNE, Q_MIN, Q_MAX, OUT, CROWN, ROUGH, SHORE, FOOT, 0, BRIDGE_S, BRIDGE_HALF, BRIDGE_CROWN, seed);
    }

    // ------------------------------------------------------------------ Lesen

    public Phase phase() { return phase; }
    public Solid solid() { return solid; }
    /** Stau des Brazo Rico über dem Hauptsee (m). */
    public double stau() { return stau; }
    /** Abfluss durch die Rinne (m³/s). */
    public double flow() { return flow; }
    /** Wie weit der Tunnel von der Seite des Brazo Rico gekommen ist (m) und sein Halbmesser (m). */
    public double progress() { return progress; }
    public double radius() { return radius; }
    /** Szenenzeit seit dem Beginn (s). */
    public double sceneTime() { return sceneTime; }
    /** Der Damm steht (als Körper in der Welt oder im Bau). */
    public boolean present() { return phase != Phase.AUS; }
    /** Ob er Wasser staut, das die App zeichnen muss. */
    public boolean raised() { return present() && stau > 0.05; }
    public boolean building() { return building; }
    public byte[] mask() { return mask; }
    public int maskVersion() { return maskVersion; }
    public String note() { return note; }

    /** Liegt (x, z) im gestauten Brazo Rico? */
    public boolean inBrazo(double x, double z) {
        byte[] m = mask;
        if (m == null) return false;
        int i = (int) Math.floor((x - Terrain.X0) / Terrain.CELL), j = (int) Math.floor((z - Terrain.Z0) / Terrain.CELL);
        if (i < 0 || j < 0 || i >= Terrain.N || j >= Terrain.N) return false;
        return m[j * Terrain.N + i] != 0;
    }

    // ------------------------------------------------------------------ Steuern

    /** Je Bild aus der App: ein Jahr mit Damm und der Stau des Jahresgangs (m); die Regie folgt ihm, solange nichts läuft. */
    public void follow(boolean dammJahr, double stauJahr, int dayOfYear) {
        wantDam = regiebuch || phase.running() || dammJahr && (stauJahr > 0.5 || phase == Phase.RUHE);
        yearStau = stauJahr;
        // ohne Regiebuch kommt die Ruptura im März von selbst, wenn der Stau hoch ist [G]
        if (dammJahr && phase == Phase.DAMM && !regiebuch && stau > 8 && dayOfYear >= com.dan.glacier.wiring.Jahr.BREAK_DAY - 4
                && dayOfYear < com.dan.glacier.wiring.Jahr.BREAK_DAY + 30) start(Phase.SICKERN, false);
        // nach der Ruptura: schließt der Damm im Juni wieder, steht ein neuer
        if (phase == Phase.RUHE && !regiebuch && dammJahr && dayOfYear >= com.dan.glacier.wiring.Jahr.CLOSE_DAY && stauJahr > 0.5 && stauJahr < 2) reset();
    }

    /** Das Regiebuch „Die Ruptura“: der Brazo Rico steht 21 m hoch und steigt auf 25 m, dann der Rest mit eigenem Zeitraffer. */
    public void regiebuch() {
        regiebuch = true;   // gleich, damit die App den Damm nicht wegnimmt, bevor der Wunsch ankommt
        wishes.add(new Object[]{"regiebuch"});
    }

    private final ConcurrentLinkedQueue<Object[]> wishes = new ConcurrentLinkedQueue<>();

    /** Von Hand: der Tunnel ist durch. */
    public void durchbruchJetzt() { wishes.add(new Object[]{"durch"}); }
    /** Von Hand: die Brücke stürzt (die Decke daneben auch). */
    public void einsturzJetzt() { wishes.add(new Object[]{"einsturz"}); }
    /** Alles zurück: neuer Damm (wenn ein Jahr mit Damm ist), kein Tunnel, Protokoll leer. */
    public void reset() { wishes.add(new Object[]{"reset"}); }

    private void start(Phase p, boolean book) {
        if (p == Phase.STAU || p == Phase.SICKERN) {
            sceneTime = 0;
            log.clear();
        }
        regiebuch = book;
        phase = p;
        event(p == Phase.STAU ? String.format(Locale.GERMAN, "Regiebuch: der Brazo Rico steht %.0f m über dem Hauptsee", stau)
                : String.format(Locale.GERMAN, "das Wasser sucht den Weg unter dem Damm (%.1f m Stau)", stau));
    }

    // ------------------------------------------------------------------ Schritt

    /** Ein Schritt der Physik (dt echte Sekunden), vor {@code fracture.step}. */
    public void step(double dt) {
        Object[] w;
        while ((w = wishes.poll()) != null) {
            switch ((String) w[0]) {
                case "reset" -> doReset();
                case "regiebuch" -> {
                    // ein frischer Damm, auch wenn der alte schon gebrochen ist
                    if (phase != Phase.DAMM || solid == null) doReset();
                    regiebuch = true;
                    wantDam = true;
                    stau = STAU_START;
                    progress = radius = flow = 0;
                    start(Phase.STAU, true);
                }
                // von Hand übernimmt der Zeitraffer des Regiebuchs (mit stehender Uhr käme sonst nichts voran)
                case "durch" -> { if (phase == Phase.DAMM || phase == Phase.STAU || phase == Phase.SICKERN) { if (phase == Phase.DAMM) start(Phase.SICKERN, true); if (phase == Phase.STAU) phase = Phase.SICKERN; regiebuch = true; progress = dam.length; } }
                case "einsturz" -> { if (phase.ordinal() >= Phase.TUNNEL.ordinal() && phase.ordinal() <= Phase.BRUECKE.ordinal()) { regiebuch = true; forceCollapse(true); } }
                default -> { }
            }
        }
        // der Körper: anlegen, wenn er gebraucht wird, wegnehmen, wenn nicht
        Solid s;
        while ((s = ready.poll()) != null) {
            building = false;
            if (!wantDam) continue;
            world.add(s);
            solid = s;
            meltedR = meltedP = -1;
            if (phase == Phase.AUS) phase = Phase.DAMM;
        }
        if (wantDam && solid == null && !building) build();
        if (!wantDam && phase != Phase.AUS && !phase.running()) {
            Solid old = solid;
            if (old != null) world.remove(old);
            solid = null;
            phase = Phase.AUS;
            stau = 0;
            flow = 0;
        }
        if (phase == Phase.AUS) { stau = 0; flow = 0; return; }
        if (phase == Phase.DAMM) {
            stau = Math.max(0, yearStau);
            flow = 0;
            progress = 0;
            radius = 0;
            shownPace = 0;
            return;
        }
        // ohne Körper (er wird noch gebaut) steht die Uhr der Ruptura
        if (solid == null) { shownPace = 0; return; }
        double pace = regiebuch ? pace(phase) : Math.max(0, clockLapse.getAsDouble());
        shownPace = pace;
        double sdt = dt * pace;
        sceneTime += sdt;
        double g = 9.81;
        switch (phase) {
            case STAU -> {
                stau = Math.min(STAU_MAX, stau + (STAU_MAX - STAU_START) / (24 * 86400.0) * sdt);
                if (stau >= STAU_MAX - 1e-6) { event("der Stau steht am höchsten; das Wasser drückt unter den Damm"); phase = Phase.SICKERN; }
            }
            case SICKERN -> {
                progress = Math.min(dam.length, progress + SEEP_PER_DAY / 86400.0 * sdt);
                radius = R_SEEP;
                stau += (0.04 + 0.08 * 0.6) / 86400 * sdt * (regiebuch ? 0 : 1);   // ohne Regiebuch steigt der Arm weiter
                flow = 0;
                if (progress >= dam.length) {
                    phase = Phase.TUNNEL;
                    event("Durchbruch: der Tunnel ist offen, Wasser schießt in den Canal de los Témpanos");
                }
            }
            case TUNNEL, RINNE, BRUECKE, ABFLUSS -> {
                progress = dam.length;
                double a = Math.PI * radius * radius / 2;
                double q = Math.min(Site.RUPTURA_FLOW, CD * a * Math.sqrt(2 * g * Math.max(0, stau)));
                flow = q;
                stau = Math.max(0, stau - q / BRAZO_AREA * sdt);
                radius = Math.min(R_MAX, radius + EROSION / 3600 * Math.sqrt(q / 1000) * sdt);
                if (phase == Phase.TUNNEL && radius >= R_ROOF) {
                    phase = Phase.RINNE;
                    event(String.format(Locale.GERMAN, "der Tunnel ist %.0f m weit, die Decke wird dünn", 2 * radius));
                }
                if (phase == Phase.RINNE) roof(dt);
                if (phase == Phase.BRUECKE) bridge(dt);
                if (phase == Phase.ABFLUSS && stau < 0.2) {
                    phase = Phase.RUHE;
                    flow = 0;
                    event("der Brazo Rico ist auf dem Spiegel des Hauptsees");
                }
                emit(dt);
            }
            default -> flow = 0;
        }
        meltTunnel();
    }

    /** Die Decke außerhalb der Brücke: ermüdet alle 3 s, bis sie gefallen ist; dann steht die Brücke allein. */
    private void roof(double dt) {
        Solid s = solid;
        if (s == null) return;
        double[] cnt = roofCells(s);
        if (cnt[0] < 0.18 * Math.max(1, cnt[1]) && radius >= R_ROOF + 3) {
            phase = Phase.BRUECKE;
            bridgeCells = cnt[3];
            fatigue = 0;
            event(String.format(Locale.GERMAN, "die Rinne ist offen, über ihr steht die Brücke (%.0f m über dem Wasser)", dam.crown + dam.bridgeCrown - waterSurface(BRIDGE_S)));
            return;
        }
        fatigue += dt;
        if (fatigue >= 3 && radius >= R_ROOF + 2) {
            fatigue = 0;
            tired++;
            // die Decke ermüdet abschnittweise, von der Mündung am Kanal her (dort ist das Wasser am schnellsten)
            for (double sv = dam.length - 15; sv > 0; sv -= 30) {
                if (Math.abs(sv - BRIDGE_S) < BRIDGE_HALF + 18) continue;
                double[] p = dam.at(sv);
                s.weaken(p[0], p[4] + crownOf(radius) + 5, p[1], 22, 0.82);
            }
            if (tired > 25) forceCollapse(false);
        }
    }

    /** Die Brücke: das Wasser weitet den Tunnel unter ihr, sie ermüdet; nach 45 s echter Zeit bricht sie an ihren Enden. */
    private void bridge(double dt) {
        Solid s = solid;
        if (s == null) return;
        inBridge += dt;
        double[] cnt = roofCells(s);
        if (cnt[3] < 0.25 * Math.max(1, bridgeCells)) {
            bridgeFell = sceneTime;
            phase = Phase.ABFLUSS;
            event(String.format(Locale.GERMAN, "Einsturz: die Brücke ist gefallen (%,.0f m³ in %d Stücken)", bridgeVolume, fallen));
            return;
        }
        fatigue += dt;
        if (fatigue >= 4) {
            fatigue = 0;
            bridgeTired++;
            double[] p = dam.at(BRIDGE_S);
            s.weaken(p[0], p[4] + crownOf(radius) + 6, p[1], BRIDGE_HALF + 10, 0.8);
        }
        if (inBridge > 45 && forced == 0) forceCollapse(true);
    }

    /**
     * Zählt Zellen über der Rinne: {Decke außerhalb der Brücke noch dran, Decke zu Beginn, frei gewordene, Brücke dran}.
     * Decke ist, was quer im Tunnel liegt und über ihm.
     */
    private double[] roofCells(Solid s) {
        double attached = 0, total = 0, freed = 0, br = 0;
        for (int i = 0; i < s.cells(); i++) {
            double[] c = s.center(i), ch = dam.channel(c[0], c[2]);
            if (ch[0] < 10 || ch[0] > dam.length - 10 || Math.abs(ch[1]) > radius * 0.8) continue;
            if (c[1] < ch[2] + crownOf(radius) * 0.7) continue;
            boolean inB = Math.abs(ch[0] - BRIDGE_S) < BRIDGE_HALF;
            if (inB) { if (s.attached(i)) br++; continue; }
            total++;
            if (s.attached(i)) attached++; else freed++;
        }
        return new double[]{attached, total, freed, br};
    }

    /** Zwingt den Einsturz: die Bindungen quer über der Rinne an den Enden der Brücke (und, wenn gewollt, der Decke) brechen. */
    private void forceCollapse(boolean bridgeToo) {
        Solid s = solid;
        if (s == null) return;
        forced++;
        if (radius < R_ROOF + 3) radius = R_ROOF + 3;
        for (int b = 0; b < s.bonds(); b++) {
            if (s.broken(b)) continue;
            double[] c = s.bondCenter(b), ch = dam.channel(c[0], c[2]);
            if (Math.abs(ch[1]) > radius + 4 || c[1] < ch[2] + WALL * radius) continue;
            double ds = Math.abs(ch[0] - BRIDGE_S);
            boolean ends = Math.abs(ds - BRIDGE_HALF) < 5;
            boolean side = Math.abs(Math.abs(ch[1]) - radius) < 5;
            if ((bridgeToo && (ends || ds < BRIDGE_HALF && side)) || (!bridgeToo && ds > BRIDGE_HALF + 5 && side)) s.breakBond(b);
        }
        if (bridgeToo && phase != Phase.BRUECKE) { phase = Phase.BRUECKE; inBridge = 0; bridgeCells = Math.max(1, roofCells(s)[3]); }
        event(bridgeToo ? "die Brücke reißt an ihren Enden" : "die Decke reißt an den Wänden der Rinne");
    }

    /** Ein Stück ist frei (aus {@code fracture.onBreak}). */
    public void broke(int pieceId, double volume) {
        Solid s = solid;
        if (s == null) return;
        for (Piece p : world.pieces()) {
            if (p.id != pieceId || p.source != s.id || p.parent() >= 0) continue;
            fallen++;
            largest = Math.max(largest, volume);
            double[] c = p.position(), ch = dam.channel(c[0], c[2]);
            if (Math.abs(ch[0] - BRIDGE_S) < BRIDGE_HALF + 8) bridgeVolume += volume;
            return;
        }
    }

    // ------------------------------------------------------------------ Tunnel

    /** Schmilzt, was im Tunnel liegt: jede Zelle, die mit einer Ecke in den Halbkreis über der Sohle reicht. */
    private void meltTunnel() {
        Solid s = solid;
        if (s == null || radius <= 0 || progress <= 0) return;
        if (Math.abs(radius - meltedR) < 0.75 && Math.abs(progress - meltedP) < 8) return;
        meltedR = radius;
        meltedP = progress;
        final double r = radius, p = progress, bridgeWide = phase == Phase.BRUECKE ? 1 : 0;
        int gone = s.meltCells(i -> {
            Poly c = s.cell(i);
            double[] ce = s.center(i), cc = dam.channel(ce[0], ce[2]);
            if (cc[0] > p + 12 || Math.abs(cc[1]) > r + 14) return false;
            for (Poly.Face f : c.faces)
                for (int k = 0; k < f.p.length; k += 3) {
                    double x = f.p[k], y = f.p[k + 1], z = f.p[k + 2];
                    double[] ch = dam.channel(x, z);
                    if (ch[0] > p || ch[0] < -OUT - 2) continue;
                    double ds = Math.abs(ch[0] - BRIDGE_S);
                    double under = 1 - smooth(BRIDGE_HALF - 4, BRIDGE_HALF + 10, ds);
                    double rr = r * (1 - (1 - BRIDGE_SLOW) * under * (1 - bridgeWide));
                    // senkrechte Wände, darüber das Gewölbe (sonst bleibt die Decke zwischen schrägen Wänden hängen)
                    if (inTube(ch[1], y - ch[2], rr)) return true;
                }
            return false;
        });
        // was auf den Wänden lag, die das Wasser gerade ausgespült hat, fällt nach (ruhende Stücke wecken)
        if (gone > 0) for (Piece pc : world.pieces()) {
            if (!pc.asleep()) continue;
            double[] c = pc.position(), ch = dam.channel(c[0], c[2]);
            if (Math.abs(ch[1]) < r + 25 && ch[0] > -OUT && ch[0] < dam.length + 20) pc.wake();
        }
    }

    /** Liegt ein Punkt quer q (m) und dy über der Sohle (m) im Tunnel vom Halbmesser r? */
    public static boolean inTube(double q, double dy, double r) {
        if (Math.abs(q) >= r || dy < -1) return false;
        double w = WALL * r;
        if (dy <= w) return true;
        double a = dy - w;
        return q * q + a * a < r * r;
    }

    /** Scheitel des Tunnels über der Sohle (m). */
    public static double crownOf(double r) { return (1 + WALL) * r; }

    // ------------------------------------------------------------------ Wasser

    /** Wasserspiegel in der Rinne bei s (m über dem Hauptsee): vom Brazo Rico zur Mündung, dort die Grenztiefe. */
    public double waterSurface(double s) {
        double hb = stau, q = flow;
        if (phase.ordinal() < Phase.TUNNEL.ordinal() || q <= 0) return hb;
        double w = Math.max(4, 1.9 * radius);
        double dc = Math.cbrt((q / w) * (q / w) / 9.81);
        double[] mouth = dam.at(dam.length);
        double out = Math.max(0, mouth[4] + dc);
        double u = Math.max(0, Math.min(1, s / dam.length));
        double h = out + (hb - out) * (1 - Math.pow(u, 2.2));
        double[] p = dam.at(s);
        return Math.max(h, p[4] + Math.min(dc, 0.8 * dc + 0.5));
    }

    /** Breite des Wassers in der Rinne (m). */
    public double waterWidth() { return Math.max(4, 1.9 * radius); }

    /** Fließgeschwindigkeit in der Rinne bei s (m/s). */
    public double speed(double s) {
        if (flow <= 0) return 0;
        double[] p = dam.at(s);
        double depth = Math.max(1, waterSurface(s) - p[4]);
        return Math.min(16, flow / (waterWidth() * depth));
    }

    /** Wasserhöhe für Stücke: der gestaute Brazo Rico, das Wasser in der offenen Rinne; sonst NaN (dann der See). */
    public double waterAt(double x, double z) {
        if (!raised() && flow <= 0) return Double.NaN;
        if (inBrazo(x, z)) return stau;
        if (flow > 0 && dam.inBox(x, z)) {
            double[] ch = dam.channel(x, z);
            if (Math.abs(ch[1]) < waterWidth() / 2 + 2 && ch[0] > -OUT && ch[0] < dam.length + 10) return waterSurface(ch[0]);
        }
        return Double.NaN;
    }

    /** Strömung in der Rinne (m/s) an (x, z) in out[0], out[1]; false, wo keine ist. */
    public boolean currentAt(double x, double z, double[] out) {
        if (flow <= 0 || !dam.inBox(x, z)) return false;
        double[] ch = dam.channel(x, z);
        if (Math.abs(ch[1]) > waterWidth() / 2 + 2 || ch[0] < -OUT || ch[0] > dam.length + 40) return false;
        double v = speed(Math.min(dam.length, Math.max(0, ch[0])));
        out[0] = ch[3] * v;
        out[1] = ch[4] * v;
        return true;
    }

    /**
     * Das Wasser in der Rinne als Band für das Bild: je Stützpunkt zwei Ecken {x, y, z, s, quer, Tempo, Gefälle,
     * Richtung x, Richtung z} und Dreiecke; vom Brazo Rico bis 35 m hinter die Mündung, wo es in den Kanal stürzt.
     * null, solange nichts fließt. lake: Spiegel des Hauptsees (m, y).
     */
    public Object[] ribbon(double lake) {
        if (flow <= 0.5 || phase.ordinal() < Phase.TUNNEL.ordinal()) return null;
        java.util.List<float[]> v = new ArrayList<>();
        double half = waterWidth() / 2, L = dam.length, fall = 35;
        double hEnd = waterSurface(L);
        double[] end = dam.at(L);
        int n = 0;
        for (double s = -OUT - 6; s <= L + fall + 1e-6; s += 4, n++) {
            double[] p;
            double h, hw = half;
            if (s < 0) { p = dam.at(0); p = new double[]{p[0] + p[2] * s, p[1] + p[3] * s, p[2], p[3], p[4]}; h = stau; }
            else if (s <= L) { p = dam.at(s); h = waterSurface(s); }
            else {
                double u = (s - L) / fall;
                p = new double[]{end[0] + end[2] * (s - L), end[1] + end[3] * (s - L), end[2], end[3], end[4]};
                h = hEnd + (0.2 - hEnd) * (1 - (1 - u) * (1 - u));
                hw = half * (1 + 1.2 * u);
            }
            double sp = speed(Math.max(0, Math.min(L, s)));
            double h2 = s + 2 <= L ? waterSurface(Math.max(0, s + 2)) : h, h1 = s - 2 >= 0 && s - 2 <= L ? waterSurface(s - 2) : h;
            double slope = s > L ? Math.abs(hEnd) / fall * 2.5 : Math.abs(h2 - h1) / 4;
            for (int sd = -1; sd <= 1; sd += 2) {
                double x = p[0] - p[3] * hw * sd, z = p[1] + p[2] * hw * sd;
                v.add(new float[]{(float) x, (float) (lake + h), (float) z, (float) s, sd, (float) sp, (float) slope, (float) p[2], (float) p[3]});
            }
        }
        float[] vb = new float[v.size() * 9];
        for (int i = 0; i < v.size(); i++) System.arraycopy(v.get(i), 0, vb, i * 9, 9);
        int[] ib = new int[(n - 1) * 6];
        for (int k = 1; k < n; k++) {
            int i0 = (k - 1) * 2, i1 = k * 2, o = (k - 1) * 6;
            ib[o] = i0; ib[o + 1] = i0 + 1; ib[o + 2] = i1;
            ib[o + 3] = i1; ib[o + 4] = i0 + 1; ib[o + 5] = i1 + 1;
        }
        return new Object[]{vb, ib};
    }

    /** Die Mündung am Kanal {x, z, Richtung x, Richtung z, Sohle}. */
    public double[] mouth() { return dam.at(dam.length); }

    /** Quelle der Welle und Gischt: je Sekunde das Wasser, das in den Kanal schießt (Faden der Physik). */
    private void emit(double dt) {
        if (flow <= 1) return;
        sourceClock += dt;
        sprayClock += dt;
        double[] m = mouth();
        double v = speed(dam.length);
        if (sourceClock >= 1) {
            // 40 m vor der Mündung im tiefen Wasser, über eine Sekunde verteilt
            sources.add(new double[]{m[0] + m[2] * 40, m[1] + m[3] * 40, flow * sourceClock, 30, sourceClock});
            sourceClock = 0;
        }
        if (sprayClock >= 0.35) {
            sprayClock = 0;
            splashes.add(new double[]{m[0] + m[2] * 25, 0, m[1] + m[3] * 25, Math.min(900, flow * 0.08), Math.max(3, v)});
        }
    }

    // ------------------------------------------------------------------ Bau

    private void build() {
        building = true;
        long key = dam.seed * 31 + log.size();
        builder.submit(() -> {
            try {
                long t0 = System.nanoTime();
                Cells cl = Wiring.cells(dam);
                Solid s = world.prepare(Kalbung.shape(cl), world.cellsFor(cells), cl::density, cl::strength);
                // gehalten: was in Gletscher, Ufer oder Rand steckt
                s.support(c -> !dam.insideXZ(c[0], c[2]) || nearSide(c[0], c[2]));
                s.anchor(f -> {
                    if (f.ny > -0.5) return false;
                    double[] c = f.centroid();
                    return c[1] < dam.bottom(c[0], c[2]) + 3;
                }, 3);
                note = String.format(Locale.GERMAN, "Damm in %d Zellen, %.1f s", s.cells(), (System.nanoTime() - t0) / 1e9);
                ready.add(s);
            } catch (RuntimeException e) {
                System.err.println("Ruptura: Damm " + e);
                note = "Damm nicht gebaut: " + e.getMessage();
                building = false;
            }
        });
    }

    private boolean nearSide(double x, double z) {
        double[] l = dam.local(x, z);
        return l[1] < Q_MIN + 6 || l[1] > Q_MAX - 6;
    }

    private void doReset() {
        Solid old = solid;
        if (old != null) world.remove(old);
        // Stücke des alten Damms bleiben liegen und treiben fort; der neue Damm wird gebaut
        solid = null;
        regiebuch = false;
        phase = Phase.AUS;
        stau = flow = progress = radius = sceneTime = 0;
        bridgeFell = Double.NaN;
        bridgeVolume = largest = 0;
        fallen = tired = bridgeTired = forced = 0;
        inBridge = fatigue = 0;
        log.clear();
    }

    private void event(String text) {
        log.add(new Ereignis(sceneTime, stau, flow, text));
        note = text;
    }

    // ------------------------------------------------------------------ Maske

    /**
     * Der Brazo Rico als Maske: alle Zellen des Geländes unter dem höchsten Stau (30 m), die vom Inneren des Arms aus
     * erreichbar sind, ohne über Gletscher oder Damm zu gehen.
     */
    public static byte[] brazoMask(Terrain t, IceDam dam, IceDam.Cover cover) {
        int n = Terrain.N;
        byte[] m = new byte[n * n];
        boolean[] seen = new boolean[n * n];
        int si = (int) ((BRAZO_SEED[0] - Terrain.X0) / Terrain.CELL), sj = (int) ((BRAZO_SEED[1] - Terrain.Z0) / Terrain.CELL);
        int[] queue = new int[n * n];
        int head = 0, tail = 0;
        queue[tail++] = sj * n + si;
        seen[sj * n + si] = true;
        double lim = Site.BRAZO_RICO_MAX_STAU + 0.5;
        while (head < tail) {
            int k = queue[head++];
            int i = k % n, j = k / n;
            m[k] = 1;
            int[] nb = {k - 1, k + 1, k - n, k + n};
            for (int q = 0; q < 4; q++) {
                int kk = nb[q];
                if (q == 0 && i == 0 || q == 1 && i == n - 1 || q == 2 && j == 0 || q == 3 && j == n - 1) continue;
                if (seen[kk]) continue;
                seen[kk] = true;
                if (Terrain.decode(t.h[kk]) >= lim || t.src[kk] == Terrain.EIS) continue;
                double x = Terrain.X0 + (kk % n + 0.5) * Terrain.CELL, z = Terrain.Z0 + (kk / n + 0.5) * Terrain.CELL;
                if (cover.ice(x, z) || dam.inBox(x, z) && (dam.insideXZ(x, z) || t.height(x, z) < lim)) continue;
                queue[tail++] = kk;
            }
        }
        return m;
    }

    /** Fläche der Maske (km²) und ob sie den Kanal berührt (dann wäre der Damm undicht). */
    public static double[] maskStats(byte[] m) {
        long c = 0;
        for (byte b : m) if (b != 0) c++;
        int ci = (int) ((-300 - Terrain.X0) / Terrain.CELL), cj = (int) ((-3000 - Terrain.Z0) / Terrain.CELL);
        return new double[]{c * Terrain.CELL * Terrain.CELL / 1e6, m[cj * Terrain.N + ci]};
    }

    // ------------------------------------------------------------------ Text

    static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    /** „3 d 4 h“ */
    public static String dauer(double s) {
        if (s < 120) return String.format(Locale.GERMAN, "%.0f s", s);
        if (s < 7200) return String.format(Locale.GERMAN, "%.0f min", s / 60);
        if (s < 2 * 86400) return String.format(Locale.GERMAN, "%.1f h", s / 3600);
        return String.format(Locale.GERMAN, "%d d %d h", (int) (s / 86400), (int) (s % 86400 / 3600));
    }

    /** „Ruptura: Rinne · Stau 21,3 m · 7 800 m³/s · Tunnel 34 m weit · 12 Stücke · seit 4 d 3 h (Zeitraffer 150)“ */
    public String describe() {
        Phase p = phase;
        if (p == Phase.AUS) return "Ruptura: kein Damm (Jahr ohne Eisdamm)" + (building ? " · Damm im Bau" : "");
        Solid s = solid;
        String body = s == null ? (building ? "Damm im Bau" : "kein Körper") : String.format(Locale.GERMAN, "Damm %d Zellen, %d dran", s.cells(), s.attachedCount());
        return String.format(Locale.GERMAN, "Ruptura: %s · Stau %.1f m · %,.0f m³/s · Tunnel %s · %d Stücke%s · %s · seit %s%s",
                p.label, stau, flow, progress <= 0 ? "zu" : progress < dam.length ? String.format(Locale.GERMAN, "%.0f von %.0f m", progress, dam.length)
                        : String.format(Locale.GERMAN, "%.0f m weit", 2 * radius), fallen,
                Double.isNaN(bridgeFell) ? "" : String.format(Locale.GERMAN, " · Brücke gefallen (%,.0f m³)", bridgeVolume), body, dauer(sceneTime),
                p.running() ? String.format(Locale.GERMAN, " (Zeitraffer %,.0f%s)", shownPace, regiebuch ? ", Regiebuch" : "") : "");
    }

    public void shutdown() { builder.shutdownNow(); }
}
