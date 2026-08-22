package org.vwtfafa.backpack;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * /backpackshare <player> [durationMinutes] - grants temporary access to
 * the sender's backpack.
 */
final class BackpackShareCommand extends SubCommand {
    private static final long MILLIS_PER_MINUTE = 60L * 1000L;
    private static final long DEFAULT_DURATION_MINUTES = 60L;
    private static final long MAX_DURATION_MINUTES = 7L * 24L * 60L;

    BackpackShareCommand(Backpack plugin) {
        super(plugin);
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        Player player = requirePlayer(source);
        if (player == null) {
            return;
        }
        if (!plugin.sharingEnabled()) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        if (args.length < 1) {
            plugin.messages().send(player, "share-usage");
            return;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            plugin.messages().send(player, "share-player-offline");
            return;
        }
        long durationMinutes = DEFAULT_DURATION_MINUTES;
        if (args.length >= 2) {
            try {
                durationMinutes = Long.parseLong(args[1]);
            } catch (NumberFormatException ignored) {
                plugin.messages().send(player, "share-usage");
                return;
            }
            if (durationMinutes <= 0 || durationMinutes > MAX_DURATION_MINUTES) {
                plugin.messages().send(player, "share-usage");
                return;
            }
        }
        plugin.manager().shareBackpack(player.getUniqueId(), target.getUniqueId(),
                durationMinutes * MILLIS_PER_MINUTE);
        plugin.messages().send(player, "share-success", "{player}", target.getName());
        plugin.messages().send(target, "share-received", "{player}", player.getName());
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }

    @Override
    public String permission() {
        return "simplebackpack.use";
    }
}
