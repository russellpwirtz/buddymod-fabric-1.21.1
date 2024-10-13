package com.dangerussell;

import com.dangerussell.entities.BeedeeEntity;
import com.dangerussell.entities.BuddyEntity;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class EntityTesting implements ModInitializer {

  public static final EntityType<BuddyEntity> BUDDY = Registry.register(
          Registries.ENTITY_TYPE,
          Identifier.of("entitytesting", "buddy"),
          EntityType.Builder
                  .create(BuddyEntity::new, SpawnGroup.CREATURE)
                  .dimensions(0.75f, 0.75f)
                  .build("buddy")
  );

  public static final EntityType<BeedeeEntity> BEEDEE = Registry.register(
          Registries.ENTITY_TYPE,
          Identifier.of("entitytesting", "beedee"),
          EntityType.Builder
                  .create(BeedeeEntity::new, SpawnGroup.CREATURE)
                  .dimensions(0.7F, 0.6F)
                  .eyeHeight(0.3F)
                  .maxTrackingRange(80)
                  .build("beedee")
  );

  @Override
  public void onInitialize() {
    FabricDefaultAttributeRegistry.register(BUDDY, BuddyEntity.createMobAttributes());
    FabricDefaultAttributeRegistry.register(BEEDEE, BeedeeEntity.createBeedeeAttributes());
  }
}