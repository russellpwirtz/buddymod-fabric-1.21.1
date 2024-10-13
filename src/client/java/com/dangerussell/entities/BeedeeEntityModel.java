package com.dangerussell.entities;

import com.google.common.collect.ImmutableList;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.*;
import net.minecraft.client.render.entity.model.AnimalModel;
import net.minecraft.client.render.entity.model.EntityModelPartNames;
import net.minecraft.util.math.MathHelper;

@Environment(EnvType.CLIENT)
public class BeedeeEntityModel<T extends BeedeeEntity> extends AnimalModel<T> {

	// The bone is an invisible model part which is used to globally control the model.
	private final ModelPart bone;
	private final ModelPart rightWing;
	private final ModelPart leftWing;
	private final ModelPart frontLegs;
	private final ModelPart middleLegs;
	private final ModelPart backLegs;
	private final ModelPart stinger;
	private final ModelPart leftAntenna;
	private final ModelPart rightAntenna;
	private float bodyPitch;

	public BeedeeEntityModel(ModelPart root) {
		super(false, 24.0F, 0.0F);
		this.bone = root.getChild(EntityModelPartNames.BONE);
		ModelPart modelPart = this.bone.getChild(EntityModelPartNames.BODY);
		this.stinger = modelPart.getChild("stinger");
		this.leftAntenna = modelPart.getChild("left_antenna");
		this.rightAntenna = modelPart.getChild("right_antenna");
		this.rightWing = this.bone.getChild(EntityModelPartNames.RIGHT_WING);
		this.leftWing = this.bone.getChild(EntityModelPartNames.LEFT_WING);
		this.frontLegs = this.bone.getChild("front_legs");
		this.middleLegs = this.bone.getChild("middle_legs");
		this.backLegs = this.bone.getChild("back_legs");
	}

	public static TexturedModelData getTexturedModelData() {
		ModelData modelData = new ModelData();
		ModelPartData modelPartData = modelData.getRoot();
		ModelPartData modelPartData2 = modelPartData.addChild(EntityModelPartNames.BONE, ModelPartBuilder.create(), ModelTransform.pivot(0.0F, 19.0F, 0.0F));
		ModelPartData modelPartData3 = modelPartData2.addChild(
			EntityModelPartNames.BODY, ModelPartBuilder.create().uv(0, 0).cuboid(-3.5F, -4.0F, -5.0F, 7.0F, 7.0F, 10.0F), ModelTransform.NONE
		);
		modelPartData3.addChild("stinger", ModelPartBuilder.create().uv(26, 7).cuboid(0.0F, -1.0F, 5.0F, 0.0F, 1.0F, 2.0F), ModelTransform.NONE);
		modelPartData3.addChild(
			"left_antenna", ModelPartBuilder.create().uv(2, 0).cuboid(1.5F, -2.0F, -3.0F, 1.0F, 2.0F, 3.0F), ModelTransform.pivot(0.0F, -2.0F, -5.0F)
		);
		modelPartData3.addChild(
			"right_antenna", ModelPartBuilder.create().uv(2, 3).cuboid(-2.5F, -2.0F, -3.0F, 1.0F, 2.0F, 3.0F), ModelTransform.pivot(0.0F, -2.0F, -5.0F)
		);
		Dilation dilation = new Dilation(0.001F);
		modelPartData2.addChild(
			EntityModelPartNames.RIGHT_WING,
			ModelPartBuilder.create().uv(0, 18).cuboid(-9.0F, 0.0F, 0.0F, 9.0F, 0.0F, 6.0F, dilation),
			ModelTransform.of(-1.5F, -4.0F, -3.0F, 0.0F, -0.2618F, 0.0F)
		);
		modelPartData2.addChild(
			EntityModelPartNames.LEFT_WING,
			ModelPartBuilder.create().uv(0, 18).mirrored().cuboid(0.0F, 0.0F, 0.0F, 9.0F, 0.0F, 6.0F, dilation),
			ModelTransform.of(1.5F, -4.0F, -3.0F, 0.0F, 0.2618F, 0.0F)
		);
		modelPartData2.addChild(
			"front_legs", ModelPartBuilder.create().cuboid("front_legs", -5.0F, 0.0F, 0.0F, 7, 2, 0, 26, 1), ModelTransform.pivot(1.5F, 3.0F, -2.0F)
		);
		modelPartData2.addChild(
			"middle_legs", ModelPartBuilder.create().cuboid("middle_legs", -5.0F, 0.0F, 0.0F, 7, 2, 0, 26, 3), ModelTransform.pivot(1.5F, 3.0F, 0.0F)
		);
		modelPartData2.addChild("back_legs", ModelPartBuilder.create().cuboid("back_legs", -5.0F, 0.0F, 0.0F, 7, 2, 0, 26, 5), ModelTransform.pivot(1.5F, 3.0F, 2.0F));
		return TexturedModelData.of(modelData, 64, 64);
	}

	public void animateModel(T beeEntity, float f, float g, float h) {
		super.animateModel(beeEntity, f, g, h);
		this.bodyPitch = beeEntity.getBodyPitch(h);
		this.stinger.visible = !beeEntity.hasStung();
	}

	public void setAngles(T beeEntity, float f, float g, float h, float i, float j) {
		this.rightWing.pitch = 0.0F;
		this.leftAntenna.pitch = 0.0F;
		this.rightAntenna.pitch = 0.0F;
		this.bone.pitch = 0.0F;
		boolean bl = beeEntity.isOnGround() && beeEntity.getVelocity().lengthSquared() < 1.0E-7;
		if (bl) {
			this.rightWing.yaw = -0.2618F;
			this.rightWing.roll = 0.0F;
			this.leftWing.pitch = 0.0F;
			this.leftWing.yaw = 0.2618F;
			this.leftWing.roll = 0.0F;
			this.frontLegs.pitch = 0.0F;
			this.middleLegs.pitch = 0.0F;
			this.backLegs.pitch = 0.0F;
		} else {
			float k = h * 120.32113F * (float) (Math.PI / 180.0);
			this.rightWing.yaw = 0.0F;
			this.rightWing.roll = MathHelper.cos(k) * (float) Math.PI * 0.15F;
			this.leftWing.pitch = this.rightWing.pitch;
			this.leftWing.yaw = this.rightWing.yaw;
			this.leftWing.roll = -this.rightWing.roll;
			this.frontLegs.pitch = (float) (Math.PI / 4);
			this.middleLegs.pitch = (float) (Math.PI / 4);
			this.backLegs.pitch = (float) (Math.PI / 4);
		}

		this.bone.pitch = 0.0F;
		this.bone.yaw = 0.0F;
		this.bone.roll = 0.0F;
		if (!bl) {
			float k = MathHelper.cos(h * 0.18F);
			this.bone.pitch = 0.1F + k * (float) Math.PI * 0.025F;
			this.leftAntenna.pitch = k * (float) Math.PI * 0.03F;
			this.rightAntenna.pitch = k * (float) Math.PI * 0.03F;
			this.frontLegs.pitch = -k * (float) Math.PI * 0.1F + (float) (Math.PI / 8);
			this.backLegs.pitch = -k * (float) Math.PI * 0.05F + (float) (Math.PI / 4);
			this.bone.pivotY = 19.0F - MathHelper.cos(h * 0.18F) * 0.9F;
		}

		if (this.bodyPitch > 0.0F) {
			this.bone.pitch = ModelUtil.interpolateAngle(this.bone.pitch, 3.0915928F, this.bodyPitch);
		}
	}

	@Override
	protected Iterable<ModelPart> getHeadParts() {
		return ImmutableList.<ModelPart>of();
	}

	@Override
	protected Iterable<ModelPart> getBodyParts() {
		return ImmutableList.<ModelPart>of(this.bone);
	}
}
