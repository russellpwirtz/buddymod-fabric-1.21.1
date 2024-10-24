package com.dangerussell.blockStructure;
import java.util.List;

public class BlockStructure {
  public String name;
  public int[] origin = new int[] { 0, 0, 0 };
  public List<BlockCoordinate> blocks;

  public static class BlockCoordinate {
    public int[] position;
    public String block;
  }
}
