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
import net.minecraft.world.entity.EntityTypes;
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

    /** The entity list is scanned at most this often; the bars themselves update every frame. */
    private static final long SCAN_EVERY_MS = 100L;

    private static final Map<UUID, Track> TRACKS = new HashMap<>();
    private static final List<LivingEntity> SHOWN = new ArrayList<>();
    private static long lastScan = 0L;

    private MiniBosses() {
    }

    static BossBarFx.Style styleOf(EntityType<?> type) {
        if (type == EntityTypes.WARDEN) {
            return BossBarFx.Style.WARDEN;
        } else if (type == EntityTypes.IRON_GOLEM) {
            return BossBarFx.Style.GOLEM;
        } else if (type == EntityTypes.RAVAGER) {
            return BossBarFx.Style.RAVAGER;
        } else if (type == EntityTypes.EVOKER) {
            return BossBarFx.Style.EVOKER;
        } else if (type == EntityTypes.GUARDIAN || type == EntityTypes.ELDER_GUARDIAN) {
            return BossBarFx.Style.GUARDIAN;
        }
        return null;
    }

    /** Mobs dangerous enough to show their bar as soon as they are close. */
    private static boolean showsOnApproach(EntityType<?> type) {
        return type == EntityTypes.WARDEN || type == EntityTypes.RAVAGER
                || type == EntityTypes.EVOKER || type == EntityTypes.ELDER_GUARDIAN;
    }

    /** Common mobs do not get the GG victory screen. */
    private static boolean earnsGg(EntityType<?> type) {
        return type != EntityTypes.GUARDIAN;
    }

    public static List<Entry> collect(long now) {
        List<Entry> out = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            TRACKS.clear();
            SHOWN.clear();
            return out;
        }
        if (now - lastScan >= SCAN_EVERY_MS || now < lastScan) {
            lastScan = now;
            scan(mc, now);
        }
        for (int i = 0; i < Math.min(MAX_BARS, SHOWN.size()); i++) {
            LivingEntity mob = SHOWN.get(i);
            if (mob.isRemoved() || mob.level() != mc.level) {
                // gone: BossBarFx counts a bar that vanished while almost dead as a win
                continue;
            }
            float max = Math.max(1.0F, mob.getMaxHealth());
            float progress = mob.isDeadOrDying() ? 0.0F : mob.getHealth() / max;
            out.add(new Entry(mob.getUUID(), mob, styleOf(mob.getType()), progress, earnsGg(mob.getType())));
        }
        return out;
    }

    private static void scan(Minecraft mc, long now) {
        SHOWN.clear();
        double scan2 = SCAN_RANGE * SCAN_RANGE;
        double engage2 = ENGAGE_RANGE * ENGAGE_RANGE;
        double approach2 = APPROACH_RANGE * APPROACH_RANGE;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity mob) || styleOf(e.getType()) == null) {
                continue;
            }
            double dist2 = e.distanceToSqr(mc.player);
            if (dist2 > scan2) {
                continue;
            }
            Track t = TRACKS.computeIfAbsent(e.getUUID(), id -> new Track());
            float hp = mob.getHealth();
            if (t.lastHp >= 0.0F && hp < t.lastHp - 0.01F && dist2 < engage2) {
                t.engagedUntil = now + ENGAGED_MS;
            }
            t.lastHp = hp;
            t.seen = now;
            if (now < t.engagedUntil || (showsOnApproach(e.getType()) && dist2 < approach2 && mob.isAlive())) {
                SHOWN.add(mob);
            }
        }
        TRACKS.values().removeIf(t -> now - t.seen > 1000L);
        SHOWN.sort(Comparator.comparingDouble(m -> m.distanceToSqr(mc.player)));
    }
}
