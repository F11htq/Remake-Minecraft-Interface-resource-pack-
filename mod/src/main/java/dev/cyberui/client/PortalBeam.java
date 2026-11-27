package dev.cyberui.client;

import java.lang.ref.WeakReference;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.TheEndPortalBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A purple energy beam through the End exit portal, as wide as its bedrock rim.
 * When the portal opens after the dragon fight a white-hot needle drops out of
 * the sky and pierces the island; on impact the beam bursts open to full width,
 * a shock front rolls across the island and the ground shakes. The beam then
 * stays, reaching up into the sky and down into the void below the island: a
 * translucent white-hot core inside streaming energy layers (the vanilla beam
 * texture, scrolling upwards) and a soft additive halo. A portal that was
 * already open when the player arrives just shows the settled beam.
 */
public final class PortalBeam {

    /** Everything the render thread needs to draw the beam for one frame. */
    public record Snapshot(float time, float sinceImpact, float tip, float depth, float width, float flash,
                           float open, int segments) {
    }

    private static final Identifier BEAM = Identifier.withDefaultNamespace("textures/entity/beacon/beacon_beam.png");
    private static final float RADIUS = 3.6F;
    private static final float TOP = 320.0F;
    /** How far below the bottom of the world the beam reaches into the void. */
    private static final float VOID_DEPTH = 48.0F;
    /** The needle falls this fast (blocks per second). */
    private static final float STRIKE_SPEED = 900.0F;
    private static final float OPEN_SECONDS = 0.7F;
    private static final float WAVE_SECONDS = 2.8F;
    private static final float WAVE_REACH = 38.0F;
    private static final float QUAKE_SECONDS = 3.2F;
    private static final float QUAKE_RANGE = 140.0F;
    private static final int SEGMENTS = 32;
    /** A dragon kill this recent makes the next portal opening strike from the sky. */
    private static final long STRIKE_ARMED_MS = 60_000L;
    /** The anchor counts as newly appeared after being gone this long. */
    private static final long GONE_MS = 2_000L;

    /** Weak, so a level the player left is not kept in memory. */
    private static WeakReference<Level> strikeLevel = new WeakReference<>(null);
    private static long strikeStartTick = 0L;
    private static long lastSeenMs = 0L;
    private static long dragonKilledAt = 0L;
    private static long lastParticleTick = -1L;
    private static boolean impactPlayed = false;
    private static boolean burstDone = false;

    private PortalBeam() {
    }

    /** Called when the Ender Dragon's bar runs out: the portal that opens next strikes from the sky. */
    public static void onDragonKilled() {
        dragonKilledAt = Util.getMillis();
    }

    /**
     * The exit portal surrounds a bedrock pillar at x 0, z 0; the portal block
     * just east of it carries the beam.
     */
    public static boolean isAnchor(TheEndPortalBlockEntity be) {
        Level level = be.getLevel();
        BlockPos pos = be.getBlockPos();
        return level != null && pos.getX() == 1 && pos.getZ() == 0 && level.dimension() == Level.END
                && level.getBlockState(pos.west()).is(Blocks.BEDROCK);
    }

    /** Seconds until the falling needle reaches the portal (portal-relative height 0). */
    private static float impactTime() {
        return TOP / STRIKE_SPEED;
    }

    /** Extract phase (game thread): advances the animation, plays sounds and spawns particles. */
    public static Snapshot extract(TheEndPortalBlockEntity be, float partialTick, Vec3 camera) {
        Level level = be.getLevel();
        long tick = level.getGameTime();
        long now = Util.getMillis();
        if (level != strikeLevel.get() || now - lastSeenMs > GONE_MS) {
            strikeLevel = new WeakReference<>(level);
            boolean strike = dragonKilledAt != 0L && now - dragonKilledAt < STRIKE_ARMED_MS;
            dragonKilledAt = 0L;
            // an already open portal starts settled: no strike, no quake, no shock wave
            strikeStartTick = strike ? tick : tick - 20L * 60L;
            impactPlayed = !strike;
            burstDone = !strike;
        }
        lastSeenMs = now;

        float time = (tick + partialTick) / 20.0F;
        float since = (tick - strikeStartTick + partialTick) / 20.0F;
        float sinceImpact = since - impactTime();
        float depth = be.getBlockPos().getY() + VOID_DEPTH;
        // the needle's tip falls from the sky through the island into the void
        float tip = Math.max(-depth, TOP - STRIKE_SPEED * since);
        // after impact the beam bursts open, overshooting a little before it settles
        float open = Mth.clamp(sinceImpact / OPEN_SECONDS, 0.0F, 1.0F);
        float width = sinceImpact <= 0.0F ? 0.0F : easeOut(open) * (1.0F + 0.35F * (1.0F - open));
        float flash = sinceImpact <= 0.0F ? 0.0F : (1.0F - open) * (1.0F - open);

        double baseY = be.getBlockPos().getY() + 0.8;
        if (sinceImpact > 0.0F && !impactPlayed) {
            impactPlayed = true;
            level.playLocalSound(0.5, baseY, 0.5, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.AMBIENT,
                    6.0F, 0.55F, false);
            level.playLocalSound(0.5, baseY, 0.5, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.AMBIENT,
                    3.0F, 0.6F, false);
        }
        spawnParticles(level, baseY, tick, sinceImpact);
        // level of detail: the column is 3.6 blocks wide, so far away fewer
        // sides look exactly the same while costing a fraction of the vertices
        double dx = camera.x - 0.5;
        double dz = camera.z - 0.5;
        double dist = Math.sqrt(dx * dx + dz * dz);
        int segments = dist < 48.0 ? SEGMENTS : dist < 112.0 ? SEGMENTS / 2 : SEGMENTS / 4;
        return new Snapshot(time, sinceImpact, tip, depth, width, flash, open, segments);
    }

    /** Submit phase: queues the beam geometry (the anchor block's pose is on the stack). */
    public static void submit(Snapshot s, PoseStack ps, SubmitNodeCollector collector) {
        ps.pushPose();
        // the anchor block is at x 1; the beam is centred on the pillar at x 0.5, z 0.5
        ps.translate(-0.5F, 0.75F, 0.5F);
        if (s.width() > 0.0F) {
            collector.submitCustomGeometry(ps, RenderTypes.beaconBeam(BEAM, true), (pose, vc) -> streaks(vc, pose, s));
        }
        collector.submitCustomGeometry(ps, RenderTypes.lightning(), (pose, vc) -> glow(vc, pose, s));
        ps.popPose();
    }

    private static void streaks(VertexConsumer vc, PoseStack.Pose m, Snapshot s) {
        float spin = s.time() * 0.3F;
        float w = s.width();
        float[] ys = streakHeights(s.tip(), s.depth());
        // white-hot core: translucent like everything else, so the pillar and dragon egg show through
        int n = s.segments();
        streakLayer(vc, m, n, 0.6F * w, 0xFFFFFF, 0.45F, s.time(), ys, s.tip(), s.depth(), spin * 2.0F, 2.2F);
        streakLayer(vc, m, n, 1.1F * w, 0xF0DCFF, 0.40F, s.time(), ys, s.tip(), s.depth(), -spin * 1.8F, 1.9F);
        streakLayer(vc, m, n, 1.9F * w, 0xFF70F0, 0.60F, s.time(), ys, s.tip(), s.depth(), -spin * 1.6F, 1.6F);
        streakLayer(vc, m, n, 2.8F * w, 0xC24CFF, 0.42F, s.time(), ys, s.tip(), s.depth(), spin * 1.2F, 1.1F);
        streakLayer(vc, m, n, RADIUS * w, 0x8A34F0, 0.30F, s.time(), ys, s.tip(), s.depth(), -spin, 0.8F);
    }

    private static void glow(VertexConsumer vc, PoseStack.Pose m, Snapshot s) {
        float time = s.time();
        float tip = s.tip();
        float width = s.width();
        float flash = s.flash();
        if (s.sinceImpact() <= 0.0F) {
            // the falling needle: a white-hot thread with a bright head
            wall(vc, m, 0.3F, tip, TOP, 0xFFFFFF, 0.9F, 0.9F);
            wall(vc, m, 0.9F, tip, TOP, 0xE070FF, 0.35F, 0.35F);
            wall(vc, m, 1.4F, tip, tip + 6.0F, 0xFFFFFF, 0.7F, 0.0F);
        } else {
            // soft halo around the column, flaring on impact
            float glowBoost = 1.0F + 2.5F * flash;
            haloLayer(vc, m, s.segments(), 0.5F * width, 0xFFFFFF, 0.22F * glowBoost, time, tip, s.depth());
            haloLayer(vc, m, s.segments(), (RADIUS + 0.9F) * width, 0x9A3CFF, 0.10F * glowBoost, time, tip, s.depth());
            haloLayer(vc, m, s.segments(), (RADIUS + 2.4F) * width, 0x6A22D0, 0.05F * glowBoost, time, tip, s.depth());
            // the beam glows brightest where it leaves the portal
            float base = (0.32F + 0.08F * Mth.sin(time * 5.0F)) * Math.min(1.0F, s.open() * 2.0F) + 0.6F * flash;
            wall(vc, m, RADIUS + 1.2F, 0.0F, 5.0F, 0xE070FF, base, 0.0F);
            wall(vc, m, RADIUS * 0.6F, 0.0F, 8.0F, 0xFFFFFF, base * 0.8F, 0.0F);
            disc(vc, m, 0.0F, RADIUS + 1.6F, 0.03F, 0xB040FF, 0.30F + 0.5F * flash, 0.0F);
            // impact flash across the ground
            if (flash > 0.0F) {
                disc(vc, m, 0.0F, RADIUS + 12.0F, 0.05F, 0xF0D0FF, 0.6F * flash, 0.0F);
            }
        }
        // the shock front and its echo
        shockFront(vc, m, s.sinceImpact() / WAVE_SECONDS, 0xE070FF, 1.0F);
        shockFront(vc, m, s.sinceImpact() / WAVE_SECONDS - 0.12F, 0x40E0FF, 0.55F);
    }

    /**
     * Camera shake for the earthquake after impact, as {yaw, pitch} offsets in
     * degrees, or null when the ground is still. Stronger near the portal.
     */
    public static float @Nullable [] quake(@Nullable Entity viewer, float partialTick) {
        Level level = strikeLevel.get();
        if (viewer == null || level == null || viewer.level() != level) {
            return null;
        }
        float t = (level.getGameTime() - strikeStartTick + partialTick) / 20.0F - impactTime();
        if (t <= 0.0F || t > QUAKE_SECONDS) {
            return null;
        }
        double dx = viewer.getX() - 0.5;
        double dz = viewer.getZ() - 0.5;
        float near = Mth.clamp(1.0F - (float) Math.sqrt(dx * dx + dz * dz) / QUAKE_RANGE, 0.0F, 1.0F);
        if (near <= 0.0F) {
            return null;
        }
        // a hard jolt, then rumbling that dies away
        float amp = (2.6F * (float) Math.exp(-t * 1.25F) + (t < 0.15F ? 2.0F * (1.0F - t / 0.15F) : 0.0F)) * near;
        float yaw = amp * (0.6F * Mth.sin(t * 47.0F) + 0.4F * Mth.sin(t * 83.0F + 1.3F));
        float pitch = amp * (0.55F * Mth.sin(t * 53.0F + 0.7F) + 0.35F * Mth.sin(t * 97.0F));
        return new float[] {yaw, pitch};
    }

    // ------------------------------------------------------------------
    // layers
    // ------------------------------------------------------------------

    private static float easeOut(float k) {
        return 1.0F - (1.0F - k) * (1.0F - k) * (1.0F - k);
    }

    /** Soft ends: fades out high in the sky and deep in the void. */
    private static float fade(float y, float bottom, float depth) {
        float top = Mth.clamp((TOP - y) / 60.0F, 0.0F, 1.0F);
        float low = Mth.clamp((y - bottom) / 4.0F, 0.0F, 1.0F) * Mth.clamp((y + depth) / 40.0F, 0.0F, 1.0F);
        // brighter where the beam leaves the portal
        float source = 1.0F + 0.6F * Mth.clamp(1.0F - Math.abs(y) / 12.0F, 0.0F, 1.0F);
        return top * low * source;
    }

    /**
     * Heights at which the streak layers are cut. The beam's alpha only changes
     * near its ends and where it leaves the portal, and the texture scrolls
     * linearly, so the long middle of the column needs no cuts at all. This
     * keeps the vertex count at roughly a third of a uniform 8-block grid.
     */
    private static float[] streakHeights(float bottom, float depth) {
        float[] out = new float[96];
        int n = 0;
        float y = bottom;
        while (y < TOP && n < out.length - 1) {
            out[n++] = y;
            boolean ramp = y < bottom + 4.0F || y < -depth + 40.0F || Math.abs(y) < 12.0F || y > TOP - 60.0F;
            float next = y + 8.0F;
            if (!ramp) {
                // jump to the next point where the alpha starts changing again
                float target = y < -12.0F ? -12.0F : TOP - 60.0F;
                next = Math.max(next, target);
            }
            y = Math.min(TOP, next);
        }
        out[n++] = TOP;
        return java.util.Arrays.copyOf(out, n);
    }

    /** Textured cylinder with the beam texture streaming upwards, from the bottom to the sky. */
    private static void streakLayer(VertexConsumer vc, PoseStack.Pose m, int segments, float r, int rgb, float alpha, float time,
                                    float[] ys, float bottom, float depth, float spin, float speed) {
        int cr = (rgb >> 16) & 255;
        int cg = (rgb >> 8) & 255;
        int cb = rgb & 255;
        float scroll = -time * speed;
        int[] as = new int[ys.length];
        for (int k = 0; k < ys.length; k++) {
            as[k] = Mth.clamp((int) (alpha * fade(ys[k], bottom, depth) * 255), 0, 255);
        }
        for (int i = 0; i < segments; i++) {
            float t0 = spin + (float) (i * Math.PI * 2.0 / segments);
            float t1 = spin + (float) ((i + 1) * Math.PI * 2.0 / segments);
            float x0 = Mth.cos(t0) * r;
            float z0 = Mth.sin(t0) * r;
            float x1 = Mth.cos(t1) * r;
            float z1 = Mth.sin(t1) * r;
            // the texture wraps around the column three times
            float u0 = i * 3.0F / segments;
            float u1 = (i + 1) * 3.0F / segments;
            for (int k = 0; k + 1 < ys.length; k++) {
                float y0 = ys[k];
                float y1 = ys[k + 1];
                int a0 = as[k];
                int a1 = as[k + 1];
                if (a0 == 0 && a1 == 0) {
                    continue;
                }
                float v0 = y0 / 12.0F + scroll;
                float v1 = y1 / 12.0F + scroll;
                tex(vc, m, x0, y0, z0, u0, v0, cr, cg, cb, a0);
                tex(vc, m, x1, y0, z1, u1, v0, cr, cg, cb, a0);
                tex(vc, m, x1, y1, z1, u1, v1, cr, cg, cb, a1);
                tex(vc, m, x0, y1, z0, u0, v1, cr, cg, cb, a1);

                tex(vc, m, x0, y1, z0, u0, v1, cr, cg, cb, a1);
                tex(vc, m, x1, y1, z1, u1, v1, cr, cg, cb, a1);
                tex(vc, m, x1, y0, z1, u1, v0, cr, cg, cb, a0);
                tex(vc, m, x0, y0, z0, u0, v0, cr, cg, cb, a0);
            }
        }
    }

    private static void tex(VertexConsumer vc, PoseStack.Pose m, float x, float y, float z, float u, float v,
                            int r, int g, int b, int a) {
        vc.addVertex(m, x, y, z).setColor(r, g, b, a).setUv(u, v).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(0.0F, 1.0F, 0.0F);
    }

    /** Soft additive halo around the column, with slow pulses travelling upwards. */
    private static void haloLayer(VertexConsumer vc, PoseStack.Pose m, int segments, float r, int rgb, float alpha, float time,
                                  float bottom, float depth) {
        float step = 10.0F;
        for (int i = 0; i < segments; i++) {
            float t0 = (float) (i * Math.PI * 2.0 / segments);
            float t1 = (float) ((i + 1) * Math.PI * 2.0 / segments);
            float x0 = Mth.cos(t0) * r;
            float z0 = Mth.sin(t0) * r;
            float x1 = Mth.cos(t1) * r;
            float z1 = Mth.sin(t1) * r;
            for (float y0 = bottom; y0 < TOP; y0 += step) {
                float y1 = Math.min(TOP, y0 + step);
                float p0 = 0.8F + 0.2F * Mth.sin((y0 - time * 10.0F) * 0.25F);
                float p1 = 0.8F + 0.2F * Mth.sin((y1 - time * 10.0F) * 0.25F);
                quad(vc, m, x0, z0, x1, z1, y0, y1, rgb, alpha * p0 * fade(y0, bottom, depth),
                        alpha * p1 * fade(y1, bottom, depth));
            }
        }
    }

    /** A full cylinder band between two heights; alpha may differ at its bottom and top. */
    private static void wall(VertexConsumer vc, PoseStack.Pose m, float r, float y0, float y1, int rgb, float aBottom,
                             float aTop) {
        if ((aBottom <= 0.004F && aTop <= 0.004F) || y1 <= y0 || r <= 0.0F) {
            return;
        }
        int segments = Math.max(SEGMENTS, (int) (r * 4));
        for (int i = 0; i < segments; i++) {
            float t0 = (float) (i * Math.PI * 2.0 / segments);
            float t1 = (float) ((i + 1) * Math.PI * 2.0 / segments);
            quad(vc, m, Mth.cos(t0) * r, Mth.sin(t0) * r, Mth.cos(t1) * r, Mth.sin(t1) * r, y0, y1, rgb, aBottom, aTop);
        }
    }

    /**
     * The expanding shock front at progress e (0..1): a glowing wall that grows
     * lower as it spreads, a soft gradient trail on the ground behind it and a
     * bright leading edge.
     */
    private static void shockFront(VertexConsumer vc, PoseStack.Pose m, float e, int rgb, float strength) {
        if (e <= 0.0F || e >= 1.0F) {
            return;
        }
        float front = RADIUS + WAVE_REACH * easeOut(e);
        float fadeOut = (float) Math.pow(1.0F - e, 1.4F) * strength;
        float wallH = 0.5F + 3.0F * (1.0F - e);
        float trail = 3.0F + 8.0F * e;
        disc(vc, m, Math.max(0.0F, front - trail), front, 0.06F, rgb, 0.0F, 0.45F * fadeOut);
        disc(vc, m, front - 0.35F, front + 0.15F, 0.08F, 0xFFFFFF, 0.8F * fadeOut, 0.8F * fadeOut);
        wall(vc, m, front, 0.0F, wallH, rgb, 0.65F * fadeOut, 0.0F);
        wall(vc, m, front - 0.25F, 0.0F, wallH * 0.6F, 0xFFFFFF, 0.35F * fadeOut, 0.0F);
    }

    /** One wall quad, both windings so it is visible from outside and from inside. */
    private static void quad(VertexConsumer vc, PoseStack.Pose m, float x0, float z0, float x1, float z1,
                             float y0, float y1, int rgb, float a0, float a1) {
        int cr = (rgb >> 16) & 255;
        int cg = (rgb >> 8) & 255;
        int cb = rgb & 255;
        int b0 = Mth.clamp((int) (a0 * 255), 0, 255);
        int b1 = Mth.clamp((int) (a1 * 255), 0, 255);
        if (b0 == 0 && b1 == 0) {
            return;
        }
        vc.addVertex(m, x0, y0, z0).setColor(cr, cg, cb, b0);
        vc.addVertex(m, x1, y0, z1).setColor(cr, cg, cb, b0);
        vc.addVertex(m, x1, y1, z1).setColor(cr, cg, cb, b1);
        vc.addVertex(m, x0, y1, z0).setColor(cr, cg, cb, b1);

        vc.addVertex(m, x0, y1, z0).setColor(cr, cg, cb, b1);
        vc.addVertex(m, x1, y1, z1).setColor(cr, cg, cb, b1);
        vc.addVertex(m, x1, y0, z1).setColor(cr, cg, cb, b0);
        vc.addVertex(m, x0, y0, z0).setColor(cr, cg, cb, b0);
    }

    /** Flat ring on the ground with a radial gradient (inner alpha to outer alpha), visible from both sides. */
    private static void disc(VertexConsumer vc, PoseStack.Pose m, float r0, float r1, float y, int rgb, float aInner,
                             float aOuter) {
        int cr = (rgb >> 16) & 255;
        int cg = (rgb >> 8) & 255;
        int cb = rgb & 255;
        int ai = Mth.clamp((int) (aInner * 255), 0, 255);
        int ao = Mth.clamp((int) (aOuter * 255), 0, 255);
        if ((ai == 0 && ao == 0) || r1 <= r0) {
            return;
        }
        int segments = Math.max(SEGMENTS, (int) (r1 * 5));
        for (int i = 0; i < segments; i++) {
            float t0 = (float) (i * Math.PI * 2.0 / segments);
            float t1 = (float) ((i + 1) * Math.PI * 2.0 / segments);
            float c0 = Mth.cos(t0);
            float s0 = Mth.sin(t0);
            float c1 = Mth.cos(t1);
            float s1 = Mth.sin(t1);
            vc.addVertex(m, c0 * r0, y, s0 * r0).setColor(cr, cg, cb, ai);
            vc.addVertex(m, c0 * r1, y, s0 * r1).setColor(cr, cg, cb, ao);
            vc.addVertex(m, c1 * r1, y, s1 * r1).setColor(cr, cg, cb, ao);
            vc.addVertex(m, c1 * r0, y, s1 * r0).setColor(cr, cg, cb, ai);

            vc.addVertex(m, c1 * r0, y, s1 * r0).setColor(cr, cg, cb, ai);
            vc.addVertex(m, c1 * r1, y, s1 * r1).setColor(cr, cg, cb, ao);
            vc.addVertex(m, c0 * r1, y, s0 * r1).setColor(cr, cg, cb, ao);
            vc.addVertex(m, c0 * r0, y, s0 * r0).setColor(cr, cg, cb, ai);
        }
    }

    // ------------------------------------------------------------------
    // particles (once per game tick)
    // ------------------------------------------------------------------

    private static void spawnParticles(Level level, double base, long tick, float sinceImpact) {
        if (tick == lastParticleTick || sinceImpact <= 0.0F) {
            return;
        }
        boolean firstTick = !burstDone;
        burstDone = true;
        lastParticleTick = tick;
        double cx = 0.5;
        double cz = 0.5;
        if (firstTick) {
            // impact burst: blast clouds and sparks thrown out of the portal
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4.0;
                level.addParticle(ParticleTypes.EXPLOSION, cx + Math.cos(a) * RADIUS, base + 0.5,
                        cz + Math.sin(a) * RADIUS, 0.0, 0.0, 0.0);
            }
            for (int i = 0; i < 60; i++) {
                double a = level.getRandom().nextDouble() * Math.PI * 2.0;
                double s = 0.4 + level.getRandom().nextDouble() * 0.8;
                level.addParticle(i % 2 == 0 ? ParticleTypes.END_ROD : ParticleTypes.REVERSE_PORTAL,
                        cx + Math.cos(a) * RADIUS, base + 0.3, cz + Math.sin(a) * RADIUS,
                        Math.cos(a) * s, 0.2 + level.getRandom().nextDouble() * 0.6, Math.sin(a) * s);
            }
        }
        // portal sparks streaming up inside the beam
        for (int i = 0; i < 8; i++) {
            double a = level.getRandom().nextDouble() * Math.PI * 2.0;
            double r = level.getRandom().nextDouble() * RADIUS;
            double y = base + level.getRandom().nextDouble() * 28.0;
            level.addParticle(ParticleTypes.REVERSE_PORTAL, cx + Math.cos(a) * r, y, cz + Math.sin(a) * r,
                    0.0, 0.3 + level.getRandom().nextDouble() * 0.4, 0.0);
        }
        // debris kicked up along the shock front, dust shaken loose by the quake
        float wave = sinceImpact / WAVE_SECONDS;
        if (wave < 0.85F) {
            double front = RADIUS + WAVE_REACH * easeOut(wave);
            for (int i = 0; i < 18; i++) {
                double a = level.getRandom().nextDouble() * Math.PI * 2.0;
                double ca = Math.cos(a);
                double sa = Math.sin(a);
                double x = cx + ca * front;
                double z = cz + sa * front;
                level.addParticle(ParticleTypes.REVERSE_PORTAL, x, base + 0.2, z, ca * 0.25, 0.15, sa * 0.25);
                if (i % 3 == 0) {
                    level.addParticle(ParticleTypes.CLOUD, x, base + 0.3, z, ca * 0.15, 0.04, sa * 0.15);
                }
            }
        }
        if (sinceImpact < QUAKE_SECONDS) {
            for (int i = 0; i < 10; i++) {
                double a = level.getRandom().nextDouble() * Math.PI * 2.0;
                double r = RADIUS + level.getRandom().nextDouble() * 30.0;
                level.addParticle(ParticleTypes.ASH, cx + Math.cos(a) * r, base + level.getRandom().nextDouble() * 1.5,
                        cz + Math.sin(a) * r, 0.0, 0.1, 0.0);
            }
        }
    }
}
