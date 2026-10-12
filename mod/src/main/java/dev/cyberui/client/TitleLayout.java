package dev.cyberui.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;

/**
 * Title screen: buttons in a left-aligned column that slide in one after
 * another, the SYS//STAT panel on the right and a hazard strip at the bottom.
 */
public final class TitleLayout {

    /** Fake screen width passed to the logo/splash renderers so they sit at the left. */
    public static final int LOGO_FAKE_WIDTH = 2 * (18 + 128);

    private static final int COLUMN_X = 18;
    private static final int LOGO_BOTTOM = 90;
    private static final int COLUMN_W = 160;
    private static final int ICON_X = COLUMN_X + COLUMN_W + 4;
    private static final int SLIDE_DISTANCE = -240;
    private static final int SLIDE_MS = 520;
    private static final int STAGGER_MS = 90;

    private static final ResourceLocation STATUS_PANEL =
            ResourceLocation.fromNamespaceAndPath("cyberui", "textures/gui/status_panel.png");
    private static final int PANEL_W = 140;
    private static final int PANEL_H = 92;
    private static final int PANEL_FRAMES = 16;

    /** A widget moved by the layout: target position and slide-in delay. */
    public record Placed(AbstractWidget widget, int x, int y, int delayMs) {
    }

    private TitleLayout() {
    }

    private static String key(AbstractWidget w) {
        if (w.getMessage() != null && w.getMessage().getContents() instanceof TranslatableContents tc) {
            return tc.getKey();
        }
        return "";
    }

    /** Re-positions the title screen buttons; returns what was placed. */
    public static List<Placed> apply(List<? extends GuiEventListener> children, int width, int height) {
        List<AbstractWidget> column = new ArrayList<>();
        AbstractWidget options = null;
        AbstractWidget quit = null;
        List<AbstractWidget> icons = new ArrayList<>();

        for (GuiEventListener l : children) {
            if (!(l instanceof AbstractWidget w) || !w.visible) {
                continue;
            }
            String k = key(w);
            switch (k) {
                case "menu.singleplayer", "menu.multiplayer", "menu.online", "menu.playdemo",
                     "menu.resetdemo", "modmenu.title", "fml.menu.mods" -> column.add(w);
                case "menu.options" -> options = w;
                case "menu.quit" -> quit = w;
                case "options.language", "narrator.button.language",
                     "options.accessibility", "narrator.button.accessibility" -> icons.add(w);
                default -> {
                    if (w.getWidth() == 20 && w.getHeight() == 20) {
                        // small square buttons left/right of Options and Quit are the icon buttons
                        icons.add(w);
                    } else if (w.getHeight() == 20 && w.getWidth() >= 150) {
                        // wide buttons added by other mods (e.g. Mod Menu) join the column
                        column.add(w);
                    }
                }
            }
        }
        column.sort(Comparator.comparingInt(AbstractWidget::getY));
        icons.sort(Comparator.comparingInt(AbstractWidget::getX));

        List<Placed> placed = new ArrayList<>();
        // start below the logo (logo + edition end at about y = 84) when the screen is short
        int rows = column.size() + (options != null ? 1 : 0) + (quit != null ? 1 : 0);
        int total = rows * 24 + 6;
        int y = Math.max(height / 2 - 46, LOGO_BOTTOM);
        y = Math.min(y, Math.max(LOGO_BOTTOM - 20, height - 16 - total));
        int n = 0;
        for (AbstractWidget w : column) {
            placed.add(place(w, COLUMN_X, y, COLUMN_W, 120 + n * STAGGER_MS));
            y += 24;
            n++;
        }
        y += 6;
        int bottomRow = y;
        if (options != null) {
            placed.add(place(options, COLUMN_X, y, COLUMN_W, 120 + n * STAGGER_MS));
            y += 24;
            n++;
        }
        if (quit != null) {
            placed.add(place(quit, COLUMN_X, y, COLUMN_W, 120 + n * STAGGER_MS));
            n++;
        }
        int iy = bottomRow;
        for (AbstractWidget w : icons) {
            placed.add(place(w, ICON_X, iy, w.getWidth(), 120 + n * STAGGER_MS));
            iy += 24;
        }
        return placed;
    }

    private static Placed place(AbstractWidget w, int x, int y, int width, int delay) {
        w.setWidth(width);
        w.setX(x);
        w.setY(y);
        return new Placed(w, x, y, delay);
    }

    /** Applies the slide-in offset for this frame. */
    public static void animate(List<Placed> placed, long openedAt) {
        long elapsed = Util.getMillis() - openedAt;
        for (Placed p : placed) {
            float t = (elapsed - p.delayMs()) / (float) SLIDE_MS;
            int offset = Math.round(SLIDE_DISTANCE * (1.0F - Draw.easeOut(t)));
            p.widget().setX(p.x() + offset);
        }
    }

    /** SYS//STAT panel sliding in from the right and the bottom hazard strip. */
    public static void renderDecor(GuiGraphics g, int width, int height, long openedAt) {
        long now = Util.getMillis();
        float t = (now - openedAt - 350L) / 520.0F;
        int slide = Math.round(220 * (1.0F - Draw.easeOut(t)));
        int x = width - PANEL_W - 16 + slide;
        int y = height / 2 - PANEL_H / 2;
        int frame = (int) ((now / 120L) % PANEL_FRAMES);
        g.blit(RenderType::guiTextured, STATUS_PANEL, x, y, 0.0F, (float) (frame * PANEL_H),
                PANEL_W, PANEL_H, PANEL_W, PANEL_H * PANEL_FRAMES);
        Draw.hazard(g, 0, height - 3, width, 3, 1.0F, now);
    }
}
