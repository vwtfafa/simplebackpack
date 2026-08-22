package org.vwtfafa.backpack;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.entity.Player;

/**
 * /leave - leaves the sender's current team.
 */
final class LeaveCommand extends SubCommand {

    LeaveCommand(Backpack plugin) {
        super(plugin);
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
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
    public String permission() {
        return "simplebackpack.team.leave";
    }
}
