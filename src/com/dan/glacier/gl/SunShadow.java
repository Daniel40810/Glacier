package com.dan.glacier.gl;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

/**
 * Eine Schattenkarte der Sonne um den Blickpunkt (eine Kaskade, aus Haithabu). Seit Phase 2 zeichnen sich die
 * Südbuchen aus {@code com.dan.forest} hinein, Gelände, Bäume und Bodendecke lesen sie (3×3-PCF); ab Phase 5 kommt
 * der Gletscher dazu. Die Karte liegt senkrecht zur Sonne über einem Würfel der halben Kante {@link #half} um den
 * Mittelpunkt; ihr Raster wird auf ganze Texel gerückt, damit die Schattenränder beim Kreisen nicht flimmern.
 * <p>
 * Die App rechnet mit umgekehrter Tiefe (glClipControl 0..1). Die Schattenkarte nimmt die gewohnte Tiefe −1..1, darum
 * schaltet {@link #begin} die Tiefenkonvention um und {@link #end} zurück.
 */
final class SunShadow {
    int tex, fbo, size;
    double half = 220;
    /** Welt → Licht (Spalten), oder null, wenn in diesem Bild kein Schatten gezeichnet wurde. */
    float[] matrix;

    void ensure(GL4 gl, int s) {
        if (s == size && tex != 0) return;
        dispose(gl);
        size = s;
        tex = Gpu.depthTexture(gl, s);
        fbo = Gpu.depthFbo(gl, tex);
    }

    /** Licht-Projektion·Blick um (cx, cy, cz), sunDir zeigt zur Sonne. */
    float[] fit(double[] sunDir, double cx, double cy, double cz) {
        double[] f = {-sunDir[0], -sunDir[1], -sunDir[2]};
        double fl = Math.sqrt(f[0] * f[0] + f[1] * f[1] + f[2] * f[2]);
        f[0] /= fl; f[1] /= fl; f[2] /= fl;
        double[] up = Math.abs(f[1]) > 0.95 ? new double[]{0, 0, -1} : new double[]{0, 1, 0};
        double[] s = norm(cross(f, up)), u = cross(s, f);
        // Mittelpunkt in Lichtkoordinaten auf das Texelraster rücken
        double texel = 2 * half / Math.max(1, size);
        double ls = dot(s, cx, cy, cz), lu = dot(u, cx, cy, cz);
        double ds = Math.floor(ls / texel) * texel - ls, du = Math.floor(lu / texel) * texel - lu;
        cx += s[0] * ds + u[0] * du; cy += s[1] * ds + u[1] * du; cz += s[2] * ds + u[2] * du;
        double depth = half * 3;
        double ex = cx - f[0] * depth, ey = cy - f[1] * depth, ez = cz - f[2] * depth;
        float[] view = new float[16];
        view[0] = (float) s[0]; view[4] = (float) s[1]; view[8] = (float) s[2];
        view[1] = (float) u[0]; view[5] = (float) u[1]; view[9] = (float) u[2];
        view[2] = (float) -f[0]; view[6] = (float) -f[1]; view[10] = (float) -f[2];
        view[12] = (float) -(s[0] * ex + s[1] * ey + s[2] * ez);
        view[13] = (float) -(u[0] * ex + u[1] * ey + u[2] * ez);
        view[14] = (float) (f[0] * ex + f[1] * ey + f[2] * ez);
        view[15] = 1;
        double n = 1, fa = depth * 2;
        float[] proj = new float[16];
        proj[0] = (float) (1 / half); proj[5] = (float) (1 / half);
        proj[10] = (float) (-2 / (fa - n)); proj[14] = (float) (-(fa + n) / (fa - n)); proj[15] = 1;
        return Mat4.mul(proj, view);
    }

    void begin(GL4 gl, boolean reversedZ) {
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo);
        gl.glViewport(0, 0, size, size);
        if (reversedZ) gl.glClipControl(GL4.GL_LOWER_LEFT, GL4.GL_NEGATIVE_ONE_TO_ONE);
        gl.glDepthMask(true);
        gl.glClearDepth(1);
        gl.glClear(GL.GL_DEPTH_BUFFER_BIT);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthFunc(GL.GL_LESS);
        gl.glDisable(GL.GL_BLEND);
        gl.glEnable(GL4.GL_POLYGON_OFFSET_FILL);
        gl.glPolygonOffset(1.5f, 2f);
    }

    void end(GL4 gl, boolean reversedZ) {
        gl.glDisable(GL4.GL_POLYGON_OFFSET_FILL);
        if (reversedZ) {
            gl.glClipControl(GL4.GL_LOWER_LEFT, GL4.GL_ZERO_TO_ONE);
            gl.glClearDepth(0);
        }
    }

    void dispose(GL4 gl) {
        Gpu.deleteFbos(gl, fbo);
        Gpu.deleteTextures(gl, tex);
        tex = fbo = size = 0;
    }

    private static double[] cross(double[] a, double[] b) { return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]}; }
    private static double[] norm(double[] a) { double l = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]); return new double[]{a[0] / l, a[1] / l, a[2] / l}; }
    private static double dot(double[] a, double x, double y, double z) { return a[0] * x + a[1] * y + a[2] * z; }
}
