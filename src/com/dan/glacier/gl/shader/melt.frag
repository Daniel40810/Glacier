// melt.frag — Schmelzwasser in seiner Rinne auf dem Eis: klar und fast farblos über blauem Eis, darum vor allem
// Spiegelung (Fresnel), das Blau des Bettes durch das flache Wasser, Schlieren, die mit der Strömung ziehen
// (Geschwindigkeit nach Manning aus com.dan.river), weiße Wellen, wo es steil wird. Licht wie das Gelände (common.glsl).
#include "common.glsl"
in vec3 vWorld;
in vec4 vFlow;   // s, quer, Geschwindigkeit, Gefälle
in vec2 vDir;    // Fließrichtung (x, z)
out vec4 frag;
uniform vec3 uSunRgb, uSunAmb, uSkyHor;

float h1(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float n2(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(h1(i), h1(i + vec2(1, 0)), f.x), mix(h1(i + vec2(0, 1)), h1(i + vec2(1, 1)), f.x), f.y);
}

void main() {
    float s = vFlow.x, q = vFlow.y, speed = vFlow.z, slope = vFlow.w;
    float edge = 1.0 - smoothstep(0.7, 1.0, abs(q));
    // Schlieren längs der Strömung: Rauschen, das mit der Geschwindigkeit flussab zieht (zwei Phasen, keine Sprünge)
    float t = uTime;
    float a = n2(vec2((s - t * speed) * 0.35, q * 3.0)), b = n2(vec2((s - t * speed * 0.8) * 0.9 + 17.0, q * 6.0));
    vec3 v = normalize(uCam - vWorld);
    vec3 dir = normalize(vec3(vDir.x, 0.0, vDir.y));
    vec3 side = normalize(cross(vec3(0.0, 1.0, 0.0), dir));
    vec3 n = normalize(vec3(0.0, 1.0, 0.0) + side * (q * 0.25 + (a - 0.5) * 0.25) + dir * (b - 0.5) * 0.25);
    float F = 0.02 + 0.98 * pow(1.0 - max(dot(n, v), 0.0), 5.0);
    vec3 sky = skyColor(reflect(-v, n));
    vec3 sun = uSkyMode == 1 ? sunLight() : uSunRgb;
    vec3 amb = uSkyMode == 1 ? uSkyAmb : uSunAmb;
    // Bett: blaues Eis durch ein paar Dezimeter klares Wasser
    vec3 bed = vec3(0.30, 0.55, 0.70) * (sun * max(uSun.y, 0.0) * 0.6 + amb * 1.2);
    vec3 col = mix(bed, sky, F);
    vec3 hv = normalize(v + uSun);
    col += sun * pow(max(dot(n, hv), 0.0), 400.0) * 3.0 * sunVisible();
    // weiße Wellen, wo das Gefälle steil ist und das Wasser schnell
    float foam = smoothstep(0.55, 0.9, a * b * 1.6) * smoothstep(0.04, 0.15, slope);
    col = mix(col, vec3(0.9, 0.93, 0.95) * (sun * max(uSun.y, 0.0) + amb * 1.5), foam * 0.7);
    float dist = length(uCam - vWorld);
    col = aerial(col, normalize(vWorld - uCam), dist);
    frag = vec4(col, edge * mix(0.75, 1.0, foam));
}
