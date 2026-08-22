package org.vwtfafa.backpack;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

final class BackpackInventoryHolder implements InventoryHolder {
    enum Type { BACKPACK, ADMIN, ADMIN_LIST, CONFIG }

    private final Type type;
    private final UUID owner;
    private final boolean preview;
    private final int page;

    private BackpackInventoryHolder(Type type, UUID owner, boolean preview, int page) {
        this.type = type;
        this.owner = owner;
        this.preview = preview;
        this.page = page;
    }

    static BackpackInventoryHolder backpack(UUID owner) {
        return new BackpackInventoryHolder(Type.BACKPACK, owner, false, 0);
    }

    static BackpackInventoryHolder admin(UUID owner, boolean preview) {
        return new BackpackInventoryHolder(Type.ADMIN, owner, preview, 0);
    }

    static BackpackInventoryHolder adminList(int page) {
        return new BackpackInventoryHolder(Type.ADMIN_LIST, null, false, page);
    }

    static BackpackInventoryHolder config() {
        return new BackpackInventoryHolder(Type.CONFIG, null, false, 0);
    }

    Type getType() {
        return type;
    }

    UUID getOwner() {
        return owner;
    }

    boolean isPreview() {
        return preview;
    }

    int getPage() {
        return page;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
