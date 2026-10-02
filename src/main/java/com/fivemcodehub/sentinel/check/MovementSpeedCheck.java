package com.fivemcodehub.sentinel.check;

import com.fivemcodehub.sentinel.telemetry.LatencyCompensator;
import com.fivemcodehub.sentinel.telemetry.ViolationTracker;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Horizontal speed heuristic.
 *
 * <p>Compares observed horizontal displacement per tick against a budget
 * derived from the player's actual movement state. Flying, vehicles, ice,
 * Speed effects and recent teleports all legitimately exceed the walking
 * baseline, so each is accounted for rather than flagged.
 */
public final class MovementSpeedCheck implements Listener {

    private static final String CHECK = "movement-speed";

    /** Vanilla sprint-jump peaks near 0.58 blocks/tick on flat ground. */
    private static final double SPRINT_JUMP_BASELINE = 0.58;

    private final JavaPlugin plugin;
    private final ViolationTracker violations;
    private final LatencyCompensator latency;

    private final Map<UUID, Long> teleportGrace = new ConcurrentHashMap<>();

    public MovementSpeedCheck(JavaPlugin plugin, ViolationTracker violations,
                              LatencyCompensator latency) {
        this.plugin = plugin;
        this.violations = violations;
        this.latency = latency;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("sentinel.bypass")) return;
        if (player.getGameMode() == GameMode.SPECTATOR
                || player.getGameMode() == GameMode.CREATIVE) return;
        if (player.isFlying() || player.isGliding() || player.isInsideVehicle()) return;
        if (latency.shouldSuspendChecks()) return;

        Long grace = teleportGrace.get(player.getUniqueId());
        if (grace != null && System.currentTimeMillis() - grace < 1_000L) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getWorld() == null || !from.getWorld().equals(to.getWorld())) return;

        // Horizontal only: vertical motion is governed by separate mechanics.
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double observed = Math.sqrt(dx * dx + dz * dz);

        double budget = SPRINT_JUMP_BASELINE;

        var speed = player.getPotionEffect(PotionEffectType.SPEED);
        if (speed != null) budget *= 1.0 + (0.20 * (speed.getAmplifier() + 1));

        if (player.isInWater()) {
            budget *= plugin.getConfig().getDouble("checks.movement-speed.water-factor", 0.9);
        }

        // Ice and packed ice legitimately exceed the walking baseline.
        var standingOn = to.clone().subtract(0, 1, 0).getBlock().getType();
        if (standingOn.name().endsWith("ICE")) {
            budget *= plugin.getConfig().getDouble("checks.movement-speed.ice-factor", 2.4);
        }

        double threshold = budget
                * plugin.getConfig().getDouble("checks.movement-speed.leniency", 1.15)
                * latency.thresholdMultiplier(player);

        if (observed <= threshold) return;

        double excess = (observed - threshold) / threshold;
        double weight = Math.min(5.0, excess * 10.0);
        double level = violations.flag(player.getUniqueId(), CHECK, weight);

        if (level >= plugin.getConfig().getDouble("checks.movement-speed.log-threshold", 12.0)) {
            plugin.getLogger().info(String.format(
                    "[%s] %s vl=%.1f observed=%.3f threshold=%.3f ping=%dms",
                    CHECK, player.getName(), level, observed, threshold, latency.pingOf(player)));
        }
    }

    /** Teleports produce one large legitimate delta; grace avoids flagging it. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        teleportGrace.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }
}
