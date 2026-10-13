package dev.cyberui.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.BossEvent;

/**
 * Boss bars with a unique animated frame per boss (Ender Dragon, Wither, raid,
 * a generic cyberpunk frame for everything else), damage trail, hit shake,
 * low-health pulse and per-boss particles, plus a 3D "GG" victory animation.
 */
public final class BossBarFx {

    private static final int FRAME_W = 256;
    private static final int FRAME_H = 64;
    private static final int BAR_X = 37;
    private static final int BAR_Y = 30;
    private static final int BAR_W = 182;
    private static final int BAR_H = 5;
    private static final int ENTRY_H = 60;

    /** Visual style of a boss bar. */
    private enum Style {
        GENERIC("boss_generic", 0xFCEE0A),
        DRAGON("boss_dragon", 0xC83CFF),
        WITHER("boss_wither", 0x965AFF),
        RAID("boss_raid", 0xFF283C);

        final ResourceLocation texture;
        /** Ornaments drawn over the fill (head, skulls, axe, optic). */
        final ResourceLocation front;
        final int neon;

        Style(String tex, int neon) {
            this.texture = ResourceLocation.fromNamespaceAndPath("cyberui", "textures/gui/" + tex + ".png");
            this.front = ResourceLocation.fromNamespaceAndPath("cyberui", "textures/gui/" + tex + "_front.png");
            this.neon = neon;
        }
    }

    /** One particle in frame-local coordinates. */
    private static final class Particle {
        float x;
        float y;
        float vx;
        float vy;
        float life;
        float maxLife;
        int color;
        int size;
    }

    /** Animation state of one boss bar. */
    private static final class State {
        Style style = Style.GENERIC;
        float shown = -1.0F;
        float trail = -1.0F;
        long lastHit = 0L;
        boolean seenAlive = false;
        boolean triggered = false;
        String name = "";
        final List<Particle> particles = new ArrayList<>();
        float spawnAcc = 0.0F;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();
    private static final Random RANDOM = new Random();
    private static long lastFrame = 0L;

    // victory animation
    private static long victoryAt = 0L;
    private static String victoryTitle = "";
    private static String victoryName = "";
    private static int victoryColor = Draw.YELLOW;

    private BossBarFx() {
    }

    // ------------------------------------------------------------------
    // entry point
    // ------------------------------------------------------------------
    public static void render(GuiGraphics g, Map<UUID, LerpingBossEvent> events) {
        long now = Util.getMillis();
        float dt = lastFrame == 0L ? 0.016F : Math.min(0.1F, (now - lastFrame) / 1000.0F);
        lastFrame = now;
        Font font = Minecraft.getInstance().font;
        int sw = g.guiWidth();
        int sh = g.guiHeight();

        Set<UUID> alive = new HashSet<>();
        int y = 4;
        for (LerpingBossEvent event : events.values()) {
            alive.add(event.getId());
            State st = STATES.computeIfAbsent(event.getId(), id -> new State());
            update(st, event, now, dt);
            if (y < sh / 3) {
                renderBar(g, font, event, st, sw / 2 - FRAME_W / 2, y, now, dt);
            }
            y += ENTRY_H;
        }
        // bars that vanished while almost dead also count as a win
        STATES.entrySet().removeIf(e -> {
            if (alive.contains(e.getKey())) {
                return false;
            }
            State st = e.getValue();
            if (!st.triggered && st.seenAlive && st.style != Style.RAID && st.shown < 0.06F) {
                startVictory("BOSS DEFEATED", st.name, st.style.neon);
            }
            return true;
        });

        renderVictory(g, font, sw, sh, now);
    }

    // ------------------------------------------------------------------
    // state
    // ------------------------------------------------------------------
    private static void collectKeys(Component c, Set<String> out) {
        if (c.getContents() instanceof TranslatableContents tc) {
            out.add(tc.getKey());
            for (Object arg : tc.getArgs()) {
                if (arg instanceof Component ac) {
                    collectKeys(ac, out);
                }
            }
        }
        for (Component sib : c.getSiblings()) {
            collectKeys(sib, out);
        }
    }

    private static void update(State st, LerpingBossEvent event, long now, float dt) {
        Set<String> keys = new HashSet<>();
        collectKeys(event.getName(), keys);
        if (keys.contains("entity.minecraft.ender_dragon")) {
            st.style = Style.DRAGON;
        } else if (keys.contains("entity.minecraft.wither")) {
            st.style = Style.WITHER;
        } else if (keys.contains("event.minecraft.raid")) {
            st.style = Style.RAID;
        } else {
            st.style = Style.GENERIC;
        }
        st.name = event.getName().getString();

        float p = Draw.clamp(event.getProgress(), 0.0F, 1.0F);
        if (st.shown < 0.0F) {
            st.shown = p;
            st.trail = p;
        }
        if (p < st.shown - 0.002F) {
            st.lastHit = now;
        }
        st.shown = p;
        // the damage trail waits a moment, then catches up smoothly
        if (st.trail < st.shown) {
            st.trail = st.shown;
        } else if (now - st.lastHit > 350L) {
            st.trail += (st.shown - st.trail) * (1.0F - (float) Math.exp(-dt * 6.0F));
        }
        if (p > 0.05F) {
            st.seenAlive = true;
        }

        if (!st.triggered && st.seenAlive) {
            if (st.style == Style.RAID) {
                if (keys.contains("event.minecraft.raid.victory")) {
                    st.triggered = true;
                    startVictory("RAID VICTORY", "", Style.RAID.neon);
                }
            } else if (p <= 0.002F) {
                st.triggered = true;
                startVictory("BOSS DEFEATED", st.name, st.style.neon);
            }
        }
    }

    // ------------------------------------------------------------------
    // bar
    // ------------------------------------------------------------------
    private static int barColor(BossEvent.BossBarColor c) {
        return switch (c) {
            case PINK -> 0xFF3CB4;
            case BLUE -> 0x00C8FF;
            case RED -> 0xFF003C;
            case GREEN -> 0x3CFF78;
            case YELLOW -> 0xFCEE0A;
            case PURPLE -> 0xAA50FF;
            case WHITE -> 0xE6EBFF;
        };
    }

    private static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int gg = Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bb = Math.round((a & 255) + ((b & 255) - (a & 255)) * t);
        return (r << 16) | (gg << 8) | bb;
    }

    private static void renderBar(GuiGraphics g, Font font, LerpingBossEvent event, State st,
                                  int fx, int fy, long now, float dt) {
        Style style = st.style;
        int fill = style == Style.GENERIC ? barColor(event.getColor()) : style.neon;

        // hit shake
        long sinceHit = now - st.lastHit;
        if (sinceHit < 220L) {
            float k = 1.0F - sinceHit / 220.0F;
            fx += Math.round((RANDOM.nextFloat() * 2.0F - 1.0F) * 2.0F * k);
            fy += Math.round((RANDOM.nextFloat() * 2.0F - 1.0F) * 1.5F * k);
        }

        // low health pulse around the casing
        if (st.shown < 0.25F && st.shown > 0.0F) {
            float pulse = 0.5F + 0.5F * (float) Math.sin(now / 130.0);
            int c = Draw.argb(Draw.RED, 0.35F * pulse);
            g.fill(fx + BAR_X - 6, fy + BAR_Y - 5, fx + BAR_X + BAR_W + 6, fy + BAR_Y - 4, c);
            g.fill(fx + BAR_X - 6, fy + BAR_Y + BAR_H + 4, fx + BAR_X + BAR_W + 6, fy + BAR_Y + BAR_H + 5, c);
        }

        // glitch: colour-split copies of the frame now and then
        long gl = (now + event.getId().hashCode()) % 5200L;
        if (gl < 120L) {
            int d = gl < 60L ? 2 : -2;
            frame(g, style, fx - d, fy, Draw.argb(Draw.CYAN, 0.55F));
            frame(g, style, fx + d, fy, Draw.argb(Draw.RED, 0.55F));
        }
        frame(g, style, fx, fy, 0xFFFFFFFF);

        // fill: damage trail first, then the live bar with crawling stripes
        int bx = fx + BAR_X;
        int by = fy + BAR_Y;
        int shownW = Math.round(BAR_W * st.shown);
        int trailW = Math.round(BAR_W * st.trail);
        if (trailW > shownW) {
            g.fill(bx + shownW, by, bx + trailW, by + BAR_H, Draw.argb(mix(fill, 0xFFFFFF, 0.7F), 0.85F));
        }
        if (shownW > 0) {
            int[] rows = {mix(fill, 0xFFFFFF, 0.55F), fill, fill, mix(fill, 0x000000, 0.25F), mix(fill, 0x000000, 0.5F)};
            for (int r = 0; r < BAR_H; r++) {
                g.fill(bx, by + r, bx + shownW, by + r + 1, Draw.argb(rows[r], 1.0F));
            }
            int shift = (int) ((now / 70L) % 6L);
            int stripe = Draw.argb(0x000000, 0.28F);
            for (int x = -shift; x < shownW; x += 6) {
                int x0 = Math.max(0, x);
                int x1 = Math.min(shownW, x + 3);
                if (x1 > x0) {
                    g.fill(bx + x0, by + 1, bx + x1, by + BAR_H - 1, stripe);
                }
            }
            // bright head at the end of the bar
            g.fill(bx + shownW - 1, by, bx + shownW, by + BAR_H, Draw.argb(0xFFFFFF, 0.9F));
        }
        // hit flash
        if (sinceHit < 120L) {
            g.fill(bx, by, bx + BAR_W, by + BAR_H, Draw.argb(0xFFFFFF, 0.5F * (1.0F - sinceHit / 120.0F)));
        }
        // Wither below half health: armour shield sweeping over the bar
        if (style == Style.WITHER && st.shown < 0.5F && st.shown > 0.0F) {
            int sweep = (int) ((now / 8L) % (BAR_W + 40)) - 20;
            for (int d = 0; d < 16; d++) {
                int x = sweep - d;
                if (x >= 0 && x < BAR_W) {
                    g.fill(bx + x, by, bx + x + 1, by + BAR_H, Draw.argb(0xC8DCFF, 0.35F * (1.0F - d / 16.0F)));
                }
            }
        }
        notches(g, event.getOverlay(), bx, by);
        g.blit(RenderType::guiTextured, style.front, fx, fy, 0.0F, 0.0F, FRAME_W, FRAME_H, FRAME_W, FRAME_H, 0xFFFFFFFF);

        particles(g, st, fx, fy, now, dt);

        // name under the casing, in the boss colour
        Component name = event.getName();
        int tw = font.width(name);
        int tx = fx + FRAME_W / 2 - tw / 2;
        int ty = fy + BAR_Y + BAR_H + 13;
        g.drawString(font, name, tx, ty, Draw.argb(mix(fill, 0xFFFFFF, 0.35F), 1.0F), true);
    }

    private static void frame(GuiGraphics g, Style style, int x, int y, int color) {
        g.blit(RenderType::guiTextured, style.texture, x, y, 0.0F, 0.0F, FRAME_W, FRAME_H, FRAME_W, FRAME_H, color);
    }

    private static void notches(GuiGraphics g, BossEvent.BossBarOverlay overlay, int bx, int by) {
        int n = switch (overlay) {
            case NOTCHED_6 -> 6;
            case NOTCHED_10 -> 10;
            case NOTCHED_12 -> 12;
            case NOTCHED_20 -> 20;
            default -> 0;
        };
        for (int i = 1; i < n; i++) {
            int x = bx + Math.round(i * (BAR_W - 1) / (float) n);
            g.fill(x, by, x + 1, by + BAR_H, Draw.argb(0x000000, 0.85F));
            g.fill(x, by - 2, x + 1, by - 1, Draw.argb(Draw.YELLOW, 0.8F));
        }
    }

    // ------------------------------------------------------------------
    // particles
    // ------------------------------------------------------------------
    private static void particles(GuiGraphics g, State st, int fx, int fy, long now, float dt) {
        float rate = switch (st.style) {
            case DRAGON -> 26.0F;
            case WITHER -> 18.0F;
            case RAID -> 14.0F;
            case GENERIC -> 6.0F;
        };
        st.spawnAcc += rate * dt;
        while (st.spawnAcc >= 1.0F && st.particles.size() < 48) {
            st.spawnAcc -= 1.0F;
            st.particles.add(spawn(st.style));
        }
        for (int i = st.particles.size() - 1; i >= 0; i--) {
            Particle p = st.particles.get(i);
            p.life += dt;
            if (p.life >= p.maxLife) {
                st.particles.remove(i);
                continue;
            }
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            float a = 1.0F - p.life / p.maxLife;
            int x = fx + Math.round(p.x);
            int y = fy + Math.round(p.y);
            g.fill(x, y, x + p.size, y + p.size, Draw.argb(p.color, a * 0.9F));
        }
    }

    private static Particle spawn(Style style) {
        Particle p = new Particle();
        boolean left = RANDOM.nextBoolean();
        switch (style) {
            case DRAGON -> {
                // sparks rising from the wing tips
                p.x = left ? 6 + RANDOM.nextFloat() * 24 : FRAME_W - 30 + RANDOM.nextFloat() * 24;
                p.y = 6 + RANDOM.nextFloat() * 14;
                p.vx = (left ? -1 : 1) * (2 + RANDOM.nextFloat() * 6);
                p.vy = -6 - RANDOM.nextFloat() * 10;
                p.color = RANDOM.nextInt(3) == 0 ? 0xFFE678 : (RANDOM.nextBoolean() ? 0xFF50DC : 0xC83CFF);
                p.size = 1;
                p.maxLife = 0.9F + RANDOM.nextFloat() * 0.8F;
            }
            case WITHER -> {
                // dark smoke drifting up from the casing
                p.x = BAR_X + RANDOM.nextFloat() * BAR_W;
                p.y = BAR_Y - 2;
                p.vx = (RANDOM.nextFloat() * 2 - 1) * 4;
                p.vy = -4 - RANDOM.nextFloat() * 6;
                p.color = RANDOM.nextInt(4) == 0 ? 0x965AFF : 0x2A2232;
                p.size = RANDOM.nextInt(3) == 0 ? 2 : 1;
                p.maxLife = 1.2F + RANDOM.nextFloat() * 0.8F;
            }
            case RAID -> {
                // embers from the banners
                p.x = left ? 12 + RANDOM.nextFloat() * 12 : FRAME_W - 24 + RANDOM.nextFloat() * 12;
                p.y = 30;
                p.vx = (RANDOM.nextFloat() * 2 - 1) * 5;
                p.vy = -8 - RANDOM.nextFloat() * 10;
                p.color = RANDOM.nextBoolean() ? 0xFF7828 : 0xFFC850;
                p.size = 1;
                p.maxLife = 0.8F + RANDOM.nextFloat() * 0.8F;
            }
            case GENERIC -> {
                // glitch pixels along the casing
                p.x = BAR_X - 4 + RANDOM.nextFloat() * (BAR_W + 8);
                p.y = RANDOM.nextBoolean() ? BAR_Y - 4 : BAR_Y + BAR_H + 3;
                p.vx = (RANDOM.nextFloat() * 2 - 1) * 20;
                p.vy = 0;
                p.color = RANDOM.nextBoolean() ? Draw.YELLOW : Draw.CYAN;
                p.size = 1;
                p.maxLife = 0.25F + RANDOM.nextFloat() * 0.3F;
            }
        }
        return p;
    }

    // ------------------------------------------------------------------
    // victory: 3D "GG"
    // ------------------------------------------------------------------
    private static void startVictory(String title, String name, int color) {
        victoryAt = Util.getMillis();
        victoryTitle = title;
        victoryName = name;
        victoryColor = color;
    }

    private static final String[] GLYPH_G = {
        ".######.",
        "##....##",
        "##......",
        "##......",
        "##..####",
        "##....##",
        "##....##",
        ".######.",
    };

    private static final long VICTORY_MS = 3800L;

    private static void renderVictory(GuiGraphics g, Font font, int sw, int sh, long now) {
        if (victoryAt == 0L) {
            return;
        }
        long t = now - victoryAt;
        if (t > VICTORY_MS) {
            victoryAt = 0L;
            return;
        }
        float fade = t > VICTORY_MS - 600L ? (VICTORY_MS - t) / 600.0F : 1.0F;
        int cx = sw / 2;
        int cy = Math.round(sh * 0.30F);
        int scale = Math.max(3, Math.min(6, sh / 60));

        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 300.0F);

        // light beams behind the letters
        for (int i = -5; i <= 5; i++) {
            float k = Draw.easeOut(t / 500.0F);
            int bx = cx + i * 14 * scale / 5;
            int bh = Math.round(sh * 0.22F * k);
            float a = (0.10F + 0.06F * (float) Math.sin(now / 120.0 + i)) * fade;
            g.fill(bx, cy - bh, bx + 1, cy + bh, Draw.argb(victoryColor, a));
        }
        // shockwave: expanding chamfered frame
        if (t < 900L) {
            float k = Draw.easeOut(t / 900.0F);
            int rw = Math.round(40 + k * sw * 0.45F);
            int rh = Math.round(12 + k * 70);
            float a = (1.0F - k) * fade;
            Draw.frame(g, cx - rw / 2, cy - rh / 2, rw, rh, Draw.argb(Draw.YELLOW, a));
            Draw.frame(g, cx - rw / 2 - 2, cy - rh / 2 - 2, rw + 4, rh + 4, Draw.argb(victoryColor, a * 0.5F));
        }

        // the two G letters spin in one after another, extruded for depth
        int letterW = 8 * scale;
        int gap = 3 * scale;
        for (int i = 0; i < 2; i++) {
            long lt = t - i * 140L;
            if (lt < 0L) {
                continue;
            }
            float k = Draw.easeOut(lt / 550.0F);
            float angle = 100.0F * (1.0F - k) * (i == 0 ? -1 : 1);
            float zoom = 2.2F - 1.2F * k;
            // a slow idle sway once settled
            if (lt > 550L) {
                angle = (float) Math.sin((now - victoryAt) / 380.0 + i) * 12.0F;
            }
            float lx = cx + (i == 0 ? -(letterW + gap) / 2.0F : (letterW + gap) / 2.0F);
            g.pose().pushPose();
            g.pose().translate(lx, cy, 0.0F);
            g.pose().mulPose(Axis.YP.rotationDegrees(angle));
            g.pose().scale(zoom, zoom, 1.0F);
            drawLetter(g, -letterW / 2, -letterW / 2, scale, fade * Math.min(1.0F, lt / 150.0F), now);
            g.pose().popPose();
        }

        // glitch slices across the letters
        if ((t % 900L) < 90L && t > 600L) {
            int sy = cy - letterW / 2 + (int) ((t / 30L) % Math.max(1, letterW));
            g.fill(cx - letterW - gap, sy, cx + letterW + gap, sy + 2, Draw.argb(Draw.CYAN, 0.6F * fade));
            g.fill(cx - letterW - gap + 4, sy + 3, cx + letterW + gap + 4, sy + 4, Draw.argb(Draw.RED, 0.6F * fade));
        }

        // title types itself out, boss name below
        if (t > 450L) {
            int chars = (int) Math.min(victoryTitle.length(), (t - 450L) / 35L);
            String title = victoryTitle.substring(0, chars);
            int ty = cy + letterW + 8;
            int tw = font.width(title);
            g.fill(cx - tw / 2 - 4, ty - 2, cx + tw / 2 + 4, ty + 10, Draw.argb(Draw.BG, 0.75F * fade));
            g.drawString(font, title, cx - tw / 2, ty, Draw.argb(Draw.YELLOW, fade), true);
            if (!victoryName.isEmpty() && t > 900L) {
                int nw = font.width(victoryName);
                g.drawString(font, victoryName, cx - nw / 2, ty + 13, Draw.argb(mix(victoryColor, 0xFFFFFF, 0.4F), fade), true);
            }
        }
        g.pose().popPose();
    }

    private static void drawLetter(GuiGraphics g, int x0, int y0, int s, float alpha, long now) {
        if (alpha <= 0.01F) {
            return;
        }
        int depth = Math.max(3, s);
        // extrusion: darker layers behind the face
        for (int d = depth; d >= 1; d--) {
            int c = mix(0x8C0020, 0x1A0006, d / (float) depth);
            glyph(g, x0 + d, y0 + d, s, Draw.argb(c, alpha));
        }
        // face: yellow with a lighter top half and a moving shine
        glyph(g, x0, y0, s, Draw.argb(Draw.YELLOW, alpha));
        int shine = (int) ((now / 6L) % (8 * s * 3)) - 8 * s;
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (GLYPH_G[r].charAt(c) != '#') {
                    continue;
                }
                int px = x0 + c * s;
                int py = y0 + r * s;
                if (r < 2) {
                    g.fill(px, py, px + s, py + s / 2, Draw.argb(0xFFFFFF, 0.45F * alpha));
                }
                if (Math.abs((c * s + r * s) - shine) < s) {
                    g.fill(px, py, px + s, py + s, Draw.argb(0xFFFFFF, 0.6F * alpha));
                }
            }
        }
    }

    private static void glyph(GuiGraphics g, int x0, int y0, int s, int color) {
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (GLYPH_G[r].charAt(c) == '#') {
                    g.fill(x0 + c * s, y0 + r * s, x0 + c * s + s, y0 + r * s + s, color);
                }
            }
        }
    }
}
