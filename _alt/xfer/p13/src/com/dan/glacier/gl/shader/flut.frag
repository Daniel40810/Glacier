// flut.frag — Phase 13: das Wasser der Ruptura in der Rinne. Trüb und milchig türkis vom Gletschermehl wie der See,
// aber schnell: Schlieren und Walzen ziehen mit der Strömung, weißes Wasser nach Geschwindigkeit und Gefälle, an der
// Mündung (steil) fast ganz Schaum. Band und Ecken wie die Schmelzbäche (melt.vert). Licht wie das Gelände.
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
    float edge = 1.0 - smoothstep(0.85, 1.0, abs(q));
    float t = uTime;
    // Walzen quer zur Strömung und Schlieren längs, beide flussab
    float a = n2(vec2((s - t * speed) * 0.12, q * 2.5)), b = n2(vec2((s - t * speed * 0.9) * 0.45 + 17.0, q * 7.0));
    float c = n2(vec2((s - t * speed * 1.1) * 1.3 - 5.0, q * 13.0 + t * 0.7));
    vec3 v = normalize(uCam - vWorld);
    vec3 dir = normalize(vec3(vDir.x, 0.0, vDir.y));
    vec3 side = normalize(cross(vec3(0.0, 1.0, 0.0), dir));
    float chop = clamp(speed / 10.0, 0.15, 1.0);
    vec3 n = normalize(vec3(0.0, 1.0, 0.0) + side * (q * 0.2 + (b - 0.5) * 0.6 * chop) + dir * ((a - 0.5) * 0.9 + (c - 0.5) * 0.4) * chop);
    float F = 0.02 + 0.98 * pow(1.0 - max(dot(n, v), 0.0), 5.0);
    vec3 sky = skyColor(reflect(-v, n));
    vec3 sun = uSkyMode == 1 ? sunLight() : uSunRgb;
    vec3 amb = uSkyMode == 1 ? uSkyAmb : uSunAmb;
    vec3 lit = sun * max(uSun.y, 0.0) * 0.7 + amb * 1.3;
    // Gletschermehl: das Wasser selbst streut (milchig türkis), man sieht keinen Grund
    vec3 body = vec3(0.10, 0.36, 0.36) * lit;
    vec3 col = mix(body, sky, F * 0.8);
    vec3 hv = normalize(v + uSun);
    col += sun * pow(max(dot(n, hv), 0.0), 200.0) * 2.0 * sunVisible();
    // weißes Wasser: schnell, steil, und in den Walzen
    float rough = smoothstep(3.0, 12.0, speed) * 0.55 + smoothstep(0.05, 0.4, slope) * 0.8;
    float foam = clamp(smoothstep(0.35, 0.85, a * 0.6 + b * 0.5 + c * 0.3) * rough + smoothstep(0.3, 1.0, slope) * 0.6, 0.0, 1.0);
    foam *= 0.7 + 0.3 * edge;
    col = mix(col, vec3(0.88, 0.92, 0.94) * (sun * max(uSun.y, 0.0) + amb * 1.6), foam * 0.85);
    float dist = length(uCam - vWorld);
    col = aerial(col, normalize(vWorld - uCam), dist);
    frag = vec4(col, edge * mix(0.92, 1.0, foam));
}
