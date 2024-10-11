package com.dangerussell.entities;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.world.World;

public class BuddyEntity extends PathAwareEntity {

    public BuddyEntity(EntityType<? extends PathAwareEntity> entityType, World world) {
        super(entityType, world);
    }
}