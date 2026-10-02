package com.fivemcodehub.sentinel.telemetry;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Violation ledger with time decay.
 *
 * <p>Decay is the point: a single anomalous reading is noise, while a sustained
 * stream outruns decay and accumulates. That distinction is what keeps a
 * teleport or a lag spike from being treated as cheating.
 */
public final class ViolationTracker {

    public record Record(String check, double level, long lastFlagged) {}

    private final JavaPlugin plugin;
    private final Map<UUID, Map<String, Double>> levels = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastFlag = new ConcurrentHashMap<>();
    private BukkitTask decayTask;

    public ViolationTracker(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        int perMinute = plugin.getConfig().getInt("violations.decay-per-minute", 2);
        // Runs async: this touches only concurrent collections, never world state.
        this.decayTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(
                plugin, () -> decay(perMinute / 60.0), 20L, 20L);
    }

    private void decay(double amount) {
        levels.forEach((uuid, checks) -> checks.replaceAll((check, level) ->
                Math.max(0.0, level - amount)));
    }

    /** Raises a violation level and returns the new value. */
    public double flag(UUID uuid, String check, double weight) {
        lastFlag.put(uuid, System.currentTimeMillis());
        return levels.computeIfAbsent(uuid, key -> new ConcurrentHashMap<>())
                .merge(check, weight, Double::sum);
    }

    public double levelOf(UUID uuid, String check) {
        return levels.getOrDefault(uuid, Map.of()).getOrDefault(check, 0.0);
    }

    public Map<String, Double> snapshot(UUID uuid) {
        return Map.copyOf(levels.getOrDefault(uuid, Map.of()));
    }

    public void clear(UUID uuid) {
        levels.remove(uuid);
        lastFlag.remove(uuid);
    }

    public int trackedPlayers() {
        return levels.size();
    }

    public long totalFlags() {
        return levels.values().stream()
                .flatMap(m -> m.values().stream())
                .filter(v -> v > 0)
                .count();
    }

    public void shutdown() {
        if (decayTask != null) decayTask.cancel();
        levels.clear();
        lastFlag.clear();
    }
}
