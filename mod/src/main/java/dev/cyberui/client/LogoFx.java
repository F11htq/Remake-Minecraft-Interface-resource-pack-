package dev.cyberui.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LogoRenderer;

/**
 * Effects for the "MINECRAFT / JAVA EDITION" logo on the title screen:
 * a slice-assembly intro, a pulsing neon glow, red/cyan chromatic split,
 * periodic glitch bursts, a scan line sweep and a gentle float.
 */
public final class LogoFx {

    // logo box when drawn with TitleLayout.LOGO_FAKE_WIDTH (logo 256x44 at y=30, edition below)
    private static final int X0 = TitleLayout.LOGO_FAKE_WIDTH / 2 - 128;
    private static final int Y0 = 28;
    private static final int W = 256;
    private static final int H = 56;
    private static final int BANDS = 7;

    private static final long INTRO_MS = 900L;
    private static final long BURST_EVERY_MS = 3700L;
    private static final long BURST_MS = 160L;
    private static final long SCAN_EVERY_MS = 4300L;
    private static final long SCAN_MS = 650L;

    private LogoFx() {
    }

    public static void render(LogoRenderer logo, GuiGraphics g, float alpha, long openedAt) {
        long now = Util.getMillis();
        long age = now - openedAt;
        float intro = Draw.clamp(age / (float) INTRO_MS, 0.0F, 1.0F);
        boolean burst = age > INTRO_MS && (age % BURST_EVERY_MS) < BURST_MS;
        float t = now / 1000.0F;
        float pulse = 0.5F + 0.5F * (float) Math.sin(t * 2.4F);
        float bob = (float) Math.sin(t * 1.6F) * 1.5F;
        float a = alpha * Draw.easeOut(intro * 1.4F);

        g.pose().pushPose();
        g.pose().translate(0.0F, bob, 0.0F);

        // neon glow: slightly enlarged yellow copies behind the logo
        float glow = (0.10F + 0.10F * pulse) * a;
        for (int i = 0; i < 2; i++) {
            float s = 1.012F + i * 0.012F;
            g.pose().pushPose();
            g.pose().translate(X0 + W / 2.0F, Y0 + H / 2.0F, 0.0F);
            g.pose().scale(s, s, 1.0F);
            g.pose().translate(-(X0 + W / 2.0F), -(Y0 + H / 2.0F), 0.0F);
            tinted(logo, g, 1.0F, 0.93F, 0.04F, glow, 0, 0, a);
            g.pose().popPose();
        }

        // chromatic split: red to the left, cyan to the right
        int split = burst ? 4 : 1;
        float splitAlpha = (burst ? 0.75F : 0.30F + 0.15F * pulse) * a;
        tinted(logo, g, 1.0F, 0.0F, 0.24F, splitAlpha, -split, 0, a);
        tinted(logo, g, 0.0F, 0.94F, 1.0F, splitAlpha, split, 0, a);

        // main logo: assembled from sliding bands during the intro and glitch bursts
        if (intro < 1.0F || burst) {
            float spread = burst ? 0.35F : 1.0F - Draw.easeOut(intro);
            long seed = burst ? age / BURST_EVERY_MS : 7L;
            int bandH = (H + BANDS - 1) / BANDS;
            for (int b = 0; b < BANDS; b++) {
                double r = Math.sin((b + 1) * 12.9898 + seed * 78.233) * 43758.5453;
                r = r - Math.floor(r);
                int dx = Math.round((float) (r * 2.0 - 1.0) * 60.0F * spread);
                int y = Y0 + b * bandH;
                g.enableScissor(0, y, g.guiWidth(), y + bandH);
                draw(logo, g, dx, 0, a);
                g.disableScissor();
            }
        } else {
            draw(logo, g, 0, 0, a);
        }

        // scan line sweeping across the logo
        long scanAge = (age % SCAN_EVERY_MS);
        if (age > INTRO_MS && scanAge < SCAN_MS) {
            float st = scanAge / (float) SCAN_MS;
            int sy = Y0 + Math.round(H * st);
            float sa = (1.0F - Math.abs(st * 2.0F - 1.0F)) * a;
            g.fill(X0, sy, X0 + W, sy + 1, Draw.argb(0xFFFFFF, 0.55F * sa));
            g.fill(X0, sy - 2, X0 + W, sy, Draw.argb(Draw.YELLOW, 0.20F * sa));
        }

        g.pose().popPose();
    }

    private static void draw(LogoRenderer logo, GuiGraphics g, int dx, int dy, float alpha) {
        g.pose().pushPose();
        g.pose().translate(dx, dy, 0.0F);
        logo.renderLogo(g, TitleLayout.LOGO_FAKE_WIDTH, alpha);
        g.pose().popPose();
    }

    /** Draws the logo multiplied by a colour (flushes so the shader colour applies). */
    private static void tinted(LogoRenderer logo, GuiGraphics g, float r, float gr, float b, float a,
                               int dx, int dy, float alpha) {
        if (a <= 0.01F) {
            return;
        }
        g.flush();
        RenderSystem.setShaderColor(r, gr, b, a);
        draw(logo, g, dx, dy, alpha);
        g.flush();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
