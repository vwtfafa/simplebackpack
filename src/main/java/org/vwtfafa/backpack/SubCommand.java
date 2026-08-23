package org.vwtfafa.backpack;

import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Base class for SimpleBackpack Brigadier commands. Each subclass builds a
 * full command tree whose visibility and execution are gated by
 * {@link #permission()}.
 */
abstract class SubCommand {
    protected final Backpack plugin;

    protected SubCommand(Backpack plugin) {
        this.plugin = plugin;
    }

    /**
     * Returns the executing player, or null after reporting that the sender
     * cannot use this command (console or non-player executor).
     */
    protected final Player requirePlayer(CommandSourceStack source) {
        CommandSender sender = source.getSender();
        if (sender instanceof Player player) {
            return player;
        }
        plugin.messages().send(sender, "players-only");
        return null;
    }

    /**
     * Permission that controls both tree visibility and execution.
     */
    abstract String permission();

    /**
     * Builds the complete Brigadier command tree for registration.
     */
    abstract LiteralCommandNode<CommandSourceStack> node();
}
