package org.vwtfafa.backpack;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AdminGUI implements Listener {
    private static final int PAGE_SIZE = 45;
    private static final int SLOT_PREVIOUS = 45;
    private static final int SLOT_INFO = 49;
    private static final int SLOT_NEXT = 53;

    private final BackpackManager manager;
    private final Messages messages;
    private final NamespacedKey ownerKey;

    public AdminGUI(BackpackManager manager, Messages messages) {
        this.manager = manager;
        this.messages = messages;
        this.ownerKey = new NamespacedKey(manager.getPlugin(), "backpack_owner");
        Bukkit.getPluginManager().registerEvents(this, manager.getPlugin());
    }

    public void openAdminGUI(Player admin) {
        openAdminGUI(admin, 0);
    }

    public void openAdminGUI(Player admin, int page) {
        List<UUID> known = new ArrayList<>(manager.listKnownBackpacks());
        known.sort(UUID::compareTo);
        int maxPage = Math.max(0, (known.size() - 1) / PAGE_SIZE);
        int current = Math.min(Math.max(0, page), maxPage);

        BackpackInventoryHolder holder = BackpackInventoryHolder.adminList(current);
        Inventory gui = Bukkit.createInventory(holder, 54,
                messages.component("gui-admin-list-title"));
        holder.setInventory(gui);
        for (int i = current * PAGE_SIZE; i < Math.min(known.size(), (current + 1) * PAGE_SIZE); i++) {
            gui.addItem(createEntryItem(known.get(i)));
        }
        if (current > 0) {
            gui.setItem(SLOT_PREVIOUS, navItem(messages.component("gui-prev-page")));
        }
        gui.setItem(SLOT_INFO, infoItem(messages.component("gui-admin-info",
                "{total}", String.valueOf(known.size()),
                "{page}", String.valueOf(current + 1),
                "{max}", String.valueOf(maxPage + 1))));
        if (current < maxPage) {
            gui.setItem(SLOT_NEXT, navItem(messages.component("gui-next-page")));
        }
        admin.openInventory(gui);
    }

    private ItemStack createEntryItem(UUID uuid) {
        OfflinePlayer owner = Bukkit.getOfflinePlayer(uuid);
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();
        String name = owner.getName() != null ? owner.getName() : "Unknown player";
        meta.displayName(Component.text(name));
        List<Component> lore = new ArrayList<>();
        lore.add(messages.component("gui-entry-size", "{size}",
                String.valueOf(manager.getBackpackSizeFor(uuid))));
        lore.add(messages.component("gui-entry-instructions"));
        meta.lore(lore);
        // Owner is stored in the PDC instead of parsing it from the lore
        meta.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, uuid.toString());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack navItem(Component label) {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(label);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack infoItem(Component label) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(label);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof BackpackInventoryHolder holder)
            || holder.getType() != BackpackInventoryHolder.Type.ADMIN_LIST) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player admin)) return;
        // Only react to clicks inside the admin list itself
        Inventory top = event.getView().getTopInventory();
        if (!top.equals(event.getClickedInventory())) return;

        int slot = event.getSlot();
        if (slot == SLOT_PREVIOUS && holder.getPage() > 0) {
            openAdminGUI(admin, holder.getPage() - 1);
            return;
        }
        if (slot == SLOT_NEXT) {
            openAdminGUI(admin, holder.getPage() + 1);
            return;
        }
        if (slot == SLOT_INFO || slot == SLOT_PREVIOUS || slot == SLOT_NEXT) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String uuidStr = clicked.getItemMeta().getPersistentDataContainer()
                .get(ownerKey, PersistentDataType.STRING);
        if (uuidStr == null) return;
        try {
            UUID target = UUID.fromString(uuidStr);
            boolean preview = event.isRightClick();
            manager.openForAdmin(target, admin, preview);
        } catch (IllegalArgumentException ignored) {}
    }
}
