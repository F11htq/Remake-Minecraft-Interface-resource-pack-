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
 * bedrock rim. It grows upwards when the portal first appears, then keeps
 * pulsing: layered additive glow, pulses running up the column, cyberpunk
 * scan rings, a shockwave on the ground and portal particles.
 */
public final class PortalBeam {

    private static final float RADIUS = 3.6F;
    private static final float MAX_HEIGHT = 300.0F;
    private static final float RISE_SECONDS = 4.0F;
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
        float sinceRise = (tick - riseStartTick + partialTick) / 20.0F;
        float rise = Mth.clamp(sinceRise / RISE_SECONDS, 0.0F, 1.0F);
        float height = MAX_HEIGHT * (1.0F - (1.0F - rise) * (1.0F - rise) * (1.0F - rise));

        ps.pushPose();
        // the anchor block is at x 1; the beam is centred on the pillar at x 0.5, z 0.5
        ps.translate(-0.5F, 0.75F, 0.5F);
        Matrix4f m = ps.last().pose();
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());

        // rare glitch: the column jitters wider for a moment
        float glitch = Mth.frac(time * 0.37F) < 0.015F ? 0.35F : 0.0F;

        // layered column, from the white-hot core out to the soft rim (additive)
        column(vc, m, 0.55F, height, 0xF4E6FF, 0.55F, time, 1.4F);
        column(vc, m, 1.4F, height, 0xE070FF, 0.30F, time, 1.0F);
        column(vc, m, 2.5F + glitch, height, 0xB040FF, 0.20F, time, 0.8F);
        column(vc, m, RADIUS + glitch, height, 0x7A28E0, 0.13F, time, 0.6F);

        // the leading edge glows brighter while the beam is still rising
        if (rise < 1.0F) {
            float capA = 0.6F * (1.0F - rise);
            band(vc, m, RADIUS * 0.9F, Math.max(0.0F, height - 6.0F), height, 0xFFFFFF, capA);
            band(vc, m, 1.6F, Math.max(0.0F, height - 10.0F), height, 0xFF80F0, capA);
        }

        // cyberpunk scan rings running up the outside of the column
        for (int i = 0; i < 4; i++) {
            float phase = Mth.frac(time * 0.45F + i * 0.25F);
            float y = phase * Math.min(height, 90.0F);
            int c = i % 2 == 0 ? 0x00F0FF : 0xFF2AD4;
            band(vc, m, RADIUS + 0.25F, y, y + 0.22F, c, 0.55F * (1.0F - phase));
        }

        // glowing floor disc and the shockwave when the beam is born
        ring(vc, m, 0.0F, RADIUS + 0.6F, 0.02F, 0xB040FF, 0.22F + 0.08F * Mth.sin(time * 3.0F));
        if (sinceRise < 2.5F) {
            float e = sinceRise / 2.5F;
            float r = RADIUS + 26.0F * (1.0F - (1.0F - e) * (1.0F - e));
            ring(vc, m, r - 1.6F, r, 0.05F, 0xE070FF, 0.7F * (1.0F - e));
            ring(vc, m, r - 0.3F, r, 0.06F, 0xFFFFFF, 0.8F * (1.0F - e));
        }
        ps.popPose();

        // particles once per tick: portal sparks streaming up the beam
        if (tick != lastParticleTick) {
            lastParticleTick = tick;
            double cx = 0.5;
            double cz = 0.5;
            double base = be.getBlockPos().getY() + 0.8;
            for (int i = 0; i < 6; i++) {
                double a = level.random.nextDouble() * Math.PI * 2.0;
                double r = level.random.nextDouble() * RADIUS;
                double y = base + level.random.nextDouble() * Math.min(height, 24.0);
                level.addParticle(ParticleTypes.REVERSE_PORTAL, cx + Math.cos(a) * r, y, cz + Math.sin(a) * r,
                        0.0, 0.25 + level.random.nextDouble() * 0.3, 0.0);
            }
            if (level.random.nextInt(3) == 0) {
                double a = level.random.nextDouble() * Math.PI * 2.0;
                level.addParticle(ParticleTypes.END_ROD, cx + Math.cos(a) * RADIUS, base + 0.2, cz + Math.sin(a) * RADIUS,
                        0.0, 0.35, 0.0);
            }
        }
    }

    /** A cylinder whose brightness pulses upwards and fades out at the top. */
    private static void column(VertexConsumer vc, Matrix4f m, float r, float height, int rgb, float alpha,
                               float time, float pulse) {
        if (height <= 0.01F) {
            return;
        }
        float step = 6.0F;
        for (float y0 = 0.0F; y0 < height; y0 += step) {
            float y1 = Math.min(height, y0 + step);
            float a0 = alpha * shade(y0, height, time, pulse);
            float a1 = alpha * shade(y1, height, time, pulse);
            tube(vc, m, r, y0, y1, rgb, a0, a1, time);
        }
    }

    private static float shade(float y, float height, float time, float pulse) {
        float wave = 0.78F + 0.22F * Mth.sin((y - time * 9.0F) * 0.32F) * pulse;
        float top = Mth.clamp((height - y) / 40.0F, 0.0F, 1.0F);
        float bottom = Mth.clamp(y / 1.5F + 0.6F, 0.0F, 1.0F);
        return wave * top * bottom;
    }

    private static void band(VertexConsumer vc, Matrix4f m, float r, float y0, float y1, int rgb, float alpha) {
        if (alpha > 0.005F && y1 > y0) {
            tube(vc, m, r, y0, y1, rgb, alpha, alpha, 0.0F);
        }
    }

    /** Open cylinder, both windings so it is visible from outside and from inside. */
    private static void tube(VertexConsumer vc, Matrix4f m, float r, float y0, float y1, int rgb,
                             float a0, float a1, float time) {
        int cr = (rgb >> 16) & 255;
        int cg = (rgb >> 8) & 255;
        int cb = rgb & 255;
        int ia0 = Mth.clamp((int) (a0 * 255), 0, 255);
        int ia1 = Mth.clamp((int) (a1 * 255), 0, 255);
        float spin = time * 0.6F;
        for (int i = 0; i < SEGMENTS; i++) {
            float t0 = spin + (float) (i * Math.PI * 2.0 / SEGMENTS);
            float t1 = spin + (float) ((i + 1) * Math.PI * 2.0 / SEGMENTS);
            float x0 = Mth.cos(t0) * r;
            float z0 = Mth.sin(t0) * r;
            float x1 = Mth.cos(t1) * r;
            float z1 = Mth.sin(t1) * r;
            // faint spiral streaks swirling around the column
            float streak = 0.75F + 0.25F * Mth.sin(i * 1.7F + time * 2.0F);
            int b0 = (int) (ia0 * streak);
            int b1 = (int) (ia1 * streak);
            vc.addVertex(m, x0, y0, z0).setColor(cr, cg, cb, b0);
            vc.addVertex(m, x1, y0, z1).setColor(cr, cg, cb, b0);
            vc.addVertex(m, x1, y1, z1).setColor(cr, cg, cb, b1);
            vc.addVertex(m, x0, y1, z0).setColor(cr, cg, cb, b1);

            vc.addVertex(m, x0, y1, z0).setColor(cr, cg, cb, b1);
            vc.addVertex(m, x1, y1, z1).setColor(cr, cg, cb, b1);
            vc.addVertex(m, x1, y0, z1).setColor(cr, cg, cb, b0);
            vc.addVertex(m, x0, y0, z0).setColor(cr, cg, cb, b0);
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
