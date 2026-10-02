package com.fivemcodehub.sentinel.check;

import com.fivemcodehub.sentinel.telemetry.LatencyCompensator;
import com.fivemcodehub.sentinel.telemetry.ViolationTracker;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Attack-distance heuristic.
 *
 * <p>Measures eye-to-hitbox distance at the moment damage is applied. The
 * subtlety is that the server sees the attacker's position after their
 * movement packet, while the client aimed at where the target was one or more
 * ticks earlier; at 200ms that discrepancy alone is roughly a block. The
 * latency compensator absorbs it so only sustained overreach accumulates.
 */
public final class CombatReachCheck implements Listener {

    private static final String CHECK = "combat-reach";

    /** Vanilla survival interaction range, before compensation. */
    private static final double BASE_REACH = 3.0;

    private final JavaPlugin plugin;
    private final ViolationTracker violations;
    private final LatencyCompensator latency;

    public CombatReachCheck(JavaPlugin plugin, ViolationTracker violations,
                            LatencyCompensator latency) {
        this.plugin = plugin;
        this.violations = violations;
        this.latency = latency;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (attacker.hasPermission("sentinel.bypass")) return;
        if (attacker.getGameMode() == GameMode.CREATIVE) return;
        if (latency.shouldSuspendChecks()) return;

        Entity target = event.getEntity();
        if (target.getWorld() != attacker.getWorld()) return;

        // Measure to the nearest point of the hitbox, not the entity origin:
        // origin distance over-reports against tall mobs.
        double distance = attacker.getEyeLocation()
                .toVector()
                .distance(nearestHitboxPoint(attacker, target));

        double threshold = BASE_REACH
                * plugin.getConfig().getDouble("checks.combat-reach.leniency", 1.10)
                * latency.thresholdMultiplier(attacker);

        if (distance <= threshold) return;

        double excess = distance - threshold;
        double weight = Math.min(5.0, excess * 4.0);
        double level = violations.flag(attacker.getUniqueId(), CHECK, weight);

        if (level >= plugin.getConfig().getDouble("checks.combat-reach.log-threshold", 10.0)) {
            plugin.getLogger().info(String.format(
                    "[%s] %s vl=%.1f distance=%.2f threshold=%.2f ping=%dms",
                    CHECK, attacker.getName(), level, distance, threshold,
                    latency.pingOf(attacker)));
        }
    }

    private org.bukkit.util.Vector nearestHitboxPoint(Player attacker, Entity target) {
        var box = target.getBoundingBox();
        var eye = attacker.getEyeLocation().toVector();
        return new org.bukkit.util.Vector(
                clamp(eye.getX(), box.getMinX(), box.getMaxX()),
                clamp(eye.getY(), box.getMinY(), box.getMaxY()),
                clamp(eye.getZ(), box.getMinZ(), box.getMaxZ()));
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
