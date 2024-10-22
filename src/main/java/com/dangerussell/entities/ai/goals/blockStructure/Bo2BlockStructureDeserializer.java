package com.dangerussell.entities.ai.goals.blockStructure;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class Bo2BlockStructureDeserializer extends BlockStructureDeserializer {
  @Override
  public BlockStructure deserialize(InputStream inputStream) throws IOException {
    BlockStructure blockStructure = new BlockStructure();
    blockStructure.name = "Unknown Structure";

    try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
      String line;
//      List<String> metaLines = new ArrayList<>();
      List<BlockStructure.BlockCoordinate> blocks = new ArrayList<>();

      boolean foundData = false;
      while ((line = reader.readLine()) != null) {
        line = line.trim();

        if (!foundData && !line.startsWith("[DATA]")) {
          continue;
        }

        foundData = true;

        if (line.startsWith("[META]") || line.startsWith("[DATA]")) {
          continue;
        }

        String[] parts = line.split(":");
        if (parts.length != 2) {
          throw new RuntimeException("Invalid format: " + line);
        }

        String[] coords = parts[0].split(",");
        if (coords.length != 3) {
          throw new RuntimeException("Invalid coordinates: " + parts[0]);
        }

        BlockStructure.BlockCoordinate block = new BlockStructure.BlockCoordinate();
        block.position = new int[]{Integer.parseInt(coords[0]), Integer.parseInt(coords[2]), Integer.parseInt(coords[1])};
        block.block = "dirt"; // for now

        blocks.add(block);
      }

      blockStructure.blocks = blocks;

//      parseMeta(metaLines, blockStructure);
    }

    if (validateStructureDefinition(blockStructure)) {
      return blockStructure;
    } else {
      throw new IOException("Unable to parse structure definition");
    }
  }

//  private void parseMeta(List<String> metaLines, BlockStructure blockStructure) {
//    for (String metaLine : metaLines) {
//      String[] parts = metaLine.split("=");
//      if (parts.length != 2) {
//        throw new RuntimeException("Invalid meta line: " + metaLine);
//      }
//
//      switch (parts[0]) {
//        case "spawnSunlight":
//          blockStructure.spawnSunlight = Boolean.parseBoolean(parts[1]);
//          break;
//        case "spawnDarkness":
//          blockStructure.spawnDarkness = Boolean.parseBoolean(parts[1]);
//          break;
//        // ... add more fields as needed
//        default:
//          throw new RuntimeException("Unknown meta key: " + parts[0]);
//      }
//    }
//  }
}