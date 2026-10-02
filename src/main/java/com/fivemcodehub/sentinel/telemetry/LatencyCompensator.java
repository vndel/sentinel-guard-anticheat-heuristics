package com.fivemcodehub.sentinel.telemetry;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Converts raw check margins into latency- and TPS-aware thresholds.
 *
 * <p>Without this, every heuristic becomes a ping test: a 250ms player fails
 * reach checks that a 20ms player passes while doing exactly the same thing.
 * Likewise, a server at 14 TPS delivers movement in bursts that look identical
 * to speed hacking.
 */
public final class LatencyCompensator {

    private final JavaPlugin plugin;

    public LatencyCompensator(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Observed round-trip latency in milliseconds. */
    public int pingOf(Player player) {
        try {
            return Math.max(0, player.getPing());
        } catch (Throwable ignored) {
            // Older API surface without getPing().
            return 0;
        }
    }

    /**
     * Extra tolerance, as a multiplier, granted for a player's latency.
     * Capped so a deliberately laggy connection cannot buy unlimited slack.
     */
    public double latencyTolerance(Player player) {
        double perHundredMs = plugin.getConfig()
                .getDouble("compensation.tolerance-per-100ms", 0.08);
        double cap = plugin.getConfig().getDouble("compensation.max-tolerance", 0.60);
        return Math.min(cap, (pingOf(player) / 100.0) * perHundredMs);
    }

    /** Additional tolerance while the server itself is behind schedule. */
    public double tickTolerance() {
        double mspt = plugin.getServer().getAverageTickTime();
        double budget = plugin.getConfig().getDouble("compensation.mspt-budget", 45.0);
        if (mspt <= budget) return 0.0;

        double over = (mspt - budget) / budget;
        return Math.min(plugin.getConfig().getDouble("compensation.max-tick-tolerance", 0.75), over);
    }

    /** Final multiplier applied to a check's base threshold. */
    public double thresholdMultiplier(Player player) {
        return 1.0 + latencyTolerance(player) + tickTolerance();
    }

    /** True when conditions are too poor for any verdict to be trustworthy. */
    public boolean shouldSuspendChecks() {
        double[] tps = plugin.getServer().getTPS();
        double floor = plugin.getConfig().getDouble("compensation.suspend-below-tps", 16.0);
        return tps.length > 0 && tps[0] < floor;
    }
}
