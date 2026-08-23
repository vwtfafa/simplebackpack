package org.vwtfafa.backpack;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * /team [accept] - shows team members or accepts a pending invitation.
 */
final class TeamCommand extends SubCommand {

    TeamCommand(Backpack plugin) {
        super(plugin);
    }

    @Override
    LiteralCommandNode<CommandSourceStack> node() {
        return Commands.literal("team")
                .requires(source -> source.getSender().hasPermission(permission()))
                .executes(ctx -> {
                    Player player = requirePlayer(ctx.getSource());
                    if (player != null) {
                        showTeamInfo(player);
                    }
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("accept")
                        .executes(ctx -> {
                            Player player = requirePlayer(ctx.getSource());
                            if (player != null) {
                                acceptInvite(player);
                            }
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }

    private void acceptInvite(Player player) {
        UUID targetId = player.getUniqueId();
        TeamInvite invite = plugin.pendingInvites().remove(targetId);
        if (invite == null) {
            showTeamInfo(player);
            return;
        }
        if (invite.isExpired()) {
            plugin.messages().send(player, "team-invite-expired");
            return;
        }
        UUID inviterId = invite.inviter();
        UUID owner = plugin.teamRegistry().findOwner(inviterId);
        if (owner == null) {
            Set<UUID> members = new HashSet<>();
            members.add(inviterId);
            members.add(targetId);
            plugin.teamRegistry().createTeam(inviterId, members);
        } else {
            // The team may have filled up between invite and accept
            if (plugin.teamRegistry().membersOf(owner).size() >= plugin.teamMaxSize()) {
                plugin.messages().send(player, "team-full");
                return;
            }
            plugin.teamRegistry().addMember(owner, targetId);
        }
        plugin.saveTeams();
        OfflinePlayer inviterOffline = Bukkit.getOfflinePlayer(inviterId);
        String inviterName = inviterOffline.getName() != null ? inviterOffline.getName() : inviterId.toString();
        plugin.messages().send(player, "team-joined", "{player}", inviterName);
        Player inviter = Bukkit.getPlayer(inviterId);
        if (inviter != null) {
            plugin.messages().send(inviter, "team-joined", "{player}", player.getName());
        }
    }

    private void showTeamInfo(Player player) {
        UUID uuid = player.getUniqueId();
        UUID teamOwner = plugin.teamRegistry().findOwner(uuid);
        Set<UUID> teamMembers = teamOwner == null ? null : plugin.teamRegistry().membersOf(teamOwner);
        if (teamMembers == null || teamMembers.isEmpty()) {
            TeamInvite invite = plugin.pendingInvites().get(uuid);
            if (invite != null && !invite.isExpired()) {
                OfflinePlayer inviterOffline = Bukkit.getOfflinePlayer(invite.inviter());
                String inviterName = inviterOffline.getName() != null
                        ? inviterOffline.getName()
                        : invite.inviter().toString();
                plugin.messages().send(player, "team-pending", "{player}", inviterName);
            } else {
                plugin.messages().send(player, "not-in-team");
            }
            return;
        }
        StringBuilder names = new StringBuilder();
        for (UUID member : teamMembers) {
            // Resolve offline members by name as well; fall back to the UUID
            String name = Bukkit.getOfflinePlayer(member).getName();
            if (name == null) {
                name = member.toString();
            }
            if (member.equals(teamOwner)) {
                name += " (L)";
            }
            names.append(name).append(", ");
        }
        if (!names.isEmpty()) {
            names.setLength(names.length() - 2); // remove trailing separator
        }
        plugin.messages().send(player, "team-members", "{members}", names.toString());
    }

    @Override
    String permission() {
        return "simplebackpack.team";
    }
}
