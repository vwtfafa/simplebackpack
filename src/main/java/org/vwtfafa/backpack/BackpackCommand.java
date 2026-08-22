package org.vwtfafa.backpack;

import io.papermc.paper.command.brigadier.CommandSourceStack;
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
    public void execute(CommandSourceStack source, String[] args) {
        Player player = requirePlayer(source);
        if (player == null) {
            return;
        }
        if (!plugin.backpacksEnabled()) {
            plugin.messages().send(player, "backpacks-disabled");
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
    public String permission() {
        return "simplebackpack.use";
    }
}
