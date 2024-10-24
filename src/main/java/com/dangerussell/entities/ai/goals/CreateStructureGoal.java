package com.dangerussell.entities.ai.goals;

import com.dangerussell.blockStructure.BlockStructure;
import com.dangerussell.blockStructure.Bo2BlockStructureDeserializer;
import com.mojang.logging.LogUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.EnumSet;

public class CreateStructureGoal extends Goal {
  private final MobEntity mob;
  private final PlayerEntity player;
  private boolean isRunning = false;
  private static final Logger LOGGER = LogUtils.getLogger();
  private int currentBlockIndex = 0;
  private BlockStructure structureDefinition;
  private int blockPlacementCooldown = 0;
  private static final int COOLDOWN_TICKS = 5;
  private BlockPos startPosition;

//  private static final String BEEHIVE_FILE = "assets/entitytesting/structures/beehive_structure.json";
//  private static final String BEEHIVE_FILE = "assets/entitytesting/structures/tower01.bo2";
  private static final String BEEHIVE_FILE = "assets/entitytesting/structures/23.bo2";

  public CreateStructureGoal(MobEntity mob, PlayerEntity player) {
    this.mob = mob;
    this.player = player;
    this.setControls(EnumSet.of(Control.MOVE));
  }

  @Override
  public boolean canStart() {
    return this.mob.getNavigation().isIdle() && !this.isRunning
            && this.player != null
            && this.player.getStackInHand(Hand.OFF_HAND) != null
            && this.player.getStackInHand(Hand.OFF_HAND).getItem().equals(Items.BEEHIVE);
  }

  @Override
  public boolean shouldContinue() {
    return this.isRunning;
  }

  @Override
  public void tick() {
    try {
      if (this.mob.getWorld() instanceof ServerWorld serverWorld) {
        if (!this.isRunning) {
          this.isRunning = true;
          this.startPosition = this.mob.getBlockPos();
          this.blockPlacementCooldown = 0;
          this.currentBlockIndex = 0;
          this.structureDefinition = getStructureDefinition(BEEHIVE_FILE);
        }

        if (this.player != null && this.player.isCreative()) {
          // Build the entire structure in 1 tick if in Creative mode
          for (int i = 0; i < structureDefinition.blocks.size(); i++) {
            BlockStructure.BlockCoordinate coord = structureDefinition.blocks.get(i);
            BlockPos targetPos = new BlockPos(
                    this.startPosition.getX() + structureDefinition.origin[0] + coord.position[0],
                    this.startPosition.getY() + structureDefinition.origin[1] + coord.position[1],
                    this.startPosition.getZ() + structureDefinition.origin[2] + coord.position[2]
            );

            Identifier blockId = Identifier.of(coord.block.contains(":") ? coord.block : "minecraft:" + coord.block);
            Block blockInstance = Registries.BLOCK.get(blockId);
            BlockState newBlockState = blockInstance.getDefaultState();
            if (canPlaceBlock(serverWorld, targetPos)) {
              serverWorld.setBlockState(targetPos, newBlockState, 3);
              LOGGER.info("Placed block {} at {}", coord.block, targetPos);
            } else {
              LOGGER.info("Could not place block {} at {}", coord.block, targetPos);
            }
          }
          this.isRunning = false;
          this.blockPlacementCooldown = 0;
          this.currentBlockIndex = 0;
          LOGGER.info("Structure creation completed");
        } else {
          if (this.blockPlacementCooldown == 0) {
            if (currentBlockIndex < structureDefinition.blocks.size()) {
              BlockStructure.BlockCoordinate coord = structureDefinition.blocks.get(currentBlockIndex);
              BlockPos targetPos = new BlockPos(
                      this.startPosition.getX() + structureDefinition.origin[0] + coord.position[0],
                      this.startPosition.getY() + structureDefinition.origin[1] + coord.position[1],
                      this.startPosition.getZ() + structureDefinition.origin[2] + coord.position[2]
              );

              Identifier blockId = Identifier.of(coord.block.contains(":") ? coord.block : "minecraft:" + coord.block);
              Block blockInstance = Registries.BLOCK.get(blockId);
              BlockState newBlockState = blockInstance.getDefaultState();
              if (canPlaceBlock(serverWorld, targetPos)) {
                serverWorld.setBlockState(targetPos, newBlockState, 3);
                LOGGER.info("Placed block {} at {}", coord.block, targetPos);
              } else {
                LOGGER.info("Could not place block {} at {}", coord.block, targetPos);
              }
              currentBlockIndex++;

              if (currentBlockIndex >= structureDefinition.blocks.size()) {
                this.isRunning = false;
                this.blockPlacementCooldown = 0;
                this.currentBlockIndex = 0;
                LOGGER.info("Structure creation completed");
              }
            }
            this.blockPlacementCooldown = COOLDOWN_TICKS;
          } else {
            this.blockPlacementCooldown--;
          }
        }
      }
    } catch (Exception e) {
      LOGGER.error("Unable to start structure creation: ", e);
    }
  }

  private BlockStructure getStructureDefinition(String fileName) throws IOException {
    try (InputStream inputStream = CreateStructureGoal.class.getClassLoader().getResourceAsStream(fileName)) {
      if (inputStream != null) {
//        return new JsonBlockStructureDeserializer().deserialize(inputStream);
        return new Bo2BlockStructureDeserializer().deserialize(inputStream);
      } else {
        LOGGER.error("Could not find structure resource");
        throw new IOException("Unable to deserialize structure");
      }
    }
  }

  private boolean canPlaceBlock(ServerWorld world, BlockPos pos) {
    BlockState state = world.getBlockState(pos);
    if (this.mob.getPos().isWithinRangeOf(pos.toCenterPos(), 1f, 1f)) {
      return false;
    }
    return state.isAir();
  }
}
