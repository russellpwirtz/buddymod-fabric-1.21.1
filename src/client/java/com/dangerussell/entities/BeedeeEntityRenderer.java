package com.dangerussell.entities;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class BeedeeEntityRenderer extends MobEntityRenderer<BeedeeEntity, BeedeeEntityModel<BeedeeEntity>> {
	private static final Identifier PASSIVE_TEXTURE = Identifier.of("entitytesting", "textures/entity/buddy/beedee.png");
	private static final Identifier NECTAR_TEXTURE = Identifier.of("entitytesting", "textures/entity/bee/beedee_loaded.png");

	public BeedeeEntityRenderer(EntityRendererFactory.Context context) {
		super(context, new BeedeeEntityModel<>(context.getPart(EntityTestingClient.MODEL_BEEDEE_LAYER)), 0.4F);
	}

	@Override
	public Identifier getTexture(BeedeeEntity beeEntity) {
		return beeEntity.hasNectar() ? NECTAR_TEXTURE : PASSIVE_TEXTURE;
	}
}
