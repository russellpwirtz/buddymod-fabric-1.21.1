package com.dangerussell.entities.ai.goals.blockStructure;

import com.google.gson.Gson;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
public class JsonBlockStructureDeserializer extends BlockStructureDeserializer {
  private static final Gson gson = new Gson();

  @Override
  public BlockStructure deserialize(InputStream inputStream) throws IOException {
    BlockStructure blockStructure = gson.fromJson(new InputStreamReader(inputStream, StandardCharsets.UTF_8), BlockStructure.class);
    if (validateStructureDefinition(blockStructure)) {
      return blockStructure;
    } else {
      throw new IOException("Unable to parse structure definition");
    }
  }
}