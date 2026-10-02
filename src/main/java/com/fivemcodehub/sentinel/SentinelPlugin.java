package com.fivemcodehub.sentinel;

import com.fivemcodehub.sentinel.check.CombatReachCheck;
import com.fivemcodehub.sentinel.check.MovementSpeedCheck;
import com.fivemcodehub.sentinel.command.SentinelCommand;
import com.fivemcodehub.sentinel.telemetry.LatencyCompensator;
import com.fivemcodehub.sentinel.telemetry.ViolationTracker;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Heuristic anti-cheat telemetry layer.
 *
 * <p>Deliberate scope limit: this collects evidence and raises violation
 * levels, it does not kick. Heuristics produce false positives under packet
 * loss and TPS dips, so the punishment decision is left to an operator or an
 * external handler consuming the violation feed.
 */
public final class SentinelPlugin extends JavaPlugin {

    private ViolationTracker violations;
    private LatencyCompensator latency;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.latency = new LatencyCompensator(this);
        this.violations = new ViolationTracker(this);
        this.violations.start();

        var pm = getServer().getPluginManager();
        if (getConfig().getBoolean("checks.movement-speed.enabled", true)) {
            pm.registerEvents(new MovementSpeedCheck(this, violations, latency), this);
        }
        if (getConfig().getBoolean("checks.combat-reach.enabled", true)) {
            pm.registerEvents(new CombatReachCheck(this, violations, latency), this);
        }

        var cmd = getCommand("sentinel");
        if (cmd != null) {
            SentinelCommand executor = new SentinelCommand(this, violations);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }

        getLogger().info("SentinelGuard active (decay="
                + getConfig().getInt("violations.decay-per-minute", 2) + "/min)");
    }

    @Override
    public void onDisable() {
        if (violations != null) violations.shutdown();
    }

    public LatencyCompensator latency() {
        return latency;
    }
}
