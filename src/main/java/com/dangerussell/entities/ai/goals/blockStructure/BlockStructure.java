package com.dangerussell.entities.ai.goals.blockStructure;
import java.util.List;

public class BlockStructure {
  public String name;
  public int[] origin;
  public List<BlockCoordinate> blocks;

  public static class BlockCoordinate {
    public int[] position;
    public String block;
  }
}
