package com.dangerussell.blockStructure;

import com.dangerussell.entities.ai.goals.CreateStructureGoal;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Bo2BlockMapping implements BlockMapping {

  private final Map<String, String> blockMapping;
  private static final Gson gson = new Gson();

  public Bo2BlockMapping() {
    this.blockMapping = new HashMap<>();
    try (InputStream inputStream = CreateStructureGoal.class.getClassLoader().getResourceAsStream("assets/entitytesting/structures/idmapping_raw.json")) {
      if (inputStream != null) {
        Type collectionType = new TypeToken<Collection<List<Object>>>(){}.getType();
        Collection<List<Object>> collection = gson.fromJson(new InputStreamReader(inputStream, StandardCharsets.UTF_8), collectionType);

        for (List<Object> list : collection) {
          String blockInt = ((Double)list.get(0)).toString().split("\\.")[0];
          String blockName = ((String)list.get(2)).split("\\[")[0];
          if (!blockMapping.containsKey(blockInt)) {
            blockMapping.put(blockInt, blockName);
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
