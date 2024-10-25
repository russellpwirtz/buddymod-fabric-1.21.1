package com.dangerussell.blockStructure;

import com.dangerussell.entities.ai.goals.CreateStructureGoal;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class Bo2BlockMapping implements BlockMapping {

  private final Map<String, String> blockMapping;
  private static final Gson gson = new Gson();
  private static final Logger LOGGER = LogUtils.getLogger();

  public Bo2BlockMapping() {
    this.blockMapping = new HashMap<>();
    try (InputStream inputStream = CreateStructureGoal.class.getClassLoader().getResourceAsStream("assets/entitytesting/structures/idmapping_raw.json")) {
      if (inputStream != null) {
        Type collectionType = new TypeToken<Collection<List<Object>>>(){}.getType();
        Collection<List<Object>> collection = gson.fromJson(new InputStreamReader(inputStream, StandardCharsets.UTF_8), collectionType);

        for (List<Object> list : collection) {
          String blockInt = String.valueOf(((Double)list.get(0)).intValue());
          String blockVariant = String.valueOf(((Double)list.get(1)).intValue());
          String blockName = ((String)list.get(2));
          String key = blockInt + ":" + blockVariant;
          if (!blockMapping.containsKey(key)) {
            LOGGER.info("Adding key:{} value: {}", key, blockName);
            blockMapping.put(key, blockName);
          }
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Override
  public String getBlockName(String id) {
    return blockMapping.get(id);
  }
}
