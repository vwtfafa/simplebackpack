package org.vwtfafa.backpack;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.entity.Player;

/**
 * /backpackconfig - opens the in-game configuration GUI.
 */
final class BackpackConfigCommand extends SubCommand {

    BackpackConfigCommand(Backpack plugin) {
        super(plugin);
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
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
    public String permission() {
        return "simplebackpack.config";
    }
}
