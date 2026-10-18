package com.dan.glacier.gl;

import com.dan.fracture.Fracture;
import com.dan.fracture.Piece;
import com.dan.fracture.Poly;
import com.dan.fracture.Solid;
import com.dan.glacier.scene.Kalbung;
import com.dan.ice.FrontBlock;
import com.dan.ice.Ice;
import com.dan.ice.IceMesh;
import com.dan.ice.gl.IceGl;
import com.jogamp.opengl.GL4;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Zeichnet die Kalbung (Phase 7) mit dem Eis aus {@code com.dan.ice}: je Abschnitt der Front ein Netz aus den Zellen,
 * die noch halten, je Stück ein Netz in seinem eigenen Achsenkreuz, das mit seiner Lage gezeichnet wird. Wo ein
 * Abschnitt steht, schneidet die Wand des Gletschers einen Quader aus ({@link IceGl#setCuts}); die Zellen füllen ihn.
 * <p>
 * Ein Riss ist zu sehen, bevor er bricht: Ecken in der Nähe gebrochener Flächen bekommen Verdeckung (dunkel und blau
 * wie der Grund einer Spalte), so zeichnet sich der Weg des Risses auf der Wand ab. Die Netze baut ein eigener Faden;
 * hochgeladen wird im GL-Faden.
 */
public final class KalbungGl {
    private final IceGl ice;
    private final Kalbung kalbung;
    /** Eis der Front (wie die Wand) und frisch gebrochenes Eis (blauer, kaum verwittert). */
    public volatile Ice wallIce = Ice.front(), freshIce = Ice.iceberg();
    public volatile double wallThickness = 25;
    private final Map<Solid, Slot> solids = new HashMap<>();
    private final Map<Piece, IceGl.Body> pieces = new HashMap<>();
    private final ConcurrentLinkedQueue<Object[]> built = new ConcurrentLinkedQueue<>();
    private final Set<Object> pending = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final ExecutorService worker;
    private final AtomicInteger queued = new AtomicInteger();
    private int lastSolids, lastPieces, lastTris, lastBergs;
    /** Phase 9: die Eisberge (com.dan.buoyancy) und ihr Eis (verwittert an der Luft, blau, wo es gerade gekentert ist). */
    public volatile com.dan.buoyancy.Bergs bergs;
    public volatile Ice bergIce = Ice.iceberg();
    public volatile boolean showBergs = true;
    private final Map<com.dan.buoyancy.Berg, IceGl.Body> bergBodies = new HashMap<>();
    /** Phase 13: der Eisdamm der Ruptura (sein Körper in derselben Welt; die Stücke zeichnet die Schleife der Stücke). */
    public volatile com.dan.glacier.scene.Ruptura ruptura;
    /** Seiten des Damms, die in Gletscher, Ufer und Grund stecken (quer links, quer rechts, unten). */
    private static final int[] DAM_HIDDEN = {2, 3, 4}, BOX_HIDDEN = {0, 1, 3, 4};

    private static final class Slot { int version = -1, wanted = -1; IceGl.Body body; }

    public KalbungGl(IceGl ice, Kalbung kalbung) {
        this.ice = ice;
        this.kalbung = kalbung;
        worker = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Kalbung-Netze");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        });
    }

    /** Je Bild im GL-Faden: Fertiges hochladen, Aufträge vergeben, Ausschnitte setzen; die Körper, die zu zeichnen sind. */
    public List<IceGl.Instance> instances(GL4 gl) {
        Object[] b;
        while ((b = built.poll()) != null) {
            if (b[0] instanceof Solid s) {
                Slot slot = solids.get(s);
                if (slot == null) continue;
                IceGl.Body old = slot.body;
                IceMesh m = (IceMesh) b[1];
                slot.body = m.idx.length > 0 ? up(gl, m) : null;
                slot.version = (Integer) b[2];
                if (old != null) ice.release(gl, old);
            } else if (b[0] instanceof Piece p) {
                IceMesh m = (IceMesh) b[1];
                if (m.idx.length > 0) pieces.put(p, up(gl, m));
            }
            pending.remove(b[0]);
        }
        List<IceGl.Instance> out = new ArrayList<>();
        List<Float> cuts = new ArrayList<>();
        Set<Solid> live = new HashSet<>();
        int tris = 0;
        for (Kalbung.Abschnitt a : kalbung.sections()) {
            Solid s = a.solid;
            live.add(s);
            for (float c : a.block.cut()) cuts.add(c);
            Slot slot = solids.computeIfAbsent(s, k -> new Slot());
            int v = s.version();
            if (slot.wanted != v && !pending.contains(s)) {
                slot.wanted = v;
                pending.add(s);
                queued.incrementAndGet();
                FrontBlock blk = a.block;
                worker.submit(() -> {
                    try {
                        built.add(new Object[]{s, solidMesh(s, blk.planes(), BOX_HIDDEN, blk.waterLevel), v});
                    } catch (RuntimeException e) {
                        System.err.println("Kalbung: Netz " + e);
                        pending.remove(s);
                    } finally {
                        queued.decrementAndGet();
                    }
                });
            }
            if (slot.body != null) {
                IceGl.Instance in = new IceGl.Instance(slot.body, wallIce).open(wallThickness);
                out.add(in);
                tris += slot.body.triangles();
            }
        }
        // Phase 13: der Damm der Ruptura
        com.dan.glacier.scene.Ruptura ru = ruptura;
        Solid ds = ru != null ? ru.solid() : null;
        if (ds != null) {
            live.add(ds);
            Slot slot = solids.computeIfAbsent(ds, k -> new Slot());
            int v = ds.version();
            if (slot.wanted != v && !pending.contains(ds)) {
                slot.wanted = v;
                pending.add(ds);
                queued.incrementAndGet();
                double[] planes = ru.dam.planes();
                worker.submit(() -> {
                    try {
                        built.add(new Object[]{ds, solidMesh(ds, planes, DAM_HIDDEN, 0), v});
                    } catch (RuntimeException e) {
                        System.err.println("Ruptura: Netz " + e);
                        pending.remove(ds);
                    } finally {
                        queued.decrementAndGet();
                    }
                });
            }
            if (slot.body != null) {
                out.add(new IceGl.Instance(slot.body, wallIce).open(wallThickness));
                tris += slot.body.triangles();
            }
        }
        // Abschnitte, die wieder Wand sind
        solids.entrySet().removeIf(e -> {
            if (live.contains(e.getKey())) return false;
            if (e.getValue().body != null) ice.release(gl, e.getValue().body);
            return true;
        });
        // Stücke
        Set<Piece> alive = new HashSet<>();
        int np = 0;
        for (Fracture.Pose pose : kalbung.world.poses()) {
            Piece p = pose.piece();
            alive.add(p);
            IceGl.Body body = pieces.get(p);
            if (body == null) {
                if (!pending.contains(p)) {
                    pending.add(p);
                    queued.incrementAndGet();
                    worker.submit(() -> {
                        try {
                            built.add(new Object[]{p, pieceMesh(p)});
                        } catch (RuntimeException e) {
                            pending.remove(p);
                        } finally {
                            queued.decrementAndGet();
                        }
                    });
                }
                continue;
            }
            IceGl.Instance in = new IceGl.Instance(body, freshIce);
            double[] R = pose.rotation(), t = pose.position();
            in.model = new float[]{(float) R[0], (float) R[3], (float) R[6], 0, (float) R[1], (float) R[4], (float) R[7], 0,
                    (float) R[2], (float) R[5], (float) R[8], 0, (float) t[0], (float) t[1], (float) t[2], 1};
            out.add(in);
            tris += body.triangles();
            np++;
        }
        pieces.entrySet().removeIf(e -> {
            if (alive.contains(e.getKey())) return false;
            ice.release(gl, e.getValue());
            return true;
        });
        // Eisberge: je Bild höchstens vier neue Netze hochladen
        int nb = 0, uploads = 0;
        Set<com.dan.buoyancy.Berg> livingBergs = new HashSet<>();
        com.dan.buoyancy.Bergs bs = bergs;
        if (bs != null && showBergs)
            for (com.dan.buoyancy.Berg bg : bs.bergs) {
                livingBergs.add(bg);
                IceGl.Body body = bergBodies.get(bg);
                if (body == null) {
                    if (uploads >= 4 || !(bg.tag instanceof IceMesh m) || m.idx.length == 0) continue;
                    body = up(gl, m);
                    bergBodies.put(bg, body);
                    uploads++;
                }
                IceGl.Instance in = new IceGl.Instance(body, bergIce);
                in.model = bg.pose();
                out.add(in);
                tris += body.triangles();
                nb++;
            }
        bergBodies.entrySet().removeIf(e -> {
            if (livingBergs.contains(e.getKey())) return false;
            ice.release(gl, e.getValue());
            return true;
        });
        lastBergs = nb;
        float[] c = new float[Math.min(cuts.size(), IceGl.MAX_CUTS * 8)];
        for (int i = 0; i < c.length; i++) c[i] = cuts.get(i);
        ice.setCuts(c);
        lastSolids = live.size();
        lastPieces = np;
        lastTris = tris;
        return out;
    }

    private IceGl.Body up(GL4 gl, IceMesh m) {
        IceGl.Body b = ice.upload(gl, m);
        b.mesh = null;
        return b;
    }

    /**
     * Netz eines Abschnitts: die Flächen der haltenden Zellen nach außen (Rand, gelöste oder geschmolzene Nachbarn),
     * ohne die Seiten des Quaders, die im Gletscher stecken. Verdeckung nahe gebrochener Flächen (der Riss).
     */
    static IceMesh solidMesh(Solid s, double[] planes, int[] hidden, double waterLevel) {
        // Mitten der gebrochenen Flächen zwischen Zellen, die beide noch halten (ein Riss, der noch nicht durch ist)
        List<double[]> crack = new ArrayList<>();
        for (int k = 0; k < s.bonds(); k++) {
            if (!s.broken(k)) continue;
            int i = s.bondA(k), j = s.bondB(k);
            if (s.attached(i) && s.attached(j)) crack.add(s.bondCenter(k));
        }
        List<float[]> v = new ArrayList<>();
        for (int i = 0; i < s.cells(); i++) {
            if (!s.attached(i)) continue;
            for (Poly.Face f : s.cell(i).faces) {
                if (f.tag >= 0 && s.attached(f.tag)) continue;
                if (f.tag < 0 && onBoxSide(f, planes, hidden)) continue;
                emit(v, f, crack, waterLevel);
            }
        }
        return mesh(v);
    }

    /** Liegt die Fläche in einer Seite, der Rückseite oder dem Boden des Quaders (nicht außen sichtbar)? */
    static boolean onBoxSide(Poly.Face f, double[] pl, int[] hidden) {
        for (int q : hidden) {
            double nx = pl[q * 4], ny = pl[q * 4 + 1], nz = pl[q * 4 + 2], d = pl[q * 4 + 3];
            if (f.nx * nx + f.ny * ny + f.nz * nz < 0.999) continue;
            if (Math.abs(f.d - d) < 0.05) return true;
        }
        return false;
    }

    /** Netz eines Stücks im eigenen Achsenkreuz: die äußeren Flächen seiner Zellen. */
    public static IceMesh pieceMesh(Piece p) {
        List<float[]> v = new ArrayList<>();
        for (int i = 0; i < p.cellCount(); i++)
            for (Poly.Face f : p.cell(i).faces) if (p.outer(f)) emit(v, f, List.of(), Double.NaN);
        return mesh(v);
    }

    private static void emit(List<float[]> v, Poly.Face f, List<double[]> crack, double wl) {
        double[] p = f.p;
        int n = p.length / 3;
        float nx = (float) f.nx, ny = (float) f.ny, nz = (float) f.nz;
        // Umlauf der Ecken einer Zellfläche ist nicht festgelegt: gegen den Uhrzeigersinn von außen (die Grafikkarte
        // verwirft Rückseiten), also nach der Normalen ausrichten
        double sx = 0, sy = 0, sz = 0;
        for (int k = 1; k + 1 < n; k++) {
            double ax = p[k * 3] - p[0], ay = p[k * 3 + 1] - p[1], az = p[k * 3 + 2] - p[2];
            double bx = p[k * 3 + 3] - p[0], by = p[k * 3 + 4] - p[1], bz = p[k * 3 + 5] - p[2];
            sx += ay * bz - az * by; sy += az * bx - ax * bz; sz += ax * by - ay * bx;
        }
        boolean flip = sx * f.nx + sy * f.ny + sz * f.nz < 0;
        for (int k = 1; k + 1 < n; k++) {
            int[] tri = flip ? new int[]{0, k + 1, k} : new int[]{0, k, k + 1};
            for (int t : tri) {
                double x = p[t * 3], y = p[t * 3 + 1], z = p[t * 3 + 2];
                float ao = 1;
                if (!crack.isEmpty()) {
                    double best = 1e9;
                    for (double[] c : crack) {
                        double dx = c[0] - x, dy = c[1] - y, dz = c[2] - z;
                        best = Math.min(best, dx * dx + dy * dy + dz * dz);
                    }
                    double d = Math.sqrt(best);
                    // innerhalb von 2 m dunkel, bis 5 m auslaufend
                    ao = (float) (1 - 0.75 * (1 - smooth(2, 5, d)));
                }
                // unter Wasser dunkler wie die Wand (GlacierWall): sonst leuchtet der Teil unter Wasser durch den See
                if (!Double.isNaN(wl)) ao *= (float) Math.max(0.15, 1 - 0.85 * smooth(wl, wl - 12, y));
                v.add(new float[]{(float) x, (float) y, (float) z, nx, ny, nz, ao});
            }
        }
    }

    private static IceMesh mesh(List<float[]> v) {
        float[] pos = new float[v.size() * 3], nrm = new float[v.size() * 3], ao = new float[v.size()];
        int[] idx = new int[v.size()];
        for (int i = 0; i < v.size(); i++) {
            float[] e = v.get(i);
            pos[i * 3] = e[0]; pos[i * 3 + 1] = e[1]; pos[i * 3 + 2] = e[2];
            nrm[i * 3] = e[3]; nrm[i * 3 + 1] = e[4]; nrm[i * 3 + 2] = e[5];
            ao[i] = e[6];
            idx[i] = i;
        }
        return new IceMesh(pos, nrm, idx, ao);
    }

    static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    /** Ob noch Netze im Bau sind (für Standbilder). */
    public boolean busy() { return queued.get() > 0 || !built.isEmpty(); }

    /** „Kalbung: 2 Abschnitte, 14 Stücke, 48 000 Dreiecke, 0 im Bau“ */
    public String info() {
        return String.format(Locale.GERMAN, "Abschnitte %d, Stücke %d, Eisberge %d, %,d Dreiecke, %d Netze im Bau", lastSolids, lastPieces, lastBergs, lastTris, queued.get());
    }

    public void dispose(GL4 gl) {
        worker.shutdownNow();
        for (Slot s : solids.values()) if (s.body != null) ice.release(gl, s.body);
        for (IceGl.Body b : pieces.values()) ice.release(gl, b);
        for (IceGl.Body b : bergBodies.values()) ice.release(gl, b);
        bergBodies.clear();
        solids.clear();
        pieces.clear();
        ice.setCuts(null);
    }
}
