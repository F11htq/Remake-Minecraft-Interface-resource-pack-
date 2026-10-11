package dev.cyberui.client;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;

/**
 * Screen transition for menu screens: a dark scanline shutter covers the new
 * screen and slides up, its glowing yellow/red edge sweeping across the menu.
 * Inventories and other in-game screens are never touched.
 */
public final class Transition {

    private static final long HOLD_MS = 90L;
    private static final long SLIDE_MS = 620L;

    private static Screen current;
    private static long openedAt;

    private Transition() {
    }

    public static void onScreenSet(Screen screen) {
        if (screen != null && screen != current && isMenu(screen)) {
            current = screen;
            openedAt = Util.getMillis();
        } else if (screen == null) {
            current = null;
        }
    }

    private static boolean isMenu(Screen s) {
        if (s instanceof AbstractContainerScreen<?>) {
            return false;
        }
        return s instanceof TitleScreen || s instanceof PauseScreen || s instanceof OptionsScreen
                || s instanceof OptionsSubScreen || s instanceof SelectWorldScreen || s instanceof CreateWorldScreen
                || s instanceof JoinMultiplayerScreen || s instanceof PackSelectionScreen;
    }

    public static void render(Screen screen, GuiGraphics g) {
        if (screen != current) {
            return;
        }
        long elapsed = Util.getMillis() - openedAt;
        if (elapsed > HOLD_MS + SLIDE_MS) {
            return;
        }
        int w = g.guiWidth();
        int h = g.guiHeight();
        float t = Math.max(0L, elapsed - HOLD_MS) / (float) SLIDE_MS;
        int bottom = Math.round((h + 8) * (1.0F - Draw.easeOut(t)));

        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 500.0F);
        // shutter body with scanlines
        g.fill(0, 0, w, bottom, Draw.argb(Draw.BG, 1.0F));
        for (int y = 0; y < bottom; y += 2) {
            g.fill(0, y, w, y + 1, Draw.argb(Draw.BLACK, 1.0F));
        }
        // glowing edge
        g.fill(0, bottom, w, bottom + 1, Draw.argb(Draw.RED, 0.5F));
        g.fill(0, bottom + 1, w, bottom + 2, Draw.argb(Draw.RED, 1.0F));
        g.fill(0, bottom + 2, w, bottom + 4, Draw.argb(Draw.YELLOW, 1.0F));
        g.fill(0, bottom + 4, w, bottom + 5, Draw.argb(Draw.YELLOW, 0.6F));
        g.fill(0, bottom + 5, w, bottom + 6, Draw.argb(Draw.YELLOW, 0.25F));
        // glitch sparks on the edge
        long seed = elapsed / 40L;
        for (int i = 0; i < 6; i++) {
            long v = (seed * 6364136223846793005L + i * 1442695040888963407L) >>> 33;
            int x = (int) (v % Math.max(1, w));
            int len = 2 + (int) ((v >>> 8) % 6);
            g.fill(x, bottom + 2, x + len, bottom + 3, Draw.argb(i % 2 == 0 ? Draw.CYAN : 0xFFFFFF, 1.0F));
        }
        g.pose().popPose();
    }
}
