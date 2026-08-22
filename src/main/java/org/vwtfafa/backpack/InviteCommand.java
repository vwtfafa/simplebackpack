package org.vwtfafa.backpack;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * /invite <player> - invites a player to the sender's team.
 */
final class InviteCommand extends SubCommand {

    InviteCommand(Backpack plugin) {
        super(plugin);
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        Player player = requirePlayer(source);
        if (player == null) {
            return;
        }
        if (args.length < 1) {
            plugin.messages().send(player, "invite-usage");
            return;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            plugin.messages().send(player, "share-player-offline");
            return;
        }
        UUID owner = plugin.teamRegistry().findOwner(player.getUniqueId());
        if (owner != null && plugin.teamRegistry().membersOf(owner).size() >= plugin.teamMaxSize()) {
            plugin.messages().send(player, "team-full");
            return;
        }
        if (plugin.teamRegistry().findOwner(target.getUniqueId()) != null) {
            plugin.messages().send(player, "target-in-team");
            return;
        }
        plugin.pendingInvites().values().removeIf(TeamInvite::isExpired);
        plugin.pendingInvites().put(target.getUniqueId(),
                new TeamInvite(player.getUniqueId(), System.currentTimeMillis() + Backpack.INVITE_EXPIRY_MILLIS));
        String targetName = target.getName() != null ? target.getName() : target.getUniqueId().toString();
        String playerName = player.getName() != null ? player.getName() : player.getUniqueId().toString();
        plugin.messages().send(player, "team-invite", "{player}", targetName);
        plugin.messages().send(target, "team-invite-recv", "{player}", playerName);
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
        return "simplebackpack.team.invite";
    }
}
