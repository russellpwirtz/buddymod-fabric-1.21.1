package com.dangerussell.entities;

import com.dangerussell.EntityTesting;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendereregistry.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class EntityTestingClient implements ClientModInitializer {
  public static final EntityModelLayer MODEL_BUDDY_LAYER = new EntityModelLayer(Identifier.of("entitytesting", "buddy"), "main");
  @Override
  public void onInitializeClient() {
    /*
     * Registers our Buddy Entity's renderer, which provides a model and texture for the entity.
     *
     * Entity Renderers can also manipulate the model before it renders based on entity context (EndermanEntityRenderer#render).
     */
    EntityRendererRegistry.INSTANCE.register(EntityTesting.BUDDY, BuddyEntityRenderer::new);

    EntityModelLayerRegistry.registerModelLayer(MODEL_BUDDY_LAYER, BuddyEntityModel::getTexturedModelData);
  }
}
