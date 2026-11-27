package dev.cyberui.client;

import net.minecraft.util.Util;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Smooth in-game inventory effects: a slot highlight that glides between slots,
 * a soft reveal when a container screen opens and a fade when the creative tab
 * changes. The glide is drawn in panel
 * coordinates (vanilla translates the pose to the panel), the rest in screen
 * coordinates. Later elements in the GUI render state are layered above
 * earlier overlapping ones, so no z offsets are needed.
 */
public final class InventoryFx {

    private InventoryFx() {
    }

    /** Per-screen animation state for the gliding slot highlight. */
    public static final class Glide {
        private float x;
        private float y;
        private float alpha;
        private long last;

        /**
         * Advances the animation towards the hovered slot and draws it.
         *
         * @param hovered whether a slot is hovered; tx/ty are its top-left corner
         */
        public void render(GuiGraphicsExtractor g, boolean hovered, int tx, int ty) {
            long now = Util.getMillis();
            float dt = this.last == 0L ? 0.016F : Math.min(0.1F, (now - this.last) / 1000.0F);
            this.last = now;

            if (hovered) {
                if (this.alpha < 0.05F) {
                    // appear in place instead of flying in from the last slot
                    this.x = tx;
                    this.y = ty;
                }
                float k = 1.0F - (float) Math.exp(-dt * 22.0F);
                this.x += (tx - this.x) * k;
                this.y += (ty - this.y) * k;
                this.alpha = Math.min(1.0F, this.alpha + dt * 9.0F);
            } else {
                this.alpha = Math.max(0.0F, this.alpha - dt * 7.0F);
            }
            if (this.alpha <= 0.01F) {
                return;
            }

            int x = Math.round(this.x);
            int y = Math.round(this.y);
            float a = this.alpha;
            // soft fill over the slot
            g.fill(x, y, x + 16, y + 16, Draw.argb(0xFFFFFF, 0.16F * a));
            // glow just outside the slot
            g.fill(x - 1, y - 1, x + 17, y, Draw.argb(Draw.YELLOW, 0.25F * a));
            g.fill(x - 1, y + 16, x + 17, y + 17, Draw.argb(Draw.YELLOW, 0.25F * a));
            g.fill(x - 1, y, x, y + 16, Draw.argb(Draw.YELLOW, 0.25F * a));
            g.fill(x + 16, y, x + 17, y + 16, Draw.argb(Draw.YELLOW, 0.25F * a));
            // targeting brackets that tighten as the highlight settles
            int gap = Math.round(2.0F * (1.0F - a));
            brackets(g, x - 2 - gap, y - 2 - gap, 20 + gap * 2, Draw.argb(Draw.YELLOW, a));
        }
    }

    private static void brackets(GuiGraphicsExtractor g, int x, int y, int size, int color) {
        int len = 4;
        int x1 = x + size - 1;
        int y1 = y + size - 1;
        g.fill(x, y, x + len, y + 1, color);
        g.fill(x, y, x + 1, y + len, color);
        g.fill(x1 - len + 1, y, x1 + 1, y + 1, color);
        g.fill(x1, y, x1 + 1, y + len, color);
        g.fill(x, y1, x + len, y1 + 1, color);
        g.fill(x, y1 - len + 1, x + 1, y1 + 1, color);
        g.fill(x1 - len + 1, y1, x1 + 1, y1 + 1, color);
        g.fill(x1, y1 - len + 1, x1 + 1, y1 + 1, color);
    }

    /** Soft reveal of a container panel during the first ~220 ms after it opens. */
    public static void reveal(GuiGraphicsExtractor g, int x, int y, int w, int h, long openedAt) {
        long elapsed = Util.getMillis() - openedAt;
        if (elapsed > 220L) {
            return;
        }
        float t = elapsed / 220.0F;
        float cover = 1.0F - Draw.easeOut(t);
        g.fill(x, y, x + w, y + h, Draw.argb(Draw.BG, cover * 0.85F));
        int scan = y + Math.round(h * Draw.easeOut(t));
        g.fill(x, scan, x + w, scan + 1, Draw.argb(Draw.YELLOW, cover));
        g.fill(x, scan - 3, x + w, scan, Draw.argb(Draw.YELLOW, cover * 0.25F));
    }

    /** Short fade over a region (e.g. the creative item grid after a tab change). */
    public static void fade(GuiGraphicsExtractor g, int x, int y, int w, int h, long since, long durationMs) {
        long elapsed = Util.getMillis() - since;
        if (elapsed > durationMs) {
            return;
        }
        float t = elapsed / (float) durationMs;
        float cover = 1.0F - Draw.easeOut(t);
        g.fill(x, y, x + w, y + h, Draw.argb(Draw.SLOT, cover * 0.9F));
        int sweep = x + Math.round(w * Draw.easeOut(t));
        g.fill(sweep, y, sweep + 1, y + h, Draw.argb(Draw.YELLOW, cover));
    }
}
