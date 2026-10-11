package dev.cyberui.client;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Drawing helpers that only use plain fills, so they also work on the very
 * first loading screen, before fonts and textures are loaded.
 */
public final class Draw {

    public static final int BG = 0x0A0A0F;
    public static final int BLACK = 0x050508;
    public static final int YELLOW = 0xFCEE0A;
    public static final int DARK_YELLOW = 0xC89600;
    public static final int RED = 0xFF003C;
    public static final int CYAN = 0x00F0FF;
    public static final int GREY = 0x5A5A64;

    private Draw() {
    }

    /** Combines an RGB colour with an alpha in 0..1. */
    public static int argb(int rgb, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    public static float clamp(float v, float min, float max) {
        return v < min ? min : (v > max ? max : v);
    }

    /** Cubic ease-out, t in 0..1. */
    public static float easeOut(float t) {
        t = clamp(t, 0.0F, 1.0F);
        float u = 1.0F - t;
        return 1.0F - u * u * u;
    }

    public static void rect(GuiGraphics g, int x, int y, int w, int h, int argb) {
        g.fill(x, y, x + w, y + h, argb);
    }

    public static void frame(GuiGraphics g, int x, int y, int w, int h, int argb) {
        rect(g, x, y, w, 1, argb);
        rect(g, x, y + h - 1, w, 1, argb);
        rect(g, x, y, 1, h, argb);
        rect(g, x + w - 1, y, 1, h, argb);
    }

    /** Animated yellow/black hazard stripes. */
    public static void hazard(GuiGraphics g, int x, int y, int w, int h, float alpha, long now) {
        int shift = (int) ((now / 60L) % 8L);
        int yellow = argb(YELLOW, alpha);
        int black = argb(BLACK, alpha);
        rect(g, x, y, w, h, black);
        for (int row = 0; row < h; row++) {
            for (int sx = -8 + ((row + shift) % 8); sx < w; sx += 8) {
                int x0 = Math.max(0, sx);
                int x1 = Math.min(w, sx + 4);
                if (x1 > x0) {
                    g.fill(x + x0, y + row, x + x1, y + row + 1, yellow);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // 3x5 pixel font
    // ------------------------------------------------------------------
    private static final String CHARS = "ABCDEFGHIJKLMNOPRSTUVWXY0123456789/:.%->_ ";
    private static final String[] GLYPHS = {
        "010101111101101", "110101110101110", "011100100100011", "110101101101110", "111100110100111",
        "111100110100100", "011100101101011", "101101111101101", "111010010010111", "001001001101010",
        "101101110101101", "100100100100111", "101111111101101", "110101101101101", "010101101101010",
        "110101110100100", "110101110101101", "011100010001110", "111010010010010", "101101101101111",
        "101101101101010", "101101111111101", "101101010101101", "101101010010010",
        "111101101101111", "010110010010111", "110001010100111", "110001010001110", "101101111001001",
        "111100110001110", "011100111101111", "111001010010010", "111101111101111", "111101111001110",
        "001001010100100", "000010000010000", "000000000000010", "101001010100101", "000000111000000",
        "100010001010100", "000000000000111", "000000000000000",
    };

    /** Draws text with the pixel font; returns the x after the text. */
    public static int text(GuiGraphics g, int x, int y, String s, int scale, int argb) {
        String up = s.toUpperCase();
        for (int c = 0; c < up.length(); c++) {
            int idx = CHARS.indexOf(up.charAt(c));
            if (idx >= 0) {
                String glyph = GLYPHS[idx];
                for (int i = 0; i < 15; i++) {
                    if (glyph.charAt(i) == '1') {
                        int px = x + (i % 3) * scale;
                        int py = y + (i / 3) * scale;
                        g.fill(px, py, px + scale, py + scale, argb);
                    }
                }
            }
            x += 4 * scale;
        }
        return x;
    }

    public static int textWidth(String s, int scale) {
        return s.length() * 4 * scale - scale;
    }
}
