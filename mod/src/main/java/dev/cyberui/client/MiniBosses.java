package dev.cyberui.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

/**
 * Health bars for strong mobs that have no vanilla boss bar (Warden, Iron Golem,
 * Ravager, Evoker, Guardian). A bar appears once the mob takes
 * damage near the player; the most dangerous ones also show it on approach.
 */
public final class MiniBosses {

    /** One mob bar for this frame. */
    public record Entry(UUID id, LivingEntity entity, BossBarFx.Style style, float progress, boolean gg) {
    }

    private static final class Track {
        float lastHp = -1.0F;
        long engagedUntil = 0L;
        long seen = 0L;
    }

    private static final int MAX_BARS = 2;
    private static final double SCAN_RANGE = 48.0;
    private static final double ENGAGE_RANGE = 32.0;
    private static final double APPROACH_RANGE = 16.0;
    private static final long ENGAGED_MS = 20000L;

    private static final Map<UUID, Track> TRACKS = new HashMap<>();

    private MiniBosses() {
    }

    static BossBarFx.Style styleOf(EntityType<?> type) {
        if (type == EntityType.WARDEN) {
            return BossBarFx.Style.WARDEN;
        } else if (type == EntityType.IRON_GOLEM) {
            return BossBarFx.Style.GOLEM;
        } else if (type == EntityType.RAVAGER) {
            return BossBarFx.Style.RAVAGER;
        } else if (type == EntityType.EVOKER) {
            return BossBarFx.Style.EVOKER;
        } else if (type == EntityType.GUARDIAN || type == EntityType.ELDER_GUARDIAN) {
            return BossBarFx.Style.GUARDIAN;
        }
        return null;
    }

    /** Mobs dangerous enough to show their bar as soon as they are close. */
    private static boolean showsOnApproach(EntityType<?> type) {
        return type == EntityType.WARDEN || type == EntityType.RAVAGER
                || type == EntityType.EVOKER || type == EntityType.ELDER_GUARDIAN;
    }

    /** Common mobs do not get the GG victory screen. */
    private static boolean earnsGg(EntityType<?> type) {
        return type != EntityType.GUARDIAN;
    }

    public static List<Entry> collect(long now) {
        List<Entry> out = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            TRACKS.clear();
            return out;
        }
        List<LivingEntity> shown = new ArrayList<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity mob) || styleOf(e.getType()) == null) {
                continue;
            }
            double dist = Math.sqrt(e.distanceToSqr(mc.player));
            if (dist > SCAN_RANGE) {
                continue;
            }
            Track t = TRACKS.computeIfAbsent(e.getUUID(), id -> new Track());
            float hp = mob.getHealth();
            if (t.lastHp >= 0.0F && hp < t.lastHp - 0.01F && dist < ENGAGE_RANGE) {
                t.engagedUntil = now + ENGAGED_MS;
            }
            t.lastHp = hp;
            t.seen = now;
            if (now < t.engagedUntil || (showsOnApproach(e.getType()) && dist < APPROACH_RANGE && mob.isAlive())) {
                shown.add(mob);
            }
        }
        TRACKS.values().removeIf(t -> now - t.seen > 1000L);

        shown.sort(Comparator.comparingDouble(m -> m.distanceToSqr(mc.player)));
        for (int i = 0; i < Math.min(MAX_BARS, shown.size()); i++) {
            LivingEntity mob = shown.get(i);
            float max = Math.max(1.0F, mob.getMaxHealth());
            float progress = mob.isDeadOrDying() ? 0.0F : mob.getHealth() / max;
            out.add(new Entry(mob.getUUID(), mob, styleOf(mob.getType()), progress, earnsGg(mob.getType())));
        }
        return out;
    }
}
