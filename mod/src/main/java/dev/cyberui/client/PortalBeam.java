package dev.cyberui.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.TheEndPortalBlockEntity;
import org.joml.Matrix4f;

/**
 * A purple energy beam rising out of the End exit portal, as wide as its
 * bedrock rim. When the portal first appears a glowing circle is traced around
 * the rim and the beam rises around it segment by segment, then keeps pulsing: layered additive glow, pulses running up the column, cyberpunk
 * scan rings, a shockwave on the ground and portal particles.
 */
public final class PortalBeam {

    private static final float RADIUS = 3.6F;
    private static final float MAX_HEIGHT = 300.0F;
    private static final float RISE_SECONDS = 4.0F;
    /** Time to trace the circle around the rim; the wall rises around it in the same rhythm. */
    private static final float TRACE_SECONDS = 1.6F;
    private static final int SEGMENTS = 28;

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
        // the circle is traced first; the column only starts turning once it is complete
        float spin = Math.max(0.0F, since - TRACE_SECONDS) * 0.6F;
        float settled = Mth.clamp((since - TRACE_SECONDS) / 1.5F, 0.0F, 1.0F);

        ps.pushPose();
        // the anchor block is at x 1; the beam is centred on the pillar at x 0.5, z 0.5
        ps.translate(-0.5F, 0.75F, 0.5F);
        Matrix4f m = ps.last().pose();
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());

        // rare glitch: the column jitters wider for a moment
        float glitch = settled >= 1.0F && Mth.frac(time * 0.37F) < 0.015F ? 0.35F : 0.0F;

        // 1. a glowing circle is traced around the bedrock rim, a spark leading the way
        float trace = Mth.clamp(since / TRACE_SECONDS, 0.0F, 1.0F);
        float traceA = since < TRACE_SECONDS ? 0.95F : 0.95F * Math.max(0.0F, 1.0F - (since - TRACE_SECONDS) / 1.2F);
        if (traceA > 0.0F) {
            arc(vc, m, RADIUS - 0.25F, RADIUS + 0.3F, 0.03F, 0.0F, trace, 0xE070FF, traceA);
            arc(vc, m, RADIUS - 0.05F, RADIUS + 0.1F, 0.04F, 0.0F, trace, 0xFFFFFF, traceA);
        }
        if (since < TRACE_SECONDS) {
            float head = (float) (trace * Math.PI * 2.0);
            float hx = Mth.cos(head) * RADIUS;
            float hz = Mth.sin(head) * RADIUS;
            spark(vc, m, hx, hz, 0.18F, 3.5F, 0xFFFFFF, 0.9F);
            spark(vc, m, hx, hz, 0.45F, 2.0F, 0xFF60F0, 0.5F);
        }

        // 2. the wall rises segment by segment around the circle, inner layers follow
        column(vc, m, RADIUS + glitch, 0x7A28E0, 0.13F, time, 0.6F, since, 0.15F, spin, true);
        column(vc, m, 2.5F + glitch, 0xB040FF, 0.20F, time, 0.8F, since, 0.55F, spin, false);
        column(vc, m, 1.4F, 0xE070FF, 0.30F, time, 1.0F, since, 1.0F, spin, false);
        column(vc, m, 0.55F, 0xF4E6FF, 0.55F, time, 1.4F, since, 1.4F, spin, false);

        // cyberpunk scan rings running up the outside of the column
        if (settled > 0.0F) {
            for (int i = 0; i < 4; i++) {
                float phase = Mth.frac(time * 0.45F + i * 0.25F);
                float y = phase * 90.0F;
                int c = i % 2 == 0 ? 0x00F0FF : 0xFF2AD4;
                band(vc, m, RADIUS + 0.25F, y, y + 0.22F, c, 0.55F * (1.0F - phase) * settled, spin);
            }
        }

        // glowing floor disc, and a shockwave the moment the circle closes
        ring(vc, m, 0.0F, RADIUS + 0.6F, 0.02F, 0xB040FF, (0.22F + 0.08F * Mth.sin(time * 3.0F)) * settled);
        float wave = since - TRACE_SECONDS;
        if (wave > 0.0F && wave < 2.5F) {
            float e = wave / 2.5F;
            float r = RADIUS + 26.0F * (1.0F - (1.0F - e) * (1.0F - e));
            ring(vc, m, r - 1.6F, r, 0.05F, 0xE070FF, 0.7F * (1.0F - e));
            ring(vc, m, r - 0.3F, r, 0.06F, 0xFFFFFF, 0.8F * (1.0F - e));
        }
        ps.popPose();

        // particles once per tick: sparks following the trace, then portal sparks streaming up
        if (tick != lastParticleTick) {
            lastParticleTick = tick;
            double cx = 0.5;
            double cz = 0.5;
            double base = be.getBlockPos().getY() + 0.8;
            if (since < TRACE_SECONDS) {
                double head = trace * Math.PI * 2.0;
                for (int i = 0; i < 3; i++) {
                    level.addParticle(ParticleTypes.END_ROD, cx + Math.cos(head) * RADIUS, base + 0.1,
                            cz + Math.sin(head) * RADIUS, (level.random.nextDouble() - 0.5) * 0.1,
                            0.05 + level.random.nextDouble() * 0.15, (level.random.nextDouble() - 0.5) * 0.1);
                }
            } else {
                for (int i = 0; i < 6; i++) {
                    double a = level.random.nextDouble() * Math.PI * 2.0;
                    double r = level.random.nextDouble() * RADIUS;
                    double y = base + level.random.nextDouble() * 24.0;
                    level.addParticle(ParticleTypes.REVERSE_PORTAL, cx + Math.cos(a) * r, y, cz + Math.sin(a) * r,
                            0.0, 0.25 + level.random.nextDouble() * 0.3, 0.0);
                }
                if (level.random.nextInt(3) == 0) {
                    double a = level.random.nextDouble() * Math.PI * 2.0;
                    level.addParticle(ParticleTypes.END_ROD, cx + Math.cos(a) * RADIUS, base + 0.2,
                            cz + Math.sin(a) * RADIUS, 0.0, 0.35, 0.0);
                }
            }
        }
    }

    /**
     * A cylinder that rises one segment after another around the circle (each
     * segment starts a little later than the previous one), with brightness
     * pulses running upwards and a soft fade at the top.
     */
    private static void column(VertexConsumer vc, Matrix4f m, float r, int rgb, float alpha, float time,
                               float pulse, float since, float delay, float spin, boolean caps) {
        for (int i = 0; i < SEGMENTS; i++) {
            float frac = i / (float) SEGMENTS;
            float k = Mth.clamp((since - delay - frac * TRACE_SECONDS) / RISE_SECONDS, 0.0F, 1.0F);
            if (k <= 0.0F) {
                continue;
            }
            float height = MAX_HEIGHT * (1.0F - (1.0F - k) * (1.0F - k) * (1.0F - k));
            float t0 = spin + (float) (i * Math.PI * 2.0 / SEGMENTS);
            float t1 = spin + (float) ((i + 1) * Math.PI * 2.0 / SEGMENTS);
            float x0 = Mth.cos(t0) * r;
            float z0 = Mth.sin(t0) * r;
            float x1 = Mth.cos(t1) * r;
            float z1 = Mth.sin(t1) * r;
            // faint spiral streaks swirling around the column
            float streak = 0.75F + 0.25F * Mth.sin(i * 1.7F + time * 2.0F);
            float step = 6.0F;
            for (float y0 = 0.0F; y0 < height; y0 += step) {
                float y1 = Math.min(height, y0 + step);
                quad(vc, m, x0, z0, x1, z1, y0, y1, rgb,
                        alpha * streak * shade(y0, height, time, pulse), alpha * streak * shade(y1, height, time, pulse));
            }
            // the rising tip of each segment glows white-hot
            if (caps && k < 1.0F) {
                float capA = 0.7F * (1.0F - k);
                quad(vc, m, x0, z0, x1, z1, Math.max(0.0F, height - 5.0F), height, 0xFFFFFF, 0.0F, capA);
            }
        }
    }

    private static float shade(float y, float height, float time, float pulse) {
        float wave = 0.78F + 0.22F * Mth.sin((y - time * 9.0F) * 0.32F) * pulse;
        float top = Mth.clamp((height - y) / 40.0F, 0.0F, 1.0F);
        float bottom = Mth.clamp(y / 1.5F + 0.6F, 0.0F, 1.0F);
        return wave * top * bottom;
    }

    private static void band(VertexConsumer vc, Matrix4f m, float r, float y0, float y1, int rgb, float alpha,
                             float spin) {
        if (alpha <= 0.005F || y1 <= y0) {
            return;
        }
        for (int i = 0; i < SEGMENTS; i++) {
            float t0 = spin + (float) (i * Math.PI * 2.0 / SEGMENTS);
            float t1 = spin + (float) ((i + 1) * Math.PI * 2.0 / SEGMENTS);
            quad(vc, m, Mth.cos(t0) * r, Mth.sin(t0) * r, Mth.cos(t1) * r, Mth.sin(t1) * r, y0, y1, rgb, alpha, alpha);
        }
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

    /** Part of a flat ring, from turn fraction f0 to f1 (0..1). */
    private static void arc(VertexConsumer vc, Matrix4f m, float r0, float r1, float y, float f0, float f1,
                            int rgb, float alpha) {
        int cr = (rgb >> 16) & 255;
        int cg = (rgb >> 8) & 255;
        int cb = rgb & 255;
        int a = Mth.clamp((int) (alpha * 255), 0, 255);
        int segments = 64;
        int n = (int) Math.ceil((f1 - f0) * segments);
        for (int i = 0; i < n; i++) {
            float t0 = (float) ((f0 + i / (float) segments) * Math.PI * 2.0);
            float t1 = (float) (Math.min(f1, f0 + (i + 1) / (float) segments) * Math.PI * 2.0);
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

    /** Flat ring on the ground, visible from above and below. */
    private static void ring(VertexConsumer vc, Matrix4f m, float r0, float r1, float y, int rgb, float alpha) {
        int cr = (rgb >> 16) & 255;
        int cg = (rgb >> 8) & 255;
        int cb = rgb & 255;
        int a = Mth.clamp((int) (alpha * 255), 0, 255);
        int segments = Math.max(SEGMENTS, (int) (r1 * 6));
        for (int i = 0; i < segments; i++) {
            float t0 = (float) (i * Math.PI * 2.0 / segments);
            float t1 = (float) ((i + 1) * Math.PI * 2.0 / segments);
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
}
