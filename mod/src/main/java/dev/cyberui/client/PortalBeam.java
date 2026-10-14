package dev.cyberui.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.TheEndPortalBlockEntity;
import org.joml.Matrix4f;

/**
 * A purple energy beam rising out of the End exit portal, as wide as its
 * bedrock rim. When the portal first appears a glowing circle is traced around
 * the rim, the beam rises around it segment by segment and a shock front rolls
 * out across the island. The beam itself is a textured white-hot core inside
 * streaming energy layers (the vanilla beam texture, scrolling upwards) and a
 * soft additive halo, brightest at its base, with cyberpunk scan rings.
 */
public final class PortalBeam {

    private static final ResourceLocation BEAM = ResourceLocation.withDefaultNamespace("textures/entity/beacon_beam.png");
    private static final float RADIUS = 3.6F;
    private static final float MAX_HEIGHT = 300.0F;
    private static final float RISE_SECONDS = 4.0F;
    /** Time to trace the circle around the rim; the wall rises around it in the same rhythm. */
    private static final float TRACE_SECONDS = 1.6F;
    private static final float WAVE_SECONDS = 2.6F;
    private static final float WAVE_REACH = 34.0F;
    private static final int SEGMENTS = 32;

    private static Level riseLevel = null;
    private static long riseStartTick = 0L;
    private static long lastParticleTick = -1L;

    private PortalBeam() {
    }

    /**
     * The exit portal surrounds a bedrock pillar at x 0, z 0; the portal block
     * just east of it carries the beam.
     */
    public static boolean isAnchor(TheEndPortalBlockEntity be) {
        Level level = be.getLevel();
        BlockPos pos = be.getBlockPos();
        return level != null && level.dimension() == Level.END && pos.getX() == 1 && pos.getZ() == 0
                && level.getBlockState(pos.west()).is(Blocks.BEDROCK);
    }

    public static void render(TheEndPortalBlockEntity be, float partialTick, PoseStack ps, MultiBufferSource buffers) {
        if (!isAnchor(be)) {
            return;
        }
        Level level = be.getLevel();
        long tick = level.getGameTime();
        if (level != riseLevel) {
            riseLevel = level;
            riseStartTick = tick;
        }
        float time = (tick + partialTick) / 20.0F;
        float since = (tick - riseStartTick + partialTick) / 20.0F;
        float settled = Mth.clamp((since - TRACE_SECONDS) / 1.5F, 0.0F, 1.0F);
        float wave = (since - TRACE_SECONDS) / WAVE_SECONDS;

        ps.pushPose();
        // the anchor block is at x 1: move onto the pillar's block, the core centres itself on it
        ps.translate(-1.0F, 0.75F, 0.0F);

        // 1. white-hot textured core, rising once the circle is complete
        float coreK = Mth.clamp((since - TRACE_SECONDS) / (RISE_SECONDS * 0.8F), 0.0F, 1.0F);
        int coreHeight = (int) (MAX_HEIGHT * easeOut(coreK));
        if (coreHeight > 0) {
            BeaconRenderer.renderBeaconBeam(ps, buffers, BEAM, partialTick, 1.0F, tick, 0, coreHeight,
                    0xFFF0DCFF, 0.85F, 1.25F);
        }

        // move to the pillar centre for everything else
        ps.translate(0.5F, 0.0F, 0.5F);
        Matrix4f m = ps.last().pose();
        float spin = Math.max(0.0F, since - TRACE_SECONDS) * 0.35F;

        // 2. streaming energy layers, rising around the circle; inner ones turn the other way
        VertexConsumer streaks = buffers.getBuffer(RenderType.beaconBeam(BEAM, true));
        streakLayer(streaks, m, 1.9F, 0xFF70F0, 0.60F, time, since, 0.9F, -spin * 1.6F, 1.6F);
        streakLayer(streaks, m, 2.8F, 0xC24CFF, 0.42F, time, since, 0.5F, spin * 1.2F, 1.1F);
        streakLayer(streaks, m, RADIUS, 0x8A34F0, 0.30F, time, since, 0.15F, -spin, 0.8F);

        // 3. additive glow: halo, base flare, the circle trace, scan rings, shock front
        VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
        float glitch = settled >= 1.0F && Mth.frac(time * 0.37F) < 0.012F ? 0.4F : 0.0F;
        haloLayer(glow, m, RADIUS + 0.9F + glitch, 0x9A3CFF, 0.10F, time, since, 0.3F, spin);
        haloLayer(glow, m, RADIUS + 2.4F + glitch, 0x6A22D0, 0.05F, time, since, 0.45F, spin);

        // the beam is brightest where it leaves the portal
        if (settled > 0.0F) {
            float flare = (0.32F + 0.08F * Mth.sin(time * 5.0F)) * settled;
            wall(glow, m, RADIUS + 1.2F, 0.0F, 5.0F, 0xE070FF, flare, 0.0F, 0.0F);
            wall(glow, m, RADIUS * 0.6F, 0.0F, 8.0F, 0xFFFFFF, flare * 0.8F, 0.0F, 0.0F);
            disc(glow, m, 0.0F, RADIUS + 1.6F, 0.03F, 0xB040FF, 0.30F * settled, 0.0F);
        }

        // the glowing circle traced around the bedrock rim, a spark leading the way
        float trace = Mth.clamp(since / TRACE_SECONDS, 0.0F, 1.0F);
        float traceA = since < TRACE_SECONDS ? 0.95F : 0.95F * Math.max(0.0F, 1.0F - (since - TRACE_SECONDS) / 1.2F);
        if (traceA > 0.0F) {
            arc(glow, m, RADIUS - 0.35F, RADIUS + 0.35F, 0.03F, trace, 0xE070FF, traceA);
            arc(glow, m, RADIUS - 0.08F, RADIUS + 0.12F, 0.04F, trace, 0xFFFFFF, traceA);
        }
        if (since < TRACE_SECONDS) {
            float head = (float) (trace * Math.PI * 2.0);
            float hx = Mth.cos(head) * RADIUS;
            float hz = Mth.sin(head) * RADIUS;
            spark(glow, m, hx, hz, 0.2F, 4.0F, 0xFFFFFF, 0.95F);
            spark(glow, m, hx, hz, 0.6F, 2.5F, 0xFF60F0, 0.5F);
        }

        // cyberpunk scan rings running up the outside of the column
        if (settled > 0.0F) {
            for (int i = 0; i < 4; i++) {
                float phase = Mth.frac(time * 0.4F + i * 0.25F);
                float y = phase * 100.0F;
                int c = i % 2 == 0 ? 0x00F0FF : 0xFF2AD4;
                float a = 0.6F * (1.0F - phase) * settled;
                wall(glow, m, RADIUS + 0.35F, y, y + 0.3F, c, a, a, spin);
            }
        }

        // the shock front: a glowing wall rolling outwards, a soft trail and an echo
        shockFront(glow, m, wave, 0xE070FF, 1.0F);
        shockFront(glow, m, wave - 0.12F, 0x40E0FF, 0.55F);
        if (wave > 0.0F && wave < 0.25F) {
            // ground flash the moment the circle closes
            disc(glow, m, 0.0F, RADIUS + 9.0F, 0.05F, 0xF0D0FF, 0.5F * (1.0F - wave / 0.25F), 0.0F);
        }
        ps.popPose();

        spawnParticles(level, be.getBlockPos().getY() + 0.8, tick, since, trace, wave);
    }

    // ------------------------------------------------------------------
    // layers
    // ------------------------------------------------------------------

    private static float easeOut(float k) {
        return 1.0F - (1.0F - k) * (1.0F - k) * (1.0F - k);
    }

    /** Height of segment i of a layer that starts after delay and rises around the circle. */
    private static float segmentHeight(int i, float since, float delay) {
        float k = Mth.clamp((since - delay - i / (float) SEGMENTS * TRACE_SECONDS) / RISE_SECONDS, 0.0F, 1.0F);
        return MAX_HEIGHT * easeOut(k);
    }

    private static float fade(float y, float height) {
        float top = Mth.clamp((height - y) / 50.0F, 0.0F, 1.0F);
        // brighter near the source
        float base = 1.0F + 0.6F * Mth.clamp(1.0F - y / 12.0F, 0.0F, 1.0F);
        return top * base;
    }

    /** Textured cylinder with the beam texture streaming upwards. */
    private static void streakLayer(VertexConsumer vc, Matrix4f m, float r, int rgb, float alpha, float time,
                                    float since, float delay, float spin, float speed) {
        int cr = (rgb >> 16) & 255;
        int cg = (rgb >> 8) & 255;
        int cb = rgb & 255;
        float scroll = -time * speed;
        for (int i = 0; i < SEGMENTS; i++) {
            float height = segmentHeight(i, since, delay);
            if (height <= 0.01F) {
                continue;
            }
            float t0 = spin + (float) (i * Math.PI * 2.0 / SEGMENTS);
            float t1 = spin + (float) ((i + 1) * Math.PI * 2.0 / SEGMENTS);
            float x0 = Mth.cos(t0) * r;
            float z0 = Mth.sin(t0) * r;
            float x1 = Mth.cos(t1) * r;
            float z1 = Mth.sin(t1) * r;
            // the texture wraps around the column three times
            float u0 = i * 3.0F / SEGMENTS;
            float u1 = (i + 1) * 3.0F / SEGMENTS;
            float step = 8.0F;
            for (float y0 = 0.0F; y0 < height; y0 += step) {
                float y1 = Math.min(height, y0 + step);
                int a0 = Mth.clamp((int) (alpha * fade(y0, height) * 255), 0, 255);
                int a1 = Mth.clamp((int) (alpha * fade(y1, height) * 255), 0, 255);
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

    private static void tex(VertexConsumer vc, Matrix4f m, float x, float y, float z, float u, float v,
                            int r, int g, int b, int a) {
        vc.addVertex(m, x, y, z).setColor(r, g, b, a).setUv(u, v).setLight(LightTexture.FULL_BRIGHT).setNormal(0.0F, 1.0F, 0.0F);
    }

    /** Soft additive halo around the column, rising with it, gently pulsing. */
    private static void haloLayer(VertexConsumer vc, Matrix4f m, float r, int rgb, float alpha, float time,
                                  float since, float delay, float spin) {
        for (int i = 0; i < SEGMENTS; i++) {
            float height = segmentHeight(i, since, delay);
            if (height <= 0.01F) {
                continue;
            }
            float t0 = spin + (float) (i * Math.PI * 2.0 / SEGMENTS);
            float t1 = spin + (float) ((i + 1) * Math.PI * 2.0 / SEGMENTS);
            float x0 = Mth.cos(t0) * r;
            float z0 = Mth.sin(t0) * r;
            float x1 = Mth.cos(t1) * r;
            float z1 = Mth.sin(t1) * r;
            float step = 10.0F;
            for (float y0 = 0.0F; y0 < height; y0 += step) {
                float y1 = Math.min(height, y0 + step);
                float p0 = 0.8F + 0.2F * Mth.sin((y0 - time * 10.0F) * 0.25F);
                float p1 = 0.8F + 0.2F * Mth.sin((y1 - time * 10.0F) * 0.25F);
                quad(vc, m, x0, z0, x1, z1, y0, y1, rgb, alpha * p0 * fade(y0, height), alpha * p1 * fade(y1, height));
            }
        }
    }

    /** A full cylinder band between two heights; alpha may differ at its bottom and top. */
    private static void wall(VertexConsumer vc, Matrix4f m, float r, float y0, float y1, int rgb, float aBottom,
                             float aTop, float spin) {
        if ((aBottom <= 0.004F && aTop <= 0.004F) || y1 <= y0 || r <= 0.0F) {
            return;
        }
        int segments = Math.max(SEGMENTS, (int) (r * 4));
        for (int i = 0; i < segments; i++) {
            float t0 = spin + (float) (i * Math.PI * 2.0 / segments);
            float t1 = spin + (float) ((i + 1) * Math.PI * 2.0 / segments);
            quad(vc, m, Mth.cos(t0) * r, Mth.sin(t0) * r, Mth.cos(t1) * r, Mth.sin(t1) * r, y0, y1, rgb, aBottom, aTop);
        }
    }

    /**
     * The expanding shock front at progress e (0..1): a glowing wall that grows
     * lower as it spreads, a soft gradient trail on the ground behind it and a
     * bright leading edge.
     */
    private static void shockFront(VertexConsumer vc, Matrix4f m, float e, int rgb, float strength) {
        if (e <= 0.0F || e >= 1.0F) {
            return;
        }
        float front = RADIUS + WAVE_REACH * easeOut(e);
        float fadeOut = (float) Math.pow(1.0F - e, 1.4F) * strength;
        float wallH = 0.4F + 2.6F * (1.0F - e);
        float trail = 3.0F + 7.0F * e;
        // trail: transparent far behind, brightest right behind the front
        disc(vc, m, Math.max(0.0F, front - trail), front, 0.06F, rgb, 0.0F, 0.45F * fadeOut);
        // the front itself, white-hot at the edge
        disc(vc, m, front - 0.35F, front + 0.15F, 0.08F, 0xFFFFFF, 0.8F * fadeOut, 0.8F * fadeOut);
        wall(vc, m, front, 0.0F, wallH, rgb, 0.65F * fadeOut, 0.0F, 0.0F);
        wall(vc, m, front - 0.25F, 0.0F, wallH * 0.6F, 0xFFFFFF, 0.35F * fadeOut, 0.0F, 0.0F);
    }

    /** A vertical bright streak (the spark tracing the circle). */
    private static void spark(VertexConsumer vc, Matrix4f m, float x, float z, float w, float h, int rgb, float alpha) {
        quad(vc, m, x - w, z, x + w, z, 0.0F, h, rgb, alpha, 0.0F);
        quad(vc, m, x, z - w, x, z + w, 0.0F, h, rgb, alpha, 0.0F);
    }

    /** One wall quad, both windings so it is visible from outside and from inside. */
    private static void quad(VertexConsumer vc, Matrix4f m, float x0, float z0, float x1, float z1,
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
    private static void disc(VertexConsumer vc, Matrix4f m, float r0, float r1, float y, int rgb, float aInner,
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

    /** The traced part of the rim circle, from 0 to turn fraction f (0..1). */
    private static void arc(VertexConsumer vc, Matrix4f m, float r0, float r1, float y, float f, int rgb, float alpha) {
        int cr = (rgb >> 16) & 255;
        int cg = (rgb >> 8) & 255;
        int cb = rgb & 255;
        int a = Mth.clamp((int) (alpha * 255), 0, 255);
        int segments = 64;
        int n = (int) Math.ceil(f * segments);
        for (int i = 0; i < n; i++) {
            float t0 = (float) (i / (float) segments * Math.PI * 2.0);
            float t1 = (float) (Math.min(f, (i + 1) / (float) segments) * Math.PI * 2.0);
            float c0 = Mth.cos(t0);
            float s0 = Mth.sin(t0);
            float c1 = Mth.cos(t1);
            float s1 = Mth.sin(t1);
            vc.addVertex(m, c0 * r0, y, s0 * r0).setColor(cr, cg, cb, a);
            vc.addVertex(m, c0 * r1, y, s0 * r1).setColor(cr, cg, cb, a);
            vc.addVertex(m, c1 * r1, y, s1 * r1).setColor(cr, cg, cb, a);
            vc.addVertex(m, c1 * r0, y, s1 * r0).setColor(cr, cg, cb, a);

            vc.addVertex(m, c1 * r0, y, s1 * r0).setColor(cr, cg, cb, a);
            vc.addVertex(m, c1 * r1, y, s1 * r1).setColor(cr, cg, cb, a);
            vc.addVertex(m, c0 * r1, y, s0 * r1).setColor(cr, cg, cb, a);
            vc.addVertex(m, c0 * r0, y, s0 * r0).setColor(cr, cg, cb, a);
        }
    }

    // ------------------------------------------------------------------
    // particles (once per game tick)
    // ------------------------------------------------------------------

    private static void spawnParticles(Level level, double base, long tick, float since, float trace, float wave) {
        if (tick == lastParticleTick) {
            return;
        }
        lastParticleTick = tick;
        double cx = 0.5;
        double cz = 0.5;
        if (since < TRACE_SECONDS) {
            // sparks thrown off by the spark tracing the circle
            double head = trace * Math.PI * 2.0;
            for (int i = 0; i < 4; i++) {
                level.addParticle(ParticleTypes.END_ROD, cx + Math.cos(head) * RADIUS, base + 0.1,
                        cz + Math.sin(head) * RADIUS, (level.random.nextDouble() - 0.5) * 0.12,
                        0.05 + level.random.nextDouble() * 0.2, (level.random.nextDouble() - 0.5) * 0.12);
            }
            return;
        }
        // portal sparks streaming up inside the beam
        for (int i = 0; i < 8; i++) {
            double a = level.random.nextDouble() * Math.PI * 2.0;
            double r = level.random.nextDouble() * RADIUS;
            double y = base + level.random.nextDouble() * 28.0;
            level.addParticle(ParticleTypes.REVERSE_PORTAL, cx + Math.cos(a) * r, y, cz + Math.sin(a) * r,
                    0.0, 0.3 + level.random.nextDouble() * 0.4, 0.0);
        }
        if (level.random.nextInt(2) == 0) {
            double a = level.random.nextDouble() * Math.PI * 2.0;
            level.addParticle(ParticleTypes.END_ROD, cx + Math.cos(a) * RADIUS, base + 0.2,
                    cz + Math.sin(a) * RADIUS, 0.0, 0.4, 0.0);
        }
        // debris and sparks kicked up along the shock front
        if (wave > 0.0F && wave < 0.85F) {
            double front = RADIUS + WAVE_REACH * easeOut(wave);
            for (int i = 0; i < 18; i++) {
                double a = level.random.nextDouble() * Math.PI * 2.0;
                double ca = Math.cos(a);
                double sa = Math.sin(a);
                double x = cx + ca * front;
                double z = cz + sa * front;
                level.addParticle(ParticleTypes.REVERSE_PORTAL, x, base + 0.2, z, ca * 0.25, 0.15, sa * 0.25);
                if (i % 3 == 0) {
                    level.addParticle(ParticleTypes.CLOUD, x, base + 0.3, z, ca * 0.15, 0.04, sa * 0.15);
                }
                if (i % 6 == 0) {
                    level.addParticle(ParticleTypes.END_ROD, x, base + 0.4, z, ca * 0.1, 0.2, sa * 0.1);
                }
            }
        }
    }
}
