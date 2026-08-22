package org.vwtfafa.backpack;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;

/**
 * /backpackreload - reloads the plugin configuration.
 */
final class BackpackReloadCommand extends SubCommand {

    BackpackReloadCommand(Backpack plugin) {
        super(plugin);
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        if (!plugin.liveConfigReload()) {
            return;
        }
        plugin.reloadConfiguration();
        plugin.messages().send(sender, "reload-success");
    }

    @Override
    public String permission() {
        return "simplebackpack.reload";
    }
}
