package com.dangerussell.entities.ai.goals;

import com.google.gson.Gson;
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
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;

public class CreateStructureGoal extends Goal {
  private final MobEntity mob;
  private final PlayerEntity player;
  private boolean isRunning = false;
  private static final Logger LOGGER = LogUtils.getLogger();
  private int currentBlockIndex = 0;
  private int blockPlacementCooldown = 0;
  private static final int COOLDOWN_TICKS = 5;

  public CreateStructureGoal(MobEntity mob, PlayerEntity player) {
    this.mob = mob;
    this.player = player;
    this.setControls(EnumSet.of(Control.MOVE));
  }

  private static StructuredBlockGrid structureDefinition;

  static {
    try (InputStream inputStream = CreateStructureGoal.class.getClassLoader()
            .getResourceAsStream("assets/entitytesting/structures/beehive_structure.json")) {
      if (inputStream != null) {
        Gson gson = new Gson();
        structureDefinition = gson.fromJson(new InputStreamReader(inputStream, StandardCharsets.UTF_8), StructuredBlockGrid.class);
      } else {
        LOGGER.error("Could not find beehive_structure.json resource");
      }
    } catch (IOException e) {
      LOGGER.error("Error reading beehive_structure.json resource", e);
    }

    if (!validateStructure(structureDefinition)) {
      LOGGER.error("Failed to initialize structure definition. Exiting.");
      System.exit(1);
    }
  }

  private static class StructuredBlockGrid {
    public String name;
    public int[] origin;
    public List<BlockCoordinate> blocks;

    public static class BlockCoordinate {
      public int[] position;
      public String block;
    }
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
    if (this.mob.getWorld() instanceof ServerWorld serverWorld) {
      if (!this.isRunning) {
        this.isRunning = true;
        this.blockPlacementCooldown = 0;
      }

      if (this.blockPlacementCooldown == 0) {
        if (currentBlockIndex < structureDefinition.blocks.size()) {
          StructuredBlockGrid.BlockCoordinate blockCoord = structureDefinition.blocks.get(currentBlockIndex);
          int centerX = (int) this.mob.getX() + structureDefinition.origin[0];
          int startY = (int) this.mob.getY() + structureDefinition.origin[1];
          int centerZ = (int) this.mob.getZ() + structureDefinition.origin[2];

          BlockPos targetPos = new BlockPos(
                  centerX + blockCoord.position[0],
                  startY + blockCoord.position[1],
                  centerZ + blockCoord.position[2]
          );

          Identifier blockId = Identifier.of(blockCoord.block.contains(":") ? blockCoord.block : "minecraft:" + blockCoord.block);
          Block blockInstance = Registries.BLOCK.get(blockId);
          BlockState newBlockState = blockInstance.getDefaultState();
          if (canPlaceBlock(serverWorld, targetPos)) {
            serverWorld.setBlockState(targetPos, newBlockState, 3);
            LOGGER.info("Placed block {} at {}", blockCoord.block, targetPos);
          } else {
            LOGGER.info("Could not place block {} at {}", blockCoord.block, targetPos);
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

  private boolean canPlaceBlock(ServerWorld world, BlockPos pos) {
    BlockState state = world.getBlockState(pos);
    if (this.mob.getPos().isWithinRangeOf(pos.toCenterPos(), 1f, 1f)) {
      return false;
    }
    return state.isAir();
  }

  /**
   * Validates a given StructuredBlockGrid map to ensure it conforms to the expected format.
   *
   * @param structureMap the map to be validated
   * @return true if the structure map is valid, false otherwise
   */
  private static boolean validateStructure(StructuredBlockGrid structureMap) {
    if (structureMap == null) {
      LOGGER.error("Structure map is null");
      return false;
    }

    if (structureMap.name == null || structureMap.name.isEmpty()) {
      LOGGER.error("Structure name is empty or null");
      return false;
    }

    if (structureMap.origin == null || structureMap.origin.length != 3) {
      LOGGER.error("Structure origin must be an array of exactly 3 integers (x, y, z)");
      return false;
    }

    if (structureMap.blocks == null || structureMap.blocks.isEmpty()) {
      LOGGER.error("Structure blocks list is empty or null");
      return false;
    }

    for (StructuredBlockGrid.BlockCoordinate blockCoord : structureMap.blocks) {
      if (blockCoord == null) {
        LOGGER.error("Null BlockCoordinate found in structure blocks");
        return false;
      }

      if (blockCoord.position == null || blockCoord.position.length != 3) {
        LOGGER.error("Block position must be an array of exactly 3 integers (x, y, z) for block {}", blockCoord.block);
        return false;
      }
    }

    LOGGER.info("Structure map validated successfully");
    return true;
  }

}
