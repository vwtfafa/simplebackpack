package org.vwtfafa.backpack;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

/**
 * /backpack [alias bp] - opens the player's personal backpack.
 */
final class BackpackCommand extends SubCommand {

    BackpackCommand(Backpack plugin) {
        super(plugin);
    }

    @Override
    LiteralCommandNode<CommandSourceStack> node() {
        return Commands.literal("backpack")
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
        if (!plugin.backpacksEnabled()) {
            plugin.messages().send(player, "backpacks-disabled");
            return;
        }
        if (plugin.disabledWorlds().contains(player.getWorld().getName())) {
            plugin.messages().send(player, "not-allowed-world");
            return;
        }
        if (!plugin.allowInCreative() && player.getGameMode() == GameMode.CREATIVE) {
            plugin.messages().send(player, "creative-not-allowed");
            return;
        }
        plugin.messages().send(player, "open-success");
        plugin.manager().openBackpack(player);
    }

    @Override
    String permission() {
        return "simplebackpack.use";
    }
}
