package com.fivemcodehub.sentinel.command;

import com.fivemcodehub.sentinel.telemetry.ViolationTracker;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

public final class SentinelCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final JavaPlugin plugin;
    private final ViolationTracker violations;

    public SentinelCommand(JavaPlugin plugin, ViolationTracker violations) {
        this.plugin = plugin;
        this.violations = violations;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage(MM.deserialize(
                    "<gray>/sentinel <stats|inspect|clear|reload> [player]</gray>"));
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "stats" -> {
                double[] tps = plugin.getServer().getTPS();
                sender.sendMessage(MM.deserialize(
                        "<gray>Tracked:</gray> <white><n></white>  "
                                + "<gray>TPS:</gray> <white><tps></white>  "
                                + "<gray>MSPT:</gray> <white><mspt></white>",
                        Placeholder.unparsed("n", String.valueOf(violations.trackedPlayers())),
                        Placeholder.unparsed("tps", String.format("%.2f", tps.length > 0 ? tps[0] : 0)),
                        Placeholder.unparsed("mspt",
                                String.format("%.2f", plugin.getServer().getAverageTickTime()))));
            }
            case "inspect" -> {
                if (args.length < 2) {
                    sender.sendMessage(MM.deserialize("<red>Specify a player.</red>"));
                    return true;
                }
                Player target = plugin.getServer().getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(MM.deserialize("<red>Not online.</red>"));
                    return true;
                }
                var snapshot = violations.snapshot(target.getUniqueId());
                if (snapshot.isEmpty()) {
                    sender.sendMessage(MM.deserialize("<green>No violations recorded.</green>"));
                    return true;
                }
                sender.sendMessage(MM.deserialize("<gray>Violations for <name>:</gray>",
                        Placeholder.unparsed("name", target.getName())));
                snapshot.forEach((check, level) -> sender.sendMessage(MM.deserialize(
                        "  <white><c></white> <gray>-></gray> <yellow><v></yellow>",
                        Placeholder.unparsed("c", check),
                        Placeholder.unparsed("v", String.format("%.1f", level)))));
            }
            case "clear" -> {
                if (args.length < 2) {
                    sender.sendMessage(MM.deserialize("<red>Specify a player.</red>"));
                    return true;
                }
                Player target = plugin.getServer().getPlayerExact(args[1]);
                if (target != null) {
                    violations.clear(target.getUniqueId());
                    sender.sendMessage(MM.deserialize("<green>Cleared.</green>"));
                }
            }
            case "reload" -> {
                plugin.reloadConfig();
                sender.sendMessage(MM.deserialize("<green>Configuration reloaded.</green>"));
            }
            default -> sender.sendMessage(MM.deserialize("<red>Unknown subcommand.</red>"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) return List.of("stats", "inspect", "clear", "reload");
        if (args.length == 2) {
            return plugin.getServer().getOnlinePlayers().stream().map(Player::getName).toList();
        }
        return List.of();
    }
}
