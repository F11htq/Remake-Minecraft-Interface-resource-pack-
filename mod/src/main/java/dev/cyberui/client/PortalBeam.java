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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.TheEndPortalBlockEntity;
import org.joml.Matrix4f;

/**
 * A purple energy beam through the End exit portal, as wide as its bedrock rim.
 * When the portal first appears a white-hot needle drops out of the sky and
 * pierces the island; on impact the beam bursts open to full width, a shock
 * front rolls across the island and the ground shakes. The beam then stays,
 * reaching up into the sky and down into the void below the island: a
 * textured white-hot core inside streaming energy layers (the vanilla beam
 * texture, scrolling upwards) and a soft additive halo.
 */
public final class PortalBeam {

    private static final ResourceLocation BEAM = ResourceLocation.withDefaultNamespace("textures/entity/beacon_beam.png");
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

    private static Level strikeLevel = null;
    private static long strikeStartTick = 0L;
    private static long lastParticleTick = -1L;
    private static boolean impactPlayed = false;
    private static boolean burstDone = false;

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

    /** Seconds until the falling needle reaches the portal (portal-relative height 0). */
    private static float impactTime() {
        return TOP / STRIKE_SPEED;
    }

    public static void render(TheEndPortalBlockEntity be, float partialTick, PoseStack ps, MultiBufferSource buffers) {
        if (!isAnchor(be)) {
            return;
        }
        Level level = be.getLevel();
        long tick = level.getGameTime();
        if (level != strikeLevel) {
            strikeLevel = level;
            strikeStartTick = tick;
            impactPlayed = false;
            burstDone = false;
        }
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

        ps.pushPose();
        // the anchor block is at x 1: move onto the pillar's block, the core centres itself on it
        ps.translate(-1.0F, 0.75F, 0.0F);

        // white-hot textured core
        if (width > 0.0F) {
            int bottom = (int) Math.floor(tip);
            BeaconRenderer.renderBeaconBeam(ps, buffers, BEAM, partialTick, 1.0F, tick, bottom, (int) TOP - bottom,
                    0xFFF0DCFF, 0.85F * width, 1.3F * width);
        }

        // move to the pillar centre for everything else
        ps.translate(0.5F, 0.0F, 0.5F);
        Matrix4f m = ps.last().pose();
        float spin = time * 0.3F;

        // streaming energy layers; inner ones turn the other way
        if (width > 0.0F) {
            VertexConsumer streaks = buffers.getBuffer(RenderType.beaconBeam(BEAM, true));
            streakLayer(streaks, m, 1.9F * width, 0xFF70F0, 0.60F, time, tip, depth, -spin * 1.6F, 1.6F);
            streakLayer(streaks, m, 2.8F * width, 0xC24CFF, 0.42F, time, tip, depth, spin * 1.2F, 1.1F);
            streakLayer(streaks, m, RADIUS * width, 0x8A34F0, 0.30F, time, tip, depth, -spin, 0.8F);
        }

        VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
        if (sinceImpact <= 0.0F) {
            // the falling needle: a white-hot thread with a bright head
            wall(glow, m, 0.3F, tip, TOP, 0xFFFFFF, 0.9F, 0.9F);
            wall(glow, m, 0.9F, tip, TOP, 0xE070FF, 0.35F, 0.35F);
            wall(glow, m, 1.4F, tip, tip + 6.0F, 0xFFFFFF, 0.7F, 0.0F);
        } else {
            // soft halo around the column, flaring on impact
            float glowBoost = 1.0F + 2.5F * flash;
            haloLayer(glow, m, (RADIUS + 0.9F) * width, 0x9A3CFF, 0.10F * glowBoost, time, tip, depth);
            haloLayer(glow, m, (RADIUS + 2.4F) * width, 0x6A22D0, 0.05F * glowBoost, time, tip, depth);
            // the beam glows brightest where it leaves the portal
            float base = (0.32F + 0.08F * Mth.sin(time * 5.0F)) * Math.min(1.0F, open * 2.0F) + 0.6F * flash;
            wall(glow, m, RADIUS + 1.2F, 0.0F, 5.0F, 0xE070FF, base, 0.0F);
            wall(glow, m, RADIUS * 0.6F, 0.0F, 8.0F, 0xFFFFFF, base * 0.8F, 0.0F);
            disc(glow, m, 0.0F, RADIUS + 1.6F, 0.03F, 0xB040FF, 0.30F + 0.5F * flash, 0.0F);
            // impact flash across the ground
            if (flash > 0.0F) {
                disc(glow, m, 0.0F, RADIUS + 12.0F, 0.05F, 0xF0D0FF, 0.6F * flash, 0.0F);
            }
        }

        // the shock front and its echo
        shockFront(glow, m, sinceImpact / WAVE_SECONDS, 0xE070FF, 1.0F);
        shockFront(glow, m, sinceImpact / WAVE_SECONDS - 0.12F, 0x40E0FF, 0.55F);
        ps.popPose();

        double baseY = be.getBlockPos().getY() + 0.8;
        if (sinceImpact > 0.0F && !impactPlayed) {
            impactPlayed = true;
            level.playLocalSound(0.5, baseY, 0.5, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.AMBIENT,
                    6.0F, 0.55F, false);
            level.playLocalSound(0.5, baseY, 0.5, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.AMBIENT,
                    3.0F, 0.6F, false);
        }
        spawnParticles(level, baseY, tick, sinceImpact);
    }

    /**
     * Camera shake for the earthquake after impact, as {yaw, pitch} offsets in
     * degrees, or null when the ground is still. Stronger near the portal.
     */
    public static float[] quake(Entity viewer, float partialTick) {
        if (viewer == null || strikeLevel == null || viewer.level() != strikeLevel) {
            return null;
        }
        float t = (strikeLevel.getGameTime() - strikeStartTick + partialTick) / 20.0F - impactTime();
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

    /** Textured cylinder with the beam texture streaming upwards, from the bottom to the sky. */
    private static void streakLayer(VertexConsumer vc, Matrix4f m, float r, int rgb, float alpha, float time,
                                    float bottom, float depth, float spin, float speed) {
        int cr = (rgb >> 16) & 255;
        int cg = (rgb >> 8) & 255;
        int cb = rgb & 255;
        float scroll = -time * speed;
        float step = 8.0F;
        for (int i = 0; i < SEGMENTS; i++) {
            float t0 = spin + (float) (i * Math.PI * 2.0 / SEGMENTS);
            float t1 = spin + (float) ((i + 1) * Math.PI * 2.0 / SEGMENTS);
            float x0 = Mth.cos(t0) * r;
            float z0 = Mth.sin(t0) * r;
            float x1 = Mth.cos(t1) * r;
            float z1 = Mth.sin(t1) * r;
            // the texture wraps around the column three times
            float u0 = i * 3.0F / SEGMENTS;
            float u1 = (i + 1) * 3.0F / SEGMENTS;
            for (float y0 = bottom; y0 < TOP; y0 += step) {
                float y1 = Math.min(TOP, y0 + step);
                int a0 = Mth.clamp((int) (alpha * fade(y0, bottom, depth) * 255), 0, 255);
                int a1 = Mth.clamp((int) (alpha * fade(y1, bottom, depth) * 255), 0, 255);
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

    private static void tex(VertexConsumer vc, Matrix4f m, float x, float y, float z, float u, float v,
                            int r, int g, int b, int a) {
        vc.addVertex(m, x, y, z).setColor(r, g, b, a).setUv(u, v).setLight(LightTexture.FULL_BRIGHT).setNormal(0.0F, 1.0F, 0.0F);
    }

    /** Soft additive halo around the column, with slow pulses travelling upwards. */
    private static void haloLayer(VertexConsumer vc, Matrix4f m, float r, int rgb, float alpha, float time,
                                  float bottom, float depth) {
        float step = 10.0F;
        for (int i = 0; i < SEGMENTS; i++) {
            float t0 = (float) (i * Math.PI * 2.0 / SEGMENTS);
            float t1 = (float) ((i + 1) * Math.PI * 2.0 / SEGMENTS);
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
    private static void wall(VertexConsumer vc, Matrix4f m, float r, float y0, float y1, int rgb, float aBottom,
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
    private static void shockFront(VertexConsumer vc, Matrix4f m, float e, int rgb, float strength) {
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
                double a = level.random.nextDouble() * Math.PI * 2.0;
                double s = 0.4 + level.random.nextDouble() * 0.8;
                level.addParticle(i % 2 == 0 ? ParticleTypes.END_ROD : ParticleTypes.REVERSE_PORTAL,
                        cx + Math.cos(a) * RADIUS, base + 0.3, cz + Math.sin(a) * RADIUS,
                        Math.cos(a) * s, 0.2 + level.random.nextDouble() * 0.6, Math.sin(a) * s);
            }
        }
        // portal sparks streaming up inside the beam
        for (int i = 0; i < 8; i++) {
            double a = level.random.nextDouble() * Math.PI * 2.0;
            double r = level.random.nextDouble() * RADIUS;
            double y = base + level.random.nextDouble() * 28.0;
            level.addParticle(ParticleTypes.REVERSE_PORTAL, cx + Math.cos(a) * r, y, cz + Math.sin(a) * r,
                    0.0, 0.3 + level.random.nextDouble() * 0.4, 0.0);
        }
        // debris kicked up along the shock front, dust shaken loose by the quake
        float wave = sinceImpact / WAVE_SECONDS;
        if (wave < 0.85F) {
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
            }
        }
        if (sinceImpact < QUAKE_SECONDS) {
            for (int i = 0; i < 10; i++) {
                double a = level.random.nextDouble() * Math.PI * 2.0;
                double r = RADIUS + level.random.nextDouble() * 30.0;
                level.addParticle(ParticleTypes.ASH, cx + Math.cos(a) * r, base + level.random.nextDouble() * 1.5,
                        cz + Math.sin(a) * r, 0.0, 0.1, 0.0);
            }
        }
    }
}
