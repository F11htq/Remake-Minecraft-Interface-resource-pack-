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
 * strong mobs from {@link MiniBosses}, a generic cyberpunk frame for everything
 * else), damage trail, hit shake, low-health pulse and per-boss particles, plus
 * a 3D "GG" victory animation.
 */
public final class BossBarFx {

    private static final int FRAME_W = 256;
    private static final int FRAME_H = 64;
    private static final int BAR_X = 37;
    private static final int BAR_Y = 30;
    private static final int BAR_W = 182;
    private static final int BAR_H = 5;
    private static final int ENTRY_H = 60;
    /** On-screen size of the whole bar (frame, emblem, name) relative to the textures. */
    private static final float SCALE = 0.8F;

    /** Visual style of a boss bar. */
    public enum Style {
        GENERIC("boss_generic", 0xFCEE0A, false),
        DRAGON("boss_dragon", 0xD24BFF, true),
        WITHER("boss_wither", 0x3C78FF, true),
        RAID("boss_raid", 0xFF283C, false),
        WARDEN("boss_warden", 0x1EE6F0, true),
        GOLEM("boss_golem", 0x8CE650, true),
        RAVAGER("boss_ravager", 0xFF1E32, true),
        EVOKER("boss_evoker", 0xA555FF, true),
        GUARDIAN("boss_guardian", 0x28DCD2, true);

        final ResourceLocation texture;
        /** Ornaments drawn over the fill (head, skulls, axe, emblem). */
        final ResourceLocation front;
        /** Fill texture cut from the artwork, or null for a flat neon fill. */
        final ResourceLocation fill;
        final int neon;

        Style(String tex, int neon, boolean fillTexture) {
            this.texture = ResourceLocation.fromNamespaceAndPath("cyberui", "textures/gui/" + tex + ".png");
            this.front = ResourceLocation.fromNamespaceAndPath("cyberui", "textures/gui/" + tex + "_front.png");
            this.fill = fillTexture
                    ? ResourceLocation.fromNamespaceAndPath("cyberui", "textures/gui/" + tex + "_fill.png") : null;
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
        boolean gg = true;
        /** Set when the bar shatters (Ender Dragon): from then on only the burst is drawn. */
        boolean shattered = false;
        float lastX = 0.0F;
        int lastY = 0;
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
    // a victory waiting for a shatter burst to finish
    private static long pendingVictoryAt = 0L;
    private static String pendingName = "";
    private static int pendingColor = Draw.YELLOW;

    /** One flying piece of a shattered bar, in frame-local coordinates. */
    private static final class Shard {
        ResourceLocation tex;
        int u;
        int v;
        int w;
        int h;
        int texW;
        int texH;
        float x;
        float y;
        float vx;
        float vy;
        float rot;
        float vrot;
    }

    /** A bar exploding into shards with sparks, glitches and a shockwave. */
    private static final class Burst {
        final Style style;
        final float x;
        final int y;
        final long at;
        final List<Shard> shards = new ArrayList<>();
        final List<Particle> sparks = new ArrayList<>();

        Burst(Style style, float x, int y, long at) {
            this.style = style;
            this.x = x;
            this.y = y;
            this.at = at;
        }
    }

    private static final List<Burst> BURSTS = new ArrayList<>();
    private static final long BURST_MS = 1700L;
    private static final long BURST_TO_GG_MS = 1250L;

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
            st.lastX = sw / 2.0F;
            st.lastY = y;
            if (st.shattered) {
                // the bar is gone; keep its slot so the other bars do not jump
            } else if (y < sh / 3) {
                int fill = st.style == Style.GENERIC ? barColor(event.getColor()) : st.style.neon;
                g.pose().pushPose();
                g.pose().translate(sw / 2.0F, y, 0.0F);
                g.pose().scale(SCALE, SCALE, 1.0F);
                renderBar(g, font, event.getId(), event.getName(), fill, event.getOverlay(), st,
                        -FRAME_W / 2, 0, now, dt);
                g.pose().popPose();
            }
            y += Math.round(ENTRY_H * SCALE);
        }
        for (MiniBosses.Entry mob : MiniBosses.collect(now)) {
            alive.add(mob.id());
            State st = STATES.computeIfAbsent(mob.id(), id -> new State());
            st.style = mob.style();
            st.gg = mob.gg();
            st.name = mob.entity().getDisplayName().getString();
            st.lastX = sw / 2.0F;
            st.lastY = y;
            track(st, mob.progress(), now, dt);
            if (y < sh / 3) {
                g.pose().pushPose();
                g.pose().translate(sw / 2.0F, y, 0.0F);
                g.pose().scale(SCALE, SCALE, 1.0F);
                renderBar(g, font, mob.id(), mob.entity().getDisplayName(), st.style.neon,
                        BossEvent.BossBarOverlay.PROGRESS, st, -FRAME_W / 2, 0, now, dt);
                g.pose().popPose();
            }
            y += Math.round(ENTRY_H * SCALE);
        }
        // bars that vanished while almost dead also count as a win
        STATES.entrySet().removeIf(e -> {
            if (alive.contains(e.getKey())) {
                return false;
            }
            State st = e.getValue();
            if (!st.triggered && st.seenAlive && st.gg && st.style != Style.RAID && st.shown < 0.06F) {
                defeat(st, now);
            }
            return true;
        });

        if (pendingVictoryAt != 0L && now >= pendingVictoryAt) {
            pendingVictoryAt = 0L;
            startVictory("BOSS DEFEATED", pendingName, pendingColor);
        }
        renderBursts(g, font, sw, now, dt);
        renderVictory(g, font, sw, sh, now);
    }

    /** A boss died: the Ender Dragon's bar explodes first, then the GG plays. */
    private static void defeat(State st, long now) {
        if (st.style == Style.DRAGON) {
            st.shattered = true;
            BURSTS.add(burst(st.style, st.lastX, st.lastY, now));
            pendingVictoryAt = now + BURST_TO_GG_MS;
            pendingName = st.name;
            pendingColor = st.style.neon;
        } else {
            startVictory("BOSS DEFEATED", st.name, st.style.neon);
        }
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
        track(st, event.getProgress(), now, dt);

        if (!st.triggered && st.seenAlive && st.style == Style.RAID && keys.contains("event.minecraft.raid.victory")) {
            st.triggered = true;
            startVictory("RAID VICTORY", "", Style.RAID.neon);
        }
    }

    /** Damage trail, hit detection and the defeat trigger for any bar. */
    private static void track(State st, float progress, long now, float dt) {
        float p = Draw.clamp(progress, 0.0F, 1.0F);
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

        if (!st.triggered && st.seenAlive && st.style != Style.RAID && p <= 0.002F) {
            st.triggered = true;
            if (st.gg) {
                defeat(st, now);
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

    private static void renderBar(GuiGraphics g, Font font, UUID id, Component name, int fill,
                                  BossEvent.BossBarOverlay overlay, State st, int fx, int fy, long now, float dt) {
        Style style = st.style;

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
        long gl = (now + id.hashCode()) % 5200L;
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
        if (shownW > 0 && style.fill != null) {
            // the artwork's own bar texture, revealed up to the current health
            g.blit(RenderType::guiTextured, style.fill, bx, by, 0.0F, 0.0F, shownW, BAR_H, BAR_W, BAR_H);
            // a soft shine sweeping along it
            int sweep = (int) ((now / 9L) % (BAR_W + 60)) - 30;
            for (int d = -8; d <= 8; d++) {
                int x = sweep + d;
                if (x >= 0 && x < shownW) {
                    g.fill(bx + x, by, bx + x + 1, by + BAR_H, Draw.argb(0xFFFFFF, 0.22F * (1.0F - Math.abs(d) / 8.0F)));
                }
            }
            g.fill(bx + shownW - 1, by, bx + shownW, by + BAR_H, Draw.argb(0xFFFFFF, 0.9F));
        } else if (shownW > 0) {
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
        notches(g, overlay, bx, by);
        g.blit(RenderType::guiTextured, style.front, fx, fy, 0.0F, 0.0F, FRAME_W, FRAME_H, FRAME_W, FRAME_H, 0xFFFFFFFF);

        particles(g, st, fx, fy, now, dt);

        // name plate over the emblem, like the artwork's label
        int tw = font.width(name);
        int cx = fx + FRAME_W / 2;
        int py = fy + 1;
        int edge = Draw.argb(style.neon, 1.0F);
        g.fill(cx - tw / 2 - 6, py, cx + tw / 2 + 6, py + 11, Draw.argb(0x07060C, 0.92F));
        g.fill(cx - tw / 2 - 5, py - 1, cx + tw / 2 + 5, py, edge);
        g.fill(cx - tw / 2 - 5, py + 11, cx + tw / 2 + 5, py + 12, edge);
        g.fill(cx - tw / 2 - 7, py + 1, cx - tw / 2 - 6, py + 10, edge);
        g.fill(cx + tw / 2 + 6, py + 1, cx + tw / 2 + 7, py + 10, edge);
        // cyber tick marks running off the plate
        g.fill(cx - tw / 2 - 16, py + 5, cx - tw / 2 - 8, py + 6, Draw.argb(style.neon, 0.6F));
        g.fill(cx + tw / 2 + 8, py + 5, cx + tw / 2 + 16, py + 6, Draw.argb(style.neon, 0.6F));
        g.drawString(font, name, cx - tw / 2, py + 2, Draw.argb(mix(style.neon, 0xFFFFFF, 0.55F), 1.0F), true);
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
            case GENERIC -> 6.0F;
            default -> 16.0F;
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
            case WITHER -> drift(p, 0x3C78FF, 0x14183A, -5, 1.4F);
            case WARDEN -> {
                // sculk pulses: short vertical flickers along the bar
                drift(p, 0x1EE6F0, 0x0A5A64, 0, 0.5F);
                p.vy = 0;
                p.size = 1;
            }
            case GOLEM -> {
                // leaves and petals drifting down
                drift(p, 0x6EC83C, 0xF0F0DC, 6, 1.6F);
                p.y = BAR_Y - 12 + RANDOM.nextFloat() * 6;
            }
            case RAVAGER -> drift(p, 0xFF1E32, 0x501014, -7, 1.0F);
            case EVOKER -> {
                // floating magic orbs
                drift(p, 0xA555FF, 0xE6C8FF, -4, 1.8F);
                p.size = 2;
            }
            case GUARDIAN -> {
                // rising bubbles
                drift(p, 0x8CF0FF, 0x28DCD2, -8, 1.5F);
                p.size = RANDOM.nextInt(3) == 0 ? 2 : 1;
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

    /** Motes around the bar in two colours, drifting with the given vertical speed. */
    private static void drift(Particle p, int a, int b, float vy, float life) {
        p.x = BAR_X - 10 + RANDOM.nextFloat() * (BAR_W + 20);
        p.y = BAR_Y - 10 + RANDOM.nextFloat() * (BAR_H + 20);
        p.vx = (RANDOM.nextFloat() * 2 - 1) * 3;
        p.vy = vy * (0.6F + RANDOM.nextFloat() * 0.8F);
        p.color = RANDOM.nextInt(3) == 0 ? b : a;
        p.size = 1;
        p.maxLife = life * (0.7F + RANDOM.nextFloat() * 0.6F);
    }

    // ------------------------------------------------------------------
    // shatter burst
    // ------------------------------------------------------------------
    private static Burst burst(Style style, float x, int y, long now) {
        Burst b = new Burst(style, x, y, now);
        float cx = FRAME_W / 2.0F;
        float cy = BAR_Y + BAR_H / 2.0F;
        int tile = 16;
        // frame and emblem cut into 16x16 tiles
        for (ResourceLocation tex : new ResourceLocation[] {style.texture, style.front}) {
            for (int ty = 0; ty < FRAME_H; ty += tile) {
                for (int tx = 0; tx < FRAME_W; tx += tile) {
                    b.shards.add(shard(tex, tx, ty, tile, tile, FRAME_W, FRAME_H, tx, ty, cx, cy, 1.0F));
                }
            }
        }
        // the health fill splinters into thin glowing slivers
        if (style.fill != null) {
            for (int fx = 0; fx < BAR_W; fx += 7) {
                b.shards.add(shard(style.fill, fx, 0, 7, BAR_H, BAR_W, BAR_H, BAR_X + fx, BAR_Y, cx, cy, 1.6F));
            }
        }
        // sparks bursting out of the bar line
        for (int i = 0; i < 120; i++) {
            Particle p = new Particle();
            p.x = BAR_X + RANDOM.nextFloat() * BAR_W;
            p.y = cy;
            double a = RANDOM.nextDouble() * Math.PI * 2.0;
            float speed = 40.0F + RANDOM.nextFloat() * 220.0F;
            p.vx = (float) Math.cos(a) * speed;
            p.vy = (float) Math.sin(a) * speed * 0.6F;
            int pick = RANDOM.nextInt(6);
            p.color = pick == 0 ? Draw.YELLOW : pick == 1 ? Draw.CYAN : pick == 2 ? 0xFFFFFF : style.neon;
            p.size = RANDOM.nextInt(4) == 0 ? 2 : 1;
            p.maxLife = 0.5F + RANDOM.nextFloat() * 0.9F;
            b.sparks.add(p);
        }
        return b;
    }

    private static Shard shard(ResourceLocation tex, int u, int v, int w, int h, int texW, int texH,
                               int x, int y, float cx, float cy, float boost) {
        Shard s = new Shard();
        s.tex = tex;
        s.u = u;
        s.v = v;
        s.w = w;
        s.h = h;
        s.texW = texW;
        s.texH = texH;
        s.x = x + w / 2.0F;
        s.y = y + h / 2.0F;
        float dx = s.x - cx;
        float dy = s.y - cy;
        float len = (float) Math.max(1.0, Math.sqrt(dx * dx + dy * dy));
        float speed = (50.0F + RANDOM.nextFloat() * 120.0F) * boost;
        s.vx = dx / len * speed + (RANDOM.nextFloat() * 2 - 1) * 30.0F;
        s.vy = dy / len * speed * 0.8F - 40.0F - RANDOM.nextFloat() * 60.0F;
        s.vrot = (RANDOM.nextFloat() * 2 - 1) * 420.0F;
        return s;
    }

    private static void renderBursts(GuiGraphics g, Font font, int sw, long now, float dt) {
        for (int i = BURSTS.size() - 1; i >= 0; i--) {
            Burst b = BURSTS.get(i);
            long t = now - b.at;
            if (t > BURST_MS) {
                BURSTS.remove(i);
                continue;
            }
            float k = t / (float) BURST_MS;
            float fade = t > 900L ? 1.0F - (t - 900L) / (float) (BURST_MS - 900L) : 1.0F;
            int neon = b.style.neon;

            g.pose().pushPose();
            g.pose().translate(0.0F, 0.0F, 250.0F);

            // the bar line flares out across the whole screen
            if (t < 420L) {
                float f = 1.0F - t / 420.0F;
                int half = Math.round((FRAME_W * SCALE / 2.0F) + (sw / 2.0F) * Draw.easeOut(t / 420.0F));
                int ly = b.y + Math.round((BAR_Y + BAR_H / 2.0F) * SCALE);
                g.fill(Math.round(b.x) - half, ly - 1, Math.round(b.x) + half, ly + 1, Draw.argb(0xFFFFFF, 0.85F * f));
                g.fill(Math.round(b.x) - half, ly - 3, Math.round(b.x) + half, ly + 3, Draw.argb(neon, 0.35F * f));
            }

            g.pose().pushPose();
            g.pose().translate(b.x, b.y, 0.0F);
            g.pose().scale(SCALE, SCALE, 1.0F);
            g.pose().translate(-FRAME_W / 2.0F, 0.0F, 0.0F);

            if (t < 140L) {
                // overload: the intact bar flickers white, sliced by glitches
                for (int s = 0; s < FRAME_H; s += 4) {
                    int off = RANDOM.nextInt(5) == 0 ? (RANDOM.nextInt(13) - 6) : 0;
                    g.blit(RenderType::guiTextured, b.style.texture, off, s, 0.0F, s, FRAME_W, 4, FRAME_W, FRAME_H, 0xFFFFFFFF);
                    g.blit(RenderType::guiTextured, b.style.front, off, s, 0.0F, s, FRAME_W, 4, FRAME_W, FRAME_H, 0xFFFFFFFF);
                }
                g.fill(0, 0, FRAME_W, FRAME_H, Draw.argb(0xFFFFFF, 0.55F * (1.0F - t / 140.0F)));
            } else {
                float st = (t - 140L) / 1000.0F;
                for (Shard s : b.shards) {
                    float x = s.x + s.vx * st;
                    float y = s.y + s.vy * st + 0.5F * 260.0F * st * st;
                    float rot = s.vrot * st;
                    float scale = Math.max(0.0F, 1.0F - st * 0.45F);
                    if (t < 600L) {
                        // chromatic split on the freshly broken pieces
                        float ca = 0.45F * (1.0F - (t - 140L) / 460.0F) * fade;
                        drawShard(g, s, x - 1.5F, y, rot, scale, Draw.argb(Draw.CYAN, ca));
                        drawShard(g, s, x + 1.5F, y, rot, scale, Draw.argb(Draw.RED, ca));
                    }
                    drawShard(g, s, x, y, rot, scale, Draw.argb(0xFFFFFF, fade));
                }
            }

            // sparks
            for (Particle p : b.sparks) {
                float lt = t / 1000.0F;
                if (lt > p.maxLife) {
                    continue;
                }
                float drag = (float) (1.0 - Math.exp(-lt * 2.2)) / 2.2F;
                int x = Math.round(p.x + p.vx * drag);
                int y = Math.round(p.y + p.vy * drag + 40.0F * lt * lt);
                g.fill(x, y, x + p.size, y + p.size, Draw.argb(p.color, (1.0F - lt / p.maxLife) * fade));
            }

            // shockwave: chamfered rings rushing outwards
            for (int r = 0; r < 2; r++) {
                long rt = t - r * 120L;
                if (rt > 0L && rt < 700L) {
                    float e = Draw.easeOut(rt / 700.0F);
                    int rw = Math.round(60 + e * 360);
                    int rh = Math.round(8 + e * 70);
                    int cx = FRAME_W / 2;
                    int cy = BAR_Y + BAR_H / 2;
                    float a = (1.0F - rt / 700.0F) * fade;
                    Draw.frame(g, cx - rw / 2, cy - rh / 2, rw, rh, Draw.argb(r == 0 ? neon : Draw.YELLOW, a));
                }
            }

            // glitch slices across the blast
            if (t > 140L && t < 1100L && (t / 70L) % 3L == 0L) {
                int gy = RANDOM.nextInt(FRAME_H);
                g.fill(-20, gy, FRAME_W + 20, gy + 1, Draw.argb(Draw.CYAN, 0.5F * fade));
                g.fill(-14, gy + 2, FRAME_W + 26, gy + 3, Draw.argb(Draw.RED, 0.5F * fade));
            }

            // terminal readout
            if (t > 220L) {
                String msg = "// TARGET TERMINATED";
                int chars = (int) Math.min(msg.length(), (t - 220L) / 28L);
                String shown = msg.substring(0, chars) + ((t / 120L) % 2L == 0L && chars < msg.length() ? "_" : "");
                int tw = font.width(msg);
                int tx = FRAME_W / 2 - tw / 2;
                int ty = BAR_Y - 4;
                boolean flicker = (t / 50L) % 9L == 0L;
                g.fill(tx - 4, ty - 2, tx + tw + 4, ty + 10, Draw.argb(0x07060C, 0.8F * fade));
                g.drawString(font, shown, tx + (flicker ? 1 : 0), ty, Draw.argb(flicker ? Draw.CYAN : Draw.RED, fade), true);
            }

            g.pose().popPose();
            g.pose().popPose();
            if (k >= 1.0F) {
                BURSTS.remove(i);
            }
        }
    }

    private static void drawShard(GuiGraphics g, Shard s, float x, float y, float rot, float scale, int color) {
        if (scale <= 0.01F) {
            return;
        }
        g.pose().pushPose();
        g.pose().translate(x, y, 0.0F);
        g.pose().mulPose(Axis.ZP.rotationDegrees(rot));
        g.pose().scale(scale, scale, 1.0F);
        g.blit(RenderType::guiTextured, s.tex, -s.w / 2, -s.h / 2, (float) s.u, (float) s.v, s.w, s.h, s.texW, s.texH, color);
        g.pose().popPose();
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
