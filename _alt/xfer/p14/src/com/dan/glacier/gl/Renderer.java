package com.dan.glacier.gl;

import com.dan.cloud.Clouds;
import com.dan.cloud.Noise;
import com.dan.cloud.gl.CloudGl;
import com.dan.fog.Fog;
import com.dan.fog.gl.FogGl;
import com.dan.glacier.camera.Camera;
import com.dan.forest.ForestField;
import com.dan.forest.gl.ForestGl;
import com.dan.glacier.scene.Bodendecke;
import com.dan.glacier.scene.Physik;
import com.dan.glacier.scene.Welle;
import com.dan.glacier.scene.SceneClock;
import com.dan.glacier.scene.Site;
import com.dan.glacier.scene.Sun;
import com.dan.glacier.scene.Terrain;
import com.dan.glacier.scene.Vegetation;
import com.dan.glacier.wiring.Wiring;
import com.dan.ground.Biome;
import com.dan.ground.Meadow;
import com.dan.ground.gl.GroundGl;
import com.dan.ice.Ice;
import com.dan.ice.IceMesh;
import com.dan.ice.IceShapes;
import com.dan.ice.gl.GlacierGl;
import com.dan.ice.gl.IceGl;
import com.dan.glacier.scene.Gletscher;
import com.dan.ocean.Ocean;
import com.dan.ocean.gl.OceanGl;
import com.dan.reed.Reed;
import com.dan.reed.gl.ReedGl;
import com.dan.lightning.Bolt;
import com.dan.lightning.Lightning;
import com.dan.lightning.gl.LightningGl;
import com.dan.rain.Rain;
import com.dan.rain.gl.RainGl;
import com.dan.sky.Sky;
import com.dan.sky.gl.SkyGl;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Die Engine auf der Grafikkarte, übernommen aus Haithabu und entschlackt (F4 A). Je Bild:
 * <ol>
 * <li>Phase 3: der Himmel aus {@code com.dan.sky} (Streuung, Sonne über Norden, Mond, Sterne des Südhimmels mit den
 * Magellanschen Wolken) in einen HDR-Puffer (RGBA16F, umgekehrte 32F-Tiefe), vorbelichtet mit der Augenanpassung
 * (geglättet über eine Sekunde, wie Haithabu); die Wolken aus {@code com.dan.cloud} mit Lentikularis darüber, dann
 * Himmel und Wolken als kleines Rundumbild für Spiegelung und Himmelslicht. Startet {@code com.dan.sky} nicht, bleibt
 * der Verlauf aus Phase 1.</li>
 * <li>Phase 2: Sonnenschatten der Südbuchen um den Blickpunkt ({@link SunShadow}).</li>
 * <li>Phase 2: das Gelände aus Copernicus GLO-30 ({@link Terrain}, {@link TerrainLod}, {@code terrain.vert/frag}):
 * Fels, Moräne, Steppe, Waldboden und Kronendach, Schnee über der Schneegrenze, die Zunge als weiße Fläche, der
 * Seegrund; außerhalb des Rasters ein Rahmen aus Bergen.</li>
 * <li>Phase 2: Wald aus {@code com.dan.forest} (Lenga, Ñire, Guindo), Bodendecke aus {@code com.dan.ground} (nur zu
 * Fuß), der See aus {@code com.dan.ocean} (Preset „Lago“, das Gelände als Grund, Türkis vom Gletschermehl),
 * Steppengras und Ufergras aus {@code com.dan.reed}; alle im Licht von Sonne, Mond und Himmel, mit Wolkenschatten.</li>
 * <li>Phase 3: Regen ({@code com.dan.rain}), Seenebel ({@code com.dan.fog}), das Leuchten der Blitze
 * ({@code com.dan.lightning}) bis zur Szenentiefe.</li>
 * <li>Nachbearbeitung: Filmkurve, Gamma, Stäbchensehen in der Nacht; dann das HUD.</li>
 * </ol>
 * Geblieben aus Haithabu: Qualitätsstufen mit Auflösungsregelung, Zeitmessung jedes Durchgangs auf der Grafikkarte
 * (Zeitstempel, ein Bild versetzt gelesen), Compute-Test beim Start, HUD kurz/voll/aus, Foto (F12), Shader neu laden
 * (F5), Standbilder ohne Fenster ({@code tools.Tempo}). Neu: der Faden der Physik ({@link Physik}) mit festem Schritt
 * (F21 A); der Bildlauf gibt ihm nur die Zeit. Im Bildlauf werden keine Dateien gelesen.
 * <p>
 * Abgestellt gegenüber Haithabu (kommt mit der jeweiligen Phase wieder): Anpassung an helle Bildteile in der Nacht,
 * Bildfolgen, Regiebücher, Klang. Ohne Höhendaten zeichnet dasselbe Gelände die Bühne von Phase 1 ({@link Terrain#flat}).
 */
public final class Renderer implements GLEventListener {
    public static final int MIN_MAJOR = 4, MIN_MINOR = 3;
    public static final String VERSION = "Phase 5";
    /** Die Phase, die dieser Stand erfüllt (für Ansichten und HUD). */
    public static final int PHASE = 14;
    static final String[] PASSES = {"Himmel", "Wolken", "Schatten", "Gelände", "Wald", "Boden", "Eis", "See", "Halme", "Regen", "Nebel", "Blitz",
            "Nachbearbeitung", "HUD"};
    private static final int MARKS = PASSES.length + 1;

    public final SceneClock clock;
    public final Wiring wiring;
    public final Camera camera = new Camera();
    /** Der Faden der Physik (Phase 1 ohne Aufgaben). */
    public final Physik physik = new Physik();
    /**
     * Phase 6: die Bruch-Engine im Faden der Physik. Boden ist der Gletscher mit Spalten oder das Gelände, Wasser der
     * Seespiegel. Phase 6 läuft sie leer mit; die Front als Körper bekommt sie in Phase 7.
     */
    public final com.dan.fracture.Fracture bruch = com.dan.fracture.Fracture.glacier();
    /** Phase 7: die Kalbung (nach dem Bau des Gletschers) und ihre Zeichnung. */
    public volatile com.dan.glacier.scene.Kalbung kalbung;
    private KalbungGl kalbungGl;
    public volatile boolean showKalbung = true;
    /** Phase 7: Einschläge ins Wasser aus dem Faden der Physik {x, y, z, Volumen, Tempo}, im Bildlauf verteilt. */
    private final java.util.concurrent.ConcurrentLinkedQueue<double[]> impacts = new java.util.concurrent.ConcurrentLinkedQueue<>();
    /** Phase 7: Gischt der Einschläge (com.dan.spray, Einschlag ohne Körper). */
    public final com.dan.spray.Spray spray = new com.dan.spray.Spray();
    private final com.dan.spray.gl.SprayGl sprayGl = new com.dan.spray.gl.SprayGl();
    private boolean sprayOk;
    private long splashes;
    /** Phase 8: die Welle (com.dan.wave) im eigenen Faden „Welle“; das Bild liest ihren Stand als Textur. */
    public final com.dan.wave.Wave wave = com.dan.wave.Wave.lake();
    public final Physik welle = new Physik("Welle");
    private final com.dan.wave.gl.WaveGl waveGl = new com.dan.wave.gl.WaveGl();
    /** Seit die Gitter stehen (der Grund wird beim Start abgetastet). */
    public volatile boolean waveReady;
    public volatile boolean showWave = true;
    /** Pegel vor dem Steg (für die Kurve der Höhe) und wo er steht. */
    public volatile com.dan.wave.Wave.Gauge pegel;
    /** Die Front im Blick des Stegs mit tiefem Wasser davor {x, z, nx, nz} (für die Probe-Welle ohne Zielen). */
    public volatile double[] waveFront;
    private long runups;
    /**
     * Phase 9: die Eisberge (com.dan.buoyancy) im Faden der Physik. Stücke, die {@code fracture} auf dem Wasser zur Ruhe
     * bringt, gehen hier hinein (Proben werden in einem eigenen Faden gebaut); das Treibeis ist ein Feld vor der Front.
     */
    public final com.dan.buoyancy.Bergs bergs = new com.dan.buoyancy.Bergs();
    public volatile com.dan.buoyancy.BrashField brash;
    private final com.dan.buoyancy.gl.BrashGl brashGl = new com.dan.buoyancy.gl.BrashGl();
    public volatile boolean showBergs = true;
    private final java.util.concurrent.ConcurrentLinkedQueue<Object[]> handover = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private final java.util.concurrent.ExecutorService bergWorker = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Eisberge-Proben");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });
    private int handedOver;
    private double obstacleClock;
    /** Phase 11: Eis in den Buchten des Brazo Rico und fallender Schnee (com.dan.season). */
    private final com.dan.season.gl.IceGl lakeIceGl = new com.dan.season.gl.IceGl();
    private final com.dan.season.gl.SnowfallGl snowfallGl = new com.dan.season.gl.SnowfallGl();
    private boolean seasonGlOk;
    /** Phase 11: Schneefall am Auge (mm/h Wasser), der Teil des Niederschlags, der unter 0,5 bis 2,5 °C als Schnee fällt. */
    private volatile double snowRate;
    /** Phase 11: der Brazo Rico südlich der Front {x0, z0, x1, z1} (dort friert es in den flachen Buchten). */
    public static final double[] BRAZO_RICO = {-1500, 600, 6400, 4600};
    /** Phase 14: die Regiebücher (Text mit Blatt am Ende), ihre Fotos und was sie sagen. */
    public final com.dan.glacier.scene.Regie regie = new com.dan.glacier.scene.Regie();
    private final RegieCtx regieCtx = new RegieCtx();
    private volatile String captureCaption, sheetName, regieMsg = "";
    private final List<BufferedImage> album = new ArrayList<>();
    private final List<String> albumCaps = new ArrayList<>();
    private double albumFps, albumGpu;
    private int albumSamples;
    /** Phase 13: die Ruptura (Eisdamm an der Halbinsel, Stau, Tunnel, Brücke, Abfluss) und ihre Zeichnung. */
    public volatile com.dan.glacier.scene.Ruptura ruptura;
    private final RupturaGl rupturaGl = new RupturaGl();
    public volatile boolean showRuptura = true;
    private long rupturaSplashes;
    /** Phase 12: der Klang (com.dan.soundscape über {@link com.dan.glacier.scene.Klang}) und der Lautsprecher. */
    public final com.dan.glacier.scene.Klang klang = new com.dan.glacier.scene.Klang();
    private com.dan.soundscape.AudioOut audio;
    private volatile boolean tonAn = !"false".equals(System.getProperty("glacier.ton"));
    private boolean klangFront;
    /** Phase 10: Vögel, Stege, Katamaran (siehe {@link Leben}). */
    final Leben leben;
    /** Phase 2: das Gelände (aus daten/gelaende) und was darauf wächst. */
    public final Terrain terrain;
    public final Vegetation vegetation;
    private final TerrainLod lod = new TerrainLod();
    private int coverTex, panoTex, panoFbo;
    static final int PANO_W = 256, PANO_H = 128;
    /** Phase 2: der See (Preset „Lago“; die Verdrahtung gibt Wind und Spiegel). */
    public final Ocean ocean = Ocean.lago(4);
    private final OceanGl oceanGl = new OceanGl();
    private volatile boolean oceanOk;
    private volatile String oceanCheck = "";
    /** Phase 2: Steppengras und Ufergras. */
    public final Reed reed = Reed.beaufort(4);
    private final ReedGl reedGl = new ReedGl();
    private volatile boolean reedOk;
    /** Phase 2: Wald aus Lenga, Ñire, Guindo, mit Sonnenschatten. */
    public final ForestField forest;
    private final ForestGl forestGl;
    private volatile boolean forestOk;
    private final SunShadow sunShadow = new SunShadow();
    /** Phase 2: Bodendecke um das Auge (Biom Patagonien). */
    public final Meadow meadow;
    private final GroundGl groundGl = new GroundGl();
    private volatile boolean groundOk;
    public volatile boolean showWater = true, showReed = true, showForest = true, showSunShadow = true, showGround = true;

    /** Phase 4: Probe-Eisberge im Kanal vor dem Steg (Material aus {@code com.dan.ice}; der Gletscher folgt in Phase 5). */
    public final Ice bergIce = Ice.iceberg();
    private final IceGl iceGl = new IceGl();
    private final List<IceGl.Instance> icebergs = new ArrayList<>();
    /** Je Probe-Eisberg: x, z, Drehung (Grad), seed, Halbmesser (m), Höhenfaktor. */
    private final List<double[]> bergPlaces = new ArrayList<>();
    private volatile boolean iceOk;
    public volatile boolean showIce = true;

    /**
     * Phase 5: der Gletscher aus {@code com.dan.ice}. Gebaut im Hintergrund (rund 1,5 s), dann auf der Grafikkarte:
     * Kacheln mit Stufen nach dem Abstand und die Front als Wand; das Gelände unter dem Eis sinkt auf den Grund.
     */
    private volatile Gletscher gletscher;
    private GlacierGl glacierGl;
    private volatile boolean glacierOk, glacierFailed;
    private volatile String glacierLine = "Gletscher wird gebaut …";
    private int meltVao, meltCount;
    public volatile boolean showGlacier = true, showMelt = true;
    /** Phase 10: größter Zeitraffer für die Schmelze der Eisberge und des Treibeises. */
    public static final double MELT_LAPSE_MAX = 60;
    /** Phase 10: Sicht unter Wasser (m) im trüben Gletscherwasser (vorher 16 m). */
    public static final float UNDERWATER_VIS = 30f;
    /** Phase 10: Kacheln des Bodens für fracture, und für welchen Stand des Gletschers sie gelten. */
    private volatile com.dan.fracture.GroundCache bruchGround;
    private double groundAdvance = Double.NaN;
    private boolean groundGlacier = true;

    /** Phase 3: der Himmel (Kern ohne Grafikkarte; die Uhr gibt Zeit und Ort, die Kamera die Augenhöhe). */
    public final Sky sky = new Sky();
    private final SkyGl skyGl = new SkyGl();
    private volatile boolean skyOk;
    private volatile String skyCheck = "";
    /** Sternbildfiguren (Kreuz des Südens, Zentaur …) einblenden. */
    public volatile boolean showFigures;
    /** Augenanpassung: geglättete Leuchtdichte, Leuchtdichte im Blickfeld, Zeit bis zur nächsten Messung. */
    private double adaptLum = -1, viewLum = -1, viewTimer;
    /** Anteil der zwölf Messstrahlen der Augenanpassung, die auf Eis treffen (Phase 6), und der Faktor dafür. */
    private double iceInView;
    static final double ICE_GAIN = 12.0;
    /** Anteil der Anpassung (1 = volle, 0 = keine); 0,85 lässt die Nacht Nacht bleiben (wie Haithabu). */
    public volatile double adaptation = 0.85;
    /** Phase 3: die Wolken (Bedeckung, Art, Wind in Wolkenhöhe und Lentikularis aus der Verdrahtung). */
    public final Clouds clouds = new Clouds();
    private final CloudGl cloudGl = new CloudGl();
    private volatile boolean cloudsOk;
    private volatile String cloudCheck = "";
    private double cloudShade = 1;
    public volatile boolean showClouds = true;
    /** Phase 3: Regen. */
    public final Rain rain = new Rain();
    private final RainGl rainGl = new RainGl();
    private volatile boolean rainOk;
    private volatile String rainCheck = "";
    public volatile boolean showRain = true;
    /** Deckkraft des Regens 0..2 (Bedienfeld). */
    public volatile double rainStrength = 1;
    /** Phase 3: Seenebel über dem Lago Argentino. */
    public final Fog fog = new Fog();
    private final FogGl fogGl = new FogGl();
    private volatile boolean fogOk;
    private volatile String fogCheck = "";
    public volatile boolean showFog = true;
    /** Phase 3: Gewitter (in Patagonien selten). */
    public final Lightning lightning = new Lightning();
    private final LightningGl lightningGl = new LightningGl();
    private volatile boolean lightningOk;
    private volatile String lightningCheck = "";
    private Lightning.Snapshot lightningSnap;
    public volatile boolean showLightning = true;
    /** Wie weit die Belichtung im Blitz nachgibt (1 = gar nicht); erholt sich in einer Viertelsekunde. */
    private double flashGive = 1;
    private double[] lightningSeenFrom;
    private volatile String lastBoltText = "";
    private double weatherCpuMs;
    private int depthCopyTex, depthCopyFbo, depthCopyW, depthCopyH;

    private volatile Quality quality = Quality.MITTEL;
    public volatile double renderScale = quality.renderScale;
    /**
     * Regelt die Auflösung nach der Zeit der Grafikkarte: über dem Budget von {@link #targetFps} sinkt sie in Schritten
     * bis {@link #MIN_SCALE}, mit Luft steigt sie wieder bis zur Stufe (aus Haithabu).
     */
    public volatile boolean autoScale = true;
    public volatile double targetFps = 60;
    public static final double MIN_SCALE = 0.5;
    private long scaleNanos;
    private int scaleSteps;

    public volatile boolean showHud = true, hudFull;
    /** Marken im Gelände: Raster von 1 km. */
    public volatile boolean showMarks = true;
    /** Szene anhalten (Uhr, Kamera, Physik). */
    public volatile boolean frozen;
    /** Belichtung in Blendenstufen von Hand dazu. */
    public volatile double exposureBias;
    public volatile boolean photoRequest;
    /** Das nächste fertige Fensterbild an diesen Empfänger (einmal; im GL-Faden). */
    public volatile Consumer<BufferedImage> grabRequest;
    /** Ordner für Fotos (F12); ohne Angabe {@code docs/status} im Arbeitsverzeichnis. */
    public volatile File photoDir;

    private final Shader skyShader = new Shader("Himmel", null, "fullscreen.vert", "sky.frag"),
            skyPano = new Shader("Himmelspanorama", null, "fullscreen.vert", "skypano.frag"),
            terrainShader = new Shader("Gelände", null, "terrain.vert", "terrain.frag"),
            post = new Shader("Nachbearbeitung", null, "fullscreen.vert", "post.frag"),
            overlay = new Shader("Überlagerung", null, "fullscreen.vert", "overlay.frag"),
            meltShader = new Shader("Schmelzbäche", null, "melt.vert", "melt.frag"),
            flutShader = new Shader("Ruptura", null, "melt.vert", "flut.frag"),
            underwaterShader = new Shader("Unter Wasser", null, "fullscreen.vert", "underwater.frag");
    private final Shader[] all = {skyShader, skyPano, terrainShader, post, overlay, meltShader, flutShader, underwaterShader};
    private final HudOverlay hud = new HudOverlay();
    private int emptyVao, hdrTex, depthTex, hdrFbo, hdrW, hdrH, surfW = 1, surfH = 1, defaultFbo;
    private boolean reversedZ, ready;
    private final int[][] stamp = new int[2][MARKS];
    private final double[] passMs = new double[MARKS - 1];
    private int frame;
    private long lastNanos, statsNanos;
    private int statsFrames;
    private double fps, gpuMs, cpuMs, worstMs;
    private volatile String glInfo = "", error;
    private volatile ComputeProbe.Result compute;
    private volatile boolean reloadShaders;
    private Consumer<String> statusListener = s -> { };
    private Consumer<String> errorListener = s -> { };
    private volatile String photoMsg = "";
    private volatile long photoMsgUntil;

    public Renderer(SceneClock clock, Wiring wiring) { this(clock, wiring, Terrain.load(Terrain.defaultDir())); }

    public Renderer(SceneClock clock, Wiring wiring, Terrain terrain) {
        this.clock = clock;
        this.wiring = wiring;
        this.terrain = terrain;
        com.dan.glacier.camera.Ansichten.use(terrain);
        this.vegetation = new Vegetation(terrain);
        this.forestGl = new ForestGl(Vegetation.species(), 4, 2026);
        this.forest = new ForestField(3, 4, vegetation, (x, z) -> (float) terrain.height(x, z), 2026);
        forest.setHeights(forestGl.heights());
        this.meadow = new Meadow(Biome.patagonia(), new Bodendecke(terrain, vegetation));
        meadow.radius = 24;
        meadow.near = 6.5f;
        meadow.seed = 2026;
        ocean.seed = 2026;
        sky.latDeg = Site.LAT;
        sky.lonDeg = Site.LON;
        sky.epochMillis = clock.epochMillis();
        fog.seed = 2026;
        lightning.seed = 2026;
        // das Rauschen der Wolken (8 MB, 0,5 bis 3 s) schon jetzt im Hintergrund rechnen
        Thread nt = new Thread(() -> { Noise.base(); Noise.detail(); Noise.weather(); }, "cloud-noise");
        nt.setDaemon(true);
        nt.start();
        setQuality(quality);
        if (wiring != null) {
            wiring.terrain = terrain;
            wiring.feed(ocean);
            wiring.feed(sky, Site.LAKE_LEVEL_ASL + 10);
        }
        camera.groundLevel = terrain.height(camera.eye[0], camera.eye[2]);
        camera.snap();
        leben = new Leben(this);
    }

    public Renderer() { this(new SceneClock(), null, Terrain.flat()); }

    // ------------------------------------------------------------------ Zugriff
    public void setStatusListener(Consumer<String> l) { statusListener = l; }
    public void setErrorListener(Consumer<String> l) { errorListener = l; }
    public String glInfo() { return glInfo; }
    public String error() { return error; }
    public double fps() { return fps; }
    public double gpuMs() { return gpuMs; }
    public double cpuMs() { return cpuMs; }
    /** Phase 13 (Befund Status_Phase12: 38 Bilder/s bei 9,5 + 6 ms): vom Prozessor die Szene ohne GL-Aufträge. */
    private double sceneCpuMs;
    public double sceneCpuMs() { return sceneCpuMs; }
    public boolean reversedDepth() { return reversedZ; }
    public ComputeProbe.Result compute() { return compute; }
    public Quality quality() { return quality; }
    public void requestShaderReload() { reloadShaders = true; }
    public double[] lastPassTimes() { return passMs.clone(); }
    public long frames() { return frame; }
    public double takeWorstFrameMs() { double w = worstMs; worstMs = 0; return w; }

    /** Boden unter (x, z): über dem Gletscher seine Oberfläche mit Spalten, sonst das Gelände. */
    public double groundAt(double x, double z) {
        Gletscher g = gletscher;
        if (g != null && showGlacier && g.glacier.covers(x, z)) return g.glacier.surface(x, z);
        return terrain.height(x, z);
    }

    /** Ob der Gletscher noch Kacheln baut (oder noch nicht auf der Grafikkarte ist). */
    public boolean glacierBusy() {
        if (glacierFailed || !terrain.measured() || !iceOk) return false;
        GlacierGl g = glacierGl;
        KalbungGl k = kalbungGl;
        return g == null || g.busy() || k != null && k.busy();
    }

    /** Der Gletscher (im Hintergrund gebaut; null, solange er fehlt). */
    public Gletscher gletscher() { return gletscher; }

    /** Baut den Gletscher sofort (für Prüfung und Werkzeuge ohne Grafikkarte). */
    public synchronized Gletscher gletscherJetzt() {
        if (gletscher == null) setGletscher(new Gletscher(terrain));
        return gletscher;
    }

    private void setGletscher(Gletscher g) {
        gletscher = g;
        com.dan.glacier.camera.Ansichten.iceSurface = (x, z) -> g.glacier.covers(x, z) ? g.glacier.surface(x, z) : Double.NaN;
    }

    /** Die Fahrt „Über die Zunge“ (Phase 5), sobald der Gletscher steht; sonst null. */
    public com.dan.glacier.camera.Fahrt zungenFahrt() {
        Gletscher g = gletscher;
        if (g == null) return null;
        com.dan.glacier.camera.Fahrt f = new com.dan.glacier.camera.Fahrt("Über die Zunge", g.zungenWeg(), 150, 0, 12);
        f.endHeading = Double.NaN;
        return f;
    }

    /**
     * Phase 14: die Fahrt „Durch die Spalte“: hinab in eine tiefe Spalte nahe der Front, ihr entlang in halber Tiefe,
     * wieder hinauf; 5 m/s, leicht nach unten. null, solange der Gletscher fehlt oder keine Spalte gefunden ist.
     */
    public com.dan.glacier.camera.Fahrt spaltenFahrt() {
        Gletscher g = gletscher;
        if (g == null) return null;
        double[][] w = g.spaltenWeg();
        if (w == null) return null;
        com.dan.glacier.camera.Fahrt f = new com.dan.glacier.camera.Fahrt("Durch die Spalte", w, 1, 0, 4);
        f.duration = Math.max(30, f.length / 5);
        return f;
    }

    /** Phase 14: ein Regiebuch von vorn (das Album beginnt leer). */
    public void regieStart(com.dan.glacier.scene.Regie.Book b) {
        album.clear();
        albumCaps.clear();
        albumFps = albumGpu = 0;
        albumSamples = 0;
        regieMsg = "";
        regie.start(b, regieCtx);
    }

    public void regieStop() { regie.stop(); }

    /** Ob ein Foto oder das Blatt auf das nächste Bild wartet (Tempo zeichnet dann sofort). */
    public boolean regieWantsFrame() { return captureCaption != null || sheetName != null; }

    /** Phase 14: „Regie: Ein Sommertag an der Front · 10 Uhr: … · 4:10 von 16:00 · 3 Fotos“ (HUD). */
    public String regieLine() {
        return "Regie: " + regie.describe() + (album.isEmpty() ? "" : " · " + album.size() + " Fotos") + (regieMsg.isEmpty() ? "" : " · " + regieMsg);
    }

    /** Die drei teuersten Posten der Grafikkarte jetzt: „Eis 3,1 · See 1,4 · Wald 1,1 ms“. */
    public String topPasses(int n) {
        double[] t = passMs.clone();
        StringBuilder b = new StringBuilder();
        for (int k = 0; k < n; k++) {
            int best = -1;
            for (int i = 0; i < t.length && i < PASSES.length; i++) if (best < 0 || t[i] > t[best]) best = i;
            if (best < 0 || t[best] <= 0) break;
            b.append(k > 0 ? " · " : "").append(PASSES[best]).append(' ').append(String.format(Locale.GERMAN, "%.1f", t[best]));
            t[best] = -1;
        }
        return b.append(" ms").toString();
    }

    /** Was die Regiebücher in der App tun (Phase 14). */
    private final class RegieCtx implements com.dan.glacier.scene.Regie.Ctx {
        @Override public void time(java.time.LocalDate date, double hour) { clock.set(date, hour); readapt(); }
        @Override public void lapse(double x) { clock.lapse = x; }
        @Override public void weather(com.dan.weather.Scenario s) {
            if (wiring == null) return;
            wiring.regie = true;
            wiring.start(s);
        }
        @Override public void weatherFree(com.dan.weather.Weather.Phase p, double bft, double fromDeg) {
            if (wiring == null) return;
            wiring.regie = true;
            wiring.startFree(p);
            if (!Double.isNaN(bft)) wiring.hand(com.dan.weather.Weather.Var.WIND, com.dan.glacier.wiring.Beaufort.mid((int) Math.round(bft)));
            if (!Double.isNaN(fromDeg)) wiring.hand(com.dan.weather.Weather.Var.DIRECTION, fromDeg);
            wiring.hand(com.dan.weather.Weather.Var.RAIN, 0);
            wiring.hand(com.dan.weather.Weather.Var.COVER, 1);
            wiring.hand(com.dan.weather.Weather.Var.FOG, 0);
        }
        @Override public void view(String name) {
            camera.stopFahrt();
            camera.stopMitfahrt();
            com.dan.glacier.camera.Ansichten.Ansicht a = com.dan.glacier.camera.Ansichten.byName(name);
            if (a != null) com.dan.glacier.camera.Ansichten.apply(a, camera, PHASE);
            readapt();
        }
        @Override public void fahrt(String name) {
            com.dan.glacier.camera.Fahrt f = name.toLowerCase(Locale.ROOT).contains("spalte") ? spaltenFahrt() : zungenFahrt();
            if (f == null) { regieMsg = "Fahrt „" + name + "“ steht noch nicht"; return; }
            camera.stopMitfahrt();
            camera.startFahrt(f);
        }
        @Override public void boot() { if (!startBootsfahrt()) regieMsg = "die Route der Bootsfahrt steht noch nicht"; }
        @Override public void aussteigen() { stopBootsfahrt(); }
        @Override public void kalbung(com.dan.glacier.scene.Kalbung.Art art) {
            com.dan.glacier.scene.Kalbung k = kalbung;
            if (k == null) return;
            double[] fb = frontImBlick();
            k.trigger(art, fb[0], fb[1]);
        }
        @Override public void kalbungRegie(boolean on) { com.dan.glacier.scene.Kalbung k = kalbung; if (k != null) k.regie = on; }
        @Override public void ruptura() { rupturaRegiebuch(); }
        @Override public void damm(boolean on) {
            if (wiring != null) wiring.jahr.dammJahr = on;
            com.dan.glacier.scene.Ruptura ru = ruptura;
            if (!on && ru != null && ru.present()) ru.reset();
        }
        @Override public void figuren(boolean on) { showFigures = on; }
        @Override public void capture(String caption) { captureCaption = caption; }
        @Override public void sheet(String name) { sheetName = name; }
        @Override public void say(String text) { regieMsg = text; }
    }

    /** Die Fotos des Regiebuchs als ein Blatt nach docs/status (zwei Spalten, Unterschrift in zwei Zeilen; aus Haithabu). */
    private void writeSheet(String name) {
        if (album.isEmpty()) return;
        try {
            int n = album.size(), cols = 2, rows = (n + 1) / 2, cw = 960, ch = (int) Math.round(album.get(0).getHeight() * cw / (double) album.get(0).getWidth()), cap = 50, head = 44;
            BufferedImage out = new BufferedImage(cols * cw, head + rows * (ch + cap), BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = out.createGraphics();
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(new java.awt.Color(11, 18, 25));
            g.fillRect(0, 0, out.getWidth(), out.getHeight());
            g.setColor(new java.awt.Color(230, 236, 240));
            g.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 20));
            g.drawString(String.format(Locale.GERMAN, "Glacier · Regiebuch „%s“ · %s · %s · Ø %.0f Bilder/s · Grafikkarte Ø %.2f ms · %s", name, clock.dateLabel(), quality.label,
                    albumFps / Math.max(1, albumSamples), albumGpu / Math.max(1, albumSamples), glInfo.replaceAll(" ·.*", "")), 14, 29);
            g.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 16));
            for (int i = 0; i < n; i++) {
                int cx = (i % cols) * cw, cy = head + (i / cols) * (ch + cap);
                g.drawImage(album.get(i), cx, cy, cw, ch, null);
                g.setColor(new java.awt.Color(143, 162, 174));
                String[] ln = albumCaps.get(i).split("\n");
                for (int k = 0; k < ln.length; k++) g.drawString(ln[k], cx + 10, cy + ch + 21 + 20 * k);
            }
            g.dispose();
            File dir = photoDir != null ? photoDir : new File("docs/status");
            if (!dir.isDirectory() && !dir.mkdirs()) throw new java.io.IOException("Ordner " + dir + " lässt sich nicht anlegen");
            String fn = "Regiebuch_" + name.replace("ü", "ue").replace("ö", "oe").replace("ä", "ae").replace("ß", "ss").replace(' ', '_') + "_"
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".png";
            File f = new File(dir, fn);
            javax.imageio.ImageIO.write(out, "png", f);
            photoMsg = "Regiebuch gespeichert: " + f.getPath();
            lastSheet = f;
            System.out.println(photoMsg);
        } catch (Exception e) {
            photoMsg = "Blatt des Regiebuchs fehlgeschlagen: " + e.getMessage();
            System.err.println(photoMsg);
        }
        photoMsgUntil = System.currentTimeMillis() + 6000;
    }

    /** Das zuletzt geschriebene Blatt (für Tempo und die Prüfung); null: noch keins. */
    public volatile File lastSheet;

    /** Die Programme der App (für die Prüfung). */
    public static String[][] shaderFiles() {
        return new String[][]{{"fullscreen.vert", "sky.frag"}, {"fullscreen.vert", "skypano.frag"}, {"terrain.vert", "terrain.frag"},
                {"fullscreen.vert", "post.frag"}, {"fullscreen.vert", "overlay.frag"}, {"melt.vert", "melt.frag"},
                {"melt.vert", "flut.frag"}, {"fullscreen.vert", "underwater.frag"}, {"probe.comp"}};
    }

    public void setQuality(Quality q) {
        quality = q;
        renderScale = q.renderScale;
        lod.lodRange = q.terrainRange;
        ocean.quality = q.ocean;
        reed.quality = q.reed;
        if (forest != null) forest.apply(q.forest);
        sunShadow.half = q.shadowHalf;
        if (meadow != null) meadow.radius = (float) q.groundRadius;
        clouds.quality = q.clouds;
        rain.quality = q.rain;
        fog.quality = q.fog;
        lightning.quality = q.lightning;
        bruch.quality = q.fracture;
        bergIce.quality = q.ice;
        if (glacierGl != null) applyGlacierQuality();
    }

    private void applyGlacierQuality() {
        double f = switch (quality) { case NIEDRIG -> 0.6; case MITTEL -> 1.0; case HOCH -> 1.4; case ULTRA -> 2.0; };
        double[] r = com.dan.ice.GlacierTiles.RANGE_MEDIUM.clone();
        for (int i = 0; i < r.length; i++) r[i] *= f;
        glacierGl.range = r;
        glacierGl.surfaceIce.quality = quality.ice;
        glacierGl.wallIce.quality = quality.ice;
    }

    /**
     * Phase 8: die Welle anlegen. Das feine Gitter (2 m) liegt zwischen Steg und Front, das grobe (8 m) darum, über
     * beide Seearme. Grund: das Gelände unter dem See, die Gletscherfront ist eine Wand. Abgetastet wird in einem
     * eigenen Faden; danach läuft die Welle im Faden „Welle“.
     */
    private void startWave(Gletscher g) {
        Thread t = new Thread(() -> {
            try {
                long t0 = System.nanoTime();
                // erst, wenn die Front auf dem Vorstoß der Jahreszeit steht (sie ist die Wand der Welle)
                for (int i = 0; i < 200 && Math.abs(g.glacier.advance() - Wiring.glacierAdvance(dayOfYear())) > 1.5; i++) Thread.sleep(50);
                double[] st = terrain.steg;
                double level = camera.waterLevel;
                double[] f = Welle.front(terrain, g.glacier, level);
                waveFront = f;
                com.dan.glacier.scene.Kalbung kb = kalbung;
                if (kb != null && f != null) { kb.focusX = f[0]; kb.focusZ = f[1]; }   // Phase 10: die Regie kalbt meist im Blick
                double[] c = Welle.center(terrain, f);
                wave.onRunup = (x, z, h, tt) -> { runups++; leben.runup(x, z, h); klang.sound(com.dan.glacier.wiring.SoundEvent.Kind.AUFLAUFEN, x, level, z, h); };   // Phase 10/12
                wave.place(c[0], c[1], c[0], c[1]).build(Welle.bed(terrain, g.glacier, level));
                // Phase 9: Treibeis auf 2 km um dieselbe Mitte
                brash = new com.dan.buoyancy.BrashField(500, 500, c[0] - 1000, c[1] - 1000, 4);
                bergs.brash = brash;
                // Phase 10: 24 m vor der Wand, 6 m tief (bei 30 m Sicht steht die Wand im Bild; mit 70 m war nur Nebel zu sehen)
                com.dan.glacier.camera.Ansichten.underwater = f == null ? null : new double[]{f[0] + f[2] * 24, f[1] + f[3] * 24,
                        (Math.toDegrees(Math.atan2(-f[2], f[3])) + 360) % 360, level - 6};
                double[] gp = Welle.gauge(terrain, wave, f);
                double gx = gp[0], gz = gp[1];
                pegel = wave.gauge("Steg", gx, gz);
                welle.add(new Physik.Aufgabe() {
                    @Override public void step(double tt, double dt) { wave.step(dt); }
                    @Override public String name() { return "wave"; }
                });
                leben.place(g, f, level);
                welle.start();
                waveReady = true;
                System.out.printf(Locale.GERMAN, "Glacier: Welle steht in %.1f s · %s · Pegel %.0f m vor dem Steg%n",
                        (System.nanoTime() - t0) / 1e9, wave.describe(), Math.hypot(gx - st[0], gz - st[1]));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException e) {
                System.err.println("Glacier: Welle nicht verfügbar: " + e);
                errorListener.accept("Die Welle (com.dan.wave) lässt sich nicht anlegen.\n" + e.getMessage());
            }
        }, "Welle-Aufbau");
        t.setDaemon(true);
        t.start();
    }

    /** Eine Probe-Welle: so viel Volumen (m³) schlägt an der Front ein, die (x, z) am nächsten ist. */
    public boolean probeWave(double x, double z, double volume) {
        Gletscher g = gletscher;
        if (!waveReady || g == null) return false;
        double[] f = g.glacier.nearestFront(x, z, 3000);
        if (f == null) return false;
        double l = Math.hypot(f[2], f[3]);
        double px = f[0] + f[2] / l * 15, pz = f[1] + f[3] / l * 15;
        // Befund Status_Phase8: im flachen Winkel am Ufer staut sich der Einschlag und läuft nicht weg; dann an die
        // Front mit tiefem Wasser im Blick des Stegs
        double[] wf = waveFront;
        if (camera.waterLevel - terrain.height(f[0] + f[2] / l * 40, f[1] + f[3] / l * 40) < 15 && wf != null) {
            px = wf[0] + wf[2] * 15;
            pz = wf[1] + wf[3] * 15;
        }
        // wie ein Turm, der in 5 s eintaucht: das Volumen über 5 s Laufzeit der Welle verteilt
        wave.addVolumeOver(px, pz, volume, 15, 5);
        if (pegel != null) pegel.reset();
        return true;
    }

    /** Phase 9: ein Tag Kalbung auf einmal (Eisberge und Treibeis); gibt die Zahl der neuen Berge zurück. */
    public int fillDay() {
        Gletscher g = gletscher;
        com.dan.glacier.scene.Kalbung k = kalbung;
        if (g == null) return 0;
        double rate = k != null ? k.rateSummer * (k.summer()) + k.rateWinter * (1 - k.summer()) : 4;
        return com.dan.glacier.scene.Eisberge.fillDay(bergs, g.glacier, bergs.sea, rate, bergs.windFromDeg, System.nanoTime(), brash);
    }

    /** Phase 9: ein hoher, schmaler Berg vor der Front im Blick, der kentert. */
    public boolean toppler(double x, double z) {
        Gletscher g = gletscher;
        if (g == null) return false;
        double[] f = g.glacier.nearestFront(x, z, 3000);
        if (f == null) return false;
        double l = Math.hypot(f[2], f[3]), nx = f[2] / l, nz = f[3] / l;
        double px = f[0] + nx * 80, pz = f[1] + nz * 80;
        if (camera.waterLevel - terrain.height(px, pz) < 45) { double[] wf = waveFront; if (wf != null) { px = wf[0] + wf[2] * 80; pz = wf[1] + wf[3] * 80; nx = wf[2]; nz = wf[3]; } }
        double heading = Math.toDegrees(Math.atan2(nz, -nx));
        bergs.add(com.dan.glacier.scene.Eisberge.toppler(px, pz, camera.waterLevel, heading, System.nanoTime()));
        return true;
    }

    /** „Welle: läuft, größte Höhe 1,2 m · am Steg 0,4 m“ */
    public String waveLine() {
        if (!waveReady) return "Welle: Gitter werden angelegt";
        com.dan.wave.Wave.Gauge p = pegel;
        return wave.describe() + (p != null ? String.format(Locale.GERMAN, " · Pegel am Steg %+.2f m (höchste %.2f m)", wave.heightAt(p.x, p.z), p.max()) : "")
                + String.format(Locale.GERMAN, " · Auflaufen %d · %s · %s", runups, welle.describe(), String.format(Locale.GERMAN, "%,d KiB hochgeladen", waveGl.uploaded() / 1024));
    }

    /**
     * Den Gletscher auf die Grafikkarte: Kacheln in der gröbsten Stufe und die Wand sofort, die feinen nebenher; das
     * Gelände unter dem Eis sinkt auf den Grund, die Schmelzbäche werden hochgeladen.
     */
    private void startGlacierGl(GL4 gl) {
        try {
            long t0 = System.nanoTime();
            Gletscher g = gletscher;
            glacierGl = new GlacierGl(iceGl, g.glacier);
            glacierGl.setAdvance(Wiring.glacierAdvance(dayOfYear()));
            glacierGl.prime(gl);
            applyGlacierQuality();
            short[] hh = terrain.h.clone();
            for (int j = 0; j < Terrain.N; j++)
                for (int i = 0; i < Terrain.N; i++) {
                    double x = Terrain.X0 + (i + 0.5) * Terrain.CELL, z = Terrain.Z0 + (j + 0.5) * Terrain.CELL;
                    int k = j * Terrain.N + i;
                    if (terrain.src[k] == Terrain.EIS || g.glacier.iceShare(x, z) > 0.2) hh[k] = terrain.bed[k];
                }
            lod.uploadHeights(gl, hh);
            Object[] rb = g.ribbon();
            float[] vb = (float[]) rb[0];
            int[] ib = (int[]) rb[1];
            if (ib.length > 0) {
                int[] t = new int[3];
                gl.glGenVertexArrays(1, t, 0);
                gl.glGenBuffers(2, t, 1);
                meltVao = t[0];
                gl.glBindVertexArray(meltVao);
                gl.glBindBuffer(GL.GL_ARRAY_BUFFER, t[1]);
                gl.glBufferData(GL.GL_ARRAY_BUFFER, vb.length * 4L, Buffers.newDirectFloatBuffer(vb), GL.GL_STATIC_DRAW);
                gl.glEnableVertexAttribArray(0);
                gl.glVertexAttribPointer(0, 3, GL.GL_FLOAT, false, 36, 0);
                gl.glEnableVertexAttribArray(1);
                gl.glVertexAttribPointer(1, 4, GL.GL_FLOAT, false, 36, 12);
                gl.glEnableVertexAttribArray(2);
                gl.glVertexAttribPointer(2, 2, GL.GL_FLOAT, false, 36, 28);
                gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, t[2]);
                gl.glBufferData(GL.GL_ELEMENT_ARRAY_BUFFER, ib.length * 4L, Buffers.newDirectIntBuffer(ib), GL.GL_STATIC_DRAW);
                gl.glBindVertexArray(0);
                meltCount = ib.length;
            }
            glacierOk = true;
            if (wiring != null) wiring.colliders.add(Wiring.glacierCollider(g.glacier));
            // Phase 7: die Kalbung an der Front dieses Gletschers
            kalbung = new com.dan.glacier.scene.Kalbung(bruch, g.glacier, () -> clock.lapse, this::dayOfYear, 7);
            kalbungGl = new KalbungGl(iceGl, kalbung);
            kalbungGl.bergs = bergs;
            // Phase 13: der Eisdamm an der Spitze (nur mit echtem Gelände: dort liegt die Halbinsel)
            if (terrain.measured()) {
                ruptura = new com.dan.glacier.scene.Ruptura(bruch, terrain, g.glacier, () -> clock.lapse, 13);
                kalbungGl.ruptura = ruptura;
            }
            startWave(g);
            // Phase 14: die Spalte für „Durch die Spalte“ suchen (einige Sekunden, nebenher)
            Thread sp = new Thread(() -> { try { g.spaltenWeg(); } catch (RuntimeException e) { System.err.println("Glacier: keine Spalte: " + e); } }, "Spalte-Suche");
            sp.setDaemon(true);
            sp.setPriority(Thread.MIN_PRIORITY);
            sp.start();
            System.out.println(String.format(Locale.GERMAN, "Glacier: Gletscher auf der Grafikkarte in %.2f s · %s", (System.nanoTime() - t0) / 1e9, g.describeBaeche()));
        } catch (RuntimeException e) {
            glacierFailed = true;
            glacierLine = "!Gletscher: " + e.getMessage();
            System.err.println("Glacier: Gletscher nicht auf der Grafikkarte: " + e);
            errorListener.accept("Der Gletscher (com.dan.ice) lässt sich nicht zeichnen.\n" + e.getMessage());
        }
    }

    /** Die Schmelzbäche: Band mit Strömung, durchscheinend über der Rinne. */
    /**
     * Phase 10: der Punkt an der Front, auf den das Auge blickt (in jeder Kameraart, auch bei der Mitfahrt): längs der
     * Blickrichtung bis 4 km der erste Ort im Eis, sonst die Front, die dem Auge am nächsten ist. {x, z} oder null.
     */
    public double[] frontImBlick() {
        Gletscher g = gletscher;
        double ex = camera.eye[0], ez = camera.eye[2];
        if (g == null) return new double[]{camera.tx, camera.tz};
        double h = Math.toRadians(camera.heading()), dx = Math.sin(h), dz = -Math.cos(h);
        for (double d = 20; d <= 4000; d += 10) {
            double x = ex + dx * d, z = ez + dz * d;
            if (g.glacier.covers(x, z)) {
                double[] f = g.glacier.nearestFront(x, z, 400);
                return f != null ? new double[]{f[0], f[1]} : new double[]{x, z};
            }
        }
        double[] f = g.glacier.nearestFront(ex, ez, 4000);
        return f != null ? new double[]{f[0], f[1]} : new double[]{camera.tx, camera.tz};
    }

    /** Phase 12: Ohr, Wetter, Front und Boot an den Klang. */
    private void stepKlang(double dt, double level) {
        Gletscher g = gletscher;
        if (!klangFront && g != null && waveReady) {
            klang.front = com.dan.glacier.scene.Klang.frontPoints(g.glacier.frontLines());
            klangFront = true;
        }
        if (wiring != null) klang.summer = 0.5 + 0.5 * Math.cos(2 * Math.PI * (dayOfYear() - 16) / 365.25);
        double[] e = camera.eye;
        boolean onIce = g != null && showGlacier && g.glacier.covers(e[0], e[2]);
        boolean inForest = vegetation.forest(e[0], e[2]) > 0.3 && e[1] - terrain.height(e[0], e[2]) < 30;
        Ocean.State os = ocean.state();
        double ws = wiring != null ? wiring.wind.speed() : 6, wd = wiring != null ? wiring.wind.fromDeg() : 270, gu = wiring != null ? wiring.wind.gustiness() : 0.3;
        double air = wiring != null ? wiring.airTempC : 12;
        com.dan.buoyancy.Catamaran b = leben.boat;
        klang.boat(b.x, b.y + 1, b.z, b.speed(), leben.riding() && camera.mitfahrt != null);
        klang.step(dt, e, camera.heading(), ws, wd, gu, rain.rate + snowRate * 0.3, air, os == null ? 0.3 : os.windSeaHs, os == null ? 3 : os.peakPeriod,
                level, onIce, inForest);
    }

    /** Phase 12: Lautsprecher an oder aus. */
    public void setTon(boolean on) {
        tonAn = on;
        try {
            if (on && audio == null) { audio = new com.dan.soundscape.AudioOut(klang.s); audio.start(); }
            else if (!on && audio != null) { audio.stop(); audio = null; }
        } catch (RuntimeException | LinkageError e) {
            System.err.println("Glacier: kein Ton: " + e.getMessage());
            audio = null;
        }
    }

    public boolean tonAn() { return audio != null; }

    /** Phase 12: „Klang: …“ (HUD). */
    public String klangLine() {
        com.dan.soundscape.Soundscape s = klang.s;
        return klang.describe() + String.format(Locale.GERMAN, " · %s · %.1f ms je s Ton", audio != null ? "Lautsprecher an" : "Lautsprecher aus", s.msPerSecond());
    }

    /** Phase 11: Schneegrenze für das Gelände (m über dem See); reicht sie an den See, liegt der Schnee bis ans Ufer. */
    double snowLineNow() {
        if (wiring == null) return SNOW_LINE;
        double l = wiring.jahr.snowLine();
        return l <= 0.5 ? -150 : l;
    }

    private com.dan.season.gl.SeasonGl.Light seasonLight(double[] sun, Sky.State st, double pre, double[][] L, double[] sl, double[] hor) {
        com.dan.season.gl.SeasonGl.Light l = new com.dan.season.gl.SeasonGl.Light();
        l.sunDir = sun;
        if (skyOk) {
            l.sunColor = scale(st.sunColor, pre * cloudShade);
            l.moonDir = st.moonDir;
            l.moonColor = scale(st.moonColor, pre * cloudShade);
            l.sky = L[2];
            l.fogColor = L[3];
            l.fogDistance = fogDistance(st);
        } else {
            l.sunColor = sl;
            l.fogColor = hor;
            l.fogDistance = FOG_DISTANCE;
        }
        return l;
    }

    private com.dan.season.gl.SeasonGl.View seasonView(float[] vp, int w, int h) {
        com.dan.season.gl.SeasonGl.View v = new com.dan.season.gl.SeasonGl.View();
        v.viewProj = vp;
        v.eye = camera.eye.clone();
        v.reversedZ = reversedZ;
        v.width = w;
        v.height = h;
        v.fovyDeg = fovy();
        return v;
    }

    /** Phase 11: Eis in den flachen Buchten des Brazo Rico (nach dem See, mit Tiefentest). */
    private void drawLakeIce(GL4 gl, float[] vp, double[] sun, Sky.State st, double pre, double[][] L, double[] sl, double[] hor, int w, int h) {
        if (!seasonGlOk || wiring == null) return;
        com.dan.season.Year.State ys = wiring.jahr.state();
        com.dan.season.gl.IceGl.Input in = new com.dan.season.gl.IceGl.Input();
        in.bedTexture = lod.heightTex;
        in.bedRect = new double[]{Terrain.X0, Terrain.Z0, Terrain.N * Terrain.CELL};
        in.bedScale = Terrain.HMAX - Terrain.HMIN;
        in.bedOffset = Terrain.HMIN;
        in.waterLevel = wiring.waterLevel.brazoRico();
        in.thickness = ys.ice();
        in.iceDepth = ys.iceDepth();
        in.snow = ys.snowCover();
        in.frost = ys.frost();
        double[] to = wiring.wind.toVector();
        in.windTo = new double[]{to[0], to[2]};
        in.clip = BRAZO_RICO;
        if (!com.dan.season.gl.IceGl.any(in)) return;
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
        gl.glViewport(0, 0, w, h);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthFunc(reversedZ ? GL.GL_GREATER : GL.GL_LESS);
        lakeIceGl.draw(gl, seasonView(vp, w, h), seasonLight(sun, st, pre, L, sl, hor), in);
        gl.glBindVertexArray(emptyVao);
    }

    /** Phase 11: fallender Schnee um das Auge. */
    private void drawSnowfall(GL4 gl, float[] vp, double[] sun, Sky.State st, double pre, double[][] L, double[] sl, double[] hor, int w, int h) {
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
        gl.glViewport(0, 0, w, h);
        gl.glDepthFunc(reversedZ ? GL.GL_GREATER : GL.GL_LESS);
        double[] to = wiring != null ? wiring.wind.toVector() : new double[]{1, 0, 0};
        double ws = wiring != null ? wiring.wind.speed() : 3;
        snowfallGl.draw(gl, seasonView(vp, w, h), seasonLight(sun, st, pre, L, sl, hor), snowRate, new double[]{to[0], to[2]}, ws, clock.t());
        gl.glDisable(GL.GL_BLEND);
        gl.glDepthMask(true);
        gl.glBindVertexArray(emptyVao);
    }

    /** Phase 11: „Jahr: …“ (HUD). */
    public String jahrLine() {
        if (wiring == null) return "Jahr: ohne Verdrahtung";
        return wiring.jahr.describe() + (snowRate > 0.01 ? String.format(Locale.GERMAN, " · Schneefall %.1f mm/h (%,d Flocken)", snowRate, com.dan.season.gl.SnowfallGl.flakes(snowRate)) : "")
                + (seasonGlOk ? " · " + lakeIceGl.info() : "");
    }

    /** Phase 10: Stege, Katamaran und Vögel ins HDR-Ziel, mit dem Licht wie Wald und Eis. */
    private void drawLeben(GL4 gl, float[] vp, double[] sun, Sky.State st, double pre, double[][] L, double[] sl, double[] zen, double[] hor, int w, int h) {
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
        gl.glViewport(0, 0, w, h);
        com.dan.buoyancy.gl.BuoyancyGl.View bv = new com.dan.buoyancy.gl.BuoyancyGl.View();
        bv.viewProj = vp;
        bv.eye = camera.eye.clone();
        bv.reversedZ = reversedZ;
        com.dan.buoyancy.gl.BuoyancyGl.Light bl = new com.dan.buoyancy.gl.BuoyancyGl.Light();
        com.dan.flock.gl.FlockGl.View fv = new com.dan.flock.gl.FlockGl.View();
        fv.viewProj = vp;
        fv.eye = camera.eye.clone();
        fv.reversedZ = reversedZ;
        com.dan.flock.gl.FlockGl.Light fl = new com.dan.flock.gl.FlockGl.Light();
        bl.sunDir = fl.sunDir = sun;
        if (skyOk) {
            bl.sunColor = fl.sunColor = scale(st.sunColor, pre * cloudShade);
            bl.moonDir = fl.moonDir = st.moonDir;
            bl.moonColor = fl.moonColor = scale(st.moonColor, pre * cloudShade);
            bl.skyTop = fl.skyTop = L[2];
            bl.skyHorizon = fl.skyHorizon = L[3];
            bl.fogDistance = fl.fogDistance = fogDistance(st);
        } else {
            bl.sunColor = fl.sunColor = sl;
            bl.skyTop = fl.skyTop = zen;
            bl.skyHorizon = fl.skyHorizon = hor;
            bl.fogDistance = fl.fogDistance = FOG_DISTANCE;
        }
        // nachts brennt im Salon Licht
        bl.windowGlow = st.sunAlt < -3 ? 0.4 * pre : 0;
        leben.draw(gl, bv, bl, fv, fl);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
        gl.glViewport(0, 0, w, h);
        gl.glDepthFunc(reversedZ ? GL.GL_GREATER : GL.GL_LESS);
        gl.glBindVertexArray(emptyVao);
    }

    // ------------------------------------------------------------------ Phase 10: Bootsfahrt, Vögel, Stege

    /** Die Bootsfahrt beginnen: der Katamaran legt ab, die Kamera fährt mit (Blick nach Backbord zur Front). */
    public boolean startBootsfahrt() { return leben.startRide(); }
    /** Aussteigen: die Kamera bleibt, wo sie ist; das Boot fährt weiter. */
    public void stopBootsfahrt() { leben.stopRide(); }
    public boolean bootsfahrtLaeuft() { return leben.riding(); }
    /** Die Route der Bootsfahrt {x, z, Fahrt} oder null, solange die Front nicht feststeht. */
    public List<double[]> bootsRoute() { return leben.route; }
    public com.dan.buoyancy.Catamaran katamaran() { return leben.boat; }
    public com.dan.flock.Flock voegel() { return leben.flock; }
    public com.dan.glacier.scene.Stege stege() { return leben.stege; }
    public com.dan.weathering.Weathering stegeNass() { return leben.nass; }
    public String lebenLine() { return leben.describe(); }
    public int moewenAufBergen() { return leben.gullsOnBergs(); }
    public void showLeben(boolean birds, boolean boat, boolean stege) { leben.showBirds = birds; leben.showBoat = boat; leben.showStege = stege; }
    public boolean[] lebenShown() { return new boolean[]{leben.showBirds, leben.showBoat, leben.showStege}; }

    private void drawMelt(GL4 gl, float[] vp, float[] inv, double[] sun, double level, double[] sl, double[] zen, double[] hor) {
        common(gl, meltShader, vp, inv, sun, level);
        meltShader.set(gl, "uSunRgb", sl);
        meltShader.set(gl, "uSunAmb", zen);
        meltShader.set(gl, "uSkyHor", hor);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthFunc(reversedZ ? GL.GL_GEQUAL : GL.GL_LEQUAL);
        gl.glDepthMask(false);
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);
        gl.glEnable(GL4.GL_POLYGON_OFFSET_FILL);
        gl.glPolygonOffset(reversedZ ? 1f : -1f, reversedZ ? 1f : -1f);
        gl.glBindVertexArray(meltVao);
        gl.glDrawElements(GL.GL_TRIANGLES, meltCount, GL.GL_UNSIGNED_INT, 0);
        gl.glDisable(GL4.GL_POLYGON_OFFSET_FILL);
        gl.glDisable(GL.GL_BLEND);
        gl.glDepthMask(true);
        gl.glDepthFunc(reversedZ ? GL.GL_GREATER : GL.GL_LESS);
        gl.glBindVertexArray(emptyVao);
    }

    /** Phase 13: das Wasser der Ruptura in der Rinne (wie die Schmelzbäche, mit dem Shader flut.frag). */
    private void drawFlut(GL4 gl, float[] vp, float[] inv, double[] sun, double level, double[] sl, double[] zen, double[] hor) {
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
        gl.glViewport(0, 0, hdrW, hdrH);
        common(gl, flutShader, vp, inv, sun, level);
        flutShader.set(gl, "uSunRgb", sl);
        flutShader.set(gl, "uSunAmb", zen);
        flutShader.set(gl, "uSkyHor", hor);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthFunc(reversedZ ? GL.GL_GEQUAL : GL.GL_LEQUAL);
        gl.glDepthMask(false);
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);
        gl.glDisable(GL.GL_CULL_FACE);
        rupturaGl.drawRibbon(gl);
        gl.glDisable(GL.GL_BLEND);
        gl.glDepthMask(true);
        gl.glDepthFunc(reversedZ ? GL.GL_GREATER : GL.GL_LESS);
        gl.glBindVertexArray(emptyVao);
    }

    /** Phase 13: „Ruptura: …“ (HUD). */
    public String rupturaLine() {
        com.dan.glacier.scene.Ruptura ru = ruptura;
        if (ru == null) return terrain.measured() ? "Ruptura: wartet auf den Gletscher" : "!Ruptura: ohne Höhendaten keine Halbinsel";
        return ru.describe() + " · " + rupturaGl.info() + (rupturaSplashes > 0 ? String.format(Locale.GERMAN, " · %d Schwälle an der Mündung", rupturaSplashes) : "");
    }

    /** Phase 13: das Regiebuch „Die Ruptura“: Jahr mit Damm, 4. März 15 Uhr, die Uhr steht; der Blick auf die Brücke. */
    public void rupturaRegiebuch() {
        com.dan.glacier.scene.Ruptura ru = ruptura;
        if (ru == null || wiring == null) return;
        wiring.jahr.dammJahr = true;
        clock.set(java.time.LocalDate.of(2027, 3, 4), 15.0);
        clock.lapse = 0;
        com.dan.glacier.scene.Kalbung k = kalbung;
        if (k != null) k.regie = false;   // die Kalbung der Front stört die Bildfolge nicht
        ru.regiebuch();
        readapt();
    }

    public TerrainLod lod() { return lod; }
    public boolean oceanRunning() { return oceanOk; }
    public String oceanCheck() { return oceanCheck; }
    public boolean forestReady() { return forestOk && forestGl.ready(); }
    public String forestInfo() { return forestOk ? forestGl.info() : "Wald: nicht gestartet"; }
    public String reedInfo() { return reedOk ? reedGl.info() : "Halme: nicht gestartet"; }

    /** Tag im Jahr (1..365) an der Front. */
    public int dayOfYear() { return clock.date().getDayOfYear(); }

    /**
     * Belichtung. Mit {@code com.dan.sky}: aus der geglätteten Anpassungsleuchtdichte mit nur teilweiser Anpassung
     * ({@link #adaptation}); im Blitz gibt sie kurz nach. Ohne: aus der Helligkeitstabelle von Phase 1.
     */
    public double exposure() {
        if (skyOk && adaptLum > 0) return Sky.State.exposureFor(adaptLum, adaptation) * Math.pow(2, exposureBias) / flashGive;
        double b = Sun.skyBrightness(clock.sun()[0]);
        return 0.6 * Math.pow(b, -0.85) * Math.pow(2, exposureBias);
    }

    public boolean skyRunning() { return skyOk; }
    public boolean cloudsRunning() { return cloudsOk; }
    public boolean rainRunning() { return rainOk; }
    public boolean fogRunning() { return fogOk; }
    public boolean lightningRunning() { return lightningOk; }
    public double cloudShade() { return cloudShade; }
    public String skyCheck() { return skyCheck; }
    public String cloudCheck() { return cloudCheck; }
    public String lastBolt() { return lastBoltText; }

    /** Der aktuelle Himmelszustand (nie null nach dem ersten Schritt). */
    public Sky.State skyState() {
        Sky.State st = sky.state();
        return st != null ? st : sky.update();
    }

    /** Augenanpassung neu (nach Sprüngen der Uhr oder der Kamera). */
    public void readapt() {
        viewLum = -1;
        adaptLum = -1;
        adapt(skyState(), 1);
    }

    /** Senkrechter Bildwinkel in Grad. */
    public double fovy() { return Camera.FOVY; }

    // ------------------------------------------------------------------ Lebenslauf

    @Override
    public void init(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        int major = d.getContext().getGLVersionNumber().getMajor(), minor = d.getContext().getGLVersionNumber().getMinor();
        String renderer = gl.glGetString(GL.GL_RENDERER), vendor = gl.glGetString(GL.GL_VENDOR), version = gl.glGetString(GL.GL_VERSION);
        glInfo = renderer + " · OpenGL " + major + "." + minor;
        System.out.println("Glacier: " + vendor + " · " + renderer + " · " + version);
        if (major < MIN_MAJOR || (major == MIN_MAJOR && minor < MIN_MINOR)) {
            fail("Die Grafikkarte meldet OpenGL " + major + "." + minor + " (" + renderer + ", Treiber " + version
                    + "). Glacier braucht OpenGL " + MIN_MAJOR + "." + MIN_MINOR + ".\nBitte den Grafiktreiber aktualisieren.");
            return;
        }
        gl.setSwapInterval(1);
        defaultFbo = d.getContext().getDefaultDrawFramebuffer();
        reversedZ = (major > 4 || minor >= 5) || gl.isExtensionAvailable("GL_ARB_clip_control");
        if (reversedZ) gl.glClipControl(GL4.GL_LOWER_LEFT, GL4.GL_ZERO_TO_ONE);
        try {
            for (Shader s : all) s.build(gl);
        } catch (RuntimeException e) {
            fail(e.getMessage());
            return;
        }
        int[] v = new int[1];
        gl.glGenVertexArrays(1, v, 0);
        emptyVao = v[0];
        for (int[] s : stamp) gl.glGenQueries(MARKS, s, 0);
        compute = ComputeProbe.run(gl);
        System.out.println("Glacier: " + compute.line());
        // Phase 2: Gelände, Bedeckung, Himmelspanorama, See, Halme, Wald, Bodendecke (jedes für sich; was nicht startet, fehlt)
        lod.upload(gl, terrain);
        coverTex = coverTexture(gl, vegetation);
        panoTex = Gpu.texture(gl, PANO_W, PANO_H, GL.GL_RGBA16F, false);
        gl.glBindTexture(GL.GL_TEXTURE_2D, panoTex);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_REPEAT);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        panoFbo = Gpu.fbo(gl, panoTex, 0);
        System.out.println("Glacier: Gelände " + terrain.describe());
        System.out.println("Glacier: " + vegetation.describe());
        if (compute.ok()) {
            try {
                oceanGl.init(gl);
                oceanOk = true;
                System.out.println("Glacier: See " + oceanGl.info());
            } catch (RuntimeException e) {
                System.err.println("Glacier: See nicht verfügbar: " + e.getMessage());
                errorListener.accept("Der See (com.dan.ocean) lässt sich nicht starten.\n" + e.getMessage());
            }
        }
        try {
            reedGl.init(gl);
            reedOk = true;
        } catch (RuntimeException e) {
            System.err.println("Glacier: Halme nicht verfügbar: " + e.getMessage());
            errorListener.accept("Steppe und Gras (com.dan.reed) lassen sich nicht starten.\n" + e.getMessage());
        }
        try {
            forestGl.init(gl);
            forestGl.setSeason(Wiring.northernDay(dayOfYear()), wiring != null ? (float) wiring.jahr.forestSnow() : 0);
            sunShadow.ensure(gl, quality.shadowSize);
            forestOk = true;
        } catch (RuntimeException e) {
            System.err.println("Glacier: Wald nicht verfügbar: " + e.getMessage());
            errorListener.accept("Der Wald (com.dan.forest) lässt sich nicht starten.\n" + e.getMessage());
        }
        try {
            groundGl.init(gl);
            groundOk = true;
        } catch (RuntimeException e) {
            System.err.println("Glacier: Bodendecke nicht verfügbar: " + e.getMessage());
            errorListener.accept("Die Bodendecke (com.dan.ground) lässt sich nicht starten.\n" + e.getMessage());
        }
        // Phase 4: Eis (Probe-Eisberge im Kanal vor dem Steg)
        try {
            iceGl.init(gl);
            placeIcebergs(gl);
            iceOk = true;
            System.out.println("Glacier: " + iceGl.info() + " · " + bergIce.describe());
        } catch (RuntimeException e) {
            System.err.println("Glacier: Eis nicht verfügbar: " + e.getMessage());
            errorListener.accept("Das Eis (com.dan.ice) lässt sich nicht starten.\n" + e.getMessage());
        }
        // Phase 5: der Gletscher im Hintergrund (auf der Grafikkarte, sobald er steht)
        if (gletscher == null && terrain.measured()) {
            Thread gt = new Thread(() -> {
                try {
                    setGletscher(new Gletscher(terrain));
                    System.out.println("Glacier: " + gletscher.glacier.describe() + String.format(Locale.GERMAN, " · gebaut in %.1f s", gletscher.buildMs / 1000));
                } catch (RuntimeException e) {
                    glacierFailed = true;
                    glacierLine = "!Gletscher: " + e.getMessage();
                    System.err.println("Glacier: Gletscher nicht gebaut: " + e);
                    e.printStackTrace();
                }
            }, "Gletscher");
            gt.setDaemon(true);
            gt.start();
        } else if (!terrain.measured()) glacierLine = "!Gletscher: ohne Höhendaten (Bühne) nicht gebaut";
        // Phase 3: Himmel, Wolken, Regen, Nebel, Blitz (jedes für sich; was nicht startet, fehlt, der Rest läuft)
        try {
            skyGl.init(gl);
            skyOk = true;
            System.out.println("Glacier: Himmel " + skyGl.info());
        } catch (RuntimeException e) {
            System.err.println("Glacier: Himmel nicht verfügbar, Verlauf aus Phase 1: " + e.getMessage());
            errorListener.accept("Der Himmel (com.dan.sky) lässt sich nicht starten, es bleibt ein einfacher Verlauf.\n" + e.getMessage());
        }
        if (skyOk) {
            try {
                cloudGl.init(gl);
                cloudsOk = true;
                if (wiring != null) wiring.cloudsDrawn = true;
                System.out.println("Glacier: Wolken " + cloudGl.info());
            } catch (RuntimeException e) {
                System.err.println("Glacier: Wolken nicht verfügbar: " + e.getMessage());
                errorListener.accept("Die Wolken (com.dan.cloud) lassen sich nicht starten, es bleibt der Schleier des Himmels.\n" + e.getMessage());
            }
        }
        try {
            rainGl.init(gl);
            rainOk = true;
        } catch (RuntimeException e) {
            System.err.println("Glacier: Regen nicht verfügbar: " + e.getMessage());
            errorListener.accept("Der Regen (com.dan.rain) lässt sich nicht starten.\n" + e.getMessage());
        }
        try {
            fogGl.init(gl);
            fogOk = true;
        } catch (RuntimeException e) {
            System.err.println("Glacier: Nebel nicht verfügbar: " + e.getMessage());
            errorListener.accept("Der Nebel (com.dan.fog) lässt sich nicht starten.\n" + e.getMessage());
        }
        try {
            lightningGl.init(gl);
            lightningOk = true;
        } catch (RuntimeException e) {
            System.err.println("Glacier: Gewitter nicht verfügbar: " + e.getMessage());
            errorListener.accept("Das Gewitter (com.dan.lightning) lässt sich nicht starten.\n" + e.getMessage());
        }
        // Phase 10: der Boden für die Stöße aus Kacheln (Spalten und Rinnen des Gletschers kosten je Abfrage Mikrosekunden,
        // beim Fallen fragt jede Zelle an der Oberfläche jedes Stücks in jedem Schritt)
        bruchGround = new com.dan.fracture.GroundCache(this::groundAt, 1.0);
        bruch.ground = bruchGround;
        // Phase 8: die Stücke schwimmen auf der Welle (Sea = Seespiegel + Welle; die Windsee bleibt außen vor)
        bruch.water = (x, z, t) -> {
            // Phase 13: im gestauten Brazo Rico und in der Rinne der Ruptura steht das Wasser höher
            com.dan.glacier.scene.Ruptura ru = ruptura;
            if (ru != null) {
                double w = ru.waterAt(x, z);
                if (!Double.isNaN(w)) return camera.waterLevel + w;
            }
            return camera.waterLevel + (waveReady ? wave.heightAt(x, z) : 0);
        };
        bruch.current = (x, z, out) -> { com.dan.glacier.scene.Ruptura ru = ruptura; return ru != null && ru.currentAt(x, z, out); };   // Phase 13
        bruch.quality = quality.fracture;
        bruch.asyncSolve = true;   // Phase 7: das Bild wartet nie auf die Spannung
        bruch.maxPieces = 100;     // darüber zerschellt nichts mehr (Phase 10: 100 statt 150)
        bruch.dissolveBelow = 25;  // Brocken unter 25 m³ gehen im Wasser im Treibeis auf
        bruch.sleepAfter = 12;     // Phase 10: nach 12 s ruhig auf dem Wasser an buoyancy (statt 30 s)
        bruch.forgetDryAfter = 90; // Phase 10: was auf dem Eis liegen bleibt, geht nach 90 s in ihm auf
        bruch.onCrack = klang::crack;   // Phase 12: Knacken, der Weg des Risses für den Donner
        bruch.onBreak = (id, vol, m) -> {
            klang.broke(vol);
            com.dan.glacier.scene.Kalbung k = kalbung;
            if (k != null) k.broke(id, vol);
            com.dan.glacier.scene.Ruptura ru = ruptura;
            if (ru != null) ru.broke(id, vol);   // Phase 13
        };
        bruch.onImpact = (x, y, z, vol, sp, water) -> {
            if (!water || vol <= 0.5) return;
            if (vol > 2) klang.sound(com.dan.glacier.wiring.SoundEvent.Kind.AUFSCHLAG, x, y, z, vol);   // Phase 12
            // Phase 8: das verdrängte Volumen ist die Quelle der Welle (aus dem Faden der Physik, sicher)
            if (waveReady) wave.addVolume(x, z, vol, 12);   // über die Breite eines Stücks verteilt
            com.dan.buoyancy.BrashField bf = brash;
            if (bf != null) bf.add(x, z, vol * 0.08, 20, bergs.sea);   // Phase 9: was beim Aufschlag zerbröselt
            impacts.add(new double[]{x, y, z, vol, sp});
        };
        // Phase 9: Eisberge. Wasser = Seespiegel + Welle, Grund = Gelände, die Gletscherfront ist eine Wand.
        bergs.sea = new com.dan.buoyancy.Sea() {
            @Override public double height(double x, double z) { return camera.waterLevel + (waveReady ? wave.heightAt(x, z) : 0); }
            @Override public double level() { return camera.waterLevel; }
            @Override public double bed(double x, double z) {
                Gletscher g = gletscher;
                if (g != null && g.glacier.covers(x, z)) return camera.waterLevel + 100;
                return terrain.height(x, z);
            }
        };
        bergs.onCapsize = b -> {   // Phase 12: ein kenternder Berg klatscht und ächzt
            double[] c = b.cgWorld();
            klang.sound(com.dan.glacier.wiring.SoundEvent.Kind.AUFSCHLAG, c[0], c[1], c[2], b.volume() * 0.25);
            klang.sound(com.dan.glacier.wiring.SoundEvent.Kind.AECHZEN, c[0], c[1], c[2], 0.6);
        };
        bruch.onSettle = p -> {
            // Phase 13: was in der Rinne der Ruptura zur Ruhe kommt, bleibt ein Stück (dort steht das Wasser höher als der See)
            com.dan.glacier.scene.Ruptura ru = ruptura;
            if (ru != null && ru.present()) { double[] c = p.position(); if (ru.dam.inBox(c[0], c[2]) || ru.inBrazo(c[0], c[2])) return; }
            // im Faden der Physik, mitten im Schritt: die Proben baut ein anderer Faden, übergeben wird danach
            bergWorker.submit(() -> {
                try {
                    IceMesh m = KalbungGl.pieceMesh(p);
                    com.dan.buoyancy.Berg b = new com.dan.buoyancy.Berg(m.pos, m.idx, (int) Math.max(40, Math.min(120, 30 + p.volume() / 300)));
                    b.tag = m;
                    handover.add(new Object[]{p, b});
                } catch (RuntimeException e) {
                    System.err.println("Glacier: Eisberg aus Stück " + p.id + ": " + e);
                }
            });
        };
        physik.add(new Physik.Aufgabe() {
            @Override public void step(double t, double dt) {
                com.dan.glacier.scene.Kalbung k = kalbung;
                if (k != null) k.step(dt);
                // Phase 13: die Ruptura vor dem Bruch; was in den Kanal schießt, ist eine Quelle der Welle
                com.dan.glacier.scene.Ruptura ru = ruptura;
                if (ru != null) {
                    ru.step(dt);
                    double[] q;
                    while ((q = ru.sources.poll()) != null) if (waveReady) wave.addVolumeOver(q[0], q[1], q[2], q[3], q[4]);
                }
                bruch.step(dt);
                // Phase 9: übergebene Stücke werden Eisberge (in der Lage, in der sie auf dem Wasser liegen)
                Object[] h;
                while ((h = handover.poll()) != null) {
                    com.dan.fracture.Piece p = (com.dan.fracture.Piece) h[0];
                    com.dan.buoyancy.Berg b = (com.dan.buoyancy.Berg) h[1];
                    double[] c = p.position();
                    b.placeMatrix(c[0], c[1], c[2], p.rotation(), p.velocity(), p.spin());
                    b.origin = "aus der Kalbung";
                    bruch.remove(p);
                    if (bergs.add(b) != null) handedOver++;
                }
                // Phase 10: die Schmelze rafft höchstens 60-fach – schneller kommt die Kalbung nicht nach (höchstens zwei
                // Abschnitte zugleich), und ohne Nachschub wäre der See nach einer Woche Zeitraffer leer
                bergs.step(dt, dt * Math.min(MELT_LAPSE_MAX, Math.max(0, clock.lapse)));
            }
            @Override public String name() { return "fracture"; }
        });
        try {
            sprayGl.init(gl);
            spray.sea = new com.dan.spray.Sea() {
                @Override public double height(double x, double z) { return ocean.waterLevel; }
                @Override public double[] velocity(double x, double z) { return new double[3]; }
                @Override public double level() { return ocean.waterLevel; }
            };
            sprayOk = true;
        } catch (RuntimeException e) {
            System.err.println("Glacier: Gischt nicht verfügbar: " + e.getMessage());
            errorListener.accept("Die Gischt (com.dan.spray) lässt sich nicht starten.\n" + e.getMessage());
        }
        leben.init(gl);   // Phase 10: nach der Gischt (die Pfähle sind ihre Körper)
        klang.stege(leben.stege);   // Phase 12: Geländer singen im Wind, Pfähle glucksen
        setTon(tonAn);
        try {
            lakeIceGl.init(gl);
            snowfallGl.init(gl);
            seasonGlOk = true;
        } catch (RuntimeException e) {
            System.err.println("Glacier: Eis und Schneefall (com.dan.season) nicht verfügbar: " + e.getMessage());
            errorListener.accept("Eis und Schneefall (com.dan.season) lassen sich nicht starten.\n" + e.getMessage());
        }
        physik.start();
        lastNanos = System.nanoTime();
        statsNanos = lastNanos;
        camera.snap();
        ready = true;
    }

    @Override
    public void reshape(GLAutoDrawable d, int x, int y, int w, int h) {
        surfW = Math.max(1, d.getSurfaceWidth());
        surfH = Math.max(1, d.getSurfaceHeight());
    }

    /** Uhr, Physik und Kamera um dt weiterrechnen (vom Bildlauf aufgerufen; Standbilder rufen es mit festem dt). */
    public void step(double dt) {
        regie.step(dt, regieCtx);   // Phase 14: fällige Stichworte des Regiebuchs zuerst (Uhr, Wetter, Ansicht)
        clock.step(dt);
        physik.advance(dt);
        welle.advance(dt);
        long t0 = System.nanoTime();
        double level = wiring != null ? wiring.waterLevel.level() : 0;
        // Phase 3: zuerst die Wetterregie, dann bekommen alle Packages ihre Eingänge
        if (wiring != null) {
            if (wiring.regie) wiring.feedWeather(dt);
            wiring.feed(ocean);
            wiring.feed(reed, dayOfYear());
            wiring.feed(sky, camera.eye[1] - level + Site.LAKE_LEVEL_ASL);
            wiring.feed(clouds);
        } else {
            sky.epochMillis = clock.epochMillis();
        }
        sky.figures = showFigures;
        Sky.State st = sky.step(dt);
        clouds.step(dt);
        // Wolkenschatten am Auge (für Halme, Nebel, Regen; Gelände und See lesen die Schattenkarte)
        cloudShade = cloudsOk && showClouds ? Clouds.shadow(clouds.snapshot(), camera.eye[0], camera.eye[2], keyDir(st)) : 1;
        adapt(st, dt);
        if (wiring != null) {
            wiring.feed(rain, clouds, clouds.snapshot(), cloudsOk && showClouds, camera.eye.clone());
            wiring.feed(fog, st.sunAlt);
            wiring.feed(lightning, clouds.snapshot(), cloudsOk && showClouds, camera.eye);
        }
        // Phase 11: das Jahr; was unter 0,5 bis 2,5 °C fällt, ist Schnee (am Auge, 0,65 K je 100 m kühler als am See)
        if (wiring != null) {
            com.dan.glacier.wiring.Season se = wiring.feedSeason(dt * Math.max(0, clock.lapse), st.sunAlt);
            com.dan.glacier.scene.Kalbung kb = kalbung;
            if (kb != null) kb.notchPerMinute = 0.96 * se.melt();   // im Jahresmittel 0,6 m je Minute Szenenzeit
            // Phase 13: die Ruptura folgt dem Jahr (Damm und Stau), solange kein Regiebuch läuft; steht der Damm, gibt sie den Stau
            com.dan.glacier.scene.Ruptura ru = ruptura;
            if (ru != null) {
                ru.follow(wiring.jahr.dammJahr, wiring.jahr.stau(), dayOfYear());
                if (ru.present()) wiring.waterLevel.stau = ru.stau();
                double[] m = ru.mouth();
                klang.flood(m[0] + m[2] * 15, level + 2, m[1] + m[3] * 15, ru.flow());
                double[] sp;
                while ((sp = ru.splashes.poll()) != null) if (sprayOk) { spray.impact(sp[0], level + sp[1], sp[2], sp[3], sp[4]); rupturaSplashes++; }
            }
            double tEye = wiring.airTempC - 0.0065 * Math.max(0, camera.eye[1] - level);
            double total = rain.rate, frac = com.dan.season.Year.rainFraction(tEye);
            snowRate = total * (1 - frac);
            rain.rate = total * frac;
        }
        if (!showRain) { rain.rate = 0; snowRate = 0; }
        rain.step(dt);
        fog.step(dt);
        stepLightning(dt);
        weatherCpuMs = weatherCpuMs * 0.95 + (System.nanoTime() - t0) / 1e6 * 0.05;
        ocean.step(dt);
        // Phase 7: Einschläge der Kalbung als Ringwellen des Sees (Vorgriff auf com.dan.wave) und als Gischt
        double[] im;
        while ((im = impacts.poll()) != null) {
            if (!waveReady) ocean.addImpulse(im[0], im[2], Math.min(1, Math.cbrt(im[3]) / 12));   // Ersatz, solange die Welle noch nicht steht
            if (sprayOk) spray.impact(im[0], ocean.waterLevel, im[2], im[3], im[4]);
            splashes++;
        }
        if (wiring != null) { bergs.windSpeed = wiring.wind.speed(); bergs.windFromDeg = wiring.wind.fromDeg(); }
        if (sprayOk) {
            if (wiring != null) { spray.windSpeed = wiring.wind.speed(); spray.windFromDeg = wiring.wind.fromDeg(); }
            spray.waveHeight = 0.5;
            spray.step(dt);
        }
        leben.step(dt, dt * Math.max(0, clock.lapse), st.sunAlt);   // Phase 10
        stepKlang(dt, level);   // Phase 12
        camera.groundLevel = groundAt(camera.eye[0], camera.eye[2]);
        camera.targetGround = groundAt(camera.tx, camera.tz);
        camera.waterLevel = ocean.waterLevel;
        camera.step(dt);
    }

    @Override
    public void display(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        if (!ready) {
            gl.glClearColor(0.04f, 0.07f, 0.10f, 1);
            gl.glClear(GL.GL_COLOR_BUFFER_BIT);
            return;
        }
        if (reloadShaders) {
            reloadShaders = false;
            try {
                for (Shader s : all) s.build(gl);
                System.out.println("Shader neu geladen.");
            } catch (RuntimeException e) {
                System.err.println(e.getMessage());
                errorListener.accept(e.getMessage());
            }
        }
        defaultFbo = d.getContext().getDefaultDrawFramebuffer();
        readMarks(gl);
        long now = System.nanoTime();
        double rawMs = (now - lastNanos) / 1e6;
        if (frame > 2 && rawMs > worstMs) worstMs = rawMs;
        double dt = Math.min(0.1, (now - lastNanos) / 1e9);
        lastNanos = now;
        if (!frozen) step(dt);
        sceneCpuMs = sceneCpuMs * 0.95 + (System.nanoTime() - now) / 1e6 * 0.05;   // Phase 13: davon die Szene (ohne GL)

        surfW = Math.max(1, d.getSurfaceWidth());
        surfH = Math.max(1, d.getSurfaceHeight());
        regulateScale(now);
        int w = Math.max(64, (int) Math.round(surfW * renderScale)), h = Math.max(64, (int) Math.round(surfH * renderScale));
        if (w != hdrW || h != hdrH) resizeTargets(gl, w, h);
        renderFrame(gl, w, h);
        if (photoRequest) { photoRequest = false; takePhoto(gl, surfW, surfH); }
        // Phase 14: Fotos eines Regiebuchs und das Blatt am Ende
        String cap = captureCaption;
        if (cap != null) {
            captureCaption = null;
            album.add(readDefault(gl, surfW, surfH));
            albumCaps.add(clock.timeLabel() + " · " + cap + "\nteuer: " + topPasses(3) + String.format(Locale.GERMAN, " · %.0f Bilder/s", fps));
        }
        if (regie.running()) { albumFps += fps; albumGpu += gpuMs; albumSamples++; }
        String sn = sheetName;
        if (sn != null) { sheetName = null; writeSheet(sn); }
        Consumer<BufferedImage> gr = grabRequest;
        if (gr != null) { grabRequest = null; try { gr.accept(readDefault(gl, surfW, surfH)); } catch (RuntimeException e) { System.err.println("Glacier: Bild nicht abgeholt: " + e); } }
        cpuMs = cpuMs * 0.95 + (System.nanoTime() - now) / 1e6 * 0.05;
        frame++;
        stats(now, w, h);
    }

    /** Ein ganzes Bild in Rechengröße w × h, dann ins Fenster. Bei stehender Szene ({@link #frozen}) malt das HUD gleich. */
    void renderFrame(GL4 gl, int w, int h) {
        mark(gl, 0);
        Sky.State st = skyState();
        if (adaptLum <= 0) adapt(st, 1);
        double pre = skyOk ? exposure() : 1;
        double[] sp = clock.sun();
        double[] sun = skyOk ? st.sunDir : Sun.direction(sp[0], sp[1]);
        float[] proj = Mat4.perspective(fovy(), w / (double) h, NEAR, FAR, reversedZ);
        float[] vp = Mat4.mul(proj, camera.view());
        float[] inv = Mat4.invert(vp);
        double level = wiring != null ? wiring.waterLevel.level() : 0;

        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
        gl.glViewport(0, 0, w, h);
        gl.glClearColor(0, 0, 0, 1);
        gl.glClearDepth(reversedZ ? 0 : 1);
        gl.glDepthMask(true);
        gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
        gl.glBindVertexArray(emptyVao);

        // 1 Himmel: com.dan.sky (Streuung, Sonne über Norden, Mond, Südhimmel), sonst der Verlauf aus Phase 1
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glDisable(GL.GL_CULL_FACE);
        if (skyOk) {
            skyGl.update(gl, st);
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
            gl.glViewport(0, 0, w, h);
            SkyGl.View sv = new SkyGl.View();
            sv.viewProj = vp;
            sv.invViewProj = inv;
            sv.reversedZ = reversedZ;
            sv.width = w;
            sv.height = h;
            sv.fovyDeg = fovy();
            sv.preExposure = pre;
            sv.time = clock.t();
            skyGl.draw(gl, st, sv);
            if (skyCheck.isEmpty() && frame > 30) skyCheck = skyGl.selfCheck(gl, st);
            gl.glBindVertexArray(emptyVao);
        } else {
            common(gl, skyShader, vp, inv, sun, level);
            gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        }
        mark(gl, 1);

        // Blitze übernehmen (neue Kanäle hochladen, Lichtpunkte für Wolken, See, Gelände)
        if (lightningOk) {
            lightningSnap = lightning.snapshot();
            lightningGl.update(gl, lightningSnap);
        }
        boolean flashNow = lightningOk && showLightning && lightningGl.active();
        double[] air = lightningAir();

        // 2 Wolken (mit Lentikularis) über den Himmel, Kanäle der Blitze, dann das Rundumbild (Spiegelung, Himmelslicht)
        boolean cloudsNow = cloudsOk && showClouds;
        Clouds.Snapshot cs = clouds.snapshot();
        CloudGl.Light cl = cloudsNow ? cloudLight(st, pre) : null;
        if (cloudsNow) {
            cloudGl.update(gl, cs, cl, camera.eye.clone());
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
            gl.glViewport(0, 0, w, h);
            CloudGl.View cv = new CloudGl.View();
            cv.viewProj = vp;
            cv.invViewProj = inv;
            cv.eye = camera.eye.clone();
            cv.reversedZ = reversedZ;
            cv.width = w;
            cv.height = h;
            cloudGl.draw(gl, cs, cl, cv);
            if (cloudCheck.isEmpty() && frame > 40) cloudCheck = cloudGl.selfCheck(gl, cs, cl);
            gl.glBindVertexArray(emptyVao);
        }
        if (flashNow) {
            lightningGl.drawChannels(gl, lightningView(vp, inv, w, h, pre, air));
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
            gl.glViewport(0, 0, w, h);
            gl.glBindVertexArray(emptyVao);
        }
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, panoFbo);
        gl.glViewport(0, 0, PANO_W, PANO_H);
        if (skyOk) {
            skyGl.drawPanorama(gl, st, pre);
            if (cloudsNow) cloudGl.drawPanorama(gl, cs, cl, camera.eye.clone());
            if (flashNow) lightningGl.drawPanorama(gl, PANO_W, PANO_H, camera.eye.clone(), pre, air[0], air[1], cloudsNow ? clouds.type.base : lightning.cloudBase);
            gl.glBindVertexArray(emptyVao);
        } else {
            common(gl, skyPano, vp, inv, sun, level);
            gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        }
        if (rainOk) {
            rainGl.update(gl, rain.snapshot());
            if (rainCheck.isEmpty() && rain.rate > 0 && frame > 60) rainCheck = rainGl.selfCheck(gl, rain.snapshot());
        }
        if (flashNow && lightningCheck.isEmpty()) {
            for (Bolt b : lightningSnap.bolts())
                if (b.kind == Bolt.Kind.CLOUD_GROUND && lightningSnap.time() > b.t0 + b.leaderTime) {
                    lightningCheck = lightningGl.selfCheck(gl, lightningSnap);
                    lightningGl.update(gl, lightningSnap);
                    break;
                }
        }
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
        gl.glViewport(0, 0, w, h);
        mark(gl, 2);

        // 3 Wald: Bäume für das Bild wählen, Instanzen hochladen, Sonnenschatten der Bäume um den Blickpunkt
        double[] to = wiring != null ? wiring.wind.toVector() : new double[]{1, 0, 0};
        double windSpeed = wiring != null ? (wiring.regie ? wiring.gustNow : wiring.wind.speed()) : 6.7;
        boolean forestNow = forestOk && showForest;
        sunShadow.matrix = null;
        if (forestNow) {
            forestGl.setSeason(Wiring.northernDay(dayOfYear()), wiring != null ? (float) wiring.jahr.forestSnow() : 0);
            forestGl.setWind(to[0], to[2], windSpeed, wiring != null ? wiring.wind.gustiness() : 0.3);
            forest.update(camera.eye, vp, 24);
            forestGl.update(gl, forest);
        }
        boolean forestReady = forestNow && forestGl.ready();
        if (showSunShadow && sun[1] > 0.03 && forestReady) {
            sunShadow.ensure(gl, quality.shadowSize);
            double[] c = shadowCenter();
            sunShadow.matrix = sunShadow.fit(sun, c[0], c[1], c[2]);
            sunShadow.begin(gl, reversedZ);
            forestGl.drawShadow(gl, sunShadow.matrix, clock.t(), 3);
            sunShadow.end(gl, reversedZ);
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
            gl.glViewport(0, 0, w, h);
        }
        mark(gl, 3);

        // 4 Gelände (Wolkenschatten aus der Karte, Mond, Blitzlicht; ohne com.dan.sky der Verlauf aus Phase 2)
        double[] sl = sunLight(sp[0]), zen = skyZenith(sp[0]), hor = skyHorizon(sp[0]);
        double[][] L = ambientLight(st, pre);
        double[][] fla = flashLight(skyOk ? pre : 1);
        double wet = Math.min(1, rain.rate * (showRain ? 1 : 0) / 3);
        gl.glDepthMask(true);
        gl.glDisable(GL.GL_BLEND);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthFunc(reversedZ ? GL.GL_GREATER : GL.GL_LESS);
        common(gl, terrainShader, vp, inv, sun, level);
        lod.bind(gl, terrainShader);
        gl.glActiveTexture(GL.GL_TEXTURE10);
        gl.glBindTexture(GL.GL_TEXTURE_2D, coverTex);
        gl.glActiveTexture(GL.GL_TEXTURE12);
        gl.glBindTexture(GL.GL_TEXTURE_2D, cloudsNow ? cloudGl.shadowTexture() : 0);
        gl.glActiveTexture(GL.GL_TEXTURE13);
        gl.glBindTexture(GL.GL_TEXTURE_2D, sunShadow.matrix != null ? sunShadow.tex : 0);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        terrainShader.set(gl, "uCover", 10);
        terrainShader.set(gl, "uCoverRect", (float) Terrain.X0, (float) Terrain.Z0, (float) (Terrain.N * Terrain.CELL), 1f);
        terrainShader.set(gl, "uWood", (float) (forest.radius * forest.thinFrom), (float) forest.radius, forestReady ? 1f : 0f);
        terrainShader.set(gl, "uSunRgb", sl);
        terrainShader.set(gl, "uSunAmb", zen);
        terrainShader.set(gl, "uSkyHor", hor);
        terrainShader.set(gl, "uSnowLine", (float) snowLineNow());   // Phase 11: nach der Jahreszeit
        terrainShader.set(gl, "uMarks", showMarks ? 1 : 0);
        terrainShader.set(gl, "uCloudShadow", 12);
        double[] sr = cloudsNow ? cloudGl.shadowRect() : new double[]{0, 0, 1};
        terrainShader.set(gl, "uCloudRect", (float) sr[0], (float) sr[1], (float) Math.max(1, sr[2]), cloudsNow ? 1f : 0f);
        terrainShader.set(gl, "uFlashDir", fla != null ? fla[0] : new double[]{0, 1, 0});
        terrainShader.set(gl, "uFlashRgb", fla != null ? fla[1] : new double[]{0, 0, 0});
        terrainShader.set(gl, "uWet", (float) wet);
        // Phase 8: nasser Saum, wo die Welle aufgelaufen ist
        boolean wv = waveReady && showWave && waveGl.wetTexture(0) != 0;
        gl.glActiveTexture(GL.GL_TEXTURE14);
        gl.glBindTexture(GL.GL_TEXTURE_2D, wv ? waveGl.wetTexture(0) : 0);
        gl.glActiveTexture(GL.GL_TEXTURE15);
        gl.glBindTexture(GL.GL_TEXTURE_2D, wv ? waveGl.wetTexture(1) : 0);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        terrainShader.set(gl, "uWaveWet0", 14);
        terrainShader.set(gl, "uWaveWet1", 15);
        double[] w0 = wv ? waveGl.rect(0) : new double[]{0, 0, 1, 0}, w1 = wv ? waveGl.rect(1) : new double[]{0, 0, 1, 0};
        terrainShader.set(gl, "uWaveR0", (float) w0[0], (float) w0[1], (float) w0[2], wv ? (float) w0[3] : 0f);
        terrainShader.set(gl, "uWaveR1", (float) w1[0], (float) w1[1], (float) w1[2], wv && waveGl.wetTexture(1) != 0 ? (float) w1[3] : 0f);
        terrainShader.set(gl, "uWaveTime", waveGl.time());
        terrainShader.set(gl, "uSunShadow", 13);
        terrainShader.mat(gl, "uSunShadowM", sunShadow.matrix != null ? sunShadow.matrix : Mat4.identity());
        terrainShader.set(gl, "uSunShadowOn", sunShadow.matrix != null ? 1 : 0);
        lod.draw(gl, terrainShader, camera.eye, vp);
        gl.glBindVertexArray(emptyVao);
        mark(gl, 4);

        // 5 Wald zeichnen (Licht wie die Halme, dazu Sonnen- und Wolkenschatten)
        if (forestReady) {
            ForestGl.View fv = new ForestGl.View();
            fv.viewProj = vp;
            fv.eye = camera.eye.clone();
            fv.reversedZ = reversedZ;
            ForestGl.Light fl = new ForestGl.Light();
            fl.sunDir = sun;
            fl.fogColor = L[3];
            fl.skyColor = L[2];
            fl.wetness = wet;
            if (skyOk) {
                fl.skyTexture = panoTex;
                fl.sunColor = scale(st.sunColor, pre);
                fl.moonDir = st.moonDir;
                fl.moonColor = scale(st.moonColor, pre);
                fl.skyWeight = 1.15;
                fl.fogDistance = fogDistance(st);
                if (cloudsNow) {
                    fl.shadowTexture = cloudGl.shadowTexture();
                    fl.shadowRect = cloudGl.shadowRect();
                }
            } else {
                fl.sunColor = sl;
                fl.skyColor = zen;
                fl.fogColor = hor;
                fl.fogDistance = FOG_DISTANCE;
            }
            if (sunShadow.matrix != null) {
                fl.sunShadowTexture = sunShadow.tex;
                fl.sunShadowMatrix = sunShadow.matrix;
                fl.sunShadowBias = 0.0006;
            }
            forestGl.draw(gl, fv, fl, clock.t());
            gl.glBindVertexArray(emptyVao);
        }
        mark(gl, 5);

        // 6 Bodendecke um das Auge (nur zu Fuß)
        boolean groundNow = groundOk && showGround && camera.eye[1] - terrain.height(camera.eye[0], camera.eye[2]) < 25;
        if (groundOk) {
            meadow.setSeason(Wiring.northernDay(dayOfYear()), wiring != null ? (float) wiring.jahr.groundSnow() : 0);
            meadow.wind.speed = (float) windSpeed;
            meadow.wind.direction = (float) Math.toDegrees(Math.atan2(to[2], to[0]));
            groundGl.update(gl, meadow, (float) (clock.t() % 3600), camera.eye[0], camera.eye[2], !groundNow);
        }
        if (groundNow) {
            GroundGl.View gv = new GroundGl.View();
            gv.viewProj = vp;
            gv.eye = camera.eye.clone();
            gv.reversedZ = reversedZ;
            GroundGl.Light gL = new GroundGl.Light();
            gL.sunDir = sun;
            gL.fogColor = L[3];
            gL.skyColor = L[2];
            if (skyOk) {
                gL.skyTexture = panoTex;
                gL.sunColor = scale(st.sunColor, pre);
                gL.moonDir = st.moonDir;
                gL.moonColor = scale(st.moonColor, pre);
                gL.skyWeight = 1.2;
                gL.fogDistance = fogDistance(st);
                if (cloudsNow) {
                    gL.shadowTexture = cloudGl.shadowTexture();
                    gL.shadowRect = cloudGl.shadowRect();
                }
            } else {
                gL.sunColor = sl;
                gL.skyColor = zen;
                gL.fogColor = hor;
                gL.fogDistance = FOG_DISTANCE;
            }
            if (sunShadow.matrix != null) {
                gL.sunShadowTexture = sunShadow.tex;
                gL.sunShadowMatrix = sunShadow.matrix;
            }
            groundGl.draw(gl, gv, gL);
            gl.glBindVertexArray(emptyVao);
        }
        mark(gl, 6);

        // 7 Eis: der Gletscher (Kacheln, Front) und die Probe-Eisberge (vor dem See gezeichnet, er deckt ihren Fuß)
        if (iceOk && gletscher != null && glacierGl == null && !glacierFailed) startGlacierGl(gl);
        List<IceGl.Instance> iceList = new ArrayList<>();
        if (glacierOk && showGlacier) {
            double adv = Wiring.glacierAdvance(dayOfYear());
            glacierGl.setAdvance(adv);
            if (wiring != null) glacierGl.surfaceIce.snow = wiring.jahr.glacierSnow();   // Phase 11: Schnee auf der Zunge
            if (adv != groundAdvance || showGlacier != groundGlacier) {
                groundAdvance = adv;
                groundGlacier = showGlacier;
                com.dan.fracture.GroundCache gc = bruchGround;
                if (gc != null) gc.clear();
            }
            iceList.addAll(glacierGl.instances(gl, camera.eye, vp));
        }
        if (iceOk && showIce) iceList.addAll(icebergs);
        // Phase 7: Abschnitte der Front als Zellen und die Stücke; setzt auch die Ausschnitte in der Wand
        if (kalbungGl != null) kalbungGl.showBergs = showBergs;
        if (iceOk && kalbungGl != null && showKalbung && glacierOk && showGlacier) iceList.addAll(kalbungGl.instances(gl));
        else if (iceOk) iceGl.setCuts(null);
        if (iceOk && !iceList.isEmpty()) {
            for (int i = 0; i < icebergs.size(); i++) {
                double[] pl = bergPlaces.get(i);
                icebergs.get(i).ice = bergIce;
                icebergs.get(i).place(pl[0], ocean.waterLevel + 0.04 * Math.sin(clock.t() * 0.4 + i * 2.1), pl[1], pl[2]);
            }
            bergIce.melt = Math.max(0.7, wet);
            IceGl.View iv = new IceGl.View();
            iv.viewProj = vp;
            iv.eye = camera.eye.clone();
            iv.reversedZ = reversedZ;
            iv.width = w;
            iv.height = h;
            IceGl.Light il = new IceGl.Light();
            il.sunDir = sun;
            il.waterLevel = ocean.waterLevel;
            il.waterAbsorption = new double[]{0.5, 0.16, 0.16};
            il.waterColor = new double[]{Ocean.LAGO_SSS[0] * 0.5, Ocean.LAGO_SSS[1] * 0.5, Ocean.LAGO_SSS[2] * 0.5};
            if (skyOk) {
                il.sunColor = scale(st.sunColor, pre * cloudShade);
                il.moonDir = st.moonDir;
                il.moonColor = scale(st.moonColor, pre * cloudShade);
                il.skyTop = L[2];
                il.skyHorizon = L[3];
                il.fogColor = L[3];
                il.skyTexture = panoTex;
                il.fogDistance = fogDistance(st);
            } else {
                il.sunColor = sl;
                il.skyTop = zen;
                il.skyHorizon = hor;
                il.fogColor = hor;
                il.fogDistance = FOG_DISTANCE;
            }
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
            gl.glViewport(0, 0, w, h);
            gl.glDepthMask(true);
            gl.glDisable(GL.GL_BLEND);
            gl.glEnable(GL.GL_DEPTH_TEST);
            gl.glDepthFunc(reversedZ ? GL.GL_GREATER : GL.GL_LESS);
            iceGl.draw(gl, iceList, iv, il);
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
            gl.glViewport(0, 0, w, h);
            gl.glDepthFunc(reversedZ ? GL.GL_GREATER : GL.GL_LESS);
            gl.glBindVertexArray(emptyVao);
        }
        if (glacierOk && showGlacier && showMelt && meltCount > 0) drawMelt(gl, vp, inv, sun, level, sl, zen, hor);
        mark(gl, 7);

        // 7 See: rechnen (Compute), dann zeichnen; das Gelände ist sein Grund, die Wolken werfen Schatten
        if (oceanOk && showWater) {
            oceanGl.update(gl, ocean);
            if (oceanCheck.isEmpty() && frame > 30) oceanCheck = oceanGl.selfCheck(gl, ocean);
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
            gl.glViewport(0, 0, w, h);
            OceanGl.View ov = new OceanGl.View();
            ov.viewProj = vp;
            ov.eye = camera.eye.clone();
            ov.reversedZ = reversedZ;
            OceanGl.Light ol = new OceanGl.Light();
            ol.sunDir = sun;
            ol.skyTexture = panoTex;
            if (skyOk) {
                ol.sunColor = scale(st.sunColor, pre);
                ol.moonDir = st.moonDir;
                ol.moonColor = scale(st.moonColor, pre);
                ol.fogDistance = fogDistance(st);
                if (cloudsNow) {
                    ol.shadowTexture = cloudGl.shadowTexture();
                    ol.shadowRect = cloudGl.shadowRect();
                }
            } else {
                ol.sunColor = sl;
                ol.fogDistance = FOG_DISTANCE;
            }
            if (rainOk && showRain && rain.rate > 0) {
                ol.detailTexture = rainGl.rippleTexture();
                ol.detailTile = rainGl.rippleTile();
                ol.detailStrength = rainGl.rippleStrength() * Math.min(1, rainStrength);
                ol.detailRange = 60;
            }
            if (flashNow) {
                int n = lightningGl.lightCount();
                ol.flashCount = n;
                for (int i = 0; i < n; i++) {
                    double[] lp = lightningGl.lightPos(i), lr = lightningGl.lightRgb(i);
                    for (int k = 0; k < 4; k++) ol.flashPos[i * 4 + k] = (float) lp[k];
                    for (int k = 0; k < 3; k++) ol.flashRgb[i * 4 + k] = (float) (lr[k] * (skyOk ? pre : 1));
                }
                ol.pointSigma = air[0];
            }
            ol.waterColor = Ocean.LAGO_WATER;
            ol.sssColor = Ocean.LAGO_SSS;
            ol.bedTexture = lod.heightTex;
            ol.bedRect = new double[]{Terrain.X0, Terrain.Z0, Terrain.N * Terrain.CELL};
            ol.bedScale = Terrain.HMAX - Terrain.HMIN;
            ol.bedOffset = Terrain.HMIN;
            ol.bedTurbidity = 0.35;
            ol.bedColor = new double[]{0.15, 0.15, 0.14};
            // Phase 9: Treibeis und Brandungsringe um die Eisberge
            com.dan.buoyancy.BrashField bf = brash;
            if (bf != null && showBergs) {
                brashGl.update(gl, bf, clock.t());
                ol.brashTexture = brashGl.texture();
                ol.brashRect = new double[]{bf.x0, bf.z0, bf.nx * bf.cell, bf.nz * bf.cell};
            }
            if (clock.t() - obstacleClock > 0.5 || clock.t() < obstacleClock) {
                obstacleClock = clock.t();
                java.util.List<double[]> circles = new java.util.ArrayList<>();
                if (showBergs)
                    for (com.dan.buoyancy.Berg b : bergs.bergs) {
                        double[] c = b.cgWorld();
                        if (Math.hypot(c[0] - camera.eye[0], c[2] - camera.eye[2]) < 1500) circles.add(new double[]{c[0], c[2], b.waterlineRadius(camera.waterLevel)});
                    }
                ocean.obstacleField = circles.isEmpty() ? null
                        : com.dan.ocean.ObstacleField.of(circles, camera.eye[0] - 1500, camera.eye[2] - 1500, 3000, 3000, 4, 12);
            }
            // Phase 8: die lange Welle aus com.dan.wave
            if (waveReady && showWave) {
                waveGl.update(gl, wave);
                ol.longTexture0 = waveGl.heightTexture(0);
                ol.longTexture1 = waveGl.heightTexture(1);
                ol.longRect0 = waveGl.rect(0);
                ol.longRect1 = waveGl.rect(1);
            }
            gl.glDepthFunc(reversedZ ? GL.GL_GREATER : GL.GL_LESS);
            oceanGl.draw(gl, ocean, ov, ol);
            gl.glBindVertexArray(emptyVao);
            // Phase 13: der gestaute Brazo Rico als zweiter Spiegel, nur in seiner Maske; ohne lange Welle und Treibeis
            com.dan.glacier.scene.Ruptura ru = ruptura;
            if (ru != null && showRuptura) {
                rupturaGl.update(gl, ru, camera.waterLevel);
                if (ru.raised() && rupturaGl.maskTexture() != 0) {
                    ol.level = camera.waterLevel + ru.stau();
                    ol.maskTexture = rupturaGl.maskTexture();
                    ol.maskRect = RupturaGl.maskRect();
                    ol.maskMode = 1;
                    ol.longTexture0 = ol.longTexture1 = 0;
                    ol.brashTexture = 0;
                    gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
                    gl.glViewport(0, 0, w, h);
                    oceanGl.draw(gl, ocean, ov, ol);
                    gl.glBindVertexArray(emptyVao);
                }
            }
            drawLakeIce(gl, vp, sun, st, pre, L, sl, hor, w, h);   // Phase 11
            if (ru != null && showRuptura && rupturaGl.flowing()) drawFlut(gl, vp, inv, sun, level, sl, zen, hor);   // Phase 13
        }
        mark(gl, 8);

        // 8 Halme um die Kamera: Steppe und Ufergras
        if (reedOk && showReed) {
            reedGl.update(gl, reed, camera.eye[0], camera.eye[2], vegetation, (x, z) -> terrain.height(x, z));
            ReedGl.View rv = new ReedGl.View();
            rv.viewProj = vp;
            rv.eye = camera.eye.clone();
            rv.reversedZ = reversedZ;
            ReedGl.Light rl = new ReedGl.Light();
            rl.sunDir = sun;
            rl.fogColor = L[3];
            if (skyOk) {
                // Sonne und Mond ohne den Schatten am Auge: jeder Halm liest die Wolkenschatten selbst
                rl.sunColor = scale(st.sunColor, pre);
                rl.moonDir = st.moonDir;
                rl.moonColor = scale(st.moonColor, pre);
                rl.skyColor = L[2];
                rl.skyWeight = 1.3;
                rl.fogDistance = fogDistance(st);
                if (cloudsNow) {
                    rl.shadowTexture = cloudGl.shadowTexture();
                    rl.shadowRect = cloudGl.shadowRect();
                }
            } else {
                rl.sunColor = sl;
                rl.skyColor = zen;
                rl.fogColor = hor;
                rl.fogDistance = FOG_DISTANCE;
            }
            reedGl.draw(gl, reed, rv, rl, clock.t());
            gl.glBindVertexArray(emptyVao);
        }
        // Phase 10: Stege, Katamaran, Vögel (nach dem See, der den Rumpf unter Wasser verdeckt)
        drawLeben(gl, vp, sun, st, pre, L, sl, zen, hor, w, h);
        mark(gl, 9);

        // Phase 9: unter Wasser (Ansicht „Unter Wasser“): trübes, türkises Wasser über allem
        if (camera.eye[1] < camera.waterLevel + (waveReady ? wave.heightAt(camera.eye[0], camera.eye[2]) : 0)) {
            copyDepth(gl, w, h);
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
            gl.glViewport(0, 0, w, h);
            gl.glDisable(GL.GL_DEPTH_TEST);
            gl.glDepthMask(false);
            gl.glEnable(GL.GL_BLEND);
            gl.glBlendFunc(GL.GL_ONE, GL.GL_SRC_ALPHA);   // Phase 10: Ziel · Licht von oben · (1 − Nebel) + Nebel
            underwaterShader.use(gl);
            underwaterShader.mat(gl, "uInvVP", inv);
            underwaterShader.set(gl, "uCam", camera.eye);
            underwaterShader.set(gl, "uLevel", (float) camera.waterLevel);
            underwaterShader.set(gl, "uReversed", reversedZ ? 1 : 0);
            underwaterShader.set(gl, "uVis", UNDERWATER_VIS);
            underwaterShader.set(gl, "uAtten", 18f);
            double[] up = L[2], sunL = L[0];
            double sy = Math.max(0, skyOk ? st.sunDir[1] : 0.5), e0 = up[0] * 3.5 + sunL[0] * sy, e1 = up[1] * 3.5 + sunL[1] * sy, e2 = up[2] * 3.5 + sunL[2] * sy;
            underwaterShader.set(gl, "uWaterLight", (float) (Ocean.LAGO_SSS[0] * e0 * 0.09), (float) (Ocean.LAGO_SSS[1] * e1 * 0.09), (float) (Ocean.LAGO_SSS[2] * e2 * 0.09));
            gl.glActiveTexture(GL.GL_TEXTURE0);
            gl.glBindTexture(GL.GL_TEXTURE_2D, depthCopyTex);
            underwaterShader.set(gl, "uDepth", 0);
            gl.glBindVertexArray(emptyVao);
            gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
            gl.glDisable(GL.GL_BLEND);
            gl.glDepthMask(true);
            gl.glEnable(GL.GL_DEPTH_TEST);
        }

        // 9 Regen über die Szene (Tiefe als Kopie)
        boolean rainNow = rainOk && showRain && rain.rate > 0;
        boolean fogNow = fogOk && showFog && fog.snapshot().any();
        boolean copied = false;
        if (rainNow || fogNow || flashNow) { copyDepth(gl, w, h); copied = true; }
        if (rainNow) drawRain(gl, st, vp, inv, w, h, pre, L);
        if (seasonGlOk && snowRate > 0.01) drawSnowfall(gl, vp, sun, st, pre, L, sl, hor, w, h);   // Phase 11
        // Phase 7: Gischt der Kalbung
        if (sprayOk && !spray.parcels().isEmpty()) {
            if (!copied) { copyDepth(gl, w, h); copied = true; }
            drawSpray(gl, st, vp, w, h, pre, L);
        }
        mark(gl, 10);

        // 10 Seenebel bis zur Szenentiefe
        if (fogNow) drawFog(gl, st, inv, w, h, L);
        mark(gl, 11);

        // 11 Blitz: das Leuchten der Luft um die Kanäle
        if (flashNow && copied) {
            LightningGl.View lv = lightningView(vp, inv, w, h, pre, air);
            lv.depthTexture = depthCopyTex;
            lightningGl.drawGlow(gl, lv);
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
            gl.glViewport(0, 0, w, h);
            gl.glBindVertexArray(emptyVao);
        }
        gl.glDisable(GL.GL_DEPTH_TEST);
        mark(gl, 12);

        // 12 Nachbearbeitung ins Fenster (mit com.dan.sky ist das Bild schon vorbelichtet)
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, defaultFbo);
        gl.glViewport(0, 0, surfW, surfH);
        post.use(gl);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, hdrTex);
        post.set(gl, "uHdr", 0);
        post.set(gl, "uExposure", skyOk ? 1f : (float) exposure());
        post.set(gl, "uScotopic", (float) (skyOk ? scotopic(st) : 0));
        gl.glBindVertexArray(emptyVao);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        mark(gl, 13);

        // 13 HUD
        if (showHud) {
            hud.refresh(gl, 250, this::paintHud, frozen);
            hud.draw(gl, overlay, emptyVao, 12, Math.max(0, surfH - hud.height - 12), surfW, surfH);
        }
        gl.glBindVertexArray(0);
        gl.glUseProgram(0);
        mark(gl, 14);
    }

    // ------------------------------------------------------------------ Phase 3: Licht, Auge, Gewitter

    /**
     * Phase 6: wie viele der zwölf Messstrahlen (dasselbe Raster wie {@code Sky.State.viewLuminance}) unter der Kimm
     * auf Eis fallen, als Anteil 0 bis 1. Jeder Strahl läuft in wachsenden Schritten bis 12 km gegen den Boden.
     */
    double iceInView() {
        Gletscher g = gletscher;
        double[] f = camera.fwd, up = {0, 1, 0};
        double[] r = {f[1] * up[2] - f[2] * up[1], f[2] * up[0] - f[0] * up[2], f[0] * up[1] - f[1] * up[0]};
        double rl = Math.max(1e-9, Math.sqrt(r[0] * r[0] + r[1] * r[1] + r[2] * r[2]));
        r[0] /= rl; r[1] /= rl; r[2] /= rl;
        double[] u = {r[1] * f[2] - r[2] * f[1], r[2] * f[0] - r[0] * f[2], r[0] * f[1] - r[1] * f[0]};
        double ty = Math.tan(Math.toRadians(fovy()) / 2), tx = ty * surfW / (double) Math.max(1, surfH);
        int ice = 0;
        for (int j = 0; j < 3; j++)
            for (int i = 0; i < 4; i++) {
                double sx = ((i + 0.5) / 4 * 2 - 1) * tx * 0.8, sy = ((j + 0.5) / 3 * 2 - 1) * ty * 0.8;
                double dx = f[0] + r[0] * sx + u[0] * sy, dy = f[1] + r[1] * sx + u[1] * sy, dz = f[2] + r[2] * sx + u[2] * sy;
                double dl = Math.sqrt(dx * dx + dy * dy + dz * dz);
                dx /= dl; dy /= dl; dz /= dl;
                if (dy >= 0) continue;
                double t = 1, step = 2;
                while (t < 12000) {
                    double x = camera.eye[0] + dx * t, y = camera.eye[1] + dy * t, z = camera.eye[2] + dz * t;
                    if (y <= Math.max(groundAt(x, z), camera.waterLevel)) {
                        boolean onIce = (g != null && showGlacier && g.glacier.covers(x, z)) || terrain.sourceAt(x, z) == Terrain.EIS;
                        if (onIce && y > camera.waterLevel + 0.5) ice++;
                        break;
                    }
                    t += step;
                    step = Math.min(120, step * 1.25);
                }
            }
        return ice / 12.0;
    }

    /**
     * Augenanpassung (wie Haithabu): alle 0,2 s die Leuchtdichte im Blickfeld messen (auf dem Prozessor), mit der des
     * ganzen Himmels mitteln und über eine Sekunde glätten (logarithmisch, wie das Auge). Unter Wolken dunkler.
     */
    private void adapt(Sky.State st, double dt) {
        viewTimer -= dt;
        if (viewLum <= 0 || viewTimer <= 0) {
            viewLum = st.viewLuminance(camera.fwd.clone(), new double[]{0, 1, 0}, fovy(), surfW / (double) Math.max(1, surfH));
            iceInView = iceInView();
            // Phase 6 (Befund Status_Phase5b): com.dan.sky rechnet unter der Kimm mit einem Siebtel des gespiegelten
            // Himmels (Wasser). Eis und Schnee werfen rund fünfmal so viel zurück; wo der Blick auf Eis fällt, zählt es so
            viewLum *= Math.pow(ICE_GAIN, iceInView);
            viewTimer = 0.2;
        }
        double target = Math.sqrt(Math.max(1e-14, st.adaptation) * Math.max(1e-14, viewLum)) * (cloudsOk && showClouds ? clouds.brightnessFactor() : 1);
        if (adaptLum <= 0 || frozen || dt > 0.5) adaptLum = target;
        else adaptLum = Math.exp(Math.log(adaptLum) + (Math.log(target) - Math.log(adaptLum)) * Math.min(1, dt / 1.0));
    }

    /** Das Gewitter in Szenenzeit weiter, Sichtbarkeit neu nach großen Sprüngen des Auges, Nachgeben der Belichtung. */
    private void stepLightning(double dt) {
        double sceneDt = dt * Math.max(0, clock.lapse);
        int before = lightning.flashes();
        lightning.step(Math.min(sceneDt, 1.0));
        double[] e = camera.eye;
        if (lightningSeenFrom == null || Math.hypot(e[0] - lightningSeenFrom[0], e[2] - lightningSeenFrom[2]) > 300 || Math.abs(e[1] - lightningSeenFrom[1]) > 100) {
            lightning.updateVisibility();
            lightningGl.invalidate();
            lightningSeenFrom = e.clone();
        }
        if (lightning.flashes() != before && !lightning.bolts().isEmpty()) {
            Bolt nb = lightning.bolts().get(lightning.bolts().size() - 1);
            lastBoltText = boltText(nb);
            klang.sound(com.dan.glacier.wiring.SoundEvent.Kind.DONNER, nb.hitX, 0, nb.hitZ, 1);   // Phase 12
        }
        double base = exposure() * flashGive;
        double lf = showLightning ? lightning.snapshot().flashLuminance(e[0], e[1], e[2], lightning.cloudBase) : 0;
        double give = 1 + base * lf / 1.5;
        flashGive = frozen ? give : Math.max(give, flashGive * Math.exp(-Math.max(0, dt) / 0.25));
    }

    /** „Erdblitz 3,4 km NW, 31 kA“ */
    private String boltText(Bolt b) {
        double[] e = camera.eye;
        double d = Math.hypot(b.hitX - e[0], b.hitZ - e[2]), az = (Math.toDegrees(Math.atan2(b.hitX - e[0], -(b.hitZ - e[2]))) + 360) % 360;
        String[] dirs = {"N", "NO", "O", "SO", "S", "SW", "W", "NW"};
        String kind = switch (b.kind) {
            case CLOUD_GROUND -> "Erdblitz";
            case INTRA_CLOUD -> "Wolkenblitz";
            case SPIDER -> "Spinnenblitz";
            case SHEET -> "Wetterleuchten";
        };
        double ka = 0;
        for (double k : b.strokeKA) ka = Math.max(ka, k);
        return String.format(Locale.GERMAN, "%s %.1f km %s, %.0f kA", kind, d / 1000, dirs[(int) Math.round(az / 45) % 8], ka);
    }

    /** Auslöschung und Streuung der Luft für das Licht der Blitze (Dunst, Regen, Nebel am Auge), 1/m. */
    private double[] lightningAir() {
        double haze = wiring != null ? wiring.hazePerM() : 4e-5;
        double[] e = camera.eye;
        double r = rainOk && showRain ? rain.scatter(e[0], e[1], e[2]) * 3e-3 : 0;
        double f = fogOk && showFog ? fog.density(e[0], e[1], e[2]) : 0;
        return new double[]{haze + r + f, haze * 0.9 + r * 0.98 + f};
    }

    /** Einen Blitz von Hand auslösen (Bedienfeld): Erdblitz in 1,5 bis 4 km in Blickrichtung. */
    public Bolt triggerLightning(Bolt.Kind kind) {
        if (wiring != null) wiring.feed(lightning, clouds.snapshot(), cloudsOk && showClouds, camera.eye);
        double[] e = camera.eye, f = camera.fwd;
        double h = Math.hypot(f[0], f[2]), fx = h > 1e-6 ? f[0] / h : 0, fz = h > 1e-6 ? f[2] / h : -1, r = 1500 + Math.random() * 2500;
        return kind == Bolt.Kind.CLOUD_GROUND ? lightning.strike(e[0] + fx * r, e[2] + fz * r) : lightning.discharge(kind, e[0] + fx * r, e[2] + fz * r);
    }

    static boolean sunIsKey(Sky.State st) { return st.sunAlt > -4; }

    static double[] keyDir(Sky.State st) { return sunIsKey(st) ? st.sunDirTrue : st.moonDirTrue; }

    /** Licht für die Wolken aus dem Himmelszustand, vorbelichtet (wie Haithabu); von unten See, Wald und Eis. */
    CloudGl.Light cloudLight(Sky.State st, double pre) {
        CloudGl.Light l = new CloudGl.Light();
        boolean sun = sunIsKey(st);
        l.dir = keyDir(st);
        for (int i = 0; i < 8; i++) l.colorAt[i] = scale(sun ? st.sunColorAt(CloudGl.LIGHT_HEIGHTS[i]) : st.moonColorAt(CloudGl.LIGHT_HEIGHTS[i]), pre);
        double[] e = st.skyIrradiance;
        l.ambTop = scale(e, pre / Math.PI);
        double s = Math.max(0, st.sunDir[1]), m = Math.max(0, st.moonDir[1]);
        l.ambBot = new double[3];
        for (int c = 0; c < 3; c++) l.ambBot[c] = (e[c] + st.sunColor[c] * s + st.moonColor[c] * m) * 0.15 / Math.PI * pre;
        l.haze = st.groundExtinction;
        l.flash = new double[]{st.flashDir[0] * 5000, st.flashDir[1] * 5000, st.flashDir[2] * 5000, st.flash * pre};
        l.flashCount = 0;
        if (lightningOk && showLightning && lightningGl.active()) {
            int n = lightningGl.cloudLightCount();
            for (int i = 0; i < n; i++) {
                double[] c = lightningGl.cloudLight(i);
                for (int k = 0; k < 3; k++) {
                    l.flashPos[i * 4 + k] = (float) c[k];
                    l.flashRgb[i * 4 + k] = (float) (c[3] * Lightning.CHANNEL_RGB[k] * pre);
                }
            }
            l.flashCount = n;
        }
        return l;
    }

    /** Licht für Wald, Boden, Halme, Nebel und Regen: {Sonne, Mond, Himmel oben, Himmel am Horizont}, vorbelichtet. */
    private double[][] ambientLight(Sky.State st, double pre) {
        if (skyOk) {
            double[] hor = new double[3];
            for (int i = 0; i < 6; i++) {
                double a = Math.PI * i / 3;
                double[] h = st.radiance(norm3(new double[]{Math.sin(a), 0.08, -Math.cos(a)}));
                for (int k = 0; k < 3; k++) hor[k] += h[k] / 6;
            }
            // oben: das Himmelslicht als Bestrahlung/π (der tiefblaue Zenit allein gäbe nur ein Viertel davon)
            return new double[][]{scale(st.sunColor, pre * cloudShade), scale(st.moonColor, pre * cloudShade), scale(st.skyIrradiance, pre / Math.PI), scale(hor, pre)};
        }
        double[] sp = clock.sun();
        return new double[][]{sunLight(sp[0]), {0, 0, 0}, skyZenith(sp[0]), skyHorizon(sp[0])};
    }

    /** Das stärkste Blitzlicht als Richtung und Bestrahlung am Auge (vorbelichtet), oder null. */
    private double[][] flashLight(double pre) {
        if (!lightningOk || !showLightning || !lightningGl.active() || lightningGl.lightCount() == 0) return null;
        double[] e = camera.eye, dir = null;
        double bestL = 0;
        double[] tot = new double[3];
        for (int i = 0; i < lightningGl.lightCount(); i++) {
            double[] p = lightningGl.lightPos(i), c = lightningGl.lightRgb(i);
            double dx = p[0] - e[0], dy = p[1] - e[1], dz = p[2] - e[2], d2 = dx * dx + dy * dy + dz * dz + p[3] * p[3], d = Math.sqrt(dx * dx + dy * dy + dz * dz + 1);
            double el = (0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]) / d2;
            for (int k = 0; k < 3; k++) tot[k] += c[k] / d2 * pre;
            if (el > bestL) { bestL = el; dir = new double[]{dx / d, dy / d, dz / d}; }
        }
        return dir == null ? null : new double[][]{dir, tot};
    }

    /** Dunstlänge für exp(−d/L) aus der Sichtweite (Koschmieder). */
    static double fogDistance(Sky.State st) { return st.visibilityKm * 1000 / 3.912; }

    static double[] scale(double[] v, double k) { return new double[]{v[0] * k, v[1] * k, v[2] * k}; }

    static double[] norm3(double[] v) { double l = Math.max(1e-12, Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])); return new double[]{v[0] / l, v[1] / l, v[2] / l}; }

    private LightningGl.View lightningView(float[] vp, float[] inv, int w, int h, double pre, double[] air) {
        LightningGl.View v = new LightningGl.View();
        v.viewProj = vp;
        v.invViewProj = inv;
        v.eye = camera.eye.clone();
        v.reversedZ = reversedZ;
        v.width = w;
        v.height = h;
        v.fovyDeg = fovy();
        v.targetFbo = hdrFbo;
        v.preExposure = skyOk ? pre : 1;
        v.extinction = air[0];
        v.scatter = air[1];
        v.ceiling = cloudsOk && showClouds ? clouds.type.base : lightning.cloudBase;
        return v;
    }

    /** Die Tiefe der Szene als Kopie (Regen, Nebel und Blitz lesen sie für weiche Ränder und ihre Weglänge). */
    private void copyDepth(GL4 gl, int w, int h) {
        if (depthCopyTex == 0 || depthCopyW != w || depthCopyH != h) {
            Gpu.deleteFbos(gl, depthCopyFbo);
            Gpu.deleteTextures(gl, depthCopyTex);
            depthCopyTex = Gpu.texture(gl, w, h, GL4.GL_DEPTH_COMPONENT32F, false);
            gl.glBindTexture(GL.GL_TEXTURE_2D, depthCopyTex);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST);
            gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
            depthCopyFbo = Gpu.depthFbo(gl, depthCopyTex);
            depthCopyW = w;
            depthCopyH = h;
        }
        gl.glBindFramebuffer(GL4.GL_READ_FRAMEBUFFER, hdrFbo);
        gl.glBindFramebuffer(GL4.GL_DRAW_FRAMEBUFFER, depthCopyFbo);
        gl.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL.GL_DEPTH_BUFFER_BIT, GL.GL_NEAREST);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
        gl.glViewport(0, 0, w, h);
    }

    private void drawRain(GL4 gl, Sky.State st, float[] vp, float[] inv, int w, int h, double pre, double[][] L) {
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
        gl.glViewport(0, 0, w, h);
        RainGl.View v = new RainGl.View();
        v.viewProj = vp;
        v.invViewProj = inv;
        v.eye = camera.eye.clone();
        v.forward = camera.fwd.clone();
        v.reversedZ = reversedZ;
        v.near = NEAR;
        v.far = FAR;
        v.fovyDeg = fovy();
        v.width = w;
        v.height = h;
        v.depthTexture = depthCopyTex;
        RainGl.Light l = new RainGl.Light();
        l.sunDir = skyOk ? st.sunDir : Sun.direction(clock.sun()[0], clock.sun()[1]);
        l.sunColor = L[0];
        l.moonDir = skyOk ? st.moonDir : new double[]{0, -1, 0};
        l.moonColor = L[1];
        double[][] fl = flashLight(pre);
        if (fl != null && fl[1][1] > l.moonColor[1]) { l.moonDir = fl[0]; l.moonColor = fl[1]; }
        // unter der Wolke: Licht von oben nur, so weit sie es durchlässt
        double under = cloudsOk && showClouds ? Math.min(1, clouds.type.overcastBrightness * 1.6) : 1;
        l.skyTop = skyOk ? scale(st.skyIrradiance, pre / Math.PI * under) : L[2];
        l.skyHorizon = L[3];
        l.fogDistance = skyOk ? fogDistance(st) : FOG_DISTANCE;
        gl.glDepthFunc(reversedZ ? GL.GL_GREATER : GL.GL_LESS);
        rainGl.draw(gl, v, l, rainStrength);
        gl.glDisable(GL.GL_BLEND);
        gl.glDepthMask(true);
    }

    /** Phase 7: die Gischt der Einschläge (com.dan.spray), Licht wie der Regen. */
    private void drawSpray(GL4 gl, Sky.State st, float[] vp, int w, int h, double pre, double[][] L) {
        sprayGl.update(gl, spray);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
        gl.glViewport(0, 0, w, h);
        com.dan.spray.gl.SprayGl.View v = new com.dan.spray.gl.SprayGl.View();
        v.viewProj = vp;
        v.eye = camera.eye.clone();
        v.forward = camera.fwd.clone();
        v.reversedZ = reversedZ;
        v.near = NEAR;
        v.far = FAR;
        v.fovyDeg = fovy();
        v.width = w;
        v.height = h;
        v.depthTexture = depthCopyTex;
        com.dan.spray.gl.SprayGl.Light l = new com.dan.spray.gl.SprayGl.Light();
        l.sunDir = skyOk ? st.sunDir : Sun.direction(clock.sun()[0], clock.sun()[1]);
        l.sunColor = L[0];
        l.moonDir = skyOk ? st.moonDir : new double[]{0, -1, 0};
        l.moonColor = L[1];
        l.skyTop = L[2];
        l.skyHorizon = L[3];
        l.fogDistance = skyOk ? fogDistance(st) : FOG_DISTANCE;
        gl.glDepthFunc(reversedZ ? GL.GL_GREATER : GL.GL_LESS);
        sprayGl.draw(gl, v, l, 1);
        gl.glDisable(GL.GL_BLEND);
        gl.glDepthMask(true);
        gl.glBindVertexArray(emptyVao);
    }

    private void drawFog(GL4 gl, Sky.State st, float[] inv, int w, int h, double[][] L) {
        Fog.Snapshot s = fog.snapshot();
        fogGl.update(gl, s, camera.eye.clone());
        if (fogCheck.isEmpty() && frame > 100) fogCheck = fogGl.selfCheck(gl, fog, camera.eye.clone());
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
        gl.glViewport(0, 0, w, h);
        FogGl.View v = new FogGl.View();
        v.invViewProj = inv;
        v.eye = camera.eye.clone();
        v.reversedZ = reversedZ;
        v.width = w;
        v.height = h;
        v.depthTexture = depthCopyTex;
        v.jitter = frozen ? 0.5 : -1;
        v.frame = frame;
        FogGl.Light l = new FogGl.Light();
        l.sunDir = skyOk ? st.sunDir : Sun.direction(clock.sun()[0], clock.sun()[1]);
        l.sunColor = L[0];
        if (skyOk) l.moonDir = st.moonDir;
        l.moonColor = L[1];
        l.ambientTop = L[2];
        l.ambientHorizon = L[3];
        fogGl.draw(gl, s, v, l);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
        gl.glViewport(0, 0, w, h);
        gl.glBindVertexArray(emptyVao);
    }

    /** Anteil des Stäbchensehens (nachts farblos und bläulich), aus der Anpassung des Himmels. */
    private static double scotopic(Sky.State st) {
        double l = Math.log10(Math.max(1e-12, st.adaptation * Sky.CD_PER_UNIT));
        double t = Math.max(0, Math.min(1, (l - Math.log10(0.003)) / (Math.log10(3) - Math.log10(0.003))));
        return 1 - t * t * (3 - 2 * t);
    }

    /** Schneegrenze über dem See im Sommer (m) [G]: rund 1 430 m ü. M. an den Bergen um den Gletscher. */
    public static final double SNOW_LINE = 1250;
    /** Dunstlänge für exp(−d/L) mit dem Platzhalter-Himmel (wie common.glsl: 60 km). */
    static final double FOG_DISTANCE = 60_000;
    /** Nah- und Fernebene der Projektion (m). */
    static final double NEAR = 0.5, FAR = 80_000;

    /** Mittelpunkt der Sonnenschattenkarte: zwischen Auge und Drehpunkt, frei fliegend ein Stück vor dem Auge. */
    private double[] shadowCenter() {
        double cx, cz;
        if (camera.mode == Camera.Mode.FREI) {
            double k = sunShadow.half * 0.7;
            double hl = Math.max(1e-6, Math.hypot(camera.fwd[0], camera.fwd[2]));
            cx = camera.eye[0] + camera.fwd[0] / hl * k;
            cz = camera.eye[2] + camera.fwd[2] / hl * k;
        } else {
            double hl = Math.max(1e-6, Math.hypot(camera.tx - camera.eye[0], camera.tz - camera.eye[2]));
            double d = Math.min(hl, sunShadow.half * 1.2) * 0.85;
            cx = camera.eye[0] + (camera.tx - camera.eye[0]) / hl * d;
            cz = camera.eye[2] + (camera.tz - camera.eye[2]) / hl * d;
        }
        return new double[]{cx, terrain.height(cx, cz) + 8, cz};
    }

    /** Wald (R), Steppe (G), Moräne (B), Fels (A) als Textur zu 25 m für das Gelände. */
    private static int coverTexture(GL4 gl, Vegetation v) {
        int m = Vegetation.M;
        ByteBuffer b = Buffers.newDirectByteBuffer(m * m * 4);
        for (int k = 0; k < m * m; k++) {
            int f = Math.min(255, (v.lenga[k] & 0xff) + (v.nire[k] & 0xff) + (v.guindo[k] & 0xff));
            b.put((byte) f).put(v.steppe[k]).put(v.moraine[k]).put(v.rock[k]);
        }
        b.flip();
        int t = Gpu.texture(gl, m, m, GL.GL_RGBA8, false);
        gl.glBindTexture(GL.GL_TEXTURE_2D, t);
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 1);
        gl.glTexSubImage2D(GL.GL_TEXTURE_2D, 0, 0, 0, m, m, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, b);
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 4);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        return t;
    }

    /** Sonnenlicht nach der Sonnenhöhe (wie sunLight() in common.glsl). */
    public static double[] sunLight(double elevDeg) {
        double e = Math.sin(Math.toRadians(elevDeg));
        double vis = smooth(-0.015, 0.01, e), t = smooth(0.02, 0.35, e), f = 2.6 * vis * smooth(-0.02, 0.12, e);
        return new double[]{f, (0.55 + 0.40 * t) * f, (0.28 + 0.60 * t) * f};
    }

    /** Zenit- und Horizontfarbe des Platzhalter-Himmels (wie skyColorPlaceholder in common.glsl, ohne Sonnensaum). */
    public static double[] skyZenith(double elevDeg) { return skyPlaceholder(elevDeg, 1); }
    public static double[] skyHorizon(double elevDeg) { return skyPlaceholder(elevDeg, 0); }

    static double[] skyPlaceholder(double e, double up) {
        double b = Sun.skyBrightness(e);
        double day = smooth(-2, 12, e), night = 1 - smooth(-16, -8, e), tw = Math.max(0, Math.min(1, 1 - day - night));
        double hw = Math.pow(1 - up, 4);
        double[] zen = {0.16 * day + 0.10 * tw + 0.05 * night, 0.34 * day + 0.17 * tw + 0.08 * night, 0.72 * day + 0.42 * tw + 0.20 * night};
        double[] hor = {0.62 * day + 0.6 * tw + 0.07 * night, 0.74 * day + 0.4 * tw + 0.09 * night, 0.88 * day + 0.36 * tw + 0.18 * night};
        double[] c = new double[3];
        for (int i = 0; i < 3; i++) c[i] = (zen[i] + (hor[i] - zen[i]) * hw) * b / 0.32;
        return c;
    }

    static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    private void common(GL4 gl, Shader s, float[] vp, float[] inv, double[] sun, double level) {
        s.use(gl);
        // Phase 3: mit com.dan.sky Himmel aus dem Rundumbild, Sonne und Mond vorbelichtet, Dunst aus der Sicht
        boolean sm = skyOk && s != skyShader && s != skyPano;
        s.set(gl, "uSkyMode", sm ? 1 : 0);
        if (sm) {
            Sky.State st = skyState();
            double pre = exposure();
            gl.glActiveTexture(GL.GL_TEXTURE7);
            gl.glBindTexture(GL.GL_TEXTURE_2D, panoTex);
            gl.glActiveTexture(GL.GL_TEXTURE0);
            s.set(gl, "uPano", 7);
            s.set(gl, "uSunColor", scale(st.sunColor, pre));
            s.set(gl, "uMoonColor", scale(st.moonColor, pre));
            s.set(gl, "uMoonDir", st.moonDir);
            s.set(gl, "uFogDist", (float) fogDistance(st));
            s.set(gl, "uPre", (float) pre);
            s.set(gl, "uSkyAmb", scale(st.skyIrradiance, pre / Math.PI));
        }
        s.mat(gl, "uVP", vp);
        s.mat(gl, "uInvVP", inv);
        s.set(gl, "uCam", camera.eye);
        s.set(gl, "uSun", sun);
        s.set(gl, "uTime", (float) (clock.t() % 10_000));
        s.set(gl, "uLevel", (float) level);
        s.set(gl, "uReversed", reversedZ ? 1 : 0);
    }

    private void resizeTargets(GL4 gl, int w, int h) {
        Gpu.deleteFbos(gl, hdrFbo);
        Gpu.deleteTextures(gl, hdrTex, depthTex);
        hdrTex = Gpu.texture(gl, w, h, GL.GL_RGBA16F, false);
        depthTex = Gpu.texture(gl, w, h, GL4.GL_DEPTH_COMPONENT32F, false);
        gl.glBindTexture(GL.GL_TEXTURE_2D, depthTex);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        hdrFbo = Gpu.fbo(gl, hdrTex, depthTex);
        hdrW = w;
        hdrH = h;
    }

    // ------------------------------------------------------------------ Zeitmessung

    private void mark(GL4 gl, int i) { gl.glQueryCounter(stamp[frame & 1][i], GL4.GL_TIMESTAMP); }

    /**
     * Phase 4: zwei Probe-Eisberge im Wasser vor dem Steg, in der Blickschneise zur Front – einer von 7 m Halbmesser
     * rund 120 m entfernt, einer von 12 m rund 260 m entfernt (Abstände [G]). Liegt dort Land, rücken sie weiter
     * hinaus, bis Wasser von mindestens 15 m Tiefe unter ihnen ist. Sie schwimmen mit dem Anteil
     * ρ_Eis/ρ_Wasser unter Wasser (Süßwasser 1000 kg/m³; Wasserlinie aus dem Volumen des Netzes).
     */
    private void placeIcebergs(GL4 gl) {
        for (double[] pl : icebergPlaces()) {
            IceMesh m = IceShapes.iceberg((long) pl[3], pl[4], pl[5], 3);
            double wl = m.waterline(bergIce.submergedFraction(1000));
            icebergs.add(new IceGl.Instance(iceGl.upload(gl, IceShapes.shifted(m, 0, -wl, 0)), bergIce));
        }
    }

    /** Wo die Probe-Eisberge schwimmen: {x, z, Drehung, seed, Halbmesser, Höhenfaktor} (auch ohne Grafikkarte, für die Prüfung). */
    public synchronized List<double[]> icebergPlaces() {
        if (!bergPlaces.isEmpty()) return bergPlaces;
        double[] st = terrain.steg;
        double b = Math.toRadians(st[3]), level = ocean.waterLevel;
        double[][] want = {{120, 7, 0.85, 11, -8}, {260, 12, 0.7, 23, 14}};
        // Befund Status_Phase4a: auf deinem Rechner fand der zweite Eisberg kein Wasser; jetzt sucht jeder auch
        // seitlich (bis ±90 m) und weiter hinaus, braucht nur 10 m Tiefe und hält 60 m Abstand zum ersten
        double[] sides = {0, 30, -30, 60, -60, 90, -90};
        for (double[] wv : want) {
            boolean found = false;
            for (int k = 0; k < 120 && !found; k++) {
                double d = wv[0] + k * 10;
                for (double sd : sides) {
                    double side = wv[4] + sd;
                    double x = st[0] + Math.sin(b) * d + Math.cos(b) * side, z = st[1] - Math.cos(b) * d + Math.sin(b) * side;
                    boolean wet = !terrain.measured() || terrain.sourceAt(x, z) == Terrain.SEE && terrain.height(x, z) < level - 10;
                    boolean apart = true;
                    for (double[] o : bergPlaces) apart &= Math.hypot(o[0] - x, o[1] - z) > 60;
                    // nicht dort, wo die Front im Dezember hinkommt (40 m Vorstoß, dazu der Halbmesser)
                    for (int q = 0; q < 16 && apart && terrain.measured(); q++) {
                        double a = q * Math.PI / 8, rr = 45 + wv[1] + Wiring.ADVANCE_AMPLITUDE;
                        apart &= terrain.sourceAt(x + Math.cos(a) * rr, z + Math.sin(a) * rr) != Terrain.EIS;
                    }
                    if (wet && apart) {
                        bergPlaces.add(new double[]{x, z, wv[3] * 37 % 360, wv[3], wv[1], wv[2]});
                        found = true;
                        break;
                    }
                }
            }
        }
        return bergPlaces;
    }

    public boolean iceRunning() { return iceOk; }

    /** Liest die Zeitstempel des vorigen Bilds, sobald sie da sind (kein Warten im Bildlauf). */
    private void readMarks(GL4 gl) {
        if (frame < 2) return;
        int o = (frame - 1) & 1;
        int[] av = new int[1];
        gl.glGetQueryObjectiv(stamp[o][MARKS - 1], GL4.GL_QUERY_RESULT_AVAILABLE, av, 0);
        if (av[0] == 0) return;
        long[] t = new long[MARKS];
        for (int i = 0; i < MARKS; i++) gl.glGetQueryObjecti64v(stamp[o][i], GL4.GL_QUERY_RESULT, t, i);
        for (int i = 0; i < MARKS - 1; i++) passMs[i] = passMs[i] * 0.9 + Math.max(0, t[i + 1] - t[i]) / 1e6 * 0.1;
        gpuMs = gpuMs * 0.9 + Math.max(0, t[MARKS - 1] - t[0]) / 1e6 * 0.1;
    }

    /** „Himmel 0,10 · Bühne 0,60 · …“ in ms (gleitendes Mittel). */
    public String passTimes() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < passMs.length; i++)
            sb.append(i > 0 ? " · " : "").append(PASSES[i]).append(' ').append(String.format(Locale.GERMAN, "%.2f", passMs[i]));
        return sb.toString();
    }

    private void stats(long now, int w, int h) {
        statsFrames++;
        if (now - statsNanos < 500_000_000L) return;
        fps = statsFrames / ((now - statsNanos) / 1e9);
        statsFrames = 0;
        statsNanos = now;
        statusListener.accept(statusLine(w, h));
    }

    /** Auflösung nach der Zeit der Grafikkarte regeln (siehe {@link #autoScale}). */
    private void regulateScale(long now) {
        if (!autoScale || frame < 60 || now - scaleNanos < 1_500_000_000L) return;
        scaleNanos = now;
        double budget = 1000 / Math.max(10, targetFps), top = quality.renderScale, s = renderScale;
        double want = s * Math.sqrt(budget * 0.9 / Math.max(0.1, gpuMs));
        double next = s;
        // Befund Status_Phase3: 13 ms auf der Grafikkarte, aber nur 33 Bilder/s (das Bild verpasst den Takt des
        // Bildschirms). Darum auch die gemessenen Bilder/s: deutlich unter dem Ziel bei mehr als 65 % des Budgets zählt als zu teuer.
        boolean slow = fps > 1 && fps < targetFps * 0.85 && gpuMs > budget * 0.65;
        if (slow) want = Math.min(want, s * 0.9);
        if (gpuMs > budget * 1.05 || slow) next = Math.max(MIN_SCALE, Math.max(want, s - 0.15));
        else if (gpuMs < budget * 0.6 && fps >= targetFps * 0.9 && s < top) next = Math.min(top, Math.min(want, s + 0.05));
        next = Math.round(next * 20) / 20.0;
        if (Math.abs(next - s) >= 0.045) { renderScale = next; scaleSteps++; }
    }

    /** Wie oft die Regelung die Auflösung geändert hat. */
    public int scaleSteps() { return scaleSteps; }

    /** Für Prüfungen: die Regelung mit vorgegebener Grafikkartenzeit und Bildnummer. */
    public void regulateForTest(double gpuMsNow, long nowNanos) {
        gpuMs = gpuMsNow;
        frame = Math.max(frame, 60);
        regulateScale(nowNanos);
    }

    public String statusLine(int w, int h) {
        String s = String.format(Locale.GERMAN, "%s · %.0f Bilder/s · Grafikkarte %.2f ms · Prozessor %.2f ms · %d×%d (%s%s) · %s",
                clock.label(), fps, gpuMs, cpuMs, w, h, quality.label, autoScale ? String.format(Locale.GERMAN, ", %.0f %%", renderScale * 100) : "", glInfo);
        if (System.currentTimeMillis() < photoMsgUntil) s = photoMsg + " · " + s;
        return s;
    }

    // ------------------------------------------------------------------ HUD

    BufferedImage paintHud() {
        String[] lines = hudFull ? hudLines() : hudShort();
        Font font = new Font(Font.MONOSPACED, Font.PLAIN, 12);
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D pg = probe.createGraphics();
        java.awt.FontMetrics fm = pg.getFontMetrics(font);
        int tw = 0;
        for (String l : lines) tw = Math.max(tw, fm.stringWidth(l.startsWith("!") ? l.substring(1) : l));
        pg.dispose();
        int w = Math.max(200, tw + 22), lh = 17, h = 16 + lines.length * lh;
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(11, 18, 25, 180));
        g.fillRoundRect(0, 0, w - 1, h - 1, 10, 10);
        g.setColor(new Color(138, 211, 236, 220));
        g.setStroke(new BasicStroke(2f));
        g.drawLine(8, 1, 60, 1);
        g.setFont(font);
        for (int i = 0; i < lines.length; i++) {
            String l = lines[i];
            boolean warn = l.startsWith("!");
            if (warn) l = l.substring(1);
            g.setColor(i == 0 ? new Color(241, 246, 249) : warn ? new Color(224, 163, 63) : new Color(185, 200, 210));
            g.drawString(l, 10, 18 + i * lh);
        }
        g.dispose();
        return im;
    }

    /** Das kurze HUD: Tempo, Zeit und Wind, Kamera. Meldungen mit ! aus den vollen Zeilen kommen dazu. */
    String[] hudShort() {
        java.util.List<String> l = new java.util.ArrayList<>();
        String q = autoScale ? String.format(Locale.GERMAN, "%s %.0f %%", quality.label, renderScale * 100) : quality.label;
        l.add(String.format(Locale.GERMAN, "%.0f Bilder/s · Grafikkarte %.1f ms · Prozessor %.1f ms · %s %d×%d", fps, gpuMs, cpuMs, q, hdrW, hdrH));
        l.add(String.format(Locale.GERMAN, "%s · Sonne %.0f° · %s", clock.label(), clock.sun()[0], windLine()));
        l.add(String.format(Locale.GERMAN, "%s · Auge %.0f m · Blick %03.0f° · %.0f m von der Front · H: volles HUD, F1: Tasten",
                camera.modeName(), camera.eye[1], camera.heading(), camera.originDistance()));
        com.dan.glacier.camera.Fahrt fa = camera.fahrt;
        if (fa != null) l.add("Fahrt " + fa.describe() + " · Pfeiltaste oder Maus: anhalten");
        for (String s : hudLines()) if (s.startsWith("!")) l.add(s);
        if (System.currentTimeMillis() < photoMsgUntil) l.add(photoMsg);
        return l.toArray(new String[0]);
    }

    /** „fracture: 0 Stücke · Kräfte 0,00 / Berührungen 0,00 / Stöße 0,00 ms“ (Phase 6). */
    String bruchLine() {
        double[] t = bruch.timings();
        return String.format(Locale.GERMAN, "fracture %s: %d Körper, %d Stücke (%d wach) · Kräfte %.2f, Berührungen %.2f, Stöße %.2f ms",
                bruch.quality, bruch.solidCount(), bruch.pieceCount(), bruch.awakeCount(), t[0], t[1], t[2]);
    }

    /** „Eis“, „See“, „Fels“ unter dem Auge. */
    String zone() { return "unter dem Auge: " + com.dan.glacier.camera.Ansichten.zone(camera.eye[0], camera.eye[2]); }

    /** „Himmel com.dan.sky · Sonne 58,1° · Mond 45 % · Sicht 70 km · Belichtung …“ */
    public String skyLine() {
        if (!skyOk) return "!Himmel: einfacher Verlauf (com.dan.sky nicht gestartet)";
        Sky.State st = skyState();
        return String.format(Locale.GERMAN, "Himmel %s · Sonne %.1f° · Mond %.0f %% · Sicht %.0f km · Belichtung %.3g · Stäbchen %.0f %%%s", skyGl.info(), st.sunAlt,
                st.moonFraction * 100, st.visibilityKm, exposure(), scotopic(st) * 100, skyCheck.isEmpty() ? "" : " · " + skyCheck);
    }

    public String cloudLine() {
        if (!cloudsOk) return "!Wolken: com.dan.cloud nicht gestartet";
        return String.format(Locale.GERMAN, "Wolken %s · Sonne am Auge %.0f %% · %s%s", clouds.describe(), cloudShade * 100, cloudGl.info(), cloudCheck.isEmpty() ? "" : " · " + cloudCheck);
    }

    String weatherLine2() {
        if (wiring == null) return "Wetter —";
        return wiring.weatherLine() + String.format(Locale.GERMAN, " · %.1f °C · %.0f hPa · Sicht %.0f km · Regen %.1f mm/h · Nebel %.0f %%",
                wiring.airTempC, wiring.pressureHPa, wiring.visibilityKm, wiring.rainRate, wiring.fogAmount * 100);
    }

    public String lightningLine() {
        if (!lightningOk) return "!Gewitter: com.dan.lightning nicht gestartet";
        if (lightning.level() == Lightning.Level.OFF && lightning.flashes() == 0) return "Gewitter: keines";
        return String.format(Locale.GERMAN, "Gewitter %s · %d Blitze, %d Erdblitze%s%s", lightning.level().name().toLowerCase(Locale.ROOT), lightning.flashes(),
                lightning.groundFlashes(), lastBoltText.isEmpty() ? "" : " · zuletzt " + lastBoltText, lightningCheck.isEmpty() ? "" : " · " + lightningCheck);
    }

    String windLine() {
        if (wiring == null) return "Wind —";
        var w = wiring.wind;
        return String.format(Locale.GERMAN, "Wind %.1f m/s (Bft %d) aus %03.0f°", w.speed(), w.beaufort(), w.fromDeg());
    }

    public String[] hudLines() {
        double[] sp = clock.sun();
        ComputeProbe.Result c = compute;
        return new String[]{
                String.format(Locale.GERMAN, "%.0f Bilder/s   Grafikkarte %.2f ms   Prozessor %.2f ms (Szene %.2f, Aufträge %.2f)   Budget %.1f ms", fps, gpuMs, cpuMs, sceneCpuMs, Math.max(0, cpuMs - sceneCpuMs), quality.budgetMs()),
                passTimes(),
                (c == null ? "!Compute-Test läuft …" : (c.ok() ? "" : "!") + c.line()),
                String.format(Locale.GERMAN, "%s · Sonne %.1f° / %03.0f° · Belichtung %.2g", clock.label(), sp[0], sp[1], exposure()),
                windLine() + (wiring != null ? String.format(Locale.GERMAN, " · See %+.2f m · Brazo Rico %+.1f m", wiring.waterLevel.level(), wiring.waterLevel.brazoRico()) : ""),
                skyLine(),
                cloudLine(),
                weatherLine2(),
                rainOk ? rainGl.info() + (rainCheck.isEmpty() ? "" : " · " + rainCheck) + String.format(Locale.GERMAN, " · Wetter auf dem Prozessor %.2f ms", weatherCpuMs) : "!Regen: com.dan.rain nicht gestartet",
                fogOk ? "Nebel " + fog.describe() + " · " + fogGl.info() + (fogCheck.isEmpty() ? "" : " · " + fogCheck) : "!Nebel: com.dan.fog nicht gestartet",
                lightningLine(),
                (terrain.measured() ? "" : "!") + "Gelände " + terrain.describe() + String.format(Locale.GERMAN, " · %d Knoten, %,d Punkte", lod.nodes, lod.vertices),
                vegetation.describe() + " · " + zone(),
                iceOk ? iceGl.info() + " · " + bergIce.describe() : "!Eis: com.dan.ice nicht gestartet",
                glacierOk ? gletscher.glacier.describe() + " · " + glacierGl.info() + " · " + gletscher.describeBaeche() : glacierLine,
                oceanOk ? "See " + ocean.describe() + " · " + oceanGl.info() + (oceanCheck.isEmpty() ? "" : " · " + oceanCheck) : "!See: com.dan.ocean nicht gestartet",
                forestOk ? forestGl.info() + (sunShadow.matrix != null ? String.format(Locale.GERMAN, " · Sonnenschatten %d² über %.0f m", sunShadow.size, sunShadow.half * 2) : "") : "!Wald: com.dan.forest nicht gestartet",
                reedOk ? "Halme " + reed.describe() + " · " + reedGl.info() : "!Halme: com.dan.reed nicht gestartet",
                groundOk ? groundGl.info() : "!Bodendecke: com.dan.ground nicht gestartet",
                physik.describe() + " · " + bruchLine(),
                regieLine(),
                rupturaLine(),
                kalbung == null ? "Kalbung: wartet auf den Gletscher" : kalbung.describe() + (kalbungGl != null ? " · " + kalbungGl.info() : "")
                        + String.format(Locale.GERMAN, " · Einschläge %d", splashes) + (sprayOk ? " · Gischt " + spray.parcels().size() + " Bündel" : ""),
                waveLine(),
                bergs.describe() + String.format(Locale.GERMAN, " · %d aus der Kalbung übergeben", handedOver),
                leben.describe(),
                jahrLine(),
                klangLine(),
                wiring == null ? "Verdrahtung —" : "Verdrahtung " + wiring.summary(),
                String.format(Locale.GERMAN, "%s · Auge %.1f m · Blick %03.0f° · %.0f m von der Front · %s",
                        camera.modeName(), camera.eye[1], camera.heading(), camera.originDistance(),
                        Site.dms(Site.latLon(camera.eye[0], camera.eye[2])[0], Site.latLon(camera.eye[0], camera.eye[2])[1])),
                String.format(Locale.GERMAN, "%s · %d×%d · %s", quality.label, hdrW, hdrH, reversedZ ? "umgekehrte Tiefe" : "klassische Tiefe"),
                glInfo};
    }

    // ------------------------------------------------------------------ Bilder

    private void takePhoto(GL4 gl, int w, int h) {
        try {
            BufferedImage img = readDefault(gl, w, h);
            File dir = photoDir != null ? photoDir : new File("docs/status");
            if (!dir.isDirectory() && !dir.mkdirs()) throw new java.io.IOException("Ordner " + dir + " lässt sich nicht anlegen");
            File f = new File(dir, "Glacier_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".png");
            javax.imageio.ImageIO.write(img, "png", f);
            photoMsg = "Foto gespeichert: " + f.getPath();
            System.out.println(photoMsg);
        } catch (Exception e) {
            photoMsg = "Foto fehlgeschlagen: " + e.getMessage();
            System.err.println(photoMsg);
        }
        photoMsgUntil = System.currentTimeMillis() + 5000;
    }

    /** Liest das fertige Fensterbild (nach Nachbearbeitung und HUD). */
    BufferedImage readDefault(GL4 gl, int w, int h) {
        ByteBuffer bb = Buffers.newDirectByteBuffer(w * h * 4);
        gl.glBindFramebuffer(GL.GL_READ_FRAMEBUFFER, defaultFbo);
        gl.glReadBuffer(defaultFbo == 0 ? GL.GL_BACK : GL.GL_COLOR_ATTACHMENT0);
        gl.glPixelStorei(GL.GL_PACK_ALIGNMENT, 1);
        gl.glReadPixels(0, 0, w, h, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, bb);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            int o = (h - 1 - y) * w * 4;
            for (int x = 0; x < w; x++) {
                int i = o + x * 4;
                row[x] = ((bb.get(i) & 255) << 16) | ((bb.get(i + 1) & 255) << 8) | (bb.get(i + 2) & 255);
            }
            img.setRGB(0, y, w, 1, row, 0, w);
        }
        return img;
    }

    /** Für Werkzeuge: das letzte Bild als Bild (im GL-Faden aufrufen). */
    public BufferedImage grab(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        defaultFbo = d.getContext().getDefaultDrawFramebuffer();
        return readDefault(gl, d.getSurfaceWidth(), d.getSurfaceHeight());
    }

    // ------------------------------------------------------------------ Ende

    private void fail(String msg) {
        error = msg;
        System.err.println(msg);
        errorListener.accept(msg);
    }

    @Override
    public void dispose(GLAutoDrawable d) {
        physik.stop();
        welle.stop();
        GL4 gl = d.getGL().getGL4();
        for (Shader s : all) s.dispose(gl);
        hud.dispose(gl);
        lod.dispose(gl);
        if (oceanOk) oceanGl.dispose(gl);
        if (reedOk) reedGl.dispose(gl);
        if (forestOk) forestGl.dispose(gl);
        if (groundOk) groundGl.dispose(gl);
        if (skyOk) skyGl.dispose(gl);
        if (cloudsOk) cloudGl.dispose(gl);
        if (rainOk) rainGl.dispose(gl);
        if (fogOk) fogGl.dispose(gl);
        if (lightningOk) lightningGl.dispose(gl);
        if (kalbungGl != null) kalbungGl.dispose(gl);
        waveGl.dispose(gl);
        brashGl.dispose(gl);
        bergWorker.shutdownNow();
        if (kalbung != null) kalbung.shutdown();
        if (ruptura != null) ruptura.shutdown();
        if (sprayOk) sprayGl.dispose(gl);
        leben.dispose(gl);
        setTon(false);
        if (seasonGlOk) { lakeIceGl.dispose(gl); snowfallGl.dispose(gl); }
        if (glacierGl != null) glacierGl.dispose(gl);
        if (iceOk) iceGl.dispose(gl);
        Gpu.deleteFbos(gl, depthCopyFbo);
        Gpu.deleteTextures(gl, depthCopyTex);
        sunShadow.dispose(gl);
        Gpu.deleteFbos(gl, panoFbo);
        Gpu.deleteTextures(gl, panoTex, coverTex);
        Gpu.deleteFbos(gl, hdrFbo);
        Gpu.deleteTextures(gl, hdrTex, depthTex);
        if (emptyVao != 0) gl.glDeleteVertexArrays(1, new int[]{emptyVao}, 0);
        for (int[] s : stamp) if (s[0] != 0) gl.glDeleteQueries(MARKS, s, 0);
        ready = false;
    }
}
