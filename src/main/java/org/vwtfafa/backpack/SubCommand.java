package org.vwtfafa.backpack;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Base class for SimpleBackpack Brigadier commands. Root visibility is
 * controlled by {@link #permission()} and execution helpers provide
 * localized feedback.
 */
abstract class SubCommand implements io.papermc.paper.command.brigadier.BasicCommand {
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
        plugin.messages().send(sender, "no-permission");
        return null;
    }
}
