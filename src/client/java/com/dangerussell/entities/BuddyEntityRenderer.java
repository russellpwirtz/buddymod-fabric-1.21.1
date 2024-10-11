package com.dangerussell.entities;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.util.Identifier;

public class BuddyEntityRenderer extends MobEntityRenderer<BuddyEntity, BuddyEntityModel> {

  public BuddyEntityRenderer(EntityRendererFactory.Context context) {
    super(context, new BuddyEntityModel(context.getPart(EntityTestingClient.MODEL_BUDDY_LAYER)), 0.5f);
  }

  @Override
  public Identifier getTexture(BuddyEntity entity) {
    return Identifier.of("entitytesting", "textures/entity/buddy/buddy.png");
  }
}
