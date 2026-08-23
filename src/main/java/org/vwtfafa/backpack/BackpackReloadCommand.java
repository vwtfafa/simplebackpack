package org.vwtfafa.backpack;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.CommandSender;

/**
 * /backpackreload - reloads the plugin configuration.
 */
final class BackpackReloadCommand extends SubCommand {

    BackpackReloadCommand(Backpack plugin) {
        super(plugin);
    }

    @Override
    LiteralCommandNode<CommandSourceStack> node() {
        return Commands.literal("backpackreload")
                .requires(source -> source.getSender().hasPermission(permission()))
                .executes(ctx -> {
                    execute(ctx.getSource());
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }

    private void execute(CommandSourceStack source) {
        CommandSender sender = source.getSender();
        if (!plugin.liveConfigReload()) {
            return;
        }
        plugin.reloadConfiguration();
        plugin.messages().send(sender, "reload-success");
    }

    @Override
    String permission() {
        return "simplebackpack.reload";
    }
}
