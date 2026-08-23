package org.vwtfafa.backpack;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.entity.Player;

/**
 * /backpackadmin [gui] - admin entry point with the overview GUI.
 */
final class BackpackAdminCommand extends SubCommand {

    BackpackAdminCommand(Backpack plugin) {
        super(plugin);
    }

    @Override
    LiteralCommandNode<CommandSourceStack> node() {
        return Commands.literal("backpackadmin")
                .requires(source -> source.getSender().hasPermission(permission()))
                .executes(ctx -> {
                    Player player = requirePlayer(ctx.getSource());
                    if (player != null) {
                        plugin.messages().send(player, "admin-enabled");
                    }
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("gui")
                        .executes(ctx -> {
                            Player player = requirePlayer(ctx.getSource());
                            if (player != null) {
                                executeGui(player);
                            }
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }

    private void executeGui(Player player) {
        if (plugin.adminGui() == null) {
            plugin.messages().send(player, "admin-gui-disabled");
            return;
        }
        // Open on the next tick of the player's region for a clean inventory state
        player.getScheduler().run(plugin, task -> {
            if (plugin.adminGui() != null) {
                plugin.adminGui().openAdminGUI(player);
            }
        }, null);
    }

    @Override
    String permission() {
        return "simplebackpack.admin";
    }
}
