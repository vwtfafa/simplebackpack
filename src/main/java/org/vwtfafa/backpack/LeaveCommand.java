package org.vwtfafa.backpack;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.entity.Player;

/**
 * /leave - leaves the sender's current team.
 */
final class LeaveCommand extends SubCommand {

    LeaveCommand(Backpack plugin) {
        super(plugin);
    }

    @Override
    LiteralCommandNode<CommandSourceStack> node() {
        return Commands.literal("leave")
                .requires(source -> source.getSender().hasPermission(permission()))
                .executes(ctx -> {
                    execute(ctx.getSource());
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }

    private void execute(CommandSourceStack source) {
        Player player = requirePlayer(source);
        if (player == null) {
            return;
        }
        // removeMember handles empty teams and deterministic owner succession
        if (plugin.teamRegistry().removeMember(player.getUniqueId())) {
            plugin.saveTeams();
            plugin.messages().send(player, "team-leave");
        } else {
            plugin.messages().send(player, "not-in-team");
        }
    }

    @Override
    String permission() {
        return "simplebackpack.team.leave";
    }
}
