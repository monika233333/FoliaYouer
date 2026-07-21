package net.minecraft.world.level.entity;

import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.bukkit.event.entity.EntityRemoveEvent;

public interface EntityAccess {
    int getId();

    UUID getUUID();

    BlockPos blockPosition();

    AABB getBoundingBox();

    void setLevelCallback(EntityInLevelCallback p_156797_);

    Stream<? extends EntityAccess> getSelfAndPassengers();

    Stream<? extends EntityAccess> getPassengersAndSelf();

    void setRemoved(Entity.RemovalReason p_156798_);

    // CraftBukkit start - add Bukkit remove cause
    default void setRemovedCB(Entity.RemovalReason entity_removalreason, EntityRemoveEvent.Cause cause) {
        this.setRemoved(entity_removalreason);
    }
    // CraftBukkit end

    boolean shouldBeSaved();

    boolean isAlwaysTicking();
}
