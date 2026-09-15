package org.vwtfafa.backpack;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import org.bukkit.entity.Player;

/**
 * /backpackshare <player> [durationMinutes] - grants temporary access to
 * the sender's backpack.
 */
final class BackpackShareCommand extends SubCommand {
    private static final long MILLIS_PER_MINUTE = 60L * 1000L;
    private static final long DEFAULT_DURATION_MINUTES = 60L;
    private static final long MAX_DURATION_MINUTES = 7L * 24L * 60L;

    BackpackShareCommand(Backpack plugin) {
        super(plugin);
    }

    @Override
    LiteralCommandNode<CommandSourceStack> node() {
        return Commands.literal("backpackshare")
                .requires(source -> source.getSender().hasPermission(permission()))
                .then(Commands.argument("player", ArgumentTypes.player())
                        .executes(ctx -> {
                            Player target = ctx.getArgument("player", PlayerSelectorArgumentResolver.class)
                                    .resolve(ctx.getSource()).getFirst();
                            execute(ctx.getSource(), target, DEFAULT_DURATION_MINUTES);
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.argument("minutes",
                                        IntegerArgumentType.integer(1, (int) MAX_DURATION_MINUTES))
                                .executes(ctx -> {
                                    Player target = ctx.getArgument("player", PlayerSelectorArgumentResolver.class)
                                            .resolve(ctx.getSource()).getFirst();
                                    int minutes = IntegerArgumentType.getInteger(ctx, "minutes");
                                    execute(ctx.getSource(), target, minutes);
                                    return Command.SINGLE_SUCCESS;
                                })))
                .build();
    }

    private void execute(CommandSourceStack source, Player target, long durationMinutes) {
        Player player = requirePlayer(source);
        if (player == null) {
            return;
        }
        if (!plugin.sharingEnabled()) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        plugin.manager().shareBackpack(player.getUniqueId(), target.getUniqueId(),
                durationMinutes * MILLIS_PER_MINUTE);
        plugin.messages().send(player, "share-success", "{player}", target.getName());
        plugin.messages().send(target, "share-received", "{player}", player.getName());
    }

    @Override
    String permission() {
        return "simplebackpack.use";
    }
}
