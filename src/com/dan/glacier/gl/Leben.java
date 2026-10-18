package com.dan.glacier.gl;

import com.dan.buoyancy.Berg;
import com.dan.buoyancy.Catamaran;
import com.dan.buoyancy.Mesh;
import com.dan.buoyancy.gl.BuoyancyGl;
import com.dan.flock.Flock;
import com.dan.flock.Species;
import com.dan.flock.gl.FlockGl;
import com.dan.glacier.scene.Bootsfahrt;
import com.dan.glacier.scene.Gletscher;
import com.dan.glacier.scene.Stege;
import com.dan.glacier.scene.Terrain;
import com.dan.glacier.wiring.Collider;
import com.dan.weathering.Weathering;
import com.jogamp.opengl.GL4;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Phase 10: Leben am Gletscher, wie die App es verdrahtet.
 * <ul>
 * <li><b>Vögel</b> ({@code flock}): Dominikanermöwen um Front und Steg, sie sitzen auf den Geländern, den Pfählen des
 * Anlegers und auf Eisbergen (die Plätze wandern mit den Bergen); Andenkondore kreisen in der Thermik über den Hängen
 * der Halbinsel. Wind kommt aus {@code weather} (Wiring „weather → flock“).</li>
 * <li><b>Stege</b> ({@link Stege}): Wege, Balkone und Anleger als Netz, gezeichnet mit {@code buoyancy.gl}. Nass macht sie
 * {@code weathering}: Spiegel des Sees, Regen, die Welle am Anleger ({@code wave.onRunup}) und Gischt an den Pfählen.</li>
 * <li><b>Katamaran</b> ({@link Catamaran}): liegt am Anleger, fährt auf Wunsch die {@link Bootsfahrt} und schwimmt dabei
 * auf Seespiegel + langer Welle + Windsee, geschoben von der Strömung der Welle; gerechnet im Faden des Bilds, damit
 * Boot und Auge an Bord aus demselben Stand kommen. Die Kamera fährt mit ({@link com.dan.glacier.camera.Camera#startMitfahrt}).</li>
 * </ul>
 */
final class Leben {
    private final Renderer r;
    final Flock flock = Flock.beaufort(4);
    private final FlockGl flockGl = new FlockGl();
    private final BuoyancyGl bodyGl = new BuoyancyGl();
    private boolean flockOk, bodyOk;
    volatile boolean showBirds = true, showBoat = true, showStege = true;

    final Stege stege;
    /** Die Nässe der Stege: ein {@code Weathering} für alle Teile (Spiegel, Regen, nasse Linie). */
    final Weathering nass = Weathering.calm();
    private volatile Mesh stegeMesh;
    private Mesh stegeDrawn;
    private volatile boolean rebuilding;
    private double wetClock, builtLine = Double.NaN, builtRain = Double.NaN;
    private volatile double pierRunup, pierSpray;
    private volatile long pierRunups;

    volatile List<double[]> route;
    final Catamaran boat;
    private Mesh boatMesh;
    private volatile double[] boatModel, boatPose;
    private volatile boolean startWanted, rideRunning;
    private volatile String boatLine = "";
    private double boatSample, wakeClock;
    private com.dan.buoyancy.Sea boatSea;

    /** Sitzplätze auf Eisbergen: Berg und Ecke je Platz (aus dem Faden des Bilds gesetzt, vom Schwarm gelesen). */
    private static final int BERG_PERCHES = 10;
    private final Berg[] perchBerg = new Berg[BERG_PERCHES];
    private final int[] perchVertex = new int[BERG_PERCHES];
    private final double[][] perchSpot = new double[BERG_PERCHES][];
    private double perchClock;

    Leben(Renderer r) {
        this.r = r;
        Terrain t = r.terrain;
        double level = 0;
        stege = new Stege(t, level);
        stegeMesh = stege.mesh(null);
        // Vögel
        flock.seed = 10;
        flock.kelpGulls = true;
        flock.quality = Flock.Quality.HIGH;
        flock.homeX = (t.steg[0] + stege.pierEnd[0]) / 2;
        flock.homeZ = (t.steg[1] + stege.pierEnd[1]) / 2;
        flock.homeRadius = 450;
        flock.coastDeg = 60;
        flock.ground = (x, z) -> Math.max(t.height(x, z), r.camera.waterLevel);
        int[] mix = new int[Species.values().length];
        mix[Species.KONDOR.ordinal()] = 4;
        flock.mix = mix;
        // Phase 12: Rufe an den Klang (Kondore rauschen mit den Schwingen)
        flock.onCall = (x, y, z, st, g) -> r.klang.sound(g.species == Species.KONDOR ? com.dan.glacier.wiring.SoundEvent.Kind.KONDOR
                : com.dan.glacier.wiring.SoundEvent.Kind.MOEWE, x, y, z, st);
        // Kondore über den Hängen östlich des Stegs (Thermik an sonnigen Hängen über 150 m)
        flock.condorX = t.steg[0] + 900;
        flock.condorZ = t.steg[1] - 200;
        flock.condorRadius = 1800;
        flock.condorFloor = 120;
        flock.condorCeiling = 700;
        java.util.SplittableRandom rnd = new java.util.SplittableRandom(5);
        for (int k = 0; k < 80 && flock.thermals.size() < 5; k++) {
            double a = rnd.nextDouble() * 2 * Math.PI, d = 500 + 1300 * rnd.nextDouble();
            double x = flock.condorX + Math.sin(a) * d, z = flock.condorZ - Math.cos(a) * d;
            if (t.height(x, z) > 150 && !t.ice(x, z)) flock.thermals.add(new Flock.Area(x, z, 90));
        }
        // Sitzplätze: das vordere Geländer der Balkone, die Pfähle des Anlegers, Eisberge
        for (Stege.Balkon b : stege.balkone) {
            double hr = Math.toRadians(b.heading()), fx = Math.sin(hr), fz = -Math.cos(hr), rx = Math.cos(hr), rz = Math.sin(hr);
            for (double u = -b.width() / 2 + 1; u <= b.width() / 2 - 1; u += 2.5) {
                double x = b.x() + rx * u + fx * b.depth() / 2, z = b.z() + rz * u + fz * b.depth() / 2;
                flock.perches.add(new Flock.Perch(b.name() + " Geländer", x, b.y() + Stege.RAIL + Flock.legHeight(Species.MOEWE), z).only(Species.MOEWE));
            }
        }
        int pile = 0;
        for (Stege.Pfosten p : stege.pfosten())
            if (p.y0() < level && pile++ % 3 == 0)
                flock.perches.add(new Flock.Perch("Pfahl am Anleger", p.x(), p.y1() + Flock.legHeight(Species.MOEWE), p.z()).only(Species.MOEWE).appeal(2.5));
        for (int i = 0; i < BERG_PERCHES; i++) {
            final int k = i;
            flock.perches.add(new Flock.Perch("Eisberg", new Flock.Spot() {
                @Override public double[] position() { double[] s = perchSpot[k]; return s == null ? new double[]{0, -100, 0} : s; }
                @Override public boolean usable() { return perchSpot[k] != null; }
            }).only(Species.MOEWE).appeal(4));
        }
        // der Katamaran am Anleger (die Route kommt, wenn die Front feststeht)
        boat = new Catamaran("Katamaran");
        boat.via(stege.berth[0], stege.berth[1], 0).via(stege.berth[0] + Math.sin(Math.toRadians(stege.berth[2])) * 30, stege.berth[1] - Math.cos(Math.toRadians(stege.berth[2])) * 30, 0);
    }

    /** Im GL-Faden: Grafik der Vögel und der Körper; Gischt und Stöße an den Pfählen. */
    void init(GL4 gl) {
        try {
            flockGl.init(gl);
            flockOk = true;
        } catch (RuntimeException e) {
            System.err.println("Glacier: Vögel nicht verfügbar: " + e.getMessage());
        }
        try {
            bodyGl.init(gl);
            bodyOk = true;
            boatMesh = Mesh.catamaran(boat);
        } catch (RuntimeException e) {
            System.err.println("Glacier: Katamaran und Stege nicht zu zeichnen: " + e.getMessage());
        }
        // Pfähle im und am Wasser: Gischt schlägt an (spray), fester Körper für die Verdrahtung (Collider)
        for (Stege.Pfosten p : stege.pfosten()) {
            if (p.y0() > 2.5) continue;
            try { r.spray.addBody(com.dan.spray.Body.cylinder(p.x(), p.z(), Math.max(0.05, p.r()), p.y0(), p.y1())); } catch (IllegalArgumentException ignored) { }
            if (r.wiring != null) r.wiring.colliders.add(Collider.cylinder(p.x(), p.z(), p.r(), p.y0(), p.y1()));
        }
        if (r.wiring != null)
            for (Stege.Balkon b : stege.balkone) r.wiring.colliders.add(Collider.box(b.x(), b.y() - 0.1, b.z(), b.width() / 2, 0.1, b.width() / 2));
        // ein Spritzer an einem Pfahl macht ihn bis dort hinauf nass (spray → weathering)
        com.dan.spray.Spray.Hit before = r.spray.onHit;
        r.spray.onHit = (x, y, z, m, s, k) -> {
            before.hit(x, y, z, m, s, k);
            if (Math.hypot(x - stege.pierEnd[0], z - stege.pierEnd[1]) < 60) { pierSpray = Math.max(pierSpray, y); nass.setWetLine(Math.max(nass.wetLine(), y)); }
        };
    }

    /** Die Front steht fest (Phase 8, {@code startWave}): Route, Revier der Möwen, Sitzplätze. */
    void place(Gletscher g, double[] front, double level) {
        if (front == null) return;
        List<double[]> rt = Bootsfahrt.route(r.terrain, g.glacier, level, front, stege.berth, stege.pierEnd);
        route = rt;
        flock.homeX = (stege.pierEnd[0] + front[0] + front[2] * 150) / 2;
        flock.homeZ = (stege.pierEnd[1] + front[1] + front[3] * 150) / 2;
        flock.homeRadius = Math.max(350, Math.hypot(stege.pierEnd[0] - front[0], stege.pierEnd[1] - front[1]) * 0.8);
        flock.waters.add(new Flock.Area(front[0] + front[2] * 250, front[1] + front[3] * 250, 220));
        Terrain t = r.terrain;
        flock.water = (x, z) -> t.height(x, z) < r.camera.waterLevel - 0.5 && !t.ice(x, z) && !g.glacier.covers(x, z)
                ? r.camera.waterLevel + (r.waveReady ? r.wave.heightAt(x, z) : 0) : Double.NaN;
        System.out.println("Glacier: " + Bootsfahrt.describe(rt) + " · " + stege.describe());
    }

    /** Strömung der langen Welle am Boot (m/s), aus der Neigung der Welle aufintegriert. */
    private final double[] current = new double[2];

    /**
     * Phase 10 (nach Befund „Boot flackert“): der Katamaran rechnet im Faden des Bilds mit dem Bild-dt, so dass Boot und
     * Auge an Bord in jedem Bild aus demselben Stand kommen. Im Faden „Welle“ lief er stoßweise (die Welle rechnet alle
     * 0,064 s einen großen Schritt), und Auge und Boot kamen aus verschiedenen Schritten. Die Strömung der Welle kommt ohne
     * Sperre aus ihrem Stand: du/dt = −g ∇η an der Stelle des Boots (die Bewegungsgleichung der langen Welle ohne Reibung),
     * mit einer Rückkehr zu null in einer Minute gegen Abdrift.
     */
    private void initBoat() {
        boatSea = new com.dan.buoyancy.Sea() {
            @Override public double height(double x, double z) {
                double h = r.camera.waterLevel + (r.waveReady ? r.wave.heightAt(x, z) : 0);
                // die Windsee dazu (der Katamaran schaukelt auch ohne Welle)
                if (r.showWater) h += r.ocean.heightAt(x, z) - r.ocean.waterLevel;
                return h;
            }
            @Override public double[] velocity(double x, double z) { return new double[]{current[0], 0, current[1]}; }
            @Override public double level() { return r.camera.waterLevel; }
            @Override public double bed(double x, double z) { return r.terrain.height(x, z); }
        };
        boat.moor(boatSea);
        publishBoat();
    }

    /** Ein Schritt des Boots im Faden des Bilds (vor der Kamera). */
    private void stepBoat(double dt) {
        if (boatSea == null) initBoat();
        if (dt <= 0) { publishBoat(); return; }
        if (startWanted) {
            startWanted = false;
            List<double[]> rt = route;
            if (rt != null) {
                boat.route.clear();
                for (double[] p : rt) boat.via(p[0], p[1], p[2]);
                boat.start(boatSea);
                rideRunning = true;
            }
        }
        double h = Math.min(dt, 0.1);
        if (r.waveReady) {
            double[] sl = r.wave.slopeAt(boat.x, boat.z);
            double k = Math.exp(-h / 60);
            current[0] = (current[0] - 9.81 * sl[0] * h) * k;
            current[1] = (current[1] - 9.81 * sl[1] * h) * k;
            double cl = Math.hypot(current[0], current[1]);
            if (cl > 4) { current[0] *= 4 / cl; current[1] *= 4 / cl; }
        }
        boat.step(boatSea, h);
        // Phase 12: Kielwasser – die Rümpfe stoßen Ringwellen in den See (ocean), je schneller, desto stärker
        wakeClock += h;
        if (boat.speed() > 1 && wakeClock > 0.4) {
            wakeClock = 0;
            double[] f = boat.forward(), rt = boat.right();
            double st = Math.min(0.25, 0.04 * boat.speed());
            for (int sd = -1; sd <= 1; sd += 2) r.ocean.addImpulse(boat.x + f[0] * 8 + rt[0] * sd * boat.hullSpacing / 2, boat.z + f[1] * 8 + rt[1] * sd * boat.hullSpacing / 2, st);
        }
        if (rideRunning && boat.done()) rideRunning = false;
        publishBoat();
    }

    private void publishBoat() {
        boatModel = boat.model();
        boatPose = boat.pose(true);
        boatLine = boat.describe();
    }

    /** Bootsfahrt beginnen (die Kamera fährt mit, Blick nach Backbord zur Front). */
    boolean startRide() {
        if (route == null) return false;
        startWanted = true;
        rideRunning = true;
        r.camera.startMitfahrt(this::pose, -70);
        return true;
    }

    void stopRide() { r.camera.stopMitfahrt(); }

    boolean riding() { return rideRunning; }

    /** Pose des Auges an Bord für die Kamera {x, y, z, Kurs, Stampfen, Rollen}. */
    double[] pose() {
        double[] p = boatPose;
        if (p != null) return p;
        double[] e = boat.eye(true);
        return new double[]{e[0], e[1], e[2], boat.heading, 0, 0};
    }

    /** Eine Welle läuft auf (aus dem Faden „Welle“): am Anleger wird es bis zur Auflaufhöhe nass. */
    void runup(double x, double z, double h) {
        if (Math.hypot(x - stege.pierStart[0], z - stege.pierStart[1]) > 150 && Math.hypot(x - stege.pierEnd[0], z - stege.pierEnd[1]) > 150) return;
        pierRunups++;
        pierRunup = Math.max(pierRunup, h);
        double y = r.camera.waterLevel + h;
        if (y > nass.wetLine()) nass.setWetLine(y);
    }

    /** Je Bild im Faden des Bilds: Wind, Licht, Vögel, Sitzplätze auf den Bergen, Nässe der Stege. */
    void step(double dt, double sceneDt, double sunAltDeg) {
        double level = r.camera.waterLevel;
        stepBoat(dt);
        if (r.wiring != null) {
            flock.windSpeed = r.wiring.wind.speed();
            flock.windFromDeg = r.wiring.wind.fromDeg();
            nass.windSpeed = flock.windSpeed;
            nass.windFromDeg = flock.windFromDeg;
        }
        // Helligkeit grob aus dem Sonnenstand (Tag 10⁵ lx, Dämmerung bei −6°)
        double s = Math.sin(Math.toRadians(sunAltDeg));
        flock.ambientLux = sunAltDeg > 0 ? 400 + 110_000 * s : sunAltDeg > -6 ? 400 * (1 + sunAltDeg / 6) : 1;
        flock.dayOfYear = r.dayOfYear();
        flock.disturbers.clear();
        double[] bm = boatModel;
        if (bm != null) flock.disturbers.add(new double[]{bm[12], bm[13] + 2, bm[14]});
        if (showBirds) flock.step(dt);
        // Möwen auf Eisbergen: die größten in der Nähe, die Ecke ganz oben (bleibt, bis der Berg sich dreht)
        perchClock += dt;
        if (perchClock >= 0.5) {
            perchClock = 0;
            List<Berg> cand = new ArrayList<>();
            for (Berg b : r.bergs.bergs) {
                double[] c = b.cgWorld();
                if (b.volume() > 250 && Math.hypot(c[0] - flock.homeX, c[2] - flock.homeZ) < flock.homeRadius * 1.6) cand.add(b);
            }
            cand.sort((a, b) -> Double.compare(b.volume(), a.volume()));
            for (int i = 0; i < BERG_PERCHES; i++) {
                Berg b = perchBerg[i];
                if (b != null && !r.bergs.bergs.contains(b)) b = null;
                if (b == null) for (Berg c : cand) { boolean used = false; for (Berg u : perchBerg) used |= u == c; if (!used) { b = c; break; } }
                perchBerg[i] = b;
                if (b == null) { perchSpot[i] = null; continue; }
                double[] v = b.vertexWorld(perchVertex[i] < b.meshPositions().length / 3 ? perchVertex[i] : 0);
                int top = b.topVertex();
                double[] tv = b.vertexWorld(top);
                if (tv[1] - v[1] > 1.0 || perchSpot[i] == null) { perchVertex[i] = top; v = tv; }
                perchSpot[i] = new double[]{v[0], v[1] + Flock.legHeight(Species.MOEWE), v[2]};
            }
        }
        // Nässe der Stege
        nass.waterLevel = level;
        nass.waveHeight = 0.25;
        nass.rainRate = r.rain.rate;
        nass.sun = Math.max(0, Math.min(1, s * 1.5));
        nass.step(sceneDt);
        wetClock += dt;
        if (wetClock >= 1 && !rebuilding) {
            wetClock = 0;
            Weathering.Snapshot sn = nass.snapshot();
            if (Double.isNaN(builtLine) || Math.abs(sn.wetLine() - builtLine) > 0.04 || Math.abs(sn.rainWet() - builtRain) > 0.04) {
                builtLine = sn.wetLine();
                builtRain = sn.rainWet();
                rebuilding = true;
                Thread th = new Thread(() -> {
                    try { stegeMesh = stege.mesh(sn); } finally { rebuilding = false; }
                }, "Stege-Netz");
                th.setDaemon(true);
                th.start();
            }
        }
    }

    /** Zeichnen nach dem See (der den Rumpf unter Wasser verdeckt). */
    void draw(GL4 gl, BuoyancyGl.View bv, BuoyancyGl.Light bl, FlockGl.View fv, FlockGl.Light fl) {
        if (bodyOk) {
            Mesh sm = stegeMesh;
            if (sm != stegeDrawn) {
                if (stegeDrawn != null) bodyGl.forget(gl, stegeDrawn);
                stegeDrawn = sm;
            }
            List<Object[]> items = new ArrayList<>();
            if (showStege && sm != null) items.add(new Object[]{sm, null});
            if (!items.isEmpty()) bodyGl.drawMeshes(gl, bv, bl, items, false);
            double[] m = boatModel;
            if (showBoat && m != null && boatMesh != null) {
                items.clear();
                items.add(new Object[]{boatMesh, m});
                bodyGl.drawMeshes(gl, bv, bl, items, true);
            }
        }
        if (flockOk && showBirds) flockGl.draw(gl, flock, fv, fl);
    }

    void dispose(GL4 gl) {
        if (bodyOk) bodyGl.dispose(gl);
        if (flockOk) flockGl.dispose(gl);
        bodyOk = flockOk = false;
    }

    /** Wie viele Möwen gerade auf einem Eisberg sitzen. */
    int gullsOnBergs() {
        int n = 0;
        for (Flock.Gull g : flock.gulls()) if (g.state == Flock.State.PERCH && g.perch() != null && "Eisberg".equals(g.perch().name)) n++;
        return n;
    }

    /** „Leben: 16 Möwen (3 auf Eisbergen) · 4 Kondore · Katamaran … · Stege nass bis +1,2 m“ */
    String describe() {
        Weathering.Snapshot sn = nass.snapshot();
        return String.format(Locale.GERMAN, "Leben: %s · %d auf Eisbergen · %s · Stege nass bis %+.2f m (Regen %.0f %%), Auflaufen am Anleger %d× bis %.2f m",
                flock.describe(), gullsOnBergs(), boatLine, sn.wetLine() - r.camera.waterLevel, 100 * sn.rainWet(), pierRunups, pierRunup);
    }
}
