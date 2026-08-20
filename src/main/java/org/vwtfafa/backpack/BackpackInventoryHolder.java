package org.vwtfafa.backpack;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

final class BackpackInventoryHolder implements InventoryHolder {
    enum Type { BACKPACK, ADMIN, CONFIG }

    private final Type type;
    private final UUID owner;
    private final boolean preview;

    private BackpackInventoryHolder(Type type, UUID owner, boolean preview) {
        this.type = type;
        this.owner = owner;
        this.preview = preview;
    }

    static BackpackInventoryHolder backpack(UUID owner) {
        return new BackpackInventoryHolder(Type.BACKPACK, owner, false);
    }

    static BackpackInventoryHolder admin(UUID owner, boolean preview) {
        return new BackpackInventoryHolder(Type.ADMIN, owner, preview);
    }

    static BackpackInventoryHolder config() {
        return new BackpackInventoryHolder(Type.CONFIG, null, false);
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

    @Override
    public Inventory getInventory() {
        return null;
    }
}
