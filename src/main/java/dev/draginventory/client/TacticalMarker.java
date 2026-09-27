package dev.draginventory.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Refreshed markers replace their map entry, retaining a single icon for each target. */
public record TacticalMarker(Type type, Vec3 position, Entity target, long createdAt) {
    public enum Type { LOCATION, ENEMY, ITEM }

    public Vec3 position(float partialTick) {
        return type == Type.ITEM && target != null
                ? target.getPosition(partialTick).add(0, target.getBbHeight() * 0.5, 0) : position;
    }

    public ItemStack item() { return target instanceof ItemEntity item ? item.getItem() : ItemStack.EMPTY; }

    public boolean valid(ClientLevel level, long now) {
        return !TacticalMarkerLogic.expired(now, createdAt) && (target == null
                || target.level() == level && target.isAlive() && !target.isRemoved()
                && level.getEntity(target.getId()) == target
                && (!(target instanceof ItemEntity item) || !item.getItem().isEmpty()));
    }
}
