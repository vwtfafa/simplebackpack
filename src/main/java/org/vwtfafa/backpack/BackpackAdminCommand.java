package org.vwtfafa.backpack;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import org.bukkit.entity.Player;

/**
 * /backpackadmin [gui|clear <player>|enable|disable] - admin entry point
 * with the overview GUI plus live maintenance commands.
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
                    plugin.messages().send(ctx.getSource().getSender(), "admin-hint");
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
                .then(Commands.literal("clear")
                        .then(Commands.argument("player", ArgumentTypes.player())
                                .executes(ctx -> {
                                    Player admin = requirePlayer(ctx.getSource());
                                    if (admin == null) {
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    Player target = ctx.getArgument("player", PlayerSelectorArgumentResolver.class)
                                            .resolve(ctx.getSource()).getFirst();
                                    plugin.manager().clearForAdmin(
                                            plugin.manager().resolveEffectiveOwner(target.getUniqueId()), admin);
                                    plugin.messages().send(admin, "admin-cleared", "{player}", target.getName());
                                    return Command.SINGLE_SUCCESS;
                                })))
                .then(Commands.literal("enable")
                        .executes(ctx -> {
                            plugin.setBackpacksEnabled(true);
                            plugin.messages().send(ctx.getSource().getSender(), "admin-enabled");
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("disable")
                        .executes(ctx -> {
                            plugin.setBackpacksEnabled(false);
                            plugin.messages().send(ctx.getSource().getSender(), "admin-disabled");
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
