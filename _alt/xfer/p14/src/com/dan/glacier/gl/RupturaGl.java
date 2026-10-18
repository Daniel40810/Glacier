package com.dan.glacier.gl;

import com.dan.glacier.scene.Ruptura;
import com.dan.glacier.scene.Terrain;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.util.Locale;

/**
 * Phase 13: was die Ruptura auf der Grafikkarte braucht. Die Maske des Brazo Rico (R8 wie das Gelände, 1 = gestauter
 * Arm) für das zweite Zeichnen der See auf dem Spiegel des Staus, und das Wasser in der Rinne als Band mit dem Shader
 * {@code flut.frag} (Ecken wie die Schmelzbäche). Das Band wird je Bild neu hochgeladen (gut 300 Ecken).
 */
public final class RupturaGl {
    private int maskTex, maskVersion = -1, vao, vbo, ibo, count;
    private boolean init;
    private long uploads;

    /** Je Bild im GL-Faden: Maske (einmal), Band (wenn Wasser fließt). */
    public void update(GL4 gl, Ruptura r, double lake) {
        if (!init) {
            int[] t = new int[3];
            gl.glGenVertexArrays(1, t, 0);
            gl.glGenBuffers(2, t, 1);
            vao = t[0];
            vbo = t[1];
            ibo = t[2];
            gl.glBindVertexArray(vao);
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo);
            gl.glEnableVertexAttribArray(0);
            gl.glVertexAttribPointer(0, 3, GL.GL_FLOAT, false, 36, 0);
            gl.glEnableVertexAttribArray(1);
            gl.glVertexAttribPointer(1, 4, GL.GL_FLOAT, false, 36, 12);
            gl.glEnableVertexAttribArray(2);
            gl.glVertexAttribPointer(2, 2, GL.GL_FLOAT, false, 36, 28);
            gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, ibo);
            gl.glBindVertexArray(0);
            init = true;
        }
        byte[] m = r.mask();
        if (m != null && r.maskVersion() != maskVersion) {
            if (maskTex == 0) {
                int[] t = new int[1];
                gl.glGenTextures(1, t, 0);
                maskTex = t[0];
            }
            gl.glBindTexture(GL.GL_TEXTURE_2D, maskTex);
            gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 1);
            gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL4.GL_R8, Terrain.N, Terrain.N, 0, GL4.GL_RED, GL.GL_UNSIGNED_BYTE, java.nio.ByteBuffer.wrap(m));
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
            gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 4);
            gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
            maskVersion = r.maskVersion();
        }
        Object[] rb = r.ribbon(lake);
        if (rb == null) { count = 0; return; }
        float[] vb = (float[]) rb[0];
        int[] ib = (int[]) rb[1];
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, vb.length * 4L, Buffers.newDirectFloatBuffer(vb), GL.GL_DYNAMIC_DRAW);
        gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, ibo);
        gl.glBufferData(GL.GL_ELEMENT_ARRAY_BUFFER, ib.length * 4L, Buffers.newDirectIntBuffer(ib), GL.GL_DYNAMIC_DRAW);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
        count = ib.length;
        uploads++;
    }

    public int maskTexture() { return maskVersion >= 0 ? maskTex : 0; }

    /** {x0, z0, Kantenlänge} der Maske. */
    public static double[] maskRect() { return new double[]{Terrain.X0, Terrain.Z0, Terrain.N * Terrain.CELL}; }

    /** Zeichnet das Band (Shader und Zustand setzt der Aufrufer). */
    public void drawRibbon(GL4 gl) {
        if (count == 0) return;
        gl.glBindVertexArray(vao);
        gl.glDrawElements(GL.GL_TRIANGLES, count, GL.GL_UNSIGNED_INT, 0);
        gl.glBindVertexArray(0);
    }

    public boolean flowing() { return count > 0; }

    public String info() { return String.format(Locale.GERMAN, "Maske %s, Band %d Ecken", maskVersion >= 0 ? "geladen" : "fehlt", count / 3); }

    public void dispose(GL4 gl) {
        if (!init) return;
        gl.glDeleteBuffers(2, new int[]{vbo, ibo}, 0);
        gl.glDeleteVertexArrays(1, new int[]{vao}, 0);
        if (maskTex != 0) gl.glDeleteTextures(1, new int[]{maskTex}, 0);
        init = false;
    }
}
