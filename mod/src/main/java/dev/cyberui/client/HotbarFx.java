package dev.cyberui.client;

import java.lang.ref.WeakReference;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The animated CYBER//UI hotbar:
 * <ul>
 *   <li>boot sequence when the hotbar appears (joining a world, respawning):
 *       it rises into place and the slots come online one by one behind a scan line;</li>
 *   <li>the selection frame glides between slots with afterimages and a
 *       red/cyan split, its corner brackets lock on when it arrives;</li>
 *   <li>the selected item lifts slightly, a slot number flashes above it;</li>
 *   <li>picked-up items flash their slot, nearly broken tools pulse red;</li>
 *   <li>the item name is typed out on a cyber name plate;</li>
 *   <li>a slow data stream runs along the bottom edge.</li>
 * </ul>
 * The look of the bar itself (hotbar sprites) comes from the resource pack;
 * everything here is drawn on top with plain fills and the selection sprite.
 */
public final class HotbarFx {

    public static final Identifier SELECTION = Identifier.withDefaultNamespace("hud/hotbar_selection");

    private static final long BOOT_MS = 520L;
    private static final long SLOT_STAGGER_MS = 45L;
    private static final long NUMBER_MS = 750L;
    private static final long TYPE_CHAR_MS = 18L;
    private static final int BOOT_RISE = 26;

    private static WeakReference<Player> player = new WeakReference<>(null);
    private static long bootAt = 0L;
    private static long lastFrame = 0L;
    private static float dt = 0.016F;

    private static int selected = -1;
    private static long selectedAt = 0L;
    /** Glided selection position in slots (0..8). */
    private static float glide = 0.0F;
    private static final float[] TRAIL = new float[3];
    private static float lift = 0.0F;

    private static boolean drawnThisFrame = false;
    private static String plateText = "";
    private static long plateAt = 0L;

    private HotbarFx() {
    }

    // ------------------------------------------------------------------
    // frame state
    // ------------------------------------------------------------------

    /** Called before vanilla draws the hotbar: advances the animations and applies the boot rise. */
    public static void begin(GuiGraphicsExtractor g, Player p) {
        long now = Util.getMillis();
        dt = lastFrame == 0L ? 0.016F : Math.min(0.1F, (now - lastFrame) / 1000.0F);
        lastFrame = now;
        int sel = p.getInventory().getSelectedSlot();

        if (player.get() != p) {
            // a new player entity: joined a world or respawned
            player = new WeakReference<>(p);
            bootAt = now;
            selected = sel;
            selectedAt = 0L;
            glide = sel;
            java.util.Arrays.fill(TRAIL, sel);
            lift = 0.0F;
        }
        if (sel != selected) {
            selected = sel;
            selectedAt = now;
        }

        // afterimages remember where the frame was over the last frames
        TRAIL[2] = TRAIL[1];
        TRAIL[1] = TRAIL[0];
        TRAIL[0] = glide;
        float k = 1.0F - (float) Math.exp(-dt * 24.0F);
        glide += (sel - glide) * k;
        if (Math.abs(sel - glide) < 0.002F) {
            glide = sel;
        }
        float targetLift = 2.0F;
        lift += (targetLift - lift) * (1.0F - (float) Math.exp(-dt * 16.0F));

        g.pose().pushMatrix();
        g.pose().translate(0.0F, bootRise(now));
    }

    /** Called after vanilla finished the hotbar. */
    public static void end(GuiGraphicsExtractor g, Player p) {
        long now = Util.getMillis();
        int w = g.guiWidth();
        int h = g.guiHeight();
        int x0 = w / 2 - 91;
        int y0 = h - 22;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = p.getInventory().getItem(i);
            int sx = x0 + 3 + i * 20;
            int sy = y0 + 3;
            // pick-up flash
            float pop = stack.isEmpty() ? 0.0F : stack.getPopTime() / 5.0F;
            if (pop > 0.0F) {
                g.fill(sx, sy, sx + 16, sy + 16, Draw.argb(Draw.YELLOW, Math.min(0.45F, 0.12F * pop)));
            }
            // tools about to break pulse red
            if (stack.isDamageableItem() && stack.getMaxDamage() > 0
                    && stack.getMaxDamage() - stack.getDamageValue() <= Math.max(1, stack.getMaxDamage() / 10)) {
                float pulse = 0.45F + 0.45F * (float) Math.sin(now / 140.0);
                Draw.frame(g, sx - 1, sy - 1, 18, 18, Draw.argb(Draw.RED, pulse));
            }
        }

        dataStream(g, x0, y0 + 21, 182, now);
        boot(g, x0, y0, now);
        g.pose().popMatrix();
        drawnThisFrame = true;
    }

    /**
     * Called after hearts, food and the experience bar are drawn, so the slot
     * number above the selection is not covered by them.
     */
    public static void overlay(GuiGraphicsExtractor g) {
        if (!drawnThisFrame) {
            return;
        }
        drawnThisFrame = false;
        long now = Util.getMillis();
        g.pose().pushMatrix();
        g.pose().translate(0.0F, bootRise(now));
        slotNumber(g, g.guiWidth() / 2 - 91, g.guiHeight() - 22, now);
        g.pose().popMatrix();
    }

    private static float bootRise(long now) {
        float t = (now - bootAt) / (float) BOOT_MS;
        return t >= 1.0F ? 0.0F : BOOT_RISE * (1.0F - Draw.easeOut(t));
    }

    // ------------------------------------------------------------------
    // selection
    // ------------------------------------------------------------------

    /**
     * Replaces the vanilla selection sprite: drawn at the glided position with
     * afterimages, a colour split while moving and lock-on brackets.
     */
    public static void selection(GuiGraphicsExtractor g, int vanillaX, int y, int width, int height) {
        long now = Util.getMillis();
        // vanillaX is for the selected slot; derive the bar origin from it
        float base = vanillaX - selected * 20.0F;
        float speed = Math.abs(TRAIL[0] - glide) / Math.max(0.001F, dt) / 20.0F;
        float moving = Draw.clamp(speed / 6.0F, 0.0F, 1.0F);

        // afterimages, oldest first
        for (int i = TRAIL.length - 1; i >= 0; i--) {
            if (Math.abs(TRAIL[i] - glide) < 0.05F) {
                continue;
            }
            float a = (0.32F - i * 0.09F) * moving;
            sprite(g, base + TRAIL[i] * 20.0F, y, width, height, Draw.argb(i % 2 == 0 ? Draw.CYAN : Draw.RED, a));
        }
        float x = base + glide * 20.0F;
        if (moving > 0.05F) {
            sprite(g, x - 1.5F, y, width, height, Draw.argb(Draw.RED, 0.5F * moving));
            sprite(g, x + 1.5F, y, width, height, Draw.argb(Draw.CYAN, 0.5F * moving));
        }
        sprite(g, x, y, width, height, 0xFFFFFFFF);

        // corner brackets close in when the frame settles on a slot
        long since = now - selectedAt;
        float settle = selectedAt == 0L ? 1.0F : Draw.easeOut(since / 220.0F);
        int gap = Math.round(3.0F * (1.0F - settle));
        int bx = Math.round(x) - 1 - gap;
        int by = y - 1 - gap;
        int bw = width + 2 + gap * 2;
        int bh = height + 2 + gap * 2;
        int c = Draw.argb(Draw.YELLOW, 0.55F + 0.45F * settle);
        brackets(g, bx, by, bw, bh, c);

        // a bright scan running down the slot right after switching
        if (selectedAt != 0L && since < 260L) {
            float t = since / 260.0F;
            int sx = Math.round(x) + 4;
            int sy = y + 3 + Math.round(15 * Draw.easeOut(t));
            g.fill(sx, sy, sx + 16, sy + 1, Draw.argb(0xFFFFFF, 0.7F * (1.0F - t)));
            g.fill(sx, sy - 3, sx + 16, sy, Draw.argb(Draw.YELLOW, 0.25F * (1.0F - t)));
        }
    }

    private static void sprite(GuiGraphicsExtractor g, float x, int y, int w, int h, int color) {
        if ((color >>> 24) < 3) {
            return;
        }
        int ix = (int) Math.floor(x);
        g.pose().pushMatrix();
        g.pose().translate(x - ix, 0.0F);
        g.blitSprite(RenderPipelines.GUI_TEXTURED, SELECTION, ix, y, w, h, color);
        g.pose().popMatrix();
    }

    private static void brackets(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        int len = 4;
        int x1 = x + w - 1;
        int y1 = y + h - 1;
        g.fill(x, y, x + len, y + 1, color);
        g.fill(x, y, x + 1, y + len, color);
        g.fill(x1 - len + 1, y, x1 + 1, y + 1, color);
        g.fill(x1, y, x1 + 1, y + len, color);
        g.fill(x, y1, x + len, y1 + 1, color);
        g.fill(x, y1 - len + 1, x + 1, y1 + 1, color);
        g.fill(x1 - len + 1, y1, x1 + 1, y1 + 1, color);
        g.fill(x1, y1 - len + 1, x1 + 1, y1 + 1, color);
    }

    // ------------------------------------------------------------------
    // items
    // ------------------------------------------------------------------

    /** Vertical offset for an item in the hotbar: the selected one rides a little higher. */
    public static float itemLift(int slotX, int screenWidth) {
        int x = screenWidth / 2 - 90 + selected * 20 + 2;
        if (slotX != x) {
            return 0.0F;
        }
        long since = Util.getMillis() - selectedAt;
        // a small hop when it becomes selected
        float hop = selectedAt != 0L && since < 240L ? (float) Math.sin(since / 240.0 * Math.PI) * 1.5F : 0.0F;
        return lift + hop;
    }

    // ------------------------------------------------------------------
    // decorations
    // ------------------------------------------------------------------

    /** Slot number ("03") above the selection for a moment after switching. */
    private static void slotNumber(GuiGraphicsExtractor g, int x0, int y0, long now) {
        long since = now - selectedAt;
        if (selectedAt == 0L || since > NUMBER_MS) {
            return;
        }
        float t = since / (float) NUMBER_MS;
        float a = t < 0.7F ? 1.0F : 1.0F - (t - 0.7F) / 0.3F;
        String label = "0" + (selected + 1);
        int cx = Math.round(x0 + glide * 20.0F) + 11;
        int y = y0 - 5 - Math.round(2.0F * Draw.easeOut(t * 2.0F));
        int tw = Draw.textWidth(label, 1);
        g.fill(cx - tw / 2 - 2, y - 2, cx + tw / 2 + 3, y + 7, Draw.argb(Draw.BG, 0.8F * a));
        g.fill(cx - tw / 2 - 2, y + 7, cx + tw / 2 + 3, y + 8, Draw.argb(Draw.YELLOW, a));
        Draw.text(g, cx - tw / 2, y, label, 1, Draw.argb(Draw.YELLOW, a));
    }

    /** Thin data stream running along the bottom edge of the bar. */
    private static void dataStream(GuiGraphicsExtractor g, int x, int y, int w, long now) {
        int shift = (int) ((now / 45L) % 24L);
        int c = Draw.argb(Draw.YELLOW, 0.55F);
        for (int i = -shift; i < w; i += 24) {
            int a = Math.max(0, i);
            int b = Math.min(w, i + 7);
            if (b > a) {
                g.fill(x + a, y, x + b, y + 1, c);
            }
        }
        // a red packet now and then
        int p = (int) ((now / 9L) % (w + 400)) - 200;
        if (p >= 0 && p < w - 3) {
            g.fill(x + p, y, x + p + 3, y + 1, Draw.argb(Draw.RED, 0.9F));
        }
    }

    /** Boot sequence: slots are covered and come online one after another behind a scan line. */
    private static void boot(GuiGraphicsExtractor g, int x0, int y0, long now) {
        long age = now - bootAt;
        long total = 160L + 9 * SLOT_STAGGER_MS + 160L;
        if (age > total) {
            return;
        }
        for (int i = 0; i < 9; i++) {
            long t = age - 160L - i * SLOT_STAGGER_MS;
            float cover = t <= 0L ? 1.0F : 1.0F - Draw.easeOut(t / 160.0F);
            if (cover <= 0.01F) {
                continue;
            }
            int sx = x0 + 1 + i * 20;
            g.fill(sx, y0 + 1, sx + 20, y0 + 21, Draw.argb(Draw.BG, 0.92F * cover));
            if (t > 0L) {
                int line = y0 + 1 + Math.round(20 * (1.0F - cover));
                g.fill(sx, line, sx + 20, line + 1, Draw.argb(Draw.YELLOW, cover));
            }
        }
    }

    // ------------------------------------------------------------------
    // item name
    // ------------------------------------------------------------------

    /** Replaces vanilla's backdrop text with a name plate where the name is typed out. */
    public static void namePlate(GuiGraphicsExtractor g, Font font, Component name, int x, int y, int width, int color) {
        float alpha = (color >>> 24) / 255.0F;
        if (alpha <= 0.01F) {
            return;
        }
        long now = Util.getMillis();
        String plain = name.getString();
        if (!plain.equals(plateText)) {
            plateText = plain;
            plateAt = now;
        }
        long age = now - plateAt;
        int chars = (int) Math.min(plain.length(), age / TYPE_CHAR_MS + 1);
        Component shown = chars >= plain.length() ? name : Component.literal(plain.substring(0, chars)).withStyle(name.getStyle());

        Integer rgb = name.getStyle().getColor() != null ? name.getStyle().getColor().getValue() : null;
        int edge = rgb != null && rgb != 0xFFFFFF ? rgb : Draw.YELLOW;
        // the plate opens from the centre
        float open = Draw.easeOut(age / 160.0F);
        int half = Math.round((width / 2.0F + 7) * open);
        int cx = x + width / 2;
        g.fill(cx - half, y - 3, cx + half, y + 11, Draw.argb(0x07060C, 0.82F * alpha));
        g.fill(cx - half, y + 11, cx + half, y + 12, Draw.argb(edge, alpha));
        g.fill(cx - half, y - 3, cx - half + 2, y + 11, Draw.argb(Draw.YELLOW, alpha));
        g.fill(cx + half - 3, y - 3, cx + half, y - 2, Draw.argb(Draw.RED, alpha));
        if (open < 1.0F) {
            return;
        }
        // short colour split while typing
        boolean typing = chars < plain.length();
        if (typing) {
            g.text(font, shown, x - 1, y, Draw.argb(Draw.CYAN, 0.6F * alpha), false);
            g.text(font, shown, x + 1, y, Draw.argb(Draw.RED, 0.6F * alpha), false);
        }
        g.text(font, shown, x, y, color, true);
        if (typing && (now / 120L) % 2L == 0L) {
            int cw = font.width(shown);
            g.fill(x + cw + 1, y, x + cw + 4, y + 8, Draw.argb(Draw.YELLOW, alpha));
        }
    }
}
