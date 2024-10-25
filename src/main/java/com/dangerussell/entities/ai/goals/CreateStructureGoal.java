package com.dangerussell.entities.ai.goals;

import com.dangerussell.blockStructure.BlockStructure;
import com.dangerussell.blockStructure.Bo2BlockStructureDeserializer;
import com.mojang.logging.LogUtils;
import net.minecraft.block.*;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.DoorHinge;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

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
  private static final String BASE_ASSET_DIR = "assets/entitytesting/structures/";

  //  private static final String STRUCTURE_FILE = BASE_ASSET_DIR + "beehive_structure.json";
    private static final String STRUCTURE_FILE = BASE_ASSET_DIR + "5_modern-tower.bo2";
//  private static final String STRUCTURE_FILE = BASE_ASSET_DIR + "3_agility-arena.bo2";
//  private static final String STRUCTURE_FILE = BASE_ASSET_DIR + "23_small-house.bo2";
//    private static final String STRUCTURE_FILE = BASE_ASSET_DIR + "2582_jar9-castle.bo2";

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
          this.structureDefinition = getStructureDefinition(STRUCTURE_FILE);
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

            if (coord.block == null ) {
              LOGGER.warn("Null block");
              continue;
            }
            String blockName = coord.block.split("\\[")[0];
            String blockVariant = coord.block.split("\\[").length < 2
                    ? "0"
                    : coord.block.split("\\[")[1].split("]")[0];

            Map<String, Object> variants = new HashMap<>();
            Arrays.stream(blockVariant.split(",")).forEach(variant -> {
              String[] keyValue = variant.split("=");
              if (keyValue.length == 2) {
                String key = keyValue[0].trim();
                String value = keyValue[1].trim();

                if (value.equals("true") || value.equals("false")) {
                  variants.put(key, Boolean.parseBoolean(value));
                } else {
                  variants.put(key, value);
                }
              }
            });

            LOGGER.info("Block name: {}, variant: {}", blockName, blockVariant);
            Identifier blockId = Identifier.of(blockName);
            Block blockInstance = Registries.BLOCK.get(blockId);

            if (blockInstance == Blocks.AIR) {
              LOGGER.warn("Placed AIR block {} at {}. Block: {}", blockName, targetPos, blockId);
              continue;
            }

            BlockState currentBlock = blockInstance.getDefaultState();

            try {
              for (String key : variants.keySet()) {
                currentBlock = setFacingValues(key, variants, currentBlock);
                currentBlock = setHalfValues(key, variants, currentBlock);
                currentBlock = setOpenValues(key, variants, currentBlock);
                currentBlock = setPoweredValues(key, variants, currentBlock);
                currentBlock = setHingeValues(key, variants, currentBlock);
//            {variant=dirt, snowy=false}
//            {variant=stone}
//            {variant=stonebrick}
//            minecraft:stone_brick_stairs[facing=west,half=bottom,shape=straight]
//            .with(SHAPE, RailShape.NORTH_SOUTH).with(POWERED, Boolean.valueOf(false))
//            .with(WATERLOGGED, Boolean.valueOf(false))
              }
            } catch (Exception e) {
              LOGGER.error("Unable to set variant: ", e);
            }

            if (canPlaceBlock(serverWorld, targetPos)) {
              serverWorld.setBlockState(targetPos, currentBlock, 3);
              LOGGER.info("Placed block {} at {}", blockName, targetPos);
            } else {
              BlockState existing = serverWorld.getBlockState(targetPos);
              LOGGER.info("Could not place block {} at {}. Existing: {}", blockName, targetPos, existing.getBlock().getName().getLiteralString());
            }
          }
//          this.isRunning = false;
          this.blockPlacementCooldown = 0;
          this.currentBlockIndex = 0;
          LOGGER.info("Structure creation completed");
        } else {
          // NON CREATIVE MODE // TODO
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

  private static BlockState setHalfValues(String key, Map<String, Object> variants, BlockState blockState) {
    String registryKey = blockState.getBlock().getRegistryEntry().getIdAsString();
    if (key.equals("half")) {
      if (registryKey.endsWith("slab") || registryKey.endsWith("slab2")) {
        return switch ((String) variants.get(key)) {
          case "top" -> blockState.with(Properties.SLAB_TYPE, SlabType.TOP);
          case "bottom" -> blockState.with(Properties.SLAB_TYPE, SlabType.BOTTOM);
          default -> blockState;
        };
      } else if (registryKey.endsWith("trapdoor")) {
        return switch ((String) variants.get(key)) {
          case "top" -> blockState.with(TrapdoorBlock.HALF, BlockHalf.TOP);
          case "bottom" -> blockState.with(TrapdoorBlock.HALF, BlockHalf.BOTTOM);
          default -> blockState;
        };
      } else if (registryKey.endsWith("door")) {
        return switch ((String) variants.get(key)) {
          case "upper" -> blockState.with(DoorBlock.HALF, DoubleBlockHalf.UPPER);
          case "lower" -> blockState.with(DoorBlock.HALF, DoubleBlockHalf.LOWER);
          default -> blockState;
        };
      } else if (registryKey.endsWith("stairs")) {
        return switch ((String) variants.get(key)) {
          case "top" -> blockState.with(StairsBlock.HALF, BlockHalf.TOP);
          case "bottom" -> blockState.with(StairsBlock.HALF, BlockHalf.BOTTOM);
          default -> blockState;
        };
      }
    }

    return blockState;
  }

  private static BlockState setOpenValues(String key, Map<String, Object> variants, BlockState blockState) {
    String registryKey = blockState.getBlock().getRegistryEntry().getIdAsString();
    if (key.equals("open")) {
      if (registryKey.endsWith("trapdoor")) {
        return blockState.with(TrapdoorBlock.OPEN, (Boolean) variants.get(key));
      } else if (registryKey.endsWith("door")) {
        blockState = blockState.with(DoorBlock.OPEN, (Boolean) variants.get(key));
      }
    }
    return blockState;
  }

  private static BlockState setPoweredValues(String key, Map<String, Object> variants, BlockState blockState) {
    String registryKey = blockState.getBlock().getRegistryEntry().getIdAsString();
    if (key.equals("powered")) {
      if (registryKey.endsWith("iron_door")) {
        blockState = blockState.with(DoorBlock.POWERED, (Boolean) variants.get(key));
      } else if (registryKey.endsWith("pressure_plate")) {
        blockState = blockState.with(PressurePlateBlock.POWERED, (Boolean) variants.get(key));
      }
      // TODO Rail
    }
    return blockState;
  }

  private static BlockState setHingeValues(String key, Map<String, Object> variants, BlockState blockState) {
    String registryKey = blockState.getBlock().getRegistryEntry().getIdAsString();
    if (key.equals("hinge")) {
      if (registryKey.endsWith("door")) {
        if (variants.get(key).equals("left")) {
          blockState = blockState.with(DoorBlock.HINGE, DoorHinge.LEFT);
        } else if (variants.get(key).equals("right")) {
          blockState = blockState.with(DoorBlock.HINGE, DoorHinge.RIGHT);
        }
      }
    }
    return blockState;
  }

  private static BlockState setFacingValues(String key, Map<String, Object> variants, BlockState blockState) {
    String registryKey = blockState.getBlock().getRegistryEntry().getIdAsString();
    if (key.equals("facing")) {
      if (registryKey.endsWith("_stairs")) {
        return setStairsFacing(key, blockState, variants);
      } else if (registryKey.endsWith("trapdoor")) {
        return setTrapdoorFacing(key, blockState, variants);
      } else if (registryKey.endsWith("_door")) {
        return setDoorFacing(key, blockState, variants);
      } else if (registryKey.endsWith("wall_sign")) {
        return setSignFacing(key, blockState, variants);
      } else if (registryKey.endsWith("ladder")) {
        return setLadderFacing(key, blockState, variants);
      }
    } else if (registryKey.endsWith("_pane") && Arrays.asList("north", "west", "south", "east").contains(key)) {
      return setPaneFacing(key, blockState, variants);
    }

    return blockState;
  }

  private static BlockState setStairsFacing(String key, BlockState blockState, Map<String, Object> variants) {
    return switch ((String)variants.get(key)) {
      case "east" -> blockState.with(StairsBlock.FACING, Direction.EAST);
      case "west" -> blockState.with(StairsBlock.FACING, Direction.WEST);
      case "south" -> blockState.with(StairsBlock.FACING, Direction.SOUTH);
      case "north" -> blockState.with(StairsBlock.FACING, Direction.NORTH);
      default -> blockState;
    };
  }

  private static BlockState setDoorFacing(String key, BlockState blockState, Map<String, Object> variants) {
    return switch ((String)variants.get(key)) {
      case "east" -> blockState.with(HorizontalFacingBlock.FACING, Direction.EAST);
      case "west" -> blockState.with(HorizontalFacingBlock.FACING, Direction.WEST);
      case "south" -> blockState.with(HorizontalFacingBlock.FACING, Direction.SOUTH);
      case "north" -> blockState.with(HorizontalFacingBlock.FACING, Direction.NORTH);
      default -> blockState;
    };
  }

  private static BlockState setTrapdoorFacing(String key, BlockState blockState, Map<String, Object> variants) {
    return switch ((String)variants.get(key)) {
      case "east" -> blockState.with(TrapdoorBlock.FACING, Direction.EAST);
      case "west" -> blockState.with(TrapdoorBlock.FACING, Direction.WEST);
      case "south" -> blockState.with(TrapdoorBlock.FACING, Direction.SOUTH);
      case "north" -> blockState.with(TrapdoorBlock.FACING, Direction.NORTH);
      default -> blockState;
    };
  }

  private static BlockState setPaneFacing(String key, BlockState blockState, Map<String, Object> variants) {
    return switch (key) {
      case "east" -> blockState.with(PaneBlock.EAST, (Boolean) variants.get(key));
      case "west" -> blockState.with(PaneBlock.WEST, (Boolean) variants.get(key));
      case "north" -> blockState.with(PaneBlock.NORTH, (Boolean) variants.get(key));
      case "south" -> blockState.with(PaneBlock.SOUTH, (Boolean) variants.get(key));
      default -> blockState;
    };
  }

  private static BlockState setSignFacing(String key, BlockState blockState, Map<String, Object> variants) {
    return switch ((String)variants.get(key)) {
      case "north" -> blockState.with(WallHangingSignBlock.FACING, Direction.NORTH);
      case "west" -> blockState.with(WallHangingSignBlock.FACING, Direction.WEST);
      case "south" -> blockState.with(WallHangingSignBlock.FACING, Direction.SOUTH);
      case "east" -> blockState.with(WallHangingSignBlock.FACING, Direction.EAST);
      default -> blockState;
    };
  }

  private static BlockState setLadderFacing(String key, BlockState blockState, Map<String, Object> variants) {
    return switch ((String)variants.get(key)) {
      case "north" -> blockState.with(LadderBlock.FACING, Direction.NORTH);
      case "west" -> blockState.with(LadderBlock.FACING, Direction.WEST);
      case "south" -> blockState.with(LadderBlock.FACING, Direction.SOUTH);
      case "east" -> blockState.with(LadderBlock.FACING, Direction.EAST);
      default -> blockState;
    };
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
