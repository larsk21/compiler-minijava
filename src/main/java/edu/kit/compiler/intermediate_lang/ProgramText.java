package edu.kit.compiler.intermediate_lang;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * ProgramText is a linear order of blocks which can be lowered to x64 assembly
 */
public class ProgramText {

    private final List<Block> blocks = new LinkedList<>();

    public void addBlock(Block block) {
        blocks.add(block);
    }
    public Block getBlock(int num) {
        return blocks.get(num);
    }

}
