package org.vwtfafa.backpack;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.entity.Player;

/**
 * /backpackconfig - opens the in-game configuration GUI.
 */
final class BackpackConfigCommand extends SubCommand {

    BackpackConfigCommand(Backpack plugin) {
        super(plugin);
    }

    @Override
    LiteralCommandNode<CommandSourceStack> node() {
        return Commands.literal("backpackconfig")
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
        if (!plugin.guiConfigurable()) {
            return;
        }
        plugin.manager().openConfigGUI(player);
    }

    @Override
    String permission() {
        return "simplebackpack.config";
    }
}
