package org.vwtfafa.backpack;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AdminGUI implements Listener {
    private final BackpackManager manager;

    public AdminGUI(BackpackManager manager) {
        this.manager = manager;
        Bukkit.getPluginManager().registerEvents(this, manager.getPlugin());
    }

    public void openAdminGUI(Player admin) {
        Inventory gui = Bukkit.createInventory(admin, 54, "SimpleBackpack Admin");
        // populate with known backpacks
        for (UUID uuid : manager.listKnownBackpacks()) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            ItemStack item = new ItemStack(Material.CHEST);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(op.getName() == null ? uuid.toString() : op.getName());
            List<String> lore = new ArrayList<>();
            lore.add("UUID: " + uuid.toString());
            lore.add("Size: " + manager.getBackpackSizeFor(uuid));
            lore.add("Left-click: Edit, Right-click: Preview");
            meta.setLore(lore);
            item.setItemMeta(meta);
            gui.addItem(item);
        }
        admin.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals("SimpleBackpack Admin")) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player admin = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null || meta.getLore() == null || meta.getLore().isEmpty()) return;
        String uuidLine = meta.getLore().get(0);
        if (!uuidLine.startsWith("UUID: ")) return;
        String uuidStr = uuidLine.substring(6).trim();
        try {
            UUID target = UUID.fromString(uuidStr);
            boolean preview = event.isRightClick();
            manager.openForAdmin(target, admin, preview);
        } catch (IllegalArgumentException ignored) {}
    }
}
