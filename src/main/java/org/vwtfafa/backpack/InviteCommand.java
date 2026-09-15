package org.vwtfafa.backpack;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * /invite <player> - invites a player to the sender's team.
 */
final class InviteCommand extends SubCommand {

    InviteCommand(Backpack plugin) {
        super(plugin);
    }

    @Override
    LiteralCommandNode<CommandSourceStack> node() {
        return Commands.literal("invite")
                .requires(source -> source.getSender().hasPermission(permission()))
                .then(Commands.argument("player", ArgumentTypes.player())
                        .executes(ctx -> {
                            Player target = ctx.getArgument("player", PlayerSelectorArgumentResolver.class)
                                    .resolve(ctx.getSource()).getFirst();
                            execute(ctx.getSource(), target);
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }

    private void execute(CommandSourceStack source, Player target) {
        Player player = requirePlayer(source);
        if (player == null) {
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            plugin.messages().send(player, "invite-self");
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
        plugin.messages().send(player, "team-invite", "{player}", target.getName());
        plugin.messages().send(target, "team-invite-recv", "{player}", player.getName());
    }

    @Override
    String permission() {
        return "simplebackpack.team.invite";
    }
}
