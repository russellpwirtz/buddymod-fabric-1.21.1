package com.dangerussell.entities;

import com.google.common.collect.Lists;
import net.minecraft.block.*;
import net.minecraft.block.entity.BeehiveBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.Flutterer;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.AboveGroundTargeting;
import net.minecraft.entity.ai.NoPenaltySolidTargeting;
import net.minecraft.entity.ai.NoWaterTargeting;
import net.minecraft.entity.ai.control.FlightMoveControl;
import net.minecraft.entity.ai.control.LookControl;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.ai.pathing.BirdNavigation;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.Path;
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
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.registry.tag.PointOfInterestTypeTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.util.annotation.Debug;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.World;
import net.minecraft.world.WorldEvents;
import net.minecraft.world.WorldView;
import net.minecraft.world.poi.PointOfInterest;
import net.minecraft.world.poi.PointOfInterestStorage;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BeedeeEntity extends PathAwareEntity implements Flutterer {
	public static final int field_28638 = MathHelper.ceil(1.4959966F);
	private static final TrackedData<Byte> BEE_FLAGS = DataTracker.registerData(BeedeeEntity.class, TrackedDataHandlerRegistry.BYTE);
	private static final int NEAR_TARGET_FLAG = 2;
	private static final int HAS_STUNG_FLAG = 4;
	private static final int HAS_NECTAR_FLAG = 8;
	@Nullable
	private float currentPitch;
	private float lastPitch;
	private int ticksSinceSting;
	int ticksSincePollination;
	private int cannotEnterHiveTicks;
	private int cropsGrownSincePollination;
	int ticksLeftToFindHive;
	int ticksUntilCanPollinate = MathHelper.nextInt(this.random, 20, 60);
	@Nullable
	BlockPos flowerPos;
	@Nullable
	BlockPos hivePos;
	PollinateGoal pollinateGoal;
	MoveToHiveGoal moveToHiveGoal;
	private MoveToFlowerGoal moveToFlowerGoal;
	private int ticksInsideWater;

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
	public float getPathfindingFavor(BlockPos pos, WorldView world) {
		return world.getBlockState(pos).isAir() ? 10.0F : 0.0F;
	}

	@Override
	protected void initGoals() {
		this.goalSelector.add(0, new StingGoal(this, 1.4F, true));
		this.goalSelector.add(1, new EnterHiveGoal());
		this.goalSelector.add(3, new TemptGoal(this, 1.25, stack -> stack.isIn(ItemTags.BEE_FOOD), false));
		this.pollinateGoal = new PollinateGoal();
		this.goalSelector.add(4, this.pollinateGoal);
		this.goalSelector.add(5, new FindHiveGoal());
		this.moveToHiveGoal = new MoveToHiveGoal();
		this.goalSelector.add(5, this.moveToHiveGoal);
		this.moveToFlowerGoal = new MoveToFlowerGoal();
		this.goalSelector.add(6, this.moveToFlowerGoal);
		this.goalSelector.add(7, new GrowCropsGoal());
		this.goalSelector.add(8, new BeeWanderAroundGoal());
		this.goalSelector.add(9, new SwimGoal(this));
		this.targetSelector.add(2, new StingTargetGoal(this));
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		if (this.hasHive()) {
			nbt.put("hive_pos", NbtHelper.fromBlockPos(this.getHivePos()));
		}

		if (this.hasFlower()) {
			nbt.put("flower_pos", NbtHelper.fromBlockPos(this.getFlowerPos()));
		}

		nbt.putBoolean("HasNectar", this.hasNectar());
		nbt.putBoolean("HasStung", this.hasStung());
		nbt.putInt("TicksSincePollination", this.ticksSincePollination);
		nbt.putInt("CannotEnterHiveTicks", this.cannotEnterHiveTicks);
		nbt.putInt("CropsGrownSincePollination", this.cropsGrownSincePollination);
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		this.hivePos = (BlockPos)NbtHelper.toBlockPos(nbt, "hive_pos").orElse(null);
		this.flowerPos = (BlockPos)NbtHelper.toBlockPos(nbt, "flower_pos").orElse(null);
		super.readCustomDataFromNbt(nbt);
		this.setHasNectar(nbt.getBoolean("HasNectar"));
		this.setHasStung(nbt.getBoolean("HasStung"));
		this.ticksSincePollination = nbt.getInt("TicksSincePollination");
		this.cannotEnterHiveTicks = nbt.getInt("CannotEnterHiveTicks");
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
	public BlockPos getFlowerPos() {
		return this.flowerPos;
	}

	public boolean hasFlower() {
		return this.flowerPos != null;
	}

	private boolean failedPollinatingTooLong() {
		return this.ticksSincePollination > 3600;
	}

	boolean canEnterHive() {
		if (this.cannotEnterHiveTicks <= 0 && !this.pollinateGoal.isRunning() && !this.hasStung() && this.getTarget() == null) {
			boolean bl = this.failedPollinatingTooLong() || this.getWorld().isRaining() || this.getWorld().isNight() || this.hasNectar();
			return bl && !this.isHiveNearFire();
		} else {
			return false;
		}
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

	private boolean isHiveNearFire() {
		if (this.hivePos == null) {
			return false;
		} else {
			BlockEntity blockEntity = this.getWorld().getBlockEntity(this.hivePos);
			return blockEntity instanceof BeehiveBlockEntity && ((BeehiveBlockEntity)blockEntity).isNearFire();
		}
	}

	private boolean doesHiveHaveSpace(BlockPos pos) {
		BlockEntity blockEntity = this.getWorld().getBlockEntity(pos);
		return blockEntity instanceof BeehiveBlockEntity ? !((BeehiveBlockEntity)blockEntity).isFullOfBees() : false;
	}

	@Debug
	public boolean hasHive() {
		return this.hivePos != null;
	}

	@Nullable
	@Debug
	public BlockPos getHivePos() {
		return this.hivePos;
	}

	@Override
	protected void sendAiDebugData() {
		super.sendAiDebugData();
//		DebugInfoSender.sendBeeDebugData(this);
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
		if (!this.getWorld().isClient) {
			if (this.cannotEnterHiveTicks > 0) {
				this.cannotEnterHiveTicks--;
			}

			if (this.ticksLeftToFindHive > 0) {
				this.ticksLeftToFindHive--;
			}

			if (this.ticksUntilCanPollinate > 0) {
				this.ticksUntilCanPollinate--;
			}

//			boolean bl = this.hasAngerTime() && !this.hasStung() && this.getTarget() != null && this.getTarget().squaredDistanceTo(this) < 4.0;
//			this.setNearTarget(bl);
			if (this.age % 20 == 0 && !this.isHiveValid()) {
				this.hivePos = null;
			}
		}
	}

	boolean isHiveValid() {
		if (!this.hasHive()) {
			return false;
		} else if (this.isTooFar(this.hivePos)) {
			return false;
		} else {
			BlockEntity blockEntity = this.getWorld().getBlockEntity(this.hivePos);
			return blockEntity != null && blockEntity.getType() == BlockEntityType.BEEHIVE;
		}
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

	boolean isTooFar(BlockPos pos) {
		return !this.isWithinDistance(pos, 32);
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
				if (!BeedeeEntity.this.pollinateGoal.isRunning()) {
					super.tick();
				}
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
			if (!this.getWorld().isClient) {
				this.pollinateGoal.cancel();
			}

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
			return !BeedeeEntity.this.pollinateGoal.isRunning();
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
			Vec3d vec3d2;
			if (BeedeeEntity.this.isHiveValid() && !BeedeeEntity.this.isWithinDistance(BeedeeEntity.this.hivePos, 22)) {
				Vec3d vec3d = Vec3d.ofCenter(BeedeeEntity.this.hivePos);
				vec3d2 = vec3d.subtract(BeedeeEntity.this.getPos()).normalize();
			} else {
				vec3d2 = BeedeeEntity.this.getRotationVec(0.0F);
			}

			int i = 8;
			Vec3d vec3d3 = AboveGroundTargeting.find(BeedeeEntity.this, 8, 7, vec3d2.x, vec3d2.z, (float) (Math.PI / 2), 3, 1);
			return vec3d3 != null ? vec3d3 : NoPenaltySolidTargeting.find(BeedeeEntity.this, 8, 4, -2, vec3d2.x, vec3d2.z, (float) (Math.PI / 2));
		}
	}

	class EnterHiveGoal extends NotAngryGoal {
		@Override
		public boolean canBeeStart() {
			if (BeedeeEntity.this.hasHive()
				&& BeedeeEntity.this.canEnterHive()
				&& BeedeeEntity.this.hivePos.isWithinDistance(BeedeeEntity.this.getPos(), 2.0)
				&& BeedeeEntity.this.getWorld().getBlockEntity(BeedeeEntity.this.hivePos) instanceof BeehiveBlockEntity beehiveBlockEntity) {
				if (!beehiveBlockEntity.isFullOfBees()) {
					return true;
				}

				BeedeeEntity.this.hivePos = null;
			}

			return false;
		}

		@Override
		public boolean canBeeContinue() {
			return false;
		}

		@Override
		public void start() {
			if (BeedeeEntity.this.getWorld().getBlockEntity(BeedeeEntity.this.hivePos) instanceof BeehiveBlockEntity beehiveBlockEntity) {
				beehiveBlockEntity.tryEnterHive(BeedeeEntity.this);
			}
		}
	}

	class FindHiveGoal extends NotAngryGoal {
		@Override
		public boolean canBeeStart() {
			return BeedeeEntity.this.ticksLeftToFindHive == 0 && !BeedeeEntity.this.hasHive() && BeedeeEntity.this.canEnterHive();
		}

		@Override
		public boolean canBeeContinue() {
			return false;
		}

		@Override
		public void start() {
			BeedeeEntity.this.ticksLeftToFindHive = 200;
			List<BlockPos> list = this.getNearbyFreeHives();
			if (!list.isEmpty()) {
				for (BlockPos blockPos : list) {
					if (!BeedeeEntity.this.moveToHiveGoal.isPossibleHive(blockPos)) {
						BeedeeEntity.this.hivePos = blockPos;
						return;
					}
				}

				BeedeeEntity.this.moveToHiveGoal.clearPossibleHives();
				BeedeeEntity.this.hivePos = (BlockPos)list.get(0);
			}
		}

		private List<BlockPos> getNearbyFreeHives() {
			BlockPos blockPos = BeedeeEntity.this.getBlockPos();
			PointOfInterestStorage pointOfInterestStorage = ((ServerWorld) BeedeeEntity.this.getWorld()).getPointOfInterestStorage();
			Stream<PointOfInterest> stream = pointOfInterestStorage.getInCircle(
				poiType -> poiType.isIn(PointOfInterestTypeTags.BEE_HOME), blockPos, 20, PointOfInterestStorage.OccupationStatus.ANY
			);
			return (List<BlockPos>)stream.map(PointOfInterest::getPos)
				.filter(BeedeeEntity.this::doesHiveHaveSpace)
				.sorted(Comparator.comparingDouble(blockPos2 -> blockPos2.getSquaredDistance(blockPos)))
				.collect(Collectors.toList());
		}
	}

	class GrowCropsGoal extends NotAngryGoal {
		@Override
		public boolean canBeeStart() {
			if (BeedeeEntity.this.getCropsGrownSincePollination() >= 10) {
				return false;
			} else {
				return BeedeeEntity.this.random.nextFloat() < 0.3F ? false : BeedeeEntity.this.hasNectar() && BeedeeEntity.this.isHiveValid();
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
						if (block instanceof CropBlock) {
							CropBlock cropBlock = (CropBlock)block;
							if (!cropBlock.isMature(blockState)) {
								blockState2 = cropBlock.withAge(cropBlock.getAge(blockState) + 1);
							}
						} else if (block instanceof StemBlock) {
							int j = (Integer)blockState.get(StemBlock.AGE);
							if (j < 7) {
								blockState2 = blockState.with(StemBlock.AGE, Integer.valueOf(j + 1));
							}
						} else if (blockState.isOf(Blocks.SWEET_BERRY_BUSH)) {
							int j = (Integer)blockState.get(SweetBerryBushBlock.AGE);
							if (j < 3) {
								blockState2 = blockState.with(SweetBerryBushBlock.AGE, Integer.valueOf(j + 1));
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

	public class MoveToFlowerGoal extends NotAngryGoal {
		private static final int MAX_FLOWER_NAVIGATION_TICKS = 600;
		int ticks = BeedeeEntity.this.getWorld().random.nextInt(10);

		MoveToFlowerGoal() {
			this.setControls(EnumSet.of(Control.MOVE));
		}

		@Override
		public boolean canBeeStart() {
			return BeedeeEntity.this.flowerPos != null
				&& !BeedeeEntity.this.hasPositionTarget()
				&& this.shouldMoveToFlower()
				&& BeedeeEntity.this.isFlowers(BeedeeEntity.this.flowerPos)
				&& !BeedeeEntity.this.isWithinDistance(BeedeeEntity.this.flowerPos, 2);
		}

		@Override
		public boolean canBeeContinue() {
			return this.canBeeStart();
		}

		@Override
		public void start() {
			this.ticks = 0;
			super.start();
		}

		@Override
		public void stop() {
			this.ticks = 0;
			BeedeeEntity.this.navigation.stop();
			BeedeeEntity.this.navigation.resetRangeMultiplier();
		}

		@Override
		public void tick() {
			if (BeedeeEntity.this.flowerPos != null) {
				this.ticks++;
				if (this.ticks > this.getTickCount(600)) {
					BeedeeEntity.this.flowerPos = null;
				} else if (!BeedeeEntity.this.navigation.isFollowingPath()) {
					if (BeedeeEntity.this.isTooFar(BeedeeEntity.this.flowerPos)) {
						BeedeeEntity.this.flowerPos = null;
					} else {
						BeedeeEntity.this.startMovingTo(BeedeeEntity.this.flowerPos);
					}
				}
			}
		}

		private boolean shouldMoveToFlower() {
			return BeedeeEntity.this.ticksSincePollination > 2400;
		}
	}

	@Debug
	public class MoveToHiveGoal extends NotAngryGoal {
		public static final int field_30295 = 600;
		int ticks = BeedeeEntity.this.getWorld().random.nextInt(10);
		private static final int field_30296 = 3;
		final List<BlockPos> possibleHives = Lists.<BlockPos>newArrayList();
		@Nullable
		private Path path;
		private static final int field_30297 = 60;
		private int ticksUntilLost;

		MoveToHiveGoal() {
			this.setControls(EnumSet.of(Control.MOVE));
		}

		@Override
		public boolean canBeeStart() {
			return BeedeeEntity.this.hivePos != null
				&& !BeedeeEntity.this.hasPositionTarget()
				&& BeedeeEntity.this.canEnterHive()
				&& !this.isCloseEnough(BeedeeEntity.this.hivePos)
				&& BeedeeEntity.this.getWorld().getBlockState(BeedeeEntity.this.hivePos).isIn(BlockTags.BEEHIVES);
		}

		@Override
		public boolean canBeeContinue() {
			return this.canBeeStart();
		}

		@Override
		public void start() {
			this.ticks = 0;
			this.ticksUntilLost = 0;
			super.start();
		}

		@Override
		public void stop() {
			this.ticks = 0;
			this.ticksUntilLost = 0;
			BeedeeEntity.this.navigation.stop();
			BeedeeEntity.this.navigation.resetRangeMultiplier();
		}

		@Override
		public void tick() {
			if (BeedeeEntity.this.hivePos != null) {
				this.ticks++;
				if (this.ticks > this.getTickCount(600)) {
					this.makeChosenHivePossibleHive();
				} else if (!BeedeeEntity.this.navigation.isFollowingPath()) {
					if (!BeedeeEntity.this.isWithinDistance(BeedeeEntity.this.hivePos, 16)) {
						if (BeedeeEntity.this.isTooFar(BeedeeEntity.this.hivePos)) {
							this.setLost();
						} else {
							BeedeeEntity.this.startMovingTo(BeedeeEntity.this.hivePos);
						}
					} else {
						boolean bl = this.startMovingToFar(BeedeeEntity.this.hivePos);
						if (!bl) {
							this.makeChosenHivePossibleHive();
						} else if (this.path != null && BeedeeEntity.this.navigation.getCurrentPath().equalsPath(this.path)) {
							this.ticksUntilLost++;
							if (this.ticksUntilLost > 60) {
								this.setLost();
								this.ticksUntilLost = 0;
							}
						} else {
							this.path = BeedeeEntity.this.navigation.getCurrentPath();
						}
					}
				}
			}
		}

		private boolean startMovingToFar(BlockPos pos) {
			BeedeeEntity.this.navigation.setRangeMultiplier(10.0F);
			BeedeeEntity.this.navigation.startMovingTo((double)pos.getX(), (double)pos.getY(), (double)pos.getZ(), 2, 1.0);
			return BeedeeEntity.this.navigation.getCurrentPath() != null && BeedeeEntity.this.navigation.getCurrentPath().reachesTarget();
		}

		boolean isPossibleHive(BlockPos pos) {
			return this.possibleHives.contains(pos);
		}

		private void addPossibleHive(BlockPos pos) {
			this.possibleHives.add(pos);

			while (this.possibleHives.size() > 3) {
				this.possibleHives.remove(0);
			}
		}

		void clearPossibleHives() {
			this.possibleHives.clear();
		}

		private void makeChosenHivePossibleHive() {
			if (BeedeeEntity.this.hivePos != null) {
				this.addPossibleHive(BeedeeEntity.this.hivePos);
			}

			this.setLost();
		}

		private void setLost() {
			BeedeeEntity.this.hivePos = null;
			BeedeeEntity.this.ticksLeftToFindHive = 200;
		}

		private boolean isCloseEnough(BlockPos pos) {
			if (BeedeeEntity.this.isWithinDistance(pos, 2)) {
				return true;
			} else {
				Path path = BeedeeEntity.this.navigation.getCurrentPath();
				return path != null && path.getTarget().equals(pos) && path.reachesTarget() && path.isFinished();
			}
		}
	}

	abstract class NotAngryGoal extends Goal {
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

	class PollinateGoal extends NotAngryGoal {
		private static final int field_30300 = 400;
		private static final int field_30301 = 20;
		private static final int field_30302 = 60;
		private final Predicate<BlockState> flowerPredicate = state -> {
			if (state.contains(Properties.WATERLOGGED) && (Boolean)state.get(Properties.WATERLOGGED)) {
				return false;
			} else if (state.isIn(BlockTags.FLOWERS)) {
				return state.isOf(Blocks.SUNFLOWER) ? state.get(TallPlantBlock.HALF) == DoubleBlockHalf.UPPER : true;
			} else {
				return false;
			}
		};
		private int pollinationTicks;
		private int lastPollinationTick;
		private boolean running;
		@Nullable
		private Vec3d nextTarget;
		private int ticks;

		PollinateGoal() {
			this.setControls(EnumSet.of(Control.MOVE));
		}

		@Override
		public boolean canBeeStart() {
			if (BeedeeEntity.this.ticksUntilCanPollinate > 0) {
				return false;
			} else if (BeedeeEntity.this.hasNectar()) {
				return false;
			} else if (BeedeeEntity.this.getWorld().isRaining()) {
				return false;
			} else {
				Optional<BlockPos> optional = this.getFlower();
				if (optional.isPresent()) {
					BeedeeEntity.this.flowerPos = (BlockPos)optional.get();
					BeedeeEntity.this.navigation
						.startMovingTo(
							(double) BeedeeEntity.this.flowerPos.getX() + 0.5, (double) BeedeeEntity.this.flowerPos.getY() + 0.5, (double) BeedeeEntity.this.flowerPos.getZ() + 0.5, 1.2F
						);
					return true;
				} else {
					BeedeeEntity.this.ticksUntilCanPollinate = MathHelper.nextInt(BeedeeEntity.this.random, 20, 60);
					return false;
				}
			}
		}

		@Override
		public boolean canBeeContinue() {
			if (!this.running) {
				return false;
			} else if (!BeedeeEntity.this.hasFlower()) {
				return false;
			} else if (BeedeeEntity.this.getWorld().isRaining()) {
				return false;
			} else if (this.completedPollination()) {
				return BeedeeEntity.this.random.nextFloat() < 0.2F;
			} else if (BeedeeEntity.this.age % 20 == 0 && !BeedeeEntity.this.isFlowers(BeedeeEntity.this.flowerPos)) {
				BeedeeEntity.this.flowerPos = null;
				return false;
			} else {
				return true;
			}
		}

		private boolean completedPollination() {
			return this.pollinationTicks > 400;
		}

		boolean isRunning() {
			return this.running;
		}

		void cancel() {
			this.running = false;
		}

		@Override
		public void start() {
			this.pollinationTicks = 0;
			this.ticks = 0;
			this.lastPollinationTick = 0;
			this.running = true;
			BeedeeEntity.this.resetPollinationTicks();
		}

		@Override
		public void stop() {
			if (this.completedPollination()) {
				BeedeeEntity.this.setHasNectar(true);
			}

			this.running = false;
			BeedeeEntity.this.navigation.stop();
			BeedeeEntity.this.ticksUntilCanPollinate = 200;
		}

		@Override
		public boolean shouldRunEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			this.ticks++;
			if (this.ticks > 600) {
				BeedeeEntity.this.flowerPos = null;
			} else {
				Vec3d vec3d = Vec3d.ofBottomCenter(BeedeeEntity.this.flowerPos).add(0.0, 0.6F, 0.0);
				if (vec3d.distanceTo(BeedeeEntity.this.getPos()) > 1.0) {
					this.nextTarget = vec3d;
					this.moveToNextTarget();
				} else {
					if (this.nextTarget == null) {
						this.nextTarget = vec3d;
					}

					boolean bl = BeedeeEntity.this.getPos().distanceTo(this.nextTarget) <= 0.1;
					boolean bl2 = true;
					if (!bl && this.ticks > 600) {
						BeedeeEntity.this.flowerPos = null;
					} else {
						if (bl) {
							boolean bl3 = BeedeeEntity.this.random.nextInt(25) == 0;
							if (bl3) {
								this.nextTarget = new Vec3d(vec3d.getX() + (double)this.getRandomOffset(), vec3d.getY(), vec3d.getZ() + (double)this.getRandomOffset());
								BeedeeEntity.this.navigation.stop();
							} else {
								bl2 = false;
							}

							BeedeeEntity.this.getLookControl().lookAt(vec3d.getX(), vec3d.getY(), vec3d.getZ());
						}

						if (bl2) {
							this.moveToNextTarget();
						}

						this.pollinationTicks++;
						if (BeedeeEntity.this.random.nextFloat() < 0.05F && this.pollinationTicks > this.lastPollinationTick + 60) {
							this.lastPollinationTick = this.pollinationTicks;
							BeedeeEntity.this.playSound(SoundEvents.ENTITY_BEE_POLLINATE, 1.0F, 1.0F);
						}
					}
				}
			}
		}

		private void moveToNextTarget() {
			BeedeeEntity.this.getMoveControl().moveTo(this.nextTarget.getX(), this.nextTarget.getY(), this.nextTarget.getZ(), 0.35F);
		}

		private float getRandomOffset() {
			return (BeedeeEntity.this.random.nextFloat() * 2.0F - 1.0F) * 0.33333334F;
		}

		private Optional<BlockPos> getFlower() {
			return this.findFlower(this.flowerPredicate, 5.0);
		}

		private Optional<BlockPos> findFlower(Predicate<BlockState> predicate, double searchDistance) {
			BlockPos blockPos = BeedeeEntity.this.getBlockPos();
			BlockPos.Mutable mutable = new BlockPos.Mutable();

			for (int i = 0; (double)i <= searchDistance; i = i > 0 ? -i : 1 - i) {
				for (int j = 0; (double)j < searchDistance; j++) {
					for (int k = 0; k <= j; k = k > 0 ? -k : 1 - k) {
						for (int l = k < j && k > -j ? j : 0; l <= j; l = l > 0 ? -l : 1 - l) {
							mutable.set(blockPos, k, i - 1, l);
							if (blockPos.isWithinDistance(mutable, searchDistance) && predicate.test(BeedeeEntity.this.getWorld().getBlockState(mutable))) {
								return Optional.of(mutable);
							}
						}
					}
				}
			}

			return Optional.empty();
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
//			super(bee, PlayerEntity.class, 10, true, false, bee::shouldAngerAt);
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
