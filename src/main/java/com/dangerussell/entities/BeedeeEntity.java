package com.dangerussell.entities;

import com.mojang.logging.LogUtils;
import net.minecraft.block.*;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.AboveGroundTargeting;
import net.minecraft.entity.ai.NoPenaltySolidTargeting;
import net.minecraft.entity.ai.NoWaterTargeting;
import net.minecraft.entity.ai.control.FlightMoveControl;
import net.minecraft.entity.ai.control.LookControl;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.ai.pathing.BirdNavigation;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.*;
import net.minecraft.world.event.GameEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.*;

public class BeedeeEntity extends PathAwareEntity implements Flutterer, InventoryOwner {
	public static final int field_28638 = MathHelper.ceil(1.4959966F);
	private static final TrackedData<Byte> BEE_FLAGS = DataTracker.registerData(BeedeeEntity.class, TrackedDataHandlerRegistry.BYTE);
	private static final int NEAR_TARGET_FLAG = 2;
	private static final int HAS_STUNG_FLAG = 4;
	private static final int HAS_NECTAR_FLAG = 8;
	public static final int PLAYER_SEARCH_DISTANCE = 55;
	private static final Logger LOGGER = LogUtils.getLogger();
	@Nullable
	private float currentPitch;
	private float lastPitch;
	private int ticksSinceSting;
	int ticksSincePollination;
	private int cropsGrownSincePollination;
	@Nullable
	PlayerEntity player;
	@Nullable
	BlockPos playerPos;
	private int ticksInsideWater;
	private final SimpleInventory inventory = new SimpleInventory(8);

	public BeedeeEntity(EntityType<? extends PathAwareEntity> entityType, World world) {
		super(entityType, world);
		this.moveControl = new FlightMoveControl(this, 20, true);
		this.lookControl = new BeeLookControl(this);
		this.setPathfindingPenalty(PathNodeType.DANGER_FIRE, -1.0F);
		this.setPathfindingPenalty(PathNodeType.WATER, -1.0F);
		this.setPathfindingPenalty(PathNodeType.WATER_BORDER, 16.0F);
		this.setPathfindingPenalty(PathNodeType.COCOA, -1.0F);
		this.setPathfindingPenalty(PathNodeType.FENCE, -1.0F);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(BEE_FLAGS, (byte)0);
	}

	@Override
	public SimpleInventory getInventory() {
		return this.inventory;
	}

	@Override
	public float getPathfindingFavor(BlockPos pos, WorldView world) {
		return world.getBlockState(pos).isAir() ? 10.0F : 0.0F;
	}

	@Override
	protected void initGoals() {
		this.goalSelector.add(0, new StingGoal(this, 1.4F, true));
		this.goalSelector.add(6, new CollectBlockGoal(this, BlockTags.LOGS, 64));
		this.goalSelector.add(6, new PlaceBlockGoal(this));
		this.goalSelector.add(6, new MoveToPlayerGoal());
		this.goalSelector.add(7, new GrowCropsGoal());
		this.goalSelector.add(8, new BeeWanderAroundGoal());
		this.goalSelector.add(9, new SwimGoal(this));
		this.targetSelector.add(2, new StingTargetGoal(this));
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);

		try {
			this.writeInventory(nbt, this.getRegistryManager());
		} catch (Exception e) {
			LOGGER.error("Unable to save inventory for Beedee", e);
		}

		if (this.hasPlayer()) {
			nbt.put("player_pos", NbtHelper.fromBlockPos(this.getPlayerPos()));
		}

		nbt.putBoolean("HasNectar", this.hasNectar());
		nbt.putBoolean("HasStung", this.hasStung());
		nbt.putInt("TicksSincePollination", this.ticksSincePollination);
		nbt.putInt("CropsGrownSincePollination", this.cropsGrownSincePollination);
	}

	static class CollectBlockGoal extends Goal {
		private final BeedeeEntity beedee;
		private final TagKey<Block> blocksToCollect;

		public CollectBlockGoal(BeedeeEntity beedee, TagKey<Block> blocksToCollect, int maxPerStack) {
			this.beedee = beedee;
			this.blocksToCollect = blocksToCollect;
		}

		@Override
		public boolean canStart() {
      return beedee.getInventory().heldStacks.stream().anyMatch(itemStack ->
				itemStack.getName().getString().equalsIgnoreCase("Air")
			);
		}

		@Override
		public void tick() {
			Random random = this.beedee.getRandom();
			World world = this.beedee.getWorld();
			int i = MathHelper.floor(this.beedee.getX() - 2.0 + random.nextDouble() * 4.0);
			int j = MathHelper.floor(this.beedee.getY() + random.nextDouble() * 3.0);
			int k = MathHelper.floor(this.beedee.getZ() - 2.0 + random.nextDouble() * 4.0);

			Vec3d vec3d = new Vec3d((double)this.beedee.getBlockX() + 0.5, (double)j + 0.5, (double)this.beedee.getBlockZ() + 0.5);
			Vec3d vec3d2 = new Vec3d((double)i + 2.5, (double)j + 2.5, (double)k + 2.5);
			BlockHitResult blockHitResult = world.raycast(
							new RaycastContext(vec3d, vec3d2, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, this.beedee)
			);
			BlockState blockState = world.getBlockState(blockHitResult.getBlockPos());
			LOGGER.debug("See {} at [{},{},{}]", blockState.toString(), blockHitResult.getBlockPos().getX(), blockHitResult.getBlockPos().getY(), blockHitResult.getBlockPos().getZ());

			if (blockState.isIn(blocksToCollect)) {
				LOGGER.info("Found {}", blockState.getBlock().toString());
				Item convertedToItem = blockState.getBlock().asItem();
				world.removeBlock(blockHitResult.getBlockPos(), false);
				world.emitGameEvent(GameEvent.BLOCK_DESTROY, blockHitResult.getBlockPos(), GameEvent.Emitter.of(this.beedee, blockState));
				try {
					ItemStack stackToAdd = convertedToItem.getDefaultStack();
					LOGGER.info("Adding {} to {} stack", stackToAdd.getCount(), stackToAdd.getItem().toString());
					this.beedee.inventory.addStack(stackToAdd);
				} catch (Exception e) {
					LOGGER.error("Couldn't addStack: ", e);
				}
			}
		}
	}

	static class PlaceBlockGoal extends Goal {
		private final BeedeeEntity beedee;

		public PlaceBlockGoal(BeedeeEntity beedee) {
			this.beedee = beedee;
		}

		@Override
		public boolean canStart() {
			int itemsCount = beedee.getInventory().heldStacks.stream()
							.filter(itemStack -> !itemStack.getName().getString().equalsIgnoreCase("Air"))
							.peek(itemStack -> LOGGER.info("Inventory item: {}, count: {}", itemStack.getItem().toString(), itemStack.getCount()))
							.mapToInt(ItemStack::getCount)
							.sum();

			if (itemsCount > 0) {
				LOGGER.info("Total items count: {}", itemsCount);
			}

			return itemsCount > 0;
		}

		@Override
		public void tick() {
			Random random = this.beedee.getRandom();
			World world = this.beedee.getWorld();
			int i = MathHelper.floor(this.beedee.getX() + 1.0 + random.nextDouble() * 2.0);
			int j = MathHelper.floor(this.beedee.getY() + random.nextDouble() * 2.0);
			int k = MathHelper.floor(this.beedee.getZ() + 1.0 + random.nextDouble() * 2.0);

			BlockPos blockPos = new BlockPos(i, j, k);
			BlockState blockState = world.getBlockState(blockPos);

			BlockPos blockPos2 = blockPos.down();
			BlockState blockState2 = world.getBlockState(blockPos2);
			ItemStack itemStack = this.beedee.inventory.removeItem(
							this.beedee.getInventory()
											.heldStacks
											.stream()
											.filter(itemStack1 -> !itemStack1.getItem().getName().getString().equals("Air"))
											.findFirst().get()
											.getItem(), 1);

			LOGGER.info("Found item to place: {}", itemStack.getName().getString());
			if (!itemStack.isEmpty() && itemStack.getItem() instanceof BlockItem blockItem) {
				BlockState blockState3 = blockItem.getBlock().getDefaultState();
				if (this.canPlaceOn(world, blockPos, blockState3, blockState, blockState2, blockPos2)) {
					world.setBlockState(blockPos, blockState3);
					world.emitGameEvent(GameEvent.BLOCK_PLACE, blockPos, GameEvent.Emitter.of(this.beedee, blockState3));
					LOGGER.info("Placed item: {}", itemStack.getName().getString());
				} else {
					LOGGER.info("Couldn't place item: {}", itemStack.getName().getString());
				}
			}
		}

		private boolean canPlaceOn(World world, BlockPos posAbove, BlockState carriedState, BlockState stateAbove, BlockState state, BlockPos pos) {
			return stateAbove.isAir()
							&& !state.isAir()
							&& !state.isOf(Blocks.BEDROCK)
							&& state.isFullCube(world, pos)
							&& carriedState.canPlaceAt(world, posAbove)
							&& world.getOtherEntities(this.beedee, Box.from(Vec3d.of(posAbove))).isEmpty();
		}
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		this.playerPos = NbtHelper.toBlockPos(nbt, "player_pos").orElse(null);
		super.readCustomDataFromNbt(nbt);
		this.readInventory(nbt, this.getRegistryManager());
		this.setHasNectar(nbt.getBoolean("HasNectar"));
		this.setHasStung(nbt.getBoolean("HasStung"));
		this.ticksSincePollination = nbt.getInt("TicksSincePollination");
		this.cropsGrownSincePollination = nbt.getInt("CropsGrownSincePollination");
	}

	@Override
	public boolean tryAttack(Entity target) {
		DamageSource damageSource = this.getDamageSources().sting(this);
		boolean bl = target.damage(damageSource, (float)((int)this.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE)));
		if (bl) {
			if (this.getWorld() instanceof ServerWorld serverWorld) {
				EnchantmentHelper.onTargetDamaged(serverWorld, target, damageSource);
			}

			if (target instanceof LivingEntity livingEntity) {
				livingEntity.setStingerCount(livingEntity.getStingerCount() + 1);
				int i = 0;
				if (this.getWorld().getDifficulty() == Difficulty.NORMAL) {
					i = 10;
				} else if (this.getWorld().getDifficulty() == Difficulty.HARD) {
					i = 18;
				}

				if (i > 0) {
					livingEntity.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, i * 20, 0), this);
				}
			}

			this.setHasStung(true);
			this.playSound(SoundEvents.ENTITY_BEE_STING, 1.0F, 1.0F);
		}

		return bl;
	}

	@Override
	public void tick() {
		super.tick();
		if (this.hasNectar() && this.getCropsGrownSincePollination() < 10 && this.random.nextFloat() < 0.05F) {
			for (int i = 0; i < this.random.nextInt(2) + 1; i++) {
				this.addParticle(
					this.getWorld(), this.getX() - 0.3F, this.getX() + 0.3F, this.getZ() - 0.3F, this.getZ() + 0.3F, this.getBodyY(0.5), ParticleTypes.FALLING_NECTAR
				);
			}
		}

		this.updateBodyPitch();
	}

	private void addParticle(World world, double lastX, double x, double lastZ, double z, double y, ParticleEffect effect) {
		world.addParticle(effect, MathHelper.lerp(world.random.nextDouble(), lastX, x), y, MathHelper.lerp(world.random.nextDouble(), lastZ, z), 0.0, 0.0, 0.0);
	}

	void startMovingTo(BlockPos pos) {
		Vec3d vec3d = Vec3d.ofBottomCenter(pos);
		int i = 0;
		BlockPos blockPos = this.getBlockPos();
		int j = (int)vec3d.y - blockPos.getY();
		if (j > 2) {
			i = 4;
		} else if (j < -2) {
			i = -4;
		}

		int k = 6;
		int l = 8;
		int m = blockPos.getManhattanDistance(pos);
		if (m < 15) {
			k = m / 2;
			l = m / 2;
		}

		Vec3d vec3d2 = NoWaterTargeting.find(this, k, l, i, vec3d, (float) (Math.PI / 10));
		if (vec3d2 != null) {
			this.navigation.setRangeMultiplier(0.5F);
			this.navigation.startMovingTo(vec3d2.x, vec3d2.y, vec3d2.z, 1.0);
		}
	}

	@Nullable
	public BlockPos getPlayerPos() {
		return this.playerPos;
	}

	public boolean hasPlayer() {
		return this.playerPos != null;
	}

	public float getBodyPitch(float tickDelta) {
		return MathHelper.lerp(tickDelta, this.lastPitch, this.currentPitch);
	}

	private void updateBodyPitch() {
		this.lastPitch = this.currentPitch;
		if (this.isNearTarget()) {
			this.currentPitch = Math.min(1.0F, this.currentPitch + 0.2F);
		} else {
			this.currentPitch = Math.max(0.0F, this.currentPitch - 0.24F);
		}
	}

	@Override
	protected void mobTick() {
		boolean bl = this.hasStung();
		if (this.isInsideWaterOrBubbleColumn()) {
			this.ticksInsideWater++;
		} else {
			this.ticksInsideWater = 0;
		}

		if (this.ticksInsideWater > 20) {
			this.damage(this.getDamageSources().drown(), 1.0F);
		}

		if (bl) {
			this.ticksSinceSting++;
			if (this.ticksSinceSting % 5 == 0 && this.random.nextInt(MathHelper.clamp(1200 - this.ticksSinceSting, 1, 1200)) == 0) {
				this.damage(this.getDamageSources().generic(), this.getHealth());
			}
		}

		if (!this.hasNectar()) {
			this.ticksSincePollination++;
		}

	}

	public void resetPollinationTicks() {
		this.ticksSincePollination = 0;
	}

	@Override
	protected void sendAiDebugData() {
		super.sendAiDebugData();
	}

	int getCropsGrownSincePollination() {
		return this.cropsGrownSincePollination;
	}

	private void resetCropCounter() {
		this.cropsGrownSincePollination = 0;
	}

	void addCropCounter() {
		this.cropsGrownSincePollination++;
	}

	@Override
	public void tickMovement() {
		super.tickMovement();
//		if (!this.getWorld().isClient) {
//			boolean bl = this.hasAngerTime() && !this.hasStung() && this.getTarget() != null && this.getTarget().squaredDistanceTo(this) < 4.0;
//			this.setNearTarget(bl);
//		}
	}

	public boolean hasNectar() {
		return this.getBeeFlag(HAS_NECTAR_FLAG);
	}

	void setHasNectar(boolean hasNectar) {
		if (hasNectar) {
			this.resetPollinationTicks();
		}

		this.setBeeFlag(HAS_NECTAR_FLAG, hasNectar);
	}

	public boolean hasStung() {
		return this.getBeeFlag(HAS_STUNG_FLAG);
	}

	private void setHasStung(boolean hasStung) {
		this.setBeeFlag(HAS_STUNG_FLAG, hasStung);
	}

	private boolean isNearTarget() {
		return this.getBeeFlag(NEAR_TARGET_FLAG);
	}

	private void setNearTarget(boolean nearTarget) {
		this.setBeeFlag(NEAR_TARGET_FLAG, nearTarget);
	}

	boolean isTooFarFromPlayer(BlockPos pos) {
		return !this.isWithinDistance(pos, PLAYER_SEARCH_DISTANCE);
	}

	private void setBeeFlag(int bit, boolean value) {
		if (value) {
			this.dataTracker.set(BEE_FLAGS, (byte)(this.dataTracker.get(BEE_FLAGS) | bit));
		} else {
			this.dataTracker.set(BEE_FLAGS, (byte)(this.dataTracker.get(BEE_FLAGS) & ~bit));
		}
	}

	private boolean getBeeFlag(int location) {
		return (this.dataTracker.get(BEE_FLAGS) & location) != 0;
	}

	public static DefaultAttributeContainer.Builder createBeedeeAttributes() {
		return MobEntity.createMobAttributes()
			.add(EntityAttributes.GENERIC_MAX_HEALTH, 10.0)
			.add(EntityAttributes.GENERIC_FLYING_SPEED, 0.6F)
			.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3F)
			.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 2.0)
			.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48.0);
	}

	@Override
	protected EntityNavigation createNavigation(World world) {
		BirdNavigation birdNavigation = new BirdNavigation(this, world) {
			@Override
			public boolean isValidPosition(BlockPos pos) {
				return !this.world.getBlockState(pos.down()).isAir();
			}

			@Override
			public void tick() {
				super.tick();
			}
		};
		birdNavigation.setCanPathThroughDoors(false);
		birdNavigation.setCanSwim(false);
		birdNavigation.setCanEnterOpenDoors(true);
		return birdNavigation;
	}

	boolean isFlowers(BlockPos pos) {
		return this.getWorld().canSetBlock(pos) && this.getWorld().getBlockState(pos).isIn(BlockTags.FLOWERS);
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return null;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ENTITY_BEE_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ENTITY_BEE_DEATH;
	}

	@Override
	protected float getSoundVolume() {
		return 0.4F;
	}

	@Override
	protected void fall(double heightDifference, boolean onGround, BlockState state, BlockPos landedPosition) {
	}

	@Override
	public boolean isFlappingWings() {
		return this.isInAir() && this.age % field_28638 == 0;
	}

	@Override
	public boolean isInAir() {
		return !this.isOnGround();
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		if (this.isInvulnerableTo(source)) {
			return false;
		} else {
			return super.damage(source, amount);
		}
	}

	@Override
	protected void swimUpward(TagKey<Fluid> fluid) {
		this.setVelocity(this.getVelocity().add(0.0, 0.01, 0.0));
	}

	@Override
	public Vec3d getLeashOffset() {
		return new Vec3d(0.0, (double)(0.5F * this.getStandingEyeHeight()), (double)(this.getWidth() * 0.2F));
	}

	boolean isWithinDistance(BlockPos pos, int distance) {
		return pos.isWithinDistance(this.getBlockPos(), (double)distance);
	}

	class BeeLookControl extends LookControl {
		BeeLookControl(final MobEntity entity) {
			super(entity);
		}

		@Override
		public void tick() {
				super.tick();
		}

		@Override
		protected boolean shouldStayHorizontal() {
//			return !BeedeeEntity.this.pollinateGoal.isRunning();
			return true;
		}
	}

	class BeeWanderAroundGoal extends Goal {
		private static final int MAX_DISTANCE = 22;

		BeeWanderAroundGoal() {
			this.setControls(EnumSet.of(Control.MOVE));
		}

		@Override
		public boolean canStart() {
			return BeedeeEntity.this.navigation.isIdle() && BeedeeEntity.this.random.nextInt(10) == 0;
		}

		@Override
		public boolean shouldContinue() {
			return BeedeeEntity.this.navigation.isFollowingPath();
		}

		@Override
		public void start() {
			Vec3d vec3d = this.getRandomLocation();
			if (vec3d != null) {
				BeedeeEntity.this.navigation.startMovingAlong(BeedeeEntity.this.navigation.findPathTo(BlockPos.ofFloored(vec3d), 1), 1.0);
			}
		}

		@Nullable
		private Vec3d getRandomLocation() {
			Vec3d vec3d2 = BeedeeEntity.this.getRotationVec(0.0F);
			Vec3d vec3d3 = AboveGroundTargeting.find(BeedeeEntity.this, 8, 7, vec3d2.x, vec3d2.z, (float) (Math.PI / 2), 3, 1);
			return vec3d3 != null ? vec3d3 : NoPenaltySolidTargeting.find(BeedeeEntity.this, 8, 4, -2, vec3d2.x, vec3d2.z, (float) (Math.PI / 2));
		}
	}

	class GrowCropsGoal extends NotAngryGoal {
		@Override
		public boolean canBeeStart() {
			if (BeedeeEntity.this.getCropsGrownSincePollination() >= 10) {
				return false;
			} else {
				return !(BeedeeEntity.this.random.nextFloat() < 0.3F) && BeedeeEntity.this.hasNectar();
			}
		}

		@Override
		public boolean canBeeContinue() {
			return this.canBeeStart();
		}

		@Override
		public void tick() {
			if (BeedeeEntity.this.random.nextInt(this.getTickCount(30)) == 0) {
				for (int i = 1; i <= 2; i++) {
					BlockPos blockPos = BeedeeEntity.this.getBlockPos().down(i);
					BlockState blockState = BeedeeEntity.this.getWorld().getBlockState(blockPos);
					Block block = blockState.getBlock();
					BlockState blockState2 = null;
					if (blockState.isIn(BlockTags.BEE_GROWABLES)) {
						if (block instanceof CropBlock cropBlock) {
              if (!cropBlock.isMature(blockState)) {
								blockState2 = cropBlock.withAge(cropBlock.getAge(blockState) + 1);
							}
						} else if (block instanceof StemBlock) {
							int j = (Integer)blockState.get(StemBlock.AGE);
							if (j < 7) {
								blockState2 = blockState.with(StemBlock.AGE, j + 1);
							}
						} else if (blockState.isOf(Blocks.SWEET_BERRY_BUSH)) {
							int j = (Integer)blockState.get(SweetBerryBushBlock.AGE);
							if (j < 3) {
								blockState2 = blockState.with(SweetBerryBushBlock.AGE, j + 1);
							}
						} else if (blockState.isOf(Blocks.CAVE_VINES) || blockState.isOf(Blocks.CAVE_VINES_PLANT)) {
							((Fertilizable)blockState.getBlock()).grow((ServerWorld) BeedeeEntity.this.getWorld(), BeedeeEntity.this.random, blockPos, blockState);
						}

						if (blockState2 != null) {
							BeedeeEntity.this.getWorld().syncWorldEvent(WorldEvents.BEE_FERTILIZES_PLANT, blockPos, 15);
							BeedeeEntity.this.getWorld().setBlockState(blockPos, blockState2);
							BeedeeEntity.this.addCropCounter();
						}
					}
				}
			}
		}
	}

	public class MoveToPlayerGoal extends NotAngryGoal {

		MoveToPlayerGoal() {
			this.setControls(EnumSet.of(Control.MOVE));
		}

		@Override
		public boolean canBeeStart() {
			if (BeedeeEntity.this.player == null) {
				final Vec3d beedeePos = BeedeeEntity.this.getPos();
				BeedeeEntity.this.player = BeedeeEntity.this
								.getWorld()
								.getClosestPlayer(beedeePos.x, beedeePos.y, beedeePos.z, 55, false);
				if (BeedeeEntity.this.player == null) {
					return false;
				}

				playerPos = BeedeeEntity.this.player.getBlockPos();
			}

			BeedeeEntity.this.playerPos = BeedeeEntity.this.player.getBlockPos();

			boolean canStart = BeedeeEntity.this.playerPos != null
							&& !BeedeeEntity.this.hasPositionTarget()
//							&& this.shouldMoveToFlower()
//							&& BeedeeEntity.this.isFlowers(BeedeeEntity.this.flowerPos)
//							&& !BeedeeEntity.this.isWithinDistance(BeedeeEntity.this.flowerPos, 2)
			;
			return canStart;
		}

		@Override
		public boolean canBeeContinue() {
			return this.canBeeStart();
		}

		@Override
		public void start() {
			super.start();
		}

		@Override
		public void stop() {
			BeedeeEntity.this.navigation.stop();
			BeedeeEntity.this.navigation.resetRangeMultiplier();
		}

		@Override
		public void tick() {
			if (BeedeeEntity.this.playerPos != null) {
					if (BeedeeEntity.this.isTooFarFromPlayer(BeedeeEntity.this.playerPos)) {
						BeedeeEntity.this.playerPos = null;
					} else {
						BeedeeEntity.this.startMovingTo(BeedeeEntity.this.playerPos);
					}
			}
		}
	}

	abstract static class NotAngryGoal extends Goal {
		public abstract boolean canBeeStart();

		public abstract boolean canBeeContinue();

		@Override
		public boolean canStart() {
			return this.canBeeStart();
		}

		@Override
		public boolean shouldContinue() {
			return this.canBeeContinue();
		}
	}

	class StingGoal extends MeleeAttackGoal {
		StingGoal(final PathAwareEntity mob, final double speed, final boolean pauseWhenMobIdle) {
			super(mob, speed, pauseWhenMobIdle);
		}

		@Override
		public boolean canStart() {
			return super.canStart() && !BeedeeEntity.this.hasStung();
		}

		@Override
		public boolean shouldContinue() {
			return super.shouldContinue() && !BeedeeEntity.this.hasStung();
		}
	}

	static class StingTargetGoal extends ActiveTargetGoal<PlayerEntity> {
		StingTargetGoal(BeedeeEntity bee) {
			super(bee, PlayerEntity.class, 10, true, false, null);
		}

		@Override
		public boolean canStart() {
			return this.canSting() && super.canStart();
		}

		@Override
		public boolean shouldContinue() {
			boolean bl = this.canSting();
			if (bl && this.mob.getTarget() != null) {
				return super.shouldContinue();
			} else {
				this.target = null;
				return false;
			}
		}

		private boolean canSting() {
			BeedeeEntity beedeeEntity = (BeedeeEntity)this.mob;
			return !beedeeEntity.hasStung();
		}
	}
}
