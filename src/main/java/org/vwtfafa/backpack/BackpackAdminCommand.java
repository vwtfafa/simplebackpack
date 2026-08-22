package org.vwtfafa.backpack;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * /backpackadmin [gui] - admin entry point with the overview GUI.
 */
final class BackpackAdminCommand extends SubCommand {

    BackpackAdminCommand(Backpack plugin) {
        super(plugin);
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        Player player = requirePlayer(source);
        if (player == null) {
            return;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("gui")) {
            if (plugin.adminGui() == null) {
                plugin.messages().send(player, "admin-gui-disabled");
                return;
            }
            // Open on the next tick for a clean inventory state
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (plugin.adminGui() != null) {
                    plugin.adminGui().openAdminGUI(player);
                }
            });
            return;
        }
        plugin.messages().send(player, "admin-enabled");
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        if (args.length == 1 && "gui".startsWith(args[0].toLowerCase(Locale.ROOT))) {
            return List.of("gui");
        }
        return List.of();
    }

    @Override
    public String permission() {
        return "simplebackpack.admin";
    }
}
