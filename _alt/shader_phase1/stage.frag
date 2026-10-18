// stage.frag — die flache Bühne (Phase 1, Platzhalter bis zum Gelände aus den Höhendaten in Phase 2 und dem Gletscher
// aus com.dan.ice in Phase 4 und 5). Gleiche Formen wie scene/Buehne.java, alle [G] und nur zur Ausrichtung:
//   der See auf y = 0 (Canal de los Témpanos nördlich, Brazo Rico südlich der Halbinsel), milchig türkis;
//   das Eis als Block: Front als gerade Wand bei x = 0 über 5 km, 74 m hoch, nach Westen 8 km lang;
//   die Halbinsel Magallanes als flache Landzunge von Osten, ihre Spitze 400 m vor der Front (dort die Stege).
// Der Sichtstrahl wird mit dem Block und der Ebene geschnitten; der See spiegelt Himmel und Wand. Marken: Raster
// 100 m und 1 km, die Front als Linie, der Ring am Steg, ein Nordstrich am Ursprung. Licht: Sonne und Platzhalter-
// Himmel aus common.glsl. Die Tiefe wird aus dem Schnittpunkt geschrieben (umgekehrte Tiefe).
#include "common.glsl"
in vec2 vUV;
out vec4 frag;
uniform vec2 uWindTo;      // wohin der Wind weht (x, z), Einheitsvektor
uniform float uWindSpeed;  // m/s
uniform int uMarks;        // 1 = Marken zeigen
uniform float uIceTop;     // Höhe der Front über dem See (m)
uniform float uHalfFront;  // halbe Breite der Front (m)
uniform float uIceWest;    // Länge des Eisblocks nach Westen (m)
uniform vec4 uPen;         // Halbinsel: Spitze x, halbe Breite an der Spitze, an der Wurzel, x der Wurzel

float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float vnoise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1, 0)), u.x), mix(hash(i + vec2(0, 1)), hash(i + vec2(1, 1)), u.x), u.y);
}
float fbm(vec2 p) {
    float s = 0.0, a = 0.5;
    for (int i = 0; i < 4; i++) { s += a * vnoise(p); p = p * 2.03 + 17.1; a *= 0.5; }
    return s;
}

float segDist(vec2 p, vec2 a, vec2 b) {
    vec2 pa = p - a, ba = b - a;
    float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
    return length(pa - ba * h);
}
// Linie mit Breite w (m), weich nach der Größe eines Bildpunkts
float line(float d, float w, float px) { return 1.0 - smoothstep(w, w + px * 1.5, d); }

// Halbinsel wie Buehne.land(): Mittellinie biegt leicht nach Süden, runde Spitze
bool land(vec2 p) {
    if (p.x < uPen.x) return false;
    float u = min(1.0, (p.x - uPen.x) / (uPen.w - uPen.x));
    float hw = uPen.y + (uPen.z - uPen.y) * sqrt(u), mid = 150.0 * u;
    if (p.x < uPen.x + uPen.y) return length(vec2(p.x - (uPen.x + uPen.y), p.y - mid)) <= uPen.y;
    return abs(p.y - mid) <= hw;
}

// Schnitt mit dem Eisblock (Slab-Verfahren): Abstand t oder −1, Normale in n
float iceBox(vec3 ro, vec3 rd, out vec3 n) {
    vec3 lo = vec3(-uIceWest, -200.0, -uHalfFront), hi = vec3(0.0, uIceTop, uHalfFront);
    vec3 inv = 1.0 / rd;
    vec3 t0 = (lo - ro) * inv, t1 = (hi - ro) * inv;
    vec3 tn = min(t0, t1), tf = max(t0, t1);
    float a = max(max(tn.x, tn.y), tn.z), b = min(min(tf.x, tf.y), tf.z);
    if (a > b || b < 0.0 || a < 0.0) return -1.0;
    n = a == tn.x ? vec3(-sign(rd.x), 0, 0) : a == tn.y ? vec3(0, -sign(rd.y), 0) : vec3(0, 0, -sign(rd.z));
    return a;
}

// Platzhalter-Eis (das Material kommt in Phase 4): Firn weiß, Wand blauweiß mit senkrechten Rinnen, unten an der
// Wasserlinie dunkler und blauer (Schmelzkerbe angedeutet), oben Querspalten (quer zur Fließrichtung +x).
vec3 iceColor(vec3 p, vec3 n, float px) {
    vec3 white = vec3(0.70, 0.75, 0.80), blue = vec3(0.42, 0.66, 0.84), deep = vec3(0.16, 0.38, 0.62);
    vec3 alb;
    if (n.y > 0.5) {
        float sp = abs(fract(p.x / 38.0 + 0.3 * fbm(p.xz * 0.004)) - 0.5) * 38.0;   // Querspalten alle rund 40 m
        float crev = line(sp, 2.5 + 2.0 * fbm(p.xz * 0.01), px) * smoothstep(0.35, 0.6, fbm(p.xz * 0.006 + 3.0));
        float dirt = smoothstep(0.62, 0.8, fbm(vec2(p.z * 0.004, p.x * 0.0004)));     // Moränenband längs
        alb = mix(white, deep, crev * 0.9);
        alb *= 0.85 + 0.15 * fbm(p.xz * 0.05);                                             // Firnflecken
        alb = mix(alb, vec3(0.32, 0.30, 0.28), dirt * 0.6);
    } else {
        vec2 q = abs(n.x) > 0.5 ? vec2(p.z, p.y) : vec2(p.x, p.y);
        float col = fbm(vec2(q.x * 0.05, q.y * 0.004));                               // senkrechte Rinnen
        float seam = smoothstep(0.55, 0.75, fbm(vec2(q.x * 0.02, q.y * 0.01) + 7.0));
        alb = mix(white, blue, 0.35 + 0.4 * col);
        alb = mix(alb, deep, seam * 0.6);
        float foot = 1.0 - smoothstep(0.0, 9.0, p.y);                                    // an der Wasserlinie
        alb = mix(alb, deep * 0.8, foot * 0.7);
    }
    float diff = max(dot(n, uSun), 0.0);
    vec3 sky = skyColor(vec3(0.0, 1.0, 0.0)) * (0.55 + 0.45 * n.y);
    return alb * (sunLight() * diff + sky * 0.95);
}

// Farbe in Richtung rd von ro (für die Spiegelung im See): Eiswand oder Himmel
vec3 reflected(vec3 ro, vec3 rd) {
    vec3 n;
    float t = iceBox(ro, rd, n);
    if (t > 0.0) return aerial(iceColor(ro + rd * t, n, t * 0.004), rd, t);
    return skyColor(vec3(rd.x, max(rd.y, 0.0), rd.z));
}

void main() {
    vec3 dir = viewRay(vUV);
    vec3 nIce;
    float tIce = iceBox(uCam, dir, nIce);
    float tPlane = (dir.y < -1e-5 && uCam.y > 0.0) ? uCam.y / -dir.y : -1.0;
    if (tIce < 0.0 && tPlane < 0.0) discard;
    bool hitIce = tIce > 0.0 && (tPlane < 0.0 || tIce < tPlane);
    float t = hitIce ? tIce : tPlane;
    vec3 p = uCam + dir * t;
    vec2 xz = p.xz;
    float px = t * 0.0012 / max(0.06, abs(hitIce && nIce.y < 0.5 ? dot(dir, nIce) : dir.y));

    vec3 col;
    bool water = false;
    if (hitIce) {
        col = iceColor(p, nIce, px);
    } else if (land(xz)) {
        // Halbinsel: Fels, Steppengras und dunkler Lenga-Wald in Flecken
        float g = fbm(xz * 0.01);
        vec3 alb = mix(vec3(0.11, 0.10, 0.08), vec3(0.05, 0.08, 0.04), smoothstep(0.45, 0.6, g));
        alb = mix(alb, vec3(0.20, 0.18, 0.13), smoothstep(0.7, 0.85, fbm(xz * 0.03 + 5.0)));
        col = alb * (sunLight() * max(uSun.y, 0.0) + skyColor(vec3(0.0, 1.0, 0.0)) * 0.9);
    } else {
        water = true;
        // See: kurze Wellen in Windrichtung, Fresnel, Spiegelung von Himmel und Wand, Sonnenglitzer;
        // das Wasser selbst milchig türkis vom Gletschermehl [G]
        vec2 dx = vec2(0.0);
        float L = 8.0 + uWindSpeed * 1.0;
        for (int i = 0; i < 6; i++) {
            float k = 6.2831853 / L, ang = sin(float(i) * 2.39996) * 1.1;
            vec2 wd = mat2(cos(ang), -sin(ang), sin(ang), cos(ang)) * uWindTo;
            float A = L * 0.006 * clamp(uWindSpeed / 8.0, 0.05, 1.2);
            float fade = 1.0 - smoothstep(L * 0.08, L * 0.35, px);
            float ph = k * dot(wd, xz) - sqrt(9.81 * k) * uTime + float(i) * 2.17;
            dx += wd * (A * k * cos(ph)) * fade;
            L *= 0.7;
        }
        vec3 n = normalize(vec3(-dx.x, 1.0, -dx.y));
        vec3 V = -dir;
        float F = 0.02 + 0.98 * pow(1.0 - max(dot(n, V), 0.0), 5.0);
        vec3 r = reflect(dir, n);
        r.y = abs(r.y);
        vec3 body = vec3(0.05, 0.16, 0.16) * (skyColor(vec3(0.0, 1.0, 0.0)) * 1.2 + sunLight() * max(uSun.y, 0.0) * 0.35);
        // Spiegelung halb aus der glatten, halb aus der bewegten Fläche: die Wand bricht weich auf statt in harten Flecken
        vec3 r0 = reflect(dir, vec3(0.0, 1.0, 0.0));
        vec3 refl = mix(reflected(p + vec3(0.0, 0.05, 0.0), r0), reflected(p + vec3(0.0, 0.05, 0.0), r), 0.45);
        col = mix(body, refl, F);
        col += sunLight() * 6.0 * pow(max(dot(r, uSun), 0.0), 500.0) * F;
        // Brash und helles Wasser dicht an der Front [G]
        float nearFront = (1.0 - smoothstep(0.0, 140.0, xz.x)) * step(abs(xz.y), uHalfFront) * step(0.0, xz.x);
        float brash = nearFront * smoothstep(0.55, 0.75, fbm(xz * 0.08));
        col = mix(col, vec3(0.8, 0.86, 0.9) * (sunLight() * max(uSun.y, 0.0) + skyColor(vec3(0.0, 1.0, 0.0))), brash * 0.7);
    }

    if (uMarks == 1) {
        vec3 ink = skyColor(vec3(0.0, 1.0, 0.0)) * 0.9 + sunLight() * max(uSun.y, 0.0) * 0.4;
        if (!hitIce || nIce.y > 0.5) {
            vec2 g100 = abs(fract(xz / 100.0 + 0.5) - 0.5) * 100.0, g1k = abs(fract(xz / 1000.0 + 0.5) - 0.5) * 1000.0;
            float grid = max(line(min(g100.x, g100.y), 0.3, px) * 0.18 * (1.0 - smoothstep(800.0, 2500.0, t)),
                             line(min(g1k.x, g1k.y), 1.2, px) * 0.35);
            grid *= (1.0 - smoothstep(6000.0, 14000.0, t)) * (water ? 0.5 : 1.0);
            col = mix(col, ink * vec3(0.55, 0.85, 1.0), grid);
        }
        if (!hitIce) {
            // die Front als Linie auf dem Wasser, der Ring um die Stege, der Nordstrich am Ursprung
            float front = line(segDist(xz, vec2(1.0, -uHalfFront), vec2(1.0, uHalfFront)), 1.5, px);
            vec2 steg = vec2(uPen.x + 30.0, 20.0);
            float ring = line(abs(length(xz - steg) - 25.0), 1.0, px);
            float north = line(segDist(xz, vec2(12.0, 0.0), vec2(12.0, -60.0)), 0.8, px);
            col = mix(col, vec3(1.0, 0.45, 0.2) * (0.4 + 0.6 * sunLight().r) + 0.05, max(ring, north));
            col = mix(col, ink * vec3(0.6, 0.9, 1.1) * 1.3, front * 0.6);
        }
    }

    col = aerial(col, dir, t);
    frag = vec4(col, 1.0);
    gl_FragDepth = depthOf(p);
}
