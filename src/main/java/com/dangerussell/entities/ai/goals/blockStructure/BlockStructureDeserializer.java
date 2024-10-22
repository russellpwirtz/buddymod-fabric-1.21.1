package com.dangerussell.entities.ai.goals.blockStructure;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;

public abstract class BlockStructureDeserializer {
  protected static final Logger LOGGER = LogUtils.getLogger();

  public abstract BlockStructure deserialize(InputStream inputStream) throws IOException;

  public boolean validateStructureDefinition(BlockStructure blockStructure) {
    return validateStructure(blockStructure);
  }

  /**
   * Validates a given StructuredBlockGrid map to ensure it conforms to the expected format.
   *
   * @param structureMap the map to be validated
   * @return true if the structure map is valid, false otherwise
   */
  protected boolean validateStructure(BlockStructure structureMap) {
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

    for (BlockStructure.BlockCoordinate blockCoord : structureMap.blocks) {
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
