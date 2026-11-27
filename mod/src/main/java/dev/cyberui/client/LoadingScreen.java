package dev.cyberui.client;

import java.io.InputStream;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * The loading screen (replaces the red Mojang Studios screen). The logo is
 * read straight from the mod jar and everything else is drawn with plain fills,
 * so it works on the very first loading screen, before any resources exist.
 */
public final class LoadingScreen {

    private static final Identifier LOGO = Identifier.fromNamespaceAndPath("cyberui", "loading_logo");
    private static final String LOGO_FILE = "/assets/cyberui/textures/gui/loading_logo.png";
    private static final String[] TASKS = {
        "LOADING TEXTURES", "LINKING SHADERS", "SYNCING SOUNDS", "DECRYPTING DATA", "BOOTING NEURAL LINK",
    };

    private static boolean logoTried = false;
    private static boolean logoReady = false;
    private static int logoW = 512;
    private static int logoH = 110;
    private static long firstFrame = 0L;

    private LoadingScreen() {
    }

    private static void loadLogo() {
        if (logoTried) {
            return;
        }
        logoTried = true;
        try (InputStream in = LoadingScreen.class.getResourceAsStream(LOGO_FILE)) {
            if (in == null) {
                return;
            }
            NativeImage image = NativeImage.read(in);
            logoW = image.getWidth();
            logoH = image.getHeight();
            Minecraft.getInstance().getTextureManager().register(LOGO, new DynamicTexture(() -> "cyberui loading logo", image));
            logoReady = true;
        } catch (Exception e) {
            logoReady = false;
        }
    }

    /**
     * @param alpha     element opacity (follows the vanilla fade)
     * @param progress  smoothed loading progress 0..1
     * @param barAlpha  progress bar opacity (0 once vanilla stops drawing its bar)
     */
    public static void render(GuiGraphicsExtractor g, float alpha, float progress, float barAlpha) {
        long now = Util.getMillis();
        if (firstFrame == 0L) {
            firstFrame = now;
        }
        int w = g.guiWidth();
        int h = g.guiHeight();

        renderGrid(g, w, h, alpha * 0.6F, now);

        // logo, dropping in from above, with an occasional colour-split glitch
        loadLogo();
        if (logoReady) {
            int lw = (int) Math.min(w * 0.55F, 320);
            int lh = lw * logoH / logoW;
            float drop = 1.0F - Draw.easeOut((now - firstFrame) / 700.0F);
            int lx = (w - lw) / 2;
            int ly = h / 2 - lh - 18 - Math.round(80 * drop);
            boolean glitch = (now % 2600L) < 140L;
            if (glitch) {
                int dx = (int) ((now / 30L) % 5L) - 2;
                logo(g, lx - 3 + dx, ly, lw, lh, Draw.argb(Draw.CYAN, alpha * 0.6F));
                logo(g, lx + 3 - dx, ly, lw, lh, Draw.argb(Draw.RED, alpha * 0.6F));
            }
            logo(g, lx, ly, lw, lh, Draw.argb(0xFFFFFF, alpha));
        } else {
            String title = "CYBER//UI";
            Draw.text(g, (w - Draw.textWidth(title, 4)) / 2, h / 2 - 40, title, 4, Draw.argb(Draw.YELLOW, alpha));
        }

        // progress bar
        int bx0 = w / 2 - 100;
        int by = h / 2 + 18;
        int bw = 200;
        float ba = barAlpha * alpha;
        if (ba > 0.01F) {
            renderBar(g, bx0, by, bw, 8, progress, ba, now);
            renderSpinner(g, bx0 - 14, by - 4, ba, now);
            String pct = Math.round(progress * 100.0F) + "%";
            Draw.text(g, bx0 + bw + 6, by + 2, pct, 1, Draw.argb(Draw.YELLOW, ba));

            int task = (int) (((now - firstFrame) / 900L) % TASKS.length);
            int dots = (int) ((now / 250L) % 4L);
            String line = "> " + TASKS[task] + ".".repeat(dots);
            int x = Draw.text(g, bx0, by + 16, line, 1, Draw.argb(Draw.YELLOW, ba));
            if ((now / 300L) % 2L == 0L) {
                Draw.rect(g, x + 1, by + 16, 3, 5, Draw.argb(Draw.YELLOW, ba));
            }
            Draw.text(g, bx0, by + 26, hexLine(now), 1, Draw.argb(0x007882, ba));
        }

        Draw.hazard(g, 0, 0, w, 3, alpha, now);
        Draw.hazard(g, 0, h - 3, w, 3, alpha, now);
    }

    private static void logo(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.blit(RenderPipelines.GUI_TEXTURED, LOGO, x, y, 0.0F, 0.0F, w, h, logoW, logoH, logoW, logoH, color);
    }

    private static void renderGrid(GuiGraphicsExtractor g, int w, int h, float alpha, long now) {
        int line = Draw.argb(0x18160E, alpha);
        for (int x = 0; x < w; x += 24) {
            g.fill(x, 0, x + 1, h, line);
        }
        for (int y = 0; y < h; y += 24) {
            g.fill(0, y, w, y + 1, line);
        }
        // sweeping scan bar
        int sweep = (int) ((now / 12L) % (h + 40));
        for (int d = 0; d < 30; d++) {
            float k = (1.0F - d / 30.0F);
            g.fill(0, sweep - d, w, sweep - d + 1, Draw.argb(Draw.YELLOW, alpha * 0.18F * k * k));
        }
        g.fill(0, sweep, w, sweep + 1, Draw.argb(Draw.YELLOW, alpha * 0.45F));
    }

    private static void renderBar(GuiGraphicsExtractor g, int x, int y, int w, int h, float progress, float alpha, long now) {
        Draw.rect(g, x, y, w, h, Draw.argb(Draw.BG, alpha * 0.9F));
        Draw.frame(g, x, y, w, h, Draw.argb(Draw.YELLOW, alpha));
        Draw.rect(g, x, y, 4, 2, Draw.argb(Draw.RED, alpha));
        int fill = Math.round((w - 4) * Draw.clamp(progress, 0.0F, 1.0F));
        int shift = (int) ((now / 80L) % 6L);
        if (fill > 0) {
            // dark base, then the bright 3-pixel stripes crawling along it
            g.fill(x + 2, y + 2, x + 2 + fill, y + h - 2, Draw.argb(Draw.DARK_YELLOW, alpha));
            int yellow = Draw.argb(Draw.YELLOW, alpha);
            for (int i = -shift; i < fill; i += 6) {
                int x0 = Math.max(0, i);
                int x1 = Math.min(fill, i + 3);
                if (x1 > x0) {
                    g.fill(x + 2 + x0, y + 2, x + 2 + x1, y + h - 2, yellow);
                }
            }
        }
    }

    private static void renderSpinner(GuiGraphicsExtractor g, int x, int y, float alpha, long now) {
        int head = (int) ((now / 60L) % 12L);
        for (int seg = 0; seg < 12; seg++) {
            int age = (head - seg + 12) % 12;
            if (age > 6) {
                continue;
            }
            double ang = seg / 12.0 * Math.PI * 2.0;
            float a = alpha * (1.0F - age / 7.0F);
            int color = Draw.argb(age < 2 ? Draw.YELLOW : Draw.RED, a);
            for (int r = 4; r <= 6; r++) {
                int px = x + 8 + (int) Math.round(r * Math.cos(ang));
                int py = y + 8 + (int) Math.round(r * Math.sin(ang));
                g.fill(px, py, px + 1, py + 1, color);
            }
        }
    }

    private static long hexSeed = -1L;
    private static String hexText = "";

    /** Scrolling hex dump; rebuilt only when it changes (every 110 ms), not every frame. */
    private static String hexLine(long now) {
        long seed = now / 110L;
        if (seed != hexSeed) {
            hexSeed = seed;
            StringBuilder sb = new StringBuilder(44);
            for (int i = 0; i < 9; i++) {
                long v = ((seed * 6364136223846793005L + i * 1442695040888963407L) >>> 48) & 0xFFFF;
                for (int shift = 12; shift >= 0; shift -= 4) {
                    sb.append(Character.toUpperCase(Character.forDigit((int) ((v >> shift) & 0xF), 16)));
                }
                if (i < 8) {
                    sb.append(' ');
                }
            }
            hexText = sb.toString();
        }
        return hexText;
    }
}
